package dev.zprestige.prestige.data;

import java.awt.Color;
import java.util.HashMap;
import java.util.Map;
import org.lwjgl.opengl.GL20;

public record ProgramUniforms(int programId) {
    private static final Map LOCATION_CACHE = new HashMap();

    public static ProgramUniforms of(int program) {
        return new ProgramUniforms(program);
    }

    private int getUniformLocation(String uniformName) {
        return (Integer)((Map)LOCATION_CACHE.computeIfAbsent(this.programId, programKey -> new HashMap<>())).computeIfAbsent(uniformName, uniform -> GL20.glGetUniformLocation(this.programId, ((java.lang.String)uniform)));
    }

    public ProgramUniforms setInt(String uniformName, int value) {
        GL20.glUniform1i(this.getUniformLocation(uniformName), value);
        return this;
    }

    public ProgramUniforms setInt2(String uniformName, int x, int y) {
        GL20.glUniform2i(this.getUniformLocation(uniformName), x, y);
        return this;
    }

    public ProgramUniforms setInt3(String uniformName, int x, int y, int z) {
        GL20.glUniform3i(this.getUniformLocation(uniformName), x, y, z);
        return this;
    }

    public ProgramUniforms setInt4(String uniformName, int x, int y, int z, int w) {
        GL20.glUniform4i(this.getUniformLocation(uniformName), x, y, z, w);
        return this;
    }

    public ProgramUniforms setFloat(String uniformName, float value) {
        GL20.glUniform1f(this.getUniformLocation(uniformName), value);
        return this;
    }

    public ProgramUniforms setVec2(String uniformName, float x, float y) {
        GL20.glUniform2f(this.getUniformLocation(uniformName), x, y);
        return this;
    }

    public ProgramUniforms setVec3(String uniformName, float x, float y, float z) {
        GL20.glUniform3f(this.getUniformLocation(uniformName), x, y, z);
        return this;
    }

    public ProgramUniforms setVec4(String uniformName, float x, float y, float z, float w) {
        GL20.glUniform4f(this.getUniformLocation(uniformName), x, y, z, w);
        return this;
    }

    public ProgramUniforms setMatrix4(String uniformName, boolean transpose, float[] matrix) {
        GL20.glUniformMatrix4fv(this.getUniformLocation(uniformName), transpose, matrix);
        return this;
    }

    public ProgramUniforms setMatrix3(String uniformName, boolean transpose, float[] matrix) {
        GL20.glUniformMatrix3fv(this.getUniformLocation(uniformName), transpose, matrix);
        return this;
    }

    public ProgramUniforms setMatrix2(String uniformName, boolean transpose, float[] matrix) {
        GL20.glUniformMatrix2fv(this.getUniformLocation(uniformName), transpose, matrix);
        return this;
    }

    public ProgramUniforms setColor(String uniformName, Color color) {
        GL20.glUniform4f(this.getUniformLocation(uniformName), color.getRed() / 255.0F, color.getGreen() / 255.0F, color.getBlue() / 255.0F, color.getAlpha() / 255.0F);
        return this;
    }

    public ProgramUniforms setFloat4(String uniformName, float x, float y, float z, float w) {
        GL20.glUniform4f(this.getUniformLocation(uniformName), x, y, z, w);
        return this;
    }
}
