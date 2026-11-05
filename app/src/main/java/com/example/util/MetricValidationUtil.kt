package com.example.util

/**
 * Utility containing validation rules, limits, and input filtering for health and body metrics.
 * Ensures users cannot enter unrealistically large numbers or negative/zero values.
 */
object MetricValidationUtil {
    const val MIN_WEIGHT_KG = 20.0f
    const val MAX_WEIGHT_KG = 500.0f

    const val MIN_HEIGHT_CM = 50.0f
    const val MAX_HEIGHT_CM = 300.0f

    const val MIN_MEASUREMENT_CM = 10.0f
    const val MAX_MEASUREMENT_CM = 250.0f

    const val MIN_AGE = 14
    const val MAX_AGE = 120

    const val MAX_NOTES_LENGTH = 250

    /**
     * Checks whether an entered string is a valid partial or full decimal input
     * that does not exceed [maxValue] and has at most [maxDecimals] decimal places.
     * Prevents typing or pasting numbers larger than [maxValue].
     */
    fun isValidDecimalInput(
        input: String,
        maxValue: Float,
        maxDecimals: Int = 1
    ): Boolean {
        if (input.isEmpty()) return true

        // Disallow negative sign or non-digit/non-punctuation characters
        if (!input.all { it.isDigit() || it == '.' || it == ',' }) return false

        val dotCount = input.count { it == '.' || it == ',' }
        if (dotCount > 1) return false

        val normalized = input.replace(',', '.')

        if (normalized.endsWith('.')) {
            val integerPart = normalized.dropLast(1)
            if (integerPart.isEmpty()) return true
            val parsedInt = integerPart.toFloatOrNull() ?: return false
            return parsedInt <= maxValue
        }

        if (normalized.contains('.')) {
            val parts = normalized.split('.')
            if (parts.size != 2) return false
            if (parts[1].length > maxDecimals) return false
            val parsed = normalized.toFloatOrNull() ?: return false
            return parsed <= maxValue
        }

        // Plain integer
        val parsed = normalized.toFloatOrNull() ?: return false
        return parsed <= maxValue
    }

    /**
     * Weight is valid if it is present and within [MIN_WEIGHT_KG]..[MAX_WEIGHT_KG].
     */
    fun isWeightValid(weight: Float?): Boolean {
        return weight != null && !weight.isNaN() && !weight.isInfinite() && weight in MIN_WEIGHT_KG..MAX_WEIGHT_KG
    }

    /**
     * Height is valid if it is present and within [MIN_HEIGHT_CM]..[MAX_HEIGHT_CM].
     */
    fun isHeightValid(height: Float?): Boolean {
        return height != null && !height.isNaN() && !height.isInfinite() && height in MIN_HEIGHT_CM..MAX_HEIGHT_CM
    }

    /**
     * Optional body measurement (waist, chest, hips, biceps, thigh, neck).
     * If blank/null, valid. If entered, must be in [MIN_MEASUREMENT_CM]..[MAX_MEASUREMENT_CM].
     */
    fun isMeasurementValid(value: Float?): Boolean {
        if (value == null) return true
        return !value.isNaN() && !value.isInfinite() && value in MIN_MEASUREMENT_CM..MAX_MEASUREMENT_CM
    }

    /**
     * Age in years. If null, valid (optional). If provided, must be in [MIN_AGE]..[MAX_AGE].
     */
    fun isAgeValid(age: Int?): Boolean {
        if (age == null) return true
        return age in MIN_AGE..MAX_AGE
    }
}
