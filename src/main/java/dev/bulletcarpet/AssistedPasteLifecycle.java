package dev.bulletcarpet;

import fi.dy.masa.litematica.scheduler.tasks.TaskPasteSchematicPerChunkCommand;
import net.minecraft.client.Minecraft;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.PacketBuffer;
import net.minecraft.util.text.TextComponentString;

public final class AssistedPasteLifecycle {
    private static TaskPasteSchematicPerChunkCommand task;
    private static long operation;
    private static long rejectedOperation;

    private AssistedPasteLifecycle() {
    }

    public static void register(TaskPasteSchematicPerChunkCommand currentTask, long currentOperation) {
        task = currentTask;
        operation = currentOperation;
    }

    public static void clear(long completedOperation) {
        if (operation == completedOperation) {
            task = null;
            operation = 0;
        }
    }

    public static void onAck(PacketBuffer data) {
        try {
            NBTTagCompound ack = data.readCompoundTag();
            if (ack == null || data.isReadable() || !ack.hasKey("Operation", 4) ||
                    !ack.hasKey("Status", 8) || !ack.hasKey("Applied", 3) || !ack.hasKey("Message", 8)) return;
            long ackOperation = ack.getLong("Operation");
            String status = ack.getString("Status");
            int applied = ack.getInteger("Applied");
            String message = ack.getString("Message");
            Minecraft.getMinecraft().addScheduledTask(() -> {
                if ("Rejected".equals(status)) {
                    if (rejectedOperation == ackOperation) return;
                    rejectedOperation = ackOperation;
                }
                if (Minecraft.getMinecraft().player != null) {
                    Minecraft.getMinecraft().player.sendMessage(new TextComponentString("[Kisde-Carpet] " + message + " (" + applied + " updates)."));
                }
                if ("Rejected".equals(status) && operation == ackOperation && task != null) {
                    ((AssistedPasteTask) task).bulletcarpet$markOperationClosed();
                    task.stop();
                }
            });
        } catch (Exception ignored) {
        }
    }
}
