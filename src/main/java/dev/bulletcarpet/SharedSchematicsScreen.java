package dev.bulletcarpet;

import fi.dy.masa.litematica.data.DataManager;
import fi.dy.masa.malilib.gui.util.ScreenContext;
import fi.dy.masa.malilib.gui.widget.button.GenericButton;
import fi.dy.masa.malilib.MaLiLibConfigs;
import fi.dy.masa.malilib.render.RenderUtils;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import net.minecraft.client.gui.GuiScreen;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.ScaledResolution;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.GlStateManager;
import net.minecraft.nbt.NBTTagCompound;
import org.lwjgl.input.Mouse;

public final class SharedSchematicsScreen extends GuiScreen {
    private final GuiScreen parent;
    private final SharedSchematics sharing = SharedSchematics.INSTANCE;
    private final List<GenericButton> widgets = new ArrayList<>();
    private String snapshot = "";
    private int rows, left, panelWidth, labelWidth;
    private static final int LIST_TOP = 45, ROW_HEIGHT = 22;
    private int listBottom;
    private GuiTextField renameField, searchField;
    private NBTTagCompound renaming;
    private int screenScale;
    private boolean draggingScroll;
    private int scrollGrabY;

    public SharedSchematicsScreen(GuiScreen parent) { this.parent = parent; }
    public void setWorldAndResolution(Minecraft mc, int width, int height) {
        int configured = MaLiLibConfigs.Generic.CUSTOM_SCREEN_SCALE.getIntegerValue();
        screenScale = configured > 0 ? configured : new ScaledResolution(mc).getScaleFactor();
        super.setWorldAndResolution(mc, (int) Math.ceil((double) mc.displayWidth / screenScale), (int) Math.ceil((double) mc.displayHeight / screenScale));
    }
    public void initGui() {
        listBottom = height - 29;
        rows = Math.max(1, Math.min(64, (listBottom - LIST_TOP) / ROW_HEIGHT)); sharing.pageSize = rows;
        left = 10; panelWidth = width - 20;
        searchField = new GuiTextField(1, fontRenderer, left + 4, 30, panelWidth - 8, 12);
        searchField.setEnableBackgroundDrawing(false); searchField.setMaxStringLength(96); searchField.setText(sharing.search); searchField.setFocused(true);
        renameField = null; renaming = null; sharing.showWindow(); sharing.refresh(); rebuild();
        draggingScroll = false;
    }
    private void rebuild() {
        widgets.clear();
        int y = LIST_TOP;
        for (NBTTagCompound entry : sharing.pageEntries.subList(0, Math.min(rows, sharing.pageEntries.size()))) {
            final String id = entry.getString("Id");
            GenericButton[] actions = {
                GenericButton.create(20, "Load", () -> sharing.load(id)),
                GenericButton.create(20, "Update", () -> sharing.share(id)),
                GenericButton.create(20, "Rename", () -> openRename(entry)),
                GenericButton.create(20, "Remove", () -> sharing.delete(id))
            };
            int right = left + panelWidth - (sharing.total > rows ? 12 : 0);
            for (int i = actions.length - 1; i >= 0; i--) {
                GenericButton button = actions[i]; right -= button.getWidth(); button.setPosition(right, y); right -= 4;
                final int action = i;
                button.setEnabledStatusSupplier(() -> sharing.enabled && !sharing.busy() && (action == 0 ? mc.player != null && mc.player.dimension == entry.getInteger("Dimension") : entry.getBoolean("CanManage") && (action != 1 || DataManager.getSchematicPlacementManager().getSelectedSchematicPlacement() != null)));
                widgets.add(button);
            }
            actions[1].addHoverStrings("Replace this share with the selected Litematica placement");
            labelWidth = Math.max(1, right - left); y += ROW_HEIGHT;
        }
        int navY = height - 24;
        addButton(8, navY, sharing.busy() ? "Cancel" : "Refresh", () -> { if (sharing.busy()) { sharing.cancel(); sharing.status = "Transfer cancelled"; } else sharing.refresh(); }, () -> sharing.enabled || sharing.busy());
        GenericButton menu = GenericButton.create(20, "Litematica Main Menu", () -> mc.displayGuiScreen(parent));
        menu.setPosition(width - menu.getWidth() - 8, navY); widgets.add(menu);
    }
    private int addButton(int x, int y, String text, fi.dy.masa.malilib.listener.EventListener action, java.util.function.BooleanSupplier enabled) {
        GenericButton button = GenericButton.create(20, text, action); button.setPosition(x, y); button.setEnabledStatusSupplier(enabled); widgets.add(button); return button.getRight() + 4;
    }
    private void openRename(NBTTagCompound entry) {
        renaming = entry.copy(); int fieldWidth = Math.min(280, width - 32);
        renameField = new GuiTextField(0, fontRenderer, (width - fieldWidth) / 2, height / 2 - 10, fieldWidth, 20);
        renameField.setMaxStringLength(96); renameField.setText(entry.getString("Name")); renameField.setFocused(true);
        widgets.clear();
        addButton(renameField.x, renameField.y + 30, "Rename", this::confirmRename, () -> sharing.enabled && !sharing.busy());
        addButton(renameField.x + 80, renameField.y + 30, "Cancel", this::closeRename, () -> true);
    }
    private void confirmRename() { if (sharing.rename(renaming.getString("Id"), renaming.getLong("Revision"), renameField.getText())) closeRename(); }
    private void closeRename() { renameField = null; renaming = null; rebuild(); }
    public void updateScreen() {
        if (renameField != null) { renameField.updateCursorCounter(); return; }
        searchField.updateCursorCounter();
        if (!sharing.search.equals(searchField.getText())) { sharing.search = searchField.getText(); sharing.offset = 0; sharing.showWindow(); }
        String current = sharing.enabled + ":" + sharing.busy() + ":" + sharing.offset + ":" + sharing.total + sharing.pageEntries.toString();
        if (!current.equals(snapshot)) { snapshot = current; rebuild(); }
    }
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        RenderUtils.setupScaledScreenRendering(screenScale);
        try {
            mouseX = Mouse.getX() * width / mc.displayWidth;
            mouseY = height - Mouse.getY() * height / mc.displayHeight - 1;
            drawRect(0, 0, width, height, 0xb0000000);
            drawString(fontRenderer, "Litematica - Shared Schematics", 7, 2, 0xffffff);
            if (renameField == null) {
                drawRect(8, 27, width - 8, 28, 0xffb0b0b0);
                drawRect(8, listBottom + 1, width - 8, listBottom + 2, 0xffb0b0b0);
                drawRect(8, 27, 9, listBottom + 2, 0xffb0b0b0);
                drawRect(width - 9, 27, width - 8, listBottom + 2, 0xffb0b0b0);
                drawRect(left + 2, 29, left + panelWidth - 2, 43, 0x80000000);
                drawRect(left + 2, 42, left + panelWidth - 2, 43, searchField.isFocused() ? 0xffaaaaaa : 0xff555555);
                searchField.drawTextBox();
                if (searchField.getText().isEmpty()) drawString(fontRenderer, "Search schematics...", searchField.x, searchField.y, 0xb0b0b0);
                int y = LIST_TOP + 6, index = sharing.offset;
                for (NBTTagCompound entry : sharing.pageEntries.subList(0, Math.min(rows, sharing.pageEntries.size()))) {
                    drawRect(left, y - 6, left + panelWidth - (sharing.total > rows ? 12 : 0), y + 16, (index++ & 1) == 0 ? 0xa0404040 : 0xa0000000);
                    String label = entry.getString("Name") + " — " + entry.getString("Author") + " (" + dimensionName(entry.getInteger("Dimension")) + ")";
                    drawString(fontRenderer, fontRenderer.trimStringToWidth(label, Math.max(1, labelWidth - 6)), left + 4, y, 0xffffff); y += ROW_HEIGHT;
                }
                if (sharing.total > rows) {
                    int x = left + panelWidth - 8, top = scrollTop();
                    drawRect(x, LIST_TOP, x + 8, listBottom, 0x80000000);
                    drawRect(x + 1, top, x + 7, top + scrollHeight(), draggingScroll ? 0xffdddddd : 0xff888888);
                }
            } else {
                drawCenteredString(fontRenderer, "Rename shared schematic", width / 2, renameField.y - 20, 0xffffff);
                renameField.drawTextBox();
            }
            int hovered = -1;
            for (GenericButton button : widgets) if (button.isMouseOver(mouseX, mouseY)) hovered = button.getId();
            ScreenContext context = new ScreenContext(mouseX, mouseY, hovered, true);
            GlStateManager.color(1, 1, 1, 1);
            for (GenericButton button : widgets) { button.updateWidgetState(); button.renderAt(button.getX(), button.getY(), 0, context); }
            for (GenericButton button : widgets) button.postRenderHovered(context);
        } finally { RenderUtils.setupScaledScreenRendering(new ScaledResolution(mc).getScaleFactor()); }
    }
    protected void mouseClicked(int mouseX, int mouseY, int mouseButton) throws IOException {
        if (renameField == null && mouseButton == 0 && sharing.total > rows && mouseX >= left + panelWidth - 8 && mouseX < left + panelWidth && mouseY >= LIST_TOP && mouseY < listBottom) {
            draggingScroll = true;
            scrollGrabY = mouseY >= scrollTop() && mouseY < scrollTop() + scrollHeight() ? mouseY - scrollTop() : scrollHeight() / 2;
            scrollToMouse(mouseY, true); return;
        }
        if (renameField != null) renameField.mouseClicked(mouseX, mouseY, mouseButton);
        else searchField.mouseClicked(mouseX, mouseY, mouseButton);
        for (GenericButton button : new ArrayList<>(widgets)) if (button.tryMouseClick(mouseX, mouseY, mouseButton)) break;
    }
    public void handleMouseInput() throws IOException {
        super.handleMouseInput();
        int wheel = Mouse.getEventDWheel();
        int x = Mouse.getEventX() * width / mc.displayWidth, y = height - Mouse.getEventY() * height / mc.displayHeight - 1;
        if (wheel != 0 && renameField == null && x >= left && x < left + panelWidth && y >= LIST_TOP && y < listBottom) scrollTo(sharing.offset - Integer.signum(wheel), true);
    }
    protected void mouseClickMove(int mouseX, int mouseY, int button, long elapsed) {
        if (draggingScroll && button == 0) scrollToMouse(mouseY, false);
    }
    protected void mouseReleased(int mouseX, int mouseY, int button) {
        if (draggingScroll && button == 0) { scrollToMouse(mouseY, true); draggingScroll = false; }
        super.mouseReleased(mouseX, mouseY, button);
    }
    private static String dimensionName(int dimension) {
        switch (dimension) {
            case 0: return "Overworld";
            case -1: return "Nether";
            case 1: return "The End";
            default: return "Dimension " + dimension;
        }
    }
    private int scrollMaximum() { return Math.max(0, sharing.total - rows); }
    private int scrollHeight() { return Math.max(12, (listBottom - LIST_TOP) * rows / sharing.total); }
    private int scrollTop() { return LIST_TOP + (listBottom - LIST_TOP - scrollHeight()) * Math.min(sharing.offset, scrollMaximum()) / Math.max(1, scrollMaximum()); }
    private void scrollToMouse(int mouseY, boolean force) {
        if (sharing.total <= rows) { draggingScroll = false; return; }
        int travel = listBottom - LIST_TOP - scrollHeight();
        scrollTo(Math.round((float) (mouseY - LIST_TOP - scrollGrabY) * scrollMaximum() / travel), force);
    }
    private void scrollTo(int offset, boolean force) {
        if (renameField != null || !sharing.enabled) return;
        int maximum = scrollMaximum();
        int bounded = Math.max(0, Math.min(maximum, offset));
        if (bounded == sharing.offset) return;
        sharing.offset = bounded; sharing.showWindow();
    }
    protected void keyTyped(char typedChar, int keyCode) throws IOException {
        if (renameField != null) {
            if (keyCode == 1) closeRename(); else if (keyCode == 28 || keyCode == 156) confirmRename(); else renameField.textboxKeyTyped(typedChar, keyCode);
        } else if (keyCode == 1) mc.displayGuiScreen(parent);
        else if (searchField.textboxKeyTyped(typedChar, keyCode)) { sharing.search = searchField.getText(); sharing.offset = 0; sharing.showWindow(); rebuild(); }
        else super.keyTyped(typedChar, keyCode);
    }
    public boolean doesGuiPauseGame() { return false; }
}
