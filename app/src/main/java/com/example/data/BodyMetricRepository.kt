package com.example.data

import kotlinx.coroutines.flow.Flow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BodyMetricRepository(private val dao: BodyMetricDao) {

    val allEntriesDescending: Flow<List<BodyMetricEntry>> = dao.getAllEntriesDescending()
    val allEntriesAscending: Flow<List<BodyMetricEntry>> = dao.getAllEntriesAscending()

    suspend fun getLatestEntry(): BodyMetricEntry? = dao.getLatestEntry()

    suspend fun insert(entry: BodyMetricEntry): Long = dao.insert(entry)

    suspend fun update(entry: BodyMetricEntry) = dao.update(entry)

    suspend fun delete(entry: BodyMetricEntry) = dao.delete(entry)

    suspend fun deleteById(id: Long) = dao.deleteById(id)

    suspend fun deleteAll() = dao.deleteAll()

    suspend fun insertAll(entries: List<BodyMetricEntry>) = dao.insertAll(entries)

    companion object {
        const val UTF8_BOM = "\uFEFF"
    }

    /**
     * Exports a list of entries to a CSV formatted string.
     * Starts with UTF-8 BOM (\uFEFF) so Microsoft Excel (mobile & desktop)
     * correctly detects UTF-8 encoding and renders Cyrillic text without hieroglyphs.
     */
    fun exportToCsv(entries: List<BodyMetricEntry>): String {
        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val sb = StringBuilder()
        // Prepend UTF-8 BOM for Microsoft Excel Cyrillic encoding support
        sb.append(UTF8_BOM)
        // Header
        sb.append("ID,Дата,Время,Вес (кг),Рост (см),ИМТ,Грудь (см),Талия (см),Бёдра (см),Бицепс (см),Бедро (см),Шея (см),Заметки\n")

        for (entry in entries) {
            val d = Date(entry.dateMillis)
            val dateStr = dateFormat.format(d)
            val timeStr = timeFormat.format(d)
            val bmiFormatted = String.format(Locale.US, "%.1f", entry.bmi)
            val chestStr = entry.chestCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val waistStr = entry.waistCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val hipsStr = entry.hipsCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val bicepsStr = entry.bicepsCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val thighStr = entry.thighCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val neckStr = entry.neckCm?.let { String.format(Locale.US, "%.1f", it) } ?: ""
            val safeNotes = entry.notes.replace("\"", "\"\"")

            sb.append("${entry.id},")
                .append("$dateStr,")
                .append("$timeStr,")
                .append(String.format(Locale.US, "%.1f", entry.weightKg)).append(",")
                .append(String.format(Locale.US, "%.1f", entry.heightCm)).append(",")
                .append("$bmiFormatted,")
                .append("$chestStr,")
                .append("$waistStr,")
                .append("$hipsStr,")
                .append("$bicepsStr,")
                .append("$thighStr,")
                .append("$neckStr,")
                .append("\"$safeNotes\"\n")
        }
        return sb.toString()
    }

    /**
     * Exports a formatted text summary suitable for sharing to messengers or notes.
     */
    fun exportToTextSummary(entries: List<BodyMetricEntry>): String {
        if (entries.isEmpty()) return "Нет записей для формирования отчёта."
        val dateFormat = SimpleDateFormat("dd.MM.yyyy", Locale("ru"))
        val latest = entries.first()
        val oldest = entries.last()

        val sb = StringBuilder()
        sb.append("📊 Отчёт параметров тела — BodyMetrics\n")
        sb.append("📅 Период: ${dateFormat.format(Date(oldest.dateMillis))} — ${dateFormat.format(Date(latest.dateMillis))}\n")
        sb.append("Всего замеров: ${entries.size}\n\n")

        sb.append("📌 Текущие показатели (${dateFormat.format(Date(latest.dateMillis))}):\n")
        sb.append("• Вес: ${String.format(Locale.US, "%.1f", latest.weightKg)} кг\n")
        sb.append("• Рост: ${String.format(Locale.US, "%.0f", latest.heightCm)} см\n")
        sb.append("• ИМТ: ${String.format(Locale.US, "%.1f", latest.bmi)} (${latest.bmiCategory.titleRu})\n")
        latest.waistCm?.let { sb.append("• Талия: ${String.format(Locale.US, "%.1f", it)} см\n") }
        latest.chestCm?.let { sb.append("• Грудь: ${String.format(Locale.US, "%.1f", it)} см\n") }
        latest.hipsCm?.let { sb.append("• Бёдра: ${String.format(Locale.US, "%.1f", it)} см\n") }
        latest.bicepsCm?.let { sb.append("• Бицепс: ${String.format(Locale.US, "%.1f", it)} см\n") }
        latest.thighCm?.let { sb.append("• Бедро: ${String.format(Locale.US, "%.1f", it)} см\n") }
        latest.neckCm?.let { sb.append("• Шея: ${String.format(Locale.US, "%.1f", it)} см\n") }

        if (entries.size > 1) {
            val weightDiff = latest.weightKg - oldest.weightKg
            val sign = if (weightDiff > 0) "+" else ""
            sb.append("\n📈 Динамика веса за всё время: $sign${String.format(Locale.US, "%.1f", weightDiff)} кг\n")
        }

        sb.append("\n📝 Последние записи:\n")
        entries.take(5).forEach { entry ->
            val dStr = dateFormat.format(Date(entry.dateMillis))
            sb.append("• $dStr: ${String.format(Locale.US, "%.1f", entry.weightKg)} кг")
            entry.waistCm?.let { sb.append(", талия ${String.format(Locale.US, "%.1f", it)} см") }
            if (entry.notes.isNotBlank()) sb.append(" (${entry.notes})")
            sb.append("\n")
        }

        return sb.toString()
    }

    /**
     * Parses a CSV string and returns parsed BodyMetricEntry list.
     * Gracefully strips UTF-8 BOM (\uFEFF) if present, and auto-detects delimiter (, or ;).
     */
    fun parseCsv(csvText: String): List<BodyMetricEntry> {
        val cleanText = csvText.removePrefix(UTF8_BOM).removePrefix("\uFEFF").trim()
        val lines = cleanText.lines()
            .map { it.trim().removePrefix(UTF8_BOM).removePrefix("\uFEFF") }
            .filter { it.isNotBlank() }
        if (lines.size <= 1) return emptyList()

        val headerLine = lines.first()
        val detectedDelimiter = if (headerLine.count { it == ';' } > headerLine.count { it == ',' }) ';' else ','

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val altDateFormat = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
        val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())

        val result = mutableListOf<BodyMetricEntry>()
        // Skip header
        for (i in 1 until lines.size) {
            val line = lines[i]
            val tokens = parseCsvLine(line, detectedDelimiter)
            if (tokens.size >= 4) {
                try {
                    val dateStr = tokens.getOrNull(1)?.trim() ?: ""
                    val timeStr = tokens.getOrNull(2)?.trim() ?: ""
                    val parsedDate = try {
                        dateFormat.parse(dateStr)
                    } catch (e: Exception) {
                        try {
                            altDateFormat.parse(dateStr)
                        } catch (e2: Exception) {
                            Date()
                        }
                    } ?: Date()

                    val dateMillis = parsedDate.time
                    val weight = tokens.getOrNull(3)?.replace(",", ".")?.toFloatOrNull() ?: continue
                    val height = tokens.getOrNull(4)?.replace(",", ".")?.toFloatOrNull() ?: 175f

                    val chest = tokens.getOrNull(6)?.replace(",", ".")?.toFloatOrNull()
                    val waist = tokens.getOrNull(7)?.replace(",", ".")?.toFloatOrNull()
                    val hips = tokens.getOrNull(8)?.replace(",", ".")?.toFloatOrNull()
                    val biceps = tokens.getOrNull(9)?.replace(",", ".")?.toFloatOrNull()
                    val thigh = tokens.getOrNull(10)?.replace(",", ".")?.toFloatOrNull()
                    val neck = tokens.getOrNull(11)?.replace(",", ".")?.toFloatOrNull()
                    val notes = tokens.getOrNull(12)?.trim() ?: ""

                    result.add(
                        BodyMetricEntry(
                            id = 0L,
                            dateMillis = dateMillis,
                            weightKg = weight,
                            heightCm = height,
                            chestCm = chest,
                            waistCm = waist,
                            hipsCm = hips,
                            bicepsCm = biceps,
                            thighCm = thigh,
                            neckCm = neck,
                            notes = notes
                        )
                    )
                } catch (_: Exception) {
                    // Skip invalid line
                }
            }
        }
        return result
    }

    private fun parseCsvLine(line: String, defaultDelimiter: Char = ','): List<String> {
        val cleanLine = line.removePrefix(UTF8_BOM).removePrefix("\uFEFF")
        val tokens = mutableListOf<String>()
        val sb = StringBuilder()
        var insideQuotes = false
        var i = 0
        val delimiter = if (cleanLine.contains(";") && !cleanLine.contains(",")) ';' else defaultDelimiter

        while (i < cleanLine.length) {
            val c = cleanLine[i]
            if (c == '"') {
                if (insideQuotes && i + 1 < cleanLine.length && cleanLine[i + 1] == '"') {
                    sb.append('"')
                    i++
                } else {
                    insideQuotes = !insideQuotes
                }
            } else if (c == delimiter && !insideQuotes) {
                tokens.add(sb.toString())
                sb.clear()
            } else {
                sb.append(c)
            }
            i++
        }
        tokens.add(sb.toString())
        return tokens
    }

    /**
     * Generates realistic demo data spanning the last 30-60 days.
     */
    suspend fun generateDemoData() {
        val now = System.currentTimeMillis()
        val oneDayMillis = 86_400_000L
        val sampleEntries = listOf(
            BodyMetricEntry(
                dateMillis = now - (35 * oneDayMillis),
                weightKg = 82.5f,
                heightCm = 178f,
                chestCm = 104f,
                waistCm = 92f,
                hipsCm = 103f,
                bicepsCm = 36f,
                thighCm = 59f,
                neckCm = 39.5f,
                notes = "Старт программы тренировок"
            ),
            BodyMetricEntry(
                dateMillis = now - (28 * oneDayMillis),
                weightKg = 81.8f,
                heightCm = 178f,
                chestCm = 103.5f,
                waistCm = 91f,
                hipsCm = 102.5f,
                bicepsCm = 36f,
                thighCm = 58.5f,
                neckCm = 39.5f,
                notes = "Первая неделя позади, самочувствие отличное"
            ),
            BodyMetricEntry(
                dateMillis = now - (21 * oneDayMillis),
                weightKg = 80.9f,
                heightCm = 178f,
                chestCm = 103f,
                waistCm = 89.5f,
                hipsCm = 101.5f,
                bicepsCm = 36.5f,
                thighCm = 58f,
                neckCm = 39f,
                notes = "Талия уменьшилась на 2.5 см!"
            ),
            BodyMetricEntry(
                dateMillis = now - (14 * oneDayMillis),
                weightKg = 80.1f,
                heightCm = 178f,
                chestCm = 102.5f,
                waistCm = 88f,
                hipsCm = 100.5f,
                bicepsCm = 36.5f,
                thighCm = 57.5f,
                neckCm = 39f,
                notes = "Утренний замер натощак"
            ),
            BodyMetricEntry(
                dateMillis = now - (7 * oneDayMillis),
                weightKg = 79.4f,
                heightCm = 178f,
                chestCm = 102f,
                waistCm = 87f,
                hipsCm = 100f,
                bicepsCm = 37f,
                thighCm = 57f,
                neckCm = 38.5f,
                notes = "Виден рельеф, вес стабильно снижается"
            ),
            BodyMetricEntry(
                dateMillis = now,
                weightKg = 78.6f,
                heightCm = 178f,
                chestCm = 102f,
                waistCm = 85.5f,
                hipsCm = 99f,
                bicepsCm = 37.2f,
                thighCm = 56.5f,
                neckCm = 38.5f,
                notes = "Целевой прогресс достигнут!"
            )
        )
        dao.insertAll(sampleEntries)
    }
}
