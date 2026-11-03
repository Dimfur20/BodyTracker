package com.example.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.unit.dp
import com.example.data.BmiCategory

/**
 * 4-segment colored BMI gauge bar with a prominent, larger pointer marker showing the user's current BMI.
 * Gauge range is calibrated from BMI 14 to 38.
 */
@Composable
fun BmiPointerBar(
    bmi: Float,
    modifier: Modifier = Modifier
) {
    val minBmi = 14f
    val maxBmi = 38f
    val clampedBmi = bmi.coerceIn(minBmi, maxBmi)
    val fraction = if (maxBmi > minBmi) (clampedBmi - minBmi) / (maxBmi - minBmi) else 0.5f

    Column(modifier = modifier.fillMaxWidth()) {
        // Larger, prominent pointer marker above the bar indicating current BMI
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(26.dp)
        ) {
            Canvas(modifier = Modifier.fillMaxSize()) {
                val pointerX = (size.width * fraction).coerceIn(12f, size.width - 12f)
                val pointerY = size.height

                // Draw outer subtle shadow / ring
                drawCircle(
                    color = Color(0x33000000),
                    radius = 9f,
                    center = Offset(pointerX, pointerY - 14f)
                )

                // Draw prominent downward arrow pointer
                val path = Path().apply {
                    moveTo(pointerX, pointerY)
                    lineTo(pointerX - 12f, pointerY - 20f)
                    lineTo(pointerX + 12f, pointerY - 20f)
                    close()
                }
                drawPath(path = path, color = Color(0xFF0F172A))

                // Solid pin head circle
                drawCircle(
                    color = Color(0xFF0F172A),
                    radius = 8f,
                    center = Offset(pointerX, pointerY - 14f)
                )

                // Inner bright accent dot
                drawCircle(
                    color = Color.White,
                    radius = 4f,
                    center = Offset(pointerX, pointerY - 14f)
                )
            }
        }

        // 4-segment colored horizontal gauge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .height(12.dp)
                .clip(RoundedCornerShape(6.dp))
        ) {
            // Underweight: 14 to 18.5 (4.5 range)
            Box(
                modifier = Modifier
                    .weight(4.5f)
                    .fillMaxSize()
                    .background(Color(BmiCategory.UNDERWEIGHT.colorHex))
            )
            Spacer(modifier = Modifier.width(2.5.dp))
            // Normal: 18.5 to 25.0 (6.5 range)
            Box(
                modifier = Modifier
                    .weight(6.5f)
                    .fillMaxSize()
                    .background(Color(BmiCategory.NORMAL.colorHex))
            )
            Spacer(modifier = Modifier.width(2.5.dp))
            // Overweight: 25.0 to 30.0 (5.0 range)
            Box(
                modifier = Modifier
                    .weight(5.0f)
                    .fillMaxSize()
                    .background(Color(BmiCategory.OVERWEIGHT.colorHex))
            )
            Spacer(modifier = Modifier.width(2.5.dp))
            // Obese: 30.0 to 38.0 (8.0 range)
            Box(
                modifier = Modifier
                    .weight(8.0f)
                    .fillMaxSize()
                    .background(Color(BmiCategory.OBESE.colorHex))
            )
        }

        Spacer(modifier = Modifier.height(6.dp))

        // Range labels under the bar
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 2.dp),
            horizontalArrangement = androidx.compose.foundation.layout.Arrangement.SpaceBetween
        ) {
            Text(text = "<18.5\nДефицит", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "18.5–24.9\nНорма", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "25.0–29.9\nИзбыток", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Text(text = "≥30.0\nОжирение", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
    }
}
