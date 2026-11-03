package com.example.ui.screens

import android.Manifest
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AddAlert
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DeleteSweep
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PlayCircleOutline
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.example.ui.theme.MetricGreen
import com.example.util.MetricValidationUtil
import com.example.util.NotificationScheduler
import java.io.BufferedReader
import java.io.InputStreamReader
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

data class NormalBmiPreset(
    val title: String,
    val subtitle: String,
    val targetBmi: Float
)

@Composable
fun SettingsScreen(
    currentAge: Int?,
    targetWeightKg: Float?,
    userHeightCm: Float = 175f,
    weeklyReminderEnabled: Boolean = false,
    entryCount: Int,
    onSaveAge: (Int?) -> Unit,
    onSaveTargetWeight: (Float?) -> Unit,
    onToggleWeeklyReminder: (Boolean) -> Unit,
    onGetCsvData: () -> String,
    onGetTextSummary: () -> String,
    onPrepareCloudBackupFile: () -> Pair<Uri, String>?,
    onImportCsv: (String) -> Unit,
    onGenerateDemo: () -> Unit,
    onClearAll: () -> Unit,
    onShowMessage: (String) -> Unit,
    modifier: Modifier = Modifier
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()
    var showClearConfirmDialog by remember { mutableStateOf(false) }

    var ageInput by remember(currentAge) {
        mutableStateOf(currentAge?.toString() ?: "")
    }
    var targetWeightInput by remember(targetWeightKg) {
        mutableStateOf(targetWeightKg?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }

    // Permission launcher for POST_NOTIFICATIONS (Android 13+)
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { isGranted ->
        if (isGranted) {
            onToggleWeeklyReminder(true)
        } else {
            onShowMessage("Разрешение на уведомления отклонено")
            onToggleWeeklyReminder(false)
        }
    }

    // 5 Normal BMI Presets
    val bmiPresets = remember {
        listOf(
            NormalBmiPreset("Нижний предел", "ИМТ 18.5", 18.5f),
            NormalBmiPreset("Между низом и сер.", "ИМТ 20.1", 20.1f),
            NormalBmiPreset("Середина", "ИМТ 21.7", 21.7f),
            NormalBmiPreset("Между сер. и верхом", "ИМТ 23.3", 23.3f),
            NormalBmiPreset("Верхний предел", "ИМТ 24.9", 24.9f)
        )
    }

    val heightM = (if (userHeightCm > 50f) userHeightCm else 175f) / 100f
    val currentTargetBmi = if (targetWeightKg != null && heightM > 0f) {
        targetWeightKg / (heightM * heightM)
    } else 21.7f

    var sliderStep by remember(targetWeightKg, userHeightCm) {
        val closestIndex = bmiPresets.indices.minByOrNull {
            kotlin.math.abs(bmiPresets[it].targetBmi - currentTargetBmi)
        } ?: 2
        mutableFloatStateOf(closestIndex.toFloat())
    }

    // Launcher for saving CSV to storage
    val createDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val csvContent = onGetCsvData()
                val contentWithBom = if (!csvContent.startsWith("\uFEFF")) {
                    "\uFEFF$csvContent"
                } else {
                    csvContent
                }
                context.contentResolver.openOutputStream(uri)?.use { stream ->
                    stream.write(contentWithBom.toByteArray(Charsets.UTF_8))
                }
                onShowMessage("Файл CSV успешно сохранён!")
            } catch (e: Exception) {
                onShowMessage("Ошибка при сохранении: ${e.localizedMessage}")
            }
        }
    }

    // Launcher for importing CSV from storage
    val openDocumentLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri: Uri? ->
        if (uri != null) {
            try {
                val sb = StringBuilder()
                context.contentResolver.openInputStream(uri)?.use { inputStream ->
                    BufferedReader(InputStreamReader(inputStream, Charsets.UTF_8)).use { reader ->
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            sb.append(line).append("\n")
                        }
                    }
                }
                onImportCsv(sb.toString())
            } catch (e: Exception) {
                onShowMessage("Ошибка при чтении файла: ${e.localizedMessage}")
            }
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .verticalScroll(scrollState)
            .padding(16.dp)
            .testTag("settings_screen_root")
    ) {
        Text(
            text = "Настройки",
            style = MaterialTheme.typography.headlineSmall,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(14.dp))

        // Card 1: User Profile & Goals (Age and Target Weight with 5-position slider)
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("profile_settings_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(38.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Person,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = "Профиль и цели",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Age input field
                val parsedAge = ageInput.toIntOrNull()
                val isAgeValid = parsedAge != null && MetricValidationUtil.isAgeValid(parsedAge)
                val isAgeError = ageInput.isNotBlank() && !isAgeValid
                val isTeen = parsedAge != null && parsedAge in 14..17
                val isAdult = parsedAge != null && parsedAge >= 18

                Text(
                    text = "Возраст (поддерживается от 14 лет):",
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = ageInput,
                        onValueChange = { input ->
                            if (input.all { it.isDigit() }) {
                                val num = input.toIntOrNull()
                                if (input.isEmpty() || (num != null && num <= MetricValidationUtil.MAX_AGE)) {
                                    ageInput = input
                                }
                            }
                        },
                        isError = isAgeError,
                        supportingText = if (isAgeError) {
                            { Text("От ${MetricValidationUtil.MIN_AGE} до ${MetricValidationUtil.MAX_AGE} лет") }
                        } else null,
                        label = { Text("Возраст (лет)") },
                        placeholder = { Text("например: 16 или 28") },
                        trailingIcon = { Text("лет") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("age_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (isAgeValid) {
                                onSaveAge(parsedAge)
                            } else if (ageInput.isBlank()) {
                                onSaveAge(null)
                            } else {
                                onShowMessage("Укажите возраст от ${MetricValidationUtil.MIN_AGE} до ${MetricValidationUtil.MAX_AGE} лет")
                            }
                        },
                        enabled = isAgeValid || ageInput.isBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_age_button")
                    ) {
                        Text("ОК")
                    }
                }

                // Age validation badges
                if (ageInput.isNotBlank()) {
                    Spacer(modifier = Modifier.height(6.dp))
                    if (!isAgeValid) {
                        Text(
                            text = "Пожалуйста, введите возраст от 14 лет",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.error
                        )
                    } else if (isTeen) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🧒 Подростковый возраст (14–17 лет). Учитываются особенности роста.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSecondaryContainer,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    } else if (isAdult) {
                        Surface(
                            color = MetricGreen.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(8.dp)
                        ) {
                            Text(
                                text = "🧑 Совершеннолетний ($parsedAge лет). Стандартная шкала норм ВОЗ.",
                                style = MaterialTheme.typography.labelSmall,
                                color = MetricGreen,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                // Target Weight section with 5-position slider within normal BMI
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.Tune,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = "Желаемый вес в пределах нормы ИМТ:",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))
                Text(
                    text = "Выберите позицию на шкале нормы по вашему росту (${String.format(Locale.US, "%.0f", userHeightCm)} см):",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(10.dp))

                // 5-position Slider (0: Нижний предел, 1: Ниже середины, 2: Середина, 3: Выше середины, 4: Верхний предел)
                val currentPreset = bmiPresets[sliderStep.roundToInt().coerceIn(0, 4)]
                val rawPresetWeight = currentPreset.targetBmi * (heightM * heightM)
                val calculatedPresetWeight = (kotlin.math.floor(rawPresetWeight * 10.0) / 10.0).toFloat()

                Slider(
                    value = sliderStep,
                    onValueChange = { newStep ->
                        sliderStep = newStep
                        val idx = newStep.roundToInt().coerceIn(0, 4)
                        val preset = bmiPresets[idx]
                        val raw = preset.targetBmi * (heightM * heightM)
                        // Floor in lesser direction to 1 decimal place: e.g. 65.749729 -> 65.7
                        val floored = (kotlin.math.floor(raw * 10.0) / 10.0).toFloat()
                        val formatted = String.format(Locale.US, "%.1f", floored)
                        targetWeightInput = formatted
                        onSaveTargetWeight(floored)
                    },
                    valueRange = 0f..4f,
                    steps = 3, // 3 intermediate steps gives 5 discrete positions: 0, 1, 2, 3, 4
                    colors = SliderDefaults.colors(
                        thumbColor = MaterialTheme.colorScheme.primary,
                        activeTrackColor = MaterialTheme.colorScheme.primary,
                        inactiveTrackColor = MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.testTag("target_weight_norm_slider")
                )

                // 5 position labels row under slider
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(text = "Низ\n18.5", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "¼\n20.1", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Центр\n21.7", style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, color = MaterialTheme.colorScheme.primary)
                    Text(text = "¾\n23.3", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(text = "Верх\n24.9", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                Spacer(modifier = Modifier.height(12.dp))

                // Current selected preset card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Column {
                            Text(
                                text = currentPreset.title,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Text(
                                text = "${currentPreset.subtitle} для роста ${String.format(Locale.US, "%.0f", userHeightCm)} см",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            text = "${String.format(Locale.US, "%.1f", calculatedPresetWeight)} кг",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Exact Target Weight input field (allows fine-tuning or custom number)
                val parsedTarget = targetWeightInput.replace(",", ".").toFloatOrNull()
                val isTargetValid = MetricValidationUtil.isWeightValid(parsedTarget)
                val isTargetError = targetWeightInput.isNotBlank() && !isTargetValid

                Text(
                    text = "Или введите точный вес вручную (кг):",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = targetWeightInput,
                        onValueChange = { input ->
                            if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_WEIGHT_KG, maxDecimals = 1)) {
                                targetWeightInput = input
                            }
                        },
                        isError = isTargetError,
                        supportingText = if (isTargetError) {
                            { Text("От ${MetricValidationUtil.MIN_WEIGHT_KG.toInt()} до ${MetricValidationUtil.MAX_WEIGHT_KG.toInt()} кг") }
                        } else null,
                        label = { Text("Целевой вес") },
                        placeholder = { Text("например: 70.0") },
                        trailingIcon = { Text("кг") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        modifier = Modifier
                            .weight(1f)
                            .testTag("target_weight_input_field"),
                        shape = RoundedCornerShape(12.dp)
                    )

                    Spacer(modifier = Modifier.width(10.dp))

                    Button(
                        onClick = {
                            if (isTargetValid) {
                                onSaveTargetWeight(parsedTarget)
                            } else if (targetWeightInput.isBlank()) {
                                onSaveTargetWeight(null)
                            } else {
                                onShowMessage("Введите корректный вес (от ${MetricValidationUtil.MIN_WEIGHT_KG.toInt()} до ${MetricValidationUtil.MAX_WEIGHT_KG.toInt()} кг)")
                            }
                        },
                        enabled = isTargetValid || targetWeightInput.isBlank(),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.testTag("save_target_weight_button")
                    ) {
                        Text("ОК")
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 2: Weekly Backup Reminder Notification Switch
        Card(
            modifier = Modifier
                .fillMaxWidth()
                .testTag("notification_settings_card"),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(18.dp)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Surface(
                            color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.6f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.size(38.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (weeklyReminderEnabled) Icons.Default.NotificationsActive else Icons.Default.Notifications,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.secondary,
                                    modifier = Modifier.size(22.dp)
                                )
                            }
                        }
                        Spacer(modifier = Modifier.width(12.dp))
                        Column {
                            Text(
                                text = "Еженедельное напоминание",
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                            Text(
                                text = "Напоминать о создании резервной копии раз в неделю",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    Switch(
                        checked = weeklyReminderEnabled,
                        onCheckedChange = { isChecked ->
                            if (isChecked) {
                                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                                } else {
                                    onToggleWeeklyReminder(true)
                                }
                            } else {
                                onToggleWeeklyReminder(false)
                            }
                        },
                        colors = SwitchDefaults.colors(
                            checkedThumbColor = MaterialTheme.colorScheme.primary,
                            checkedTrackColor = MaterialTheme.colorScheme.primaryContainer
                        ),
                        modifier = Modifier.testTag("weekly_reminder_switch")
                    )
                }

                if (weeklyReminderEnabled) {
                    Spacer(modifier = Modifier.height(10.dp))
                    OutlinedButton(
                        onClick = {
                            NotificationScheduler.showReminderNotification(context)
                            onShowMessage("Тестовое уведомление отправлено!")
                        },
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.testTag("test_notification_button")
                    ) {
                        Icon(Icons.Default.AddAlert, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Проверить уведомление сейчас", style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(16.dp))

        // Card 3: Age-adaptive BMI Guide
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(22.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Default.Info,
                        contentDescription = null,
                        tint = MaterialTheme.colorScheme.secondary,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "О нормах ИМТ для вашего возраста",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                }

                Spacer(modifier = Modifier.height(8.dp))

                val explanationText = when {
                    currentAge != null && currentAge in 14..17 ->
                        "Для возраста $currentAge лет: в подростковом возрасте (14–17 лет) тело активно растёт и формирует костную и мышечную ткань. Классические взрослые пороги являются лишь ориентиром; здоровый диапазон ИМТ обычно составляет 18.0–24.0."
                    currentAge != null && currentAge >= 18 ->
                        "Для возраста $currentAge лет: применяются классические критерии Всемирной организации здравоохранения (ВОЗ). Оптимальный нормальный диапазон ИМТ: 18.5 – 24.9."
                    else ->
                        "Укажите ваш возраст выше (от 14 лет), чтобы приложение адаптировало рекомендации по индексу массы тела."
                }

                Text(
                    text = explanationText,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Spacer(modifier = Modifier.height(20.dp))

        // Card 4: Export & Backup Section (including Google Drive!)
        Text(
            text = "Резервное копирование и экспорт",
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.Bold,
            color = MaterialTheme.colorScheme.onBackground
        )

        Spacer(modifier = Modifier.height(10.dp))

        // Special Google Drive Backup Action Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.35f)),
            elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        color = MaterialTheme.colorScheme.primary,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.CloudUpload,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onPrimary,
                                modifier = Modifier.size(24.dp)
                            )
                        }
                    }
                    Spacer(modifier = Modifier.width(12.dp))
                    Column {
                        Text(
                            text = "Резервная копия на Google Диск",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                        Text(
                            text = "Сохранение файла в облако Google",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Экспортирует ваши замеры в CSV файл и запускает сохранение на Google Диск с выбором папки и аккаунта.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )

                Spacer(modifier = Modifier.height(14.dp))

                Button(
                    onClick = {
                        val backupInfo = onPrepareCloudBackupFile()
                        if (backupInfo != null) {
                            val (fileUri, fileName) = backupInfo
                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                type = "text/csv"
                                putExtra(Intent.EXTRA_STREAM, fileUri)
                                putExtra(Intent.EXTRA_SUBJECT, "BodyMetrics Резервная копия: $fileName")
                                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            }
                            // Direct Google Drive app chooser if available
                            val driveChooser = Intent.createChooser(sendIntent, "Сохранить на Google Диск")
                            context.startActivity(driveChooser)
                        } else {
                            onShowMessage("Ошибка при подготовке файла резервной копии")
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("backup_to_google_drive_button"),
                    shape = RoundedCornerShape(12.dp),
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                ) {
                    Icon(Icons.Default.CloudUpload, contentDescription = null, modifier = Modifier.size(20.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Сохранить на Google Диск", fontWeight = FontWeight.Bold)
                }
            }
        }

        Spacer(modifier = Modifier.height(12.dp))

        // Save to File (CSV via local Storage Access Framework)
        SettingsActionCard(
            title = "Сохранить в файл (CSV)",
            description = "Таблица со всеми параметрами (вес, рост, ИМТ, замеры тела), совместимая с Excel и Google Таблицами.",
            buttonText = "Сохранить CSV в файлы",
            icon = Icons.Default.FileDownload,
            buttonTag = "settings_export_csv_button",
            onClick = {
                val fileDate = SimpleDateFormat("yyyyMMdd_HHmm", Locale.US).format(Date())
                createDocumentLauncher.launch("bodymetrics_$fileDate.csv")
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Share Summary
        SettingsActionCard(
            title = "Поделиться отчётом",
            description = "Отправить текстовую сводку с замерами и динамикой в Telegram, WhatsApp или Заметки.",
            buttonText = "Поделиться сводкой",
            icon = Icons.Default.Share,
            buttonTag = "settings_share_summary_button",
            onClick = {
                val summary = onGetTextSummary()
                val sendIntent = Intent().apply {
                    action = Intent.ACTION_SEND
                    putExtra(Intent.EXTRA_TEXT, summary)
                    type = "text/plain"
                }
                val shareIntent = Intent.createChooser(sendIntent, "Поделиться отчётом параметров")
                context.startActivity(shareIntent)
            }
        )

        Spacer(modifier = Modifier.height(12.dp))

        // Import CSV
        SettingsActionCard(
            title = "Импорт из файла CSV",
            description = "Восстановить замеры из ранее выгруженного CSV файла.",
            buttonText = "Выбрать CSV файл для импорта",
            icon = Icons.Default.FileUpload,
            buttonTag = "settings_import_csv_button",
            onClick = {
                openDocumentLauncher.launch(arrayOf("text/csv", "text/comma-separated-values", "text/plain", "*/*"))
            }
        )

        Spacer(modifier = Modifier.height(20.dp))

        // Database Management Card
        Card(
            modifier = Modifier.fillMaxWidth(),
            shape = RoundedCornerShape(20.dp),
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
        ) {
            Column(modifier = Modifier.padding(16.dp)) {
                Text(
                    text = "База замеров: $entryCount записей",
                    style = MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
                Spacer(modifier = Modifier.height(12.dp))

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    OutlinedButton(
                        onClick = onGenerateDemo,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_load_demo_button"),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Icon(Icons.Default.PlayCircleOutline, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Пример данных", style = MaterialTheme.typography.labelMedium)
                    }

                    OutlinedButton(
                        onClick = { showClearConfirmDialog = true },
                        modifier = Modifier
                            .weight(1f)
                            .testTag("settings_clear_all_button"),
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = MaterialTheme.colorScheme.error
                        )
                    ) {
                        Icon(Icons.Default.DeleteSweep, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Очистить всё", style = MaterialTheme.typography.labelMedium)
                    }
                }
            }
        }

        Spacer(modifier = Modifier.height(80.dp))
    }

    if (showClearConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showClearConfirmDialog = false },
            title = { Text("Очистить все замеры?") },
            text = { Text("Все сохранённые записи веса, роста и размеров будут удалены без возможности восстановления.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        onClearAll()
                        showClearConfirmDialog = false
                    },
                    modifier = Modifier.testTag("confirm_clear_all_settings_button")
                ) {
                    Text("Удалить все", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showClearConfirmDialog = false }) {
                    Text("Отмена")
                }
            }
        )
    }
}

@Composable
fun SettingsActionCard(
    title: String,
    description: String,
    buttonText: String,
    icon: ImageVector,
    buttonTag: String,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
    ) {
        Column(modifier = Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.size(36.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = icon,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.width(10.dp))
                Text(
                    text = title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onSurface
                )
            }

            Spacer(modifier = Modifier.height(6.dp))

            Text(
                text = description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )

            Spacer(modifier = Modifier.height(12.dp))

            Button(
                onClick = onClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag(buttonTag),
                shape = RoundedCornerShape(12.dp)
            ) {
                Icon(icon, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Text(buttonText)
            }
        }
    }
}
