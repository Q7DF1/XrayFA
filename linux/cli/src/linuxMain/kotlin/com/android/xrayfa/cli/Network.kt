@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

// Ubuntu 18.04 commonly exposes /sbin/ip as a symlink to /bin/ip. Mutation
// helpers are opened with O_NOFOLLOW, so prefer a real executable path.
internal val IP:String get() = when {
    access("/usr/sbin/ip",X_OK)==0 -> "/usr/sbin/ip"
    access("/bin/ip",X_OK)==0 -> "/bin/ip"
    else -> "/sbin/ip"
}
internal const val NFT="/usr/sbin/nft"
internal const val SYSTEM_RESOLVE="/usr/bin/systemd-resolve"
// Ubuntu 18.04 ships systemd-resolve; newer systemd releases use resolvectl.
// Keep one selected executable identity so journal validation and recovery
// authorize exactly the helper observed at runtime.
internal val RESOLVE:String get()=when {
    access("/usr/bin/resolvectl",X_OK)==0 -> "/usr/bin/resolvectl"
    access(SYSTEM_RESOLVE,X_OK)==0 -> SYSTEM_RESOLVE
    else -> "/usr/bin/resolvectl"
}
internal const val RESOLV_CONF="/etc/resolv.conf"
private const val JSON_OUTPUT_LIMIT=4*1024*1024
internal fun command(path:String,vararg args:String):String {
    val r=tool(path,args.toList())
    if(r.exit!=0 || r.timedOut)fail(7,"System command failed: $path ${args.take(2).joinToString(" ")} exit=${r.exit} timeout=${r.timedOut} detail=${clean(r.output).take(512)}")
    return r.output.trim()
}
private fun legacyRouteJson(text:String,family:String,allTables:Boolean):JsonArray {
    val typed=setOf("unicast","local","broadcast","throw","blackhole","prohibit","unreachable")
    val rows=text.lineSequence().map{it.trim()}.filter{it.isNotEmpty()}.map{line->
        val t=line.split(Regex("\\s+"));val o=mutableMapOf<String,JsonElement>("family" to JsonPrimitive(family),"raw" to JsonPrimitive(line));var i=0
        if(t.firstOrNull() in typed){o["type"]=JsonPrimitive(t[0]);i=1}
        if(i<t.size && t[i] !in setOf("via","dev","proto","scope","src","metric","table","pref","onlink")){o["dst"]=JsonPrimitive(t[i]);i++}
        while(i<t.size){when(t[i]){
            "via"->if(i+1<t.size){o["gateway"]=JsonPrimitive(t[i+1]);i++}
            "dev"->if(i+1<t.size){o["dev"]=JsonPrimitive(t[i+1]);i++}
            "proto"->if(i+1<t.size){o["protocol"]=JsonPrimitive(t[i+1]);i++}
            "scope"->if(i+1<t.size){o["scope"]=JsonPrimitive(t[i+1]);i++}
            "src","prefsrc"->if(i+1<t.size){o["prefsrc"]=JsonPrimitive(t[i+1]);i++}
            "metric"->if(i+1<t.size){t[i+1].toIntOrNull()?.let{o["metric"]=JsonPrimitive(it)};i++}
            "table"->if(i+1<t.size){o["table"]=JsonPrimitive(t[i+1]);i++}
        };i++}
        if(!o.containsKey("dst"))o["dst"]=JsonPrimitive("default")
        if(allTables && !o.containsKey("table"))o["table"]=JsonPrimitive("main")
        JsonObject(o)
    }.toList()
    return JsonArray(rows)
}
private fun legacyRuleJson(text:String):JsonArray=JsonArray(text.lineSequence().map{it.trim()}.filter{it.isNotEmpty()}.map{line->
    val t=line.split(Regex("\\s+"));buildJsonObject{put("raw",line);t.firstOrNull()?.removeSuffix(":")?.toIntOrNull()?.let{put("priority",it)};t.indexOf("lookup").takeIf{it>=0 && it+1<t.size}?.let{put("table",t[it+1])}}
}.toList())
internal fun ipJson(vararg args:String):JsonArray {
    if(args.firstOrNull()=="route") {
        val all=args.contains("all");val v4=tool(IP,listOf("-4")+args.toList(),maxLineLength=JSON_OUTPUT_LIMIT,maxCapturedBytes=JSON_OUTPUT_LIMIT)
        if(v4.exit!=0 || v4.timedOut)fail(7,"System command failed: $IP ${args.take(2).joinToString(" ")}")
        val v6=tool(IP,listOf("-6")+args.toList(),maxLineLength=JSON_OUTPUT_LIMIT,maxCapturedBytes=JSON_OUTPUT_LIMIT)
        if(v6.exit!=0 || v6.timedOut)fail(7,"System command failed: $IP -6 ${args.take(2).joinToString(" ")}")
        return JsonArray((legacyRouteJson(v4.output,"inet",all)+legacyRouteJson(v6.output,"inet6",all)))
    }
    if(args.firstOrNull()=="rule") {
        val r=tool(IP,args.toList(),maxLineLength=JSON_OUTPUT_LIMIT,maxCapturedBytes=JSON_OUTPUT_LIMIT)
        if(r.exit!=0 || r.timedOut)fail(7,"System command failed: $IP rule")
        return legacyRuleJson(r.output)
    }
    val r=tool(IP,listOf("-j",*args),maxLineLength=JSON_OUTPUT_LIMIT,maxCapturedBytes=JSON_OUTPUT_LIMIT)
    if(r.exit!=0 || r.timedOut)fail(7,"System command failed: $IP ${args.take(2).joinToString(" ")}")
    return json.parseToJsonElement(r.output).jsonArray
}
internal fun networkBaseline():JsonObject=buildJsonObject {
    put("routes",ipJson("route","show","table","all"));put("rules",ipJson("rule"))
    put("resolver",resolverSnapshot());put("links",ipJson("link"));put("addresses",ipJson("address"))
}
/** Read-only IPv6 capability summary; the current runtime policy remains IPv4-only. */
internal fun ipv6CapabilitySnapshot():JsonObject {
    val routes=ipJson("route","show","table","all").map{it.jsonObject}
    val globalDefault=routes.any {
        it["family"]?.jsonPrimitive?.content=="inet6" &&
            it["dst"]?.jsonPrimitive?.content=="default"
    }
    return buildJsonObject {
        put("policy","block-non-loopback")
        put("globalDefaultRoute",globalDefault)
        put("status",if(globalDefault)"global-route-observed" else "link-local-only-or-no-default")
    }
}
/**
 * Cuttlefish exposes its persistent TAP ports as linkinfo.info_kind="tun"
 * with linkinfo.info_data.type="tap". They are not host VPN interfaces and
 * must not block XrayFA's own TUN session. Keep the exception narrow: only
 * the known cvd-* TAP devices are ignored; real TUN, WireGuard and PPP links
 * remain conflicts.
 */
internal fun isForeignVpnLink(link:JsonObject):Boolean {
    val kind=link["linkinfo"]?.jsonObject?.get("info_kind")?.jsonPrimitive?.content ?: return false
    if(kind in setOf("wireguard","ppp"))return true
    if(kind!="tun")return false
    val name=link["ifname"]?.jsonPrimitive?.content ?: return true
    // The active XrayFA session owns this TUN. It is reported separately as
    // xrayfa0/runtime-journal conflict; do not misclassify it as an external
    // VPN link in the read-only doctor inventory.
    if(name=="xrayfa0")return false
    val type=link["linkinfo"]?.jsonObject?.get("info_data")?.jsonObject
        ?.get("type")?.jsonPrimitive?.content
    return !(name.startsWith("cvd-") && type=="tap")
}
/** Read-only conflict inventory shared by doctor and the runtime preflight. */
internal fun runtimeConflictSnapshot():JsonObject {
    val items=mutableListOf<String>()
    var unknown=false
    val journal=access("/run/xrayfa/session.json",F_OK)
    if(journal==0)items+="runtime-journal"
    else if(xf_errno()!=ENOENT)unknown=true
    val links=ipJson("link").map{it.jsonObject}
    if(links.any{it["ifname"]?.jsonPrimitive?.content=="xrayfa0"})items+="xrayfa0"
    if(ipJson("-d","link").any{isForeignVpnLink(it.jsonObject)})items+="foreign-vpn-link"
    val routes=ipJson("route","show","table","all").map{it.jsonObject}
    if(routes.any{it["dst"]?.jsonPrimitive?.content in setOf("0.0.0.0/1","128.0.0.0/1","198.18.0.0/30")})items+="managed-route"
    val addresses=ipJson("address").flatMap{it.jsonObject["addr_info"]?.jsonArray ?: JsonArray(emptyList())}
    if(addresses.any{
            val a=it.jsonObject
            a["family"]?.jsonPrimitive?.content=="inet" &&
                a["local"]?.jsonPrimitive?.content=="198.18.0.1" &&
                a["prefixlen"]?.jsonPrimitive?.int==30
        })items+="managed-address"
    val firewall=tool(NFT,listOf("list","ruleset"),maxLineLength=JSON_OUTPUT_LIMIT,maxCapturedBytes=JSON_OUTPUT_LIMIT)
    if(firewall.exit!=0 || firewall.timedOut)unknown=true
    else if(Regex("\\btable\\s+(?:inet|ip|ip6)\\s+xfa_[A-Za-z0-9_]+\\b").containsMatchIn(firewall.output))items+="xrayfa-firewall-table"
    val status=when {unknown->"unknown";items.isNotEmpty()->"conflict";else->"clear"}
    return buildJsonObject {
        put("status",status)
        putJsonArray("items"){items.distinct().forEach(::add)}
        put("networkNamespace",networkNamespace())
    }
}
internal data class ManagementRoute(val destination:String,val gateway:String?,val device:String)
internal data class NetworkPlan(val underlay:String,val table:String,val baseline:JsonObject,val managementRoutes:List<ManagementRoute>) {
    companion object {
        private fun establishedSshPeers():List<String> {
            val text=readBytes("/proc/net/tcp",256*1024,missing=true)?.decodeToString() ?: return emptyList()
            return text.lineSequence().drop(1).mapNotNull { line ->
                val fields=line.trim().split(Regex("\\s+"))
                if(fields.size<4 || fields[3]!="01")return@mapNotNull null
                val local=fields[1].split(':');val remote=fields[2].split(':')
                if(local.size!=2 || remote.size!=2 || local[1].toIntOrNull(16)!=22)return@mapNotNull null
                val hex=remote[0]
                if(hex.length!=8 || hex=="00000000")return@mapNotNull null
                try { hex.chunked(2).reversed().joinToString("."){it.toInt(16).toString()} } catch(_:Exception){null}
            }.distinct().filter{it!="127.0.0.1"}.toList()
        }
        fun inspect(session:String):NetworkPlan {
            for(path in listOf(IP,NFT,"/usr/bin/getent"))if(access(path,X_OK)!=0)fail(5,"Missing required system tool: $path")
            if(access("/dev/net/tun",R_OK or W_OK)!=0)fail(5,"TUN device is unavailable")
            val caps=readBytes("/proc/self/status",65536)!!.decodeToString().lineSequence().firstOrNull{it.startsWith("CapEff:")}
                ?.substringAfter(':')?.trim()?.toULongOrNull(16) ?: fail(5,"Cannot inspect capabilities")
            if(caps and (1uL shl 12)==0uL)fail(5,"CAP_NET_ADMIN is required")
            val baseline=networkBaseline()
            val backend=baseline.getValue("resolver").jsonObject.getValue("backend").jsonPrimitive.content
            if(backend !in setOf("systemd-resolved","static-resolv.conf"))fail(5,"Unsupported resolver backend: $backend")
            val links=baseline.getValue("links").jsonArray.map{it.jsonObject}
            if(links.any{it["ifname"]?.jsonPrimitive?.content=="xrayfa0"})fail(8,"TUN name xrayfa0 already exists")
            if(links.any{it["link_type"]?.jsonPrimitive?.content in setOf("wireguard","ppp","tun")})fail(5,"Other VPN interfaces require explicit conflict resolution")
            // `ip -j link` omits the kernel kind for persistent TUN devices;
            // inspect the detailed linkinfo as well so a pre-existing VPN is
            // rejected before lease/network side effects.
            val detailedLinks=ipJson("-d","link").map{it.jsonObject}
            if(detailedLinks.any(::isForeignVpnLink))fail(5,"Other VPN interfaces require explicit conflict resolution")
            val routes=baseline.getValue("routes").jsonArray.map{it.jsonObject}
            if(routes.any{it["dst"]?.jsonPrimitive?.content in setOf("0.0.0.0/1","128.0.0.0/1","198.18.0.0/30")})fail(8,"Managed TUN address/route conflicts with existing network")
            val addresses=baseline.getValue("addresses").jsonArray.flatMap{it.jsonObject["addr_info"]?.jsonArray ?: JsonArray(emptyList())}
            if(addresses.any{
                    val a=it.jsonObject
                    a["family"]?.jsonPrimitive?.content=="inet" &&
                        a["local"]?.jsonPrimitive?.content=="198.18.0.1" &&
                        a["prefixlen"]?.jsonPrimitive?.int==30
                })fail(8,"Managed TUN address conflicts with existing interface")
            val defaults=routes.filter{
                it["family"]?.jsonPrimitive?.content=="inet" &&
                    it["dst"]?.jsonPrimitive?.content=="default" &&
                    (it["table"]?.jsonPrimitive?.content ?: "main")=="main"
            }
            if(defaults.size!=1)fail(5,"Exactly one IPv4 underlay default is required")
            val underlay=defaults.single().getValue("dev").jsonPrimitive.content
            val gateway=defaults.single()["gateway"]?.jsonPrimitive?.content
            if(!Regex("[a-zA-Z0-9_.-]{1,15}").matches(underlay))fail(5,"Unsupported underlay interface name")
            val rules=baseline.getValue("rules").jsonArray
            if(rules.size!=3)fail(5,"Nonstandard policy routing is unsupported")
            command(NFT,"list","ruleset")
            val management=establishedSshPeers().filter{peer->
                routes.none{route->route["dst"]?.jsonPrimitive?.content in setOf(peer,"$peer/32")}
            }.map{ManagementRoute(it,gateway,underlay)}
            return NetworkPlan(underlay,"xfa_"+session.replace('-','_'),baseline,management)
        }
    }
}
/** Resolve node domains once before resolver takeover; preserve TLS/REALITY names. */
internal fun bootstrapOutbound(outbound:JsonObject,log:LogSession):JsonObject {
    val protocol=outbound.getValue("protocol").jsonPrimitive.content
    val settings=outbound.getValue("settings").jsonObject
    val collection=when(protocol){"vless","vmess"->"vnext";"hysteria"->null;else->"servers"}
    val endpoint=if(collection==null)settings else settings.getValue(collection).jsonArray.single().jsonObject
    val address=endpoint.getValue("address").jsonPrimitive.content
    fun ipv4(value:String)=value.split('.').let{parts->parts.size==4 && parts.all{it.toIntOrNull() in 0..255 && it.isNotEmpty()}}
    if(ipv4(address))return outbound
    if(':' in address)fail(5,"IPv4 runtime requires an IPv4-reachable proxy endpoint")
    val result=tool("/usr/bin/getent",listOf("ahostsv4",address))
    val resolved=result.output.lineSequence().map{it.substringBefore(' ')}.firstOrNull{ipv4(it)}
    if(result.exit!=0 || result.timedOut || resolved==null)fail(5,"Cannot bootstrap node hostname before DNS takeover")
    val pinned=JsonObject(endpoint.toMutableMap().apply{put("address",JsonPrimitive(resolved))})
    val newSettings=if(collection==null)pinned else JsonObject(settings.toMutableMap().apply{put(collection,JsonArray(listOf(pinned)))})
    log.event("bootstrap","Resolved node $address to $resolved before network takeover; TLS/REALITY verification name preserved",force=true)
    return JsonObject(outbound.toMutableMap().apply{put("settings",newSettings)})
}
internal fun runtimeFinalConfig(outbound:JsonObject,level:String,plan:NetworkPlan):JsonObject {
    val adapted=JsonObject(outbound.toMutableMap().apply {
        outbound["streamSettings"]?.jsonObject?.let{stream->
            if(stream["security"]?.jsonPrimitive?.content=="tls")put("streamSettings",JsonObject(stream.toMutableMap().apply{
                put("tlsSettings",JsonObject((stream["tlsSettings"]?.jsonObject ?: JsonObject(emptyMap())).toMutableMap().apply{put("useSystemCA",JsonPrimitive(true))}))
            }))
        }
    })
    val base=runtimeConfig(adapted,level).toMutableMap()
    val inbound=base.getValue("inbounds").jsonArray.single().jsonObject.toMutableMap()
    inbound["settings"]=JsonObject(inbound.getValue("settings").jsonObject.toMutableMap().apply{put("autoOutboundsInterface",JsonPrimitive(plan.underlay))})
    base["inbounds"]=JsonArray(listOf(JsonObject(inbound)))
    // TCP upstream works for all baseline protocols, including HTTP/SOCKS.
    base["dns"]=buildJsonObject {putJsonArray("servers"){add("tcp://1.1.1.1")};put("queryStrategy","UseIPv4")}
    return JsonObject(base)
}
internal fun nftState(table:String):JsonObject? {
    val r=tool(NFT,listOf("-j","list","table","inet",table))
    if(r.exit!=0) {
        val legacy=tool(NFT,listOf("list","table","inet",table))
        if(legacy.exit!=0){if(!legacy.timedOut && ("No such file or directory" in legacy.output || "does not exist" in legacy.output))return null;fail(7,"Cannot inspect nft state")}
        val text=legacy.output.replace(Regex("packets [0-9]+ bytes [0-9]+"),"counter").trim()
        return buildJsonObject {put("legacyText",text)}
    }
    fun strip(v:JsonElement):JsonElement=when(v){
        is JsonObject->JsonObject(v.filterKeys{it !in setOf("packets","bytes","metainfo")}.mapValues{strip(it.value)})
        is JsonArray->JsonArray(v.filterNot{it is JsonObject && "metainfo" in it}.map{strip(it)})
        else->v
    }
    return try {strip(json.parseToJsonElement(r.output)).jsonObject} catch(_:Exception) {
        val legacy=tool(NFT,listOf("list","table","inet",table))
        if(legacy.exit!=0){if(!legacy.timedOut && ("No such file or directory" in legacy.output || "does not exist" in legacy.output))return null;fail(7,"Cannot inspect nft state")}
        buildJsonObject {put("legacyText",legacy.output.replace(Regex("packets [0-9]+ bytes [0-9]+"),"counter").trim())}
    }
}
/** Handles are only unknown across an intent/commit crash; all semantic fields stay exact. */
internal fun nftSemantics(v:JsonElement):JsonElement=when(v){
    is JsonObject->JsonObject(v.filterKeys{it!="handle"}.mapValues{nftSemantics(it.value)})
    is JsonArray->JsonArray(v.map{nftSemantics(it)})
    else->v
}
internal fun applyIpv6(plan:NetworkPlan,lease:NetworkLease) {
    if(nftState(plan.table)!=null)fail(8,"Session nft table already exists")
    val token=lease.record.getValue("ownershipToken").jsonPrimitive.content
    if(!Regex("[0-9a-f]{32}").matches(token))fail(10,"Invalid lease ownership token")
    val intent=json.parseToJsonElement("""{"nftables":[
      {"table":{"family":"inet","name":"${plan.table}"}},
      {"chain":{"family":"inet","table":"${plan.table}","name":"output","type":"filter","hook":"output","prio":-50,"policy":"accept"}},
      {"rule":{"family":"inet","table":"${plan.table}","chain":"output","expr":[{"match":{"op":"!=","left":{"payload":{"protocol":"ip6","field":"daddr"}},"right":"::1"}},{"counter":{}},{"drop":null}],"comment":"xrayfa:$token"}}
    ]}""").jsonObject
    val policyPath=lease.record.getValue("configPath").jsonPrimitive.content.substringBeforeLast('/')+"/ipv6.nft"
    newFile(policyPath,"create table inet ${plan.table}\n"+
        "add chain inet ${plan.table} output { type filter hook output priority -50; policy accept; }\n"+
        "add rule inet ${plan.table} output ip6 daddr != ::1 counter drop comment \"xrayfa:$token\"\n")
    if(chmod(policyPath,256u)!=0)fail(10,"Cannot freeze nft policy")
    // nft commits this entire batch atomically. Durable intent includes exact semantic
    // ownership evidence before any operation, including a crash before after-image save.
    lease.update("phase" to JsonPrimitive("ipv6-intent"),"nftTable" to JsonPrimitive(plan.table),"nftIntent" to intent)
    lease.mutate(NFT,"-f",policyPath)
    val actual=nftState(plan.table) ?: fail(7,"Cannot inspect committed IPv6 policy")
    if(actual["legacyText"]!=null) {
        if(token !in actual.getValue("legacyText").jsonPrimitive.content)fail(7,"IPv6 policy ownership token is missing")
    } else if(nftSemantics(actual)!=intent)fail(7,"IPv6 policy does not match durable intent")
    lease.update("nftAfter" to actual,"phase" to JsonPrimitive("ipv6-applied"))
}
internal fun tunIdentity():JsonObject? {
    val r=tool(IP,listOf("-j","address","show"))
    if(r.exit!=0){if(!r.timedOut && "does not exist" in r.output)return null;fail(7,"Cannot inspect TUN state")}
    val link=json.parseToJsonElement(r.output).jsonArray.map{it.jsonObject}.firstOrNull{it["ifname"]?.jsonPrimitive?.content=="xrayfa0"} ?: return null
    return buildJsonObject {
        put("ifindex",link.getValue("ifindex"));put("ifname",link.getValue("ifname"));put("mtu",link.getValue("mtu"))
        put("addr_info",link.getValue("addr_info"))
    }
}
internal fun tunLinkIdentity():JsonObject? {
    val r=tool(IP,listOf("-j","link","show"))
    if(r.exit!=0){if(!r.timedOut && "does not exist" in r.output)return null;fail(7,"Cannot inspect TUN link")}
    return json.parseToJsonElement(r.output).jsonArray.map{it.jsonObject}.firstOrNull{it["ifname"]?.jsonPrimitive?.content=="xrayfa0"}
}
/** Ubuntu 18.04's bundled core can create the TUN but may not install its
 * address/routes. Complete only the exact managed image after the core owns the
 * interface; all commands are target-side and the before image is journaled. */
internal fun ensureTunNetwork(lease:NetworkLease,log:LogSession) {
    if(tunLinkIdentity()==null)fail(7,"Core did not create xrayfa0")
    lease.update("tunIntent" to buildJsonObject {
        put("interface","xrayfa0");put("address","198.18.0.1/30")
        putJsonArray("routes"){add("0.0.0.0/1");add("128.0.0.0/1")}
    },"phase" to JsonPrimitive("tun-intent"))
    val current=tunIdentity()
    val hasAddress=current?.get("addr_info")?.jsonArray?.any{val a=it.jsonObject;a["local"]?.jsonPrimitive?.content=="198.18.0.1" && a["prefixlen"]?.jsonPrimitive?.int==30}==true
    if(!hasAddress)command(IP,"address","add","198.18.0.1/30","dev","xrayfa0")
    val routes=ipJson("route","show").map{it.jsonObject}
    for(dst in listOf("0.0.0.0/1","128.0.0.0/1")) if(routes.none{it["dst"]?.jsonPrimitive?.content==dst && it["dev"]?.jsonPrimitive?.content=="xrayfa0"}) command(IP,"route","add",dst,"dev","xrayfa0")
    val after=tunIdentity() ?: fail(7,"TUN disappeared while applying address/routes")
    if(after.get("mtu")?.jsonPrimitive?.int!=1500)fail(7,"Unexpected TUN MTU")
    lease.update("tunAfter" to after,"phase" to JsonPrimitive("tun-ready"))
    log.event("route","Managed xrayfa0 address/routes observed; auto-route fallback was applied only where absent",force=true)
}
/** Keep already-established SSH management peers on the physical underlay while
 * the legacy core fallback installs split routes in the main table. The routes
 * are journaled and removed only when their exact owned image is still present. */
internal fun ensureManagementRoutes(plan:NetworkPlan,lease:NetworkLease,log:LogSession) {
    if(plan.managementRoutes.isEmpty())return
    val intent=JsonArray(plan.managementRoutes.map{route->buildJsonObject {
        put("destination",route.destination);route.gateway?.let{put("gateway",it)};put("device",route.device);put("owned",true)
    }})
    lease.update("managementRouteIntent" to intent,"phase" to JsonPrimitive("management-route-intent"))
    for(route in plan.managementRoutes) {
        val args=mutableListOf("route","add","${route.destination}/32")
        route.gateway?.let{args+=listOf("via",it)}
        args+=listOf("dev",route.device)
        lease.mutate(IP,*args.toTypedArray())
    }
    lease.update("managementRoutesAfter" to JsonArray(plan.managementRoutes.map{route->buildJsonObject {
        put("destination",route.destination);route.gateway?.let{put("gateway",it)};put("device",route.device);put("owned",true)
    }}),"phase" to JsonPrimitive("management-routes-applied"))
    log.event("route","Preserved ${plan.managementRoutes.size} established SSH management route(s) on ${plan.underlay}",force=true)
}
