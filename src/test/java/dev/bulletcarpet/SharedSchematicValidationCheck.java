package dev.bulletcarpet;

import java.io.*;
import java.nio.file.*;
import net.minecraft.init.Bootstrap;
import net.minecraft.nbt.*;

public final class SharedSchematicValidationCheck {
    private static final String PLACEMENT = "{\"origin\":[12,64,-30],\"rotation\":\"CLOCKWISE_90\",\"mirror\":\"LEFT_RIGHT\"}";
    private interface Action { void run() throws Exception; }
    private static void check(boolean valid, String message) { if (!valid) throw new AssertionError(message); }
    private static void rejected(Action action) throws Exception {
        try { action.run(); throw new AssertionError("Malformed shared data accepted"); }
        catch (IOException | IllegalArgumentException expected) {}
    }
    private static NBTTagCompound position(int x, int y, int z) {
        NBTTagCompound tag = new NBTTagCompound(); tag.setInteger("x", x); tag.setInteger("y", y); tag.setInteger("z", z); return tag;
    }
    private static NBTTagCompound fixture(int sx, int sy, int sz) {
        NBTTagCompound file = new NBTTagCompound(), regions = new NBTTagCompound(), region = new NBTTagCompound();
        file.setInteger("Version", 4); file.setTag("Metadata", new NBTTagCompound());
        region.setTag("Position", position(0, 0, 0)); region.setTag("Size", position(sx * 3, sy, sz));
        NBTTagList palette = new NBTTagList();
        for (String name : new String[] {"minecraft:stone", "minecraft:dirt", "minecraft:chest"}) { NBTTagCompound state = new NBTTagCompound(); state.setString("Name", name); palette.appendTag(state); }
        region.setTag("BlockStatePalette", palette); region.setTag("BlockStates", new NBTTagLongArray(new long[] {36}));
        NBTTagList tiles = new NBTTagList(); NBTTagCompound tile = position(2, 0, 0); tile.setString("id", "minecraft:chest"); tiles.appendTag(tile); region.setTag("TileEntities", tiles);
        regions.setTag("fixture", region); file.setTag("Regions", regions); return file;
    }
    private static void validate(NBTTagCompound data, String placement) throws Exception {
        Path file = Files.createTempFile("kisde-client-validation-", ".litematic");
        try {
            try (OutputStream output = Files.newOutputStream(file)) { CompressedStreamTools.writeCompressed(data, output); }
            SharedSchematicValidation.validate(file, placement);
        } finally { Files.deleteIfExists(file); }
    }
    public static void main(String[] args) throws Exception {
        Bootstrap.register();
        for (int x : new int[] {-1, 1}) for (int y : new int[] {-1, 1}) for (int z : new int[] {-1, 1}) validate(fixture(x, y, z), PLACEMENT);
        NBTTagCompound bad = fixture(1, 1, 1); bad.getCompoundTag("Regions").getCompoundTag("fixture").setTag("BlockStates", new NBTTagLongArray(new long[0]));
        final NBTTagCompound shortStates = bad; rejected(() -> validate(shortStates, PLACEMENT));
        bad = fixture(1, 1, 1); bad.getCompoundTag("Regions").getCompoundTag("fixture").setTag("BlockStates", new NBTTagLongArray(new long[] {3}));
        final NBTTagCompound badIndex = bad; rejected(() -> validate(badIndex, PLACEMENT));
        bad = fixture(1, 1, 1); bad.getCompoundTag("Regions").getCompoundTag("fixture").getTagList("BlockStatePalette", 10).getCompoundTagAt(0).setString("Name", "minecraft:does_not_exist");
        final NBTTagCompound unknown = bad; rejected(() -> validate(unknown, PLACEMENT));
        bad = fixture(1, 1, 1); NBTTagCompound region = bad.getCompoundTag("Regions").getCompoundTag("fixture"); region.getTagList("TileEntities", 10).getCompoundTagAt(0).setInteger("x", 3);
        final NBTTagCompound outsideTile = bad; rejected(() -> validate(outsideTile, PLACEMENT));
        bad = fixture(1, 1, 1); region = bad.getCompoundTag("Regions").getCompoundTag("fixture"); region.getTagList("TileEntities", 10).appendTag(region.getTagList("TileEntities", 10).getCompoundTagAt(0).copy());
        final NBTTagCompound duplicate = bad; rejected(() -> validate(duplicate, PLACEMENT));
        bad = fixture(1, 1, 1); region = bad.getCompoundTag("Regions").getCompoundTag("fixture"); region.setTag("Size", position(4096, 1, 4096));
        final NBTTagCompound chunks = bad; rejected(() -> validate(chunks, PLACEMENT));
        bad = fixture(1, 1, 1); region = bad.getCompoundTag("Regions").getCompoundTag("fixture"); region.setTag("Size", position(256, 256, 256)); region.setTag("BlockStates", new NBTTagLongArray(new long[524288]));
        bad.getCompoundTag("Regions").setTag("second", fixture(1, 1, 1).getCompoundTag("Regions").getCompoundTag("fixture"));
        final NBTTagCompound aggregate = bad; rejected(() -> validate(aggregate, PLACEMENT));
        rejected(() -> SharedSchematicValidation.placement("{\"origin\":[0,256,0]}"));
        rejected(() -> SharedSchematicValidation.placement("{\"origin\":[0,64,0],\"rotation\":\"INVALID\"}"));
        rejected(() -> SharedSchematicValidation.placement("{\"origin\":[18446744073709551616,64,0]}"));
        check(!SharedSchematicValidation.placement("{\"origin\":[0,64,0],\"schematic\":\"../../untrusted\"}").contains("untrusted"), "Remote file path retained");
        for (int width : new int[] {1, 4, 8}) {
            final int bytes = width;
            rejected(() -> SharedSchematicValidation.arrayLength(-1, bytes));
            rejected(() -> SharedSchematicValidation.arrayLength(Integer.MAX_VALUE, bytes));
            check(SharedSchematicValidation.arrayLength(0, width) == 0, "Empty array rejected");
            check(SharedSchematicValidation.arrayLength(64 * 1024 * 1024 / width, width) > 0, "Array boundary rejected");
        }
        System.out.println("Shared signed regions, palette/packing/tile validation, aggregate bounds, placement sanitization and NBT array guards passed");
    }
}
