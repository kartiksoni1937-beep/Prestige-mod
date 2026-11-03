package dev.zprestige.prestige.module.visual;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.ColorSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.MultiSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.data.StorageBlockMarker;
import dev.zprestige.prestige.data.CameraMatrices;
import dev.zprestige.prestige.event.ClientTickEvent;
import dev.zprestige.prestige.event.Render3DEvent;
import dev.zprestige.prestige.render.DrawPipeline;
import dev.zprestige.prestige.render.RenderEvents;
import dev.zprestige.prestige.render.RenderCallback;
import dev.zprestige.prestige.render.RenderCore;
import dev.zprestige.prestige.render.StorageOutlineRenderer;
import dev.zprestige.prestige.render.MappedMeshBuilder;
import dev.zprestige.prestige.type.GlPrimitive;
import dev.zprestige.prestige.world.ClientChunkManagerAccessor;
import dev.zprestige.prestige.world.ClientChunkMapAccessor;
import java.awt.Color;
import java.util.ArrayList;
import java.util.List;
import java.util.Map.Entry;
import java.util.concurrent.atomic.AtomicReferenceArray;
import java.util.function.Predicate;
import net.minecraft.block.entity.AbstractFurnaceBlockEntity;
import net.minecraft.block.entity.BarrelBlockEntity;
import net.minecraft.block.entity.BlockEntity;
import net.minecraft.block.entity.BrewingStandBlockEntity;
import net.minecraft.block.entity.ChestBlockEntity;
import net.minecraft.block.entity.DispenserBlockEntity;
import net.minecraft.block.entity.DropperBlockEntity;
import net.minecraft.block.entity.EnderChestBlockEntity;
import net.minecraft.block.entity.HopperBlockEntity;
import net.minecraft.block.entity.ShulkerBoxBlockEntity;
import net.minecraft.block.entity.TrappedChestBlockEntity;
import net.minecraft.client.gui.screen.ChatScreen;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.chunk.WorldChunk;
import org.joml.Matrix4f;
import org.joml.Vector3d;

public class StorageESP extends Module {
    private MultiSetting targets;
    private BoolSetting fill;
    private ColorSetting chestsColor;
    private ColorSetting trappedChestsColor;
    private ColorSetting enderChestsColor;
    private ColorSetting shulkersColor;
    private ColorSetting barrelsColor;
    private ColorSetting hoppersColor;
    private ColorSetting dispensersColor;
    private ColorSetting droppersColor;
    private ColorSetting furnacesColor;
    private ColorSetting brewingStandsColor;
    private static StorageOutlineRenderer outlineRenderer;
    private boolean callbackRegistered = false;
    private List markers = new ArrayList();

    public StorageESP() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
        this.hoppersColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Hoppers")
            );
        this.chestsColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Chests")
            );
        this.barrelsColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Barrels")
            );
        this.brewingStandsColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Brewing Stands")
            );
        this.droppersColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Droppers")
            );
        this.dispensersColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Dispensers")
            );
        this.furnacesColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Furnaces")
            );
        this.shulkersColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Shulker Boxes")
            );
        this.trappedChestsColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Trapped Chests")
            );
        this.enderChestsColor
            .visibleWhen(
                (Predicate<Color>)ignored -> this.targets.isSelected("Ender Chests")
            );
    }

    @Override
    public void onDisable() {
        this.markers.clear();
    }

    @Subscribe
    public void onClientTick(ClientTickEvent event) {
        if (mc.world != null && mc.player != null) {
            this.markers.clear();
            AtomicReferenceArray loadedChunks = new ClientChunkMapAccessor(new ClientChunkManagerAccessor(mc.world.getChunkManager()).getChunkMap()).getChunks();

            for (int chunkIndex = 0; chunkIndex < loadedChunks.length(); chunkIndex++) {
                WorldChunk chunk = (WorldChunk)loadedChunks.get(chunkIndex);
                if (chunk != null) {
                    for (Entry entry : chunk.getBlockEntities().entrySet()) {
                        BlockEntity blockEntity = (BlockEntity)entry.getValue();
                        if (blockEntity != null) {
                            Color color = this.getStorageColor(blockEntity);
                            if (color != null) {
                                BlockPos pos = (BlockPos)entry.getKey();
                                if (pos != null) {
                                    int rgb = color.getRGB();
                                    int abgrColor = 0xFF000000 | (rgb & 0xFF) << 16 | (rgb >> 8 & 0xFF) << 8 | (rgb >> 16 & 0xFF);
                                    this.markers.add(new StorageBlockMarker(pos.getX(), pos.getY(), pos.getZ(), abgrColor));
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    @Override
    public void onEnable() {
        if (!this.callbackRegistered) {
            RenderEvents.AFTER_HUD_RENDER.register((RenderCallback)(buffers, context) -> {
                if (this.isEnabled()) {
                    if (this.canRenderOverlay()) {
                        getOutlineRenderer().renderOutline(2, 15.0F, this.fill.getValue() ? 0.3F : 0.0F);
                    }
                }
            });
            this.callbackRegistered = true;
        }
    }

    @Subscribe
    public void onRender3D(Render3DEvent event) {
        if (mc.world != null && mc.player != null) {
            if (this.canRenderOverlay()) {
                DrawPipeline maskPipeline = getOutlineRenderer().getMaskDrawMode();
                MappedMeshBuilder meshBuilder = event.renderer.getBuffer(maskPipeline);
                Matrix4f matrix = new Matrix4f();
                boolean anyDrawn = false;
                meshBuilder.beginShape();

                for (StorageBlockMarker marker : (Iterable<StorageBlockMarker>)(this.markers)) {
                    int abgrColor = marker.abgrColor();
                    CameraMatrices camera = event.context;
                    double worldX = marker.x();
                    double worldY = marker.y();
                    double worldZ = marker.z();
                    Vector3d minCorner = RenderCore.toCameraRelative(camera, worldX, worldY, worldZ);
                    camera = event.context;
                    worldX = marker.x() + 1.0F;
                    worldY = marker.y() + 1.0F;
                    worldZ = marker.z() + 1.0F;
                    Vector3d maxCorner = RenderCore.toCameraRelative(camera, worldX, worldY, worldZ);
                    float minX = (float)minCorner.x;
                    float minY = (float)minCorner.y;
                    float minZ = (float)minCorner.z;
                    float maxX = (float)maxCorner.x;
                    float maxY = (float)maxCorner.y;
                    float maxZ = (float)maxCorner.z;
                    meshBuilder.appendTransformedPosition(matrix, minX, maxY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, maxY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, maxY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, maxY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, minY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, minY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, minY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, minY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, minY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, minY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, maxY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, maxY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, minY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, maxY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, maxY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, minY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, minY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, minY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, maxY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, minX, maxY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, minY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, maxY, minZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, maxY, maxZ).appendInt(abgrColor).appendTransformedPosition(matrix, maxX, minY, maxZ).appendInt(abgrColor);
                    anyDrawn = true;
                }

                if (anyDrawn) {
                    ((MappedMeshBuilder)meshBuilder.generateIndices(GlPrimitive.QUADS)).clearBaseVertex();
                } else {
                    meshBuilder.clearBaseVertex();
                }
            }
        }
    }

    private boolean canRenderOverlay() {
        return mc.currentScreen == null || mc.currentScreen instanceof ChatScreen;
    }

    private Color getStorageColor(BlockEntity blockEntity) {
        if (blockEntity instanceof TrappedChestBlockEntity) {
            return this.targets.isSelected("Trapped Chests") ? this.trappedChestsColor.getValue() : null;
        } else if (blockEntity instanceof ChestBlockEntity) {
            return this.targets.isSelected("Chests") ? this.chestsColor.getValue() : null;
        } else if (blockEntity instanceof EnderChestBlockEntity) {
            return this.targets.isSelected("Ender Chests") ? this.enderChestsColor.getValue() : null;
        } else if (blockEntity instanceof ShulkerBoxBlockEntity) {
            return this.targets.isSelected("Shulker Boxes") ? this.shulkersColor.getValue() : null;
        } else if (blockEntity instanceof BarrelBlockEntity) {
            return this.targets.isSelected("Barrels") ? this.barrelsColor.getValue() : null;
        } else if (blockEntity instanceof HopperBlockEntity) {
            return this.targets.isSelected("Hoppers") ? this.hoppersColor.getValue() : null;
        } else if (blockEntity instanceof DropperBlockEntity) {
            return this.targets.isSelected("Droppers") ? this.droppersColor.getValue() : null;
        } else if (blockEntity instanceof DispenserBlockEntity) {
            return this.targets.isSelected("Dispensers") ? this.dispensersColor.getValue() : null;
        } else if (blockEntity instanceof AbstractFurnaceBlockEntity) {
            return this.targets.isSelected("Furnaces") ? this.furnacesColor.getValue() : null;
        } else if (blockEntity instanceof BrewingStandBlockEntity) {
            return this.targets.isSelected("Brewing Stands") ? this.brewingStandsColor.getValue() : null;
        } else {
            return null;
        }
    }

    private static synchronized StorageOutlineRenderer getOutlineRenderer() {
        if (outlineRenderer == null) {
            outlineRenderer = new StorageOutlineRenderer();
        }

        return outlineRenderer;
    }
}
