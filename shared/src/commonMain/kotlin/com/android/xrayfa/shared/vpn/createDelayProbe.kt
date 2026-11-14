package com.android.xrayfa.shared.vpn

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.DelayMeasurement
import com.android.xrayfa.common.core.DelayProbe
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.model.Node
import com.android.xrayfa.parser.ParserFactory
import kotlinx.coroutines.CancellationException

fun createDelayProbe(
    xrayCore: XrayCore,
    parserFactory: ParserFactory,
): DelayProbe =
    DelayProbe(
        measureLive = xrayCore::measureDelaySync,
        parseConfig = { url -> parserFactory.getParser(url).parse(CoreStartOptions(url = url)) },
        measureOutbound = xrayCore::measureOutboundDelay,
        parseStartOptions = { options -> parserFactory.getParser(options.url).parse(options) },
        parseNodeConfig = { url, mux -> parserFactory.getParser(url).parse(CoreStartOptions(url = url, muxSettings = mux)) },
    )

/** Resolve by identity so duplicate share links can retain different local options. */
suspend fun DelayProbe.measureNodeWithOptions(
    node: Node,
    testUrl: String,
    resolver: VpnStartOptionsResolver,
): Long = try {
    measureNode(node.url, testUrl, node.muxSettings, resolver.resolveNode(node))
} catch (e: Exception) {
    if (e is CancellationException) throw e
    DelayMeasurement.TIMEOUT_SENTINEL
}
