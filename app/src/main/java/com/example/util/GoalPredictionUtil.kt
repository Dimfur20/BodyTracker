package com.example.util

import com.example.data.BodyMetricEntry
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

data class GoalPrediction(
    val remainingKg: Float,
    val weeklyPace: Double?,
    val prevWeeklyPace: Double? = null,
    val paceTrendComparison: String? = null,
    val paceTrendDelta: Double? = null,
    val weeksRemaining: Double?,
    val predictedDate: Date?,
    val formattedPredictedDate: String?,
    val durationText: String,
    val paceText: String,
    val paceDescription: String,
    val isAchieved: Boolean,
    val isMovingTowardsGoal: Boolean,
    val explanationText: String
)

object GoalPredictionUtil {

    private const val THIRTY_DAYS_MILLIS = 30L * 86_400_000L

    fun calculate(
        currentWeight: Float,
        targetWeight: Float?,
        allEntries: List<BodyMetricEntry>
    ): GoalPrediction {
        if (targetWeight == null) {
            return GoalPrediction(
                remainingKg = 0f,
                weeklyPace = null,
                prevWeeklyPace = null,
                paceTrendComparison = null,
                paceTrendDelta = null,
                weeksRemaining = null,
                predictedDate = null,
                formattedPredictedDate = null,
                durationText = "—",
                paceText = "—",
                paceDescription = "Задайте цель в Настройках",
                isAchieved = false,
                isMovingTowardsGoal = false,
                explanationText = "Целевой вес ещё не установлен. Укажите желаемый вес на вкладке Настроек."
            )
        }

        val diff = currentWeight - targetWeight
        val absDiff = abs(diff)

        if (absDiff < 0.15f) {
            return GoalPrediction(
                remainingKg = 0f,
                weeklyPace = null,
                prevWeeklyPace = null,
                paceTrendComparison = null,
                paceTrendDelta = null,
                weeksRemaining = 0.0,
                predictedDate = Date(),
                formattedPredictedDate = "Цель уже достигнута!",
                durationText = "0 дней",
                paceText = "0.0 кг/нед",
                paceDescription = "Целевой вес достигнут",
                isAchieved = true,
                isMovingTowardsGoal = true,
                explanationText = "Поздравляем! Ваш текущий вес соответствует целевому."
            )
        }

        // Filter entries for the last 30 days relative to newest recorded date
        val sorted = allEntries.sortedBy { it.dateMillis }
        val newestEntry = sorted.lastOrNull()
        val windowCutoff = if (newestEntry != null) newestEntry.dateMillis - THIRTY_DAYS_MILLIS else 0L
        val windowPrevCutoff = if (newestEntry != null) newestEntry.dateMillis - (2 * THIRTY_DAYS_MILLIS) else 0L

        val recentEntries = sorted.filter { it.dateMillis >= windowCutoff }
        val prevCandidates = sorted.filter { it.dateMillis in windowPrevCutoff until windowCutoff }
        val prevEntries = if (prevCandidates.size >= 2) {
            prevCandidates
        } else if (prevCandidates.size == 1 && recentEntries.isNotEmpty()) {
            listOf(prevCandidates.first(), recentEntries.first())
        } else {
            prevCandidates
        }

        // Calculate average weekly pace across the 30-day window
        val weeklyPace: Double? = if (recentEntries.size >= 2) {
            val oldest = recentEntries.first()
            val newest = recentEntries.last()
            val days = (newest.dateMillis - oldest.dateMillis) / (86_400_000.0)
            val deltaKg = (newest.weightKg - oldest.weightKg).toDouble()
            val weeks = days / 7.0
            if (weeks >= 0.5) deltaKg / weeks
            else if (days >= 1.0) (deltaKg / days) * 7.0
            else deltaKg
        } else null

        // Calculate average weekly pace across the previous 30-day window [newest - 60d, newest - 30d]
        val prevWeeklyPace: Double? = if (prevEntries.size >= 2) {
            val oldest = prevEntries.first()
            val newest = prevEntries.last()
            val days = (newest.dateMillis - oldest.dateMillis) / (86_400_000.0)
            val deltaKg = (newest.weightKg - oldest.weightKg).toDouble()
            val weeks = days / 7.0
            if (weeks >= 0.5) deltaKg / weeks
            else if (days >= 1.0) (deltaKg / days) * 7.0
            else deltaKg
        } else null

        // Calculate trend acceleration or deceleration compared to previous 30-day window
        var paceTrendComparison: String? = null
        var paceTrendDelta: Double? = null

        if (weeklyPace != null && prevWeeklyPace != null) {
            val currSpeed = if (diff > 0) {
                -weeklyPace
            } else if (diff < 0) {
                weeklyPace
            } else if (weeklyPace < -0.05) {
                -weeklyPace
            } else if (weeklyPace > 0.05) {
                weeklyPace
            } else {
                -abs(weeklyPace)
            }

            val prevSpeed = if (diff > 0) {
                -prevWeeklyPace
            } else if (diff < 0) {
                prevWeeklyPace
            } else if (weeklyPace < -0.05) {
                -prevWeeklyPace
            } else if (weeklyPace > 0.05) {
                prevWeeklyPace
            } else {
                -abs(prevWeeklyPace)
            }

            val delta = currSpeed - prevSpeed
            paceTrendDelta = delta
            paceTrendComparison = when {
                delta >= 0.08 -> "▲ Ускорение"
                delta <= -0.08 -> "▼ Замедление"
                else -> "≈ Стабильно"
            }
        }

        val paceText = when {
            weeklyPace == null -> "—"
            abs(weeklyPace) < 0.05 -> "0.0 кг/нед"
            weeklyPace > 0 -> "+${String.format(Locale.US, "%.1f", weeklyPace)} кг/нед"
            else -> "${String.format(Locale.US, "%.1f", weeklyPace)} кг/нед"
        }

        val paceDesc = when {
            weeklyPace == null -> if (allEntries.size < 2) "Мало замеров" else "Мало данных за 30 дн."
            abs(weeklyPace) < 0.05 -> "Стабильно за 30 дн."
            weeklyPace > 0 -> "Набор за 30 дн."
            else -> "Снижение за 30 дн."
        }

        val trendSummarySuffix = if (paceTrendComparison != null && paceTrendDelta != null) {
            when (paceTrendComparison) {
                "▲ Ускорение" -> " (Темп ускорился на ${String.format(Locale.US, "%.1f", abs(paceTrendDelta))} кг/нед к прошл. 30 дн.)"
                "▼ Замедление" -> " (Темп замедлился на ${String.format(Locale.US, "%.1f", abs(paceTrendDelta))} кг/нед к прошл. 30 дн.)"
                else -> " (Темп стабилен относительно прошлых 30 дн.)"
            }
        } else ""

        if (weeklyPace == null || abs(weeklyPace) < 0.05) {
            return GoalPrediction(
                remainingKg = absDiff,
                weeklyPace = weeklyPace,
                prevWeeklyPace = prevWeeklyPace,
                paceTrendComparison = paceTrendComparison,
                paceTrendDelta = paceTrendDelta,
                weeksRemaining = null,
                predictedDate = null,
                formattedPredictedDate = null,
                durationText = "—",
                paceText = paceText,
                paceDescription = paceDesc,
                isAchieved = false,
                isMovingTowardsGoal = false,
                explanationText = if (allEntries.size < 2) {
                    "Сделайте замеры за разные дни, чтобы приложение рассчитало темп за последние 30 дней и дату достижения цели."
                } else {
                    "За последние 30 дней недостаточно замеров для вычисления темпа. Добавьте новый замер, чтобы увидеть прогресс за месяц.$trendSummarySuffix"
                }
            )
        }

        // Check direction:
        // To lose weight (diff > 0), pace must be negative (weeklyPace < -0.05)
        // To gain weight (diff < 0), pace must be positive (weeklyPace > 0.05)
        val isMovingTowardsGoal = (diff > 0 && weeklyPace < -0.05) || (diff < 0 && weeklyPace > 0.05)

        if (!isMovingTowardsGoal) {
            return GoalPrediction(
                remainingKg = absDiff,
                weeklyPace = weeklyPace,
                prevWeeklyPace = prevWeeklyPace,
                paceTrendComparison = paceTrendComparison,
                paceTrendDelta = paceTrendDelta,
                weeksRemaining = null,
                predictedDate = null,
                formattedPredictedDate = "Темп обратный",
                durationText = "Движение от цели",
                paceText = paceText,
                paceDescription = paceDesc,
                isAchieved = false,
                isMovingTowardsGoal = false,
                explanationText = (if (diff > 0)
                    "Для цели нужно снижение веса, но за последние 30 дней зафиксирован набор (+${String.format(Locale.US, "%.1f", weeklyPace)} кг/нед)."
                else
                    "Для цели нужен набор веса, но за последние 30 дней зафиксировано снижение (${String.format(Locale.US, "%.1f", weeklyPace)} кг/нед).") + trendSummarySuffix
            )
        }

        val weeksNeeded = absDiff / abs(weeklyPace)
        val targetMillis = System.currentTimeMillis() + (weeksNeeded * 7 * 86_400_000L).toLong()
        val predictedDate = Date(targetMillis)
        val dateFormat = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
        val formattedDate = dateFormat.format(predictedDate)

        val durationStr = if (weeksNeeded < 1.0) "< 1 недели"
        else if (weeksNeeded < 5.0) "~${weeksNeeded.roundToInt()} нед."
        else "~${String.format(Locale.US, "%.1f", weeksNeeded / 4.33)} мес."

        return GoalPrediction(
            remainingKg = absDiff,
            weeklyPace = weeklyPace,
            prevWeeklyPace = prevWeeklyPace,
            paceTrendComparison = paceTrendComparison,
            paceTrendDelta = paceTrendDelta,
            weeksRemaining = weeksNeeded,
            predictedDate = predictedDate,
            formattedPredictedDate = formattedDate,
            durationText = durationStr,
            paceText = paceText,
            paceDescription = paceDesc,
            isAchieved = false,
            isMovingTowardsGoal = true,
            explanationText = "При текущем среднем темпе за 30 дней ($paceText) прогнозируемая дата достижения цели: $formattedDate.$trendSummarySuffix"
        )
    }
}
