package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.LabelTextEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CModifyArgs;
import net.lenni0451.classtransform.utils.CArgs;
import net.minecraft.client.MinecraftClient;
import net.minecraft.client.render.entity.DisplayEntityRenderer.TextDisplayEntityRenderer;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.entity.player.PlayerEntity;
import net.minecraft.text.Text;

@CTransformer({TextDisplayEntityRenderer.class})
public class TextDisplayEntityRendererMixin {
    @CModifyArgs(
        method = {"getLines"},
        target = {@CTarget(
            value = "INVOKE",
            target = "Lnet/minecraft/client/font/TextRenderer;wrapLines(Lnet/minecraft/text/StringVisitable;I)Ljava/util/List;",
            ordinal = 0
        )}
    )
    public void modifyLabelTextBeforeWrapLines(CArgs cArgs) {
        if (Prestige.eventBus != null && HookBridge.hasTextDisplayLabelListeners(Prestige.eventBus, LabelTextEvent.class)) {
            Text text = (Text)cArgs.get(0);
            String stringText = text.getString();
            ClientWorld world = MinecraftClient.getInstance().world;
            if (!stringText.isBlank() && world != null) {
                for (PlayerEntity player : world.getPlayers()) {
                    int index = stringText.indexOf(player.getNameForScoreboard());
                    boolean surrounded = index == -1
                        || index > 0 && Character.isLetterOrDigit(stringText.charAt(index - 1))
                        || index + player.getNameForScoreboard().length() < stringText.length()
                            && Character.isLetterOrDigit(stringText.charAt(index + player.getNameForScoreboard().length()));
                    if (!surrounded) {
                        LabelTextEvent labelTextEvent = HookBridge.createTextDisplayLabelEvent(player, text);
                        if (HookBridge.postTextDisplayLabelEvent(labelTextEvent)) {
                            cArgs.set(0, HookBridge.getTextDisplayLabelText(labelTextEvent));
                        }
                    }
                }
            }
        }
    }
}
