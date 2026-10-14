package dev.bulletcarpet;

import net.minecraft.client.Minecraft;
import net.minecraft.inventory.Container;
import net.minecraft.inventory.ContainerRepair;
import net.minecraft.network.PacketBuffer;

public final class AnvilSync {
    public static final String CHANNEL = "bc:anvil";

    public static void receive(PacketBuffer data) {
        if (data.readableBytes() != 9) return;
        int window = data.readInt(), cost = data.readInt();
        boolean uncapped = data.readBoolean();
        Minecraft mc = Minecraft.getMinecraft();
        if (window < 1 || window > 255 || cost < 0 || mc.player == null || !LiteModBulletCarpet.serverHasChannel(CHANNEL)) return;
        apply(mc.player.openContainer, window, cost, uncapped);
    }

    static boolean apply(Container container, int window, int cost, boolean uncapped) {
        if (!(container instanceof ContainerRepair) || !(container instanceof AnvilAccess) || container.windowId != window || window < 1 || window > 255 || cost < 0) return false;
        ((ContainerRepair)container).maximumCost = cost;
        ((AnvilAccess)container).kisde$setUncappedCost(uncapped);
        return true;
    }
}
