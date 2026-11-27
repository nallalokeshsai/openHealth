package app.openhealth.core.security

/**
 * SHA-256 in pure Kotlin, used for the on-device MCP token digests. It lives here rather
 * than in `java.security` so the domain modules stay portable (`Tech-Stack.md` §3); the
 * tests pin the NIST vectors.
 */
object Sha256 {
    private val K = intArrayOf(
        1116352408, 1899447441, -1245643825, -373957723, 961987163, 1508970993,
        -1841331548, -1424204075, -670586216, 310598401, 607225278, 1426881987,
        1925078388, -2132889090, -1680079193, -1046744716, -459576895, -272742522,
        264347078, 604807628, 770255983, 1249150122, 1555081692, 1996064986,
        -1740746414, -1473132947, -1341970488, -1084653625, -958395405, -710438585,
        113926993, 338241895, 666307205, 773529912, 1294757372, 1396182291,
        1695183700, 1986661051, -2117940946, -1838011259, -1564481375, -1474664885,
        -1035236496, -949202525, -778901479, -694614492, -200395387, 275423344,
        430227734, 506948616, 659060556, 883997877, 958139571, 1322822218,
        1537002063, 1747873779, 1955562222, 2024104815, -2067236844, -1933114872,
        -1866530822, -1538233109, -1090935817, -965641998,
    )

    private val INITIAL = intArrayOf(
        0x6a09e667, -0x4498517b, 0x3c6ef372, -0x5ab00ac6, 0x510e527f, -0x64fa9774,
        0x1f83d9ab, 0x5be0cd19,
    )

    private const val BLOCK = 64
    private const val WORD_BYTES = 4
    private const val WORD_BITS = 32
    private const val MASK = 0xFF
    private const val ROUNDS = 64
    private const val BYTE_MASK = 0xFF

    fun digest(input: ByteArray): ByteArray {
        val padded = pad(input)
        val state = INITIAL.copyOf()
        val words = IntArray(ROUNDS)
        var offset = 0
        while (offset < padded.size) {
            for (index in 0 until 16) {
                words[index] = wordAt(padded, offset + index * WORD_BYTES)
            }
            for (index in 16 until ROUNDS) {
                val s0 = rotateRight(words[index - 15], 7) xor
                    rotateRight(words[index - 15], 18) xor (words[index - 15] ushr 3)
                val s1 = rotateRight(words[index - 2], 17) xor
                    rotateRight(words[index - 2], 19) xor (words[index - 2] ushr 10)
                words[index] = words[index - 16] + s0 + words[index - 7] + s1
            }
            var a = state[0]
            var b = state[1]
            var c = state[2]
            var d = state[3]
            var e = state[4]
            var f = state[5]
            var g = state[6]
            var h = state[7]
            for (index in 0 until ROUNDS) {
                val s1 = rotateRight(e, 6) xor rotateRight(e, 11) xor rotateRight(e, 25)
                val choose = (e and f) xor (e.inv() and g)
                val temp1 = h + s1 + choose + K[index] + words[index]
                val s0 = rotateRight(a, 2) xor rotateRight(a, 13) xor rotateRight(a, 22)
                val majority = (a and b) xor (a and c) xor (b and c)
                val temp2 = s0 + majority
                h = g
                g = f
                f = e
                e = d + temp1
                d = c
                c = b
                b = a
                a = temp1 + temp2
            }
            state[0] += a
            state[1] += b
            state[2] += c
            state[3] += d
            state[4] += e
            state[5] += f
            state[6] += g
            state[7] += h
            offset += BLOCK
        }
        return ByteArray(WORD_BYTES * state.size) { index ->
            val word = state[index / WORD_BYTES]
            (word ushr (WORD_BITS - (index % WORD_BYTES + 1) * Byte.SIZE_BITS)).toByte()
        }
    }

    fun hex(bytes: ByteArray): String = buildString(bytes.size * 2) {
        bytes.forEach { byte ->
            val value = byte.toInt() and BYTE_MASK
            append(HEX[value ushr 4])
            append(HEX[value and 0x0F])
        }
    }

    fun hexDigest(text: String): String = hex(digest(text.encodeToByteArray()))

    private fun pad(input: ByteArray): ByteArray {
        val messageBits = input.size.toLong() * Byte.SIZE_BITS
        val padding = BLOCK - (input.size + 9) % BLOCK
        val total = input.size + 1 + padding + Long.SIZE_BYTES
        return ByteArray(total).also { output ->
            input.copyInto(output)
            output[input.size] = 0x80.toByte()
            for (index in 0 until Long.SIZE_BYTES) {
                output[output.size - 1 - index] =
                    (messageBits ushr (index * Byte.SIZE_BITS)).toByte()
            }
        }
    }

    private fun wordAt(bytes: ByteArray, offset: Int): Int =
        ((bytes[offset].toInt() and BYTE_MASK) shl 24) or
            ((bytes[offset + 1].toInt() and BYTE_MASK) shl 16) or
            ((bytes[offset + 2].toInt() and BYTE_MASK) shl 8) or
            (bytes[offset + 3].toInt() and BYTE_MASK)

    private fun rotateRight(value: Int, bits: Int): Int =
        (value ushr bits) or (value shl (WORD_BITS - bits))

    private val HEX = "0123456789abcdef".toCharArray()
}
