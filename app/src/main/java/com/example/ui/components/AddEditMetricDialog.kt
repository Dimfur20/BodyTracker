package com.example.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material.icons.filled.Straighten
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.data.BmiCategory
import com.example.data.BodyMetricEntry
import com.example.util.MetricValidationUtil
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

@Composable
fun AddEditMetricDialog(
    initialEntry: BodyMetricEntry? = null,
    lastHeightDefault: Float = 175f,
    lastWeightDefault: Float = 70f,
    onDismiss: () -> Unit,
    onSave: (BodyMetricEntry) -> Unit
) {
    val context = LocalContext.current
    val scrollState = rememberScrollState()

    var dateMillis by remember {
        mutableLongStateOf(initialEntry?.dateMillis ?: System.currentTimeMillis())
    }
    var showCalendarDialog by remember { mutableStateOf(false) }
    var weightText by remember {
        mutableStateOf(
            initialEntry?.weightKg?.let { String.format(Locale.US, "%.1f", it) }
                ?: String.format(Locale.US, "%.1f", lastWeightDefault)
        )
    }
    var heightText by remember {
        mutableStateOf(
            initialEntry?.heightCm?.let { String.format(Locale.US, "%.0f", it) }
                ?: String.format(Locale.US, "%.0f", lastHeightDefault)
        )
    }

    var chestText by remember {
        mutableStateOf(initialEntry?.chestCm?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var waistText by remember {
        mutableStateOf(initialEntry?.waistCm?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var hipsText by remember {
        mutableStateOf(initialEntry?.hipsCm?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var bicepsText by remember {
        mutableStateOf(initialEntry?.bicepsCm?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var thighText by remember {
        mutableStateOf(initialEntry?.thighCm?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var neckText by remember {
        mutableStateOf(initialEntry?.neckCm?.let { String.format(Locale.US, "%.1f", it) } ?: "")
    }
    var notesText by remember {
        mutableStateOf(initialEntry?.notes ?: "")
    }

    var showMeasurementsSection by remember {
        mutableStateOf(
            initialEntry != null && (
                initialEntry.chestCm != null || initialEntry.waistCm != null ||
                initialEntry.hipsCm != null || initialEntry.bicepsCm != null ||
                initialEntry.thighCm != null || initialEntry.neckCm != null
            )
        )
    }

    val dateFormat = remember { SimpleDateFormat("dd MMMM yyyy", Locale("ru")) }

    // Parsed metric values and validation
    val parsedWeight = weightText.replace(",", ".").toFloatOrNull()
    val parsedHeight = heightText.replace(",", ".").toFloatOrNull()
    val parsedWaist = waistText.replace(",", ".").toFloatOrNull()
    val parsedChest = chestText.replace(",", ".").toFloatOrNull()
    val parsedHips = hipsText.replace(",", ".").toFloatOrNull()
    val parsedBiceps = bicepsText.replace(",", ".").toFloatOrNull()
    val parsedThigh = thighText.replace(",", ".").toFloatOrNull()
    val parsedNeck = neckText.replace(",", ".").toFloatOrNull()

    val isWeightValid = MetricValidationUtil.isWeightValid(parsedWeight)
    val isHeightValid = MetricValidationUtil.isHeightValid(parsedHeight)
    val isWaistValid = MetricValidationUtil.isMeasurementValid(parsedWaist)
    val isChestValid = MetricValidationUtil.isMeasurementValid(parsedChest)
    val isHipsValid = MetricValidationUtil.isMeasurementValid(parsedHips)
    val isBicepsValid = MetricValidationUtil.isMeasurementValid(parsedBiceps)
    val isThighValid = MetricValidationUtil.isMeasurementValid(parsedThigh)
    val isNeckValid = MetricValidationUtil.isMeasurementValid(parsedNeck)

    val isWeightError = weightText.isNotBlank() && !isWeightValid
    val isHeightError = heightText.isNotBlank() && !isHeightValid
    val isWaistError = waistText.isNotBlank() && !isWaistValid
    val isChestError = chestText.isNotBlank() && !isChestValid
    val isHipsError = hipsText.isNotBlank() && !isHipsValid
    val isBicepsError = bicepsText.isNotBlank() && !isBicepsValid
    val isThighError = thighText.isNotBlank() && !isThighValid
    val isNeckError = neckText.isNotBlank() && !isNeckValid

    val isFormValid = isWeightValid && isHeightValid &&
            isWaistValid && isChestValid && isHipsValid &&
            isBicepsValid && isThighValid && isNeckValid

    // Live BMI calculation
    val liveBmi = if (isHeightValid && isWeightValid && parsedHeight != null && parsedWeight != null) {
        val hM = parsedHeight / 100f
        parsedWeight / (hM * hM)
    } else 0f
    val bmiCategory = BmiCategory.fromBmi(liveBmi)

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            modifier = Modifier
                .fillMaxWidth(0.94f)
                .testTag("add_edit_metric_dialog"),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surface,
            tonalElevation = 6.dp
        ) {
            Column(
                modifier = Modifier
                    .padding(20.dp)
                    .verticalScroll(scrollState)
            ) {
                // Header
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = if (initialEntry == null) "Новый замер" else "Редактировать замер",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onSurface
                    )
                    IconButton(onClick = onDismiss, modifier = Modifier.size(36.dp)) {
                        Icon(
                            imageVector = Icons.Default.Close,
                            contentDescription = "Закрыть",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Date Picker row
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showCalendarDialog = true }
                        .testTag("date_picker_button"),
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.CalendarMonth,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = dateFormat.format(Date(dateMillis)),
                                style = MaterialTheme.typography.bodyLarge,
                                fontWeight = FontWeight.Medium,
                                color = MaterialTheme.colorScheme.onSurface
                            )
                        }
                        Text(
                            text = "Изменить",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Weight input with quick stepper buttons
                Text(
                    text = "Вес тела (кг)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = {
                            val current = weightText.replace(",", ".").toFloatOrNull() ?: 70f
                            weightText = String.format(Locale.US, "%.1f", (current - 0.1f).coerceAtLeast(MetricValidationUtil.MIN_WEIGHT_KG))
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("weight_step_minus")
                    ) {
                        Icon(Icons.Default.Remove, contentDescription = "Минус 0.1 кг")
                    }

                    Spacer(modifier = Modifier.width(8.dp))

                    OutlinedTextField(
                        value = weightText,
                        onValueChange = { input ->
                            if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_WEIGHT_KG, maxDecimals = 1)) {
                                weightText = input
                            }
                        },
                        isError = isWeightError,
                        supportingText = if (isWeightError) {
                            { Text("От ${MetricValidationUtil.MIN_WEIGHT_KG.toInt()} до ${MetricValidationUtil.MAX_WEIGHT_KG.toInt()} кг") }
                        } else null,
                        modifier = Modifier
                            .weight(1f)
                            .testTag("weight_input_field"),
                        keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                        singleLine = true,
                        shape = RoundedCornerShape(14.dp),
                        trailingIcon = { Text("кг", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    IconButton(
                        onClick = {
                            val current = weightText.replace(",", ".").toFloatOrNull() ?: 70f
                            weightText = String.format(Locale.US, "%.1f", (current + 0.1f).coerceAtMost(MetricValidationUtil.MAX_WEIGHT_KG))
                        },
                        modifier = Modifier
                            .size(44.dp)
                            .background(MaterialTheme.colorScheme.surfaceVariant, CircleShape)
                            .testTag("weight_step_plus")
                    ) {
                        Icon(Icons.Default.Add, contentDescription = "Плюс 0.1 кг")
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Height input
                Text(
                    text = "Рост (см)",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(modifier = Modifier.height(6.dp))
                OutlinedTextField(
                    value = heightText,
                    onValueChange = { input ->
                        if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_HEIGHT_CM, maxDecimals = 1)) {
                            heightText = input
                        }
                    },
                    isError = isHeightError,
                    supportingText = if (isHeightError) {
                        { Text("От ${MetricValidationUtil.MIN_HEIGHT_CM.toInt()} до ${MetricValidationUtil.MAX_HEIGHT_CM.toInt()} см") }
                    } else null,
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("height_input_field"),
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    shape = RoundedCornerShape(14.dp),
                    trailingIcon = { Text("см", color = MaterialTheme.colorScheme.onSurfaceVariant) }
                )

                // Live BMI banner
                if (liveBmi > 0f) {
                    Spacer(modifier = Modifier.height(14.dp))
                    Surface(
                        color = Color(bmiCategory.colorHex).copy(alpha = 0.15f),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column {
                                Text(
                                    text = "Индекс массы тела (ИМТ)",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                Text(
                                    text = String.format(Locale.US, "%.1f", liveBmi),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = MaterialTheme.colorScheme.onSurface
                                )
                            }
                            Surface(
                                color = Color(bmiCategory.colorHex),
                                shape = RoundedCornerShape(8.dp)
                            ) {
                                Text(
                                    text = bmiCategory.titleRu,
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.SemiBold,
                                    color = Color.White,
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp)
                                )
                            }
                        }
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                // Expandable Body Measurements toggle
                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(14.dp))
                        .clickable { showMeasurementsSection = !showMeasurementsSection }
                        .testTag("toggle_measurements_button"),
                    color = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.4f),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Default.Straighten,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.secondary,
                                modifier = Modifier.size(20.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Размеры и объёмы тела (см)",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onSecondaryContainer
                            )
                        }
                        Icon(
                            imageVector = if (showMeasurementsSection) Icons.Default.ExpandLess else Icons.Default.ExpandMore,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                }

                AnimatedVisibility(visible = showMeasurementsSection) {
                    Column(modifier = Modifier.padding(top = 12.dp)) {
                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = waistText,
                                onValueChange = { input ->
                                    if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_MEASUREMENT_CM, maxDecimals = 1)) {
                                        waistText = input
                                    }
                                },
                                isError = isWaistError,
                                supportingText = if (isWaistError) {
                                    { Text("10–250 см") }
                                } else null,
                                label = { Text("Талия") },
                                trailingIcon = { Text("см") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("waist_input_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = chestText,
                                onValueChange = { input ->
                                    if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_MEASUREMENT_CM, maxDecimals = 1)) {
                                        chestText = input
                                    }
                                },
                                isError = isChestError,
                                supportingText = if (isChestError) {
                                    { Text("10–250 см") }
                                } else null,
                                label = { Text("Грудь") },
                                trailingIcon = { Text("см") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("chest_input_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = hipsText,
                                onValueChange = { input ->
                                    if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_MEASUREMENT_CM, maxDecimals = 1)) {
                                        hipsText = input
                                    }
                                },
                                isError = isHipsError,
                                supportingText = if (isHipsError) {
                                    { Text("10–250 см") }
                                } else null,
                                label = { Text("Бёдра") },
                                trailingIcon = { Text("см") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("hips_input_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = bicepsText,
                                onValueChange = { input ->
                                    if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_MEASUREMENT_CM, maxDecimals = 1)) {
                                        bicepsText = input
                                    }
                                },
                                isError = isBicepsError,
                                supportingText = if (isBicepsError) {
                                    { Text("10–250 см") }
                                } else null,
                                label = { Text("Бицепс") },
                                trailingIcon = { Text("см") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("biceps_input_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }

                        Spacer(modifier = Modifier.height(10.dp))

                        Row(modifier = Modifier.fillMaxWidth()) {
                            OutlinedTextField(
                                value = thighText,
                                onValueChange = { input ->
                                    if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_MEASUREMENT_CM, maxDecimals = 1)) {
                                        thighText = input
                                    }
                                },
                                isError = isThighError,
                                supportingText = if (isThighError) {
                                    { Text("10–250 см") }
                                } else null,
                                label = { Text("Бедро") },
                                trailingIcon = { Text("см") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("thigh_input_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            OutlinedTextField(
                                value = neckText,
                                onValueChange = { input ->
                                    if (MetricValidationUtil.isValidDecimalInput(input, maxValue = MetricValidationUtil.MAX_MEASUREMENT_CM, maxDecimals = 1)) {
                                        neckText = input
                                    }
                                },
                                isError = isNeckError,
                                supportingText = if (isNeckError) {
                                    { Text("10–250 см") }
                                } else null,
                                label = { Text("Шея") },
                                trailingIcon = { Text("см") },
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                singleLine = true,
                                modifier = Modifier
                                    .weight(1f)
                                    .testTag("neck_input_field"),
                                shape = RoundedCornerShape(12.dp)
                            )
                        }
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Notes input
                OutlinedTextField(
                    value = notesText,
                    onValueChange = { input ->
                        if (input.length <= MetricValidationUtil.MAX_NOTES_LENGTH) {
                            notesText = input
                        }
                    },
                    label = { Text("Заметка (по желанию)") },
                    placeholder = { Text("например: натощак, после бега") },
                    modifier = Modifier
                        .fillMaxWidth()
                        .testTag("notes_input_field"),
                    shape = RoundedCornerShape(14.dp),
                    maxLines = 2
                )

                Spacer(modifier = Modifier.height(20.dp))

                // Actions
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    OutlinedButton(
                        onClick = onDismiss,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("cancel_button")
                    ) {
                        Text("Отмена")
                    }
                    Spacer(modifier = Modifier.width(10.dp))
                    Button(
                        onClick = {
                            if (isFormValid && parsedWeight != null && parsedHeight != null) {
                                val entry = BodyMetricEntry(
                                    id = initialEntry?.id ?: 0L,
                                    dateMillis = dateMillis,
                                    weightKg = parsedWeight,
                                    heightCm = parsedHeight,
                                    chestCm = parsedChest,
                                    waistCm = parsedWaist,
                                    hipsCm = parsedHips,
                                    bicepsCm = parsedBiceps,
                                    thighCm = parsedThigh,
                                    neckCm = parsedNeck,
                                    notes = notesText.trim()
                                )
                                onSave(entry)
                            }
                        },
                        enabled = isFormValid,
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.testTag("save_metric_button")
                    ) {
                        Text(if (initialEntry == null) "Сохранить" else "Обновить")
                    }
                }
            }
        }
    }

    if (showCalendarDialog) {
        AppCalendarDialog(
            mode = CalendarSelectionMode.SINGLE,
            initialDateMillis = dateMillis,
            title = "Дата замера",
            onDismiss = { showCalendarDialog = false },
            onDateSelected = { pickedMillis ->
                dateMillis = pickedMillis
            }
        )
    }
}
