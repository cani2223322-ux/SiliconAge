package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.sc.energy.FieldMode;
import com.sc.handler.FieldNetSC;
import com.sc.manual.Lang;
import com.sc.tileentity.FieldShapeSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.GuiTextField;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.AxisAlignedBB;

/**
 * The Field Generator master's screen, four tabs:
 * - Field: status, range, shape, shell colour / visibility, redstone control, the energy bar;
 * - Functions: no spawning, no ender teleports, mob damage, targets, warnings, wireless charging, healing;
 * - Access: the owner, the private zone, pushing strangers out, the access list (owner edits it);
 * - Map: the cluster seen from above - the field at the master's height, the nodes, the player.
 * Buttons go through the vanilla GUI-button packet (ContainerFieldGeneratorSC.enchantItem), the
 * access list through FieldNetSC; the state comes back with the block (description packet).
 * Drawn in code, vanilla style (the old 176x100 texture couldn't hold it all).
 */
public class GuiFieldGeneratorSC extends GuiContainer {

    private static final int W = 248, H = 226;
    private static final int TAB_BASE = 100, ADD_ID = 200, REMOVE_BASE = 300;
    private static final int ENERGY_X = 224, ENERGY_Y = 34, ENERGY_W = 14, ENERGY_H = 76;
    private static final int MAP_X = 10, MAP_Y = 34, MAP = 150, CELL = 2;

    private static int tab;                 // remembered while the game runs

    private final TileEntityFieldGeneratorSC field;
    private GuiTextField nameField;
    /** The map, recomputed when the field's shape changes. */
    private boolean[][] mapCells;
    private String mapKey = "";
    private AxisAlignedBB mapBounds;

    public GuiFieldGeneratorSC(net.minecraft.entity.player.InventoryPlayer playerInv, TileEntityFieldGeneratorSC field) {
        super(new ContainerFieldGeneratorSC(playerInv, field));
        this.field = field;
        xSize = W;
        ySize = H;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void initGui() {
        super.initGui();
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        String[] tabs = {"sc.fieldgui.tab.field", "sc.fieldgui.tab.functions", "sc.fieldgui.tab.access", "sc.fieldgui.tab.map"};
        int tw = (W - 16) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            GuiButton b = new GuiButton(TAB_BASE + i, guiLeft + 8 + i * tw, guiTop + 6, tw - 2, 18, Lang.tr(tabs[i]));
            b.enabled = i != tab;
            buttonList.add(b);
        }
        nameField = null;
        int x = guiLeft + 8, y = guiTop + 30;
        switch (tab) {
            case 0: {
                int ry = guiTop + 118;
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_RANGE_MINUS_16, x, ry, 30, 20, "-16"));
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_RANGE_MINUS_1, x + 32, ry, 30, 20, "-1"));
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_RANGE_PLUS_1, x + 64, ry, 30, 20, "+1"));
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_RANGE_PLUS_16, x + 96, ry, 30, 20, "+16"));
                int by = guiTop + 142;
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_MODE, x, by, 114, 20, ""));
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_COLOR, x + 118, by, 114, 20, ""));
                buttonList.add(new GuiButton(flagId(TileEntityFieldGeneratorSC.F_SHOW), x, by + 22, 114, 20, ""));
                buttonList.add(new GuiButton(ContainerFieldGeneratorSC.BTN_REDSTONE, x + 118, by + 22, 114, 20, ""));
                break;
            }
            case 1: {
                int[] rows = {TileEntityFieldGeneratorSC.F_NO_SPAWN, TileEntityFieldGeneratorSC.F_NO_ENDER, TileEntityFieldGeneratorSC.F_DAMAGE,
                        -1, TileEntityFieldGeneratorSC.F_WARN, TileEntityFieldGeneratorSC.F_CHARGE, TileEntityFieldGeneratorSC.F_HEAL};
                for (int i = 0; i < rows.length; i++) {
                    int id = rows[i] < 0 ? ContainerFieldGeneratorSC.BTN_FILTER : flagId(rows[i]);
                    buttonList.add(new GuiButton(id, x, y + i * 22, W - 16, 20, ""));
                }
                break;
            }
            case 2: {
                buttonList.add(new GuiButton(flagId(TileEntityFieldGeneratorSC.F_PRIVATE), x, y + 14, 114, 20, ""));
                buttonList.add(new GuiButton(flagId(TileEntityFieldGeneratorSC.F_PUSH_PLAYERS), x + 118, y + 14, 114, 20, ""));
                List<String> names = field.getAccess();
                for (int i = 0; i < names.size() && i < TileEntityFieldGeneratorSC.MAX_ACCESS; i++) {
                    int col = i % 3, row = i / 3;
                    GuiButton b = new GuiButton(REMOVE_BASE + i, x + col * 78, y + 56 + row * 16, 76, 15, "");
                    b.enabled = mayEditAccess();
                    buttonList.add(b);
                }
                nameField = new GuiTextField(fontRendererObj, x + 1, guiTop + H - 24, 160, 16);
                nameField.setMaxStringLength(16);
                nameField.setEnabled(mayEditAccess());
                GuiButton add = new GuiButton(ADD_ID, x + 166, guiTop + H - 26, 66, 20, Lang.tr("sc.fieldgui.access.add"));
                add.enabled = mayEditAccess();
                buttonList.add(add);
                break;
            }
            default:
                break;
        }
        refresh();
    }

    private static int flagId(int flag) {
        return ContainerFieldGeneratorSC.BTN_FLAG_BASE + Integer.numberOfTrailingZeros(flag);
    }

    private boolean mayEditAccess() {
        return field.getOwner().isEmpty() || field.isOwner(mc.thePlayer);
    }

    private boolean mayEdit() {
        return field.allowed(mc.thePlayer);
    }

    private static String onOff(boolean on) {
        return Lang.tr(on ? "sc.fieldgui.on" : "sc.fieldgui.off");
    }

    /** Button captions from the current state (it changes under the open screen). */
    private void refresh() {
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            int id = b.id;
            if (id >= TAB_BASE) {
                if (id >= REMOVE_BASE && id - REMOVE_BASE < field.getAccess().size()) {
                    b.displayString = "§c×§r " + field.getAccess().get(id - REMOVE_BASE);
                }
                continue;
            }
            if (id >= ContainerFieldGeneratorSC.BTN_FLAG_BASE) {
                int flag = 1 << (id - ContainerFieldGeneratorSC.BTN_FLAG_BASE);
                boolean on = field.has(flag);
                b.displayString = (on ? "§a" : "§7") + Lang.tr("sc.fieldgui.flag." + flag) + ": " + onOff(on);
            } else if (id == ContainerFieldGeneratorSC.BTN_MODE) {
                b.displayString = Lang.tr("sc.fieldgui.shape", modeName(field.getMode()));
            } else if (id == ContainerFieldGeneratorSC.BTN_COLOR) {
                b.displayString = Lang.tr("sc.fieldgui.color", Lang.tr("sc.fieldgui.color." + field.getColor()));
            } else if (id == ContainerFieldGeneratorSC.BTN_REDSTONE) {
                b.displayString = Lang.tr("sc.fieldgui.redstone", Lang.tr("sc.fieldgui.redstone." + field.getRedstone()));
            } else if (id == ContainerFieldGeneratorSC.BTN_FILTER) {
                b.displayString = Lang.tr("sc.fieldgui.filter", Lang.tr("sc.fieldgui.filter." + field.getFilter()));
            } else if (id <= ContainerFieldGeneratorSC.BTN_RANGE_PLUS_16) {
                boolean down = id == ContainerFieldGeneratorSC.BTN_RANGE_MINUS_16 || id == ContainerFieldGeneratorSC.BTN_RANGE_MINUS_1;
                b.enabled = mayEdit() && (down ? field.getRange() > FieldShapeSC.MIN_RANGE : field.getRange() < FieldShapeSC.MAX_RANGE);
                continue;
            }
            b.enabled = mayEdit();
        }
    }

    private static String modeName(FieldMode m) {
        return Lang.trOr("sc.field.mode." + m.name().toLowerCase(java.util.Locale.ROOT), m.name());
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        int names = 0;
        for (Object o : buttonList) {
            names += ((GuiButton) o).id >= REMOVE_BASE ? 1 : 0;
        }
        if (tab == 2 && names != Math.min(field.getAccess().size(), TileEntityFieldGeneratorSC.MAX_ACCESS)) {
            String typed = nameField == null ? "" : nameField.getText();
            initGui();                                       // the list changed
            if (nameField != null) {
                nameField.setText(typed);
            }
        }
        if (nameField != null) {
            nameField.updateCursorCounter();
        }
        refresh();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        Keyboard.enableRepeatEvents(false);
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void actionPerformed(GuiButton button) {
        if (button.id >= TAB_BASE && button.id < TAB_BASE + 4) {
            tab = button.id - TAB_BASE;
            initGui();
        } else if (button.id == ADD_ID) {
            addTyped();
        } else if (button.id >= REMOVE_BASE) {
            int i = button.id - REMOVE_BASE;
            if (i < field.getAccess().size()) {
                FieldNetSC.CHANNEL.sendToServer(new FieldNetSC.Message(field, FieldNetSC.REMOVE, field.getAccess().get(i)));
            }
        } else {
            mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
        }
    }

    private void addTyped() {
        if (nameField != null && !nameField.getText().trim().isEmpty()) {
            FieldNetSC.CHANNEL.sendToServer(new FieldNetSC.Message(field, FieldNetSC.ADD, nameField.getText().trim()));
            nameField.setText("");
        }
    }

    @Override
    protected void keyTyped(char c, int key) {
        if (nameField != null && nameField.isFocused()) {
            if (key == Keyboard.KEY_RETURN || key == Keyboard.KEY_NUMPADENTER) {
                addTyped();
            } else if (key == Keyboard.KEY_ESCAPE) {
                nameField.setFocused(false);
            } else {
                nameField.textboxKeyTyped(c, key);
            }
            return;                                          // typing "e" mustn't close the screen
        }
        super.keyTyped(c, key);
    }

    @Override
    protected void mouseClicked(int x, int y, int button) {
        super.mouseClicked(x, y, button);
        if (nameField != null) {
            nameField.mouseClicked(x, y, button);
        }
    }

    // ------------------------------------------------------------------ drawing

    /** A vanilla-style raised panel. */
    private void panel(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF000000);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFC6C6C6);
        drawRect(x + 1, y + 1, x + w - 2, y + 2, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + 2, y + h - 2, 0xFFFFFFFF);
        drawRect(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF555555);
        drawRect(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF555555);
    }

    /** A sunken inset (energy bar, map, name list). */
    private void inset(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF373737);
        drawRect(x + 1, y + 1, x + w, y + h, 0xFFFFFFFF);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF8B8B8B);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        panel(guiLeft, guiTop, W, H);
        if (tab == 0) {
            inset(guiLeft + ENERGY_X - 1, guiTop + ENERGY_Y - 1, ENERGY_W + 2, ENERGY_H + 2);
            float fill = (float) field.getEnergyStored() / Math.max(1, field.getMaxEnergyStored());
            int h = (int) (ENERGY_H * Math.min(1F, fill));
            drawRect(guiLeft + ENERGY_X, guiTop + ENERGY_Y + ENERGY_H - h, guiLeft + ENERGY_X + ENERGY_W, guiTop + ENERGY_Y + ENERGY_H, 0xFFD02020);
            drawRect(guiLeft + ENERGY_X, guiTop + ENERGY_Y + ENERGY_H - h, guiLeft + ENERGY_X + 3, guiTop + ENERGY_Y + ENERGY_H, 0xFFFF6060);
        } else if (tab == 2) {
            inset(guiLeft + 7, guiTop + 84, W - 14, 6 * 16 + 4);
        } else if (tab == 3) {
            inset(guiLeft + MAP_X - 1, guiTop + MAP_Y - 1, MAP + 2, MAP + 2);
            drawMap(guiLeft + MAP_X, guiTop + MAP_Y);
        }
        if (nameField != null) {
            nameField.drawTextBox();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int c = 0x404040, dim = 0x606060;
        switch (tab) {
            case 0: {
                String status = field.isRedstoneOff() ? Lang.tr("sc.fieldgui.status.redstone")
                        : Lang.tr(field.isActive() ? "sc.gui.field.active" : "sc.gui.field.inactive");
                fontRendererObj.drawString(status, 8, 32, field.isActive() ? 0x2E7D32 : field.isRedstoneOff() ? 0x8A5A00 : 0xA02020);
                fontRendererObj.drawString(Lang.tr("sc.gui.field.mode", modeName(field.getMode())), 8, 46, c);
                fontRendererObj.drawString(Lang.tr("sc.gui.field.nodes", field.getNodeCount()), 8, 58, c);
                fontRendererObj.drawString(Lang.tr("sc.gui.field.range", field.getRange()), 8, 70, c);
                fontRendererObj.drawString(Lang.tr("sc.gui.field.upkeep", field.upkeepPerTick()), 8, 82, c);
                fontRendererObj.drawString(Lang.tr("sc.fieldgui.owner", field.getOwner().isEmpty() ? "-" : field.getOwner()), 8, 96, dim);
                break;
            }
            case 1:
                break;
            case 2: {
                fontRendererObj.drawString(Lang.tr("sc.fieldgui.owner", field.getOwner().isEmpty() ? "-" : field.getOwner()), 8, 32, c);
                fontRendererObj.drawString(Lang.tr("sc.fieldgui.access.list", field.getAccess().size(), TileEntityFieldGeneratorSC.MAX_ACCESS),
                        8, 72, c);
                if (field.getAccess().isEmpty()) {
                    fontRendererObj.drawString(Lang.tr("sc.fieldgui.access.empty"), 12, 90, 0xE0E0E0);
                }
                if (!mayEditAccess()) {
                    fontRendererObj.drawString(Lang.tr("sc.fieldgui.access.ownerOnly"), 8, H - 38, 0xA02020);
                }
                break;
            }
            default: {
                int lx = MAP_X + MAP + 8;
                fontRendererObj.drawString(Lang.tr("sc.fieldgui.map.title"), lx, 32, c);
                legend(lx, 48, 0xFFFFE040, Lang.tr("sc.fieldgui.map.master"));
                legend(lx, 60, 0xFFFFFFFF, Lang.tr("sc.fieldgui.map.node"));
                legend(lx, 72, 0xFFFF4040, Lang.tr("sc.fieldgui.map.you"));
                float[] col = TileEntityFieldGeneratorSC.COLORS[field.getColor()];
                legend(lx, 84, 0xFF000000 | ((int) (col[0] * 200) << 16) | ((int) (col[1] * 200) << 8) | (int) (col[2] * 200),
                        Lang.tr("sc.fieldgui.map.field"));
                fontRendererObj.drawString("N ↑", MAP_X + MAP / 2 - 6, MAP_Y + 2, 0xFFFFFF);
                if (mapBounds != null) {
                    int wBlocks = (int) Math.round(mapBounds.maxX - mapBounds.minX), dBlocks = (int) Math.round(mapBounds.maxZ - mapBounds.minZ);
                    fontRendererObj.drawString(wBlocks + " x " + dBlocks, lx, 104, dim);
                }
                fontRendererObj.drawSplitString(Lang.tr("sc.fieldgui.map.hint"), lx, 120, W - lx - 6, dim);
            }
        }
    }

    private void legend(int x, int y, int color, String text) {
        drawRect(x, y + 1, x + 6, y + 7, color);
        fontRendererObj.drawString(text, x + 9, y, 0x404040);
    }

    /** The field at the master's height, seen from above (north up), with the nodes and the player. */
    private void drawMap(int x0, int y0) {
        List<int[]> nodes = field.getNodePositions();
        if (nodes.isEmpty()) {
            return;
        }
        long hash = 17;
        for (int[] nd : nodes) {
            hash = hash * 31 + ((long) nd[0] * 73856093L ^ (long) nd[1] * 19349663L ^ (long) nd[2] * 83492791L);
        }
        String key = field.getMode() + "/" + field.getRange() + "/" + nodes.size() + "/" + hash;
        int n = MAP / CELL;
        if (!key.equals(mapKey)) {
            mapKey = key;
            AxisAlignedBB b = FieldShapeSC.bounds(field.getMode(), nodes, field.getRange());
            double size = Math.max(b.maxX - b.minX, b.maxZ - b.minZ) + 4;
            double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
            mapBounds = AxisAlignedBB.getBoundingBox(cx - size / 2, 0, cz - size / 2, cx + size / 2, 0, cz + size / 2);
            double y = field.yCoord + 0.5;
            mapCells = new boolean[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double wx = mapBounds.minX + (i + 0.5) * size / n, wz = mapBounds.minZ + (j + 0.5) * size / n;
                    mapCells[i][j] = FieldShapeSC.contains(field.getMode(), nodes, field.getRange(), wx, y, wz);
                }
            }
        }
        float[] c = TileEntityFieldGeneratorSC.COLORS[field.getColor()];
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glEnable(GL11.GL_BLEND);
        Tessellator t = Tessellator.instance;
        t.startDrawingQuads();
        t.setColorRGBA_F(c[0], c[1], c[2], 0.55F);
        for (int i = 0; i < n; i++) {
            for (int j = 0; j < n; j++) {
                if (mapCells[i][j]) {
                    int px = x0 + i * CELL, py = y0 + j * CELL;
                    t.addVertex(px, py + CELL, zLevel);
                    t.addVertex(px + CELL, py + CELL, zLevel);
                    t.addVertex(px + CELL, py, zLevel);
                    t.addVertex(px, py, zLevel);
                }
            }
        }
        t.draw();
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        double size = mapBounds.maxX - mapBounds.minX;
        for (int[] node : nodes) {
            int px = x0 + (int) ((node[0] + 0.5 - mapBounds.minX) / size * MAP);
            int py = y0 + (int) ((node[2] + 0.5 - mapBounds.minZ) / size * MAP);
            boolean isMaster = node[0] == field.xCoord && node[1] == field.yCoord && node[2] == field.zCoord;
            drawRect(px - 2, py - 2, px + 2, py + 2, isMaster ? 0xFFFFE040 : 0xFFFFFFFF);
        }
        double pxw = (mc.thePlayer.posX - mapBounds.minX) / size * MAP, pzw = (mc.thePlayer.posZ - mapBounds.minZ) / size * MAP;
        if (pxw >= 0 && pxw < MAP && pzw >= 0 && pzw < MAP) {
            drawRect(x0 + (int) pxw - 2, y0 + (int) pzw - 2, x0 + (int) pxw + 2, y0 + (int) pzw + 2, 0xFFFF4040);
        }
    }

    // ------------------------------------------------------------------ tooltips

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tip = new ArrayList<String>();
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (mouseX < b.xPosition || mouseY < b.yPosition || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
                continue;
            }
            String key = null;
            if (b.id >= ContainerFieldGeneratorSC.BTN_FLAG_BASE && b.id < TAB_BASE) {
                key = "sc.fieldgui.flag." + (1 << (b.id - ContainerFieldGeneratorSC.BTN_FLAG_BASE)) + ".desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_MODE) {
                key = "sc.fieldgui.shape.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_REDSTONE) {
                key = "sc.fieldgui.redstone.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_FILTER) {
                key = "sc.fieldgui.filter.desc";
            } else if (b.id >= REMOVE_BASE) {
                key = "sc.fieldgui.access.remove";
            }
            if (key != null) {
                tip.addAll(fontRendererObj.listFormattedStringToWidth(Lang.tr(key), 200));
            }
        }
        if (tab == 0 && mouseX >= guiLeft + ENERGY_X && mouseX < guiLeft + ENERGY_X + ENERGY_W
                && mouseY >= guiTop + ENERGY_Y && mouseY < guiTop + ENERGY_Y + ENERGY_H) {
            tip.add(Lang.tr("sc.gui.energy"));
            tip.add(field.getEnergyStored() + " / " + field.getMaxEnergyStored() + " EU");
            tip.add(Lang.tr("sc.gui.field.upkeep", field.upkeepPerTick()));
        }
        if (!tip.isEmpty()) {
            drawHoveringText(tip, mouseX, mouseY, fontRendererObj);
        }
    }
}
