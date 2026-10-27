package com.yoloc.app.features.detection

import android.content.Context
import com.yoloc.app.R

data class ClassInfo(
    val displayName: String,
    /** 0 healthy, 1 early, 2 infected. Drives the verdict. */
    val severity: Int,
    /** Status color and icon family (healthy | early | infected). Stored in classes.color_token. */
    val colorToken: String,
    val summary: String,
    val symptoms: String,
    val recommendedActions: List<String>,
)

/**
 * Per-class metadata, keyed by the class key in model.config.json. This seeds the `classes` table;
 * screens read advice from that table, never from here. A retrained model with the same class
 * list needs no change; a new class key must be added here first (seeding fails loudly otherwise).
 *
 * All agronomic text is placeholder until reviewed.
 */
fun classCatalog(context: Context): Map<String, ClassInfo> = mapOf(
    "early_stage" to ClassInfo(
        displayName = context.getString(R.string.status_early),
        severity = 1,
        colorToken = "early",
        summary = "TODO(verify with agronomist): Early signs of Frosty Pod Rot may be present.",
        symptoms = "TODO(verify with agronomist): describe early-stage symptoms.",
        recommendedActions = listOf(
            "TODO(verify with agronomist): first recommended action for early-stage pods.",
            "TODO(verify with agronomist): second recommended action for early-stage pods.",
        ),
    ),
    "fully_infected_pod" to ClassInfo(
        displayName = context.getString(R.string.status_infected),
        severity = 2,
        colorToken = "infected",
        summary = "TODO(verify with agronomist): The pod shows signs of advanced Frosty Pod Rot.",
        symptoms = "TODO(verify with agronomist): describe fully infected pod symptoms.",
        recommendedActions = listOf(
            "TODO(verify with agronomist): first recommended action for infected pods.",
            "TODO(verify with agronomist): second recommended action for infected pods.",
        ),
    ),
    "healthy_pod" to ClassInfo(
        displayName = context.getString(R.string.status_healthy),
        severity = 0,
        colorToken = "healthy",
        summary = "TODO(verify with agronomist): No signs of Frosty Pod Rot were detected on this pod.",
        symptoms = "TODO(verify with agronomist): describe what a healthy pod looks like.",
        recommendedActions = listOf("TODO(verify with agronomist): routine monitoring advice."),
    ),
)
