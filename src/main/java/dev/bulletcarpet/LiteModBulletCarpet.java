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
    @Override
    public String getName() {
        return "Bullet Carpet";
    }

    @Override
    public String getVersion() {
        return "0.1.1";
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
        ServerInventoryPreview.instance().onRunGameLoop(minecraft);
    }

    @Override
    public List<String> getChannels() {
        return Arrays.asList("bc:paste_ack", "pca:update_be", "bc:inv_ack");
    }

    @Override
    public void onCustomPayload(String channel, PacketBuffer data) {
    }

    @Override
    public void upgradeSettings(String version, File configPath, File oldConfigPath) {
    }
}
