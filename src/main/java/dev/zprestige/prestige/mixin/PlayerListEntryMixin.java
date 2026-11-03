package dev.zprestige.prestige.mixin;

import com.mojang.authlib.GameProfile;
import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.SkinTexturesEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.network.PlayerListEntry;
import net.minecraft.entity.player.SkinTextures;

@CTransformer({PlayerListEntry.class})
public class PlayerListEntryMixin {
    @CShadow("profile")
    public GameProfile profile;

    @CInject(
        method = {"getSkinTextures"},
        target = {@CTarget("RETURN")},
        cancellable = true
    )
    private void overrideListEntrySkinAtGetSkinTexturesReturn(InjectionCallback ci) {
        if (this.profile != null && Prestige.eventBus != null && HookBridge.hasListEntrySkinListeners(Prestige.eventBus, SkinTexturesEvent.class)) {
            SkinTexturesEvent event = HookBridge.createListEntrySkinEvent((SkinTextures)ci.getReturnValue(), HookBridge.getProfileName(this.profile));
            if (HookBridge.postListEntrySkinEvent(event)) {
                ci.setReturnValue(HookBridge.getListEntrySkinTextures(event));
            }
        }
    }
}
