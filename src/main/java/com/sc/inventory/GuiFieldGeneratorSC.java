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
 * - Upgrades: four slots for energy storage (a bigger buffer) and transformer (EV input) upgrades, the player's inventory;
 * - Zone: radius, height, offset, centre and shape (at once, or held as a preview and applied), the
 *   outline, dashes, shell, animation, brightness, node beams, hum, and the three colours (RGB).
 * Buttons go through the vanilla GUI-button packet (ContainerFieldGeneratorSC.enchantItem), the
 * access list through FieldNetSC; the state comes back with the block (description packet).
 * Drawn in code, vanilla style (the old 176x100 texture couldn't hold it all).
 */
public class GuiFieldGeneratorSC extends GuiContainer {

    private static final int W = 248, H = 226;
    /**
     * Six tabs take two rows (4 + 2): the panel grows TABS_UP px upwards and guiTop stays the
     * content's origin (initGui moves it down), so every tab keeps its old coordinates.
     */
    private static final int TABS_UP = 22;
    private static final int TAB_BASE = 100, ADD_ID = 200, REMOVE_BASE = 300, TAB_UPGRADES = 4, TAB_ZONE = 5, PAGE_ID = 40;
    /** The Zone tab's own buttons (client side: they edit the draft, then FieldNetSC). */
    private static final int Z_BASE = 50, Z_RANGE = 50, Z_HEIGHT = 54, Z_OFFX = 58, Z_OFFZ = 62, Z_OFFY = 66, Z_SHAPE = 70,
            Z_ANCHOR = 71, Z_PREVIEW = 72, Z_APPLY = 73, Z_CANCEL = 74, Z_TARGET = 75, Z_PRESET = 80, Z_SLIDER = 90;
    private static final int[] STEP_BIG = {-16, -1, 1, 16}, STEP_OFF = {-8, -1, 1, 8};
    /** Draft indices: range, height, offset x / y / z, anchor, shape, set point x / y / z. */
    private static final int D_RANGE = 0, D_HEIGHT = 1, D_OX = 2, D_OY = 3, D_OZ = 4, D_ANCHOR = 5, D_MODE = 6, D_PX = 7;
    /** The energy gauge right of the screen, on every tab, under the redstone button (as a machine's). */
    private static final int ENERGY_X = 214, ENERGY_Y = 24, ENERGY_W = 26, ENERGY_H = 82;
    private static final int MAP_X = 10, MAP_Y = 34, MAP = 140, CELL = 2;
    /** The holo screen: its left edge and where the content's room ends (the gauge column past it). */
    private static final int SCREEN_X = 7, SCREEN_R = 209, CW = 198;

    private static int tab;                 // remembered while the game runs
    /** The Functions tab's second page: wireless charging. */
    private static boolean chargePage;
    /** The Zone tab: its draft (whose master), preview mode, the colour being edited. */
    private static int[] draft;
    private static String draftOf = "";
    private static boolean previewMode;
    private static int colorTarget;
    /** Ticks the screen keeps its own values after sending them, until the block's update arrives. */
    private int holdZone, holdRgb;
    private int editRgb = -1;
    private String volumeKey = "";
    private long volume;

    private final TileEntityFieldGeneratorSC field;
    /** The power switch and the redstone button over the gauge, as a machine's. */
    private GuiPowerSC power;
    /** The battery slot under the gauge (every tab). */
    private GuiBatterySlotSC battery;
    private GuiTextField nameField;
    /** The map, recomputed when the field's shape changes. */
    private boolean[][] mapCells;
    private String mapKey = "";
    private AxisAlignedBB mapBounds;

    public GuiFieldGeneratorSC(net.minecraft.entity.player.InventoryPlayer playerInv, TileEntityFieldGeneratorSC field) {
        super(new ContainerFieldGeneratorSC(playerInv, field));
        this.field = field;
        xSize = W;
        ySize = H + TABS_UP;
    }

    // ------------------------------------------------------------------ layout

    @Override
    public void initGui() {
        super.initGui();
        guiTop += TABS_UP;
        Keyboard.enableRepeatEvents(true);
        buttonList.clear();
        String[] tabs = {"sc.fieldgui.tab.field", "sc.fieldgui.tab.functions", "sc.fieldgui.tab.access", "sc.fieldgui.tab.map",
                "sc.fieldgui.tab.upgrades", "sc.fieldgui.tab.zone"};
        // tabs: an icon and a short name (smaller, or left out when there's no room), the full name as the tooltip
        net.minecraft.item.ItemStack[] icons = {new net.minecraft.item.ItemStack(com.sc.init.ModBlocks.fieldGeneratorSC),
                new net.minecraft.item.ItemStack(net.minecraft.init.Blocks.lever), new net.minecraft.item.ItemStack(net.minecraft.init.Items.name_tag),
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.map),
                com.sc.init.ModItems.upgrade.stackOf(com.sc.machine.UpgradeType.ENERGY_STORAGE),
                new net.minecraft.item.ItemStack(net.minecraft.init.Items.compass)};
        for (int i = 0; i < tabs.length; i++) {
            GuiButton b = new HoloTab(TAB_BASE + i, guiLeft + 7 + i * 34, guiTop - 4, 33, 13, icons[i],
                    Lang.tr(tabs[i] + ".short"), Lang.tr(tabs[i]));
            ((HoloTab) b).current = i == tab;
            buttonList.add(b);
        }
        ((ContainerFieldGeneratorSC) inventorySlots).setSlotsShown(tab == TAB_UPGRADES);
        nameField = null;
        power = new GuiPowerSC(field, ContainerFieldGeneratorSC.BTN_POWER, ContainerFieldGeneratorSC.BTN_REDSTONE,
                ENERGY_X, 11, ENERGY_W, ENERGY_Y, ENERGY_H);
        power.addButtons(buttonList, guiLeft, guiTop);
        battery = new GuiBatterySlotSC(field, ContainerFieldGeneratorSC.BTN_BATTERY_MODE, ENERGY_X, ENERGY_Y + ENERGY_H);
        battery.addButton(buttonList, guiLeft, guiTop);
        int x = guiLeft + 8, y = guiTop + 30;
        switch (tab) {
            case 0:
                break;
            case TAB_ZONE: {
                ensureDraft();
                int[] bases = {Z_RANGE, Z_HEIGHT, Z_OFFX, Z_OFFZ, Z_OFFY};
                for (int k = 0; k < bases.length; k++) {
                    int[] steps = k < 2 ? STEP_BIG : STEP_OFF;
                    for (int j = 0; j < 4; j++) {
                        buttonList.add(new HoloButton(bases[k] + j, x + j * 28, guiTop + 40 + k * 24, 27, 12,
                                (steps[j] > 0 ? "+" : "") + steps[j]));
                    }
                }
                buttonList.add(new HoloButton(Z_SHAPE, x, guiTop + 152, 114, 13, ""));
                buttonList.add(new HoloButton(Z_ANCHOR, x, guiTop + 167, 114, 13, ""));
                buttonList.add(new HoloButton(Z_PREVIEW, x, guiTop + 182, 114, 13, ""));
                buttonList.add(new HoloButton(Z_APPLY, x, guiTop + 197, 56, 13, Lang.tr("sc.fieldzone.apply")));
                buttonList.add(new HoloButton(Z_CANCEL, x + 58, guiTop + 197, 56, 13, Lang.tr("sc.fieldzone.cancel")));
                int rx = guiLeft + 126, rw = 80;
                int[] right = {ContainerFieldGeneratorSC.BTN_OUTLINE, flagId(TileEntityFieldGeneratorSC.F_DASH),
                        flagId(TileEntityFieldGeneratorSC.F_SHOW), ContainerFieldGeneratorSC.BTN_ANIM, ContainerFieldGeneratorSC.BTN_BRIGHT,
                        flagId(TileEntityFieldGeneratorSC.F_BEAMS), flagId(TileEntityFieldGeneratorSC.F_HUM)};
                for (int i = 0; i < right.length; i++) {
                    buttonList.add(new HoloButton(right[i], rx, guiTop + 30 + i * 14, rw, 13, ""));
                }
                buttonList.add(new HoloButton(Z_TARGET, rx, guiTop + 130, rw - 18, 13, ""));
                buttonList.add(new HoloButton(Z_PRESET, rx, guiTop + 146, 16, 12, "<"));        // leaf through the ready colours
                buttonList.add(new HoloButton(Z_PRESET + 1, rx + rw - 16, guiTop + 146, 16, 12, ">"));
                for (int ch = 0; ch < 3; ch++) {
                    buttonList.add(new Slider(Z_SLIDER + ch, rx, guiTop + 162 + ch * 14, rw, 12, ch));
                }
                break;
            }
            case 1: {
                buttonList.add(new HoloButton(PAGE_ID, guiLeft + SCREEN_R - 4 - 90, guiTop + H - 26, 90, 18, ""));
                if (chargePage) {
                    buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_CHARGE), x, y + 14, 96, 20, ""));
                    buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_CHARGE_FX), x + 100, y + 14, 98, 20, ""));
                    buttonList.add(new HoloButton(ContainerFieldGeneratorSC.BTN_CHARGE_MODE, x, y + 38, CW, 20, ""));
                    buttonList.add(new HoloButton(ContainerFieldGeneratorSC.BTN_RESERVE_MINUS, x, y + 62, 34, 20, "-10%"));
                    buttonList.add(new HoloButton(ContainerFieldGeneratorSC.BTN_RESERVE_PLUS, x + 36, y + 62, 34, 20, "+10%"));
                    buttonList.add(new HoloButton(ContainerFieldGeneratorSC.BTN_BELOW_MINUS, x, y + 86, 34, 20, "-10%"));
                    buttonList.add(new HoloButton(ContainerFieldGeneratorSC.BTN_BELOW_PLUS, x + 36, y + 86, 34, 20, "+10%"));
                    buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_SKIP_BATTERIES), x, y + 110, 96, 20, ""));
                    buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_CHARGE_EACH), x + 100, y + 110, 98, 20, ""));
                    break;
                }
                int[] rows = {TileEntityFieldGeneratorSC.F_NO_SPAWN, TileEntityFieldGeneratorSC.F_NO_ENDER, TileEntityFieldGeneratorSC.F_DAMAGE,
                        -1, TileEntityFieldGeneratorSC.F_WARN, TileEntityFieldGeneratorSC.F_HEAL};
                for (int i = 0; i < rows.length; i++) {
                    int id = rows[i] < 0 ? ContainerFieldGeneratorSC.BTN_FILTER : flagId(rows[i]);
                    buttonList.add(new HoloButton(id, x, y + i * 22, CW, 20, ""));
                }
                // the last row: the two weather / radiation shields side by side
                buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_RAIN), x, y + rows.length * 22, 96, 20, ""));
                buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_RADIATION), x + 100, y + rows.length * 22, 98, 20, ""));
                break;
            }
            case 2: {
                buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_PRIVATE), x, y + 14, 96, 20, ""));
                buttonList.add(new HoloButton(flagId(TileEntityFieldGeneratorSC.F_PUSH_PLAYERS), x + 100, y + 14, 98, 20, ""));
                List<String> names = field.getAccess();
                for (int i = 0; i < names.size() && i < TileEntityFieldGeneratorSC.MAX_ACCESS; i++) {
                    int col = i % 3, row = i / 3;
                    GuiButton b = new HoloButton(REMOVE_BASE + i, x + 2 + col * 65, y + 56 + row * 16, 63, 15, "");
                    b.enabled = mayEditAccess();
                    buttonList.add(b);
                }
                nameField = new GuiTextField(fontRendererObj, x + 1, guiTop + H - 24, 126, 16);
                nameField.setMaxStringLength(16);
                nameField.setEnabled(mayEditAccess());
                GuiButton add = new HoloButton(ADD_ID, x + 132, guiTop + H - 26, 66, 20, Lang.tr("sc.fieldgui.access.add"));
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
            if (id >= Z_BASE && id < TAB_BASE) {
                refreshZone(b);
                continue;
            }
            if (id == ContainerFieldGeneratorSC.BTN_OUTLINE) {
                b.displayString = Lang.tr("sc.fieldzone.outline", Lang.tr("sc.fieldzone.outline." + field.getOutline()));
                b.enabled = mayEdit();
                continue;
            } else if (id == ContainerFieldGeneratorSC.BTN_ANIM) {
                b.displayString = Lang.tr("sc.fieldzone.anim", Lang.tr("sc.fieldzone.anim." + field.getAnim()));
                b.enabled = mayEdit();
                continue;
            } else if (id == ContainerFieldGeneratorSC.BTN_BRIGHT) {
                b.displayString = Lang.tr("sc.fieldzone.bright", field.getBrightness());
                b.enabled = mayEdit();
                continue;
            }
            if (id >= TAB_BASE) {
                if (id >= REMOVE_BASE && id - REMOVE_BASE < field.getAccess().size()) {
                    b.displayString = "§cx§r " + field.getAccess().get(id - REMOVE_BASE);
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
            } else if (id == ContainerFieldGeneratorSC.BTN_BELOW_MINUS || id == ContainerFieldGeneratorSC.BTN_BELOW_PLUS) {
                b.enabled = mayEdit() && (id == ContainerFieldGeneratorSC.BTN_BELOW_MINUS
                        ? field.getChargeBelow() > TileEntityFieldGeneratorSC.CHARGE_BELOW_MIN : field.getChargeBelow() < 100);
                continue;
            } else if (id >= ContainerFieldGeneratorSC.BTN_FLAG_BASE) {
                int flag = 1 << (id - ContainerFieldGeneratorSC.BTN_FLAG_BASE);
                boolean on = field.has(flag);
                b.displayString = (on ? "§a" : "§7") + Lang.tr("sc.fieldgui.flag." + flag) + ": " + onOff(on);
            } else if (id == ContainerFieldGeneratorSC.BTN_MODE) {
                b.displayString = Lang.tr("sc.fieldgui.shape", modeName(field.getMode()));
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

    /** The Zone tab's own buttons from the draft. */
    private void refreshZone(GuiButton b) {
        int id = b.id;
        boolean edit = mayEdit();
        if (id < Z_SHAPE) {
            int row = (id - Z_BASE) / 4, j = (id - Z_BASE) % 4;
            int step = (row < 2 ? STEP_BIG : STEP_OFF)[j];
            int v = draft[row == 0 ? D_RANGE : row == 1 ? D_HEIGHT : row == 2 ? D_OX : row == 3 ? D_OZ : D_OY];
            if (row == 0) {
                b.enabled = edit && (step < 0 ? v > FieldShapeSC.MIN_RANGE : v < FieldShapeSC.MAX_RANGE);
            } else if (row == 1) {
                b.enabled = edit && (step < 0 ? v > 0 : v < FieldShapeSC.MAX_RANGE);
            } else {
                b.enabled = edit && (step < 0 ? v > -TileEntityFieldGeneratorSC.MAX_OFFSET : v < TileEntityFieldGeneratorSC.MAX_OFFSET);
            }
            return;
        }
        boolean pending = previewMode && differs();
        switch (id) {
            case Z_SHAPE:
                b.displayString = Lang.tr("sc.fieldgui.shape", modeName(FieldMode.values()[draft[D_MODE]]));
                break;
            case Z_ANCHOR:
                b.displayString = Lang.tr("sc.fieldzone.anchor", Lang.tr("sc.fieldzone.anchor." + draft[D_ANCHOR]));
                break;
            case Z_PREVIEW:
                b.displayString = (previewMode ? "\u00a7a" : "\u00a77") + Lang.tr("sc.fieldzone.preview", onOff(previewMode));
                break;
            case Z_APPLY:
            case Z_CANCEL:
                b.enabled = edit && pending;
                return;
            case Z_TARGET:
                b.displayString = Lang.tr("sc.fieldzone.target", Lang.tr("sc.fieldzone.target." + colorTarget));
                break;
            default:
                break;
        }
        b.enabled = edit;
    }

    // ------------------------------------------------------------------ the Zone tab's draft

    private int[] fromField() {
        int[] ap = field.getAnchorPoint();
        return new int[]{field.getRange(), field.getHeight(), field.getOffset(0), field.getOffset(1), field.getOffset(2),
                field.getAnchor(), field.getMode().ordinal(), ap != null ? ap[0] : field.xCoord, ap != null ? ap[1] : field.yCoord,
                ap != null ? ap[2] : field.zCoord};
    }

    private String key() {
        return field.xCoord + "," + field.yCoord + "," + field.zCoord;
    }

    private void ensureDraft() {
        if (draft == null || !draftOf.equals(key())) {
            draft = fromField();
            draftOf = key();
            previewClear();
        }
        if (editRgb < 0) {
            editRgb = field.getRgb(colorTarget);
        }
    }

    /** The draft differs from the field's shape in anything that moves it. */
    private boolean differs() {
        int[] f = fromField();
        for (int i = 0; i < D_PX; i++) {
            if (f[i] != draft[i]) {
                return true;
            }
        }
        return draft[D_ANCHOR] == TileEntityFieldGeneratorSC.ANCHOR_POINT
                && (f[D_PX] != draft[D_PX] || f[D_PX + 1] != draft[D_PX + 1] || f[D_PX + 2] != draft[D_PX + 2]);
    }

    private void sendZone() {
        FieldNetSC.CHANNEL.sendToServer(new FieldNetSC.Message(field, FieldNetSC.ZONE, draft.clone()));
        holdZone = 20;
    }

    private void sendRgb() {
        FieldNetSC.CHANNEL.sendToServer(new FieldNetSC.Message(field, FieldNetSC.RGB, colorTarget, editRgb));
        holdRgb = 20;
    }

    /** The preview in the world: the draft while it differs from the field, in preview mode. */
    private void updatePreview() {
        if (!previewMode || !differs()) {
            previewClear();
            return;
        }
        int[] point = {draft[D_PX], draft[D_PX + 1], draft[D_PX + 2]};
        previewShow(field, FieldMode.values()[draft[D_MODE]],
                TileEntityFieldGeneratorSC.zoneNodesFor(field.getNodePositions(), draft[D_ANCHOR], point, draft[D_OX], draft[D_OY], draft[D_OZ]),
                draft[D_RANGE], draft[D_HEIGHT]);
    }

    private static void previewClear() {
        com.sc.client.FieldRendererSC.preview = null;
    }

    private static void previewShow(TileEntityFieldGeneratorSC f, FieldMode mode, List<int[]> nodes, int range, int height) {
        com.sc.client.FieldRendererSC.Preview p = new com.sc.client.FieldRendererSC.Preview();
        p.x = f.xCoord;
        p.y = f.yCoord;
        p.z = f.zCoord;
        p.mode = mode;
        p.nodes = nodes;
        p.range = range;
        p.height = height;
        com.sc.client.FieldRendererSC.preview = p;
    }

    /** A Zone tab button: edit the draft, send it at once (or leave it as a preview). */
    private void zoneAction(int id) {
        if (id < Z_SHAPE) {
            int row = (id - Z_BASE) / 4, j = (id - Z_BASE) % 4;
            int step = (row < 2 ? STEP_BIG : STEP_OFF)[j];
            if (row == 0) {
                draft[D_RANGE] = FieldShapeSC.clampRange(draft[D_RANGE] + step);
            } else if (row == 1) {
                int h = draft[D_HEIGHT] == 0 ? draft[D_RANGE] : draft[D_HEIGHT];
                h += step;
                draft[D_HEIGHT] = h <= 0 ? 0 : FieldShapeSC.clampRange(h);
                if (draft[D_HEIGHT] == 0 && step < -1) {
                    draft[D_HEIGHT] = 1;                    // -16 stops at 1; -1 from 1 goes back to "as the radius"
                }
            } else {
                int idx = row == 2 ? D_OX : row == 3 ? D_OZ : D_OY;
                int m = TileEntityFieldGeneratorSC.MAX_OFFSET;
                draft[idx] = Math.max(-m, Math.min(m, draft[idx] + step));
            }
        } else if (id == Z_SHAPE) {
            draft[D_MODE] = (draft[D_MODE] + 1) % FieldMode.values().length;
        } else if (id == Z_ANCHOR) {
            draft[D_ANCHOR] = (draft[D_ANCHOR] + 1) % TileEntityFieldGeneratorSC.ANCHORS;
            if (draft[D_ANCHOR] == TileEntityFieldGeneratorSC.ANCHOR_POINT) {       // the point: where the player stands
                draft[D_PX] = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posX);
                draft[D_PX + 1] = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.boundingBox.minY);
                draft[D_PX + 2] = net.minecraft.util.MathHelper.floor_double(mc.thePlayer.posZ);
            }
        } else if (id == Z_PREVIEW) {
            previewMode = !previewMode;
            draft = fromField();
            updatePreview();
            return;
        } else if (id == Z_APPLY) {
            sendZone();
            previewClear();
            return;
        } else if (id == Z_CANCEL) {
            draft = fromField();
            previewClear();
            return;
        } else if (id == Z_TARGET) {
            colorTarget = (colorTarget + 1) % TileEntityFieldGeneratorSC.RGB_TARGETS;
            editRgb = field.getRgb(colorTarget);
            return;
        } else if (id == Z_PRESET || id == Z_PRESET + 1) {
            int n = TileEntityFieldGeneratorSC.PRESETS.length, at = presetIndex(editRgb);
            at = at < 0 ? (id == Z_PRESET ? n - 1 : 0) : (at + (id == Z_PRESET ? n - 1 : 1)) % n;
            editRgb = TileEntityFieldGeneratorSC.PRESETS[at];
            sendRgb();
            return;
        } else {
            return;                                         // sliders send on release
        }
        if (previewMode) {
            updatePreview();
        } else {
            sendZone();
        }
    }

    /** An R / G / B slider for the colour being edited; sends when let go. */
    private class Slider extends GuiButton {
        private final int channel;
        private boolean dragging;

        Slider(int id, int x, int y, int w, int h, int channel) {
            super(id, x, y, w, h, "");
            this.channel = channel;
        }

        private int value() {
            return (editRgb >> (16 - channel * 8)) & 255;
        }

        private void setFrom(int mx) {
            int v = Math.max(0, Math.min(255, Math.round((mx - xPosition - 3) * 255F / (width - 6))));
            int shift = 16 - channel * 8;
            editRgb = (editRgb & ~(255 << shift)) | v << shift;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            if (dragging) {
                setFrom(mx);
            }
            int v = value(), x = xPosition, y = yPosition;
            drawRect(x, y, x + width, y + height, 0xFF000000);
            drawRect(x + 1, y + 1, x + width - 1, y + height - 1, 0xFF28282C);
            int tint = channel == 0 ? 0x70FF4040 : channel == 1 ? 0x7040FF60 : 0x704080FF;
            drawRect(x + 1, y + 1, x + 1 + (width - 2) * v / 255, y + height - 1, tint);
            int kx = x + (width - 6) * v / 255;
            drawRect(kx, y - 1, kx + 6, y + height + 1, 0xFF000000);
            drawRect(kx + 1, y, kx + 5, y + height, enabled ? 0xFFA8A8A8 : 0xFF606060);
            drawRect(kx + 1, y, kx + 5, y + 1, 0xFFE0E0E0);
            String s = "RGB".charAt(channel) + ": " + v;
            mc.fontRenderer.drawStringWithShadow(s, x + (width - mc.fontRenderer.getStringWidth(s)) / 2, y + (height - 8) / 2 + 1,
                    enabled ? 0xFFFFFF : 0xA0A0A0);
        }

        @Override
        public boolean mousePressed(net.minecraft.client.Minecraft mc, int mx, int my) {
            if (enabled && visible && mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height) {
                dragging = true;
                setFrom(mx);
                return true;
            }
            return false;
        }

        @Override
        public void mouseReleased(int mx, int my) {
            if (dragging) {
                dragging = false;
                sendRgb();
            }
        }

        boolean isDragging() {
            return dragging;
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
        if (tab == TAB_ZONE) {
            if (holdZone > 0) {
                holdZone--;
            } else if (!previewMode) {
                draft = fromField();
            }
            boolean dragging = false;
            for (Object o : buttonList) {
                dragging |= o instanceof Slider && ((Slider) o).isDragging();
            }
            if (holdRgb > 0) {
                holdRgb--;
            } else if (!dragging) {
                editRgb = field.getRgb(colorTarget);
            }
            updatePreview();
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
        if (!power.allowClick(button)) {
            return;
        }
        if (button.id == PAGE_ID) {
            chargePage = !chargePage;
            initGui();
        } else if (button.id >= Z_BASE && button.id < TAB_BASE) {
            zoneAction(button.id);
        } else if (button.id >= TAB_BASE && button.id <= TAB_BASE + TAB_ZONE) {
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

    /** A dark well on the holo screen (map, name list, slots). */
    private void inset(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF1E3444);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF050A10);
    }

    /** The window as a machine's: the frame, the dark title strip. */
    private void window(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF1E2024);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFFB9C1CC);
        drawRect(x + 1, y + 1, x + w - 2, y + 2, 0xFFECF0F6);
        drawRect(x + 1, y + 1, x + 2, y + h - 2, 0xFFE2E8F0);
        drawRect(x + 2, y + h - 2, x + w - 1, y + h - 1, 0xFF6E747E);
        drawRect(x + w - 2, y + 2, x + w - 1, y + h - 1, 0xFF767C86);
        drawRect(x + 3, y + 3, x + w - 3, y + 4, 0xFF6E747E);
        drawRect(x + 3, y + 3, x + 4, y + 16, 0xFF6E747E);
        drawRect(x + 4, y + 4, x + w - 4, y + 15, 0xFF2E3642);
        drawRect(x + 4, y + 15, x + w - 4, y + 16, 0xFFF0F2F6);
    }

    /** The Field tab's four readings: mode, nodes, range, upkeep. */
    private static final int[][] F_CARDS = {{14, 28}, {58, 28}, {14, 52}, {58, 52}};

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int gx = guiLeft, gy = guiTop;
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        window(gx, gy - TABS_UP, W, H + TABS_UP);
        // the holo screen: short where something sits under it (the Field tab's summary, the inventory)
        int screenH = tab == 0 ? 94 : tab == TAB_UPGRADES ? 106 : H - 14;
        GuiHoloSC.screen(gx + SCREEN_X, gy + 12, SCREEN_R - SCREEN_X, screenH);
        GuiEnergyGaugeSC.draw(gx + ENERGY_X, gy + ENERGY_Y, ENERGY_W, ENERGY_H,
                (float) field.getEnergyStored() / Math.max(1, field.getMaxEnergyStored()));
        battery.draw(gx, gy, field.getStackInSlot(TileEntityFieldGeneratorSC.SLOT_BATTERY));
        if (tab == 0) {
            for (int[] c : F_CARDS) {
                drawRect(gx + c[0], gy + c[1], gx + c[0] + 42, gy + c[1] + 20, 0xFF2A6A8A);
                drawRect(gx + c[0] + 1, gy + c[1] + 1, gx + c[0] + 41, gy + c[1] + 19, 0xFF0E3A50);
            }
            float[] rgb = field.rgbF(TileEntityFieldGeneratorSC.RGB_SHELL);
            int col = (int) (rgb[0] * 255) << 16 | (int) (rgb[1] * 255) << 8 | (int) (rgb[2] * 255);
            FieldMode mode = field.getMode();
            GuiSceneSC.fieldScene(gx + 104, gy + 18, 100, 82, t, mode != FieldMode.UNION && mode != FieldMode.DOME,
                    field.isActive(), col);
            GuiHoloSC.screen(gx + SCREEN_X, gy + 110, SCREEN_R - SCREEN_X, H - 112);   // the summary under it
            GuiHoloSC.glint(gx + SCREEN_X, gy + 110, SCREEN_R - SCREEN_X, H - 112);
        } else if (tab == 2) {
            inset(gx + 8, gy + 84, CW, 6 * 16 + 4);
        } else if (tab == 3) {
            inset(guiLeft + MAP_X - 1, guiTop + MAP_Y - 1, MAP + 2, MAP + 2);
            drawMap(guiLeft + MAP_X, guiTop + MAP_Y);
        } else if (tab == TAB_UPGRADES) {
            for (int i = 0; i < TileEntityFieldGeneratorSC.UPGRADE_SLOTS; i++) {
                GuiHoloSC.slot(gx + ContainerFieldGeneratorSC.UPGRADE_X + i * 18, gy + ContainerFieldGeneratorSC.UPGRADE_Y, false);
            }
            for (int row = 0; row < 4; row++) {
                int y = gy + ContainerFieldGeneratorSC.INV_Y - 1 + row * 18 + (row == 3 ? 4 : 0);
                for (int col = 0; col < 9; col++) {
                    int px = gx + ContainerFieldGeneratorSC.INV_X - 1 + col * 18;
                    drawRect(px, y, px + 18, y + 18, 0xFF343C48);
                    drawRect(px + 1, y + 1, px + 18, y + 18, 0xFFECF1F7);
                    drawRect(px + 1, y + 1, px + 17, y + 17, 0xFF707A88);
                }
            }
            // the buffer, as a segmented bar
            int bx = gx + 14, by = gy + 101, n = 36;
            GuiHoloSC.bar(bx, by, 188, 6, (float) field.getEnergyStored() / Math.max(1, field.getMaxEnergyStored()), n, 0xFF6EE6FF);
        }
        if (tab != 0) {
            GuiHoloSC.glint(gx + SCREEN_X, gy + 12, SCREEN_R - SCREEN_X, screenH);
        } else {
            GuiHoloSC.glint(gx + SCREEN_X, gy + 12, SCREEN_R - SCREEN_X, screenH);
        }
        if (nameField != null) {
            nameField.drawTextBox();
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int c = GuiHoloSC.VALUE, dim = GuiHoloSC.LABEL;
        fontRendererObj.drawString(Lang.tr("tile.siliconage.fieldGeneratorSC.name"), 8, 5 - TABS_UP, 0xF0F4FA);
        power.drawGaugeOff(fontRendererObj);
        String[] captions = {"sc.fieldgui.cap.field", chargePage ? "sc.fieldgui.cap.charge" : "sc.fieldgui.cap.functions",
                "sc.fieldgui.cap.access", "sc.fieldgui.cap.map", "sc.fieldgui.cap.upgrades", "sc.fieldgui.cap.zone"};
        if (tab != 0) {
            fit(Lang.tr(captions[tab]), 12, 15, 90, GuiHoloSC.CYAN & 0xFFFFFF);
        }
        switch (tab) {
            case 0: {
                fit(Lang.tr(captions[0]), 12, 15, 40, GuiHoloSC.CYAN & 0xFFFFFF);
                boolean foreign = !field.isActive() && field.isForeignNear();      // power-on / zone refused: a stranger's field
                String status = foreign ? Lang.tr("sc.fieldgui.state.foreign")
                        : !field.isPowerOn() ? Lang.tr("sc.fieldgui.state.disabled")
                        : field.isRedstoneOff() ? Lang.tr("sc.fieldgui.state.redstone")
                        : Lang.tr(field.isActive() ? "sc.fieldgui.state.on" : "sc.fieldgui.state.off");
                fit(status, 54, 16, 48, field.isActive() ? GuiHoloSC.OK : foreign ? GuiHoloSC.BAD
                        : !field.isPowerOn() ? GuiHoloSC.IDLE : field.isRedstoneOff() ? GuiHoloSC.WARN : GuiHoloSC.BAD);
                String[] lab = {Lang.tr("sc.fieldgui.card.mode"), Lang.tr("sc.fieldgui.card.nodes"), Lang.tr("sc.fieldgui.card.range"),
                        Lang.tr("sc.fieldgui.card.upkeep")};
                String[] val = {modeName(field.getMode()), String.valueOf(field.getNodeCount()),
                        Lang.tr("sc.fieldgui.card.blocks", field.getRange()), field.upkeepPerTick() + " EU/t"};
                for (int i = 0; i < 4; i++) {
                    int cx = F_CARDS[i][0], cy = F_CARDS[i][1];
                    small(lab[i], cx + 21, cy + 2, 38, dim, true);
                    TextFitSC.drawCentered(fontRendererObj, val[i], cx + 1, cy + 9, 40, c, false, guiLeft, guiTop);
                }
                small(Lang.tr("sc.fieldgui.owner", field.getOwner().isEmpty() ? "-" : field.getOwner()), 14, 78, 88, dim, false);
                small(Lang.tr("sc.fieldgui.card.access", field.getAccess().size()), 14, 86, 88, dim, false);
                // the summary: what's switched on
                fit(Lang.tr("sc.fieldgui.cap.summary"), 12, 114, 120, GuiHoloSC.CYAN & 0xFFFFFF);
                int[] flags = {TileEntityFieldGeneratorSC.F_NO_SPAWN, TileEntityFieldGeneratorSC.F_NO_ENDER, TileEntityFieldGeneratorSC.F_DAMAGE,
                        TileEntityFieldGeneratorSC.F_WARN, TileEntityFieldGeneratorSC.F_HEAL, TileEntityFieldGeneratorSC.F_CHARGE,
                        TileEntityFieldGeneratorSC.F_PRIVATE, TileEntityFieldGeneratorSC.F_PUSH_PLAYERS};
                for (int i = 0; i < flags.length; i++) {
                    int fx = 14 + (i % 2) * 98, fy = 127 + (i / 2) * 11;
                    boolean on = field.has(flags[i]);
                    drawRect(fx, fy + 1, fx + 4, fy + 5, on ? 0xFF5AE66E : 0xFF3A4450);
                    fit(Lang.tr("sc.fieldgui.flag." + flags[i]), fx + 7, fy, 88, on ? c : 0x465A6E);
                }
                String rain;
                int rainCol = dim;
                if (!field.has(TileEntityFieldGeneratorSC.F_RAIN)) {
                    rain = Lang.tr("sc.fieldgui.sum.rain.off");
                } else if (!field.rainOverField()) {
                    rain = Lang.tr("sc.fieldgui.sum.rain.dry");
                } else if (field.isRainShield()) {
                    rain = Lang.tr("sc.fieldgui.sum.rain.on", field.rainExtraPerTick());
                    rainCol = GuiHoloSC.OK;
                } else {
                    rain = Lang.tr("sc.fieldgui.sum.rain.low", field.rainExtraPerTick());
                    rainCol = GuiHoloSC.WARN;
                }
                small(rain, 14, 171, CW - 10, rainCol, false);
                small(Lang.tr("sc.fieldgui.sum.modules", field.storageUpgrades(),
                        field.upgradeCount(com.sc.machine.UpgradeType.CHARGE_BOOSTER)), 14, 180, CW - 10, dim, false);
                small(Lang.tr("sc.fieldgui.sum.input", field.inputTier().name(), field.inputTier().getVoltage(),
                        field.getEnergyStored(), field.getMaxEnergyStored()), 14, 188, CW - 10, dim, false);
                small(Lang.tr("sc.fieldgui.sum.hint"), 14, 198, CW - 10, 0x465A6E, false);
                break;
            }
            case 1:
                if (chargePage) {
                    drawChargePage(c, dim);
                }
                break;
            case 2: {
                fit(Lang.tr("sc.fieldgui.owner", field.getOwner().isEmpty() ? "-" : field.getOwner()), 100, 15, CW - 92, c);
                fit(Lang.tr("sc.fieldgui.access.list", field.getAccess().size(), TileEntityFieldGeneratorSC.MAX_ACCESS), 8, 72, CW, dim);
                if (field.getAccess().isEmpty()) {
                    fit(Lang.tr("sc.fieldgui.access.empty"), 12, 90, CW - 8, 0x465A6E);
                }
                if (!mayEditAccess()) {
                    fit(Lang.tr("sc.fieldgui.access.ownerOnly"), 8, H - 38, CW, GuiHoloSC.BAD);
                }
                break;
            }
            case TAB_ZONE:
                drawZone(c);
                break;
            case TAB_UPGRADES: {
                String title = Lang.tr("sc.fieldgui.upgrades.title");
                fitCentered(title, 32, c);
                int tw = Math.min(CW - 14, fontRendererObj.getStringWidth(title));
                TextFitSC.help(fontRendererObj, 8 + (CW + tw) / 2 + 3, 31, Lang.tr("sc.fieldgui.upgrades.hint"), guiLeft, guiTop);
                int n = field.storageUpgrades();
                String count = Lang.tr("sc.fieldgui.upgrades.count", n, com.sc.machine.UpgradeType.MAX_EFFECTIVE,
                        n * com.sc.machine.UpgradeType.STORAGE_PER_UPGRADE);
                fitCentered(count, 70, c);
                String buf = Lang.tr("sc.fieldgui.upgrades.buffer", field.getEnergyStored(), field.getMaxEnergyStored());
                fitCentered(buf, 80, c);
                String input = Lang.tr("sc.fieldgui.upgrades.input", field.inputTier().name(), field.inputTier().getVoltage());
                fitCentered(input, 90, c);
                fit(Lang.tr("container.inventory"), ContainerFieldGeneratorSC.INV_X, ContainerFieldGeneratorSC.INV_Y - 11, 162, 0x404040);
                break;
            }
            default: {
                int lx = MAP_X + MAP + 8;
                fit(Lang.tr("sc.fieldgui.map.title"), lx, 32, SCREEN_R - 3 - lx, GuiHoloSC.CYAN & 0xFFFFFF);
                legend(lx, 48, 0xFFFFE040, Lang.tr("sc.fieldgui.map.master"));
                legend(lx, 60, 0xFFFFFFFF, Lang.tr("sc.fieldgui.map.node"));
                legend(lx, 72, 0xFFFF4040, Lang.tr("sc.fieldgui.map.you"));
                float[] col = field.rgbF(TileEntityFieldGeneratorSC.RGB_SHELL);
                legend(lx, 84, 0xFF000000 | ((int) (col[0] * 200) << 16) | ((int) (col[1] * 200) << 8) | (int) (col[2] * 200),
                        Lang.tr("sc.fieldgui.map.field"));
                fontRendererObj.drawString("N ↑", MAP_X + MAP / 2 - 6, MAP_Y + 2, 0xFFFFFF);
                if (mapBounds != null) {
                    int wBlocks = (int) Math.round(mapBounds.maxX - mapBounds.minX), dBlocks = (int) Math.round(mapBounds.maxZ - mapBounds.minZ);
                    fontRendererObj.drawString(wBlocks + " x " + dBlocks, lx, 104, dim);
                }
                fontRendererObj.drawSplitString(Lang.tr("sc.fieldgui.map.hint"), lx, 120, SCREEN_R - 3 - lx, dim);
                GL11.glColor4f(1F, 1F, 1F, 1F);
            }
        }
    }

    /** The Zone tab's labels, the colour swatches and what the draft holds and costs. */
    private void drawZone(int c) {
        FieldMode mode = FieldMode.values()[draft[D_MODE]];
        String height = draft[D_HEIGHT] > 0 ? Lang.tr("sc.fieldzone.height", draft[D_HEIGHT])
                : Lang.tr(mode == FieldMode.PRISM ? "sc.fieldzone.height.world" : "sc.fieldzone.height.auto");
        String[] labels = {Lang.tr("sc.fieldzone.radius", draft[D_RANGE], FieldShapeSC.MAX_RANGE), height,
                Lang.tr("sc.fieldzone.offx", signed(draft[D_OX])), Lang.tr("sc.fieldzone.offz", signed(draft[D_OZ])),
                Lang.tr("sc.fieldzone.offy", signed(draft[D_OY]))};
        for (int k = 0; k < labels.length; k++) {
            fit(labels[k], 8, 30 + k * 24, 114, c);
        }
        // the colour being edited; between < and > the ready colour it is (its name, which of how many) or "own"
        int rx = 126;
        drawRect(rx + 64, 130, rx + 80, 143, 0xFF1E3444);
        drawRect(rx + 65, 131, rx + 79, 142, 0xFF000000 | editRgb);
        drawRect(rx + 17, 146, rx + 63, 158, 0xFF1E3444);
        drawRect(rx + 18, 147, rx + 62, 157, 0xFF000000 | editRgb);
        int at = presetIndex(editRgb);
        String name = at < 0 ? Lang.tr("sc.fieldzone.preset.own")
                : Lang.tr("sc.fieldzone.preset." + at) + " " + (at + 1) + "/" + TileEntityFieldGeneratorSC.PRESETS.length;
        int lum = ((editRgb >> 16 & 255) * 3 + (editRgb >> 8 & 255) * 6 + (editRgb & 255)) / 10;
        small(name, rx + 40, 149, 42, lum > 140 ? 0x0A1420 : 0xF0F4FA, true);
        // the readout: blocks held and the upkeep, for the draft
        int[] point = {draft[D_PX], draft[D_PX + 1], draft[D_PX + 2]};
        List<int[]> nodes = TileEntityFieldGeneratorSC.zoneNodesFor(field.getNodePositions(), draft[D_ANCHOR], point,
                draft[D_OX], draft[D_OY], draft[D_OZ]);
        String vk = java.util.Arrays.toString(draft) + "/" + field.getNodePositions().size();
        if (!vk.equals(volumeKey)) {
            volumeKey = vk;
            volume = FieldShapeSC.volume(mode, nodes, draft[D_RANGE], draft[D_HEIGHT]);
        }
        int upkeep = TileEntityFieldGeneratorSC.upkeepFor(field.getNodeCount(), draft[D_RANGE], draft[D_HEIGHT], mode)
                + com.sc.util.ConfigSC.scale(field.extrasPerTick(), com.sc.util.ConfigSC.fieldUpkeep, 0);
        String info = Lang.tr("sc.fieldzone.info", thousands(volume), upkeep);
        boolean pending = previewMode && differs();
        if (pending) {
            info += " " + Lang.tr("sc.fieldzone.pending");
        }
        fit(info, 8, 213, CW, pending ? GuiHoloSC.WARN : 0x6EB4FF);
    }

    private static String signed(int v) {
        return v > 0 ? "+" + v : String.valueOf(v);
    }

    private static String thousands(long v) {
        String s = String.valueOf(v);
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < s.length(); i++) {
            if (i > 0 && (s.length() - i) % 3 == 0) {
                out.append(' ');
            }
            out.append(s.charAt(i));
        }
        return out.toString();
    }

    /** The Functions tab's second page: wireless charging settings and last second's numbers. */
    private void drawChargePage(int c, int dim) {
        String title = Lang.tr("sc.fieldgui.charge.title");
        fitCentered(title, 32, c);
        int tw = Math.min(CW - 14, fontRendererObj.getStringWidth(title));
        TextFitSC.help(fontRendererObj, 8 + (CW + tw) / 2 + 3, 31, Lang.tr("sc.fieldgui.charge.help"), guiLeft, guiTop);
        int reserveEu = (int) ((long) field.getMaxEnergyStored() * field.getChargeReserve() / 100);
        fit(Lang.tr("sc.fieldgui.charge.reserve", field.getChargeReserve(), reserveEu), 82, 98, CW - 74, c);
        int boosters = Math.min(com.sc.machine.UpgradeType.MAX_CHARGE_BOOSTERS,
                field.upgradeCount(com.sc.machine.UpgradeType.CHARGE_BOOSTER));
        fit(Lang.tr(field.getChargeBelow() >= 100 ? "sc.fieldgui.charge.below.all" : "sc.fieldgui.charge.below", field.getChargeBelow()),
                82, 122, CW - 74, c);
        fit(Lang.tr(field.chargesEachAtFullRate() ? "sc.fieldgui.charge.rate.each" : "sc.fieldgui.charge.rate", field.chargeRate()), 8, 166, CW, c);
        fit(Lang.tr("sc.fieldgui.charge.boosters", boosters, com.sc.machine.UpgradeType.MAX_CHARGE_BOOSTERS), 8, 176, CW, dim);
        boolean on = field.has(TileEntityFieldGeneratorSC.F_CHARGE) && field.isActive();
        fit(on ? Lang.tr("sc.fieldgui.charge.now", field.getChargedLastSecond(), field.getPlayersLastSecond())
                : Lang.tr("sc.fieldgui.charge.off"), 8, 188, CW - 96, on ? GuiHoloSC.OK : GuiHoloSC.BAD);
    }

    private void legend(int x, int y, int color, String text) {
        drawRect(x, y + 1, x + 6, y + 7, color);
        fit(text, x + 9, y, SCREEN_R - 3 - x - 9, GuiHoloSC.VALUE);
    }

    /** Which ready colour this is, or -1. */
    private static int presetIndex(int rgb) {
        for (int i = 0; i < TileEntityFieldGeneratorSC.PRESETS.length; i++) {
            if (TileEntityFieldGeneratorSC.PRESETS[i] == (rgb & 0xFFFFFF)) {
                return i;
            }
        }
        return -1;
    }

    /** A small label (5/8 size) fitted into `maxW`, centred on x when `centre`. */
    private void small(String text, int x, int y, int maxW, int color, boolean centre) {
        float k = Math.min(0.625F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslatef(centre ? x - fontRendererObj.getStringWidth(text) * k / 2 : x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /**
     * A button in the holo style: dark, cyan-edged, brighter under the mouse. A switch's caption
     * starting "§a" / "§7" gets a lamp - green on, grey off - and the colour code is dropped.
     */
    static class HoloButton extends TextFitSC.Button {
        HoloButton(int id, int x, int y, int w, int h, String text) {
            super(id, x, y, w, h, text);
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            field_146123_n = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            boolean hover = field_146123_n && enabled;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, hover ? 0xFF6EE6FF : 0xFF2A6A8A);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, enabled ? 0xFF0E3A50 : 0xFF0A1C26);
            String text = displayString;
            int lamp = 0;
            if (text.startsWith("\u00a7a")) {
                lamp = 0xFF5AE66E;
                text = text.substring(2);
            } else if (text.startsWith("\u00a77")) {
                lamp = 0xFF3A4450;
                text = text.substring(2);
            }
            int tx = xPosition + 3, tw = width - 6;
            if (lamp != 0) {
                drawRect(xPosition + 3, yPosition + height / 2 - 2, xPosition + 7, yPosition + height / 2 + 2, lamp);
                tx += 6;
                tw -= 6;
            }
            int color = !enabled ? 0x465A6E : hover ? 0xFFFFA0 : lamp == 0xFF3A4450 ? 0x8AA0B4 : 0xE6F0FA;
            // shrink to fit rather than cut (down to 0.4 - the redstone button's "без сигнала" in 26 px)
            int sw = mc.fontRenderer.getStringWidth(text);
            float k = Math.max(0.4F, Math.min(1F, tw / (float) Math.max(1, sw)));
            GL11.glPushMatrix();
            GL11.glTranslatef(tx + (tw - sw * k) / 2F, yPosition + (height - 8 * k) / 2F, 0F);
            GL11.glScalef(k, k, 1F);
            mc.fontRenderer.drawString(text, 0, 0, color);
            GL11.glPopMatrix();
        }
    }

    /** A tab in the holo style: a small icon and the short name; the current one lit. */
    static class HoloTab extends TextFitSC.Tab {
        boolean current;
        private final net.minecraft.item.ItemStack icon;
        private final String full;

        HoloTab(int id, int x, int y, int w, int h, net.minecraft.item.ItemStack icon, String shortName, String fullName) {
            super(id, x, y, w, h, icon, shortName, fullName);
            this.icon = icon;
            this.full = fullName;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean hover = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, current ? 0xFF6EE6FF : hover ? 0xFF3CAADC : 0xFF1E3444);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - (current ? 0 : 1),
                    current ? 0xFF0E3A50 : 0xFF0A1218);
            if (icon != null) {
                GL11.glPushMatrix();
                GL11.glTranslatef(xPosition + 2, yPosition + 2.5F, 0F);
                GL11.glScalef(0.5F, 0.5F, 1F);
                net.minecraft.client.renderer.RenderHelper.enableGUIStandardItemLighting();
                GL11.glEnable(org.lwjgl.opengl.GL12.GL_RESCALE_NORMAL);
                itemRender.renderItemAndEffectIntoGUI(mc.fontRenderer, mc.getTextureManager(), icon, 0, 0);
                net.minecraft.client.renderer.RenderHelper.disableStandardItemLighting();
                GL11.glDisable(GL11.GL_LIGHTING);
                GL11.glPopMatrix();
                GL11.glColor4f(1F, 1F, 1F, 1F);
            }
            String text = displayString;
            float k = Math.min(0.625F, (width - 13) / (float) Math.max(1, mc.fontRenderer.getStringWidth(text)));
            GL11.glPushMatrix();
            GL11.glTranslatef(xPosition + 11, yPosition + (height - 8 * k) / 2F + 0.5F, 0F);
            GL11.glScalef(k, k, 1F);
            mc.fontRenderer.drawString(text, 0, 0, current ? 0xE6F0FA : hover ? 0xFFFFA0 : 0x6AA8C8);
            GL11.glPopMatrix();
            if (hover) {
                TextFitSC.hover(xPosition, yPosition, width, height, full);
            }
        }
    }

    private static final net.minecraft.client.renderer.entity.RenderItem itemRender = new net.minecraft.client.renderer.entity.RenderItem();

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    private void fitCentered(String text, int y, int color) {
        TextFitSC.drawCentered(fontRendererObj, text, 8, y, CW, color, false, guiLeft, guiTop);
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
        List<int[]> zone = field.zoneNodes();
        String key = field.getMode() + "/" + field.getRange() + "/" + field.getHeight() + "/" + nodes.size() + "/" + hash
                + "/" + (zone.isEmpty() ? "" : zone.get(0)[0] + "," + zone.get(0)[1] + "," + zone.get(0)[2] + "," + zone.size());
        int n = MAP / CELL;
        if (!key.equals(mapKey)) {
            mapKey = key;
            AxisAlignedBB b = FieldShapeSC.bounds(field.getMode(), zone, field.getRange(), field.getHeight());
            double size = Math.max(b.maxX - b.minX, b.maxZ - b.minZ) + 4;
            double cx = (b.minX + b.maxX) / 2, cz = (b.minZ + b.maxZ) / 2;
            mapBounds = AxisAlignedBB.getBoundingBox(cx - size / 2, 0, cz - size / 2, cx + size / 2, 0, cz + size / 2);
            double y = (zone.isEmpty() ? field.yCoord : zone.get(0)[1]) + 0.5;
            mapCells = new boolean[n][n];
            for (int i = 0; i < n; i++) {
                for (int j = 0; j < n; j++) {
                    double wx = mapBounds.minX + (i + 0.5) * size / n, wz = mapBounds.minZ + (j + 0.5) * size / n;
                    mapCells[i][j] = FieldShapeSC.contains(field.getMode(), zone, field.getRange(), field.getHeight(), wx, y, wz);
                }
            }
        }
        float[] c = field.rgbF(TileEntityFieldGeneratorSC.RGB_SHELL);
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
        List<String> powerTip = power.tooltip(mouseX - guiLeft, mouseY - guiTop);
        if (powerTip == null) {
            powerTip = battery.tooltip(mouseX - guiLeft, mouseY - guiTop, field.getStackInSlot(TileEntityFieldGeneratorSC.SLOT_BATTERY));
        }
        if (powerTip != null) {
            tip.addAll(powerTip);
        }
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
            } else if (b.id == ContainerFieldGeneratorSC.BTN_BELOW_MINUS || b.id == ContainerFieldGeneratorSC.BTN_BELOW_PLUS) {
                key = "sc.fieldgui.charge.below.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_OUTLINE) {
                key = "sc.fieldzone.outline.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_ANIM) {
                key = "sc.fieldzone.anim.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_BRIGHT) {
                key = "sc.fieldzone.bright.desc";
            } else if (b.id >= Z_HEIGHT && b.id < Z_HEIGHT + 4) {
                key = "sc.fieldzone.height.desc";
            } else if (b.id >= Z_OFFX && b.id < Z_SHAPE) {
                key = "sc.fieldzone.offset.desc";
            } else if (b.id == Z_SHAPE) {
                key = "sc.fieldgui.shape.desc";
            } else if (b.id == Z_ANCHOR) {
                key = "sc.fieldzone.anchor.desc";
            } else if (b.id == Z_PREVIEW || b.id == Z_APPLY || b.id == Z_CANCEL) {
                key = "sc.fieldzone.preview.desc";
            } else if (b.id == Z_TARGET || b.id >= Z_PRESET && b.id < TAB_BASE) {
                key = "sc.fieldzone.target.desc";
            } else if (b.id >= ContainerFieldGeneratorSC.BTN_FLAG_BASE
                    && b.id < ContainerFieldGeneratorSC.BTN_FLAG_BASE + ContainerFieldGeneratorSC.FLAG_COUNT) {
                key = "sc.fieldgui.flag." + (1 << (b.id - ContainerFieldGeneratorSC.BTN_FLAG_BASE)) + ".desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_MODE) {
                key = "sc.fieldgui.shape.desc";
            } else if (b.id == ContainerFieldGeneratorSC.BTN_FILTER) {
                key = "sc.fieldgui.filter.desc";
            } else if (b.id >= REMOVE_BASE) {
                key = "sc.fieldgui.access.remove";
            }
            if (key != null) {
                tip.addAll(fontRendererObj.listFormattedStringToWidth(Lang.tr(key), 200));
            }
        }
        if (mouseX >= guiLeft + ENERGY_X && mouseX < guiLeft + ENERGY_X + ENERGY_W
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
