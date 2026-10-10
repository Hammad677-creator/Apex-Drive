package com.example.graphics

import android.opengl.GLES20
import android.opengl.Matrix
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.nio.FloatBuffer
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

/**
 * Procedural asphalt test track circuit with curbs, guardrails,
 * surrounding environment, distance mountains, and dynamic tire skidmark trails.
 */
class TrackGeometry {

    private lateinit var trackVertexBuffer: FloatBuffer
    private lateinit var trackColorBuffer: FloatBuffer
    private var trackVertexCount = 0

    private lateinit var barrierVertexBuffer: FloatBuffer
    private lateinit var barrierColorBuffer: FloatBuffer
    private var barrierVertexCount = 0

    private lateinit var sceneryVertexBuffer: FloatBuffer
    private lateinit var sceneryColorBuffer: FloatBuffer
    private var sceneryVertexCount = 0

    // Dynamic Skidmarks buffer
    private val maxSkidSegments = 1200
    private val skidFloatsPerVertex = 7 // X, Y, Z, R, G, B, A
    private val skidVerticesPerSegment = 6 // 2 triangles
    private val skidBufferSize = maxSkidSegments * skidVerticesPerSegment * skidFloatsPerVertex
    private val skidRawBuffer = ByteBuffer.allocateDirect(skidBufferSize * 4)
        .order(ByteOrder.nativeOrder()).asFloatBuffer()
    private var activeSkidVertices = 0

    private val prevWheelPos = Array(4) { FloatArray(2) }
    private val hasPrevWheelPos = BooleanArray(4) { false }

    private val mvpMatrix = FloatArray(16)
    private val identityModel = FloatArray(16).apply { Matrix.setIdentityM(this, 0) }

    init {
        buildCircuitMesh()
        buildSceneryMesh()
    }

    private fun buildCircuitMesh() {
        val vertices = ArrayList<Float>()
        val colors = ArrayList<Float>()

        // Circuit spline path points: a challenging dynamic test track
        // with high-speed straight, banked hairpin, esses, and wide sweepers
        val numPathPoints = 120
        val trackRadiusX = 140f
        val trackRadiusZ = 85f
        val trackWidth = 14f // Wide 14-meter asphalt test track

        val pathX = FloatArray(numPathPoints)
        val pathZ = FloatArray(numPathPoints)

        for (i in 0 until numPathPoints) {
            val t = (i.toFloat() / numPathPoints) * 2f * PI.toFloat()
            // Organic test track shape (kidney / formula test loop)
            val rModifier = 1.0f + 0.28f * sin(2f * t) - 0.18f * cos(3f * t)
            pathX[i] = sin(t) * trackRadiusX * rModifier
            pathZ[i] = cos(t) * trackRadiusZ * rModifier
        }

        // Build continuous track ribbon with road markings and curbs
        for (i in 0 until numPathPoints) {
            val iNext = (i + 1) % numPathPoints
            val p0X = pathX[i]; val p0Z = pathZ[i]
            val p1X = pathX[iNext]; val p1Z = pathZ[iNext]

            // Tangent & Normal vectors
            val dx = p1X - p0X; val dz = p1Z - p0Z
            val len = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            val nx = -dz / len; val nz = dx / len

            val halfW = trackWidth * 0.5f

            // Asphalt coordinates:
            val left0X = p0X - nx * halfW; val left0Z = p0Z - nz * halfW
            val right0X = p0X + nx * halfW; val right0Z = p0Z + nz * halfW

            val left1X = p1X - nx * halfW; val left1Z = p1Z - nz * halfW
            val right1X = p1X + nx * halfW; val right1Z = p1Z + nz * halfW

            // Main Asphalt surface: dark textured asphalt #1E2127
            val aspR = 0.14f; val aspG = 0.15f; val aspB = 0.17f
            addQuad(vertices, colors,
                left0X, 0.0f, left0Z,
                right0X, 0.0f, right0Z,
                right1X, 0.0f, right1Z,
                left1X, 0.0f, left1Z,
                aspR, aspG, aspB
            )

            // White Edge Boundaries (0.3m wide)
            val ew = 0.35f
            addQuad(vertices, colors,
                left0X, 0.002f, left0Z,
                left0X + nx * ew, 0.002f, left0Z + nz * ew,
                left1X + nx * ew, 0.002f, left1Z + nz * ew,
                left1X, 0.002f, left1Z,
                0.95f, 0.96f, 0.98f
            )
            addQuad(vertices, colors,
                right0X - nx * ew, 0.002f, right0Z - nz * ew,
                right0X, 0.002f, right0Z,
                right1X, 0.002f, right1Z,
                right1X - nx * ew, 0.002f, right1Z - nz * ew,
                0.95f, 0.96f, 0.98f
            )

            // Dashed Center Divider Line (every alternating segment)
            if (i % 2 == 0) {
                val cw = 0.22f
                addQuad(vertices, colors,
                    p0X - nx * cw, 0.002f, p0Z - nz * cw,
                    p0X + nx * cw, 0.002f, p0Z + nz * cw,
                    p1X + nx * cw, 0.002f, p1Z + nz * cw,
                    p1X - nx * cw, 0.002f, p1Z - nz * cw,
                    0.92f, 0.94f, 0.96f
                )
            }

            // Alternating Red & White Curbs (Rumble Strips on corners)
            val isApex = (i in 15..35) || (i in 65..85) || (i in 100..115)
            if (isApex) {
                val curbW = 1.1f
                val isRed = (i % 2 == 0)
                val cR = if (isRed) 0.90f else 0.95f
                val cG = if (isRed) 0.12f else 0.95f
                val cB = if (isRed) 0.15f else 0.95f

                // Outer and inner curbs
                addQuad(vertices, colors,
                    left0X - nx * curbW, 0.04f, left0Z - nz * curbW,
                    left0X, 0.02f, left0Z,
                    left1X, 0.02f, left1Z,
                    left1X - nx * curbW, 0.04f, left1Z - nz * curbW,
                    cR, cG, cB
                )
                addQuad(vertices, colors,
                    right0X, 0.02f, right0Z,
                    right0X + nx * curbW, 0.04f, right0Z + nz * curbW,
                    right1X + nx * curbW, 0.04f, right1Z + nz * curbW,
                    right1X, 0.02f, right1Z,
                    cR, cG, cB
                )
            }
        }

        // Start/Finish Checkered Line across the straight
        val pStart = 0
        val pStartX = pathX[pStart]; val pStartZ = pathZ[pStart]
        val pNextX = pathX[1]; val pNextZ = pathZ[1]
        val tdx = pNextX - pStartX; val tdz = pNextZ - pStartZ
        val tlen = kotlin.math.sqrt(tdx * tdx + tdz * tdz)
        val tnx = -tdz / tlen; val tnz = tdx / tlen

        for (grid in 0 until 14) {
            val gW = 1.0f
            val gOffset = -trackWidth * 0.5f + grid * gW
            val isCheckeredWhite = (grid % 2 == 0)
            val clr = if (isCheckeredWhite) 0.95f else 0.1f
            addQuad(vertices, colors,
                pStartX + tnx * gOffset, 0.003f, pStartZ + tnz * gOffset,
                pStartX + tnx * (gOffset + gW), 0.003f, pStartZ + tnz * (gOffset + gW),
                pStartX + tnx * (gOffset + gW) + tdx * 0.2f, 0.003f, pStartZ + tnz * (gOffset + gW) + tdz * 0.2f,
                pStartX + tnx * gOffset + tdx * 0.2f, 0.003f, pStartZ + tnz * gOffset + tdz * 0.2f,
                clr, clr, clr
            )
        }

        trackVertexCount = vertices.size / 3
        trackVertexBuffer = createFloatBuffer(vertices)
        trackColorBuffer = createFloatBuffer(colors)

        // Build Armco Metal Barriers along track perimeter
        buildBarriers(pathX, pathZ, trackWidth)
    }

    private fun buildBarriers(pathX: FloatArray, pathZ: FloatArray, trackWidth: Float) {
        val vertices = ArrayList<Float>()
        val colors = ArrayList<Float>()
        val numPoints = pathX.size

        for (i in 0 until numPoints) {
            val iNext = (i + 1) % numPoints
            val p0X = pathX[i]; val p0Z = pathZ[i]
            val p1X = pathX[iNext]; val p1Z = pathZ[iNext]

            val dx = p1X - p0X; val dz = p1Z - p0Z
            val len = kotlin.math.sqrt(dx * dx + dz * dz).coerceAtLeast(0.001f)
            val nx = -dz / len; val nz = dx / len

            val barrierOffset = trackWidth * 0.5f + 3.2f // Placed 3.2m beyond track edge
            val b0X = p0X + nx * barrierOffset; val b0Z = p0Z + nz * barrierOffset
            val b1X = p1X + nx * barrierOffset; val b1Z = p1Z + nz * barrierOffset

            // Galvanized corrugated steel barrier
            val steelR = 0.72f; val steelG = 0.76f; val steelB = 0.82f
            addQuad(vertices, colors,
                b0X, 0.25f, b0Z,
                b1X, 0.25f, b1Z,
                b1X, 0.85f, b1Z,
                b0X, 0.85f, b0Z,
                steelR, steelG, steelB
            )
        }

        barrierVertexCount = vertices.size / 3
        barrierVertexBuffer = createFloatBuffer(vertices)
        barrierColorBuffer = createFloatBuffer(colors)
    }

    private fun buildSceneryMesh() {
        val vertices = ArrayList<Float>()
        val colors = ArrayList<Float>()

        // 1. Surrounding Grass Runoff Terrain Ground Plane
        val groundR = 0.22f; val groundG = 0.32f; val groundB = 0.24f
        val groundSize = 350f
        addQuad(vertices, colors,
            -groundSize, -0.01f, -groundSize,
             groundSize, -0.01f, -groundSize,
             groundSize, -0.01f,  groundSize,
            -groundSize, -0.01f,  groundSize,
            groundR, groundG, groundB
        )

        // 2. Distant Horizon Mountain Silhouette under overcast sky
        val numMtn = 36
        val mtnRadius = 380f
        for (i in 0 until numMtn) {
            val a1 = (i.toFloat() / numMtn) * 2f * PI.toFloat()
            val a2 = ((i + 1).toFloat() / numMtn) * 2f * PI.toFloat()

            val x1 = sin(a1) * mtnRadius; val z1 = cos(a1) * mtnRadius
            val x2 = sin(a2) * mtnRadius; val z2 = cos(a2) * mtnRadius

            val h1 = 35f + 25f * sin(3f * a1) + 15f * cos(5f * a1)
            val h2 = 35f + 25f * sin(3f * a2) + 15f * cos(5f * a2)

            // Atmospheric hazy mountain colors (cool misty blue-slate)
            val mtnR = 0.36f; val mtnG = 0.42f; val mtnB = 0.50f
            val baseR = 0.28f; val baseG = 0.34f; val baseB = 0.38f

            // Triangle 1
            vertices.add(x1); vertices.add(0f); vertices.add(z1)
            colors.add(baseR); colors.add(baseG); colors.add(baseB); colors.add(1f)
            vertices.add(x2); vertices.add(0f); vertices.add(z2)
            colors.add(baseR); colors.add(baseG); colors.add(baseB); colors.add(1f)
            vertices.add(x2); vertices.add(h2); vertices.add(z2)
            colors.add(mtnR); colors.add(mtnG); colors.add(mtnB); colors.add(1f)

            // Triangle 2
            vertices.add(x1); vertices.add(0f); vertices.add(z1)
            colors.add(baseR); colors.add(baseG); colors.add(baseB); colors.add(1f)
            vertices.add(x2); vertices.add(h2); vertices.add(z2)
            colors.add(mtnR); colors.add(mtnG); colors.add(mtnB); colors.add(1f)
            vertices.add(x1); vertices.add(h1); vertices.add(z1)
            colors.add(mtnR); colors.add(mtnG); colors.add(mtnB); colors.add(1f)
        }

        sceneryVertexCount = vertices.size / 3
        sceneryVertexBuffer = createFloatBuffer(vertices)
        sceneryColorBuffer = createFloatBuffer(colors)
    }

    /**
     * Records dynamic tire skidmark points deposited on the asphalt during drifts and hard braking.
     */
    fun recordSkidmark(wheelIndex: Int, curX: Float, curZ: Float, intensity: Float) {
        if (!hasPrevWheelPos[wheelIndex]) {
            prevWheelPos[wheelIndex][0] = curX
            prevWheelPos[wheelIndex][1] = curZ
            hasPrevWheelPos[wheelIndex] = true
            return
        }

        val pX = prevWheelPos[wheelIndex][0]
        val pZ = prevWheelPos[wheelIndex][1]
        val dx = curX - pX; val dz = curZ - pZ
        val distSq = dx * dx + dz * dz

        // Only emit if wheel moved at least 0.25m and less than 3m (to avoid teleports)
        if (distSq in 0.06f..9f) {
            val dist = kotlin.math.sqrt(distSq)
            val nx = -dz / dist * 0.12f // 0.24m wide tire mark ribbon
            val nz = dx / dist * 0.12f

            val alpha = (intensity * 0.75f).coerceIn(0.15f, 0.85f)
            val r = 0.06f; val g = 0.06f; val b = 0.07f

            // Add quad (6 vertices) to circular skid buffer
            if (activeSkidVertices + 6 > maxSkidSegments * skidVerticesPerSegment) {
                // Wrap to start of buffer
                skidRawBuffer.position(0)
                activeSkidVertices = 0
            }

            // Quad vertices
            val v1x = pX - nx; val v1z = pZ - nz
            val v2x = pX + nx; val v2z = pZ + nz
            val v3x = curX + nx; val v3z = curZ + nz
            val v4x = curX - nx; val v4z = curZ - nz

            // Put V1, V2, V3
            putVertex(v1x, 0.008f, v1z, r, g, b, alpha)
            putVertex(v2x, 0.008f, v2z, r, g, b, alpha)
            putVertex(v3x, 0.008f, v3z, r, g, b, alpha)

            // Put V1, V3, V4
            putVertex(v1x, 0.008f, v1z, r, g, b, alpha)
            putVertex(v3x, 0.008f, v3z, r, g, b, alpha)
            putVertex(v4x, 0.008f, v4z, r, g, b, alpha)

            activeSkidVertices += 6
        }

        prevWheelPos[wheelIndex][0] = curX
        prevWheelPos[wheelIndex][1] = curZ
    }

    private fun putVertex(x: Float, y: Float, z: Float, r: Float, g: Float, b: Float, a: Float) {
        skidRawBuffer.put(x); skidRawBuffer.put(y); skidRawBuffer.put(z)
        skidRawBuffer.put(r); skidRawBuffer.put(g); skidRawBuffer.put(b); skidRawBuffer.put(a)
    }

    fun draw(viewMatrix: FloatArray, projMatrix: FloatArray, groundProgram: Int, blendProgram: Int) {
        Matrix.multiplyMM(mvpMatrix, 0, projMatrix, 0, viewMatrix, 0)

        // 1. Draw Scenery (Terrain & Mountains) & Asphalt Circuit
        GLES20.glUseProgram(groundProgram)
        val posHandle = GLES20.glGetAttribLocation(groundProgram, "a_Position")
        val colorHandle = GLES20.glGetAttribLocation(groundProgram, "a_Color")
        val mvpHandle = GLES20.glGetUniformLocation(groundProgram, "u_MVPMatrix")

        GLES20.glUniformMatrix4fv(mvpHandle, 1, false, mvpMatrix, 0)

        // Draw Scenery
        GLES20.glEnableVertexAttribArray(posHandle)
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, sceneryVertexBuffer)
        GLES20.glEnableVertexAttribArray(colorHandle)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, sceneryColorBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, sceneryVertexCount)

        // Draw Asphalt Track & Curbs
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, trackVertexBuffer)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, trackColorBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, trackVertexCount)

        // Draw Metal Barriers
        GLES20.glVertexAttribPointer(posHandle, 3, GLES20.GL_FLOAT, false, 0, barrierVertexBuffer)
        GLES20.glVertexAttribPointer(colorHandle, 4, GLES20.GL_FLOAT, false, 0, barrierColorBuffer)
        GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, barrierVertexCount)

        // 2. Draw Dynamic Tire Skidmarks (Alpha Blended)
        if (activeSkidVertices > 0) {
            GLES20.glUseProgram(blendProgram)
            GLES20.glEnable(GLES20.GL_BLEND)
            GLES20.glBlendFunc(GLES20.GL_SRC_ALPHA, GLES20.GL_ONE_MINUS_SRC_ALPHA)

            val bPosHandle = GLES20.glGetAttribLocation(blendProgram, "a_Position")
            val bColorHandle = GLES20.glGetAttribLocation(blendProgram, "a_Color")
            val bMvpHandle = GLES20.glGetUniformLocation(blendProgram, "u_MVPMatrix")

            GLES20.glUniformMatrix4fv(bMvpHandle, 1, false, mvpMatrix, 0)

            val stride = skidFloatsPerVertex * 4
            skidRawBuffer.position(0)
            GLES20.glEnableVertexAttribArray(bPosHandle)
            GLES20.glVertexAttribPointer(bPosHandle, 3, GLES20.GL_FLOAT, false, stride, skidRawBuffer)

            skidRawBuffer.position(3)
            GLES20.glEnableVertexAttribArray(bColorHandle)
            GLES20.glVertexAttribPointer(bColorHandle, 4, GLES20.GL_FLOAT, false, stride, skidRawBuffer)

            GLES20.glDrawArrays(GLES20.GL_TRIANGLES, 0, activeSkidVertices)

            GLES20.glDisable(GLES20.GL_BLEND)
            GLES20.glDisableVertexAttribArray(bPosHandle)
            GLES20.glDisableVertexAttribArray(bColorHandle)
        }

        GLES20.glDisableVertexAttribArray(posHandle)
        GLES20.glDisableVertexAttribArray(colorHandle)
    }

    private fun addQuad(
        vertices: ArrayList<Float>, colors: ArrayList<Float>,
        x1: Float, y1: Float, z1: Float,
        x2: Float, y2: Float, z2: Float,
        x3: Float, y3: Float, z3: Float,
        x4: Float, y4: Float, z4: Float,
        r: Float, g: Float, b: Float, a: Float = 1.0f
    ) {
        // T1: 1 -> 2 -> 3
        vertices.add(x1); vertices.add(y1); vertices.add(z1)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
        vertices.add(x2); vertices.add(y2); vertices.add(z2)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
        vertices.add(x3); vertices.add(y3); vertices.add(z3)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)

        // T2: 1 -> 3 -> 4
        vertices.add(x1); vertices.add(y1); vertices.add(z1)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
        vertices.add(x3); vertices.add(y3); vertices.add(z3)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
        vertices.add(x4); vertices.add(y4); vertices.add(z4)
        colors.add(r); colors.add(g); colors.add(b); colors.add(a)
    }

    private fun createFloatBuffer(list: List<Float>): FloatBuffer {
        val bb = ByteBuffer.allocateDirect(list.size * 4)
        bb.order(ByteOrder.nativeOrder())
        val fb = bb.asFloatBuffer()
        for (f in list) fb.put(f)
        fb.position(0)
        return fb
    }
}
