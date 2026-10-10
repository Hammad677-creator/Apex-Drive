package com.example

import com.example.physics.DriveMode
import com.example.physics.Gear
import com.example.physics.VehiclePhysicsEngine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExampleUnitTest {

    @Test
    fun testVehiclePhysicsInitialization() {
        val physics = VehiclePhysicsEngine()
        assertEquals(0f, physics.posX, 0.001f)
        assertEquals(0f, physics.posZ, 0.001f)
        assertEquals(0f, physics.speedMps, 0.001f)
        assertEquals(Gear.FIRST, physics.currentGear)
        assertTrue(physics.engineRpm >= 800f)
    }

    @Test
    fun testVehicleAccelerationAndSpeed() {
        val physics = VehiclePhysicsEngine()
        physics.inputs.throttle = 1.0f

        // Simulate 2 seconds of acceleration
        val dt = 1f / 60f
        for (i in 0 until 120) {
            physics.update(dt)
        }

        assertTrue("Vehicle should gain speed upon full throttle", physics.speedMps > 5.0f)
        assertTrue("Engine RPM should increase", physics.engineRpm > 1500f)

        val telemetry = physics.getTelemetry()
        assertNotNull(telemetry)
        assertTrue("Speed in KM/H should be greater than zero", telemetry.speedKmh > 10f)
    }

    @Test
    fun testBrakingDeceleration() {
        val physics = VehiclePhysicsEngine()
        physics.speedMps = 25f // moving at 90 km/h
        physics.inputs.brake = 1.0f

        val initialSpeed = physics.speedMps
        // Simulate 0.5s of hard braking
        for (i in 0 until 30) {
            physics.update(1f / 60f)
        }

        assertTrue("Vehicle should decelerate under hard braking", physics.speedMps < initialSpeed)
    }

    @Test
    fun testAutomaticGearShifting() {
        val physics = VehiclePhysicsEngine()
        physics.driveMode = DriveMode.COMFORT
        physics.inputs.throttle = 1.0f

        // Accelerate through gears
        for (i in 0 until 300) {
            physics.update(1f / 60f)
        }

        assertTrue("Vehicle should upshift from first gear", physics.currentGear.index >= 2)
    }
}
