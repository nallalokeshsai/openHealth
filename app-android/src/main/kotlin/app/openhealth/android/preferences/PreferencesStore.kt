package app.openhealth.android.preferences

import app.openhealth.core.ai.CoachCategory
import app.openhealth.core.ai.CoachConsent
import app.openhealth.core.ai.CoachProviderConfig
import app.openhealth.core.ai.CoachProviderKind
import app.openhealth.core.preferences.DisplayPolicy
import app.openhealth.core.preferences.HealthModule
import app.openhealth.core.preferences.ModulePreset
import app.openhealth.data.repo.ProfileRepository
import app.openhealth.data.repo.SettingKeys
import app.openhealth.data.repo.SettingsRepository
import app.openhealth.ui.theme.OhAccent
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** The appearance choice of `DESIGN.md` §12.4. The theme setting, not the design token. */
enum class ThemeMode(val key: String) {
    SYSTEM("system"),
    DARK("dark"),
    LIGHT("light"),
    ;

    companion object {
        fun fromKey(key: String?): ThemeMode = entries.firstOrNull { it.key == key } ?: SYSTEM
    }
}

/** One immutable read of every preference a screen asks for. */
data class Preferences(
    val onboardingComplete: Boolean,
    val weightNeutral: Boolean,
    val modules: Set<HealthModule>,
    val preset: ModulePreset,
    val theme: ThemeMode,
    val accent: OhAccent,
    val appLock: Boolean,
    val demo: Boolean,
    val highContrast: Boolean,
    val reduceTransparency: Boolean,
    val reducedMotion: Boolean,
    /** B15: off by default, per-category, and Health Connect separately (`pr.md` §6.15). */
    val coachConsent: CoachConsent,
    val coachProvider: CoachProviderConfig,
    val coachDisclosed: Boolean,
    val mcpEnabled: Boolean,
    val mcpPort: Int,
) {
    /** The object screens ask; none of them reads [weightNeutral] (`pr.md` §6.25). */
    val displayPolicy: DisplayPolicy get() = DisplayPolicy.of(weightNeutral)

    fun isEnabled(module: HealthModule): Boolean = module in modules
}

/**
 * The layer-5 settings the shell reads (`pr.md` §7.5). One place writes them, so a
 * missing key means the documented default and never "off because it was never asked".
 *
 * Weight-neutral mode has one canonical row, [SettingKeys.WEIGHT_NEUTRAL]. The profile's
 * `weight_neutral` column is kept in step for export completeness only; no screen reads
 * it (the policy object is the only door).
 */
@Suppress("TooManyFunctions") // One writer for the layer-5 settings, one method per switch.
class PreferencesStore(
    private val settings: SettingsRepository,
    private val profiles: ProfileRepository,
) {
    fun snapshot(): Preferences {
        val preset = ModulePreset.fromKey(settings.get(SettingKeys.MODULE_PRESET))
            ?: ModulePreset.EVERYTHING
        val modules = HealthModule.entries.filterTo(mutableSetOf()) { module ->
            settings.get(moduleKey(module))?.let { it == "true" } ?: true
        }
        return Preferences(
            onboardingComplete = settings.get(SettingKeys.ONBOARDING_COMPLETE) == "true",
            weightNeutral = settings.get(SettingKeys.WEIGHT_NEUTRAL) == "true",
            modules = modules,
            preset = preset,
            theme = ThemeMode.fromKey(settings.get(SettingKeys.THEME)),
            accent = OhAccent.fromKey(settings.get(SettingKeys.ACCENT_PRESET)) ?: OhAccent.Brand,
            appLock = settings.get(SettingKeys.APP_LOCK) == "true",
            demo = settings.get(SettingKeys.DEMO_DATA) == "true",
            highContrast = settings.get(SettingKeys.HIGH_CONTRAST) == "true",
            reduceTransparency = settings.get(SettingKeys.REDUCE_TRANSPARENCY) == "true",
            reducedMotion = settings.get(SettingKeys.REDUCED_MOTION) == "true",
            coachConsent = coachConsent(),
            coachProvider = coachProvider(),
            coachDisclosed = settings.get(SettingKeys.COACH_DISCLOSED) == "true",
            mcpEnabled = settings.get(SettingKeys.MCP_ENABLED) == "true",
            mcpPort = settings.get(SettingKeys.MCP_PORT)?.toIntOrNull()
                ?.takeIf { it in MCP_PORTS } ?: DEFAULT_MCP_PORT,
        )
    }

    fun coachConsent(): CoachConsent {
        val doc = settings.get(SettingKeys.COACH_CONSENT)?.let { raw ->
            runCatching { json.decodeFromString(ConsentDoc.serializer(), raw) }.getOrNull()
        }
        return doc?.let {
            CoachConsent(
                enabled = it.enabled,
                categories = it.categories.mapNotNull(CoachCategory::fromKey).toSet(),
                healthConnect = it.healthConnect,
            )
        } ?: CoachConsent.Off
    }

    fun setCoachConsent(consent: CoachConsent) {
        settings.put(
            SettingKeys.COACH_CONSENT,
            json.encodeToString(
                ConsentDoc.serializer(),
                ConsentDoc(
                    enabled = consent.enabled,
                    categories = consent.categories.map { it.key }.sorted(),
                    healthConnect = consent.healthConnect,
                ),
            ),
        )
    }

    fun coachProvider(): CoachProviderConfig {
        val doc = settings.get(SettingKeys.COACH_PROVIDER)?.let { raw ->
            runCatching { json.decodeFromString(ProviderDoc.serializer(), raw) }.getOrNull()
        }
        return doc?.let {
            CoachProviderConfig(
                kind = CoachProviderKind.fromKey(it.kind) ?: CoachProviderKind.ANTHROPIC,
                model = it.model.takeIf { model -> model.isNotBlank() }
                    ?: CoachProviderConfig.ANTHROPIC_DEEP_MODEL,
                baseUrl = it.baseUrl,
            )
        } ?: CoachProviderConfig()
    }

    fun setCoachProvider(config: CoachProviderConfig) {
        settings.put(
            SettingKeys.COACH_PROVIDER,
            json.encodeToString(
                ProviderDoc.serializer(),
                ProviderDoc(
                    kind = config.kind.key,
                    model = config.model,
                    baseUrl = config.baseUrl,
                ),
            ),
        )
    }

    fun setCoachDisclosed(accepted: Boolean) {
        settings.put(SettingKeys.COACH_DISCLOSED, accepted.toString())
    }

    fun setMcpEnabled(enabled: Boolean) {
        settings.put(SettingKeys.MCP_ENABLED, enabled.toString())
    }

    fun setMcpPort(port: Int) {
        if (port !in MCP_PORTS) return
        settings.put(SettingKeys.MCP_PORT, port.toString())
    }

    fun setWeightNeutral(enabled: Boolean) {
        val value = enabled.toString()
        settings.put(SettingKeys.WEIGHT_NEUTRAL, value)
        profiles.active()?.let { profile ->
            if (profile.weight_neutral != enabled) {
                profiles.upsert(
                    profile.copy(
                        weight_neutral = enabled,
                        updated_at = settings.updatedAt(SettingKeys.WEIGHT_NEUTRAL)
                            ?: profile.updated_at,
                    ),
                )
            }
        }
    }

    fun setModules(modules: Set<HealthModule>) {
        HealthModule.entries.forEach { module ->
            settings.put(moduleKey(module), (module in modules).toString())
        }
    }

    fun setModule(module: HealthModule, enabled: Boolean) {
        settings.put(moduleKey(module), enabled.toString())
    }

    fun setPreset(preset: ModulePreset) {
        settings.put(SettingKeys.MODULE_PRESET, preset.key)
    }

    fun setTheme(mode: ThemeMode) {
        settings.put(SettingKeys.THEME, mode.key)
    }

    fun setAccent(accent: OhAccent) {
        settings.put(SettingKeys.ACCENT_PRESET, accent.key)
    }

    fun setAppLock(enabled: Boolean) {
        settings.put(SettingKeys.APP_LOCK, enabled.toString())
    }

    fun setDemo(enabled: Boolean) {
        settings.put(SettingKeys.DEMO_DATA, enabled.toString())
    }

    /** One write for the three accessibility options (`DESIGN.md` §12.4.1). */
    fun setAccessibility(
        highContrast: Boolean,
        reduceTransparency: Boolean,
        reducedMotion: Boolean,
    ) {
        settings.put(SettingKeys.HIGH_CONTRAST, highContrast.toString())
        settings.put(SettingKeys.REDUCE_TRANSPARENCY, reduceTransparency.toString())
        settings.put(SettingKeys.REDUCED_MOTION, reducedMotion.toString())
    }

    fun markOnboardingComplete(tier: Int) {
        settings.put(SettingKeys.ONBOARDING_TIER, tier.toString())
        settings.put(SettingKeys.ONBOARDING_COMPLETE, "true")
    }

    @Serializable
    private data class ConsentDoc(
        val enabled: Boolean = false,
        val categories: List<String> = emptyList(),
        val healthConnect: Boolean = false,
    )

    @Serializable
    private data class ProviderDoc(
        val kind: String = CoachProviderKind.ANTHROPIC.key,
        val model: String = CoachProviderConfig.ANTHROPIC_DEEP_MODEL,
        val baseUrl: String = "",
    )

    private companion object {
        val json = Json { ignoreUnknownKeys = true }
        val MCP_PORTS = 8_765..8_775
        const val DEFAULT_MCP_PORT = 8_765

        fun moduleKey(module: HealthModule): String = "module.${module.key}.enabled"
    }
}
