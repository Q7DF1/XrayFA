package com.android.xrayfa.agent.appfunctions

fun interface AgentAppFunctionEnabledWriter {
    suspend fun setEnabled(functionId: String, enabled: Boolean)
}

/** Pushes the in-app Agent master switch to OS-level AppFunction enablement (B2). */
class AgentAppFunctionEnableSync(
    private val writer: AgentAppFunctionEnabledWriter?,
    private val functionIds: List<String> = AgentAppFunctionIds.ALL,
    private val onError: (functionId: String, error: Throwable) -> Unit = { _, _ -> },
) {
    suspend fun sync(enabled: Boolean) {
        val sink = writer ?: return
        for (id in functionIds) {
            try {
                sink.setEnabled(id, enabled)
            } catch (error: Exception) {
                onError(id, error)
            }
        }
    }
}
