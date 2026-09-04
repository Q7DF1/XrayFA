package com.android.xrayfa.common.utils

import kotlin.test.Test
import kotlin.test.assertContains
import kotlin.test.assertTrue

class BugReportIssueComposerTest {

    @Test
    fun composeIssueUrl_encodesTitleAndIncludesEnvironment() {
        val url = BugReportIssueComposer.composeIssueUrl(
            title = "vpn drop",
            description = "dies after 1m",
            expectedBehavior = "stays up",
            actualBehavior = "tunnel dies",
            labels = BugReportIssueLabels(
                header = "Bug Report",
                description = "Description",
                expectedBehavior = "Expected Behavior",
                actualBehavior = "Actual Behavior",
            ),
            appVersion = "1.7.0",
            osName = "iOS",
            osVersion = "18.0",
            deviceModel = "iPhone17,1",
        )

        assertTrue(url.startsWith("${BugReportIssueComposer.NEW_ISSUE_BASE}?"))
        assertContains(url, "title=[Bug]%20vpn+drop")
        assertContains(url, "labels=bug")
        assertContains(url, UrlCodec.encode("1.7.0"))
        assertContains(url, UrlCodec.encode("iOS Version"))
        assertContains(url, UrlCodec.encode("iPhone17,1"))
        assertContains(url, UrlCodec.encode("dies after 1m"))
    }
}
