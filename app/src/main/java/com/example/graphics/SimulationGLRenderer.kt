package com.example.graphics

import android.opengl.GLES20
import android.opengl.GLSurfaceView
import com.example.physics.CarColor
import com.example.physics.VehiclePhysicsEngine
import com.example.physics.VehicleTelemetry
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10

/**
 * High-performance OpenGL ES 2.0 / 3.0 renderer for 3D car driving simulation.
 * Manages fixed-timestep physics updates, PBR-style vehicle rendering, track drawing,
 * and dynamic camera interpolation at a locked 60 FPS.
 */
class SimulationGLRenderer(
    val physics: VehiclePhysicsEngine,
    val camera: FollowCamera
) : GLSurfaceView.Renderer {

    private var mainProgram = 0
    private var groundProgram = 0
    private var blendProgram = 0

    private var carGeometry: CarGeometry? = null
    private var trackGeometry: TrackGeometry? = null

    var carColor: CarColor = CarColor.ROSSO_CORSA
    var headlightsOn: Boolean = true

    // Overcast lighting parameters
    private val lightDir = floatArrayOf(-0.35f, 0.85f, -0.40f) // Soft overcast light from above
    private val camPos = FloatArray(3)

    private var lastTimeNanos = 0L
    private var physicsAccumulator = 0f
    private val fixedDeltaTime = 1f / 60f

    var onTelemetryUpdated: ((VehicleTelemetry) -> Unit)? = null

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        // Overcast sky atmospheric clear color
        GLES20.glClearColor(0.72f, 0.78f, 0.86f, 1.0f)
        GLES20.glEnable(GLES20.GL_DEPTH_TEST)
        GLES20.glDepthFunc(GLES20.GL_LEQUAL)
        GLES20.glEnable(GLES20.GL_CULL_FACE)
        GLES20.glCullFace(GLES20.GL_BACK)

        // Compile shaders
        mainProgram = Shaders.createProgram(Shaders.MAIN_VERTEX_SHADER, Shaders.MAIN_FRAGMENT_SHADER)
        groundProgram = Shaders.createProgram(Shaders.GROUND_VERTEX_SHADER, Shaders.GROUND_FRAGMENT_SHADER)
        blendProgram = Shaders.createProgram(Shaders.BLEND_VERTEX_SHADER, Shaders.BLEND_FRAGMENT_SHADER)

        // Initialize 3D models
        carGeometry = CarGeometry()
        val track = TrackGeometry()
        trackGeometry = track

        // Connect tire skidmark callback from physics to track renderer
        physics.onSkidmarkCallback = { wheelIdx, x, z, intensity ->
            track.recordSkidmark(wheelIdx, x, z, intensity)
        }

        camera.init(physics)
        lastTimeNanos = System.nanoTime()
    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
        camera.updateProjection(width, height)
    }

    override fun onDrawFrame(gl: GL10?) {
        val currentTimeNanos = System.nanoTime()
        val frameTimeSec = if (lastTimeNanos > 0L) {
            ((currentTimeNanos - lastTimeNanos) / 1_000_000_000f).coerceIn(0.001f, 0.05f)
        } else fixedDeltaTime
        lastTimeNanos = currentTimeNanos

        // Fixed-timestep physics update to ensure numerical stability
        physicsAccumulator += frameTimeSec
        var steps = 0
        while (physicsAccumulator >= fixedDeltaTime && steps < 3) {
            physics.update(fixedDeltaTime)
            physicsAccumulator -= fixedDeltaTime
            steps++
        }
        if (physicsAccumulator >= fixedDeltaTime) {
            physicsAccumulator = 0f
        }

        // Update follow camera
        camera.update(physics, frameTimeSec)
        camPos[0] = camera.eyeX
        camPos[1] = camera.eyeY
        camPos[2] = camera.eyeZ

        // Dispatch telemetry for HUD
        onTelemetryUpdated?.invoke(physics.getTelemetry())

        // Render 3D Scene
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        // 1. Draw Track, Curbs, Guardrails & Horizon Mountains
        trackGeometry?.draw(camera.viewMatrix, camera.projMatrix, groundProgram, blendProgram)

        // 2. Draw High-Fidelity Car with Metallic Reflections & Contact Shadow
        carGeometry?.draw(
            physics = physics,
            viewMatrix = camera.viewMatrix,
            projMatrix = camera.projMatrix,
            lightDir = lightDir,
            camPos = camPos,
            carColor = carColor,
            headlightsOn = headlightsOn,
            mainProgram = mainProgram,
            shadowProgram = blendProgram
        )
    }

    fun handleOrbitDrag(deltaX: Float, deltaY: Float) {
        camera.orbitYawDeg = (camera.orbitYawDeg - deltaX * 0.4f) % 360f
        camera.orbitPitchDeg = (camera.orbitPitchDeg + deltaY * 0.3f).coerceIn(5f, 75f)
    }
}
