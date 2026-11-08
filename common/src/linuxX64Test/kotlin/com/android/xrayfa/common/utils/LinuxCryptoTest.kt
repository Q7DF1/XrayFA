package com.android.xrayfa.common.utils

import kotlin.test.*

class LinuxCryptoTest {
    @Test fun randomBoundsAndEmptyBuffer() {
        defaultCryptoRandom.nextBytes(ByteArray(0))
        val data=ByteArray(32);defaultCryptoRandom.nextBytes(data)
        assertTrue(data.any {it!=0.toByte()})
        for(bound in listOf(1,2,3,257,Int.MAX_VALUE)) repeat(1000) {
            assertTrue(defaultCryptoRandom.nextInt(bound) in 0 until bound)
        }
        assertFails {defaultCryptoRandom.nextInt(0)}
    }
    @Test fun digestRejectsInvalidRangesAndReuse() {
        val digest=LinuxDigest.create("SHA-256")
        assertFails {digest.update(byteArrayOf(1),0,2)}
        assertFails {digest.update(byteArrayOf(1),-1,1)}
        digest.update(byteArrayOf(1,2,3),1,1)
        assertContentEquals(LinuxDigest.create("SHA-256").also{it.update(byteArrayOf(2),0,1)}.finalize(),digest.finalize())
        assertFails {digest.finalize()}
        assertFails {digest.update(byteArrayOf(1),0,1)}
    }
}
