package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import dev.bulletcarpet.ServerInventoryPreview;
import fi.dy.masa.malilib.render.inventory.InventoryRenderDefinition;
import fi.dy.masa.malilib.render.inventory.InventoryRenderUtils;
import fi.dy.masa.malilib.render.inventory.BuiltinInventoryRenderDefinitions;
import net.minecraft.client.Minecraft;
import net.minecraft.entity.Entity;
import net.minecraft.inventory.IInventory;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.apache.commons.lang3.tuple.ImmutablePair;
import org.apache.commons.lang3.tuple.Pair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.ModifyArg;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

@Mixin(value = InventoryRenderUtils.class, remap = false)
public abstract class InventoryRenderUtilsMixin {
    @ModifyArg(method = "getPointedInventory", at = @At(value = "INVOKE",
            target = "Lfi/dy/masa/malilib/util/game/RayTraceUtils;getRayTraceFromEntity", remap = false), index = 1, remap = false)
    private static Entity bulletcarpet$useCameraInventory(Entity player) {
        Entity camera = Minecraft.getMinecraft().getRenderViewEntity();
        return camera == null ? player : camera;
    }

    @Inject(method = "getInventoryViewFromBlock", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bulletcarpet$useServerBlockInventory(BlockPos pos, World world, CallbackInfoReturnable<Pair> cir) {
        if (world != Minecraft.getMinecraft().world || !LiteModBulletCarpet.serverHasChannel("pca:sync_be")) return;
        TileEntity tile = world.getTileEntity(pos);
        if (!(tile instanceof IInventory)) return;
        Object snapshot = ServerInventoryPreview.getBlockSnapshot(pos);
        InventoryRenderDefinition definition = BuiltinInventoryRenderDefinitions.GENERIC;
        try {
            for (java.lang.reflect.Method method : InventoryRenderUtils.class.getMethods()) {
                if (method.getName().equals("getInventoryType") && method.getParameterTypes().length == 1 &&
                        method.getParameterTypes()[0].isInstance(tile)) {
                    definition = (InventoryRenderDefinition) method.invoke(null, tile);
                    break;
                }
            }
        } catch (ReflectiveOperationException | LinkageError ignored) {
        }
        cir.setReturnValue(snapshot == null ? null : ImmutablePair.of(snapshot, definition));
    }

    @Inject(method = "getInventoryViewFromEntity", at = @At("HEAD"), cancellable = true, remap = false)
    private static void bulletcarpet$useServerEntityInventory(Entity entity, CallbackInfoReturnable<Pair> cir) {
        if (entity.world != Minecraft.getMinecraft().world || !ServerInventoryPreview.supportedMinecart(entity) ||
                !LiteModBulletCarpet.serverHasChannel("bc:inv_req")) return;
        Object snapshot = ServerInventoryPreview.getEntitySnapshot(entity);
        cir.setReturnValue(snapshot == null ? null : ImmutablePair.of(snapshot, BuiltinInventoryRenderDefinitions.GENERIC));
    }
}
