package com.android.xrayfa.shared.vpn

import com.android.xrayfa.common.core.CoreStartOptions
import com.android.xrayfa.common.core.DelayProbe
import com.android.xrayfa.common.core.XrayCore
import com.android.xrayfa.parser.ParserFactory

fun createDelayProbe(
    xrayCore: XrayCore,
    parserFactory: ParserFactory,
): DelayProbe =
    DelayProbe(
        measureLive = xrayCore::measureDelaySync,
        parseConfig = { url -> parserFactory.getParser(url).parse(CoreStartOptions(url = url)) },
        measureOutbound = xrayCore::measureOutboundDelay,
    )
