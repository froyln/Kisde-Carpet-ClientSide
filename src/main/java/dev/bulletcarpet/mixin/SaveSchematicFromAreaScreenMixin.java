package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import fi.dy.masa.litematica.gui.SaveSchematicFromAreaScreen;
import fi.dy.masa.litematica.gui.SaveSchematicFromAreaScreen.SaveSide;
import fi.dy.masa.litematica.selection.AreaSelection;
import fi.dy.masa.litematica.schematic.util.SchematicSaveSettings;
import fi.dy.masa.malilib.config.option.BooleanConfig;
import fi.dy.masa.malilib.config.option.OptionListConfig;
import fi.dy.masa.malilib.gui.widget.BooleanEditWidget;
import fi.dy.masa.malilib.gui.widget.button.BooleanConfigButton;
import fi.dy.masa.malilib.gui.widget.button.OptionListConfigButton;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.nio.file.Path;

@Mixin(value = SaveSchematicFromAreaScreen.class, remap = false)
public abstract class SaveSchematicFromAreaScreenMixin {
    @Shadow @Final protected SchematicSaveSettings settings;
    @Shadow @Final protected OptionListConfig<SaveSide> saveSide;
    @Shadow @Final protected BooleanConfig customSettingsEnabled;
    @Shadow @Final protected BooleanConfigButton customSettingsButton;
    @Shadow @Final protected OptionListConfigButton saveSideButton;
    @Shadow @Final protected BooleanEditWidget saveBlocksWidget;
    @Shadow @Final protected BooleanEditWidget saveBlockEntitiesWidget;
    @Shadow @Final protected BooleanEditWidget saveBlockTicksWidget;
    @Shadow @Final protected BooleanEditWidget saveEntitiesWidget;
    @Shadow @Final protected BooleanEditWidget exposedBlocksOnlyWidget;
    @Shadow @Final protected BooleanEditWidget fromNormalWorldWidget;
    @Shadow @Final protected BooleanEditWidget fromSchematicWorldWidget;
    @Shadow protected abstract void reAddActiveWidgets();
    @Shadow protected abstract void saveSchematicOnServer(SchematicSaveSettings settings, Path path, boolean overwrite);

    @Inject(method = "<init>", at = @At("RETURN"), remap = false)
    private void bulletcarpet$configureServerCapture(AreaSelection selection, CallbackInfo ci) {
        if (!LiteModBulletCarpet.serverHasChannel("bulletcarpet:paste")) return;
        this.saveSide.setValueChangeCallback((current, previous) -> this.reAddActiveWidgets());
        this.saveSideButton.translateAndAddHoverString("Kisde-Carpet: Auto captures server blocks and block entities, with sign text; inventory contents depend on the server rule. Entities and scheduled ticks are unsupported.");
        this.bulletcarpet$updateCaptureOptions();
    }

    @Inject(method = "reAddActiveWidgets", at = @At("HEAD"), remap = false)
    private void bulletcarpet$showSupportedCaptureScope(CallbackInfo ci) {
        if (LiteModBulletCarpet.serverHasChannel("bulletcarpet:paste")) this.bulletcarpet$updateCaptureOptions();
    }

    @Unique
    private void bulletcarpet$updateCaptureOptions() {
        boolean server = this.saveSide.getValue() != SaveSide.CLIENT;
        boolean inventoryRules = LiteModBulletCarpet.serverHasChannel(LiteModBulletCarpet.SCHEMATIC_RULES);
        if (server) {
            this.settings.saveBlocks.setBooleanValue(true);
            if (!inventoryRules) this.settings.saveBlockEntities.setBooleanValue(true);
            else if (!LiteModBulletCarpet.canSaveInventories()) this.settings.saveBlockEntities.setBooleanValue(false);
            this.settings.saveEntities.setBooleanValue(false);
            this.settings.saveBlockTicks.setBooleanValue(false);
            this.settings.exposedBlocksOnly.setBooleanValue(false);
            this.settings.saveFromNormalWorld.setBooleanValue(true);
            this.settings.saveFromSchematicWorld.setBooleanValue(false);
            this.customSettingsEnabled.setBooleanValue(true);
        }
        this.saveBlockEntitiesWidget.getLabelWidget().setLabelText(server && inventoryRules ? "Save inventories" : "litematica.button.schematic_save.save_block_entities");
        this.saveBlockEntitiesWidget.getButton().setEnabled(!server || (inventoryRules && LiteModBulletCarpet.canSaveInventories()));
        this.customSettingsButton.setEnabled(!server);
        for (BooleanEditWidget widget : new BooleanEditWidget[] {this.saveBlocksWidget,
                this.saveBlockTicksWidget, this.saveEntitiesWidget, this.exposedBlocksOnlyWidget,
                this.fromNormalWorldWidget, this.fromSchematicWorldWidget}) {
            widget.getButton().setEnabled(!server);
        }
    }

    @Inject(method = "saveSchematicOnServer", at = @At("HEAD"), remap = false)
    private void bulletcarpet$applyInventoryRule(SchematicSaveSettings settings, Path path, boolean overwrite, CallbackInfo ci) {
        if (LiteModBulletCarpet.serverHasChannel("bulletcarpet:paste") && !LiteModBulletCarpet.canSaveInventories()) {
            settings.saveBlockEntities.setBooleanValue(false);
        }
    }

    @Inject(method = "saveSchematicOnClient", at = @At("HEAD"), cancellable = true, remap = false)
    private void bulletcarpet$useServerForAutoSave(SchematicSaveSettings settings, Path path, boolean overwrite, CallbackInfo ci) {
        if (this.saveSide.getValue() == SaveSide.AUTO && LiteModBulletCarpet.serverHasChannel("bulletcarpet:paste")) {
            this.saveSchematicOnServer(settings, path, overwrite);
            ci.cancel();
        }
    }
}
