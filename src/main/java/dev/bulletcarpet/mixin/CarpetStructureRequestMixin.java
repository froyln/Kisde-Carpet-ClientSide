package dev.bulletcarpet.mixin;

import net.minecraft.client.Minecraft;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import io.netty.buffer.Unpooled;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Pseudo
@Mixin(targets = "fi.dy.masa.minihud.data.StructureStorage", remap = false)
public abstract class CarpetStructureRequestMixin {
    @Inject(method = "requestStructureDataUpdates", at = @At("RETURN"), remap = false)
    private void bulletcarpet$requestCarpetMarkers(CallbackInfo ci) {
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.isSingleplayer() || minecraft.player == null || minecraft.player.connection == null) return;
        try {
            Class<?> rendererToggle = Class.forName("fi.dy.masa.minihud.config.RendererToggle", false, getClass().getClassLoader());
            Object toggle = rendererToggle.getField("STRUCTURE_BOUNDING_BOXES").get(null);
            if (!((Boolean) rendererToggle.getMethod("isRendererEnabled").invoke(toggle))) return;
        } catch (ReflectiveOperationException | LinkageError e) {
            return;
        }
        PacketBuffer request = new PacketBuffer(Unpooled.buffer(5));
        request.writeVarInt(4);
        request.writeInt(3);
        minecraft.player.connection.sendPacket(new CPacketCustomPayload("carpet:client", request));
    }
}
