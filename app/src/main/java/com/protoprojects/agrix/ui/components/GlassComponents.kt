package com.protoprojects.agrix.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.blur
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.protoprojects.agrix.ui.theme.AgrixGlassBorder
import com.protoprojects.agrix.ui.theme.AgrixGlassBorderStrong
import com.protoprojects.agrix.ui.theme.AgrixMutedSilver
import com.protoprojects.agrix.ui.theme.AgrixNeonGreen
import com.protoprojects.agrix.ui.theme.AgrixSurfaceGrey
import com.protoprojects.agrix.ui.theme.AgrixSurfaceGreyElevated

/**
 * The building blocks of the "premium dark glass" look: modular cards with a
 * translucent surface + thin light border standing in for glassmorphism
 * (true backdrop blur needs Android 12's RenderEffect API, which would
 * exclude this app's minSdk 26 target — the translucent-surface-plus-border
 * approach reads as "glass" without that dependency), a neon-green primary
 * button with a soft glow, and small shared pieces (micro-copy labels,
 * confidence bars) reused across every feature screen.
 */

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(20.dp),
    padding: Dp = 20.dp,
    content: @Composable BoxScope.() -> Unit
) {
    Box(
        modifier = modifier
            .clip(shape)
            .background(AgrixSurfaceGrey.copy(alpha = 0.7f), shape)
            .border(1.dp, AgrixGlassBorder, shape)
            .padding(padding),
        content = content
    )
}

@Composable
fun GlassIconButton(
    icon: ImageVector,
    contentDescription: String?,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    tint: Color = Color.White
) {
    Box(
        modifier = modifier
            .size(44.dp)
            .clip(CircleShape)
            .background(AgrixSurfaceGrey.copy(alpha = 0.8f), CircleShape)
            .border(1.dp, AgrixGlassBorder, CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center
    ) {
        Icon(icon, contentDescription = contentDescription, tint = tint, modifier = Modifier.size(20.dp))
    }
}

/**
 * The primary CTA style: solid neon green, black uppercase label, and a
 * soft glow behind it (a blurred green box sitting under the button —
 * Modifier.blur degrades to "no blur" gracefully below API 31 rather than
 * crashing, so the button still looks correct, just without the glow, on
 * older devices).
 */
@Composable
fun NeonPrimaryButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    icon: (@Composable () -> Unit)? = null
) {
    val shape = RoundedCornerShape(16.dp)
    Box(modifier = modifier, contentAlignment = Alignment.Center) {
        if (enabled) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .blur(24.dp)
                    .background(AgrixNeonGreen.copy(alpha = 0.35f), shape)
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(52.dp)
                .clip(shape)
                .background(if (enabled) AgrixNeonGreen else AgrixSurfaceGreyElevated, shape)
                .then(if (enabled) Modifier.clickable(onClick = onClick) else Modifier),
            contentAlignment = Alignment.Center
        ) {
            androidx.compose.foundation.layout.Row(verticalAlignment = Alignment.CenterVertically) {
                icon?.invoke()
                Text(
                    text.uppercase(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (enabled) Color.Black else AgrixMutedSilver,
                    fontWeight = FontWeight.Bold
                )
            }
        }
    }
}

/** Small uppercase, wide-tracked status/section label — e.g. "SYSTEM STATUS: ONLINE". */
@Composable
fun MicroLabel(text: String, modifier: Modifier = Modifier, color: Color = AgrixMutedSilver) {
    Text(text.uppercase(), style = MaterialTheme.typography.labelMedium, color = color, modifier = modifier)
}

/** Horizontal confidence meter used under AI results — neon green fill on a dim track. */
@Composable
fun ConfidenceBar(confidence: Float, modifier: Modifier = Modifier) {
    val clamped = confidence.coerceIn(0f, 1f)
    Box(
        modifier
            .fillMaxWidth()
            .height(6.dp)
            .clip(RoundedCornerShape(3.dp))
            .background(AgrixGlassBorderStrong)
    ) {
        Box(
            Modifier
                .fillMaxWidth(clamped)
                .height(6.dp)
                .clip(RoundedCornerShape(3.dp))
                .background(AgrixNeonGreen)
        )
    }
}
