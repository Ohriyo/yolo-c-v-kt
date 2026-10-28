package com.yoloc.app.db.repositories

import com.yoloc.app.db.AppDatabaseHolder
import com.yoloc.app.db.SettingEntity
import com.yoloc.app.util.toJsString

enum class ThemeSetting(val key: String) {
    System("system"),
    Light("light"),
    Dark("dark"),
}

/** Typed view of the key/value settings table. */
data class AppSettings(
    val onboardingDone: Boolean,
    val confThreshold: Double,
    val iouThreshold: Double,
    val theme: ThemeSetting,
    val gpsOptIn: Boolean,
    val keepOriginalImages: Boolean,
    val activeModelId: String,
    /** Redesign 2026-10-28 (Kotlin only): the first name Home greets; empty when not given. */
    val userName: String = "",
)

/** How one setting is stored: its key, how a raw value parses (with fallback), how it formats. */
class SettingCodec<T>(val key: String, val parse: (String?) -> T, val format: (T) -> String)

private fun bool(key: String, fallback: Boolean) =
    SettingCodec(key, { raw -> if (raw == null) fallback else raw == "1" }, { if (it) "1" else "0" })

private fun num(key: String, fallback: Double) =
    SettingCodec(key, { raw -> raw?.toDoubleOrNull()?.takeIf { it.isFinite() } ?: fallback }, { it.toJsString() })

private fun text(key: String, fallback: String) = SettingCodec(key, { raw -> raw ?: fallback }, { it })

/**
 * Same keys, fallbacks and allowed values as RN's settingsRepository.ts. Fallbacks only apply if a
 * row is missing; the database's create callback and the seed write the real defaults.
 */
object SettingCodecs {
    val onboardingDone = bool("onboarding_done", false)
    val confThreshold = num("conf_threshold", 0.25)
    val iouThreshold = num("iou_threshold", 0.45)
    val theme = SettingCodec(
        "theme",
        { raw -> ThemeSetting.entries.firstOrNull { it.key == raw } ?: ThemeSetting.System },
        { it.key },
    )
    val gpsOptIn = bool("gps_opt_in", false)
    val keepOriginalImages = bool("keep_original_images", true)
    val activeModelId = text("active_model_id", "")

    /** Kotlin only (redesign 2026-10-28). A new key in the key/value table: no schema change. */
    val userName = text("user_name", "")
}

/** RN: src/db/repositories/settingsRepository.ts. */
class SettingsRepository(private val database: AppDatabaseHolder) {

    suspend fun getSettings(): AppSettings {
        val raw = database.get().settingsDao().all().associate { it.key to it.value }
        fun <T> read(codec: SettingCodec<T>) = codec.parse(raw[codec.key])
        return AppSettings(
            onboardingDone = read(SettingCodecs.onboardingDone),
            confThreshold = read(SettingCodecs.confThreshold),
            iouThreshold = read(SettingCodecs.iouThreshold),
            theme = read(SettingCodecs.theme),
            gpsOptIn = read(SettingCodecs.gpsOptIn),
            keepOriginalImages = read(SettingCodecs.keepOriginalImages),
            activeModelId = read(SettingCodecs.activeModelId),
            userName = read(SettingCodecs.userName),
        )
    }

    suspend fun <T> setSetting(codec: SettingCodec<T>, value: T, now: Long = System.currentTimeMillis()) {
        database.get().settingsDao().upsert(SettingEntity(codec.key, codec.format(value), now))
    }
}
