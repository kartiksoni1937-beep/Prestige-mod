package dev.zprestige.prestige.module.misc;

import dev.zprestige.prestige.BoolSetting;
import dev.zprestige.prestige.Module;
import dev.zprestige.prestige.Subscribe;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SwingHandEvent;
import dev.zprestige.prestige.event.AttackEntityEvent;
import dev.zprestige.prestige.event.TextReplaceEvent;
import dev.zprestige.prestige.event.ResetAttackCooldownEvent;
import dev.zprestige.prestige.type.EventPhase;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.util.Formatting;
import net.minecraft.util.hit.EntityHitResult;
import net.minecraft.util.hit.HitResult;
import net.minecraft.util.hit.HitResult.Type;

public class Friends extends Module {
    public Friends() {
        dev.zprestige.prestige.ui.bridge.Bootstrap.pre(this);
    }

    private BoolSetting noAttack;
    private BoolSetting highlight;

    @Subscribe
    public void onTextReplace(TextReplaceEvent event) {
        if (this.highlight.getValue() && event.getText() != null) {
            for (Object friendName : Prestige.friendManager.getFriends()) {
                if (event.getText().contains((CharSequence)friendName)) {
                    event.setTarget((String)friendName);
                    event.setText("" + Formatting.AQUA + friendName + Formatting.RESET);
                    event.cancel();
                }
            }
        }
    }

    @Subscribe
    public void onAttackEntity(AttackEntityEvent event) {
        if (this.noAttack.getValue()) {
            Entity target = event.getTarget();
            if (target instanceof PlayerEntity && Prestige.friendManager.isFriend(target.getName().getString())) {
                event.cancel();
            }
        }
    }

    @Subscribe
    public void onResetAttackCooldown(ResetAttackCooldownEvent event) {
        if (this.noAttack.getValue()) {
            HitResult hitResult = mc.crosshairTarget;
            if (hitResult != null) {
                if (hitResult.getType() == Type.ENTITY) {
                    Entity targetEntity = ((EntityHitResult)hitResult).getEntity();
                    if (targetEntity instanceof PlayerEntity && Prestige.friendManager.isFriend(targetEntity.getName().getString())) {
                        event.cancel();
                    }
                }
            }
        }
    }

    @Subscribe
    public void onSwingHand(SwingHandEvent event) {
        if (event.getPhase() == EventPhase.PRE) {
            if (this.noAttack.getValue()) {
                HitResult hitResult = mc.crosshairTarget;
                if (hitResult != null) {
                    if (hitResult.getType() == Type.ENTITY) {
                        Entity targetEntity = ((EntityHitResult)hitResult).getEntity();
                        if (targetEntity instanceof PlayerEntity && Prestige.friendManager.isFriend(targetEntity.getName().getString())) {
                            if (mc.player != null) {
                                mc.player.handSwinging = false;
                            }
                            event.cancel();
                        }
                    }
                }
            }
        }
    }
}
