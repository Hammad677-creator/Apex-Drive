package com.example.ui

import android.view.MotionEvent
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.pointerInteropFilter
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.physics.TransmissionMode
import com.example.physics.VehicleInputs
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingAmber
import com.example.ui.theme.RacingRed

/**
 * On-screen touch controls for vehicle simulation.
 * Left hand: Interactive steering slider or steering buttons, handbrake.
 * Right hand: Progressive analog throttle & brake pedals, gear shift paddles.
 */
@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun DrivingControls(
    inputs: VehicleInputs,
    transmissionMode: TransmissionMode,
    isReverse: Boolean,
    onShiftUp: () -> Unit,
    onShiftDown: () -> Unit,
    onToggleReverse: () -> Unit,
    modifier: Modifier = Modifier
) {
    var steerOffsetPx by remember { mutableFloatStateOf(0f) }
    var isThrottlePressed by remember { mutableStateOf(false) }
    var isBrakePressed by remember { mutableStateOf(false) }
    var isHandbrakePressed by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp, vertical = 14.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.Bottom
    ) {
        // LEFT CONTROLS: Steering Bar & Handbrake
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Handbrake Drift Button
            Box(
                modifier = Modifier
                    .size(width = 110.dp, height = 44.dp)
                    .clip(RoundedCornerShape(22.dp))
                    .background(
                        if (isHandbrakePressed) RacingRed else Color(0x991A1E29)
                    )
                    .border(1.5.dp, if (isHandbrakePressed) RacingRed else Color(0x44FFFFFF), RoundedCornerShape(22.dp))
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN -> {
                                isHandbrakePressed = true
                                inputs.handbrake = true
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isHandbrakePressed = false
                                inputs.handbrake = false
                                true
                            }
                            else -> false
                        }
                    }
                    .testTag("handbrake_button"),
                contentAlignment = Alignment.Center
            ) {
                Text(
                    text = "E-BRAKE",
                    color = Color.White,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                    letterSpacing = 1.sp
                )
            }

            // Interactive Analog Horizontal Steering Bar
            Box(
                modifier = Modifier
                    .size(width = 200.dp, height = 54.dp)
                    .clip(RoundedCornerShape(27.dp))
                    .background(Color(0xA0121620))
                    .border(1.5.dp, Color(0x3300E5FF), RoundedCornerShape(27.dp))
                    .pointerInput(Unit) {
                        val maxDragPx = 75f
                        detectDragGestures(
                            onDrag = { change, dragAmount ->
                                change.consume()
                                steerOffsetPx = (steerOffsetPx + dragAmount.x).coerceIn(-maxDragPx, maxDragPx)
                                inputs.steer = (steerOffsetPx / maxDragPx).coerceIn(-1.0f, 1.0f)
                            },
                            onDragEnd = {
                                // Auto-centering spring back
                                steerOffsetPx = 0f
                                inputs.steer = 0f
                            },
                            onDragCancel = {
                                steerOffsetPx = 0f
                                inputs.steer = 0f
                            }
                        )
                    }
                    .testTag("steering_slider"),
                contentAlignment = Alignment.Center
            ) {
                // Background steering track guides
                Row(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 14.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(
                        imageVector = Icons.Default.ChevronLeft,
                        contentDescription = "Steer Left",
                        tint = if (inputs.steer < -0.1f) NeonCyan else Color(0x44FFFFFF)
                    )
                    Text(
                        text = "STEER",
                        color = Color(0x66FFFFFF),
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        letterSpacing = 2.sp
                    )
                    Icon(
                        imageVector = Icons.Default.ChevronRight,
                        contentDescription = "Steer Right",
                        tint = if (inputs.steer > 0.1f) NeonCyan else Color(0x44FFFFFF)
                    )
                }

                // Draggable Steering Thumb Knob
                Box(
                    modifier = Modifier
                        .offset { IntOffset(steerOffsetPx.toInt(), 0) }
                        .size(44.dp)
                        .clip(CircleShape)
                        .background(
                            brush = Brush.radialGradient(
                                colors = listOf(NeonCyan, Color(0xFF0091EA))
                            )
                        )
                        .border(2.dp, Color.White, CircleShape)
                )
            }
        }

        // CENTER SPACER (Leaves road and car completely unobstructed)
        Spacer(modifier = Modifier.weight(1f))

        // RIGHT CONTROLS: Pedals (Brake & Throttle) + Shifter
        Row(
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.Bottom
        ) {
            // Gear Shifter Column (Auto R/D toggle or Manual Paddle Shifters)
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(8.dp),
                modifier = Modifier.padding(bottom = 6.dp)
            ) {
                if (transmissionMode == TransmissionMode.MANUAL) {
                    // Shift Up Paddle (+)
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x991A1E29))
                            .border(1.dp, NeonCyan, RoundedCornerShape(10.dp))
                            .pointerInteropFilter { motionEvent ->
                                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                                    onShiftUp()
                                    true
                                } else false
                            }
                            .testTag("paddle_shift_up"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowUp,
                            contentDescription = "Shift Up",
                            tint = NeonCyan
                        )
                    }

                    // Shift Down Paddle (-)
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 44.dp)
                            .clip(RoundedCornerShape(10.dp))
                            .background(Color(0x991A1E29))
                            .border(1.dp, RacingAmber, RoundedCornerShape(10.dp))
                            .pointerInteropFilter { motionEvent ->
                                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                                    onShiftDown()
                                    true
                                } else false
                            }
                            .testTag("paddle_shift_down"),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Default.KeyboardArrowDown,
                            contentDescription = "Shift Down",
                            tint = RacingAmber
                        )
                    }
                } else {
                    // Reverse Toggle Button
                    Box(
                        modifier = Modifier
                            .size(width = 54.dp, height = 50.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(if (isReverse) RacingRed else Color(0x991A1E29))
                            .border(1.5.dp, if (isReverse) RacingRed else Color(0x44FFFFFF), RoundedCornerShape(12.dp))
                            .pointerInteropFilter { motionEvent ->
                                if (motionEvent.action == MotionEvent.ACTION_DOWN) {
                                    onToggleReverse()
                                    true
                                } else false
                            }
                            .testTag("reverse_gear_toggle"),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = if (isReverse) "R" else "D",
                            color = Color.White,
                            fontSize = 20.sp,
                            fontWeight = FontWeight.Black,
                            fontFamily = FontFamily.Monospace
                        )
                    }
                }
            }

            // Analog Brake Pedal (Wide rectangular pedal with ribs)
            Box(
                modifier = Modifier
                    .size(width = 72.dp, height = 120.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isBrakePressed) Color(0xD0E53935) else Color(0xB01C212D)
                    )
                    .border(2.dp, if (isBrakePressed) RacingRed else Color(0x44FFFFFF), RoundedCornerShape(16.dp))
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                isBrakePressed = true
                                inputs.brake = 1.0f
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isBrakePressed = false
                                inputs.brake = 0.0f
                                true
                            }
                            else -> false
                        }
                    }
                    .testTag("brake_pedal"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    for (i in 0 until 4) {
                        Box(
                            modifier = Modifier
                                .size(width = 44.dp, height = 5.dp)
                                .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(4.dp))
                    Text(
                        text = "BRAKE",
                        color = Color.White,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Analog Throttle / Gas Pedal (Tall vertical pedal)
            Box(
                modifier = Modifier
                    .size(width = 68.dp, height = 145.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(
                        if (isThrottlePressed) Color(0xD000B0FF) else Color(0xB01A202C)
                    )
                    .border(2.dp, if (isThrottlePressed) NeonCyan else Color(0x44FFFFFF), RoundedCornerShape(16.dp))
                    .pointerInteropFilter { motionEvent ->
                        when (motionEvent.action) {
                            MotionEvent.ACTION_DOWN, MotionEvent.ACTION_MOVE -> {
                                isThrottlePressed = true
                                inputs.throttle = 1.0f
                                true
                            }
                            MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                                isThrottlePressed = false
                                inputs.throttle = 0.0f
                                true
                            }
                            else -> false
                        }
                    }
                    .testTag("throttle_pedal"),
                contentAlignment = Alignment.Center
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(7.dp)
                ) {
                    for (i in 0 until 5) {
                        Box(
                            modifier = Modifier
                                .size(width = 40.dp, height = 4.dp)
                                .background(Color(0x55FFFFFF), RoundedCornerShape(2.dp))
                        )
                    }
                    Spacer(modifier = Modifier.height(6.dp))
                    Text(
                        text = "GAS",
                        color = Color.White,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }
        }
    }
}
