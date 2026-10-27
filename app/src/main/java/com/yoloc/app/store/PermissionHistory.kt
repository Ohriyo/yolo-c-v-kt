package com.yoloc.app.store

import android.content.Context
import androidx.core.content.edit

/**
 * Whether the app has shown a permission's system prompt before. Android has no Expo-style
 * `canAskAgain`; together with `shouldShowRequestPermissionRationale` this tells "never asked"
 * from "blocked" (cameraAccessFrom, locationAccessFrom). Kept out of the database: it describes
 * this install's permission state, not the user's data, so Clear all data leaves it alone.
 */
class PermissionHistory(context: Context) {
    private val prefs = context.getSharedPreferences("permissions", Context.MODE_PRIVATE)

    var cameraAsked: Boolean
        get() = prefs.getBoolean(CAMERA, false)
        set(value) = prefs.edit { putBoolean(CAMERA, value) }

    var locationAsked: Boolean
        get() = prefs.getBoolean(LOCATION, false)
        set(value) = prefs.edit { putBoolean(LOCATION, value) }

    private companion object {
        const val CAMERA = "camera_asked"
        const val LOCATION = "location_asked"
    }
}

/**
 * After a permission request: does it now count as asked? A real refusal turns the rationale on,
 * so "refused, no rationale" on a first request means the dialog was dismissed (tapped outside),
 * which Android does not count; the prompt will still appear. A refusal after an earlier one with
 * no rationale is the permanent "don't ask again".
 */
fun countsAsAsked(granted: Boolean, showRationale: Boolean, refusedBefore: Boolean): Boolean =
    granted || showRationale || refusedBefore
