package com.android.xrayfa.agent.appfunctions

import org.junit.Assert.assertEquals
import org.junit.Test
import kotlinx.coroutines.runBlocking

class AgentAppFunctionEnableSyncTest {

    @Test
    fun sync_nullWriter_isNoOp() = runBlocking {
        AgentAppFunctionEnableSync(writer = null, functionIds = listOf("a")).sync(true)
    }

    @Test
    fun sync_writesEveryId() = runBlocking {
        val writes = mutableListOf<Pair<String, Boolean>>()
        val sync = AgentAppFunctionEnableSync(
            writer = AgentAppFunctionEnabledWriter { id, enabled -> writes += id to enabled },
            functionIds = listOf("one", "two"),
        )

        sync.sync(false)

        assertEquals(listOf("one" to false, "two" to false), writes)
    }

    @Test
    fun sync_continuesAfterWriterError() = runBlocking {
        val writes = mutableListOf<String>()
        val errors = mutableListOf<String>()
        val sync = AgentAppFunctionEnableSync(
            writer = AgentAppFunctionEnabledWriter { id, _ ->
                if (id == "bad") error("nope")
                writes += id
            },
            functionIds = listOf("ok1", "bad", "ok2"),
            onError = { id, _ -> errors += id },
        )

        sync.sync(true)

        assertEquals(listOf("ok1", "ok2"), writes)
        assertEquals(listOf("bad"), errors)
    }

    @Test
    fun allIds_matchEveryGeneratedFunctionId() {
        val generated = XrayFAAppFunctionsIds::class.java.declaredFields
            .filter { field ->
                field.name.endsWith("_ID") &&
                    java.lang.reflect.Modifier.isStatic(field.modifiers)
            }
            .map { field -> field.get(null) as String }
            .toSet()

        assertEquals(generated, AgentAppFunctionIds.ALL.toSet())
        AgentAppFunctionIds.ALL.forEach { id ->
            assertEquals(true, id.startsWith(AgentAppFunctionIds.PREFIX + "#"))
        }
    }
}
