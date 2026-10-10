package dev.bulletcarpet.mixin;

import fi.dy.masa.litematica.task.MultiplayerCreateSchematicTask;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.schematic.ISchematic;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.UUID;
import java.util.function.Consumer;

@Mixin(value = MultiplayerCreateSchematicTask.class, remap = false)
public abstract class MultiplayerCreateSchematicTaskMixin {
    @Unique private static final long bulletcarpet$timeoutNanos = 60_000_000_000L;
    @Unique private long bulletcarpet$startedAt;

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bulletcarpet$startTimeout(AreaSelection selection, UUID taskId, Consumer<ISchematic> consumer, CallbackInfo ci) {
        this.bulletcarpet$startedAt = System.nanoTime();
    }

    @Inject(method = "execute", at = @At("HEAD"), cancellable = true, remap = false)
    private void bulletcarpet$expireTimedOutRequest(CallbackInfoReturnable<Boolean> cir) {
        if (this.bulletcarpet$startedAt == 0L || System.nanoTime() - this.bulletcarpet$startedAt < bulletcarpet$timeoutNanos) return;
        ((MultiplayerCreateSchematicTask) (Object) this).stop();
        Minecraft minecraft = Minecraft.getMinecraft();
        if (minecraft.player != null) minecraft.player.sendMessage(new TextComponentString("[Kisde-Carpet] Server schematic capture timed out"));
        cir.setReturnValue(true);
    }
}
