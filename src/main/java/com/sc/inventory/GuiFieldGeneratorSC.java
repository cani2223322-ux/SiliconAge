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
 * - Map: the cluster seen from above - the field at the master's height, the nodes, the player;
 * - Upgrades: four slots for energy storage (a bigger buffer) and transformer (EV input) upgrades, the player's inventory.
 * Buttons go through the vanilla GUI-button packet (ContainerFieldGeneratorSC.enchantItem), the
 * access list through FieldNetSC; the state comes back with the block (description packet).
 * Drawn in code, vanilla style (the old 176x100 texture couldn't hold it all).
 */
public class GuiFieldGeneratorSC extends GuiContainer {

    private static final int W = 248, H = 226;
    private static final int TAB_BASE = 100, ADD_ID = 200, REMOVE_BASE = 300, TAB_UPGRADES = 4, PAGE_ID = 40;
    /** The energy gauge (GuiEnergyGaugeSC); the Field tab's text rooms end 4 px before it. */
    private static final int ENERGY_X = 222, ENERGY_Y = 33, ENERGY_W = 22, ENERGY_H = 79;
    private static final int MAP_X = 10, MAP_Y = 34, MAP = 150, CELL = 2;

    private static int tab;                 // remembered while the game runs
    /** The Functions tab's second page: wireless charging. */
    private static boolean chargePage;

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
        String[] tabs = {"sc.fieldgui.tab.field", "sc.fieldgui.tab.functions", "sc.fieldgui.tab.access", "sc.fieldgui.tab.map",
                "sc.fieldgui.tab.upgrades"};
        // tabs: an icon and a short name (smaller, or left out when there's no room), the full name as the tooltip
        net.minecraft.item.ItemStack[] icons = {new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.fieldGeneratorSC),
                new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.lever), new net.minecraft.item.ItemStack(net.minecraft.init.Items.name_tag),
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.map),
                com.sc.init.ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE)};
        int tw = (W - 16 - 2 * (tabs.length - 1)) / tabs.length;
        for (int i = 0; i < tabs.length; i++) {
            GuiButton b = new TextFitSC.Tab(TAB_BASE + i, guiLeft + 8 + i * (tw + 2), guiTop + 5, tw, 20, icons[i],
                    Lang.tr(tabs[i] + ".short"), Lang.tr(tabs[i]));
            b.enabled = i != tab;
            buttonList.add(b);
        }
        ((ContainerFieldGeneratorSC) inventorySlots).setSlotsShown(tab == TAB_UPGRADES);
        nameField = null;
        int x = guiLeft + 8, y = guiTop + 30;
        switch (tab) {
            case 0: {
                int ry = guiTop + 118;
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_RANGE_MINUS_16, x, ry, 30, 20, "-16"));
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_RANGE_MINUS_1, x + 32, ry, 30, 20, "-1"));
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_RANGE_PLUS_1, x + 64, ry, 30, 20, "+1"));
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_RANGE_PLUS_16, x + 96, ry, 30, 20, "+16"));
                int by = guiTop + 142;
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_MODE, x, by, 114, 20, ""));
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_COLOR, x + 118, by, 114, 20, ""));
                buttonList.add(new TextFitSC.Button(flagId(TileEntityFieldGeneratorSC.F_SHOW), x, by + 22, 114, 20, ""));
                buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_REDSTONE, x + 118, by + 22, 114, 20, ""));
                break;
            }
            case 1: {
                buttonList.add(new TextFitSC.Button(PAGE_ID, guiLeft + W - 8 - 110, guiTop + H - 26, 110, 20, ""));
                if (chargePage) {
                    buttonList.add(new TextFitSC.Button(flagId(TileEntityFieldGeneratorSC.F_CHARGE), x, y + 14, 114, 20, ""));
                    buttonList.add(new TextFitSC.Button(flagId(TileEntityFieldGeneratorSC.F_CHARGE_FX), x + 118, y + 14, 114, 20, ""));
                    buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_CHARGE_MODE, x, y + 38, W - 16, 20, ""));
                    buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_RESERVE_MINUS, x, y + 62, 34, 20, "-10%"));
                    buttonList.add(new TextFitSC.Button(ContainerFieldGeneratorSC.BTN_RESERVE_PLUS, x + 36, y + 62, 34, 20, "+10%"));
                    break;
                }
                int[] rows = {TileEntityFieldGeneratorSC.F_NO_SPAWN, TileEntityFieldGeneratorSC.F_NO_ENDER, TileEntityFieldGeneratorSC.F_DAMAGE,
                        -1, TileEntityFieldGeneratorSC.F_WARN, TileEntityFieldGeneratorSC.F_HEAL};
                for (int i = 0; i < rows.length; i++) {
                    int id = rows[i] < 0 ? ContainerFieldGeneratorSC.BTN_FILTER : flagId(rows[i]);
                    buttonList.add(new TextFitSC.Button(id, x, y + i * 22, W - 16, 20, ""));
                }
                break;
            }
            case 2: {
                buttonList.add(new TextFitSC.Button(flagId(TileEntityFieldGeneratorSC.F_PRIVATE), x, y + 14, 114, 20, ""));
                buttonList.add(new TextFitSC.Button(flagId(TileEntityFieldGeneratorSC.F_PUSH_PLAYERS), x + 118, y + 14, 114, 20, ""));
                List<String> names = field.getAccess();
                for (int i = 0; i < names.size() && i < TileEntityFieldGeneratorSC.MAX_ACCESS; i++) {
                    int col = i % 3, row = i / 3;
                    GuiButton b = new TextFitSC.Button(REMOVE_BASE + i, x + col * 78, y + 56 + row * 16, 76, 15, "");
                    b.enabled = mayEditAccess();
                    buttonList.add(b);
                }
                nameField = new GuiTextField(fontRendererObj, x + 1, guiTop + H - 24, 160, 16);
                nameField.setMaxStringLength(16);
                nameField.setEnabled(mayEditAccess());
                GuiButton add = new TextFitSC.Button(ADD_ID, x + 166, guiTop + H - 26, 66, 20, Lang.tr("sc.fieldgui.access.add"));
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
            if (id == PAGE_ID) {
                b.displayString = Lang.tr(chargePage ? "sc.fieldgui.page.functions" : "sc.fieldgui.page.charge");
                continue;
            } else if (id == ContainerFieldGeneratorSC.BTN_CHARGE_MODE) {
                b.displayString = Lang.tr("sc.fieldgui.charge.mode", Lang.tr("sc.fieldgui.charge.mode." + field.getChargeMode()));
            } else if (id == ContainerFieldGeneratorSC.BTN_RESERVE_MINUS || id == ContainerFieldGeneratorSC.BTN_RESERVE_PLUS) {
                b.enabled = mayEdit() && (id == ContainerFieldGeneratorSC.BTN_RESERVE_MINUS ? field.getChargeReserve() > 0
                        : field.getChargeReserve() < TileEntityFieldGeneratorSC.RESERVE_MAX);
                continue;
            } else if (id >= ContainerFieldGeneratorSC.BTN_FLAG_BASE) {
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
        if (button.id == PAGE_ID) {
            chargePage = !chargePage;
            initGui();
        } else if (button.id >= TAB_BASE && button.id <= TAB_BASE + TAB_UPGRADES) {
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
            GuiEnergyGaugeSC.draw(guiLeft + ENERGY_X, guiTop + ENERGY_Y, ENERGY_W, ENERGY_H,
                    (float) field.getEnergyStored() / Math.max(1, field.getMaxEnergyStored()));
        } else if (tab == 2) {
            inset(guiLeft + 7, guiTop + 84, W - 14, 6 * 16 + 4);
        } else if (tab == 3) {
            inset(guiLeft + MAP_X - 1, guiTop + MAP_Y - 1, MAP + 2, MAP + 2);
            drawMap(guiLeft + MAP_X, guiTop + MAP_Y);
        } else if (tab == TAB_UPGRADES) {
            for (int i = 0; i < TileEntityFieldGeneratorSC.UPGRADE_SLOTS; i++) {
                inset(guiLeft + ContainerFieldGeneratorSC.UPGRADE_X - 1 + i * 18, guiTop + ContainerFieldGeneratorSC.UPGRADE_Y - 1, 18, 18);
            }
            for (int row = 0; row < 4; row++) {
                int y = guiTop + ContainerFieldGeneratorSC.INV_Y - 1 + row * 18 + (row == 3 ? 4 : 0);
                for (int col = 0; col < 9; col++) {
                    inset(guiLeft + ContainerFieldGeneratorSC.INV_X - 1 + col * 18, y, 18, 18);
                }
            }
            // the buffer, as a horizontal bar
            int bx = guiLeft + 44, by = guiTop + 101, bw = W - 88;
            inset(bx - 1, by - 1, bw + 2, 10);
            float fill = (float) field.getEnergyStored() / Math.max(1, field.getMaxEnergyStored());
            drawRect(bx, by, bx + (int) (bw * Math.min(1F, fill)), by + 8, 0xFFD02020);
            drawRect(bx, by, bx + (int) (bw * Math.min(1F, fill)), by + 2, 0xFFFF6060);
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
                int room = ENERGY_X - 12;
                fit(status, 8, 32, room, field.isActive() ? 0x2E7D32 : field.isRedstoneOff() ? 0x8A5A00 : 0xA02020);
                fit(Lang.tr("sc.gui.field.mode", modeName(field.getMode())), 8, 46, room, c);
                fit(Lang.tr("sc.gui.field.nodes", field.getNodeCount()), 8, 58, room, c);
                fit(Lang.tr("sc.gui.field.range", field.getRange()), 8, 70, room, c);
                fit(Lang.tr("sc.gui.field.upkeep", field.upkeepPerTick()), 8, 82, room, c);
                fit(Lang.tr("sc.fieldgui.owner", field.getOwner().isEmpty() ? "-" : field.getOwner()), 8, 96, room, dim);
                break;
            }
            case 1:
                if (chargePage) {
                    drawChargePage(c, dim);
                }
                break;
            case 2: {
                fit(Lang.tr("sc.fieldgui.owner", field.getOwner().isEmpty() ? "-" : field.getOwner()), 8, 32, W - 16, c);
                fit(Lang.tr("sc.fieldgui.access.list", field.getAccess().size(), TileEntityFieldGeneratorSC.MAX_ACCESS), 8, 72, W - 16, c);
                if (field.getAccess().isEmpty()) {
                    fit(Lang.tr("sc.fieldgui.access.empty"), 12, 90, W - 24, 0xE0E0E0);
                }
                if (!mayEditAccess()) {
                    fit(Lang.tr("sc.fieldgui.access.ownerOnly"), 8, H - 38, W - 16, 0xA02020);
                }
                break;
            }
            case TAB_UPGRADES: {
                String title = Lang.tr("sc.fieldgui.upgrades.title");
                fitCentered(title, 32, c);
                int tw = Math.min(W - 30, fontRendererObj.getStringWidth(title));
                TextFitSC.help(fontRendererObj, (W + tw) / 2 + 3, 31, Lang.tr("sc.fieldgui.upgrades.hint"), guiLeft, guiTop);
                int n = field.storageUpgrades();
                String count = Lang.tr("sc.fieldgui.upgrades.count", n, com.sc.machine.UpgradeType.MAX_EFFECTIVE,
                        n * com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE);
                fitCentered(count, 70, c);
                String buf = Lang.tr("sc.fieldgui.upgrades.buffer", field.getEnergyStored(), field.getMaxEnergyStored());
                fitCentered(buf, 80, c);
                String input = Lang.tr("sc.fieldgui.upgrades.input", field.inputTier().name(), field.inputTier().getVoltage());
                fitCentered(input, 90, c);
                fit(Lang.tr("container.inventory"), ContainerFieldGeneratorSC.INV_X, ContainerFieldGeneratorSC.INV_Y - 11, 162, c);
                break;
            }
            default: {
                int lx = MAP_X + MAP + 8;
                fit(Lang.tr("sc.fieldgui.map.title"), lx, 32, W - lx - 6, c);
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

    /** The Functions tab's second page: wireless charging settings and last second's numbers. */
    private void drawChargePage(int c, int dim) {
        String title = Lang.tr("sc.fieldgui.charge.title");
        fitCentered(title, 32, c);
        int tw = Math.min(W - 30, fontRendererObj.getStringWidth(title));
        TextFitSC.help(fontRendererObj, (W + tw) / 2 + 3, 31, Lang.tr("sc.fieldgui.charge.help"), guiLeft, guiTop);
        int reserveEu = (int) ((long) field.getMaxEnergyStored() * field.getChargeReserve() / 100);
        fit(Lang.tr("sc.fieldgui.charge.reserve", field.getChargeReserve(), reserveEu), 82, 98, W - 90, c);
        int boosters = Math.min(com.sc.machine.UpgradeType.MAX_CHARGE_BOOSTERS,
                field.upgradeCount(com.sc.machine.UpgradeType.CHARGE_BOOSTER));
        fit(Lang.tr("sc.fieldgui.charge.rate", field.chargeRate()), 8, 124, W - 16, c);
        fit(Lang.tr("sc.fieldgui.charge.boosters", boosters, com.sc.machine.UpgradeType.MAX_CHARGE_BOOSTERS), 8, 136, W - 16, c);
        boolean on = field.has(TileEntityFieldGeneratorSC.F_CHARGE) && field.isActive();
        fit(on ? Lang.tr("sc.fieldgui.charge.now", field.getChargedLastSecond(), field.getPlayersLastSecond())
                : Lang.tr("sc.fieldgui.charge.off"), 8, 150, W - 16, on ? 0x2E7D32 : 0xA02020);
        fontRendererObj.drawSplitString(Lang.tr("sc.fieldgui.charge.items"), 8, 168, W - 16, dim);
    }

    private void legend(int x, int y, int color, String text) {
        drawRect(x, y + 1, x + 6, y + 7, color);
        fit(text, x + 9, y, W - x - 15, 0x404040);
    }

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private void fitCentered(String text, int y, int color) {
        TextFitSC.drawCentered(fontRendererObj, text, 8, y, W - 16, color, false, guiLeft, guiTop);
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
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tip = new ArrayList<String>();
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            if (mouseX < b.xPosition || mouseY < b.yPosition || mouseX >= b.xPosition + b.width || mouseY >= b.yPosition + b.height) {
                continue;
            }
            String key = null;
            if (b.id == PAGE_ID) {
                key = null;
            } else if (b.id == ContainerFieldGeneratorSC.BTN_CHARGE_MODE) {
                key = "sc.fieldgui.charge.mode.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_RESERVE_MINUS || b.id == ContainerFieldGeneratorSC.BTN_RESERVE_PLUS) {
                key = "sc.fieldgui.charge.reserve.desc";
            } else if (b.id >= ContainerFieldGeneratorSC.BTN_FLAG_BASE && b.id < TAB_BASE) {
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
        if (tip.isEmpty() && TextFitSC.hoverAt(mouseX, mouseY) != null) {
            tip.addAll(TextFitSC.hoverAt(mouseX, mouseY));
        }
        if (!tip.isEmpty()) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }
}
