package ch.lostkeyfinder

import ch.lostkeyfinder.identity.normalizeEmail
import org.junit.jupiter.api.Test
import kotlin.test.assertEquals
import kotlin.test.assertFailsWith

class EmailNormalizationTest {
    @Test fun `email normalization lowercases trims and preserves nontransitional IDNs`() {
        for ((raw, expected) in mapOf(
            " Owner+Tag@Example.COM " to "owner+tag@example.com",
            "A@BÜCHER.DE" to "a@xn--bcher-kva.de",
            "A@FAß.DE" to "a@xn--fa-hia.de",
            "a@localhost" to "a@localhost",
            "a@127.1" to "a@127.0.0.1",
            "a@2130706433" to "a@127.0.0.1",
            "a@0x7f.1" to "a@127.0.0.1",
            "a@0127.1" to "a@87.0.0.1",
            "a@127.0.0.1." to "a@127.0.0.1",
            "a@example.0xzz" to "a@example.0xzz",
            "a!#$%&'*+/=?^_{}|~-@example.com" to "a!#$%&'*+/=?^_{}|~-@example.com",
        )) {
            assertEquals(expected, normalizeEmail(raw))
        }
    }

    @Test fun `invalid addresses and label boundaries are rejected`() {
        for (raw in listOf(
            "",
            "a",
            "@example.com",
            "a@@example.com",
            ".a@example.com",
            "a.@example.com",
            "a..b@example.com",
            "a b@example.com",
            "ü@example.com",
            "a@",
            "a@-example.com",
            "a@example-.com",
            "a@example..com",
            "a@example.com.",
            "a@exa_mple.com",
            "a@127.0.0.999",
            "a@a.123",
            "a@1.2.3.4.5",
            "a@09",
            "a@0xffffffffff",
            "a@0xffffffffffffffff",
            "\u001Ca@example.com",
            "a@${"x".repeat(64)}.com",
            "${"a".repeat(65)}@example.com",
            "${"a".repeat(64)}@${"x".repeat(63)}.${"x".repeat(63)}.${"x".repeat(63)}.com",
        )) {
            assertFailsWith<IllegalArgumentException>(raw) { normalizeEmail(raw) }
        }
    }
}
