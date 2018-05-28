import cglew.glewExperimental
import cglew.glewInit
import cglew.GLEW_OK
import cglew.glClear
import cglfw.*
import kotlinx.cinterop.*
import platform.OpenGL3.*
import platform.OpenGLCommon.GLintVar

val vertexShaderSource: String = """#version 330 core
layout (location = 0) in vec3 position;
void main() {
  gl_Position = vec4(position.x, position.y, position.z, 1.0);
}
"""
val fragmentShaderSource: String = """#version 330 core
out vec4 color;
void main() {
  color = vec4(1.0f, 0.5f, 0.2f, 1.0f);
}
"""

fun main(args: Array<String>) {
    glewExperimental = GL_TRUE.narrow()

    if (glfwInit() == GL_FALSE) {
        throw Error("Failed to initialize GLFW")
    }

    glfwWindowHint(GL_SAMPLES, 4)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MAJOR, 3)
    glfwWindowHint(GLFW_CONTEXT_VERSION_MINOR, 3)
    glfwWindowHint(GLFW_OPENGL_FORWARD_COMPAT, GL_TRUE)
    glfwWindowHint(GLFW_OPENGL_PROFILE, GLFW_OPENGL_CORE_PROFILE)

    val window = glfwCreateWindow(1024, 768, "GLLab", null, null) ?:
        throw Error("Failed to open GLFW window. If you have an Intel GPU, they are not 3.3 compatible. Try the 2.1 version of the tutorials.")

    glfwMakeContextCurrent(window)
    glewExperimental = GL_TRUE.narrow()

    if (glewInit() != GLEW_OK) {
        throw Error("Failed to initialize GLEW")
    }

    glfwSetInputMode(window, GLFW_STICKY_KEYS, GL_TRUE)

    glViewport(0, 0, 1024, 768)

    val vertexShader = compileShader(GL_VERTEX_SHADER, vertexShaderSource)
    val fragmentShader = compileShader(GL_FRAGMENT_SHADER, fragmentShaderSource)

    val shaderProgram = glCreateProgram()
    glAttachShader(shaderProgram, vertexShader)
    glAttachShader(shaderProgram, fragmentShader)
    glLinkProgram(shaderProgram)

    checkProgramStatus(shaderProgram)

    glDeleteShader(vertexShader)
    glDeleteShader(fragmentShader)

    val vao: Int = memScoped {
        val resultVar: IntVarOf<Int> = alloc()
        glGenVertexArrays(1, resultVar.ptr)
        resultVar.value
    }

    glBindVertexArray(vao)

    val vertexBufferData: FloatArray = floatArrayOf(
            -0.5f, -0.5f,  0f,
             0.5f, -0.5f,  0f,
               0f,  0.5f,  0f
    )

    val vbo: Int = generateBuffer()
    glBindBuffer(GL_ARRAY_BUFFER, vbo)
    glBufferData(GL_ARRAY_BUFFER, (vertexBufferData.size * 4).signExtend(), vertexBufferData.toCValues(), GL_STATIC_DRAW)
    glVertexAttribPointer(0, 3, GL_FLOAT, GL_FALSE.narrow(), 4, null)
    glEnableVertexAttribArray(0)
    glBindBuffer(GL_ARRAY_BUFFER, 0)

    glBindVertexArray(0)

    while (glfwGetKey(window, GLFW_KEY_ESCAPE) != GLFW_PRESS && glfwWindowShouldClose(window) == 0) {
        glfwPollEvents()
        checkError("glfwPollEvents")
        glClearColor(0.2f, 0.3f, 0.3f, 1f)
        checkError("glClearColor")
        glClear(GL_COLOR_BUFFER_BIT)
        checkError("glClear")

        glUseProgram(shaderProgram)
        glBindVertexArray(vao)
        glDrawArrays(GL_TRIANGLES, 0, 3)
        checkError("glDrawArrays")
        glBindVertexArray(0)

        glfwSwapBuffers(window)

    }

    glfwTerminate()
}

fun generateBuffer(): Int = memScoped {
    val bufferVar: IntVarOf<Int> = alloc()
    glGenBuffers(1, bufferVar.ptr)
    bufferVar.value
}

fun checkError(message: String?) {
    val error = glGetError()
    if (error != 0) {
        val errorString = when (error) {
            GL_INVALID_ENUM -> "GL_INVALID_ENUM"
            GL_INVALID_VALUE -> "GL_INVALID_VALUE"
            GL_INVALID_OPERATION -> "GL_INVALID_OPERATION"
            GL_INVALID_FRAMEBUFFER_OPERATION -> "GL_INVALID_FRAMEBUFFER_OPERATION"
            GL_OUT_OF_MEMORY -> "GL_OUT_OF_MEMORY"
            else -> "unknown"
        }

        if (message != null) println("- $message")
        throw Exception("\tGL error: 0x${error.toString(16)} ($errorString)")
    }
}

fun checkProgramStatus(program: Int) {
    val status = alloc<GLintVar>()
    glGetProgramiv(program, GL_LINK_STATUS, status.ptr)
    if (status.value != GL_TRUE) {
        val log = allocArray<ByteVar>(512)
        glGetShaderInfoLog(program, 512, null, log)
        throw Error("Program linkin errors: ${log.toKString()}")
    }
}

fun compileShader(type: Int, source: String) = memScoped {
    val shader = glCreateShader(type)

    if (shader == 0) throw Error("Failed to create shader")

    glShaderSource(shader, 1, cValuesOf(source.cstr.getPointer(memScope)), null)
    glCompileShader(shader)

    val status = alloc<GLintVar>()
    glGetShaderiv(shader, GL_COMPILE_STATUS, status.ptr)

    if (status.value != GL_TRUE) {
        val log = allocArray<ByteVar>(512)
        glGetShaderInfoLog(shader, 512, null, log)
        throw Error("Shader compilation failed: ${log.toKString()}")
    }

    checkError("glShaderSource")

    shader
}