@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import com.android.xrayfa.common.utils.calculateBytesHash
import kotlinx.cinterop.*
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.*
import platform.posix.*

private const val VERSION="0.2.0-runtime-dev"
private val help="""XrayFA $VERSION — Linux TUN client (runtime development)
Usage:
  xrayfa add (--link URI | --link-file FILE)
  xrayfa ls
  xrayfa doctor [--core PATH] [--json]
  sudo xrayfa run ID [--core PATH] [--log-level debug|info|warning|error]
  sudo xrayfa stop
  sudo xrayfa recover
  xrayfa check ID --output FILE [--core PATH] [--log-level debug|info|warning|error]
  xrayfa logs [--system] [--tail N] [--follow]
  xrayfa --version | --help
run requires root, stays in the foreground and owns a TUN session. Ctrl+C or `stop` stops it.
System logs: /var/log/xrayfa/<UID>; recovery journal: /run/xrayfa/session.json
Private data: ~/.local/share/xrayfa; logs: ~/.local/state/xrayfa/logs
XRAYFA_HOME isolates add/ls/check/user logs. Runtime commands use the passwd caller identity.
"""
internal fun options(args:List<String>,allowed:Set<String>,flags:Set<String> = emptySet()):Map<String,String> {
    val result=mutableMapOf<String,String>();var i=0
    while(i<args.size) {
        val key=args[i++]
        if(key !in allowed || key in result) fail(2,"Unknown or repeated option")
        result[key]=if(key in flags) "true" else {
            if(i==args.size || args[i].startsWith("--")) fail(2,"Missing option value")
            args[i++]
        }
    }
    return result
}
internal fun hashFile(path:String,follow:Boolean=false):String {
    // Core is larger than node/config files. Streaming avoids loading it into memory.
    val fd=open(path,O_RDONLY or (if(follow)0 else O_NOFOLLOW) or O_CLOEXEC)
    if(fd<0) fail(5,"Cannot read executable for identity hash")
    try {
        // Public API provides a streaming digest factory on Linux.
        val digest=com.android.xrayfa.common.utils.LinuxDigest.create("SHA-256")
        val b=ByteArray(65536)
        while(true) {
            val n=b.usePinned { read(fd,it.addressOf(0),b.size.convert()).toInt() }
            if(n==0) break
            if(n<0) {if(xf_errno()==EINTR)continue;fail(5,"Cannot hash core")}
            digest.update(b,0,n)
        }
        return digest.finalize().joinToString("") { (it.toInt() and 255).toString(16).padStart(2,'0') }
    } finally { close(fd) }
}
internal fun canonicalCore(path:String):String = memScoped {
    val b=allocArray<ByteVar>(4096)
    realpath(absolute(path),b)?.toKString() ?: fail(5,"Core path does not exist")
}
private suspend fun checkNode(id:Int,output:String,coreOption:String?,level:String):Int {
    val log=LogSession(id,level)
    var exitCode=7
    var primaryFailureAt:Long?=null
    var coreExit:Int?=null;var artifact:String?=null;var configHash:String?=null
    var corePath:String?=null;var coreHash:String?=null;var coreVersion="";var timeout=false;var interrupted=false
    var reportReserved=false;var reportPath:String?=null
    val parser=NodeParser()
    log.event("prepare","START client=$VERSION logs=${log.path}",force=true)
    try {
        val node=storeLock { loadStore().getValue("nodes").jsonArray.firstOrNull{it.jsonObject.getValue("id").jsonPrimitive.int==id}?.jsonObject }
            ?: fail(3,"Node ID does not exist")
        val link=node.getValue("url").jsonPrimitive.content
        // No parser exception text reaches logs: exceptions may contain link/JSON secrets.
        val outbound=try { parser.outbound(link) } catch(_:Exception) { fail(6,"Stored node/link validation failed") }
        log.secrets=secretValues(outbound)+link
        log.event("prepare","Node loaded: ${summary(node)}",force=true)
        val protocol=outbound.getValue("protocol").jsonPrimitive.content
        val storedProtocol=node.getValue("protocol").jsonPrimitive.content
        val expectedProtocol=mapOf("ss" to "shadowsocks","hy2" to "hysteria","hysteria2" to "hysteria","socks5" to "socks")[storedProtocol] ?: storedProtocol
        if(protocol!=expectedProtocol)fail(6,"Stored protocol does not match parsed outbound")
        val settings=outbound.getValue("settings").jsonObject
        val endpoint=when(protocol) {
            "vless","vmess"->settings.getValue("vnext").jsonArray.single().jsonObject
            "hysteria"->settings
            else->settings.getValue("servers").jsonArray.single().jsonObject
        }
        if(endpoint["address"]!=node["address"] || endpoint["port"]!=node["port"])fail(6,"Stored endpoint does not match parsed outbound")
        val final=runtimeConfig(outbound,level)
        require(final.getValue("inbounds").jsonArray.single().jsonObject.getValue("protocol").jsonPrimitive.content=="tun")
        val text=json.encodeToString(JsonObject.serializer(),final)+"\n"
        val path=absolute(output);val rp=path.removeSuffix(".json")+".validation.json"
        if(access(path,F_OK)==0 || access(rp,F_OK)==0) fail(8,"Output or validation report already exists")
        newFile(rp,"{}");reportReserved=true;reportPath=rp
        newFile(path,text);artifact=path;configHash=calculateBytesHash(text.encodeToByteArray())
        log.event("config","Final JSON saved: $path sha256=$configHash",force=true)
        parser.adaptations.forEach {log.event("adapt",it,force=true)}
        corePath=canonicalCore(coreOption ?: "${xf_home()?.toKString() ?: home}/app/Xray/xray")
        if(access(corePath,X_OK)!=0) fail(4,"Core is not executable")
        coreHash=hashFile(corePath)
        log.event("prepare","Core path=$corePath sha256=$coreHash",force=true)
        val version=coreProcess(corePath,listOf("version"),log,5000)
        if(version.exit!=0 || version.timedOut || version.interrupted) {primaryFailureAt=version.observedAtMillis;fail(if(version.timedOut || version.interrupted)7 else 5,"Core version probe failed")}
        coreVersion=version.output.trim()
        log.event("prepare","Core version=$coreVersion",force=true)
        val result=coreProcess(corePath,listOf("run","-test","-c",path),log)
        coreExit=result.exit;timeout=result.timedOut;interrupted=result.interrupted
        if(coreExit!=0 || timeout || interrupted)primaryFailureAt=result.observedAtMillis
        if(calculateBytesHash(readBytes(path)!!)!=configHash) fail(6,"Final JSON changed during validation")
        if(hashFile(corePath)!=coreHash) fail(5,"Core identity changed during validation")
        exitCode=when {timeout || interrupted->7; coreExit!=0->6;else->0}
        log.event("validate",if(exitCode==0) "Config validation passed; core_exit=0" else "Config validation failed; core_exit=$coreExit timeout=$timeout interrupted=$interrupted",severity=if(exitCode==0)"info" else "error",force=true)
    } catch(e:CliError) { if(primaryFailureAt==null)primaryFailureAt=xf_millis();exitCode=e.code;log.event("error",e.message,severity="error",force=true) }
      catch(_:Exception) { if(primaryFailureAt==null)primaryFailureAt=xf_millis();exitCode=6;log.event("error","Configuration generation/validation failed",severity="error",force=true) }
    finally {
        if(log.failedAtMillis?.let {primaryFailureAt==null || it<primaryFailureAt!!}==true)exitCode=11
        log.event("finish","END client=$VERSION exit=$exitCode core_exit=${coreExit ?: "not-executed"}",severity=if(exitCode==0)"info" else "error",force=true)
        log.close()
        if(log.failedAtMillis?.let {primaryFailureAt==null || it<primaryFailureAt!!}==true) {
            if(exitCode!=11) fputs("Log persistence failed; final check exit=11 (first observed failure)\n",stderr)
            exitCode=11
        }
        if(reportReserved) {
            val report=buildJsonObject {
                put("schemaVersion",1);put("clientVersion",VERSION);put("nodeId",id);put("utc",utc());put("sessionId",log.session)
                put("configPath",artifact?.let(::JsonPrimitive) ?: JsonNull);put("configSha256",configHash?.let(::JsonPrimitive) ?: JsonNull)
                put("corePath",corePath?.let(::JsonPrimitive) ?: JsonNull);put("coreVersion",coreVersion);put("coreSha256",coreHash?.let(::JsonPrimitive) ?: JsonNull)
                put("coreExitCode",coreExit?.let(::JsonPrimitive) ?: JsonNull);put("exitCode",exitCode);put("success",exitCode==0);put("timedOut",timeout);put("interrupted",interrupted);put("logPath",log.path)
                putJsonArray("adaptations"){parser.adaptations.forEach{add(it)}}
            }
            try {
                val fd=open(reportPath!!,O_WRONLY or O_TRUNC or O_NOFOLLOW or O_CLOEXEC)
                if(fd<0) fail(10,"Cannot write validation report")
                try {writeFd(fd,(json.encodeToString(JsonObject.serializer(),report)+"\n").encodeToByteArray());if(fsync(fd)!=0) fail(10,"Report sync failed")} finally {close(fd)}
            } catch(_:Exception) {if(exitCode==0)exitCode=10;fputs("Validation report write failed\n",stderr)}
        }
    }
    return exitCode
}
fun main(args:Array<String>) {
    umask(63u);xf_signals()
    val code=try { runBlocking {
        when(args.firstOrNull()) {
            null,"--help","-h"->{if(args.size>1)fail(2,"Unexpected arguments");println(help);0}
            "--version"->{if(args.size!=1)fail(2,"Unexpected arguments");println("XrayFA $VERSION");0}
            "run"->{
                val id=args.getOrNull(1)?.toIntOrNull()?.takeIf{it>0} ?: fail(2,"Positive node ID required")
                val opt=options(args.drop(2),setOf("--core","--log-level"))
                val level=opt["--log-level"] ?: "info"
                if(level !in setOf("debug","info","warning","error"))fail(2,"Invalid log level")
                runNode(id,opt["--core"],level)
            }
            "stop"->{if(args.size!=1)fail(2,"Unexpected arguments");stopSession()}
            "recover"->{if(args.size!=1)fail(2,"Unexpected arguments");recoverSession()}
            "doctor"->{
                val opt=options(args.drop(1),setOf("--core","--json"),setOf("--json"))
                doctor(opt["--core"],"--json" in opt)
            }
            "add"->{
                val opt=options(args.drop(1),setOf("--link","--link-file"))
                if(opt.size!=1)fail(2,"Exactly one link source is required")
                val link=opt["--link"] ?: readBytes(absolute(opt.getValue("--link-file")),1048576)!!.decodeToString().trim().also {
                    if(it.lines().size!=1 || it.isEmpty())fail(2,"Link file must contain exactly one link")
                }
                val node=try {NodeParser().parse(link)} catch(_:Exception) {fail(6,"Invalid or unsupported share link/parameters")}
                val record=storeLock {
                    val store=loadStore();val id=store.getValue("nextId").jsonPrimitive.int
                    if(id==Int.MAX_VALUE)fail(10,"Node ID range exhausted")
                    val n=nodeRecord(node,id)
                    saveStore(buildJsonObject {put("schemaVersion",1);put("nextId",id+1);put("nodes",JsonArray(store.getValue("nodes").jsonArray+n))});n
                }
                println("Added node ${summary(record)}");0
            }
            "ls"->{if(args.size!=1)fail(2,"Unexpected arguments");storeLock {loadStore().getValue("nodes").jsonArray.map {summary(it.jsonObject)}}.forEach(::println);0}
            "check"->{
                val id=args.getOrNull(1)?.toIntOrNull()?.takeIf{it>0} ?: fail(2,"Positive node ID required")
                val opt=options(args.drop(2),setOf("--output","--core","--log-level"))
                val output=opt["--output"] ?: fail(2,"--output is required")
                val level=opt["--log-level"] ?: "info"
                if(level !in setOf("debug","info","warning","error"))fail(2,"Invalid log level")
                checkNode(id,output,opt["--core"],level)
            }
            "logs"->{
                val opt=options(args.drop(1),setOf("--tail","--follow","--system"),setOf("--follow","--system"))
                val tail=opt["--tail"]?.toIntOrNull() ?: if("--tail" in opt)fail(2,"Invalid tail count") else 100
                if(tail !in 0..10000)fail(2,"Tail count must be 0..10000")
                val directory=if("--system" in opt){if(geteuid()!=0u)fail(4,"System logs require root");callerIdentity().systemLogs}else logDir
                try {showLogs(tail,"--follow" in opt,directory)} catch(_:Exception) {fail(11,"Cannot read/follow private logs")};0
            }
            else->fail(2,"Unknown command; see --help")
        }
    } } catch(e:CliError) {fputs("Error: ${e.message}\n",stderr);e.code}
        catch(_:Exception) {fputs("Error: operation failed\n",stderr);10}
    exit(code)
}
