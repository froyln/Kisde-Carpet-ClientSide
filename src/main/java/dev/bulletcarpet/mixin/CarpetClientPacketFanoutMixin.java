package dev.bulletcarpet.mixin;

import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.server.SPacketCustomPayload;
import io.netty.buffer.Unpooled;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Pseudo;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.Redirect;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Pseudo
@Mixin(targets = "fi.dy.masa.malilib.network.ClientPacketChannelHandlerImpl", remap = false)
public abstract class CarpetClientPacketFanoutMixin {
    @Unique private static final ThreadLocal<SPacketCustomPayload> bulletcarpet$packet = new ThreadLocal<>();
    @Unique private static final ThreadLocal<PacketBuffer> bulletcarpet$payload = new ThreadLocal<>();

    @Inject(method = "processPacketFromServer", at = @At("HEAD"), remap = false)
    private void bulletcarpet$beginPacket(CallbackInfoReturnable<Boolean> cir) {
        bulletcarpet$clear();
    }

    @Redirect(method = "processPacketFromServer", at = @At(value = "INVOKE", target = "Lfi/dy/masa/malilib/network/PacketSplitter;receive(Lbrz;Liw;)Lgy;"), remap = false)
    private PacketBuffer bulletcarpet$copySplitPayload(NetHandlerPlayClient handler, SPacketCustomPayload packet) {
        if (!"carpet:client".equals(packet.getChannelName())) return bulletcarpet$receive(handler, packet);
        PacketBuffer previous = bulletcarpet$packet.get() == packet ? bulletcarpet$payload.get() : null;
        if (bulletcarpet$packet.get() == packet) return previous == null ? null : bulletcarpet$copy(previous);
        PacketBuffer received = bulletcarpet$receive(handler, packet);
        bulletcarpet$packet.set(packet);
        if (received != null) {
            PacketBuffer copy = new PacketBuffer(Unpooled.buffer(received.readableBytes()));
            copy.writeBytes(received, received.readerIndex(), received.readableBytes());
            bulletcarpet$payload.set(copy);
        }
        return received;
    }

    @Unique
    private static PacketBuffer bulletcarpet$receive(NetHandlerPlayClient handler, SPacketCustomPayload packet) {
        try {
            Class<?> splitter = Class.forName("fi.dy.masa.malilib.network.PacketSplitter", false, CarpetClientPacketFanoutMixin.class.getClassLoader());
            for (java.lang.reflect.Method method : splitter.getDeclaredMethods()) {
                if (method.getName().equals("receive") && method.getParameterCount() == 2 &&
                        method.getParameterTypes()[0].isInstance(handler) && method.getParameterTypes()[1].isInstance(packet)) {
                    return (PacketBuffer) method.invoke(null, handler, packet);
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        return null;
    }

    @Unique
    private static PacketBuffer bulletcarpet$copy(PacketBuffer source) {
        PacketBuffer copy = new PacketBuffer(Unpooled.buffer(source.readableBytes()));
        copy.writeBytes(source, source.readerIndex(), source.readableBytes());
        return copy;
    }

    @Unique
    private static void bulletcarpet$clear() {
        PacketBuffer payload = bulletcarpet$payload.get();
        if (payload != null && payload.refCnt() > 0) payload.release();
        bulletcarpet$packet.remove();
        bulletcarpet$payload.remove();
    }

    @Inject(method = "processPacketFromServer", at = @At("RETURN"), remap = false)
    private void bulletcarpet$endPacket(CallbackInfoReturnable<Boolean> cir) {
        bulletcarpet$clear();
    }
}
