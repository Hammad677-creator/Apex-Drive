package com.example.ui

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.physics.VehicleTelemetry
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingAmber
import com.example.ui.theme.RacingRed
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Modern diegetic circular cluster placed strictly in the corner of the screen.
 * Displays Speedometer, Tachometer with responsive RPM bounce, Gear Indicator,
 * and ABS / ESC status indicators.
 */
@Composable
fun CircularSpeedoCluster(
    telemetry: VehicleTelemetry,
    useMph: Boolean,
    onToggleUnit: () -> Unit,
    modifier: Modifier = Modifier
) {
    // Smooth animated RPM needle with spring bounce during gear shifts
    val animatedRpm by animateFloatAsState(
        targetValue = telemetry.rpm,
        animationSpec = spring(dampingRatio = 0.65f, stiffness = 500f),
        label = "rpm_anim"
    )

    val speedDisplay = if (useMph) {
        telemetry.speedMph.toInt()
    } else {
        telemetry.speedKmh.toInt()
    }

    val unitLabel = if (useMph) "MPH" else "KM/H"

    // Amber pulse when ABS/ESC are actively intervening
    val absColor by animateColorAsState(
        targetValue = if (telemetry.isAbsActive) RacingAmber else Color(0x33FFB300),
        label = "abs_color"
    )
    val escColor by animateColorAsState(
        targetValue = if (telemetry.isEscActive) RacingAmber else Color(0x33FFB300),
        label = "esc_color"
    )

    Box(
        modifier = modifier
            .size(190.dp)
            .background(
                brush = Brush.radialGradient(
                    colors = listOf(
                        Color(0xD910141D),
                        Color(0xF0080A0E)
                    )
                ),
                shape = CircleShape
            )
            .padding(8.dp)
            .testTag("circular_speedo_cluster"),
        contentAlignment = Alignment.Center
    ) {
        // 1. Canvas Tachometer Dial & Needle
        Canvas(modifier = Modifier.size(174.dp)) {
            val center = Offset(size.width / 2f, size.height / 2f)
            val strokeWidth = 7.dp.toPx()
            val arcRadius = (size.width - strokeWidth * 2f) / 2f

            // Tachometer arc angles: Start at 135 degrees (bottom-left) to 45 degrees (bottom-right), spanning 270 deg
            val startAngle = 135f
            val totalSweep = 270f

            // A. Background track
            drawArc(
                color = Color(0x2BFFFFFF),
                startAngle = startAngle,
                sweepAngle = totalSweep,
                useCenter = false,
                topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                size = Size(arcRadius * 2f, arcRadius * 2f),
                style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
            )

            // B. Active RPM Arc (Normal: Neon Cyan, Redline > 6800 RPM: Racing Red)
            val rpmFraction = (animatedRpm / 8000f).coerceIn(0f, 1f)
            val activeSweep = totalSweep * rpmFraction

            val normalSweep = if (rpmFraction > 0.85f) totalSweep * 0.85f else activeSweep
            val redlineSweep = if (rpmFraction > 0.85f) activeSweep - normalSweep else 0f

            // Normal powerband sweep
            if (normalSweep > 0f) {
                drawArc(
                    brush = Brush.sweepGradient(
                        colors = listOf(NeonCyan, Color(0xFF64FFDA), NeonCyan),
                        center = center
                    ),
                    startAngle = startAngle,
                    sweepAngle = normalSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                )
            }

            // Redline sweep
            if (redlineSweep > 0f) {
                drawArc(
                    color = RacingRed,
                    startAngle = startAngle + normalSweep,
                    sweepAngle = redlineSweep,
                    useCenter = false,
                    topLeft = Offset(center.x - arcRadius, center.y - arcRadius),
                    size = Size(arcRadius * 2f, arcRadius * 2f),
                    style = Stroke(width = strokeWidth + 1.dp.toPx(), cap = StrokeCap.Round)
                )
            }

            // C. Tick marks around the dial
            val numTicks = 9
            for (i in 0 until numTicks) {
                val tickAngle = (startAngle + (i.toFloat() / (numTicks - 1)) * totalSweep) * (PI.toFloat() / 180f)
                val isRedline = i >= 7
                val tickColor = if (isRedline) RacingRed else Color(0x88FFFFFF)
                val innerR = arcRadius - (if (i % 2 == 0) 10.dp.toPx() else 6.dp.toPx())
                val outerR = arcRadius - 2.dp.toPx()

                val p1 = Offset(center.x + cos(tickAngle) * innerR, center.y + sin(tickAngle) * innerR)
                val p2 = Offset(center.x + cos(tickAngle) * outerR, center.y + sin(tickAngle) * outerR)
                drawLine(
                    color = tickColor,
                    start = p1,
                    end = p2,
                    strokeWidth = 2.dp.toPx()
                )
            }

            // D. Bouncing Needle / Leading indicator dot
            val needleAngle = (startAngle + activeSweep) * (PI.toFloat() / 180f)
            val needleTip = Offset(center.x + cos(needleAngle) * arcRadius, center.y + sin(needleAngle) * arcRadius)
            drawCircle(
                color = if (animatedRpm > 6800f) RacingRed else Color.White,
                radius = 5.dp.toPx(),
                center = needleTip
            )
        }

        // 2. Digital Cluster Inner Readouts
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center,
            modifier = Modifier.padding(bottom = 4.dp)
        ) {
            // A. Prominent Active Gear Indicator Badge
            Box(
                modifier = Modifier
                    .background(
                        color = when (telemetry.gearLabel) {
                            "R" -> RacingRed.copy(alpha = 0.35f)
                            "N" -> RacingAmber.copy(alpha = 0.35f)
                            else -> NeonCyan.copy(alpha = 0.22f)
                        },
                        shape = RoundedCornerShape(6.dp)
                    )
                    .padding(horizontal = 9.dp, vertical = 2.dp)
                    .testTag("gear_indicator_badge"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = telemetry.gearLabel,
                    color = when (telemetry.gearLabel) {
                        "R" -> RacingRed
                        "N" -> RacingAmber
                        else -> Color.White
                    },
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Black,
                    fontFamily = FontFamily.Monospace
                )
            }

            Spacer(modifier = Modifier.height(2.dp))

            // B. Digital Speedometer
            Text(
                text = "$speedDisplay",
                color = Color.White,
                fontSize = 38.sp,
                fontWeight = FontWeight.ExtraBold,
                fontFamily = FontFamily.SansSerif,
                letterSpacing = (-1).sp,
                modifier = Modifier.testTag("speedometer_readout")
            )

            // C. Speed Unit (KM/H or MPH) - Clickable to toggle
            Box(
                modifier = Modifier
                    .clickable { onToggleUnit() }
                    .padding(horizontal = 4.dp, vertical = 1.dp)
                    .testTag("unit_toggle_button")
            ) {
                Text(
                    text = unitLabel,
                    color = NeonCyan,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Bold,
                    letterSpacing = 1.sp
                )
            }

            Spacer(modifier = Modifier.height(4.dp))

            // D. ABS & ESC Intervention Badges
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                AssistPill(label = "ABS", active = telemetry.isAbsActive, color = absColor)
                AssistPill(label = "ESC", active = telemetry.isEscActive, color = escColor)
            }
        }
    }
}

@Composable
private fun AssistPill(label: String, active: Boolean, color: Color) {
    Box(
        modifier = Modifier
            .background(
                color = if (active) color.copy(alpha = 0.25f) else Color(0x15FFFFFF),
                shape = RoundedCornerShape(4.dp)
            )
            .padding(horizontal = 5.dp, vertical = 1.dp)
    ) {
        Text(
            text = label,
            color = if (active) color else Color(0x55FFFFFF),
            fontSize = 9.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace
        )
    }
}

/**
 * Live vehicle telemetry card (G-meter, suspension travel, power output).
 */
@Composable
fun TelemetryOverlayCard(
    telemetry: VehicleTelemetry,
    modifier: Modifier = Modifier
) {
    Box(
        modifier = modifier
            .background(Color(0xD0121620), RoundedCornerShape(12.dp))
            .padding(10.dp)
            .testTag("telemetry_card")
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(
                text = "TELEMETRY",
                color = NeonCyan,
                fontSize = 10.sp,
                fontWeight = FontWeight.Bold,
                letterSpacing = 1.sp
            )

            // G-Meter
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = String.format("LAT: %+.2f G", telemetry.lateralG),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = String.format("LONG: %+.2f G", telemetry.longitudinalG),
                    color = Color.White,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // Power & Steer
            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "PWR: ${telemetry.powerKw.toInt()} kW",
                    color = RacingAmber,
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
                Text(
                    text = "STEER: ${telemetry.steerAngleDeg.toInt()}°",
                    color = Color(0xFF90CAF9),
                    fontSize = 11.sp,
                    fontFamily = FontFamily.Monospace
                )
            }

            // 4-Wheel Suspension compression diagram
            Row(
                horizontalArrangement = Arrangement.spacedBy(6.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SuspensionBar("FL", telemetry.suspensionFL)
                SuspensionBar("FR", telemetry.suspensionFR)
                SuspensionBar("RL", telemetry.suspensionRL)
                SuspensionBar("RR", telemetry.suspensionRR)
            }
        }
    }
}

@Composable
private fun SuspensionBar(label: String, compression: Float) {
    Column(horizontalAlignment = Alignment.CenterHorizontally) {
        Text(text = label, color = Color(0x88FFFFFF), fontSize = 8.sp)
        Box(
            modifier = Modifier
                .width(10.dp)
                .height(26.dp)
                .background(Color(0x33FFFFFF), RoundedCornerShape(2.dp)),
            contentAlignment = Alignment.BottomCenter
        ) {
            Box(
                modifier = Modifier
                    .width(10.dp)
                    .height((26 * compression).dp)
                    .background(NeonCyan, RoundedCornerShape(2.dp))
            )
        }
    }
}
