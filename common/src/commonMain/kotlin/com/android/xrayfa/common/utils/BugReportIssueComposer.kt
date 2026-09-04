package com.android.xrayfa.common.utils

data class BugReportIssueLabels(
    val header: String,
    val description: String,
    val expectedBehavior: String,
    val actualBehavior: String,
)

object BugReportIssueComposer {
    const val NEW_ISSUE_BASE = "https://github.com/Q7DF1/XrayFA/issues/new"

    fun composeIssueUrl(
        title: String,
        description: String,
        expectedBehavior: String,
        actualBehavior: String,
        labels: BugReportIssueLabels,
        appVersion: String,
        osName: String,
        osVersion: String,
        deviceModel: String,
    ): String {
        val issueBody = """
            ### [${labels.header}] $title
        
        **${labels.description}:**
        $description
        
        **${labels.expectedBehavior}:**
        $expectedBehavior
        
        **${labels.actualBehavior}:**
        $actualBehavior
        
        **Environment:**
        - **App Version:** $appVersion
        - **$osName Version:** $osVersion
        - **Device Model:** $deviceModel
        """.trimIndent()

        return "$NEW_ISSUE_BASE?title=[Bug]%20${UrlCodec.encode(title)}" +
            "&body=${UrlCodec.encode(issueBody)}&labels=bug"
    }
}
