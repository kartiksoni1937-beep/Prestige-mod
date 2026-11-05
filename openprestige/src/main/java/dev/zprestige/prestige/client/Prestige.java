package dev.zprestige.prestige.client;

import dev.zprestige.prestige.EventBus;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.ModuleManager;
import dev.zprestige.prestige.Native;
import dev.zprestige.prestige.combat.ClickScheduler;
import dev.zprestige.prestige.combat.ExplosionDamageCalculator;
import dev.zprestige.prestige.combat.VisualRotationManager;
import dev.zprestige.prestige.combat.BotFilter;
import dev.zprestige.prestige.combat.TargetTracker;
import dev.zprestige.prestige.config.ConfigManager;
import dev.zprestige.prestige.event.ModuleStateSync;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.Render2DEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.gui.ClickGuiScreen;
import dev.zprestige.prestige.gui.HudEditorScreen;
import dev.zprestige.prestige.input.KeybindManager;
import dev.zprestige.prestige.network.SilentSlotManager;
import dev.zprestige.prestige.network.InventorySwapQueue;
import dev.zprestige.prestige.network.TotemPopManager;
import dev.zprestige.prestige.render.EspShaderManager;
import dev.zprestige.prestige.render.FontManager;
import dev.zprestige.prestige.render.Render2D;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.RenderEvents;
import dev.zprestige.prestige.render.RenderCallback;
import dev.zprestige.prestige.render.ShapeRenderer;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.SilentRotationManager;
import dev.zprestige.prestige.social.FriendManager;
import dev.zprestige.prestige.util.SimulatedClickManager;
import dev.zprestige.prestige.util.FrameDeltaTimer;
import dev.zprestige.prestige.util.NotificationManager;
import java.util.concurrent.atomic.AtomicBoolean;
import net.fabricmc.api.ModInitializer;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.gui.screen.ChatScreen;

public class Prestige implements ModInitializer {
    private static final AtomicBoolean initialized = new AtomicBoolean(false);
    private static final java.util.concurrent.atomic.AtomicInteger renderErrorCount = new java.util.concurrent.atomic.AtomicInteger();
    public static EventBus eventBus;
    public static FontManager fontManager;
    public static ModuleManager moduleManager;
    public static ConfigManager configManager;
    public static ExplosionDamageCalculator damageCalculator;
    public static FriendManager friendManager;
    public static FrameDeltaTimer frameDeltaTimer;
    public static BotFilter botFilter;
    public static ClickScheduler clickScheduler;
    public static ClickGuiScreen clickGuiScreen;
    public static HudEditorScreen hudEditorScreen;
    public static ModuleStateSync moduleStateSync;
    public static TargetTracker targetTracker;
    public static SilentRotationManager silentRotationManager;
    public static VisualRotationManager visualRotationManager;
    public static SimulatedClickManager simulatedClickManager;
    public static TotemPopManager totemPopManager;
    public static KeybindManager keybindManager;
    public static SilentSlotManager silentSlotManager;
    public static InventorySwapQueue inventorySwapQueue;
    public static NotificationManager notificationManager;
    public static boolean selfDestructed = false;

    public void init() {
        boolean partiallyInitialized = false;
        try {
        if (initialized.compareAndSet(false, true)) {
            eventBus = new EventBus();
            moduleManager = new ModuleManager();
            Native.registerModules(moduleManager.getModules());
            fontManager = new FontManager();
            configManager = new ConfigManager();
            damageCalculator = new ExplosionDamageCalculator();
            friendManager = new FriendManager();
            frameDeltaTimer = new FrameDeltaTimer();
            botFilter = new BotFilter();
            clickScheduler = new ClickScheduler();
            clickGuiScreen = new ClickGuiScreen();
            partiallyInitialized = true;
            hudEditorScreen = new HudEditorScreen();
            moduleStateSync = new ModuleStateSync();
            targetTracker = new TargetTracker();
            silentRotationManager = new SilentRotationManager();
            visualRotationManager = new VisualRotationManager();
            simulatedClickManager = new SimulatedClickManager();
            totemPopManager = new TotemPopManager();
            silentSlotManager = new SilentSlotManager();
            inventorySwapQueue = new InventorySwapQueue();
            keybindManager = new KeybindManager();
            notificationManager = new NotificationManager();
            RenderCore.setRenderThread(Thread.currentThread());
            RenderEvents.HUD_RENDER
                .register(
                    (RenderCallback)(hudRenderer, hudContext) -> {
                        if (!selfDestructed) {
                            try {
                                if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
                                    if ((MinecraftAccess.mc.currentScreen == null || MinecraftAccess.mc.currentScreen instanceof ChatScreen)
                                        && eventBus.hasListeners(Render2DEvent.class)) {
                                        new Render2DEvent((hudRenderer), (hudContext)).post();
                                    }

                                    new FrameEvent((hudRenderer), (hudContext)).post();
                                    ShapeRenderer.flushDrawQueue();
                                }
                            } catch (Throwable error) {
                                logRenderError(error);
                            }
                        }
                    }
                );
            RenderEvents.WORLD_RENDER.register((RenderCallback)(worldRenderer, worldContext) -> {
                if (!selfDestructed) {
                    try {
                        if (MinecraftClient.getInstance().world != null && MinecraftClient.getInstance().player != null) {
                            Render2D.captureViewport();
                            new Render3DEvent((worldRenderer), (worldContext)).post();
                        }
                    } catch (Throwable error) {
                        logRenderError(error);
                    }
                }
            });
        }
        } catch (Throwable error) {
            if (!partiallyInitialized) {
                initialized.set(false);
            }

            error.printStackTrace();
        }
    }

    public static void logRenderError(Throwable error) {
        if (renderErrorCount.getAndIncrement() < 5) {
            error.printStackTrace();
        }
    }

    public static boolean needsWorldRender() {
        return EspShaderManager.enabled || eventBus != null && eventBus.hasListeners(Render3DEvent.class);
    }

    public static boolean needsHudRender() {
        return eventBus == null
            ? false
            : needsWorldRender()
                || eventBus.hasListeners(Render2DEvent.class)
                || eventBus.hasListenerOfType(FrameEvent.class, Module.class)
                || hudEditorScreen != null && hudEditorScreen.shouldRenderHud()
                || notificationManager != null && notificationManager.hasNotifications();
    }

    public static void postFrameEvent() {
        if (!selfDestructed && eventBus != null && MinecraftAccess.mc.world != null && MinecraftAccess.mc.player != null) {
            new FrameEvent(RenderCore.BUFFER_SOURCE, null).post();
        }
    }

    @Override
    public void onInitialize() {
        this.init();
    }
}
