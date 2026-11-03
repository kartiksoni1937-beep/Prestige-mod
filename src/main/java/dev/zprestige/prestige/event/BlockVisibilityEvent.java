package dev.zprestige.prestige.event;

import net.minecraft.block.Block;

public class BlockVisibilityEvent extends Event {
    private final Block block;
    private boolean visible = false;

    public BlockVisibilityEvent(Block queriedBlock) {
        this.block = queriedBlock;
    }

    public Block getBlock() {
        return this.block;
    }

    public boolean isVisible() {
        return this.visible;
    }

    public void setVisible(boolean value) {
        this.visible = value;
    }
}
