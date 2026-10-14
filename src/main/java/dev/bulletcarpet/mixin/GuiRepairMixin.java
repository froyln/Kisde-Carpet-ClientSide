package dev.bulletcarpet.mixin;

import dev.bulletcarpet.AnvilAccess;
import net.minecraft.client.gui.GuiRepair;
import net.minecraft.entity.player.PlayerCapabilities;
import net.minecraft.inventory.ContainerRepair;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin(GuiRepair.class)
public abstract class GuiRepairMixin {
    @Shadow @Final private ContainerRepair anvil;

    @Redirect(method = "drawGuiContainerForegroundLayer", at = @At(value = "FIELD", target = "Lnet/minecraft/entity/player/PlayerCapabilities;isCreativeMode:Z"))
    private boolean kisde$costLabel(PlayerCapabilities capabilities) {
        return capabilities.isCreativeMode || ((AnvilAccess)this.anvil).kisde$hasUncappedCost();
    }
}
