package com.example

import com.example.data.BmiCategory
import com.example.data.BodyMetricEntry
import com.example.data.TrackableMetric
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.floor

class ExampleUnitTest {

    @Test
    fun testBmiCalculation() {
        val entry = BodyMetricEntry(
            id = 1L,
            dateMillis = System.currentTimeMillis(),
            weightKg = 70f,
            heightCm = 175f
        )
        // 70 / (1.75^2) = 22.857
        assertTrue(entry.bmi in 22.8f..22.9f)
        assertEquals(BmiCategory.NORMAL, entry.bmiCategory)

        // Underweight test: 45 kg, 170 cm -> 15.57
        val underEntry = entry.copy(weightKg = 45f, heightCm = 170f)
        assertEquals(BmiCategory.UNDERWEIGHT, underEntry.bmiCategory)

        // Overweight test: 80 kg, 170 cm -> 27.68
        val overEntry = entry.copy(weightKg = 80f, heightCm = 170f)
        assertEquals(BmiCategory.OVERWEIGHT, overEntry.bmiCategory)

        // Obese test: 105 kg, 170 cm -> 36.33
        val obeseEntry = entry.copy(weightKg = 105f, heightCm = 170f)
        assertEquals(BmiCategory.OBESE, obeseEntry.bmiCategory)
    }

    @Test
    fun testTrackableMetricExtraction() {
        val entry = BodyMetricEntry(
            id = 1L,
            dateMillis = System.currentTimeMillis(),
            weightKg = 75f,
            heightCm = 180f,
            chestCm = 102f,
            waistCm = 84f,
            hipsCm = 99f,
            bicepsCm = 37f,
            thighCm = 56f,
            neckCm = 38f
        )

        assertEquals(75f, TrackableMetric.WEIGHT.extractValue(entry) ?: 0f, 0.01f)
        assertEquals(84f, TrackableMetric.WAIST.extractValue(entry) ?: 0f, 0.01f)
        assertEquals(102f, TrackableMetric.CHEST.extractValue(entry) ?: 0f, 0.01f)
        assertEquals(99f, TrackableMetric.HIPS.extractValue(entry) ?: 0f, 0.01f)
        assertEquals(37f, TrackableMetric.BICEPS.extractValue(entry) ?: 0f, 0.01f)
        assertEquals(56f, TrackableMetric.THIGH.extractValue(entry) ?: 0f, 0.01f)
        assertEquals(38f, TrackableMetric.NECK.extractValue(entry) ?: 0f, 0.01f)
    }

    @Test
    fun testTargetWeightRoundingDown() {
        // Test rounding down to tenths (e.g. 65.749729 -> 65.7)
        val rawWeight = 65.749729
        val floored = (floor(rawWeight * 10.0) / 10.0).toFloat()
        assertEquals(65.7f, floored, 0.001f)
        assertEquals("65.7", String.format(Locale.US, "%.1f", floored))

        val exactWeight = 65.0
        val flooredExact = (floor(exactWeight * 10.0) / 10.0).toFloat()
        assertEquals(65.0f, flooredExact, 0.001f)
    }

    @Test
    fun testCsvFormattingLogic() {
        val entry = BodyMetricEntry(
            id = 1L,
            dateMillis = 1700000000000L,
            weightKg = 72.3f,
            heightCm = 176f,
            waistCm = 82.5f,
            notes = "Отличная тренировка"
        )

        val dateFormat = SimpleDateFormat("yyyy-MM-dd", Locale.getDefault())
        val dateStr = dateFormat.format(Date(entry.dateMillis))

        val line = "${entry.id},$dateStr,,${entry.weightKg},${entry.heightCm},${entry.bmi},,${entry.waistCm},,,,,\"${entry.notes}\""
        assertTrue(line.contains("72.3"))
        assertTrue(line.contains("176.0"))
        assertTrue(line.contains("Отличная тренировка"))
    }
}
