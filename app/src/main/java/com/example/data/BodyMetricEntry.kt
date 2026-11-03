package com.example.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "body_metrics")
data class BodyMetricEntry(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0L,
    val dateMillis: Long,
    val weightKg: Float,
    val heightCm: Float,
    val chestCm: Float? = null,
    val waistCm: Float? = null,
    val hipsCm: Float? = null,
    val bicepsCm: Float? = null,
    val thighCm: Float? = null,
    val neckCm: Float? = null,
    val notes: String = ""
) {
    /**
     * Body Mass Index (BMI / ИМТ)
     * BMI = weight (kg) / (height (m))^2
     */
    val bmi: Float
        get() {
            if (heightCm <= 0f) return 0f
            val heightM = heightCm / 100f
            return weightKg / (heightM * heightM)
        }

    val bmiCategory: BmiCategory
        get() = BmiCategory.fromBmi(bmi)
}

enum class BmiCategory(val titleRu: String, val rangeRu: String, val colorHex: Long) {
    UNDERWEIGHT("Дефицит массы", "< 18.5", 0xFF38BDF8),
    NORMAL("Нормальный вес", "18.5 – 24.9", 0xFF10B981),
    OVERWEIGHT("Избыточный вес", "25.0 – 29.9", 0xFFF59E0B),
    OBESE("Ожирение", "≥ 30.0", 0xFFEF4444);

    companion object {
        fun fromBmi(bmi: Float): BmiCategory {
            return when {
                bmi <= 0f -> NORMAL
                bmi < 18.5f -> UNDERWEIGHT
                bmi < 25.0f -> NORMAL
                bmi < 30.0f -> OVERWEIGHT
                else -> OBESE
            }
        }
    }
}

enum class TrackableMetric(
    val key: String,
    val labelRu: String,
    val unit: String
) {
    WEIGHT("weight", "Вес", "кг"),
    BMI("bmi", "ИМТ", ""),
    HEIGHT("height", "Рост", "см"),
    WAIST("waist", "Талия", "см"),
    CHEST("chest", "Грудь", "см"),
    HIPS("hips", "Бёдра", "см"),
    BICEPS("biceps", "Бицепс", "см"),
    THIGH("thigh", "Бедро", "см"),
    NECK("neck", "Шея", "см");

    fun extractValue(entry: BodyMetricEntry): Float? {
        return when (this) {
            WEIGHT -> entry.weightKg
            BMI -> entry.bmi
            HEIGHT -> entry.heightCm
            WAIST -> entry.waistCm
            CHEST -> entry.chestCm
            HIPS -> entry.hipsCm
            BICEPS -> entry.bicepsCm
            THIGH -> entry.thighCm
            NECK -> entry.neckCm
        }
    }
}
