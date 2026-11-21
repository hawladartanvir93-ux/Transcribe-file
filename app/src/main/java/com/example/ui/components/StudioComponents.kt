package com.example.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import kotlin.math.sin

@Composable
fun PrivacyShieldBadge(
    text: String = "100% On-Device • Zero Cloud",
    modifier: Modifier = Modifier,
) {
    Surface(
        modifier = modifier,
        shape = RoundedCornerShape(50),
        color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
        tonalElevation = 1.dp,
    ) {
        Row(
            modifier = Modifier
                .border(
                    width = 1.dp,
                    color = MaterialTheme.colorScheme.primary.copy(alpha = 0.35f),
                    shape = RoundedCornerShape(50),
                )
                .padding(horizontal = 12.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            Icon(
                imageVector = Icons.Filled.Lock,
                contentDescription = "On-device privacy lock",
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = text,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
        }
    }
}

@Composable
fun WaveformVisualizer(
    bars: List<Float>,
    progress: Float,
    isActiveProcessing: Boolean = false,
    onSeekFraction: ((Float) -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val activeColor by animateColorAsState(
        targetValue = if (isActiveProcessing) {
            MaterialTheme.colorScheme.secondary
        } else {
            MaterialTheme.colorScheme.primary
        },
        label = "waveActiveColor",
    )
    val inactiveColor = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.28f)
    val displayBars = if (bars.isNotEmpty()) {
        bars
    } else {
        List(48) { idx -> (0.22f + 0.55f * kotlin.math.abs(sin(idx * 0.37f))).coerceIn(0.15f, 0.9f) }
    }

    Canvas(
        modifier = modifier
            .fillMaxWidth()
            .height(68.dp)
            .clip(RoundedCornerShape(12.dp))
            .background(
                Brush.verticalGradient(
                    colors = listOf(
                        MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.35f),
                        MaterialTheme.colorScheme.surface.copy(alpha = 0.6f),
                    )
                )
            )
            .then(
                if (onSeekFraction != null) {
                    Modifier.pointerInput(Unit) {
                        detectTapGestures { offset ->
                            val frac = (offset.x / size.width.toFloat()).coerceIn(0f, 1f)
                            onSeekFraction(frac)
                        }
                    }
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 10.dp, vertical = 10.dp),
    ) {
        val count = displayBars.size.coerceAtLeast(1)
        val totalW = size.width
        val totalH = size.height
        val slotW = totalW / count
        val barW = (slotW * 0.58f).coerceAtLeast(2.dp.toPx())
        val corner = CornerRadius(barW / 2f, barW / 2f)

        displayBars.forEachIndexed { idx, amp ->
            val fraction = idx.toFloat() / count.toFloat()
            val barH = (totalH * amp.coerceIn(0.10f, 1f)).coerceAtLeast(4.dp.toPx())
            val x = idx * slotW + (slotW - barW) / 2f
            val y = (totalH - barH) / 2f
            val color = if (fraction <= progress) activeColor else inactiveColor
            drawRoundRect(
                color = color,
                topLeft = Offset(x, y),
                size = Size(barW, barH),
                cornerRadius = corner,
            )
        }
    }
}

@Composable
fun SpecBadge(
    label: String,
    accentColor: Color = MaterialTheme.colorScheme.tertiary,
    modifier: Modifier = Modifier,
) {
    Box(
        modifier = modifier
            .clip(RoundedCornerShape(8.dp))
            .background(accentColor.copy(alpha = 0.14f))
            .border(1.dp, accentColor.copy(alpha = 0.30f), RoundedCornerShape(8.dp))
            .padding(horizontal = 8.dp, vertical = 4.dp),
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = accentColor,
        )
    }
}

@Composable
fun StatusDotLabel(
    label: String,
    isOnlineReady: Boolean,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .size(8.dp)
                .clip(CircleShape)
                .background(
                    if (isOnlineReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.secondary
                ),
        )
        Spacer(modifier = Modifier.width(6.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
