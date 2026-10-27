package com.yoloc.app.db

import androidx.room3.withWriteTransaction
import com.yoloc.app.features.detection.ClassInfo
import com.yoloc.app.features.detection.ModelConfig
import com.yoloc.app.util.toJsString
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

const val FINGERPRINT_KEY = "model_config_fingerprint"

/**
 * Seeds model_versions, classes and model-derived settings from model.config.json (RN: seed.ts).
 * Runs on every start but only writes when the config changed since the last seed, so a swapped
 * model is picked up without a migration. Returns true if it wrote anything.
 */
suspend fun seedFromModelConfig(
    db: AppDatabase,
    config: ModelConfig,
    catalog: Map<String, ClassInfo>,
    now: () -> Long = System::currentTimeMillis,
): Boolean {
    val dao = db.seedDao()
    val fingerprint = fingerprintOf(Json.encodeToString(ModelConfig.serializer(), config) + catalog)
    if (dao.setting(FINGERPRINT_KEY) == fingerprint) return false

    // Fail before writing anything if the config names a class we have no metadata for.
    val classes = config.classes.mapIndexed { id, key ->
        val info = catalog[key] ?: error("Class \"$key\" in model.config.json is missing from classCatalog")
        ClassEntity(
            id = id,
            key = key,
            displayName = info.displayName,
            severity = info.severity,
            colorToken = info.colorToken,
            summary = info.summary,
            symptoms = info.symptoms,
            recommendedActions = Json.encodeToString(ListSerializer(String.serializer()), info.recommendedActions),
        )
    }

    val t = now()
    db.withWriteTransaction {
        dao.deactivateOthers(config.id)
        dao.upsertActiveModel(
            id = config.id,
            displayName = config.displayName,
            fileName = config.file,
            inputSize = config.inputSize,
            numClasses = config.classes.size,
            valMap50 = config.metrics.valMap50,
            testMap50 = config.metrics.testMap50,
            earlyStageRecall = config.metrics.earlyStageRecall,
            installedAt = t,
        )
        // Class ids are model output indices and are referenced by saved detections, so classes
        // are upserted by id, never deleted. Reordering classes needs a retraining-aware migration.
        dao.upsertClasses(classes)
        // Thresholds are user-adjustable, so only fill them when missing. The active model always
        // follows the config.
        dao.insertIfMissing(SettingEntity("conf_threshold", config.confThreshold.toJsString(), t))
        dao.insertIfMissing(SettingEntity("iou_threshold", config.iouThreshold.toJsString(), t))
        dao.upsertSetting(SettingEntity("active_model_id", config.id, t))
        dao.upsertSetting(SettingEntity(FINGERPRINT_KEY, fingerprint, t))
    }
    return true
}

/** Small stable hash (FNV-1a) of the seed inputs; not security-sensitive. Ints wrap like Math.imul. */
fun fingerprintOf(text: String): String {
    var hash = 0x811c9dc5.toInt()
    for (ch in text) {
        hash = hash xor ch.code
        hash *= 0x01000193
    }
    return hash.toUInt().toString(16)
}
