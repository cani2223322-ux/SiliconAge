package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.sc.handler.QuarryNetSC;
import com.sc.item.ItemQuarryModuleSC;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityQuarrySC;

import cpw.mods.fml.client.config.GuiSlider;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.client.renderer.entity.RenderItem;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The quarry's screen, six tabs (like the field generator's, in the machines' steel colours):
 * - Quarry: status, layer and progress, cost and speed, forecast, totals, experience, start /
 *   pause / reset, redstone, power mode, the tanks;
 * - Area: size, offset, bottom, shape, what's left behind - and how the area is shown in the
 *   world: when, dashes, scan plane, ore outlines, brightness, colours (palette + R/G/B);
 * - Map: the area from above - what's done on this layer, the drill, the ore found, you;
 * - Output: filter (9 examples, mode), output side, the 27-slot buffer;
 * - Functions: what each module adds, switched on / off, and the power mode;
 * - Upgrades: 8 module slots, the drill head, scanner and area card.
 * Buttons go to the server through QuarryNetSC.
 */
public class GuiQuarrySC extends GuiContainer {

    private static final int W = 248, H = 240;
    private static final int TAB_BASE = 1000;
    private static final int ENERGY_X = 226, ENERGY_Y = 30, ENERGY_W = 12, ENERGY_H = 80;
    private static final int MAP_X = 8, MAP_Y = 30, MAP = 150;
    private static final String[] TABS = {"quarry", "area", "map", "output", "functions", "upgrades"};

    /** The quarry whose screen is open (the renderer shows its area "while the menu is open"). */
    public static int[] openAt;

    private static int tab;
    private static boolean targetPlane;

    private final TileEntityQuarrySC quarry;
    private final ContainerQuarrySC container;

    public GuiQuarrySC(InventoryPlayer inv, TileEntityQuarrySC quarry) {
        super(new ContainerQuarrySC(inv, quarry));
        this.quarry = quarry;
        this.container = (ContainerQuarrySC) inventorySlots;
        xSize = W;
        ySize = H;
    }

    // ------------------------------------------------------------------ layout

    private static final int B_RUN = 1, B_RESET = 2, B_XP = 3, B_REDSTONE = 4, B_POWER = 5, B_SHAPE = 10, B_REPLACE = 11,
            B_SHOW = 20, B_DASH = 21, B_PLANE = 22, B_ORES = 23, B_BRIGHT = 24, B_TARGET = 25, B_SWATCH = 30, B_SLIDER = 40,
            B_SCAN = 50, B_FILTER = 60, B_OUTSIDE = 61, B_FLAG = 100, B_FORTUNE_DOWN = 130, B_FORTUNE_UP = 131, B_STEP = 200;

    @Override
    public void initGui() {
        super.initGui();
        openAt = new int[]{quarry.xCoord, quarry.yCoord, quarry.zCoord};
        buttonList.clear();
        int[] widths = new int[TABS.length];
        int text = 0;
        for (int i = 0; i < TABS.length; i++) {
            widths[i] = fontRendererObj.getStringWidth(Lang.tr("sc.quarrygui.tab." + TABS[i]));
            text += widths[i];
        }
        int spare = Math.max(0, W - 16 - text - 2 * TABS.length) / TABS.length;
        for (int i = 0, x = guiLeft + 8; i < TABS.length; i++) {
            int w = i == TABS.length - 1 ? guiLeft + W - 8 - x : widths[i] + spare;
            GuiButton b = new GuiButton(TAB_BASE + i, x, guiTop + 6, w, 18, Lang.tr("sc.quarrygui.tab." + TABS[i]));
            b.enabled = i != tab;
            buttonList.add(b);
            x += w + 2;
        }
        int x = guiLeft + 8, y = guiTop;
        switch (tab) {
            case 0:
                buttonList.add(new GuiButton(B_RUN, x, y + 118, 72, 20, ""));
                buttonList.add(new GuiButton(B_RESET, x + 76, y + 118, 72, 20, Lang.tr("sc.quarrygui.reset")));
                buttonList.add(new GuiButton(B_XP, x + 152, y + 118, 80, 20, ""));
                buttonList.add(new GuiButton(B_REDSTONE, x, y + 142, 114, 20, ""));
                buttonList.add(new GuiButton(B_POWER, x + 118, y + 142, 114, 20, ""));
                break;
            case 1: {
                String[] rows = {"sizex", "sizez", "offx", "offz", "bottom"};
                int[] steps = {-16, -1, 1, 16};
                for (int r = 0; r < rows.length; r++) {
                    for (int s = 0; s < 4; s++) {
                        int id = B_STEP + r * 10 + s;
                        String label = (steps[s] > 0 ? "+" : "") + steps[s];
                        buttonList.add(new GuiButton(id, x + s * 28, y + 40 + r * 30, 27, 16, label));
                    }
                }
                buttonList.add(new GuiButton(B_SHAPE, x, y + 190, 112, 16, ""));
                buttonList.add(new GuiButton(B_REPLACE, x, y + 210, 112, 16, ""));
                int rx = guiLeft + 128;
                buttonList.add(new GuiButton(B_SHOW, rx, y + 30, 112, 16, ""));
                buttonList.add(new GuiButton(B_DASH, rx, y + 50, 112, 16, ""));
                buttonList.add(new GuiButton(B_PLANE, rx, y + 70, 112, 16, ""));
                buttonList.add(new GuiButton(B_ORES, rx, y + 90, 112, 16, ""));
                buttonList.add(new GuiButton(B_BRIGHT, rx, y + 110, 112, 16, ""));
                buttonList.add(new GuiButton(B_TARGET, rx, y + 130, 92, 16, ""));
                for (int i = 0; i < TileEntityQuarrySC.PALETTE.length; i++) {
                    buttonList.add(new Swatch(B_SWATCH + i, rx + i * 14, y + 150, TileEntityQuarrySC.PALETTE[i]));
                }
                int c = targetPlane ? quarry.getColorPlane() : quarry.getColorFrame();
                String[] names = {"R", "G", "B"};
                for (int i = 0; i < 3; i++) {
                    int v = c >> (16 - 8 * i) & 0xFF;
                    buttonList.add(new GuiSlider(B_SLIDER + i, rx, y + 168 + i * 20, 112, 16, names[i] + ": ", "", 0, 255, v, false, true));
                }
                break;
            }
            case 2:
                buttonList.add(new GuiButton(B_SCAN, guiLeft + 166, y + 208, 74, 20, Lang.tr("sc.quarrygui.scan")));
                break;
            case 3:
                buttonList.add(new GuiButton(B_FILTER, x, y + 28, 114, 16, ""));
                buttonList.add(new GuiButton(B_OUTSIDE, x + 118, y + 28, 114, 16, ""));
                break;
            case 4:
                buttonList.add(new GuiButton(B_POWER, x, y + 28, 150, 16, ""));
                for (int i = 0; i < TileEntityQuarrySC.FLAG_COUNT; i++) {
                    int col = i / 9, row = i % 9;
                    int bw = i == 1 ? 80 : 114;
                    buttonList.add(new GuiButton(B_FLAG + i, x + col * 118, y + 50 + row * 18, bw, 16, ""));
                }
                buttonList.add(new GuiButton(B_FORTUNE_DOWN, x + 82, y + 68, 15, 16, "-"));
                buttonList.add(new GuiButton(B_FORTUNE_UP, x + 99, y + 68, 15, 16, "+"));
                break;
            default:
        }
        container.setShown(tab == 3, tab == 3, tab == 5, tab == 3 || tab == 5);
        refresh();
    }

    @Override
    public void onGuiClosed() {
        super.onGuiClosed();
        openAt = null;
        sendSliders();
    }

    // ------------------------------------------------------------------ captions

    private static String onOff(boolean on) {
        return Lang.tr(on ? "sc.fieldgui.on" : "sc.fieldgui.off");
    }

    /** Module a switch belongs to, or null for the built-in ones. */
    private static ItemQuarryModuleSC.Kind moduleOf(int flagIndex) {
        switch (flagIndex) {
            case 0: return ItemQuarryModuleSC.Kind.SPEED;
            case 1: return ItemQuarryModuleSC.Kind.FORTUNE;
            case 2: return ItemQuarryModuleSC.Kind.SILK;
            case 3: return ItemQuarryModuleSC.Kind.CRUSH;
            case 4: return ItemQuarryModuleSC.Kind.WASH;
            case 5:
            case 6: return ItemQuarryModuleSC.Kind.PUMP;
            case 7:
            case 8: return ItemQuarryModuleSC.Kind.MAGNET;
            case 9: return ItemQuarryModuleSC.Kind.RADIUS;
            case 10: return ItemQuarryModuleSC.Kind.SILENT;
            case 11: return ItemQuarryModuleSC.Kind.AUTOSTOP;
            default: return null;
        }
    }

    private void refresh() {
        boolean may = quarry.allowed(mc.thePlayer);
        boolean card = quarry.usesCard();
        for (Object o : buttonList) {
            GuiButton b = (GuiButton) o;
            int id = b.id;
            if (id >= TAB_BASE) {
                continue;
            }
            b.enabled = may;
            if (id == B_RUN) {
                b.displayString = Lang.tr(quarry.isRunning() ? "sc.quarrygui.pause" : "sc.quarrygui.start");
            } else if (id == B_XP) {
                b.displayString = Lang.tr("sc.quarrygui.xp", quarry.getXp());
                b.enabled = may && quarry.getXp() > 0;
            } else if (id == B_REDSTONE) {
                b.displayString = Lang.tr("sc.fieldgui.redstone", Lang.tr("sc.fieldgui.redstone." + quarry.getRedstone()));
            } else if (id == B_POWER) {
                b.displayString = Lang.tr("sc.quarrygui.power", Lang.tr("sc.quarrygui.power." + quarry.getPowerMode()));
            } else if (id >= B_STEP) {
                b.enabled = may && !card;
            } else if (id == B_SHAPE) {
                b.displayString = Lang.tr("sc.quarrygui.shape", Lang.tr("sc.quarrygui.shape." + quarry.getShape()));
                b.enabled = may && !card;
            } else if (id == B_REPLACE) {
                b.displayString = Lang.tr("sc.quarrygui.replace", Lang.tr("sc.quarrygui.replace." + quarry.getReplace()));
            } else if (id == B_SHOW) {
                b.displayString = Lang.tr("sc.quarrygui.show", Lang.tr("sc.quarrygui.show." + quarry.getShow()));
            } else if (id == B_DASH) {
                b.displayString = Lang.tr("sc.quarrygui.dash", onOff((quarry.getVflags() & TileEntityQuarrySC.V_DASH) != 0));
            } else if (id == B_PLANE) {
                b.displayString = Lang.tr("sc.quarrygui.plane", onOff((quarry.getVflags() & TileEntityQuarrySC.V_PLANE) != 0));
            } else if (id == B_ORES) {
                b.displayString = Lang.tr("sc.quarrygui.ores", onOff((quarry.getVflags() & TileEntityQuarrySC.V_ORES) != 0));
            } else if (id == B_BRIGHT) {
                b.displayString = Lang.tr("sc.quarrygui.bright", quarry.getBrightness() * 25);
            } else if (id == B_TARGET) {
                b.displayString = Lang.tr(targetPlane ? "sc.quarrygui.target.plane" : "sc.quarrygui.target.frame");
                b.enabled = true;
            } else if (id == B_FILTER) {
                b.displayString = Lang.tr("sc.quarrygui.filter", Lang.tr("sc.quarrygui.filter." + quarry.getFilterMode()));
            } else if (id == B_OUTSIDE) {
                int side = quarry.getOutSide();
                b.displayString = Lang.tr("sc.quarrygui.out", Lang.tr("sc.quarrygui.side." + (side + 1)));
            } else if (id >= B_FLAG && id < B_FLAG + TileEntityQuarrySC.FLAG_COUNT) {
                int i = id - B_FLAG;
                ItemQuarryModuleSC.Kind k = moduleOf(i);
                boolean on = (quarry.getFlags() & (1 << i)) != 0;
                String name = Lang.tr("sc.quarrygui.flag." + i);
                if (i == 1 && k != null && quarry.moduleCount(k) > 0) {
                    name = Lang.tr("sc.quarrygui.flag.fortune", roman(Math.min(quarry.getFortuneLevel(), quarry.moduleCount(k))));
                }
                if (k != null && quarry.moduleCount(k) == 0) {
                    b.displayString = "§8" + name + ": " + Lang.tr("sc.quarrygui.needmodule");
                    b.enabled = false;
                } else {
                    b.displayString = (on ? "§a" : "§7") + name + ": " + onOff(on);
                }
            } else if (id == B_FORTUNE_DOWN || id == B_FORTUNE_UP) {
                b.enabled = may && quarry.moduleCount(ItemQuarryModuleSC.Kind.FORTUNE) > 1;
            } else if (id == B_SCAN) {
                b.enabled = may && quarry.hasScanner();
            }
        }
    }

    private static String roman(int n) {
        return new String[]{"0", "I", "II", "III", "IV", "V"}[Math.max(0, Math.min(5, n))];
    }

    @Override
    public void updateScreen() {
        super.updateScreen();
        refresh();
    }

    // ------------------------------------------------------------------ input

    @Override
    protected void actionPerformed(GuiButton b) {
        int id = b.id;
        if (id >= TAB_BASE) {
            sendSliders();
            tab = id - TAB_BASE;
            initGui();
            return;
        }
        if (id >= B_STEP) {
            int row = (id - B_STEP) / 10, s = (id - B_STEP) % 10;
            int[] steps = {-16, -1, 1, 16};
            int[] actions = {TileEntityQuarrySC.A_SIZE_X, TileEntityQuarrySC.A_SIZE_Z, TileEntityQuarrySC.A_OFF_X,
                    TileEntityQuarrySC.A_OFF_Z, TileEntityQuarrySC.A_BOTTOM};
            QuarryNetSC.send(quarry, actions[row], steps[s]);
            return;
        }
        if (id >= B_FLAG && id < B_FLAG + TileEntityQuarrySC.FLAG_COUNT) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FLAG, id - B_FLAG);
            return;
        }
        if (id >= B_SWATCH && id < B_SWATCH + TileEntityQuarrySC.PALETTE.length) {
            int c = TileEntityQuarrySC.PALETTE[id - B_SWATCH];
            QuarryNetSC.send(quarry, targetPlane ? TileEntityQuarrySC.A_COLOR_PLANE : TileEntityQuarrySC.A_COLOR_FRAME, c);
            setSliders(c);
            return;
        }
        switch (id) {
            case B_RUN: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_RUN, 0); break;
            case B_RESET: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_RESET, 0); break;
            case B_XP: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_XP, 0); break;
            case B_REDSTONE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_REDSTONE, 0); break;
            case B_POWER: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_POWER, 0); break;
            case B_SHAPE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_SHAPE, 0); break;
            case B_REPLACE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_REPLACE, 0); break;
            case B_SHOW: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_SHOW, 0); break;
            case B_DASH: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_VFLAG, TileEntityQuarrySC.V_DASH); break;
            case B_PLANE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_VFLAG, TileEntityQuarrySC.V_PLANE); break;
            case B_ORES: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_VFLAG, TileEntityQuarrySC.V_ORES); break;
            case B_BRIGHT: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_BRIGHT, 0); break;
            case B_TARGET:
                sendSliders();
                targetPlane = !targetPlane;
                setSliders(targetPlane ? quarry.getColorPlane() : quarry.getColorFrame());
                break;
            case B_SCAN: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_SCAN, 0); break;
            case B_FILTER: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FILTER_MODE, 0); break;
            case B_OUTSIDE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_OUT_SIDE, 0); break;
            case B_FORTUNE_DOWN: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FORTUNE, -1); break;
            case B_FORTUNE_UP: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FORTUNE, 1); break;
            default:
        }
    }

    private GuiSlider slider(int i) {
        for (Object o : buttonList) {
            if (o instanceof GuiSlider && ((GuiButton) o).id == B_SLIDER + i) {
                return (GuiSlider) o;
            }
        }
        return null;
    }

    private int sliderColor() {
        int c = 0;
        for (int i = 0; i < 3; i++) {
            GuiSlider s = slider(i);
            if (s == null) {
                return -1;
            }
            c |= Math.max(0, Math.min(255, s.getValueInt())) << (16 - 8 * i);
        }
        return c;
    }

    private void setSliders(int c) {
        for (int i = 0; i < 3; i++) {
            GuiSlider s = slider(i);
            if (s != null) {
                s.setValue(c >> (16 - 8 * i) & 0xFF);
                s.updateSlider();
            }
        }
        lastSent = c;
    }

    private int lastSent = -1;

    /** The sliders' colour goes to the server when it changes (checked each frame, sent at most every few ticks). */
    private void sendSliders() {
        int c = sliderColor();
        if (c < 0 || c == lastSent || !quarry.allowed(mc.thePlayer)) {
            return;
        }
        int now = targetPlane ? quarry.getColorPlane() : quarry.getColorFrame();
        if (c != now) {
            QuarryNetSC.send(quarry, targetPlane ? TileEntityQuarrySC.A_COLOR_PLANE : TileEntityQuarrySC.A_COLOR_FRAME, c);
        }
        lastSent = c;
    }

    private int sliderTick;

    @Override
    protected void mouseMovedOrUp(int x, int y, int button) {
        super.mouseMovedOrUp(x, y, button);
        if (button == 0 && tab == 1) {
            sendSliders();
        }
    }

    // ------------------------------------------------------------------ drawing

    private void panel(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, GuiGaugeSC.OUTLINE);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, GuiGaugeSC.PANEL);
        drawRect(x + 1, y + 1, x + w - 2, y + 2, GuiGaugeSC.BEVEL_LIGHT);
        drawRect(x + 1, y + 1, x + 2, y + h - 2, GuiGaugeSC.BEVEL_LIGHT);
        drawRect(x + 2, y + h - 2, x + w - 1, y + h - 1, GuiGaugeSC.BEVEL_DARK);
        drawRect(x + w - 2, y + 2, x + w - 1, y + h - 1, GuiGaugeSC.BEVEL_DARK);
    }

    private void inset(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF343C48);
        drawRect(x + 1, y + 1, x + w, y + h, 0xFFECF1F7);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF707A88);
    }

    private void pockets(int x, int y, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                inset(x - 1 + c * 18, y - 1 + r * 18, 18, 18);
            }
        }
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        panel(x, y, W, H);
        if (tab == 0) {
            inset(x + ENERGY_X - 1, y + ENERGY_Y - 1, ENERGY_W + 2, ENERGY_H + 2);
            float f = (float) quarry.getEnergyStored() / Math.max(1, quarry.getMaxEnergyStored());
            int h = (int) (ENERGY_H * Math.min(1F, f));
            drawRect(x + ENERGY_X, y + ENERGY_Y + ENERGY_H - h, x + ENERGY_X + ENERGY_W, y + ENERGY_Y + ENERGY_H, 0xFFD02020);
            drawRect(x + ENERGY_X, y + ENERGY_Y + ENERGY_H - h, x + ENERGY_X + 3, y + ENERGY_Y + ENERGY_H, 0xFFFF6060);
        } else if (tab == 1) {
            int c = targetPlane ? quarry.getColorPlane() : quarry.getColorFrame();
            int px = x + 128 + 96, py = y + 130;
            drawRect(px - 1, py - 1, px + 17, py + 17, GuiGaugeSC.OUTLINE);
            drawRect(px, py, px + 16, py + 16, 0xFF000000 | c);
        } else if (tab == 2) {
            inset(x + MAP_X - 1, y + MAP_Y - 1, MAP + 2, MAP + 2);
            drawMap(x + MAP_X, y + MAP_Y);
        } else if (tab == 3) {
            pockets(x + ContainerQuarrySC.FILTER_X, y + ContainerQuarrySC.FILTER_Y, 9, 1);
            pockets(x + ContainerQuarrySC.BUFFER_X, y + ContainerQuarrySC.BUFFER_Y, 9, 3);
        } else if (tab == 5) {
            pockets(x + ContainerQuarrySC.UPGRADE_X, y + ContainerQuarrySC.UPGRADE_Y, 4, 2);
            pockets(x + ContainerQuarrySC.HEAD_X, y + ContainerQuarrySC.HEAD_Y, 1, 1);
            pockets(x + ContainerQuarrySC.HEAD_X, y + ContainerQuarrySC.SCANNER_Y, 1, 1);
            pockets(x + ContainerQuarrySC.HEAD_X, y + ContainerQuarrySC.CARD_Y, 1, 1);
        }
        if (tab == 3 || tab == 5) {
            pockets(x + ContainerQuarrySC.INV_X, y + ContainerQuarrySC.INV_Y, 9, 3);
            pockets(x + ContainerQuarrySC.INV_X, y + ContainerQuarrySC.INV_Y + 58, 9, 1);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The area from above: the layer's done part, the drill, the ore found, the quarry, you. */
    private void drawMap(int x0, int y0) {
        int[] a = quarry.area();
        if (a == null) {
            return;
        }
        int w = a[2] - a[0] + 1, d = a[3] - a[1] + 1;
        float cell = Math.min((float) MAP / w, (float) MAP / d);
        int ox = x0 + (int) ((MAP - w * cell) / 2), oz = y0 + (int) ((MAP - d * cell) / 2);
        drawRect(ox, oz, ox + (int) (w * cell), oz + (int) (d * cell), 0xFF3C4450);
        // done rows of the current layer
        int doneCells = Math.max(0, quarry.getCursor());
        int doneRows = doneCells / w;
        drawRect(ox, oz, ox + (int) (w * cell), oz + (int) (doneRows * cell), 0xFF1E232B);
        drawRect(ox, oz + (int) (doneRows * cell), ox + (int) ((doneCells % w) * cell), oz + (int) ((doneRows + 1) * cell), 0xFF1E232B);
        int frame = 0xFF000000 | quarry.getColorFrame();
        drawRect(ox, oz, ox + (int) (w * cell), oz + 1, frame);
        drawRect(ox, oz + (int) (d * cell) - 1, ox + (int) (w * cell), oz + (int) (d * cell), frame);
        drawRect(ox, oz, ox + 1, oz + (int) (d * cell), frame);
        drawRect(ox + (int) (w * cell) - 1, oz, ox + (int) (w * cell), oz + (int) (d * cell), frame);
        for (int[] o : quarry.getOres()) {
            int px = ox + (int) ((o[0] - a[0] + 0.5F) * cell), pz = oz + (int) ((o[2] - a[1] + 0.5F) * cell);
            drawRect(px - 1, pz - 1, px + 1, pz + 1, 0xFF000000 | o[3]);
        }
        int bx = ox + (int) ((doneCells % w + 0.5F) * cell), bz = oz + (int) ((doneRows + 0.5F) * cell);
        drawRect(bx - 2, bz - 2, bx + 2, bz + 2, 0xFF000000 | quarry.getColorPlane());
        int qx = ox + (int) ((quarry.xCoord - a[0] + 0.5F) * cell), qz = oz + (int) ((quarry.zCoord - a[1] + 0.5F) * cell);
        drawRect(qx - 2, qz - 2, qx + 2, qz + 2, 0xFFFFE040);
        int mx = ox + (int) ((mc.thePlayer.posX - a[0]) * cell), mz = oz + (int) ((mc.thePlayer.posZ - a[1]) * cell);
        if (mx >= x0 && mx < x0 + MAP && mz >= y0 && mz < y0 + MAP) {
            drawRect(mx - 2, mz - 2, mx + 2, mz + 2, 0xFFFF4040);
        }
    }

    private void legend(int x, int y, int color, String text) {
        drawRect(x, y + 1, x + 6, y + 7, color);
        fontRendererObj.drawString(text, x + 9, y, 0x404040);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        int c = 0x303844, dim = 0x5A6472;
        int[] a = quarry.area();
        switch (tab) {
            case 0: {
                TileEntityQuarrySC.Status s = quarry.getStatus();
                int sc = s == TileEntityQuarrySC.Status.RUNNING ? 0x2E7D32 : s == TileEntityQuarrySC.Status.DONE ? 0x2A62A8
                        : s == TileEntityQuarrySC.Status.PAUSED ? 0x606060 : 0xA02020;
                fontRendererObj.drawString(Lang.tr("sc.quarry.status." + s.name().toLowerCase(java.util.Locale.ROOT)), 8, 30, sc);
                if (a != null) {
                    long total = (long) (a[4] - a[5] + 1) * (a[2] - a[0] + 1) * (a[3] - a[1] + 1);
                    long left = Math.min(total, container.blocksLeftClient);
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.layer", quarry.getLayerY(), a[5],
                            total == 0 ? 100 : (int) ((total - left) * 100 / total)), 8, 42, c);
                    double bps = quarry.blocksPerSecond();
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.cost", quarry.getLastCost(), String.format(java.util.Locale.ROOT, "%.1f", bps)), 8, 54, c);
                    long sec = bps <= 0 ? 0 : (long) (left / bps);
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.left", String.valueOf(left), sec / 3600, sec / 60 % 60), 8, 66, c);
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.size", a[2] - a[0] + 1, a[3] - a[1] + 1, a[4] - a[5] + 1), 8, 78, c);
                }
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.mined", String.valueOf(quarry.getMined())), 8, 90, c);
                fontRendererObj.drawString(Lang.tr("sc.fieldgui.owner", quarry.getOwner().isEmpty() ? "-" : quarry.getOwner()), 8, 102, dim);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.pump", quarry.getPumped().getFluidAmount(), quarry.getPumped().getCapacity(),
                        quarry.getPumped().getFluid() == null ? "-" : quarry.getPumped().getFluid().getLocalizedName()), 8, 170, c);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.water", quarry.getWater().getFluidAmount(), quarry.getWater().getCapacity()), 8, 182, c);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.head", headName()), 8, 194, c);
                if (!quarry.allowed(mc.thePlayer)) {
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.owneronly"), 8, 222, 0xA02020);
                }
                break;
            }
            case 1: {
                String[] rows = {Lang.tr("sc.quarrygui.sizex", quarry.getSizeX(), quarry.maxSize()),
                        Lang.tr("sc.quarrygui.sizez", quarry.getSizeZ(), quarry.maxSize()),
                        Lang.tr("sc.quarrygui.offx", quarry.getOffX()), Lang.tr("sc.quarrygui.offz", quarry.getOffZ()),
                        Lang.tr("sc.quarrygui.bottom", quarry.getBottomY())};
                for (int r = 0; r < rows.length; r++) {
                    fontRendererObj.drawString(rows[r], 8, 30 + r * 30, c);
                }
                if (quarry.usesCard()) {
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.bycard"), 8, 228, 0x2A62A8);
                }
                break;
            }
            case 2: {
                int lx = MAP_X + MAP + 8;
                fontRendererObj.drawString(Lang.tr("sc.fieldgui.map.title"), lx, 30, c);
                legend(lx, 44, 0xFFFFE040, Lang.tr("sc.quarrygui.map.quarry"));
                legend(lx, 56, 0xFF000000 | quarry.getColorPlane(), Lang.tr("sc.quarrygui.map.drill"));
                legend(lx, 68, 0xFF1E232B, Lang.tr("sc.quarrygui.map.done"));
                legend(lx, 80, 0xFFFF4040, Lang.tr("sc.fieldgui.map.you"));
                if (!quarry.hasScanner()) {
                    fontRendererObj.drawSplitString(Lang.tr("sc.quarrygui.noscanner"), lx, 96, W - lx - 6, dim);
                } else {
                    fontRendererObj.drawString(Lang.tr("sc.quarrygui.orefound"), lx, 96, c);
                    int row = 0;
                    RenderHelper.enableGUIStandardItemLighting();
                    for (Map.Entry<String, Integer> e : quarry.getOreCounts().entrySet()) {
                        if (row >= 6) {
                            break;
                        }
                        ItemStack st = stackOf(e.getKey());
                        int ry = 108 + row * 16;
                        if (st != null) {
                            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), st, lx, ry);
                        }
                        GL11.glDisable(GL11.GL_LIGHTING);
                        fontRendererObj.drawString("x" + e.getValue(), lx + 18, ry + 4, c);
                        row++;
                    }
                    RenderHelper.disableStandardItemLighting();
                }
                break;
            }
            case 3:
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.filterlabel"), ContainerQuarrySC.FILTER_X, ContainerQuarrySC.FILTER_Y - 10, c);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.buffer"), ContainerQuarrySC.BUFFER_X, ContainerQuarrySC.BUFFER_Y - 10, c);
                fontRendererObj.drawString(Lang.tr("container.inventory"), ContainerQuarrySC.INV_X, ContainerQuarrySC.INV_Y - 10, c);
                break;
            case 4: {
                int on = 0, total = 0;
                for (int i = 0; i < TileEntityQuarrySC.FLAG_COUNT; i++) {
                    ItemQuarryModuleSC.Kind k = moduleOf(i);
                    if (k == null || quarry.moduleCount(k) > 0) {
                        total++;
                        on += (quarry.getFlags() & (1 << i)) != 0 ? 1 : 0;
                    }
                }
                fontRendererObj.drawString(Lang.tr("sc.tooltip.functions", on, total), 164, 32, c);
                break;
            }
            case 5:
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.modules"), ContainerQuarrySC.UPGRADE_X, ContainerQuarrySC.UPGRADE_Y - 10, c);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.slot.head"), ContainerQuarrySC.HEAD_X - 60, ContainerQuarrySC.HEAD_Y + 4, c);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.slot.scanner"), ContainerQuarrySC.HEAD_X - 60, ContainerQuarrySC.SCANNER_Y + 4, c);
                fontRendererObj.drawString(Lang.tr("sc.quarrygui.slot.card"), ContainerQuarrySC.HEAD_X - 60, ContainerQuarrySC.CARD_Y + 4, c);
                fontRendererObj.drawSplitString(Lang.tr("sc.quarrygui.modules.hint"), 8, 112, W - 16, dim);
                fontRendererObj.drawString(Lang.tr("container.inventory"), ContainerQuarrySC.INV_X, ContainerQuarrySC.INV_Y - 10, c);
                break;
            default:
        }
    }

    private String headName() {
        ItemStack h = quarry.getStackInSlot(TileEntityQuarrySC.SLOT_HEAD);
        return h == null ? Lang.tr("sc.quarrygui.nohead") : h.getDisplayName();
    }

    private static ItemStack stackOf(String key) {
        int at = key.lastIndexOf('@');
        if (at < 0) {
            return null;
        }
        Object item = Item.itemRegistry.getObject(key.substring(0, at));
        if (!(item instanceof Item)) {
            return null;
        }
        try {
            return new ItemStack((Item) item, 1, Integer.parseInt(key.substring(at + 1)));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tab == 1 && ++sliderTick % 10 == 0 && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            sendSliders();
        }
        List<String> tip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    private List<String> tooltipAt(int mx, int my) {
        List<String> lines = new ArrayList<String>();
        if (tab == 0 && GuiGaugeSC.isOver(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mx, my)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(quarry.getEnergyStored() + " / " + quarry.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.input", quarry.inputTier().name(), quarry.inputTier().getVoltage()));
            return lines;
        }
        if (tab == 4) {
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.id >= B_FLAG && b.id < B_FLAG + TileEntityQuarrySC.FLAG_COUNT && GuiGaugeSC.isOver(b.xPosition - guiLeft,
                        b.yPosition - guiTop, b.width, b.height, mx, my)) {
                    lines.add(Lang.tr("sc.quarrygui.flag." + (b.id - B_FLAG) + ".hint"));
                    return lines;
                }
            }
        }
        return null;
    }

    /** A palette colour button. */
    private static class Swatch extends GuiButton {
        private final int color;

        Swatch(int id, int x, int y, int color) {
            super(id, x, y, 12, 12, "");
            this.color = color;
        }

        @Override
        public void drawButton(net.minecraft.client.Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, over ? 0xFFFFFFFF : 0xFF101319);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, 0xFF000000 | color);
        }
    }
}
