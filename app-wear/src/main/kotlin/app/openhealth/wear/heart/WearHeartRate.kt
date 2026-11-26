package app.openhealth.wear.heart

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.health.services.client.HealthServices
import androidx.health.services.client.MeasureCallback
import androidx.health.services.client.MeasureClient
import androidx.health.services.client.data.Availability
import androidx.health.services.client.data.DataPointContainer
import androidx.health.services.client.data.DataType
import androidx.health.services.client.data.DeltaDataType
import app.openhealth.core.watch.WatchProtocol
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

/**
 * The optical heart-rate stream on Wear OS (`pr.md` §6.18). It is display-only on the
 * wrist and sends at most one sample a minute, stamped with the wrist's time, so a
 * workout adds a small stream of observations rather than one row a second.
 */
class WearHeartRate(private val context: Context, private val scope: CoroutineScope) {
    private var client: MeasureClient? = null
    private var callback: MeasureCallback? = null

    fun hasPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.BODY_SENSORS) ==
            PackageManager.PERMISSION_GRANTED

    fun start(onBpm: (Int) -> Unit) {
        if (!hasPermission()) return
        stop()
        scope.launch {
            val measure = runCatching {
                HealthServices.getClient(context).measureClient
            }.getOrNull() ?: return@launch
            val listener = object : MeasureCallback {
                override fun onAvailabilityChanged(
                    dataType: DeltaDataType<*, *>,
                    availability: Availability,
                ) = Unit

                override fun onDataReceived(data: DataPointContainer) {
                    val bpm = data.sampleDataPoints
                        .filter { it.dataType == DataType.HEART_RATE_BPM }
                        .mapNotNull { point -> (point.value as? Float)?.toInt() }
                        .lastOrNull { bpmValue ->
                            bpmValue in WatchProtocol.MIN_HEART_RATE..WatchProtocol.MAX_HEART_RATE
                        }
                    if (bpm != null) onBpm(bpm)
                }
            }
            client = measure
            callback = listener
            runCatching {
                measure.registerMeasureCallback(DataType.HEART_RATE_BPM, listener)
            }
        }
    }

    fun stop() {
        val measure = client ?: return
        val listener = callback ?: return
        runCatching { measure.unregisterMeasureCallbackAsync(DataType.HEART_RATE_BPM, listener) }
        client = null
        callback = null
    }
}
