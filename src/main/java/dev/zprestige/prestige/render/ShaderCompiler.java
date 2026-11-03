package dev.zprestige.prestige.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import org.lwjgl.opengl.GL20;

public class ShaderCompiler {
    public static final boolean ASSERTIONS_DISABLED = !ShaderCompiler.class.desiredAssertionStatus();

    public static Shader loadProgram(String vertexPath, String fragmentPath, boolean spirv) {
        int vertexShader = compileShader(35633, vertexPath, spirv);
        int fragmentShader;
        try {
            fragmentShader = compileShader(35632, fragmentPath, spirv);
        } catch (RuntimeException exception) {
            GL20.glDeleteShader(vertexShader);
            throw exception;
        }

        int program = GL20.glCreateProgram();
        GL20.glAttachShader(program, vertexShader);
        GL20.glAttachShader(program, fragmentShader);
        GL20.glLinkProgram(program);
        if (GL20.glGetProgrami(program, 35714) == 0) {
            String linkLog = GL20.glGetProgramInfoLog(program);
            System.err.println(linkLog);
            GL20.glDeleteProgram(program);
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            throw new RuntimeException("Program link failed. See error above");
        } else {
            GL20.glDeleteShader(vertexShader);
            GL20.glDeleteShader(fragmentShader);
            return new Shader(program);
        }
    }

    private static int compileShader(int shaderType, String path, boolean spirv) {
        if (spirv) {
            throw new UnsupportedOperationException("SPIR-V shader loading was removed for GL 3.3 compatibility. Provide GLSL source instead: " + path);
        } else {
            String source;
            try (InputStream stream = ShaderCompiler.class.getClassLoader().getResourceAsStream(path)) {
                if (stream == null) {
                    throw new IOException("Shader resource not found: " + path);
                }

                source = new String(stream.readAllBytes(), StandardCharsets.UTF_8);
            } catch (IOException exception) {
                throw new RuntimeException(exception);
            }

            int shader = GL20.glCreateShader(shaderType);
            GL20.glShaderSource(shader, source);
            GL20.glCompileShader(shader);
            if (GL20.glGetShaderi(shader, 35713) == 0) {
                String compileLog = GL20.glGetShaderInfoLog(shader);
                System.err.println(compileLog);
                GL20.glDeleteShader(shader);
                throw new RuntimeException("Shader compile failed for " + path + ". See logs above for error");
            } else {
                return shader;
            }
        }
    }
}
