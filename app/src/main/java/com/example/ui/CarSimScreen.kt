package com.example.ui

import android.annotation.SuppressLint
import android.opengl.GLSurfaceView
import android.view.MotionEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Analytics
import androidx.compose.material.icons.filled.ColorLens
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Videocam
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.example.graphics.FollowCamera
import com.example.graphics.SimulationGLRenderer
import com.example.physics.CameraMode
import com.example.physics.CarColor
import com.example.physics.DriveMode
import com.example.physics.TransmissionMode
import com.example.physics.VehiclePhysicsEngine
import com.example.physics.VehicleTelemetry
import com.example.ui.theme.CarbonCard
import com.example.ui.theme.CarbonDark
import com.example.ui.theme.NeonCyan
import com.example.ui.theme.RacingAmber
import com.example.ui.theme.RacingRed

/**
 * Main 3D Car Driving Simulation screen.
 * Integrates hardware-accelerated OpenGL ES 3D rendering with responsive
 * Jetpack Compose diegetic HUD, circular speedo cluster, telemetry, and driving controls.
 */
@SuppressLint("ClickableViewAccessibility")
@Composable
fun CarSimScreen(modifier: Modifier = Modifier) {
    val context = LocalContext.current

    // Physics Engine and Camera instances
    val physics = remember { VehiclePhysicsEngine() }
    val camera = remember { FollowCamera() }
    val renderer = remember { SimulationGLRenderer(physics, camera) }

    // State for Compose UI
    var telemetry by remember { mutableStateOf(VehicleTelemetry()) }
    var useMph by remember { mutableStateOf(false) }
    var showTelemetryCard by remember { mutableStateOf(false) }
    var showColorPicker by remember { mutableStateOf(false) }
    var currentCarColor by remember { mutableStateOf(CarColor.ROSSO_CORSA) }
    var currentCameraMode by remember { mutableStateOf(CameraMode.CHASE) }
    var currentDriveMode by remember { mutableStateOf(DriveMode.SPORT) }
    var currentTransmissionMode by remember { mutableStateOf(TransmissionMode.AUTO) }

    // Connect telemetry update callback to Compose state
    DisposableEffect(renderer) {
        renderer.onTelemetryUpdated = { newTelemetry ->
            telemetry = newTelemetry
        }
        onDispose {
            renderer.onTelemetryUpdated = null
        }
    }

    Box(modifier = modifier.fillMaxSize().background(CarbonDark)) {
        // 1. OpenGL ES 3D Viewport (Full Screen)
        var lastTouchX = 0f
        var lastTouchY = 0f

        AndroidView(
            factory = { ctx ->
                GLSurfaceView(ctx).apply {
                    setEGLContextClientVersion(2)
                    setRenderer(renderer)
                    renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

                    // Orbit camera touch drag listener
                    setOnTouchListener { _, event ->
                        if (camera.cameraMode == CameraMode.ORBIT) {
                            when (event.action) {
                                MotionEvent.ACTION_DOWN -> {
                                    lastTouchX = event.x
                                    lastTouchY = event.y
                                    true
                                }
                                MotionEvent.ACTION_MOVE -> {
                                    val dx = event.x - lastTouchX
                                    val dy = event.y - lastTouchY
                                    lastTouchX = event.x
                                    lastTouchY = event.y
                                    renderer.handleOrbitDrag(dx, dy)
                                    true
                                }
                                else -> false
                            }
                        } else false
                    }
                }
            },
            modifier = Modifier.fillMaxSize().testTag("gl_surface_view")
        )

        // 2. Top Navigation Bar (Modes, Camera, Paint, Telemetry, Reset)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 12.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            // Brand & Drive Mode Pill
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                // Title Badge
                Box(
                    modifier = Modifier
                        .background(Color(0xD0121620), RoundedCornerShape(8.dp))
                        .padding(horizontal = 10.dp, vertical = 6.dp)
                ) {
                    Text(
                        text = "APEX DRIVE",
                        color = Color.White,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Black,
                        fontFamily = FontFamily.SansSerif,
                        letterSpacing = 1.sp
                    )
                }

                // Drive Mode Switcher (Comfort / Sport / Track)
                Row(
                    modifier = Modifier
                        .background(Color(0xD0121620), RoundedCornerShape(8.dp))
                        .padding(2.dp),
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    DriveMode.values().forEach { mode ->
                        val isSelected = currentDriveMode == mode
                        Box(
                            modifier = Modifier
                                .clip(RoundedCornerShape(6.dp))
                                .background(
                                    if (isSelected) {
                                        when (mode) {
                                            DriveMode.COMFORT -> NeonCyan
                                            DriveMode.SPORT -> RacingAmber
                                            DriveMode.TRACK -> RacingRed
                                        }
                                    } else Color.Transparent
                                )
                                .clickable {
                                    currentDriveMode = mode
                                    physics.driveMode = mode
                                }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                                .testTag("drive_mode_${mode.name.lowercase()}"),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = mode.label,
                                color = if (isSelected) CarbonDark else Color(0x99FFFFFF),
                                fontSize = 10.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }
                    }
                }

                // Transmission Mode Toggle (Auto / Manual)
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xD0121620))
                        .clickable {
                            val nextMode = if (currentTransmissionMode == TransmissionMode.AUTO) {
                                TransmissionMode.MANUAL
                            } else TransmissionMode.AUTO
                            currentTransmissionMode = nextMode
                            physics.transmissionMode = nextMode
                        }
                        .padding(horizontal = 9.dp, vertical = 6.dp)
                        .testTag("transmission_toggle"),
                    contentAlignment = Alignment.Center
                ) {
                    Text(
                        text = if (currentTransmissionMode == TransmissionMode.AUTO) "AUTO" else "MANUAL",
                        color = NeonCyan,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        fontFamily = FontFamily.Monospace
                    )
                }
            }

            // Quick Actions: Camera Switch, Car Color, Telemetry, Reset
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                // Camera Mode Switch
                Box(
                    modifier = Modifier
                        .clip(RoundedCornerShape(8.dp))
                        .background(Color(0xD0121620))
                        .clickable {
                            val newMode = camera.cycleNextMode()
                            currentCameraMode = newMode
                        }
                        .padding(horizontal = 8.dp, vertical = 6.dp)
                        .testTag("camera_switch_button"),
                    contentAlignment = Alignment.Center
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Videocam,
                            contentDescription = "Switch Camera",
                            tint = Color.White,
                            modifier = Modifier.size(16.dp)
                        )
                        Text(
                            text = currentCameraMode.label,
                            color = Color.White,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.SemiBold
                        )
                    }
                }

                // Car Paint Color Picker Toggle
                IconButton(
                    onClick = { showColorPicker = !showColorPicker },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xD0121620), RoundedCornerShape(8.dp))
                        .testTag("color_picker_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.ColorLens,
                        contentDescription = "Car Color",
                        tint = Color(
                            (currentCarColor.r * 255).toInt(),
                            (currentCarColor.g * 255).toInt(),
                            (currentCarColor.b * 255).toInt()
                        ),
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Telemetry Card Toggle
                IconButton(
                    onClick = { showTelemetryCard = !showTelemetryCard },
                    modifier = Modifier
                        .size(36.dp)
                        .background(
                            if (showTelemetryCard) NeonCyan else Color(0xD0121620),
                            RoundedCornerShape(8.dp)
                        )
                        .testTag("telemetry_toggle_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Analytics,
                        contentDescription = "Telemetry Overlay",
                        tint = if (showTelemetryCard) CarbonDark else Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }

                // Reset Vehicle Position
                IconButton(
                    onClick = { physics.reset(startX = 0f, startZ = 0f, startYaw = 0f) },
                    modifier = Modifier
                        .size(36.dp)
                        .background(Color(0xD0121620), RoundedCornerShape(8.dp))
                        .testTag("reset_car_button")
                ) {
                    Icon(
                        imageVector = Icons.Default.Refresh,
                        contentDescription = "Reset Car",
                        tint = Color.White,
                        modifier = Modifier.size(18.dp)
                    )
                }
            }
        }

        // 3. Dropdown Color Picker Row (Animated)
        AnimatedVisibility(
            visible = showColorPicker,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopEnd)
                .padding(top = 58.dp, end = 16.dp)
        ) {
            Row(
                modifier = Modifier
                    .background(Color(0xE610141D), RoundedCornerShape(12.dp))
                    .padding(8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                CarColor.values().forEach { col ->
                    Box(
                        modifier = Modifier
                            .size(28.dp)
                            .clip(CircleShape)
                            .background(
                                Color(
                                    (col.r * 255).toInt(),
                                    (col.g * 255).toInt(),
                                    (col.b * 255).toInt()
                                )
                            )
                            .border(
                                2.dp,
                                if (currentCarColor == col) Color.White else Color(0x33FFFFFF),
                                CircleShape
                            )
                            .clickable {
                                currentCarColor = col
                                renderer.carColor = col
                                showColorPicker = false
                            }
                            .testTag("color_${col.name.lowercase()}")
                    )
                }
            }
        }

        // 4. Live Telemetry Card (Top Left)
        AnimatedVisibility(
            visible = showTelemetryCard,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.TopStart)
                .padding(top = 58.dp, start = 16.dp)
        ) {
            TelemetryOverlayCard(telemetry = telemetry)
        }

        // 5. Minimalistic Diegetic Speedometer & Tachometer Cluster
        // Placed strictly in the bottom corner of the screen
        CircularSpeedoCluster(
            telemetry = telemetry,
            useMph = useMph,
            onToggleUnit = { useMph = !useMph },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(bottom = 12.dp)
        )

        // 6. Interactive Driving Controls (Left: Steering & Handbrake, Right: Pedals & Shifter)
        DrivingControls(
            inputs = physics.inputs,
            transmissionMode = currentTransmissionMode,
            isReverse = physics.currentGear.index == -1,
            onShiftUp = { physics.shiftUp() },
            onShiftDown = { physics.shiftDown() },
            onToggleReverse = { physics.toggleReverse() }
        )
    }
}
