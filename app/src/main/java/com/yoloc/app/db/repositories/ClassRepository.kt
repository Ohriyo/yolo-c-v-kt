package com.yoloc.app.db.repositories

import com.yoloc.app.db.AppDatabaseHolder
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonPrimitive

data class DetectionClass(
    val id: Int,
    val key: String,
    val displayName: String,
    /** 0 healthy, 1 early, 2 infected. */
    val severity: Int,
    /** healthy | early | infected: status color and icon family. */
    val colorToken: String,
    val summary: String,
    val symptoms: String?,
    val recommendedActions: List<String>,
)

/** RN: src/db/repositories/classRepository.ts. Screens get advice from here, never the catalog. */
class ClassRepository(private val database: AppDatabaseHolder) {

    /** All classes in model index order. */
    suspend fun listClasses(): List<DetectionClass> =
        database.get().classDao().listClasses().map {
            DetectionClass(
                id = it.id,
                key = it.key,
                displayName = it.displayName,
                severity = it.severity,
                colorToken = it.colorToken,
                summary = it.summary,
                symptoms = it.symptoms,
                recommendedActions = parseActions(it.recommendedActions),
            )
        }
}

/** The JSON array of strings in classes.recommended_actions; [] when missing or malformed. */
internal fun parseActions(raw: String?): List<String> {
    if (raw.isNullOrEmpty()) return emptyList()
    val value = try {
        Json.parseToJsonElement(raw)
    } catch (_: IllegalArgumentException) {
        return emptyList()
    }
    return (value as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content }.orEmpty()
}
