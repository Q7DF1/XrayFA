package com.android.xrayfa.core

import android.os.SystemClock
import android.content.Context
import android.util.Log
import androidx.annotation.StringDef
import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.VpnStartupStateStore
import com.android.xrayfa.common.core.StartupStage
import com.android.xrayfa.common.core.StartupFailureKind
import com.android.xrayfa.common.core.VpnStartupFailure
import com.android.xrayfa.common.core.GeodataStatus
import com.android.xrayfa.common.core.classifyStartupFailure
import com.android.xrayfa.common.core.startupExceptionDetails
import java.io.File
import com.android.xrayfa.common.core.TrafficDetector
import com.android.xrayfa.common.core.XrayAssetPaths
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.datastore.SettingsRepository
import com.android.xrayfa.nativebridge.XrayBridge
import com.android.xrayfa.nativebridge.XrayCoreCallback
import com.android.xrayfa.nativebridge.XrayCoreController
import com.android.xrayfa.nativebridge.outboundTrafficValue
import com.android.xrayfa.parser.ParserFactory
import com.android.xrayfa.utils.Device
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

const val TAG_PROXY = "proxy"
const val TAG_DIRECT = "direct"
@StringDef(value = [
    TAG_PROXY,
    TAG_DIRECT
])
@Retention(AnnotationRetention.SOURCE)
annotation class Tag

const val UP_STEAM = "uplink"
const val DOWN_STEAM = "downlink"
@StringDef(value =[
    UP_STEAM,
    DOWN_STEAM
])
annotation class Stream

class XrayCoreManager(
    private val context: Context,
    private val coroutineScope: CoroutineScope,
    private val parserFactory: ParserFactory,
    private val settingsRepository: SettingsRepository,
    private val assetPaths: XrayAssetPaths,
    private val xrayBridge: XrayBridge,
    private val assetInstaller: XrayAssetInstaller,
    private val startupStateStore: VpnStartupStateStore,
): XrayCore {

    companion object {
        const val TAG = "XrayCoreManager"
    }
    private var coreController: XrayCoreController? = null
    private var job: Job? = null
    private var jsonSession = false
    private val _sessionTraffic = MutableStateFlow(VpnSessionTraffic())
    val sessionTraffic = _sessionTraffic.asStateFlow()

    private val _trafficFlow = MutableSharedFlow<Pair<Double, Double>>(replay = 1)
    override val trafficFlow: SharedFlow<Pair<Double, Double>> = _trafficFlow.asSharedFlow()

    private val controllerCallback = object : XrayCoreCallback {
        override fun onEmitStatus(code: Long, message: String?): Long {
            Log.i(TAG, if (jsonSession) "JSON core status: $code" else "onEmitStatus: $code $message")
            return 0L
        }

        override fun onShutdown(): Long {
            Log.i(TAG, "shutdown: end")
            return 0L
        }

        override fun onStartup(): Long {
            Log.i(TAG, "startup: start")
            return 0L
        }
    }

    init {
        Log.i(TAG, assetPaths.basePath)
        try {
            xrayBridge.initCoreEnv(assetPaths.basePath, Device.getDeviceIdForXUDPBaseKey())
            coreController = xrayBridge.newCoreController(controllerCallback)
            coroutineScope.launch {
                try {
                    val xrayCoreVersion = xrayBridge.checkVersion()
                    if (settingsRepository.settingsFlow.first().xrayCoreVersion != xrayCoreVersion) {
                        settingsRepository.setXrayCoreVersion(xrayCoreVersion)
                    }
                } catch (e: LinkageError) {
                    startupStateStore.fail(classifyStartupFailure(e, StartupStage.APPLICATION))
                }
            }
        } catch (e: LinkageError) {
            startupStateStore.fail(classifyStartupFailure(e, StartupStage.APPLICATION))
        }
    }


    override fun measureDelaySync(url: String): Long {
        if (coreController?.isRunning == false) return -1
        return try {
            coreController?.measureDelay(url) ?: 0L
        } catch (e: Exception) {
            Log.e(TAG, "measureDelaySync: ${e.message}")
            -1
        }
    }

    override fun measureOutboundDelay(config: String, url: String): Long {
        assetInstaller.ensureInstalled()
        return xrayBridge.measureOutboundDelay(config, url)
    }

    override suspend fun startXrayCore(startOptions: CoreStartOptions, tunFd: Int?): Boolean {
        jsonSession = startOptions.jsonConfig != null
        val attemptId = startOptions.startupAttemptId ?: startupStateStore.state.value.attemptId
        var stage = StartupStage.GEODATA
        try {
            withContext(Dispatchers.IO) { assetInstaller.ensureInstalled() }
            val controller = coreController ?: run {
                val existing = startupStateStore.state.value.failure
                startupStateStore.fail(existing ?: VpnStartupFailure(StartupFailureKind.CORE_INITIALIZATION,
                    StartupStage.CORE, "Core controller is unavailable"), attemptId)
                return false
            }
            stage = StartupStage.CONFIGURATION
            val config = startOptions.jsonConfig ?: parserFactory.getParser(startOptions.url).parse(startOptions)
            stage = StartupStage.CORE
            check(tunFd != null) { "VPN_INTERFACE: TUN descriptor is unavailable" }
            controller.startLoop(config, tunFd)
            check(controller.isRunning) { "Core did not enter the running state" }
            _sessionTraffic.value = VpnSessionTraffic(
                startedAtElapsed = SystemClock.elapsedRealtime(),
                available = !jsonSession,
            )
            // Start traffic detection after core is confirmed running
            if (!jsonSession) startTrafficDetection() else _trafficFlow.emit(Pair(0.0, 0.0))
            return true
        }catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            reportStartFailure(e, stage, attemptId)
            return false
        } catch (e: LinkageError) {
            reportStartFailure(e, StartupStage.APPLICATION, attemptId)
            return false
        }
    }

    private fun reportStartFailure(error: Throwable, stage: StartupStage, attemptId: Long) {
        val message = startupExceptionDetails(error)
        val match = Regex("(?i)failed to (?:check|load) code\\s+([a-z0-9_.@-]+)\\s+from\\s+(geo(?:site|ip)\\.dat)").find(message)
        val name = match?.groupValues?.get(2)?.lowercase()
            ?: listOf("geosite.dat", "geoip.dat").firstOrNull { message.contains(it, ignoreCase = true) }
        val geoStatus = name?.let {
            val file = File(if (it == "geosite.dat") assetPaths.geoSitePath else assetPaths.geoIpPath)
            val codes = XrayAssetInstaller.readCodes(file)
            when {
                !file.exists() -> GeodataStatus.MISSING
                codes == null -> GeodataStatus.INVALID
                match != null && match.groupValues[1].uppercase() !in codes -> GeodataStatus.CATEGORY_MISSING
                else -> GeodataStatus.VALID
            }
        }
        val failure = classifyStartupFailure(error, stage, geoStatus)
        startupStateStore.fail(failure, attemptId)
        Log.e(TAG, "${failure.kind.code}: ${failure.technicalDetails}")
    }

    override fun stopXrayCore() {
        stopTrafficDetection()
        coreController?.stopLoop()
        _sessionTraffic.value = VpnSessionTraffic()
    }

    override fun startTrafficDetection() {
        job?.cancel()
        if (jsonSession) return
        job = coroutineScope.launch(Dispatchers.IO) {
            var last = System.currentTimeMillis()
            // send initial zero values
            _trafficFlow.emit(Pair(0.0, 0.0))
            delay(3000L)
            while (true) {
                val cur = System.currentTimeMillis()
                val snapshot = coreController?.queryAllOutboundTrafficStats().orEmpty()
                val up = outboundTrafficValue(snapshot, TAG_PROXY, UP_STEAM)
                val down = outboundTrafficValue(snapshot, TAG_PROXY, DOWN_STEAM)
                _sessionTraffic.update { it.add(up, down) }
                val deltaTimeSec = (cur - last) / 1000.0
                val upSpeed = if (deltaTimeSec > 0) (up / deltaTimeSec) / 1024 else 0.0
                val downSpeed = if (deltaTimeSec > 0) (down / deltaTimeSec) / 1024 else 0.0
                _trafficFlow.emit(Pair(upSpeed, downSpeed))
                last = cur
                delay(3000L)
            }
        }
    }

    override fun stopTrafficDetection() {
        job?.cancel()
        Log.d(TAG, "stopTrafficDetection: ${job?.isActive}")
    }
}
