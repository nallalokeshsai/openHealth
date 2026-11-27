package app.openhealth.integrations.connectiq

import app.openhealth.core.watch.WatchBlePacket
import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNotNull
import kotlin.test.assertTrue

/**
 * The Garmin half cannot be compiled here: Garmin's SDK licence is the owner's to accept
 * and no developer key exists on this machine (`docs/adr/0019`). This test is what stands
 * in for `monkeyc`: the Monkey C app, the manifest, the resources, the build script and
 * the Kotlin wire model have to agree, so a packet field, a UUID or a resource cannot
 * drift on one side only.
 */
class ConnectIqSourceConsistencyTest {
    private val dir: File = File(
        System.getProperty("openhealth.connectiq.dir")
            ?: error("openhealth.connectiq.dir is not set"),
    )

    private fun source(name: String): String = File(dir, "source/$name").readText()

    private fun constantOf(text: String, name: String): String {
        val match = assertNotNull(
            Regex("""const\s+$name\s*=\s*([^;\n]+)""").find(text),
            "$name is not declared",
        )
        return match.groupValues[1].trim().trim('"')
    }

    @Test
    fun theWatchAndThePhoneUseTheSameGattUuids() {
        val link = source("BleLink.mc")
        assertEquals(WatchBlePacket.SERVICE_UUID, constantOf(link, "SERVICE_UUID"))
        assertEquals(WatchBlePacket.COMMAND_UUID, constantOf(link, "COMMAND_UUID"))
        assertEquals(WatchBlePacket.SNAPSHOT_UUID, constantOf(link, "SNAPSHOT_UUID"))
    }

    /**
     * The phone keeps its Bluetooth name off the advertisement, so matching on a name
     * would never find it. The watch matches the service UUID.
     */
    @Test
    fun theWatchFindsThePhoneByServiceUuidNotByName() {
        val link = source("BleLink.mc")
        assertTrue(
            "getServiceUuids()" in link,
            "BleLink.mc must match the advertised service UUID",
        )
        assertTrue(
            "getDeviceName()" !in link,
            "the phone's Bluetooth name is the person's and is never advertised",
        )
    }

    @Test
    fun thePacketHeaderAndSizesMatchTheKotlinCodec() {
        val codec = source("ByteCodec.mc")
        assertEquals(
            WatchBlePacket.MAGIC,
            Integer.decode(constantOf(codec, "MAGIC")),
        )
        assertEquals(WatchBlePacket.VERSION, constantOf(codec, "VERSION").toInt())
        assertEquals(WatchBlePacket.COMMAND_SIZE, constantOf(codec, "COMMAND_SIZE").toInt())
        assertEquals(WatchBlePacket.SNAPSHOT_SIZE, constantOf(codec, "SNAPSHOT_SIZE").toInt())
    }

    /**
     * A Connect IQ characteristic write and notification are limited to 20 bytes and there
     * is no long write, so neither packet may grow past that (`docs/adr/0019`).
     */
    @Test
    fun neitherPacketOutgrowsACharacteristic() {
        assertTrue(WatchBlePacket.COMMAND_SIZE <= MAX_CHARACTERISTIC_BYTES)
        assertTrue(WatchBlePacket.SNAPSHOT_SIZE <= MAX_CHARACTERISTIC_BYTES)
    }

    /**
     * The snapshot the watch decodes has to read the fields the phone wrote. The Kotlin
     * encoder is the reference; this pins the Monkey C reader to the same offsets.
     */
    @Test
    fun theWatchReadsTheSnapshotFieldsThePhoneWrote() {
        val decoded = assertNotNull(
            WatchBlePacket.decodeSnapshot(WatchBlePacket.encodeSnapshot(REFERENCE_SNAPSHOT)),
        )
        assertEquals(REFERENCE_SNAPSHOT, decoded)

        val codec = source("ByteCodec.mc")
        val reader = codec.substringAfter("function decodeSnapshot")
            .substringBefore("function u16")
        listOf(
            ":weightKg" to "u16(bytes, 3)",
            ":reps" to "bytes[5]",
            ":doneSets" to "bytes[6]",
            ":targetSets" to "bytes[7]",
            ":restRemaining" to "u16(bytes, 8)",
            ":restTotal" to "u16(bytes, 10)",
            ":incrementKg" to "u16(bytes, 12)",
            ":ackId" to "u32(bytes, 14)",
        ).forEach { (field, access) ->
            assertTrue(
                reader.contains(access),
                "$field must be read from $access, matching WatchBlePacket.encodeSnapshot",
            )
        }
    }

    /** The command the watch writes has to land where the phone's decoder looks. */
    @Test
    fun theWatchWritesTheCommandFieldsThePhoneReads() {
        val codec = source("ByteCodec.mc")
        val writer = codec.substringAfter("function encodeCommand")
            .substringBefore("function decodeSnapshot")
        listOf(
            "out[0] = MAGIC",
            "out[1] = VERSION",
            "out[2] = type",
            "out[3] =",
            "put16(out, 4,",
            "out[6] = reps",
            "out[7] = bpm",
            "put32(out, 8, id)",
            "put32(out, 12, happenedSeconds)",
        ).forEach { line ->
            assertTrue(writer.contains(line), "encodeCommand must write $line")
        }
    }

    /** The three command codes are the enum the phone decodes, in the same order. */
    @Test
    fun theCommandCodesMatchTheKotlinEnum() {
        val model = source("Model.mc")
        assertEquals(1, WatchBlePacket.Type.LOG_SET.code)
        assertEquals(2, WatchBlePacket.Type.FINISH.code)
        assertEquals(3, WatchBlePacket.Type.HEART_RATE.code)
        assertTrue(Regex("""logSet\(\)[\s\S]*?queue\(1,""").containsMatchIn(model))
        assertTrue(Regex("""finish\(\)[\s\S]*?queue\(2,""").containsMatchIn(model))
        assertTrue(Regex("""noteHeartRate\([\s\S]*?queue\(3,""").containsMatchIn(model))
    }

    /**
     * A command id is the phone's idempotency key, so it may never be reissued: a reused
     * id makes the phone drop a real set as a duplicate. The watch therefore persists the
     * counter rather than re-seeding it from the clock on every launch.
     */
    @Test
    fun commandIdsSurviveARestartOfTheWatchApp() {
        val model = source("Model.mc")
        assertTrue(
            """Storage.getValue("nextCommandId")""" in model,
            "Model.load must restore the command counter",
        )
        assertTrue(
            """Storage.setValue("nextCommandId"""" in model,
            "Model.queue must persist the command counter",
        )
    }

    /**
     * `nextPacket()` returns nothing while a write is in flight, and only
     * `onCharacteristicWrite` clears that flag. A link that drops mid-write never
     * delivers it, so the disconnected branch has to clear the flag itself or the queue
     * stays wedged until the app is relaunched and the person's sets sit on the wrist.
     */
    @Test
    fun aDroppedLinkDoesNotWedgeTheQueueOnAnUnfinishedWrite() {
        val link = source("BleLink.mc")
        val disconnect = assertNotNull(
            Regex("""CONNECTION_STATE_CONNECTED[\s\S]*?\} else \{([\s\S]*?)\n        \}""")
                .find(link),
            "BleLink.onConnectedStateChanged must have a disconnected branch",
        ).groupValues[1]
        assertTrue(
            "Model.wrote()" in disconnect,
            "the disconnected branch must clear the in-flight write flag",
        )
        val model = source("Model.mc")
        assertTrue(
            "writing" in model && "function wrote()" in model,
            "Model must keep the write flag it clears",
        )
    }

    @Test
    fun everyResourceTheManifestNamesIsDeclared() {
        val manifest = File(dir, "manifest.xml").readText()
        val drawables = File(dir, "resources/drawables/drawables.xml")
        assertTrue(drawables.isFile, "resources/drawables/drawables.xml is missing")
        Regex("""@Drawables\.(\w+)""").findAll(manifest).forEach { match ->
            val id = match.groupValues[1]
            assertTrue(
                """id="$id"""" in drawables.readText(),
                "manifest.xml names @Drawables.$id but it is not declared",
            )
        }
        val strings = File(dir, "resources/strings.xml").readText()
        Regex("""@Strings\.(\w+)""").findAll(manifest).forEach { match ->
            val id = match.groupValues[1]
            assertTrue(
                """id="$id"""" in strings,
                "manifest.xml names @Strings.$id but it is not declared",
            )
        }
    }

    /** A drawable declaration that names a file nobody shipped fails `monkeyc`. */
    @Test
    fun everyDeclaredDrawableFileExists() {
        val drawables = File(dir, "resources/drawables/drawables.xml").readText()
        Regex("""filename="([^"]+)"""").findAll(drawables).forEach { match ->
            val file = File(dir, "resources/drawables/${match.groupValues[1]}")
            assertTrue(file.isFile, "${file.name} is declared but not present")
        }
    }

    /**
     * `Rez.Strings.X` is a resource id, so every one the views use has to exist, and a
     * view has to load it before drawing it or the watch draws a number.
     */
    @Test
    fun everyStringTheViewsUseIsDeclaredAndLoaded() {
        val strings = File(dir, "resources/strings.xml").readText()
        File(dir, "source").listFiles().orEmpty().filter { it.extension == "mc" }
            .forEach { file ->
                val text = file.readText()
                Regex("""Rez\.Strings\.(\w+)""").findAll(text).forEach { match ->
                    val id = match.groupValues[1]
                    assertTrue(
                        """id="$id"""" in strings,
                        "${file.name} uses Rez.Strings.$id, which strings.xml does not declare",
                    )
                    assertTrue(
                        "loadResource(Rez.Strings.$id)" in text,
                        "${file.name} must loadResource(Rez.Strings.$id) before drawing it",
                    )
                }
            }
    }

    /**
     * `build.sh` reads its targets from the manifest. A second hard-coded list is the
     * drift the table check cannot see.
     */
    @Test
    fun theBuildScriptKeepsNoSecondDeviceList() {
        val script = File(dir, "build.sh").readText()
        assertTrue("manifest.xml" in script, "build.sh must read manifest.xml")
        val table = ConnectIqCompatibility.parse(File(dir, "devices.json").readText())
        table.devices.filterNot { it.manifestTarget }.forEach { device ->
            assertTrue(
                device.productId !in script,
                "build.sh names ${device.productId}, which is not a manifest target",
            )
        }
    }

    /** The jungle has to point at the files that are actually here. */
    @Test
    fun theJungleNamesTheRealSourceAndResourcePaths() {
        val jungle = File(dir, "monkey.jungle").readText()
        assertTrue("project.manifest = manifest.xml" in jungle)
        val sourcePath = assertNotNull(
            Regex("""base\.sourcePath\s*=\s*(\S+)""").find(jungle),
        ).groupValues[1]
        val resourcePath = assertNotNull(
            Regex("""base\.resourcePath\s*=\s*(\S+)""").find(jungle),
        ).groupValues[1]
        assertTrue(File(dir, sourcePath).isDirectory, "$sourcePath is not a directory")
        assertTrue(File(dir, resourcePath).isDirectory, "$resourcePath is not a directory")
    }

    private companion object {
        const val MAX_CHARACTERISTIC_BYTES = 20

        val REFERENCE_SNAPSHOT = app.openhealth.core.watch.WatchBleSnapshot(
            sessionOpen = true,
            restRunning = true,
            connected = true,
            weightUnit = "lb",
            weightKg = 82.5,
            reps = 6,
            doneSets = 2,
            targetSets = 4,
            restRemainingSeconds = 75,
            restTotalSeconds = 120,
            weightIncrementKg = 2.5,
            ackCommandId = 4_242,
        )
    }
}
