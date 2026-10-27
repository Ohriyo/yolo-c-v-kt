package com.yoloc.app.features.detection

import android.content.Context
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

/** Shape of assets/models/model.config.json, the single source of truth for the model. */
@Serializable
data class ModelConfig(
    val id: String,
    val displayName: String,
    val file: String,
    val inputSize: Int,
    /** Class keys in model output index order. */
    val classes: List<String>,
    val confThreshold: Double,
    val iouThreshold: Double,
    /** Best detection below this shows the "Uncertain result" banner. */
    val lowConfidenceThreshold: Double,
    val letterboxPadValue: Int,
    val metrics: Metrics,
) {
    @Serializable
    data class Metrics(
        val valMap50: Double? = null,
        val testMap50: Double? = null,
        val earlyStageTestMap50: Double? = null,
        val earlyStageRecall: Double? = null,
    )

    companion object {
        const val ASSET_PATH = "models/model.config.json"

        fun parse(json: String): ModelConfig = Json.decodeFromString(serializer(), json)

        /** RN imports the JSON at bundle time; Kotlin reads it from assets at startup. */
        fun load(context: Context): ModelConfig =
            context.assets.open(ASSET_PATH).use { parse(it.readBytes().decodeToString()) }
    }
}
