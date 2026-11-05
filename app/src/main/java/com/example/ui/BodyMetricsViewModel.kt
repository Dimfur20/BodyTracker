package com.example.ui

import android.app.Application
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.example.data.AppDatabase
import com.example.data.BodyMetricEntry
import com.example.data.BodyMetricRepository
import com.example.data.TrackableMetric
import com.example.data.UserSettingsRepository
import com.example.util.NotificationScheduler
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import com.example.data.backup.JsonBackupManager
import com.example.data.backup.JsonImportSummary
import com.example.data.backup.MiFitnessArchiveParser
import com.example.data.backup.MiFitnessParseResult
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

enum class TimeFilter(val titleRu: String, val days: Int?) {
    MONTH_1("30 дней", 30),
    MONTH_3("3 месяца", 90),
    YEAR_1("1 год", 365),
    ALL("Всё время", null),
    CUSTOM("Свой период", null)
}

data class MonthOption(
    val key: String, // e.g. "2026-11"
    val labelRu: String // e.g. "Ноябрь 2026"
)

class BodyMetricsViewModel(application: Application) : AndroidViewModel(application) {

    private val repository: BodyMetricRepository
    private val settingsRepository: UserSettingsRepository

    init {
        val db = AppDatabase.getInstance(application)
        repository = BodyMetricRepository(db.bodyMetricDao())
        settingsRepository = UserSettingsRepository(application)
        NotificationScheduler.createNotificationChannel(application)
    }

    val userAge: StateFlow<Int?> = settingsRepository.userAge
    val targetWeightKg: StateFlow<Float?> = settingsRepository.targetWeightKg
    val weeklyReminderEnabled: StateFlow<Boolean> = settingsRepository.weeklyReminderEnabled
    val isSecureScreenEnabled: StateFlow<Boolean> = settingsRepository.isSecureScreenEnabled
    val isBiometricLockEnabled: StateFlow<Boolean> = settingsRepository.isBiometricLockEnabled

    private val _isAppLocked = MutableStateFlow(false)
    val isAppLocked: StateFlow<Boolean> = _isAppLocked.asStateFlow()

    // Undo deletion state: holds the last deleted entry and callback to show snackbar
    private val _pendingUndoEntry = MutableStateFlow<BodyMetricEntry?>(null)
    val pendingUndoEntry: StateFlow<BodyMetricEntry?> = _pendingUndoEntry.asStateFlow()

    val allEntriesDescending: StateFlow<List<BodyMetricEntry>> = repository.allEntriesDescending
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    val allEntriesAscending: StateFlow<List<BodyMetricEntry>> = repository.allEntriesAscending
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = emptyList()
        )

    private val _selectedMetric = MutableStateFlow(TrackableMetric.WEIGHT)
    val selectedMetric: StateFlow<TrackableMetric> = _selectedMetric.asStateFlow()

    private val _selectedTimeFilter = MutableStateFlow(TimeFilter.ALL)
    val selectedTimeFilter: StateFlow<TimeFilter> = _selectedTimeFilter.asStateFlow()

    private val _customDateRange = MutableStateFlow<Pair<Long, Long>?>(null)
    val customDateRange: StateFlow<Pair<Long, Long>?> = _customDateRange.asStateFlow()

    private val _selectedMonthKey = MutableStateFlow<String?>(null)
    val selectedMonthKey: StateFlow<String?> = _selectedMonthKey.asStateFlow()

    private val _userMessage = MutableStateFlow<String?>(null)
    val userMessage: StateFlow<String?> = _userMessage.asStateFlow()

    private val _editingEntry = MutableStateFlow<BodyMetricEntry?>(null)
    val editingEntry: StateFlow<BodyMetricEntry?> = _editingEntry.asStateFlow()

    private val _isAddEditOpen = MutableStateFlow(false)
    val isAddEditOpen: StateFlow<Boolean> = _isAddEditOpen.asStateFlow()

    private val _isMiFitnessParsing = MutableStateFlow(false)
    val isMiFitnessParsing: StateFlow<Boolean> = _isMiFitnessParsing.asStateFlow()

    private val _miFitnessProgressLines = MutableStateFlow(0)
    val miFitnessProgressLines: StateFlow<Int> = _miFitnessProgressLines.asStateFlow()

    private val _miFitnessParseResult = MutableStateFlow<MiFitnessParseResult?>(null)
    val miFitnessParseResult: StateFlow<MiFitnessParseResult?> = _miFitnessParseResult.asStateFlow()

    private val _miFitnessErrorMessage = MutableStateFlow<String?>(null)
    val miFitnessErrorMessage: StateFlow<String?> = _miFitnessErrorMessage.asStateFlow()

    // Distinct list of months available in the recorded dataset
    val availableMonths: StateFlow<List<MonthOption>> = allEntriesAscending.map { entries ->
        val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
        val labelFormat = SimpleDateFormat("LLLL yyyy", Locale("ru"))
        val map = linkedMapOf<String, String>()

        for (entry in entries.reversed()) {
            val d = Date(entry.dateMillis)
            val k = keyFormat.format(d)
            if (!map.containsKey(k)) {
                val rawLabel = labelFormat.format(d)
                val capitalized = rawLabel.replaceFirstChar { if (it.isLowerCase()) it.titlecase(Locale("ru")) else it.toString() }
                map[k] = capitalized
            }
        }
        map.map { MonthOption(key = it.key, labelRu = it.value) }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    // Filtered entries for chart: if a specific month is selected, filter by that month;
    // if CUSTOM is selected, filter by custom date range; otherwise use standard time filter
    val chartEntries: StateFlow<List<BodyMetricEntry>> = combine(
        allEntriesAscending,
        _selectedTimeFilter,
        _selectedMonthKey,
        _customDateRange
    ) { entries, filter, monthKey, customRange ->
        if (monthKey != null) {
            val keyFormat = SimpleDateFormat("yyyy-MM", Locale.US)
            entries.filter { keyFormat.format(Date(it.dateMillis)) == monthKey }
        } else if (filter == TimeFilter.CUSTOM && customRange != null) {
            entries.filter { it.dateMillis in customRange.first..customRange.second }
        } else if (filter.days != null && entries.isNotEmpty()) {
            val cutoff = System.currentTimeMillis() - (filter.days.toLong() * 86_400_000L)
            entries.filter { it.dateMillis >= cutoff }
        } else {
            entries
        }
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5000),
        initialValue = emptyList()
    )

    fun selectMetric(metric: TrackableMetric) {
        _selectedMetric.value = metric
    }

    fun selectTimeFilter(filter: TimeFilter) {
        _selectedMonthKey.value = null // clear explicit month when choosing general time filter
        _selectedTimeFilter.value = filter
    }

    fun setCustomDateRange(startUtcMillis: Long, endUtcMillis: Long) {
        val utcCal = java.util.Calendar.getInstance(java.util.TimeZone.getTimeZone("UTC")).apply {
            timeInMillis = startUtcMillis
        }
        val startYear = utcCal.get(java.util.Calendar.YEAR)
        val startMonth = utcCal.get(java.util.Calendar.MONTH)
        val startDay = utcCal.get(java.util.Calendar.DAY_OF_MONTH)

        val localStart = java.util.Calendar.getInstance().apply {
            set(startYear, startMonth, startDay, 0, 0, 0)
            set(java.util.Calendar.MILLISECOND, 0)
        }.timeInMillis

        utcCal.timeInMillis = endUtcMillis
        val endYear = utcCal.get(java.util.Calendar.YEAR)
        val endMonth = utcCal.get(java.util.Calendar.MONTH)
        val endDay = utcCal.get(java.util.Calendar.DAY_OF_MONTH)

        val localEnd = java.util.Calendar.getInstance().apply {
            set(endYear, endMonth, endDay, 23, 59, 59)
            set(java.util.Calendar.MILLISECOND, 999)
        }.timeInMillis

        _customDateRange.value = Pair(localStart, localEnd)
        _selectedMonthKey.value = null
        _selectedTimeFilter.value = TimeFilter.CUSTOM
    }

    fun clearCustomDateRange() {
        _customDateRange.value = null
        if (_selectedTimeFilter.value == TimeFilter.CUSTOM) {
            _selectedTimeFilter.value = TimeFilter.ALL
        }
    }

    fun selectMonth(monthKey: String?) {
        _selectedMonthKey.value = monthKey
    }

    fun updateUserAge(age: Int?) {
        settingsRepository.setAge(age)
        _userMessage.value = if (age != null) "Возраст сохранён: $age лет" else "Возраст очищен"
    }

    fun updateTargetWeight(target: Float?) {
        settingsRepository.setTargetWeight(target)
        _userMessage.value = if (target != null) "Целевой вес: $target кг" else "Целевой вес очищен"
    }

    fun setWeeklyReminder(enabled: Boolean) {
        settingsRepository.setWeeklyReminder(enabled)
        val context = getApplication<Application>()
        if (enabled) {
            NotificationScheduler.scheduleWeeklyReminder(context)
            _userMessage.value = "Еженедельное напоминание включено"
        } else {
            NotificationScheduler.cancelWeeklyReminder(context)
            _userMessage.value = "Напоминание выключено"
        }
    }

    fun setSecureScreenEnabled(enabled: Boolean) {
        settingsRepository.setSecureScreenEnabled(enabled)
        _userMessage.value = if (enabled) "Скрытие в списке приложений и защита скриншотов включены" else "Защита скриншотов выключена"
    }

    fun setBiometricLockEnabled(enabled: Boolean) {
        settingsRepository.setBiometricLockEnabled(enabled)
        _userMessage.value = if (enabled) "Блокировка устройства включена" else "Блокировка устройства выключена"
    }

    fun lockApp() {
        if (isBiometricLockEnabled.value) {
            _isAppLocked.value = true
        }
    }

    fun unlockApp() {
        _isAppLocked.value = false
    }

    fun openAddDialog() {
        _editingEntry.value = null
        _isAddEditOpen.value = true
    }

    fun openEditDialog(entry: BodyMetricEntry) {
        _editingEntry.value = entry
        _isAddEditOpen.value = true
    }

    fun closeAddEditDialog() {
        _editingEntry.value = null
        _isAddEditOpen.value = false
    }

    fun clearUserMessage() {
        _userMessage.value = null
    }

    fun showMessage(msg: String) {
        _userMessage.value = msg
    }

    fun saveEntry(entry: BodyMetricEntry) {
        viewModelScope.launch {
            if (entry.id == 0L) {
                repository.insert(entry)
                _userMessage.value = "Замер успешно добавлен"
            } else {
                repository.update(entry)
                _userMessage.value = "Замер обновлен"
            }
            closeAddEditDialog()
        }
    }

    fun deleteEntry(entry: BodyMetricEntry) {
        viewModelScope.launch {
            repository.delete(entry)
            _pendingUndoEntry.value = entry
        }
    }

    fun restoreEntry(entry: BodyMetricEntry) {
        viewModelScope.launch {
            repository.insert(entry)
            _userMessage.value = "Замер восстановлен"
            _pendingUndoEntry.value = null
        }
    }

    fun clearPendingUndo() {
        _pendingUndoEntry.value = null
    }

    fun generateDemoData() {
        viewModelScope.launch {
            repository.generateDemoData()
            _userMessage.value = "Тестовые данные загружены"
        }
    }

    fun clearAllData() {
        viewModelScope.launch {
            repository.deleteAll()
            _userMessage.value = "Все записи очищены"
        }
    }

    fun exportToCsv(): String {
        return repository.exportToCsv(allEntriesDescending.value)
    }

    fun exportToTextSummary(): String {
        return repository.exportToTextSummary(allEntriesDescending.value)
    }

    /**
     * Prepares a file in cacheDir/exports and returns a FileProvider Uri for Google Drive or cloud apps.
     * Cleans up previously created export files immediately.
     */
    fun createBackupFileForShare(): Pair<Uri, String>? {
        val context = getApplication<Application>()
        return try {
            val exportsDir = File(context.cacheDir, "exports").apply {
                if (!exists()) mkdirs()
            }
            // Delete previous temporary exports
            exportsDir.listFiles()?.forEach { it.delete() }

            val fileDate = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
            val fileName = "bodymetrics_backup_$fileDate.csv"
            val cacheFile = File(exportsDir, fileName)
            val csvContent = exportToCsv()
            val contentWithBom = if (!csvContent.startsWith("\uFEFF")) "\uFEFF$csvContent" else csvContent
            cacheFile.writeText(contentWithBom, Charsets.UTF_8)
            val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", cacheFile)
            Pair(uri, fileName)
        } catch (_: Exception) {
            null
        }
    }

    fun importCsv(csvContent: String) {
        viewModelScope.launch {
            try {
                val lastHeight = allEntriesDescending.value.firstOrNull()?.heightCm
                val parseResult = repository.parseCsvDetailed(
                    csvText = csvContent,
                    lastKnownHeight = lastHeight,
                    existingEntries = allEntriesDescending.value
                )
                if (parseResult.entries.isEmpty() && parseResult.duplicatesCount == 0 && parseResult.skippedInvalidCount == 0) {
                    _userMessage.value = "Ошибка: не удалось распознать строки CSV"
                } else {
                    if (parseResult.entries.isNotEmpty()) {
                        repository.insertAll(parseResult.entries)
                    }
                    val msg = buildString {
                        append("Импорт CSV: добавлено ${parseResult.entries.size}")
                        if (parseResult.duplicatesCount > 0) {
                            append(", дубликатов: ${parseResult.duplicatesCount}")
                        }
                        if (parseResult.skippedInvalidCount > 0) {
                            append(", пропущено некорректных: ${parseResult.skippedInvalidCount}")
                        }
                    }
                    _userMessage.value = msg
                }
            } catch (e: Exception) {
                _userMessage.value = "Ошибка при импорте: ${e.localizedMessage ?: "неверный формат"}"
            }
        }
    }

    // --- JSON Backup & Restore ---

    fun exportJsonBackup(): String {
        return JsonBackupManager.exportToJson(
            entries = allEntriesDescending.value,
            userAge = userAge.value,
            targetWeightKg = targetWeightKg.value,
            weeklyReminderEnabled = weeklyReminderEnabled.value
        )
    }

    fun prepareJsonBackupSummary(jsonString: String): Result<JsonImportSummary> {
        return JsonBackupManager.parseAndValidateJson(jsonString, allEntriesDescending.value)
    }

    fun applyJsonImport(summary: JsonImportSummary) {
        viewModelScope.launch {
            if (summary.entriesToAdd.isNotEmpty()) {
                repository.insertAll(summary.entriesToAdd)
            }
            summary.settingsToRestore?.let { settings ->
                settings.age?.let { settingsRepository.setAge(it) }
                settings.targetWeightKg?.let { settingsRepository.setTargetWeight(it) }
                settings.weeklyReminderEnabled?.let { setWeeklyReminder(it) }
            }
            val msg = buildString {
                append("Импорт завершён: добавлено ${summary.entriesToAdd.size}, дубликатов: ${summary.duplicatesCount}")
                if (summary.skippedInvalidCount > 0) {
                    append(", пропущено некорректных: ${summary.skippedInvalidCount}")
                }
            }
            _userMessage.value = msg
        }
    }

    // --- Mi Fitness (Xiaomi) Import ---

    fun parseMiFitnessArchive(uri: Uri) {
        viewModelScope.launch {
            _isMiFitnessParsing.value = true
            _miFitnessProgressLines.value = 0
            _miFitnessErrorMessage.value = null
            _miFitnessParseResult.value = null

            val currentHeight = allEntriesDescending.value.firstOrNull()?.heightCm ?: 175f
            val result = MiFitnessArchiveParser.parseArchive(
                context = getApplication(),
                zipUri = uri,
                existingEntries = allEntriesDescending.value,
                userCurrentHeight = currentHeight,
                onProgress = { lines ->
                    _miFitnessProgressLines.value = lines
                }
            )

            _isMiFitnessParsing.value = false
            result.onSuccess { parsed ->
                _miFitnessParseResult.value = parsed
            }.onFailure { err ->
                _miFitnessErrorMessage.value = err.localizedMessage ?: "Не удалось обработать архив"
            }
        }
    }

    fun clearMiFitnessState() {
        _miFitnessParseResult.value = null
        _miFitnessErrorMessage.value = null
        _isMiFitnessParsing.value = false
        _miFitnessProgressLines.value = 0
    }

    fun applyMiFitnessImport(result: MiFitnessParseResult, updateHeight: Boolean) {
        viewModelScope.launch {
            if (result.newRecords.isNotEmpty()) {
                repository.insertAll(result.newRecords)
            }
            if (updateHeight && result.profileHeightCm != null) {
                val latest = repository.getLatestEntry()
                if (latest != null) {
                    repository.update(latest.copy(heightCm = result.profileHeightCm))
                }
            }
            val msg = buildString {
                append("Импорт из Mi Fitness завершён: +${result.newRecords.size} записей, дубликатов: ${result.duplicatesCount}")
                if (result.skippedInvalidCount > 0) {
                    append(", пропущено некорректных: ${result.skippedInvalidCount}")
                }
            }
            _userMessage.value = msg
            clearMiFitnessState()
        }
    }
}
