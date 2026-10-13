package dev.bulletcarpet.mixin;

import dev.bulletcarpet.SharedSchematicValidation;
import java.io.DataInput;
import java.io.IOException;
import net.minecraft.nbt.NBTTagByteArray;
import net.minecraft.nbt.NBTTagIntArray;
import net.minecraft.nbt.NBTTagLongArray;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Redirect;

@Mixin({NBTTagByteArray.class, NBTTagIntArray.class, NBTTagLongArray.class})
public abstract class NbtArrayBoundsMixin {
    @Redirect(method = "read", at = @At(value = "INVOKE", target = "Ljava/io/DataInput;readInt()I", ordinal = 0, remap = false))
    private int kisde$boundedArrayLength(DataInput input) throws IOException {
        int bytes = (Object) this instanceof NBTTagLongArray ? 8 : (Object) this instanceof NBTTagIntArray ? 4 : 1;
        return SharedSchematicValidation.arrayLength(input.readInt(), bytes);
    }
}
