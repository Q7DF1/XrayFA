package com.android.xrayfa.common.utils

import org.junit.Assert.assertEquals
import org.junit.Test
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * Parity test: UrlCodec / parseUri must match the JDK reference implementation
 * (java.net.URLDecoder / URLEncoder / URI) on every edge case the protocol
 * parsers depend on. The case list was captured empirically from OpenJDK 21;
 * see docs/KMP_MIGRATION_STEP3_HANDOVER.md.
 */
class UrlCodecTest {

    // region decode

    private val decodeCases = listOf(
        "",
        "hello+world",
        "hello%20world",
        "+",
        "%2b",
        "%2B",
        "a+b%2Bc",
        "%E4%B8%AD%E6%96%87",
        "%41%42",
        "100%",
        "%zz",
        "%2",
        "a%b",
        "%ff%fe",
        "%c3%28",
        "%E4%B8%AD",
        "a+b+c",
        "%0A%0D",
        "%25",
        "%%41",
        "caf%C3%A9",
        "%F0%9F%9A%80",
        "中文",
    )

    @Test
    fun decode_matchesJdk() {
        for (case in decodeCases) {
            val expected = runCatching { URLDecoder.decode(case, "UTF-8") }
            val actual = runCatching { UrlCodec.decode(case) }
            assertEquals("decode [$case] throw-state", expected.isFailure, actual.isFailure)
            if (expected.isSuccess) {
                assertEquals("decode [$case]", expected.getOrThrow(), actual.getOrThrow())
            }
        }
    }

    // endregion

    // region encode

    private val encodeCases = listOf(
        "",
        "hello world",
        "中文",
        "a*b-c.d_e",
        "~",
        "+",
        "a+b",
        "\n",
        "\r\n",
        "%",
        "#",
        "&",
        "=",
        "?",
        "/path",
        "@",
        ":",
        "!()",
        "café",
        "🚀", // 🚀
        "tag with space & 中文 #1",
    )

    @Test
    fun encode_matchesJdk() {
        for (case in encodeCases) {
            val expected = URLEncoder.encode(case, "UTF-8")
            val actual = UrlCodec.encode(case)
            assertEquals("encode [$case]", expected, actual)
        }
    }

    // endregion

    // region parseUri

    private val uriCases = listOf(
        "socks://user:pass@host.com:1080#remark",
        "socks://host.com#remark",
        "socks://host.com:1080#",
        "socks://host.com:1080",
        "socks://[::1]:1080#r",
        "socks://[2001:db8::1]:1080",
        "socks://host:abc",
        "socks://host:12ab",
        "socks://ho_st:1080",
        "trojan://pass@host:443?type=ws&path=%2F#r",
        "socks://u@u2@host:1080",
        "socks://user:p@ss@host:1080",
        "socks://host:1080/#r",
        "socks://host:1080?x=1#r",
        "trojan://p@h:443#hello world",
        "trojan://p@h:443#中文",
        "trojan://p@h:443?a=b#f1#f2",
        "socks://HOST.COM:1080",
        "socks://host:99999",
        "socks://host:",
        "socks://:1080",
        "socks://user@:1080",
        "socks://",
        "socks://user@host",
        "trojan://密码@host:443",
        "",
        "notauri",
        "socks://host:1080?type=ws&security=tls#rem ark",
        "socks5://u:p@h:1#%E4%B8%AD%E6%96%87",
        "socks://h:1#tag with space",
        "socks://h:1/%2F",
        "socks://%75ser@host:1080",
        "socks://host.com.:1080",
        "socks://192.168.1.1:1080",
        "socks://user:pass@host:1080?",
        "socks://user:pass@host:1080?#",
        "socks://[::1]",
        "socks://[]:1080",
        "socks://host:0",
        "socks://h:1#%zz",
        "socks://h:1#a+b",
        "socks://h:1?a=b+c",
        "socks://h:1?a=%zz",
        "socks://h:1?a=%",
        "socks://u%40ser:p@host:1080",
        "socks://h%6Fst:1080",
        "socks://%75:p@h%6Fst:1080",
        "socks://h:1#%41",
        "socks://h:1#%c3",
        "socks://h:1#%ff",
        "SOCKS://h:1#r",
        "Trojan://P@H:443",
        "socks://h:1#a[b",
        "socks://h:1?a=[",
        "socks://[h]:1",
        "socks://user@",
        "socks://user@host:1080/extra?x=1#f",
        "socks://h:1?q#",
        "socks://h:1##",
        "socks://h:1#f?x",
        "socks://h:1?x#y?z",
        "socks://-host-:1080",
        "socks://h..com:1080",
        "socks://.com:1080",
        "socks://com.:1080",
        "socks://1host:1080",
        "socks://123:1080",
        "socks://1.2.3.4.5:1080",
        "socks://user:p@ss@h:1#f",
        "socks://:p@h:1",
        "socks://u:@h:1",
        "socks://u:p:q@h:1",
        "socks://h:65536",
        "socks://h:2147483647",
        "socks://h:2147483648",
        "socks://h:+1080",
        "socks://h: 1080",
        "socks://h\t:1",
        "socks://h:1#x y",
        "socks://h:1#x y",
        "socks://a.1:1080",
        "socks://example.123:1080",
        "socks://1.2.3:1080",
        "socks://1.2.3.4:1080",
        "socks://999.1.1.1:1080",
        "socks://256.1.1.1:1080",
        "socks://1.2.3.04:1080",
        "socks://1.2.3.4.:1080",
        "socks://a.b.1:1080",
        "socks://12a.34b:1080",
        "socks://h-:1080",
        "socks://-h:1080",
        "socks://h-1:1080",
        "socks://H_ost.com:1080",
        "socks://u[:p@h:1",
        "socks://u]:p@h:1",
        "socks://u;p@h:1",
        "socks://u&p@h:1",
        "socks://u,p@h:1",
        "socks://u=p@h:1",
        "socks://u+p@h:1",
        "socks://u\$p@h:1",
        "socks://u/p@h:1",
        "socks://u?p@h:1",
        "socks://u!p@h:1",
        "socks://u~p@h:1",
        "socks://u'p@h:1",
        "socks://u(p)@h:1",
        "socks://h:1:2",
        "socks://h::1",
        "socks://h%20:1",
        "socks://a.b1:1080",
        "socks://1a.b:1080",
        "socks://123.:1080",
        "socks://h:1/a[b",
        "socks://h:1/a]b",
        "socks://#f",
        "socks://?q",
        "trojan:foo",
        "trojan:foo#bar",
        "socks://[::1]:abc",
        "socks://[::1]x",
        "socks://u@[::1]:1080",
        "socks://u@[::1]",
        "socks://[::1]:",
        "socks://[1:2:3:4:5:6:7:8:9]:1",
        "socks://[::ffff:1.2.3.4]:1",
        "socks://[fe80::1%eth0]:1",
        "socks://u%3Ap@h:1",
        "socks://h:1?#f",
        "socks://H:1#%20",
        "socks://h:1/%61",
        "vless://uuid@server.com:443?type=ws&security=tls#remark",
        "hysteria2://auth@server.com:443?insecure=1#r",
    )

    @Test
    fun parseUri_matchesJdk() {
        for (case in uriCases) {
            val jdk = runCatching { URI(case) }
            val actual = runCatching { UrlCodec.parseUri(case) }
            assertEquals("parseUri [$case] throw-state", jdk.isFailure, actual.isFailure)
            if (jdk.isSuccess) {
                val u = jdk.getOrThrow()
                val p = actual.getOrThrow()
                assertEquals("parseUri [$case] scheme", u.scheme, p.scheme)
                assertEquals("parseUri [$case] userInfo", u.userInfo, p.userInfo)
                assertEquals("parseUri [$case] host", u.host, p.host)
                assertEquals("parseUri [$case] port", u.port, p.port)
                assertEquals("parseUri [$case] query", u.query, p.query)
                assertEquals("parseUri [$case] fragment", u.fragment, p.fragment)
            }
        }
    }

    // endregion

    // region fuzz parity over a URI-symbol alphabet

    /**
     * Exhaustive parity check over every string of length 1..3 built from URI-significant
     * symbols (~3000 strings). Catches interactions the hand-picked cases missed.
     */
    @Test
    fun fuzz_matchesJdk() {
        val alphabet = listOf('a', '1', '%', '+', ' ', ':', '/', '@', '#', '?', '[', ']', '.', '-', '_', '中', '４', 'ｆ')
        val inputs = ArrayList<String>()
        for (a in alphabet) {
            inputs.add(a.toString())
            for (b in alphabet) {
                inputs.add("$a$b")
                for (c in alphabet) {
                    inputs.add("$a$b$c")
                }
            }
        }
        var decodeChecked = 0
        var parseChecked = 0
        for (s in inputs) {
            val expectedDec = runCatching { URLDecoder.decode(s, "UTF-8") }
            val actualDec = runCatching { UrlCodec.decode(s) }
            assertEquals("decode [$s] throw-state", expectedDec.isFailure, actualDec.isFailure)
            if (expectedDec.isSuccess) assertEquals("decode [$s]", expectedDec.getOrThrow(), actualDec.getOrThrow())
            decodeChecked++

            assertEquals("encode [$s]", URLEncoder.encode(s, "UTF-8"), UrlCodec.encode(s))

            val prefixed = "socks://u:p@$s:1080/p?q=$s#f$s"
            val jdk = runCatching { URI(prefixed) }
            val actual = runCatching { UrlCodec.parseUri(prefixed) }
            assertEquals("parseUri [$prefixed] throw-state", jdk.isFailure, actual.isFailure)
            if (jdk.isSuccess) {
                val u = jdk.getOrThrow()
                val p = actual.getOrThrow()
                assertEquals("parseUri [$prefixed] userInfo", u.userInfo, p.userInfo)
                assertEquals("parseUri [$prefixed] host", u.host, p.host)
                assertEquals("parseUri [$prefixed] port", u.port, p.port)
                assertEquals("parseUri [$prefixed] query", u.query, p.query)
                assertEquals("parseUri [$prefixed] fragment", u.fragment, p.fragment)
            }
            parseChecked++
        }
        assertEquals(alphabet.size + alphabet.size * alphabet.size + alphabet.size * alphabet.size * alphabet.size, decodeChecked)
        assertEquals(decodeChecked, parseChecked)
    }

    // endregion

    // region parser-shaped end-to-end sanity

    @Test
    fun typicalShareLinks() {
        val socks = UrlCodec.parseUri("socks://user:pass@host.com:1080#%E5%A4%87%E6%B3%A8")
        assertEquals("host.com", socks.host)
        assertEquals(1080, socks.port)
        assertEquals("user:pass", socks.userInfo)
        assertEquals("备注", UrlCodec.decode(socks.fragment ?: error("fragment")))

        val trojan = UrlCodec.parseUri("trojan://p%40ss@host:443?type=ws&path=%2Fws#r")
        assertEquals("p@ss", UrlCodec.decode(trojan.userInfo ?: error("userInfo")))
        assertEquals("host", trojan.host)
        assertEquals("type=ws&path=/ws", trojan.query)
    }

    // endregion
}
