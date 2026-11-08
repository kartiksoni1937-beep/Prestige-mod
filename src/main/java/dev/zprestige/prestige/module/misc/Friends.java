package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.ResetAttackCooldownEvent;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.social.FriendManager;
import dev.zprestige.prestige.type.EventPhase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;

/**
 * Friend switches. The registry itself lives in {@link FriendManager}; this module only owns the toggles and the
 * attack-side protection, so every other module asks the manager instead of duplicating friend logic.
 *
 * <p>Switches off means untouched behaviour: with the module disabled nothing here runs, targeting behaves exactly as
 * it did before (friends are still ignored), and attacks are not intercepted.
 */
public class Friends extends Module {
    public Friends() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting greenTags;
    private BoolSetting protect;
    private BoolSetting noHit;
    private BoolSetting hitboxProtect;

    // ------------------------------------------------------------------ switches

    public boolean isGreenTags() {
        return this.greenTags != null && this.greenTags.getValue();
    }

    public boolean isFriendsProtected() {
        return this.protect != null && this.protect.getValue();
    }

    public boolean isNoHit() {
        return this.noHit != null && this.noHit.getValue();
    }

    public boolean isHitboxProtection() {
        return this.hitboxProtect != null && this.hitboxProtect.getValue();
    }

    // ------------------------------------------------------------------ no hit

    /** The one attack path every click, aura and silent rotation goes through. */
    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (!this.isNoHit()) {
            return;
        }
        if (this.isFriend(event.getTarget())) {
            event.cancel();
        }
    }

    @Subscribe
    public void onResetAttackCooldown(ResetAttackCooldownEvent event) {
        if (!this.isNoHit()) {
            return;
        }
        Entity target = this.crosshairTarget();
        if (target != null && this.isFriend(target)) {
            event.cancel();
        }
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (event.getPhase() != EventPhase.PRE || !this.isNoHit()) {
            return;
        }
        if (this.isFriend(this.crosshairTarget()) && mc.player != null) {
            mc.player.handSwinging = false;
            event.cancel();
        }
    }

    private Entity crosshairTarget() {
        if (mc.crosshairTarget instanceof EntityHitResult hit && hit.getType() == HitResult.Type.ENTITY) {
            return hit.getEntity();
        }
        return null;
    }

    /**
     * Checks both identities: the UUID is authoritative, the name covers friends stored before their UUID was known.
     * Teammates count as friends here, exactly like the old name check did.
     */
    private boolean isFriend(Entity entity) {
        if (!(entity instanceof PlayerEntity player) || Prestige.friendManager == null) {
            return false;
        }
        return Prestige.friendManager.hasFriend(player.getUuid())
                || Prestige.friendManager.hasFriend(player.getName().getString());
    }
}
