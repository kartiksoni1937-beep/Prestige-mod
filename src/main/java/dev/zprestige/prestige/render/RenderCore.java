package dev.zprestige.prestige.render;

import com.mojang.blaze3d.systems.RenderSystem;
import dev.zprestige.prestige.data.CameraMatrices;
import java.lang.ref.Cleaner;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.TimeoutException;
import java.util.function.Supplier;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gl.GlBackend;
import net.minecraft.client.texture.GlTexture;
import net.minecraft.util.math.Vec3d;
import org.joml.Vector3d;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class RenderCore {
    private static final Logger LOGGER = LoggerFactory.getLogger(RenderCore.class);
    public static final Cleaner CLEANER = Cleaner.create();
    public static final BufferSource BUFFER_SOURCE = new BufferSource();
    private static final List TASK_QUEUE = new ArrayList();
    public static CameraMatrices worldContext = null;
    private static Thread renderThread;
    private static long frameCount = -9223372036854775807L;

    public static int getMainFramebufferId() {
        return ((GlTexture)Objects.requireNonNull(MinecraftClient.getInstance().getFramebuffer().getColorAttachment()))
            .getOrCreateFramebuffer(
                ((GlBackend)RenderSystem.getDevice()).getBufferManager(), MinecraftClient.getInstance().getFramebuffer().getDepthAttachment()
            );
    }

    public static int getMainColorTextureId() {
        return ((GlTexture)Objects.requireNonNull(MinecraftClient.getInstance().getFramebuffer().getColorAttachment())).getGlId();
    }

    public static float getScaleFactor() {
        return MinecraftClient.getInstance().getWindow().getScaleFactor();
    }

    public static int getFramebufferHeight() {
        return MinecraftClient.getInstance().getWindow().getFramebufferHeight();
    }

    public static Vector3d toCameraRelative(CameraMatrices context, double x, double y, double z) {
        return toCameraRelative(context, x, y, z, new Vector3d());
    }

    public static Vector3d toCameraRelative(CameraMatrices context, double x, double y, double z, Vector3d dest) {
        Vec3d cameraPos = context.camera().getCameraPos();
        return dest.set(x - cameraPos.x, y - cameraPos.y, z - cameraPos.z);
    }

    public static void queueTask(Runnable task) {
        synchronized (TASK_QUEUE) {
            TASK_QUEUE.add(task);
        }
    }

    public static List drainTasks() {
        synchronized (TASK_QUEUE) {
            if (TASK_QUEUE.isEmpty()) {
                return List.of();
            }

            ArrayList drained = new ArrayList(TASK_QUEUE);
            TASK_QUEUE.clear();
            return drained;
        }
    }

    public static void runTasks(List tasks) {
        for (Runnable task : (Iterable<Runnable>)(tasks)) {
            try {
                task.run();
            } catch (Throwable throwable) {
                LOGGER.error("Render thread task failed", throwable);
            }
        }
    }

    public static void renderWorld(CameraMatrices context) {
        Render2D.currentContext = context;
        Render2D.currentBuffers = BUFFER_SOURCE;
        worldContext = context;
        try {
            ((RenderCallback)RenderEvents.WORLD_RENDER.invoker()).onRender(BUFFER_SOURCE, context);
            BUFFER_SOURCE.flush(context);
        } catch (Throwable throwable) {
            LOGGER.error("World render failed", throwable);
        }
    }

    public static void renderHud(CameraMatrices context) {
        nextFrame();
        if (MinecraftAccess.mc.world != null && MinecraftAccess.mc.player != null) {
            Render2D.currentContext = context;
            Render2D.currentBuffers = BUFFER_SOURCE;
            try {
                ((RenderCallback)RenderEvents.HUD_RENDER.invoker()).onRender(BUFFER_SOURCE, context);
                BUFFER_SOURCE.flush(context);
                ((RenderCallback)RenderEvents.AFTER_HUD_RENDER.invoker()).onRender(BUFFER_SOURCE, context);
                BUFFER_SOURCE.flush(context);
            } catch (Throwable throwable) {
                LOGGER.error("HUD render failed", throwable);
            }
        }
    }

    public static void runOnRenderThread(Runnable task) {
        if (Thread.currentThread() == renderThread) {
            task.run();
        } else {
            queueTask(task);
        }
    }

    public static Object callOnRenderThread(Supplier supplier) {
        if (renderThread != null && Thread.currentThread() != renderThread) {
            CompletableFuture future = new CompletableFuture();
            queueTask((Runnable)() -> {
                try {
                    future.complete(supplier.get());
                } catch (Throwable throwable) {
                    future.completeExceptionally(throwable);
                }
            });

            try {
                return future.get(30L, TimeUnit.SECONDS);
            } catch (InterruptedException interrupted) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("interrupted waiting on render thread", interrupted);
            } catch (TimeoutException timeout) {
                throw new RuntimeException("timed out waiting for render thread to execute supplier", timeout);
            } catch (ExecutionException executionException) {
                Object cause = executionException.getCause() == null ? executionException : executionException.getCause();
                if (cause instanceof RuntimeException) {
                    throw (RuntimeException)cause;
                } else {
                    throw new RuntimeException((Throwable)cause);
                }
            }
        } else {
            return supplier.get();
        }
    }

    public static void setRenderThread(Thread thread) {
        renderThread = thread;
    }

    public static Thread getRenderThread() {
        return renderThread;
    }

    public static long getFrameCount() {
        return frameCount;
    }

    public static void nextFrame() {
        frameCount++;
    }
}
