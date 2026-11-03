package com.example.ui.components

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ArrowDropUp
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

enum class CalendarSelectionMode {
    SINGLE,
    RANGE
}

data class CalendarDay(
    val year: Int,
    val month: Int, // 0-indexed (0 = Jan, 11 = Dec)
    val day: Int
) : Comparable<CalendarDay> {
    override fun compareTo(other: CalendarDay): Int {
        if (year != other.year) return year.compareTo(other.year)
        if (month != other.month) return month.compareTo(other.month)
        return day.compareTo(other.day)
    }

    fun toStartOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, 0, 0, 0)
            set(Calendar.MILLISECOND, 0)
        }.timeInMillis
    }

    fun toEndOfDayMillis(): Long {
        return Calendar.getInstance().apply {
            set(year, month, day, 23, 59, 59)
            set(Calendar.MILLISECOND, 999)
        }.timeInMillis
    }

    companion object {
        fun fromMillis(millis: Long): CalendarDay {
            val cal = Calendar.getInstance().apply { timeInMillis = millis }
            return CalendarDay(
                year = cal.get(Calendar.YEAR),
                month = cal.get(Calendar.MONTH),
                day = cal.get(Calendar.DAY_OF_MONTH)
            )
        }

        fun today(): CalendarDay = fromMillis(System.currentTimeMillis())
    }
}

private const val BASE_YEAR = 2000
private const val TOTAL_YEARS = 100
private const val TOTAL_PAGES = TOTAL_YEARS * 12

private fun pageToYearMonth(page: Int): Pair<Int, Int> {
    val year = BASE_YEAR + (page / 12)
    val month = page % 12
    return Pair(year, month)
}

private fun yearMonthToPage(year: Int, month: Int): Int {
    return ((year - BASE_YEAR) * 12 + month).coerceIn(0, TOTAL_PAGES - 1)
}

private val MONTH_NAMES_RU = listOf(
    "Январь", "Февраль", "Март", "Апрель", "Май", "Июнь",
    "Июль", "Август", "Сентябрь", "Октябрь", "Ноябрь", "Декабрь"
)

private val MONTH_SHORT_RU = listOf(
    "Янв", "Фев", "Мар", "Апр", "Май", "Июн",
    "Июл", "Авг", "Сен", "Окт", "Ноя", "Дек"
)

private val WEEKDAY_HEADERS_RU = listOf("Пн", "Вт", "Ср", "Чт", "Пт", "Сб", "Вс")

/**
 * Intuitive, modern Jetpack Compose calendar dialog.
 * Features:
 * - Horizontal swipe between months with page animation.
 * - Fast month switching with previous/next chevron buttons.
 * - Fast month & year jump selector (clicking on month/year opens quick 12-month grid).
 * - Quick preset chips ("Сегодня", "Вчера", "7 дней", "30 дней", "Этот месяц").
 * - Supports both SINGLE date selection and date RANGE selection.
 */
@Composable
fun AppCalendarDialog(
    mode: CalendarSelectionMode,
    initialDateMillis: Long? = null,
    initialStartDateMillis: Long? = null,
    initialEndDateMillis: Long? = null,
    title: String? = null,
    onDismiss: () -> Unit,
    onDateSelected: (selectedDateMillis: Long) -> Unit = {},
    onRangeSelected: (startMillis: Long, endMillis: Long) -> Unit = { _, _ -> }
) {
    val today = remember { CalendarDay.today() }

    // Selection states
    var selectedSingleDay by remember {
        mutableStateOf(
            if (initialDateMillis != null) CalendarDay.fromMillis(initialDateMillis) else today
        )
    }

    var rangeStartDay by remember {
        mutableStateOf(
            if (initialStartDateMillis != null) CalendarDay.fromMillis(initialStartDateMillis)
            else if (mode == CalendarSelectionMode.RANGE) today
            else null
        )
    }

    var rangeEndDay by remember {
        mutableStateOf(
            if (initialEndDateMillis != null) CalendarDay.fromMillis(initialEndDateMillis)
            else if (mode == CalendarSelectionMode.RANGE) today
            else null
        )
    }

    // Determine initial page for pager
    val initialDayForPager = when (mode) {
        CalendarSelectionMode.SINGLE -> selectedSingleDay
        CalendarSelectionMode.RANGE -> rangeStartDay ?: today
    }
    val initialPage = remember {
        yearMonthToPage(initialDayForPager.year, initialDayForPager.month)
    }

    val pagerState = rememberPagerState(
        initialPage = initialPage,
        pageCount = { TOTAL_PAGES }
    )
    val coroutineScope = rememberCoroutineScope()

    var isQuickMonthPickerOpen by remember { mutableStateOf(false) }

    val (currentDisplayedYear, currentDisplayedMonth) = pageToYearMonth(pagerState.currentPage)
    var quickPickerYear by remember(currentDisplayedYear) { mutableIntStateOf(currentDisplayedYear) }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Card(
            modifier = Modifier
                .fillMaxWidth(0.95f)
                .widthIn(max = 420.dp)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
                .testTag("app_calendar_dialog_root"),
            shape = RoundedCornerShape(26.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(20.dp)
            ) {
                // Header: Title & Selected Date Preview
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = title ?: if (mode == CalendarSelectionMode.RANGE) "Выбор периода" else "Выбор даты",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Spacer(modifier = Modifier.height(2.dp))

                        val previewText = when (mode) {
                            CalendarSelectionMode.SINGLE -> {
                                val d = selectedSingleDay
                                val date = Calendar.getInstance().apply { set(d.year, d.month, d.day) }.time
                                SimpleDateFormat("d MMMM yyyy (EEEE)", Locale("ru")).format(date)
                            }
                            CalendarSelectionMode.RANGE -> {
                                val start = rangeStartDay
                                val end = rangeEndDay
                                if (start != null && end != null) {
                                    val df = SimpleDateFormat("dd.MM.yyyy", Locale.getDefault())
                                    val date1 = Calendar.getInstance().apply { set(start.year, start.month, start.day) }.time
                                    val date2 = Calendar.getInstance().apply { set(end.year, end.month, end.day) }.time
                                    "${df.format(date1)} — ${df.format(date2)}"
                                } else if (start != null) {
                                    val date1 = Calendar.getInstance().apply { set(start.year, start.month, start.day) }.time
                                    "От: ${SimpleDateFormat("dd.MM.yyyy", Locale.getDefault()).format(date1)}"
                                } else {
                                    "Выберите интервал"
                                }
                            }
                        }

                        Text(
                            text = previewText,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.primary,
                            fontWeight = FontWeight.SemiBold
                        )
                    }

                    // Jump to Today button
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(10.dp))
                            .clickable {
                                val todayPage = yearMonthToPage(today.year, today.month)
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(todayPage)
                                }
                                if (mode == CalendarSelectionMode.SINGLE) {
                                    selectedSingleDay = today
                                }
                            }
                            .testTag("calendar_today_button"),
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                        shape = RoundedCornerShape(10.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = Icons.Default.Today,
                                contentDescription = "Сегодня",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(
                                text = "Сегодня",
                                style = MaterialTheme.typography.labelSmall,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Quick preset buttons row
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    if (mode == CalendarSelectionMode.SINGLE) {
                        CalendarPresetChip(
                            label = "Сегодня",
                            isSelected = selectedSingleDay == today,
                            onClick = {
                                selectedSingleDay = today
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(yearMonthToPage(today.year, today.month))
                                }
                            }
                        )
                        val yesterday = remember {
                            CalendarDay.fromMillis(System.currentTimeMillis() - 86_400_000L)
                        }
                        CalendarPresetChip(
                            label = "Вчера",
                            isSelected = selectedSingleDay == yesterday,
                            onClick = {
                                selectedSingleDay = yesterday
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(yearMonthToPage(yesterday.year, yesterday.month))
                                }
                            }
                        )
                    } else {
                        // Range presets
                        CalendarPresetChip(
                            label = "7 дней",
                            isSelected = false,
                            onClick = {
                                val start = CalendarDay.fromMillis(System.currentTimeMillis() - 6 * 86_400_000L)
                                rangeStartDay = start
                                rangeEndDay = today
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(yearMonthToPage(today.year, today.month))
                                }
                            }
                        )
                        CalendarPresetChip(
                            label = "30 дней",
                            isSelected = false,
                            onClick = {
                                val start = CalendarDay.fromMillis(System.currentTimeMillis() - 29 * 86_400_000L)
                                rangeStartDay = start
                                rangeEndDay = today
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(yearMonthToPage(today.year, today.month))
                                }
                            }
                        )
                        CalendarPresetChip(
                            label = "Этот месяц",
                            isSelected = false,
                            onClick = {
                                val cal = Calendar.getInstance()
                                val start = CalendarDay(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), 1)
                                val end = CalendarDay(cal.get(Calendar.YEAR), cal.get(Calendar.MONTH), cal.getActualMaximum(Calendar.DAY_OF_MONTH))
                                rangeStartDay = start
                                rangeEndDay = end
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(yearMonthToPage(start.year, start.month))
                                }
                            }
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Month/Year navigation bar with chevron buttons and fast picker dropdown
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f),
                            shape = RoundedCornerShape(14.dp)
                        )
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            if (pagerState.currentPage > 0) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage - 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage > 0,
                        modifier = Modifier.testTag("calendar_prev_month")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronLeft,
                            contentDescription = "Предыдущий месяц",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }

                    // Clickable Month & Year Header to toggle quick picker
                    Surface(
                        modifier = Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable {
                                isQuickMonthPickerOpen = !isQuickMonthPickerOpen
                                if (isQuickMonthPickerOpen) {
                                    quickPickerYear = currentDisplayedYear
                                }
                            }
                            .testTag("calendar_month_year_header"),
                        color = if (isQuickMonthPickerOpen) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
                        shape = RoundedCornerShape(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                text = "${MONTH_NAMES_RU[currentDisplayedMonth]} $currentDisplayedYear",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = if (isQuickMonthPickerOpen) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                            )
                            Spacer(modifier = Modifier.width(4.dp))
                            Icon(
                                imageVector = if (isQuickMonthPickerOpen) Icons.Default.ArrowDropUp else Icons.Default.ArrowDropDown,
                                contentDescription = "Выбрать месяц",
                                tint = if (isQuickMonthPickerOpen) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    IconButton(
                        onClick = {
                            if (pagerState.currentPage < TOTAL_PAGES - 1) {
                                coroutineScope.launch {
                                    pagerState.animateScrollToPage(pagerState.currentPage + 1)
                                }
                            }
                        },
                        enabled = pagerState.currentPage < TOTAL_PAGES - 1,
                        modifier = Modifier.testTag("calendar_next_month")
                    ) {
                        Icon(
                            imageVector = Icons.Default.ChevronRight,
                            contentDescription = "Следующий месяц",
                            tint = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                AnimatedContent(
                    targetState = isQuickMonthPickerOpen,
                    transitionSpec = { fadeIn() togetherWith fadeOut() },
                    label = "CalendarPickerView"
                ) { isOpen ->
                    if (isOpen) {
                        // Fast Month & Year selector view
                        QuickMonthYearSelector(
                            year = quickPickerYear,
                            selectedMonth = currentDisplayedMonth,
                            onYearChange = { newYear -> quickPickerYear = newYear },
                            onMonthSelect = { selectedMonth ->
                                val targetPage = yearMonthToPage(quickPickerYear, selectedMonth)
                                isQuickMonthPickerOpen = false
                                coroutineScope.launch {
                                    pagerState.scrollToPage(targetPage)
                                }
                            }
                        )
                    } else {
                        // Standard Month Calendar with horizontal swiping
                        Column(modifier = Modifier.fillMaxWidth()) {
                            // Weekday headers: Пн, Вт, Ср, Чт, Пт, Сб, Вс
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(vertical = 4.dp),
                                horizontalArrangement = Arrangement.SpaceAround
                            ) {
                                WEEKDAY_HEADERS_RU.forEachIndexed { idx, header ->
                                    val isWeekend = (idx >= 5)
                                    Text(
                                        text = header,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.Bold,
                                        color = if (isWeekend) MaterialTheme.colorScheme.error.copy(alpha = 0.8f) else MaterialTheme.colorScheme.onSurfaceVariant,
                                        textAlign = TextAlign.Center,
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }

                            Spacer(modifier = Modifier.height(6.dp))

                            // Horizontal swipeable pager of months
                            HorizontalPager(
                                state = pagerState,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .testTag("calendar_month_pager")
                            ) { page ->
                                val (y, m) = pageToYearMonth(page)
                                MonthDaysGrid(
                                    year = y,
                                    month = m,
                                    mode = mode,
                                    today = today,
                                    selectedSingleDay = selectedSingleDay,
                                    rangeStartDay = rangeStartDay,
                                    rangeEndDay = rangeEndDay,
                                    onDayClick = { clickedDay ->
                                        when (mode) {
                                            CalendarSelectionMode.SINGLE -> {
                                                selectedSingleDay = clickedDay
                                            }
                                            CalendarSelectionMode.RANGE -> {
                                                val start = rangeStartDay
                                                val end = rangeEndDay
                                                if (start == null || end != null) {
                                                    // Starting new range
                                                    rangeStartDay = clickedDay
                                                    rangeEndDay = null
                                                } else {
                                                    // Completing range
                                                    if (clickedDay < start) {
                                                        // Tapped before start -> set as new start
                                                        rangeStartDay = clickedDay
                                                        rangeEndDay = null
                                                    } else {
                                                        rangeEndDay = clickedDay
                                                    }
                                                }
                                            }
                                        }
                                    }
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Bottom Action Buttons
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("calendar_dialog_dismiss_button")
                    ) {
                        Text("Отмена")
                    }

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            when (mode) {
                                CalendarSelectionMode.SINGLE -> {
                                    onDateSelected(selectedSingleDay.toStartOfDayMillis())
                                }
                                CalendarSelectionMode.RANGE -> {
                                    val start = rangeStartDay ?: today
                                    val end = rangeEndDay ?: start
                                    onRangeSelected(start.toStartOfDayMillis(), end.toEndOfDayMillis())
                                }
                            }
                            onDismiss()
                        },
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("calendar_dialog_confirm_button")
                    ) {
                        Icon(Icons.Default.Check, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Применить", fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

/**
 * 7-column day grid for a single calendar month.
 */
@Composable
private fun MonthDaysGrid(
    year: Int,
    month: Int,
    mode: CalendarSelectionMode,
    today: CalendarDay,
    selectedSingleDay: CalendarDay?,
    rangeStartDay: CalendarDay?,
    rangeEndDay: CalendarDay?,
    onDayClick: (CalendarDay) -> Unit
) {
    val cal = remember(year, month) {
        Calendar.getInstance().apply {
            set(year, month, 1)
        }
    }
    val daysInMonth = remember(year, month) {
        cal.getActualMaximum(Calendar.DAY_OF_MONTH)
    }
    // Monday is index 0. (DAY_OF_WEEK: Sunday=1, Monday=2 -> (2+5)%7=0)
    val firstDayOfWeekOffset = remember(year, month) {
        (cal.get(Calendar.DAY_OF_WEEK) + 5) % 7
    }

    val totalCells = firstDayOfWeekOffset + daysInMonth
    val rows = (totalCells + 6) / 7

    Column(
        modifier = Modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(4.dp)
    ) {
        for (row in 0 until rows) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceAround
            ) {
                for (col in 0 until 7) {
                    val cellIndex = row * 7 + col
                    val dayNumber = cellIndex - firstDayOfWeekOffset + 1

                    if (dayNumber in 1..daysInMonth) {
                        val currentDay = CalendarDay(year, month, dayNumber)
                        val isToday = (currentDay == today)

                        val isSingleSelected = (mode == CalendarSelectionMode.SINGLE && currentDay == selectedSingleDay)
                        val isRangeStart = (mode == CalendarSelectionMode.RANGE && currentDay == rangeStartDay)
                        val isRangeEnd = (mode == CalendarSelectionMode.RANGE && currentDay == rangeEndDay)
                        val isInRange = (mode == CalendarSelectionMode.RANGE &&
                                rangeStartDay != null && rangeEndDay != null &&
                                currentDay > rangeStartDay && currentDay < rangeEndDay)

                        val isEndpoint = isRangeStart || isRangeEnd

                        DayCell(
                            day = dayNumber,
                            isToday = isToday,
                            isSelected = isSingleSelected || isEndpoint,
                            isInRange = isInRange,
                            isRangeStart = isRangeStart,
                            isRangeEnd = isRangeEnd,
                            onClick = { onDayClick(currentDay) },
                            modifier = Modifier.weight(1f)
                        )
                    } else {
                        // Empty cell for alignment
                        Spacer(modifier = Modifier.weight(1f))
                    }
                }
            }
        }
    }
}

/**
 * Single day cell in the calendar with selection highlight and range band.
 */
@Composable
private fun DayCell(
    day: Int,
    isToday: Boolean,
    isSelected: Boolean,
    isInRange: Boolean,
    isRangeStart: Boolean,
    isRangeEnd: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(1.dp),
        contentAlignment = Alignment.Center
    ) {
        // Range continuous background strip
        if (isInRange || isRangeStart || isRangeEnd) {
            val rangeColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.45f)
            val stripShape = when {
                isRangeStart && isRangeEnd -> RoundedCornerShape(18.dp)
                isRangeStart -> RoundedCornerShape(topStart = 18.dp, bottomStart = 18.dp)
                isRangeEnd -> RoundedCornerShape(topEnd = 18.dp, bottomEnd = 18.dp)
                else -> RoundedCornerShape(0.dp)
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .background(rangeColor, shape = stripShape)
            )
        }

        // Selected circle / button
        val cellShape = CircleShape
        val cellBackground = if (isSelected) {
            MaterialTheme.colorScheme.primary
        } else {
            Color.Transparent
        }

        val textColor = when {
            isSelected -> MaterialTheme.colorScheme.onPrimary
            isInRange -> MaterialTheme.colorScheme.onPrimaryContainer
            isToday -> MaterialTheme.colorScheme.primary
            else -> MaterialTheme.colorScheme.onSurface
        }

        Box(
            modifier = Modifier
                .size(34.dp)
                .clip(cellShape)
                .background(cellBackground, shape = cellShape)
                .clickable(onClick = onClick)
                .testTag("calendar_day_$day"),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = day.toString(),
                style = MaterialTheme.typography.bodyMedium,
                fontWeight = if (isSelected || isToday) FontWeight.Bold else FontWeight.Normal,
                color = textColor,
                textAlign = TextAlign.Center
            )

            // Today subtle bottom dot indicator if not selected
            if (isToday && !isSelected) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 3.dp)
                        .size(4.dp)
                        .background(MaterialTheme.colorScheme.primary, CircleShape)
                )
            }
        }
    }
}

/**
 * Fast Month & Year selector overlay.
 */
@Composable
private fun QuickMonthYearSelector(
    year: Int,
    selectedMonth: Int,
    onYearChange: (Int) -> Unit,
    onMonthSelect: (Int) -> Unit
) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp)
    ) {
        // Year stepper row
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(
                onClick = { onYearChange(year - 1) },
                modifier = Modifier.testTag("quick_picker_prev_year")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Предыдущий год")
            }

            Text(
                text = year.toString(),
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.ExtraBold,
                color = MaterialTheme.colorScheme.primary
            )

            IconButton(
                onClick = { onYearChange(year + 1) },
                modifier = Modifier.testTag("quick_picker_next_year")
            ) {
                Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = "Следующий год")
            }
        }

        Spacer(modifier = Modifier.height(10.dp))

        // 3x4 Grid of Month chips
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            for (row in 0 until 4) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    for (col in 0 until 3) {
                        val monthIdx = row * 3 + col
                        val isCurrent = (monthIdx == selectedMonth)
                        Surface(
                            modifier = Modifier
                                .weight(1f)
                                .height(44.dp)
                                .clip(RoundedCornerShape(12.dp))
                                .clickable { onMonthSelect(monthIdx) }
                                .testTag("quick_picker_month_$monthIdx"),
                            shape = RoundedCornerShape(12.dp),
                            color = if (isCurrent) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Text(
                                    text = MONTH_SHORT_RU[monthIdx],
                                    style = MaterialTheme.typography.bodyMedium,
                                    fontWeight = if (isCurrent) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isCurrent) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Filter chip for calendar presets (e.g. "Сегодня", "7 дней", "30 дней").
 */
@Composable
private fun CalendarPresetChip(
    label: String,
    isSelected: Boolean,
    onClick: () -> Unit
) {
    Surface(
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(8.dp),
        color = if (isSelected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Medium,
            color = if (isSelected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
        )
    }
}
