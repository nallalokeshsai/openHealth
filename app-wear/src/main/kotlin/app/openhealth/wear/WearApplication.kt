package app.openhealth.wear

import android.app.Application
import app.openhealth.wear.heart.WearHeartRate
import app.openhealth.wear.link.DataLayerWearLink
import app.openhealth.wear.watch.WatchStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/**
 * The watch process. It owns only the link, the local queue and the optical sensor; the
 * record itself stays on the phone (`docs/adr/0019`).
 */
class WearApplication : Application() {
    lateinit var services: WearServices
        private set

    override fun onCreate() {
        super.onCreate()
        services = WearServices(this)
    }
}

class WearServices(context: Application) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    val store: WatchStore = WatchStore(context)
    val link: DataLayerWearLink = DataLayerWearLink(context, scope)
    val heart: WearHeartRate = WearHeartRate(context, scope)
}
