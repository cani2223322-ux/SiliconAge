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
import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiButton;
import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.client.renderer.RenderHelper;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/**
 * The quarry's screen in the machines' holo style: the window with its title and tier badge, one
 * row of tabs, holo screens, and on the right the power switch, the redstone button and the energy
 * gauge (GuiPowerSC) on every tab. Seven tabs (the Exo Drilling Rig: six, no tanks):
 * - Quarry: status, four reading cards, the pit (or the rig's beam) drawn live; start / pause /
 *   reset, experience, power mode, the head; the pump's compartments and the washing water;
 * - Area: size, offset, bottom, shape, what's left behind - and how the area is shown in the
 *   world: when, dashes, scan plane, brightness, colours (palette + R/G/B);
 * - Map: the area from above - what's done on this layer, the drill, the ore found, you;
 *   the rig: its lenses and what comes up how often;
 * - Output: filter (9 examples, mode), output side, the 27-slot buffer;
 * - Functions: what each module adds, switched on / off, and the power mode;
 * - Upgrades: 18 module slots (as many open as the tier gives), the head, scanner and area card;
 * - Tanks: the four compartments, the washing water, the fluid vein, the fluid filter.
 * All coordinates here are from the window's corner; the slots' are ContainerQuarrySC's + TOP.
 * Buttons go to the server through QuarryNetSC.
 */
public class GuiQuarrySC extends GuiContainer {

    private static final int W = 248, H = 262, TOP = ContainerQuarrySC.TOP;
    private static final int TAB_BASE = 1000;
    /** The gauge column right of the screens: the power switch and redstone button, the gauge under them. */
    private static final int ENERGY_X = 214, ENERGY_W = 26, BUTTONS_Y = 34, ENERGY_Y = 46, ENERGY_H = 82;
    /** The holo screen: left edge, width (the gauge column past it), top; a short one over the inventory ends at SHORT_B. */
    private static final int SX = 7, SW = 202, ST = 34, FULL_B = 256, SHORT_B = 166;
    private static final int MAP_X = 12, MAP_Y = 40, MAP = 140;
    private static final String[] TABS = {"quarry", "area", "map", "output", "functions", "upgrades", "tanks"};
    /** The Quarry tab's four reading cards. */
    private static final int[][] CARDS = {{14, 50}, {58, 50}, {14, 72}, {58, 72}};

    /** The quarry whose screen is open (the renderer shows its area "while the menu is open"). */
    public static int[] openAt;

    private static int tab;
    private static boolean targetPlane;

    private final TileEntityQuarrySC quarry;
    private final ContainerQuarrySC container;
    private GuiPowerSC power;
    /** The battery slot under the gauge (every tab). */
    private GuiBatterySlotSC battery;

    public GuiQuarrySC(InventoryPlayer inv, TileEntityQuarrySC quarry) {
        super(new ContainerQuarrySC(inv, quarry));
        this.quarry = quarry;
        this.container = (ContainerQuarrySC) inventorySlots;
        xSize = W;
        ySize = H;
    }

    // ------------------------------------------------------------------ layout

    private static final int B_RUN = 1, B_RESET = 2, B_XP = 3, B_REDSTONE = 4, B_POWER = 5, B_SWITCH = 6, B_BATTERY = 7, B_SHAPE = 10, B_REPLACE = 11,
            B_SHOW = 20, B_DASH = 21, B_PLANE = 22, B_BRIGHT = 24, B_TARGET = 25, B_SWATCH = 30, B_SLIDER = 40,
            B_SCAN = 50, B_FILTER = 60, B_OUTSIDE = 61, B_PAGE = 62, B_OUTPAGE = 63, B_FF_MODE = 64,
            B_FF_REMOVE = 65, B_TANK_FULL = 66, B_FF_HAND = 67, B_FF_CLEAR = 68, B_TANK_SIDE = 70, B_TANK_CLEAR = 74,
            B_TANK_FILTER = 78, B_FF_DEL = 82, B_TANK_PIN = 88, B_TANK_AUTO = 92, B_WASH_CLEAR = 96, B_FVEIN = 97, B_FVEIN_RANGE = 98, B_FVEIN_FLOWING = 99, B_WASH_FEED = 69,
            B_FLAG = 100, B_FORTUNE_DOWN = 140, B_FORTUNE_UP = 141, B_STEP = 200;

    /** The Functions tab's two pages: what the modules add; then the rest (silence, auto-stop, filter, chests, chat). */
    private static final int[][] FN_PAGES = {{0, 1, 2, 3, 4, 19, 5, 6, 7, 8, 9, 18, 20, 21, 22, 23, 24, 25, 26, 27},
            {10, 11, 12, 13, 14, 15, 16, 17}};
    private static int fnPage;
    /** The Tanks tab: where the four gauges and the washing water's stand. */
    private static final int[] GAUGE_X = {12, 52, 92, 132};
    private static final int WASH_X = 172, GAUGE_Y = 60, TAB_TANKS = 6;

    private static GuiButton holo(int id, int x, int y, int w, int h, String text) {
        return new GuiFieldGeneratorSC.HoloButton(id, x, y, w, h, text);
    }

    @Override
    @SuppressWarnings("unchecked")
    public void initGui() {
        super.initGui();
        openAt = new int[]{quarry.xCoord, quarry.yCoord, quarry.zCoord};
        buttonList.clear();
        // one row of tabs: an icon and a short name, the full name as the tooltip
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
        for (int i = 0; i < (exo ? TABS.length - 1 : TABS.length); i++) {
            String key = exo && i < 3 ? "sc.quarrygui.exo.tab" : "sc.quarrygui.tab";
            GuiFieldGeneratorSC.HoloTab b = new GuiFieldGeneratorSC.HoloTab(TAB_BASE + i, guiLeft + 7 + i * 34, guiTop + 18, 33, 13,
                    icons[i], Lang.tr(key + "short." + TABS[i]), Lang.tr(key + "." + TABS[i]));
            b.current = i == tab;
            buttonList.add(b);
        }
        power = new GuiPowerSC(quarry, B_SWITCH, B_REDSTONE, ENERGY_X, BUTTONS_Y, ENERGY_W, ENERGY_Y, ENERGY_H);
        power.addButtons(buttonList, guiLeft, guiTop);
        battery = new GuiBatterySlotSC(quarry, B_BATTERY, ENERGY_X, ENERGY_Y + ENERGY_H);
        battery.addButton(buttonList, guiLeft, guiTop);
        int x = guiLeft, y = guiTop;
        switch (tab) {
            case 0:
                buttonList.add(holo(B_RUN, x + 14, y + 144, 60, 16, ""));
                buttonList.add(holo(B_RESET, x + 78, y + 144, 54, 16, Lang.tr("sc.quarrygui.reset")));
                buttonList.add(holo(B_XP, x + 136, y + 144, 66, 16, ""));
                buttonList.add(holo(B_POWER, x + 14, y + 164, 92, 16, ""));
                break;
            case 1: {
                int[] steps = {-16, -1, 1, 16};
                for (int r = 0; r < (exo ? 0 : 5); r++) {
                    for (int s = 0; s < 4; s++) {
                        String label = (steps[s] > 0 ? "+" : "") + steps[s];
                        buttonList.add(holo(B_STEP + r * 10 + s, x + 14 + s * 22, y + 61 + r * 26, 20, 12, label));
                    }
                }
                if (!exo) {
                    buttonList.add(holo(B_SHAPE, x + 14, y + 186, 90, 14, ""));
                    buttonList.add(holo(B_REPLACE, x + 14, y + 204, 90, 14, ""));
                }
                int rx = x + 110;
                buttonList.add(holo(B_SHOW, rx, y + 50, 96, 14, ""));
                buttonList.add(holo(B_DASH, rx, y + 68, 96, 14, ""));
                buttonList.add(holo(B_PLANE, rx, y + 86, 96, 14, ""));
                buttonList.add(holo(B_BRIGHT, rx, y + 104, 96, 14, ""));
                buttonList.add(holo(B_TARGET, rx, y + 124, 78, 14, ""));
                for (int i = 0; i < TileEntityQuarrySC.PALETTE.length; i++) {
                    buttonList.add(new Swatch(B_SWATCH + i, rx + i * 12, y + 142, TileEntityQuarrySC.PALETTE[i]));
                }
                int c = targetPlane ? quarry.getColorPlane() : quarry.getColorFrame();
                String[] names = {"R", "G", "B"};
                int[] tints = {0xFFE63C3C, 0xFF5AE66E, 0xFF4A80F0};
                for (int i = 0; i < 3; i++) {
                    buttonList.add(new HoloSlider(B_SLIDER + i, rx, y + 158 + i * 16, 96, 13, names[i] + ": ", c >> (16 - 8 * i) & 0xFF, tints[i]));
                }
                break;
            }
            case 2:
                if (!exo) {
                    buttonList.add(holo(B_SCAN, x + 158, y + 234, 46, 16, Lang.tr("sc.quarrygui.scan")));
                }
                break;
            case 3:
                buttonList.add(holo(B_FILTER, x + 14, y + 48, 94, 14, ""));
                buttonList.add(holo(B_OUTSIDE, x + 112, y + 48, 94, 14, ""));
                break;
            case TAB_TANKS: {
                for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
                    int bx = x + GAUGE_X[i] - 2;
                    buttonList.add(holo(B_TANK_SIDE + i, bx, y + 134, 36, 11, ""));
                    buttonList.add(holo(B_TANK_CLEAR + i, bx, y + 146, 36, 11, ""));
                    buttonList.add(holo(B_TANK_FILTER + i, bx, y + 158, 36, 11, ""));
                    buttonList.add(holo(B_TANK_PIN + i, bx, y + 170, 36, 11, ""));
                    buttonList.add(holo(B_TANK_AUTO + i, bx, y + 182, 36, 11, ""));
                }
                buttonList.add(holo(B_WASH_CLEAR, x + WASH_X - 2, y + 134, 36, 11, ""));
                buttonList.add(holo(B_WASH_FEED, x + WASH_X - 2, y + 146, 36, 11, ""));
                buttonList.add(holo(B_FVEIN, x + 12, y + 198, 62, 11, ""));
                buttonList.add(holo(B_FVEIN_RANGE, x + 76, y + 198, 62, 11, ""));
                buttonList.add(holo(B_FVEIN_FLOWING, x + 140, y + 198, 64, 11, ""));
                buttonList.add(holo(B_FF_MODE, x + 12, y + 211, 94, 11, ""));
                buttonList.add(holo(B_FF_REMOVE, x + 108, y + 211, 58, 11, ""));
                buttonList.add(holo(B_FF_HAND, x + 168, y + 211, 36, 11, ""));
                for (int i = 0; i < TileEntityQuarrySC.FLUID_FILTER_MAX; i++) {
                    buttonList.add(holo(B_FF_DEL + i, x + 12 + i * 32, y + 224, 30, 11, ""));
                }
                buttonList.add(holo(B_TANK_FULL, x + 12, y + 238, 138, 12, ""));
                buttonList.add(holo(B_FF_CLEAR, x + 152, y + 238, 52, 12, ""));
                break;
            }
            case 4: {
                buttonList.add(holo(B_POWER, x + 14, y + 50, 108, 14, ""));
                buttonList.add(holo(B_PAGE, x + 126, y + 50, 78, 14, ""));
                int[] page = FN_PAGES[fnPage];
                for (int n = 0; n < page.length; n++) {
                    int i = page[n], col = n % 2, row = n / 2;
                    int fx = x + 14 + col * 96, fy = y + 70 + row * 18;
                    buttonList.add(holo(B_FLAG + i, fx, fy, i == 1 ? 60 : 92, 16, ""));
                    if (i == 1) {
                        buttonList.add(holo(B_FORTUNE_DOWN, fx + 62, fy, 14, 16, "-"));
                        buttonList.add(holo(B_FORTUNE_UP, fx + 78, fy, 14, 16, "+"));
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
        boolean may = container.may(mc.thePlayer);
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
                boolean on = (quarry.getVflags() & TileEntityQuarrySC.V_DASH) != 0;
                b.displayString = (on ? "§a" : "§7") + Lang.tr(quarry.isExo() ? "sc.quarrygui.exo.dash" : "sc.quarrygui.dash", onOff(on));
            } else if (id == B_PLANE) {
                boolean on = (quarry.getVflags() & TileEntityQuarrySC.V_PLANE) != 0;
                b.displayString = (on ? "§a" : "§7") + Lang.tr(quarry.isExo() ? "sc.quarrygui.exo.plane" : "sc.quarrygui.plane", onOff(on));
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
                boolean on = has && quarry.isFluidVeinOn();
                b.displayString = (on ? "§a" : "§7") + Lang.tr("sc.quarrygui.fvein", onOff(on));
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
                b.displayString = (quarry.getTankAuto(i) ? "§a" : "§7") + Lang.tr("sc.quarrygui.tank.auto", onOff(quarry.getTankAuto(i)));
                b.enabled = may && i < quarry.unlockedTanks();
            } else if (id >= B_FF_DEL && id < B_FF_DEL + TileEntityQuarrySC.FLUID_FILTER_MAX) {
                int i = id - B_FF_DEL;
                java.util.List<String> ff = quarry.getFluidFilter();
                b.visible = i < ff.size();
                if (i < ff.size()) {
                    net.minecraftforge.fluids.Fluid f = net.minecraftforge.fluids.FluidRegistry.getFluid(ff.get(i));
                    b.displayString = (f == null ? ff.get(i) : f.getLocalizedName(new net.minecraftforge.fluids.FluidStack(f, 1000))) + " x";
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
                    b.displayString = name;
                    b.enabled = false;
                } else if (k != null && quarry.moduleCount(k) == 0) {
                    b.displayString = name;               // greyed; "needs a module" is in its tooltip
                    b.enabled = false;
                } else {
                    b.displayString = (on ? "§a" : "§7") + name;   // the lamp says on / off
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
        if (!power.allowClick(b)) {
            return;
        }
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
            case B_SWITCH: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_SWITCH, 0); break;
            case B_BATTERY: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_BATTERY_MODE, 0); break;
            case B_POWER: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_POWER, 0); break;
            case B_SHAPE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_SHAPE, 0); break;
            case B_REPLACE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_REPLACE, 0); break;
            case B_SHOW: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_SHOW, 0); break;
            case B_DASH: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_VFLAG, TileEntityQuarrySC.V_DASH); break;
            case B_PLANE: QuarryNetSC.send(quarry, TileEntityQuarrySC.A_VFLAG, TileEntityQuarrySC.V_PLANE); break;
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
        if (c < 0 || c == lastSent || !container.may(mc.thePlayer)) {
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

    // ------------------------------------------------------------------ drawing: background

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

    /** The tier's badge colour: LV steel, MV copper, HV gold, EV violet, the rig's XV deep purple. */
    private int badgeColour() {
        switch (quarry.getTier().ordinal()) {
            case 0: return 0xFF9098A4;
            case 1: return 0xFFE0903A;
            case 2: return 0xFFE8C040;
            case 3: return 0xFFA070E0;
            default: return 0xFF6A3A8A;
        }
    }

    /** A slot pocket on the window's steel (the player's inventory). */
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

    /** Cyan-framed slot pockets on a holo screen, (x, y) the first item's corner. */
    private static void holoSlots(int x, int y, int cols, int rows) {
        for (int r = 0; r < rows; r++) {
            for (int c = 0; c < cols; c++) {
                GuiHoloSC.slot(x + c * 18, y + r * 18, false);
            }
        }
    }

    /** A dark card on the screen (a reading, or a caption that's not a button). */
    private void card(int x, int y, int w, int h) {
        drawRect(x, y, x + w, y + h, 0xFF2A6A8A);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0E3A50);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        float t = mc.theWorld == null ? 0F : (mc.theWorld.getTotalWorldTime() % 1000000L) + partialTicks;
        boolean exo = quarry.isExo();
        window(x, y, W, H);
        drawRect(x + W - 24, y + 4, x + W - 6, y + 14, badgeColour());
        GuiEnergyGaugeSC.draw(x + ENERGY_X, y + ENERGY_Y, ENERGY_W, ENERGY_H,
                (float) quarry.getEnergyStored() / Math.max(1, quarry.getMaxEnergyStored()));
        boolean inv = tab == 3 || tab == 5 || exo && tab == 2;
        if (tab == 0) {
            GuiHoloSC.screen(x + SX, y + ST, SW, 100);
            GuiHoloSC.screen(x + SX, y + 138, SW, 118);
            for (int[] c : CARDS) {
                card(x + c[0], y + c[1], 42, 20);
            }
            if (exo) {
                rigScene(x + 104, y + 40, 100, 88, t);
            } else {
                quarryScene(x + 104, y + 40, 100, 88, t);
            }
            if (!exo) {
                card(x + 110, y + 164, 92, 16);                  // the head: shown, not a button (the rig has none)
            }
            if (exo) {
                GuiHoloSC.bar(x + 15, y + 199, 186, 4, (float) quarry.getWater().getFluidAmount() / Math.max(1, quarry.waterCapacity()),
                        36, 0xFF4A8AE8);
            } else {
                tankBars(x, y);
            }
            GuiHoloSC.glint(x + SX, y + ST, SW, 100);
            GuiHoloSC.glint(x + SX, y + 138, SW, 118);
        } else {
            int bottom = inv ? SHORT_B : FULL_B;
            GuiHoloSC.screen(x + SX, y + ST, SW, bottom - ST);
            if (tab == 1) {
                int c = targetPlane ? quarry.getColorPlane() : quarry.getColorFrame();
                int px = x + 110 + 80, py = y + 124;
                drawRect(px, py, px + 16, py + 14, 0xFF1E3444);
                drawRect(px + 1, py + 1, px + 15, py + 13, 0xFF000000 | c);
                if (exo) {
                    rigScene(x + 14, y + 124, 88, 124, t);
                }
            } else if (tab == 2 && exo) {
                holoSlots(x + ContainerQuarrySC.LENS_X, y + ContainerQuarrySC.LENS_Y + TOP, 4, 2);
                for (int i = quarry.unlockedLenses(); i < TileEntityQuarrySC.LENSES; i++) {
                    lock(x + ContainerQuarrySC.LENS_X + i % 4 * 18, y + ContainerQuarrySC.LENS_Y + TOP + i / 4 * 18);
                }
            } else if (tab == 2) {
                drawRect(x + MAP_X - 1, y + MAP_Y - 1, x + MAP_X + MAP + 1, y + MAP_Y + MAP + 1, 0xFF1E3444);
                drawRect(x + MAP_X, y + MAP_Y, x + MAP_X + MAP, y + MAP_Y + MAP, 0xFF050A10);
                drawMap(x + MAP_X, y + MAP_Y);
            } else if (tab == TAB_TANKS) {
                int cap = quarry.tankCapacity();
                for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
                    GuiTankGaugeSC.draw(mc, x + GAUGE_X[i], y + GAUGE_Y, quarry.getTank(i).getFluid(), cap, quarry.getTankPinned(i),
                            i >= quarry.unlockedTanks());
                }
                GuiTankGaugeSC.draw(mc, x + WASH_X, y + GAUGE_Y, quarry.getWater().getFluid(), quarry.waterCapacity(), null, false);
                drawRect(x + 12, y + 195, x + 204, y + 196, 0xFF1E3444);
            } else if (tab == 3) {
                holoSlots(x + ContainerQuarrySC.FILTER_X, y + ContainerQuarrySC.FILTER_Y + TOP, 9, 1);
                holoSlots(x + ContainerQuarrySC.BUFFER_X, y + ContainerQuarrySC.BUFFER_Y + TOP, 9, 3);
            } else if (tab == 5) {
                holoSlots(x + ContainerQuarrySC.UPGRADE_X, y + ContainerQuarrySC.UPGRADE_Y + TOP, 9, 2);
                for (int i = quarry.unlockedUpgrades(); i < TileEntityQuarrySC.UPGRADES; i++) {
                    lock(x + ContainerQuarrySC.UPGRADE_X + i % 9 * 18, y + ContainerQuarrySC.UPGRADE_Y + TOP + i / 9 * 18);
                }
                if (!exo) {
                    GuiHoloSC.slot(x + ContainerQuarrySC.HEAD_X, y + ContainerQuarrySC.TOOLS_Y + TOP, false);
                    GuiHoloSC.slot(x + ContainerQuarrySC.SCANNER_X, y + ContainerQuarrySC.TOOLS_Y + TOP, false);
                    GuiHoloSC.slot(x + ContainerQuarrySC.CARD_X, y + ContainerQuarrySC.TOOLS_Y + TOP, false);
                }
            }
            GuiHoloSC.glint(x + SX, y + ST, SW, bottom - ST);
        }
        battery.draw(x, y, quarry.getStackInSlot(TileEntityQuarrySC.SLOT_BATTERY));
        if (inv) {
            pockets(x + ContainerQuarrySC.INV_X, y + ContainerQuarrySC.INV_Y + TOP, 9, 3);
            pockets(x + ContainerQuarrySC.INV_X, y + ContainerQuarrySC.INV_Y + TOP + 58, 9, 1);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The Quarry tab's four compartments as thin bars (a closed one hatched), (x, y) the window's corner. */
    private void tankBars(int x, int y) {
        int cap = Math.max(1, quarry.tankCapacity());
        for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
            int bx = x + 14 + i * 48, by = y + 203;
            drawRect(bx, by, bx + 44, by + 6, 0xFF04080C);
            if (i >= quarry.unlockedTanks()) {
                for (int k = 0; k < 42; k += 4) {
                    drawRect(bx + 1 + k, by + 1, bx + 3 + k, by + 5, 0xFF1A2430);
                }
                continue;
            }
            net.minecraftforge.fluids.FluidStack f = quarry.getTank(i).getFluid();
            if (f != null && f.amount > 0) {
                int fill = Math.max(1, Math.min(42, f.amount * 42 / cap));
                drawRect(bx + 1, by + 1, bx + 1 + fill, by + 5, 0xFF000000 | GuiTankGaugeSC.colourOf(f.getFluid()));
            }
        }
    }

    /** A locked slot: a dim well with a small padlock. */
    private void lock(int x, int y) {
        drawRect(x - 1, y - 1, x + 17, y + 17, 0xFF1A3444);
        drawRect(x, y, x + 16, y + 16, 0xFF0A1218);
        drawRect(x + 5, y + 7, x + 11, y + 12, 0xFF4A5A6A);
        drawRect(x + 6, y + 4, x + 10, y + 8, 0xFF4A5A6A);
        drawRect(x + 7, y + 5, x + 9, y + 7, 0xFF0A1218);
    }

    /**
     * The quarry side-on: the frame on the surface, the pit as wide as the area (of the widest,
     * 64) and as deep as it's got, the next layer lit, the drill on its gantry - moving while it digs.
     */
    private void quarryScene(int x, int y, int w, int h, float t) {
        drawRect(x, y, x + w, y + h, 0xFF1E3444);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0A1218);
        int gy = y + 16;
        drawRect(x + 1, gy, x + w - 1, y + h - 1, 0xFF3A3A40);
        for (int i = 0; i < 20; i++) {
            int sx = x + 4 + (i * 17) % (w - 8), sy = gy + 4 + (i * 11) % (h - 22);
            drawRect(sx, sy, sx + 2, sy + 1, 0xFF4A4A52);
        }
        drawRect(x + 1, gy - 2, x + w - 1, gy, 0xFF4A8A3A);
        int[] a = quarry.area();
        int size = a == null ? quarry.maxSize() : Math.max(a[2] - a[0] + 1, a[3] - a[1] + 1);
        int pw = Math.max(10, Math.min(w - 12, (int) ((w - 12) * Math.min(1F, size / 64F))));
        int px0 = x + w / 2 - pw / 2, px1 = px0 + pw;
        float dug = 0.1F;
        if (a != null && a[4] > a[5]) {
            dug = Math.max(0.1F, Math.min(1F, (a[4] - quarry.getLayerY()) / (float) (a[4] - a[5])));
        }
        int depth = Math.max(3, (int) ((h - 22) * dug));
        drawRect(px0, gy, px1, gy + depth, 0xFF0A1218);
        int[] ores = {0xFFD6A432, 0xFF6EE6FF, 0xFFE63C3C, 0xFF5AE66E};
        for (int k = 0; k < 4; k++) {
            int ox = x + 8 + k * 22, oy = Math.min(y + h - 5, gy + depth + 6 + (k % 2) * 6);
            drawRect(ox, oy, ox + 3, oy + 2, ores[k]);
        }
        drawRect(px0, gy + depth, px1, gy + depth + 1, 0xFF000000 | quarry.getColorPlane());
        drawRect(px0 - 2, gy - 10, px0, gy, 0xFF8A909A);
        drawRect(px1, gy - 10, px1 + 2, gy, 0xFF8A909A);
        drawRect(px0 - 2, gy - 11, px1 + 2, gy - 9, 0xFF8A909A);
        boolean digging = quarry.getStatus() == TileEntityQuarrySC.Status.RUNNING;
        int span = Math.max(1, pw - 4);
        int dx = px0 + 2 + (digging ? (int) (t * 0.5F) % span : span / 2);
        drawRect(dx, gy - 9, dx + 1, gy + depth - 1, 0xFFB0B8C4);
        drawRect(dx - 2, gy + depth - 4, dx + 3, gy + depth, 0xFFD6A432);
        if (digging && ((int) t / 4) % 2 == 0) {
            drawRect(dx - 3, gy + depth - 1, dx - 2, gy + depth, 0xFFFFE070);
            drawRect(dx + 3, gy + depth - 2, dx + 4, gy + depth - 1, 0xFFFFE070);
        }
    }

    /** The rig: a tower on the surface, its beam going down into the dark deep, ore chunks rising up it while it works. */
    private void rigScene(int x, int y, int w, int h, float t) {
        drawRect(x, y, x + w, y + h, 0xFF1E3444);
        drawRect(x + 1, y + 1, x + w - 1, y + h - 1, 0xFF0A1218);
        int gy = y + 20;
        for (int yy = gy; yy < y + h - 1; yy++) {
            drawRect(x + 1, yy, x + w - 1, yy + 1, GuiSceneSC.mix(0xFF3A3A40, 0xFF12080E, (yy - gy) / (float) (h - 21)));
        }
        drawRect(x + 1, gy - 2, x + w - 1, gy, 0xFF4A8A3A);
        int cx = x + w / 2;
        drawRect(cx - 8, gy - 16, cx + 8, gy - 2, 0xFF6A707A);
        drawRect(cx - 6, gy - 14, cx + 6, gy - 4, 0xFF3A2A5A);
        drawRect(cx - 2, gy - 12, cx + 2, gy - 6, 0xFFE05AF0);
        boolean on = quarry.getStatus() == TileEntityQuarrySC.Status.RUNNING;
        int beam = 0xFF000000 | quarry.getColorFrame();
        for (int yy = gy; yy < y + h - 2; yy++) {
            float k = on ? 0.5F + 0.5F * (float) Math.sin(yy * 0.4F - t * 0.3F) : 0.8F;
            drawRect(cx - 1, yy, cx + 2, yy + 1, GuiSceneSC.mix(0xFFFFFFFF, beam, on ? k : 0.9F));
        }
        if (on) {
            int[] ores = {0xFFD6A432, 0xFF6EE6FF, 0xFFE63C3C, 0xFF5AE66E};
            for (int i = 0; i < 4; i++) {
                int k = (int) ((t * 0.6F + i * 9) % (h - 24));
                int ox = cx - 4 + (i % 2) * 6, oy = y + h - 4 - k;
                drawRect(ox, oy, ox + 3, oy + 3, ores[i]);
            }
        }
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
        drawRect(ox, oz, ox + (int) (w * cell), oz + (int) (d * cell), 0xFF3A3A40);
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
        if (qx >= x0 && qx < x0 + MAP && qz >= y0 && qz < y0 + MAP) {
            drawRect(qx - 2, qz - 2, qx + 2, qz + 2, 0xFFFFE040);
        }
        int mx = ox + (int) ((mc.thePlayer.posX - a[0]) * cell), mz = oz + (int) ((mc.thePlayer.posZ - a[1]) * cell);
        if (mx >= x0 && mx < x0 + MAP && mz >= y0 && mz < y0 + MAP) {
            drawRect(mx - 2, mz - 2, mx + 2, mz + 2, 0xFFFF4040);
        }
    }

    // ------------------------------------------------------------------ drawing: texts

    private static final int C = GuiHoloSC.VALUE, DIM = GuiHoloSC.LABEL, CAP = GuiHoloSC.CYAN & 0xFFFFFF, INFO = 0x6EB4FF;

    /** A string that fits its room (smaller, or cut with the full text as a tooltip). */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    /** Small print (5/8 size, smaller still to fit, down to 0.4; the full text as a tooltip when shrunk that far). */
    private void small(String text, int x, int y, int maxW, int color) {
        int sw = fontRendererObj.getStringWidth(text);
        float k = Math.max(0.4F, Math.min(0.625F, maxW / (float) Math.max(1, sw)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
        if (sw * k > maxW + 1) {
            TextFitSC.hover(guiLeft + x, guiTop + y, maxW, 6, text);
        }
    }

    private void caption(String key, int x, int y) {
        fontRendererObj.drawString(Lang.tr(key), x, y, CAP);
    }

    /** A reading card's texts: the label small on top, the value under it. */
    private void cardText(int i, String label, String value) {
        int cx = CARDS[i][0], cy = CARDS[i][1];
        int lw = fontRendererObj.getStringWidth(label);
        float k = Math.min(0.5F, 38F / Math.max(1, lw));
        GL11.glPushMatrix();
        GL11.glTranslatef(cx + 21 - lw * k / 2F, cy + 2, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(label, 0, 0, DIM);
        GL11.glPopMatrix();
        TextFitSC.drawCentered(fontRendererObj, value, cx + 1, cy + 10, 40, C, false, guiLeft, guiTop);
    }

    private int statusColour(TileEntityQuarrySC.Status s) {
        switch (s) {
            case RUNNING: return GuiHoloSC.OK;
            case DONE: return INFO;
            case PAUSED:
            case DISABLED: return GuiHoloSC.IDLE;
            case REDSTONE:
            case REPAIRING:
            case WAITING_CHUNK: return GuiHoloSC.WARN;
            default: return GuiHoloSC.BAD;
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        ItemStack self = new ItemStack(com.sc.init.ModBlocks.quarrySC, 1, quarry.getBlockMetadata());
        TextFitSC.draw(fontRendererObj, self.getDisplayName(), 8, 5, W - 36, 0xF0F4FA, guiLeft, guiTop);
        String tier = quarry.getTier().name();
        fontRendererObj.drawString(tier, W - 15 - fontRendererObj.getStringWidth(tier) / 2, 5, quarry.getTier().ordinal() > 3 ? 0xF0E6FA : 0x282C34);
        power.drawGaugeOff(fontRendererObj);
        boolean inv = tab == 3 || tab == 5 || quarry.isExo() && tab == 2;
        if (inv) {
            fontRendererObj.drawString(Lang.tr("container.inventory"), ContainerQuarrySC.INV_X, ContainerQuarrySC.INV_Y + TOP - 10, 0x404040);
        }
        switch (tab) {
            case 0:
                if (quarry.isExo()) {
                    rigStatus();
                } else {
                    quarryStatus();
                }
                break;
            case 1:
                areaTab();
                break;
            case 2:
                if (quarry.isExo()) {
                    lenses();
                } else {
                    mapTab();
                }
                break;
            case TAB_TANKS:
                tanksTab();
                break;
            case 3:
                caption("sc.quarrygui.cap.output", 14, 38);
                small(Lang.tr("sc.quarrygui.filterlabel"), ContainerQuarrySC.FILTER_X, ContainerQuarrySC.FILTER_Y + TOP - 8, 150, DIM);
                TextFitSC.help(fontRendererObj, ContainerQuarrySC.FILTER_X + 153, ContainerQuarrySC.FILTER_Y + TOP - 11,
                        Lang.tr("sc.quarrygui.filter.help"), guiLeft, guiTop);
                small(Lang.tr("sc.quarrygui.buffer"), ContainerQuarrySC.BUFFER_X, ContainerQuarrySC.BUFFER_Y + TOP - 8, 150, DIM);
                break;
            case 4: {
                caption("sc.quarrygui.cap.functions", 14, 38);
                int on = 0, total = 0;
                for (int i = 0; i < TileEntityQuarrySC.FLAG_COUNT; i++) {
                    ItemQuarryModuleSC.Kind k = moduleOf(i);
                    boolean mine = quarry.isExo() ? !notForRig(i) : !rigOnly(i);
                    if (mine && (k == null || quarry.moduleCount(k) > 0)) {
                        total++;
                        on += (quarry.getFlags() & (1 << i)) != 0 ? 1 : 0;
                    }
                }
                small(Lang.tr("sc.quarrygui.fncount", on, total), 90, 40, 110, DIM);
                small(Lang.tr("sc.quarrygui.fn.grey"), 14, 248, 190, 0x465A6E);
                break;
            }
            case 5:
                modulesTab();
                break;
            default:
        }
    }

    /** The Quarry tab: status, the cards, what's left, the area, the totals; the pump and the water, the owner. */
    private void quarryStatus() {
        TileEntityQuarrySC.Status s = quarry.getStatus();
        caption("sc.quarrygui.cap.quarry", 14, 38);
        small(Lang.tr("sc.quarry.status." + s.name().toLowerCase(java.util.Locale.ROOT)), 58, 39, 44, statusColour(s));
        int[] a = quarry.area();
        long left = 0;
        double bps = quarry.blocksPerSecond();
        int pct = 0;
        if (a != null) {
            long total = (long) (a[4] - a[5] + 1) * (a[2] - a[0] + 1) * (a[3] - a[1] + 1);
            left = Math.min(total, container.blocksLeftClient);
            pct = total == 0 ? 100 : (int) ((total - left) * 100 / total);
        }
        cardText(0, Lang.tr("sc.quarrygui.card.layer"), a == null ? "-" : quarry.getLayerY() + " / " + a[5]);
        cardText(1, Lang.tr("sc.quarrygui.card.done"), pct + "%");
        cardText(2, Lang.tr("sc.quarrygui.card.cost"), quarry.getLastCost() + " EU");
        cardText(3, Lang.tr("sc.quarrygui.card.speed"), Lang.tr("sc.quarrygui.card.speedv", String.format(java.util.Locale.ROOT, "%.1f", bps)));
        if (a != null) {
            long sec = bps <= 0 ? 0 : (long) (left / bps);
            small(Lang.tr("sc.quarrygui.left2", String.valueOf(left), sec / 3600, sec / 60 % 60), 14, 96, 88, C);
            small(Lang.tr("sc.quarrygui.area2", a[2] - a[0] + 1, a[3] - a[1] + 1, quarry.maxSize(), a[4] - a[5] + 1), 14, 104, 88, DIM);
        }
        small(Lang.tr("sc.quarrygui.mined", String.valueOf(quarry.getMined())), 14, 112, 88, DIM);
        if (quarry.getSkippedPrivate() > 0) {
            small(Lang.tr("sc.quarrygui.skippedprivate", quarry.getSkippedPrivate()), 14, 120, 88, GuiHoloSC.WARN);
        }
        chunksLine(128);
        headCard();
        caption("sc.quarrygui.cap.pump", 14, 186);
        int used = 0;
        for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
            used += quarry.getTank(i).getFluidAmount() > 0 ? 1 : 0;
        }
        small(Lang.tr("sc.quarrygui.pumped", quarry.pumpedTotal(), used, quarry.unlockedTanks()), 100, 187, 102, DIM);
        for (int i = 0; i < TileEntityQuarrySC.TANKS; i++) {
            String label;
            if (i >= quarry.unlockedTanks()) {
                label = Lang.tr("sc.quarrygui.tankclosed");
            } else {
                net.minecraftforge.fluids.FluidStack f = quarry.getTank(i).getFluid();
                label = f != null && f.amount > 0 ? f.getLocalizedName() : Lang.tr("sc.quarrygui.tankshort", i + 1);
            }
            small(label, 14 + i * 48, 197, 44, i >= quarry.unlockedTanks() ? 0x465A6E : DIM);
        }
        small(Lang.tr("sc.quarrygui.water", quarry.getWater().getFluidAmount(), quarry.waterCapacity()), 14, 214, 188, DIM);
        owner();
    }

    /** The rig's Quarry tab: status, hauls and their cost, the draw, the totals, the water. */
    private void rigStatus() {
        TileEntityQuarrySC.Status s = quarry.getStatus();
        caption("sc.quarrygui.cap.rig", 14, 38);
        String st = s == TileEntityQuarrySC.Status.NO_AREA ? Lang.tr("sc.quarry.status.exo_nothing")
                : Lang.tr("sc.quarry.status." + s.name().toLowerCase(java.util.Locale.ROOT));
        small(st, 40, 39, 62, statusColour(s));
        double rate = quarry.haulsPerSecond();
        int cost = quarry.haulCost();
        cardText(0, Lang.tr("sc.quarrygui.card.hauls"), String.format(java.util.Locale.ROOT, "%.2f", rate));
        cardText(1, Lang.tr("sc.quarrygui.card.haul"), String.valueOf(cost));
        cardText(2, Lang.tr("sc.quarrygui.card.use"), String.valueOf((long) (cost * rate / 20)));
        cardText(3, Lang.tr("sc.quarrygui.card.lifted"), String.valueOf(quarry.getMined()));
        small(Lang.tr("sc.quarrygui.rig.line1"), 14, 96, 88, DIM);
        small(Lang.tr("sc.quarrygui.rig.line2"), 14, 104, 88, DIM);
        chunksLine(112);
        caption("sc.quarrygui.cap.wash", 14, 186);
        small(Lang.tr("sc.quarrygui.water", quarry.getWater().getFluidAmount(), quarry.waterCapacity()), 14, 208, 188, DIM);
        owner();
    }

    /** The chunk-keeping module at work: how many chunks it holds. */
    private void chunksLine(int y) {
        if (quarry.getChunksHeld() > 0) {
            small(Lang.tr("sc.quarrygui.chunks", quarry.getChunksHeld()), 14, y, 88, GuiHoloSC.OK);
        }
    }

    /** The head card beside the power mode button: which head, how worn. */
    private void headCard() {
        ItemStack h = quarry.getStackInSlot(TileEntityQuarrySC.SLOT_HEAD);
        String name = h == null ? Lang.tr("sc.quarrygui.nohead") : h.getDisplayName();
        if (h != null && h.getMaxDamage() > 0) {
            name += " " + (100 - h.getItemDamage() * 100 / h.getMaxDamage()) + "%";
        }
        TextFitSC.drawCentered(fontRendererObj, Lang.tr("sc.quarrygui.head", name), 112, 168, 88, h == null ? GuiHoloSC.WARN : C, false,
                guiLeft, guiTop);
    }

    private void owner() {
        if (!container.may(mc.thePlayer)) {
            small(Lang.tr("sc.quarrygui.owneronly"), 14, 230, 188, GuiHoloSC.BAD);
        }
        small(Lang.tr("sc.fieldgui.owner", quarry.getOwner().isEmpty() ? "-" : quarry.getOwner()), 14, 242, 188, 0x465A6E);
    }

    /** The Area tab: the five rows over their buttons, or the rig's note; the display column is buttons only. */
    private void areaTab() {
        if (quarry.isExo()) {
            caption("sc.quarrygui.cap.beam", 14, 38);
            fontRendererObj.drawSplitString(Lang.tr("sc.quarrygui.exo.noarea"), 14, 52, 92, DIM);
            return;
        }
        caption("sc.quarrygui.cap.area", 14, 38);
        String[] rows = {Lang.tr("sc.quarrygui.sizex", quarry.getSizeX(), quarry.maxSize()),
                Lang.tr("sc.quarrygui.sizez", quarry.getSizeZ(), quarry.maxSize()),
                Lang.tr("sc.quarrygui.offx", quarry.getOffX()), Lang.tr("sc.quarrygui.offz", quarry.getOffZ()),
                Lang.tr("sc.quarrygui.bottom", quarry.getBottomY())};
        for (int r = 0; r < rows.length; r++) {
            small(rows[r], 14, 53 + r * 26, 90, C);
        }
        if (quarry.usesCard()) {
            small(Lang.tr("sc.quarrygui.bycard"), 14, 224, 90, INFO);
        }
    }

    /** The Map tab's legend column: what's what, the scan, the ore found. */
    private void mapTab() {
        int lx = MAP_X + MAP + 6, room = SX + SW - 4 - lx;
        caption("sc.quarrygui.cap.map", lx, 38);
        legend(lx, 52, 0xFFFFE040, Lang.tr("sc.quarrygui.map.quarry"));
        legend(lx, 62, 0xFF000000 | quarry.getColorPlane(), Lang.tr("sc.quarrygui.map.drill"));
        legend(lx, 72, 0xFF1E232B, Lang.tr("sc.quarrygui.map.done"));
        legend(lx, 82, 0xFFFF4040, Lang.tr("sc.fieldgui.map.you"));
        if (!quarry.hasScanner()) {
            GL11.glPushMatrix();
            GL11.glTranslatef(lx, 96, 0F);
            GL11.glScalef(0.625F, 0.625F, 1F);
            fontRendererObj.drawSplitString(Lang.tr("sc.quarrygui.noscanner"), 0, 0, (int) (room / 0.625F), DIM);
            GL11.glPopMatrix();
            return;
        }
        int pct = quarry.getScanPercentClient();
        small(pct < 0 ? Lang.tr("sc.quarrygui.notscanned") : pct < 100 ? Lang.tr("sc.quarrygui.scanning", pct)
                : Lang.tr("sc.quarrygui.orefound"), lx, 96, room - 10, pct == 100 ? C : INFO);
        TextFitSC.help(fontRendererObj, SX + SW - 12, 94, Lang.tr("sc.quarrygui.scan.help", TileEntityQuarrySC.SCAN_COST), guiLeft, guiTop);
        int row = 0;
        RenderHelper.enableGUIStandardItemLighting();
        for (Map.Entry<String, Integer> e : quarry.getOreCounts().entrySet()) {
            if (row >= 7) {
                break;
            }
            ItemStack st = stackOf(e.getKey());
            int ry = 106 + row * 17;
            if (st != null) {
                itemRender.renderItemAndEffectIntoGUI(fontRendererObj, mc.getTextureManager(), st, lx, ry);
            }
            GL11.glDisable(GL11.GL_LIGHTING);
            fontRendererObj.drawString("x" + e.getValue(), lx + 18, ry + 4, C);
            row++;
        }
        RenderHelper.disableStandardItemLighting();
    }

    private void legend(int x, int y, int color, String text) {
        drawRect(x, y + 1, x + 5, y + 6, color);
        small(text, x + 8, y + 1, SX + SW - 12 - x, C);
    }

    /** The Upgrades tab: the grid (closed slots locked), the head / scanner / card, which tier opens what. */
    private void modulesTab() {
        caption("sc.quarrygui.cap.modules", 14, 38);
        small(Lang.tr("sc.quarrygui.slots", quarry.unlockedUpgrades(), TileEntityQuarrySC.UPGRADES), 68, 40, 90, DIM);
        TextFitSC.help(fontRendererObj, SX + SW - 14, 37, Lang.tr("sc.quarrygui.modules.hint"), guiLeft, guiTop);
        int ty = ContainerQuarrySC.TOOLS_Y + TOP;
        if (!quarry.isExo()) {
            String[] tools = {"head", "scanner", "card"};
            int[] xs = {ContainerQuarrySC.HEAD_X, ContainerQuarrySC.SCANNER_X, ContainerQuarrySC.CARD_X};
            for (int i = 0; i < 3; i++) {
                small(Lang.tr("sc.quarrygui.slot." + tools[i]), xs[i] - 1, ty + 19, 34, DIM);
            }
        }
        int tx = quarry.isExo() ? 14 : 122;
        small(Lang.tr(quarry.isExo() ? "sc.quarrygui.slots.rig" : "sc.quarrygui.slots.tiers"), tx, ty + 2, SX + SW - 6 - tx, DIM);
        small(Lang.tr("sc.quarrygui.slots.list"), tx, ty + 10, SX + SW - 6 - tx, 0x465A6E);
    }

    /** The Tanks tab's texts: the title, each gauge's label, the washing water. */
    private void tanksTab() {
        caption("sc.quarrygui.cap.tanks", 14, 38);
        small(Lang.tr("sc.quarrygui.tanks.title", quarry.unlockedTanks(), TileEntityQuarrySC.TANKS, quarry.tankCapacity()), 50, 39, 140, DIM);
        TextFitSC.help(fontRendererObj, SX + SW - 14, 37, Lang.tr("sc.quarrygui.tanks.help", TileEntityQuarrySC.TANK_PER_MODULE,
                TileEntityQuarrySC.CLEAR_MB_PER_EU), guiLeft, guiTop);
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
            small(text, GAUGE_X[i] - 2, 51, 38, i >= quarry.unlockedTanks() ? 0x465A6E : C);
        }
        small(Lang.tr("sc.quarrygui.wash"), WASH_X - 2, 51, 36, INFO);
        small(String.valueOf(quarry.getWater().getFluidAmount()) + " /", WASH_X - 2, 160, 36, DIM);
        small(Lang.tr("sc.quarrygui.wash.cap", quarry.waterCapacity()), WASH_X - 2, 167, 36, DIM);
    }

    private static final String[] TIER_NAMES = {"LV", "MV", "HV", "EV"};

    /** The rig's Lenses tab: the lens slots, and what comes up how often (the lenses and filter counted in). */
    private void lenses() {
        caption("sc.quarrygui.cap.lenses", 14, 38);
        TextFitSC.help(fontRendererObj, 78, 37, Lang.tr("sc.quarrygui.exo.lenses.help"), guiLeft, guiTop);
        int total = Math.max(1, quarry.totalWeight());
        List<com.sc.machine.ExoOreTableSC.Entry> list = new ArrayList<com.sc.machine.ExoOreTableSC.Entry>(quarry.exoEntries());
        java.util.Collections.sort(list, new java.util.Comparator<com.sc.machine.ExoOreTableSC.Entry>() {
            @Override
            public int compare(com.sc.machine.ExoOreTableSC.Entry a, com.sc.machine.ExoOreTableSC.Entry b) {
                return quarry.weightOf(b) - quarry.weightOf(a);
            }
        });
        int x = 92, y = 38;
        small(Lang.tr("sc.quarrygui.exo.chances"), x, y + 1, SX + SW - 6 - x, CAP);
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
            small(pct + (lens > 0 ? "  x" + (1 + quarry.lensBoost() * lens) : "") + "  " + e.ore.getDisplayName(),
                    x + 18, ry + 5, SX + SW - 6 - x - 18, lens > 0 ? GuiHoloSC.OK : C);
            RenderHelper.enableGUIStandardItemLighting();
            shown++;
        }
        RenderHelper.disableStandardItemLighting();
        small(Lang.tr("sc.quarrygui.exo.lensgreen"), 14, 94, 72, DIM);
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
        List<String> tip = power.tooltip(mouseX - guiLeft, mouseY - guiTop);
        if (tip == null) {
            tip = battery.tooltip(mouseX - guiLeft, mouseY - guiTop, quarry.getStackInSlot(TileEntityQuarrySC.SLOT_BATTERY));
        }
        if (tip == null) {
            tip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        }
        if (tip == null) {
            tip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    /**
     * A module in a module slot: when there are more of its kind than work (an old world's stack past
     * the slot's limit, or the same module in two slots), the tooltip says how many do.
     */
    /** ItemQuarryModuleSC's tooltip line "N of M work" for a module stack in this screen's upgrade slots (null when not over the limit). */
    public static String moduleWorkingLine(ItemStack stack) {
        net.minecraft.client.gui.GuiScreen screen = net.minecraft.client.Minecraft.getMinecraft().currentScreen;
        if (!(screen instanceof GuiQuarrySC) || stack == null || !(stack.getItem() instanceof ItemQuarryModuleSC)) {
            return null;
        }
        GuiQuarrySC g = (GuiQuarrySC) screen;
        if (g.tab != 5) {
            return null;
        }
        boolean inSlot = false;
        for (int i = ContainerQuarrySC.FIRST_UPGRADE; i < ContainerQuarrySC.FIRST_TOOLS; i++) {
            if (((net.minecraft.inventory.Slot) g.container.inventorySlots.get(i)).getStack() == stack) {
                inSlot = true;
                break;
            }
        }
        ItemQuarryModuleSC.Kind k = ItemQuarryModuleSC.kindOf(stack);
        int total = 0;
        for (int i = TileEntityQuarrySC.FIRST_UPGRADE; i < TileEntityQuarrySC.FIRST_UPGRADE + TileEntityQuarrySC.UPGRADES; i++) {
            ItemStack s = g.quarry.getStackInSlot(i);
            if (s != null && s.getItem() instanceof ItemQuarryModuleSC && ItemQuarryModuleSC.kindOf(s) == k) {
                total += s.stackSize;
            }
        }
        return inSlot && total > k.max ? "§e" + Lang.tr("sc.quarrygui.module.working", k.max, total) : null;
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

    private boolean over(GuiButton b, int mx, int my) {
        return GuiGaugeSC.isOver(b.xPosition - guiLeft, b.yPosition - guiTop, b.width, b.height, mx, my);
    }

    /** Tooltips, (mx, my) from the window's corner. */
    private List<String> tooltipAt(int mx, int my) {
        List<String> lines = new ArrayList<String>();
        if (GuiGaugeSC.isOver(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mx, my)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(quarry.getEnergyStored() + " / " + quarry.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.input", quarry.inputTier().name(), quarry.inputTier().getVoltage()));
            return lines;
        }
        if (tab == 5) {
            for (int i = quarry.unlockedUpgrades(); i < TileEntityQuarrySC.UPGRADES; i++) {
                if (GuiGaugeSC.isOver(ContainerQuarrySC.UPGRADE_X - 1 + i % 9 * 18, ContainerQuarrySC.UPGRADE_Y + TOP - 1 + i / 9 * 18,
                        18, 18, mx, my)) {
                    lines.add(Lang.tr("sc.quarrygui.locked", TileEntityQuarrySC.tierUnlocking(i).name()));
                    return lines;
                }
            }
        }
        if (tab == TAB_TANKS) {
            for (Object o : buttonList) {
                GuiButton b = (GuiButton) o;
                if (b.id == B_WASH_FEED && over(b, mx, my)) {
                    lines.add(Lang.tr("sc.quarrygui.wash.feed.tip", onOff(quarry.isWashFromTanks())));
                    lines.add("§7" + Lang.tr("sc.quarrygui.wash.feed.help", TileEntityQuarrySC.AUTO_OUT));
                    return lines;
                }
                if (b.id >= B_FVEIN && b.id <= B_FVEIN_FLOWING && over(b, mx, my)) {
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
                if (clear && over(b, mx, my)) {
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
                if (b.id >= B_FLAG && b.id < B_FLAG + TileEntityQuarrySC.FLAG_COUNT && over(b, mx, my)) {
                    int i = b.id - B_FLAG;
                    boolean on = (quarry.getFlags() & (1 << i)) != 0;
                    lines.add(Lang.tr("sc.quarrygui.flag." + i) + ": " + onOff(on));
                    lines.add(i == 24
                            ? Lang.tr("sc.quarrygui.flag.24.hint", TileEntityQuarrySC.REPAIR_PER_TICK, TileEntityQuarrySC.REPAIR_COST)
                            : Lang.tr("sc.quarrygui.flag." + i + ".hint"));
                    if ((1 << i) == TileEntityQuarrySC.F_SKIP_TILES) {
                        lines.add("§e" + Lang.tr("sc.quarrygui.flag.13.warn"));   // off: other mods' blocks may lose what they hold
                    }
                    ItemQuarryModuleSC.Kind k = moduleOf(i);
                    if (quarry.isExo() && notForRig(i)) {
                        lines.add("§c" + Lang.tr("sc.quarrygui.exo.notused"));
                    } else if (!quarry.isExo() && rigOnly(i)) {
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

    private static final net.minecraft.client.renderer.entity.RenderItem itemRender = new net.minecraft.client.renderer.entity.RenderItem();

    /** A palette colour button, framed as the holo screen's. */
    private static class Swatch extends GuiButton {
        private final int color;

        Swatch(int id, int x, int y, int color) {
            super(id, x, y, 11, 11, "");
            this.color = color;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            boolean over = mx >= xPosition && my >= yPosition && mx < xPosition + width && my < yPosition + height;
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, over ? 0xFF6EE6FF : 0xFF1E3444);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, 0xFF000000 | color);
        }
    }

    /** A colour channel's slider in the holo style: a dark well filled in the channel's colour, a light knob. */
    private static class HoloSlider extends GuiSlider {
        private final int tint;

        HoloSlider(int id, int x, int y, int w, int h, String prefix, int value, int tint) {
            super(id, x, y, w, h, prefix, "", 0, 255, value, false, true);
            this.tint = tint;
        }

        @Override
        public void drawButton(Minecraft mc, int mx, int my) {
            if (!visible) {
                return;
            }
            if (dragging) {
                sliderValue = (mx - (xPosition + 4)) / (float) (width - 8);
                updateSlider();
            }
            drawRect(xPosition, yPosition, xPosition + width, yPosition + height, 0xFF1E3444);
            drawRect(xPosition + 1, yPosition + 1, xPosition + width - 1, yPosition + height - 1, 0xFF04080C);
            int fill = (int) ((width - 2) * sliderValue);
            drawRect(xPosition + 1, yPosition + 1, xPosition + 1 + fill, yPosition + height - 1, GuiSceneSC.mix(tint, 0xFF000000, 0.35F));
            int kx = xPosition + (int) ((width - 3) * sliderValue);
            drawRect(kx, yPosition, kx + 3, yPosition + height, 0xFFD8E0E8);
            String text = displayString;
            mc.fontRenderer.drawStringWithShadow(text, xPosition + (width - mc.fontRenderer.getStringWidth(text)) / 2,
                    yPosition + (height - 8) / 2, 0xE6F0FA);
        }

        @Override
        protected void mouseDragged(Minecraft mc, int x, int y) {
        }
    }
}
