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

    private static final int W = 248, H = 262, TOP = ContainerQuarrySC.TOP;
    private static final int TAB_BASE = 1000;
    private static final int ENERGY_X = 226, ENERGY_Y = 30, ENERGY_W = 12, ENERGY_H = 80;
    private static final int MAP_X = 8, MAP_Y = 30, MAP = 150;
    private static final String[] TABS = {"quarry", "area", "map", "output", "functions", "upgrades", "tanks"};

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
            B_SCAN = 50, B_FILTER = 60, B_OUTSIDE = 61, B_PAGE = 62, B_OUTPAGE = 63, B_FF_MODE = 64,
            B_FF_REMOVE = 65, B_TANK_FULL = 66, B_FF_HAND = 67, B_FF_CLEAR = 68, B_TANK_SIDE = 70, B_TANK_CLEAR = 74,
            B_TANK_FILTER = 78, B_FF_DEL = 82, B_TANK_PIN = 88, B_TANK_AUTO = 92, B_WASH_CLEAR = 96, B_FVEIN = 97, B_FVEIN_RANGE = 98, B_FVEIN_FLOWING = 99, B_WASH_FEED = 69,
            B_FLAG = 100, B_FORTUNE_DOWN = 140, B_FORTUNE_UP = 141, B_STEP = 200;

    /** The Functions tab's two pages: what the modules add; then the rest (silence, auto-stop, filter, chests, chat). */
    private static final int[][] FN_PAGES = {{0, 1, 2, 3, 4, 19, 5, 6, 7, 8, 9, 18, 20, 21, 22, 23, 24, 25, 26, 27},
            {10, 11, 12, 13, 14, 15, 16, 17}};
    private static int fnPage;
    /** The Tanks tab: where the four gauges and the washing water's stand (x from the GUI's left, y in the tab area). */
    private static final int[] GAUGE_X = {8, 55, 102, 149};
    private static final int WASH_X = 203, GAUGE_Y = 38, TAB_TANKS = 6;

    @Override
    public void initGui() {
        super.initGui();
        openAt = new int[]{quarry.xCoord, quarry.yCoord, quarry.zCoord};
        buttonList.clear();
        // two rows of four tabs: an icon and a short name, the full name as the tooltip
        boolean exo = quarry.isExo();
        ItemStack[] icons = {new ItemStack(com.sc.init.ModBlocks.quarrySC, 1, quarry.getBlockMetadata()),
                exo ? new ItemStack(com.sc.init.ModItems.DRILL_HEADS.get(3)) : new ItemStack(com.sc.init.ModItems.areaCard),
                exo ? new ItemStack(com.sc.init.ModItems.oreLens, 1, 1) : new ItemStack(com.sc.init.ModItems.oreScanner),
                new ItemStack(net.minecraft.init.Blocks.chest), new ItemStack(net.minecraft.init.Blocks.lever),
                com.sc.init.ModItems.quarryModule.stackOf(ItemQuarryModuleSC.Kind.SPEED),
                new ItemStack(net.minecraft.init.Items.water_bucket)};
        if (exo && tab == TAB_TANKS) {
            tab = 0;
        }
        int tw = (W - 16 - 6) / 4;
        for (int i = 0; i < (exo ? TABS.length - 1 : TABS.length); i++) {
            String key = exo && i < 3 ? "sc.quarrygui.exo.tab" : "sc.quarrygui.tab";
            GuiButton b = new TextFitSC.Tab(TAB_BASE + i, guiLeft + 8 + (i % 4) * (tw + 2), guiTop + 5 + (i / 4) * 20, tw, 19,
                    icons[i], Lang.tr(key + "short." + TABS[i]), Lang.tr(key + "." + TABS[i]));
            b.enabled = i != tab;
            buttonList.add(b);
        }
        int x = guiLeft + 8, y = guiTop + TOP;
        switch (tab) {
            case 0:
                buttonList.add(new TextFitSC.Button(B_RUN, x, y + 118, 72, 20, ""));
                buttonList.add(new TextFitSC.Button(B_RESET, x + 76, y + 118, 72, 20, Lang.tr("sc.quarrygui.reset")));
                buttonList.add(new TextFitSC.Button(B_XP, x + 152, y + 118, 80, 20, ""));
                buttonList.add(new TextFitSC.Button(B_REDSTONE, x, y + 142, 114, 20, ""));
                buttonList.add(new TextFitSC.Button(B_POWER, x + 118, y + 142, 114, 20, ""));
                break;
            case 1: {
                String[] rows = {"sizex", "sizez", "offx", "offz", "bottom"};
                int[] steps = {-16, -1, 1, 16};
                for (int r = 0; r < (exo ? 0 : rows.length); r++) {
                    for (int s = 0; s < 4; s++) {
                        int id = B_STEP + r * 10 + s;
                        String label = (steps[s] > 0 ? "+" : "") + steps[s];
                        buttonList.add(new TextFitSC.Button(id, x + s * 28, y + 40 + r * 30, 27, 16, label));
                    }
                }
                if (!exo) {
                    buttonList.add(new TextFitSC.Button(B_SHAPE, x, y + 190, 112, 16, ""));
                    buttonList.add(new TextFitSC.Button(B_REPLACE, x, y + 210, 112, 16, ""));
                }
                int rx = guiLeft + 128;
                buttonList.add(new TextFitSC.Button(B_SHOW, rx, y + 30, 112, 16, ""));
                buttonList.add(new TextFitSC.Button(B_DASH, rx, y + 50, 112, 16, ""));
                buttonList.add(new TextFitSC.Button(B_PLANE, rx, y + 70, 112, 16, ""));

                buttonList.add(new TextFitSC.Button(B_BRIGHT, rx, y + 110, 112, 16, ""));
                buttonList.add(new TextFitSC.Button(B_TARGET, rx, y + 130, 92, 16, ""));
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
                if (!exo) {
                    buttonList.add(new TextFitSC.Button(B_SCAN, guiLeft + 166, y + 208, 74, 20, Lang.tr("sc.quarrygui.scan")));
                }
                break;
            case 3:
                buttonList.add(new TextFitSC.Button(B_FILTER, x, y + 28, 114, 16, ""));
                buttonList.add(new TextFitSC.Button(B_OUTSIDE, x + 118, y + 28, 114, 16, ""));
                break;
            case TAB_TANKS: {
                for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
                    int bx = guiLeft + GAUGE_X[i] - 2;
                    buttonList.add(new TextFitSC.Button(B_TANK_SIDE + i, bx, y + 122, 44, 11, ""));
                    buttonList.add(new TextFitSC.Button(B_TANK_CLEAR + i, bx, y + 134, 44, 11, ""));
                    buttonList.add(new TextFitSC.Button(B_TANK_FILTER + i, bx, y + 146, 44, 11, ""));
                    buttonList.add(new TextFitSC.Button(B_TANK_PIN + i, bx, y + 158, 44, 11, ""));
                    buttonList.add(new TextFitSC.Button(B_TANK_AUTO + i, bx, y + 170, 44, 11, ""));
                }
                buttonList.add(new TextFitSC.Button(B_WASH_CLEAR, guiLeft + WASH_X - 2, y + 122, 40, 11, ""));
                buttonList.add(new TextFitSC.Button(B_WASH_FEED, guiLeft + WASH_X - 2, y + 134, 40, 11, ""));
                buttonList.add(new TextFitSC.Button(B_FVEIN, guiLeft + 8, y + 185, 62, 11, ""));
                buttonList.add(new TextFitSC.Button(B_FVEIN_RANGE, guiLeft + 72, y + 185, 62, 11, ""));
                buttonList.add(new TextFitSC.Button(B_FVEIN_FLOWING, guiLeft + 136, y + 185, 62, 11, ""));
                buttonList.add(new TextFitSC.Button(B_FF_MODE, guiLeft + 8, y + 199, 114, 11, ""));
                buttonList.add(new TextFitSC.Button(B_FF_REMOVE, guiLeft + 126, y + 199, 72, 11, ""));
                buttonList.add(new TextFitSC.Button(B_FF_HAND, guiLeft + 202, y + 199, 38, 11, ""));
                for (int i = 0; i < TileEntityQuarrySC.FLUID_FILTER_MAX; i++) {
                    buttonList.add(new TextFitSC.Button(B_FF_DEL + i, guiLeft + 8 + i * 39, y + 212, 37, 11, ""));
                }
                buttonList.add(new TextFitSC.Button(B_TANK_FULL, guiLeft + 8, y + 225, 158, 12, ""));
                buttonList.add(new TextFitSC.Button(B_FF_CLEAR, guiLeft + 170, y + 225, 70, 12, ""));
                break;
            }
            case 4: {
                buttonList.add(new TextFitSC.Button(B_POWER, x, y + 28, 114, 16, ""));
                buttonList.add(new TextFitSC.Button(B_PAGE, x + 118, y + 28, 54, 16, ""));
                int[] page = FN_PAGES[fnPage];
                for (int n = 0; n < page.length; n++) {
                    int i = page[n], col = n % 2, row = n / 2;
                    int bw = i == 1 ? 80 : 114;
                    buttonList.add(new TextFitSC.Button(B_FLAG + i, x + col * 118, y + 50 + row * 18, bw, 16, ""));
                    if (i == 1) {
                        buttonList.add(new TextFitSC.Button(B_FORTUNE_DOWN, x + col * 118 + 82, y + 50 + row * 18, 15, 16, "-"));
                        buttonList.add(new TextFitSC.Button(B_FORTUNE_UP, x + col * 118 + 99, y + 50 + row * 18, 15, 16, "+"));
                    }
                }
                break;
            }
            default:
        }
        // groups: filter, buffer, modules, inventory, lenses, head / scanner / card
        boolean items = tab == 3;
        container.setShown(items, items, tab == 5, items || tab == 5 || exo && tab == 2, exo && tab == 2, !exo && tab == 5);
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
            case 18: return ItemQuarryModuleSC.Kind.TRASH;
            case 19: return ItemQuarryModuleSC.Kind.CENTRIFUGE;
            case 20: return ItemQuarryModuleSC.Kind.VEIN;
            case 21: return ItemQuarryModuleSC.Kind.DOUBLE;
            case 22: return ItemQuarryModuleSC.Kind.FLUID_GUARD;
            case 23: return ItemQuarryModuleSC.Kind.GENTLE;
            case 24: return ItemQuarryModuleSC.Kind.REPAIR;
            case 25: return ItemQuarryModuleSC.Kind.ECONOMY;
            case 26: return ItemQuarryModuleSC.Kind.STABILIZER;
            case 27: return ItemQuarryModuleSC.Kind.DEEP_SCAN;
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
            } else if (id == B_PAGE) {
                b.displayString = Lang.tr("sc.quarrygui.page", fnPage + 1, FN_PAGES.length);
                b.enabled = true;
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
                b.displayString = Lang.tr(quarry.isExo() ? "sc.quarrygui.exo.dash" : "sc.quarrygui.dash",
                        onOff((quarry.getVflags() & TileEntityQuarrySC.V_DASH) != 0));
            } else if (id == B_PLANE) {
                b.displayString = Lang.tr(quarry.isExo() ? "sc.quarrygui.exo.plane" : "sc.quarrygui.plane",
                        onOff((quarry.getVflags() & TileEntityQuarrySC.V_PLANE) != 0));
            } else if (id == B_ORES) {
                b.displayString = Lang.tr("sc.quarrygui.ores", onOff((quarry.getVflags() & TileEntityQuarrySC.V_ORES) != 0));
            } else if (id == B_BRIGHT) {
                b.displayString = Lang.tr("sc.quarrygui.bright", quarry.getBrightness() * 25);
            } else if (id == B_TARGET) {
                b.displayString = Lang.tr(targetPlane ? "sc.quarrygui.target.plane" : "sc.quarrygui.target.frame");
                b.enabled = true;
            } else if (id == B_FF_MODE) {
                b.displayString = Lang.tr("sc.quarrygui.ff", Lang.tr("sc.quarrygui.ff." + quarry.getFluidFilterMode()));
            } else if (id == B_FF_REMOVE) {
                b.displayString = Lang.tr("sc.quarrygui.ffact." + quarry.getFluidFilterRemove());
                b.enabled = may && quarry.getFluidFilterMode() != TileEntityQuarrySC.FF_OFF;
            } else if (id == B_TANK_FULL) {
                b.displayString = Lang.tr("sc.quarrygui.tankfull", Lang.tr("sc.quarrygui.tankfull." + quarry.getTankFull()));
            } else if (id >= B_TANK_SIDE && id < B_TANK_SIDE + TileEntityQuarrySC.TANKS) {
                int side = quarry.getTankSide(id - B_TANK_SIDE);
                b.displayString = Lang.tr("sc.quarrygui.tank.sidebtn", Lang.tr("sc.quarrygui.sideshort." + (side + 1)));
                b.enabled = may && id - B_TANK_SIDE < quarry.unlockedTanks();
            } else if (id >= B_TANK_CLEAR && id < B_TANK_CLEAR + TileEntityQuarrySC.TANKS) {
                int i = id - B_TANK_CLEAR;
                b.displayString = Lang.tr("sc.quarrygui.tank.clear");
                b.enabled = may && i < quarry.unlockedTanks() && quarry.getTank(i).getFluidAmount() > 0
                        && quarry.getEnergyStored() >= quarry.clearCost(i);
            } else if (id == B_FVEIN) {
                boolean has = quarry.moduleCount(ItemQuarryModuleSC.Kind.FLUID_VEIN) > 0;
                b.displayString = Lang.tr("sc.quarrygui.fvein", onOff(has && quarry.isFluidVeinOn()));
                b.enabled = may && has;
            } else if (id == B_FVEIN_RANGE) {
                b.displayString = Lang.tr("sc.quarrygui.fvein.range", quarry.fluidVeinReach());
                b.enabled = may && quarry.fluidVeinActive();
            } else if (id == B_FVEIN_FLOWING) {
                b.displayString = Lang.tr(quarry.isFluidVeinKeepFlowing() ? "sc.quarrygui.fvein.keep" : "sc.quarrygui.fvein.take");
                b.enabled = may && quarry.fluidVeinActive();
            } else if (id == B_WASH_FEED) {
                b.displayString = (quarry.isWashFromTanks() ? "§a" : "§7") + Lang.tr("sc.quarrygui.wash.feed");
            } else if (id == B_WASH_CLEAR) {
                b.displayString = Lang.tr("sc.quarrygui.tank.clear");
                b.enabled = may && quarry.getWater().getFluidAmount() > 0
                        && quarry.getEnergyStored() >= quarry.clearCost(TileEntityQuarrySC.TANKS);
            } else if (id >= B_TANK_FILTER && id < B_TANK_FILTER + TileEntityQuarrySC.TANKS) {
                int i = id - B_TANK_FILTER;
                b.displayString = Lang.tr("sc.quarrygui.tank.tofilter");
                b.enabled = may && i < quarry.unlockedTanks() && quarry.getTank(i).getFluidAmount() > 0;
            } else if (id >= B_TANK_PIN && id < B_TANK_PIN + TileEntityQuarrySC.TANKS) {
                int i = id - B_TANK_PIN;
                boolean pinned = quarry.getTankPinned(i) != null;
                b.displayString = Lang.tr(pinned ? "sc.quarrygui.tank.unpin" : "sc.quarrygui.tank.pin");
                b.enabled = may && i < quarry.unlockedTanks() && (pinned || quarry.getTank(i).getFluidAmount() > 0);
            } else if (id >= B_TANK_AUTO && id < B_TANK_AUTO + TileEntityQuarrySC.TANKS) {
                int i = id - B_TANK_AUTO;
                b.displayString = Lang.tr("sc.quarrygui.tank.auto", onOff(quarry.getTankAuto(i)));
                b.enabled = may && i < quarry.unlockedTanks();
            } else if (id >= B_FF_DEL && id < B_FF_DEL + TileEntityQuarrySC.FLUID_FILTER_MAX) {
                int i = id - B_FF_DEL;
                java.util.List<String> ff = quarry.getFluidFilter();
                b.visible = i < ff.size();
                if (i < ff.size()) {
                    net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(ff.get(i));
                    b.displayString = (f == null ? ff.get(i) : f.getLocalizedName(new net.minecraftforge.fluids.FluidStack(f, 1000))) + " \u00d7";
                }
            } else if (id == B_FF_HAND) {
                b.displayString = Lang.tr("sc.quarrygui.ff.hand");
                b.enabled = may && quarry.getFluidFilter().size() < TileEntityQuarrySC.FLUID_FILTER_MAX;
            } else if (id == B_FF_CLEAR) {
                b.displayString = Lang.tr("sc.quarrygui.ff.clear");
                b.enabled = may && !quarry.getFluidFilter().isEmpty();
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
                if (i == 24 && on) {
                    name = Lang.tr("sc.quarrygui.flag.24.active");
                }
                if (i == 1 && k != null && quarry.moduleCount(k) > 0) {
                    name = Lang.tr("sc.quarrygui.flag.fortune", roman(Math.min(quarry.getFortuneLevel(), quarry.moduleCount(k))));
                }
                if (quarry.isExo() ? notForRig(i) : rigOnly(i)) {
                    b.displayString = "§8" + name;
                    b.enabled = false;
                } else if (k != null && quarry.moduleCount(k) == 0) {
                    b.displayString = "§8" + name;        // greyed; "needs a module" is in its tooltip
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

    /** Switches the rig has no use for: silk touch, the pump, the magnet, the area, chests. */
    private static boolean notForRig(int flag) {
        return flag == 2 || flag == 5 || flag == 6 || flag == 7 || flag == 8 || flag == 9 || flag == 13
                || flag >= 20 && flag <= 24;
    }

    /** Switches only the drilling rig has. */
    private static boolean rigOnly(int flag) {
        return flag == 26 || flag == 27;
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
        if (id >= B_TANK_SIDE && id < B_TANK_SIDE + TileEntityQuarrySC.TANKS) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_SIDE, id - B_TANK_SIDE);
            return;
        }
        if (id >= B_TANK_CLEAR && id < B_TANK_CLEAR + TileEntityQuarrySC.TANKS) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_CLEAR, id - B_TANK_CLEAR);
            return;
        }
        if (id >= B_TANK_FILTER && id < B_TANK_FILTER + TileEntityQuarrySC.TANKS) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_TO_FILTER, id - B_TANK_FILTER);
            return;
        }
        if (id >= B_TANK_PIN && id < B_TANK_PIN + TileEntityQuarrySC.TANKS) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_PIN, id - B_TANK_PIN);
            return;
        }
        if (id >= B_TANK_AUTO && id < B_TANK_AUTO + TileEntityQuarrySC.TANKS) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_AUTO, id - B_TANK_AUTO);
            return;
        }
        if (id >= B_FF_DEL && id < B_FF_DEL + TileEntityQuarrySC.FLUID_FILTER_MAX) {
            QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FF_DELETE, id - B_FF_DEL);
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
            case B_WASH_CLEAR: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_CLEAR, TileEntityQuarrySC.TANKS); break;
            case B_FVEIN: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FVEIN, 0); break;
            case B_WASH_FEED: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_WASH_FEED, 0); break;
            case B_FVEIN_RANGE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FVEIN_RANGE, 0); break;
            case B_FVEIN_FLOWING: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FVEIN_FLOWING, 0); break;
            case B_FF_MODE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FF_MODE, 0); break;
            case B_FF_REMOVE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FF_REMOVE, 0); break;
            case B_TANK_FULL: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_TANK_FULL, 0); break;
            case B_FF_HAND: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FF_HAND, 0); break;
            case B_FF_CLEAR: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FF_CLEAR, 0); break;
            case B_FORTUNE_DOWN: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FORTUNE, -1); break;
            case B_FORTUNE_UP: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_FORTUNE, 1); break;
            case B_PAGE:
                fnPage = (fnPage + 1) % FN_PAGES.length;
                initGui();
                break;
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
        int x = guiLeft, y = guiTop + TOP;
        panel(x, guiTop, W, H);
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
        } else if (tab == 2 && quarry.isExo()) {
            pockets(x + ContainerQuarrySC.LENS_X, y + ContainerQuarrySC.LENS_Y, 4, 2);
            for (int i = quarry.unlockedLenses(); i < TileEntityQuarrySC.LENSES; i++) {
                lock(x + ContainerQuarrySC.LENS_X + i % 4 * 18, y + ContainerQuarrySC.LENS_Y + i / 4 * 18);
            }
        } else if (tab == 2) {
            inset(x + MAP_X - 1, y + MAP_Y - 1, MAP + 2, MAP + 2);
            drawMap(x + MAP_X, y + MAP_Y);
        } else if (tab == TAB_TANKS) {
            int cap = quarry.tankCapacity();
            for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
                GuiTankGaugeSC.draw(mc, x + GAUGE_X[i], y + GAUGE_Y, quarry.getTank(i).getFluid(), cap, quarry.getTankPinned(i),
                        i >= quarry.unlockedTanks());
            }
            GuiTankGaugeSC.draw(mc, x + WASH_X, y + GAUGE_Y, quarry.getWater().getFluid(), quarry.waterCapacity(), null, false);
            drawRect(x + 6, y + 182, x + W - 6, y + 183, 0xFF969696);
            drawRect(x + 6, y + 197, x + W - 6, y + 198, 0xFF969696);
        } else if (tab == 3) {
            pockets(x + ContainerQuarrySC.FILTER_X, y + ContainerQuarrySC.FILTER_Y, 9, 1);
            pockets(x + ContainerQuarrySC.BUFFER_X, y + ContainerQuarrySC.BUFFER_Y, 9, 3);
        } else if (tab == 5) {
            pockets(x + ContainerQuarrySC.UPGRADE_X, y + ContainerQuarrySC.UPGRADE_Y, 9, 2);
            for (int i = quarry.unlockedUpgrades(); i < TileEntityQuarrySC.UPGRADES; i++) {
                lock(x + ContainerQuarrySC.UPGRADE_X + i % 9 * 18, y + ContainerQuarrySC.UPGRADE_Y + i / 9 * 18);
            }
            if (!quarry.isExo()) {
                pockets(x + ContainerQuarrySC.HEAD_X, y + ContainerQuarrySC.TOOLS_Y, 1, 1);
                pockets(x + ContainerQuarrySC.SCANNER_X, y + ContainerQuarrySC.TOOLS_Y, 1, 1);
                pockets(x + ContainerQuarrySC.CARD_X, y + ContainerQuarrySC.TOOLS_Y, 1, 1);
            }
        }
        if (tab == 3 || tab == 5 || tab == 2 && quarry.isExo()) {
            pockets(x + ContainerQuarrySC.INV_X, y + ContainerQuarrySC.INV_Y, 9, 3);
            pockets(x + ContainerQuarrySC.INV_X, y + ContainerQuarrySC.INV_Y + 58, 9, 1);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The Tanks tab's texts: the title, each gauge's label, the washing water. */
    private void tanksTab(int c, int dim) {
        fit(Lang.tr("sc.quarrygui.tanks.title", quarry.unlockedTanks(), TileEntityQuarrySC.TANKS, quarry.tankCapacity()), 8, 27, W - 30, c);
        TextFitSC.help(fontRendererObj, W - 16, 26, Lang.tr("sc.quarrygui.tanks.help", TileEntityQuarrySC.TANK_PER_MODULE,
                TileEntityQuarrySC.CLEAR_MB_PER_EU), guiLeft, guiTop + TOP);
        for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
            net.minecraftforge.fluids.FluidStack f = quarry.getTank(i).getFluid();
            String pin = quarry.getTankPinned(i);
            String text;
            if (i >= quarry.unlockedTanks()) {
                text = Lang.tr("sc.quarrygui.tank.label.locked", i + 1, TIER_NAMES[Math.min(3, i)]);
            } else if (f != null && f.amount > 0) {
                text = Lang.tr(pin != null ? "sc.quarrygui.tank.label.pinned" : "sc.quarrygui.tank.label", i + 1, f.getLocalizedName());
            } else if (pin != null && net.minecraftforge.fluids.FluidRegistry.getFluid(pin) != null) {
                net.minecraftforge.fluids.Fluid pf = net.minecraftforge.fluids.FluidRegistry.getFluid(pin);
                text = Lang.tr("sc.quarrygui.tank.label.pinned", i + 1, pf.getLocalizedName(new net.minecraftforge.fluids.FluidStack(pf, 1000)));
            } else {
                text = Lang.tr("sc.quarrygui.tank.label.empty", i + 1);
            }
            fit(text, GAUGE_X[i] - 2, 111, 45, i >= quarry.unlockedTanks() ? dim : c);
        }
        fit(Lang.tr("sc.quarrygui.wash"), WASH_X - 2, 111, 42, 0x2A62A8);
        if (quarry.moduleCount(ItemQuarryModuleSC.Kind.FLUID_VEIN) > 0) {
            fit(Lang.tr("sc.quarrygui.fvein.count", quarry.getFluidVeinLast()), 200, 187, 42, dim);
        }
        fit(String.valueOf(quarry.getWater().getFluidAmount()) + " /", WASH_X - 2, 149, 42, dim);
        fit(Lang.tr("sc.quarrygui.wash.cap", quarry.waterCapacity()), WASH_X - 2, 157, 42, dim);
    }

    private static final String[] TIER_NAMES = {"LV", "MV", "HV", "EV"};

    /** A locked module slot: darkened, a small padlock. */
    private void lock(int x, int y) {
        drawRect(x, y, x + 16, y + 16, 0xC0101319);
        drawRect(x + 5, y + 3, x + 11, y + 4, 0xFFB8C0CB);
        drawRect(x + 5, y + 3, x + 6, y + 8, 0xFFB8C0CB);
        drawRect(x + 10, y + 3, x + 11, y + 8, 0xFFB8C0CB);
        drawRect(x + 4, y + 8, x + 12, y + 14, 0xFFD9A834);
        drawRect(x + 7, y + 10, x + 9, y + 12, 0xFF3A2A08);
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
        fit(text, x + 9, y, W - x - 15, 0x404040);
    }

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop + TOP);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        GL11.glPushMatrix();
        GL11.glTranslatef(0, TOP, 0);
        foreground();
        GL11.glPopMatrix();
    }

    private void foreground() {
        int c = 0x303844, dim = 0x5A6472;
        int[] a = quarry.area();
        switch (tab) {
            case 0: {
                if (quarry.isExo()) {
                    rigStatus(c, dim);
                    break;
                }
                TileEntityQuarrySC.Status s = quarry.getStatus();
                int sc = s == TileEntityQuarrySC.Status.RUNNING ? 0x2E7D32 : s == TileEntityQuarrySC.Status.DONE ? 0x2A62A8
                        : s == TileEntityQuarrySC.Status.PAUSED ? 0x606060 : 0xA02020;
                int room = ENERGY_X - 12;
                fit(Lang.tr("sc.quarry.status." + s.name().toLowerCase(java.util.Locale.ROOT)), 8, 30, room, sc);
                if (a != null) {
                    long total = (long) (a[4] - a[5] + 1) * (a[2] - a[0] + 1) * (a[3] - a[1] + 1);
                    long left = Math.min(total, container.blocksLeftClient);
                    fit(Lang.tr("sc.quarrygui.layer", quarry.getLayerY(), a[5],
                            total == 0 ? 100 : (int) ((total - left) * 100 / total)), 8, 42, room, c);
                    double bps = quarry.blocksPerSecond();
                    fit(Lang.tr("sc.quarrygui.cost", quarry.getLastCost(), String.format(java.util.Locale.ROOT, "%.1f", bps)), 8, 54, room, c);
                    long sec = bps <= 0 ? 0 : (long) (left / bps);
                    fit(Lang.tr("sc.quarrygui.left", String.valueOf(left), sec / 3600, sec / 60 % 60), 8, 66, room, c);
                    fit(Lang.tr("sc.quarrygui.size", a[2] - a[0] + 1, a[3] - a[1] + 1, a[4] - a[5] + 1), 8, 78, room, c);
                }
                fit(Lang.tr("sc.quarrygui.mined", String.valueOf(quarry.getMined())), 8, 90, room, c);
                fit(Lang.tr("sc.fieldgui.owner", quarry.getOwner().isEmpty() ? "-" : quarry.getOwner()), 8, 102, room, dim);
                int used = 0;
                for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
                    used += quarry.getTank(i).getFluidAmount() > 0 ? 1 : 0;
                }
                fit(Lang.tr("sc.quarrygui.pump2", quarry.pumpedTotal(), used, quarry.unlockedTanks()), 8, 170, W - 16, c);
                fit(Lang.tr("sc.quarrygui.water", quarry.getWater().getFluidAmount(), quarry.waterCapacity()), 8, 182, W - 16, c);
                fit(Lang.tr("sc.quarrygui.head", headName()), 8, 194, W - 16, c);
                if (!quarry.allowed(mc.thePlayer)) {
                    fit(Lang.tr("sc.quarrygui.owneronly"), 8, 222, W - 16, 0xA02020);
                }
                break;
            }
            case 1: {
                if (quarry.isExo()) {
                    fontRendererObj.drawSplitString(Lang.tr("sc.quarrygui.exo.noarea"), 8, 30, 114, dim);
                    break;
                }
                String[] rows = {Lang.tr("sc.quarrygui.sizex", quarry.getSizeX(), quarry.maxSize()),
                        Lang.tr("sc.quarrygui.sizez", quarry.getSizeZ(), quarry.maxSize()),
                        Lang.tr("sc.quarrygui.offx", quarry.getOffX()), Lang.tr("sc.quarrygui.offz", quarry.getOffZ()),
                        Lang.tr("sc.quarrygui.bottom", quarry.getBottomY())};
                for (int r = 0; r < rows.length; r++) {
                    fit(rows[r], 8, 30 + r * 30, 114, c);
                }
                if (quarry.usesCard()) {
                    fit(Lang.tr("sc.quarrygui.bycard"), 8, 230, W - 16, 0x2A62A8);
                }
                break;
            }
            case 2: {
                if (quarry.isExo()) {
                    lenses(c, dim);
                    break;
                }
                int lx = MAP_X + MAP + 8;
                fit(Lang.tr("sc.fieldgui.map.title"), lx, 30, W - lx - 6, c);
                legend(lx, 44, 0xFFFFE040, Lang.tr("sc.quarrygui.map.quarry"));
                legend(lx, 56, 0xFF000000 | quarry.getColorPlane(), Lang.tr("sc.quarrygui.map.drill"));
                legend(lx, 68, 0xFF1E232B, Lang.tr("sc.quarrygui.map.done"));
                legend(lx, 80, 0xFFFF4040, Lang.tr("sc.fieldgui.map.you"));
                if (!quarry.hasScanner()) {
                    fontRendererObj.drawSplitString(Lang.tr("sc.quarrygui.noscanner"), lx, 96, W - lx - 6, dim);
                } else {
                    int pct = quarry.getScanPercentClient();
                    fit(pct < 0 ? Lang.tr("sc.quarrygui.notscanned") : pct < 100 ? Lang.tr("sc.quarrygui.scanning", pct)
                            : Lang.tr("sc.quarrygui.orefound"), lx, 96, W - lx - 6, pct == 100 ? c : 0x2A62A8);
                    TextFitSC.help(fontRendererObj, W - 16, 96, Lang.tr("sc.quarrygui.scan.help", TileEntityQuarrySC.SCAN_COST),
                            guiLeft, guiTop + TOP);
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
            case TAB_TANKS:
                tanksTab(c, dim);
                break;
            case 3:
                fit(Lang.tr("sc.quarrygui.filterlabel"), ContainerQuarrySC.FILTER_X, ContainerQuarrySC.FILTER_Y - 10, 150, c);
                TextFitSC.help(fontRendererObj, ContainerQuarrySC.FILTER_X + 153, ContainerQuarrySC.FILTER_Y - 11,
                        Lang.tr("sc.quarrygui.filter.help"), guiLeft, guiTop + TOP);
                fit(Lang.tr("sc.quarrygui.buffer"), ContainerQuarrySC.BUFFER_X, ContainerQuarrySC.BUFFER_Y - 10, 162, c);
                fit(Lang.tr("container.inventory"), ContainerQuarrySC.INV_X, ContainerQuarrySC.INV_Y - 10, 162, c);
                break;
            case 4: {
                int on = 0, total = 0;
                for (int i = 0; i < TileEntityQuarrySC.FLAG_COUNT; i++) {
                    ItemQuarryModuleSC.Kind k = moduleOf(i);
                    boolean mine = quarry.isExo() ? !notForRig(i) : !rigOnly(i);
                    if (mine && (k == null || quarry.moduleCount(k) > 0)) {
                        total++;
                        on += (quarry.getFlags() & (1 << i)) != 0 ? 1 : 0;
                    }
                }
                fit(Lang.tr("sc.quarrygui.fncount", on, total), 184, 32, W - 190, c);
                break;
            }
            case 5:
                fit(Lang.tr("sc.quarrygui.modules"), ContainerQuarrySC.UPGRADE_X, ContainerQuarrySC.UPGRADE_Y - 10, 62, c);
                TextFitSC.help(fontRendererObj, ContainerQuarrySC.UPGRADE_X + 64, ContainerQuarrySC.UPGRADE_Y - 11,
                        Lang.tr("sc.quarrygui.modules.hint"), guiLeft, guiTop + TOP);
                if (!quarry.isExo()) {
                    String[] tools = {"head", "scanner", "card"};
                    for (int i = 0; i < 3; i++) {
                        fit(Lang.tr("sc.quarrygui.slot." + tools[i]), 8 + i * ContainerQuarrySC.TOOL_COL, ContainerQuarrySC.TOOLS_Y + 4,
                                ContainerQuarrySC.TOOL_SLOT - 4, c);
                    }
                }
                fit(Lang.tr("sc.quarrygui.slots", quarry.unlockedUpgrades(), TileEntityQuarrySC.UPGRADES),
                        ContainerQuarrySC.UPGRADE_X + 90, ContainerQuarrySC.UPGRADE_Y - 10, 72, dim);
                fit(Lang.tr("container.inventory"), ContainerQuarrySC.INV_X, ContainerQuarrySC.INV_Y - 10, 162, c);
                break;
            default:
        }
    }

    /** The rig's Quarry tab: status, hauls and their cost, the draw, the totals, the water. */
    private void rigStatus(int c, int dim) {
        TileEntityQuarrySC.Status s = quarry.getStatus();
        int sc = s == TileEntityQuarrySC.Status.RUNNING ? 0x2E7D32 : s == TileEntityQuarrySC.Status.PAUSED ? 0x606060 : 0xA02020;
        int room = ENERGY_X - 12;
        String st = s == TileEntityQuarrySC.Status.NO_AREA ? Lang.tr("sc.quarry.status.exo_nothing")
                : Lang.tr("sc.quarry.status." + s.name().toLowerCase(java.util.Locale.ROOT));
        fit(st, 8, 30, room, sc);
        double rate = quarry.haulsPerSecond();
        int cost = quarry.haulCost();
        fit(Lang.tr("sc.quarrygui.exo.rate", String.format(java.util.Locale.ROOT, "%.2f", rate), String.valueOf(cost)), 8, 42, room, c);
        fit(Lang.tr("sc.quarrygui.exo.use", String.valueOf((long) (cost * rate / 20))), 8, 54, room, c);
        fit(Lang.tr("sc.quarrygui.exo.mined", String.valueOf(quarry.getMined())), 8, 66, room, c);
        fit(Lang.tr("sc.fieldgui.owner", quarry.getOwner().isEmpty() ? "-" : quarry.getOwner()), 8, 102, room, dim);
        fit(Lang.tr("sc.quarrygui.water", quarry.getWater().getFluidAmount(), quarry.waterCapacity()), 8, 182, W - 16, c);
        if (!quarry.allowed(mc.thePlayer)) {
            fit(Lang.tr("sc.quarrygui.owneronly"), 8, 222, W - 16, 0xA02020);
        }
    }

    /** The rig's Lenses tab: 4 lens slots, and what comes up how often (the lenses and filter counted in). */
    private void lenses(int c, int dim) {
        fit(Lang.tr("sc.quarrygui.exo.lenses"), ContainerQuarrySC.LENS_X, ContainerQuarrySC.LENS_Y - 10, 60, c);
        TextFitSC.help(fontRendererObj, ContainerQuarrySC.LENS_X + 64, ContainerQuarrySC.LENS_Y - 11,
                Lang.tr("sc.quarrygui.exo.lenses.help"), guiLeft, guiTop + TOP);
        int total = Math.max(1, quarry.totalWeight());
        List<com.sc.machine.ExoOreTableSC.Entry> list = new ArrayList<com.sc.machine.ExoOreTableSC.Entry>(quarry.exoEntries());
        java.util.Collections.sort(list, new java.util.Comparator<com.sc.machine.ExoOreTableSC.Entry>() {
            @Override
            public int compare(com.sc.machine.ExoOreTableSC.Entry a, com.sc.machine.ExoOreTableSC.Entry b) {
                return quarry.weightOf(b) - quarry.weightOf(a);
            }
        });
        int x = 90, y = 26;
        fit(Lang.tr("sc.quarrygui.exo.chances"), x, y, W - x - 6, c);
        RenderHelper.enableGUIStandardItemLighting();
        int shown = 0;
        for (com.sc.machine.ExoOreTableSC.Entry e : list) {
            int w = quarry.weightOf(e);
            if (w <= 0 || shown >= 7) {
                continue;
            }
            int ry = y + 10 + shown * 16;
            itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), e.ore, x, ry);
            GL11.glDisable(GL11.GL_LIGHTING);
            String pct = String.format(java.util.Locale.ROOT, "%.1f%%", w * 100.0 / total);
            int lens = e.lens >= 0 ? quarry.lensCount(e.lens) : 0;
            fit(pct + (lens > 0 ? "  x" + (1 + quarry.lensBoost() * lens) : "") + "  " + e.ore.getDisplayName(),
                    x + 18, ry + 4, W - x - 24, lens > 0 ? 0x2E7D32 : c);
            RenderHelper.enableGUIStandardItemLighting();
            shown++;
        }
        RenderHelper.disableStandardItemLighting();
        fit(Lang.tr("container.inventory"), ContainerQuarrySC.INV_X, ContainerQuarrySC.INV_Y - 10, 162, c);
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
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        if (tab == 1 && ++sliderTick % 10 == 0 && !org.lwjgl.input.Mouse.isButtonDown(0)) {
            sendSliders();
        }
        List<String> tip = tooltipAt(mouseX - guiLeft, mouseY - guiTop - TOP);
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    /** A gauge's tooltip: the fluid and amount, its side, the pin, auto output (i = TANKS: the washing water). */
    private List<String> tankTip(int i) {
        List<String> lines = new ArrayList<String>();
        boolean wash = i == TileEntityQuarrySC.TANKS;
        net.minecraftforge.fluids.FluidStack f = wash ? quarry.getWater().getFluid() : quarry.getTank(i).getFluid();
        int cap = wash ? quarry.waterCapacity() : quarry.tankCapacity();
        String name = f != null && f.amount > 0 ? f.getLocalizedName() : Lang.tr("sc.quarrygui.tank.none");
        lines.add(wash ? Lang.tr("sc.quarrygui.wash") : Lang.tr("sc.quarrygui.tank.n", i + 1));
        if (!wash && i >= quarry.unlockedTanks()) {
            lines.add("§c" + Lang.tr("sc.quarrygui.locked", TIER_NAMES[Math.min(3, i)]));
            return lines;
        }
        lines.add(Lang.tr("sc.quarrygui.tank.amount", name, f == null ? 0 : f.amount, cap));
        if (!wash) {
            int side = quarry.getTankSide(i);
            lines.add("§7" + Lang.tr("sc.quarrygui.tank.side", Lang.tr("sc.quarrygui.sideshort." + (side + 1))));
            String pin = quarry.getTankPinned(i);
            if (pin != null) {
                net.minecraftforge.fluids.Fluid pf = net.minecraftforge.fluids.FluidRegistry.getFluid(pin);
                lines.add("§9" + Lang.tr("sc.quarrygui.tank.pinned", pf == null ? pin
                        : pf.getLocalizedName(new net.minecraftforge.fluids.FluidStack(pf, 1000))));
            }
            if (quarry.getTankAuto(i)) {
                lines.add("§a" + Lang.tr("sc.quarrygui.tank.autoon"));
            }
        }
        return lines;
    }

    private List<String> tooltipAt(int mx, int my) {
        List<String> lines = new ArrayList<String>();
        if (tab == 0 && GuiGaugeSC.isOver(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mx, my)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(quarry.getEnergyStored() + " / " + quarry.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.input", quarry.inputTier().name(), quarry.inputTier().getVoltage()));
            return lines;
        }
        if (tab == 5) {
            for (int i = quarry.unlockedUpgrades(); i < TileEntityQuarrySC.UPGRADES; i++) {
                if (GuiGaugeSC.isOver(ContainerQuarrySC.UPGRADE_X - 1 + i % 9 * 18, ContainerQuarrySC.UPGRADE_Y - 1 + i / 9 * 18, 18, 18, mx, my)) {
                    lines.add(Lang.tr("sc.quarrygui.locked", TileEntityQuarrySC.tierUnlocking(i).name()));
                    return lines;
                }
            }
        }
        if (tab == TAB_TANKS) {
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.id == B_WASH_FEED
                        && GuiGaugeSC.isOver(b.xPosition - guiLeft, b.yPosition - guiTop - TOP, b.width, b.height, mx, my)) {
                    lines.add(Lang.tr("sc.quarrygui.wash.feed.tip", onOff(quarry.isWashFromTanks())));
                    lines.add("§7" + Lang.tr("sc.quarrygui.wash.feed.help", TileEntityQuarrySC.AUTO_OUT));
                    return lines;
                }
                if (b.id >= B_FVEIN && b.id <= B_FVEIN_FLOWING
                        && GuiGaugeSC.isOver(b.xPosition - guiLeft, b.yPosition - guiTop - TOP, b.width, b.height, mx, my)) {
                    lines.add(Lang.tr("sc.quarrygui.fvein.help", TileEntityQuarrySC.FLUID_VEIN_COST,
                            TileEntityQuarrySC.FLUID_VEIN_RANGE[Math.min(3, quarry.getTier().ordinal())]));
                    if (quarry.moduleCount(ItemQuarryModuleSC.Kind.FLUID_VEIN) == 0) {
                        lines.add("§c" + Lang.tr("sc.quarrygui.fvein.nomodule"));
                    } else {
                        lines.add("§7" + Lang.tr("sc.quarrygui.fvein.stats", quarry.getFluidVeinLast(), quarry.getFluidVeinTotal()));
                    }
                    return lines;
                }
            }
            for (int i = 0; i <= TileEntityQuarrySC.TANKS; i++) {
                int gx = i < TileEntityQuarrySC.TANKS ? GAUGE_X[i] : WASH_X;
                if (GuiGaugeSC.isOver(gx, GAUGE_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mx, my)) {
                    return tankTip(i);
                }
            }
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                boolean clear = b.id >= B_TANK_CLEAR && b.id < B_TANK_CLEAR + TileEntityQuarrySC.TANKS || b.id == B_WASH_CLEAR;
                if (clear && GuiGaugeSC.isOver(b.xPosition - guiLeft, b.yPosition - guiTop - TOP, b.width, b.height, mx, my)) {
                    int i = b.id == B_WASH_CLEAR ? TileEntityQuarrySC.TANKS : b.id - B_TANK_CLEAR;
                    int amount = i == TileEntityQuarrySC.TANKS ? quarry.getWater().getFluidAmount() : quarry.getTank(i).getFluidAmount();
                    lines.add(Lang.tr("sc.quarrygui.tank.clear.tip", amount));
                    int cost = quarry.clearCost(i);
                    lines.add((quarry.getEnergyStored() >= cost ? "§e" : "§c")
                            + Lang.tr("sc.quarrygui.tank.clear.cost", cost, quarry.getEnergyStored()));
                    return lines;
                }
            }
        }
        if (tab == 4) {
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.id >= B_FLAG && b.id < B_FLAG + TileEntityQuarrySC.FLAG_COUNT && GuiGaugeSC.isOver(b.xPosition - guiLeft,
                        b.yPosition - guiTop - TOP, b.width, b.height, mx, my)) {
                    lines.add(b.id - B_FLAG == 24
                            ? Lang.tr("sc.quarrygui.flag.24.hint", TileEntityQuarrySC.REPAIR_PER_TICK, TileEntityQuarrySC.REPAIR_COST)
                            : Lang.tr("sc.quarrygui.flag." + (b.id - B_FLAG) + ".hint"));
                    ItemQuarryModuleSC.Kind k = moduleOf(b.id - B_FLAG);
                    if (quarry.isExo() && notForRig(b.id - B_FLAG)) {
                        lines.add("§c" + Lang.tr("sc.quarrygui.exo.notused"));
                    } else if (!quarry.isExo() && rigOnly(b.id - B_FLAG)) {
                        lines.add("§c" + Lang.tr("sc.quarrygui.rigonly"));
                    } else if (k != null && quarry.moduleCount(k) == 0) {
                        lines.add("§c" + Lang.tr("sc.quarrygui.needmodule.tip", Lang.tr("item.siliconage.quarryModule."
                                + k.name().toLowerCase(java.util.Locale.ROOT) + ".name")));
                    }
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
