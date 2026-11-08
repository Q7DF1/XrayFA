@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

internal fun procStart(pid:Int):String? = try {
    readBytes("/proc/$pid/stat",8192)!!.decodeToString().substringAfterLast(") ").split(' ')[19]
} catch(_:CliError){null}
internal fun linkTarget(path:String):String?=memScoped {
    val b=allocArray<ByteVar>(4096)
    val n=readlink(path,b,4095u).toInt()
    if(n<0)null else {b[n]=0;b.toKString()}
}
internal fun leaseNonce():String {
    val bytes=UByteArray(16)
    if(bytes.usePinned{xf_random_bytes(it.addressOf(0),bytes.size)}!=0)fail(10,"Cannot generate lease ownership token")
    return bytes.joinToString(""){it.toString(16).padStart(2,'0')}
}
internal fun bootId()=readBytes("/proc/sys/kernel/random/boot_id",128)!!.decodeToString().trim()
internal fun networkNamespace()=linkTarget("/proc/self/ns/net") ?: fail(5,"Cannot identify network namespace")
internal fun atomicPrivate(path:String,value:JsonObject) {
    val parent=path.substringBeforeLast('/')
    val tmp="$parent/.journal-${getpid()}-${xf_millis()}"
    try {
        newFile(tmp,json.encodeToString(JsonObject.serializer(),value)+"\n")
        if(rename(tmp,path)!=0)fail(10,"Journal atomic replacement failed")
        val fd=open(parent,O_RDONLY or O_DIRECTORY or O_NOFOLLOW or O_CLOEXEC)
        if(fd<0)fail(10,"Cannot open journal directory")
        try{if(fsync(fd)!=0)fail(10,"Journal directory sync failed")}finally{close(fd)}
    } finally {unlink(tmp)}
}
/** Global lease acquisition has no network side effects; an old journal is never overwritten. */
internal class NetworkLease private constructor(private val fd:Int,var record:JsonObject) {
    val path="/run/xrayfa/session.json"
    fun update(vararg fields:Pair<String,JsonElement>) {
        val next=JsonObject(record.toMutableMap().apply{putAll(fields)})
        atomicPrivate(path,next);record=next
    }
    /** Every network-mutating helper is gated until its exact identity is durable. */
    fun mutate(path:String,vararg arguments:String):String {
        if(path !in setOf(IP,NFT,RESOLVE))fail(10,"Unrecognized network mutation helper")
        val executable=open(path,O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
        if(executable<0)fail(7,"Cannot open network mutation helper")
        try {
            if(xf_trusted_core(executable)==0)fail(4,"Unsafe network mutation helper")
            val args=arguments.toList()
            val budget=cleanupDeadlineMillis?.let{it-xf_millis()-1000}
            if(budget!=null && budget<=0)fail(9,"Recovery deadline reached before mutation")
            val result=coreProcess(path,args,null,if(budget==null)5000 else minOf(5000,budget),ignoreSignals=true,
                gate=ProcessGate(executable){pid->
                    val self=open("/proc/self/exe",O_RDONLY or O_CLOEXEC)
                    if(self<0)fail(10,"Cannot pin controller executable identity")
                    val identity=try{buildJsonObject {
                        put("pid",pid);put("start",procStart(pid) ?: fail(10,"Cannot identify gated network helper"))
                        put("path",path);put("device",xf_fd_device(executable).toString());put("inode",xf_fd_inode(executable).toString())
                        put("gateDevice",xf_fd_device(self).toString());put("gateInode",xf_fd_inode(self).toString())
                        put("argv",JsonArray((listOf(path)+args).map(::JsonPrimitive)))
                        put("gateArgv",JsonArray(readBytes("/proc/self/cmdline",65536)!!.decodeToString().split('\u0000').filter{it.isNotEmpty()}.map(::JsonPrimitive)))
                        put("state","gated");put("intentUtc",utc())
                    }}finally{close(self)}
                    update("pendingTool" to identity)
                })
            val old=record.getValue("pendingTool").jsonObject
            update("pendingTool" to JsonObject(old.toMutableMap().apply{
                put("state",JsonPrimitive("reaped"));put("exit",JsonPrimitive(result.exit));put("timedOut",JsonPrimitive(result.timedOut))
            }))
            if(result.exit!=0 || result.timedOut)fail(7,"Network mutation helper failed: $path ${args.take(2).joinToString(" ")}")
            return result.output.trim()
        } finally {close(executable)}
    }
    fun close(){close(fd)}
    companion object {
        fun attached(fd:Int,record:JsonObject)=NetworkLease(fd,record)
        fun acquire(caller:CallerIdentity,nodeId:Int,configHash:String,coreHash:String):NetworkLease {
            if(geteuid()!=0u)fail(4,"TUN sessions require root")
            privateDir("/run/xrayfa")
            val fd=open("/run/xrayfa/tun.lock",O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC,384)
            if(fd<0)fail(10,"Cannot open runtime lock")
            try {
                if(xf_fd_owned(fd,0u,0)==0)fail(10,"Unsafe runtime lock")
                if(xf_try_lock(fd)!=0)fail(8,"An active TUN session owns the global lock")
                if(access("/run/xrayfa/session.json",F_OK)==0)fail(9,"Previous journal retained; use recover before starting a session")
                val record=buildJsonObject {
                    put("schemaVersion",1);put("bootId",bootId());put("namespace",networkNamespace())
                    put("controllerPid",getpid());put("controllerStart",procStart(getpid()) ?: fail(10,"Cannot identify controller"))
                    put("controllerExe",linkTarget("/proc/self/exe") ?: fail(10,"Cannot identify CLI"))
                    put("uid",caller.uid.toLong());put("nodeId",nodeId);put("configSha256",configHash);put("coreSha256",coreHash)
                    put("ownershipToken",leaseNonce());put("phase","leased");put("createdUtc",utc());putJsonArray("intents"){}
                }
                try {
                    atomicPrivate("/run/xrayfa/session.json",record)
                } catch(e:Exception) {
                    // The journal is ours only after the pre-existing-path check
                    // above. If atomicPrivate renamed the file and then failed
                    // its directory fsync, remove that just-created lease before
                    // returning the pre-network exit-10 error; never touch an
                    // older journal belonging to another session.
                    if(access("/run/xrayfa/session.json",F_OK)==0)unlink("/run/xrayfa/session.json")
                    throw e
                }
                return NetworkLease(fd,record)
            } catch(e:Exception){close(fd);throw e}
        }
    }
}
internal fun nodeSnapshot(caller:CallerIdentity,id:Int):JsonObject {
    val fd=xf_snapshot_open_path(caller.profile,caller.uid)
    if(fd<0)fail(if(xf_errno()==ENOENT)3 else 10,"Cannot open caller's private node snapshot")
    val text=try {
        val bytes=ByteArray(16*1024*1024+1);var total=0
        while(total<bytes.size){
            val n=bytes.usePinned{read(fd,it.addressOf(total),(bytes.size-total).convert()).toInt()}
            if(n==0)break
            if(n<0){if(xf_errno()==EINTR)continue;fail(10,"Node snapshot read failed")};total+=n
        }
        if(total>=bytes.size)fail(10,"Node snapshot exceeds size limit")
        bytes.copyOf(total).decodeToString()
    }finally{close(fd)}
    val store=try{json.parseToJsonElement(text).jsonObject}catch(_:Exception){fail(10,"Invalid node store JSON")}
    try {
        require(store.getValue("schemaVersion").jsonPrimitive.int==1)
        val nodes=store.getValue("nodes").jsonArray.map{it.jsonObject}
        val next=store.getValue("nextId").jsonPrimitive.int
        val ids=nodes.map{n->
            require(n.getValue("url").jsonPrimitive.content.isNotBlank())
            require(n.getValue("port").jsonPrimitive.int in 1..65535)
            require(n.getValue("address").jsonPrimitive.content.isNotBlank())
            require(n.getValue("name").jsonPrimitive.isString)
            require(n.getValue("protocol").jsonPrimitive.content in setOf("vless","vmess","trojan","ss","hysteria2","hy2","socks","socks5","http"))
            n.getValue("id").jsonPrimitive.int
        }
        require(next>0 && ids.all{it>0 && it<next} && ids.distinct().size==ids.size)
        return nodes.firstOrNull{it.getValue("id").jsonPrimitive.int==id} ?: fail(3,"Node ID does not exist")
    }catch(e:CliError){throw e}catch(_:Exception){fail(10,"Unsupported node snapshot schema")}
}
