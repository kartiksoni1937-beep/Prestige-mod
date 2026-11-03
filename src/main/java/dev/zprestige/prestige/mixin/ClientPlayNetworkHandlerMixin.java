package dev.zprestige.prestige.mixin;

import dev.zprestige.prestige.event.GameJoinEvent;
import dev.zprestige.prestige.util.HookBridge;
import net.lenni0451.classtransform.InjectionCallback;
import net.lenni0451.classtransform.annotations.CShadow;
import net.lenni0451.classtransform.annotations.CTarget;
import net.lenni0451.classtransform.annotations.CTransformer;
import net.lenni0451.classtransform.annotations.injection.CInject;
import net.minecraft.client.network.ClientPlayNetworkHandler;
import net.minecraft.client.world.ClientWorld;
import net.minecraft.network.packet.s2c.play.ChunkDataS2CPacket;
import net.minecraft.network.packet.s2c.play.GameJoinS2CPacket;
import net.minecraft.world.chunk.WorldChunk;

@CTransformer({ClientPlayNetworkHandler.class})
public class ClientPlayNetworkHandlerMixin {
    @CShadow("world")
    private ClientWorld world;

    @CInject(
        method = {"onGameJoin"},
        target = {@CTarget("TAIL")}
    )
    private void postGameJoinEventAtTail(GameJoinS2CPacket gameJoinS2CPacket, InjectionCallback ci) {
        if (this.world != null) {
            GameJoinEvent event = HookBridge.createGameJoinEvent();
            HookBridge.postGameJoinEvent(event);
        }
    }

    @CInject(
        method = {"onChunkData"},
        target = {@CTarget("TAIL")}
    )
    private void postChunkDataEventAtTail(ChunkDataS2CPacket packet, InjectionCallback info) {
        if (this.world == null) {
            return;
        }
        WorldChunk chunk = this.world.getChunk(packet.getChunkX(), packet.getChunkZ());
        if (chunk != null) {
            HookBridge.postChunkDataEvent(HookBridge.createChunkDataEvent(chunk));
        }
    }
}
