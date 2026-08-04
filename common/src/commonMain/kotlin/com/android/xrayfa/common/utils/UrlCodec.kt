package com.android.xrayfa.common.utils

/**
 * Multiplatform replacement for java.net.URLDecoder / java.net.URLEncoder and the
 * subset of java.net.URI parsing used by the protocol parsers.
 *
 * Behavior was verified case-by-case against OpenJDK 21 (see UrlCodecTest in
 * common/src/test, which asserts parity with the JDK implementation):
 *
 *  - [decode] keeps application/x-www-form-urlencoded semantics: '+' decodes to
 *    a space, malformed '%' escapes throw IllegalArgumentException, and the
 *    decoded bytes are interpreted as UTF-8 with U+FFFD replacement.
 *  - [encode] emits '+' for spaces, passes [A-Za-z0-9.*-_] through untouched and
 *    percent-encodes everything else as uppercase %XX UTF-8 bytes.
 *  - [parseUri] mirrors java.net.URI getters: percent-escapes in
 *    userInfo/query/fragment are decoded ('+' is NOT touched), the constructor-level
 *    rejections (control/space/illegal characters, bad escapes, misplaced '[' ']',
 *    empty authority at end of input, malformed IPv6 literals) surface as
 *    IllegalArgumentException, and authorities that are not "server-based"
 *    (bad hostname, non-numeric port, multiple '@', escapes in host) yield
 *    null userInfo/host with port -1 exactly like the JDK.
 */
object UrlCodec {

    private const val HEX = "0123456789ABCDEF"

    /** Equivalent to java.net.URLDecoder.decode(value, "UTF-8"). */
    fun decode(value: String): String {
        if (!value.contains('%')) return value.replace('+', ' ')
        // Byte mode, same strategy as the JDK: literal characters are UTF-8 encoded
        // into the byte buffer, %XX contributes one raw byte, '+' contributes a space.
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            when (value[i]) {
                '+' -> {
                    bytes.add(0x20)
                    i++
                }
                '%' -> {
                    if (i + 2 > value.length - 1) {
                        throw IllegalArgumentException("URLDecoder: Incomplete trailing escape (%) pattern")
                    }
                    bytes.add(parseEscapeValue(value, i).toByte())
                    i += 3
                }
                else -> {
                    var j = i
                    while (j < value.length && value[j] != '%' && value[j] != '+') j++
                    value.substring(i, j).encodeToByteArray().forEach { bytes.add(it) }
                    i = j
                }
            }
        }
        // Malformed UTF-8 is replaced with U+FFFD, matching the JDK decoder.
        return bytes.toByteArray().decodeToString()
    }

    /** Equivalent to java.net.URLEncoder.encode(value, "UTF-8"). */
    fun encode(value: String): String {
        val out = StringBuilder(value.length)
        var i = 0
        while (i < value.length) {
            val c = value[i]
            when {
                isUnreserved(c) -> {
                    out.append(c)
                    i++
                }
                c == ' ' -> {
                    out.append('+')
                    i++
                }
                else -> {
                    var j = i
                    while (j < value.length && !isUnreserved(value[j]) && value[j] != ' ') j++
                    value.substring(i, j).encodeToByteArray().forEach { b ->
                        out.append('%')
                        out.append(HEX[(b.toInt() shr 4) and 0xF])
                        out.append(HEX[b.toInt() and 0xF])
                    }
                    i = j
                }
            }
        }
        return out.toString()
    }

    /**
     * Equivalent to new java.net.URI(url), restricted to the getters the parsers use.
     * Invalid input throws IllegalArgumentException where the JDK throws
     * URISyntaxException (all call sites catch generic Exception).
     */
    fun parseUri(url: String): ParsedUri {
        for (c in url) checkUriChar(c, url)

        var rest = url

        var fragment: String? = null
        val hashIdx = rest.indexOf('#')
        if (hashIdx >= 0) {
            fragment = rest.substring(hashIdx + 1)
            if (fragment.contains('#')) fail("# is not allowed in a fragment", url)
            validateEscapes(fragment, url)
            rest = rest.substring(0, hashIdx)
        }

        var query: String? = null
        val queryIdx = rest.indexOf('?')
        if (queryIdx >= 0) {
            query = rest.substring(queryIdx + 1)
            validateEscapes(query, url)
            rest = rest.substring(0, queryIdx)
        }

        var scheme: String? = null
        val colonIdx = rest.indexOf(':')
        if (colonIdx > 0 && isSchemeStart(rest[0]) &&
            rest.substring(0, colonIdx).all { isSchemeChar(it) }
        ) {
            scheme = rest.substring(0, colonIdx)
            rest = rest.substring(colonIdx + 1)
        }

        var userInfo: String? = null
        var host: String? = null
        var port = -1
        if (rest.startsWith("//")) {
            val afterSlashes = rest.substring(2)
            // The JDK only rejects a missing authority when nothing at all follows
            // ("socks://" throws, but "socks://#f" / "socks://?q" are accepted).
            if (afterSlashes.isEmpty() && hashIdx < 0 && queryIdx < 0) fail("Expected authority", url)
            val slashIdx = afterSlashes.indexOf('/')
            val authority = if (slashIdx >= 0) afterSlashes.substring(0, slashIdx) else afterSlashes
            val path = if (slashIdx >= 0) afterSlashes.substring(slashIdx) else ""
            if (path.any { it == '[' || it == ']' }) fail("Illegal character in path", url)
            validateEscapes(path, url)
            if (authority.isNotEmpty()) {
                parseAuthority(authority, url)?.let { auth ->
                    userInfo = auth.userInfo
                    host = auth.host
                    port = auth.port
                }
            }
            // An empty authority followed by query/fragment is a registry-based
            // authority in the JDK: all authority getters return null.
        } else {
            // Opaque or relative URI: no authority component.
            if (rest.any { it == '[' || it == ']' }) fail("Illegal character in opaque part", url)
            validateEscapes(rest, url)
        }

        return ParsedUri(
            scheme = scheme,
            userInfo = userInfo?.let { decodeEscapes(it) },
            host = host,
            port = port,
            query = query?.let { decodeEscapes(it) },
            fragment = fragment?.let { decodeEscapes(it) },
        )
    }

    private class Authority(val userInfo: String?, val host: String, val port: Int)

    /** Returns null for a registry-based authority (JDK: getHost()/getUserInfo() -> null, getPort() -> -1). */
    private fun parseAuthority(authority: String, url: String): Authority? {
        // In the JDK, misplaced brackets make authority parsing fail fatally (throw),
        // while a bracket-free multi-'@' authority merely falls back to registry-based.
        val hasBrackets = authority.any { it == '[' || it == ']' }
        if (!hasBrackets && authority.count { it == '@' } > 1) return null
        val atIdx = authority.indexOf('@')
        val userInfo = if (atIdx >= 0) authority.substring(0, atIdx) else null
        val hostPort = if (atIdx >= 0) authority.substring(atIdx + 1) else authority

        if (userInfo != null) {
            if (userInfo.any { it == '[' || it == ']' }) fail("Illegal character in user info", url)
            validateEscapes(userInfo, url)
        }

        if (hostPort.startsWith("[")) {
            // Bracketed IPv6 literal: strict parsing, deviations throw like the JDK.
            val close = hostPort.indexOf(']')
            if (close < 0) fail("Expected closing bracket for IPv6 address", url)
            val literal = hostPort.substring(1, close)
            if (!isValidIpv6Literal(literal)) fail("Malformed IPv6 address", url)
            val tail = hostPort.substring(close + 1)
            val port = when {
                tail.isEmpty() || tail == ":" -> -1
                tail.startsWith(":") -> {
                    val digits = tail.substring(1)
                    if (digits.all { it in '0'..'9' }) {
                        val v = digits.toLongOrNull()
                        if (v == null || v > Int.MAX_VALUE) fail("Illegal character in port", url)
                        v.toInt()
                    } else {
                        fail("Illegal character in port", url)
                    }
                }
                else -> fail("Illegal character after IPv6 address", url)
            }
            // The JDK host getter keeps the square brackets.
            return Authority(userInfo, "[$literal]", port)
        }
        if (hostPort.any { it == '[' || it == ']' }) fail("Illegal character in authority", url)

        val colonIdx = hostPort.indexOf(':')
        val hostCandidate = if (colonIdx >= 0) hostPort.substring(0, colonIdx) else hostPort
        val portCandidate = if (colonIdx >= 0) hostPort.substring(colonIdx + 1) else null

        if (!isValidHostname(hostCandidate)) return null
        val port = when {
            portCandidate == null -> -1
            portCandidate.isEmpty() -> -1
            portCandidate.all { it in '0'..'9' } -> {
                val v = portCandidate.toLongOrNull() ?: return null
                if (v > Int.MAX_VALUE) return null
                v.toInt()
            }
            else -> return null
        }
        return Authority(userInfo, hostCandidate, port)
    }

    /**
     * java.net.URI hostname grammar (RFC 2396 3.2.2 as implemented by the JDK):
     * dot-separated labels of alphanumerics and interior dashes; a single label is
     * accepted as-is; an all-numeric dotted host must be a valid IPv4 address;
     * otherwise the top label must start with a letter. One trailing dot is allowed.
     */
    private fun isValidHostname(host: String): Boolean {
        if (host.isEmpty()) return false
        val hasTrailingDot = host.endsWith(".")
        val core = if (hasTrailingDot) host.dropLast(1) else host
        if (core.isEmpty()) return false
        val labels = core.split('.')
        if (labels.any { !isValidLabel(it) }) return false
        if (labels.size == 1) return true
        if (!hasTrailingDot && labels.all { label -> label.all { it in '0'..'9' } }) {
            return labels.size == 4 && labels.all { octet ->
                octet.length <= 3 && (octet.toIntOrNull() ?: 256) <= 255
            }
        }
        return labels.last().first() in 'a'..'z' || labels.last().first() in 'A'..'Z'
    }

    private fun isValidLabel(label: String): Boolean {
        if (label.isEmpty()) return false
        if (!label.first().isAsciiAlnum() || !label.last().isAsciiAlnum()) return false
        return label.all { it.isAsciiAlnum() || it == '-' }
    }

    /** Compact IPv6 literal validation: hex groups, optional single '::', embedded IPv4, optional %zone. */
    private fun isValidIpv6Literal(literal: String): Boolean {
        var s = literal
        val zoneIdx = s.indexOf('%')
        if (zoneIdx >= 0) {
            val zone = s.substring(zoneIdx + 1)
            if (zone.isEmpty() || zone.contains('%')) return false
            s = s.substring(0, zoneIdx)
        }
        if (s.isEmpty()) return false

        var v4Groups = 0
        if (s.contains('.')) {
            val lastColon = s.lastIndexOf(':')
            if (lastColon < 0) return false
            val octets = s.substring(lastColon + 1).split('.')
            if (octets.size != 4 || octets.any { octet ->
                    octet.isEmpty() || octet.length > 3 ||
                        !octet.all { it in '0'..'9' } || (octet.toIntOrNull() ?: 256) > 255
                }
            ) return false
            // The separator colon is shared with a '::' when the previous char is also ':'.
            s = if (lastColon >= 1 && s[lastColon - 1] == ':') {
                s.substring(0, lastColon + 1)
            } else {
                s.substring(0, lastColon)
            }
            v4Groups = 2
        }

        val halves = s.split("::")
        if (halves.size > 2) return false
        val hasCompression = halves.size == 2
        val left = if (halves[0].isEmpty()) emptyList() else halves[0].split(':')
        val right = if (!hasCompression || halves[1].isEmpty()) {
            emptyList()
        } else {
            halves[1].split(':')
        }
        for (group in left + right) {
            if (group.isEmpty() || group.length > 4 ||
                !group.all { it in '0'..'9' || it in 'a'..'f' || it in 'A'..'F' }
            ) return false
        }
        val total = left.size + right.size + v4Groups
        return if (hasCompression) total < 8 else total == 8
    }

    /** java.net.URI getter decode: %XX escapes -> UTF-8 (U+FFFD on malformed); '+' is NOT special. */
    private fun decodeEscapes(value: String): String {
        if (!value.contains('%')) return value
        val bytes = ArrayList<Byte>(value.length)
        var i = 0
        while (i < value.length) {
            if (value[i] == '%') {
                bytes.add(((hexVal(value[i + 1]) shl 4) + hexVal(value[i + 2])).toByte())
                i += 3
            } else {
                var j = i
                while (j < value.length && value[j] != '%') j++
                value.substring(i, j).encodeToByteArray().forEach { bytes.add(it) }
                i = j
            }
        }
        return bytes.toByteArray().decodeToString()
    }

    private fun validateEscapes(value: String, url: String) {
        var i = 0
        while (i < value.length) {
            if (value[i] == '%') {
                if (i + 2 > value.length - 1 ||
                    hexVal(value[i + 1]) < 0 || hexVal(value[i + 2]) < 0
                ) {
                    fail("Malformed escape pair", url)
                }
                i += 3
            } else {
                i++
            }
        }
    }

    private fun checkUriChar(c: Char, url: String) {
        if (c.isISOControl()) fail("Illegal character (control)", url)
        if (c.code < 0x80) {
            if (c == ' ' || c == '"' || c == '<' || c == '>' ||
                c == '{' || c == '}' || c == '|' || c == '\\' || c == '^' || c == '`'
            ) {
                fail("Illegal character '$c'", url)
            }
        } else {
            val category = c.category
            if (category == CharCategory.SPACE_SEPARATOR ||
                category == CharCategory.LINE_SEPARATOR ||
                category == CharCategory.PARAGRAPH_SEPARATOR
            ) {
                fail("Illegal character (space)", url)
            }
        }
    }

    private fun isUnreserved(c: Char): Boolean =
        c in 'a'..'z' || c in 'A'..'Z' || c in '0'..'9' ||
            c == '.' || c == '-' || c == '*' || c == '_'

    private fun isSchemeStart(c: Char): Boolean = c in 'a'..'z' || c in 'A'..'Z'

    private fun isSchemeChar(c: Char): Boolean =
        isSchemeStart(c) || c in '0'..'9' || c == '+' || c == '-' || c == '.'

    private fun Char.isAsciiAlnum(): Boolean =
        this in 'a'..'z' || this in 'A'..'Z' || this in '0'..'9'

    /**
     * Mimics Integer.parseInt(twoChars, 16) as used (and leaked) by the JDK URLDecoder:
     * an optional leading '+'/'-' sign followed by a single hex digit is accepted,
     * Unicode full-width digits/letters count as digits, and a negative result throws.
     */
    private fun parseEscapeValue(s: String, percentIdx: Int): Int {
        val hi = s[percentIdx + 1]
        val lo = s[percentIdx + 2]
        val hiDigit = hi.digitToIntOrNull(16) ?: -1
        if (hiDigit >= 0) {
            val loDigit = lo.digitToIntOrNull(16) ?: -1
            if (loDigit < 0) illegalEscape(s)
            return (hiDigit shl 4) + loDigit
        }
        if (hi == '+' || hi == '-') {
            val loDigit = lo.digitToIntOrNull(16) ?: -1
            if (loDigit < 0) illegalEscape(s)
            if (hi == '-' && loDigit > 0) illegalEscape(s)
            return loDigit
        }
        illegalEscape(s)
    }

    private fun illegalEscape(s: String): Nothing =
        throw IllegalArgumentException("URLDecoder: Illegal hex characters in escape (%) pattern: $s")

    private fun hexVal(c: Char): Int = when (c) {
        in '0'..'9' -> c - '0'
        in 'a'..'f' -> c - 'a' + 10
        in 'A'..'F' -> c - 'A' + 10
        else -> -1
    }

    private fun fail(reason: String, url: String): Nothing =
        throw IllegalArgumentException("$reason: $url")
}

/**
 * The subset of java.net.URI state used by the protocol parsers. Component values
 * follow the JDK decoded getters (percent-escapes decoded, '+' untouched); [port]
 * is -1 when absent or when the authority is not server-based.
 */
class ParsedUri(
    val scheme: String?,
    val userInfo: String?,
    val host: String?,
    val port: Int,
    val query: String?,
    val fragment: String?,
)
