package com.example.graphics

import android.opengl.Matrix
import com.example.physics.CameraMode
import com.example.physics.VehiclePhysicsEngine
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.sin

/**
 * High-performance follow camera system with critically damped spring interpolation.
 * Positions slightly low behind the car for speed sensation, fluidly catching up
 * during acceleration and cornering with dynamic yaw/slip compliance.
 */
class FollowCamera {

    var cameraMode: CameraMode = CameraMode.CHASE

    // Camera current position and target in world space
    var eyeX: Float = 0f
    var eyeY: Float = 2.0f
    var eyeZ: Float = -6.0f

    var targetX: Float = 0f
    var targetY: Float = 0.85f
    var targetZ: Float = 0f

    // Orbit parameters for Free Orbit mode
    var orbitYawDeg: Float = 0f
    var orbitPitchDeg: Float = 20f
    var orbitDistance: Float = 6.5f

    val viewMatrix = FloatArray(16)
    val projMatrix = FloatArray(16)

    fun init(physics: VehiclePhysicsEngine) {
        val carYaw = physics.yaw
        eyeX = physics.posX - sin(carYaw) * 6.2f
        eyeY = physics.posY + 1.85f
        eyeZ = physics.posZ - cos(carYaw) * 6.2f
        targetX = physics.posX
        targetY = physics.posY + 0.85f
        targetZ = physics.posZ
    }

    fun update(physics: VehiclePhysicsEngine, dt: Float) {
        val clampDt = dt.coerceIn(0.001f, 0.05f)
        val carYaw = physics.yaw
        val speedMps = physics.speedMps

        when (cameraMode) {
            CameraMode.CHASE -> {
                // Dynamic speed compliance: camera pushes slightly back and low at high speed
                val speedFactor = (speedMps / 40f).coerceIn(0f, 1f)
                val dist = 5.8f + speedFactor * 0.9f
                val height = 1.75f - speedFactor * 0.2f

                // Desired camera position behind car with slight yaw lag
                val idealEyeX = physics.posX - sin(carYaw) * dist
                val idealEyeY = (physics.posY + height).coerceAtLeast(0.6f)
                val idealEyeZ = physics.posZ - cos(carYaw) * dist

                // Look-ahead target point ahead of car
                val lookAheadDist = 3.5f + speedFactor * 3.0f
                val idealTargetX = physics.posX + sin(carYaw) * lookAheadDist
                val idealTargetY = physics.posY + 0.90f
                val idealTargetZ = physics.posZ + cos(carYaw) * lookAheadDist

                // Smooth exponential spring damping (fluid catch-up, zero jitter)
                val posLag = 1f - exp(-7.5f * clampDt)
                val targetLag = 1f - exp(-14.0f * clampDt)

                eyeX += (idealEyeX - eyeX) * posLag
                eyeY += (idealEyeY - eyeY) * posLag
                eyeZ += (idealEyeZ - eyeZ) * posLag

                targetX += (idealTargetX - targetX) * targetLag
                targetY += (idealTargetY - targetY) * targetLag
                targetZ += (idealTargetZ - targetZ) * targetLag
            }
            CameraMode.COCKPIT -> {
                // Driver eye perspective inside cabin (left-hand drive)
                val seatOffsetX = -0.32f
                val seatOffsetY = 0.98f
                val seatOffsetZ = -0.15f

                val cosY = cos(carYaw); val sinY = sin(carYaw)
                val idealEyeX = physics.posX + (seatOffsetX * cosY - seatOffsetZ * sinY)
                val idealEyeY = physics.posY + seatOffsetY + physics.pitch * 0.4f
                val idealEyeZ = physics.posZ + (seatOffsetX * sinY + seatOffsetZ * cosY)

                val idealTargetX = physics.posX + sin(carYaw) * 12f
                val idealTargetY = idealEyeY - 0.05f + physics.pitch * 0.8f
                val idealTargetZ = physics.posZ + cos(carYaw) * 12f

                val lag = 1f - exp(-22.0f * clampDt)
                eyeX += (idealEyeX - eyeX) * lag
                eyeY += (idealEyeY - eyeY) * lag
                eyeZ += (idealEyeZ - eyeZ) * lag
                targetX += (idealTargetX - targetX) * lag
                targetY += (idealTargetY - targetY) * lag
                targetZ += (idealTargetZ - targetZ) * lag
            }
            CameraMode.HOOD -> {
                // Low front bumper / hood view for pure asphalt velocity
                val hoodOffsetZ = 1.6f
                val idealEyeX = physics.posX + sin(carYaw) * hoodOffsetZ
                val idealEyeY = physics.posY + 0.62f
                val idealEyeZ = physics.posZ + cos(carYaw) * hoodOffsetZ

                val idealTargetX = physics.posX + sin(carYaw) * 16f
                val idealTargetY = idealEyeY - 0.02f
                val idealTargetZ = physics.posZ + cos(carYaw) * 16f

                val lag = 1f - exp(-25.0f * clampDt)
                eyeX += (idealEyeX - eyeX) * lag
                eyeY += (idealEyeY - eyeY) * lag
                eyeZ += (idealEyeZ - eyeZ) * lag
                targetX += (idealTargetX - targetX) * lag
                targetY += (idealTargetY - targetY) * lag
                targetZ += (idealTargetZ - targetZ) * lag
            }
            CameraMode.ORBIT -> {
                // Interactive 360 orbit camera around the vehicle
                val radYaw = (orbitYawDeg + (physics.yaw * 180f / PI.toFloat())) * (PI.toFloat() / 180f)
                val radPitch = orbitPitchDeg * (PI.toFloat() / 180f)

                val horizDist = orbitDistance * cos(radPitch)
                val idealEyeX = physics.posX - sin(radYaw) * horizDist
                val idealEyeY = (physics.posY + 0.85f + orbitDistance * sin(radPitch)).coerceAtLeast(0.4f)
                val idealEyeZ = physics.posZ - cos(radYaw) * horizDist

                val idealTargetX = physics.posX
                val idealTargetY = physics.posY + 0.75f
                val idealTargetZ = physics.posZ

                val lag = 1f - exp(-12.0f * clampDt)
                eyeX += (idealEyeX - eyeX) * lag
                eyeY += (idealEyeY - eyeY) * lag
                eyeZ += (idealEyeZ - eyeZ) * lag
                targetX += (idealTargetX - targetX) * lag
                targetY += (idealTargetY - targetY) * lag
                targetZ += (idealTargetZ - targetZ) * lag
            }
        }

        Matrix.setLookAtM(viewMatrix, 0, eyeX, eyeY, eyeZ, targetX, targetY, targetZ, 0f, 1f, 0f)
    }

    fun updateProjection(width: Int, height: Int) {
        val aspect = width.toFloat() / height.coerceAtLeast(1).toFloat()
        // 60-degree field of view for natural sports car perspective
        Matrix.perspectiveM(projMatrix, 0, 60.0f, aspect, 0.35f, 600.0f)
    }

    fun cycleNextMode(): CameraMode {
        val modes = CameraMode.values()
        val nextIdx = (cameraMode.ordinal + 1) % modes.size
        cameraMode = modes[nextIdx]
        return cameraMode
    }
}
