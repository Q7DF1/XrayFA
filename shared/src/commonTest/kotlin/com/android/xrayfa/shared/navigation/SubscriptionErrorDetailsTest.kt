package com.android.xrayfa.shared.navigation

import com.android.xrayfa.network.SubscriptionFetchException
import com.android.xrayfa.repository.SubscriptionFailure
import com.android.xrayfa.repository.SubscriptionParseFailure
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse

class SubscriptionErrorDetailsTest {
    @Test
    fun parseContextDoesNotCopyUnsupportedProtocolOrParameters() {
        val result = subscriptionErrorDetails(SubscriptionFailure(
            SubscriptionFailure.Stage.PARSE,
            SubscriptionParseFailure("parse_node", 3, "token=private-value", IllegalArgumentException(
                "Unsupported transport: private-value; https://example.invalid/?token=private-value",
            )),
        ))
        assertEquals("step=parse_node, node=3, protocol=unknown", result.parseContext)
        assertEquals("Unsupported transport", result.validationReason)
        assertFalse(result.toString().contains("private-value"))
    }

    @Test
    fun multilineExceptionMessageCannotInjectFakeStackFrames() {
        val result = subscriptionErrorDetails(IllegalArgumentException(
            "invalid URL\n\tat secret.privatevalue.method(SourceFile:1)",
        ))
        assertFalse(result.toString().contains("privatevalue"))
    }

    @Test
    fun subscriptionDecodeFailureIsDistinctFromNodeParsing() {
        val result = subscriptionErrorDetails(SubscriptionFailure(
            SubscriptionFailure.Stage.PARSE,
            SubscriptionParseFailure("decode_subscription", null, null, IllegalArgumentException("Invalid base64")),
        ))
        assertEquals("step=decode_subscription, node=null, protocol=unknown", result.parseContext)
    }

    @Test
    fun httpStatusSurvivesWrapping() {
        val result = subscriptionErrorDetails(SubscriptionFailure(
            SubscriptionFailure.Stage.FETCH, SubscriptionFetchException("HTTP error: 403"),
        ))
        assertEquals(SubscriptionErrorKind.HTTP, result.kind)
        assertEquals(403, result.httpStatus)
    }

    @Test
    fun errorMessagesAndAuthenticatedUrlsAreNotExposed() {
        val result = subscriptionErrorDetails(SubscriptionFailure(
            SubscriptionFailure.Stage.PARSE,
            IllegalArgumentException("https://example.invalid/?token=private-value"),
        ))
        assertEquals(SubscriptionErrorKind.PARSE, result.kind)
        assertFalse(result.toString().contains("private-value"))
        assertFalse(result.toString().contains("example.invalid"))
    }

    @Test
    fun arbitraryHttpErrorMessageIsNotForwarded() {
        val result = subscriptionErrorDetails(SubscriptionFailure(
            SubscriptionFailure.Stage.FETCH, SubscriptionFetchException("HTTP error: 403 token=private-value"),
        ))
        assertEquals(SubscriptionErrorKind.NETWORK, result.kind)
        assertEquals(null, result.httpStatus)
        assertFalse(result.toString().contains("private-value"))
    }

    @Test
    fun nestedTimeoutAndTlsCausesAreRecognized() {
        assertEquals(SubscriptionErrorKind.TIMEOUT, subscriptionErrorDetails(Exception(SocketTimeoutException())).kind)
        assertEquals(SubscriptionErrorKind.TLS, subscriptionErrorDetails(Exception(SSLHandshakeException())).kind)
    }
}

private class SocketTimeoutException : Exception()
private class SSLHandshakeException : Exception()
