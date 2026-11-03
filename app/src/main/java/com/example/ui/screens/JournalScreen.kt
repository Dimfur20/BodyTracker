package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.Flag
import androidx.compose.material.icons.filled.HourglassTop
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material.icons.filled.Swipe
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.data.BodyMetricEntry
import com.example.ui.components.BmiPointerBar
import com.example.ui.theme.MetricGreen
import com.example.ui.theme.MetricRed
import com.example.util.GoalPredictionUtil
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun JournalScreen(
    entries: List<BodyMetricEntry>,
    targetWeightKg: Float? = null,
    onAddClick: () -> Unit,
    onEditClick: (BodyMetricEntry) -> Unit,
    onDeleteClick: (BodyMetricEntry) -> Unit,
    onGenerateDemoClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    var entryToDelete by remember { mutableStateOf<BodyMetricEntry?>(null) }
    var showJumpCalendarDialog by remember { mutableStateOf(false) }
    val context = LocalContext.current
    val coroutineScope = rememberCoroutineScope()

    Box(modifier = modifier.fillMaxSize()) {
        if (entries.isEmpty()) {
            EmptyJournalView(
                onAddClick = onAddClick,
                onDemoClick = onGenerateDemoClick
            )
        } else {
            val pagerState = rememberPagerState(
                initialPage = (entries.size - 1).coerceAtLeast(0),
                pageCount = { entries.size }
            )
            val dateChipsListState = rememberLazyListState()

            // When data loads, scroll to the newest entry once
            var hasInitializedToLast by remember { mutableStateOf(false) }
            LaunchedEffect(entries.size) {
                if (!hasInitializedToLast && entries.isNotEmpty()) {
                    val lastIdx = entries.size - 1
                    pagerState.scrollToPage(lastIdx)
                    dateChipsListState.scrollToItem(lastIdx)
                    hasInitializedToLast = true
                }
            }

            // Automatically scroll date chips so the active date is always in view
            LaunchedEffect(pagerState.currentPage) {
                if (entries.isNotEmpty()) {
                    dateChipsListState.animateScrollToItem(pagerState.currentPage)
                }
            }

            val currentEntry = entries.getOrNull(pagerState.currentPage)
            val fullDateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale("ru")) }
            val shortDateFormat = remember { SimpleDateFormat("d MMM", Locale("ru")) }

            Column(
                modifier = Modifier.fillMaxSize()
            ) {
                // Top Date Navigation Bar with arrows, clickable calendar date, and auto-scrolling date chips
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("date_navigation_header_card"),
                    color = MaterialTheme.colorScheme.surface,
                    tonalElevation = 2.dp,
                    shadowElevation = 2.dp
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp)
                    ) {
                        // Arrow controls and date picker
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            // Left arrow navigates to older (previous) date
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage > 0) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage > 0,
                                modifier = Modifier.testTag("prev_entry_button")
                            ) {
                                Icon(Icons.Default.ChevronLeft, contentDescription = "Предыдущая дата")
                            }

                            // Center clickable date with calendar
                            Surface(
                                modifier = Modifier
                                    .clip(RoundedCornerShape(12.dp))
                                    .clickable { showJumpCalendarDialog = true }
                                    .testTag("date_picker_jump_button"),
                                color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                                shape = RoundedCornerShape(12.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(
                                        imageVector = Icons.Default.CalendarMonth,
                                        contentDescription = null,
                                        tint = MaterialTheme.colorScheme.primary,
                                        modifier = Modifier.size(16.dp)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(
                                        text = currentEntry?.let { fullDateFormat.format(Date(it.dateMillis)) } ?: "",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onPrimaryContainer
                                    )
                                }
                            }

                            // Right arrow navigates to newer (next) date
                            IconButton(
                                onClick = {
                                    if (pagerState.currentPage < entries.size - 1) {
                                        coroutineScope.launch {
                                            pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                        }
                                    }
                                },
                                enabled = pagerState.currentPage < entries.size - 1,
                                modifier = Modifier.testTag("next_entry_button")
                            ) {
                                Icon(Icons.Default.ChevronRight, contentDescription = "Следующая дата")
                            }
                        }

                        Spacer(modifier = Modifier.height(6.dp))

                        // Horizontal Date Chips list with LazyRow and auto-scroll
                        LazyRow(
                            state = dateChipsListState,
                            modifier = Modifier
                                .fillMaxWidth()
                                .testTag("date_chips_lazy_row"),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            items(entries.size) { index ->
                                val entry = entries[index]
                                val isSelected = (index == pagerState.currentPage)
                                Surface(
                                    modifier = Modifier
                                        .clip(RoundedCornerShape(10.dp))
                                        .clickable {
                                            coroutineScope.launch {
                                                pagerState.animateScrollToPage(index)
                                            }
                                        }
                                        .testTag("date_chip_$index"),
                                    color = if (isSelected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                                    shape = RoundedCornerShape(10.dp)
                                ) {
                                    Text(
                                        text = shortDateFormat.format(Date(entry.dateMillis)),
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        color = if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.onSurfaceVariant,
                                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                                    )
                                }
                            }
                        }

                        Spacer(modifier = Modifier.height(4.dp))

                        // Page counter info with swipe hint
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "Запись ${pagerState.currentPage + 1} из ${entries.size}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(
                                    imageVector = Icons.Default.Swipe,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(13.dp)
                                )
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(
                                    text = "‹ влево: новее | вправо: старше ›",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                }

                // Full-screen HorizontalPager spanning the entire remaining viewport
                HorizontalPager(
                    state = pagerState,
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f)
                        .testTag("journal_horizontal_pager")
                ) { pageIndex ->
                    val entry = entries[pageIndex]
                    val olderEntry = entries.getOrNull(pageIndex - 1)
                    val weightDelta = olderEntry?.let { entry.weightKg - it.weightKg }

                    SingleEntryPageView(
                        entry = entry,
                        weightDelta = weightDelta,
                        allEntries = entries,
                        targetWeightKg = targetWeightKg,
                        onEdit = { onEditClick(entry) },
                        onDelete = { entryToDelete = entry }
                    )
                }
            }

            if (showJumpCalendarDialog) {
                com.example.ui.components.AppCalendarDialog(
                    mode = com.example.ui.components.CalendarSelectionMode.SINGLE,
                    initialDateMillis = currentEntry?.dateMillis,
                    title = "Перейти к дате",
                    onDismiss = { showJumpCalendarDialog = false },
                    onDateSelected = { targetMillis ->
                        val closestIdx = entries.indices.minByOrNull {
                            kotlin.math.abs(entries[it].dateMillis - targetMillis)
                        } ?: (entries.size - 1)
                        coroutineScope.launch {
                            pagerState.animateScrollToPage(closestIdx)
                        }
                    }
                )
            }
        }

        FloatingActionButton(
            onClick = onAddClick,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(bottom = 16.dp, end = 16.dp)
                .testTag("add_metric_fab"),
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary
        ) {
            Icon(imageVector = Icons.Default.Add, contentDescription = "Добавить замер")
        }
    }

    entryToDelete?.let { entry ->
        AlertDialog(
            onDismissRequest = { entryToDelete = null },
            title = { Text("Удалить замер?") },
            text = { Text("Запись за ${SimpleDateFormat("dd.MM.yyyy", Locale("ru")).format(Date(entry.dateMillis))} будет удалена безвозвратно.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onDeleteClick(entry)
                        entryToDelete = null
                    },
                    modifier = Modifier.testTag("confirm_delete_button")
                ) {
                    Text("Удалить", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { entryToDelete = null }) {
                    Text("Отмена")
                }
            }
        )
    }
}

/**
 * Single Entry Full Page view displayed inside HorizontalPager.
 * Full screen layout with rich Material 3 modular cards and edge-to-edge touch target.
 */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SingleEntryPageView(
    entry: BodyMetricEntry,
    weightDelta: Float?,
    allEntries: List<BodyMetricEntry>,
    targetWeightKg: Float?,
    onEdit: () -> Unit,
    onDelete: () -> Unit,
    modifier: Modifier = Modifier
) {
    val scrollState = rememberScrollState()
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale("ru")) }
    val bmi = entry.bmi
    val category = entry.bmiCategory

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(horizontal = 16.dp, vertical = 10.dp)
            .testTag("single_entry_card_${entry.id}")
    ) {
        // Main Stats Card: Weight, delta and Height
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // Time and action buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Время замера: ${timeFormat.format(Date(entry.dateMillis))}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row {
                        IconButton(onClick = onEdit, modifier = Modifier.size(36.dp).testTag("edit_entry_button")) {
                            Icon(Icons.Default.Edit, contentDescription = "Редактировать", tint = MaterialTheme.colorScheme.primary)
                        }
                        IconButton(onClick = onDelete, modifier = Modifier.size(36.dp).testTag("delete_entry_button")) {
                            Icon(Icons.Default.Delete, contentDescription = "Удалить", tint = MaterialTheme.colorScheme.error)
                        }
                    }
                }

                Spacer(modifier = Modifier.height(6.dp))

                // Stats row: Weight and Height
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.Bottom
                ) {
                    Column {
                        Text(
                            text = "Вес тела",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.1f", entry.weightKg),
                                style = MaterialTheme.typography.displayMedium,
                                fontWeight = FontWeight.ExtraBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "кг",
                                style = MaterialTheme.typography.titleMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 6.dp)
                            )
                        }

                        if (weightDelta != null && weightDelta != 0f) {
                            Spacer(modifier = Modifier.height(4.dp))
                            val isLoss = weightDelta < 0f
                            val deltaColor = if (isLoss) MetricGreen else MetricRed
                            val icon = if (isLoss) Icons.Default.ArrowDownward else Icons.Default.ArrowUpward
                            val sign = if (isLoss) "" else "+"

                            Surface(
                                color = deltaColor.copy(alpha = 0.14f),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 3.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Icon(icon, contentDescription = null, tint = deltaColor, modifier = Modifier.size(14.dp))
                                    Spacer(modifier = Modifier.width(3.dp))
                                    Text(
                                        text = "$sign${String.format(Locale.US, "%.1f", weightDelta)} кг с прошлого замера",
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = deltaColor
                                    )
                                }
                            }
                        }
                    }

                    Column(horizontalAlignment = Alignment.End) {
                        Text(
                            text = "Рост",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Row(verticalAlignment = Alignment.Bottom) {
                            Text(
                                text = String.format(Locale.US, "%.0f", entry.heightCm),
                                style = MaterialTheme.typography.headlineMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(3.dp))
                            Text(
                                text = "см",
                                style = MaterialTheme.typography.titleSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(bottom = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // BMI Card with BmiPointerBar
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                // BMI Header and Category Badge
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = "ИМТ: ",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = String.format(Locale.US, "%.1f", bmi),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.ExtraBold,
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

                Spacer(modifier = Modifier.height(10.dp))

                // Dynamic Pointer Bar marking the user's BMI on the colored gauge
                BmiPointerBar(bmi = bmi)
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Target, Pace, and Time to Goal Analytics Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
            ) {
                GoalPaceAnalyticsSection(
                    currentWeight = entry.weightKg,
                    targetWeight = targetWeightKg,
                    allEntries = allEntries
                )
            }
        }

        // Body Measurements section
        val hasBodySizes = entry.waistCm != null || entry.chestCm != null ||
                entry.hipsCm != null || entry.bicepsCm != null ||
                entry.thighCm != null || entry.neckCm != null

        if (hasBodySizes) {
            Spacer(modifier = Modifier.height(12.dp))
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(16.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Straighten,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            text = "Размеры и объёмы тела",
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    Spacer(modifier = Modifier.height(10.dp))

                    FlowRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        entry.waistCm?.let { MetricChip(label = "Талия", value = it) }
                        entry.chestCm?.let { MetricChip(label = "Грудь", value = it) }
                        entry.hipsCm?.let { MetricChip(label = "Бёдра", value = it) }
                        entry.bicepsCm?.let { MetricChip(label = "Бицепс", value = it) }
                        entry.thighCm?.let { MetricChip(label = "Бедро", value = it) }
                        entry.neckCm?.let { MetricChip(label = "Шея", value = it) }
                    }
                }
            }
        }

        // Notes section
        if (entry.notes.isNotBlank()) {
            Spacer(modifier = Modifier.height(12.dp))
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                shape = RoundedCornerShape(16.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(14.dp)) {
                    Text(
                        text = "Заметка:",
                        style = MaterialTheme.typography.labelSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = entry.notes,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }
            }
        }

        // Ample bottom space so all content can be scrolled comfortably above FAB
        Spacer(modifier = Modifier.height(84.dp))
    }
}

/**
 * Analytics Section with 3 dedicated zones:
 * 1. Сколько до цели (кг)
 * 2. Средний темп в неделю (кг/нед)
 * 3. Сколько времени до цели (~нед/мес)
 */
@Composable
fun GoalPaceAnalyticsSection(
    currentWeight: Float,
    targetWeight: Float?,
    allEntries: List<BodyMetricEntry>,
    modifier: Modifier = Modifier
) {
    val prediction = remember(currentWeight, targetWeight, allEntries) {
        GoalPredictionUtil.calculate(currentWeight, targetWeight, allEntries)
    }

    val (targetText, targetSub, targetColor) = when {
        targetWeight == null -> Triple("Не задан", "В Настройках", MaterialTheme.colorScheme.onSurfaceVariant)
        prediction.isAchieved -> Triple("0.0 кг", "Цель достигнута! 🏆", MetricGreen)
        currentWeight > targetWeight -> Triple(
            "-${String.format(Locale.US, "%.1f", prediction.remainingKg)} кг",
            "Осталось сбросить",
            MaterialTheme.colorScheme.primary
        )
        else -> Triple(
            "+${String.format(Locale.US, "%.1f", prediction.remainingKg)} кг",
            "Осталось набрать",
            MaterialTheme.colorScheme.secondary
        )
    }

    val paceColor = when {
        prediction.weeklyPace == null || abs(prediction.weeklyPace) < 0.05 -> MaterialTheme.colorScheme.onSurfaceVariant
        prediction.weeklyPace < -0.05 -> MetricGreen
        else -> MaterialTheme.colorScheme.secondary
    }

    val paceTrendColor = when (prediction.paceTrendComparison) {
        "▲ Ускорение" -> if (prediction.isMovingTowardsGoal) MetricGreen else MaterialTheme.colorScheme.primary
        "▼ Замедление" -> Color(0xFFF59E0B)
        else -> MaterialTheme.colorScheme.onSurfaceVariant
    }

    val timeColor = when {
        targetWeight == null || prediction.weeklyPace == null -> MaterialTheme.colorScheme.onSurfaceVariant
        prediction.isAchieved -> MetricGreen
        prediction.isMovingTowardsGoal -> MaterialTheme.colorScheme.primary
        else -> MetricRed
    }

    Column(
        modifier = modifier.fillMaxWidth()
    ) {
        Text(
            text = "Прогресс и темп цели",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface
        )

        Spacer(modifier = Modifier.height(10.dp))

        // 3 Zones grid
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            // Zone 1: До цели
            AnalyticsZoneCard(
                title = "До цели",
                value = targetText,
                subtitle = targetSub,
                icon = Icons.Default.Flag,
                accentColor = targetColor,
                modifier = Modifier.weight(1f)
            )

            // Zone 2: Темп за 30 дней
            AnalyticsZoneCard(
                title = "Темп за 30 дн.",
                value = prediction.paceText,
                subtitle = prediction.paceDescription,
                indicatorText = prediction.paceTrendComparison,
                indicatorColor = paceTrendColor,
                icon = Icons.Default.Speed,
                accentColor = paceColor,
                modifier = Modifier.weight(1f)
            )

            // Zone 3: Время до цели
            AnalyticsZoneCard(
                title = "Время до цели",
                value = prediction.durationText,
                subtitle = if (prediction.isMovingTowardsGoal) "При текущем темпе" else "По прогнозу",
                icon = Icons.Default.HourglassTop,
                accentColor = timeColor,
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(modifier = Modifier.height(10.dp))

        // Predicted completion date banner
        Surface(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("goal_date_prediction_banner"),
            shape = RoundedCornerShape(16.dp),
            color = when {
                prediction.isAchieved -> MetricGreen.copy(alpha = 0.12f)
                prediction.predictedDate != null -> MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
                !prediction.isMovingTowardsGoal && prediction.weeklyPace != null -> MetricRed.copy(alpha = 0.12f)
                else -> MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
            }
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 10.dp),
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
                    modifier = Modifier.size(24.dp)
                )

                Spacer(modifier = Modifier.width(10.dp))

                Column {
                    Text(
                        text = when {
                            prediction.isAchieved -> "Цель достигнута! 🏆"
                            prediction.predictedDate != null && prediction.formattedPredictedDate != null ->
                                "Прогноз цели: ${prediction.formattedPredictedDate}"
                            targetWeight == null -> "Цель по весу не задана"
                            else -> "Прогноз достижения цели"
                        },
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = when {
                            prediction.isAchieved -> MetricGreen
                            prediction.predictedDate != null -> MaterialTheme.colorScheme.primary
                            !prediction.isMovingTowardsGoal && prediction.weeklyPace != null -> MetricRed
                            else -> MaterialTheme.colorScheme.onSurface
                        }
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = prediction.explanationText,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }
    }
}

@Composable
fun AnalyticsZoneCard(
    title: String,
    value: String,
    subtitle: String,
    icon: ImageVector,
    accentColor: Color,
    indicatorText: String? = null,
    indicatorColor: Color? = null,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier.heightIn(min = 94.dp),
        shape = RoundedCornerShape(16.dp),
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.45f)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 10.dp, vertical = 10.dp),
            horizontalAlignment = Alignment.Start,
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = icon,
                        contentDescription = null,
                        tint = accentColor,
                        modifier = Modifier.size(16.dp)
                    )
                    Spacer(modifier = Modifier.width(4.dp))
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(4.dp))

                Text(
                    text = value,
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.ExtraBold,
                    color = accentColor,
                    maxLines = 1
                )

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }

            if (indicatorText != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Surface(
                    color = (indicatorColor ?: accentColor).copy(alpha = 0.14f),
                    shape = RoundedCornerShape(6.dp)
                ) {
                    Text(
                        text = indicatorText,
                        style = MaterialTheme.typography.labelSmall.copy(fontSize = 10.sp),
                        fontWeight = FontWeight.Bold,
                        color = indicatorColor ?: MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        modifier = Modifier.padding(horizontal = 5.dp, vertical = 2.dp)
                    )
                }
            }
        }
    }
}

@Composable
fun MetricChip(label: String, value: Float) {
    Surface(
        color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.6f),
        shape = RoundedCornerShape(10.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text(
                text = "$label: ",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Text(
                text = "${String.format(Locale.US, "%.1f", value)} см",
                style = MaterialTheme.typography.bodySmall,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.onSurface
            )
        }
    }
}

@Composable
fun EmptyJournalView(
    onAddClick: () -> Unit,
    onDemoClick: () -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(24.dp)
            .testTag("empty_journal_view"),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(
            color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
            shape = CircleShape,
            modifier = Modifier.size(90.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = Icons.Default.FitnessCenter,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(44.dp)
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        Text(
            text = "Пока нет замеров",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(8.dp))

        Text(
            text = "Начните отслеживать свой вес, рост и размеры тела (грудь, талия, бёдра и др.), чтобы видеть динамику на графиках.",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center
        )

        Spacer(modifier = Modifier.height(24.dp))

        Button(
            onClick = onAddClick,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("empty_add_first_button")
        ) {
            Icon(Icons.Default.Add, contentDescription = null)
            Spacer(modifier = Modifier.width(8.dp))
            Text("Добавить первый замер")
        }

        Spacer(modifier = Modifier.height(12.dp))

        OutlinedButton(
            onClick = onDemoClick,
            shape = RoundedCornerShape(14.dp),
            modifier = Modifier.testTag("empty_load_demo_button")
        ) {
            Text("Загрузить пример данных")
        }
    }
}
