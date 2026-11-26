package app.openhealth.wear.link

import android.content.Context
import app.openhealth.core.watch.WatchCommand
import app.openhealth.core.watch.WatchProtocol
import app.openhealth.core.watch.WatchSnapshot
import com.google.android.gms.wearable.DataEvent
import com.google.android.gms.wearable.DataEventBuffer
import com.google.android.gms.wearable.MessageEvent
import com.google.android.gms.wearable.Node
import com.google.android.gms.wearable.Wearable
import com.google.android.gms.wearable.WearableListenerService
import kotlin.coroutines.resume
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.suspendCancellableCoroutine

/**
 * The watch half of the Data Layer (`pr.md` §6.18; `docs/adr/0019`). `send` is
 * fire-and-forget: the command is persisted by [app.openhealth.wear.watch.WatchStore]
 * first, and the snapshot's `appliedCommandIds` is the acknowledgement.
 */
interface WearLink {
    val snapshots: SharedFlow<WatchSnapshot>
    val connected: StateFlow<Boolean>

    fun requestSnapshot()

    fun send(command: WatchCommand)
}

/** The one in-process bus shared by the listener service and the screen. */
object WearLinkBus {
    private val _snapshots = MutableSharedFlow<WatchSnapshot>(
        replay = 1,
        extraBufferCapacity = 8,
    )
    val snapshots: SharedFlow<WatchSnapshot> = _snapshots.asSharedFlow()

    private val _connected = MutableStateFlow(false)
    val connected: StateFlow<Boolean> = _connected.asStateFlow()

    fun snapshot(snapshot: WatchSnapshot) {
        _connected.value = true
        _snapshots.tryEmit(snapshot)
    }

    fun connection(connected: Boolean) {
        _connected.value = connected
    }
}

class DataLayerWearLink(private val context: Context, private val scope: CoroutineScope) :
    WearLink {
    override val snapshots: SharedFlow<WatchSnapshot> = WearLinkBus.snapshots
    override val connected: StateFlow<Boolean> = WearLinkBus.connected

    override fun requestSnapshot() {
        scope.launch(Dispatchers.IO) {
            val nodes = nodes()
            WearLinkBus.connection(nodes.isNotEmpty())
            nodes.forEach { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WatchProtocol.REQUEST_PATH, ByteArray(0))
                }
            }
        }
    }

    override fun send(command: WatchCommand) {
        val bytes = WatchProtocol.encode(WatchProtocol.command(command)).toByteArray()
        scope.launch(Dispatchers.IO) {
            val nodes = nodes()
            WearLinkBus.connection(nodes.isNotEmpty())
            nodes.forEach { node ->
                runCatching {
                    Wearable.getMessageClient(context)
                        .sendMessage(node.id, WatchProtocol.COMMAND_PATH, bytes)
                }
            }
        }
    }

    private suspend fun nodes(): List<Node> = suspendCancellableCoroutine { continuation ->
        Wearable.getNodeClient(context).connectedNodes
            .addOnSuccessListener { continuation.resume(it) }
            .addOnFailureListener { continuation.resume(emptyList()) }
    }
}

/** Receives the phone's snapshot item and connection changes. */
class WearLinkService : WearableListenerService() {
    override fun onDataChanged(dataEvents: DataEventBuffer) {
        dataEvents.forEach { event ->
            if (event.type == DataEvent.TYPE_CHANGED &&
                event.dataItem.uri.path == WatchProtocol.SNAPSHOT_PATH
            ) {
                val bytes = event.dataItem.data ?: return@forEach
                val text = runCatching { String(bytes, Charsets.UTF_8) }
                    .getOrNull() ?: return@forEach
                WatchProtocol.decodeSnapshot(text)?.let(WearLinkBus::snapshot)
            }
        }
    }

    override fun onMessageReceived(event: MessageEvent) {
        if (event.path != WatchProtocol.SNAPSHOT_PATH) return
        val text = runCatching { String(event.data, Charsets.UTF_8) }.getOrNull() ?: return
        WatchProtocol.decodeSnapshot(text)?.let(WearLinkBus::snapshot)
    }

    override fun onPeerConnected(peer: Node) {
        WearLinkBus.connection(true)
    }

    override fun onPeerDisconnected(peer: Node) {
        WearLinkBus.connection(false)
    }
}
