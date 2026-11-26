package app.openhealth.android

import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.security.NetworkSecurityPolicy
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

/**
 * The B10 security posture, asserted against the installed APK rather than the source
 * folder: backup off, cleartext off, only the permissions the product uses, and no
 * telemetry SDK reachable from the app process (`pr.md` §6.23, §6.31).
 *
 * A source scan cannot see what the manifest merger and the dependency resolution did,
 * which is exactly what this test is for.
 */
@RunWith(AndroidJUnit4::class)
class SecurityPostureTest {

    private val context = InstrumentationRegistry.getInstrumentation().targetContext
    private val pack = context.packageName
    private val info: ApplicationInfo =
        context.packageManager.getApplicationInfo(pack, PackageManager.GET_META_DATA)

    @Test
    fun backupIsOffAndBothBackupRuleFilesAreDeclared() {
        assertEquals(0, info.flags and ApplicationInfo.FLAG_ALLOW_BACKUP)
        assertTrue(
            "the cloud-backup rules must be declared",
            app.openhealth.R.xml.data_extraction_rules != 0,
        )
        assertTrue(
            "the full-backup rules must be declared",
            app.openhealth.R.xml.backup_rules != 0,
        )
    }

    @Test
    fun cleartextTrafficIsRefusedEverywhere() {
        assertFalse(
            "the app must never allow cleartext (pr.md §6.23)",
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(),
        )
        assertFalse(
            "no host may be exempted from the cleartext rule",
            NetworkSecurityPolicy.getInstance().isCleartextTrafficPermitted(
                "world.openfoodfacts.org",
            ),
        )
        assertTrue(
            "a network security config must be declared",
            app.openhealth.R.xml.network_security_config != 0,
        )
    }

    @Test
    fun thePermissionListIsExactlyWhatTheProductUses() {
        val requested = context.packageManager
            .getPackageInfo(pack, PackageManager.GET_PERMISSIONS)
            .requestedPermissions
            ?.toSet()
            .orEmpty()
        val expected = setOf(
            "android.permission.USE_BIOMETRIC",
            "android.permission.CAMERA",
            "android.permission.INTERNET",
            // MapLibre's connectivity receiver; the map itself reads a local pack.
            "android.permission.ACCESS_NETWORK_STATE",
            "android.permission.POST_NOTIFICATIONS",
            "android.permission.FOREGROUND_SERVICE",
            "android.permission.FOREGROUND_SERVICE_SPECIAL_USE",
            "android.permission.FOREGROUND_SERVICE_HEALTH",
            "android.permission.VIBRATE",
            "android.permission.ACTIVITY_RECOGNITION",
            "android.permission.RECEIVE_BOOT_COMPLETED",
            // B11: the location foreground service records a route only while running.
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.ACCESS_COARSE_LOCATION",
            "android.permission.FOREGROUND_SERVICE_LOCATION",
            "android.permission.BLUETOOTH_SCAN",
            "android.permission.BLUETOOTH_CONNECT",
            // B14: the Connect IQ watch link advertises a local GATT service while a
            // workout is open; the Wear Data Layer is Play-only and declares no extra
            // permission of its own.
            "android.permission.BLUETOOTH_ADVERTISE",
            "android.permission.FOREGROUND_SERVICE_CONNECTED_DEVICE",
            // Health Connect: reads only, one per data type the Devices page offers.
            "android.permission.health.READ_STEPS",
            "android.permission.health.READ_DISTANCE",
            "android.permission.health.READ_ACTIVE_CALORIES_BURNED",
            "android.permission.health.READ_FLOORS_CLIMBED",
            "android.permission.health.READ_HEART_RATE",
            "android.permission.health.READ_RESTING_HEART_RATE",
            "android.permission.health.READ_SLEEP",
            "android.permission.health.READ_EXERCISE",
            "android.permission.health.READ_WEIGHT",
            "android.permission.health.READ_BODY_FAT",
            "android.permission.health.READ_HEALTH_DATA_HISTORY",
        )
        // The platform injects a per-package receiver permission; it is not a grant.
        val product = requested.filterNot { it.startsWith("$pack.") }.toSet()
        // Below Android 12 the two legacy Bluetooth permissions take the place of the
        // runtime pair; above it, the pair is requested and the legacy ones are filtered
        // out by their maxSdkVersion. Both shapes are correct.
        val legacyBluetooth = setOf(
            "android.permission.BLUETOOTH",
            "android.permission.BLUETOOTH_ADMIN",
        )
        // Android 17 adds this to apps targeting its SDK whether or not they declare it;
        // it is the platform's own sensor gate, not a permission this app asked for.
        val platformInjected = setOf("android.permission.OTHER_SENSORS")
        assertEquals(
            "the app requests a permission the product does not use",
            emptySet<String>(),
            product - expected - legacyBluetooth - platformInjected,
        )
        assertTrue(
            "a permission the product uses is missing: ${expected - product}",
            product.containsAll(expected),
        )
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.S) {
            assertTrue(
                "the legacy Bluetooth pair must not be requested on Android 12 and later",
                product.none { it in legacyBluetooth },
            )
        }
        // Location is exactly the pair plus its service type, never a background grant.
        assertEquals(
            setOf(
                "android.permission.ACCESS_FINE_LOCATION",
                "android.permission.ACCESS_COARSE_LOCATION",
            ),
            product.filter {
                it.startsWith("android.permission.ACCESS_") && it.contains("LOCATION")
            }.toSet(),
        )
        assertFalse("no storage permission", product.any { it.contains("EXTERNAL_STORAGE") })
        assertFalse(
            "no install-referrer permission",
            product.any {
                it.contains("INSTALL_REFERRER")
            },
        )
    }

    @Test
    fun noTelemetrySdkClassCanBeLoadedFromTheApp() {
        listOf(
            "com.google.firebase.FirebaseApp",
            "com.google.android.gms.analytics.GoogleAnalytics",
            "com.google.android.datatransport.TransportFactory",
            "com.crashlytics.android.Crashlytics",
            "io.sentry.Sentry",
            "com.datadog.android.Datadog",
            "com.amplitude.api.Amplitude",
            "com.mixpanel.android.mpmetrics.MixpanelAPI",
        ).forEach { className ->
            val loaded = runCatching {
                Class.forName(className, false, context.classLoader)
            }.getOrNull()
            assertTrue("$className must not be on the runtime classpath", loaded == null)
        }
        // The class loader really does resolve app classes, so the assertion above is not
        // vacuous: this one is present in the APK.
        assertNotNull(
            Class.forName("app.openhealth.android.MainActivity", false, context.classLoader),
        )
    }
}
