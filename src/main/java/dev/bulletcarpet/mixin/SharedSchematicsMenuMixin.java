package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import dev.bulletcarpet.SharedSchematics;
import dev.bulletcarpet.SharedSchematicsScreen;
import fi.dy.masa.litematica.gui.MainMenuScreen;
import fi.dy.masa.malilib.gui.widget.InteractableWidget;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainMenuScreen.class, remap = false)
public abstract class SharedSchematicsMenuMixin {
    @Unique private GenericButton kisde$sharingButton;
    @Inject(method = "reAddActiveWidgets", at = @At("RETURN"), remap = false)
    private void kisde$addSharing(CallbackInfo ci) {
        if (!LiteModBulletCarpet.serverHasChannel(SharedSchematics.CHANNEL)) return;
        if (kisde$sharingButton == null) kisde$sharingButton = GenericButton.create("Shared schematics", () -> Minecraft.getMinecraft().displayGuiScreen(new SharedSchematicsScreen((GuiScreen) (Object) this)));
        try { getClass().getMethod("addWidget", InteractableWidget.class).invoke(this, kisde$sharingButton); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Unable to add shared schematic menu", e); }
    }
    @Inject(method = "updateWidgetPositions", at = @At("RETURN"), remap = false)
    private void kisde$positionSharing(CallbackInfo ci) { if (kisde$sharingButton != null) kisde$sharingButton.setPosition(Math.max(8, new net.minecraft.client.gui.ScaledResolution(Minecraft.getMinecraft()).getScaledWidth() - kisde$sharingButton.getWidth() - 8), 8); }
}
