package dev.zprestige.prestige.render;

import java.io.IOException;
import java.io.InputStream;
import java.nio.ByteBuffer;
import java.nio.IntBuffer;
import org.lwjgl.opengl.GL;
import org.lwjgl.opengl.GL11;
import org.lwjgl.opengl.GL15;
import org.lwjgl.opengl.GL30;
import org.lwjgl.stb.STBImage;
import org.lwjgl.system.MemoryStack;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class TextureLoader {
    private static final Logger LOGGER = LoggerFactory.getLogger(TextureLoader.class);
    private static final boolean ANISOTROPY_SUPPORTED = GL.getCapabilities().GL_EXT_texture_filter_anisotropic;
    private static final float MAX_ANISOTROPY;

    public static GlTextureHandle loadTexture(String path) {
        try {
            return loadTexture(path, 9729, 33071, true);
        } catch (IOException exception) {
            throw new RuntimeException(exception);
        }
    }

    public static GlTextureHandle loadTexture(String path, int filter, int wrapMode, boolean mipmaps) throws IOException {
        ByteBuffer fileData = readResource(path);

        GlTextureHandle result;
        try (MemoryStack stack = MemoryStack.stackPush()) {
            IntBuffer widthBuffer = stack.mallocInt(1);
            IntBuffer heightBuffer = stack.mallocInt(1);
            ByteBuffer pixels = STBImage.stbi_load_from_memory(fileData, widthBuffer, heightBuffer, stack.mallocInt(1), 4);
            if (pixels == null) {
                throw new IOException("Failed to load image: " + STBImage.stbi_failure_reason());
            }

            try {
                int width = widthBuffer.get(0);
                int height = heightBuffer.get(0);
                int expectedSize = width * height * 4;
                if (pixels.remaining() < expectedSize) {
                    throw new IOException("Image buffer too small: " + pixels.remaining() + " bytes, expected " + expectedSize);
                }

                int previousTexture = GL11.glGetInteger(32873);
                GlTextureHandle texture = GlTextureHandle.create(3553);
                texture.allocate(32856, width, height);
                GL15.glBindBuffer(35052, 0);
                GL11.glPixelStorei(3314, 0);
                GL11.glPixelStorei(3316, 0);
                GL11.glPixelStorei(3315, 0);
                GL11.glPixelStorei(3317, 4);
                GL11.glBindTexture(3553, texture.glId);
                texture.uploadRegion(0, 0, width, height, 6408, 5121, pixels);
                if (mipmaps) {
                    GL30.glGenerateMipmap(3553);
                    if (filter == 9729) {
                        GL11.glTexParameteri(3553, 10241, 9987);
                        GL11.glTexParameteri(3553, 10240, 9729);
                    } else {
                        texture.setFilter(filter);
                    }
                } else {
                    texture.setFilter(filter);
                }

                texture.setWrapMode(wrapMode);
                if (ANISOTROPY_SUPPORTED && mipmaps) {
                    GL11.glTexParameterf(3553, 34046, Math.min(64.0F, MAX_ANISOTROPY));
                }

                GL11.glBindTexture(3553, previousTexture);
                result = texture;
            } finally {
                STBImage.stbi_image_free(pixels);
            }
        }

        return result;
    }

    private static ByteBuffer readResource(String path) throws IOException {
        String resourcePath = path.startsWith("/") ? path.substring(1) : path;

        ByteBuffer result;
        try (InputStream stream = TextureLoader.class.getClassLoader().getResourceAsStream(resourcePath)) {
            if (stream == null) {
                throw new IOException("Resource not found: " + resourcePath);
            }

            byte[] bytes = stream.readAllBytes();
            ByteBuffer buffer = ByteBuffer.allocateDirect(bytes.length);
            buffer.put(bytes);
            buffer.flip();
            result = buffer;
        }

        return result;
    }

    static {
        if (ANISOTROPY_SUPPORTED) {
            MAX_ANISOTROPY = GL11.glGetFloat(34047);
            LOGGER.info("Anisotropic filtering supported: Max {}x", MAX_ANISOTROPY);
        } else {
            MAX_ANISOTROPY = 1.0F;
            LOGGER.info("Anisotropic filtering not supported");
        }
    }
}
