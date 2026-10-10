package dev.bulletcarpet.mixin;

import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

import java.util.UUID;

@Mixin(value = fi.dy.masa.litematica.task.MultiplayerCreateSchematicTask.class, remap = false)
public interface MultiplayerCreateSchematicTaskAccessor {
    @Accessor(value = "schematic", remap = false)
    LitematicaSchematic bulletcarpet$getSchematic();

    @Accessor(value = "taskId", remap = false)
    UUID bulletcarpet$getTaskId();
}
