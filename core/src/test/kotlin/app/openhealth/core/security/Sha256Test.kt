package app.openhealth.core.security

import kotlin.test.Test
import kotlin.test.assertEquals

/** The NIST vectors, so the MCP token digest cannot drift unnoticed. */
class Sha256Test {
    @Test
    fun theEmptyStringHasThePublishedDigest() {
        assertEquals(
            "e3b0c44298fc1c149afbf4c8996fb92427ae41e4649b934ca495991b7852b855",
            Sha256.hexDigest(""),
        )
    }

    @Test
    fun abcHasThePublishedDigest() {
        assertEquals(
            "ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad",
            Sha256.hexDigest("abc"),
        )
    }

    @Test
    fun aMultiBlockMessageUsesLengthPadding() {
        assertEquals(
            "248d6a61d20638b8e5c026930c3e6039a33ce45964ff2167f6ecedd419db06c1",
            Sha256.hexDigest(
                "abcdbcdecdefdefgefghfghighijhijkijkljklmklmnlmnomnopnopq",
            ),
        )
    }

    @Test
    fun thePaddingBoundaryAt55And119BytesMatchesTheJvm() {
        listOf(55, 56, 119, 120).forEach { size ->
            val input = ByteArray(size) { (it % 0x20 + 0x41).toByte() }
            val expected = java.security.MessageDigest.getInstance("SHA-256")
                .digest(input)
                .joinToString("") { "%02x".format(it) }
            assertEquals(expected, Sha256.hex(Sha256.digest(input)), "length $size")
        }
    }

    @Test
    fun aDifferentInputGivesADifferentDigest() {
        val first = Sha256.hexDigest("openhealth-token-a")
        val second = Sha256.hexDigest("openhealth-token-b")
        assertEquals(64, first.length)
        assertEquals(false, first == second)
    }
}
