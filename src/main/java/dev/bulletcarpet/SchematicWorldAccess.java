package dev.bulletcarpet;

import dev.bulletcarpet.mixin.WorldAccessor;
import fi.dy.masa.litematica.world.WorldSchematic;
import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;

public final class SchematicWorldAccess {
    private SchematicWorldAccess() {
    }

    public static IBlockState getBlockState(WorldSchematic world, BlockPos pos) {
        return ((WorldAccessor) (Object) world).bulletcarpet$getBlockState(pos);
    }

    public static TileEntity getTileEntity(WorldSchematic world, BlockPos pos) {
        return ((WorldAccessor) (Object) world).bulletcarpet$getTileEntity(pos);
    }
}
