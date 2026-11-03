package dev.zprestige.prestige.util;

import com.mojang.authlib.GameProfile;
import dev.zprestige.prestige.EventBus;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.combat.VisualRotationManager;
import dev.zprestige.prestige.config.ClasspathResourcePack;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.BlockVisibilityEvent;
import dev.zprestige.prestige.event.BlockStateUpdateEvent;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.SkyColorEvent;
import dev.zprestige.prestige.event.CameraPositionEvent;
import dev.zprestige.prestige.event.MouseUpdateEvent;
import dev.zprestige.prestige.event.VelocityUpdateEvent;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.BoundingBoxEvent;
import dev.zprestige.prestige.event.EntityAddRemoveEvent;
import dev.zprestige.prestige.event.PlayerRenderStateEvent;
import dev.zprestige.prestige.event.LabelTextEvent;
import dev.zprestige.prestige.event.RenderLabelEvent;
import dev.zprestige.prestige.event.FogEvent;
import dev.zprestige.prestige.event.ExplosionEvent;
import dev.zprestige.prestige.event.CameraRotationEvent;
import dev.zprestige.prestige.event.SendMovementPacketsEvent;
import dev.zprestige.prestige.event.TravelEvent;
import dev.zprestige.prestige.event.ReachEvent;
import dev.zprestige.prestige.event.SwingArmEvent;
import dev.zprestige.prestige.event.MovementYawEvent;
import dev.zprestige.prestige.event.FloatingItemTickEvent;
import dev.zprestige.prestige.event.KeyEvent;
import dev.zprestige.prestige.event.HandSwingDurationEvent;
import dev.zprestige.prestige.event.MouseButtonEvent;
import dev.zprestige.prestige.event.ShieldBreakEvent;
import dev.zprestige.prestige.event.TimeOfDayEvent;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.event.HealthIndicatorEvent;
import dev.zprestige.prestige.event.SkinTexturesEvent;
import dev.zprestige.prestige.event.TextReplaceEvent;
import dev.zprestige.prestige.event.FireOverlayEvent;
import dev.zprestige.prestige.event.HandleBlockBreakingEvent;
import dev.zprestige.prestige.event.ItemBobbingEvent;
import dev.zprestige.prestige.event.ChunkDataEvent;
import dev.zprestige.prestige.event.RenderCrosshairEvent;
import dev.zprestige.prestige.event.KeyboardInputTickEvent;
import dev.zprestige.prestige.event.RenderStatusEffectsEvent;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.FullBrightEvent;
import dev.zprestige.prestige.event.HurtCamEvent;
import dev.zprestige.prestige.event.AddParticleEvent;
import dev.zprestige.prestige.event.FrustumSetupEvent;
import dev.zprestige.prestige.event.RainGradientEvent;
import dev.zprestige.prestige.event.OverlayMessageEvent;
import dev.zprestige.prestige.event.ResetAttackCooldownEvent;
import dev.zprestige.prestige.event.ResolutionChangedEvent;
import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.event.WorldRendererReloadEvent;
import dev.zprestige.prestige.event.RenderScoreboardEvent;
import dev.zprestige.prestige.event.PreTickEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.event.ScreenBackgroundEvent;
import dev.zprestige.prestige.inventory.PlayerInteractions;
import dev.zprestige.prestige.network.InventorySwapQueue;
import dev.zprestige.prestige.reflect.CameraAccessor;
import dev.zprestige.prestige.render.EspShaderManager;
import dev.zprestige.prestige.render.GlStateSnapshot;
import dev.zprestige.prestige.render.PostShader;
import dev.zprestige.prestige.render.Rotation;
import dev.zprestige.prestige.render.GameRendererAccessor;
import dev.zprestige.prestige.render.GuiRendererAccessor;
import dev.zprestige.prestige.render.ProjectionMatrixAccessor;
import dev.zprestige.prestige.render.RenderLayerUtil;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.SilentRotationManager;
import dev.zprestige.prestige.type.EventPhase;
import dev.zprestige.prestige.world.PositionUtil;
import dev.zprestige.prestige.world.HeightContextAccessor;
import java.awt.Color;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import net.minecraft.block.Block;
import net.minecraft.block.BlockState;
import net.minecraft.client.gui.hud.InGameOverlayRenderer;
import net.minecraft.client.gui.render.GuiRenderer;
import net.minecraft.client.render.Camera;
import net.minecraft.client.render.Frustum;
import net.minecraft.client.render.GameRenderer;
import net.minecraft.client.render.ProjectionMatrix2;
import net.minecraft.client.render.RenderLayer;
import net.minecraft.client.render.VertexConsumer;
import net.minecraft.client.render.entity.model.EntityModel;
import net.minecraft.client.util.Window;
import net.minecraft.client.util.math.MatrixStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.LivingEntity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.entity.player.SkinTextures;
import net.minecraft.network.packet.Packet;
import net.minecraft.resource.Resource;
import net.minecraft.text.Text;
import net.minecraft.util.Arm;
import net.minecraft.util.Identifier;
import net.minecraft.util.PlayerInput;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.Box;
import net.minecraft.util.math.Vec3d;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Matrix4fc;
import org.joml.Vector4f;

public class HookBridge {
    public static Resource loadGlowProgramJson(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static boolean hasMouseMoveListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static GlStateSnapshot captureWorldGlState() {
        return GlStateSnapshot.capture();
    }

    public static InGameOverlayRenderer getOverlayRenderer(GameRendererAccessor accessor) {
        return accessor.getOverlayRenderer();
    }

    public static Color getFogColorForBlue(FogEvent event) {
        return event.getColor();
    }

    public static boolean postChunkOcclusionEvent(BlockVisibilityEvent event) {
        return event.post();
    }

    public static boolean postStatusEffectOverlayEvent(RenderStatusEffectsEvent event) {
        return event.post();
    }

    public static boolean hasHudRenderWork() {
        return Prestige.needsHudRender();
    }

    public static Rotation getJumpRotation(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getActiveRotation();
    }

    public static void setHeightContextMinY(HeightContextAccessor accessor, int minY) {
        accessor.setMinY(minY);
    }

    public static boolean postFallbackClientTickEvent(ClientTickEvent event) {
        return event.post();
    }

    public static CameraPositionEvent createCameraPosEvent(double x, double y, double z) {
        return new CameraPositionEvent(x, y, z);
    }

    public static boolean postBlockBreakingEvent(HandleBlockBreakingEvent event) {
        return event.post();
    }

    public static void setCameraPos(CameraAccessor accessor, double x, double y, double z) {
        accessor.setPos(x, y, z);
    }

    public static boolean postExplosionEvent(ExplosionEvent event) {
        return event.post();
    }

    public static ResetAttackCooldownEvent createAttackCooldownResetEvent() {
        return new ResetAttackCooldownEvent();
    }

    public static boolean postRainGradientEvent(RainGradientEvent event) {
        return event.post();
    }

    public static BlockVisibilityEvent createChunkOcclusionEvent(Block block) {
        return new BlockVisibilityEvent(block);
    }

    public static boolean postInputTickPreEvent(KeyboardInputTickEvent event) {
        return event.post();
    }

    public static boolean hasAddParticleListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static Vec3d getUpdateVelocityInputOrNull(MovementYawEvent event) {
        return event.getMovementInput();
    }

    public static boolean postDoAttackEvent(DoAttackEvent event) {
        return event.post();
    }

    public static Predicate getTargetableEntityPredicate() {
        return ReachEvent.getHittableFilter();
    }

    public static boolean hasOverlayMessageListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static ClientTickEvent createClientTickEvent() {
        return new ClientTickEvent();
    }

    public static SwingHandEvent createSwingHandPostEvent(EventPhase phase) {
        return new SwingHandEvent(phase);
    }

    public static boolean postLabelTextEvent(LabelTextEvent event) {
        return event.post();
    }

    public static boolean postMouseButtonEvent(MouseButtonEvent event) {
        return event.post();
    }

    public static ResolutionChangedEvent createResolutionChangedEvent(Window window) {
        return new ResolutionChangedEvent(window);
    }

    public static boolean postEntityAddedEvent(EntityAddRemoveEvent event) {
        return event.post();
    }

    public static Optional getRenderLayerTextureId(RenderLayer renderLayer) {
        return RenderLayerUtil.getTextureId(renderLayer);
    }

    public static Color createSkyColor(int red, int green, int blue) {
        return new Color(red, green, blue);
    }

    public static ItemBobbingEvent createSetBobbingTimeEvent() {
        return new ItemBobbingEvent();
    }

    public static SwingHandEvent createSwingHandPreEvent(EventPhase phase) {
        return new SwingHandEvent(phase);
    }

    public static Vec3d getUpdateVelocityInput(MovementYawEvent event) {
        return event.getMovementInput();
    }

    public static FrustumSetupEvent createFrustumEvent(Frustum frustum) {
        return new FrustumSetupEvent(frustum);
    }

    public static boolean hasSkyColorListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasRenderLabelListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static Resource loadPrestigeResource(Identifier identifier) {
        return ClasspathResourcePack.createAssetResource(identifier);
    }

    public static float getMovementPacketsPitch(SendMovementPacketsEvent event) {
        return event.getPitch();
    }

    public static boolean hasChunkOcclusionListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static FogEvent createFogColorEvent(float fogStart, float fogEnd, Color fogColor) {
        return new FogEvent(fogStart, fogEnd, fogColor);
    }

    public static GuiRendererAccessor createGuiRendererAccessor(GuiRenderer guiRenderer) {
        return new GuiRendererAccessor(guiRenderer);
    }

    public static AttackEntityEvent createAttackEntityEvent(Entity target) {
        return new AttackEntityEvent(target);
    }

    public static float getFogRenderDistanceEnd(FogEvent event) {
        return event.getEnd();
    }

    public static boolean postTextReplaceEvent(TextReplaceEvent event) {
        return event.post();
    }

    public static AddParticleEvent createAddParticleEvent() {
        return new AddParticleEvent();
    }

    public static MatrixStack createCameraMatrixStack() {
        return new MatrixStack();
    }

    public static boolean postCrosshairRenderEvent(RenderCrosshairEvent event) {
        return event.post();
    }

    public static DoItemUseEvent createItemUseEvent() {
        return new DoItemUseEvent();
    }

    public static boolean postUpdateVelocityEvent(MovementYawEvent event) {
        return event.post();
    }

    public static boolean postSwingArmEvent(SwingArmEvent event) {
        return event.post();
    }

    public static boolean postResolutionChangedEvent(ResolutionChangedEvent event) {
        return event.post();
    }

    public static boolean postBoundingBoxEvent(BoundingBoxEvent event) {
        return event.post();
    }

    public static boolean postHealthIndicatorEvent(HealthIndicatorEvent event) {
        return event.post();
    }

    public static double getSetVelocityY(VelocityUpdateEvent event) {
        return event.getVelocityY();
    }

    public static VelocityUpdateEvent createSetVelocityEvent(double x, double y, double z) {
        return new VelocityUpdateEvent(x, y, z);
    }

    public static boolean postMouseMoveEvent(MouseUpdateEvent event) {
        return event.post();
    }

    public static Rotation getUpdateVelocityRotation(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getActiveRotation();
    }

    public static boolean postCameraPosEvent(CameraPositionEvent event) {
        return event.post();
    }

    public static boolean shouldCaptureCameraMatrices() {
        return Prestige.needsHudRender();
    }

    public static SkinTextures getPlayerSkinTextures(SkinTexturesEvent event) {
        return event.getSkinTextures();
    }

    public static RenderStatusEffectsEvent createStatusEffectOverlayEvent() {
        return new RenderStatusEffectsEvent();
    }

    public static MouseButtonEvent createMouseButtonEvent(int button, int action) {
        return new MouseButtonEvent(button, action);
    }

    public static boolean hasSwingArmListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasLightmapListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static TravelEvent createTravelPostEvent(EventPhase phase, float yaw, float pitch) {
        return new TravelEvent(phase, yaw, pitch);
    }

    public static RenderLabelEvent createRenderLabelEvent(Text originalName, Text displayName, Entity entity) {
        return new RenderLabelEvent(originalName, displayName, entity);
    }

    public static RenderScoreboardEvent createScoreboardRenderEvent() {
        return new RenderScoreboardEvent();
    }

    public static void fireIdleFrameEventAfterTasks() {
        Prestige.postFrameEvent();
    }

    public static HeightContextAccessor createHeightContextAccessor(Object heightContext) {
        return new HeightContextAccessor(heightContext);
    }

    public static ExplosionEvent createExplosionEvent(float power, double x, double y, double z) {
        return new ExplosionEvent(power, x, y, z);
    }

    public static Color getFogColorForGreen(FogEvent event) {
        return event.getColor();
    }

    public static void runWorldRenderTasks(List tasks) {
        RenderCore.runTasks(tasks);
    }

    public static Rotation getRenderStateRotation(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getServerRotation();
    }

    public static Resource loadGlowFragmentShader(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static float getRotationVecPitch(Rotation rotation) {
        return rotation.getPitch();
    }

    public static boolean postAmbientOcclusionEvent(BlockVisibilityEvent event) {
        return event.post();
    }

    public static boolean postShieldBreakEvent(ShieldBreakEvent event) {
        return event.post();
    }

    public static boolean hasHandSwingDurationListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static HandleBlockBreakingEvent createBlockBreakingEvent() {
        return new HandleBlockBreakingEvent();
    }

    public static WorldRendererReloadEvent createWorldRendererReloadEvent() {
        return new WorldRendererReloadEvent();
    }

    public static boolean hasPacketReceiveListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static String getProfileName(GameProfile profile) {
        return PositionUtil.getProfileName(profile);
    }

    public static boolean postCameraRotationEvent(CameraRotationEvent event) {
        return event.post();
    }

    public static DoAttackEvent createDoAttackEvent() {
        return new DoAttackEvent();
    }

    public static Vec3d createVelocityVector(double x, double y, double z) {
        return new Vec3d(x, y, z);
    }

    public static String getTextReplaceTarget(TextReplaceEvent event) {
        return event.getTarget();
    }

    public static boolean postLightmapEvent(FullBrightEvent event) {
        return event.post();
    }

    public static boolean postPreTickEvent(PreTickEvent event) {
        return event.post();
    }

    public static boolean isRenderRotationApplied(VisualRotationManager visualRotationManager) {
        return visualRotationManager.isRotationApplied();
    }

    public static Resource loadNamespaceResource(Identifier identifier) {
        return ClasspathResourcePack.createAssetResource(identifier);
    }

    public static double getCameraPosY(CameraPositionEvent event) {
        return event.getY();
    }

    public static boolean postGetBobbingTimeEvent(ItemBobbingEvent event) {
        return event.post();
    }

    public static boolean postKeyEvent(KeyEvent event) {
        return event.post();
    }

    public static KeyboardInputTickEvent createInputTickPreEvent(EventPhase phase) {
        return new KeyboardInputTickEvent(phase);
    }

    public static SkyColorEvent createSkyColorEvent(Color skyColor, Color horizonColor) {
        return new SkyColorEvent(skyColor, horizonColor);
    }

    public static boolean postTravelPreEvent(TravelEvent event) {
        return event.post();
    }

    public static float getJumpYaw(MovementYawEvent event) {
        return event.getYaw();
    }

    public static ItemBobbingEvent createGetBobbingTimeEvent() {
        return new ItemBobbingEvent();
    }

    public static List drainWorldRenderTasks() {
        return RenderCore.drainTasks();
    }

    public static boolean postItemUseEvent(DoItemUseEvent event) {
        return event.post();
    }

    public static boolean hasFrustumListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static void restoreWorldGlStateOnError(GlStateSnapshot glState) {
        glState.restore();
    }

    public static boolean postMovementPacketsPostEvent(SendMovementPacketsEvent event) {
        return event.post();
    }

    public static boolean postPlayerSkinEvent(SkinTexturesEvent event) {
        return event.post();
    }

    public static GlStateSnapshot captureHudGlState() {
        return GlStateSnapshot.capture();
    }

    public static boolean hasTimeOfDayListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static FullBrightEvent createEntitySkyLightEvent() {
        return new FullBrightEvent();
    }

    public static boolean hasSwingHandListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static CameraMatrices createWorldRenderContext(Matrix4f projection, Matrix4f modelView, Camera camera) {
        return new CameraMatrices(projection, modelView, camera);
    }

    public static ChunkDataEvent createChunkDataEvent(WorldChunk chunk) {
        return new ChunkDataEvent(chunk);
    }

    public static PlayerInput createPlayerInput(boolean forward, boolean backward, boolean left, boolean right, boolean jump, boolean sneak, boolean sprint) {
        return new PlayerInput(forward, backward, left, right, jump, sneak, sprint);
    }

    public static boolean hasReachListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static RenderCrosshairEvent createCrosshairRenderEvent() {
        return new RenderCrosshairEvent();
    }

    public static PlayerRenderStateEvent createRenderStatePostEvent(Entity entity, EventPhase phase) {
        return new PlayerRenderStateEvent(entity, phase);
    }

    public static void restoreHudGlState(GlStateSnapshot glState) {
        glState.restore();
    }

    public static int getExtraFloatingItemTicks(FloatingItemTickEvent event) {
        return event.getExtraTicks();
    }

    public static Resource loadBlurFragmentShader(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static SendMovementPacketsEvent createMovementPacketsPreEvent(EventPhase phase, float yaw, float pitch) {
        return new SendMovementPacketsEvent(phase, yaw, pitch);
    }

    public static PreTickEvent createPreTickEvent() {
        return new PreTickEvent();
    }

    public static boolean hasRainGradientListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasSetBobbingTimeListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasHudRenderWorkInWorldPass() {
        return Prestige.needsHudRender();
    }

    public static MovementYawEvent createJumpYawEvent(float yaw) {
        return new MovementYawEvent(yaw);
    }

    public static boolean hasRenderStatePreListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasPlayerSkinListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static TextReplaceEvent createTextReplaceEvent(String search, String text) {
        return new TextReplaceEvent(search, text);
    }

    public static Color getFogColorForRed(FogEvent event) {
        return event.getColor();
    }

    public static boolean hasTextDisplayLabelListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postAddParticleEvent(AddParticleEvent event) {
        return event.post();
    }

    public static Resource loadRoundedRectFragmentShader(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static HurtCamEvent createHurtCamEvent() {
        return new HurtCamEvent();
    }

    public static Resource loadGlowPostJson(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static boolean hasFogColorListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postTextDisplayLabelEvent(LabelTextEvent event) {
        return event.post();
    }

    public static boolean hasFloatingItemTickListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static HandSwingDurationEvent createHandSwingDurationEvent(int duration) {
        return new HandSwingDurationEvent(duration);
    }

    public static PlayerRenderStateEvent createRenderStatePreEvent(Entity entity, EventPhase phase) {
        return new PlayerRenderStateEvent(entity, phase);
    }

    public static String getTextReplacement(TextReplaceEvent event) {
        return event.getText();
    }

    public static boolean postScoreboardRenderEvent(RenderScoreboardEvent event) {
        return event.post();
    }

    public static VertexConsumer wrapWithOutlineConsumer(VertexConsumer consumer, int textureId) {
        return RenderLayerUtil.withOutlineBuffer(consumer, textureId);
    }

    public static boolean hasEntitySkyLightListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static float getLockedRotationPitch(Rotation rotation) {
        return rotation.getPitch();
    }

    public static LabelTextEvent createLabelTextEvent(Entity entity, Text displayName) {
        return new LabelTextEvent(entity, displayName);
    }

    public static RainGradientEvent createRainGradientEvent() {
        return new RainGradientEvent();
    }

    public static boolean hasLabelTextListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postSwingHandPreEvent(SwingHandEvent event) {
        return event.post();
    }

    public static HealthIndicatorEvent createHealthIndicatorEvent(PlayerEntity player, String suffix) {
        return new HealthIndicatorEvent(player, suffix);
    }

    public static Vector4f createFogBufferColor(float red, float green, float blue, float alpha) {
        return new Vector4f(red, green, blue, alpha);
    }

    public static boolean hasWorldRendererReloadListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static GameRendererAccessor createGameRendererAccessor(GameRenderer gameRenderer) {
        return new GameRendererAccessor(gameRenderer);
    }

    public static BlockStateUpdateEvent createBlockUpdateEvent(BlockPos pos, BlockState state) {
        return new BlockStateUpdateEvent(pos, state);
    }

    public static void restoreHudGlStateAfterTasks(GlStateSnapshot glState) {
        glState.restore();
    }

    public static void restoreWorldGlState(GlStateSnapshot glState) {
        glState.restore();
    }

    public static float getCameraYaw(CameraRotationEvent event) {
        return event.getYaw();
    }

    public static TravelEvent createTravelPreEvent(EventPhase phase, float yaw, float pitch) {
        return new TravelEvent(phase, yaw, pitch);
    }

    public static FullBrightEvent createLightmapEvent() {
        return new FullBrightEvent();
    }

    public static Text getTextDisplayLabelText(LabelTextEvent event) {
        return event.getText();
    }

    public static String getHealthIndicatorSuffix(HealthIndicatorEvent event) {
        return event.getSuffix();
    }

    public static ReachEvent createReachEvent(float extraReach) {
        return new ReachEvent(extraReach);
    }

    public static boolean postDrawSideEvent(BlockVisibilityEvent event) {
        return event.post();
    }

    public static boolean postFireOverlayEvent(FireOverlayEvent event) {
        return event.post();
    }

    public static boolean postBlockUpdateEvent(BlockStateUpdateEvent event) {
        return event.post();
    }

    public static boolean hasRenderStatePostListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean shouldDrawSide(BlockVisibilityEvent event) {
        return event.isVisible();
    }

    public static CameraMatrices createHudRenderContext(Matrix4f projection, Matrix4f modelView, Camera camera) {
        return new CameraMatrices(projection, modelView, camera);
    }

    public static boolean postJumpYawEvent(MovementYawEvent event) {
        return event.post();
    }

    public static boolean postSetBobbingTimeEvent(ItemBobbingEvent event) {
        return event.post();
    }

    public static Color createHorizonColor(int red, int green, int blue) {
        return new Color(red, green, blue);
    }

    public static OverlayMessageEvent createOverlayMessageEvent() {
        return new OverlayMessageEvent();
    }

    public static double getCameraPosX(CameraPositionEvent event) {
        return event.getX();
    }

    public static double getCameraPosZ(CameraPositionEvent event) {
        return event.getZ();
    }

    public static ProjectionMatrixAccessor createProjectionMatrixAccessor(ProjectionMatrix2 projectionMatrix) {
        return new ProjectionMatrixAccessor(projectionMatrix);
    }

    public static Matrix4f copyModelViewMatrix(Matrix4fc modelView) {
        return new Matrix4f(modelView);
    }

    public static boolean postScreenBackgroundEvent(ScreenBackgroundEvent event) {
        return event.post();
    }

    public static PostShader getEspPostShader() {
        return EspShaderManager.getActiveShader();
    }

    public static Resource loadRoundedRectGlowFragmentShader(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static boolean postInputTickPostEvent(KeyboardInputTickEvent event) {
        return event.post();
    }

    public static FogEvent createFogBufferEvent(float fogStart, float fogEnd, Color fogColor) {
        return new FogEvent(fogStart, fogEnd, fogColor);
    }

    public static BlockVisibilityEvent createDrawSideEvent(Block block) {
        return new BlockVisibilityEvent(block);
    }

    public static boolean postClientTickEvent(ClientTickEvent event) {
        return event.post();
    }

    public static Rotation getRotationVecOverride(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getActiveRotation();
    }

    public static float getUpdateVelocityYaw(MovementYawEvent event) {
        return event.getYaw();
    }

    public static boolean hasBoundingBoxListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static FireOverlayEvent createFireOverlayEvent() {
        return new FireOverlayEvent();
    }

    public static boolean postSkyColorEvent(SkyColorEvent event) {
        return event.post();
    }

    public static void advanceFrameCounter() {
        RenderCore.nextFrame();
    }

    public static float getFogEnvironmentalEnd(FogEvent event) {
        return event.getEnd();
    }

    public static void trackOutgoingPacket(InventorySwapQueue swapQueue, Packet packet) {
        swapQueue.onPacketSent(packet);
    }

    public static boolean postMovementPacketsPreEvent(SendMovementPacketsEvent event) {
        return event.post();
    }

    public static float getExtraReach(ReachEvent event) {
        return event.getExtraReach();
    }

    public static Vector4f createFogColorVector(float red, float green, float blue, float alpha) {
        return new Vector4f(red, green, blue, alpha);
    }

    public static boolean shouldRestoreSprint(InventorySwapQueue swapQueue) {
        return swapQueue.consumeResumeSprint();
    }

    public static void setHeightContextHeight(HeightContextAccessor accessor, int height) {
        accessor.setHeight(height);
    }

    public static boolean postRenderStatePreEvent(PlayerRenderStateEvent event) {
        return event.post();
    }

    public static boolean hasGetBobbingTimeListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postPacketReceiveEvent(PacketReceiveEvent event) {
        return event.post();
    }

    public static TimeOfDayEvent createTimeOfDayEvent(long timeOfDay) {
        return new TimeOfDayEvent(timeOfDay);
    }

    public static boolean postFogColorEvent(FogEvent event) {
        return event.post();
    }

    public static void renderWorldPass(CameraMatrices context) {
        RenderCore.renderWorld(context);
    }

    public static void restoreWorldGlStateAfterTasks(GlStateSnapshot glState) {
        glState.restore();
    }

    public static BoundingBoxEvent createBoundingBoxEvent(Entity entity, Box box) {
        return new BoundingBoxEvent(entity, box);
    }

    public static CameraRotationEvent createCameraRotationEvent(float yaw, float pitch) {
        return new CameraRotationEvent(yaw, pitch);
    }

    public static boolean hasFogBufferListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static Text getLabelText(LabelTextEvent event) {
        return event.getText();
    }

    public static PacketSendEvent createPacketSendEvent(Packet packet) {
        return new PacketSendEvent(packet);
    }

    public static boolean hasListEntrySkinListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static SwingArmEvent createSwingArmEvent(MatrixStack matrices, Arm arm, float swingProgress, float equipProgress) {
        return new SwingArmEvent(matrices, arm, swingProgress, equipProgress);
    }

    public static ProjectionMatrix2 getGuiProjectionMatrix(GuiRendererAccessor accessor) {
        return accessor.getProjectionMatrix();
    }

    public static ClientTickEvent createFallbackClientTickEvent() {
        return new ClientTickEvent();
    }

    public static boolean postChunkDataEvent(ChunkDataEvent event) {
        return event.post();
    }

    public static boolean postTimeOfDayEvent(TimeOfDayEvent event) {
        return event.post();
    }

    public static Matrix4f createHudModelViewMatrix() {
        return new Matrix4f();
    }

    public static boolean hasHealthIndicatorListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static CameraAccessor createCameraRotationAccessor(Camera camera) {
        return new CameraAccessor(camera);
    }

    public static Color getSkyColorOrNull(SkyColorEvent event) {
        return event.getSkyColor();
    }

    public static MouseUpdateEvent createMouseMoveEvent(double cursorDeltaX, double cursorDeltaY) {
        return new MouseUpdateEvent(cursorDeltaX, cursorDeltaY);
    }

    public static Color getHorizonColor(SkyColorEvent event) {
        return event.getHorizonColor();
    }

    public static LabelTextEvent createTextDisplayLabelEvent(Entity entity, Text text) {
        return new LabelTextEvent(entity, text);
    }

    public static Color getSkyColor(SkyColorEvent event) {
        return event.getSkyColor();
    }

    public static void captureViewport() {
        dev.zprestige.prestige.render.Render2D.captureViewport();
    }

    public static boolean postEntityRemovedEvent(EntityAddRemoveEvent event) {
        return event.post();
    }

    public static PacketReceiveEvent createPacketReceiveEvent(Packet packet) {
        return new PacketReceiveEvent(packet);
    }

    public static boolean postGameJoinEvent(GameJoinEvent event) {
        return event.post();
    }

    public static ShieldBreakEvent createShieldBreakEvent(LivingEntity entity) {
        return new ShieldBreakEvent(entity);
    }

    public static boolean hasHurtCamListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static KeyEvent createKeyEvent(int key, int action) {
        return new KeyEvent(key, action);
    }

    public static Color getFogColor(FogEvent event) {
        return event.getColor();
    }

    public static boolean postFloatingItemTickEvent(FloatingItemTickEvent event) {
        return event.post();
    }

    public static Matrix4f getProjectionMatrix(ProjectionMatrixAccessor accessor, float width, float height) {
        return accessor.getMatrix(width, height);
    }

    public static boolean shouldBlockMovementInput(InventorySwapQueue swapQueue) {
        return swapQueue.shouldHoldInput();
    }

    public static Float getFogSkyEndOverride(FogEvent event) {
        return event.getSkyEnd();
    }

    public static boolean isEspModelForGlow(EntityModel model) {
        return EspShaderManager.matchesTargetTypes(model);
    }

    public static boolean postPacketSendEvent(PacketSendEvent event) {
        return event.post();
    }

    public static CameraAccessor createCameraPosAccessor(Camera camera) {
        return new CameraAccessor(camera);
    }

    public static boolean postHandSwingDurationEvent(HandSwingDurationEvent event) {
        return event.post();
    }

    public static KeyboardInputTickEvent createInputTickPostEvent(EventPhase phase) {
        return new KeyboardInputTickEvent(phase);
    }

    public static BlockVisibilityEvent createAmbientOcclusionEvent(Block block) {
        return new BlockVisibilityEvent(block);
    }

    public static boolean hasShieldBreakListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static float getRotationVecYaw(Rotation rotation) {
        return rotation.getYaw();
    }

    public static boolean postSetVelocityEvent(VelocityUpdateEvent event) {
        return event.post();
    }

    public static boolean shouldSkipSwingHandPost(SwingHandEvent event) {
        return event.isSkipPost();
    }

    public static void advanceFrameCounterAfterTasks() {
        RenderCore.nextFrame();
    }

    public static boolean hasCameraPosListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static Resource loadRoundedRectShaderJson(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static SkinTextures getListEntrySkinTextures(SkinTexturesEvent event) {
        return event.getSkinTextures();
    }

    public static boolean hasBlockUpdateListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postTravelPostEvent(TravelEvent event) {
        return event.post();
    }

    public static void fireIdleFrameEvent() {
        Prestige.postFrameEvent();
    }

    public static void runHudRenderTasks(List tasks) {
        RenderCore.runTasks(tasks);
    }

    public static boolean postBackgroundTextureEvent(ScreenBackgroundEvent event) {
        return event.post();
    }

    public static ScreenBackgroundEvent createBackgroundTextureEvent() {
        return new ScreenBackgroundEvent();
    }

    public static FloatingItemTickEvent createFloatingItemTickEvent(int extraTicks) {
        return new FloatingItemTickEvent(extraTicks);
    }

    public static boolean postRenderStatePostEvent(PlayerRenderStateEvent event) {
        return event.post();
    }

    public static boolean postOverlayMessageEvent(OverlayMessageEvent event) {
        return event.post();
    }

    public static EntityAddRemoveEvent createEntityRemovedEvent(Entity entity, EventPhase phase) {
        return new EntityAddRemoveEvent(entity, phase);
    }

    public static boolean postFrustumEvent(FrustumSetupEvent event) {
        return event.post();
    }

    public static float getBaseReachDistance() {
        return PlayerInteractions.getReachDistance();
    }

    public static boolean hasCrosshairRenderListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasAmbientOcclusionListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static Resource loadBlurShaderJson(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static void restoreHudGlStateOnError(GlStateSnapshot glState) {
        glState.restore();
    }

    public static GameJoinEvent createGameJoinEvent() {
        return new GameJoinEvent();
    }

    public static double getSetVelocityZ(VelocityUpdateEvent event) {
        return event.getVelocityZ();
    }

    public static Rotation getTravelPreRotation(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getActiveRotation();
    }

    public static List drainHudRenderTasks() {
        return RenderCore.drainTasks();
    }

    public static float getFogRenderDistanceStart(FogEvent event) {
        return event.getStart();
    }

    public static Color getHorizonColorOrNull(SkyColorEvent event) {
        return event.getHorizonColor();
    }

    public static SkinTexturesEvent createListEntrySkinEvent(SkinTextures skinTextures, String playerName) {
        return new SkinTexturesEvent(skinTextures, playerName);
    }

    public static float getMovementPacketsYaw(SendMovementPacketsEvent event) {
        return event.getYaw();
    }

    public static boolean postHurtCamEvent(HurtCamEvent event) {
        return event.post();
    }

    public static SkinTexturesEvent createPlayerSkinEvent(SkinTextures skinTextures, String playerName) {
        return new SkinTexturesEvent(skinTextures, playerName);
    }

    public static void setCameraRotation(CameraAccessor accessor, float yaw, float pitch) {
        accessor.setRotation(yaw, pitch);
    }

    public static EntityAddRemoveEvent createEntityAddedEvent(Entity entity, EventPhase phase) {
        return new EntityAddRemoveEvent(entity, phase);
    }

    public static boolean postListEntrySkinEvent(SkinTexturesEvent event) {
        return event.post();
    }

    public static boolean postEntitySkyLightEvent(FullBrightEvent event) {
        return event.post();
    }

    public static float getFogEnvironmentalStart(FogEvent event) {
        return event.getStart();
    }

    public static boolean postRenderLabelEvent(RenderLabelEvent event) {
        return event.post();
    }

    public static float getLockedRotationYaw(Rotation rotation) {
        return rotation.getYaw();
    }

    public static long getTimeOfDay(TimeOfDayEvent event) {
        return event.getTime();
    }

    public static boolean isEspModelForVisibility(EntityModel model) {
        return EspShaderManager.isTargetModel(model);
    }

    public static boolean postAttackCooldownResetEvent(ResetAttackCooldownEvent event) {
        return event.post();
    }

    public static Rotation getLockedMovementRotation(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getLockedRotation();
    }

    public static boolean hasPacketSendListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postFogBufferEvent(FogEvent event) {
        return event.post();
    }

    public static void renderHudPass(CameraMatrices context) {
        RenderCore.renderHud(context);
    }

    public static boolean hasScoreboardRenderListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean hasWorldRenderWork() {
        return Prestige.needsWorldRender();
    }

    public static boolean postReachEvent(ReachEvent event) {
        return event.post();
    }

    public static Resource loadRoundedRectGlowShaderJson(String path) {
        return ClasspathResourcePack.createResource(path);
    }

    public static Color getFogColorForAlpha(FogEvent event) {
        return event.getColor();
    }

    public static MovementYawEvent createUpdateVelocityEvent(float yaw, Vec3d movementInput) {
        return new MovementYawEvent(yaw, movementInput);
    }

    public static boolean hasTextReplaceListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static boolean postAttackEntityEvent(AttackEntityEvent event) {
        return event.post();
    }

    public static boolean postSwingHandPostEvent(SwingHandEvent event) {
        return event.post();
    }

    public static double getSetVelocityX(VelocityUpdateEvent event) {
        return event.getVelocityX();
    }

    public static ScreenBackgroundEvent createScreenBackgroundEvent() {
        return new ScreenBackgroundEvent();
    }

    public static SendMovementPacketsEvent createMovementPacketsPostEvent(EventPhase phase, float yaw, float pitch) {
        return new SendMovementPacketsEvent(phase, yaw, pitch);
    }

    public static boolean postWorldRendererReloadEvent(WorldRendererReloadEvent event) {
        return event.post();
    }

    public static float getCameraPitch(CameraRotationEvent event) {
        return event.getPitch();
    }

    public static boolean hasDrawSideListeners(EventBus eventBus, Class eventClass) {
        return eventBus.hasListeners(eventClass);
    }

    public static Box getModifiedBoundingBox(BoundingBoxEvent event) {
        return event.getBox();
    }

    public static Rotation getTravelPostRotation(SilentRotationManager silentRotationManager) {
        return silentRotationManager.getActiveRotation();
    }
}
