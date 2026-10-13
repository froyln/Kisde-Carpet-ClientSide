package dev.bulletcarpet;

import com.google.gson.*;
import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.litematica.schematic.ISchematic;
import fi.dy.masa.litematica.schematic.placement.*;
import io.netty.buffer.Unpooled;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.GZIPInputStream;
import net.minecraft.client.Minecraft;
import net.minecraft.client.network.NetHandlerPlayClient;
import net.minecraft.nbt.*;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.text.TextComponentString;

public final class SharedSchematics {
    public static final String CHANNEL = "bc:schematics";
    private static final int MAX_FILE = 16 * 1024 * 1024, CHUNK = 24576;
    public static final SharedSchematics INSTANCE = new SharedSchematics();
    public final List<NBTTagCompound> pageEntries = new ArrayList<>();
    private final Map<String, NBTTagCompound> known = new HashMap<>();
    private final Map<String, SchematicPlacement> loaded = new HashMap<>();
    private final Queue<String> queuedLoads = new ArrayDeque<>();
    private NetHandlerPlayClient connection;
    private Upload upload;
    private Download download;
    private String requestedDownload;
    public boolean enabled;
    public int page, pageSize = 8, total, offset;
    public String search = "";
    private int listRequest;
    private final List<NBTTagCompound> catalog = new ArrayList<>(), incomingCatalog = new ArrayList<>();
    private boolean listing;
    private int incomingTotal;
    private long listActivity;
    public String status = "Waiting for server sharing settings";
    private long activity;
    private int dimension;

    public void tick(Minecraft mc) {
        if (connection != mc.getConnection()) reset(mc.getConnection());
        if (mc.player == null) return;
        if (dimension != mc.player.dimension) { removeLoaded(); dimension = mc.player.dimension; queuedLoads.clear(); cancel(); }
        try {
            if (listing && System.currentTimeMillis() - listActivity > 10000) { listing = false; status = "Catalog refresh timed out; use Refresh to retry"; }
            if ((upload != null || download != null || requestedDownload != null) && System.currentTimeMillis() - activity > 45000) throw new IOException("Schematic transfer timed out");
            if (upload != null && upload.ready && !upload.sent && System.currentTimeMillis() - upload.lastSent >= 20) {
                if (upload.offset < upload.bytes.length) {
                    int end = Math.min(upload.bytes.length, upload.offset + CHUNK); NBTTagCompound chunk = message("Chunk");
                    chunk.setString("Transfer", upload.token); chunk.setInteger("Offset", upload.offset); chunk.setByteArray("Data", Arrays.copyOfRange(upload.bytes, upload.offset, end)); send(chunk);
                    upload.offset = end; upload.ready = false; upload.lastSent = System.currentTimeMillis(); activity = upload.lastSent; status = "Uploading " + upload.offset * 100L / upload.bytes.length + "%";
                } else { NBTTagCompound end = message("EndUpload"); end.setString("Transfer", upload.token); send(end); upload.sent = true; activity = System.currentTimeMillis(); status = "Server is checking the schematic"; }
            }
            if (enabled && !busy() && !queuedLoads.isEmpty()) load(queuedLoads.remove());
        } catch (Exception e) { failure(e.getMessage()); }
    }
    public void receive(PacketBuffer bytes) {
        try {
            if (connection != Minecraft.getMinecraft().getConnection()) reset(Minecraft.getMinecraft().getConnection());
            NBTTagCompound data = bytes.readCompoundTag();
            if (data == null || bytes.isReadable()) throw new IOException("Invalid sharing response");
            activity = System.currentTimeMillis();
            switch (data.getString("Action")) {
                case "Status":
                    enabled = data.getBoolean("Enabled");
                    if (!enabled) { cancelLocal(); removeLoaded(); known.clear(); pageEntries.clear(); catalog.clear(); incomingCatalog.clear(); listing = false; total = 0; status = "Sharing is disabled. Ask an operator to enable schematicShare."; }
                    else { status = "Sharing enabled"; refresh(); }
                    break;
                case "List":
                    if (!listing || data.hasKey("Request", 3) && data.getInteger("Request") != listRequest) break;
                    int listedPage = data.getInteger("Page"), listedTotal = data.getInteger("Total");
                    int listedOffset = data.hasKey("Offset", 3) ? data.getInteger("Offset") : listedPage * 12;
                    NBTTagList entries = data.getTagList("Entries", 10);
                    if (listedPage < 0 || listedTotal < 0 || listedTotal > 256 || listedOffset != incomingCatalog.size() || entries.tagCount() > Math.min(12, listedTotal - listedOffset) || (listedOffset != 0 && listedTotal != incomingTotal)) throw new IOException("Shared catalog changed; use Refresh to retry");
                    incomingTotal = listedTotal; listActivity = System.currentTimeMillis();
                    for (int i = 0; i < entries.tagCount(); i++) { NBTTagCompound entry = entries.getCompoundTagAt(i); validateEntry(entry); incomingCatalog.add(entry); }
                    if (incomingCatalog.size() < incomingTotal) {
                        if (entries.tagCount() != 12) throw new IOException("Incomplete shared catalog page");
                        requestCatalogPage();
                    } else {
                        listing = false; catalog.clear(); catalog.addAll(incomingCatalog); incomingCatalog.clear();
                        for (NBTTagCompound entry : catalog) known.put(entry.getString("Id"), entry);
                        showWindow();
                        if (!busy()) status = "";
                    }
                    break;
                case "Ready": if (upload != null && upload.token.equals(data.getString("Transfer"))) { if (data.getInteger("Offset") != upload.offset) throw new IOException("Incorrect upload acknowledgement"); upload.ready = true; status = "Uploading " + upload.offset * 100L / upload.bytes.length + "%"; } break;
                case "Uploaded": if (upload != null && upload.token.equals(data.getString("Transfer"))) { upload = null; status = "Shared with the server"; refresh(); } break;
                case "Changed":
                    NBTTagCompound changed = data.getCompoundTag("Entry"); validateEntry(changed); String id = changed.getString("Id"); known.put(id, changed);
                    if (loaded.containsKey(id)) queueLoad(id);
                    refresh(); break;
                case "Deleted":
                    String removed = UUID.fromString(data.getString("Id")).toString(); known.remove(removed);
                    SchematicPlacement placement = loaded.remove(removed); if (placement != null) DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);
                    queuedLoads.remove(removed); if (download != null && removed.equals(download.entry.getString("Id"))) cancel();
                    status = "Shared schematic removed"; refresh(); break;
                case "Download": beginDownload(data); break;
                case "Chunk": receiveChunk(data); break;
                case "Downloaded": finishDownload(data); break;
                case "Error": failure(data.getString("Message")); break;
                default: throw new IOException("Unknown sharing response");
            }
        } catch (Exception e) { failure(e.getMessage() == null ? "Unable to read shared schematic" : e.getMessage()); }
    }
    public boolean busy() { return upload != null || download != null || requestedDownload != null; }
    public NBTTagCompound entry(String id) { return known.get(id); }
    public void refresh() {
        if (!enabled) return;
        incomingCatalog.clear(); listing = true; listActivity = System.currentTimeMillis(); requestCatalogPage();
    }
    private void requestCatalogPage() {
        NBTTagCompound data = message("List"); data.setInteger("Page", incomingCatalog.size() / 12);
        data.setInteger("Request", ++listRequest); data.setInteger("PageSize", 12); send(data);
    }
    public void showWindow() {
        String query = search.toLowerCase(java.util.Locale.ROOT);
        List<NBTTagCompound> matches = new ArrayList<>();
        for (NBTTagCompound entry : catalog) if (entry.getString("Name").toLowerCase(java.util.Locale.ROOT).contains(query) || entry.getString("Author").toLowerCase(java.util.Locale.ROOT).contains(query)) matches.add(entry);
        total = matches.size(); offset = Math.max(0, Math.min(Math.max(0, total - pageSize), offset)); page = offset / pageSize;
        pageEntries.clear(); pageEntries.addAll(matches.subList(offset, Math.min(total, offset + pageSize)));
    }
    public void share(String updating) {
        share(updating, DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement());
    }
    public void share(String updating, SchematicPlacement placement) {
        try {
            if (!enabled || busy()) throw new IOException("Sharing is unavailable or a transfer is active");
            if (placement == null) throw new IOException("Select a Litematica placement to share first");
            ISchematic schematic = placement.getSchematic();
            NBTTagCompound data = (NBTTagCompound) schematic.getClass().getMethod("toTag").invoke(schematic);
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (OutputStream limited = new FilterOutputStream(bytes) {
                int size;
                public void write(int b) throws IOException { if (++size > MAX_FILE) throw new IOException("Schematic exceeds 16 MiB"); out.write(b); }
                public void write(byte[] b, int off, int len) throws IOException { if (len > MAX_FILE - size) throw new IOException("Schematic exceeds 16 MiB"); size += len; out.write(b, off, len); }
            }) { CompressedStreamTools.writeCompressed(data, limited); }
            NBTTagCompound original = updating == null ? null : known.get(updating);
            if (updating != null && (original == null || !original.getBoolean("CanManage"))) throw new IOException("Only the author or an operator can update this share");
            upload = new Upload(bytes.toByteArray()); activity = System.currentTimeMillis();
            NBTTagCompound begin = message("BeginUpload"); begin.setString("Transfer", upload.token); begin.setString("Id", updating == null ? "" : updating);
            begin.setLong("Revision", original == null ? 0 : original.getLong("Revision")); begin.setString("Name", placement.getName());
            begin.setString("Placement", placement.baseSettingsToJson(false).toString()); begin.setInteger("Bytes", upload.bytes.length); begin.setString("Hash", hash(upload.bytes)); send(begin);
            status = "Requesting upload";
        } catch (Exception e) { failure(e.getMessage() == null ? "Unable to share selected placement" : e.getMessage()); }
    }
    public void delete(String id) {
        NBTTagCompound entry = known.get(id); if (entry == null || !entry.getBoolean("CanManage") || busy()) return;
        NBTTagCompound request = message("Delete"); request.setString("Id", id); request.setLong("Revision", entry.getLong("Revision")); send(request);
    }
    public boolean rename(String id, long revision, String name) {
        NBTTagCompound entry = known.get(id);
        if (!enabled || busy() || entry == null || !entry.getBoolean("CanManage")) return false;
        if (name.isEmpty() || name.length() > 96 || name.chars().anyMatch(c -> Character.isISOControl(c) || c == 167)) {
            status = "Use 1–96 characters without control or formatting codes"; return false;
        }
        NBTTagCompound request = message("Rename"); request.setString("Id", id); request.setLong("Revision", revision); request.setString("Name", name); send(request);
        return true;
    }
    private void queueLoad(String id) { if (!queuedLoads.contains(id) && queuedLoads.size() < 32) queuedLoads.add(id); }
    public void load(String id) {
        try {
            NBTTagCompound entry = known.get(id); if (entry == null || !enabled) throw new IOException("Shared schematic is unavailable");
            if (entry.getInteger("Dimension") != Minecraft.getMinecraft().player.dimension) throw new IOException("Join dimension " + entry.getInteger("Dimension") + " to load this placement");
            if (busy()) { queueLoad(id); return; }
            Path file = cacheFile(entry);
            if (Files.isRegularFile(file) && Files.size(file) == entry.getInteger("Bytes") && hash(Files.readAllBytes(file)).equals(entry.getString("Hash"))) {
                loadPlacement(entry, file); status = "Loaded shared overlay"; return;
            }
            NBTTagCompound request = message("Download"); request.setString("Id", id); requestedDownload = id; activity = System.currentTimeMillis(); send(request); status = "Requesting schematic";
        } catch (Exception e) { failure(e.getMessage() == null ? "Unable to load shared schematic" : e.getMessage()); }
    }
    private Path cacheRoot() throws IOException {
        String address = Minecraft.getMinecraft().getCurrentServerData() == null ? "unknown" : Minecraft.getMinecraft().getCurrentServerData().serverIP;
        Path root = DataManager.getSchematicsBaseDirectory().resolve(".kisde-shared").resolve(hash(address.getBytes(StandardCharsets.UTF_8)).substring(0, 16)); Files.createDirectories(root); return root;
    }
    private Path cacheFile(NBTTagCompound entry) throws IOException {
        Path directory = cacheRoot().resolve(UUID.fromString(entry.getString("Id")).toString()); Files.createDirectories(directory); return directory.resolve(entry.getString("Hash") + ".litematic");
    }
    private void beginDownload(NBTTagCompound data) throws IOException {
        if (download != null || upload != null) throw new IOException("Unexpected simultaneous download");
        NBTTagCompound entry = data.getCompoundTag("Entry"); validateEntry(entry);
        if (!enabled || !entry.getString("Id").equals(requestedDownload)) throw new IOException("Unrequested shared download");
        requestedDownload = null; known.put(entry.getString("Id"), entry);
        if (entry.getInteger("Dimension") != Minecraft.getMinecraft().player.dimension) throw new IOException("Shared dimension changed");
        String token = UUID.fromString(data.getString("Transfer")).toString(); Path temp = Files.createTempFile(cacheRoot(), "incoming-", ".tmp");
        download = new Download(token, entry, temp, Files.newOutputStream(temp)); status = "Downloading schematic";
    }
    private void receiveChunk(NBTTagCompound data) throws IOException {
        if (download == null || !download.token.equals(data.getString("Transfer"))) return;
        byte[] chunk = data.getByteArray("Data");
        if (data.getInteger("Offset") != download.offset || chunk.length == 0 || chunk.length > CHUNK || chunk.length > download.entry.getInteger("Bytes") - download.offset) throw new IOException("Invalid shared download chunk");
        download.stream.write(chunk); download.offset += chunk.length; status = "Downloading " + download.offset * 100L / download.entry.getInteger("Bytes") + "%";
    }
    private void finishDownload(NBTTagCompound data) throws Exception {
        if (download == null || !download.token.equals(data.getString("Transfer"))) return;
        Download complete = download; download = null; complete.stream.close();
        try {
            if (complete.offset != complete.entry.getInteger("Bytes") || !hash(Files.readAllBytes(complete.path)).equals(complete.entry.getString("Hash"))) throw new IOException("Shared file checksum mismatch");
            Path destination = cacheFile(complete.entry); Files.move(complete.path, destination, StandardCopyOption.ATOMIC_MOVE, StandardCopyOption.REPLACE_EXISTING);
            NBTTagCompound current = known.get(complete.entry.getString("Id"));
            if (current != null && current.getLong("Revision") != complete.entry.getLong("Revision")) { queueLoad(current.getString("Id")); return; }
            if (current != null) { loadPlacement(complete.entry, destination); status = "Downloaded and loaded shared overlay"; }
        } finally { Files.deleteIfExists(complete.path); }
    }
    private void loadPlacement(NBTTagCompound entry, Path file) throws Exception {
        SharedSchematicValidation.validate(file, entry.getString("Placement"));
        JsonObject json = new JsonParser().parse(SharedSchematicValidation.placement(entry.getString("Placement"))).getAsJsonObject();
        json.addProperty("schematic", file.toAbsolutePath().toString()); json.addProperty("name", entry.getString("Name")); json.addProperty("enabled", true); json.addProperty("locked", true);
        SchematicPlacementUnloaded settings = SchematicPlacementUnloaded.fromJson(json);
        if (settings == null) throw new IOException("Invalid shared placement settings");
        SchematicPlacement placement = settings.fullyLoadPlacement(); if (placement == null) throw new IOException("Litematica could not load the shared schematic");
        placement.setShouldBeSaved(false);
        SchematicPlacement old = loaded.remove(entry.getString("Id")); if (old != null) DataManager.getSchematicPlacementManager().removeSchematicPlacement(old);
        DataManager.getSchematicPlacementManager().addSchematicPlacement(placement, true); loaded.put(entry.getString("Id"), placement);
    }
    private static void validateEntry(NBTTagCompound entry) throws IOException {
        try { UUID.fromString(entry.getString("Id")); UUID.fromString(entry.getString("Owner")); }
        catch (IllegalArgumentException e) { throw new IOException("Invalid shared schematic identity", e); }
        SharedSchematicValidation.placement(entry.getString("Placement"));
        if (!entry.getString("Hash").matches("[0-9a-f]{64}") || entry.getInteger("Bytes") <= 0 || entry.getInteger("Bytes") > MAX_FILE || entry.getString("Placement").length() > 16384 || entry.getString("Name").length() > 96) throw new IOException("Invalid shared schematic metadata");
    }
    public void cancel() { if (busy() && connection != null) send(message("Cancel")); cancelLocal(); }
    private void cancelLocal() {
        upload = null; requestedDownload = null;
        if (download != null) { try { download.stream.close(); Files.deleteIfExists(download.path); } catch (IOException ignored) {} download = null; }
    }
    private void removeLoaded() {
        if (Minecraft.getMinecraft().world != null) for (SchematicPlacement placement : loaded.values()) {
            if (DataManager.getSchematicPlacementManager().getAllSchematicPlacements().contains(placement)) DataManager.getSchematicPlacementManager().removeSchematicPlacement(placement);
        }
        loaded.clear(); queuedLoads.clear();
    }
    private void reset(NetHandlerPlayClient next) { cancelLocal(); removeLoaded(); known.clear(); pageEntries.clear(); catalog.clear(); incomingCatalog.clear(); listing = false; enabled = false; search = ""; page = total = offset = listRequest = 0; connection = next; dimension = Minecraft.getMinecraft().player == null ? 0 : Minecraft.getMinecraft().player.dimension; status = "Waiting for server sharing settings"; }
    private void failure(String text) {
        listing = false; incomingCatalog.clear(); cancel(); status = text == null ? "Schematic sharing failed" : text.substring(0, Math.min(text.length(), 256));
        if (Minecraft.getMinecraft().player != null) Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[Kisde-Carpet] " + status));
    }
    private static NBTTagCompound message(String action) { NBTTagCompound data = new NBTTagCompound(); data.setString("Action", action); return data; }
    private void send(NBTTagCompound data) {
        if (connection == null) return;
        PacketBuffer bytes = new PacketBuffer(Unpooled.buffer());
        try { bytes.writeCompoundTag(data); if (bytes.readableBytes() > 32767) throw new IOException("Sharing request too large"); connection.sendPacket(new CPacketCustomPayload(CHANNEL, bytes)); }
        catch (IOException e) { if (bytes.refCnt() > 0) bytes.release(); throw new IllegalArgumentException(e.getMessage(), e); }
    }
    private static String hash(byte[] bytes) {
        try { MessageDigest digest = MessageDigest.getInstance("SHA-256"); StringBuilder text = new StringBuilder(); for (byte b : digest.digest(bytes)) text.append(String.format("%02x", b & 255)); return text.toString(); }
        catch (NoSuchAlgorithmException e) { throw new AssertionError(e); }
    }
    private static final class Upload {
        final String token = UUID.randomUUID().toString(); final byte[] bytes; int offset; boolean ready, sent; long lastSent;
        Upload(byte[] bytes) { this.bytes = bytes; }
    }
    private static final class Download {
        final String token; final NBTTagCompound entry; final Path path; final OutputStream stream; int offset;
        Download(String token, NBTTagCompound entry, Path path, OutputStream stream) { this.token = token; this.entry = entry; this.path = path; this.stream = stream; }
    }
}
