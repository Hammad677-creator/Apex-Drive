package com.example.physics

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sin
import kotlin.math.sqrt

/**
 * High-fidelity 4-wheel vehicle physics engine.
 * Implements suspension spring-damper dynamics, dynamic weight transfer (pitch & roll),
 * Pacejka-inspired tire friction curves, ESC (Electronic Stability Control),
 * ABS (Anti-lock Braking System), realistic engine torque curves, and automatic transmission.
 */
class VehiclePhysicsEngine {

    // Physical Vehicle Specifications
    val massKg: Float = 1450f
    val wheelbase: Float = 2.68f // distance between front and rear axle (m)
    val trackWidth: Float = 1.62f // distance between left and right wheels (m)
    val cogHeight: Float = 0.42f // Center of gravity height (m)
    val tireRadius: Float = 0.33f // 19-inch wheel + tire radius (m)
    val finalDriveRatio: Float = 3.65f
    val frontalArea: Float = 2.15f // m^2
    val dragCoefficient: Float = 0.31f
    val airDensity: Float = 1.225f

    // Suspension Parameters
    val suspensionSpringK: Float = 35000f // N/m
    val suspensionDamperC: Float = 2800f // Ns/m
    val suspensionRestLength: Float = 0.32f
    val suspensionMaxTravel: Float = 0.16f

    // State Variables
    var posX: Float = 0f
    var posY: Float = 0f // ground elevation
    var posZ: Float = 0f
    var yaw: Float = 0f // heading in radians
    var pitch: Float = 0f // radians (positive = front down / brake dive)
    var roll: Float = 0f // radians (positive = roll right)

    var pitchVelocity: Float = 0f
    var rollVelocity: Float = 0f

    var speedMps: Float = 0f
    var lateralVelocityMps: Float = 0f
    var yawRate: Float = 0f // rad/s

    // Engine & Drivetrain
    var currentGear: Gear = Gear.FIRST
    var engineRpm: Float = 850f
    val idleRpm: Float = 850f
    val maxRpm: Float = 7600f
    val redlineRpm: Float = 7000f
    var isShifting: Boolean = false
    var shiftTimer: Float = 0f

    // Wheels: [0: FL, 1: FR, 2: RL, 3: RR]
    val wheelSpinAngles = FloatArray(4) { 0f }
    val wheelAngularVelocities = FloatArray(4) { 0f }
    val wheelSuspensionCompressions = FloatArray(4) { 0.5f }
    val wheelNormalLoads = FloatArray(4) { (massKg * 9.81f) / 4f }
    val wheelSlipRatios = FloatArray(4) { 0f }
    val wheelSlipAngles = FloatArray(4) { 0f }
    val wheelIsSkidding = BooleanArray(4) { false }
    val wheelGroundPositions = Array(4) { FloatArray(2) } // X, Z for skidmarks

    // Drive & Safety Systems
    var driveMode: DriveMode = DriveMode.SPORT
    var transmissionMode: TransmissionMode = TransmissionMode.AUTO

    var isAbsIntervening: Boolean = false
    var isEscIntervening: Boolean = false
    var isTcsIntervening: Boolean = false

    var longitudinalG: Float = 0f
    var lateralG: Float = 0f
    var currentPowerKw: Float = 0f
    var steerAngleDeg: Float = 0f

    // Inputs
    val inputs = VehicleInputs()

    // Skidmark callback listener: (wheelIndex, x, z, intensity)
    var onSkidmarkCallback: ((Int, Float, Float, Float) -> Unit)? = null

    fun reset(startX: Float = 0f, startZ: Float = 0f, startYaw: Float = 0f) {
        posX = startX
        posY = 0f
        posZ = startZ
        yaw = startYaw
        pitch = 0f
        roll = 0f
        pitchVelocity = 0f
        rollVelocity = 0f
        speedMps = 0f
        lateralVelocityMps = 0f
        yawRate = 0f
        engineRpm = idleRpm
        currentGear = Gear.FIRST
        isShifting = false
        shiftTimer = 0f
        for (i in 0 until 4) {
            wheelSpinAngles[i] = 0f
            wheelAngularVelocities[i] = 0f
            wheelSuspensionCompressions[i] = 0.5f
            wheelNormalLoads[i] = (massKg * 9.81f) / 4f
            wheelSlipRatios[i] = 0f
            wheelSlipAngles[i] = 0f
            wheelIsSkidding[i] = false
        }
    }

    /**
     * Fixed-timestep physics update (typically called at 60Hz or 120Hz).
     */
    fun update(dt: Float) {
        val clampDt = dt.coerceIn(0.001f, 0.05f)

        // 1. Process steering input with dynamic speed-sensitive damping
        val maxSteerAngle = if (abs(speedMps) < 10f) {
            32f // 32 degrees at low speed for tight maneuvers
        } else {
            (32f - (abs(speedMps) - 10f) * 0.45f).coerceAtLeast(12f)
        }
        val targetSteerAngle = inputs.steer * maxSteerAngle
        // Smooth steering response
        steerAngleDeg += (targetSteerAngle - steerAngleDeg) * (18f * clampDt).coerceAtMost(1f)
        val steerRad = steerAngleDeg * (PI.toFloat() / 180f)

        // 2. Engine and Gear Shifting Logic
        updatePowertrain(clampDt)

        // 3. Weight Transfer (Pitch & Roll)
        // Aerodynamic drag
        val aeroDrag = 0.5f * airDensity * dragCoefficient * frontalArea * speedMps * abs(speedMps)
        val rollingResistance = 0.015f * massKg * 9.81f * (if (abs(speedMps) > 0.05f) 1f else 0f)

        // Calculate drive torque from engine
        val currentGearRatio = currentGear.ratio
        val engineTorque = calculateEngineTorque(engineRpm) * inputs.throttle * driveMode.throttleSensitivity
        val totalDriveTorque = engineTorque * currentGearRatio * finalDriveRatio * 0.88f // transmission efficiency

        // Apply brake torque (distributed 65% front, 35% rear for stability)
        val maxBrakeTorque = 4200f // Nm
        val targetFrontBrakeTorque = inputs.brake * maxBrakeTorque * 0.65f
        var targetRearBrakeTorque = inputs.brake * maxBrakeTorque * 0.35f
        if (inputs.handbrake) {
            targetRearBrakeTorque += 3800f
        }

        // 4. Wheels and Tire Friction Simulation
        var totalLongitudinalForce = 0f
        var totalLateralForce = 0f
        var totalYawTorque = 0f

        val halfWheelbase = wheelbase * 0.5f
        val halfTrack = trackWidth * 0.5f

        // Normal weight transfer calculation based on acceleration and lateral load
        val staticLoadPerWheel = (massKg * 9.81f) / 4f
        val longWeightTransfer = (massKg * longitudinalG * 9.81f * cogHeight) / wheelbase
        val latWeightTransfer = (massKg * lateralG * 9.81f * cogHeight) / trackWidth

        // Wheel loads [FL, FR, RL, RR]
        wheelNormalLoads[0] = (staticLoadPerWheel - longWeightTransfer * 0.5f - latWeightTransfer * 0.5f).coerceAtLeast(500f)
        wheelNormalLoads[1] = (staticLoadPerWheel - longWeightTransfer * 0.5f + latWeightTransfer * 0.5f).coerceAtLeast(500f)
        wheelNormalLoads[2] = (staticLoadPerWheel + longWeightTransfer * 0.5f - latWeightTransfer * 0.5f).coerceAtLeast(500f)
        wheelNormalLoads[3] = (staticLoadPerWheel + longWeightTransfer * 0.5f + latWeightTransfer * 0.5f).coerceAtLeast(500f)

        // ABS and ESC state tracking
        var absTriggered = false
        var escTriggered = false
        var tcsTriggered = false

        // ESC yaw stability calculation
        val desiredYawRate = if (abs(speedMps) > 0.5f) {
            (speedMps * steerRad) / wheelbase
        } else 0f
        val yawError = yawRate - desiredYawRate
        val escThreshold = 0.35f * (1f / driveMode.escAggressiveness)

        var escCorrectiveTorque = 0f
        if (abs(yawError) > escThreshold && abs(speedMps) > 4f) {
            escTriggered = true
            // ESC applies brake moment to outside front or inside rear to counter spin
            escCorrectiveTorque = -yawError * 3500f * driveMode.escAggressiveness
        }

        // Simulate each wheel
        for (i in 0 until 4) {
            val isFront = (i < 2)
            val isRight = (i % 2 == 1)

            val wheelSteer = if (isFront) steerRad else 0f
            val normalLoad = wheelNormalLoads[i]
            val maxGrip = normalLoad * 1.15f // Peak coefficient of friction mu ~ 1.15

            // Wheel velocity vector relative to ground at wheel contact patch
            val wheelForwardRelX = if (isRight) halfTrack else -halfTrack
            val wheelForwardRelZ = if (isFront) halfWheelbase else -halfWheelbase

            // Wheel local velocities
            val wheelLinearSpeed = speedMps + (if (isRight) -yawRate * halfTrack else yawRate * halfTrack) * 0.2f
            val wheelGroundLatSpeed = lateralVelocityMps + (if (isFront) yawRate * halfWheelbase else -yawRate * halfWheelbase)

            // Wheel steering rotation transform
            val wheelPatchForwardSpeed = wheelLinearSpeed * cos(wheelSteer) + wheelGroundLatSpeed * sin(wheelSteer)
            val wheelPatchLatSpeed = -wheelLinearSpeed * sin(wheelSteer) + wheelGroundLatSpeed * cos(wheelSteer)

            // Slip Angle
            val slipAngle = if (abs(wheelPatchForwardSpeed) > 0.2f) {
                atan2(wheelPatchLatSpeed, abs(wheelPatchForwardSpeed))
            } else 0f
            wheelSlipAngles[i] = slipAngle

            // Wheel slip ratio calculation (with smooth low-speed launch denominator)
            var wheelBrakeTorque = if (isFront) targetFrontBrakeTorque else targetRearBrakeTorque
            val wheelSpeedFromAngular = wheelAngularVelocities[i] * tireRadius
            val denom = max(0.5f, abs(wheelPatchForwardSpeed))
            val slipRatio = ((wheelSpeedFromAngular - wheelPatchForwardSpeed) / denom).coerceIn(-1.5f, 1.5f)
            wheelSlipRatios[i] = slipRatio

            if (inputs.brake > 0.05f && slipRatio < -0.18f && abs(speedMps) > 2.5f) {
                // ABS pulse relief
                absTriggered = true
                wheelBrakeTorque *= 0.25f // pressure dump
            }

            // Traction Control (TCS) on driven wheels (Rear Wheel Drive)
            var wheelDriveTorque = if (!isFront && !isShifting) totalDriveTorque * 0.5f else 0f
            if (!isFront && slipRatio > 0.25f && driveMode != DriveMode.TRACK) {
                tcsTriggered = true
                wheelDriveTorque *= 0.35f
            }

            // Torque balance to integrate wheel angular velocity
            val wheelInertia = 1.2f // kg m^2
            val netWheelTorque = wheelDriveTorque - (wheelBrakeTorque * wheelAngularVelocities[i].coerceIn(-1f, 1f))
            wheelAngularVelocities[i] += (netWheelTorque / wheelInertia) * clampDt

            // Damp angular velocity toward ground speed
            val groundAngularVel = wheelPatchForwardSpeed / tireRadius
            val coupling = if (inputs.throttle > 0.1f) 8f else 15f
            wheelAngularVelocities[i] += (groundAngularVel - wheelAngularVelocities[i]) * (coupling * clampDt).coerceAtMost(1f)

            // Update wheel spin rotation angle for rendering
            wheelSpinAngles[i] = (wheelSpinAngles[i] + wheelAngularVelocities[i] * clampDt) % (2f * PI.toFloat())

            // Pacejka Magic Formula simplified lateral force
            val lateralFriction = -maxGrip * sin(1.4f * atan2(7.0f * slipAngle, 1.0f))
            // Longitudinal tractive/braking force
            val longFriction = (maxGrip * slipRatio.coerceIn(-1f, 1f)).coerceIn(-maxGrip, maxGrip)

            // Transform wheel forces to vehicle chassis frame
            val fx = longFriction * cos(wheelSteer) - lateralFriction * sin(wheelSteer)
            val fy = longFriction * sin(wheelSteer) + lateralFriction * cos(wheelSteer)

            totalLongitudinalForce += fx
            totalLateralForce += fy

            // Yaw torque produced by this wheel
            val leverX = if (isRight) halfTrack else -halfTrack
            val leverZ = if (isFront) halfWheelbase else -halfWheelbase
            val yawArmTorque = (fx * leverX) + (fy * leverZ)
            totalYawTorque += yawArmTorque

            // Skidmark condition: significant lateral drift or brake lock/burnout
            val skidding = abs(slipAngle) > 0.24f || abs(slipRatio) > 0.32f || (inputs.handbrake && abs(speedMps) > 2f)
            wheelIsSkidding[i] = skidding

            // Calculate world coordinates of wheel contact patch
            val cosYaw = cos(yaw)
            val sinYaw = sin(yaw)
            val patchWorldX = posX + (leverX * cosYaw - leverZ * sinYaw)
            val patchWorldZ = posZ + (leverX * sinYaw + leverZ * cosYaw)
            wheelGroundPositions[i][0] = patchWorldX
            wheelGroundPositions[i][1] = patchWorldZ

            if (skidding && abs(speedMps) > 1.2f) {
                val skidIntensity = (abs(slipAngle) + abs(slipRatio)).coerceIn(0.2f, 1.0f)
                onSkidmarkCallback?.invoke(i, patchWorldX, patchWorldZ, skidIntensity)
            }
        }

        isAbsIntervening = absTriggered
        isEscIntervening = escTriggered
        isTcsIntervening = tcsTriggered

        // Apply ESC corrective moment
        totalYawTorque += escCorrectiveTorque

        // 5. Integrate Chassis Linear and Angular Accelerations
        val netForwardForce = totalLongitudinalForce - aeroDrag - rollingResistance
        val forwardAcc = netForwardForce / massKg
        val lateralAcc = totalLateralForce / massKg

        speedMps += forwardAcc * clampDt
        lateralVelocityMps += lateralAcc * clampDt

        // Damping on lateral drift to maintain weighted stability
        lateralVelocityMps *= exp(-3.8f * clampDt)

        // Yaw moment of inertia
        val yawInertia = (1f / 12f) * massKg * (wheelbase * wheelbase + trackWidth * trackWidth)
        val yawAcc = totalYawTorque / yawInertia
        yawRate += yawAcc * clampDt
        yawRate *= exp(-4.2f * clampDt) // rotational damping

        // Update heading and world position
        yaw += yawRate * clampDt

        val worldVelX = speedMps * sin(yaw) + lateralVelocityMps * cos(yaw)
        val worldVelZ = speedMps * cos(yaw) - lateralVelocityMps * sin(yaw)

        posX += worldVelX * clampDt
        posZ += worldVelZ * clampDt

        // 6. Dynamic Chassis Weight Transfer (Pitch & Roll Simulation)
        longitudinalG = forwardAcc / 9.81f
        lateralG = (speedMps * yawRate + lateralAcc) / 9.81f

        // Pitch dynamics: diving on braking (positive pitch), squat on acceleration (negative pitch)
        val targetPitch = (-longitudinalG * 0.045f).coerceIn(-0.08f, 0.08f)
        val pitchSpringForce = (targetPitch - pitch) * 85f
        pitchVelocity += (pitchSpringForce - pitchVelocity * 14f) * clampDt
        pitch += pitchVelocity * clampDt

        // Roll dynamics: rolling outward in turns (cornering body roll)
        val targetRoll = (lateralG * 0.055f).coerceIn(-0.1f, 0.1f)
        val rollSpringForce = (targetRoll - roll) * 90f
        rollVelocity += (rollSpringForce - rollVelocity * 15f) * clampDt
        roll += rollVelocity * clampDt

        // 7. Update Suspension Compressions for 3D visual rendering
        wheelSuspensionCompressions[0] = (0.5f - pitch * 2.5f - roll * 2.0f).coerceIn(0.1f, 0.95f)
        wheelSuspensionCompressions[1] = (0.5f - pitch * 2.5f + roll * 2.0f).coerceIn(0.1f, 0.95f)
        wheelSuspensionCompressions[2] = (0.5f + pitch * 2.5f - roll * 2.0f).coerceIn(0.1f, 0.95f)
        wheelSuspensionCompressions[3] = (0.5f + pitch * 2.5f + roll * 2.0f).coerceIn(0.1f, 0.95f)

        // Telemetry Power calculation
        currentPowerKw = ((engineTorque * (engineRpm * 2f * PI.toFloat() / 60f)) / 1000f).coerceAtLeast(0f)
    }

    private fun updatePowertrain(dt: Float) {
        if (isShifting) {
            shiftTimer -= dt
            // Drop RPM smoothly toward expected next gear RPM
            val targetShiftRpm = (abs(speedMps) / tireRadius) * finalDriveRatio * currentGear.ratio * (60f / (2f * PI.toFloat()))
            engineRpm += (targetShiftRpm.coerceIn(idleRpm, maxRpm) - engineRpm) * (18f * dt)
            if (shiftTimer <= 0f) {
                isShifting = false
            }
            return
        }

        // Compute RPM from driven wheel speed
        val avgRearWheelSpeed = (wheelAngularVelocities[2] + wheelAngularVelocities[3]) * 0.5f
        val gearRatio = abs(currentGear.ratio)

        if (currentGear == Gear.NEUTRAL) {
            // Free revving in neutral
            val revTarget = if (inputs.throttle > 0.05f) {
                idleRpm + inputs.throttle * (maxRpm - idleRpm)
            } else idleRpm
            engineRpm += (revTarget - engineRpm) * (8f * dt)
        } else {
            val mechanicalRpm = if (gearRatio > 0.01f) {
                abs(avgRearWheelSpeed) * finalDriveRatio * gearRatio * (60f / (2f * PI.toFloat()))
            } else idleRpm

            val targetRpm = max(idleRpm, mechanicalRpm) + inputs.throttle * 400f * (if (abs(speedMps) < 2f) 1f else 0.1f)
            engineRpm += (targetRpm - engineRpm) * (20f * dt)
        }

        engineRpm = engineRpm.coerceIn(idleRpm, maxRpm)

        // Automatic Transmission Shift Logic
        if (transmissionMode == TransmissionMode.AUTO) {
            if (currentGear == Gear.REVERSE) {
                // Stay in reverse unless user shifts
            } else if (currentGear.index in 1..5 && engineRpm > driveMode.shiftRpm && abs(speedMps) > 4f) {
                // Upshift
                shiftUp()
            } else if (currentGear.index > 1 && engineRpm < 2200f && abs(speedMps) > 1f) {
                // Downshift
                shiftDown()
            }
        }
    }

    fun shiftUp() {
        val nextIndex = currentGear.index + 1
        val nextGear = Gear.values().firstOrNull { it.index == nextIndex }
        if (nextGear != null && nextIndex <= 6) {
            currentGear = nextGear
            isShifting = true
            shiftTimer = 0.12f // 120ms dual-clutch shift duration
        }
    }

    fun shiftDown() {
        val prevIndex = currentGear.index - 1
        val prevGear = Gear.values().firstOrNull { it.index == prevIndex }
        if (prevGear != null && prevIndex >= 1) {
            currentGear = prevGear
            isShifting = true
            shiftTimer = 0.12f
            // Rev match blip
            engineRpm = (engineRpm + 1200f).coerceAtMost(maxRpm)
        }
    }

    fun toggleReverse() {
        currentGear = if (currentGear == Gear.REVERSE) Gear.FIRST else Gear.REVERSE
        isShifting = true
        shiftTimer = 0.15f
    }

    /**
     * Realistic sports car torque curve.
     * Peaks at ~480 Nm between 4500 - 5800 RPM.
     */
    private fun calculateEngineTorque(rpm: Float): Float {
        val peakTorque = 490f // Nm
        val x = (rpm - 5200f) / 2500f
        val curve = 1.0f - (x * x * 0.35f)
        return (peakTorque * curve.coerceIn(0.35f, 1.0f)).coerceAtLeast(150f)
    }

    fun getTelemetry(): VehicleTelemetry {
        val speedKmh = abs(speedMps) * 3.6f
        val speedMph = speedKmh * 0.621371f
        return VehicleTelemetry(
            speedKmh = speedKmh,
            speedMph = speedMph,
            rpm = engineRpm,
            gearLabel = currentGear.label,
            gearNumber = currentGear.index,
            isAbsActive = isAbsIntervening,
            isEscActive = isEscIntervening,
            isTcsActive = isTcsIntervening,
            longitudinalG = longitudinalG,
            lateralG = lateralG,
            powerKw = currentPowerKw,
            suspensionFL = wheelSuspensionCompressions[0],
            suspensionFR = wheelSuspensionCompressions[1],
            suspensionRL = wheelSuspensionCompressions[2],
            suspensionRR = wheelSuspensionCompressions[3],
            driveMode = driveMode,
            transmissionMode = transmissionMode,
            steerAngleDeg = steerAngleDeg
        )
    }
}
