@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import platform.posix.*
import kotlinx.serialization.json.*

internal class CliError(val code: Int, override val message: String) : Exception(message)
internal fun fail(code: Int, message: String): Nothing = throw CliError(code, message)
internal val json = Json { prettyPrint = true }
internal fun absolute(path: String): String {
    if (path.startsWith('/')) return path
    val cwd = memScoped { val b = allocArray<ByteVar>(4096); getcwd(b,4096u)?.toKString() } ?: fail(10,"Cannot resolve current directory")
    return "$cwd/$path"
}
internal val home: String get() {
    val ownerHome=xf_home()?.toKString() ?: fail(10,"Cannot resolve user home")
    return profileRoot(getuid().toUInt(),ownerHome)
}
internal val dataDir get() = "$home/.local/share/xrayfa"
internal val logDir get() = "$home/.local/state/xrayfa/logs"
internal fun privateDir(path: String) {
    // Shared parent directories are allowed; the application leaf must be private and owned.
    val parent = path.substringBeforeLast('/', "")
    if (parent.isNotEmpty() && access(parent,F_OK)!=0) privateDir(parent)
    if (mkdir(path,448u)!=0 && xf_errno()!=EEXIST) fail(10,"Cannot create private directory")
    if (xf_owned(path,1)!=1) fail(10,"Unsafe directory ownership, permissions or symbolic link")
}
internal fun ownedFile(path: String, missing: Boolean = false) {
    val state=xf_owned(path,0)
    if (state<0 || (!missing && state==0)) fail(10,"Unsafe or missing private file")
}
internal fun readBytes(path: String, max: Int = 16*1024*1024, missing: Boolean = false): ByteArray? {
    val fd=open(path,O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
    if (fd<0) { if (missing && xf_errno()==ENOENT) return null; fail(10,"Cannot read file") }
    try {
        val parts=ArrayList<ByteArray>(); var total=0; val b=ByteArray(8192)
        while (true) {
            val n=b.usePinned { read(fd,it.addressOf(0),b.size.convert()).toInt() }
            if(n==0) break
            if(n<0) { if(xf_errno()==EINTR) continue; fail(10,"File read failed") }
            total+=n; if(total>max) fail(10,"File exceeds size limit")
            parts.add(b.copyOf(n))
        }
        return ByteArray(total).also { out -> var pos=0; for(p in parts) { p.copyInto(out,pos); pos+=p.size } }
    } finally { close(fd) }
}
internal fun writeFd(fd: Int, data: ByteArray) {
    var pos=0
    while(pos<data.size) {
        val n=data.usePinned { write(fd,it.addressOf(pos),(data.size-pos).convert()).toInt() }
        if(n<0 && xf_errno()==EINTR) continue
        if(n<=0) fail(10,"File write failed")
        pos+=n
    }
}
internal fun newFile(path: String, text: String) {
    val fd=open(path,O_WRONLY or O_CREAT or O_EXCL or O_NOFOLLOW or O_CLOEXEC,384)
    if(fd<0) fail(if(xf_errno()==EEXIST) 8 else if(xf_errno()==EACCES) 4 else 10,"Cannot create output file (existing outputs are preserved)")
    try { writeFd(fd,text.encodeToByteArray()); if(fsync(fd)!=0) fail(10,"File sync failed") }
    finally { close(fd) }
}
internal fun <T> storeLock(block: () -> T): T {
    privateDir(dataDir)
    val path="$dataDir/nodes.lock"
    val fd=open(path,O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC,384)
    if(fd<0) fail(10,"Cannot open node lock")
    try { ownedFile(path); if(xf_lock(fd)!=0) fail(10,"Cannot lock nodes"); return block() }
    finally { close(fd) }
}
internal fun loadStore(): JsonObject {
    val path="$dataDir/nodes.json"; ownedFile(path,missing=true)
    val text=readBytes(path,missing=true)?.decodeToString() ?: return buildJsonObject {
        put("schemaVersion",1);put("nextId",1);putJsonArray("nodes"){}
    }
    try {
        val store=json.parseToJsonElement(text).jsonObject
        require(store["schemaVersion"]?.jsonPrimitive?.int==1)
        val next=store.getValue("nextId").jsonPrimitive.int
        val ids=store.getValue("nodes").jsonArray.map {
            val n=it.jsonObject
            require(n.getValue("url").jsonPrimitive.content.isNotBlank())
            require(n.getValue("port").jsonPrimitive.int in 1..65535)
            require(n.getValue("address").jsonPrimitive.content.isNotBlank())
            require(n.getValue("name").jsonPrimitive.isString)
            require(n.getValue("protocol").jsonPrimitive.content in setOf("vless","vmess","trojan","ss","hysteria2","hy2","socks","socks5","http"))
            n.getValue("id").jsonPrimitive.int
        }
        require(ids.all { it>0 && it<next } && ids.toSet().size==ids.size && next>0)
        return store
    } catch (_:Exception) { fail(10,"Node file is corrupt or has an unsupported schema; original preserved") }
}
internal fun saveStore(store: JsonObject) {
    val temp="$dataDir/.nodes-${getpid()}-${xf_millis()}.tmp"
    try {
        newFile(temp,json.encodeToString(JsonObject.serializer(),store))
        if(rename(temp,"$dataDir/nodes.json")!=0) fail(10,"Atomic node replacement failed")
        val dir=open(dataDir,O_RDONLY or O_DIRECTORY or O_CLOEXEC)
        if(dir<0) fail(10,"Cannot open node directory")
        try { if(fsync(dir)!=0) fail(10,"Node directory sync failed") } finally { close(dir) }
    } finally { unlink(temp) }
}
internal fun utc(): String = memScoped {
    val now=alloc<time_tVar>(); time(now.ptr)
    val tm=alloc<tm>(); gmtime_r(now.ptr,tm.ptr)
    val b=allocArray<ByteVar>(32); strftime(b,32u,"%Y-%m-%dT%H:%M:%SZ",tm.ptr); b.toKString()
}
internal fun clean(text: String): String = text.map { if(it.code<32 || it.code==127) ' ' else it }.joinToString("")
