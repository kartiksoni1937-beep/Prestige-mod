package dev.zprestige.prestige.util;

import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.HandleBlockBreakingEvent;
import dev.zprestige.prestige.event.FrameEvent;
import dev.zprestige.prestige.event.DoItemUseEvent;
import dev.zprestige.prestige.event.DoAttackEvent;
import dev.zprestige.prestige.render.MinecraftAccess;
import dev.zprestige.prestige.render.KeyBindingAccessor;
import dev.zprestige.prestige.render.MouseAccessor;
import java.util.HashMap;
import org.lwjgl.glfw.GLFW;

public class SimulatedClickManager implements MinecraftAccess {
    private final HashMap heldButtons;
    private boolean suppressAttack;
    private boolean suppressUse;
    public static int attackButton = 0;
    public static int useButton = 1;
    private static final long MOUSE_BUTTON_LIMIT = -954273915637596152L;

    public SimulatedClickManager() {
        super();
        this.heldButtons = new HashMap();
        Prestige.eventBus.register(this);
    }

    public boolean isSimulated(int button) {
        return this.heldButtons.containsKey(button);
    }

    private void releaseButton(int button) {
        if (this.isSimulated(button)) {
            new MouseAccessor(mc.mouse).invokeMouseButton(mc.getWindow().getHandle(), button, 0, 0);
            this.heldButtons.remove(button);
        }

        if (button == attackButton) {
            this.suppressAttack = false;
        }

        if (button == useButton) {
            this.suppressUse = false;
        }
    }

    private void tickButton(int button) {
        if (this.isSimulated(button)) {
            int remainingTicks = (Integer)this.heldButtons.get(button);
            if (remainingTicks > 0) {
                this.heldButtons.replace(button, remainingTicks - 1);
            } else {
                this.releaseButton(button);
            }
        }
    }

    @Subscribe
    public void onFrame(FrameEvent event) {
        if (!mc.isWindowFocused()) {
            this.releaseButton(attackButton);
            this.releaseButton(useButton);
            this.suppressAttack = false;
            this.suppressUse = false;
        } else {
            this.tickButton(attackButton);
            this.tickButton(useButton);
        }
    }

    @Subscribe
    public void onDoItemUse(DoItemUseEvent event) {
        if (this.suppressUse) {
            if (!this.isKeyUp(new KeyBindingAccessor(mc.options.useKey).getBoundKey().getCode())) {
                this.suppressUse = false;
                return;
            }

            event.cancel();
            if (!this.isSimulated(useButton)) {
                this.suppressUse = false;
            }
        }
    }

    @Subscribe
    public void onDoAttack(DoAttackEvent event) {
        if (this.suppressAttack) {
            if (!this.isKeyUp(new KeyBindingAccessor(mc.options.attackKey).getBoundKey().getCode())) {
                this.suppressAttack = false;
                return;
            }

            event.cancel();
            if (!this.isSimulated(attackButton)) {
                this.suppressAttack = false;
            }
        }
    }

    @Subscribe
    public void onHandleBlockBreaking(HandleBlockBreakingEvent event) {
        if (this.suppressAttack) {
            if (!this.isKeyUp(new KeyBindingAccessor(mc.options.attackKey).getBoundKey().getCode())) {
                this.suppressAttack = false;
                return;
            }

            event.cancel();
            if (!this.isSimulated(attackButton)) {
                this.suppressAttack = false;
            }
        }
    }

    private boolean isKeyUp(int keyCode) {
        if (keyCode < 0) {
            return true;
        } else {
            return keyCode <= (int)MOUSE_BUTTON_LIMIT ? GLFW.glfwGetMouseButton(mc.getWindow().getHandle(), keyCode) != 1 : GLFW.glfwGetKey(mc.getWindow().getHandle(), keyCode) != 1;
        }
    }
}
