package com.android.xrayfa.network

import com.android.xrayfa.common.core.parseIpLiteral
import java.io.DataInputStream
import java.io.DataOutputStream
import java.net.InetSocketAddress
import java.net.Socket

/** Standard DNS-over-TCP framing through local SOCKS; Xray handles port 53 with its DNS module. */
fun exchangeDnsThroughSocks(server: String, query: ByteArray, config: SocksProxyConfig): ByteArray {
    val target = requireNotNull(parseIpLiteral(server)) { "A literal DNS server IP is required" }
    require(query.size in 12..65535)
    val user = config.socksUserName.encodeToByteArray()
    val secret = config.socksPassword.encodeToByteArray()
    require(user.size <= 255 && secret.size <= 255)
    return Socket().use { socket ->
        socket.soTimeout = 4_000
        socket.connect(InetSocketAddress("127.0.0.1", config.socksPort), 4_000)
        val input = DataInputStream(socket.getInputStream())
        val output = DataOutputStream(socket.getOutputStream())
        output.write(byteArrayOf(5, 1, if (user.isEmpty()) 0 else 2))
        output.flush()
        check(input.readUnsignedByte() == 5) { "Invalid SOCKS version" }
        val method = input.readUnsignedByte()
        if (method == 2 && user.isNotEmpty()) {
            output.writeByte(1)
            output.writeByte(user.size)
            output.write(user)
            output.writeByte(secret.size)
            output.write(secret)
            output.flush()
            check(input.readUnsignedByte() == 1 && input.readUnsignedByte() == 0) { "SOCKS authentication failed" }
        } else {
            check(method == 0 && user.isEmpty()) { "SOCKS authentication unavailable" }
        }
        output.write(byteArrayOf(5, 1, 0, if (target.size == 4) 1 else 4))
        output.write(target)
        output.writeShort(53)
        output.flush()
        check(input.readUnsignedByte() == 5 && input.readUnsignedByte() == 0) { "SOCKS DNS connection failed" }
        check(input.readUnsignedByte() == 0)
        val addressLength = when (input.readUnsignedByte()) {
            1 -> 4
            4 -> 16
            3 -> input.readUnsignedByte()
            else -> error("Invalid SOCKS address type")
        }
        input.readFully(ByteArray(addressLength + 2))
        output.writeShort(query.size)
        output.write(query)
        output.flush()
        val length = input.readUnsignedShort()
        require(length >= 12) { "Invalid DNS response length" }
        ByteArray(length).also(input::readFully)
    }
}
