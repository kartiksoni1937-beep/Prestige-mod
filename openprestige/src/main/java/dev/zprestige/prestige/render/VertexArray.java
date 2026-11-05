package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.VertexAttributeBinding;
import dev.zprestige.prestige.data.VertexElement;
import java.lang.ref.Cleaner.Cleanable;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import org.jetbrains.annotations.NotNull;
import org.lwjgl.opengl.GL30;

public final class VertexArray implements AutoCloseable {
    private final int glId;
    private final VertexAttributeBinding[] bindings;
    private final Cleanable cleanable;
    private int bindingCount;
    public static final boolean ASSERTIONS_DISABLED = !VertexArray.class.desiredAssertionStatus();
    private static final String TOO_MANY_BINDINGS_PREFIX = "too many bindings (";

    private VertexArray(int arrayId, VertexAttributeBinding[] initialBindings) {
        this.glId = arrayId;
        this.bindings = new VertexAttributeBinding[16];
        if (initialBindings != null) {
            System.arraycopy(initialBindings, 0, this.bindings, 0, initialBindings.length);
        }

        this.bindingCount = initialBindings == null ? 0 : initialBindings.length;
        this.cleanable = RenderCore.CLEANER.register(this, () -> RenderCore.runOnRenderThread((Runnable)() -> GL30.glDeleteVertexArrays(arrayId)));
    }

    public static VertexArray forBuffer(GlBuffer buffer, VertexLayout format) {
        return create(createBindings(buffer, format));
    }

    @NotNull
    public static List createBindings(GlBuffer buffer, VertexLayout format) {
        ArrayList result = new ArrayList(format.elements.length);

        for (int index = 0; index < format.elements.length; index++) {
            result.add(format.createBinding(buffer, index));
        }

        return result;
    }

    public static VertexArray create(List initial) {
        VertexArray vertexArray = new VertexArray(GL30.glGenVertexArrays(), null);
        if (initial != null) {
            vertexArray.setBindings(initial);
        }

        return vertexArray;
    }

    @Override
    public void close() {
        this.cleanable.clean();
    }

    public void setBindings(List newBindings) {
        int count = newBindings.size();
        if (!ASSERTIONS_DISABLED && count > 16) {
            throw new AssertionError(TOO_MANY_BINDINGS_PREFIX + count + ")");
        } else {
            VertexAttributeBinding[] bindingArray = (VertexAttributeBinding[])newBindings.toArray(new VertexAttributeBinding[0]);
            if (this.bindingCount != count || !Arrays.equals(this.bindings, 0, this.bindingCount, bindingArray, 0, bindingArray.length)) {
                int maxCount = Math.max(this.bindingCount, count);
                int minCount = Math.min(this.bindingCount, count);
                GL30.glBindVertexArray(this.glId);

                for (int attribIndex = minCount; attribIndex < maxCount; attribIndex++) {
                    if (attribIndex >= this.bindingCount) {
                        GL30.glEnableVertexAttribArray(attribIndex);
                    } else {
                        GL30.glDisableVertexAttribArray(attribIndex);
                    }
                }

                for (int bindingIndex = 0; bindingIndex < count; bindingIndex++) {
                    VertexAttributeBinding binding = (VertexAttributeBinding)newBindings.get(bindingIndex);
                    GL30.glBindBuffer(34962, binding.buffer().getGlId());
                    VertexElement attribute = binding.element();
                    GL30.glVertexAttribPointer(bindingIndex, attribute.componentCount(), attribute.glType(), attribute.normalized(), binding.stride(), binding.offset());
                }

                GL30.glBindVertexArray(0);
                Arrays.fill(this.bindings, null);
                System.arraycopy(bindingArray, 0, this.bindings, 0, count);
                this.bindingCount = count;
            }
        }
    }

    public int getGlId() {
        return this.glId;
    }
}
