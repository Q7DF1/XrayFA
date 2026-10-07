package hev.htproxy

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import xrayfa.tun2socks.TProxyService

@RunWith(AndroidJUnit4::class)
class TProxyJniTest {
    @Test
    fun nativeLibraryRegistersAndStopsWithoutStartingVpn() {
        // Initializes the class and exercises JNI_OnLoad/RegisterNatives on device.
        TProxyService.TProxyStopService()
        assertEquals(
            Boolean::class.javaPrimitiveType,
            TProxyService::class.java.getDeclaredMethod(
                "TProxyStartService", String::class.java, Int::class.javaPrimitiveType!!,
            ).returnType,
        )
    }
}
