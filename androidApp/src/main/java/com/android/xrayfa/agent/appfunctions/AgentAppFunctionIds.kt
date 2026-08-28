package com.android.xrayfa.agent.appfunctions

object AgentAppFunctionIds {
    const val PREFIX = "com.android.xrayfa.agent.appfunctions.XrayFAAppFunctions"

    val ALL: List<String> = listOf(
        "$PREFIX#getVpnStatus",
        "$PREFIX#getSelectedNode",
        "$PREFIX#listNodes",
        "$PREFIX#getNode",
        "$PREFIX#listSubscriptions",
        "$PREFIX#getSettingsSummary",
        "$PREFIX#getTrafficSpeeds",
        "$PREFIX#getAppInfo",
        "$PREFIX#selectNode",
        "$PREFIX#setFavorite",
        "$PREFIX#connectVpn",
        "$PREFIX#disconnectVpn",
        "$PREFIX#refreshSubscription",
        "$PREFIX#measureNodeDelay",
        "$PREFIX#openScreen",
    )
}
