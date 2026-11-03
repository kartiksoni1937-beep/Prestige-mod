package dev.zprestige.prestige.render;

import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.data.MeshInfo;
import java.util.HashSet;

public class DrawPipeline {
    public VertexLayout vertexFormat;
    public int primitiveMode;
    public boolean entityMask;
    public RenderStateElement[] states;
    public static final boolean ASSERTIONS_DISABLED = !DrawPipeline.class.desiredAssertionStatus();

    public DrawPipeline(VertexLayout format, int mode, boolean entityMaskPass, RenderStateElement[] pipelineStates) {
        super();
        this.vertexFormat = format;
        this.primitiveMode = mode;
        this.entityMask = entityMaskPass;
        this.states = pipelineStates;
        if (!ASSERTIONS_DISABLED && !hasNoDuplicateStates(pipelineStates)) {
            throw new AssertionError("duplicate states that shouldnt be duplicated!");
        }
    }

    public int getPrimitiveMode() {
        return this.primitiveMode;
    }

    private static boolean hasNoDuplicateStates(RenderStateElement[] stateArray) {
        HashSet seenTypes = new HashSet(stateArray.length);

        for (RenderStateElement state : stateArray) {
            if (!state.allowsDuplicates() && !seenTypes.add(state.getClass())) {
                return false;
            }
        }

        return true;
    }

    public void draw(CameraMatrices context, VertexArray vertexArray, GlBuffer indexBuffer, MeshInfo drawInfo) {
        if (!ASSERTIONS_DISABLED && !drawInfo.layout().equals(this.vertexFormat)) {
            throw new AssertionError("format mismatch!");
        } else {
            GlRenderPassBuilder passBuilder = GlRenderPass.builder();

            for (RenderStateElement setupState : this.states) {
                setupState.configure(passBuilder);
            }

            GlRenderPass pass = passBuilder.build();
            try {
                pass.begin();
                pass.uploadMatrices(context);

                for (RenderStateElement applyState : this.states) {
                    applyState.apply(pass);
                }

                pass.drawIndexed(vertexArray, indexBuffer, this.primitiveMode, drawInfo.indexCount());
            } finally {
                for (RenderStateElement cleanupState : this.states) {
                    cleanupState.cleanup();
                }
            }
        }
    }

    public boolean isEntityMask() {
        return this.entityMask;
    }

    public VertexLayout getVertexFormat() {
        return this.vertexFormat;
    }
}
