package com.example.ui

import androidx.activity.compose.BackHandler
import androidx.compose.animation.Crossfade
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.FitnessCenter
import androidx.compose.material.icons.filled.FormatListBulleted
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ShowChart
import androidx.compose.material3.CenterAlignedTopAppBar
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.example.data.BodyMetricEntry
import com.example.ui.components.AddEditMetricDialog
import com.example.ui.screens.ChartsScreen
import com.example.ui.screens.JournalScreen
import com.example.ui.screens.MiFitnessImportScreen
import com.example.ui.screens.SettingsScreen

enum class AppTab(val titleRu: String, val icon: ImageVector, val tag: String) {
    JOURNAL("Записи", Icons.Default.FormatListBulleted, "tab_journal"),
    CHARTS("Графики", Icons.Default.ShowChart, "tab_charts"),
    SETTINGS("Настройки", Icons.Default.Settings, "tab_settings")
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MainScreen(
    viewModel: BodyMetricsViewModel,
    modifier: Modifier = Modifier
) {
    var selectedTab by remember { mutableStateOf(AppTab.JOURNAL) }
    val snackbarHostState = remember { SnackbarHostState() }

    val entriesDesc by viewModel.allEntriesDescending.collectAsStateWithLifecycle()
    val entriesAsc by viewModel.allEntriesAscending.collectAsStateWithLifecycle()
    val chartEntries by viewModel.chartEntries.collectAsStateWithLifecycle()
    val availableMonths by viewModel.availableMonths.collectAsStateWithLifecycle()
    val selectedMetric by viewModel.selectedMetric.collectAsStateWithLifecycle()
    val selectedTimeFilter by viewModel.selectedTimeFilter.collectAsStateWithLifecycle()
    val selectedMonthKey by viewModel.selectedMonthKey.collectAsStateWithLifecycle()
    val customDateRange by viewModel.customDateRange.collectAsStateWithLifecycle()

    val userAge by viewModel.userAge.collectAsStateWithLifecycle()
    val targetWeightKg by viewModel.targetWeightKg.collectAsStateWithLifecycle()
    val weeklyReminderEnabled by viewModel.weeklyReminderEnabled.collectAsStateWithLifecycle()

    val userMessage by viewModel.userMessage.collectAsStateWithLifecycle()
    val isAddEditOpen by viewModel.isAddEditOpen.collectAsStateWithLifecycle()
    val editingEntry by viewModel.editingEntry.collectAsStateWithLifecycle()

    var showMiFitnessScreen by remember { mutableStateOf(false) }

    val isMiFitnessParsing by viewModel.isMiFitnessParsing.collectAsStateWithLifecycle()
    val miFitnessProgressLines by viewModel.miFitnessProgressLines.collectAsStateWithLifecycle()
    val miFitnessParseResult by viewModel.miFitnessParseResult.collectAsStateWithLifecycle()
    val miFitnessErrorMessage by viewModel.miFitnessErrorMessage.collectAsStateWithLifecycle()

    if (showMiFitnessScreen) {
        MiFitnessImportScreen(
            isParsing = isMiFitnessParsing,
            progressLines = miFitnessProgressLines,
            parseResult = miFitnessParseResult,
            errorMessage = miFitnessErrorMessage,
            onSelectFile = { uri -> viewModel.parseMiFitnessArchive(uri) },
            onConfirmImport = { result, updateHeight ->
                viewModel.applyMiFitnessImport(result, updateHeight)
                showMiFitnessScreen = false
            },
            onDismissResult = { viewModel.clearMiFitnessState() },
            onDismissError = { viewModel.clearMiFitnessState() },
            onNavigateBack = {
                viewModel.clearMiFitnessState()
                showMiFitnessScreen = false
            }
        )
        return
    }

    // Handle back button: if on non-journal tab, return to Journal tab
    BackHandler(enabled = selectedTab != AppTab.JOURNAL) {
        selectedTab = AppTab.JOURNAL
    }

    LaunchedEffect(userMessage) {
        userMessage?.let { msg ->
            snackbarHostState.showSnackbar(msg)
            viewModel.clearUserMessage()
        }
    }

    val latestEntry = entriesDesc.firstOrNull()
    val latestHeight = latestEntry?.heightCm ?: 175f
    val latestWeight = latestEntry?.weightKg ?: 70f

    Scaffold(
        modifier = modifier.fillMaxSize(),
        topBar = {
            CenterAlignedTopAppBar(
                title = {
                    Text(
                        text = when (selectedTab) {
                            AppTab.JOURNAL -> "BodyMetrics"
                            AppTab.CHARTS -> "Графики параметров"
                            AppTab.SETTINGS -> "Настройки профиля"
                        },
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                },
                navigationIcon = {
                    Surface(
                        modifier = Modifier.padding(start = 12.dp),
                        shape = MaterialTheme.shapes.small,
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f)
                    ) {
                        Icon(
                            imageVector = Icons.Default.FitnessCenter,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier
                                .padding(6.dp)
                                .size(20.dp)
                        )
                    }
                },
                actions = {
                    if (selectedTab != AppTab.JOURNAL) {
                        IconButton(
                            onClick = { viewModel.openAddDialog() },
                            modifier = Modifier.testTag("topbar_add_button")
                        ) {
                            Icon(
                                imageVector = Icons.Default.Add,
                                contentDescription = "Добавить замер",
                                tint = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                },
                colors = TopAppBarDefaults.centerAlignedTopAppBarColors(
                    containerColor = MaterialTheme.colorScheme.surface
                )
            )
        },
        bottomBar = {
            NavigationBar(
                modifier = Modifier
                    .testTag("bottom_nav_bar")
                    .windowInsetsPadding(WindowInsets.navigationBars),
                containerColor = MaterialTheme.colorScheme.surface,
                tonalElevation = 4.dp
            ) {
                AppTab.values().forEach { tab ->
                    NavigationBarItem(
                        selected = (selectedTab == tab),
                        onClick = { selectedTab = tab },
                        icon = {
                            Icon(imageVector = tab.icon, contentDescription = tab.titleRu)
                        },
                        label = { Text(tab.titleRu) },
                        modifier = Modifier.testTag(tab.tag)
                    )
                }
            }
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
        ) {
            Crossfade(targetState = selectedTab, label = "TabCrossfade") { tab ->
                when (tab) {
                    AppTab.JOURNAL -> {
                        JournalScreen(
                            entries = entriesAsc,
                            targetWeightKg = targetWeightKg,
                            onAddClick = { viewModel.openAddDialog() },
                            onEditClick = { entry -> viewModel.openEditDialog(entry) },
                            onDeleteClick = { entry -> viewModel.deleteEntry(entry) },
                            onGenerateDemoClick = { viewModel.generateDemoData() }
                        )
                    }
                    AppTab.CHARTS -> {
                        ChartsScreen(
                            entriesAscending = chartEntries,
                            latestWeight = latestEntry?.weightKg,
                            selectedMetric = selectedMetric,
                            selectedTimeFilter = selectedTimeFilter,
                            selectedMonthKey = selectedMonthKey,
                            customDateRange = customDateRange,
                            availableMonths = availableMonths,
                            targetWeightKg = targetWeightKg,
                            onMetricSelect = { metric -> viewModel.selectMetric(metric) },
                            onTimeFilterSelect = { filter -> viewModel.selectTimeFilter(filter) },
                            onMonthSelect = { monthKey -> viewModel.selectMonth(monthKey) },
                            onCustomDateRangeSelect = { start, end -> viewModel.setCustomDateRange(start, end) },
                            onNavigateToSettings = { selectedTab = AppTab.SETTINGS }
                        )
                    }
                    AppTab.SETTINGS -> {
                        SettingsScreen(
                            currentAge = userAge,
                            targetWeightKg = targetWeightKg,
                            userHeightCm = latestHeight,
                            weeklyReminderEnabled = weeklyReminderEnabled,
                            entryCount = entriesDesc.size,
                            onSaveAge = { age -> viewModel.updateUserAge(age) },
                            onSaveTargetWeight = { target -> viewModel.updateTargetWeight(target) },
                            onToggleWeeklyReminder = { enabled -> viewModel.setWeeklyReminder(enabled) },
                            onGetCsvData = { viewModel.exportToCsv() },
                            onGetTextSummary = { viewModel.exportToTextSummary() },
                            onPrepareCloudBackupFile = { viewModel.createBackupFileForShare() },
                            onImportCsv = { csv -> viewModel.importCsv(csv) },
                            onGetJsonBackup = { viewModel.exportJsonBackup() },
                            onPrepareJsonImport = { json -> viewModel.prepareJsonBackupSummary(json) },
                            onApplyJsonImport = { summary -> viewModel.applyJsonImport(summary) },
                            onNavigateToMiFitness = { showMiFitnessScreen = true },
                            onGenerateDemo = { viewModel.generateDemoData() },
                            onClearAll = { viewModel.clearAllData() },
                            onShowMessage = { msg -> viewModel.showMessage(msg) }
                        )
                    }
                }
            }
        }
    }

    if (isAddEditOpen) {
        AddEditMetricDialog(
            initialEntry = editingEntry,
            lastHeightDefault = latestHeight,
            lastWeightDefault = latestWeight,
            onDismiss = { viewModel.closeAddEditDialog() },
            onSave = { entry -> viewModel.saveEntry(entry) }
        )
    }
}
