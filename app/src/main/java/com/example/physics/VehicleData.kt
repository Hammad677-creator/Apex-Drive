package com.example.physics

enum class DriveMode(val label: String, val escAggressiveness: Float, val shiftRpm: Float, val throttleSensitivity: Float) {
    COMFORT("COMFORT", 1.0f, 3800f, 0.85f),
    SPORT("SPORT", 0.65f, 6200f, 1.15f),
    TRACK("TRACK", 0.25f, 7200f, 1.35f)
}

enum class TransmissionMode {
    AUTO,
    MANUAL
}

enum class Gear(val index: Int, val label: String, val ratio: Float) {
    REVERSE(-1, "R", -3.42f),
    NEUTRAL(0, "N", 0.0f),
    FIRST(1, "1", 3.65f),
    SECOND(2, "2", 2.24f),
    THIRD(3, "3", 1.54f),
    FOURTH(4, "4", 1.18f),
    FIFTH(5, "5", 0.94f),
    SIXTH(6, "6", 0.78f)
}

enum class CameraMode(val label: String) {
    CHASE("Chase"),
    COCKPIT("Cockpit"),
    HOOD("Hood"),
    ORBIT("Free Orbit")
}

enum class CarColor(val label: String, val r: Float, val g: Float, val b: Float, val metallic: Float) {
    ROSSO_CORSA("Rosso Corsa", 0.92f, 0.08f, 0.12f, 0.4f),
    MIAMI_BLUE("Miami Blue", 0.05f, 0.65f, 0.95f, 0.5f),
    BRITISH_GREEN("Racing Green", 0.05f, 0.35f, 0.18f, 0.6f),
    STEALTH_BLACK("Stealth Satin", 0.12f, 0.13f, 0.15f, 0.2f),
    CYBER_SILVER("Liquid Silver", 0.78f, 0.82f, 0.88f, 0.85f),
    SOLAR_GOLD("Solar Gold", 0.95f, 0.72f, 0.15f, 0.7f)
}

data class WheelState(
    val name: String,
    val angularVelocity: Float = 0f,
    val slipAngle: Float = 0f,
    val slipRatio: Float = 0f,
    val suspensionCompression: Float = 0.5f, // 0 = fully extended, 1 = bottomed out
    val isAbsIntervening: Boolean = false,
    val isTcsIntervening: Boolean = false,
    val isSkidding: Boolean = false,
    val groundContactX: Float = 0f,
    val groundContactZ: Float = 0f
)

data class VehicleTelemetry(
    val speedKmh: Float = 0f,
    val speedMph: Float = 0f,
    val rpm: Float = 850f,
    val gearLabel: String = "1",
    val gearNumber: Int = 1,
    val isAbsActive: Boolean = false,
    val isEscActive: Boolean = false,
    val isTcsActive: Boolean = false,
    val longitudinalG: Float = 0f,
    val lateralG: Float = 0f,
    val powerKw: Float = 0f,
    val suspensionFL: Float = 0.5f,
    val suspensionFR: Float = 0.5f,
    val suspensionRL: Float = 0.5f,
    val suspensionRR: Float = 0.5f,
    val driveMode: DriveMode = DriveMode.SPORT,
    val transmissionMode: TransmissionMode = TransmissionMode.AUTO,
    val steerAngleDeg: Float = 0f
)

data class VehicleInputs(
    var steer: Float = 0f,       // -1.0 (left) to 1.0 (right)
    var throttle: Float = 0f,    // 0.0 to 1.0
    var brake: Float = 0f,       // 0.0 to 1.0
    var handbrake: Boolean = false
)
