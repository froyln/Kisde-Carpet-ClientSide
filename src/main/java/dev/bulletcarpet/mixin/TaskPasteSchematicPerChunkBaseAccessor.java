package dev.bulletcarpet.mixin;

import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicPerChunkBase;
import fi.dy.masa.litematica.util.ReplaceBehavior;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(value = TaskPasteSchematicPerChunkBase.class, remap = false)
public interface TaskPasteSchematicPerChunkBaseAccessor {
    @Accessor("changedBlockOnly") boolean bulletcarpet$getChangedBlockOnly();
    @Accessor("replace") ReplaceBehavior bulletcarpet$getReplaceBehavior();
}
