package com.yoloc.app.features.onboarding

import androidx.annotation.StringRes
import com.yoloc.app.R

/** RN: strings.onboarding.slides, in order. */
enum class OnboardingSlide(val key: String, @param:StringRes val title: Int, @param:StringRes val body: Int) {
    Scan("scan", R.string.onboarding_scan_title, R.string.onboarding_scan_body),
    Offline("offline", R.string.onboarding_offline_title, R.string.onboarding_offline_body),
    Tips("tips", R.string.onboarding_tips_title, R.string.onboarding_tips_body),
    Aid("aid", R.string.onboarding_aid_title, R.string.onboarding_aid_body),
}

/** RN: onboarding/steps.ts. First-run steps: the welcome slides plus the permission primer. */
val ONBOARDING_STEPS = OnboardingSlide.entries.size + 1
