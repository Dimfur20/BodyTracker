package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.EmojiEvents
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingDown
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.DatePickerDialog
import androidx.compose.material3.DateRangePicker
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.rememberDateRangePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BmiCategory
import com.example.data.BodyMetricEntry
import com.example.data.TrackableMetric
import com.example.ui.MonthOption
import com.example.ui.TimeFilter
import com.example.ui.components.BodyMetricChart
import com.example.ui.components.MetricStatCard
import com.example.ui.theme.MetricGreen
import com.example.ui.theme.MetricRed
import com.example.util.GoalPredictionUtil
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

@Composable
fun ChartsScreen(
    entriesAscending: List<BodyMetricEntry>,
    latestWeight: Float?,
    selectedMetric: TrackableMetric,
    selectedTimeFilter: TimeFilter,
    selectedMonthKey: String?,
    customDateRange: Pair<Long, Long>? = null,
    availableMonths: List<MonthOption>,
    targetWeightKg: Float?,
    onMetricSelect: (TrackableMetric) -> Unit,
    onTimeFilterSelect: (TimeFilter) -> Unit,
    onMonthSelect: (String?) -> Unit,
    onCustomDateRangeSelect: (startUtcMillis: Long, endUtcMillis: Long) -> Unit = { _, _ -> },
    onNavigateToSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    var showDateRangePicker by remember { mutableStateOf(false) }

    // Calculate statistics for the selected metric in current period
    val metricValues = remember(entriesAscending, selectedMetric) {
        entriesAscending.mapNotNull { entry ->
            val v = selectedMetric.extractValue(entry)
            if (v != null && v > 0f) v else null
        }
    }

    val currentVal = metricValues.lastOrNull()
    val minVal = metricValues.minOrNull()
    val maxVal = metricValues.maxOrNull()
    val deltaVal = if (metricValues.size >= 2) {
        metricValues.last() - metricValues.first()
    } else null

    val latestEntry = entriesAscending.lastOrNull()

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("charts_screen_root")
    ) {
        Text(
            text = "Графики динамики",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Month Selector row at the top above the chart
        if (availableMonths.isNotEmpty()) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.CalendarMonth,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "Выбор месяца:",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // "All months" chip
                FilterChip(
                    selected = selectedMonthKey == null,
                    onClick = { onMonthSelect(null) },
                    label = { Text("Все месяцы") },
                    colors = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                        selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                    ),
                    modifier = Modifier.testTag("month_chip_all")
                )

                availableMonths.forEach { option ->
                    val isSelected = (selectedMonthKey == option.key)
                    FilterChip(
                        selected = isSelected,
                        onClick = { onMonthSelect(option.key) },
                        label = { Text(option.labelRu) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        ),
                        modifier = Modifier.testTag("month_chip_${option.key}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(10.dp))
        }

        // Metric Selector (Horizontal scrollable chips)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            TrackableMetric.values().forEach { metric ->
                val isSelected = (metric == selectedMetric)
                FilterChip(
                    selected = isSelected,
                    onClick = { onMetricSelect(metric) },
                    label = {
                        Text(
                            text = if (metric.unit.isNotBlank()) "${metric.labelRu} (${metric.unit})" else metric.labelRu,
                            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                        )
                    },
                    modifier = Modifier.testTag("metric_chip_${metric.key}")
                )
            }
        }

        // Show general time filter only if no explicit month is selected
        if (selectedMonthKey == null) {
            Spacer(modifier = Modifier.height(6.dp))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                TimeFilter.values().forEach { filter ->
                    val isSelected = (filter == selectedTimeFilter)
                    if (filter == TimeFilter.CUSTOM) {
                        val shortDf = SimpleDateFormat("dd.MM", Locale.getDefault())
                        val customChipText = if (isSelected && customDateRange != null) {
                            "${shortDf.format(Date(customDateRange.first))} – ${shortDf.format(Date(customDateRange.second))}"
                        } else {
                            filter.titleRu
                        }
                        FilterChip(
                            selected = isSelected,
                            onClick = {
                                showDateRangePicker = true
                            },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.CalendarMonth,
                                    contentDescription = null,
                                    modifier = Modifier.size(16.dp)
                                )
                            },
                            label = {
                                Text(
                                    text = customChipText,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                            ),
                            modifier = Modifier.testTag("time_filter_${filter.name}")
                        )
                    } else {
                        FilterChip(
                            selected = isSelected,
                            onClick = { onTimeFilterSelect(filter) },
                            label = {
                                Text(
                                    text = filter.titleRu,
                                    fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                                )
                            },
                            colors = FilterChipDefaults.filterChipColors(
                                selectedContainerColor = MaterialTheme.colorScheme.secondaryContainer,
                                selectedLabelColor = MaterialTheme.colorScheme.onSecondaryContainer
                            ),
                            modifier = Modifier.testTag("time_filter_${filter.name}")
                        )
                    }
                }
            }

            // Info banner when custom date range is active
            if (selectedTimeFilter == TimeFilter.CUSTOM && customDateRange != null) {
                Spacer(modifier = Modifier.height(6.dp))
                val fullDf = SimpleDateFormat("d MMMM yyyy", Locale("ru"))
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Text(
                                text = "${fullDf.format(Date(customDateRange.first))} — ${fullDf.format(Date(customDateRange.second))}",
                                style = MaterialTheme.typography.bodySmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                        Text(
                            text = "Изменить",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .clickable { showDateRangePicker = true }
                                .padding(horizontal = 6.dp, vertical = 4.dp)
                        )
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(14.dp))

        // Interactive Chart
        val chartColor = when (selectedMetric) {
            TrackableMetric.WEIGHT -> MaterialTheme.colorScheme.primary
            TrackableMetric.BMI -> MaterialTheme.colorScheme.secondary
            TrackableMetric.HEIGHT -> Color(0xFF6366F1)
            TrackableMetric.WAIST -> Color(0xFFF59E0B)
            TrackableMetric.CHEST -> Color(0xFF8B5CF6)
            TrackableMetric.HIPS -> Color(0xFFEC4899)
            TrackableMetric.BICEPS -> Color(0xFF06B6D4)
            TrackableMetric.THIGH -> Color(0xFF10B981)
            TrackableMetric.NECK -> Color(0xFF3B82F6)
        }

        BodyMetricChart(
            entriesAscending = entriesAscending,
            selectedMetric = selectedMetric,
            lineColor = chartColor
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Section under charts: Target Weight Progress Widget
        TargetWeightProgressCard(
            currentWeight = latestWeight,
            targetWeight = targetWeightKg,
            allEntries = entriesAscending,
            onConfigureClick = onNavigateToSettings
        )

        Spacer(modifier = Modifier.height(16.dp))

        // Metric Statistics Grid for current period
        if (currentVal != null) {
            Text(
                text = "Статистика за выбранный период",
                style = MaterialTheme.typography.titleMedium,
                fontWeight = FontWeight.SemiBold,
                color = MaterialTheme.colorScheme.onBackground
            )

            Spacer(modifier = Modifier.height(8.dp))

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                MetricStatCard(
                    title = "Текущий",
                    valueStr = String.format(Locale.US, "%.1f", currentVal),
                    unit = selectedMetric.unit,
                    delta = deltaVal,
                    deltaInverted = selectedMetric == TrackableMetric.WEIGHT || selectedMetric == TrackableMetric.WAIST,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Минимум",
                    valueStr = minVal?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    unit = selectedMetric.unit,
                    modifier = Modifier.weight(1f)
                )
                MetricStatCard(
                    title = "Максимум",
                    valueStr = maxVal?.let { String.format(Locale.US, "%.1f", it) } ?: "-",
                    unit = selectedMetric.unit,
                    modifier = Modifier.weight(1f)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // BMI Analysis Card
        latestEntry?.let { entry ->
            BmiOverviewCard(entry = entry)
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showDateRangePicker) {
        com.example.ui.components.AppCalendarDialog(
            mode = com.example.ui.components.CalendarSelectionMode.RANGE,
            initialStartDateMillis = customDateRange?.first,
            initialEndDateMillis = customDateRange?.second,
            title = "Период для графиков",
            onDismiss = { showDateRangePicker = false },
            onRangeSelected = { startMillis, endMillis ->
                onCustomDateRangeSelect(startMillis, endMillis)
            }
        )
    }
}

/**
 * Card displayed under the chart showing remaining kg to reach target weight.
 */
@Composable
fun TargetWeightProgressCard(
    currentWeight: Float?,
    targetWeight: Float?,
    allEntries: List<BodyMetricEntry>,
    onConfigureClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val prediction = remember(currentWeight, targetWeight, allEntries) {
        if (currentWeight != null) {
            GoalPredictionUtil.calculate(currentWeight, targetWeight, allEntries)
        } else null
    }

    Card(
        modifier = modifier
            .fillMaxWidth()
            .testTag("target_weight_progress_card"),
        shape = RoundedCornerShape(22.dp),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surface
        ),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = CircleShape,
                        modifier = Modifier.size(36.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Flag,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Цель по весу",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                if (targetWeight != null) {
                    Surface(
                        color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Text(
                            text = "Цель: ${String.format(Locale.US, "%.1f", targetWeight)} кг",
                            style = MaterialTheme.typography.labelMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSecondaryContainer,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }
            }

            Spacer(modifier = Modifier.height(14.dp))

            if (currentWeight == null) {
                Text(
                    text = "Добавьте первый замер веса в дневник, чтобы видеть прогресс к цели.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            } else if (targetWeight == null) {
                Text(
                    text = "Целевой вес ещё не установлен. Задайте желаемый вес в настройках, чтобы приложение рассчитывало оставшиеся килограммы.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(12.dp))
                OutlinedButton(
                    onClick = onConfigureClick,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.testTag("configure_target_in_settings_button")
                ) {
                    Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Установить цель в настройках")
                }
            } else {
                val diff = currentWeight - targetWeight
                val absDiff = abs(diff)

                if (absDiff < 0.15f) {
                    // Goal achieved!
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.EmojiEvents,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B),
                            modifier = Modifier.size(28.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Цель достигнута! Вес соответствует цели.",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MetricGreen
                        )
                    }
                } else if (diff > 0) {
                    // Need to lose weight
                    Text(
                        text = "До цели осталось сбросить:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", absDiff),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.primary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "кг",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.TrendingDown,
                            contentDescription = null,
                            tint = MetricGreen,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                } else {
                    // Need to gain weight
                    Text(
                        text = "До цели осталось набрать:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text(
                            text = String.format(Locale.US, "%.1f", absDiff),
                            style = MaterialTheme.typography.headlineMedium,
                            fontWeight = FontWeight.ExtraBold,
                            color = MaterialTheme.colorScheme.secondary
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "кг",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 4.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Icon(
                            imageVector = Icons.Default.TrendingUp,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(22.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                // Status info line
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Текущий вес: ${String.format(Locale.US, "%.1f", currentWeight)} кг",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Цель: ${String.format(Locale.US, "%.1f", targetWeight)} кг",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }

                // Predicted date and weekly pace row
                if (prediction != null) {
                    Spacer(modifier = Modifier.height(10.dp))
                    Surface(
                        color = when {
                            prediction.isAchieved -> MetricGreen.copy(alpha = 0.12f)
                            prediction.predictedDate != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                            !prediction.isMovingTowardsGoal && prediction.weeklyPace != null -> MetricRed.copy(alpha = 0.12f)
                            else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .testTag("charts_goal_prediction_badge")
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = when {
                                    prediction.isAchieved -> Icons.Default.CheckCircle
                                    prediction.predictedDate != null -> Icons.Default.CalendarMonth
                                    else -> Icons.Default.Info
                                },
                                contentDescription = null,
                                tint = when {
                                    prediction.isAchieved -> MetricGreen
                                    prediction.predictedDate != null -> MaterialTheme.colorScheme.primary
                                    !prediction.isMovingTowardsGoal && prediction.weeklyPace != null -> MetricRed
                                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                                },
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(
                                    text = when {
                                        prediction.isAchieved -> "Цель уже достигнута! 🏆"
                                        prediction.predictedDate != null && prediction.formattedPredictedDate != null ->
                                            "Прогноз цели: ${prediction.formattedPredictedDate}"
                                        else -> "Прогноз: ${prediction.durationText}"
                                    },
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = when {
                                        prediction.isAchieved -> MetricGreen
                                        prediction.predictedDate != null -> MaterialTheme.colorScheme.primary
                                        !prediction.isMovingTowardsGoal && prediction.weeklyPace != null -> MetricRed
                                        else -> MaterialTheme.colorScheme.onSurface
                                    }
                                )
                                Text(
                                    text = "Темп: ${prediction.paceText} (${prediction.paceDescription})",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun BmiOverviewCard(entry: BodyMetricEntry) {
    val bmi = entry.bmi
    val category = entry.bmiCategory

    Card(
        modifier = Modifier
            .fillMaxWidth()
            .testTag("bmi_overview_card"),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Индекс массы тела (ИМТ)",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Surface(
                    color = Color(category.colorHex).copy(alpha = 0.18f),
                    shape = RoundedCornerShape(8.dp)
                ) {
                    Text(
                        text = category.titleRu,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        color = Color(category.colorHex),
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                    )
                }
            }

            Spacer(modifier = Modifier.height(12.dp))

            Row(verticalAlignment = Alignment.Bottom) {
                Text(
                    text = String.format(Locale.US, "%.1f", bmi),
                    style = MaterialTheme.typography.headlineLarge,
                    fontWeight = FontWeight.ExtraBold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.width(6.dp))
                Text(
                    text = "кг/м²",
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(bottom = 6.dp)
                )
            }

            Spacer(modifier = Modifier.height(10.dp))

            // Multi-segment color gauge with dynamic needle pointer for current BMI
            com.example.ui.components.BmiPointerBar(bmi = bmi)
        }
    }
}

