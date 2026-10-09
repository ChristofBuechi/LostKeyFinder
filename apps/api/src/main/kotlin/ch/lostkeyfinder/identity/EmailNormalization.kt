package ch.lostkeyfinder.identity

import com.ibm.icu.text.IDNA
import java.util.Locale

private val localPartPattern = Regex("[a-z0-9.!#$%&'*+/=?^_{}|~-]+")
private val domainLabelPattern = Regex("[a-z0-9](?:[a-z0-9-]{0,61}[a-z0-9])?")
private val ipv4NumberPattern = Regex("(?:0x[0-9a-f]*|0[0-7]*|[1-9][0-9]*)")
private val emailWhitespace = " \t\n\u000B\u000C\r\u00A0\u1680\u2028\u2029\u202F\u205F\u3000\uFEFF"

// ponytail: ICU supplies UTS #46; java.net.IDN would incorrectly turn faß.de into fass.de.
private val idna = IDNA.getUTS46Instance(IDNA.NONTRANSITIONAL_TO_ASCII or IDNA.CHECK_BIDI or IDNA.CHECK_CONTEXTJ)

fun normalizeEmail(input: String): String {
    val address = input.trim { it in emailWhitespace || it in '\u2000'..'\u200A' }.lowercase(Locale.ROOT)
    require(address.count { it == '@' } == 1) { "Invalid email" }
    val (local, domain) = address.split('@')
    require(
        local.length in 1..64 && localPartPattern.matches(local) && !local.startsWith('.') && !local.endsWith('.') && !local.contains(".."),
    ) {
        "Invalid email"
    }
    val info = IDNA.Info()
    val ascii = idna.nameToASCII(domain, StringBuilder(), info).toString()
    // Node's domainToASCII ignores DNS lengths until after WHATWG IPv4 canonicalization.
    require((info.errors - setOf(IDNA.Error.LABEL_TOO_LONG, IDNA.Error.DOMAIN_NAME_TOO_LONG)).isEmpty()) { "Invalid email" }
    val asciiDomain = canonicalizeNumericHost(ascii)
    require(
        asciiDomain.length in 1..253 && asciiDomain.split('.').all(domainLabelPattern::matches),
    ) { "Invalid email" }
    return "$local@$asciiDomain".also { require(it.length <= 254) { "Invalid email" } }
}

private fun canonicalizeNumericHost(domain: String): String {
    val parts = domain.removeSuffix(".").split('.')
    val last = parts.last()
    if (!ipv4NumberPattern.matches(last) && !(last.isNotEmpty() && last.all { it in '0'..'9' })) return domain
    require(parts.size <= 4) { "Invalid email" }
    val numbers =
        parts.map { part ->
            require(ipv4NumberPattern.matches(part)) { "Invalid email" }
            val (radix, digits) =
                when {
                    part.startsWith("0x") -> 16 to part.drop(2)
                    part.startsWith('0') -> 8 to part.drop(1)
                    else -> 10 to part
                }
            requireNotNull(digits.trimStart('0').ifEmpty { "0" }.toLongOrNull(radix)) { "Invalid email" }
        }
    require(numbers.dropLast(1).all { it <= 255 } && numbers.last() < (1L shl (8 * (5 - parts.size)))) { "Invalid email" }
    val address = numbers.dropLast(1).foldIndexed(numbers.last()) { index, sum, number -> sum + (number shl (8 * (3 - index))) }
    return (0..3).joinToString(".") { ((address ushr (8 * (3 - it))) and 255).toString() }
}
