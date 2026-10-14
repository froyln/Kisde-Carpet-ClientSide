package dev.bulletcarpet;

import java.lang.reflect.Field;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.math.BlockPos;
import io.netty.buffer.Unpooled;
import sun.misc.Unsafe;

public final class AnvilSyncCheck {
    private static final class Repair extends ContainerRepair implements AnvilAccess {
        boolean uncapped;
        Repair() { super(null, null, BlockPos.ORIGIN, null); }
        public void kisde$setUncappedCost(boolean value) { uncapped = value; }
        public boolean kisde$hasUncappedCost() { return uncapped; }
    }
    private static void check(boolean value, String message) { if (!value) throw new AssertionError(message); }
    public static void main(String[] args) throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe"); field.setAccessible(true);
        Repair repair = (Repair)((Unsafe)field.get(null)).allocateInstance(Repair.class); repair.windowId = 7;
        for (int cost : new int[]{0, 39, 40, 32767, 40000, Integer.MAX_VALUE}) {
            PacketBuffer packet = new PacketBuffer(Unpooled.buffer());
            try {
                packet.writeInt(7); packet.writeInt(cost); packet.writeBoolean(true);
                check(AnvilSync.apply(repair, packet.readInt(), packet.readInt(), packet.readBoolean()), "Full-width cost rejected");
                check(repair.maximumCost == cost && repair.uncapped, "Full-width cost changed");
            } finally { packet.release(); }
        }
        check(!AnvilSync.apply(repair, 8, 1, false) && repair.maximumCost == Integer.MAX_VALUE && repair.uncapped, "Stale window changed current anvil");
        check(!AnvilSync.apply(repair, 7, -1, false) && repair.maximumCost == Integer.MAX_VALUE, "Negative cost accepted");
        check(!AnvilSync.apply(null, 7, 1, true), "Closed container accepted");
        check(AnvilSync.apply(repair, 7, 40, false) && !repair.uncapped, "Rule revocation ignored");
        System.out.println("Anvil full-width encoding, stale window, invalid cost and rule-state checks passed");
    }
}
