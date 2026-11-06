package dev.zprestige.prestige.gui;

import dev.zprestige.prestige.Native;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.render.BufferSource;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.RenderCallback;
import dev.zprestige.prestige.render.RenderEvents;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.ui.Input;
import dev.zprestige.prestige.ui.Lib;
import dev.zprestige.prestige.ui.Theme;
import dev.zprestige.prestige.ui.Ui;
import dev.zprestige.prestige.ui.mc.Icons;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import net.fabricmc.loader.api.FabricLoader;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.DrawContext;
import org.joml.Matrix4f;

/**
 * The Prestige ClickGUI as a real Minecraft screen, drawn with the client's own renderer.
 *
 * <p>The original client opened a native imgui window (ui/mc/Overlay.java) from the game loop. imgui-java ships
 * desktop-only natives (Windows/Linux x86_64, macOS) and its GL3 backend needs GLFW, so that window cannot exist in
 * PojavLauncher on Android. This screen keeps the original design untouched: ui/Ui.java lays out the same 1100x700
 * canvas, the same sidebar, pages and widgets, and ui/Lib.java replays its primitives through {@link ShapeRenderer}
 * (rounded rectangles, outlines, images) and the bundled MSDF font. Only the host window is new.
 *
 * <p>Coordinates: the UI canvas is scaled by {@link Lib#scale} and placed at {@link Lib#ox}/{@link Lib#oy} inside the
 * GUI-pixel viewport, so the layout is responsive: it fills whatever the device reports (phone, tablet, desktop) and
 * mouse coordinates are converted back into canvas space for {@link Input}.
 */
public class ClickGuiScreen extends BaseScreen implements MinecraftAccess {
    private static final float CANVAS_W = 1100f, CANVAS_H = 700f;

    // ---- window placement: fractions of the free space (0 = left/top edge, 1 = right/bottom edge), persisted
    private static float fracX = 0.5f, fracY = 0.5f;
    private static boolean allowUpscale;
    private static boolean positionLoaded;
    private static boolean callbackRegistered;
    private static boolean dataLoaded;
    private static boolean dataFailed;

    private boolean guiClosed;
    private boolean wasGrabbed;
    private double dragLastX, dragLastY;
    private boolean prevDragging;
    private long lastFrameNanos = System.nanoTime();
    private float lastMouseX = Float.NaN, lastMouseY = Float.NaN;

    public ClickGuiScreen() {
        super();
    }

    public void resetClosedState() {
        this.guiClosed = false;
    }

    // ------------------------------------------------------------------ lifecycle
    @Override
    public void init() {
        super.init();
        this.guiClosed = false;
        loadPosition();
        Input.active = true;
        Input.close = false;
        Input.windowDragging = false;
        Input.ctrlDown = false;
        Input.clipboard = () -> {
            try { return MinecraftClient.getInstance().keyboard.getClipboard(); } catch (Throwable t) { return null; }
        };
        this.wasGrabbed = mc.mouse.isCursorLocked();
        if (this.wasGrabbed) mc.mouse.unlockCursor();
        net.minecraft.client.option.KeyBinding.unpressAll();
        this.prevDragging = false;
        this.dragLastX = this.dragLastY = 0;
        this.lastFrameNanos = System.nanoTime();
        this.lastMouseX = this.lastMouseY = Float.NaN;
        ensureData();
        registerRenderCallback();
        updateLayout();
        Input.mx = (float) ((mc.mouse.getX() - Lib.ox) / Math.max(0.05f, Lib.scale));
        Input.my = (float) ((mc.mouse.getY() - Lib.oy) / Math.max(0.05f, Lib.scale));
        this.lastMouseX = Input.mx;
        this.lastMouseY = Input.my;
        // The original window reset its transient state every time it opened.
        if (Input.down) Input.mouseUp(0);
        Input.down = false;
        Input.close = false;
    }

    @Override
    public void removed() {
        Input.active = false;
        if (Input.down) Input.mouseUp(0);
        Input.down = false;
        Input.windowDragging = false;
        super.removed();
        if (this.wasGrabbed && mc.currentScreen == null && !mc.mouse.isCursorLocked()) mc.mouse.lockCursor();
        this.wasGrabbed = false;
        savePosition();
    }

    @Override
    public boolean shouldPause() {
        return false;
    }

    @Override
    public boolean shouldCloseOnEsc() {
        return false;
    }

    @Override
    public void close() {
        this.guiClosed = true;
        Input.close = false;
        Input.windowDragging = false;
        if (Input.down) Input.mouseUp(0);
        Input.down = false;
        super.close();
    }

    /** Kept for HudEditorScreen/native callers: the menu is open while this screen is the current screen. */
    public boolean isOpen() {
        return mc.currentScreen == this;
    }

    @Override
    public void render(DrawContext context, int mouseX, int mouseY, float deltaTicks) {
        // Rendering happens in the HUD pass (see registerRenderCallback) so the client's GL command queue is active.
        this.pollClose();
    }

    // ------------------------------------------------------------------ layout / persistence
    private Path positionFile() {
        try { return FabricLoader.getInstance().getConfigDir().resolve("prestige_ui.json"); } catch (Throwable t) { return null; }
    }

    private void loadPosition() {
        if (positionLoaded) return;
        positionLoaded = true;
        try {
            Path f = positionFile();
            if (f == null || !Files.exists(f)) return;
            String text = Files.readString(f);
            Matcher mx = Pattern.compile("\"x\"\\s*:\\s*(-?[0-9.]+)").matcher(text);
            Matcher my = Pattern.compile("\"y\"\\s*:\\s*(-?[0-9.]+)").matcher(text);
            if (mx.find()) fracX = Math.max(0f, Math.min(1f, Float.parseFloat(mx.group(1))));
            if (my.find()) fracY = Math.max(0f, Math.min(1f, Float.parseFloat(my.group(1))));
            allowUpscale = Pattern.compile("\"allowUpscale\"\\s*:\\s*true").matcher(text).find();
            Matcher theme = Pattern.compile("\"theme\"\\s*:\\s*(\\d+)").matcher(text);
            if (theme.find()) {
                int index = Integer.parseInt(theme.group(1));
                if (index >= 0 && index < dev.zprestige.prestige.ui.Theme.NAMES.length) {
                    UiThemeMemory.active = index;
                    dev.zprestige.prestige.ui.Theme.active = index;
                }
            }
        } catch (Throwable t) {
            System.out.println("[prestige-ui] could not read the window position: " + t);
        }
    }

    private void savePosition() {
        try {
            Path f = positionFile();
            if (f != null) Files.writeString(f, "{\"x\": " + fracX + ", \"y\": " + fracY + ", \"allowUpscale\": " + allowUpscale
                    + ", \"theme\": " + dev.zprestige.prestige.ui.Theme.active + "}\n");
        } catch (Throwable t) {
            System.out.println("[prestige-ui] could not save the window position: " + t);
        }
    }

    /** Computes Lib.scale/ox/oy from the current GUI viewport. */
    private void updateLayout() {
        float fbW = Math.max(1, mc.getWindow().getScaledWidth());
        float fbH = Math.max(1, mc.getWindow().getScaledHeight());
        float scale = Math.min((fbW - 24f) / CANVAS_W, (fbH - 24f) / CANVAS_H);
        if (!(allowUpscale && fbW > CANVAS_W && fbH > CANVAS_H)) scale = Math.min(1f, scale);
        if (scale < 0.3f) scale = 0.3f;
        scale = Math.round(scale * 50f) / 50f;
        Lib.scale = scale;
        float freeX = fbW - CANVAS_W * scale, freeY = fbH - CANVAS_H * scale;
        float nx = freeX > 0 ? fracX * freeX : freeX * 0.5f;
        float ny = freeY > 0 ? fracY * freeY : freeY * 0.5f;
        if (Input.windowDragging && this.prevDragging) {
            nx += Input.mx - this.lastMouseX;
            ny += Input.my - this.lastMouseY;
            if (freeX > 0) {
                nx = Math.max(0f, Math.min(freeX, nx));
                fracX = nx / freeX;
            }
            if (freeY > 0) {
                ny = Math.max(0f, Math.min(freeY, ny));
                fracY = ny / freeY;
            }
        }
        if (!Input.windowDragging && this.prevDragging) savePosition();
        this.prevDragging = Input.windowDragging;
        Lib.ox = nx;
        Lib.oy = ny;
    }

    private void updateMouseFromWindow() {
        // Minecraft's Mouse reports the cursor in GUI pixels on every launcher, including PojavLauncher's touch pointer.
        float x = (float) mc.mouse.getX();
        float y = (float) mc.mouse.getY();
        if (Float.isNaN(x) || Float.isNaN(y)) return;
        Input.mx = (float) ((x - Lib.ox) / Math.max(0.05f, Lib.scale));
        Input.my = (float) ((y - Lib.oy) / Math.max(0.05f, Lib.scale));
    }

    private void ensureData() {
        if (dataLoaded || dataFailed) return;
        try {
            dev.zprestige.prestige.ui.Data.load(
                    ClickGuiScreen.class.getResourceAsStream("/prestige_ui/modules.tsv"),
                    ClickGuiScreen.class.getResourceAsStream("/prestige_ui/settings.tsv"));
            dev.zprestige.prestige.ui.bridge.Bridge.bind();
            long[] icons = Icons.load();
            Ui.initEntries(icons);
            Theme.active = UiThemeMemory.active;
            dataLoaded = true;
            System.out.println("[prestige-ui] ClickGUI data loaded (" + dev.zprestige.prestige.ui.ModulesPage.P.mods.size() + " modules, bridge=" + dev.zprestige.prestige.ui.bridge.Bridge.available() + ")");
        } catch (Throwable t) {
            // A broken resource must not take down the game; report it once and leave the GUI closed but usable later.
            dataFailed = true;
            System.out.println("[prestige-ui] ClickGUI resources failed to load: " + t);
            t.printStackTrace(System.out);
        }
    }

    private void registerRenderCallback() {
        if (callbackRegistered) return;
        callbackRegistered = true;
        RenderEvents.AFTER_HUD_RENDER.register((RenderCallback) (buffers, context) -> {
            if (mc.currentScreen != this) return;
            try {
                renderGui(buffers, context);
            } catch (Throwable error) {
                Prestige.logRenderError(error);
            }
        });
    }

    // ------------------------------------------------------------------ rendering
    private void renderGui(BufferSource buffers, CameraMatrices context) {
        if (!dataLoaded) return;
        long now = System.nanoTime();
        float dt = Math.min(0.1f, (now - this.lastFrameNanos) / 1e9f);
        this.lastFrameNanos = now;
        updateMouseFromWindow();
        updateLayout();
        Input.update(Math.max(1e-4f, dt));
        Lib.dl.clear();
        Lib.gAlpha = 1f;
        Ui.frame();
        Lib.dl.flush(new Matrix4f());
        // The client's shape/texture commands were queued above; flush them now so the GUI is one frame, not one late.
        ShapeRenderer.flushDrawQueue();
        this.lastMouseX = Input.mx;
        this.lastMouseY = Input.my;
        this.pollClose();
    }

    private void pollClose() {
        if (!Input.close) return;
        Input.close = false;
        close();
    }

    // ------------------------------------------------------------------ input
    @Override
    public void mouseMoved(double mouseX, double mouseY) {
        Input.mx = (float) ((mouseX - Lib.ox) / Math.max(0.05f, Lib.scale));
        Input.my = (float) ((mouseY - Lib.oy) / Math.max(0.05f, Lib.scale));
    }

    @Override
    public boolean mouseClicked(double mouseX, double mouseY, int button) {
        updateCanvasMouse(mouseX, mouseY);
        Input.mouseDown(button);
        this.pollClose();
        return true;
    }

    @Override
    public boolean mouseReleased(double mouseX, double mouseY, int button) {
        updateCanvasMouse(mouseX, mouseY);
        Input.mouseUp(button);
        return true;
    }

    @Override
    public boolean mouseDragged(double mouseX, double mouseY, int button, double offsetX, double offsetY) {
        updateCanvasMouse(mouseX, mouseY);
        return true;
    }

    @Override
    public boolean mouseScrolled(double mouseX, double mouseY, double horizontalAmount, double verticalAmount) {
        updateCanvasMouse(mouseX, mouseY);
        Input.scroll(verticalAmount);
        this.pollClose();
        return true;
    }

    @Override
    public boolean keyPressed(int keyCode, int scanCode, int modifiers) {
        int key = keyCode;
        Input.ctrlDown = (modifiers & 0x2) != 0;
        if (key == 256) {                       // GLFW_KEY_ESCAPE
            Input.escape();
            if (Input.close) {
                Input.close = false;
                close();
            }
            return true;
        }
        Input.key(key);
        this.pollClose();
        return true;
    }

    @Override
    public boolean keyReleased(int keyCode, int scanCode, int modifiers) {
        if (keyCode == 341 || keyCode == 345) Input.ctrlDown = false;
        return true;
    }

    @Override
    public boolean charTyped(char chr, int modifiers) {
        Input.typed(chr);
        this.pollClose();
        return true;
    }

    private void updateCanvasMouse(double guiX, double guiY) {
        Input.mx = (float) ((guiX - Lib.ox) / Math.max(0.05f, Lib.scale));
        Input.my = (float) ((guiY - Lib.oy) / Math.max(0.05f, Lib.scale));
    }

    // ------------------------------------------------------------------ native bridge hooks
    /** Called by {@link Native#toggleMenu()} when the native menu hook is invoked without a screen. */
    public static void openFromNative() {
        MinecraftClient client = MinecraftClient.getInstance();
        if (client == null || Prestige.clickGuiScreen == null) return;
        client.execute(() -> {
            if (client.currentScreen == null) client.setScreen(Prestige.clickGuiScreen);
        });
    }

    /** Remembers the selected theme index so it survives a menu close (the original kept it in its config). */
    static final class UiThemeMemory {
        static int active = 1;
    }
}
