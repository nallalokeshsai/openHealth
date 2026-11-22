package app.openhealth.core

import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * B0 build-infrastructure test, not feature code: proves the pure-module toolchain actually
 * honours the pinned bytecode level (Tech-Stack.md §14 — JDK 21 toolchain, Java 17 target).
 * B2 replaces this with the domain tests.
 */
class ToolchainTest {

    @Test
    fun `pure modules target Java 17 bytecode`() {
        val classFile = checkNotNull(javaClass.getResourceAsStream("ToolchainTest.class"))
        val bytes = classFile.use { it.readBytes() }
        val majorVersion = ((bytes[6].toInt() and 0xFF) shl 8) or (bytes[7].toInt() and 0xFF)
        assertEquals(JAVA_17_CLASS_MAJOR, majorVersion)
    }

    private companion object {
        const val JAVA_17_CLASS_MAJOR = 61
    }
}
