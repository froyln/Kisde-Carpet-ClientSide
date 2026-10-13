package dev.bulletcarpet;

import com.google.gson.*;
import java.io.*;
import java.lang.reflect.Field;
import java.nio.file.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.nbt.*;
import net.minecraft.block.Block;
import net.minecraft.block.properties.IProperty;
import net.minecraft.block.state.IBlockState;
import net.minecraft.util.ResourceLocation;

public final class SharedSchematicValidation {
    private static final int MAX_FILE = 16 * 1024 * 1024, MAX_REGIONS = 32, MAX_BLOCKS = 16 * 1024 * 1024;
    private SharedSchematicValidation() {}

    public static int arrayLength(int length, int elementBytes) throws IOException {
        if (length < 0 || (long) length * elementBytes > 64L * 1024 * 1024) throw new IOException("Invalid NBT array length");
        return length;
    }

    public static String placement(String text) {
        if (text.length() > 16384) throw new IllegalArgumentException("Placement settings too large");
        boundedJson(text);
        JsonObject input = new JsonParser().parse(text).getAsJsonObject(), clean = new JsonObject();
        clean.add("origin", position(input.getAsJsonArray("origin"), true));
        clean.addProperty("rotation", rotation(input, "rotation")); clean.addProperty("mirror", mirror(input));
        if (input.has("placements")) {
            JsonArray regions = input.getAsJsonArray("placements"), output = new JsonArray(); Set<String> names = new HashSet<>();
            if (regions.size() > MAX_REGIONS) throw new IllegalArgumentException("Too many subregion placements");
            for (JsonElement item : regions) {
                JsonObject source = item.getAsJsonObject(), settings = source.getAsJsonObject("placement");
                String regionName = source.get("name").getAsString();
                if (regionName.isEmpty() || regionName.length() > 128 || !names.add(regionName)) throw new IllegalArgumentException("Invalid subregion name");
                JsonObject target = new JsonObject(); target.addProperty("name", regionName);
                target.add("pos", position(settings.getAsJsonArray("pos"), false));
                target.add("default_pos", settings.has("default_pos") ? position(settings.getAsJsonArray("default_pos"), false) : target.get("pos"));
                target.addProperty("rotation", rotation(settings, "rotation")); target.addProperty("mirror", mirror(settings));
                target.addProperty("enabled", !settings.has("enabled") || settings.get("enabled").getAsBoolean());
                target.addProperty("ignore_entities", settings.has("ignore_entities") && settings.get("ignore_entities").getAsBoolean());
                JsonObject pair = new JsonObject(); pair.addProperty("name", regionName); pair.add("placement", target); output.add(pair);
            }
            clean.add("placements", output);
        }
        return clean.toString();
    }
    public static void boundedJson(String text) {
        int depth = 0; boolean quoted = false, escape = false;
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (quoted) {
                if (escape) escape = false;
                else if (c == '\\') escape = true;
                else if (c == '"') quoted = false;
            } else if (c == '"') quoted = true;
            else if (c == '{' || c == '[') { if (++depth > 16) throw new IllegalArgumentException("Placement JSON is too deeply nested"); }
            else if (c == '}' || c == ']') { if (--depth < 0) throw new IllegalArgumentException("Invalid placement JSON"); }
        }
        if (depth != 0 || quoted) throw new IllegalArgumentException("Invalid placement JSON");
    }
    private static JsonArray position(JsonArray pos, boolean absolute) {
        if (pos == null || pos.size() != 3) throw new IllegalArgumentException("Invalid placement position");
        JsonArray result = new JsonArray();
        for (int i = 0; i < 3; i++) {
            long value = Long.parseLong(pos.get(i).getAsString());
            if (value < -30000000 || value > 30000000 || (absolute && i == 1 && (value < 0 || value > 255))) throw new IllegalArgumentException("Placement outside world bounds");
            result.add(new JsonPrimitive((int) value));
        }
        return result;
    }
    private static String rotation(JsonObject object, String key) {
        String value = object.has(key) ? object.get(key).getAsString() : "NONE";
        if (!Arrays.asList("NONE", "CLOCKWISE_90", "CLOCKWISE_180", "COUNTERCLOCKWISE_90").contains(value)) throw new IllegalArgumentException("Invalid rotation");
        return value;
    }
    private static String mirror(JsonObject object) {
        String value = object.has("mirror") ? object.get("mirror").getAsString() : "NONE";
        if (!Arrays.asList("NONE", "LEFT_RIGHT", "FRONT_BACK").contains(value)) throw new IllegalArgumentException("Invalid mirror");
        return value;
    }
    public static NBTTagCompound readNbt(Path file, long limit) throws IOException {
        try (DataInputStream input = new DataInputStream(new GZIPInputStream(Files.newInputStream(file)))) {
            NBTTagCompound tag = CompressedStreamTools.read(input, new NBTSizeTracker(limit));
            if (input.read() != -1) throw new IOException("Trailing schematic data");
            return tag;
        }
    }
    public static void validate(Path file, String placement) throws IOException {
        if (Files.size(file) <= 0 || Files.size(file) > MAX_FILE) throw new IllegalArgumentException("Invalid schematic file size");
        NBTTagCompound tag = readNbt(file, 64L * 1024 * 1024);
        if (tag.getInteger("Version") < 1 || tag.getInteger("Version") > 4 || !tag.hasKey("Regions", 10)) throw new IllegalArgumentException("Expected a supported 1.12.2 Litematica file");
        NBTTagCompound metadata = tag.getCompoundTag("Metadata");
        if (metadata.getString("Name").length() > 256 || metadata.getString("Author").length() > 128 || metadata.getString("Description").length() > 8192) throw new IllegalArgumentException("Schematic metadata text too large");
        NBTTagCompound regions = tag.getCompoundTag("Regions");
        if (regions.getSize() == 0 || regions.getSize() > MAX_REGIONS) throw new IllegalArgumentException("Invalid schematic region count");
        long total = 0, chunks = 0;
        for (String key : regions.getKeySet()) {
            if (key.isEmpty() || key.length() > 128 || !regions.hasKey(key, 10)) throw new IllegalArgumentException("Invalid region name");
            NBTTagCompound region = regions.getCompoundTag(key), size = region.getCompoundTag("Size"), position = region.getCompoundTag("Position");
            for (String axis : new String[] {"x", "y", "z"}) {
                if (!size.hasKey(axis, 3) || !position.hasKey(axis, 3) || Math.abs((long) position.getInteger(axis)) > 30000000) throw new IllegalArgumentException("Invalid region position or size");
            }
            long x = Math.abs((long) size.getInteger("x")), y = Math.abs((long) size.getInteger("y")), z = Math.abs((long) size.getInteger("z"));
            if (x == 0 || y == 0 || z == 0 || x > 4096 || y > 256 || z > 4096) throw new IllegalArgumentException("Invalid region dimensions");
            long volume = x * y * z; total += volume; chunks += ((x + 30) / 16) * ((z + 30) / 16);
            if (total > MAX_BLOCKS || chunks > 16384) throw new IllegalArgumentException("Schematic exceeds render size limits");
            NBTTagList palette = region.getTagList("BlockStatePalette", 10);
            if (palette.tagCount() == 0 || palette.tagCount() > Math.min(volume, 65536)) throw new IllegalArgumentException("Invalid block palette");
            for (int i = 0; i < palette.tagCount(); i++) {
                NBTTagCompound state = palette.getCompoundTagAt(i);
                if (!Block.REGISTRY.containsKey(new ResourceLocation(state.getString("Name")))) throw new IllegalArgumentException("Unknown block state");
                validateState(state);
            }
            if (!region.hasKey("BlockStates", 12)) throw new IllegalArgumentException("Missing block data");
            long[] words;
            try { Field data = Arrays.stream(NBTTagLongArray.class.getDeclaredFields()).filter(f -> f.getType() == long[].class).findFirst().orElseThrow(() -> new IllegalStateException("Missing packed block array")); data.setAccessible(true); words = (long[]) data.get(region.getTag("BlockStates")); }
            catch (ReflectiveOperationException e) { throw new IOException("Unable to read block data", e); }
            int bits = Math.max(2, 32 - Integer.numberOfLeadingZeros(palette.tagCount() - 1));
            if (words.length != (volume * bits + 63) / 64) throw new IllegalArgumentException("Incorrect packed block length");
            long mask = (1L << bits) - 1;
            for (long i = 0; i < volume; i++) {
                long bit = i * bits; int word = (int) (bit >>> 6), offset = (int) (bit & 63); long id = words[word] >>> offset;
                if (offset + bits > 64) id |= words[word + 1] << (64 - offset);
                if ((id & mask) >= palette.tagCount()) throw new IllegalArgumentException("Invalid palette index");
            }
            NBTTagList tiles = region.getTagList("TileEntities", 10); Set<Long> positions = new HashSet<>();
            if (tiles.tagCount() > volume) throw new IllegalArgumentException("Too many block entities");
            for (int i = 0; i < tiles.tagCount(); i++) {
                NBTTagCompound tile = tiles.getCompoundTagAt(i); int tx = tile.getInteger("x"), ty = tile.getInteger("y"), tz = tile.getInteger("z");
                if (!tile.hasKey("id", 8) || !tile.hasKey("x", 3) || !tile.hasKey("y", 3) || !tile.hasKey("z", 3) || tx < 0 || ty < 0 || tz < 0 || tx >= x || ty >= y || tz >= z || !positions.add((ty * z + tz) * x + tx)) throw new IllegalArgumentException("Invalid block entity position");
            }
        }
        JsonObject transform = new JsonParser().parse(placement(placement)).getAsJsonObject();
        if (transform.has("placements")) for (JsonElement item : transform.getAsJsonArray("placements")) {
            if (!regions.hasKey(item.getAsJsonObject().get("name").getAsString(), 10)) throw new IllegalArgumentException("Placement refers to unknown subregion");
        }
    }
    @SuppressWarnings({"rawtypes", "unchecked"})
    public static void validateState(NBTTagCompound tag) {
        if (!tag.hasKey("Name", 8)) throw new IllegalArgumentException("palette state has no name");
        ResourceLocation name = new ResourceLocation(tag.getString("Name"));
        if (!Block.REGISTRY.containsKey(name)) throw new IllegalArgumentException("unknown block " + name);
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

}
