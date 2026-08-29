package com.aurora.r.ui

import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.unit.dp
import com.aurora.r.VpnState

/**
 * دکمه‌ی بزرگ اتصال با حلقه‌ی درخشان.
 * وقتی در حال اتصال است، حلقه می‌چرخد؛ وقتی متصل است طلایی روشن؛ قطع = خاکستری.
 */
@Composable
fun ConnectButton(
    state: VpnState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    val transition = rememberInfiniteTransition(label = "ring")
    val angle by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart
        ),
        label = "angle"
    )
    val pulse by transition.animateFloat(
        initialValue = 0.85f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    val ringColor = when (state) {
        VpnState.CONNECTED -> AuroraGoldBright
        VpnState.CONNECTING, VpnState.STOPPING -> AuroraGold
        VpnState.ERROR -> AuroraRed
        else -> AuroraTextDim
    }

    Box(
        modifier = modifier.size(230.dp).clickable { onClick() },
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.size(230.dp)) {
            val stroke = 10.dp.toPx()
            val radius = (size.minDimension - stroke) / 2f
            val center = Offset(size.width / 2f, size.height / 2f)

            // حلقه‌ی پس‌زمینه
            drawCircle(
                color = AuroraSurface2,
                radius = radius,
                center = center,
                style = Stroke(width = stroke)
            )

            // قوس درخشان
            val sweep = when (state) {
                VpnState.CONNECTED -> 360f
                VpnState.CONNECTING, VpnState.STOPPING -> 90f
                else -> 220f
            }
            val brush = Brush.sweepGradient(
                colors = listOf(ringColor.copy(alpha = 0.1f), ringColor, ringColor.copy(alpha = 0.1f)),
                center = center
            )
            rotate(if (state == VpnState.CONNECTING) angle else -90f) {
                drawArc(
                    brush = brush,
                    startAngle = 0f,
                    sweepAngle = sweep,
                    useCenter = false,
                    topLeft = Offset(center.x - radius, center.y - radius),
                    size = androidx.compose.ui.geometry.Size(radius * 2, radius * 2),
                    style = Stroke(width = stroke, cap = androidx.compose.ui.graphics.StrokeCap.Round)
                )
            }

            // هاله‌ی داخلی وقتی متصل
            if (state == VpnState.CONNECTED) {
                drawCircle(
                    brush = Brush.radialGradient(
                        colors = listOf(AuroraGold.copy(alpha = 0.18f * pulse), Color.Transparent),
                        center = center,
                        radius = radius
                    ),
                    radius = radius,
                    center = center
                )
            }
        }

        // آیکون و متن مرکزی
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = Icons.Filled.PowerSettingsNew,
                contentDescription = null,
                tint = ringColor,
                modifier = Modifier.size(56.dp)
            )
            Text(
                text = when (state) {
                    VpnState.CONNECTED -> "متصل"
                    VpnState.CONNECTING -> "در حال اتصال"
                    VpnState.STOPPING -> "در حال قطع"
                    VpnState.ERROR -> "خطا"
                    else -> "قطع"
                },
                color = ringColor,
                modifier = Modifier.padding(top = 8.dp)
            )
        }
    }
}
