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
    fun test30DayWindowExcludesOldEntries() {
        val now = 1700000000000L
        val oneDayMillis = 86_400_000L

        // Old entry 90 days ago: 100kg
        // Entry 20 days ago: 80kg
        // Entry today (now): 78kg (-2kg loss over 20 days = -0.7 kg/week)
        val entries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 90 * oneDayMillis, weightKg = 100f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now - 20 * oneDayMillis, weightKg = 80f, heightCm = 175f),
            BodyMetricEntry(id = 3, dateMillis = now, weightKg = 78f, heightCm = 175f)
        )

        val pred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 78f,
            targetWeight = 70f,
            allEntries = entries
        )

        assertTrue(pred.isMovingTowardsGoal)
        // deltaKg in 30 days is -2.0 kg over 20 days: weeklyPace = (-2.0 / 20.0) * 7.0 = -0.7 kg/week
        assertTrue(pred.weeklyPace != null)
        assertEquals(-0.7, pred.weeklyPace!!, 0.05)
        assertEquals("Снижение за 30 дн.", pred.paceDescription)
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

        // Accelerating case:
        // Window 1 (previous 30-day window): from day -56 (84kg) to day -28 (82kg) -> -2kg / 4 wks = -0.5 kg/week
        // Window 2 (current 30-day window): from day -28 (82kg) to day 0 (78kg) -> -4kg / 4 wks = -1.0 kg/week
        val acceleratingEntries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 56 * oneDayMillis, weightKg = 84f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now - 28 * oneDayMillis, weightKg = 82f, heightCm = 175f),
            BodyMetricEntry(id = 3, dateMillis = now, weightKg = 78f, heightCm = 175f)
        )

        val accelPred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 78f,
            targetWeight = 70f,
            allEntries = acceleratingEntries
        )

        assertEquals("▲ Ускорение", accelPred.paceTrendComparison)
        assertTrue(accelPred.prevWeeklyPace != null)
        assertTrue(accelPred.weeklyPace != null)

        // Decelerating case:
        // Window 1: from day -56 (86kg) to day -28 (82kg) -> -4kg / 4 wks = -1.0 kg/week
        // Window 2: from day -28 (82kg) to day 0 (81kg) -> -1kg / 4 wks = -0.25 kg/week
        val deceleratingEntries = listOf(
            BodyMetricEntry(id = 1, dateMillis = now - 56 * oneDayMillis, weightKg = 86f, heightCm = 175f),
            BodyMetricEntry(id = 2, dateMillis = now - 28 * oneDayMillis, weightKg = 82f, heightCm = 175f),
            BodyMetricEntry(id = 3, dateMillis = now, weightKg = 81f, heightCm = 175f)
        )

        val decelPred = com.example.util.GoalPredictionUtil.calculate(
            currentWeight = 81f,
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
}


