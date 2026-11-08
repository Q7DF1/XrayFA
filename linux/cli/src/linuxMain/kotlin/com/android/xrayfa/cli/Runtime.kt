@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

internal data class CallerIdentity(val uid:UInt,val home:String,val sudo:Boolean) {
    val profile get()="${profileRoot(uid,home)}/.local/share/xrayfa"
    val systemLogs get()="/var/log/xrayfa/$uid"
}
internal fun profileRoot(uid:UInt,ownerHome:String):String {
    val explicit=getenv("XRAYFA_HOME")?.toKString()?.takeIf{it.isNotBlank()}?.let(::absolute)
    if(explicit!=null)return explicit
    return if(xf_profile_parent_usable(ownerHome,uid.toUInt())!=0) "$ownerHome/.local/share/xrayfa" else "$ownerHome/Xray/.xrayfa"
}
internal fun callerIdentity():CallerIdentity {
    val effective=geteuid()
    val sudoUid=if(effective==0u)getenv("SUDO_UID")?.toKString() else null
    val uid=if(sudoUid!=null) {
        if(!Regex("[0-9]+").matches(sudoUid))fail(4,"Invalid SUDO_UID")
        sudoUid.toUIntOrNull() ?: fail(4,"Invalid SUDO_UID")
    } else getuid()
    val path=xf_uid_home(uid)?.toKString() ?: fail(4,"Caller UID has no passwd home")
    if(!path.startsWith('/'))fail(4,"Caller passwd home is not absolute")
    return CallerIdentity(uid,path,sudoUid!=null)
}
internal var cleanupDeadlineMillis:Long?=null
internal fun tool(path:String,args:List<String>,timeout:Long=5000,maxLineLength:Int=16384,maxCapturedBytes:Int=65536):ProcessResult {
    val budget=cleanupDeadlineMillis?.let{it-xf_millis()-1000}
    if(budget!=null && budget<=0)fail(9,"Overall recovery deadline reached")
    return coreProcess(path,args,null,if(budget==null)timeout else minOf(timeout,budget),ignoreSignals=true,maxLineLength=maxLineLength,maxCapturedBytes=maxCapturedBytes)
}

private fun executablePath(vararg candidates:String):String? = candidates.firstOrNull { access(it,X_OK)==0 }

private fun defaultCorePath(home:String):String {
    val candidates=listOf("$home/Xray/xray","$home/app/Xray/xray")
    return candidates.firstOrNull { access(it,F_OK)==0 } ?: candidates.first()
}

/** Read-only capability report. Required failures are actionable and return non-zero. */
internal fun doctor(coreOption:String?,asJson:Boolean):Int {
    val caller=callerIdentity()
    val checks=mutableListOf<JsonObject>()
    fun probe(name:String,path:String?,args:List<String>,required:Boolean):JsonObject {
        val result=try { if(path==null) null else tool(path,args) } catch(_:Exception) {null}
        val status=when {
            path==null -> "missing"
            result==null -> "unknown"
            result.timedOut -> "timeout"
            result.exit==0 -> "observed"
            result.output.contains("Permission denied",ignoreCase=true) -> "permission-denied"
            else -> "failed"
        }
        return buildJsonObject {
            put("name",name);put("required",required);put("status",status)
            put("path",path?.let(::JsonPrimitive) ?: JsonNull)
            put("exit",result?.exit?.let(::JsonPrimitive) ?: JsonNull)
            put("reason",if(status=="observed")"Probe completed" else "Probe did not establish the required capability")
            put("output",result?.output ?: "Required executable unavailable or probe failed")
            put("timedOut",result?.timedOut ?: false)
        }
    }
    val cat=executablePath("/bin/cat","/usr/bin/cat")
    val uname=executablePath("/bin/uname","/usr/bin/uname")
    val ip=executablePath("/sbin/ip","/usr/sbin/ip")
    val nft=executablePath("/usr/sbin/nft","/sbin/nft")
    var corePath:String?=null;var coreHash:String?=null;var coreVersion:String?=null
    var coreCheck=buildJsonObject {
        put("name","core");put("required",true);put("status","missing");put("path",JsonNull)
        put("reason","Core path does not exist or is not executable");put("output","")
    }
    try {
        corePath=canonicalCore(coreOption ?: defaultCorePath(caller.home))
        coreHash=hashFile(corePath)
        val version=tool(corePath,listOf("version"))
        val ok=version.exit==0 && !version.timedOut
        coreVersion=if(ok)version.output.trim() else "probe-failed"
        coreCheck=buildJsonObject {
            put("name","core");put("required",true);put("status",if(ok)"observed" else if(version.timedOut)"timeout" else "failed")
            put("path",corePath);put("exit",version.exit);put("reason",if(ok)"Core version probe completed" else "Core version probe failed")
            put("output",version.output);put("timedOut",version.timedOut)
        }
    } catch(_:CliError) {
        coreCheck=buildJsonObject {
            put("name","core");put("required",true);put("status","failed");put("path",corePath?.let(::JsonPrimitive) ?: JsonNull)
            put("reason","Core identity or version probe failed");put("output","Core path, hash, or version could not be verified")
        }
    } catch(_:Exception) {
        coreCheck=buildJsonObject {
            put("name","core");put("required",true);put("status","failed");put("path",corePath?.let(::JsonPrimitive) ?: JsonNull)
            put("reason","Core identity or version probe raised a runtime error");put("output","Core path, hash, or version could not be verified")
        }
    }
    checks.add(coreCheck)
    checks.add(probe("distribution",cat,listOf("/etc/os-release"),true))
    checks.add(probe("capabilities",cat,listOf("/proc/self/status"),true))
    checks.add(probe("kernel",uname,listOf("-a"),true))
    checks.add(probe("links",ip,listOf("address"),true))
    checks.add(probe("routes",ip,listOf("route","show","table","all"),true))
    checks.add(probe("rules",ip,listOf("rule"),true))
    val resolverCheck=try {
        val snapshot=resolverSnapshot()
        buildJsonObject {
            put("name","resolver");put("required",true);put("status","observed")
            put("path",snapshot.getValue("backend"));put("exit",0)
            put("reason","Resolver backend observed and /etc/resolv.conf is readable")
            put("output",snapshot.getValue("backend"))
            put("timedOut",false)
        }
    } catch(e:Exception) {
        buildJsonObject {
            put("name","resolver");put("required",true);put("status",if(e is CliError)"failed" else "unknown")
            put("path",JsonPrimitive(RESOLV_CONF));put("exit",JsonNull)
            put("reason",if(e is CliError)e.message ?: "Resolver probe failed" else "Resolver probe failed")
            put("output","Resolver backend could not be observed without exposing resolver contents")
            put("timedOut",false)
        }
    }
    checks.add(resolverCheck)
    checks.add(probe("nft",nft,listOf("list","ruleset"),true))
    var ipv6Capability=buildJsonObject {
        put("status","unknown");put("policy","block-non-loopback");put("globalDefaultRoute",false)
    }
    val ipv6Check=try {
        val snapshot=ipv6CapabilitySnapshot()
        ipv6Capability=snapshot
        buildJsonObject {
            put("name","ipv6");put("required",false);put("status","observed")
            put("path","ip -6 route");put("exit",0)
            put("reason",if(snapshot.getValue("globalDefaultRoute").jsonPrimitive.boolean)
                "Global IPv6 default route observed" else "No global IPv6 default route observed; runtime remains IPv4-only")
            put("output",json.encodeToString(JsonObject.serializer(),snapshot));put("timedOut",false)
        }
    } catch(e:Exception) {
        buildJsonObject {
            put("name","ipv6");put("required",false);put("status","unknown")
            put("path","ip -6 route");put("exit",JsonNull)
            put("reason",e.message?.takeIf{it.isNotBlank()} ?: "IPv6 capability probe failed")
            put("output","IPv6 capability could not be observed read-only");put("timedOut",false)
        }
    }
    checks.add(ipv6Check)
    var conflictSnapshot=buildJsonObject {
        put("status","unknown");putJsonArray("items"){};put("networkNamespace",networkNamespace())
    }
    val conflicts=try {
        val snapshot=runtimeConflictSnapshot()
        conflictSnapshot=snapshot
        buildJsonObject {
            put("name","conflicts");put("required",true)
            put("status",if(snapshot.getValue("status").jsonPrimitive.content=="clear")"observed" else snapshot.getValue("status").jsonPrimitive.content)
            put("path","network");put("exit",0)
            put("reason",if(snapshot.getValue("status").jsonPrimitive.content=="clear")"No owned or foreign runtime conflicts observed" else "Runtime resources require resolution before run")
            put("output",json.encodeToString(JsonObject.serializer(),snapshot));put("timedOut",false)
        }
    } catch(e:Exception) {
        conflictSnapshot=buildJsonObject {
            put("status","unknown");putJsonArray("items"){};put("networkNamespace",networkNamespace())
        }
        buildJsonObject {
            put("name","conflicts");put("required",true);put("status","unknown");put("path","network");put("exit",JsonNull)
            put("reason",e.message?.takeIf{it.isNotBlank()} ?: "Conflict inventory failed");put("output","Network conflict state could not be observed safely");put("timedOut",false)
        }
    }
    checks.add(conflicts)
    val tun=access("/dev/net/tun",R_OK or W_OK)==0
    val blockers=checks.filter {
        it["required"]?.jsonPrimitive?.boolean==true && it["status"]?.jsonPrimitive?.content!="observed"
    }.map { "${it.getValue("name").jsonPrimitive.content}: ${it.getValue("status").jsonPrimitive.content}" }.toMutableList()
    if(!tun)blockers+="tun: unavailable"
    val ready=blockers.isEmpty()
    val report=buildJsonObject {
        put("schemaVersion",2);put("operation","doctor");put("readOnly",true)
        put("uid",caller.uid.toLong());put("effectiveUid",geteuid().toLong());put("sudo",caller.sudo)
        put("profile",caller.profile);put("systemLogPath",caller.systemLogs);put("recoveryPath","/run/xrayfa/session.json")
        put("corePath",corePath?.let(::JsonPrimitive) ?: JsonNull)
        put("coreSha256",coreHash?.let(::JsonPrimitive) ?: JsonNull)
        put("coreVersion",coreVersion?.let(::JsonPrimitive) ?: JsonNull)
        put("tunDevice",tun);put("readyForRun",ready);put("runtimeCertified",false)
        put("bootId",bootId());put("networkNamespace",networkNamespace())
        put("ipv6",ipv6Capability)
        put("conflicts",conflictSnapshot)
        putJsonArray("blockingReasons"){blockers.forEach(::add)}
        put("checks",JsonArray(checks))
    }
    if(asJson)println(json.encodeToString(JsonObject.serializer(),report)) else {
        println("XrayFA doctor (read-only)")
        println("readyForRun=$ready UID=${caller.uid} effectiveUID=${geteuid()} profile=${caller.profile}")
        println("Core=${corePath ?: "unavailable"} sha256=${coreHash ?: "unavailable"}")
        if(blockers.isNotEmpty())println("Blocking reasons: ${blockers.joinToString(", ")}")
        checks.forEach {println("${it.getValue("name").jsonPrimitive.content}: ${it.getValue("status").jsonPrimitive.content} — ${it.getValue("reason").jsonPrimitive.content}")}
    }
    return if(ready)0 else if(blockers.any { it.contains("permission") })4 else 5
}
