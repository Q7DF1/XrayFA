package com.android.xrayfa.common.utils

import kotlin.io.encoding.Base64
import kotlin.io.encoding.ExperimentalEncodingApi

/**
 * Multiplatform replacement for java.util.Base64 / android.util.Base64.
 *
 * Decode semantics were verified case-by-case against the JDK basic decoder:
 * strict alphabet (rejects '\n', '-', '_' and other illegal symbols) while
 * tolerating missing trailing padding — identical to java.util.Base64.getDecoder().
 */
@OptIn(ExperimentalEncodingApi::class)
object Base64Compat {

    /** Equivalent to java.util.Base64.getDecoder() (strict alphabet, tolerant of missing padding). */
    private val javaCompatDecoder = Base64.Default.withPadding(Base64.PaddingOption.ABSENT_OPTIONAL)

    /** Equivalent to java.util.Base64.getUrlEncoder().withoutPadding() / android.util.Base64 URL_SAFE|NO_PADDING. */
    private val urlSafeNoPadding = Base64.UrlSafe.withPadding(Base64.PaddingOption.ABSENT)

    /** Equivalent to java.util.Base64.getDecoder().decode(String). */
    fun decode(value: String): ByteArray = javaCompatDecoder.decode(value)

    /** Equivalent to java.util.Base64.getEncoder().encodeToString(ByteArray) (standard alphabet, padded). */
    fun encode(value: ByteArray): String = Base64.Default.encode(value)

    /** Equivalent to android.util.Base64.encodeToString(bytes, URL_SAFE | NO_PADDING)
     * and java.util.Base64.getUrlEncoder().withoutPadding().encodeToString(bytes). */
    fun encodeUrlSafeNoPadding(value: ByteArray): String = urlSafeNoPadding.encode(value)
}
