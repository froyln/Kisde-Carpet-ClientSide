package dev.bulletcarpet;

import fi.dy.masa.litematica.data.DataManager;
import java.io.IOException;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.nbt.NBTTagCompound;

public final class SharedSchematicsScreen extends GuiScreen {
    private final GuiScreen parent;
    private final SharedSchematics sharing = SharedSchematics.INSTANCE;
    private String selected;
    private int rows, panelWidth;
    public SharedSchematicsScreen(GuiScreen parent) { this.parent = parent; }
    public void initGui() {
        rows = Math.max(1, Math.min(12, (height - 180) / 20)); sharing.pageSize = rows;
        buttonList.clear(); panelWidth = Math.max(280, Math.min(480, width - 20)); int left = (width - panelWidth) / 2; int half = (panelWidth - 5) / 2, quarter = (panelWidth - 15) / 4;
        for (int i = 0; i < rows; i++) buttonList.add(new GuiButton(100 + i, left, 55 + i * 20, panelWidth, 18, ""));
        int bottom = 60 + rows * 20;
        buttonList.add(new GuiButton(0, left, bottom, half, 20, "Share selected placement"));
        buttonList.add(new GuiButton(1, left + half + 5, bottom, half, 20, "Load shared overlay"));
        buttonList.add(new GuiButton(2, left, bottom + 25, half, 20, "Update selected share"));
        buttonList.add(new GuiButton(3, left + half + 5, bottom + 25, half, 20, "Remove selected share"));
        buttonList.add(new GuiButton(4, left, bottom + 50, quarter, 20, "Previous"));
        buttonList.add(new GuiButton(5, left + quarter + 5, bottom + 50, quarter, 20, "Next"));
        buttonList.add(new GuiButton(6, left + (quarter + 5) * 2, bottom + 50, quarter, 20, "Refresh"));
        buttonList.add(new GuiButton(7, left + (quarter + 5) * 3, bottom + 50, quarter, 20, "Back"));
        sharing.refresh(); updateScreen();
    }
    public void updateScreen() {
        NBTTagCompound entry = selected == null ? null : sharing.entry(selected);
        for (GuiButton button : buttonList) {
            if (button.id >= 100) {
                int index = button.id - 100; button.visible = index < sharing.pageEntries.size();
                if (button.visible) { NBTTagCompound row = sharing.pageEntries.get(index); button.displayString = (row.getString("Id").equals(selected) ? "> " : "") + fontRenderer.trimStringToWidth(row.getString("Name"), Math.max(70, panelWidth - 160)) + " — " + row.getString("Author") + " (dim " + row.getInteger("Dimension") + ")"; }
            } else if (button.id < 4) {
                button.enabled = sharing.enabled && !sharing.busy();
                if (button.id == 0) button.enabled &= DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement() != null;
                else button.enabled &= entry != null;
                if (button.id == 1 && entry != null) button.enabled &= mc.player != null && entry.getInteger("Dimension") == mc.player.dimension;
                if ((button.id == 2 || button.id == 3) && entry != null) button.enabled &= entry.getBoolean("CanManage");
            } else if (button.id == 4) button.enabled = sharing.page > 0;
            else if (button.id == 5) button.enabled = (sharing.page + 1) * rows < sharing.total;
            else if (button.id == 6) { button.enabled = sharing.enabled || sharing.busy(); button.displayString = sharing.busy() ? "Cancel" : "Refresh"; }
        }
    }
    protected void actionPerformed(GuiButton button) throws IOException {
        if (button.id >= 100) selected = sharing.pageEntries.get(button.id - 100).getString("Id");
        else switch (button.id) {
            case 0: sharing.share(null); break;
            case 1: sharing.load(selected); break;
            case 2: sharing.share(selected); break;
            case 3: sharing.delete(selected); selected = null; break;
            case 4: sharing.page--; sharing.refresh(); break;
            case 5: sharing.page++; sharing.refresh(); break;
            case 6: if (sharing.busy()) { sharing.cancel(); sharing.status = "Transfer cancelled"; } else sharing.refresh(); break;
            case 7: mc.displayGuiScreen(parent); break;
        }
        updateScreen();
    }
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        drawDefaultBackground();
        drawCenteredString(fontRenderer, "Shared schematics", width / 2, 12, 0xffffff);
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth(sharing.status, width - 20), width / 2, 28, 0xb0b0b0);
        drawCenteredString(fontRenderer, fontRenderer.trimStringToWidth("Sharing includes the schematic's contents and placement.", width - 20), width / 2, 41, 0xa0a0a0);
        super.drawScreen(mouseX, mouseY, partialTicks);
    }
    protected void keyTyped(char typedChar, int keyCode) throws IOException { if (keyCode == 1) mc.displayGuiScreen(parent); else super.keyTyped(typedChar, keyCode); }
    public boolean doesGuiPauseGame() { return false; }
}
