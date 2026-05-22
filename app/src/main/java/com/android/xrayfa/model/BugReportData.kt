package com.android.xrayfa.model

data class BugReportData(
    val title: String,
    val description: String,
    val expectedBehavior: String,
    val actualBehavior: String
)
