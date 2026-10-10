package dev.bulletcarpet.mixin;

import com.mumfrey.liteloader.PluginChannelListener;
import io.netty.buffer.Unpooled;
import net.minecraft.network.PacketBuffer;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Pseudo
@Mixin(targets = "com.mumfrey.liteloader.core.ClientPluginChannels", remap = false)
public abstract class CarpetLiteLoaderPayloadFanoutMixin {
    @Redirect(method = "onModPacketReceived", at = @At(value = "INVOKE", target = "Lcom/mumfrey/liteloader/PluginChannelListener;onCustomPayload(Ljava/lang/String;Lgy;)V"), remap = false)
    private void bulletcarpet$copyCarpetPayload(PluginChannelListener listener, String channel, PacketBuffer data) {
        if (!"carpet:client".equals(channel)) {
            listener.onCustomPayload(channel, data);
            return;
        }
        PacketBuffer copy = new PacketBuffer(Unpooled.buffer(data.readableBytes()));
        copy.writeBytes(data, data.readerIndex(), data.readableBytes());
        listener.onCustomPayload(channel, copy);
    }
}
