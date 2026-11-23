package app.openhealth.core.ids

import kotlin.random.Random

private const val BYTE_COUNT = 16
private const val UUID_LENGTH = 36
private const val VERSION_BYTE = 6
private const val VARIANT_BYTE = 8
private const val LOW_NIBBLE = 0x0F
private const val VERSION_4 = 0x40
private const val VARIANT_MASK = 0x3F
private const val VARIANT_RFC4122 = 0x80
private const val NIBBLE_SHIFT = 4
private const val HEX_RADIX = 16
private const val NOISE_RANGE = 0x10000
private const val SUFFIX_LENGTH = 12
private const val NOISE_LENGTH = 4

/** Source of new object identifiers. Deterministic implementations exist for tests. */
fun interface IdGenerator {
    fun newId(): String
}

/**
 * RFC 4122 version 4 identifiers from a [Random], formatted with the canonical
 * hyphenated lowercase shape. Implemented here rather than through `java.util.UUID`
 * so the module stays platform-neutral (`Tech-Stack.md` §3).
 */
class RandomIdGenerator(private val random: Random = Random.Default) : IdGenerator {
    override fun newId(): String {
        val bytes = ByteArray(BYTE_COUNT).also(random::nextBytes)
        // Version 4 and the RFC 4122 variant bits.
        bytes[VERSION_BYTE] = ((bytes[VERSION_BYTE].toInt() and LOW_NIBBLE) or VERSION_4).toByte()
        bytes[VARIANT_BYTE] =
            ((bytes[VARIANT_BYTE].toInt() and VARIANT_MASK) or VARIANT_RFC4122).toByte()
        return buildString(UUID_LENGTH) {
            bytes.forEachIndexed { index, byte ->
                if (index in HYPHEN_POSITIONS) append('-')
                append(HEX[(byte.toInt() shr NIBBLE_SHIFT) and LOW_NIBBLE])
                append(HEX[byte.toInt() and LOW_NIBBLE])
            }
        }
    }

    private companion object {
        val HEX = "0123456789abcdef".toCharArray()
        val HYPHEN_POSITIONS = setOf(4, 6, 8, 10)
    }
}

/** Deterministic sequence of ids for fixtures and property tests. */
class SeededIdGenerator(seed: Long = 1L) : IdGenerator {
    private var counter = 0L
    private val random = Random(seed)

    override fun newId(): String {
        counter += 1
        val suffix = counter.toString(HEX_RADIX).padStart(SUFFIX_LENGTH, '0')
        val noise = random.nextInt(0, NOISE_RANGE).toString(HEX_RADIX).padStart(NOISE_LENGTH, '0')
        return "$noise-0000-4000-8000-$suffix"
    }
}

/** A fixed id, for a test that does not care which value it is. */
fun fixedId(prefix: String, index: Int): String = "$prefix-$index"
