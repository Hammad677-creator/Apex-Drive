package com.example.graphics

import android.opengl.GLES20
import android.util.Log

object Shaders {

    // Main 3D Lit & Metallic PBR-styled Shader
    val MAIN_VERTEX_SHADER = """
        uniform mat4 u_MVPMatrix;
        uniform mat4 u_MVMatrix;
        uniform mat3 u_NormalMatrix;
        uniform vec3 u_LightDir;
        uniform vec3 u_CamPos;

        attribute vec4 a_Position;
        attribute vec3 a_Normal;
        attribute vec4 a_Color;

        varying vec4 v_Color;
        varying vec3 v_Normal;
        varying vec3 v_ViewDir;
        varying vec3 v_WorldPos;

        void main() {
            vec4 worldPos = a_Position;
            v_WorldPos = worldPos.xyz;
            v_Normal = normalize(u_NormalMatrix * a_Normal);
            v_ViewDir = normalize(u_CamPos - worldPos.xyz);
            v_Color = a_Color;
            gl_Position = u_MVPMatrix * a_Position;
        }
    """.trimIndent()

    val MAIN_FRAGMENT_SHADER = """
        precision mediump float;

        uniform vec3 u_LightDir;
        uniform vec4 u_CarColor;
        uniform float u_UseCarColor; // 1.0 = use u_CarColor, 0.0 = use vertex color
        uniform float u_Metallic;
        uniform float u_Roughness;
        uniform vec3 u_Emissive;

        varying vec4 v_Color;
        varying vec3 v_Normal;
        varying vec3 v_ViewDir;
        varying vec3 v_WorldPos;

        void main() {
            vec3 normal = normalize(v_Normal);
            vec3 viewDir = normalize(v_ViewDir);
            vec3 lightDir = normalize(u_LightDir);
            vec3 halfDir = normalize(lightDir + viewDir);

            // Base Albedo Color
            vec4 baseColor = mix(v_Color, u_CarColor, u_UseCarColor);

            // Overcast Sky Hemisphere Ambient Lighting
            vec3 skyAmbient = vec3(0.72, 0.78, 0.86); // Soft cool overcast sky
            vec3 groundAmbient = vec3(0.28, 0.30, 0.32); // Ground bounce
            float upFactor = normal.y * 0.5 + 0.5;
            vec3 ambient = mix(groundAmbient, skyAmbient, upFactor) * 0.55;

            // Soft directional sunlight with diffuse wrap
            float NdotL = max(dot(normal, lightDir), 0.0);
            float diffuse = pow(NdotL * 0.7 + 0.3, 1.2);

            // Specular Blinn-Phong highlight
            float NdotH = max(dot(normal, halfDir), 0.0);
            float specPower = mix(16.0, 96.0, 1.0 - u_Roughness);
            float spec = pow(NdotH, specPower) * mix(0.4, 1.2, u_Metallic);

            // Clearcoat Fresnel rim reflection (giving car paint its signature glossy depth)
            float NdotV = max(dot(normal, viewDir), 0.0);
            float fresnel = pow(1.0 - NdotV, 3.2) * (0.35 + 0.45 * u_Metallic);

            // Reflection color from overcast dome
            vec3 reflectionColor = vec3(0.85, 0.90, 0.98);

            vec3 litColor = baseColor.rgb * (ambient + diffuse * 0.7)
                          + vec3(spec) * 0.8
                          + reflectionColor * fresnel
                          + u_Emissive;

            gl_FragColor = vec4(litColor, baseColor.a);
        }
    """.trimIndent()

    // Ground & Asphalt Shader with built-in procedural curbs & lines
    val GROUND_VERTEX_SHADER = """
        uniform mat4 u_MVPMatrix;
        attribute vec4 a_Position;
        attribute vec4 a_Color;
        attribute vec2 a_TexCoord;

        varying vec4 v_Color;
        varying vec2 v_TexCoord;
        varying vec3 v_WorldPos;

        void main() {
            v_WorldPos = a_Position.xyz;
            v_Color = a_Color;
            v_TexCoord = a_TexCoord;
            gl_Position = u_MVPMatrix * a_Position;
        }
    """.trimIndent()

    val GROUND_FRAGMENT_SHADER = """
        precision mediump float;

        varying vec4 v_Color;
        varying vec2 v_TexCoord;
        varying vec3 v_WorldPos;

        void main() {
            // Subtle asphalt micro-grain noise pattern
            float noise = fract(sin(dot(v_WorldPos.xz, vec2(12.9898, 78.233))) * 43758.5453);
            vec3 asphalt = v_Color.rgb + (noise - 0.5) * 0.04;
            gl_FragColor = vec4(asphalt, v_Color.a);
        }
    """.trimIndent()

    // Soft Contact Shadow & Skidmark Shader
    val BLEND_VERTEX_SHADER = """
        uniform mat4 u_MVPMatrix;
        attribute vec4 a_Position;
        attribute vec4 a_Color;

        varying vec4 v_Color;

        void main() {
            v_Color = a_Color;
            gl_Position = u_MVPMatrix * a_Position;
        }
    """.trimIndent()

    val BLEND_FRAGMENT_SHADER = """
        precision mediump float;
        varying vec4 v_Color;

        void main() {
            gl_FragColor = v_Color;
        }
    """.trimIndent()

    fun createProgram(vertexCode: String, fragmentCode: String): Int {
        val vertexShader = loadShader(GLES20.GL_VERTEX_SHADER, vertexCode)
        val fragmentShader = loadShader(GLES20.GL_FRAGMENT_SHADER, fragmentCode)
        if (vertexShader == 0 || fragmentShader == 0) return 0

        val program = GLES20.glCreateProgram()
        if (program != 0) {
            GLES20.glAttachShader(program, vertexShader)
            GLES20.glAttachShader(program, fragmentShader)
            GLES20.glLinkProgram(program)
            val linkStatus = IntArray(1)
            GLES20.glGetProgramiv(program, GLES20.GL_LINK_STATUS, linkStatus, 0)
            if (linkStatus[0] != GLES20.GL_TRUE) {
                Log.e("Shaders", "Could not link program: " + GLES20.glGetProgramInfoLog(program))
                GLES20.glDeleteProgram(program)
                return 0
            }
        }
        return program
    }

    private fun loadShader(type: Int, shaderCode: String): Int {
        val shader = GLES20.glCreateShader(type)
        if (shader != 0) {
            GLES20.glShaderSource(shader, shaderCode)
            GLES20.glCompileShader(shader)
            val compiled = IntArray(1)
            GLES20.glGetShaderiv(shader, GLES20.GL_COMPILE_STATUS, compiled, 0)
            if (compiled[0] == 0) {
                Log.e("Shaders", "Could not compile shader $type: " + GLES20.glGetShaderInfoLog(shader))
                GLES20.glDeleteShader(shader)
                return 0
            }
        }
        return shader
    }
}
