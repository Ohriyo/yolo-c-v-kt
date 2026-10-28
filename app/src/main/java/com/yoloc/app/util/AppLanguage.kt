package com.yoloc.app.util

import android.app.Activity
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import androidx.core.content.edit

/**
 * Redesign 2026-10-28 (Kotlin only; docs/PARITY.md): the app's own language, chosen in Settings.
 * The Filipino strings are a draft (values-fil). Until a choice is made the app follows the phone.
 */
enum class AppLanguage(val tag: String) {
    English("en"),
    Filipino("fil"),
}

/**
 * Android 13+ keeps the choice itself (per-app language, also shown in the phone's settings) and
 * recreates the screen. Older phones keep it here and apply it to the activity's context.
 */
object AppLanguages {
    private const val PREFS = "app_language"
    private const val KEY = "tag"

    /** The language [context]'s resources are in: Filipino for fil (and its old code tl), otherwise English. */
    fun current(context: Context): AppLanguage {
        val language = context.resources.configuration.locales[0]?.language
        return if (language == "fil" || language == "tl") AppLanguage.Filipino else AppLanguage.English
    }

    fun set(activity: Activity, language: AppLanguage) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(language.tag)
        } else {
            activity.getSharedPreferences(PREFS, Context.MODE_PRIVATE).edit { putString(KEY, language.tag) }
            activity.recreate()
        }
    }

    /** Before Android 13: the saved choice applied to an activity's base context (attachBaseContext). */
    fun wrap(base: Context): Context {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) return base
        val tag = base.getSharedPreferences(PREFS, Context.MODE_PRIVATE).getString(KEY, null) ?: return base
        val config = Configuration(base.resources.configuration).apply { setLocales(LocaleList.forLanguageTags(tag)) }
        return base.createConfigurationContext(config)
    }
}
