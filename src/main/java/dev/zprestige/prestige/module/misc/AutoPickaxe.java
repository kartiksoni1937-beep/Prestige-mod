package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.RangeSetting;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.inventory.InventoryUtil;
import dev.zprestige.prestige.render.StopWatch;
import net.minecraft.block.Blocks;
import net.minecraft.item.Item;
import net.minecraft.item.Items;
import net.minecraft.util.hit.BlockHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.math.BlockPos;
import org.lwjgl.glfw.GLFW;

public class AutoPickaxe extends Module {
    public AutoPickaxe() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting onlyWhenMining;
    private BoolSetting fromInventory;
    private RangeSetting delay;
    private StopWatch actionTimer = new StopWatch();
    private static final Item[] PICKAXES = new Item[]{
        Items.NETHERITE_PICKAXE, Items.DIAMOND_PICKAXE, Items.IRON_PICKAXE, Items.STONE_PICKAXE, Items.GOLDEN_PICKAXE, Items.WOODEN_PICKAXE
    };
    private int previousSlot = -1;
    private int swappedInventorySlot = -1;

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (mc.player != null && mc.world != null && mc.currentScreen == null && mc.isWindowFocused()) {
            BlockPos belowPos = mc.player.getBlockPos().down();
            boolean notMining = this.onlyWhenMining.getValue() && GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), 0) != 1;
            HitResult hitResult = mc.crosshairTarget;
            if (!(hitResult instanceof BlockHitResult) || !((BlockHitResult)hitResult).getBlockPos().equals(belowPos) || notMining) {
                if (!this.trySwapBack()) {
                    this.resetDelay();
                }
            } else if (!mc.world.getBlockState(belowPos).isReplaceable() && mc.world.getBlockState(belowPos).getBlock() != Blocks.BEDROCK) {
                if (InventoryUtil.isPickaxe(mc.player.getMainHandStack())) {
                    this.resetDelay();
                } else {
                    if (this.actionTimer.hasElapsed(this.delay.getValue())) {
                        Integer hotbarSlot = this.findPickaxeSlot(0, 9);
                        if (hotbarSlot != null) {
                            this.previousSlot = InventoryUtil.getSelectedSlot();
                            this.swappedInventorySlot = -1;
                            InventoryUtil.selectSlot(hotbarSlot);
                            this.resetDelay();
                        } else if (this.fromInventory.getValue()) {
                            Integer inventorySlot = this.findPickaxeSlot(9, 36);
                            if (inventorySlot != null) {
                                int selectedSlot = InventoryUtil.getSelectedSlot();
                                this.previousSlot = selectedSlot;
                                this.swappedInventorySlot = inventorySlot;
                                InventoryUtil.swapSlot(inventorySlot, selectedSlot);
                                this.resetDelay();
                            }
                        }
                    }
                }
            } else {
                if (!this.trySwapBack()) {
                    this.resetDelay();
                }
            }
        }
    }

    private Integer findPickaxeSlot(int fromSlot, int toSlot) {
        for (Item pickaxe : PICKAXES) {
            for (int slot = fromSlot; slot < toSlot; slot++) {
                if (mc.player.getInventory().getStack(slot).getItem() == pickaxe) {
                    return slot;
                }
            }
        }

        return null;
    }

    private boolean trySwapBack() {
        if (this.previousSlot == -1) {
            return false;
        } else if (!this.actionTimer.hasElapsedRandom(this.delay)) {
            return true;
        } else {
            if (this.swappedInventorySlot != -1) {
                InventoryUtil.swapSlot(this.swappedInventorySlot, this.previousSlot);
            } else {
                InventoryUtil.selectSlot(this.previousSlot);
            }

            this.previousSlot = -1;
            this.swappedInventorySlot = -1;
            this.resetDelay();
            return true;
        }
    }

    private void resetDelay() {
        this.actionTimer.reset();
        this.delay.reroll();
    }

    @Override
    public void onDisable() {
        this.previousSlot = -1;
        this.swappedInventorySlot = -1;
    }
}
