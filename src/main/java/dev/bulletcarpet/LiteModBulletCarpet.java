package dev.bulletcarpet;

import java.io.File;
import com.mumfrey.liteloader.core.LiteLoader;

import com.mumfrey.liteloader.LiteMod;
import com.mumfrey.liteloader.GameLoopListener;
import com.mumfrey.liteloader.PluginChannelListener;
import net.minecraft.network.PacketBuffer;

import java.util.Arrays;
import java.util.List;
import net.minecraft.client.Minecraft;

public class LiteModBulletCarpet implements LiteMod, GameLoopListener, PluginChannelListener {
    public static final String SCHEMATIC_RULES = "bc:schem_rules";
    private static net.minecraft.client.network.NetHandlerPlayClient rulesConnection;
    private static boolean saveInventories;
    private static boolean pasteInventories;

    public static boolean canSaveInventories() {
        return !serverHasChannel(SCHEMATIC_RULES) ||
                (rulesConnection == Minecraft.getMinecraft().getConnection() && saveInventories);
    }

    public static boolean canPasteInventories() {
        return !serverHasChannel(SCHEMATIC_RULES) ||
                (rulesConnection == Minecraft.getMinecraft().getConnection() && pasteInventories);
    }

    public static void onSchematicRules(PacketBuffer data) {
        if (data.readableBytes() != 2) return;
        saveInventories = data.readBoolean();
        pasteInventories = data.readBoolean();
        Minecraft mc = Minecraft.getMinecraft();
        rulesConnection = mc.getConnection();
        if (mc.currentScreen != null && "fi.dy.masa.litematica.gui.SaveSchematicFromAreaScreen".equals(mc.currentScreen.getClass().getName())) {
            try {
                java.lang.reflect.Method refresh = mc.currentScreen.getClass().getDeclaredMethod("reAddActiveWidgets");
                refresh.setAccessible(true);
                refresh.invoke(mc.currentScreen);
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to refresh schematic inventory options", e);
            }
        }
    }
    @Override
    public String getName() {
        return "Kisde-Carpet";
    }

    @Override
    public String getVersion() {
        return "0.2.0";
    }

    @Override
    public void init(File configPath) {
    }

    public static boolean isAssistedPasteEnabled() {
        return serverHasChannel("bulletcarpet:paste");
    }

    public static boolean serverHasChannel(String channel) {
        Minecraft minecraft = Minecraft.getMinecraft();
        return minecraft.world != null && minecraft.player != null && !minecraft.isSingleplayer() &&
                LiteLoader.getClientPluginChannels() != null &&
                LiteLoader.getClientPluginChannels().isRemoteChannelRegistered(channel);
    }

    @Override
    public void onRunGameLoop(Minecraft minecraft) {
        if (rulesConnection != null && rulesConnection != minecraft.getConnection()) {
            rulesConnection = null;
            saveInventories = false;
            pasteInventories = false;
        }
        ServerInventoryPreview.instance().onRunGameLoop(minecraft);
        SharedSchematics.INSTANCE.tick(minecraft);
    }

    @Override
    public List<String> getChannels() {
        return Arrays.asList("bc:paste_ack", "pca:update_be", "bc:inv_ack", SCHEMATIC_RULES, SharedSchematics.CHANNEL);
    }

    @Override
    public void onCustomPayload(String channel, PacketBuffer data) {
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
    }
}
