package dev.zprestige.prestige.render;

import java.lang.ref.Cleaner.Cleanable;
import java.nio.ByteBuffer;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.Identifier;
import org.lwjgl.opengl.GL11;

public class GlTextureHandle implements AutoCloseable {
    public final int glId;
    private final Cleanable cleanable;
    private final int target;
    private volatile boolean allocated = false;
    private int internalFormat;

    public GlTextureHandle(int textureTarget, int textureId) {
        this(textureTarget, textureId, true, (74681995002117L));
    }

    private GlTextureHandle(int textureTarget, int textureId, boolean owned, long unusedKey) {
        this.target = textureTarget;
        this.glId = textureId;
        this.cleanable = owned ? RenderCore.CLEANER.register(this, () -> RenderCore.runOnRenderThread((Runnable)() -> GL11.glDeleteTextures(textureId))) : null;
    }

    public static GlTextureHandle create(int textureTarget) {
        return new GlTextureHandle(textureTarget, GL11.glGenTextures());
    }

    public static GlTextureHandle wrap(int textureTarget, int textureId) {
        return new GlTextureHandle(textureTarget, textureId, false, 0L);
    }

    private int getBindingQuery(int textureTarget) {
        switch (textureTarget) {
            case 3553:
                return 32873;
            case 32879:
                return 32874;
            default:
                return 32873;
        }
    }

    private int bindTemporarily() {
        int previous = GL11.glGetInteger(this.getBindingQuery(this.target));
        GL11.glBindTexture(this.target, this.glId);
        return previous;
    }

    private void restoreBinding(int previousId) {
        GL11.glBindTexture(this.target, previousId);
    }

    public void setFilter(int filter) {
        int previous = this.bindTemporarily();
        GL11.glTexParameteri(this.target, 10241, filter);
        GL11.glTexParameteri(this.target, 10240, filter);
        this.restoreBinding(previous);
    }

    public void allocate(int format, int width, int height) {
        if (this.allocated) {
            throw new IllegalStateException("already inited - this is an immutable texture!");
        } else {
            this.allocated = true;
            this.internalFormat = format;

            char pixelFormat = switch (format) {
                case 32849 -> 'ᤇ';
                default -> 'ᤈ';
                case 33321 -> 'ᤃ';
                case 33323 -> '舧';
            };
            int previous = this.bindTemporarily();
            GL11.glTexImage2D(this.target, 0, format, width, height, 0, pixelFormat, 5121, (ByteBuffer)null);
            GL11.glTexParameteri(this.target, 33084, 0);
            GL11.glTexParameteri(this.target, 33085, 0);
            this.restoreBinding(previous);
        }
    }

    public void uploadRegion(int offsetX, int offsetY, int width, int height, int format, int type, ByteBuffer pixels) {
        int previous = this.bindTemporarily();
        GL11.glTexSubImage2D(this.target, 0, offsetX, offsetY, width, height, format, type, pixels);
        this.restoreBinding(previous);
    }

    public void setWrapMode(int wrapMode) {
        int previous = this.bindTemporarily();
        GL11.glTexParameteri(this.target, 10242, wrapMode);
        GL11.glTexParameteri(this.target, 10243, wrapMode);
        this.restoreBinding(previous);
    }

    public GlTextureHandle resize(int width, int height) {
        if (!this.allocated) {
            throw new IllegalStateException("not inited");
        } else {
            this.close();
            GlTextureHandle resized = create(this.target);
            resized.allocate(this.internalFormat, width, height);
            return resized;
        }
    }

    @Override
    public void close() {
        if (this.cleanable != null) {
            this.cleanable.clean();
        }
    }

    @Override
    public boolean equals(Object other) {
        if (this == other) {
            return true;
        } else {
            return other instanceof GlTextureHandle that && this.target == that.target && this.glId == that.glId;
        }
    }

    @Override
    public int hashCode() {
        return 31 * this.target + this.glId;
    }

    public static GlTextureHandle wrapMinecraftTexture(Identifier textureIdentifier) {
        return wrap(3553, ((GlTexture)MinecraftAccess.mc.getTextureManager().getTexture(textureIdentifier).getGlTexture()).getGlId());
    }
}
