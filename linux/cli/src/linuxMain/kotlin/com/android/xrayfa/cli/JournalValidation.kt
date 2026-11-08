@file:OptIn(kotlinx.cinterop.ExperimentalForeignApi::class)
package com.android.xrayfa.cli

import com.android.xrayfa.cli.host.*
import com.android.xrayfa.common.utils.calculateBytesHash
import kotlinx.serialization.json.*
import platform.posix.*

/** Reject malformed/foreign process claims before any recovery network operation. */
internal fun validateRecoveryJournal(record:JsonObject) {
    try {
        fun number(key:String)=record.getValue(key).jsonPrimitive.content.toLong().also{require(it>0)}
        fun hash(key:String)=record.getValue(key).jsonPrimitive.content.also{require(Regex("[0-9a-f]{64}").matches(it))}
        val controller=number("controllerPid").also{require(it>1 && it<=Int.MAX_VALUE)}
        number("controllerStart");number("nodeId");record.getValue("uid").jsonPrimitive.content.toUInt()
        require(record.getValue("controllerExe").jsonPrimitive.content.startsWith('/'))
        hash("configSha256");hash("coreSha256")
        require(Regex("[0-9a-f]{32}").matches(record.getValue("ownershipToken").jsonPrimitive.content))
        val phase=record.getValue("phase").jsonPrimitive.content
        require(phase in setOf("leased","management-route-intent","management-routes-applied","ipv6-intent","ipv6-applied","core-intent","core-gated","tun-ready","resolver-intent","Running"))
        if(phase!="leased" || "baseline" in record) {
            val base=record.getValue("baseline").jsonObject
            for(key in listOf("routes","rules","links","addresses"))base.getValue(key).jsonArray
            resolverStructured(base.getValue("resolver").jsonObject)
            val config=record.getValue("configPath").jsonPrimitive.content
            require(Regex("/run/xrayfa/artifacts/$controller-[0-9]+/final\\.json").matches(config))
            ownedFile(config)
            require(calculateBytesHash(readBytes(config)!!)==hash("configSha256"))
            require(record.getValue("corePath").jsonPrimitive.content.startsWith('/'))
            number("coreDevice");number("coreInode")
        }
        if(phase in setOf("core-gated","tun-ready","resolver-intent","Running"))number("corePid").also{require(it>1 && it<=Int.MAX_VALUE)}
        record["nftTable"]?.jsonPrimitive?.content?.let {
            require(Regex("xfa_${controller}_[0-9]+").matches(it))
            require("nftIntent" in record)
        }
        for(key in listOf("managementRouteIntent","managementRoutesAfter")) record[key]?.jsonArray?.forEach { element ->
            val route=element.jsonObject
            val destination=route.getValue("destination").jsonPrimitive.content
            require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(destination))
            require(Regex("[a-zA-Z0-9_.-]{1,15}").matches(route.getValue("device").jsonPrimitive.content))
            route["gateway"]?.jsonPrimitive?.content?.let{require(Regex("(?:[0-9]{1,3}\\.){3}[0-9]{1,3}").matches(it))}
            require(route.getValue("owned").jsonPrimitive.boolean)
        }
        record["pendingTool"]?.jsonObject?.let{child->
            require(child.getValue("state").jsonPrimitive.content in setOf("gated","reaped"))
            val pid=child.getValue("pid").jsonPrimitive.int;require(pid>1)
            for(key in listOf("start","device","inode","gateDevice","gateInode"))require(child.getValue(key).jsonPrimitive.content.toULong()>0uL)
            val path=child.getValue("path").jsonPrimitive.content
            val argv=child.getValue("argv").jsonArray.map{it.jsonPrimitive.content}
            require(argv.firstOrNull()==path)
            require(child.getValue("gateArgv").jsonArray.isNotEmpty())
            when(path) {
                IP-> {
                    require(argv.size>=5 && argv[0]==IP && argv[1]=="route" && argv[2] in setOf("add","del"))
                    val destination=argv[3].removeSuffix("/32")
                    val routes=(record["managementRoutesAfter"] ?: record["managementRouteIntent"])?.jsonArray ?: fail(9,"Management route intent is missing")
                    require(routes.any{route->
                        val r=route.jsonObject
                        r.getValue("destination").jsonPrimitive.content==destination &&
                            r.getValue("device").jsonPrimitive.content==argv.last() &&
                            ((r["gateway"]?.jsonPrimitive?.content==null && "via" !in argv) || r["gateway"]?.jsonPrimitive?.content==argv.getOrNull(argv.indexOf("via")+1))
                    })
                }
                NFT->{
                    val policy=record.getValue("configPath").jsonPrimitive.content.substringBeforeLast('/')+"/ipv6.nft"
                    require(argv==listOf(NFT,"-f",policy) || argv==listOf(NFT,"delete","table","inet",record.getValue("nftTable").jsonPrimitive.content))
                }
                RESOLVE->{
                    val tun=record.getValue("tunAfter").jsonObject
                    val index=tun.getValue("ifindex").jsonPrimitive.content
                    if(RESOLVE==SYSTEM_RESOLVE) {
                        val name=tun.getValue("ifname").jsonPrimitive.content
                        require(argv==listOf(RESOLVE,"--set-dns=198.18.0.2","--interface=$name") ||
                            argv==listOf(RESOLVE,"--set-domain=~.","--interface=$name") || argv==listOf(RESOLVE,"--revert","--interface=$name"))
                    } else require(argv==listOf(RESOLVE,"dns",index,"198.18.0.2") || argv==listOf(RESOLVE,"domain",index,"~.") ||
                        argv==listOf(RESOLVE,"default-route",index,"true") || argv==listOf(RESOLVE,"revert",index))
                }
                else->fail(9,"Unknown helper in recovery journal")
            }
        }
        record["corePid"]?.jsonPrimitive?.int?.let {pid->
            require(pid>1);number("coreStart")
            if(procStart(pid)!=null) {
                val pfd=xf_pidfd_open(pid)
                if(pfd<0){if(procStart(pid)==null)return@let;fail(9,"Cannot inspect journal core process")}
                try {
                    val observed=procStart(pid)
                    if(observed==null && xf_pidfd_exited(pfd)!=0)return@let
                    if(observed!=record.getValue("coreStart").jsonPrimitive.content)fail(9,"Journal core PID has been reused")
                    if(xf_pidfd_exited(pfd)==0) {
                        val exe=xf_process_exe_matches(pid,record.getValue("coreDevice").jsonPrimitive.content.toULong(),record.getValue("coreInode").jsonPrimitive.content.toULong())!=0
                        val argv=try{readBytes("/proc/$pid/cmdline",65536)!!.decodeToString().split('\u0000').filter{it.isNotEmpty()}}catch(_:CliError){emptyList()}
                        val expected=listOf(record.getValue("corePath").jsonPrimitive.content,"run","-c",record.getValue("configPath").jsonPrimitive.content)
                        if(!exe || argv!=expected){
                            val deadline=xf_millis()+200
                            while(xf_pidfd_exited(pfd)==0 && xf_millis()<deadline)usleep(1000u)
                            if(xf_pidfd_exited(pfd)==0)fail(9,"Journal core executable/argv does not match a live owned core")
                        }
                    }
                }finally{close(pfd)}
            }
        }
    } catch(e:CliError){if(e.code==9)throw e;fail(9,"Journal identity/file validation failed; preserved")}
      catch(_:Exception){fail(9,"Journal schema or resource identity is invalid; preserved")}
}
