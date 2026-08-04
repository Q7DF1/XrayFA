package com.android.xrayfa.model.stream

data class RawSettings(
    val acceptProxyProtocol: Boolean? = null,
    val header: HeaderObject = NoneHeaderObject()
)


abstract class HeaderObject{}
data class NoneHeaderObject(
    val type: String = "none"
):HeaderObject()

data class HttpHeaderObject(
    val type: String = "http",
    val request: HttpRequestObject? = null,
    val response: HttpResponseObject? = null
):HeaderObject()


data class HttpRequestObject(
    val version: String = "1.1",
    val method: String = "GET",
    val path: List<String> = listOf("/"),
    val headers: Map<String, List<String>> = mapOf(
        "Host" to listOf(""),
        "User-Agent" to listOf(
            "Mozilla/5.0 (Windows NT 10.0; WOW64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/53.0.2785.143 Safari/537.36",
            "Mozilla/5.0 (iPhone; CPU iPhone OS 10_0_2 like Mac OS X) AppleWebKit/601.1 (KHTML, like Gecko) CriOS/53.0.2785.109 Mobile/14A456 Safari/601.1.46"
        ),
        "Accept-Encoding" to listOf("gzip, deflate"),
        "Connection" to listOf("keep-alive"),
        "Pragma" to listOf("no-cache")
    )
)



data class HttpResponseObject(
    val version: String = "1.1",
    val status: String = "200",
    val reason: String = "OK",
    val headers: Map<String, List<String>> = mapOf(
        "Content-Type" to listOf("application/octet-stream", "video/mpeg"),
        "Transfer-Encoding" to listOf("chunked"),
        "Connection" to listOf("keep-alive"),
        "Pragma" to listOf("no-cache")
    )
)

