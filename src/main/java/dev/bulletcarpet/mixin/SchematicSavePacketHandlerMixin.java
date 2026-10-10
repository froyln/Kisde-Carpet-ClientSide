package dev.bulletcarpet.mixin;

import fi.dy.masa.litematica.network.SchematicSavePacketHandler;
import fi.dy.masa.litematica.schematic.ISchematicRegion;
import fi.dy.masa.litematica.schematic.LitematicaSchematic;
import fi.dy.masa.litematica.task.MultiplayerCreateSchematicTask;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.nbt.NBTTagLongArray;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.ResourceLocation;
import net.minecraft.util.math.Vec3i;
import net.minecraft.util.text.TextComponentString;
import org.spongepowered.asm.mixin.Final;
import org.spongepowered.asm.mixin.Mixin;
import org.spongepowered.asm.mixin.Shadow;
import org.spongepowered.asm.mixin.injection.At;
import org.spongepowered.asm.mixin.injection.Inject;
import org.spongepowered.asm.mixin.injection.callback.CallbackInfo;

import java.io.IOException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

@Mixin(value = SchematicSavePacketHandler.class, remap = false)
public abstract class SchematicSavePacketHandlerMixin {
    private static final long MAX_BLOCKS = 32768;

    @Shadow(remap = false) @Final protected HashMap<UUID, MultiplayerCreateSchematicTask> pendingSaveTasks;
    @Shadow(remap = false) public abstract void removeSaveTask(UUID id);

    @Inject(method = "onPacketReceived", at = @At("HEAD"), cancellable = true, remap = false)
    private void bulletcarpet$validateBeforeNativeRead(PacketBuffer packet, CallbackInfo ci) {
        if (this.pendingSaveTasks.isEmpty()) return;
        int readerIndex = packet.readerIndex();
        NBTTagCompound data;
        try {
            data = packet.readCompoundTag();
            if (packet.isReadable()) throw new IOException("Trailing data");
        } catch (Exception e) {
            packet.readerIndex(readerIndex);
            failAll("Rejected malformed server schematic data");
            ci.cancel();
            return;
        }
        packet.readerIndex(readerIndex);
        if (data == null) {
            failAll("Rejected empty server schematic data");
            ci.cancel();
            return;
        }
        UUID id = readUuid(data);
        MultiplayerCreateSchematicTask task = id == null ? null : this.pendingSaveTasks.get(id);
        if (task == null) return;
        if (data.hasKey("Error", 8)) {
            fail(id, "Server schematic capture failed: " + data.getString("Error"));
            ci.cancel();
            return;
        }
        try {
            validate(data, ((MultiplayerCreateSchematicTaskAccessor) task).bulletcarpet$getSchematic());
            packet.clear();
            packet.writeCompoundTag(data);
            packet.readerIndex(0);
        } catch (RuntimeException e) {
            fail(id, "Rejected invalid server schematic data: " + e.getMessage());
            ci.cancel();
        }
    }

    private void failAll(String message) {
        for (UUID id : new HashSet<>(this.pendingSaveTasks.keySet())) fail(id, message);
    }

    private void fail(UUID id, String message) {
        MultiplayerCreateSchematicTask task = this.pendingSaveTasks.get(id);
        removeSaveTask(id);
        if (task != null) task.stop();
        if (Minecraft.getMinecraft().player != null) {
            Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[Kisde-Carpet] " + message));
        }
    }

    private static void validate(NBTTagCompound data, LitematicaSchematic schematic) {
        if (!"AllAtOnce".equals(data.getString("SaveMethod")) || !data.hasKey("SaveMethod", 8)) throw new IllegalArgumentException("unsupported save method");
        if (!data.hasKey("Regions", 10)) throw new IllegalArgumentException("missing regions");
        NBTTagCompound regions = data.getCompoundTag("Regions");
        Set<String> expected = new HashSet<>(schematic.getRegionNames());
        if (expected.isEmpty() || expected.size() > 32 || !expected.equals(regions.getKeySet())) throw new IllegalArgumentException("region set mismatch");
        for (String name : expected) {
            if (!regions.hasKey(name, 10)) throw new IllegalArgumentException("invalid region " + name);
            NBTTagCompound region = regions.getCompoundTag(name);
            ISchematicRegion selected = schematic.getSchematicRegion(name);
            validateRegion(name, region, selected);
            normalizeRegion(region, expectedSize(selected));
        }
    }

    private static void normalizeRegion(NBTTagCompound region, Vec3i expectedSize) {
        boolean reverseX = expectedSize.getX() < 0, reverseY = expectedSize.getY() < 0, reverseZ = expectedSize.getZ() < 0;
        if (!reverseX && !reverseY && !reverseZ) return;
        NBTTagCompound blocks = region.getCompoundTag("Blocks");
        int sizeX = blocks.getInteger("sizeX"), sizeY = blocks.getInteger("sizeY"), sizeZ = blocks.getInteger("sizeZ");
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(blocks.getTagList("BlockStatePalette", 10).tagCount() - 1));
        long[] packed = ((NBTTagLongArrayAccessor) blocks.getTag("BlockStates")).bulletcarpet$getData();
        int volume = Math.multiplyExact(Math.multiplyExact(sizeX, sizeY), sizeZ);
        int[] states = new int[volume];
        for (int i = 0; i < volume; i++) states[i] = unpackState(packed, i, bits);
        int[] reversed = new int[volume];
        for (int y = 0; y < sizeY; y++) for (int z = 0; z < sizeZ; z++) for (int x = 0; x < sizeX; x++) {
            int targetX = reverseX ? sizeX - 1 - x : x;
            int targetY = reverseY ? sizeY - 1 - y : y;
            int targetZ = reverseZ ? sizeZ - 1 - z : z;
            reversed[(targetY * sizeZ + targetZ) * sizeX + targetX] = states[(y * sizeZ + z) * sizeX + x];
        }
        blocks.setTag("BlockStates", new NBTTagLongArray(packStates(reversed, bits)));
        NBTTagList tiles = region.getTagList("BlockEntities", 10);
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(i);
            if (reverseX) tile.setInteger("x", sizeX - 1 - tile.getInteger("x"));
            if (reverseY) tile.setInteger("y", sizeY - 1 - tile.getInteger("y"));
            if (reverseZ) tile.setInteger("z", sizeZ - 1 - tile.getInteger("z"));
        }
    }

    private static int unpackState(long[] packed, int index, int bits) {
        long bitIndex = (long) index * bits;
        int word = (int) (bitIndex >>> 6), offset = (int) (bitIndex & 63);
        long state = packed[word] >>> offset;
        if (offset + bits > 64) state |= packed[word + 1] << (64 - offset);
        return (int) (state & ((1L << bits) - 1));
    }

    private static long[] packStates(int[] states, int bits) {
        long[] packed = new long[(int) (((long) states.length * bits + 63) / 64)];
        for (int i = 0; i < states.length; i++) {
            long bitIndex = (long) i * bits;
            int word = (int) (bitIndex >>> 6), offset = (int) (bitIndex & 63);
            packed[word] |= (long) states[i] << offset;
            if (offset + bits > 64) packed[word + 1] |= (long) states[i] >>> (64 - offset);
        }
        return packed;
    }

    private static void validateRegion(String name, NBTTagCompound region, ISchematicRegion expected) {
        if (!region.hasKey("Blocks", 10) || !region.hasKey("BlockEntities", 9)) throw new IllegalArgumentException("missing blocks or block entities");
        NBTTagCompound blocks = region.getCompoundTag("Blocks");
        if (!blocks.hasKey("sizeX", 3) || !blocks.hasKey("sizeY", 3) || !blocks.hasKey("sizeZ", 3)) throw new IllegalArgumentException("missing dimensions");
        int sizeX = blocks.getInteger("sizeX"), sizeY = blocks.getInteger("sizeY"), sizeZ = blocks.getInteger("sizeZ");
        Vec3i expectedSize = expectedSize(expected);
        int expectedX = Math.abs(expectedSize.getX()), expectedY = Math.abs(expectedSize.getY()), expectedZ = Math.abs(expectedSize.getZ());
        if (sizeX <= 0 || sizeY <= 0 || sizeZ <= 0 || sizeX != expectedX || sizeY != expectedY || sizeZ != expectedZ) throw new IllegalArgumentException(name + " dimensions differ: expected " + expectedX + "x" + expectedY + "x" + expectedZ + ", received " + sizeX + "x" + sizeY + "x" + sizeZ);
        long volume = Math.multiplyExact(Math.multiplyExact((long) sizeX, sizeY), sizeZ);
        if (volume > MAX_BLOCKS) throw new IllegalArgumentException("region exceeds block limit");
        if (!blocks.hasKey("BlockStatePalette", 9) || !blocks.hasKey("BlockStates", 12)) throw new IllegalArgumentException("missing block data");
        NBTTagList palette = blocks.getTagList("BlockStatePalette", 10);
        if (palette.tagCount() == 0 || palette.tagCount() > volume) throw new IllegalArgumentException("invalid palette size");
        for (int i = 0; i < palette.tagCount(); i++) validateState(palette.getCompoundTagAt(i));
        int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.tagCount() - 1));
        long bitCount = Math.multiplyExact(volume, bits);
        long[] words = ((NBTTagLongArrayAccessor) blocks.getTag("BlockStates")).bulletcarpet$getData();
        if (words.length != (bitCount + 63) / 64) throw new IllegalArgumentException("packed state length mismatch");
        long mask = (1L << bits) - 1L;
        for (long i = 0; i < volume; i++) {
            long bitIndex = i * bits;
            int word = (int) (bitIndex >>> 6), offset = (int) (bitIndex & 63);
            long state = words[word] >>> offset;
            if (offset + bits > 64) state |= words[word + 1] << (64 - offset);
            if ((state & mask) >= palette.tagCount()) throw new IllegalArgumentException("palette index out of range");
        }
        NBTTagList tiles = region.getTagList("BlockEntities", 10);
        if (tiles.tagCount() > volume) throw new IllegalArgumentException("too many block entities");
        Set<Long> positions = new HashSet<>();
        for (int i = 0; i < tiles.tagCount(); i++) {
            NBTTagCompound tile = tiles.getCompoundTagAt(i);
            if (!tile.hasKey("x", 3) || !tile.hasKey("y", 3) || !tile.hasKey("z", 3) || !tile.hasKey("id", 8)) throw new IllegalArgumentException("invalid block entity");
            int x = tile.getInteger("x"), y = tile.getInteger("y"), z = tile.getInteger("z");
            if (x < 0 || y < 0 || z < 0 || x >= sizeX || y >= sizeY || z >= sizeZ) throw new IllegalArgumentException("block entity outside region");
            long key = ((long) y * sizeZ + z) * sizeX + x;
            if (!positions.add(key)) throw new IllegalArgumentException("duplicate block entity position");
        }
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    private static void validateState(NBTTagCompound tag) {
        if (!tag.hasKey("Name", 8)) throw new IllegalArgumentException("palette state has no name");
        ResourceLocation name = new ResourceLocation(tag.getString("Name"));
        Block block = Block.REGISTRY.getObject(name);
        if (block == null) throw new IllegalArgumentException("unknown block " + name);
        IBlockState state = block.getDefaultState();
        if (tag.hasKey("Properties", 10)) {
            NBTTagCompound properties = tag.getCompoundTag("Properties");
            for (String key : properties.getKeySet()) {
                if (!properties.hasKey(key, 8)) throw new IllegalArgumentException("invalid block property");
                IProperty property = block.getBlockState().getProperty(key);
                if (property == null) throw new IllegalArgumentException("unknown block property " + key);
                com.google.common.base.Optional value = property.parseValue(properties.getString(key));
                if (!value.isPresent()) throw new IllegalArgumentException("invalid block property value");
                state = state.withProperty(property, (Comparable) value.get());
            }
        }
    }

    private static UUID readUuid(NBTTagCompound data) {
        if (!data.hasKey("UUIDM", 4) || !data.hasKey("UUIDL", 4)) return null;
        return new UUID(data.getLong("UUIDM"), data.getLong("UUIDL"));
    }

    private static Vec3i expectedSize(ISchematicRegion region) {
        try {
            return (Vec3i) region.getClass().getMethod("getSize").invoke(region);
        } catch (ReflectiveOperationException e) {
            throw new IllegalArgumentException("Unable to inspect selected region dimensions", e);
        }
    }
}
