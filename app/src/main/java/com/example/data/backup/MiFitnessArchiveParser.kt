package com.example.data.backup

import android.content.Context
import android.net.Uri
import com.example.data.BodyMetricEntry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream

data class MiFitnessParseResult(
    val totalWeightRecordsFound: Int,
    val newRecords: List<BodyMetricEntry>,
    val duplicatesCount: Int,
    val skippedInvalidCount: Int,
    val minDateMillis: Long?,
    val maxDateMillis: Long?,
    val profileHeightCm: Float?
)

object MiFitnessArchiveParser {

    private const val FITNESS_DATA_FILE_SUFFIX = "hlth_center_fitness_data.csv"
    private const val USER_PROFILE_FILE_SUFFIX = "user_member_profile.csv"

    /**
     * Streams and parses a Mi Fitness .zip archive from a content Uri.
     */
    suspend fun parseArchive(
        context: Context,
        zipUri: Uri,
        existingEntries: List<BodyMetricEntry>,
        userCurrentHeight: Float = 175f,
        onProgress: (lineCountProcessed: Int) -> Unit = {}
    ): Result<MiFitnessParseResult> = withContext(Dispatchers.IO) {
        val stream = try {
            context.contentResolver.openInputStream(zipUri)
        } catch (e: Exception) {
            return@withContext Result.failure(
                IllegalArgumentException("Не удалось открыть файл архива: ${e.localizedMessage}", e)
            )
        } ?: return@withContext Result.failure(
            IllegalArgumentException("Не удалось открыть файл архива. Проверьте разрешения доступа.")
        )

        stream.use { rawStream ->
            parseStream(rawStream, existingEntries, userCurrentHeight, onProgress)
        }
    }

    /**
     * Streams and parses a Mi Fitness .zip archive directly from any InputStream.
     * Extracts weight measurements from hlth_center_fitness_data.csv and height from user_member_profile.csv.
     */
    fun parseStream(
        rawInputStream: java.io.InputStream,
        existingEntries: List<BodyMetricEntry>,
        userCurrentHeight: Float = 175f,
        onProgress: (lineCountProcessed: Int) -> Unit = {}
    ): Result<MiFitnessParseResult> {
        var foundFitnessDataFile = false
        var profileHeightCm: Float? = null

        val parsedWeightPoints = mutableListOf<Pair<Long, Float>>() // Pair<TimestampSec, WeightKg>
        var duplicatesCount = 0
        var skippedInvalidCount = 0

        return try {
            // Check if file starts with ZIP magic bytes (PK\x03\x04 or 0x50 0x4B)
            val bufferedIn = rawInputStream.buffered(8192)
            bufferedIn.mark(8)
            val header = ByteArray(4)
            val readHeaderBytes = bufferedIn.read(header, 0, 4)
            bufferedIn.reset()

            if (readHeaderBytes < 4 || header[0] != 0x50.toByte() || header[1] != 0x4B.toByte()) {
                return Result.failure(
                    IllegalArgumentException("Выбранный файл не является ZIP-архивом. Пожалуйста, выберите исходный архив Mi Fitness (.zip).")
                )
            }

            ZipInputStream(bufferedIn).use { zis ->
                var entry: ZipEntry?
                var linesRead = 0

                while (zis.nextEntry.also { entry = it } != null) {
                    val currentEntry = entry ?: break
                    val entryName = currentEntry.name

                    if (entryName.endsWith(USER_PROFILE_FILE_SUFFIX, ignoreCase = true)) {
                        // Parse single-row profile for Height
                        val reader = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))
                        val headerLine = reader.readLine()
                        val dataLine = reader.readLine()

                        if (headerLine != null && dataLine != null) {
                            val headers = parseSimpleCsvLine(headerLine)
                            val values = parseSimpleCsvLine(dataLine)
                            val heightColIdx = headers.indexOfFirst { it.equals("height", ignoreCase = true) }
                            if (heightColIdx != -1 && heightColIdx < values.size) {
                                val h = values[heightColIdx].replace(",", ".").toFloatOrNull()
                                if (h != null && com.example.util.MetricValidationUtil.isHeightValid(h)) {
                                    profileHeightCm = h
                                }
                            }
                        }
                    } else if (entryName.endsWith(FITNESS_DATA_FILE_SUFFIX, ignoreCase = true)) {
                        foundFitnessDataFile = true
                        val reader = BufferedReader(InputStreamReader(zis, Charsets.UTF_8))

                        // Read line-by-line in a streaming fashion
                        var line: String?
                        // Read header to locate Key and Time columns
                        val headerLine = reader.readLine()
                        val headers = if (headerLine != null) parseSimpleCsvLine(headerLine) else emptyList()
                        val keyColIdx = headers.indexOfFirst { it.equals("Key", ignoreCase = true) }
                        val timeColIdx = headers.indexOfFirst { it.equals("Time", ignoreCase = true) }

                        while (reader.readLine().also { line = it } != null) {
                            val currentLine = line ?: break
                            linesRead++

                            if (linesRead % 20000 == 0) {
                                onProgress(linesRead)
                            }

                            // If Key column was identified, accept only if tokens[keyColIdx] == "weight"
                            if (keyColIdx != -1) {
                                val tokens = parseSimpleCsvLine(currentLine)
                                if (keyColIdx >= tokens.size || tokens[keyColIdx] != "weight") {
                                    continue
                                }
                            } else {
                                // Fallback: check if line contains "weight"
                                if (!currentLine.contains("weight", ignoreCase = true)) {
                                    continue
                                }
                            }

                            // Extract JSON substring between '{' and '}'
                            val jsonStart = currentLine.indexOf('{')
                            val jsonEnd = currentLine.lastIndexOf('}')
                            if (jsonStart == -1 || jsonEnd <= jsonStart) {
                                skippedInvalidCount++
                                continue
                            }

                            val rawJson = currentLine.substring(jsonStart, jsonEnd + 1).replace("\"\"", "\"")
                            try {
                                val obj = JSONObject(rawJson)
                                val weight = obj.optDouble("weight", Double.NaN).toFloat()
                                if (!com.example.util.MetricValidationUtil.isWeightValid(weight)) {
                                    skippedInvalidCount++
                                    continue
                                }

                                var timeSec = obj.optLong("time", 0L)
                                if (timeSec <= 0L) {
                                    // Fallback to column Time
                                    if (timeColIdx != -1) {
                                        val tokens = parseSimpleCsvLine(currentLine)
                                        if (timeColIdx < tokens.size) {
                                            timeSec = tokens[timeColIdx].trim().toLongOrNull() ?: 0L
                                        }
                                    } else {
                                        val tokens = parseSimpleCsvLine(currentLine)
                                        if (tokens.size >= 4) {
                                            timeSec = tokens[3].trim().toLongOrNull() ?: 0L
                                        }
                                    }
                                }

                                if (timeSec > 0L) {
                                    parsedWeightPoints.add(Pair(timeSec, weight))
                                } else {
                                    skippedInvalidCount++
                                }
                            } catch (_: Exception) {
                                skippedInvalidCount++
                            }
                        }
                    }
                }
            }

            if (!foundFitnessDataFile) {
                return Result.failure(
                    IllegalArgumentException(
                        "В архиве не найден файл $FITNESS_DATA_FILE_SUFFIX.\n\nСкорее всего, выбран не тот ZIP-архив. Убедитесь, что архив получен через пункт «Копия данных» приложения Mi Fitness."
                    )
                )
            }

            if (parsedWeightPoints.isEmpty()) {
                return Result.failure(
                    IllegalArgumentException("В архиве Mi Fitness не обнаружено записей веса (Key = 'weight').")
                )
            }

            // Deduplicate within the file and against existing database entries
            // Sort by timestamp ascending
            parsedWeightPoints.sortBy { it.first }

            val heightToUse = profileHeightCm ?: userCurrentHeight
            val newEntries = mutableListOf<BodyMetricEntry>()

            // Keep track of added items: Pair(timeSec, weight)
            val addedPoints = mutableListOf<Pair<Long, Float>>()

            // Prepare existing entries in seconds and weights
            val existingPoints = existingEntries.map { Pair(it.dateMillis / 1000L, it.weightKg) }

            var minDate: Long? = null
            var maxDate: Long? = null

            for ((timeSec, weight) in parsedWeightPoints) {
                val millis = timeSec * 1000L
                if (minDate == null || millis < minDate) minDate = millis
                if (maxDate == null || millis > maxDate) maxDate = millis

                // Deduplication check:
                // 1. Exact second match against existing entries or previously accepted entries in this archive
                // 2. OR within 10 minutes (600s) AND same weight (diff <= 0.1kg)
                val isDuplicateInExisting = existingPoints.any { (exTime, exWeight) ->
                    exTime == timeSec || (kotlin.math.abs(exTime - timeSec) <= 600L && kotlin.math.abs(exWeight - weight) <= 0.1001f)
                }

                val isDuplicateInAdded = addedPoints.any { (adTime, adWeight) ->
                    adTime == timeSec || (kotlin.math.abs(adTime - timeSec) <= 600L && kotlin.math.abs(adWeight - weight) <= 0.1001f)
                }

                if (isDuplicateInExisting || isDuplicateInAdded) {
                    duplicatesCount++
                } else {
                    addedPoints.add(Pair(timeSec, weight))
                    newEntries.add(
                        BodyMetricEntry(
                            id = 0L,
                            dateMillis = millis,
                            weightKg = weight,
                            heightCm = heightToUse,
                            notes = ""
                        )
                    )
                }
            }

            Result.success(
                MiFitnessParseResult(
                    totalWeightRecordsFound = parsedWeightPoints.size,
                    newRecords = newEntries,
                    duplicatesCount = duplicatesCount,
                    skippedInvalidCount = skippedInvalidCount,
                    minDateMillis = minDate,
                    maxDateMillis = maxDate,
                    profileHeightCm = profileHeightCm
                )
            )
        } catch (oom: OutOfMemoryError) {
            Result.failure(IllegalArgumentException("Не хватило оперативной памяти для обработки архива.", oom))
        } catch (e: Exception) {
            Result.failure(IllegalArgumentException("Архив повреждён или имеет непредвиденный формат: ${e.localizedMessage}", e))
        }
    }

    private fun parseSimpleCsvLine(line: String): List<String> {
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false
        var i = 0

        while (i < line.length) {
            val c = line[i]
            if (c == '"') {
                if (insideQuotes && i + 1 < line.length && line[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            } else if (c == ',' && !insideQuotes) {
                tokens.add(sb.toString().trim())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString().trim())
        return tokens
    }
}
