package com.yoloc.app.features.scans

/**
 * Camera access (RN: cameraAccess.ts):
 * - Granted: open the camera.
 * - Ask: never asked; show the system prompt.
 * - Denied: refused once; explain why and offer to ask again.
 * - Blocked: permanently refused; only system settings can fix it.
 */
enum class CameraAccess { Granted, Ask, Denied, Blocked }

/**
 * Android has no Expo-style `canAskAgain`, so it is derived: the app records that it asked
 * (`everAsked`), and after a refusal Android only shows a rationale while it will still prompt.
 * Asked + no rationale therefore means the prompt will not appear again.
 */
fun cameraAccessFrom(granted: Boolean, everAsked: Boolean, showRationale: Boolean): CameraAccess = when {
    granted -> CameraAccess.Granted
    !everAsked -> CameraAccess.Ask
    showRationale -> CameraAccess.Denied
    else -> CameraAccess.Blocked
}
