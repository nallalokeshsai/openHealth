package app.openhealth.android

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B0 build-infrastructure test, not feature code: the Android modules compile to Java 17
 * bytecode (Tech-Stack.md §14 — JDK 21 toolchain, Java 17 target, which D8 desugars). It is the
 * Android half of `:core`'s ToolchainTest, so a drift in either half is not silent.
 */
class BytecodeTargetTest {

    @Test
    fun `app code is Java 17 bytecode`() {
        val classFile = checkNotNull(MainActivity::class.java.getResourceAsStream("MainActivity.class"))
        val bytes = classFile.use { it.readBytes() }
        val majorVersion = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        assertEquals(JAVA_17_CLASS_MAJOR, majorVersion)
    }

    private companion object {
        const val JAVA_17_CLASS_MAJOR = 61
    }
}
