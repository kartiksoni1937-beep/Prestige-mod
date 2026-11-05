package dev.zprestige.prestige.render;

import java.lang.ref.Cleaner.Cleanable;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL20;

public final class Shader implements AutoCloseable {
    private final int programId;
    private final Cleanable cleanable;
    private final int projMatLocation;
    private final int viewMatLocation;

    public Shader(int program) {
        this.programId = program;
        this.cleanable = RenderCore.CLEANER.register(this, () -> RenderCore.runOnRenderThread((Runnable)() -> GL20.glDeleteProgram(program)));
        this.projMatLocation = GL20.glGetUniformLocation(this.getProgramId(), "projMat");
        this.viewMatLocation = GL20.glGetUniformLocation(this.getProgramId(), "viewMat");
    }

    public int getProgramId() {
        return this.programId;
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    public int getProjMatLocation() {
        return this.projMatLocation;
    }

    public int getViewMatLocation() {
        return this.viewMatLocation;
    }

    public int getUniformLocation(String name) {
        return GL20.glGetUniformLocation(this.programId, name);
    }

    public void setUniformInt(String name, int value) {
        int previousProgram = GL11.glGetInteger(35725);
        GL20.glUseProgram(this.programId);
        GL20.glUniform1i(this.getUniformLocation(name), value);
        GL20.glUseProgram(previousProgram);
    }
}
