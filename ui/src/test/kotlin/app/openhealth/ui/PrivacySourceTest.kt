package app.openhealth.ui

import java.io.File
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
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
    fun theOnlyTransportsAreTheBarcodeLookupTheMapPackDownloadTheSyncClientAndTheLocalMcpServer() {
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
        // The barcode lookup and the map-pack download are B6/B11; the paired sync
        // client is B13 and is reached only after a pairing code is exchanged
        // (`pr.md` §6.24, §6.11); the loopback MCP listener is B15 and binds this device
        // only. No screen may open a socket of its own.
        assertEquals(
            listOf(
                "HttpUrlConnectionTransport.kt",
                "KtorSyncTransport.kt",
                "MapPackStore.kt",
                "McpLoopbackServer.kt",
                "SyncEngine.kt",
            ),
            network.map { it.name }.sorted(),
            "an unexpected network path exists; only the barcode lookup, the map-pack " +
                "download and the sync client are allowed",
        )
        // The one transport is reached through the :core seam, not called from a screen.
        assertTrue(
            File("../core/src/main/kotlin/app/openhealth/core/seam/HttpSeam.kt").readText()
                .contains("interface HttpTransport"),
            "the transport must sit behind the :core seam",
        )
    }

    /** B15: the local endpoint is loopback-only and every call needs a live token. */
    /** The one declared cleartext exception covers loopback and nothing else. */
    @Test
    fun theCleartextExceptionIsLoopbackOnly() {
        val config = File(
            "../app-android/src/main/res/xml/network_security_config.xml",
        ).readText()
        assertTrue(config.contains("cleartextTrafficPermitted=\"true\""))
        listOf("127.0.0.1", "localhost", "10.0.2.2", "::1").forEach {
            assertTrue(config.contains(">$it<"), "$it must be the declared loopback host")
        }
        assertFalse(
            config.contains("openfoodfacts") || config.contains("example.com"),
            "no remote host is excepted",
        )
    }

    @Test
    fun theMcpListenerBindsLoopbackAndRequiresAToken() {
        val server = File(
            "../app-android/src/main/kotlin/app/openhealth/android/mcp/McpLoopbackServer.kt",
        ).readText()
        assertTrue(
            server.contains("\"127.0.0.1\""),
            "the MCP server must bind loopback, not every interface",
        )
        assertTrue(server.contains("ServerSocket"), "the endpoint is a local socket")
        assertTrue(
            server.contains("McpProtocol.MAX_BODY_BYTES"),
            "the endpoint must cap the body it reads",
        )
        val runtime = File(
            "../app-android/src/main/kotlin/app/openhealth/android/mcp/McpRuntime.kt",
        ).readText()
        assertTrue(runtime.contains("Sha256.hexDigest"), "a token is matched by its digest")
        assertTrue(runtime.contains(".revoked"), "a revoked token is refused")
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
            // FHIR code-system and profile identifiers: names, never requests. FHIR
            // fixes these URIs in http and a rewrite would change their meaning.
            "http://loinc.org",
            "http://unitsofmeasure.org",
            "http://hl7.org/fhir/",
            "http://terminology.hl7.org/",
        )
        // A loopback endpoint is the one declared cleartext exception (the self-hosted
        // coach, docs/adr/0020 §8); the network security config permits only these hosts.
        val loopback = Regex("""http://(127\.0\.0\.1|localhost|10\.0\.2\.2|\[::1\])(?![\w.-])""")
        val hits = kotlinFiles().filter { file ->
            var text = file.readText().replace(namespace, "").replace(loopback, "")
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
