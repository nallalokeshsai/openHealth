package app.openhealth.android.wear

import app.openhealth.android.OpenHealthApp
import app.openhealth.core.watch.WatchProtocol
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.PutDataRequest
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService

/**
 * The phone end of the Wear OS Data Layer (`pr.md` §6.18; `docs/adr/0019`).
 *
 * A message is a command; the reply is the current snapshot written as a data item, so a
 * watch that was asleep when the phone changed still reads the latest state when it
 * wakes. Writes go through [WatchCommandHandler], the same path as the phone's live
 * table; nothing here decides what a set means.
 *
 * This class is in the `play` source set, so the F-Droid APK does not contain it and
 * never resolves Play Services.
 */
class WearBridgeService : WearableListenerService() {
    override fun onMessageReceived(event: MessageEvent) {
        val container = (application as? OpenHealthApp)?.container ?: return
        when (event.path) {
            WatchProtocol.COMMAND_PATH -> {
                val text = runCatching { String(event.data, Charsets.UTF_8) }.getOrNull()
                    ?: return
                val command = WatchProtocol.decodeCommand(text) ?: return
                val outcome = container.watchCommands.apply(command, WatchKind.WEAR)
                if (outcome != WatchApply.REJECTED) publishSnapshot(container)
            }

            WatchProtocol.REQUEST_PATH -> publishSnapshot(container)
        }
    }

    private fun publishSnapshot(container: app.openhealth.android.AppContainer) {
        val snapshot = runCatching { container.watchSnapshots.build() }.getOrNull() ?: return
        val bytes = WatchProtocol.encode(WatchProtocol.snapshot(snapshot)).toByteArray()
        // Urgent: the snapshot carries a running rest timer, and the default
        // non-urgent item can sit in the Data Layer's batching window for minutes.
        val request = PutDataRequest.create(WatchProtocol.SNAPSHOT_PATH)
            .setData(bytes)
            .setUrgent()
        Wearable.getDataClient(this)
            .putDataItem(request)
            .addOnFailureListener { /* the watch retries its request; nothing is lost */ }
    }
}
