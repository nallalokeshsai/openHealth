package app.openhealth.server

/**
 * Everything the server reads from its environment (`Tech-Stack.md` §7). The defaults
 * are a local `docker compose up` on loopback; a real deployment sets the domain, the
 * passkey relying-party id and the origin together.
 */
data class ServerConfig(
    val host: String = "0.0.0.0",
    val port: Int = 8080,
    /** The public name, used in the pairing page and documentation. */
    val domain: String = "localhost",
    /** The WebAuthn RP ID: a registrable domain suffix of [origin]. */
    val rpId: String = "localhost",
    val origin: String = "http://localhost:8080",
    /** Only these usernames may register (`AGENTS.md`: owner only, allowlisted). */
    val allowedUsers: Set<String> = setOf("owner"),
    /** Passwords are a fallback, off unless the operator opts in. */
    val allowPassword: Boolean = false,
    val minSchemaVersion: Int = 1,
    val maxSchemaVersion: Int = 11,
    val dbUrl: String? = null,
    val dbUser: String = "openhealth",
    val dbPassword: String = "openhealth",
    val s3Endpoint: String? = null,
    val s3Bucket: String = "openhealth-media",
    val s3AccessKey: String? = null,
    val s3SecretKey: String? = null,
) {
    val mediaConfigured: Boolean
        get() = !s3Endpoint.isNullOrBlank() &&
            !s3AccessKey.isNullOrBlank() &&
            !s3SecretKey.isNullOrBlank()

    companion object {
        fun fromEnvironment(env: Map<String, String> = System.getenv()): ServerConfig {
            val domain = env["OPENHEALTH_DOMAIN"]?.trim().orEmpty().ifBlank { "localhost" }
            val defaultOrigin = if (domain == "localhost") {
                "http://localhost:8080"
            } else {
                "https://$domain"
            }
            val allowed = env["OPENHEALTH_ALLOWED_USERS"]
                ?.split(',')
                ?.map(String::trim)
                ?.filter(String::isNotBlank)
                ?.toSet()
                .orEmpty()
                .ifEmpty { setOf("owner") }
            return ServerConfig(
                host = env["OPENHEALTH_HOST"] ?: "0.0.0.0",
                port = env["OPENHEALTH_PORT"]?.toIntOrNull() ?: 8080,
                domain = domain,
                rpId = env["OPENHEALTH_RP_ID"] ?: domain,
                origin = env["OPENHEALTH_ORIGIN"] ?: defaultOrigin,
                allowedUsers = allowed,
                allowPassword = env["OPENHEALTH_ALLOW_PASSWORD"]?.toBooleanStrictOrNull() ?: false,
                minSchemaVersion = env["OPENHEALTH_MIN_SCHEMA_VERSION"]?.toIntOrNull() ?: 1,
                maxSchemaVersion = env["OPENHEALTH_MAX_SCHEMA_VERSION"]?.toIntOrNull() ?: 11,
                dbUrl = env["OPENHEALTH_DB_URL"],
                dbUser = env["OPENHEALTH_DB_USER"] ?: "openhealth",
                dbPassword = env["OPENHEALTH_DB_PASSWORD"] ?: "openhealth",
                s3Endpoint = env["OPENHEALTH_S3_ENDPOINT"],
                s3Bucket = env["OPENHEALTH_S3_BUCKET"] ?: "openhealth-media",
                s3AccessKey = env["OPENHEALTH_S3_ACCESS_KEY"],
                s3SecretKey = env["OPENHEALTH_S3_SECRET_KEY"],
            )
        }
    }
}
