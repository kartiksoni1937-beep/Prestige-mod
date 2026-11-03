package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.TextReplaceEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CModifyArgs;
import net.lenni0451.classtransform.utils.CArgs;
import net.minecraft.text.TextVisitFactory;

@CTransformer({TextVisitFactory.class})
public class TextVisitFactoryMixin {
    @CModifyArgs(
        method = {"visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z"},
        target = {@CTarget(
            value = "INVOKE",
            target = "Lnet/minecraft/text/TextVisitFactory;visitFormatted(Ljava/lang/String;ILnet/minecraft/text/Style;Lnet/minecraft/text/Style;Lnet/minecraft/text/CharacterVisitor;)Z",
            ordinal = 0
        )}
    )
    private static void replaceTextBeforeVisitFormatted(CArgs cArgs) {
        String text = (String)cArgs.get(0);
        if (text != null) {
            if (Prestige.eventBus == null || !HookBridge.hasTextReplaceListeners(Prestige.eventBus, TextReplaceEvent.class)) {
                return;
            }

            TextReplaceEvent event = HookBridge.createTextReplaceEvent("", text);
            if (HookBridge.postTextReplaceEvent(event)) {
                cArgs.set(0, text.replace(HookBridge.getTextReplaceTarget(event), HookBridge.getTextReplacement(event)));
            }
        }
    }
}
