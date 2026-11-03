package dev.zprestige.prestige.render;

import org.lwjgl.opengl.GL30C;

public record GlStateSnapshot(
    boolean depthTest,
    boolean cullFace,
    boolean blend,
    boolean scissorTest,
    int program,
    int vertexArray,
    int arrayBuffer,
    int elementArrayBuffer,
    int activeTexture,
    int[] boundTextures,
    int blendEquationRgb,
    int blendEquationAlpha,
    int blendSrcRgb,
    int blendDstRgb,
    int blendSrcAlpha,
    int blendDstAlpha,
    int[] scissorBox,
    int readFramebuffer,
    int drawFramebuffer
) {
    private static final int TEXTURE_UNITS = 8;

    public static GlStateSnapshot capture() {
        boolean depthEnabled = GL30C.glIsEnabled(2929);
        boolean cullEnabled = GL30C.glIsEnabled(2884);
        boolean blendEnabled = GL30C.glIsEnabled(3042);
        boolean scissorEnabled = GL30C.glIsEnabled(3089);
        int currentProgram = GL30C.glGetInteger(35725);
        int boundVertexArray = GL30C.glGetInteger(34229);
        int boundArrayBuffer = GL30C.glGetInteger(34964);
        int boundElementBuffer = GL30C.glGetInteger(34965);
        int activeUnit = GL30C.glGetInteger(34016);
        int[] boundTextures = new int[8];

        for (int unit = 0; unit < 8; unit++) {
            GL30C.glActiveTexture(33984 + unit);
            boundTextures[unit] = GL30C.glGetInteger(32873);
        }

        GL30C.glActiveTexture(activeUnit);
        int blendEquationRgb = GL30C.glGetInteger(32777);
        int blendEquationAlpha = GL30C.glGetInteger(34877);
        int blendSrcRgb = GL30C.glGetInteger(32969);
        int blendDstRgb = GL30C.glGetInteger(32968);
        int blendSrcAlpha = GL30C.glGetInteger(32971);
        int blendDstAlpha = GL30C.glGetInteger(32970);
        int[] scissorBox = new int[4];
        GL30C.glGetIntegerv(3088, scissorBox);
        int drawFramebuffer = GL30C.glGetInteger(36006);
        return new GlStateSnapshot(depthEnabled, cullEnabled, blendEnabled, scissorEnabled, currentProgram, boundVertexArray, boundArrayBuffer, boundElementBuffer, activeUnit, boundTextures, blendEquationRgb, blendEquationAlpha, blendSrcRgb, blendDstRgb, blendSrcAlpha, blendDstAlpha, scissorBox, GL30C.glGetInteger(36010), drawFramebuffer);
    }

    public void restore() {
        if (this.depthTest) {
            GL30C.glEnable(2929);
        } else {
            GL30C.glDisable(2929);
        }

        if (this.cullFace) {
            GL30C.glEnable(2884);
        } else {
            GL30C.glDisable(2884);
        }

        if (this.blend) {
            GL30C.glEnable(3042);
        } else {
            GL30C.glDisable(3042);
        }

        if (this.scissorTest) {
            GL30C.glEnable(3089);
        } else {
            GL30C.glDisable(3089);
        }

        GL30C.glUseProgram(this.program);
        GL30C.glBindBuffer(34962, this.arrayBuffer);
        GL30C.glBindBuffer(34963, this.elementArrayBuffer);
        GL30C.glBindVertexArray(this.vertexArray);

        for (int unit = 0; unit < this.boundTextures.length; unit++) {
            GL30C.glActiveTexture(33984 + unit);
            GL30C.glBindTexture(3553, this.boundTextures[unit]);
        }

        GL30C.glActiveTexture(this.activeTexture);
        GL30C.glBlendEquationSeparate(this.blendEquationRgb, this.blendEquationAlpha);
        GL30C.glBlendFuncSeparate(this.blendSrcRgb, this.blendDstRgb, this.blendSrcAlpha, this.blendDstAlpha);
        GL30C.glScissor(this.scissorBox[0], this.scissorBox[1], this.scissorBox[2], this.scissorBox[3]);
        GL30C.glBindFramebuffer(36008, this.readFramebuffer);
        GL30C.glBindFramebuffer(36009, this.drawFramebuffer);
    }
}
