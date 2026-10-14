package dev.bulletcarpet.mixin;

import dev.bulletcarpet.AnvilAccess;
import dev.bulletcarpet.AnvilSync;
import dev.bulletcarpet.LiteModBulletCarpet;
import net.minecraft.inventory.ContainerRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(ContainerRepair.class)
public abstract class ContainerRepairMixin implements AnvilAccess {
    @Unique private boolean kisde$uncappedCost;
    public void kisde$setUncappedCost(boolean value) { this.kisde$uncappedCost = value; }
    public boolean kisde$hasUncappedCost() { return this.kisde$uncappedCost; }

    @Inject(method = "updateRepairOutput", at = @At("HEAD"), cancellable = true)
    private void kisde$serverResult(CallbackInfo ci) {
        if (LiteModBulletCarpet.serverHasChannel(AnvilSync.CHANNEL)) ci.cancel();
    }
}
