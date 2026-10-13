package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import dev.bulletcarpet.SharedSchematics;
import fi.dy.masa.litematica.gui.util.LitematicaIcons;
import fi.dy.masa.litematica.gui.widget.list.entry.SchematicPlacementEntryWidget;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacement;
import fi.dy.masa.litematica.schematic.placement.SchematicPlacementUnloaded;
import fi.dy.masa.malilib.gui.widget.IconWidget;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.gui.widget.list.entry.BaseDataListEntryWidget;
import fi.dy.masa.malilib.gui.widget.list.entry.DataListEntryWidgetData;
import org.spongepowered.asm.mixin.*;
import org.spongepowered.asm.mixin.injection.*;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

@Mixin(value = SchematicPlacementEntryWidget.class, remap = false)
public abstract class SharedPlacementEntryMixin extends BaseDataListEntryWidget<SchematicPlacementUnloaded> {
    @Shadow @Final protected GenericButton configureButton;
    @Shadow @Final protected IconWidget lockedIcon;
    @Shadow @Final protected IconWidget modificationNoticeIcon;
    @Shadow protected int buttonsStartX;
    @Shadow protected abstract boolean useIconButtons();
    @Unique private GenericButton kisde$shareButton;

    protected SharedPlacementEntryMixin(SchematicPlacementUnloaded data, DataListEntryWidgetData widgetData) { super(data, widgetData); }
    @Inject(method = "reAddSubWidgets", at = @At("RETURN"), remap = false)
    private void kisde$addShare(CallbackInfo ci) {
        if (!LiteModBulletCarpet.serverHasChannel(SharedSchematics.CHANNEL)) return;
        if (kisde$shareButton == null) {
            kisde$shareButton = useIconButtons() ? GenericButton.create(20, 20, LitematicaIcons.SCHEMATIC_VCS) : GenericButton.create(20, "Share");
            kisde$shareButton.setActionListener(() -> {
                SchematicPlacement placement = getData().isLoaded() ? (SchematicPlacement) getData() : getData().fullyLoadPlacement();
                SharedSchematics.INSTANCE.share(null, placement);
            });
            kisde$shareButton.addHoverStrings("Share this placement with the server");
            kisde$shareButton.setEnabledStatusSupplier(() -> SharedSchematics.INSTANCE.enabled && !SharedSchematics.INSTANCE.busy());
        }
        addWidget(kisde$shareButton);
    }
    @Inject(method = "updateSubWidgetPositions", at = @At("RETURN"), remap = false)
    private void kisde$positionShare(CallbackInfo ci) {
        if (kisde$shareButton == null) return;
        kisde$shareButton.setRight(configureButton.getX() - 1);
        kisde$shareButton.centerVerticallyInside(this);
        int shift = kisde$shareButton.getWidth() + 1;
        lockedIcon.setX(lockedIcon.getX() - shift);
        modificationNoticeIcon.setX(modificationNoticeIcon.getX() - shift);
        buttonsStartX -= shift;
    }
}
