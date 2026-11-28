package app.openhealth.server

import io.ktor.server.engine.embeddedServer
import io.ktor.server.netty.Netty

/**
 * Entry point: reads the environment, opens PostgreSQL when a URL is configured, runs
 * Flyway, and serves the sync API. Without a database it uses in-memory stores so the
 * server can be tried locally; a real deployment is the Compose file in this directory.
 */
fun main() {
    val config = ServerConfig.fromEnvironment()
    val database = config.dbUrl?.let { PostgresDatabase(config) }
    val authStore: AuthStore = database?.let(::PostgresAuthStore) ?: InMemoryAuthStore()
    val changeStore: ChangeStore = database?.let {
        PostgresChangeStore(it, config.minSchemaVersion)
    } ?: InMemoryChangeStore(config.minSchemaVersion)
    val auth = AuthService(config, authStore)
    val media: MediaStore? = if (config.mediaConfigured) {
        S3MediaStore(
            endpoint = checkNotNull(config.s3Endpoint),
            bucket = config.s3Bucket,
            accessKey = checkNotNull(config.s3AccessKey),
            secretKey = checkNotNull(config.s3SecretKey),
        )
    } else {
        null
    }
    Runtime.getRuntime().addShutdownHook(Thread { database?.close() })

    embeddedServer(
        Netty,
        port = config.port,
        host = config.host,
    ) {
        openHealthModule(config, auth, changeStore, media)
    }.start(wait = true)
}
