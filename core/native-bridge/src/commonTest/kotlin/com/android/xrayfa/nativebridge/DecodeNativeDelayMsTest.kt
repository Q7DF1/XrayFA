package com.android.xrayfa.nativebridge

import kotlin.test.Test
import kotlin.test.assertEquals

class DecodeNativeDelayMsTest {

    @Test
    fun failure_returnsMinusOne() {
        assertEquals(-1L, decodeNativeDelayMs(ok = false, delayMs = 42L))
    }

    @Test
    fun success_returnsNativeValue() {
        assertEquals(128L, decodeNativeDelayMs(ok = true, delayMs = 128L))
        assertEquals(0L, decodeNativeDelayMs(ok = true, delayMs = 0L))
    }
}
