package com.example.data.backup

import com.example.data.BodyMetricEntry
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

data class JsonBackupRecord(
    val id: String,
    val type: String,
    val value: Float,
    val unit: String,
    val timestamp: Long, // Unix seconds UTC
    val note: String? = null,
    val source: String = "manual",
    val updatedAt: Long // Unix seconds UTC
)

data class JsonBackupSettings(
    val age: Int? = null,
    val targetWeightKg: Float? = null,
    val weeklyReminderEnabled: Boolean? = null
)

data class JsonImportSummary(
    val schemaVersion: Int,
    val createdAt: String,
    val totalRecordsFound: Int,
    val entriesToAdd: List<BodyMetricEntry>,
    val duplicatesCount: Int,
    val settingsToRestore: JsonBackupSettings?
)

object JsonBackupManager {

    const val CURRENT_SCHEMA_VERSION = 1

    /**
     * Serializes entries and user settings to clean, compact JSON backup.
     * Contains ONLY real user data without empty placeholders.
     */
    fun exportToJson(
        entries: List<BodyMetricEntry>,
        userAge: Int?,
        targetWeightKg: Float?,
        weeklyReminderEnabled: Boolean
    ): String {
        val root = JSONObject()
        root.put("schema_version", CURRENT_SCHEMA_VERSION)

        val isoFormat = SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss'Z'", Locale.US).apply {
            timeZone = TimeZone.getTimeZone("UTC")
        }
        root.put("created_at", isoFormat.format(Date()))

        // Settings (only non-null fields included)
        val hasSettings = userAge != null || targetWeightKg != null || weeklyReminderEnabled
        if (hasSettings) {
            val settingsObj = JSONObject()
            userAge?.let { settingsObj.put("age", it) }
            targetWeightKg?.let { settingsObj.put("target_weight_kg", it.toDouble()) }
            if (weeklyReminderEnabled) {
                settingsObj.put("weekly_reminder_enabled", true)
            }
            root.put("settings", settingsObj)
        }

        // Records
        val recordsArray = JSONArray()
        for (entry in entries) {
            val timestampSec = entry.dateMillis / 1000L
            val noteVal = entry.notes.trim().ifEmpty { null }

            // Weight record
            val weightObj = JSONObject().apply {
                put("id", "weight_${entry.id}_$timestampSec")
                put("type", "weight")
                put("value", entry.weightKg.toDouble())
                put("unit", "kg")
                put("timestamp", timestampSec)
                noteVal?.let { put("note", it) }
                put("source", "manual")
                put("updated_at", timestampSec)
            }
            recordsArray.put(weightObj)

            // Height record (only if non-zero)
            if (entry.heightCm > 0f) {
                val heightObj = JSONObject().apply {
                    put("id", "height_${entry.id}_$timestampSec")
                    put("type", "height")
                    put("value", entry.heightCm.toDouble())
                    put("unit", "cm")
                    put("timestamp", timestampSec)
                    put("source", "manual")
                    put("updated_at", timestampSec)
                }
                recordsArray.put(heightObj)
            }

            // Body measurements (only if present)
            entry.waistCm?.let {
                recordsArray.put(createMeasurementRecord("waist", it, timestampSec, entry.id))
            }
            entry.chestCm?.let {
                recordsArray.put(createMeasurementRecord("chest", it, timestampSec, entry.id))
            }
            entry.hipsCm?.let {
                recordsArray.put(createMeasurementRecord("hips", it, timestampSec, entry.id))
            }
            entry.bicepsCm?.let {
                recordsArray.put(createMeasurementRecord("biceps", it, timestampSec, entry.id))
            }
            entry.thighCm?.let {
                recordsArray.put(createMeasurementRecord("thigh", it, timestampSec, entry.id))
            }
            entry.neckCm?.let {
                recordsArray.put(createMeasurementRecord("neck", it, timestampSec, entry.id))
            }
        }

        root.put("records", recordsArray)
        return root.toString(2)
    }

    private fun createMeasurementRecord(
        type: String,
        value: Float,
        timestampSec: Long,
        entryId: Long
    ): JSONObject {
        return JSONObject().apply {
            put("id", "${type}_${entryId}_$timestampSec")
            put("type", type)
            put("value", value.toDouble())
            put("unit", "cm")
            put("timestamp", timestampSec)
            put("source", "manual")
            put("updated_at", timestampSec)
        }
    }

    /**
     * Parses, validates schema version, and calculates deduplication summary
     * against existing database entries.
     */
    fun parseAndValidateJson(
        jsonString: String,
        existingEntries: List<BodyMetricEntry>
    ): Result<JsonImportSummary> {
        return try {
            val root = JSONObject(jsonString)

            if (!root.has("schema_version")) {
                return Result.failure(IllegalArgumentException("Отсутствует поле schema_version. Файл не является резервной копией BodyMetrics."))
            }

            val schemaVersion = root.getInt("schema_version")
            if (schemaVersion > CURRENT_SCHEMA_VERSION) {
                return Result.failure(IllegalArgumentException("Версия схемы резервной копии ($schemaVersion) новее, чем поддерживается этим приложением ($CURRENT_SCHEMA_VERSION). Пожалуйста, обновите приложение."))
            }

            val createdAt = root.optString("created_at", "Неизвестно")

            // Settings
            var backupSettings: JsonBackupSettings? = null
            if (root.has("settings")) {
                val sObj = root.getJSONObject("settings")
                val age = if (sObj.has("age")) sObj.getInt("age") else null
                val targetWeight = if (sObj.has("target_weight_kg")) sObj.getDouble("target_weight_kg").toFloat() else null
                val reminder = if (sObj.has("weekly_reminder_enabled")) sObj.getBoolean("weekly_reminder_enabled") else null
                backupSettings = JsonBackupSettings(age, targetWeight, reminder)
            }

            // Records
            if (!root.has("records")) {
                return Result.failure(IllegalArgumentException("В файле отсутствует массив записей 'records'."))
            }

            val recordsArray = root.getJSONArray("records")
            val parsedRecords = mutableListOf<JsonBackupRecord>()

            for (i in 0 until recordsArray.length()) {
                val recObj = recordsArray.getJSONObject(i)
                val type = recObj.optString("type", "")
                val value = recObj.optDouble("value", 0.0).toFloat()
                val unit = recObj.optString("unit", "")
                val timestamp = recObj.optLong("timestamp", 0L)
                val note = recObj.optString("note").ifEmpty { null }
                val source = recObj.optString("source", "backup")
                val updatedAt = recObj.optLong("updated_at", timestamp)

                if (type.isNotBlank() && timestamp > 0L) {
                    parsedRecords.add(
                        JsonBackupRecord(
                            id = recObj.optString("id", "${type}_$timestamp"),
                            type = type,
                            value = value,
                            unit = unit,
                            timestamp = timestamp,
                            note = note,
                            source = source,
                            updatedAt = updatedAt
                        )
                    )
                }
            }

            if (parsedRecords.isEmpty()) {
                return Result.failure(IllegalArgumentException("В резервной копии не найдено ни одной записи."))
            }

            // Group existing entries by timestamp seconds
            val existingByTimestamp = existingEntries.associateBy { it.dateMillis / 1000L }

            // Group backup records by timestamp to reconstruct BodyMetricEntry
            val backupRecordsByTimestamp = parsedRecords.groupBy { it.timestamp }

            val entriesToAdd = mutableListOf<BodyMetricEntry>()
            var duplicatesCount = 0

            for ((timestampSec, records) in backupRecordsByTimestamp) {
                val existing = existingByTimestamp[timestampSec]
                val weightRec = records.find { it.type == "weight" }
                val heightRec = records.find { it.type == "height" }
                val waistRec = records.find { it.type == "waist" }
                val chestRec = records.find { it.type == "chest" }
                val hipsRec = records.find { it.type == "hips" }
                val bicepsRec = records.find { it.type == "biceps" }
                val thighRec = records.find { it.type == "thigh" }
                val neckRec = records.find { it.type == "neck" }

                val weightVal = weightRec?.value ?: existing?.weightKg
                if (weightVal == null || weightVal <= 0f) {
                    // Cannot form a body metric without weight
                    continue
                }

                val heightVal = heightRec?.value ?: existing?.heightCm ?: 175f
                val noteVal = weightRec?.note ?: existing?.notes ?: ""

                if (existing != null) {
                    // Check if backup has newer updated_at
                    val latestBackupUpdated = records.maxOfOrNull { it.updatedAt } ?: timestampSec
                    val existingUpdated = existing.dateMillis / 1000L

                    if (latestBackupUpdated <= existingUpdated) {
                        duplicatesCount++
                        continue
                    }
                }

                entriesToAdd.add(
                    BodyMetricEntry(
                        id = existing?.id ?: 0L,
                        dateMillis = timestampSec * 1000L,
                        weightKg = weightVal,
                        heightCm = heightVal,
                        chestCm = chestRec?.value ?: existing?.chestCm,
                        waistCm = waistRec?.value ?: existing?.waistCm,
                        hipsCm = hipsRec?.value ?: existing?.hipsCm,
                        bicepsCm = bicepsRec?.value ?: existing?.bicepsCm,
                        thighCm = thighRec?.value ?: existing?.thighCm,
                        neckCm = neckRec?.value ?: existing?.neckCm,
                        notes = noteVal
                    )
                )
            }

            Result.success(
                JsonImportSummary(
                    schemaVersion = schemaVersion,
                    createdAt = createdAt,
                    totalRecordsFound = parsedRecords.size,
                    entriesToAdd = entriesToAdd,
                    duplicatesCount = duplicatesCount,
                    settingsToRestore = backupSettings
                )
            )
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Ошибка разбора JSON: ${e.localizedMessage ?: "неверный формат"}", e))
        }
    }
}
