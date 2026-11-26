package app.openhealth.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

/**
 * `pr.md` §6.23, §6.31 and `BUILD-ROADMAP.md` §B10: the app opens a socket for exactly
 * one explicit action, and never over cleartext.
 *
 * This is a source scan rather than a runtime check because the invariant is about the
 * whole client, including code paths no test drives. It reads every Kotlin source the
 * app is built from, so a new HTTP client in any module fails the build.
 */
class PrivacySourceTest {
    private val roots = listOf(
        "../core/src",
        "../data/src",
        "../sync/src",
        "../integrations/src",
        "../ui/src/main",
        "../app-android/src/main",
    )

    /** `:sync` has no sources until B13; every other root has to be there to be read. */
    private val mayBeEmpty = setOf("../sync/src")

    private fun kotlinFiles(): List<File> {
        val missing = roots.filterNot { it in mayBeEmpty }.filterNot { File(it).exists() }
        assertTrue(
            missing.isEmpty(),
            "the privacy scan cannot read $missing, so it would pass by looking away",
        )
        return roots
            .map(::File)
            .filter { it.exists() }
            .flatMap { root ->
                root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
            }
    }

    @Test
    fun theOnlyTransportIsTheOpenFoodFactsBarcodeLookup() {
        val files = kotlinFiles()
        assertTrue(files.size > 100, "the scan found only ${files.size} files")
        val network = files.filter { file ->
            val text = file.readText()
            listOf(
                "openConnection(",
                "openStream(",
                "HttpURLConnection(",
                "OkHttpClient",
                "okhttp3.",
                "io.ktor.client",
                "java.net.Socket",
                "java.net.DatagramSocket",
                "java.nio.channels.SocketChannel",
                "CronetEngine",
                "DownloadManager",
                "WebView",
                "HttpClient(",
            ).any { it in text }
        }
        // B11 adds exactly one path beside the barcode lookup: the user-initiated,
        // HTTPS-only, 50 MB-capped PMTiles map-pack download (`pr.md` §6.11, B11).
        assertEquals(
            listOf("HttpUrlConnectionTransport.kt", "MapPackStore.kt"),
            network.map { it.name }.sortedBy { it != "HttpUrlConnectionTransport.kt" },
            "a third network path exists; only the barcode lookup and the map-pack download are allowed",
        )
        // The one transport is reached through the :core seam, not called from a screen.
        assertTrue(
            File("../core/src/main/kotlin/app/openhealth/core/seam/HttpSeam.kt").readText()
                .contains("interface HttpTransport"),
            "the transport must sit behind the :core seam",
        )
    }

    @Test
    fun noSourceFileContainsACleartextUrl() {
        // GPX and TCX schemas identify themselves with http namespace URIs; a namespace
        // is an identifier, never a request. Strip only an xmlns declaration, then scan.
        val namespace = Regex("""xmlns(:[A-Za-z0-9]+)?="http://[^"]+"""")
        // Parser feature flags and schema identifiers are names, not endpoints.
        val identifiers = listOf(
            "http://apache.org/xml/",
            "http://xml.org/sax/",
            "http://www.topografix.com/",
            "http://www.garmin.com/xmlschemas/",
        )
        val hits = kotlinFiles().filter { file ->
            var text = file.readText().replace(namespace, "")
            identifiers.forEach { text = text.replace(it, "") }
            "http://" in text
        }
        assertTrue(hits.isEmpty(), "cleartext URL in ${hits.map { it.path }}")
    }

    @Test
    fun theOpenFoodFactsBaseUrlIsHttpsAndNamed() {
        val client = File(
            "../integrations/src/main/kotlin/app/openhealth/integrations/food/OpenFoodFacts.kt",
        )
        val text = client.readText()
        assertTrue(text.contains("https://world.openfoodfacts.org"), "the host is pinned")
        assertTrue(
            text.contains("isPlausibleBarcode"),
            "the barcode must not be able to change the host",
        )
    }
}
