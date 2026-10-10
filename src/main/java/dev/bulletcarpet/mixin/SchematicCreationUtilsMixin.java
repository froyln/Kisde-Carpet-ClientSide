package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import fi.dy.masa.litematica.network.SchematicSavePacketHandler;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.util.SchematicCreationUtils;
import fi.dy.masa.litematica.schematic.util.SchematicSaveSettings;
import fi.dy.masa.litematica.selection.AreaSelection;
import net.minecraft.client.Minecraft;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = SchematicCreationUtils.class, remap = false)
public abstract class SchematicCreationUtilsMixin {
    @Shadow private static void onInMemorySchematicCreated(ISchematic schematic, String name) {
        throw new AssertionError();
    }

    @Inject(method = "saveInMemorySchematic", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bulletcarpet$captureServerInMemory(String name, AreaSelection selection, CallbackInfoReturnable<Boolean> cir) {
        if (!LiteModBulletCarpet.serverHasChannel("bulletcarpet:paste")) return;
        SchematicSaveSettings settings = new SchematicSaveSettings();
        settings.saveEntities.setBooleanValue(false);
        settings.saveBlockTicks.setBooleanValue(false);
        Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[Bullet Carpet] Capturing server blocks and block entities; entities and scheduled ticks are excluded."));
        SchematicSavePacketHandler.INSTANCE.requestSchematicSaveAllAtOnce(selection, settings,
                schematic -> onInMemorySchematicCreated(schematic, name));
        cir.setReturnValue(true);
    }
}
