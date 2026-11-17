package com.android.xrayfa.cli

import kotlin.test.*
import kotlinx.serialization.json.*

class ReportAndFailureTest {
    @Test
    fun reportRetainsSchemaOneFieldsNullsAndNumericExitCodes() {
        val report =
            ValidationReport(
                clientVersion = "fixture",
                nodeId = 1,
                utc = "2026-11-09T00:00:00Z",
                sessionId = "42-123",
                configPath = null,
                configSha256 = null,
                corePath = null,
                coreVersion = "",
                coreSha256 = null,
                coreExitCode = null,
                exitCode = ExitCode.CONFIGURATION,
                timedOut = false,
                interrupted = false,
                logPath = "/tmp/fixture.log",
                adaptations = listOf("fixture adaptation"),
            )
        val expected =
            Json.parseToJsonElement(
                    """{
            "schemaVersion":1,"clientVersion":"fixture","nodeId":1,"utc":"2026-11-09T00:00:00Z",
            "sessionId":"42-123","configPath":null,"configSha256":null,"corePath":null,"coreVersion":"",
            "coreSha256":null,"coreExitCode":null,"exitCode":6,"success":false,"timedOut":false,
            "interrupted":false,"logPath":"/tmp/fixture.log","adaptations":["fixture adaptation"]
        }"""
                )
                .jsonObject
        assertEquals(expected, report.encode())
        assertTrue(
            report
                .copy(exitCode = ExitCode.SUCCESS, coreExitCode = 0)
                .encode()
                .getValue("success")
                .jsonPrimitive
                .boolean
        )
    }

    @Test
    fun firstObservedFailureWinsButIncompleteRecoveryHasPriority() {
        assertEquals(ExitCode.LOG, exitAfterLogging(ExitCode.SUCCESS, null, 100))
        assertEquals(ExitCode.LOG, exitAfterLogging(ExitCode.CONFIGURATION, 200, 100))
        assertEquals(ExitCode.CONFIGURATION, exitAfterLogging(ExitCode.CONFIGURATION, 100, 200))
        assertEquals(ExitCode.CONFIGURATION, exitAfterLogging(ExitCode.CONFIGURATION, 100, 100))
        assertEquals(ExitCode.SUCCESS, exitAfterLogging(ExitCode.SUCCESS, null, null))
        assertEquals(
            ExitCode.RECOVERY,
            exitAfterLogging(ExitCode.RECOVERY, 200, 100, preserveRecovery = true),
        )
        assertEquals(
            ExitCode.LOG,
            exitAfterLogging(ExitCode.EXECUTION, 200, 100, preserveRecovery = true),
        )
    }
}
