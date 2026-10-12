package dev.bulletcarpet.mixin;

import dev.bulletcarpet.ServerInventoryPreview;
import dev.bulletcarpet.LiteModBulletCarpet;
import dev.bulletcarpet.AssistedPasteLifecycle;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.play.server.SPacketCustomPayload;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(NetHandlerPlayClient.class)
public abstract class ServerInventoryPayloadMixin {
    @Inject(method = "handleCustomPayload", at = @At("HEAD"))
    private void bulletcarpet$receiveServerInventory(SPacketCustomPayload packet, CallbackInfo ci) {
        if (!Minecraft.getMinecraft().isCallingFromMinecraftThread()) return;
        if (LiteModBulletCarpet.SCHEMATIC_RULES.equals(packet.getChannelName())) {
            LiteModBulletCarpet.onSchematicRules(packet.getBufferData());
        } else if ("pca:update_be".equals(packet.getChannelName()) || "bc:inv_ack".equals(packet.getChannelName())) {
            ServerInventoryPreview.instance().onCustomPayload(packet.getChannelName(), packet.getBufferData());
        } else if ("bc:paste_ack".equals(packet.getChannelName())) {
            AssistedPasteLifecycle.onAck(packet.getBufferData());
        }
    }
}
