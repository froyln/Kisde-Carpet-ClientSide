package dev.bulletcarpet.mixin;

import net.minecraft.nbt.NBTTagLongArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Accessor;

@Mixin(NBTTagLongArray.class)
public interface NBTTagLongArrayAccessor {
    @Accessor("data")
    long[] bulletcarpet$getData();
}
