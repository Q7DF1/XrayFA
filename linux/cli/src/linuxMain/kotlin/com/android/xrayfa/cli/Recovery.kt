@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import kotlinx.cinterop.*
import kotlinx.serialization.json.*
import platform.posix.*

/** Drop only elapsed lease lifetimes, never actual addresses/routes/DNS configuration. */
internal fun normalizedNetwork(v:JsonElement):JsonElement=when(v){
    is JsonObject->JsonObject(v.filterKeys{it !in setOf("valid_life_time","preferred_life_time","expires")}.mapValues{normalizedNetwork(it.value)})
    is JsonArray->JsonArray(v.map{normalizedNetwork(it)})
    else->v
}
/** Reconcile the exact short-lived helper before touching resources it may still mutate. */
private fun stopPendingTool(record:JsonObject) {
    val child=record["pendingTool"]?.jsonObject ?: return
    if(child.getValue("state").jsonPrimitive.content=="reaped")return
    val path=child.getValue("path").jsonPrimitive.content
    if(path !in setOf(IP,NFT,RESOLVE))fail(9,"Journal contains an unknown mutation helper")
    val pid=child.getValue("pid").jsonPrimitive.int
    if(pid<=1)fail(9,"Invalid mutation helper PID")
    if(procStart(pid)==null)return
    val pfd=xf_pidfd_open(pid)
    if(pfd<0){if(procStart(pid)==null)return;fail(9,"Cannot pin pending mutation helper")}
    try {
        val observed=procStart(pid)
        if(observed==null && xf_pidfd_exited(pfd)!=0)return
        if(observed!=child.getValue("start").jsonPrimitive.content)fail(9,"Pending helper PID was reused")
        if(xf_pidfd_exited(pfd)==0) {
            val argv=try{readBytes("/proc/$pid/cmdline",65536)!!.decodeToString().split('\u0000').filter{it.isNotEmpty()}}catch(_:CliError){emptyList()}
            fun matches(device:String,inode:String,key:String):Boolean =
                xf_process_exe_matches(pid,child.getValue(device).jsonPrimitive.content.toULong(),child.getValue(inode).jsonPrimitive.content.toULong())!=0 &&
                argv==child.getValue(key).jsonArray.map{it.jsonPrimitive.content}
            val identity=matches("device","inode","argv") || matches("gateDevice","gateInode","gateArgv")
            if(!identity){
                val end=minOf(xf_millis()+200,cleanupDeadlineMillis ?: Long.MAX_VALUE)
                while(xf_pidfd_exited(pfd)==0 && xf_millis()<end)usleep(1000u)
                if(xf_pidfd_exited(pfd)==0)fail(9,"Live pending helper identity mismatches journal")
            }
            if(xf_pidfd_exited(pfd)==0){
                xf_pidfd_signal(pfd,SIGTERM)
                var end=minOf(xf_millis()+1000,(cleanupDeadlineMillis ?: Long.MAX_VALUE)-500)
                while(xf_pidfd_exited(pfd)==0 && xf_millis()<end)usleep(1000u)
                if(xf_pidfd_exited(pfd)==0)xf_pidfd_signal(pfd,SIGKILL)
                end=minOf(xf_millis()+1000,cleanupDeadlineMillis ?: Long.MAX_VALUE)
                while(xf_pidfd_exited(pfd)==0 && xf_millis()<end)usleep(1000u)
                if(xf_pidfd_exited(pfd)==0)fail(9,"Pending helper exit deadline exceeded")
            }
        }
    } finally{close(pfd)}
}
internal fun restoreOwned(record:JsonObject,log:LogSession?,core:RunningCore?,lease:NetworkLease):Boolean {
    val old=cleanupDeadlineMillis
    cleanupDeadlineMillis=xf_millis()+20000
    return try{restoreOwnedWithinDeadline(record,log,core,lease)}finally{cleanupDeadlineMillis=old}
}
private fun restoreOwnedWithinDeadline(record:JsonObject,log:LogSession?,core:RunningCore?,lease:NetworkLease):Boolean {
    var complete=true
    fun step(name:String,block:()->Unit) {
        try{block();log?.event("restore","$name restored",force=true);if(log==null)println("$name restored")}
        catch(e:Exception){
            complete=false
            val reason=if(e is CliError)e.message else "Resource operation failed"
            log?.event("restore","$name incomplete: $reason; resource preserved for inspection",severity="error",force=true)
            fputs("Recovery incomplete: $name: $reason\n",stderr)
            try {
                val previous=lease.record["recoveryErrors"]?.jsonArray ?: JsonArray(emptyList())
                lease.update("recoveryErrors" to JsonArray(previous+buildJsonObject{put("resource",name);put("reason",reason);put("utc",utc())}))
            }catch(_:Exception){fputs("Cannot persist recovery error; retained original journal\n",stderr)}
        }
    }
    var helpersSafe=true
    step("pending-tool") {
        try{stopPendingTool(record)}catch(e:Exception){helpersSafe=false;throw e}
    }
    val expectedTun=record["tunAfter"]?.jsonObject
    val before=record["baseline"]?.jsonObject
    step("resolver") {
        if(record["resolverIntent"]?.jsonPrimitive?.boolean==true) {
            val current=resolverSnapshot()
            val baseline=before?.get("resolver")?.jsonObject
            if(baseline==null || current!=baseline) {
                val backend=current["backend"]?.jsonPrimitive?.content
                if(backend=="static-resolv.conf") {
                    // Static resolv.conf is host-wide and its ownership is
                    // recorded by the post-mutation snapshot, so it remains
                    // safe to revert after a core crash has removed the TUN.
                    val expectedAfter=record["resolverAfter"]?.jsonObject
                    if(expectedAfter==null || current!=expectedAfter)fail(9,"Resolver changed outside the last owned step")
                    setSessionDns(lease,"revert")
                } else {
                    val tun=tunIdentity()
                    if(tun==null || expectedTun==null)fail(9,"TUN missing before link-scoped resolver revert")
                    if(normalizedNetwork(tun)!=normalizedNetwork(expectedTun))fail(9,"TUN identity changed")
                    val index=expectedTun.getValue("ifindex").jsonPrimitive.content
                    if(!resolverAlreadyReleased(record,current,index)) {
                        if(!resolverMayRevert(record,current,index))fail(9,"Resolver changed outside the last owned step")
                        if(!helpersSafe)fail(9,"Previous mutation helper is not safely stopped")
                        try { setSessionDns(lease,"revert") }
                        catch(e:CliError) {
                            // A concurrently killed core can close the TUN between the
                            // resolver snapshot and link-scoped resolver revert.
                            if(e.code!=7 || tunIdentity()!=null)throw e
                        }
                    }
                }
            }
        }
    }
    step("core") {
        if(core!=null){if(!core.stop(cleanupDeadlineMillis ?: (xf_millis()+5000)))fail(9,"Core exit confirmation failed")}
        else {
            val pid=record["corePid"]?.jsonPrimitive?.intOrNull
            if(pid!=null && procStart(pid)!=null) {
                val pfd=xf_pidfd_open(pid)
                if(pfd<0){if(procStart(pid)==null)return@step;fail(9,"Cannot pin orphan process")}
                try {
                    val start=record.getValue("coreStart").jsonPrimitive.content
                    val dev=record.getValue("coreDevice").jsonPrimitive.content.toULong()
                    val ino=record.getValue("coreInode").jsonPrimitive.content.toULong()
                    val observed=procStart(pid)
                    if(observed==null && xf_pidfd_exited(pfd)!=0)return@step
                    if(observed!=start)fail(9,"Orphan PID start-time mismatch")
                    // A concurrently killed core can lose /proc/exe before recover reads
                    // it. An exited pidfd plus the recorded start time proves death;
                    // there is no live process to signal or non-child waitpid to claim.
                    if(xf_pidfd_exited(pfd)==0) {
                        val exeMatches=xf_process_exe_matches(pid,dev,ino)!=0
                        val argv=try{readBytes("/proc/$pid/cmdline",65536)!!.decodeToString().split('\u0000')}catch(_:CliError){emptyList()}
                        val identityMatches=exeMatches && record.getValue("configPath").jsonPrimitive.content in argv
                        if(!identityMatches) {
                            val exitRace=minOf(xf_millis()+200,cleanupDeadlineMillis ?: Long.MAX_VALUE)
                            while(xf_pidfd_exited(pfd)==0 && xf_millis()<exitRace)usleep(1000u)
                            if(xf_pidfd_exited(pfd)==0)fail(9,"Live orphan executable/configuration identity mismatch")
                        }
                        if(xf_pidfd_exited(pfd)==0) {
                            if(xf_pidfd_signal(pfd,SIGTERM)!=0 && xf_pidfd_exited(pfd)==0)fail(9,"Cannot stop orphan core")
                            var deadline=minOf(xf_millis()+3000,(cleanupDeadlineMillis ?: Long.MAX_VALUE)-500)
                            while(xf_pidfd_exited(pfd)==0 && xf_millis()<deadline)usleep(10000u)
                            if(xf_pidfd_exited(pfd)==0)xf_pidfd_signal(pfd,SIGKILL)
                            deadline=minOf(xf_millis()+2000,cleanupDeadlineMillis ?: Long.MAX_VALUE)
                            while(xf_pidfd_exited(pfd)==0 && xf_millis()<deadline)usleep(10000u)
                            if(xf_pidfd_exited(pfd)==0)fail(9,"Orphan exit deadline exceeded")
                        }
                    }
                }finally{close(pfd)}
            }
        }
    }
    step("tun-and-routes") {
        // Nonpersistent TUN disappears when the final core FD closes. Never delete an ambiguous link.
        if(tunIdentity()!=null)fail(9,"TUN still present; ownership cannot be proven for destructive deletion")
        (record["managementRoutesAfter"] ?: record["managementRouteIntent"])?.jsonArray?.forEach { element ->
            val route=element.jsonObject
            if(route["owned"]?.jsonPrimitive?.boolean!=true)return@forEach
            val destination=route.getValue("destination").jsonPrimitive.content
            val gateway=route["gateway"]?.jsonPrimitive?.content
            val device=route.getValue("device").jsonPrimitive.content
            val present=ipJson("route","show","table","all").any{item->
                val v=item.jsonObject
                v["dst"]?.jsonPrimitive?.content in setOf(destination,"$destination/32") &&
                    v["dev"]?.jsonPrimitive?.content==device &&
                    v["gateway"]?.jsonPrimitive?.content==gateway
            }
            if(present) {
                val args=mutableListOf("route","del","$destination/32")
                gateway?.let{args+=listOf("via",it)}
                args+=listOf("dev",device)
                lease.mutate(IP,*args.toTypedArray())
            }
        }
        if(before!=null){
            if(normalizedNetwork(ipJson("route","show","table","all"))!=normalizedNetwork(before.getValue("routes")))fail(9,"Routes differ from baseline")
            if(ipJson("rule")!=before.getValue("rules"))fail(9,"Rules differ from baseline")
            if(resolverSnapshot()!=before.getValue("resolver"))fail(9,"Resolver differs from baseline")
        }
    }
    step("ipv6") {
        record["nftTable"]?.jsonPrimitive?.content?.let{table->
            if(!Regex("xfa_[0-9_]+").matches(table))fail(9,"Invalid journal nft table name")
            val current=nftState(table)
            if(current!=null){
                val after=record["nftAfter"]
                if(after!=null){if(current!=after)fail(9,"nft after-image changed")}
                else {
                    val intent=record["nftIntent"] ?: fail(9,"nft intent is missing")
                    if(nftSemantics(current)!=intent)fail(9,"nft semantic ownership evidence mismatches intent")
                    val token=record.getValue("ownershipToken").jsonPrimitive.content
                    if(!Regex("[0-9a-f]{32}").matches(token) || "xrayfa:$token" !in current.toString())fail(9,"nft ownership token is missing")
                }
                if(!helpersSafe)fail(9,"Previous mutation helper is not safely stopped")
                lease.mutate(NFT,"delete","table","inet",table)
                if(nftState(table)!=null)fail(9,"nft table retained")
            }
        }
    }
    step("addresses-and-links") {
        if(before!=null){
            if(normalizedNetwork(ipJson("address"))!=normalizedNetwork(before.getValue("addresses")))fail(9,"Addresses differ from baseline")
            if(ipJson("link")!=before.getValue("links"))fail(9,"Links differ from baseline")
        }
    }
    return complete
}
internal fun finishJournal(record:JsonObject,log:LogSession?) {
    val uid=record.getValue("uid").jsonPrimitive.content
    val directory="/var/log/xrayfa/$uid"
    privateDir("/var/log/xrayfa");privateDir(directory)
    val completed=JsonObject(record.toMutableMap().apply{put("phase",JsonPrimitive("restored"));put("restoredUtc",JsonPrimitive(utc()))})
    atomicPrivate("$directory/recovery-${getpid()}-${xf_millis()}.json",completed)
    if(unlink("/run/xrayfa/session.json")!=0)fail(9,"Cannot remove restored journal")
    val fd=open("/run/xrayfa",O_RDONLY or O_DIRECTORY or O_CLOEXEC)
    if(fd<0)fail(9,"Cannot open recovery directory")
    try{if(fsync(fd)!=0)fail(9,"Cannot persist journal completion")}finally{close(fd)}
    log?.event("restore","Journal archived; recovery complete",force=true)
}
internal fun recoverSession():Int {
    if(geteuid()!=0u)fail(4,"recover requires root")
    val caller=callerIdentity()
    privateDir("/run/xrayfa")
    val lock=open("/run/xrayfa/tun.lock",O_RDWR or O_CREAT or O_NOFOLLOW or O_CLOEXEC,384)
    if(lock<0 || xf_fd_owned(lock,0u,0)==0){if(lock>=0)close(lock);fail(9,"Unsafe recovery lock")}
    try {
        if(xf_try_lock(lock)!=0)fail(8,"Active controller owns the runtime lock")
        if(access("/run/xrayfa/session.json",F_OK)!=0){println("No retained runtime journal");return 0}
        try{ownedFile("/run/xrayfa/session.json")}catch(_:Exception){fail(9,"Unsafe recovery journal; preserved")}
        val record=try{json.parseToJsonElement(readBytes("/run/xrayfa/session.json")!!.decodeToString()).jsonObject}catch(_:Exception){fail(9,"Invalid journal; preserved")}
        try {
            require(record.getValue("schemaVersion").jsonPrimitive.int==1)
            if(record.getValue("bootId").jsonPrimitive.content!=bootId() || record.getValue("namespace").jsonPrimitive.content!=networkNamespace())fail(9,"Journal boot/namespace mismatch; preserved")
            val pid=record.getValue("controllerPid").jsonPrimitive.int
            if(procStart(pid)==record.getValue("controllerStart").jsonPrimitive.content)fail(8,"Journal controller remains active")
            validateRecoveryJournal(record)
            val uid=record.getValue("uid").jsonPrimitive.content.toUInt()
            var log:LogSession?=null
            try{privateDir("/var/log/xrayfa");log=LogSession(record["nodeId"]?.jsonPrimitive?.int,"info","/var/log/xrayfa/$uid",uid,nonBlockingTerminal=true)}catch(_:Exception){fputs("Recovery log unavailable; continuing resource restoration\n",stderr)}
            val lease=NetworkLease.attached(lock,record)
            val result=try {
                if(!restoreOwned(record,log,null,lease))9 else {finishJournal(lease.record,log);if(log==null)11 else 0}
            }finally{log?.close()}
            return if(result==0 && log?.failed==true)11 else result
        }catch(e:CliError){throw e}catch(_:Exception){fail(9,"Journal identity/schema validation failed; preserved")}
    }finally{close(lock)}
}

/** Stop only the controller identified by the live journal, then reuse the
 * normal recovery path. This is needed when a non-TTY SSH client disappears:
 * the remote root controller does not receive the terminal's Ctrl+C byte. */
internal fun stopSession():Int {
    if(geteuid()!=0u)fail(4,"stop requires root")
    if(access("/run/xrayfa/session.json",F_OK)!=0){println("No active runtime journal");return 0}
    try{ownedFile("/run/xrayfa/session.json")}catch(_:Exception){fail(9,"Unsafe runtime journal; preserved")}
    val record=try{json.parseToJsonElement(readBytes("/run/xrayfa/session.json")!!.decodeToString()).jsonObject}catch(_:Exception){fail(9,"Invalid runtime journal; preserved")}
    try {
        require(record.getValue("schemaVersion").jsonPrimitive.int==1)
        if(record.getValue("bootId").jsonPrimitive.content!=bootId() || record.getValue("namespace").jsonPrimitive.content!=networkNamespace())fail(9,"Journal boot/namespace mismatch; preserved")
        val pid=record.getValue("controllerPid").jsonPrimitive.int
        val expectedStart=record.getValue("controllerStart").jsonPrimitive.content
        val observed=procStart(pid)
        if(observed==null)return recoverSession()
        if(observed!=expectedStart)fail(9,"Journal controller PID has been reused; preserved")
        val expectedExe=record.getValue("controllerExe").jsonPrimitive.content
        fun controllerExeMatches():Boolean {
            val live=linkTarget("/proc/$pid/exe") ?: return false
            return live==expectedExe || live=="$expectedExe (deleted)"
        }
        if(!controllerExeMatches())fail(9,"Journal controller executable identity mismatch; preserved")
        val pfd=xf_pidfd_open(pid)
        if(pfd>=0)try {
            if(xf_pidfd_exited(pfd)==0) {
                if(xf_pidfd_signal(pfd,SIGINT)!=0)fail(9,"Cannot signal active controller; preserved")
                var deadline=xf_millis()+10000
                while(xf_pidfd_exited(pfd)==0 && xf_millis()<deadline)usleep(10000u)
                if(xf_pidfd_exited(pfd)==0) {
                    xf_pidfd_signal(pfd,SIGTERM)
                    deadline=xf_millis()+5000
                    while(xf_pidfd_exited(pfd)==0 && xf_millis()<deadline)usleep(10000u)
                }
                if(xf_pidfd_exited(pfd)==0)fail(9,"Active controller did not stop; journal preserved")
            }
        }finally{close(pfd)} else {
            // Ubuntu 18.04's 4.15 kernel has no pidfd_open. Re-check the
            // journal identity immediately before falling back to kill(2).
            if(procStart(pid)!=expectedStart || !controllerExeMatches())
                fail(9,"Active controller identity changed; preserved")
            if(kill(pid,SIGINT)!=0)fail(9,"Cannot signal active controller; preserved")
            var deadline=xf_millis()+10000
            while(procStart(pid)!=null && xf_millis()<deadline)usleep(10000u)
            if(procStart(pid)!=null) {
                if(procStart(pid)!=expectedStart || !controllerExeMatches())
                    fail(9,"Active controller identity changed while stopping; preserved")
                kill(pid,SIGTERM)
                deadline=xf_millis()+5000
                while(procStart(pid)!=null && xf_millis()<deadline)usleep(10000u)
            }
            if(procStart(pid)!=null)fail(9,"Active controller did not stop; journal preserved")
        }
        return recoverSession()
    }catch(e:CliError){throw e}catch(_:Exception){fail(9,"Runtime journal identity validation failed; preserved")}
}
