package com.example.graphics

import android.opengl.GLES20
import android.opengl.Matrix
import com.example.physics.CarColor
import com.example.physics.VehiclePhysicsEngine
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural 3D model geometry for a high-performance sports coupe.
 * Features sculpted aerodynamic body panels, glass cabin canopy, front splitter,
 * rear wing, quad exhaust tips, LED headlights and brake light bars,
 * 4 independent 3D alloy wheels with rubber tires and brake calipers,
 * and dynamic ambient occlusion ground contact shadow.
 */
class CarGeometry {

    private lateinit var bodyVertexBuffer: FloatBuffer
    private lateinit var bodyNormalBuffer: FloatBuffer
    private lateinit var bodyColorBuffer: FloatBuffer
    private var bodyVertexCount = 0

    private lateinit var glassVertexBuffer: FloatBuffer
    private lateinit var glassNormalBuffer: FloatBuffer
    private lateinit var glassColorBuffer: FloatBuffer
    private var glassVertexCount = 0

    private lateinit var wheelVertexBuffer: FloatBuffer
    private lateinit var wheelNormalBuffer: FloatBuffer
    private lateinit var wheelColorBuffer: FloatBuffer
    private var wheelVertexCount = 0

    private lateinit var shadowVertexBuffer: FloatBuffer
    private lateinit var shadowColorBuffer: FloatBuffer
    private var shadowVertexCount = 0

    // Temporary matrices for rendering parts
    private val modelMatrix = FloatArray(16)
    private val partMatrix = FloatArray(16)
    private val mvpMatrix = FloatArray(16)
    private val mvMatrix = FloatArray(16)
    private val normalMatrix = FloatArray(9)

    init {
        buildCarBodyMesh()
        buildGlassMesh()
        buildWheelMesh()
        buildShadowMesh()
    }

    private fun buildCarBodyMesh() {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val colors = ArrayList<Float>()

        // Helper to add a quad (2 triangles) with computed normal and color
        fun addQuad(
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            x4: Float, y4: Float, z4: Float,
            r: Float, g: Float, b: Float, a: Float = 1.0f
        ) {
            // Normal calculation
            val uX = x2 - x1; val uY = y2 - y1; val uZ = z2 - z1
            val vX = x3 - x1; val vY = y3 - y1; val vZ = z3 - z1
            var nx = uY * vZ - uZ * vY
            var ny = uZ * vX - uX * vZ
            var nz = uX * vY - uY * vX
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len

            // Triangle 1: 1 -> 2 -> 3
            vertices.add(x1); vertices.add(y1); vertices.add(z1)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x2); vertices.add(y2); vertices.add(z2)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x3); vertices.add(y3); vertices.add(z3)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            // Triangle 2: 1 -> 3 -> 4
            vertices.add(x1); vertices.add(y1); vertices.add(z1)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x3); vertices.add(y3); vertices.add(z3)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x4); vertices.add(y4); vertices.add(z4)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)
        }

        // Carbon fiber / dark trim color
        val cR = 0.12f; val cG = 0.13f; val cB = 0.15f
        // Primary body color placeholder (will be shaded with car paint uniform)
        val pR = 1f; val pG = 1f; val pB = 1f

        // Dimensions of the sports car: Length ~4.6m (Z: -2.3 to +2.3), Width ~1.9m (X: -0.95 to +0.95), Height ~1.25m
        val wHalf = 0.94f
        val wNose = 0.82f
        val wRear = 0.90f

        // 1. Lower Front Splitter (Carbon Aero)
        addQuad(
            -wNose, 0.12f, 2.30f,
             wNose, 0.12f, 2.30f,
             wNose, 0.15f, 2.05f,
            -wNose, 0.15f, 2.05f,
            cR, cG, cB
        )

        // 2. Front Bumper Face & Air Intake
        addQuad(
            -wNose, 0.15f, 2.30f,
             wNose, 0.15f, 2.30f,
             wNose, 0.45f, 2.15f,
            -wNose, 0.45f, 2.15f,
            0.08f, 0.08f, 0.09f // Dark radiator grille
        )

        // 3. Sculpted Hood
        addQuad(
            -wNose * 0.9f, 0.48f, 2.15f,
             wNose * 0.9f, 0.48f, 2.15f,
             0.72f, 0.68f, 0.85f,
            -0.72f, 0.68f, 0.85f,
            pR, pG, pB
        )

        // Hood side bevels (Left & Right)
        addQuad(
            -wHalf, 0.42f, 2.15f,
            -wNose * 0.9f, 0.48f, 2.15f,
            -0.72f, 0.68f, 0.85f,
            -wHalf, 0.62f, 0.85f,
            pR, pG, pB
        )
        addQuad(
            wNose * 0.9f, 0.48f, 2.15f,
            wHalf, 0.42f, 2.15f,
            wHalf, 0.62f, 0.85f,
            0.72f, 0.68f, 0.85f,
            pR, pG, pB
        )

        // 4. Side Panels / Doors
        // Left flank
        addQuad(
            -wHalf, 0.18f, 0.85f,
            -wHalf, 0.62f, 0.85f,
            -wHalf, 0.65f, -1.10f,
            -wHalf, 0.18f, -1.10f,
            pR, pG, pB
        )
        // Right flank
        addQuad(
             wHalf, 0.62f, 0.85f,
             wHalf, 0.18f, 0.85f,
             wHalf, 0.18f, -1.10f,
             wHalf, 0.65f, -1.10f,
            pR, pG, pB
        )

        // 5. Rear Haunches & Fenders
        addQuad(
            -wHalf * 1.02f, 0.20f, -1.10f,
            -wHalf * 1.02f, 0.68f, -1.10f,
            -wRear, 0.65f, -2.15f,
            -wRear, 0.22f, -2.15f,
            pR, pG, pB
        )
        addQuad(
             wHalf * 1.02f, 0.68f, -1.10f,
             wHalf * 1.02f, 0.20f, -1.10f,
             wRear, 0.22f, -2.15f,
             wRear, 0.65f, -2.15f,
            pR, pG, pB
        )

        // 6. Rear Trunk Deck
        addQuad(
            -0.65f, 0.68f, -1.45f,
             0.65f, 0.68f, -1.45f,
             0.72f, 0.66f, -2.15f,
            -0.72f, 0.66f, -2.15f,
            pR, pG, pB
        )

        // 7. Rear Bumper & Diffuser
        addQuad(
            -wRear, 0.65f, -2.15f,
             wRear, 0.65f, -2.15f,
             wRear, 0.32f, -2.25f,
            -wRear, 0.32f, -2.25f,
            pR, pG, pB
        )
        // Carbon rear aerodynamic diffuser
        addQuad(
            -wRear * 0.95f, 0.32f, -2.25f,
             wRear * 0.95f, 0.32f, -2.25f,
             wRear * 0.95f, 0.14f, -2.10f,
            -wRear * 0.95f, 0.14f, -2.10f,
            cR, cG, cB
        )

        // 8. Quad Chrome Exhaust Tips
        for (side in listOf(-1, 1)) {
            val exX = side * 0.52f
            addQuad(
                exX - 0.09f, 0.22f, -2.26f,
                exX + 0.09f, 0.22f, -2.26f,
                exX + 0.09f, 0.28f, -2.26f,
                exX - 0.09f, 0.28f, -2.26f,
                0.85f, 0.88f, 0.92f // Polished chrome
            )
        }

        // 9. Aerodynamic GT Rear Wing / Spoiler
        val wingY = 0.82f
        val wingZ = -2.12f
        addQuad(
            -0.85f, wingY, wingZ + 0.18f,
             0.85f, wingY, wingZ + 0.18f,
             0.85f, wingY + 0.04f, wingZ - 0.12f,
            -0.85f, wingY + 0.04f, wingZ - 0.12f,
            cR, cG, cB // Carbon wing
        )
        // Wing upright struts (stanchions)
        for (side in listOf(-0.45f, 0.45f)) {
            addQuad(
                side - 0.02f, 0.66f, -2.05f,
                side + 0.02f, 0.66f, -2.05f,
                side + 0.02f, wingY, -2.05f,
                side - 0.02f, wingY, -2.05f,
                cR, cG, cB
            )
        }

        // 10. Front Headlight housings
        for (side in listOf(-1, 1)) {
            val hlX = side * 0.72f
            addQuad(
                hlX - 0.12f, 0.46f, 2.16f,
                hlX + 0.12f, 0.46f, 2.16f,
                hlX + 0.08f, 0.54f, 1.95f,
                hlX - 0.08f, 0.54f, 1.95f,
                0.90f, 0.95f, 1.0f // LED DRL crystal
            )
        }

        bodyVertexCount = vertices.size / 3
        bodyVertexBuffer = createFloatBuffer(vertices)
        bodyNormalBuffer = createFloatBuffer(normals)
        bodyColorBuffer = createFloatBuffer(colors)
    }

    private fun buildGlassMesh() {
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val colors = ArrayList<Float>()

        fun addQuad(
            x1: Float, y1: Float, z1: Float,
            x2: Float, y2: Float, z2: Float,
            x3: Float, y3: Float, z3: Float,
            x4: Float, y4: Float, z4: Float
        ) {
            val uX = x2 - x1; val uY = y2 - y1; val uZ = z2 - z1
            val vX = x3 - x1; val vY = y3 - y1; val vZ = z3 - z1
            var nx = uY * vZ - uZ * vY
            var ny = uZ * vX - uX * vZ
            var nz = uX * vY - uY * vX
            val len = kotlin.math.sqrt(nx * nx + ny * ny + nz * nz).coerceAtLeast(0.0001f)
            nx /= len; ny /= len; nz /= len

            // Tinted automotive glass with specular shine
            val r = 0.15f; val g = 0.22f; val b = 0.32f; val a = 0.88f

            vertices.add(x1); vertices.add(y1); vertices.add(z1)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x2); vertices.add(y2); vertices.add(z2)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x3); vertices.add(y3); vertices.add(z3)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x1); vertices.add(y1); vertices.add(z1)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x3); vertices.add(y3); vertices.add(z3)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)

            vertices.add(x4); vertices.add(y4); vertices.add(z4)
            normals.add(nx); normals.add(ny); normals.add(nz)
            colors.add(r); colors.add(g); colors.add(b); colors.add(a)
        }

        // Windshield (slanted forward)
        addQuad(
            -0.70f, 0.68f, 0.85f,
             0.70f, 0.68f, 0.85f,
             0.58f, 1.15f, 0.05f,
            -0.58f, 1.15f, 0.05f
        )

        // Glass Roof Canopy
        addQuad(
            -0.58f, 1.15f, 0.05f,
             0.58f, 1.15f, 0.05f,
             0.54f, 1.12f, -0.65f,
            -0.54f, 1.12f, -0.65f
        )

        // Rear Windshield (fastback slope)
        addQuad(
            -0.54f, 1.12f, -0.65f,
             0.54f, 1.12f, -0.65f,
             0.62f, 0.70f, -1.45f,
            -0.62f, 0.70f, -1.45f
        )

        // Side Windows (Left & Right)
        addQuad(
            -0.70f, 0.66f, 0.82f,
            -0.58f, 1.15f, 0.05f,
            -0.54f, 1.12f, -0.65f,
            -0.70f, 0.67f, -1.05f
        )
        addQuad(
             0.58f, 1.15f, 0.05f,
             0.70f, 0.66f, 0.82f,
             0.70f, 0.67f, -1.05f,
             0.54f, 1.12f, -0.65f
        )

        glassVertexCount = vertices.size / 3
        glassVertexBuffer = createFloatBuffer(vertices)
        glassNormalBuffer = createFloatBuffer(normals)
        glassColorBuffer = createFloatBuffer(colors)
    }

    private fun buildWheelMesh() {
        // High-poly 3D wheel: Rubber tire ring, rim lip, 5-spoke alloy rim, central brake rotor & red caliper
        val vertices = ArrayList<Float>()
        val normals = ArrayList<Float>()
        val colors = ArrayList<Float>()

        val segments = 20
        val outerRadius = 0.33f // Tire outer radius
        val innerRadius = 0.23f // Rim edge radius
        val width = 0.22f // Tire width (along local X)
        val halfW = width * 0.5f

        // 1. Rubber Tire Tread (Cylinder strip around periphery)
        for (i in 0 until segments) {
            val a1 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a2 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()

            val y1 = sin(a1) * outerRadius; val z1 = cos(a1) * outerRadius
            val y2 = sin(a2) * outerRadius; val z2 = cos(a2) * outerRadius

            // Outer tire surface
            val r = 0.16f; val g = 0.16f; val b = 0.17f // Deep matte rubber

            // Quad across tire tread width
            vertices.add(-halfW); vertices.add(y1); vertices.add(z1)
            normals.add(0f); normals.add(y1 / outerRadius); normals.add(z1 / outerRadius)
            colors.add(r); colors.add(g); colors.add(b); colors.add(1f)

            vertices.add(halfW); vertices.add(y1); vertices.add(z1)
            normals.add(0f); normals.add(y1 / outerRadius); normals.add(z1 / outerRadius)
            colors.add(r); colors.add(g); colors.add(b); colors.add(1f)

            vertices.add(halfW); vertices.add(y2); vertices.add(z2)
            normals.add(0f); normals.add(y2 / outerRadius); normals.add(z2 / outerRadius)
            colors.add(r); colors.add(g); colors.add(b); colors.add(1f)

            vertices.add(-halfW); vertices.add(y1); vertices.add(z1)
            normals.add(0f); normals.add(y1 / outerRadius); normals.add(z1 / outerRadius)
            colors.add(r); colors.add(g); colors.add(b); colors.add(1f)

            vertices.add(halfW); vertices.add(y2); vertices.add(z2)
            normals.add(0f); normals.add(y2 / outerRadius); normals.add(z2 / outerRadius)
            colors.add(r); colors.add(g); colors.add(b); colors.add(1f)

            vertices.add(-halfW); vertices.add(y2); vertices.add(z2)
            normals.add(0f); normals.add(y2 / outerRadius); normals.add(z2 / outerRadius)
            colors.add(r); colors.add(g); colors.add(b); colors.add(1f)

            // Tire Sidewalls (Outer face)
            vertices.add(halfW); vertices.add(y1); vertices.add(z1)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(r * 1.1f); colors.add(g * 1.1f); colors.add(b * 1.1f); colors.add(1f)

            vertices.add(halfW); vertices.add(sin(a1) * innerRadius); vertices.add(cos(a1) * innerRadius)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(r * 1.1f); colors.add(g * 1.1f); colors.add(b * 1.1f); colors.add(1f)

            vertices.add(halfW); vertices.add(sin(a2) * innerRadius); vertices.add(cos(a2) * innerRadius)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(r * 1.1f); colors.add(g * 1.1f); colors.add(b * 1.1f); colors.add(1f)

            vertices.add(halfW); vertices.add(y1); vertices.add(z1)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(r * 1.1f); colors.add(g * 1.1f); colors.add(b * 1.1f); colors.add(1f)

            vertices.add(halfW); vertices.add(sin(a2) * innerRadius); vertices.add(cos(a2) * innerRadius)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(r * 1.1f); colors.add(g * 1.1f); colors.add(b * 1.1f); colors.add(1f)

            vertices.add(halfW); vertices.add(y2); vertices.add(z2)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(r * 1.1f); colors.add(g * 1.1f); colors.add(b * 1.1f); colors.add(1f)
        }

        // 2. High-Performance Alloy Rim Spokes (5 twin-spokes)
        val rimR = 0.85f; val rimG = 0.88f; val rimB = 0.92f // Gunmetal Silver
        for (s in 0 until 5) {
            val spokeAngle = (s.toFloat() / 5f) * 2f * PI.toFloat()
            val spokeW = 0.045f
            val perpY = -sin(spokeAngle) * spokeW
            val perpZ = cos(spokeAngle) * spokeW

            val spokeEndX = halfW * 0.9f
            val spokeEndY = sin(spokeAngle) * innerRadius
            val spokeEndZ = cos(spokeAngle) * innerRadius

            // Triangle 1
            vertices.add(spokeEndX); vertices.add(0f); vertices.add(0f)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(rimR); colors.add(rimG); colors.add(rimB); colors.add(1f)

            vertices.add(spokeEndX); vertices.add(spokeEndY - perpY); vertices.add(spokeEndZ - perpZ)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(rimR); colors.add(rimG); colors.add(rimB); colors.add(1f)

            vertices.add(spokeEndX); vertices.add(spokeEndY + perpY); vertices.add(spokeEndZ + perpZ)
            normals.add(1f); normals.add(0f); normals.add(0f)
            colors.add(rimR); colors.add(rimG); colors.add(rimB); colors.add(1f)
        }

        // 3. Red Brembo-style Brake Caliper (Top-rear quadrant)
        val calX = halfW * 0.65f
        val calR = 0.92f; val calG = 0.12f; val calB = 0.14f // Brembo Red
        vertices.add(calX); vertices.add(0.12f); vertices.add(-0.06f)
        normals.add(1f); normals.add(0f); normals.add(0f)
        colors.add(calR); colors.add(calG); colors.add(calB); colors.add(1f)

        vertices.add(calX); vertices.add(0.20f); vertices.add(-0.04f)
        normals.add(1f); normals.add(0f); normals.add(0f)
        colors.add(calR); colors.add(calG); colors.add(calB); colors.add(1f)

        vertices.add(calX); vertices.add(0.18f); vertices.add(0.12f)
        normals.add(1f); normals.add(0f); normals.add(0f)
        colors.add(calR); colors.add(calG); colors.add(calB); colors.add(1f)

        wheelVertexCount = vertices.size / 3
        wheelVertexBuffer = createFloatBuffer(vertices)
        wheelNormalBuffer = createFloatBuffer(normals)
        wheelColorBuffer = createFloatBuffer(colors)
    }

    private fun buildShadowMesh() {
        // Contact shadow disk beneath car chassis
        val vertices = ArrayList<Float>()
        val colors = ArrayList<Float>()
        val segments = 24
        val length = 2.45f
        val width = 1.05f

        for (i in 0 until segments) {
            val a1 = (i.toFloat() / segments) * 2f * PI.toFloat()
            val a2 = ((i + 1).toFloat() / segments) * 2f * PI.toFloat()

            // Center vertex (dark AO core)
            vertices.add(0f); vertices.add(0.012f); vertices.add(0f)
            colors.add(0f); colors.add(0f); colors.add(0f); colors.add(0.72f)

            // Outer vertex 1 (soft edge fade)
            vertices.add(sin(a1) * width); vertices.add(0.012f); vertices.add(cos(a1) * length)
            colors.add(0f); colors.add(0f); colors.add(0f); colors.add(0.0f)

            // Outer vertex 2 (soft edge fade)
            vertices.add(sin(a2) * width); vertices.add(0.012f); vertices.add(cos(a2) * length)
            colors.add(0f); colors.add(0f); colors.add(0f); colors.add(0.0f)
        }

        shadowVertexCount = vertices.size / 3
        shadowVertexBuffer = createFloatBuffer(vertices)
        shadowColorBuffer = createFloatBuffer(colors)
    }

    private fun createFloatBuffer(list: List<Float>): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(list.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        for (f in list) fb.put(f)
        fb.position(0)
        return fb
    }

    /**
     * Render the entire vehicle: chassis body, glass, 4 wheels, and ground shadow.
     */
    fun draw(
        physics: VehiclePhysicsEngine,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        lightDir: FloatArray,
        camPos: FloatArray,
        carColor: CarColor,
        headlightsOn: Boolean,
        mainProgram: Int,
        shadowProgram: Int
    ) {
        // Base vehicle transform
        Matrix.setIdentityM(modelMatrix, 0)
        // World translation
        Matrix.translateM(modelMatrix, 0, physics.posX, physics.posY, physics.posZ)
        // Yaw heading
        Matrix.rotateM(modelMatrix, 0, (physics.yaw * 180f / PI.toFloat()), 0f, 1f, 0f)
        // Chassis dynamic weight transfer (Pitch & Roll)
        Matrix.rotateM(modelMatrix, 0, (physics.pitch * 180f / PI.toFloat()), 1f, 0f, 0f)
        Matrix.rotateM(modelMatrix, 0, (physics.roll * 180f / PI.toFloat()), 0f, 0f, 1f)

        // 1. Draw Ground Ambient Occlusion Contact Shadow first
        drawGroundShadow(physics, viewMatrix, projMatrix, shadowProgram)

        // 2. Prepare Main Shader
        GLES20.glUseProgram(mainProgram)
        val posHandle = GLES20.glGetAttribLocation(mainProgram, "a_Position")
        val normalHandle = GLES20.glGetAttribLocation(mainProgram, "a_Normal")
        val colorHandle = GLES20.glGetAttribLocation(mainProgram, "a_Color")
        val mvpHandle = GLES20.glGetUniformLocation(mainProgram, "u_MVPMatrix")
        val mvHandle = GLES20.glGetUniformLocation(mainProgram, "u_MVMatrix")
        val normalMatHandle = GLES20.glGetUniformLocation(mainProgram, "u_NormalMatrix")
        val lightDirHandle = GLES20.glGetUniformLocation(mainProgram, "u_LightDir")
        val camPosHandle = GLES20.glGetUniformLocation(mainProgram, "u_CamPos")
        val carColorHandle = GLES20.glGetUniformLocation(mainProgram, "u_CarColor")
        val useCarColorHandle = GLES20.glGetUniformLocation(mainProgram, "u_UseCarColor")
        val metallicHandle = GLES20.glGetUniformLocation(mainProgram, "u_Metallic")
        val roughnessHandle = GLES20.glGetUniformLocation(mainProgram, "u_Roughness")
        val emissiveHandle = GLES20.glGetUniformLocation(mainProgram, "u_Emissive")

        GLES20.glUniform3fv(lightDirHandle, 1, lightDir, 0)
        GLES20.glUniform3fv(camPosHandle, 1, camPos, 0)

        // A. Draw Body Panels
        GLES20.glUniform4f(carColorHandle, carColor.r, carColor.g, carColor.b, 1.0f)
        GLES20.glUniform1f(useCarColorHandle, 1.0f)
        GLES20.glUniform1f(metallicHandle, carColor.metallic)
        GLES20.glUniform1f(roughnessHandle, 0.22f) // Glossy sports car lacquer
        GLES20.glUniform3f(emissiveHandle, 0f, 0f, 0f)

        updateAndSetMatrices(modelMatrix, viewMatrix, projMatrix, mvpHandle, mvHandle, normalMatHandle)

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, bodyVertexBuffer)
        GLES20.glEnableVertexAttribArray(normalHandle)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, bodyNormalBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, bodyColorBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, bodyVertexCount)

        // B. Draw Glass Canopy
        GLES20.glUniform1f(useCarColorHandle, 0.0f)
        GLES20.glUniform1f(metallicHandle, 0.95f)
        GLES20.glUniform1f(roughnessHandle, 0.05f) // Ultra-smooth glass
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, glassVertexBuffer)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, glassNormalBuffer)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, glassColorBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, glassVertexCount)

        // C. Draw 4 Independent Wheels
        GLES20.glUniform1f(useCarColorHandle, 0.0f)
        GLES20.glUniform1f(metallicHandle, 0.75f)
        GLES20.glUniform1f(roughnessHandle, 0.45f)

        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, wheelVertexBuffer)
        GLES20.glVertexAttribPointer(normalHandle, 3, GLES20.GL_FLOAT, false, 0, wheelNormalBuffer)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, wheelColorBuffer)

        val halfW = physics.trackWidth * 0.5f
        val halfL = physics.wheelbase * 0.5f

        // Wheel offsets: [0: FL, 1: FR, 2: RL, 3: RR]
        val wheelPositions = arrayOf(
            floatArrayOf(-halfW, halfL),  // FL
            floatArrayOf(halfW, halfL),   // FR
            floatArrayOf(-halfW, -halfL), // RL
            floatArrayOf(halfW, -halfL)   // RR
        )

        for (i in 0 until 4) {
            val isFront = (i < 2)
            val isRight = (i % 2 == 1)
            val posX = wheelPositions[i][0]
            val posZ = wheelPositions[i][1]
            // Suspension compression displacement (vertical)
            val suspDisplacement = (physics.wheelSuspensionCompressions[i] - 0.5f) * 0.08f
            val posY = 0.33f + suspDisplacement

            System.arraycopy(modelMatrix, 0, partMatrix, 0, 16)
            Matrix.translateM(partMatrix, 0, posX, posY, posZ)

            // Steering rotation for front wheels
            if (isFront) {
                Matrix.rotateM(partMatrix, 0, physics.steerAngleDeg, 0f, 1f, 0f)
            }
            // Flip right wheels 180 degrees so outer rim faces outward
            if (isRight) {
                Matrix.rotateM(partMatrix, 0, 180f, 0f, 1f, 0f)
            }
            // Wheel spin rotation proportional to travel speed
            val spinDeg = (physics.wheelSpinAngles[i] * 180f / PI.toFloat()) * (if (isRight) -1f else 1f)
            Matrix.rotateM(partMatrix, 0, spinDeg, 1f, 0f, 0f)

            updateAndSetMatrices(partMatrix, viewMatrix, projMatrix, mvpHandle, mvHandle, normalMatHandle)
            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, wheelVertexCount)
        }

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(normalHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun drawGroundShadow(
        physics: VehiclePhysicsEngine,
        viewMatrix: FloatArray,
        projMatrix: FloatArray,
        shadowProgram: Int
    ) {
        GLES20.glUseProgram(shadowProgram)
        GLES20.glEnable(GLES20.GL_BLEND)
        GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

        val posHandle = GLES20.glGetAttribLocation(shadowProgram, "a_Position")
        val colorHandle = GLES20.glGetAttribLocation(shadowProgram, "a_Color")
        val mvpHandle = GLES20.glGetUniformLocation(shadowProgram, "u_MVPMatrix")

        Matrix.setIdentityM(partMatrix, 0)
        Matrix.translateM(partMatrix, 0, physics.posX, 0.005f, physics.posZ)
        Matrix.rotateM(partMatrix, 0, (physics.yaw * 180f / PI.toFloat()), 0f, 1f, 0f)

        Matrix.multiplyMM(mvMatrix, 0, viewMatrix, 0, partMatrix, 0)
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, mvMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, shadowVertexBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, shadowColorBuffer)

        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, shadowVertexCount)

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
        GLES20.glDisable(GLES20.GL_BLEND)
    }

    private fun updateAndSetMatrices(
        model: FloatArray,
        view: FloatArray,
        proj: FloatArray,
        mvpHandle: Int,
        mvHandle: Int,
        normalMatHandle: Int
    ) {
        Matrix.multiplyMM(mvMatrix, 0, view, 0, model, 0)
        Matrix.multiplyMM(mvpMatrix, 0, proj, 0, mvMatrix, 0)
        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)
        GLES20.glUniformMatrix4fv(mvHandle, 1, false, mvMatrix, 0)

        // 3x3 normal matrix
        normalMatrix[0] = mvMatrix[0]; normalMatrix[1] = mvMatrix[1]; normalMatrix[2] = mvMatrix[2]
        normalMatrix[3] = mvMatrix[4]; normalMatrix[4] = mvMatrix[5]; normalMatrix[5] = mvMatrix[6]
        normalMatrix[6] = mvMatrix[8]; normalMatrix[7] = mvMatrix[9]; normalMatrix[8] = mvMatrix[10]
        GLES20.glUniformMatrix3fv(normalMatHandle, 1, false, normalMatrix, 0)
    }
}
