package com.example

import com.example.data.BmiCategory
import com.example.data.BodyMetricEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleRobolectricTest {

    @Test
    fun testBmiCalculationAndCategory() {
        val entry = BodyMetricEntry(
            id = 1L,
            dateMillis = System.currentTimeMillis(),
            weightKg = 70f,
            heightCm = 175f,
            waistCm = 80f
        )
        assertTrue(entry.bmi > 22.8f && entry.bmi < 22.9f)
        assertEquals(BmiCategory.NORMAL, entry.bmiCategory)

        val heavyEntry = entry.copy(weightKg = 95f)
        assertEquals(BmiCategory.OBESE, heavyEntry.bmiCategory)
    }

    @Test
    fun testEntryDefaultValues() {
        val entry = BodyMetricEntry(
            dateMillis = 1000L,
            weightKg = 65f,
            heightCm = 170f
        )
        assertEquals(0L, entry.id)
        assertEquals("", entry.notes)
        assertEquals(null, entry.chestCm)
        assertEquals(null, entry.waistCm)
    }

    @Test
    fun testGoalPredictionCalculation() {
        val now = 1700000000000L
        val oneWeekMillis = 7 * 86_400_000L

        // Two entries: from 80kg to 78kg over 2 weeks = -1.0 kg/week
        val entries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 2 * oneWeekMillis, weightKg = 80f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now, weightKg = 78f, heightCm = 175f)
        )

        // Target: 70kg (8kg remaining, at 1kg/week pace = ~8 weeks)
        val pred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 78f,
            targetWeight = 70f,
            allEntries = entries
        )

        assertTrue(pred.isMovingTowardsGoal)
        assertEquals(8f, pred.remainingKg, 0.01f)
        assertTrue(pred.weeklyPace != null && pred.weeklyPace!! < -0.9 && pred.weeklyPace!! > -1.1)
        assertTrue(pred.weeksRemaining != null && pred.weeksRemaining!! in 7.9..8.1)
        assertTrue(pred.predictedDate != null)
        assertTrue(!pred.formattedPredictedDate.isNullOrBlank())

        // Target already reached
        val achievedPred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 70f,
            targetWeight = 70f,
            allEntries = entries
        )
        assertTrue(achievedPred.isAchieved)

        // Reverse trend: current 78, target 85 (gain needed), but pace is negative (-1.0 kg/week)
        val reversePred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 78f,
            targetWeight = 85f,
            allEntries = entries
        )
        assertTrue(!reversePred.isMovingTowardsGoal)
        assertEquals(null, reversePred.predictedDate)
    }

    @Test
    fun testRolling7DayWindowAndFallback() {
        val now = 1700000000000L
        val oneDayMillis = 86_400_000L

        // Old entry 90 days ago: 100kg
        // Entry 5 days ago: 80kg
        // Entry today (now): 78kg (-2kg loss over 5 days: weeklyPace = (-2.0 / 5.0) * 7.0 = -2.8 kg/week)
        val entries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 90 * oneDayMillis, weightKg = 100f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now - 5 * oneDayMillis, weightKg = 80f, heightCm = 175f),
            BodyMetricEntry(id = 3, dateMillis = now, weightKg = 78f, heightCm = 175f)
        )

        val pred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 78f,
            targetWeight = 70f,
            allEntries = entries
        )

        assertTrue(pred.isMovingTowardsGoal)
        assertTrue(pred.weeklyPace != null)
        assertEquals(-2.8, pred.weeklyPace!!, 0.05)
        assertEquals("Снижение за 7 дн.", pred.paceDescription)
    }

    @Test
    fun testTrackableMetricHeight() {
        val entry = BodyMetricEntry(
            id = 1L,
            dateMillis = 1000L,
            weightKg = 70f,
            heightCm = 182.5f
        )
        val metric = com.example.data.TrackableMetric.HEIGHT
        assertEquals("height", metric.key)
        assertEquals("Рост", metric.labelRu)
        assertEquals("см", metric.unit)
        assertEquals(182.5f, metric.extractValue(entry) ?: 0f, 0.01f)
    }

    @Test
    fun testPaceTrendAccelerationAndDeceleration() {
        val now = 1700000000000L
        val oneDayMillis = 86_400_000L

        // Accelerating case across 7-day rolling windows:
        // Window 1 (previous 7-day window [-14d, -7d]): from day -14 (84kg) to day -7 (83kg) -> -1kg / week
        // Window 2 (current 7-day window [-7d, 0d]): from day -7 (83kg) to day 0 (81kg) -> -2kg / week
        val acceleratingEntries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 14 * oneDayMillis, weightKg = 84f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now - 7 * oneDayMillis, weightKg = 83f, heightCm = 175f),
            BodyMetricEntry(id = 3, dateMillis = now, weightKg = 81f, heightCm = 175f)
        )

        val accelPred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 81f,
            targetWeight = 70f,
            allEntries = acceleratingEntries
        )

        assertEquals("▲ Ускорение", accelPred.paceTrendComparison)
        assertTrue(accelPred.prevWeeklyPace != null)
        assertTrue(accelPred.weeklyPace != null)

        // Decelerating case across 7-day rolling windows:
        // Window 1 (previous 7-day window [-14d, -7d]): from day -14 (86kg) to day -7 (84kg) -> -2kg / week
        // Window 2 (current 7-day window [-7d, 0d]): from day -7 (84kg) to day 0 (83.5kg) -> -0.5kg / week
        val deceleratingEntries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 14 * oneDayMillis, weightKg = 86f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now - 7 * oneDayMillis, weightKg = 84f, heightCm = 175f),
            BodyMetricEntry(id = 3, dateMillis = now, weightKg = 83.5f, heightCm = 175f)
        )

        val decelPred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 83.5f,
            targetWeight = 70f,
            allEntries = deceleratingEntries
        )

        assertEquals("▼ Замедление", decelPred.paceTrendComparison)
    }

    @Test
    fun testMetricValidationInputFiltering() {
        // Weight input filtering (max 500.0, 1 decimal place)
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("70", 500f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("70.5", 500f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("70,5", 500f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("500", 500f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("500.0", 500f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("", 500f))

        // Large numbers or invalid decimals blocked
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("500.1", 500f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("501", 500f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("9999", 500f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("-10", 500f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("70.55", 500f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("70..5", 500f))

        // Height input filtering (max 300.0)
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("180", 300f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("300", 300f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("301", 300f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("1000", 300f))

        // Circumferences (max 250.0)
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("95.5", 250f))
        assertTrue(com.example.util.MetricValidationUtil.isValidDecimalInput("250.0", 250f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("250.5", 250f))
        assertFalse(com.example.util.MetricValidationUtil.isValidDecimalInput("350", 250f))
    }

    @Test
    fun testMetricValidationRanges() {
        // Weight range: 20..500 kg
        assertTrue(com.example.util.MetricValidationUtil.isWeightValid(20f))
        assertTrue(com.example.util.MetricValidationUtil.isWeightValid(75.5f))
        assertTrue(com.example.util.MetricValidationUtil.isWeightValid(500f))
        assertFalse(com.example.util.MetricValidationUtil.isWeightValid(19.9f))
        assertFalse(com.example.util.MetricValidationUtil.isWeightValid(500.1f))
        assertFalse(com.example.util.MetricValidationUtil.isWeightValid(0f))
        assertFalse(com.example.util.MetricValidationUtil.isWeightValid(null))

        // Height range: 50..300 cm
        assertTrue(com.example.util.MetricValidationUtil.isHeightValid(50f))
        assertTrue(com.example.util.MetricValidationUtil.isHeightValid(180f))
        assertTrue(com.example.util.MetricValidationUtil.isHeightValid(300f))
        assertFalse(com.example.util.MetricValidationUtil.isHeightValid(49.9f))
        assertFalse(com.example.util.MetricValidationUtil.isHeightValid(300.1f))
        assertFalse(com.example.util.MetricValidationUtil.isHeightValid(null))

        // Circumference range: 10..250 cm (optional)
        assertTrue(com.example.util.MetricValidationUtil.isMeasurementValid(null))
        assertTrue(com.example.util.MetricValidationUtil.isMeasurementValid(10f))
        assertTrue(com.example.util.MetricValidationUtil.isMeasurementValid(85f))
        assertTrue(com.example.util.MetricValidationUtil.isMeasurementValid(250f))
        assertFalse(com.example.util.MetricValidationUtil.isMeasurementValid(9.9f))
        assertFalse(com.example.util.MetricValidationUtil.isMeasurementValid(250.1f))

        // Age range: 14..120
        assertTrue(com.example.util.MetricValidationUtil.isAgeValid(null))
        assertTrue(com.example.util.MetricValidationUtil.isAgeValid(14))
        assertTrue(com.example.util.MetricValidationUtil.isAgeValid(30))
        assertTrue(com.example.util.MetricValidationUtil.isAgeValid(120))
        assertFalse(com.example.util.MetricValidationUtil.isAgeValid(13))
        assertFalse(com.example.util.MetricValidationUtil.isAgeValid(121))
    }

    @Test
    fun testCsvUtf8BomAndEncoding() {
        val dummyDao = object : com.example.data.BodyMetricDao {
            override fun getAllEntriesDescending() = kotlinx.coroutines.flow.emptyFlow<List<BodyMetricEntry>>()
            override fun getAllEntriesAscending() = kotlinx.coroutines.flow.emptyFlow<List<BodyMetricEntry>>()
            override suspend fun getLatestEntry(): BodyMetricEntry? = null
            override suspend fun getEntryById(id: Long): BodyMetricEntry? = null
            override suspend fun insert(entry: BodyMetricEntry): Long = 0L
            override suspend fun insertAll(entries: List<BodyMetricEntry>) {}
            override suspend fun update(entry: BodyMetricEntry) {}
            override suspend fun delete(entry: BodyMetricEntry) {}
            override suspend fun deleteById(id: Long) {}
            override suspend fun deleteAll() {}
        }
        val repository = com.example.data.BodyMetricRepository(dummyDao)

        val entry = BodyMetricEntry(
            id = 1L,
            dateMillis = 1700000000000L,
            weightKg = 75.5f,
            heightCm = 178f,
            chestCm = 100f,
            waistCm = 82f,
            hipsCm = 98f,
            bicepsCm = 36f,
            thighCm = 56f,
            neckCm = 38f,
            notes = "Отличная тренировка! Замеры после бега."
        )

        val csv = repository.exportToCsv(listOf(entry))

        // Must start with UTF-8 BOM
        assertTrue("CSV must start with UTF-8 BOM", csv.startsWith("\uFEFF"))
        assertTrue("CSV must contain Cyrillic header", csv.contains("Дата"))
        assertTrue("CSV must contain Cyrillic notes", csv.contains("Отличная тренировка! Замеры после бега."))

        // Convert to UTF-8 byte array and verify BOM bytes
        val utf8Bytes = csv.toByteArray(Charsets.UTF_8)
        assertTrue("File must be at least 3 bytes long", utf8Bytes.size >= 3)
        assertEquals(0xEF.toByte(), utf8Bytes[0])
        assertEquals(0xBB.toByte(), utf8Bytes[1])
        assertEquals(0xBF.toByte(), utf8Bytes[2])

        // Verify roundtrip parsing with BOM
        val parsedWithBom = repository.parseCsv(csv)
        assertEquals(1, parsedWithBom.size)
        assertEquals(75.5f, parsedWithBom[0].weightKg, 0.01f)
        assertEquals(178f, parsedWithBom[0].heightCm, 0.01f)
        assertEquals("Отличная тренировка! Замеры после бега.", parsedWithBom[0].notes)

        // Verify roundtrip parsing without BOM (backward compatibility)
        val csvWithoutBom = csv.removePrefix("\uFEFF")
        val parsedWithoutBom = repository.parseCsv(csvWithoutBom)
        assertEquals(1, parsedWithoutBom.size)
        assertEquals(75.5f, parsedWithoutBom[0].weightKg, 0.01f)

        // Verify semicolon delimiter support (used by European/Russian Excel)
        val semicolonCsv = "\uFEFFID;Дата;Время;Вес (кг);Рост (см);ИМТ;Грудь (см);Талия (см);Бёдра (см);Бицепс (см);Бедро (см);Шея (см);Заметки\n" +
                "1;2024-01-15;10:30;82,5;180,0;25.5;102;85;100;37;58;39;\"Заметка с запятой, внутри текста\"\n"
        val parsedSemicolon = repository.parseCsv(semicolonCsv)
        assertEquals(1, parsedSemicolon.size)
        assertEquals(82.5f, parsedSemicolon[0].weightKg, 0.01f)
        assertEquals(180f, parsedSemicolon[0].heightCm, 0.01f)
        assertEquals("Заметка с запятой, внутри текста", parsedSemicolon[0].notes)
    }

    @Test
    fun testCustomDateRangeFiltering() {
        assertEquals("Свой период", com.example.ui.TimeFilter.CUSTOM.titleRu)
        assertEquals(null, com.example.ui.TimeFilter.CUSTOM.days)

        val oneDayMillis = 86_400_000L
        val baseTime = 1700000000000L // arbitrary fixed timestamp

        val entry1 = BodyMetricEntry(id = 1L, dateMillis = baseTime - 40 * oneDayMillis, weightKg = 85f, heightCm = 180f)
        val entry2 = BodyMetricEntry(id = 2L, dateMillis = baseTime - 15 * oneDayMillis, weightKg = 82f, heightCm = 180f)
        val entry3 = BodyMetricEntry(id = 3L, dateMillis = baseTime - 5 * oneDayMillis, weightKg = 80f, heightCm = 180f)
        val entries = listOf(entry1, entry2, entry3)

        // Custom range: from 20 days ago to 10 days ago -> should match ONLY entry2
        val rangeStart = baseTime - 20 * oneDayMillis
        val rangeEnd = baseTime - 10 * oneDayMillis

        val filtered = entries.filter { it.dateMillis in rangeStart..rangeEnd }
        assertEquals(1, filtered.size)
        assertEquals(2L, filtered[0].id)
        assertEquals(82f, filtered[0].weightKg, 0.01f)

        // Inclusive boundaries test
        val exactStartMatch = entries.filter { it.dateMillis in (entry3.dateMillis)..(entry3.dateMillis + 1000) }
        assertEquals(1, exactStartMatch.size)
        assertEquals(3L, exactStartMatch[0].id)
    }

    @Test
    fun testCalendarDayComparisonAndRange() {
        val day1 = com.example.ui.components.CalendarDay(2026, 1, 10)
        val day2 = com.example.ui.components.CalendarDay(2026, 1, 15)
        val day3 = com.example.ui.components.CalendarDay(2026, 2, 1)

        assertTrue(day1 < day2)
        assertTrue(day2 < day3)
        assertTrue(day1 < day3)

        // Millis roundtrip
        val millis = day1.toStartOfDayMillis()
        val restored = com.example.ui.components.CalendarDay.fromMillis(millis)
        assertEquals(day1.year, restored.year)
        assertEquals(day1.month, restored.month)
        assertEquals(day1.day, restored.day)

        // End of day is after start of day
        assertTrue(day1.toEndOfDayMillis() > day1.toStartOfDayMillis())
    }

    @Test
    fun testAgeDeclensionAndDisplay() {
        fun formatAgeWord(age: Int): String {
            return when {
                age % 100 in 11..14 -> "лет"
                age % 10 == 1 -> "год"
                age % 10 in 2..4 -> "года"
                else -> "лет"
            }
        }

        assertEquals("год", formatAgeWord(21))
        assertEquals("года", formatAgeWord(22))
        assertEquals("года", formatAgeWord(24))
        assertEquals("лет", formatAgeWord(25))
        assertEquals("лет", formatAgeWord(11))
        assertEquals("лет", formatAgeWord(14))
        assertEquals("лет", formatAgeWord(16))
        assertEquals("лет", formatAgeWord(30))
    }

    @Test
    fun testJsonBackupExportAndValidation() {
        val entry1 = BodyMetricEntry(
            id = 10L,
            dateMillis = 1700000000000L, // 1700000000 sec
            weightKg = 76.5f,
            heightCm = 178f,
            waistCm = 82f,
            notes = "Заметка о весе"
        )
        val entry2 = BodyMetricEntry(
            id = 11L,
            dateMillis = 1700086400000L,
            weightKg = 76.0f,
            heightCm = 178f
            // no waist or other measurements
        )

        // 1. Export JSON
        val exportedJson = com.example.data.backup.JsonBackupManager.exportToJson(
            entries = listOf(entry1, entry2),
            userAge = 28,
            targetWeightKg = 72.0f,
            weeklyReminderEnabled = true
        )

        assertTrue(exportedJson.contains("\"schema_version\": 1"))
        assertTrue(exportedJson.contains("\"created_at\":"))
        assertTrue(exportedJson.contains("\"age\": 28"))
        assertTrue(exportedJson.contains("\"target_weight_kg\": 72"))
        assertTrue(exportedJson.contains("\"weekly_reminder_enabled\": true"))
        assertTrue(exportedJson.contains("\"type\": \"weight\""))
        assertTrue(exportedJson.contains("\"type\": \"waist\""))
        assertTrue(exportedJson.contains("\"value\": 76.5"))
        assertTrue(exportedJson.contains("\"unit\": \"kg\""))
        assertTrue(exportedJson.contains("\"timestamp\": 1700000000"))
        assertTrue(exportedJson.contains("\"source\": \"manual\""))
        assertTrue(exportedJson.contains("\"updated_at\": 1700000000"))

        // Entry2 had no chest, hips, neck etc - ensure empty measurement sections are not exported
        assertFalse(exportedJson.contains("\"type\": \"chest\""))
        assertFalse(exportedJson.contains("\"type\": \"hips\""))

        // 2. Parse and Validate JSON
        val parseResult = com.example.data.backup.JsonBackupManager.parseAndValidateJson(
            jsonString = exportedJson,
            existingEntries = emptyList()
        )
        assertTrue(parseResult.isSuccess)
        val summary = parseResult.getOrThrow()
        assertEquals(1, summary.schemaVersion)
        assertEquals(2, summary.entriesToAdd.size)
        assertEquals(0, summary.duplicatesCount)
        assertEquals(28, summary.settingsToRestore?.age)
        assertEquals(72.0f, summary.settingsToRestore?.targetWeightKg ?: 0f, 0.01f)
        assertEquals(true, summary.settingsToRestore?.weeklyReminderEnabled)

        // 3. Deduplication: when importing again against existing entries, should detect duplicates
        val deduplicatedResult = com.example.data.backup.JsonBackupManager.parseAndValidateJson(
            jsonString = exportedJson,
            existingEntries = listOf(entry1, entry2)
        )
        assertTrue(deduplicatedResult.isSuccess)
        val dedupSummary = deduplicatedResult.getOrThrow()
        assertEquals(2, dedupSummary.duplicatesCount)
        assertEquals(0, dedupSummary.entriesToAdd.size)

        // 4. Schema version checks
        val invalidSchemaJson = "{\"created_at\": \"2026-11-05T00:00:00Z\", \"records\": []}"
        val failedMissingSchema = com.example.data.backup.JsonBackupManager.parseAndValidateJson(
            invalidSchemaJson,
            emptyList()
        )
        assertTrue(failedMissingSchema.isFailure)

        val futureSchemaJson = "{\"schema_version\": 99, \"created_at\": \"2026-11-05T00:00:00Z\", \"records\": []}"
        val failedFutureSchema = com.example.data.backup.JsonBackupManager.parseAndValidateJson(
            futureSchemaJson,
            emptyList()
        )
        assertTrue(failedFutureSchema.isFailure)
    }

    @Test
    fun testMiFitnessArchiveParsing() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            // Entry 1: user_member_profile.csv
            val profileEntry = java.util.zip.ZipEntry("20261104_123456_MiFitness_user_member_profile.csv")
            zos.putNextEntry(profileEntry)
            val profileCsv = "Uid,Name,Sex,Birth,Height,Weight,Relation,XiaomiId,Region,SpecialMark,Icon\n" +
                    "1001,\"Test User\",1,\"1995-05-10\",182.5,79.0,0,12345,\"RU\",0,\"icon.png\"\n"
            zos.write(profileCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()

            // Entry 2: hlth_center_fitness_data.csv
            val fitnessEntry = java.util.zip.ZipEntry("20261104_123456_MiFitness_hlth_center_fitness_data.csv")
            zos.putNextEntry(fitnessEntry)
            val fitnessCsv = "Uid,Sid,Key,Time,Value,UpdateTime\n" +
                    "1001,1,heart_rate,1793683000,\"{\"\"bpm\"\":72}\",1793683000\n" +
                    "1001,1,weight,1793683435,\"{\"\"bmi\"\":24.2,\"\"time\"\":1793683435,\"\"weight\"\":78.65}\",1793683435\n" +
                    "1001,1,steps,1793683500,\"{\"\"step\"\":5000}\",1793683500\n" +
                    "1001,1,weight,1793683900,\"{\"\"bmi\"\":24.1,\"\"time\"\":1793683900,\"\"weight\"\":78.20}\",1793683900\n" +
                    "1001,1,weight,1793684000,\"{\"\"bmi\"\":0,\"\"time\"\":1793684000,\"\"weight\"\":0}\",1793684000\n" // weight <= 0 must be skipped
            zos.write(fitnessCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        // Existing entry for deduplication (same as the second record)
        val existing = listOf(
            BodyMetricEntry(
                id = 5L,
                dateMillis = 1793683900L * 1000L,
                weightKg = 78.20f,
                heightCm = 180f
            )
        )

        var progressReported = 0
        val inputStream = java.io.ByteArrayInputStream(baos.toByteArray())
        val result = com.example.data.backup.MiFitnessArchiveParser.parseStream(
            rawInputStream = inputStream,
            existingEntries = existing,
            userCurrentHeight = 175f,
            onProgress = { progressReported = it }
        )

        assertTrue(result.isSuccess)
        val parseSummary = result.getOrThrow()

        // Total weight records found (excluding weight <= 0): 2
        assertEquals(2, parseSummary.totalWeightRecordsFound)
        // Duplicates count (1 is already in existing): 1
        assertEquals(1, parseSummary.duplicatesCount)
        // New records to add: 1 (weight 78.65)
        assertEquals(1, parseSummary.newRecords.size)
        assertEquals(78.65f, parseSummary.newRecords[0].weightKg, 0.01f)
        assertEquals(1793683435L * 1000L, parseSummary.newRecords[0].dateMillis)
        // Height extracted from profile (182.5 cm) applied to new records
        assertEquals(182.5f, parseSummary.profileHeightCm ?: 0f, 0.01f)
        assertEquals(182.5f, parseSummary.newRecords[0].heightCm, 0.01f)
    }

    @Test
    fun testMiFitnessArchiveNonZipRejected() {
        val nonZipStream = java.io.ByteArrayInputStream("This is definitely not a zip file.".toByteArray(Charsets.UTF_8))
        val result = com.example.data.backup.MiFitnessArchiveParser.parseStream(
            rawInputStream = nonZipStream,
            existingEntries = emptyList()
        )

        assertTrue("Non-zip file must fail with clear error", result.isFailure)
        assertTrue(result.exceptionOrNull()?.message?.contains("ZIP") == true)
    }

    @Test
    fun testMiFitnessArchiveDeduplicationWithin10Minutes() {
        val baos = java.io.ByteArrayOutputStream()
        java.util.zip.ZipOutputStream(baos).use { zos ->
            val fitnessEntry = java.util.zip.ZipEntry("20261104_123456_MiFitness_hlth_center_fitness_data.csv")
            zos.putNextEntry(fitnessEntry)
            // Three records:
            // 1. time 1000, weight 75.0
            // 2. time 1300 (5 mins later = within 10 min), weight 75.05 (diff <= 0.1 kg) -> duplicate
            // 3. time 2000 (16 mins later), weight 75.0 -> valid new record
            val fitnessCsv = "Uid,Sid,Key,Time,Value,UpdateTime\n" +
                    "1001,1,weight,1000,\"{\"\"time\"\":1000,\"\"weight\"\":75.0}\",1000\n" +
                    "1001,1,weight,1300,\"{\"\"time\"\":1300,\"\"weight\"\":75.05}\",1300\n" +
                    "1001,1,weight,2000,\"{\"\"time\"\":2000,\"\"weight\"\":75.0}\",2000\n"
            zos.write(fitnessCsv.toByteArray(Charsets.UTF_8))
            zos.closeEntry()
        }

        val result = com.example.data.backup.MiFitnessArchiveParser.parseStream(
            rawInputStream = java.io.ByteArrayInputStream(baos.toByteArray()),
            existingEntries = emptyList()
        )
        assertTrue(result.isSuccess)
        val summary = result.getOrThrow()
        assertEquals(3, summary.totalWeightRecordsFound)
        assertEquals(1, summary.duplicatesCount)
        assertEquals(2, summary.newRecords.size)
    }

    @Test
    fun testCsvDetailedValidationAndDeduplication() {
        val dummyDao = object : com.example.data.BodyMetricDao {
            override fun getAllEntriesDescending() = kotlinx.coroutines.flow.emptyFlow<List<BodyMetricEntry>>()
            override fun getAllEntriesAscending() = kotlinx.coroutines.flow.emptyFlow<List<BodyMetricEntry>>()
            override suspend fun getLatestEntry(): BodyMetricEntry? = null
            override suspend fun getEntryById(id: Long): BodyMetricEntry? = null
            override suspend fun insert(entry: BodyMetricEntry): Long = 0L
            override suspend fun insertAll(entries: List<BodyMetricEntry>) {}
            override suspend fun update(entry: BodyMetricEntry) {}
            override suspend fun delete(entry: BodyMetricEntry) {}
            override suspend fun deleteById(id: Long) {}
            override suspend fun deleteAll() {}
        }
        val repository = com.example.data.BodyMetricRepository(dummyDao)

        // CSV with:
        // Line 1: valid (date 2024-05-01, 75.0kg, height omitted -> should use lastKnownHeight 180f)
        // Line 2: invalid date (not parsed) -> skipped
        // Line 3: invalid weight (10.0kg < 20.0kg) -> skipped
        // Line 4: duplicate of Line 1 (same exact date & time) -> duplicate
        // Line 5: valid (date 2024-05-02, 74.5kg, height 178f)
        val csv = "ID,Дата,Время,Вес (кг),Рост (см),ИМТ,Грудь (см),Талия (см),Бёдра (см),Бицепс (см),Бедро (см),Шея (см),Заметки\n" +
                "1,2024-05-01,08:30,75.0,,,100,82,,,,Заметка\n" +
                "2,invalid-date,08:30,75.0,180.0,23.1,,,,,,,\n" +
                "3,2024-05-01,09:00,10.0,180.0,3.1,,,,,,,\n" +
                "4,2024-05-01,08:30,75.0,180.0,23.1,,,,,,,\n" +
                "5,2024-05-02,08:30,74.5,178.0,23.5,,,,,,,\n"

        val parseResult = repository.parseCsvDetailed(
            csvText = csv,
            lastKnownHeight = 180f,
            existingEntries = emptyList()
        )

        assertEquals(2, parseResult.entries.size)
        assertEquals(2, parseResult.skippedInvalidCount)
        assertEquals(1, parseResult.duplicatesCount)
        assertEquals(180f, parseResult.entries[0].heightCm, 0.01f) // used lastKnownHeight
        assertEquals(75.0f, parseResult.entries[0].weightKg, 0.01f)
        assertEquals(74.5f, parseResult.entries[1].weightKg, 0.01f)
    }
}



