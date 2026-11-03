package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.client.Prestige;
import dev.zprestige.prestige.event.PacketReceiveEvent;
import dev.zprestige.prestige.event.PacketSendEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.network.ClientConnection;
import net.minecraft.network.listener.PacketListener;
import net.minecraft.network.packet.Packet;

@CTransformer({ClientConnection.class})
public class ClientConnectionMixin {
    @CInject(
        method = {"handlePacket"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private static <T extends PacketListener> void postPacketReceiveEventAtHandlePacketHead(Packet<T> packet, PacketListener listener, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasPacketReceiveListeners(Prestige.eventBus, PacketReceiveEvent.class)) {
            PacketReceiveEvent event = HookBridge.createPacketReceiveEvent(packet);
            if (HookBridge.postPacketReceiveEvent(event)) {
                ci.cancel();
            }
        }
    }

    @CInject(
        method = {"send(Lnet/minecraft/network/packet/Packet;)V"},
        target = {@CTarget("HEAD")},
        cancellable = true
    )
    private void postPacketSendEventAtSendHead(Packet<?> packet, InjectionCallback ci) {
        if (Prestige.eventBus != null && HookBridge.hasPacketSendListeners(Prestige.eventBus, PacketSendEvent.class)) {
            PacketSendEvent event = HookBridge.createPacketSendEvent(packet);
            if (HookBridge.postPacketSendEvent(event)) {
                ci.cancel();
                return;
            }
        }

        if (Prestige.inventorySwapQueue != null) {
            HookBridge.trackOutgoingPacket(Prestige.inventorySwapQueue, packet);
        }
    }
}
