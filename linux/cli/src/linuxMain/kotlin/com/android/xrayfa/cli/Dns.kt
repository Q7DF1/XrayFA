@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.common.utils.calculateBytesHash
import com.android.xrayfa.cli.host.*
import kotlinx.serialization.json.*
import kotlinx.cinterop.*
import platform.posix.*

private const val STATIC_BACKEND="static-resolv.conf"

private fun resolvedAvailable():Boolean {
    if(access(RESOLVE,X_OK)!=0)return false
    if(RESOLVE==SYSTEM_RESOLVE) {
        if(access("/run/dbus/system_bus_socket",F_OK)!=0 || access("/run/systemd/resolve/stub-resolv.conf",R_OK)!=0)return false
        return try{tool(SYSTEM_RESOLVE,listOf("--status"),timeout=2000).exit==0}catch(_:Exception){false}
    }
    return access("/run/systemd/resolve/io.systemd.Resolve",F_OK)==0 || access("/run/dbus/system_bus_socket",F_OK)==0
}

private fun staticResolverSnapshot():JsonObject {
    if(linkTarget(RESOLV_CONF)!=null)fail(5,"Static resolver requires a regular /etc/resolv.conf")
    val bytes=readBytes(RESOLV_CONF) ?: fail(5,"Cannot read /etc/resolv.conf")
    val text=bytes.decodeToString()
    val nameservers=text.lineSequence().map{it.trim()}.filter{it.startsWith("nameserver ")}.map{it.substringAfter(" ").trim()}.filter{it.isNotEmpty()}.toList()
    if(nameservers.isEmpty())fail(5,"/etc/resolv.conf has no nameserver")
    val search=text.lineSequence().map{it.trim()}.firstOrNull{it.startsWith("search ") || it.startsWith("domain ")}.orEmpty()
    return buildJsonObject {
        put("backend",STATIC_BACKEND);put("path",RESOLV_CONF);put("mode",xf_mode(RESOLV_CONF).toInt())
        put("text",text);put("sha256",calculateBytesHash(bytes));put("nameservers",JsonArray(nameservers.map(::JsonPrimitive)));put("search",search)
    }
}

/** Typed resolver values alongside raw evidence for systemd-resolved. */
internal fun resolverStructured(snapshot:JsonObject):JsonObject {
    if(snapshot["backend"]?.jsonPrimitive?.content==STATIC_BACKEND)return snapshot
    snapshot["structured"]?.jsonObject?.let{return it}
    val links=mutableMapOf<String,MutableMap<String,JsonElement>>();val global=mutableMapOf<String,JsonElement>()
    val line=Regex("Link ([0-9]+) \\(([^)]+)\\): *(.*)");var mode="unknown"
    for((kind,key) in listOf("dns" to "dns","domain" to "domains","default-route" to "defaultRoute")) for(raw in snapshot.getValue(kind).jsonPrimitive.content.lines()) {
        val text=raw.trim()
        if(text.startsWith("resolv.conf mode:")){mode=text.substringAfter(':').trim();continue}
        if(text.startsWith("Global:") && kind!="default-route"){global[key]=JsonArray(text.substringAfter(':').trim().split(Regex("\\s+")).filter{it.isNotEmpty()}.map(::JsonPrimitive));continue}
        val match=line.matchEntire(text) ?: continue;val index=match.groupValues[1];val name=match.groupValues[2];val value=match.groupValues[3]
        val link=links.getOrPut(index){mutableMapOf("ifindex" to JsonPrimitive(index.toInt()),"name" to JsonPrimitive(name))}
        if(link["name"]!=JsonPrimitive(name))fail(5,"Resolver link identity changed during observation")
        link[key]=if(kind=="default-route"){if(value !in setOf("yes","no"))fail(5,"Unsupported resolved DefaultRoute value");JsonPrimitive(value=="yes")} else JsonArray(value.split(Regex("\\s+")).filter{it.isNotEmpty()}.map(::JsonPrimitive))
    }
    for(link in links.values)if(listOf("dns","domains","defaultRoute").any{it !in link})fail(5,"Incomplete per-link resolver observation")
    return buildJsonObject {put("global",JsonObject(global));put("links",JsonObject(links.mapValues{JsonObject(it.value)}));putJsonObject("owner"){put("resolvConfTarget",linkTarget(RESOLV_CONF) ?: "regular-or-unreadable");put("mode",mode)}}
}

/** Parse Ubuntu 18.04 systemd-resolve --status into the common resolver shape. */
private fun legacyResolverStructured(text:String):JsonObject {
    val links=mutableMapOf<String,MutableMap<String,JsonElement>>()
    val globalDns=mutableListOf<JsonPrimitive>();val globalDomains=mutableListOf<JsonPrimitive>()
    var current:MutableMap<String,JsonElement>?=null;var pending:String?=null
    fun addValues(target:MutableList<JsonPrimitive>,value:String) {
        value.split(Regex("\\s+")).filter{it.contains('.') || it.contains(':')}.forEach{target+=JsonPrimitive(it)}
    }
    fun addCurrent(key:String,value:String) {
        val target=current ?: return
        val values=value.split(Regex("\\s+")).filter{it.isNotEmpty()}
        val old=target[key]?.jsonArray?.toMutableList() ?: mutableListOf()
        target[key]=JsonArray(old+values.map(::JsonPrimitive))
    }
    for(raw in text.lineSequence()) {
        val line=raw.trim()
        if(line=="Global"){current=null;pending="global";continue}
        val match=Regex("Link ([0-9]+) \\(([^)]+)\\)").matchEntire(line)
        if(match!=null){
            val index=match.groupValues[1];current=links.getOrPut(index){mutableMapOf("ifindex" to JsonPrimitive(index.toInt()),"name" to JsonPrimitive(match.groupValues[2]))};pending=null;continue
        }
        when {
            line.startsWith("DNS Servers:") -> {
                pending="dns";val value=line.substringAfter(':').trim()
                if(current==null)addValues(globalDns,value) else addCurrent("dns",value)
            }
            line.startsWith("DNS Domain:") -> {
                pending="domains";val value=line.substringAfter(':').trim()
                if(current==null)addValues(globalDomains,value) else addCurrent("domains",value)
            }
            line.startsWith("Protocols:") -> {
                current?.set("defaultRoute",JsonPrimitive("+DefaultRoute" in line));pending=null
            }
            line.startsWith("DNSSEC NTA:") || line.startsWith("Current Scopes:") ||
                line.startsWith("LLMNR setting:") || line.startsWith("MulticastDNS setting:") ||
                line.startsWith("DNSSEC setting:") || line.startsWith("DNSSEC supported:") -> pending=null
            line.isEmpty() -> pending=null
            pending=="dns" && current!=null -> addCurrent("dns",line)
            pending=="domains" && current!=null -> addCurrent("domains",line)
            pending=="global" && line.startsWith("DNS Servers:") -> addValues(globalDns,line.substringAfter(':').trim())
        }
    }
    links.values.forEach{
        if(!it.containsKey("dns"))it["dns"]=JsonArray(emptyList())
        if(!it.containsKey("domains"))it["domains"]=JsonArray(emptyList())
        if(!it.containsKey("defaultRoute"))it["defaultRoute"]=JsonPrimitive(false)
    }
    return buildJsonObject {
        put("global",buildJsonObject{put("dns",JsonArray(globalDns.distinct()));put("domains",JsonArray(globalDomains.distinct()))})
        put("links",JsonObject(links.mapValues{JsonObject(it.value)}))
        putJsonObject("owner"){put("resolvConfTarget",linkTarget(RESOLV_CONF) ?: "regular-or-unreadable");put("mode",xf_mode(RESOLV_CONF).toInt())}
    }
}

internal fun resolverSnapshot():JsonObject {
    if(!resolvedAvailable())return staticResolverSnapshot()
    if(RESOLVE==SYSTEM_RESOLVE) {
        val status=command(SYSTEM_RESOLVE,"--status")
        return buildJsonObject {
            put("backend","systemd-resolved")
            put("structured",legacyResolverStructured(status))
        }
    }
    var last:CliError?=null
    repeat(5){try{val raw=buildJsonObject{for(kind in listOf("dns","domain","default-route"))put(kind,command(RESOLVE,kind))};return JsonObject(raw.toMutableMap().apply{put("backend",JsonPrimitive("systemd-resolved"));put("structured",resolverStructured(raw))})}catch(e:CliError){if(e.code!=5)throw e;last=e;usleep(10000u)}}
    throw last ?: CliError(5,"Resolver observation failed")
}

private fun staticResolverText(before:String):String {
    val lines=before.lineSequence().filterNot{val t=it.trim();t.startsWith("nameserver ") || t.startsWith("search ") || t.startsWith("domain ")}.toMutableList()
    lines.add(0,"nameserver 198.18.0.2");return lines.joinToString("\n").trimEnd()+"\n"
}
private fun replaceStaticResolver(lease:NetworkLease,text:String,mode:Int) {
    if(linkTarget(RESOLV_CONF)!=null)fail(9,"/etc/resolv.conf changed to a symlink; refusing replacement")
    // /etc/resolv.conf may be on a different filesystem from /run. Keep the
    // temporary file beside the target so rename(2) remains atomic on 18.04.
    val path="/etc/.xrayfa-resolv-${getpid()}-${xf_millis()}"
    newFile(path,text);if(rename(path,RESOLV_CONF)!=0){unlink(path);fail(7,"Cannot atomically replace /etc/resolv.conf")};if(chmod(RESOLV_CONF,mode.toUInt())!=0)fail(7,"Cannot restore /etc/resolv.conf mode")
}

internal fun resolverWithoutLink(snapshot:JsonObject,index:String):JsonObject {
    val structured=resolverStructured(snapshot);if(structured["backend"]?.jsonPrimitive?.content==STATIC_BACKEND)return structured
    return JsonObject(structured.toMutableMap().apply{put("links",JsonObject(structured.getValue("links").jsonObject.filterKeys{it!=index}))})
}
internal fun setSessionDns(lease:NetworkLease,kind:String,value:String="") {
    if(kind !in setOf("dns","domain","default-route","revert"))fail(10,"Unknown resolver mutation")
    val before=resolverSnapshot();lease.update("resolverStepBefore" to before,"resolverStepIntent" to buildJsonObject{put("kind",kind);put("value",value)})
    if(before["backend"]?.jsonPrimitive?.content==STATIC_BACKEND){
        val baseline=lease.record.getValue("baseline").jsonObject.getValue("resolver").jsonObject
        val target=if(kind=="revert")baseline.getValue("text").jsonPrimitive.content else {if(kind!="dns" || value!="198.18.0.2")fail(5,"Static resolver supports only virtual DNS assignment");staticResolverText(before.getValue("text").jsonPrimitive.content)}
        replaceStaticResolver(lease,target,if(kind=="revert")baseline.getValue("mode").jsonPrimitive.int else before.getValue("mode").jsonPrimitive.int)
    } else {
        val tun=lease.record.getValue("tunAfter").jsonObject
        val index=tun.getValue("ifindex").jsonPrimitive.content
        if(RESOLVE==SYSTEM_RESOLVE) {
            val name=tun.getValue("ifname").jsonPrimitive.content
            when(kind) {
                "dns"->lease.mutate(RESOLVE,"--set-dns=$value","--interface=$name")
                "domain"->lease.mutate(RESOLVE,"--set-domain=$value","--interface=$name")
                // systemd-resolve 237 has no --set-default-route; ~. is the
                // route-only domain used above and expresses this policy.
                "default-route"->{ }
                "revert"->lease.mutate(RESOLVE,"--revert","--interface=$name")
            }
        } else if(kind=="revert")lease.mutate(RESOLVE,kind,index) else lease.mutate(RESOLVE,kind,index,value)
    }
    val after=resolverSnapshot();if(kind=="revert")lease.update("resolverCleanupAfter" to after,"resolverRestored" to JsonPrimitive(true)) else lease.update("resolverAfter" to after)
}
internal fun resolverMayRevert(record:JsonObject,current:JsonObject,index:String):Boolean {
    record["resolverAfter"]?.jsonObject?.let{if(resolverStructured(current)==resolverStructured(it))return true}
    val before=record["resolverStepBefore"]?.jsonObject ?: return false;val intent=record["resolverStepIntent"]?.jsonObject ?: return false
    if(before["backend"]?.jsonPrimitive?.content==STATIC_BACKEND)return false
    val old=resolverStructured(before);val now=resolverStructured(current);if(resolverWithoutLink(before,index)!=resolverWithoutLink(current,index))return false
    val a=old.getValue("links").jsonObject[index]?.jsonObject ?: return false;val b=now.getValue("links").jsonObject[index]?.jsonObject ?: return false;val expected=a.toMutableMap();val value=intent.getValue("value").jsonPrimitive.content
    when(intent.getValue("kind").jsonPrimitive.content){"dns"->{if(value!="198.18.0.2")return false;expected["dns"]=JsonArray(listOf(JsonPrimitive(value)))};"domain"->{if(value!="~.")return false;expected["domains"]=JsonArray(listOf(JsonPrimitive(value)));expected["defaultRoute"]=b.getValue("defaultRoute")};"default-route"->{if(value!="true")return false;expected["defaultRoute"]=JsonPrimitive(true)};"revert"->{expected["dns"]=JsonArray(emptyList());expected["domains"]=JsonArray(emptyList());expected["defaultRoute"]=b.getValue("defaultRoute")};else->return false}
    return JsonObject(expected)==b
}
internal fun resolverAlreadyReleased(record:JsonObject,current:JsonObject,index:String):Boolean {
    val baseline=record["baseline"]?.jsonObject?.get("resolver")?.jsonObject ?: return false
    if(baseline["backend"]?.jsonPrimitive?.content==STATIC_BACKEND)return resolverStructured(current)==baseline
    if(resolverWithoutLink(current,index)!=resolverStructured(baseline))return false
    val link=resolverStructured(current).getValue("links").jsonObject[index]?.jsonObject ?: return true
    return link.getValue("dns").jsonArray.isEmpty() && link.getValue("domains").jsonArray.isEmpty()
}
