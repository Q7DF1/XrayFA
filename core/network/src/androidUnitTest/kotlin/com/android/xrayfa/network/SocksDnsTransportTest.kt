package com.android.xrayfa.network

import com.android.xrayfa.common.core.DohDnsMessage
import com.android.xrayfa.common.core.parseIpLiteral
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetAddress
import java.net.ServerSocket
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class SocksDnsTransportTest {
    private val query = DohDnsMessage.query("node.example", 1)
    private val reply = query.copyOf().also { it[2] = 0x81.toByte(); it[3] = 0x80.toByte() }
    private val dummySecret = charArrayOf('t', 'e', 's', 't').concatToString()

    private fun checkTransport(serverIp: String, authenticated: Boolean) {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val peer = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    val input = DataInputStream(socket.getInputStream())
                    val output = DataOutputStream(socket.getOutputStream())
                    assertEquals(5, input.readUnsignedByte())
                    assertEquals(1, input.readUnsignedByte())
                    assertEquals(if (authenticated) 2 else 0, input.readUnsignedByte())
                    output.write(byteArrayOf(5, if (authenticated) 2 else 0))
                    output.flush()
                    if (authenticated) {
                        assertEquals(1, input.readUnsignedByte())
                        val user = ByteArray(input.readUnsignedByte()).also(input::readFully)
                        val secret = ByteArray(input.readUnsignedByte()).also(input::readFully)
                        assertEquals("test-user", user.decodeToString())
                        assertEquals(dummySecret, secret.decodeToString())
                        output.write(byteArrayOf(1, 0))
                        output.flush()
                    }
                    val target = requireNotNull(parseIpLiteral(serverIp))
                    assertEquals(5, input.readUnsignedByte())
                    assertEquals(1, input.readUnsignedByte())
                    assertEquals(0, input.readUnsignedByte())
                    assertEquals(if (target.size == 4) 1 else 4, input.readUnsignedByte())
                    assertArrayEquals(target, ByteArray(target.size).also(input::readFully))
                    assertEquals(53, input.readUnsignedShort())
                    output.write(byteArrayOf(5, 0, 0, 1, 127, 0, 0, 1, 0, 0))
                    output.flush()
                    assertArrayEquals(query, ByteArray(input.readUnsignedShort()).also(input::readFully))
                    output.writeShort(reply.size)
                    // Exercise readFully with a fragmented DNS response.
                    reply.forEach { output.writeByte(it.toInt()); output.flush() }
                }
            }
            try {
                val config = SocksProxyConfig(server.localPort, if (authenticated) "test-user" else "", if (authenticated) dummySecret else "")
                assertArrayEquals(reply, exchangeDnsThroughSocks(serverIp, query, config))
                peer.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }

    @Test fun ordinaryDnsUsesSocksAndTcpFraming() = checkTransport("8.8.8.8", false)
    @Test fun supportsIpv6UpstreamAndSocksAuthentication() = checkTransport("2001:4860:4860::8888", true)

    @Test
    fun socksRejectionDoesNotFallBackToDirectDns() {
        ServerSocket(0, 1, InetAddress.getByName("127.0.0.1")).use { server ->
            val executor = Executors.newSingleThreadExecutor()
            val peer = executor.submit {
                server.accept().use { socket ->
                    socket.soTimeout = 2_000
                    DataInputStream(socket.getInputStream()).readFully(ByteArray(3))
                    socket.getOutputStream().write(byteArrayOf(5, 0xff.toByte()))
                }
            }
            try {
                assertThrows(IllegalStateException::class.java) {
                    exchangeDnsThroughSocks("192.0.2.1", query, SocksProxyConfig(server.localPort, "", ""))
                }
                peer.get(5, TimeUnit.SECONDS)
            } finally {
                executor.shutdownNow()
            }
        }
    }
}
