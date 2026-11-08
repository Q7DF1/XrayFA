@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import com.android.xrayfa.common.utils.calculateBytesHash
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

private class RuntimeStop:Exception()

internal fun runNode(id:Int,coreOption:String?,level:String):Int {
    if(geteuid()!=0u)fail(4,"TUN run requires root; invoke explicitly with sudo")
    val caller=callerIdentity()
    try{privateDir("/var/log/xrayfa")}catch(_:Exception){fail(11,"Cannot initialize system log directory")}
    val log=LogSession(id,level,caller.systemLogs,caller.uid,nonBlockingTerminal=true)
    var lease:NetworkLease?=null;var core:RunningCore?=null;var coreFd=-1
    var code=7;var primaryAt:Long?=null
    var phase="prepare"
    log.event("prepare","START runtime uid=${caller.uid} profile=${caller.profile}",force=true)
    try {
        val node=nodeSnapshot(caller,id)
        val link=node.getValue("url").jsonPrimitive.content
        val parser=NodeParser()
        val outbound=try{parser.outbound(link)}catch(_:Exception){fail(6,"Stored node validation failed")}
        log.secrets=secretValues(outbound)+link
        val protocol=outbound.getValue("protocol").jsonPrimitive.content
        val storedProtocol=node.getValue("protocol").jsonPrimitive.content
        val expected=mapOf("ss" to "shadowsocks","hy2" to "hysteria","hysteria2" to "hysteria","socks5" to "socks")[storedProtocol] ?: storedProtocol
        if(protocol!=expected)fail(6,"Stored protocol does not match parsed outbound")
        val settings=outbound.getValue("settings").jsonObject
        val endpoint=when(protocol){"vless","vmess"->settings.getValue("vnext").jsonArray.single().jsonObject;"hysteria"->settings;else->settings.getValue("servers").jsonArray.single().jsonObject}
        if(endpoint["address"]!=node["address"] || endpoint["port"]!=node["port"])fail(6,"Stored endpoint does not match parsed outbound")
        log.event("prepare","Selected ${summary(node)}",force=true)
        val corePath=canonicalCore(coreOption ?: "${caller.home}/app/Xray/xray")
        coreFd=open(corePath,O_RDONLY or O_NOFOLLOW or O_CLOEXEC)
        if(coreFd<0)fail(5,"Cannot open core")
        if(xf_runtime_core(coreFd,caller.uid,if(caller.sudo)1 else 0)==0)
            fail(4,"Runtime core must be root-owned, or caller-owned when invoked via sudo, and not group/other writable")
        if(access(corePath,X_OK)!=0)fail(4,"Core is not executable")
        val pinned="/proc/${getpid()}/fd/$coreFd"
        val coreHash=hashFile(pinned,true)
        phase="core-version"
        val version=coreProcess(pinned,listOf("version"),log,5000)
        if(version.exit!=0 || version.timedOut || version.interrupted)fail(5,"Core version probe failed")
        log.event("prepare","Core path=$corePath sha256=$coreHash version=${version.output.trim()}",force=true)
        phase="network-preflight"
        val plan=NetworkPlan.inspect(log.session)
        phase="config-generation"
        val config=runtimeFinalConfig(bootstrapOutbound(outbound,log),level,plan)
        privateDir("/run/xrayfa");privateDir("/run/xrayfa/artifacts")
        val dir="/run/xrayfa/artifacts/${log.session}"
        privateDir(dir)
        val path="$dir/final.json"
        val text=json.encodeToString(JsonObject.serializer(),config)+"\n"
        newFile(path,text)
        if(chmod(path,256u)!=0)fail(10,"Cannot freeze final config permissions")
        val configHash=calculateBytesHash(text.encodeToByteArray())
        log.event("validate","Final JSON=$path sha256=$configHash",force=true)
        val test=coreProcess(pinned,listOf("run","-test","-c",path),log)
        if(test.timedOut || test.interrupted)fail(7,"Final configuration validation interrupted or timed out")
        if(test.exit!=0)fail(6,"Final configuration validation failed")
        if(calculateBytesHash(readBytes(path)!!)!=configHash)fail(6,"Final JSON changed during validation")
        if(hashFile(pinned,true)!=coreHash)fail(5,"Core changed during validation")
        if(!log.flushBoundary())fail(11,"Runtime log persistence failed before network changes")
        phase="lease-and-network-setup"
        lease=NetworkLease.acquire(caller,id,configHash,coreHash)
        lease.update("baseline" to plan.baseline,"configPath" to JsonPrimitive(path),"corePath" to JsonPrimitive(corePath),
            "coreDevice" to JsonPrimitive(xf_fd_device(coreFd).toString()),"coreInode" to JsonPrimitive(xf_fd_inode(coreFd).toString()),
            "underlay" to JsonPrimitive(plan.underlay))
        if(normalizedNetwork(networkBaseline())!=normalizedNetwork(plan.baseline))fail(8,"Network changed during validation; retry after resolving the conflict")
        ensureManagementRoutes(plan,lease,log)
        applyIpv6(plan,lease)
        lease.update("phase" to JsonPrimitive("core-intent"))
        phase="core-start"
        core=RunningCore.gated(coreFd,corePath,listOf("run","-c",path),log)
        lease.update("corePid" to JsonPrimitive(core.pid),"coreStart" to JsonPrimitive(procStart(core.pid) ?: fail(7,"Cannot identify gated core")),"phase" to JsonPrimitive("core-gated"))
        core.release()
        phase="tun-readiness"
        val deadline=xf_millis()+10000
        var tun:JsonObject?=null
        var tunConfigured=false
        while(xf_millis()<deadline && xf_interrupted()==0) {
            core.tick();if(core.exitCode!=null)fail(7,"Core exited before readiness; core_exit=${core.exitCode}")
            if(!tunConfigured && tunLinkIdentity()!=null){ensureTunNetwork(lease,log);tunConfigured=true}
            tun=tunIdentity()
            val addresses=tun?.get("addr_info")?.jsonArray
            val addressed=addresses?.any{val a=it.jsonObject;a["local"]?.jsonPrimitive?.content=="198.18.0.1" && a["prefixlen"]?.jsonPrimitive?.int==30}==true
            if(tun?.get("mtu")?.jsonPrimitive?.int==1500 && addressed){
                val installed=ipJson("route","show").map{it.jsonObject}
                if(listOf("0.0.0.0/1","128.0.0.0/1").all{dst->installed.any{it["dst"]?.jsonPrimitive?.content==dst && it["dev"]?.jsonPrimitive?.content=="xrayfa0"}})break
            }
            tun=null
            usleep(20000u)
        }
        if(xf_interrupted()!=0)throw RuntimeStop()
        if(tun==null)fail(7,"TUN readiness deadline exceeded")
        if(tun.getValue("mtu").jsonPrimitive.int!=1500 || tun.getValue("addr_info").jsonArray.none{
                val a=it.jsonObject;a["local"]?.jsonPrimitive?.content=="198.18.0.1" && a["prefixlen"]?.jsonPrimitive?.int==30})fail(7,"Unexpected TUN parameters")
        lease.update("tunAfter" to tun,"phase" to JsonPrimitive("tun-ready"))
        val routes=ipJson("route","show").map{it.jsonObject}
        if(listOf("0.0.0.0/1","128.0.0.0/1").any{dst->routes.none{it["dst"]?.jsonPrimitive?.content==dst && it["dev"]?.jsonPrimitive?.content=="xrayfa0"}})fail(7,"TUN automatic routes not ready")
        lease.update("routesAfter" to ipJson("route","show","table","all"),"rulesAfter" to ipJson("rule"))
        val dns=command("/usr/bin/getent","ahostsv4","example.com")
        if(dns.isBlank())fail(7,"Virtual DNS data path returned no address")
        phase="resolver-setup"
        lease.update("resolverIntent" to JsonPrimitive(true),"phase" to JsonPrimitive("resolver-intent"))
        val backend=resolverSnapshot().getValue("backend").jsonPrimitive.content
        if(backend=="static-resolv.conf") setSessionDns(lease,"dns","198.18.0.2")
        else for((kind,value) in listOf("dns" to "198.18.0.2","domain" to "~.","default-route" to "true")) setSessionDns(lease,kind,value)
        val resolver=resolverSnapshot()
        if(backend=="static-resolv.conf") {
            if(resolver.getValue("nameservers").jsonArray.none{it.jsonPrimitive.content=="198.18.0.2"})fail(7,"Static resolver did not select virtual DNS")
        } else {
            val tunForResolver=tunIdentity() ?: fail(7,"TUN disappeared before resolver verification")
            val link=resolverStructured(resolver).getValue("links").jsonObject
                .getValue(tunForResolver.getValue("ifindex").jsonPrimitive.content).jsonObject
            if("198.18.0.2" !in link.getValue("dns").jsonArray.map{it.jsonPrimitive.content} ||
                "~." !in link.getValue("domains").jsonArray.map{it.jsonPrimitive.content})
                fail(7,"System resolver did not select virtual DNS")
        }
        lease.update("phase" to JsonPrimitive("Running"))
        phase="running"
        log.event("Running","Local resources ready; remote request success is measured separately; underlay=${plan.underlay}",force=true)
        var inspected=xf_millis()
        while(xf_interrupted()==0) {
            core.tick()
            if(core.exitCode!=null)fail(7,"Core exited unexpectedly; core_exit=${core.exitCode}")
            if(log.failed)fail(11,"Runtime log persistence failed")
            if(xf_millis()-inspected>=500) {
                if(normalizedNetwork(tunIdentity() ?: fail(7,"TUN disappeared"))!=normalizedNetwork(tun))fail(7,"TUN identity/parameters changed")
                if(nftState(plan.table)!=lease.record["nftAfter"])fail(7,"IPv6 protection changed")
                if(resolverSnapshot()!=lease.record["resolverAfter"])fail(7,"System resolver configuration changed")
                if(normalizedNetwork(ipJson("route","show","table","all"))!=normalizedNetwork(lease.record.getValue("routesAfter")))fail(7,"Routing configuration changed")
                if(ipJson("rule")!=lease.record["rulesAfter"])fail(7,"Routing rules changed")
                inspected=xf_millis()
            }
            usleep(10000u)
        }
        code=0;log.event("exit","Signal ${xf_interrupted()} requested stop",force=true)
    }catch(_:RuntimeStop){code=0}
    catch(e:CliError){primaryAt=xf_millis();code=e.code;log.event("error",e.message,severity="error",force=true)}
    catch(e:Exception){
        primaryAt=xf_millis();code=10
        val type=e::class.simpleName ?: "${e::class}"
        val detail=e.message?.trim()?.takeIf{it.isNotEmpty()}?.let{": ${clean(it)}"} ?: ""
        log.event("error","Runtime operation failed at $phase [$type]$detail",severity="error",force=true)
    }
    finally {
        if(lease!=null) {
            val complete=restoreOwned(lease.record,log,core,lease)
            if(!complete)code=9 else try{finishJournal(lease.record,log)}catch(_:Exception){code=9}
            lease.close()
        }else if(core!=null && !core.stop())code=9
        if(coreFd>=0)close(coreFd)
        if(code!=9 && log.failedAtMillis?.let{primaryAt==null || it<primaryAt!!}==true)code=11
        log.event("finish","END runtime exit=$code core_exit=${core?.exitCode ?: "not-executed"}",severity=if(code==0)"info" else "error",force=true)
        log.close()
        if(code!=9 && log.failedAtMillis?.let{primaryAt==null || it<primaryAt!!}==true)code=11
    }
    return code
}
