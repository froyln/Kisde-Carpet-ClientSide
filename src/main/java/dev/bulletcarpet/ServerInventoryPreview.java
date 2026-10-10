package dev.bulletcarpet;

import net.minecraft.client.Minecraft;
import net.minecraft.item.ItemStack;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityMinecartChest;
import net.minecraft.entity.item.EntityMinecartHopper;
import net.minecraft.entity.item.EntityMinecartContainer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.PacketBuffer;
import net.minecraft.network.play.client.CPacketCustomPayload;
import net.minecraft.util.math.BlockPos;

import java.lang.reflect.InvocationHandler;
import java.lang.reflect.Proxy;
import java.util.Arrays;
import java.util.UUID;

public final class ServerInventoryPreview {
    private static final String REQUEST = "pca:sync_be";
    private static final String RESPONSE = "pca:update_be";
    private static final String ENTITY_REQUEST = "bc:inv_req";
    private static final String ENTITY_RESPONSE = "bc:inv_ack";
    private static final long POLL_NANOS = 300_000_000L;
    private static final long RETRY_NANOS = 1_000_000_000L;
    private static final long TARGET_IDLE_NANOS = 2_000_000_000L;
    private static final ServerInventoryPreview INSTANCE = new ServerInventoryPreview();
    private static boolean active;
    private static int dimension;
    private static BlockPos target;
    private static UUID targetEntity;
    private static Object inventoryView;
    private static boolean available;
    private static long lastRenderedAt;
    private static long nextRequestAt;
    private static long pendingStartedAt;
    private static int requestId;
    private static int pendingRequestId;

    public static ServerInventoryPreview instance() {
        return INSTANCE;
    }

    public static boolean isActive() {
        return active;
    }

    public static BlockPos getTarget() {
        return target;
    }

    public static boolean hasTarget() {
        return target != null || targetEntity != null;
    }

    public static Object getSnapshot() {
        return available ? inventoryView : null;
    }

    private static Object createSnapshot(ItemStack[] stacks) {
        try {
            Class<?> view = Class.forName("fi.dy.masa.malilib.util.inventory.InventoryView", false,
                    ServerInventoryPreview.class.getClassLoader());
            InvocationHandler handler = (proxy, method, args) -> {
                if ("getSize".equals(method.getName())) return stacks.length;
                if ("getStack".equals(method.getName())) {
                    int slot = (Integer) args[0];
                    return slot < 0 || slot >= stacks.length ? ItemStack.EMPTY : stacks[slot];
                }
                if ("toString".equals(method.getName())) return "Kisde-Carpet server inventory";
                return null;
            };
            return Proxy.newProxyInstance(view.getClassLoader(), new Class<?>[] {view}, handler);
        } catch (ClassNotFoundException e) {
            return null;
        }
    }

    public void onCustomPayload(String channel, PacketBuffer data) {
        boolean entityResponse = ENTITY_RESPONSE.equals(channel);
        if (!RESPONSE.equals(channel) && !entityResponse) return;
        try {
            String dimensionName = data.readString(64);
            UUID entityId = entityResponse ? new UUID(data.readLong(), data.readLong()) : null;
            BlockPos pos = entityResponse ? null : data.readBlockPos();
            int responseId = data.readInt();
            NBTTagCompound snapshot = data.readCompoundTag();
            if (snapshot == null || data.isReadable() || responseId != pendingRequestId ||
                    !dimensionName.equals(dimensionName(dimension)) ||
                    (entityResponse ? !entityId.equals(targetEntity) : !pos.equals(target))) {
                return;
            }
            boolean hasData = snapshot.getBoolean("Available");
            ItemStack[] parsed = hasData ? readItems(snapshot) : null;
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if (responseId != pendingRequestId ||
                        (entityResponse ? !entityId.equals(targetEntity) : !pos.equals(target)) || Minecraft.getMinecraft().world == null ||
                        Minecraft.getMinecraft().world.provider.getDimensionType().getId() != dimension) return;
                available = hasData && parsed != null;
                inventoryView = available ? createSnapshot(parsed) : null;
                pendingRequestId = 0;
            });
        } catch (Exception ignored) {
            inventoryView = null;
            available = false;
            pendingRequestId = 0;
            pendingStartedAt = 0;
        }
    }

    public static Object getBlockSnapshot(BlockPos pos) {
        trackTarget(pos, null);
        return getSnapshot();
    }

    public static Object getEntitySnapshot(Entity entity) {
        trackTarget(null, entity.getUniqueID());
        return getSnapshot();
    }

    private static void trackTarget(BlockPos block, UUID entity) {
        Minecraft minecraft = Minecraft.getMinecraft();
        int currentDimension = minecraft.world.provider.getDimensionType().getId();
        if (currentDimension != dimension || !sameTarget(block, entity)) {
            dimension = currentDimension;
            target = block == null ? null : block.toImmutable();
            targetEntity = entity;
            inventoryView = null;
            available = false;
            pendingRequestId = 0;
        }
        active = true;
        lastRenderedAt = System.nanoTime();
    }

    public void onRunGameLoop(Minecraft minecraft) {
        long now = System.nanoTime();
        if (!LiteModBulletCarpet.serverHasChannel(REQUEST) || !active ||
                now - lastRenderedAt > TARGET_IDLE_NANOS) {
            clear();
            return;
        }
        if (!hasTarget()) return;
        if (pendingRequestId != 0) {
            if (now - pendingStartedAt < RETRY_NANOS) return;
            pendingRequestId = 0;
            inventoryView = null;
            available = false;
        }
        if (now < nextRequestAt) return;
        nextRequestAt = now + POLL_NANOS;
        pendingStartedAt = now;
        pendingRequestId = ++requestId;
        if (requestId == 0) pendingRequestId = ++requestId;
        PacketBuffer request = new PacketBuffer(io.netty.buffer.Unpooled.buffer(12));
        request.writeInt(pendingRequestId);
        if (targetEntity != null) {
            request.writeLong(targetEntity.getMostSignificantBits());
            request.writeLong(targetEntity.getLeastSignificantBits());
            minecraft.player.connection.sendPacket(new CPacketCustomPayload(ENTITY_REQUEST, request));
        } else {
            request.writeBlockPos(target);
            minecraft.player.connection.sendPacket(new CPacketCustomPayload(REQUEST, request));
        }
    }

    private static void clear() {
        active = false;
        target = null;
        targetEntity = null;
        inventoryView = null;
        available = false;
        pendingRequestId = 0;
        nextRequestAt = 0;
        pendingStartedAt = 0;
        lastRenderedAt = 0;
    }

    private static boolean sameTarget(BlockPos block, UUID entity) {
        return entity != null ? entity.equals(targetEntity) && target == null : targetEntity == null && (target == null ? block == null : target.equals(block));
    }

    public static boolean supportedMinecart(Entity entity) {
        return entity != null && (entity.getClass() == EntityMinecartChest.class || entity.getClass() == EntityMinecartHopper.class) &&
                entity instanceof EntityMinecartContainer;
    }

    private static ItemStack[] readItems(NBTTagCompound snapshot) {
        int size = snapshot.getInteger("Size");
        if (size < 0 || size > 54 || !snapshot.hasKey("Items", 9)) return null;
        ItemStack[] result = new ItemStack[size];
        Arrays.fill(result, ItemStack.EMPTY);
        NBTTagList list = snapshot.getTagList("Items", 10);
        if (list.tagCount() > size) return null;
        boolean[] seen = new boolean[size];
        try {
            for (int i = 0; i < list.tagCount(); i++) {
                NBTTagCompound itemTag = list.getCompoundTagAt(i);
                if (!itemTag.hasKey("Slot", 1)) return null;
                int slot = itemTag.getByte("Slot") & 255;
                if (slot >= size || seen[slot]) return null;
                ItemStack stack = new ItemStack(itemTag);
                if (stack.isEmpty()) return null;
                result[slot] = stack;
                seen[slot] = true;
            }
            return result;
        } catch (RuntimeException e) {
            return null;
        }
    }

    private static String dimensionName(int dimension) {
        if (dimension == -1) return "minecraft:the_nether";
        if (dimension == 1) return "minecraft:the_end";
        return "minecraft:overworld";
    }

}
