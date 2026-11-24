package app.openhealth.android

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.core.context.GlobalContext
import org.koin.core.context.startKoin
import org.koin.dsl.module

/**
 * The application object. It starts Koin; the encrypted store opens on the first IO
 * access from the state holder, never in composition. A failure (for example a lost
 * Keystore key) is shown as a plain startup error rather than a crash loop.
 */
class OpenHealthApp : Application() {
    lateinit var container: AppContainer
        private set

    var startupError: Throwable? = null
        private set

    override fun onCreate() {
        super.onCreate()
        start()
    }

    /**
     * Retries the container after a startup failure (for example a Keystore key that was
     * not ready yet). Koin is started once; only the container is rebuilt.
     */
    fun retryStartup(): Boolean {
        if (startupError == null && ::container.isInitialized) return true
        if (GlobalContext.getOrNull() == null) {
            start()
        } else {
            startupError = runCatching {
                container = GlobalContext.get().get<AppContainer>()
            }.exceptionOrNull()
        }
        return startupError == null
    }

    private fun start() {
        startupError = runCatching {
            startKoin {
                androidContext(this@OpenHealthApp)
                modules(
                    module {
                        single { AppContainer(get()) }
                    },
                )
            }
            container = GlobalContext.get().get<AppContainer>()
        }.exceptionOrNull()
    }
}
