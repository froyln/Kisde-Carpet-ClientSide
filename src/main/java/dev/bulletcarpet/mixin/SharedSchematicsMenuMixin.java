package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import dev.bulletcarpet.SharedSchematics;
import dev.bulletcarpet.SharedSchematicsScreen;
import fi.dy.masa.litematica.gui.MainMenuScreen;
import fi.dy.masa.litematica.gui.util.LitematicaIcons;
import fi.dy.masa.malilib.gui.widget.InteractableWidget;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiScreen;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = MainMenuScreen.class, remap = false)
public abstract class SharedSchematicsMenuMixin {
    @Shadow @Final protected GenericButton configScreenButton;
    @Unique private GenericButton kisde$sharingButton;
    @Inject(method = "reAddActiveWidgets", at = @At("RETURN"), remap = false)
    private void kisde$addSharing(CallbackInfo ci) {
        if (!LiteModBulletCarpet.serverHasChannel(SharedSchematics.CHANNEL)) return;
        if (kisde$sharingButton == null) {
            kisde$sharingButton = GenericButton.create("Shared schematics", LitematicaIcons.SCHEMATIC_VCS).setActionListener(() -> Minecraft.getMinecraft().displayGuiScreen(new SharedSchematicsScreen((GuiScreen) (Object) this)));
            kisde$sharingButton.setAutomaticWidth(false);
        }
        try { getClass().getMethod("addWidget", InteractableWidget.class).invoke(this, kisde$sharingButton); }
        catch (ReflectiveOperationException e) { throw new IllegalStateException("Unable to add shared schematic menu", e); }
    }
    @Inject(method = "updateWidgetPositions", at = @At("RETURN"), remap = false)
    private void kisde$positionSharing(CallbackInfo ci) {
        if (kisde$sharingButton != null) {
            try {
                int right = (Integer) getClass().getMethod("getRight").invoke(this);
                kisde$sharingButton.setWidth(Math.max(20, Math.min(configScreenButton.getWidth(), right - configScreenButton.getRight() - 32)));
            } catch (ReflectiveOperationException e) { throw new IllegalStateException("Unable to position shared schematic menu", e); }
            kisde$sharingButton.setPosition(configScreenButton.getRight() + 20, configScreenButton.getY());
        }
    }
}
