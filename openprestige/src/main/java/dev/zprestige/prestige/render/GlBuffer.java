package dev.zprestige.prestige.render;

import java.lang.ref.Cleaner.Cleanable;
import java.nio.ByteBuffer;
import java.util.concurrent.atomic.AtomicLong;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class GlBuffer implements AutoCloseable {
    private static final Logger LOGGER = LoggerFactory.getLogger(GlBuffer.class);
    private final int glId;
    private final AtomicLong size;
    private final Cleanable cleanable;
    private int usage = 35044;

    public GlBuffer(int bufferId, AtomicLong sizeRef) {
        this.glId = bufferId;
        this.size = sizeRef;
        this.cleanable = RenderCore.CLEANER.register(this, () -> RenderCore.runOnRenderThread((Runnable)() -> GL15.glDeleteBuffers(bufferId)));
    }

    public static GlBuffer create() {
        return new GlBuffer(GL15.glGenBuffers(), new AtomicLong(0L));
    }

    public void allocate(int bufferUsage, long byteSize) {
        int previous = GL11.glGetInteger(34964);
        GL15.glBindBuffer(34962, this.glId);
        GL15.glBufferData(34962, byteSize, bufferUsage);
        GL15.glBindBuffer(34962, previous);
        this.usage = bufferUsage;
        this.size.set(byteSize);
    }

    public ByteBuffer mapRange(long offset, long length, int access) {
        int previous = GL11.glGetInteger(34964);
        GL15.glBindBuffer(34962, this.glId);
        ByteBuffer mapped = GL30.glMapBufferRange(34962, offset, length, access);
        GL15.glBindBuffer(34962, previous);
        return mapped;
    }

    public void unmap() {
        int previous = GL11.glGetInteger(34964);
        GL15.glBindBuffer(34962, this.glId);
        GL15.glUnmapBuffer(34962);
        GL15.glBindBuffer(34962, previous);
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    public int getGlId() {
        return this.glId;
    }

    public int getUsage() {
        return this.usage;
    }

    public AtomicLong getSize() {
        return this.size;
    }
}
