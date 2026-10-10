package dev.bulletcarpet.mixin;

import net.minecraft.block.state.IBlockState;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.math.BlockPos;
import net.minecraft.world.World;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.gen.Invoker;

@Mixin(World.class)
public interface WorldAccessor {
    @Invoker("getBlockState") IBlockState bulletcarpet$getBlockState(BlockPos pos);
    @Invoker("getTileEntity") TileEntity bulletcarpet$getTileEntity(BlockPos pos);
}
