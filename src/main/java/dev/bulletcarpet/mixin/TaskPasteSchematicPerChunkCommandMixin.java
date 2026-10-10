package dev.bulletcarpet.mixin;

import dev.bulletcarpet.LiteModBulletCarpet;
import dev.bulletcarpet.AssistedPasteLifecycle;
import dev.bulletcarpet.AssistedPasteTask;
import dev.bulletcarpet.SchematicWorldAccess;
import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;
import fi.dy.masa.litematica.world.WorldSchematic;
import fi.dy.masa.malilib.util.position.IntBoundingBox;
import io.netty.buffer.Unpooled;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.client.entity.EntityPlayerSP;
import net.minecraft.client.multiplayer.WorldClient;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.BlockPos;
import net.minecraft.util.math.ChunkPos;
import net.minecraft.block.material.Material;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.Unique;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfoReturnable;

import java.util.concurrent.atomic.AtomicLong;

@Mixin(value = TaskPasteSchematicPerChunkCommand.class, remap = false)
public abstract class TaskPasteSchematicPerChunkCommandMixin implements AssistedPasteTask {
    @Unique private static final AtomicLong BULLET_CARPET_OPERATION_IDS = new AtomicLong(System.currentTimeMillis() * 1000L);
    @Unique private long bulletcarpet$operation;
    @Unique private int bulletcarpet$sequence;
    @Unique private boolean bulletcarpet$operationClosed;

    @Shadow @Final private int maxCommandsPerTick;
    @Shadow private int sentCommandsThisTick;
    @Shadow private int sentCommandsTotal;
    @Shadow private int currentX;
    @Shadow private int currentY;
    @Shadow private int currentZ;
    @Shadow private int currentIndex;
    @Shadow private int boxVolume;
    @Shadow private boolean boxInProgress;

    @Inject(method = "processBox", at = @At("HEAD"), cancellable = true)
    private void bulletcarpet$pasteServerBlockEntities(ChunkPos chunk, IntBoundingBox box, WorldSchematic schematic, WorldClient clientWorld, EntityPlayerSP sender, CallbackInfoReturnable<Boolean> cir) {
        Minecraft mc = Minecraft.getMinecraft();
        if (!LiteModBulletCarpet.isAssistedPasteEnabled() || mc.isSingleplayer() || mc.player == null) return;
        if (!this.boxInProgress) {
            if (this.bulletcarpet$operation == 0) {
                this.bulletcarpet$operation = BULLET_CARPET_OPERATION_IDS.incrementAndGet();
                AssistedPasteLifecycle.register((TaskPasteSchematicPerChunkCommand) (Object) this, this.bulletcarpet$operation);
            }
            this.currentX = box.minX;
            this.currentY = box.minY;
            this.currentZ = box.minZ;
            this.boxVolume = (box.maxX - box.minX + 1) * (box.maxY - box.minY + 1) * (box.maxZ - box.minZ + 1);
            this.currentIndex = 0;
            this.boxInProgress = true;
        }
        BlockPos.MutableBlockPos pos = new BlockPos.MutableBlockPos();
        while (this.currentIndex < this.boxVolume) {
            pos.setPos(this.currentX, this.currentY, this.currentZ);
            this.advance(box);
            IBlockState source = SchematicWorldAccess.getBlockState(schematic, pos);
            IBlockState destination = clientWorld.getBlockState(pos);
            if (source.getMaterial() == Material.AIR && destination.getMaterial() == Material.AIR) continue;
            IBlockState state = source.getBlock().getStateFromMeta(source.getBlock().getMetaFromState(source));
            TaskPasteSchematicPerChunkBaseAccessor task = (TaskPasteSchematicPerChunkBaseAccessor) this;
            if (task.bulletcarpet$getChangedBlockOnly() && destination == state &&
                    SchematicWorldAccess.getTileEntity(schematic, pos) == null) continue;
            if (task.bulletcarpet$getReplaceBehavior() == fi.dy.masa.litematica.util.ReplaceBehavior.NONE && destination.getMaterial() != Material.AIR) continue;
            if (task.bulletcarpet$getReplaceBehavior() == fi.dy.masa.litematica.util.ReplaceBehavior.WITH_NON_AIR && source.getMaterial() == Material.AIR) continue;
            try {
                this.sendBlock(mc.player, schematic, pos, state);
                this.sentCommandsThisTick++;
                this.sentCommandsTotal++;
            } catch (Exception e) {
                mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("[Bullet Carpet] Assisted paste stopped: " + e.getMessage()));
                ((TaskPasteSchematicPerChunkCommand) (Object) this).stop();
                cir.setReturnValue(true);
                return;
            }
            if (this.sentCommandsThisTick >= this.maxCommandsPerTick) {
                cir.setReturnValue(false);
                return;
            }
        }
        this.boxInProgress = false;
        cir.setReturnValue(true);
    }

    @Inject(method = "execute", at = @At("RETURN"), remap = false)
    private void bulletcarpet$completeOperation(CallbackInfoReturnable<Boolean> cir) {
        if (Boolean.TRUE.equals(cir.getReturnValue()) && this.bulletcarpet$operation != 0 && !this.bulletcarpet$operationClosed) {
            if (this.bulletcarpet$sequence == 0) {
                Minecraft mc = Minecraft.getMinecraft();
                if (mc.player != null) mc.player.sendMessage(new net.minecraft.util.text.TextComponentString("[Bullet Carpet] No blocks matched the paste replacement and layer settings (0 updates)."));
            } else {
                this.sendControl("Complete");
            }
            this.bulletcarpet$operationClosed = true;
        }
    }

    @Inject(method = "stop", at = @At("HEAD"), remap = false)
    private void bulletcarpet$cancelOperation(CallbackInfo ci) {
        if (this.bulletcarpet$operation != 0 && !this.bulletcarpet$operationClosed) {
            if (this.bulletcarpet$sequence != 0) this.sendControl("Cancel");
            this.bulletcarpet$operationClosed = true;
        }
        AssistedPasteLifecycle.clear(this.bulletcarpet$operation);
    }

    @Override
    @Unique
    public void bulletcarpet$markOperationClosed() {
        this.bulletcarpet$operationClosed = true;
    }

    private void advance(IntBoundingBox box) {
        this.currentY++;
        if (this.currentY > box.maxY) {
            this.currentY = box.minY;
            this.currentX++;
            if (this.currentX > box.maxX) {
                this.currentX = box.minX;
                this.currentZ++;
            }
        }
        this.currentIndex++;
    }

    private void sendBlock(EntityPlayerSP player, WorldSchematic world, BlockPos pos, IBlockState state) throws Exception {
        NBTTagCompound request = new NBTTagCompound();
        request.setInteger("x", pos.getX());
        request.setInteger("y", pos.getY());
        request.setInteger("z", pos.getZ());
        request.setLong("Operation", this.bulletcarpet$operation);
        request.setInteger("Sequence", this.bulletcarpet$sequence);
        request.setInteger("Dimension", player.dimension);
        request.setTag("BlockState", writeState(state));
        TileEntity tile = SchematicWorldAccess.getTileEntity(world, pos);
        if (tile != null) request.setTag("BlockEntity", tile.writeToNBT(new NBTTagCompound()));
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        try {
            data.writeCompoundTag(request);
            if (data.writerIndex() > 32767) throw new IllegalArgumentException("block data exceeds the plugin packet limit");
            sendSplit(player, data);
            this.bulletcarpet$sequence++;
        } finally {
            if (data.refCnt() > 0) data.release();
        }
    }

    @Unique
    private void sendControl(String action) {
        Minecraft mc = Minecraft.getMinecraft();
        if (mc.player == null || mc.player.connection == null) return;
        NBTTagCompound request = new NBTTagCompound();
        request.setString("Action", action);
        request.setLong("Operation", this.bulletcarpet$operation);
        request.setInteger("Sequence", this.bulletcarpet$sequence);
        PacketBuffer data = new PacketBuffer(Unpooled.buffer());
        try {
            data.writeCompoundTag(request);
            sendSplit(mc.player, data);
        } catch (RuntimeException ignored) {
            if (data.refCnt() > 0) data.release();
        }
    }

    private static void sendSplit(EntityPlayerSP player, PacketBuffer data) {
        int length = data.writerIndex();
        data.resetReaderIndex();
        for (int offset = 0; offset < length; offset += 32762) {
            int slice = Math.min(length - offset, 32762);
            PacketBuffer fragment = new PacketBuffer(Unpooled.buffer(slice + 5));
            if (offset == 0) fragment.writeVarInt(length);
            fragment.writeBytes(data, slice);
            player.connection.sendPacket(new CPacketCustomPayload("bulletcarpet:paste", fragment));
        }
    }

    private static NBTTagCompound writeState(IBlockState state) {
        ResourceLocation name = Block.REGISTRY.getNameForObject(state.getBlock());
        if (name == null) throw new IllegalArgumentException("block has no registry name");
        NBTTagCompound tag = new NBTTagCompound();
        tag.setString("Name", name.toString());
        if (!state.getProperties().isEmpty()) {
            NBTTagCompound properties = new NBTTagCompound();
            for (IProperty<?> property : state.getProperties().keySet()) properties.setString(property.getName(), propertyValue(state, property));
            tag.setTag("Properties", properties);
        }
        return tag;
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static String propertyValue(IBlockState state, IProperty property) {
        return property.getName(state.getValue(property));
    }
}
