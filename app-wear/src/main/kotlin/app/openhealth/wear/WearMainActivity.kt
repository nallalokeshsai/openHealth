package app.openhealth.wear

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.lifecycleScope
import app.openhealth.ui.theme.OhTheme
import app.openhealth.wear.watch.WearStateHolder

class WearMainActivity : ComponentActivity() {
    private lateinit var holder: WearStateHolder
    private var heartRateAllowed by mutableStateOf(false)

    private val permission = registerForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        heartRateAllowed = granted
        if (granted) holder.startHeartRate()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val services = (application as WearApplication).services
        holder = WearStateHolder(
            store = services.store,
            link = services.link,
            heart = services.heart,
            scope = lifecycleScope,
            vibrate = ::vibrate,
        )
        heartRateAllowed = hasHeartRatePermission()
        setContent {
            val state by holder.state.collectAsStateWithLifecycle()
            OhTheme {
                WearScreen(
                    state = state,
                    connected = state.connected,
                    heartRateAllowed = heartRateAllowed,
                    onLogSet = holder::logSet,
                    onFinish = holder::finish,
                    onWeight = holder::adjustWeight,
                    onReps = holder::adjustReps,
                    onExtendRest = holder::extendRest,
                    onSkipRest = holder::skipRest,
                    onAllowHeartRate = ::askHeartRate,
                )
            }
        }
    }

    override fun onResume() {
        super.onResume()
        holder.onResume()
    }

    override fun onPause() {
        holder.onPause()
        super.onPause()
    }

    private fun askHeartRate() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            permission.launch(Manifest.permission.BODY_SENSORS)
        }
    }

    private fun hasHeartRatePermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    private fun vibrate() {
        val vibrator = getSystemService(VIBRATOR_SERVICE) as? Vibrator ?: return
        vibrator.vibrate(
            VibrationEffect.createOneShot(VIBRATION_MILLIS, VibrationEffect.DEFAULT_AMPLITUDE),
        )
    }

    companion object {
        private const val VIBRATION_MILLIS = 60L
    }
}
