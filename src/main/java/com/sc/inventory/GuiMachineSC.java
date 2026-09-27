package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.machine.MachineStatus;
import com.sc.machine.RecipeRegistry;
import com.sc.machine.UpgradeType;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityMachineSC;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;

/**
 * Generic GUI for every machine (§13), the large holo-screen layout (GuiBigSC, 248 x 232). On
 * the screen: the three input and three output slots in cyan pockets with a segmented progress
 * bar and the status between them (heat under it for heat-capable machines); on the right what
 * the machine does - its caption, and either its animated pictogram at double size with the
 * numbers (progress, energy use, input voltage) or, for a machine with tanks, the full-size tank
 * gauges (only the tanks it uses or that hold something) with the numbers beside them if they
 * leave room. The tall energy gauge stands right of the screen; the four upgrade slots are a row
 * inside the window with what they do; the player's inventory is centred below. Every gauge
 * has a hover tooltip with the actual numbers.
 *
 * All of these read the TileEntity directly; ContainerMachineSC is what keeps the client's copy
 * of those fields current (see its detectAndSendChanges).
 */
public class GuiMachineSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");

    /** Slot rows (ContainerMachineSC places the slots with slotX): closer together for a machine with tanks. */
    public static final int IN_Y = 30, OUT_Y = 88;
    /** The progress bar (NEI: a click on it opens this machine's recipes), the status, the heat bar. */
    public static final int PROGRESS_X = 14, PROGRESS_Y = 58, PROGRESS_W = 60, PROGRESS_H = 6;
    private static final int STATUS_Y = 66, HEAT_Y = 83, HEAT_H = 3;
    /** Without tanks: the picture window (a scene, or the pictogram) and two columns of numbers under it. */
    private static final int SCENE_Y = 38, SCENE_H = 40, STATS_Y = 82;
    /** The right-hand block starts here; tanks 31 x 70 each, 33 apart. */
    private static final int RIGHT_TANKS = 76, RIGHT_PLAIN = 88, CAPTION_Y = 25, TANK_Y = 42, TANK_LABEL_Y = 32, TANK_GAP = 33;

    private final TileEntityMachineSC machine;
    /** The energy gauge sits 12 px lower: the power switch and the redstone mode button above it (GuiPowerSC). */
    private static final int MG_Y = GuiPowerSC.GAUGE_Y, MG_H = GuiPowerSC.GAUGE_H;
    private final GuiPowerSC power;

    /** For NEI: which machine's recipe page the progress-bar click should open. */
    public com.sc.machine.MachineType getMachineType() {
        return machine.getMachineType();
    }
    /** Which of the four tanks any recipe of this machine type ever uses - fixed per type. */
    private final boolean[] tankUsed = new boolean[ContainerMachineSC.TANK_COUNT];
    private final boolean withTanks;
    /** The Ore Washer's own screen: its washing tub shows the water tank, the full tank gauge beside it. */
    private final boolean washer;
    /** The Blast Furnace's own screen: the furnace and a thermometer, the zoned heat bar under them, its tank. */
    private final boolean blast;
    /** The Wire Saw's own screen: the saw, the wafers lighting up with the progress, the wire's wear, its water. */
    private final boolean saw;
    /** The Dicing Saw: the Wire Saw's screen, the wafer cut into dies from above, a diamond blade's wear. */
    private final boolean dice;
    /** The Packager's own screen: the assembly scene, its four stages, the product and its pins. */
    private final boolean pack;
    /** The Centrifuge's own screen: the rotor, and what comes out with each product's chance as a bar. */
    private final boolean cent;
    /** The Chlor-alkali Electrolyzer: its recipe badges, water -> the cell -> two product tanks. */
    private final boolean elec;
    /** The Air Separator: its two modes (air, water) as badges, water -> the column -> two product tanks. */
    private final boolean air;
    /** The Refinery: its products as a row (the ones made now lit), crude -> the tower -> the product, the catalyst frame. */
    private final boolean refi;
    /** The Rolling Machine: its seven molds as badges (the one in the slot lit), the mill, the product, the mold's wear. */
    private final boolean roll;
    /** The Upgrade Stations: the tier ladder (Nano > Quantum > Exo, or chip I > II > III), the bench, what goes in and out. */
    private final boolean station;
    /** The Kiln: its five products as badges, the kiln wide, the firing curve under it with its stages. */
    private final boolean kiln;
    /** The Fluid Cell Filler: its two fluids as badges, the filler, a row of the capsules the tank still fills. */
    private final boolean fill;
    /** The Boilers: the fuel badges (coal, diesel), water + diesel tanks -> the boiler -> steam, a pressure dial. */
    private final boolean boil;
    private static final int BOIL_X = 140, BOIL_W = 33, BOIL_Y = 36, BOIL_H = 56, BOIL_BADGE_W = 31;
    private static final int FILL_Y = 36, FILL_H = 42, FILL_ROW_Y = 81, FILL_BADGE_W = 40;
    private static final int KILN_X = 88, KILN_Y = 37, KILN_H = 36, KILN_CURVE_Y = 75, KILN_CURVE_H = 18, KILN_BADGE_W = 22;
    private static final int[] KILN_COLOURS = {0xFF6A6A72, 0xFF3A3A3A, 0xFFB04A2A, 0xFFE8E0D0, 0xFF9AA0B8};
    private static final int ST_X = 88, ST_STEP_W = 36, ST_Y = 40, ST_H = 44;
    private static final int[] ST_GEAR = {0xFF4A8AD8, 0xFF9A5AE0, 0xFF3AD8A0}, ST_CHIP = {0xFF8A94A8, 0xFF4AA8E8, 0xFFE8C850};
    private static final com.sc.util.SCToolType[] ROLL_MOLDS = {com.sc.util.SCToolType.MOLD_PLATE, com.sc.util.SCToolType.MOLD_COIL,
            com.sc.util.SCToolType.MOLD_BLADE, com.sc.util.SCToolType.MOLD_TARGET, com.sc.util.SCToolType.MOLD_LEAD_FRAME_3,
            com.sc.util.SCToolType.MOLD_LEAD_FRAME_16, com.sc.util.SCToolType.MOLD_LEAD_FRAME_40};
    private static final int ROLL_X = 88, ROLL_BADGE_W = 28, ROLL_Y = 42, ROLL_H = 40, ROLL_WEAR_X = 116, ROLL_WEAR_Y = 94, ROLL_WEAR_W = 56;
    private static final int REFI_TOWER_X = 112, REFI_TOWER_W = 34, REFI_CAT_X = 148, REFI_CAT_Y = 66, REFI_BADGE_W = 24;
    private static final int[] REFI_COLOURS = {0xFFD8A040, 0xFF5A5A5A, 0xFFE07A30, 0xFFC8D0DC, 0xFFF0F0F0};
    private static final int AIR_BADGE_W = 64;
    private static final int ELEC_CELL_W = 32, ELEC_BADGE_W = 42;
    private static final String[] ELEC_BADGES = {"NaOH+Cl2", "F2", "H2+D"};
    private static final int CENT_X = 88, CENT_Y = 36, CENT_S = 50, CENT_LIST_X = 142, CENT_BAR_W = 62;
    private static final int PACK_X = 88, PACK_Y = 36, PACK_W = 76, PACK_H = 50, PACK_STAGE_X = 167, PACK_STAGE_W = 39;
    /** The Oxidation Furnace's own screen: its two modes as badges, the tube furnace, the wafers taking colour, its oxygen. */
    private final boolean oxid;
    /** The Photoresist Coater's own screen: the spin coater from above, its four stages, the film's cover, its resist. */
    private final boolean coat;
    /** The Stepper's own screen: its column and the die map, the photomask's wear, the zoned heat bar. */
    private final boolean step;
    /** The Ion Implanter's own screen: the dopant badges (P, B, As), the beam line, the heat bar, its gas. */
    private final boolean ion;
    /** The Sputterer's own screen: the target badges (Cu, Al, W), the chamber, the target's wear, its argon. */
    private final boolean sput;
    private static final String[] SPUT_BADGES = {"Cu", "Al", "W"};
    private static final int[] SPUT_COLOURS = {0xFFD8844A, 0xFFC8D0DC, 0xFF8A8A9A};
    private static final int SPUT_WEAR_X = 116, SPUT_WEAR_Y = 99, SPUT_WEAR_W = 56;
    private static final int ION_BADGE_W = 26, ION_HEAT_Y = 100;
    private static final String[] ION_BADGES = {"P · n", "B · p", "As · n"};
    private static final int STEP_W = 40, STEP_H = 44, MAP_X = 132, MAP_R = 17, MASK_Y = 82, STEP_HEAT_Y = 90;
    private static final int COAT_W = 48, COAT_H = 50, STAGE_X = 141, STAGE_W = 31, STAGE_Y = 38, STAGE_GAP = 12;
    private static final int OX_Y = 36, OX_H = 38, OX_WAFER_Y = 79, OX_BADGE_W = 40;
    private static final int SAW_X = 90, SAW_Y = 36, SAW_W = 82, SAW_H = 42, WAFER_Y = 82, WEAR_X = 124, WEAR_Y = 102, WEAR_W = 48;
    /** The Czochralski Puller's own screen: as the Blast Furnace's, the puller in place of the furnace. */
    private final boolean puller;
    /** The Chemical Reactor's own screen: its recipe as a formula, tank + tank -> flask -> tank. */
    private final boolean chem;
    private static final int CHEM_X = 76, CHEM_IN2 = 32, CHEM_FLASK = 64, CHEM_OUT = 97, CHEM_FLASK_W = 30, CHEM_FLASK_H = 50;
    /** The CVD Chamber's own screen: its recipe as a formula, tank -> the Siemens bell jar <- tank. */
    private final boolean cvd;
    /** The Etching Bath's own screen, laid out as the CVD Chamber's: tank -> two baths <- tank. */
    private final boolean etch;
    private static final int CVD_BELL_X = CHEM_X + 33, CVD_BELL_W = 64, CVD_BELL_H = 50, CVD_TANK2_X = CHEM_X + 99;
    /** Screens with tanks of their own (Chemical Reactor, CVD Chamber): which tanks, and where; null for the others. */
    private final int[] ownTanks, ownTankX;
    private static final int FURNACE_X = 90, FURNACE_Y = 36, FURNACE_W = 64, FURNACE_H = 44, THERMO_X = 158,
            HEATBAR_X = 90, HEATBAR_Y = 91, HEATBAR_W = 80, HEATBAR_H = 4;
    private static final int TUB_X = 90, TUB_Y = 36, TUB_W = 82, TUB_H = 50, WATER_X = 175, WATER_Y = 40;
    /** Tanks shown this frame (used by the type or holding fluid), left to right. */
    private final int[] shownTanks = new int[ContainerMachineSC.TANK_COUNT];
    private int shownCount;

    public GuiMachineSC(InventoryPlayer playerInv, TileEntityMachineSC machine) {
        super(new ContainerMachineSC(playerInv, machine));
        this.machine = machine;
        this.power = new GuiPowerSC(machine, ContainerMachineSC.BTN_POWER, ContainerMachineSC.BTN_REDSTONE);
        for (int i = 0; i < tankUsed.length; i++) {
            tankUsed[i] = RecipeRegistry.usesTank(machine.getMachineType(), i);
        }
        withTanks = ContainerMachineSC.tightSlots(machine.getMachineType());
        washer = machine.getMachineType() == com.sc.machine.MachineType.ORE_WASHER;
        puller = machine.getMachineType() == com.sc.machine.MachineType.CZOCHRALSKI_PULLER
                || machine.getMachineType() == com.sc.machine.MachineType.CZOCHRALSKI_PULLER_EV;
        blast = machine.getMachineType() == com.sc.machine.MachineType.BLAST_FURNACE || puller;
        dice = machine.getMachineType() == com.sc.machine.MachineType.DICING_SAW;
        pack = machine.getMachineType() == com.sc.machine.MachineType.PACKAGER;
        cent = machine.getMachineType() == com.sc.machine.MachineType.CENTRIFUGE;
        elec = machine.getMachineType() == com.sc.machine.MachineType.CHLOR_ALKALI_ELECTROLYZER;
        air = machine.getMachineType() == com.sc.machine.MachineType.AIR_SEPARATOR;
        refi = machine.getMachineType() == com.sc.machine.MachineType.REFINERY;
        roll = machine.getMachineType() == com.sc.machine.MachineType.ROLLING_MACHINE;
        kiln = machine.getMachineType() == com.sc.machine.MachineType.KILN;
        fill = machine.getMachineType() == com.sc.machine.MachineType.FLUID_CELL_FILLER;
        boil = machine.getMachineType() == com.sc.machine.MachineType.BOILER_LV || machine.getMachineType() == com.sc.machine.MachineType.BOILER_MV;
        station = machine.getMachineType() == com.sc.machine.MachineType.UPGRADE_STATION_MV
                || machine.getMachineType() == com.sc.machine.MachineType.UPGRADE_STATION_HV
                || machine.getMachineType() == com.sc.machine.MachineType.UPGRADE_STATION_EV;
        saw = machine.getMachineType() == com.sc.machine.MachineType.WIRE_SAW || dice;
        oxid = machine.getMachineType() == com.sc.machine.MachineType.OXIDATION_FURNACE;
        coat = machine.getMachineType() == com.sc.machine.MachineType.PHOTORESIST_COATER;
        step = machine.getMachineType() == com.sc.machine.MachineType.STEPPER
                || machine.getMachineType() == com.sc.machine.MachineType.STEPPER_EV;
        ion = machine.getMachineType() == com.sc.machine.MachineType.ION_IMPLANTER;
        sput = machine.getMachineType() == com.sc.machine.MachineType.SPUTTERER;
        chem = machine.getMachineType() == com.sc.machine.MachineType.CHEM_REACTOR;
        cvd = machine.getMachineType() == com.sc.machine.MachineType.CVD_CHAMBER;
        etch = machine.getMachineType() == com.sc.machine.MachineType.ETCHING_BATH;
        ownTanks = chem || boil ? new int[]{0, 1, 2} : cvd || etch ? new int[]{0, 1} : elec || air ? new int[]{0, 2, 3} : refi ? new int[]{0, 2} : null;
        ownTankX = chem ? new int[]{CHEM_X, CHEM_X + CHEM_IN2, CHEM_X + CHEM_OUT} : boil ? new int[]{CHEM_X, CHEM_X + 32, WATER_X} : cvd || etch ? new int[]{CHEM_X, CVD_TANK2_X}
                : elec || air ? new int[]{CHEM_X, WATER_X - 33, WATER_X} : refi ? new int[]{CHEM_X, WATER_X} : null;
        xSize = GuiBigSC.W;
        ySize = GuiBigSC.H;
    }

    @Override
    public void initGui() {
        super.initGui();
        buttonList.clear();
        for (int i = 0; i < ContainerMachineSC.TANK_COUNT; i++) {
            buttonList.add(new GuiBigSC.ClearButton(ContainerMachineSC.BTN_CLEAR + i));
        }
        power.addButtons(buttonList, guiLeft, guiTop);
    }

    /** The Clear buttons sit above the tanks shown (the Ore Washer: above its Water gauge). */
    @Override
    public void updateScreen() {
        super.updateScreen();
        collectTanks();
        for (Object o : buttonList) {
            if (!(o instanceof GuiBigSC.ClearButton)) {
                continue;
            }
            GuiBigSC.ClearButton b = (GuiBigSC.ClearButton) o;
            int tank = b.id - ContainerMachineSC.BTN_CLEAR;
            int gx = -1;
            if (washer || blast || saw || oxid || coat || step || ion || sput || fill) {
                gx = tank == 0 ? WATER_X : -1;
            } else if (ownTanks != null) {
                for (int k = 0; k < ownTanks.length; k++) {
                    if (ownTanks[k] == tank) {
                        gx = ownTankX[k];
                    }
                }
            } else {
                for (int k = 0; k < shownCount; k++) {
                    if (shownTanks[k] == tank) {
                        gx = rightX() + k * TANK_GAP;
                    }
                }
            }
            b.visible = gx >= 0;
            b.xPosition = guiLeft + gx + GuiTankGaugeSC.WIDTH - 7;
            b.yPosition = guiTop + 31;
            b.enabled = machine.getTank(tank).getFluidAmount() > 0 && machine.getEnergyStored() >= machine.clearCost(tank);
        }
    }

    @Override
    protected void actionPerformed(net.minecraft.client.gui.GuiButton button) {
        if (!power.allowClick(button)) {
            return;
        }
        mc.playerController.sendEnchantPacket(inventorySlots.windowId, button.id);
    }

    private static int slotX(boolean tanks, int i) {
        return ContainerMachineSC.slotX(tanks, i);
    }

    private int barW() {
        return withTanks ? 52 : PROGRESS_W;
    }

    private int rightX() {
        return withTanks ? RIGHT_TANKS : RIGHT_PLAIN;
    }

    /** The best byproduct chance of the recipe the inputs make right now ("-" without one). */
    private String bonusChance() {
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        com.sc.machine.MachineRecipe r = RecipeRegistry.findMatch(machine.getMachineType(), in, machine.getTank(0).getFluid(),
                machine.getTank(1).getFluid());
        float best = 0F;
        if (r != null) {
            for (float c : r.byproductChances) {
                best = Math.max(best, c);
            }
        }
        return best > 0F ? Math.round(best * 100) + "%" : "-";
    }

    /** Small caption text (5/8 size), foreground coordinates. */
    private void small(String text, int x, int y, int color) {
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(0.625F, 0.625F, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /** Beside the tanks the pictogram shrinks to its own size, top right - if the tanks leave it room. */
    private boolean smallIconFits() {
        return rightX() + shownCount * TANK_GAP <= GuiBigSC.SCREEN_RIGHT - 17;
    }

    private boolean heat() {
        return machine.getMachineType().heatCapable;
    }

    private void collectTanks() {
        shownCount = 0;
        if (washer || blast || saw || oxid || coat || step || ion || sput || fill || ownTanks != null) {
            return;                                                     // its tanks have places of their own
        }
        for (int i = 0; i < ContainerMachineSC.TANK_COUNT; i++) {
            if (tankUsed[i] || machine.getTank(i).getFluidAmount() > 0) {
                shownTanks[shownCount++] = i;
            }
        }
        // no more than fit in the right-hand block
        shownCount = Math.min(shownCount, (GuiBigSC.SCREEN_RIGHT - rightX() + 2) / TANK_GAP);
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        int x = guiLeft, y = guiTop;
        GuiBigSC.window(x, y, true, TileEntityMachineSC.UPGRADE_SLOTS);
        collectTanks();
        GuiHoloSC.screen(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        for (int i = 0; i < 3; i++) {
            GuiHoloSC.slot(x + slotX(withTanks, i), y + IN_Y, false);
            GuiHoloSC.slot(x + slotX(withTanks, i), y + OUT_Y, machine.getStackInSlot(TileEntityMachineSC.INPUT_SLOTS + i) != null);
            int ax = x + slotX(withTanks, i) + 6;                     // little arrows: the way items go
            drawRect(ax, y + 52, ax + 4, y + 53, GuiHoloSC.CYAN_DIM);
            drawRect(ax + 1, y + 53, ax + 3, y + 54, GuiHoloSC.CYAN_DIM);
        }
        int ticks = machine.getCurrentRecipeTicks();
        float progress = ticks > 0 ? (float) machine.getProgressTicks() / ticks : 0F;
        GuiHoloSC.bar(x + PROGRESS_X, y + PROGRESS_Y, barW(), PROGRESS_H, progress, 12,
                machine.getStatus() == MachineStatus.PROCESSING ? 0xFFFF8C1E : 0xFF6E7C8C);
        if (heat() && !blast && !step && !ion) {
            GuiHoloSC.bar(x + PROGRESS_X, y + HEAT_Y, barW(), HEAT_H, (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity(),
                    12, 0xFFFF5A3C);
        }
        int rx = rightX();
        drawRect(x + rx - 4, y + GuiBigSC.SCREEN_Y + 6, x + rx - 3, y + GuiBigSC.SCREEN_Y + GuiBigSC.SCREEN_H - 6, 0xFF1E3444);

        GuiGaugeSC.bind(mc, TEXTURE);
        if (boil) {                                                     // the fuel badges, the boiler, the dial
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int fuel = boilFuel();
            for (int i = 0; i < 2; i++) {
                int bx = x + CHEM_X + i * (BOIL_BADGE_W + 2), by = y + CAPTION_Y - 3;
                drawRect(bx, by, bx + BOIL_BADGE_W, by + 8, i == fuel ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + BOIL_BADGE_W - 1, by + 7, i == fuel ? 0xFF0E3A50 : 0xFF0A1218);
            }
            GuiSceneSC.boiler(x + BOIL_X, y + BOIL_Y, BOIL_W, BOIL_H, t, machine.getStatus() == MachineStatus.PROCESSING, fuel != 1);
            FluidTank steam = machine.getTank(2);
            GuiSceneSC.dial(x + BOIL_X + 8, y + BOIL_Y + BOIL_H + 9, 7,
                    steam.getCapacity() > 0 ? (float) steam.getFluidAmount() / steam.getCapacity() : 0F);
        } else if (refi) {                                              // the products, the flow sheet, the catalyst
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING, pt = refiCatalyst();
            for (int i = 0; i < 5; i++) {
                boolean on = pt == i >= 2;
                int bx = x + CHEM_X + i * (REFI_BADGE_W + 2), by = y + CAPTION_Y - 2;
                drawRect(bx, by, bx + REFI_BADGE_W, by + 11, on ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + REFI_BADGE_W - 1, by + 10, on ? 0xFF0E3A50 : 0xFF0A1218);
                drawRect(bx + 2, by + 8, bx + REFI_BADGE_W - 2, by + 10, on ? REFI_COLOURS[i] : GuiSceneSC.mix(REFI_COLOURS[i], 0xFF0A1218, 0.65F));
            }
            int product = pt ? REFI_COLOURS[2] : REFI_COLOURS[0];
            drawRect(x + CHEM_X + GuiTankGaugeSC.WIDTH, y + 76, x + REFI_TOWER_X, y + 78, 0xFF8A5A2A);         // crude in
            drawRect(x + REFI_TOWER_X + REFI_TOWER_W, y + 58, x + WATER_X, y + 60, product);                   // the product out
            if (running) {
                for (int i = 0; i < 3; i++) {
                    int d = (int) ((t * 1.3F + i * 9) % (WATER_X - REFI_TOWER_X - REFI_TOWER_W));
                    drawRect(x + REFI_TOWER_X + REFI_TOWER_W + d, y + 58, x + REFI_TOWER_X + REFI_TOWER_W + d + 2, y + 60, 0xFFFFF0C0);
                }
            }
            GuiSceneSC.refineryTower(x + REFI_TOWER_X, y + TANK_Y, REFI_TOWER_W, CHEM_FLASK_H, t, running, pt ? 1 : 2);
            int cx = x + REFI_CAT_X, cy = y + REFI_CAT_Y;
            drawRect(cx, cy, cx + 22, cy + 22, pt ? 0xFFE8C850 : 0xFF3A4450);                                  // the catalyst frame
            drawRect(cx + 1, cy + 1, cx + 21, cy + 21, 0xFF0A1218);
            if (pt) {
                for (int k = 0; k < 5; k++) {
                    int px = cx + 6 + (k * 3) % 9, py = cy + 7 + (k * 4) % 8;
                    drawRect(px, py, px + 3, py + 3, 0xFFD8D8E0);
                }
            }
        } else if (air) {                                               // the mode badges, the column between the tanks
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int mode = airMode();
            for (int i = 0; i < 2; i++) {
                int bx = x + CHEM_X + i * (AIR_BADGE_W + 2), by = y + CAPTION_Y - 1;
                drawRect(bx, by, bx + AIR_BADGE_W, by + 9, i == mode ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + AIR_BADGE_W - 1, by + 8, i == mode ? 0xFF0E3A50 : 0xFF0A1218);
            }
            GuiSceneSC.airColumn(x + CHEM_X + 33, y + TANK_Y, ELEC_CELL_W, CHEM_FLASK_H, t, machine.getStatus() == MachineStatus.PROCESSING, progress);
        } else if (elec) {                                              // the badges, the cell between the tanks
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int mode = elecMode();
            for (int i = 0; i < 3; i++) {
                int bx = x + CHEM_X + i * (ELEC_BADGE_W + 2), by = y + CAPTION_Y - 1;
                drawRect(bx, by, bx + ELEC_BADGE_W, by + 9, i == mode ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + ELEC_BADGE_W - 1, by + 8, i == mode ? 0xFF0E3A50 : 0xFF0A1218);
            }
            com.sc.machine.MachineRecipe r = shownRecipe();
            net.minecraftforge.fluids.FluidStack oa = r == null ? null : r.fluidOutputA, ob = r == null ? null : r.fluidOutputB;
            GuiSceneSC.electrolysisCell(x + CHEM_X + 33, y + TANK_Y, ELEC_CELL_W, CHEM_FLASK_H, t, machine.getStatus() == MachineStatus.PROCESSING,
                    colourOf(machine.getTank(ob != null ? 3 : 2).getFluid(), ob != null ? ob : oa), colourOf(machine.getTank(2).getFluid(), oa));
        } else if (etch) {                                              // the two baths between the two tanks
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            com.sc.machine.MachineRecipe r = shownRecipe();
            GuiSceneSC.etchBaths(x + CVD_BELL_X, y + TANK_Y, CVD_BELL_W, CVD_BELL_H, t, machine.getStatus() == MachineStatus.PROCESSING, progress,
                    colourOf(machine.getTank(0).getFluid(), r == null ? null : r.fluidInputA),
                    colourOf(machine.getTank(1).getFluid(), r == null ? null : r.fluidInputB));
        } else if (cvd) {                                               // the bell jar between the two gas tanks
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            com.sc.machine.MachineRecipe r = shownRecipe();
            GuiSceneSC.bell(x + CVD_BELL_X, y + TANK_Y, CVD_BELL_W, CVD_BELL_H, t, machine.getStatus() == MachineStatus.PROCESSING, progress,
                    colourOf(machine.getTank(0).getFluid(), r == null ? null : r.fluidInputA),
                    colourOf(machine.getTank(1).getFluid(), r == null ? null : r.fluidInputB));
        } else if (chem) {                                              // the flask between the input and output tanks
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            com.sc.machine.MachineRecipe r = shownRecipe();
            GuiSceneSC.flask(x + CHEM_X + CHEM_FLASK, y + TANK_Y, CHEM_FLASK_W, CHEM_FLASK_H, t,
                    machine.getStatus() == MachineStatus.PROCESSING,
                    colourOf(machine.getTank(0).getFluid(), r == null ? null : r.fluidInputA),
                    colourOf(machine.getTank(1).getFluid(), r == null ? null : r.fluidInputB),
                    colourOf(machine.getTank(2).getFluid(), r == null ? null : r.fluidOutputA), progress);
        } else if (blast) {                                                    // the furnace, its thermometer, the zoned heat bar
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            float h = (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity();
            if (puller) {
                GuiSceneSC.puller(x + FURNACE_X, y + FURNACE_Y, FURNACE_W, FURNACE_H, t, machine.getStatus() == MachineStatus.PROCESSING,
                        progress, h);
            } else {
                GuiSceneSC.furnace(x + FURNACE_X, y + FURNACE_Y, FURNACE_W, FURNACE_H, t, machine.getStatus() == MachineStatus.PROCESSING, h);
            }
            GuiSceneSC.thermometer(x + THERMO_X, y + FURNACE_Y, FURNACE_H, h, (float) TileEntityMachineSC.HEAT_RESUME / TileEntityMachineSC.getHeatCapacity());
            GuiSceneSC.heatBar(x + HEATBAR_X, y + HEATBAR_Y, HEATBAR_W, HEATBAR_H, h,
                    (float) TileEntityMachineSC.HEAT_RESUME / TileEntityMachineSC.getHeatCapacity());
        } else if (sput) {                                              // the target badges, the chamber, the wear
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int k = target();
            for (int i = 0; i < 3; i++) {
                int bx = x + SAW_X + i * (ION_BADGE_W + 2), by = y + CAPTION_Y - 1;
                drawRect(bx, by, bx + ION_BADGE_W, by + 9, i == k ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + ION_BADGE_W - 1, by + 8, i == k ? 0xFF0E3A50 : 0xFF0A1218);
                int c = i == k ? SPUT_COLOURS[i] : GuiSceneSC.mix(SPUT_COLOURS[i], 0xFF0A1218, 0.6F);
                drawRect(bx + 3, by + 2, bx + 8, by + 7, c);
            }
            GuiSceneSC.sputterChamber(x + SAW_X, y + SAW_Y, SAW_W, 44, t, machine.getStatus() == MachineStatus.PROCESSING, progress,
                    SPUT_COLOURS[Math.max(0, k)]);
            net.minecraft.item.ItemStack tg = wire();
            float left = tg == null ? 0F : 1F - (float) tg.getItemDamage() / Math.max(1, tg.getMaxDamage());
            drawRect(x + SPUT_WEAR_X, y + SPUT_WEAR_Y, x + SPUT_WEAR_X + SPUT_WEAR_W, y + SPUT_WEAR_Y + 4, 0xFF04080C);
            for (int i = 0; i < 16; i++) {
                int a = x + SPUT_WEAR_X + 1 + i * (SPUT_WEAR_W - 2) / 16, b = x + SPUT_WEAR_X + 1 + (i + 1) * (SPUT_WEAR_W - 2) / 16 - 1;
                drawRect(a, y + SPUT_WEAR_Y + 1, b, y + SPUT_WEAR_Y + 3, (i + 0.5F) / 16 < left
                        ? (left > 0.25F ? 0xFF5AE66E : 0xFFE63C3C) : 0xFF2A3038);
            }
        } else if (ion) {                                               // the dopant badges, the beam line, the heat
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int d = dopant();
            for (int i = 0; i < 3; i++) {
                int bx = x + SAW_X + i * (ION_BADGE_W + 2), by = y + CAPTION_Y - 1;
                drawRect(bx, by, bx + ION_BADGE_W, by + 9, i == d ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + ION_BADGE_W - 1, by + 8, i == d ? 0xFF0E3A50 : 0xFF0A1218);
            }
            GuiSceneSC.beamLine(x + SAW_X, y + SAW_Y, SAW_W, 44, t, machine.getStatus() == MachineStatus.PROCESSING, progress);
            float h = (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity();
            GuiSceneSC.heatBar(x + SAW_X, y + ION_HEAT_Y, SAW_W, 3, h,
                    (float) TileEntityMachineSC.HEAT_RESUME / TileEntityMachineSC.getHeatCapacity());
        } else if (step) {                                              // the column, the die map, the mask, the heat
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING;
            GuiSceneSC.stepperColumn(x + SAW_X, y + SAW_Y, STEP_W, STEP_H, t, running, progress);
            GuiSceneSC.frame(x + MAP_X, y + SAW_Y, STEP_W, STEP_H);
            int dies = GuiSceneSC.dieCount(MAP_R);
            GuiSceneSC.dieMap(x + MAP_X + STEP_W / 2, y + SAW_Y + STEP_H / 2, MAP_R, running ? (int) (progress * dies) : 0, t, running);
            net.minecraft.item.ItemStack mask = wire();
            float left = mask == null ? 0F : 1F - (float) mask.getItemDamage() / Math.max(1, mask.getMaxDamage());
            drawRect(x + MAP_X, y + MASK_Y + 1, x + MAP_X + STEP_W, y + MASK_Y + 5, 0xFF04080C);
            for (int i = 0; i < 16; i++) {
                int a = x + MAP_X + 1 + i * (STEP_W - 2) / 16, b = x + MAP_X + 1 + (i + 1) * (STEP_W - 2) / 16 - 1;
                drawRect(a, y + MASK_Y + 2, b, y + MASK_Y + 4, (i + 0.5F) / 16 < left
                        ? (left > 0.25F ? 0xFF5AE66E : 0xFFE63C3C) : 0xFF2A3038);
            }
            float h = (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity();
            GuiSceneSC.heatBar(x + SAW_X, y + STEP_HEAT_Y, SAW_W, HEATBAR_H, h,
                    (float) TileEntityMachineSC.HEAT_RESUME / TileEntityMachineSC.getHeatCapacity());
        } else if (coat) {                                              // the spin coater and its stages
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING;
            com.sc.machine.MachineRecipe r = shownRecipe();
            GuiSceneSC.spinCoater(x + SAW_X, y + SAW_Y, COAT_W, COAT_H, t, running, progress,
                    colourOf(machine.getTank(0).getFluid(), r == null ? null : r.fluidInputA));
            int stage = running ? GuiSceneSC.coatStage(progress) : -1;
            for (int i = 0; i < 4; i++) {
                int sy = y + STAGE_Y + i * STAGE_GAP, sx = x + STAGE_X;
                boolean cur = i == stage, done = i < stage;
                drawRect(sx, sy, sx + STAGE_W, sy + 10, cur ? 0xFF2A6A8A : done ? 0xFF1E4A30 : 0xFF1A2430);
                drawRect(sx + 1, sy + 1, sx + STAGE_W - 1, sy + 9, cur ? 0xFF0E3A50 : done ? 0xFF0E2A1A : 0xFF0A1218);
            }
        } else if (oxid) {                                              // the mode badges, the tube furnace, the wafers
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING, ox = oxidising();
            int n = Math.max(1, Math.min(6, wafersPerRun())), lit = running ? (int) (progress * n) : 0;
            for (int i = 0; i < 2; i++) {
                boolean on = (i == 0) == ox;
                int bx = x + SAW_X + i * (OX_BADGE_W + 4), by = y + CAPTION_Y - 1;
                drawRect(bx, by, bx + OX_BADGE_W, by + 9, on ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + OX_BADGE_W - 1, by + 8, on ? 0xFF0E3A50 : 0xFF0A1218);
            }
            GuiSceneSC.tubeFurnace(x + SAW_X, y + OX_Y, SAW_W, OX_H, t, running, progress, n, ox, ox);
            for (int i = 0; i < n; i++) {
                float k = i < lit ? 1F : i == lit && running ? progress * n - lit : 0F;
                int wx = x + SAW_X + i * 12, wy = y + OX_WAFER_Y;
                drawRect(wx, wy, wx + 10, wy + 10, 0xFF3A4450);
                drawRect(wx + 1, wy + 1, wx + 9, wy + 9, GuiSceneSC.mix(0xFF8A94A8, ox ? 0xFF6A5AE0 : 0xFFE0A860, k));
            }
        } else if (saw) {                                               // the saw, the wafers, the wire's wear
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING;
            if (dice) {
                GuiSceneSC.dicingSaw(x + SAW_X, y + SAW_Y, SAW_W, SAW_H, t, running, progress);
            } else {
                GuiSceneSC.wireSaw(x + SAW_X, y + SAW_Y, SAW_W, SAW_H, t, running, progress);
            }
            int n = Math.min(8, wafersPerRun()), lit = running ? (int) (progress * n) : 0;
            for (int i = 0; i < n; i++) {
                int wx = x + SAW_X + i * 10, wy = y + WAFER_Y;
                drawRect(wx, wy, wx + 8, wy + 8, i < lit ? 0xFF7A8AA8 : 0xFF3A4450);
                drawRect(wx + 1, wy + 1, wx + 7, wy + 7, i < lit ? (dice ? 0xFFD8844A : 0xFF9AB0D0) : 0xFF12181E);
                if (i < lit) {
                    drawRect(wx + 2, wy + 2, wx + 4, wy + 4, dice ? 0xFFFFD8A0 : 0xFFE0ECFF);
                }
            }
            net.minecraft.item.ItemStack wire = wire();
            float left = wire == null ? 0F : 1F - (float) wire.getItemDamage() / Math.max(1, wire.getMaxDamage());
            drawRect(x + WEAR_X, y + WEAR_Y, x + WEAR_X + WEAR_W, y + WEAR_Y + 4, 0xFF04080C);
            for (int i = 0; i < 16; i++) {
                int a = x + WEAR_X + 1 + i * (WEAR_W - 2) / 16, b = x + WEAR_X + 1 + (i + 1) * (WEAR_W - 2) / 16 - 1;
                drawRect(a, y + WEAR_Y + 1, b, y + WEAR_Y + 3, (i + 0.5F) / 16 < left
                        ? (left > 0.25F ? 0xFF5AE66E : 0xFFE63C3C) : 0xFF2A3038);
            }
        } else if (washer) {                                            // the washing tub: its water is the tank
            FluidTank water = machine.getTank(0);
            float level = water.getCapacity() > 0 ? (float) water.getFluidAmount() / water.getCapacity() : 0F;
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            GuiSceneSC.washer(x + TUB_X, y + TUB_Y, TUB_W, TUB_H, t, machine.getStatus() == MachineStatus.PROCESSING, level);
        } else if (fill) {                                              // the fluid badges, the filler, the capsules row
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int mode = fillMode();
            for (int i = 0; i < 2; i++) {
                int bx = x + SAW_X + i * (FILL_BADGE_W + 2), by = y + CAPTION_Y - 1;
                drawRect(bx, by, bx + FILL_BADGE_W, by + 9, i == mode ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + FILL_BADGE_W - 1, by + 8, i == mode ? 0xFF0E3A50 : 0xFF0A1218);
            }
            FluidTank tank = machine.getTank(0);
            int c = tank.getFluid() == null ? 0 : GuiTankGaugeSC.colourOf(tank.getFluid().getFluid()) | 0xFF000000;
            GuiSceneSC.cellFiller(x + SAW_X, y + FILL_Y, SAW_W, FILL_H, t, machine.getStatus() == MachineStatus.PROCESSING, progress, c);
            int n = fillCapsules();
            for (int i = 0; i < 8; i++) {
                GuiSceneSC.capsule(x + SAW_X + i * 10, y + FILL_ROW_Y, 8, 12, i < n ? 1F : 0F, c == 0 ? 0xFF12181E : c);
            }
        } else if (kiln) {                                              // the product badges, the kiln, the curve
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING;
            int k = kilnProduct();
            for (int i = 0; i < 5; i++) {
                int bx = x + KILN_X + i * (KILN_BADGE_W + 2), by = y + CAPTION_Y - 2;
                drawRect(bx, by, bx + KILN_BADGE_W, by + 11, i == k ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + KILN_BADGE_W - 1, by + 10, i == k ? 0xFF0E3A50 : 0xFF0A1218);
                drawRect(bx + 2, by + 8, bx + KILN_BADGE_W - 2, by + 10,
                        i == k ? KILN_COLOURS[i] : GuiSceneSC.mix(KILN_COLOURS[i], 0xFF0A1218, 0.65F));
            }
            com.sc.machine.MachineRecipe r = kilnRecipe();
            int from = r == null || r.inputs.length == 0 ? 0xFF6A6A72 : itemColour(r.inputs[0]);
            GuiSceneSC.kiln(x + KILN_X, y + KILN_Y, GuiBigSC.SCREEN_RIGHT - KILN_X, KILN_H, t, running, progress,
                    from == 0xFF5AE66E ? 0xFF6A6A72 : from, k < 0 ? 0xFF8A8A92 : KILN_COLOURS[k]);
            GuiSceneSC.firingCurve(x + KILN_X, y + KILN_CURVE_Y, GuiBigSC.SCREEN_RIGHT - KILN_X, KILN_CURVE_H, running ? progress : 0F);
        } else if (station) {                                           // the tier ladder and the bench
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int[] ld = stationLadder();
            int[] cols = ld[0] == 1 ? ST_CHIP : ST_GEAR;
            for (int i = 0; i < 3; i++) {
                int bx = x + ST_X + i * (ST_STEP_W + 4), by = y + CAPTION_Y - 1;
                boolean now = i == ld[1], done = i < ld[1] && ld[1] >= 0, lock = i > ld[2];
                drawRect(bx, by, bx + ST_STEP_W, by + 12, now ? 0xFF2A6A8A : done ? 0xFF1E4A30 : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + ST_STEP_W - 1, by + 11, 0xFF0A1218);
                drawRect(bx + 3, by + 3, bx + 9, by + 9, lock ? GuiSceneSC.mix(cols[i], 0xFF0A1218, 0.6F) : cols[i]);
            }
            int step = Math.max(1, ld[1]);
            GuiSceneSC.upgradeBench(x + ST_X, y + ST_Y, GuiBigSC.SCREEN_RIGHT - ST_X, ST_H, t, machine.getStatus() == MachineStatus.PROCESSING,
                    progress, cols[step - 1], cols[step], ld[0] == 1);
        } else if (roll) {                                              // the mold badges, the mill, the wear
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            int k = rollMold();
            for (int i = 0; i < ROLL_MOLDS.length; i++) {
                int bx = x + rollBadgeX(i), by = y + rollBadgeY(i);
                drawRect(bx, by, bx + ROLL_BADGE_W - 1, by + 8, i == k ? 0xFF2A6A8A : 0xFF1A2430);
                drawRect(bx + 1, by + 1, bx + ROLL_BADGE_W - 2, by + 7, i == k ? 0xFF0E3A50 : 0xFF0A1218);
            }
            com.sc.machine.MachineRecipe r = k < 0 ? null : shownRecipe();
            GuiSceneSC.rollingMill(x + ROLL_X, y + ROLL_Y, GuiBigSC.SCREEN_RIGHT - ROLL_X, ROLL_H, t, machine.getStatus() == MachineStatus.PROCESSING,
                    progress, r == null ? 0 : itemColour(rollMetal(r)));
            net.minecraft.item.ItemStack mold = wire();
            float left = mold == null ? 0F : 1F - (float) mold.getItemDamage() / Math.max(1, mold.getMaxDamage());
            drawRect(x + ROLL_WEAR_X, y + ROLL_WEAR_Y, x + ROLL_WEAR_X + ROLL_WEAR_W, y + ROLL_WEAR_Y + 4, 0xFF04080C);
            for (int i = 0; i < 16; i++) {
                int a = x + ROLL_WEAR_X + 1 + i * (ROLL_WEAR_W - 2) / 16, b = x + ROLL_WEAR_X + 1 + (i + 1) * (ROLL_WEAR_W - 2) / 16 - 1;
                drawRect(a, y + ROLL_WEAR_Y + 1, b, y + ROLL_WEAR_Y + 3, (i + 0.5F) / 16 < left
                        ? (left > 0.25F ? 0xFF5AE66E : 0xFFE63C3C) : 0xFF2A3038);
            }
        } else if (cent) {                                              // the rotor, the chance bars
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            com.sc.machine.MachineRecipe r = centRecipe();
            net.minecraft.item.ItemStack[] outs = centOutputs(r);
            GuiSceneSC.centrifuge(x + CENT_X, y + CENT_Y, CENT_S, CENT_S, t, machine.getStatus() == MachineStatus.PROCESSING, progress,
                    outs.length > 0 ? itemColour(outs[0]) : 0);
            int rowH = centRowH(outs.length);
            for (int i = 0; i < outs.length; i++) {
                int by = y + CENT_Y + 2 + i * rowH + 6;
                float c = centChance(r, i);
                drawRect(x + CENT_LIST_X, by, x + CENT_LIST_X + CENT_BAR_W, by + 4, 0xFF04080C);
                drawRect(x + CENT_LIST_X + 1, by + 1, x + CENT_LIST_X + 1 + Math.max(1, (int) ((CENT_BAR_W - 2) * c)), by + 3,
                        itemColour(outs[i]) | 0xFF000000);
            }
        } else if (pack) {                                              // the assembly scene and its stages
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING;
            int[] pd = packPinsDies();
            GuiSceneSC.packager(x + PACK_X, y + PACK_Y, PACK_W, PACK_H, t, running, progress, pd[0], pd[1]);
            int stage = running ? GuiSceneSC.packStage(progress) : -1;
            for (int i = 0; i < 4; i++) {
                int sy = y + PACK_Y + i * 12, sx = x + PACK_STAGE_X;
                boolean cur = i == stage, done = i < stage;
                drawRect(sx, sy, sx + PACK_STAGE_W, sy + 10, cur ? 0xFF2A6A8A : done ? 0xFF1E4A30 : 0xFF1A2430);
                drawRect(sx + 1, sy + 1, sx + PACK_STAGE_W - 1, sy + 9, cur ? 0xFF0E3A50 : done ? 0xFF0E2A1A : 0xFF0A1218);
            }
        } else if (shownCount == 0) {                                   // the picture window
            int wx = x + rx + 2, ww = GuiBigSC.SCREEN_RIGHT - rx - 2;
            boolean running = machine.getStatus() == MachineStatus.PROCESSING;
            if (machine.getMachineType() == com.sc.machine.MachineType.CRUSHER) {
                float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
                GuiSceneSC.crusher(wx, y + SCENE_Y, ww, SCENE_H, t, running, progress);
            } else {                                                     // the pictogram, double size, centred
                GuiSceneSC.frame(wx, y + SCENE_Y, ww, SCENE_H);
                GuiGaugeSC.bind(mc, TEXTURE);
                GL11.glPushMatrix();
                GL11.glTranslatef(wx + ww / 2 - 16, y + SCENE_Y + SCENE_H / 2 - 16, 0F);
                GL11.glScalef(2F, 2F, 1F);
                drawProcessDisplay(0, 0);
                GL11.glPopMatrix();
            }
        } else if (smallIconFits()) {
            drawProcessDisplay(x + GuiBigSC.SCREEN_RIGHT - 17, y + 23);
        }

        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + MG_Y, GuiBigSC.GAUGE_W, MG_H,
                (float) machine.getEnergyStored() / Math.max(1, machine.getMaxEnergyStored()));

        // Tanks last: drawing a fluid switches to the blocks atlas.
        if (washer || blast || saw || oxid || coat || step || ion || sput || fill) {
            FluidTank water = machine.getTank(0);
            GuiTankGaugeSC.draw(mc, x + WATER_X, y + WATER_Y, water.getFluid(), water.getCapacity(), null, false);
        }
        if (ownTanks != null) {
            for (int k = 0; k < ownTanks.length; k++) {
                FluidTank tank = machine.getTank(ownTanks[k]);
                GuiTankGaugeSC.draw(mc, x + ownTankX[k], y + TANK_Y, tank.getFluid(), tank.getCapacity(), null, false);
            }
        }
        for (int k = 0; k < shownCount; k++) {
            FluidTank tank = machine.getTank(shownTanks[k]);
            GuiTankGaugeSC.draw(mc, x + rx + k * TANK_GAP, y + TANK_Y, tank.getFluid(), tank.getCapacity(), null, false);
        }
        GuiHoloSC.glint(x + GuiBigSC.SCREEN_X, y + GuiBigSC.SCREEN_Y, GuiBigSC.SCREEN_W, GuiBigSC.SCREEN_H);
        GuiGaugeSC.bind(mc, TEXTURE);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        drawForeground();
        power.drawGaugeOff(fontRendererObj);
        power.drawWarning(fontRendererObj, 88, 82, GuiBigSC.SCREEN_RIGHT - 88, guiLeft, guiTop);
    }

    private void drawForeground() {
        fit(machine.getMachineType().localizedName(), 8, 5, GuiBigSC.titleRoom(fontRendererObj, machine.getMachineType().tier),
                GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, machine.getMachineType().tier, GuiBigSC.W - 6, 3);
        GuiBigSC.labels(fontRendererObj, Lang.tr("sc.gui.big.upgrades"), Lang.tr("container.inventory"));
        MachineStatus status = machine.getStatus();
        int ticks = machine.getCurrentRecipeTicks();
        String pctNow = ticks > 0 ? machine.getProgressTicks() * 100 / ticks + "%" : "";
        int pw = pctNow.isEmpty() ? 0 : fontRendererObj.getStringWidth(pctNow) + 3;
        fit(status.localized(), PROGRESS_X, STATUS_Y, barW() - pw, statusColor(status));
        if (pw > 0) {
            fontRendererObj.drawString(pctNow, PROGRESS_X + barW() - pw + 3, STATUS_Y, GuiHoloSC.VALUE);
        }
        small(Lang.tr("sc.gui.holo.in"), slotX(withTanks, 0), IN_Y + 18, 0x4A96BE);
        small(Lang.tr("sc.gui.holo.out"), slotX(withTanks, 0), OUT_Y + 19, 0x4A96BE);
        int rx = rightX();
        if (washer) {
            drawWasherText(rx, ticks);
            drawUpgradeLine();
            return;
        }
        if (blast) {
            drawBlastText(rx);
            drawUpgradeLine();
            return;
        }
        if (sput) {
            drawSputText();
            drawUpgradeLine();
            return;
        }
        if (ion) {
            drawIonText();
            drawUpgradeLine();
            return;
        }
        if (step) {
            drawStepText(rx);
            drawUpgradeLine();
            return;
        }
        if (coat) {
            drawCoatText(rx);
            drawUpgradeLine();
            return;
        }
        if (oxid) {
            drawOxidText();
            drawUpgradeLine();
            return;
        }
        if (saw) {
            drawSawText(rx);
            drawUpgradeLine();
            return;
        }
        if (chem) {
            drawChemText();
            drawUpgradeLine();
            return;
        }
        if (boil) {
            drawBoilText();
            drawUpgradeLine();
            return;
        }
        if (refi) {
            drawRefiText();
            drawUpgradeLine();
            return;
        }
        if (air) {
            drawAirText();
            drawUpgradeLine();
            return;
        }
        if (elec) {
            drawElecText();
            drawUpgradeLine();
            return;
        }
        if (cvd || etch) {
            drawCvdText();
            drawUpgradeLine();
            return;
        }
        if (fill) {
            drawFillText();
            drawUpgradeLine();
            return;
        }
        if (kiln) {
            drawKilnText();
            drawUpgradeLine();
            return;
        }
        if (station) {
            drawStationText();
            drawUpgradeLine();
            return;
        }
        if (roll) {
            drawRollText();
            drawUpgradeLine();
            return;
        }
        if (cent) {
            drawCentText(rx);
            drawUpgradeLine();
            return;
        }
        if (pack) {
            drawPackText(rx);
            drawUpgradeLine();
            return;
        }
        int captionRoom = (shownCount > 0 && smallIconFits() ? GuiBigSC.SCREEN_RIGHT - 19 : GuiBigSC.SCREEN_RIGHT) - rx;
        fit(Lang.tr("sc.gui.holo.process." + processKind(machine.getMachineType())), rx, CAPTION_Y, captionRoom, GuiHoloSC.CYAN & 0xFFFFFF);
        for (int k = 0; k < shownCount; k++) {
            int i = shownTanks[k];
            TextFitSC.drawCentered(fontRendererObj, Lang.tr(i < 2 ? "sc.gui.holo.tank.in" : "sc.gui.holo.tank.out", (i % 2) + 1),
                    rx + k * TANK_GAP, TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        // the numbers: beside the pictogram, or right of the tanks if they leave room
        int nx = shownCount == 0 ? rx + 40 : rx + shownCount * TANK_GAP + 2;
        int room = GuiBigSC.SCREEN_RIGHT - nx;
        String done = Lang.tr("sc.gui.holo.progress", ticks > 0 ? machine.getProgressTicks() * 100 / ticks + "%" : "-");
        int oc = machine.upgradeCount(UpgradeType.OVERCLOCKER), q = machine.upgradeCount(UpgradeType.QUALITY);
        double speed = 1 / Math.pow(0.7, oc), energy = Math.pow(1.6, oc) * Math.pow(1.25, q);
        int used = 0;
        for (int i = 0; i < TileEntityMachineSC.UPGRADE_SLOTS; i++) {
            used += machine.getStackInSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT + i) != null ? 1 : 0;
        }
        if (shownCount == 0) {                                          // two columns under the picture
            String[][] cols = {
                    {Lang.tr("sc.gui.big.col.done"), ticks > 0 ? machine.getProgressTicks() * 100 / ticks + "%" : "-"},
                    {Lang.tr("sc.gui.big.col.energy"), machine.effectiveEuPerTick() + " EU/t"},
                    {Lang.tr("sc.gui.big.col.input"), machine.inputTier().name() + " (" + machine.inputTier().getVoltage() + ")"},
                    {Lang.tr("sc.gui.big.col.upgrades"), Lang.tr("sc.gui.big.of", used, TileEntityMachineSC.UPGRADE_SLOTS)},
                    {Lang.tr("sc.gui.big.col.speed"), "x" + String.format(java.util.Locale.ROOT, "%.2f", speed)},
                    {Lang.tr("sc.gui.big.col.bonus"), bonusChance()}};
            int cx = rx + 2, half = (GuiBigSC.SCREEN_RIGHT - cx) / 2;
            for (int i = 0; i < cols.length; i++) {
                int colX = cx + (i / 3) * half, rowY = STATS_Y + (i % 3) * 10;
                fit(cols[i][0], colX, rowY, 27, GuiHoloSC.LABEL);
                fit(cols[i][1], colX + 29, rowY, half - 31, GuiHoloSC.VALUE);
            }
        } else if (room >= 40) {
            List<String> rows = new ArrayList<String>();
            rows.add(done);
            rows.add(Lang.tr("sc.gui.holo.energy", machine.effectiveEuPerTick()));
            rows.add(Lang.tr("sc.gui.holo.input", machine.inputTier().name(), machine.inputTier().getVoltage()));
            if (heat()) {
                rows.add(Lang.tr("sc.gui.holo.heat", machine.getHeat() * 100 / Math.max(1, TileEntityMachineSC.getHeatCapacity())));
            }
            int y0 = shownCount == 0 ? 40 : TANK_Y;
            for (int i = 0; i < rows.size(); i++) {
                fit(rows.get(i), nx, y0 + i * 11, room, i == 0 ? GuiHoloSC.VALUE : GuiHoloSC.LABEL);
            }
        }
        drawUpgradeLine();
    }

    /** What the upgrades do, beside their row. */
    private void drawUpgradeLine() {
        int oc = machine.upgradeCount(UpgradeType.OVERCLOCKER), q = machine.upgradeCount(UpgradeType.QUALITY);
        double speed = 1 / Math.pow(0.7, oc), energy = Math.pow(1.6, oc) * Math.pow(1.25, q);
        int used = 0;
        for (int i = 0; i < TileEntityMachineSC.UPGRADE_SLOTS; i++) {
            used += machine.getStackInSlot(TileEntityMachineSC.FIRST_UPGRADE_SLOT + i) != null ? 1 : 0;
        }
        int tr = GuiBigSC.W - 8 - GuiBigSC.UPG_TEXT_X;
        fit(Lang.tr("sc.gui.big.upgrades.effect", String.format(java.util.Locale.ROOT, "%.2f", speed),
                String.format(java.util.Locale.ROOT, "%.2f", energy)), GuiBigSC.UPG_TEXT_X, GuiBigSC.UPG_Y, tr, 0x505864);
        String second = blast || step || ion ? Lang.tr("sc.gui.blast.sinks", machine.upgradeCount(UpgradeType.HEAT_SINK),
                String.format(java.util.Locale.ROOT, "%.2f", 1.0 / (machine.upgradeCount(UpgradeType.HEAT_SINK) + 1)))
                : Lang.tr("sc.gui.big.upgrades.count", used, TileEntityMachineSC.UPGRADE_SLOTS);
        fit(second, GuiBigSC.UPG_TEXT_X, GuiBigSC.UPG_Y + 9, tr, 0x808894);
    }

    /** The Ore Washer: caption, the water level on the tub, its tank's label, and done / water a wash / washes left. */
    private void drawWasherText(int rx, int ticks) {
        fit(Lang.tr("sc.gui.holo.washing"), rx, CAPTION_Y, WATER_X - rx - 4, GuiHoloSC.CYAN & 0xFFFFFF);
        FluidTank water = machine.getTank(0);
        int pct = water.getCapacity() > 0 ? water.getFluidAmount() * 100 / water.getCapacity() : 0;
        String p = pct + "%";
        fontRendererObj.drawStringWithShadow(p, TUB_X + TUB_W / 2 - fontRendererObj.getStringWidth(p) / 2, TUB_Y + 20, 0xFFFFFF);
        TextFitSC.drawCentered(fontRendererObj, Lang.tr("sc.gui.holo.gen.tank.water"), WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7,
                GuiHoloSC.LABEL, false, guiLeft, guiTop);
        int perWash = waterPerWash();
        String[][] rows = {
                {Lang.tr("sc.gui.big.col.done"), ticks > 0 ? machine.getProgressTicks() * 100 / ticks + "%" : "-"},
                {Lang.tr("sc.gui.washer.per"), perWash > 0 ? Lang.tr("sc.gui.washer.mb", perWash) : "-"},
                {Lang.tr("sc.gui.washer.left"), perWash > 0 ? Lang.tr("sc.gui.washer.washes", water.getFluidAmount() / perWash) : "-"}};
        for (int i = 0; i < rows.length; i++) {
            int ry = TUB_Y + TUB_H + 3 + i * 8;
            fit(rows[i][0], TUB_X, ry, 29, GuiHoloSC.LABEL);
            fit(rows[i][1], TUB_X + 31, ry, WATER_X - TUB_X - 35, GuiHoloSC.VALUE);
        }
    }

    /** The Blast Furnace: caption, its tank's label, the heat, the 70 / 100 marks, what happens next. */
    private void drawBlastText(int rx) {
        fit(Lang.tr(puller ? "sc.gui.holo.growing" : "sc.gui.holo.smelting"), rx, CAPTION_Y, WATER_X - rx - 4, GuiHoloSC.CYAN & 0xFFFFFF);
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        int heat = machine.getHeat(), cap = TileEntityMachineSC.getHeatCapacity(), resume = TileEntityMachineSC.HEAT_RESUME;
        fit(Lang.tr("sc.gui.blast.heat", heat, cap), HEATBAR_X, HEATBAR_Y - 9, HEATBAR_W, 0xFFAA5A);
        small(String.valueOf(resume), HEATBAR_X + HEATBAR_W * resume / cap - 3, HEATBAR_Y + 6, 0xF0C450);
        small(String.valueOf(cap), HEATBAR_X + HEATBAR_W - 9, HEATBAR_Y + 6, 0xE65A5A);
        fit(heatLine(), HEATBAR_X, HEATBAR_Y + 13, WATER_X - HEATBAR_X - 4, GuiHoloSC.VALUE);
    }

    /** What the heat does next: the pause coming, the cooling down to resume, or cold. */
    private String heatLine() {
        int heat = machine.getHeat(), cap = TileEntityMachineSC.getHeatCapacity(), resume = TileEntityMachineSC.HEAT_RESUME;
        int sinks = machine.upgradeCount(UpgradeType.HEAT_SINK);
        if (machine.getStatus() == MachineStatus.OVERHEATED) {
            return Lang.tr("sc.gui.blast.paused", resume, Math.max(1, (heat - resume) / 2 / 20));
        } else if (machine.getStatus() == MachineStatus.PROCESSING) {
            return Lang.tr("sc.gui.blast.topause", Math.max(0, (cap - heat) * (sinks + 1) / 20));
        }
        return heat > 0 ? Lang.tr("sc.gui.blast.cooling", heat / 2 / 20 + 1) : Lang.tr("sc.gui.blast.cold");
    }

    /** Which target the Sputterer has in: 0 copper, 1 aluminium, 2 tungsten, -1 none. */
    private int target() {
        net.minecraft.item.ItemStack s = wire();
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemToolSC)) {
            return -1;
        }
        com.sc.util.SCToolType type = ((com.sc.item.ItemToolSC) s.getItem()).getType();
        return type == com.sc.util.SCToolType.SPUTTER_TARGET_COPPER ? 0 : type == com.sc.util.SCToolType.SPUTTER_TARGET_ALUMINIUM ? 1
                : type == com.sc.util.SCToolType.SPUTTER_TARGET_TUNGSTEN ? 2 : -1;
    }

    /** The Sputterer: the badges' names, its tank's label, the metal and argon a run, defects, the target's uses. */
    private void drawSputText() {
        int k = target();
        for (int i = 0; i < 3; i++) {
            small(SPUT_BADGES[i], SAW_X + i * (ION_BADGE_W + 2) + 11, CAPTION_Y + 1, i == k ? 0x96F0FF : 0x465A6E);
        }
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        com.sc.machine.MachineRecipe r = shownRecipe();
        int per = r == null || r.fluidInputA == null ? 30 : r.fluidInputA.amount;
        smallFit(k < 0 ? Lang.tr("sc.gui.sput.none") : Lang.tr("sc.gui.sput.row", Lang.tr("sc.gui.sput.el." + k), per),
                SAW_X, 83, SAW_W, k < 0 ? 0xE65A5A : 0xE6F0FA);
        if (r != null) {
            small(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)), SAW_X, 90, 0x6AA8C8);
        }
        small(Lang.tr("sc.gui.sput.target"), SAW_X, SPUT_WEAR_Y, 0x6AA8C8);
        net.minecraft.item.ItemStack tg = wire();
        small(tg == null ? Lang.tr("sc.gui.sput.notarget")
                : Lang.tr("sc.gui.sput.left", tg.getMaxDamage() - tg.getItemDamage(), tg.getMaxDamage()),
                SAW_X, SPUT_WEAR_Y + 7, tg == null ? 0xE65A5A : 0x6AA8C8);
    }

    /** Which dopant the Ion Implanter's tank holds: 0 phosphorus, 1 boron, 2 arsenic, -1 none. */
    private int dopant() {
        net.minecraftforge.fluids.FluidStack f = machine.getTank(0).getFluid();
        if (f == null || f.amount <= 0 || f.getFluid() == null) {
            return -1;
        }
        return f.getFluid() == com.sc.init.ModFluids.ph3 ? 0 : f.getFluid() == com.sc.init.ModFluids.bcl3 ? 1
                : f.getFluid() == com.sc.init.ModFluids.ash3 ? 2 : -1;
    }

    /** The Ion Implanter: the badges' names, its tank's label, the dopant and its type, defects, the heat line. */
    private void drawIonText() {
        int d = dopant();
        for (int i = 0; i < 3; i++) {
            int sw = fontRendererObj.getStringWidth(ION_BADGES[i]) * 5 / 8;
            small(ION_BADGES[i], SAW_X + i * (ION_BADGE_W + 2) + (ION_BADGE_W - sw) / 2, CAPTION_Y + 1, i == d ? 0x96F0FF : 0x465A6E);
        }
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : Lang.tr("sc.gui.ion.gas");
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        com.sc.machine.MachineRecipe r = shownRecipe();
        smallFit(d < 0 ? Lang.tr("sc.gui.ion.nogas")
                : Lang.tr("sc.gui.ion.dopant", Lang.tr("sc.gui.ion.el." + d), d == 1 ? "p" : "n", r == null || r.fluidInputA == null ? 100 : r.fluidInputA.amount),
                SAW_X, 83, SAW_W, d < 0 ? 0xE65A5A : 0xE6F0FA);
        if (r != null) {
            small(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)), SAW_X, 90, 0x6AA8C8);
        }
        String line = heatLine();
        line = line.isEmpty() ? line : line.substring(0, 1).toLowerCase() + line.substring(1);
        smallFit(Lang.tr("sc.gui.step.heat", machine.getHeat(), TileEntityMachineSC.getHeatCapacity()) + " · " + line,
                SAW_X, ION_HEAT_Y + 5, SAW_W, 0xFFAA5A);
    }

    /** The Stepper: caption, its tank's label, the mask's uses, the heat and what it does next, dies done and defects. */
    private void drawStepText(int rx) {
        fit(Lang.tr("sc.gui.holo.process.5"), rx, CAPTION_Y, WATER_X - rx - 4, GuiHoloSC.CYAN & 0xFFFFFF);
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        net.minecraft.item.ItemStack mask = wire();
        smallFit(mask == null ? Lang.tr("sc.gui.step.nomask")
                : Lang.tr("sc.gui.step.mask", mask.getMaxDamage() - mask.getItemDamage(), mask.getMaxDamage()),
                SAW_X, MASK_Y + 1, MAP_X - SAW_X - 3, mask == null ? 0xE65A5A : 0x6AA8C8);
        String line = heatLine();
        line = line.isEmpty() ? line : line.substring(0, 1).toLowerCase() + line.substring(1);
        smallFit(Lang.tr("sc.gui.step.heat", machine.getHeat(), TileEntityMachineSC.getHeatCapacity()) + " · " + line,
                SAW_X, STEP_HEAT_Y + 7, SAW_W, 0xFFAA5A);
        int ticks = machine.getCurrentRecipeTicks(), dies = GuiSceneSC.dieCount(MAP_R);
        int done = ticks > 0 && machine.getStatus() == MachineStatus.PROCESSING ? machine.getProgressTicks() * dies / ticks : 0;
        com.sc.machine.MachineRecipe r = shownRecipe();
        smallFit(Lang.tr("sc.gui.step.dies", done, dies, r == null ? 0 : Math.round(r.defectChance * 100)),
                SAW_X, STEP_HEAT_Y + 14, SAW_W, 0xE6F0FA);
    }

    /** Small text (5/8 size, or smaller to fit maxW), foreground coordinates. */
    private void smallFit(String text, int x, int y, int maxW, int color) {
        float k = Math.min(0.625F, maxW / (float) Math.max(1, fontRendererObj.getStringWidth(text)));
        GL11.glPushMatrix();
        GL11.glTranslatef(x, y, 0F);
        GL11.glScalef(k, k, 1F);
        fontRendererObj.drawString(text, 0, 0, color);
        GL11.glPopMatrix();
    }

    /** The Wire Saw: caption, its tank's label, wafers done / a run and defects, the wire's uses left. */
    private void drawSawText(int rx) {
        fit(Lang.tr(dice ? "sc.gui.holo.dicing" : "sc.gui.holo.sawing"), rx, CAPTION_Y, WATER_X - rx - 4, GuiHoloSC.CYAN & 0xFFFFFF);
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        int ticks = machine.getCurrentRecipeTicks(), n = wafersPerRun();
        int done = ticks > 0 && machine.getStatus() == MachineStatus.PROCESSING ? machine.getProgressTicks() * Math.min(8, n) / ticks : 0;
        com.sc.machine.MachineRecipe r = shownRecipe();
        String defect = Lang.tr("sc.gui.saw.defect", r == null ? 0 : Math.round(r.defectChance * 100));
        int dw = fontRendererObj.getStringWidth(defect) * 5 / 8;
        fit(Lang.tr(sk("wafers"), done, n), SAW_X, WAFER_Y + 11, WATER_X - SAW_X - dw - 8, GuiHoloSC.VALUE);
        small(defect, WATER_X - 4 - dw, WAFER_Y + 13, 0x6AA8C8);
        small(Lang.tr(sk("wire")), SAW_X, WEAR_Y, 0x6AA8C8);
        net.minecraft.item.ItemStack wire = wire();
        small(wire == null ? Lang.tr(sk("nowire"))
                : Lang.tr(sk("left"), wire.getMaxDamage() - wire.getItemDamage(), wire.getMaxDamage()),
                SAW_X, WEAR_Y + 7, wire == null ? 0xE65A5A : 0x6AA8C8);
    }

    /** The Oxidation Furnace: the mode badges' names, its tank's label, wafers done / a run, defects, gas and time. */
    private void drawOxidText() {
        boolean ox = oxidising();
        for (int i = 0; i < 2; i++) {
            String s = Lang.tr("sc.gui.oxid.mode." + i);
            int sw = fontRendererObj.getStringWidth(s) * 5 / 8;
            small(s, SAW_X + i * (OX_BADGE_W + 4) + (OX_BADGE_W - sw) / 2, CAPTION_Y + 1, (i == 0) == ox ? 0x96F0FF : 0x465A6E);
        }
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        int ticks = machine.getCurrentRecipeTicks(), n = Math.max(1, Math.min(6, wafersPerRun()));
        int done = ticks > 0 && machine.getStatus() == MachineStatus.PROCESSING ? machine.getProgressTicks() * n / ticks : 0;
        com.sc.machine.MachineRecipe r = shownRecipe();
        String defect = Lang.tr("sc.gui.saw.defect", r == null ? 0 : Math.round(r.defectChance * 100));
        int dw = fontRendererObj.getStringWidth(defect) * 5 / 8;
        fit(Lang.tr("sc.gui.saw.wafers", done, n), SAW_X, OX_WAFER_Y + 13, WATER_X - SAW_X - dw - 8, GuiHoloSC.VALUE);
        small(defect, WATER_X - 4 - dw, OX_WAFER_Y + 15, 0x6AA8C8);
        int secs = r == null ? 0 : Math.max(1, machine.effectiveTicks(r) / 20);
        small(r != null && r.fluidInputA != null
                ? Lang.tr("sc.gui.oxid.gas", r.fluidInputA.getLocalizedName(), r.fluidInputA.amount, secs)
                : Lang.tr("sc.gui.oxid.nogas", secs), SAW_X, OX_WAFER_Y + 23, 0x6AA8C8);
    }

    /** The Photoresist Coater: caption, the stages' names, its tank's label, the cover and defects, resist a run and runs left. */
    private void drawCoatText(int rx) {
        fit(Lang.tr("sc.gui.holo.coating"), rx, CAPTION_Y, WATER_X - rx - 4, GuiHoloSC.CYAN & 0xFFFFFF);
        boolean running = machine.getStatus() == MachineStatus.PROCESSING;
        int ticks = machine.getCurrentRecipeTicks();
        float p = ticks > 0 ? (float) machine.getProgressTicks() / ticks : 0F;
        int stage = running ? GuiSceneSC.coatStage(p) : -1;
        for (int i = 0; i < 4; i++) {
            String s = Lang.tr("sc.gui.coat.stage." + i);
            float k = Math.min(0.5F, (STAGE_W - 3) / (float) Math.max(1, fontRendererObj.getStringWidth(s)));
            GL11.glPushMatrix();
            GL11.glTranslatef(STAGE_X + (STAGE_W - fontRendererObj.getStringWidth(s) * k) / 2F, STAGE_Y + i * STAGE_GAP + 3, 0F);
            GL11.glScalef(k, k, 1F);
            fontRendererObj.drawString(s, 0, 0, i == stage ? 0x96F0FF : i < stage ? 0x5AE66E : 0x465A6E);
            GL11.glPopMatrix();
        }
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        com.sc.machine.MachineRecipe r = shownRecipe();
        String defect = Lang.tr("sc.gui.saw.defect", r == null ? 0 : Math.round(r.defectChance * 100));
        int dw = fontRendererObj.getStringWidth(defect) * 5 / 8;
        int cover = running ? Math.round(GuiSceneSC.coatCover(p) * 100) : 0;
        fit(Lang.tr("sc.gui.coat.cover", cover), SAW_X, SAW_Y + COAT_H + 5, WATER_X - SAW_X - dw - 8, GuiHoloSC.VALUE);
        small(defect, WATER_X - 4 - dw, SAW_Y + COAT_H + 7, 0x6AA8C8);
        int per = r == null || r.fluidInputA == null ? 0 : r.fluidInputA.amount;
        if (per > 0) {
            small(Lang.tr("sc.gui.coat.run", per, tank.getFluidAmount() / per), SAW_X, SAW_Y + COAT_H + 15, 0x6AA8C8);
        }
    }

    /** Whether the Oxidation Furnace's shown recipe takes gas (oxidation) or not (annealing). */
    private boolean oxidising() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        return r == null || r.fluidInputA != null;
    }

    /** The Boiler's recipe for what it holds now, or null. */
    private com.sc.machine.MachineRecipe boilRecipe() {
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        return RecipeRegistry.findMatch(machine.getMachineType(), in, machine.getTank(0).getFluid(), machine.getTank(1).getFluid());
    }

    /** What the Boiler burns: 0 coal, 1 diesel, -1 neither in. */
    private int boilFuel() {
        com.sc.machine.MachineRecipe r = boilRecipe();
        if (r != null) {
            return r.fluidInputB != null ? 1 : 0;
        }
        for (int i = 0; i < TileEntityMachineSC.INPUT_SLOTS; i++) {
            net.minecraft.item.ItemStack s = machine.getStackInSlot(i);
            if (s != null && s.getItem() == net.minecraft.init.Items.coal) {
                return 0;
            }
        }
        return machine.getTank(1).getFluidAmount() > 0 ? 1 : -1;
    }

    /** The Boiler: the badges' names, its tanks' labels (water, diesel, steam), the dial's caption. */
    private void drawBoilText() {
        int fuel = boilFuel();
        for (int i = 0; i < 2; i++) {
            String s = Lang.tr("sc.gui.boil.fuel." + i);
            int sw = fontRendererObj.getStringWidth(s) / 2;
            GL11.glPushMatrix();
            GL11.glTranslatef(CHEM_X + i * (BOIL_BADGE_W + 2) + (BOIL_BADGE_W - sw) / 2F, CAPTION_Y - 1, 0F);
            GL11.glScalef(0.5F, 0.5F, 1F);
            fontRendererObj.drawString(s, 0, 0, i == fuel ? 0x96F0FF : 0x465A6E);
            GL11.glPopMatrix();
        }
        net.minecraftforge.fluids.FluidStack[] want = {new net.minecraftforge.fluids.FluidStack(net.minecraftforge.fluids.FluidRegistry.WATER, 1),
                new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.diesel, 1), new net.minecraftforge.fluids.FluidStack(com.sc.init.ModFluids.steam, 1)};
        for (int k = 0; k < ownTanks.length; k++) {
            TextFitSC.drawCentered(fontRendererObj, chemLabel(ownTanks[k], want[k]), ownTankX[k], TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        smallFit(Lang.tr("sc.gui.boil.rate"), BOIL_X + 17, BOIL_Y + BOIL_H + 3, WATER_X - BOIL_X - 18, 0xE6F0FA);
        smallFit(Lang.tr("sc.gui.boil.turbine"), BOIL_X + 17, BOIL_Y + BOIL_H + 10, WATER_X - BOIL_X - 18, 0x6AA8C8);
    }

    /** Whether the Refinery's shown recipe is the catalysed one (platinum in: photoresist and plastics). */
    private boolean refiCatalyst() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        return r != null && r.fluidOutputA != null && r.fluidOutputA.getFluid() == com.sc.init.ModFluids.photoresist;
    }

    /** The Refinery: the products' names, the tanks' labels, the catalyst's caption, what else comes out, amounts and time. */
    private void drawRefiText() {
        boolean pt = refiCatalyst();
        for (int i = 0; i < 5; i++) {
            boolean on = pt == i >= 2;
            String s = Lang.tr("sc.gui.refi.p." + i);
            float k = Math.min(0.5F, (REFI_BADGE_W - 3) / (float) Math.max(1, fontRendererObj.getStringWidth(s)));
            GL11.glPushMatrix();
            GL11.glTranslatef(CHEM_X + i * (REFI_BADGE_W + 2) + (REFI_BADGE_W - fontRendererObj.getStringWidth(s) * k) / 2F, CAPTION_Y, 0F);
            GL11.glScalef(k, k, 1F);
            fontRendererObj.drawString(s, 0, 0, on ? 0x96F0FF : 0x465A6E);
            GL11.glPopMatrix();
        }
        com.sc.machine.MachineRecipe r = shownRecipe();
        net.minecraftforge.fluids.FluidStack[] want = {r == null ? null : r.fluidInputA, r == null ? null : r.fluidOutputA};
        for (int k = 0; k < ownTanks.length; k++) {
            TextFitSC.drawCentered(fontRendererObj, chemLabel(ownTanks[k], want[k]), ownTankX[k], TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        smallFit(Lang.tr("sc.gui.refi.catalyst"), REFI_CAT_X, REFI_CAT_Y + 23, 26, pt ? 0xE8C850 : 0x6AA8C8);
        if (r == null) {
            return;
        }
        StringBuilder extra = new StringBuilder();
        for (net.minecraft.item.ItemStack s : r.outputs) {
            if (s != null) {
                extra.append(extra.length() == 0 ? "+ " : ", ").append(s.stackSize > 1 ? s.stackSize + " " : "").append(s.getDisplayName());
            }
        }
        smallFit(extra.toString(), REFI_TOWER_X, TANK_Y + CHEM_FLASK_H + 3, WATER_X - REFI_TOWER_X - 2, 0xE6F0FA);
        smallFit(Lang.tr("sc.gui.refi.run", r.fluidInputA == null ? 0 : r.fluidInputA.amount, r.fluidOutputA == null ? 0 : r.fluidOutputA.amount,
                Math.max(1, machine.effectiveTicks(r) / 20)), REFI_TOWER_X, TANK_Y + CHEM_FLASK_H + 10, WATER_X - REFI_TOWER_X - 2, 0x6AA8C8);
    }

    /** The Air Separator's mode: 0 air into oxygen and argon, 1 water into liquid helium, -1 none. */
    private int airMode() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        if (r == null || r.fluidOutputA == null) {
            return -1;
        }
        return r.fluidOutputA.getFluid() == com.sc.init.ModFluids.liquidHelium ? 1 : 0;
    }

    /** The Air Separator: the badges' names (scaled to fit) and its three tanks' labels. */
    private void drawAirText() {
        int mode = airMode();
        for (int i = 0; i < 2; i++) {
            String s = Lang.tr("sc.gui.air.mode." + i);
            float k = Math.min(0.625F, (AIR_BADGE_W - 4) / (float) Math.max(1, fontRendererObj.getStringWidth(s)));
            GL11.glPushMatrix();
            GL11.glTranslatef(CHEM_X + i * (AIR_BADGE_W + 2) + (AIR_BADGE_W - fontRendererObj.getStringWidth(s) * k) / 2F, CAPTION_Y + 1, 0F);
            GL11.glScalef(k, k, 1F);
            fontRendererObj.drawString(s, 0, 0, i == mode ? 0x96F0FF : 0x465A6E);
            GL11.glPopMatrix();
        }
        com.sc.machine.MachineRecipe r = shownRecipe();
        net.minecraftforge.fluids.FluidStack[] want = {r == null ? null : r.fluidInputA, r == null ? null : r.fluidOutputA,
                r == null ? null : r.fluidOutputB};
        for (int k = 0; k < ownTanks.length; k++) {
            TextFitSC.drawCentered(fontRendererObj, chemLabel(ownTanks[k], want[k]), ownTankX[k], TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
    }

    /** The Electrolyzer's recipe by its products: 0 lye + chlorine, 1 fluorine, 2 hydrogen + deuterium, -1 none. */
    private int elecMode() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        if (r == null || r.fluidOutputA == null) {
            return -1;
        }
        net.minecraftforge.fluids.Fluid f = r.fluidOutputA.getFluid();
        return f == com.sc.init.ModFluids.naoh ? 0 : f == com.sc.init.ModFluids.fluorine ? 1 : f == com.sc.init.ModFluids.hydrogen ? 2 : -1;
    }

    /** The Electrolyzer: the badges' names and its three tanks' labels (water in, the two products out). */
    private void drawElecText() {
        int mode = elecMode();
        for (int i = 0; i < 3; i++) {
            int sw = fontRendererObj.getStringWidth(ELEC_BADGES[i]) * 5 / 8;
            small(ELEC_BADGES[i], CHEM_X + i * (ELEC_BADGE_W + 2) + (ELEC_BADGE_W - sw) / 2, CAPTION_Y + 1, i == mode ? 0x96F0FF : 0x465A6E);
        }
        com.sc.machine.MachineRecipe r = shownRecipe();
        net.minecraftforge.fluids.FluidStack[] want = {r == null ? null : r.fluidInputA, r == null ? null : r.fluidOutputA,
                r == null ? null : r.fluidOutputB};
        for (int k = 0; k < ownTanks.length; k++) {
            TextFitSC.drawCentered(fontRendererObj, chemLabel(ownTanks[k], want[k]), ownTankX[k], TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
    }

    /** The Fluid Cell Filler's recipe for the fluid in its tank, or null. */
    private com.sc.machine.MachineRecipe fillRecipe() {
        net.minecraftforge.fluids.FluidStack f = machine.getTank(0).getFluid();
        if (f == null || f.amount <= 0) {
            return null;
        }
        for (com.sc.machine.MachineRecipe r : RecipeRegistry.recipesFor(machine.getMachineType())) {
            if (r.fluidInputA != null && r.fluidInputA.getFluid() == f.getFluid()) {
                return r;
            }
        }
        return null;
    }

    /** 0 liquid helium, 1 deuterium, -1 neither in the tank. */
    private int fillMode() {
        com.sc.machine.MachineRecipe r = fillRecipe();
        return r == null ? -1 : r.fluidInputA.getFluid() == com.sc.init.ModFluids.deuterium ? 1 : 0;
    }

    /** How many capsules the tank still fills. */
    private int fillCapsules() {
        com.sc.machine.MachineRecipe r = fillRecipe();
        return r == null ? 0 : machine.getTank(0).getFluidAmount() / Math.max(1, r.fluidInputA.amount);
    }

    /** The Fluid Cell Filler: the badges' names, its tank's label, capsules left and a capsule's worth, the recipe and time. */
    private void drawFillText() {
        int mode = fillMode();
        for (int i = 0; i < 2; i++) {
            String s = Lang.tr("sc.gui.fill.mode." + i);
            int sw = fontRendererObj.getStringWidth(s) * 5 / 8;
            small(s, SAW_X + i * (FILL_BADGE_W + 2) + (FILL_BADGE_W - sw) / 2, CAPTION_Y + 1, i == mode ? 0x96F0FF : 0x465A6E);
        }
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : Lang.tr("sc.gui.fill.tank");
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        com.sc.machine.MachineRecipe r = fillRecipe();
        int w = WATER_X - SAW_X - 4;
        if (r == null) {
            smallFit(Lang.tr("sc.gui.fill.empty"), SAW_X, FILL_ROW_Y + 14, w, 0xE65A5A);
            return;
        }
        int n = fillCapsules();
        smallFit(Lang.tr("sc.gui.fill.left", n > 8 ? "8+" : String.valueOf(n), r.fluidInputA.amount), SAW_X, FILL_ROW_Y + 14, w, 0xE6F0FA);
        String in = r.inputs.length > 0 && r.inputs[0] != null ? r.inputs[0].getDisplayName() : "-";
        smallFit(Lang.tr("sc.gui.fill.row", in, r.outputs[0].getDisplayName(), Math.max(1, machine.effectiveTicks(r) / 20F)),
                SAW_X, FILL_ROW_Y + 21, w, 0x6AA8C8);
    }

    /** The Kiln's recipe: the one its inputs make, or null. */
    private com.sc.machine.MachineRecipe kilnRecipe() {
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        return RecipeRegistry.findMatch(machine.getMachineType(), in, null, null);
    }

    /** Which of the Kiln's five products the inputs make: 0 coke, 1 rubber, 2 heat-resistant rubber, 3 ceramic, 4 W-Ti; -1 none. */
    private int kilnProduct() {
        com.sc.machine.MachineRecipe r = kilnRecipe();
        net.minecraft.item.Item o = r == null || r.outputs.length == 0 || r.outputs[0] == null ? null : r.outputs[0].getItem();
        if (o == null) {
            return -1;
        }
        return o == com.sc.init.ModItems.coke ? 0 : o == com.sc.init.ModItems.rubber ? 1 : o == com.sc.init.ModItems.rubberHeatResist ? 2
                : o == com.sc.init.ModItems.component("ceramicPackage") ? 3 : o == com.sc.init.ModItems.component("wTiPlate") ? 4 : -1;
    }

    /** The Kiln: the badges' names, the curve's stage names (the current one lit), "inputs -> product · time". */
    private void drawKilnText() {
        int k = kilnProduct();
        for (int i = 0; i < 5; i++) {
            String s = Lang.tr("sc.gui.kiln.p." + i);
            float sc = Math.min(0.5F, (KILN_BADGE_W - 3) / (float) Math.max(1, fontRendererObj.getStringWidth(s)));
            GL11.glPushMatrix();
            GL11.glTranslatef(KILN_X + i * (KILN_BADGE_W + 2) + (KILN_BADGE_W - fontRendererObj.getStringWidth(s) * sc) / 2F, CAPTION_Y, 0F);
            GL11.glScalef(sc, sc, 1F);
            fontRendererObj.drawString(s, 0, 0, i == k ? 0x96F0FF : 0x465A6E);
            GL11.glPopMatrix();
        }
        boolean running = machine.getStatus() == MachineStatus.PROCESSING;
        int ticks = machine.getCurrentRecipeTicks();
        int stage = running && ticks > 0 ? GuiSceneSC.firingStage((float) machine.getProgressTicks() / ticks) : -1;
        int w = GuiBigSC.SCREEN_RIGHT - KILN_X;
        int[] sx = {KILN_X + 3, KILN_X + w * 3 / 8, KILN_X + w * 4 / 5};
        for (int i = 0; i < 3; i++) {
            small(Lang.tr("sc.gui.kiln.stage." + i), sx[i], KILN_CURVE_Y + KILN_CURVE_H + 1, i == stage ? 0x96F0FF : 0x465A6E);
        }
        com.sc.machine.MachineRecipe r = kilnRecipe();
        String line;
        if (r == null) {
            line = Lang.tr("sc.gui.kiln.empty");
        } else {
            StringBuilder b = new StringBuilder();
            for (net.minecraft.item.ItemStack s : r.inputs) {
                if (s != null) {
                    b.append(b.length() == 0 ? "" : " + ").append(s.stackSize > 1 ? s.stackSize + " × " : "").append(s.getDisplayName());
                }
            }
            net.minecraft.item.ItemStack o = r.outputs[0];
            line = Lang.tr("sc.gui.kiln.row", b.toString(), (o.stackSize > 1 ? o.stackSize + " × " : "") + o.getDisplayName(),
                    Math.max(1, machine.effectiveTicks(r) / 20));
        }
        smallFit(line, KILN_X + 2, KILN_CURVE_Y + KILN_CURVE_H + 8, w - 4, r == null ? 0x6AA8C8 : 0xE6F0FA);
    }

    /**
     * The Upgrade Station's ladder: {kind (0 gear, 1 chip), the step the shown recipe makes (1 or 2,
     * -1 none), the highest step this station can make}.
     */
    private int[] stationLadder() {
        com.sc.machine.MachineType type = machine.getMachineType();
        int stationTier = type == com.sc.machine.MachineType.UPGRADE_STATION_MV ? 0 : type == com.sc.machine.MachineType.UPGRADE_STATION_HV ? 1 : 2;
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        boolean any = false;
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
            any |= in[i] != null;
        }
        com.sc.machine.MachineRecipe r = any ? RecipeRegistry.findMatch(type, in, null, null) : null;
        net.minecraft.item.ItemStack out = r == null || r.outputs.length == 0 ? null : r.outputs[0];
        int kind = 0, step = -1;
        if (out != null && out.getItem() instanceof com.sc.item.ItemArmorChipSC) {
            kind = 1;
            step = com.sc.item.ItemArmorChipSC.tierAt(out.getItemDamage()) - 1;
        } else if (out != null && out.getItem() instanceof com.sc.item.ItemArmorSC) {
            step = ((com.sc.item.ItemArmorSC) out.getItem()).getSuit().ordinal();
        } else if (out != null && out.getItem() instanceof com.sc.item.ItemBladeSC) {
            step = ((com.sc.item.ItemBladeSC) out.getItem()).getType().ordinal();
        } else if (out != null && out.getItem() instanceof com.sc.item.ItemDrillSC) {
            step = ((com.sc.item.ItemDrillSC) out.getItem()).getType().ordinal();
        } else if (!any && stationTier == 0) {
            kind = 1;                                                   // an empty MV station: chips are all it does
        }
        int max = kind == 1 ? Math.min(2, stationTier + 1) : stationTier;
        return new int[]{kind, step, max, r == null ? 0 : 1};
    }

    /** The Upgrade Station: the steps' names, what goes in, what comes out and the time, the step it can't make. */
    private void drawStationText() {
        int[] ld = stationLadder();
        for (int i = 0; i < 3; i++) {
            boolean now = i == ld[1], done = i < ld[1] && ld[1] >= 0, lock = i > ld[2];
            String s = Lang.tr((ld[0] == 1 ? "sc.gui.station.chip." : "sc.gui.station.gear.") + i);
            smallFit(s, ST_X + i * (ST_STEP_W + 4) + 11, CAPTION_Y + 2, ST_STEP_W - 13,
                    now ? 0x96F0FF : done ? 0x5AE66E : lock ? 0x3A4658 : 0x6A7A8A);
            if (i < 2) {
                small("›", ST_X + i * (ST_STEP_W + 4) + ST_STEP_W + 1, CAPTION_Y + 2, 0x6AA8C8);
            }
        }
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        com.sc.machine.MachineRecipe r = RecipeRegistry.findMatch(machine.getMachineType(), in, null, null);
        int y0 = ST_Y + ST_H + 3, w = GuiBigSC.SCREEN_RIGHT - ST_X - 2;
        if (r == null) {
            smallFit(Lang.tr("sc.gui.station.empty"), ST_X + 2, y0, w, 0x6AA8C8);
        } else {
            StringBuilder b = new StringBuilder();
            for (net.minecraft.item.ItemStack s : r.inputs) {
                if (s != null) {
                    b.append(b.length() == 0 ? "" : " + ").append(s.stackSize > 1 ? s.stackSize + " × " : "").append(s.getDisplayName());
                }
            }
            smallFit(b.toString(), ST_X + 2, y0, w, 0xE6F0FA);
            smallFit(Lang.tr("sc.gui.station.out", r.outputs[0].getDisplayName(), Math.max(1, machine.effectiveTicks(r) / 20)),
                    ST_X + 2, y0 + 8, w, 0x6AA8C8);
        }
        if (ld[2] < 2) {
            String next = Lang.tr((ld[0] == 1 ? "sc.gui.station.chip." : "sc.gui.station.gear.") + (ld[2] + 1));
            smallFit(Lang.tr("sc.gui.station.locked", next, ld[0] == 1 ? "HV" : ld[2] == 0 ? "HV" : "EV"), ST_X + 2, y0 + 16, w, 0x465A6E);
        }
    }

    /** Badge i's place: four in the top row, the three lead frames under them. */
    private static int rollBadgeX(int i) {
        return ROLL_X + (i < 4 ? i : i - 4) * (ROLL_BADGE_W + 2);
    }

    private static int rollBadgeY(int i) {
        return CAPTION_Y - 2 + (i < 4 ? 0 : 9);
    }

    /** Which mold is in the Rolling Machine (index into ROLL_MOLDS), -1 none. */
    private int rollMold() {
        net.minecraft.item.ItemStack s = wire();
        if (s == null || !(s.getItem() instanceof com.sc.item.ItemToolSC)) {
            return -1;
        }
        com.sc.util.SCToolType type = ((com.sc.item.ItemToolSC) s.getItem()).getType();
        for (int i = 0; i < ROLL_MOLDS.length; i++) {
            if (ROLL_MOLDS[i] == type) {
                return i;
            }
        }
        return -1;
    }

    /** The recipe's metal: its first input that isn't a tool. */
    private static net.minecraft.item.ItemStack rollMetal(com.sc.machine.MachineRecipe r) {
        for (net.minecraft.item.ItemStack s : r.inputs) {
            if (s != null && !(s.getItem() instanceof com.sc.item.ItemToolSC)) {
                return s;
            }
        }
        return null;
    }

    /** The Rolling Machine: the badges' names, "N x metal -> product", the mold's presses left and the time. */
    private void drawRollText() {
        int k = rollMold();
        for (int i = 0; i < ROLL_MOLDS.length; i++) {
            String s = Lang.tr("sc.gui.roll.mold." + i);
            float sc = Math.min(0.5F, (ROLL_BADGE_W - 4) / (float) Math.max(1, fontRendererObj.getStringWidth(s)));
            GL11.glPushMatrix();
            GL11.glTranslatef(rollBadgeX(i) + (ROLL_BADGE_W - 1 - fontRendererObj.getStringWidth(s) * sc) / 2F, rollBadgeY(i) + 2, 0F);
            GL11.glScalef(sc, sc, 1F);
            fontRendererObj.drawString(s, 0, 0, i == k ? 0x96F0FF : 0x465A6E);
            GL11.glPopMatrix();
        }
        com.sc.machine.MachineRecipe r = k < 0 ? null : shownRecipe();
        net.minecraft.item.ItemStack metal = r == null ? null : rollMetal(r);
        String product = r != null && r.outputs.length > 0 && r.outputs[0] != null ? r.outputs[0].getDisplayName() : "-";
        smallFit(k < 0 ? Lang.tr("sc.gui.roll.nomold")
                : metal == null ? product : Lang.tr("sc.gui.roll.row", metal.stackSize, metal.getDisplayName(), product),
                ROLL_X + 2, ROLL_Y + ROLL_H + 3, GuiBigSC.SCREEN_RIGHT - ROLL_X - 4, k < 0 ? 0xE65A5A : 0xE6F0FA);
        small(Lang.tr("sc.gui.roll.mold"), ROLL_X + 2, ROLL_WEAR_Y, 0x6AA8C8);
        net.minecraft.item.ItemStack mold = wire();
        if (mold != null) {
            small(Lang.tr("sc.gui.roll.left", mold.getMaxDamage() - mold.getItemDamage(), mold.getMaxDamage(),
                    r == null ? 0 : Math.max(1, machine.effectiveTicks(r) / 20)), ROLL_X + 2, ROLL_WEAR_Y + 7, 0x6AA8C8);
        }
    }

    /** The Centrifuge's recipe: the one its input makes, or null (nothing to separate). */
    private com.sc.machine.MachineRecipe centRecipe() {
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        return RecipeRegistry.findMatch(machine.getMachineType(), in, null, null);
    }

    /** The main product, then the byproducts. */
    private static net.minecraft.item.ItemStack[] centOutputs(com.sc.machine.MachineRecipe r) {
        if (r == null) {
            return new net.minecraft.item.ItemStack[0];
        }
        java.util.List<net.minecraft.item.ItemStack> l = new java.util.ArrayList<net.minecraft.item.ItemStack>();
        for (net.minecraft.item.ItemStack s : r.outputs) {
            if (s != null) {
                l.add(s);
            }
        }
        int main = l.size();
        for (net.minecraft.item.ItemStack s : r.byproducts) {
            l.add(s);
        }
        return l.subList(0, Math.min(l.size(), Math.max(main, 4))).toArray(new net.minecraft.item.ItemStack[0]);
    }

    /** Output i's chance: 1 for the main products, the recipe's own for a byproduct. */
    private static float centChance(com.sc.machine.MachineRecipe r, int i) {
        int main = 0;
        for (net.minecraft.item.ItemStack s : r.outputs) {
            main += s != null ? 1 : 0;
        }
        return i < main ? 1F : i - main < r.byproductChances.length ? r.byproductChances[i - main] : 0F;
    }

    private static int centRowH(int n) {
        return n <= 3 ? 15 : 12;
    }

    /** An item's tint (a dust's metal colour), white when it has none. */
    private static int itemColour(net.minecraft.item.ItemStack s) {
        int c = s == null || s.getItem() == null ? 0xFFFFFF : s.getItem().getColorFromItemStack(s, 0) & 0xFFFFFF;
        return c == 0xFFFFFF ? 0xFF5AE66E : c | 0xFF000000;             // an untinted icon: the holo green
    }

    /** The Centrifuge: caption, each output's name and chance over its bar, the ore and the time. */
    private void drawCentText(int rx) {
        fit(Lang.tr("sc.gui.holo.separating"), rx, CAPTION_Y, GuiBigSC.SCREEN_RIGHT - rx, GuiHoloSC.CYAN & 0xFFFFFF);
        com.sc.machine.MachineRecipe r = centRecipe();
        net.minecraft.item.ItemStack[] outs = centOutputs(r);
        int rowH = centRowH(outs.length);
        for (int i = 0; i < outs.length; i++) {
            int ry = CENT_Y + 2 + i * rowH;
            String pct = Math.round(centChance(r, i) * 100) + "%";
            int pw = fontRendererObj.getStringWidth(pct) * 5 / 8;
            smallFit(outs[i].getDisplayName(), CENT_LIST_X, ry, CENT_BAR_W - pw - 3, 0xE6F0FA);
            small(pct, CENT_LIST_X + CENT_BAR_W - pw, ry, 0x6AA8C8);
        }
        if (r == null) {
            smallFit(Lang.tr("sc.gui.cent.empty"), CENT_LIST_X, CENT_Y + 2, GuiBigSC.SCREEN_RIGHT - CENT_LIST_X, 0x6AA8C8);
            return;
        }
        net.minecraft.item.ItemStack in = r.inputs.length > 0 ? r.inputs[0] : null;
        smallFit(Lang.tr("sc.gui.cent.run", in == null ? "-" : in.getDisplayName(), Math.max(1, machine.effectiveTicks(r) / 20)),
                CENT_X, CENT_Y + CENT_S + 4, GuiBigSC.SCREEN_RIGHT - CENT_X - 2, 0xE6F0FA);
        small(Lang.tr("sc.gui.cent.chance"), CENT_X, CENT_Y + CENT_S + 12, 0x6AA8C8);
    }

    /** The Packager's lead frame pins and dies a run, from the shown recipe: {3, 1}, {16, 2} or {40, 4}. */
    private int[] packPinsDies() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        int pins = 3, dies = 1;
        if (r != null) {
            for (net.minecraft.item.ItemStack s : r.inputs) {
                if (s == null) {
                    continue;
                }
                if (s.getItem() == com.sc.init.ModItems.leadFrame16) {
                    pins = 16;
                } else if (s.getItem() == com.sc.init.ModItems.leadFrame40) {
                    pins = 40;
                } else if (!(s.getItem() == com.sc.init.ModItems.leadFrame3 || s.getItem() == com.sc.init.ModItems.compound)) {
                    dies = s.stackSize;
                }
            }
        }
        return new int[]{pins, dies};
    }

    /** The Packager: caption, the stages' names, the product and its pins, dies a run, defects and time. */
    private void drawPackText(int rx) {
        fit(Lang.tr("sc.gui.holo.packaging"), rx, CAPTION_Y, GuiBigSC.SCREEN_RIGHT - rx, GuiHoloSC.CYAN & 0xFFFFFF);
        boolean running = machine.getStatus() == MachineStatus.PROCESSING;
        int ticks = machine.getCurrentRecipeTicks();
        float p = ticks > 0 ? (float) machine.getProgressTicks() / ticks : 0F;
        int stage = running ? GuiSceneSC.packStage(p) : -1;
        for (int i = 0; i < 4; i++) {
            String s = Lang.tr("sc.gui.pack.stage." + i);
            float k = Math.min(0.625F, (PACK_STAGE_W - 3) / (float) Math.max(1, fontRendererObj.getStringWidth(s)));
            GL11.glPushMatrix();
            GL11.glTranslatef(PACK_STAGE_X + (PACK_STAGE_W - fontRendererObj.getStringWidth(s) * k) / 2F, PACK_Y + i * 12 + 3, 0F);
            GL11.glScalef(k, k, 1F);
            fontRendererObj.drawString(s, 0, 0, i == stage ? 0x96F0FF : i < stage ? 0x5AE66E : 0x465A6E);
            GL11.glPopMatrix();
        }
        com.sc.machine.MachineRecipe r = shownRecipe();
        int[] pd = packPinsDies();
        String product = r != null && r.outputs != null && r.outputs.length > 0 && r.outputs[0] != null ? r.outputs[0].getDisplayName() : "-";
        smallFit(Lang.tr("sc.gui.pack.product", product, pd[0]), PACK_X, PACK_Y + PACK_H + 4, GuiBigSC.SCREEN_RIGHT - PACK_X - 2, 0xE6F0FA);
        if (r != null) {
            smallFit(Lang.tr("sc.gui.pack.run", pd[1], Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)),
                    PACK_X, PACK_Y + PACK_H + 12, GuiBigSC.SCREEN_RIGHT - PACK_X - 2, 0x6AA8C8);
        }
    }

    /** The Wire Saw's key, or the Dicing Saw's own wording of it. */
    private String sk(String key) {
        return (dice ? "sc.gui.dice." : "sc.gui.saw.") + key;
    }

    /** The Wire Saw's diamond wire (a tool in an input slot), or null. */
    private net.minecraft.item.ItemStack wire() {
        for (int i = 0; i < TileEntityMachineSC.INPUT_SLOTS; i++) {
            net.minecraft.item.ItemStack s = machine.getStackInSlot(i);
            if (s != null && s.getItem() instanceof com.sc.item.ItemToolSC && s.getMaxDamage() > 0) {
                return s;
            }
        }
        return null;
    }

    /** How many wafers a run of the shown recipe gives. */
    private int wafersPerRun() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        return r == null || r.outputs == null || r.outputs.length == 0 || r.outputs[0] == null ? 8 : r.outputs[0].stackSize;
    }

    /**
     * The recipe the Chemical Reactor shows: the one its inputs make now, else the one that best
     * fits the fluids in its tanks, else the first with two fluids in.
     */
    private com.sc.machine.MachineRecipe shownRecipe() {
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        net.minecraftforge.fluids.FluidStack a = machine.getTank(0).getFluid(), b = machine.getTank(1).getFluid();
        com.sc.machine.MachineRecipe r = RecipeRegistry.findMatch(machine.getMachineType(), in, a, b);
        if (r != null) {
            return r;
        }
        com.sc.machine.MachineRecipe best = null;
        int bestScore = -1;
        for (com.sc.machine.MachineRecipe c : RecipeRegistry.recipesFor(machine.getMachineType())) {
            int score = (sameFluid(a, c.fluidInputA) || sameFluid(a, c.fluidInputB) ? 4 : 0)
                    + (sameFluid(b, c.fluidInputB) || sameFluid(b, c.fluidInputA) ? 4 : 0)
                    + (c.fluidInputA != null ? 1 : 0) + (c.fluidInputB != null ? 1 : 0);
            if (score > bestScore) {
                best = c;
                bestScore = score;
            }
        }
        return best;
    }

    private static boolean sameFluid(net.minecraftforge.fluids.FluidStack have, net.minecraftforge.fluids.FluidStack want) {
        return have != null && want != null && have.getFluid() == want.getFluid();
    }

    /** A tank's fluid colour, or the recipe's when the tank is empty (0: nothing). */
    private static int colourOf(net.minecraftforge.fluids.FluidStack have, net.minecraftforge.fluids.FluidStack recipe) {
        net.minecraftforge.fluids.FluidStack f = have != null && have.amount > 0 ? have : recipe;
        return f == null || f.getFluid() == null ? 0 : GuiTankGaugeSC.colourOf(f.getFluid());
    }

    /** A tank's label: its fluid, or the recipe's, or "In 1" / "Out 1". */
    private String chemLabel(int tank, net.minecraftforge.fluids.FluidStack recipe) {
        net.minecraftforge.fluids.FluidStack have = machine.getTank(tank).getFluid();
        if (have != null && have.amount > 0) {
            return have.getLocalizedName();
        }
        if (recipe != null) {
            return recipe.getLocalizedName();
        }
        return Lang.tr(tank < 2 ? "sc.gui.holo.tank.in" : "sc.gui.holo.tank.out", (tank % 2) + 1);
    }

    /** The CVD Chamber: the formula on top, the gases' names, what a run takes, its defect chance and time. */
    private void drawCvdText() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        fit(formulaOf(r), CHEM_X, CAPTION_Y - 1, GuiBigSC.SCREEN_RIGHT - CHEM_X, GuiHoloSC.CYAN & 0xFFFFFF);
        net.minecraftforge.fluids.FluidStack[] want = {r == null ? null : r.fluidInputA, r == null ? null : r.fluidInputB};
        for (int k = 0; k < ownTanks.length; k++) {
            TextFitSC.drawCentered(fontRendererObj, chemLabel(ownTanks[k], want[k]), ownTankX[k], TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        if (r != null) {
            int fx = CVD_BELL_X + 2, fy = TANK_Y + CVD_BELL_H + 2;
            String a = r.fluidInputA != null ? String.valueOf(r.fluidInputA.amount) : "";
            String b = r.fluidInputB != null ? String.valueOf(r.fluidInputB.amount) : "";
            small(Lang.tr("sc.gui.cvd.run", a.isEmpty() ? b : b.isEmpty() ? a : a + " + " + b), fx, fy, 0xE6F0FA);
            small(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)),
                    fx, fy + 7, 0x6AA8C8);
        }
    }

    /** "A + B + C -> product" for a recipe (items and fluids in, the first fluid or item out). */
    private String formulaOf(com.sc.machine.MachineRecipe r) {
        if (r == null) {
            return Lang.tr("sc.gui.holo.process." + processKind(machine.getMachineType()));
        }
        java.util.List<String> ins = new java.util.ArrayList<String>();
        for (net.minecraft.item.ItemStack s : r.inputs) {
            if (s != null) {
                ins.add(s.getDisplayName());
            }
        }
        if (r.fluidInputA != null) {
            ins.add(r.fluidInputA.getLocalizedName());
        }
        if (r.fluidInputB != null) {
            ins.add(r.fluidInputB.getLocalizedName());
        }
        String out = r.fluidOutputA != null ? r.fluidOutputA.getLocalizedName()
                : r.outputs.length > 0 && r.outputs[0] != null ? r.outputs[0].getDisplayName() : "?";
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < ins.size(); i++) {
            sb.append(i > 0 ? " + " : "").append(ins.get(i));
        }
        return sb.append(" \u2192 ").append(out).toString();
    }

    /** The Chemical Reactor: the formula on top, the tanks' fluid names, the amounts a run takes under the flask. */
    private void drawChemText() {
        com.sc.machine.MachineRecipe r = shownRecipe();
        String formula;
        if (r == null) {
            formula = Lang.tr("sc.gui.holo.process.2");
        } else {
            java.util.List<String> ins = new java.util.ArrayList<String>();
            for (net.minecraft.item.ItemStack s : r.inputs) {
                if (s != null) {
                    ins.add(s.getDisplayName());
                }
            }
            if (r.fluidInputA != null) {
                ins.add(r.fluidInputA.getLocalizedName());
            }
            if (r.fluidInputB != null) {
                ins.add(r.fluidInputB.getLocalizedName());
            }
            String out = r.fluidOutputA != null ? r.fluidOutputA.getLocalizedName()
                    : r.outputs.length > 0 && r.outputs[0] != null ? r.outputs[0].getDisplayName() : "?";
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < ins.size(); i++) {
                sb.append(i > 0 ? " + " : "").append(ins.get(i));
            }
            formula = sb.append(" \u2192 ").append(out).toString();
        }
        fit(formula, CHEM_X, CAPTION_Y - 1, GuiBigSC.SCREEN_RIGHT - CHEM_X, GuiHoloSC.CYAN & 0xFFFFFF);
        net.minecraftforge.fluids.FluidStack[] want = {r == null ? null : r.fluidInputA, r == null ? null : r.fluidInputB,
                r == null ? null : r.fluidOutputA};
        for (int k = 0; k < ownTanks.length; k++) {
            TextFitSC.drawCentered(fontRendererObj, chemLabel(ownTanks[k], want[k]), ownTankX[k], TANK_LABEL_Y, GuiTankGaugeSC.WIDTH - 7,
                    GuiHoloSC.LABEL, false, guiLeft, guiTop);
        }
        if (r != null) {                                                // what one run takes and gives, small, under the flask
            int fx = CHEM_X + CHEM_FLASK, fy = TANK_Y + CHEM_FLASK_H + 2;
            String a = r.fluidInputA != null ? String.valueOf(r.fluidInputA.amount) : "";
            String b = r.fluidInputB != null ? String.valueOf(r.fluidInputB.amount) : "";
            String in = a.isEmpty() ? b : b.isEmpty() ? a : a + "+" + b;
            String out = r.fluidOutputA != null ? "\u2192" + r.fluidOutputA.amount : "";
            small(Lang.tr("sc.gui.chem.run"), fx + 1, fy, 0x4A96BE);
            small(in.isEmpty() ? "-" : in, fx + 1, fy + 6, 0xE6F0FA);
            small(out + (out.isEmpty() ? "" : " mB"), fx + 1, fy + 12, 0xE6F0FA);
        }
    }

    /** The fluid this furnace's recipes take (for the label while the tank is empty). */
    private String recipeFluidName() {
        for (com.sc.machine.MachineRecipe r : RecipeRegistry.recipesFor(machine.getMachineType())) {
            if (r.fluidInputA != null) {
                return r.fluidInputA.getLocalizedName();
            }
        }
        return "-";
    }

    /** Water one wash takes: the recipe the inputs make now, or the washer's first recipe. */
    private int waterPerWash() {
        net.minecraft.item.ItemStack[] in = new net.minecraft.item.ItemStack[TileEntityMachineSC.INPUT_SLOTS];
        for (int i = 0; i < in.length; i++) {
            in[i] = machine.getStackInSlot(i);
        }
        com.sc.machine.MachineRecipe r = RecipeRegistry.findMatch(machine.getMachineType(), in, machine.getTank(0).getFluid(),
                machine.getTank(1).getFluid());
        if (r == null && !RecipeRegistry.recipesFor(machine.getMachineType()).isEmpty()) {
            r = RecipeRegistry.recipesFor(machine.getMachineType()).get(0);
        }
        return r != null && r.fluidInputA != null ? r.fluidInputA.amount : 0;
    }

    /**
     * Gauge tooltips are drawn after everything else, in screen space. GuiContainer hands
     * drawGuiContainerForegroundLayer the RAW screen mouse position (while GL is translated to
     * the panel), so hover tests there were off by (guiLeft, guiTop): the real gauges never
     * showed a tooltip and the panel's top-left corner did. Drawing here also avoids the
     * item-lighting drawHoveringText leaves behind for the cursor stack.
     */
    @Override
    public void drawScreen(int mouseX, int mouseY, float partialTicks) {
        TextFitSC.beginFrame();
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
        if (tooltip == null) {
            tooltip = TextFitSC.hoverAt(mouseX, mouseY);
        }
        if (tooltip != null) {
            drawHoveringText(GuiGaugeSC.wrapTooltip(fontRendererObj, tooltip, width), mouseX, mouseY, fontRendererObj);
        }
    }

    /**
     * The little process display: what this machine physically does (jaws crushing, a flame
     * under a crucible, a saw blade, a spinning rotor, a UV beam, plasma, electrolysis, a
     * pick-and-place head). Animated while processing, a dimmed still frame otherwise.
     */
    private void drawProcessDisplay(int x, int y) {
        boolean running = machine.getStatus() == MachineStatus.PROCESSING;
        int frame = running && mc.theWorld != null ? (int) (mc.theWorld.getTotalWorldTime() / 4 % 4) : 0;
        int kind = processKind(machine.getMachineType());
        int u = kind < 8 ? GuiGaugeSC.SPR_PROCESS_U + frame * 16 : GuiGaugeSC.SPR_PROCESS_U + 64;
        int v = kind < 8 ? GuiGaugeSC.SPR_PROCESS_V + kind * 16 : GuiGaugeSC.SPR_PROCESS_V + frame * 16;
        if (!running) {
            GL11.glColor4f(0.45F, 0.45F, 0.45F, 1F);
        }
        GuiGaugeSC.drawBlended(this, x, y, u, v, 16, 16);
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** Row in the sheet's pictogram block: 0 crush, 1 heat, 2 wash, 3 cut, 4 spin, 5 beam, 6 plasma, 7 electrolysis, 8 assembly. */
    private static int processKind(com.sc.machine.MachineType type) {
        switch (type) {
            case CRUSHER:
            case ROLLING_MACHINE:
                return 0;
            case BLAST_FURNACE:
            case CZOCHRALSKI_PULLER:
            case CZOCHRALSKI_PULLER_EV:
            case OXIDATION_FURNACE:
            case KILN:
            case BOILER_LV:
            case BOILER_MV:
            case REFINERY:
                return 1;
            case ORE_WASHER:
            case CHEM_REACTOR:
            case ETCHING_BATH:
            case FLUID_CELL_FILLER:
                return 2;
            case WIRE_SAW:
            case DICING_SAW:
                return 3;
            case CENTRIFUGE:
            case PHOTORESIST_COATER:
                return 4;
            case STEPPER:
            case STEPPER_EV:
            case ION_IMPLANTER:
                return 5;
            case CVD_CHAMBER:
            case SPUTTERER:
            case AIR_SEPARATOR:
                return 6;
            case CHLOR_ALKALI_ELECTROLYZER:
                return 7;
            default:
                return 8;      // Packager, Upgrade Stations
        }
    }

    /** A string that fits its room (smaller, or cut with the full text as a tooltip) - foreground coordinates. */
    private void fit(String text, int x, int y, int maxW, int color) {
        TextFitSC.draw(fontRendererObj, text, x, y, maxW, color, guiLeft, guiTop);
    }

    /** Status colours for the dark screen. */
    private static int statusColor(MachineStatus status) {
        switch (status) {
            case PROCESSING: return GuiHoloSC.OK;
            case OUTPUT_FULL: return GuiHoloSC.WARN;
            case NO_POWER:
            case OVERHEATED: return GuiHoloSC.BAD;
            default: return GuiHoloSC.IDLE;
        }
    }

    /** @return the lines to show for whatever gauge the cursor is over, or null for none. */
    private List<String> tooltipAt(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<String>();

        for (Object o : buttonList) {
            if (o instanceof GuiBigSC.ClearButton && ((GuiBigSC.ClearButton) o).over(mouseX + guiLeft, mouseY + guiTop)) {
                int tank = ((GuiBigSC.ClearButton) o).id - ContainerMachineSC.BTN_CLEAR;
                return GuiBigSC.clearTip(machine.getTank(tank).getFluidAmount(), machine.clearCost(tank), machine.getEnergyStored());
            }
        }

        List<String> powerTip = power.tooltip(mouseX, mouseY);
        if (powerTip != null) {
            return powerTip;
        }
        if (GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, MG_Y, GuiBigSC.GAUGE_W, MG_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(machine.getEnergyStored() + " / " + machine.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.usage", machine.effectiveEuPerTick()));
            lines.add(Lang.tr("sc.gui.input", machine.inputTier().name(), machine.inputTier().getVoltage()));
            return lines;
        }

        boolean overTub = washer && GuiGaugeSC.isOver(TUB_X, TUB_Y, TUB_W, TUB_H, mouseX, mouseY);
        boolean overOwnTank = (washer || blast || saw || oxid || coat || step || ion || sput || fill)
                && GuiGaugeSC.isOver(WATER_X, WATER_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mouseX, mouseY);
        if (ownTanks != null) {
            for (int k = 0; k < ownTanks.length; k++) {
                if (GuiGaugeSC.isOver(ownTankX[k], TANK_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mouseX, mouseY)) {
                    int i = ownTanks[k];
                    FluidTank tank = machine.getTank(i);
                    lines.add(Lang.tr(i < 2 ? "sc.gui.tank.input" : "sc.gui.tank.output", (i % 2) + 1));
                    lines.add(GuiGaugeSC.fluidLabel(tank.getFluid(), tank.getCapacity()));
                    lines.add(GuiTankGaugeSC.percentLine(tank.getFluid(), tank.getCapacity()));
                    return lines;
                }
            }
        }
        if (overTub || overOwnTank) {
            FluidTank tank = machine.getTank(0);
            lines.add(Lang.tr("sc.gui.tank.input", 1));
            lines.add(GuiGaugeSC.fluidLabel(tank.getFluid(), tank.getCapacity()));
            lines.add(GuiTankGaugeSC.percentLine(tank.getFluid(), tank.getCapacity()));
            return lines;
        }
        for (int k = 0; k < shownCount; k++) {
            int i = shownTanks[k];
            if (GuiGaugeSC.isOver(rightX() + k * TANK_GAP, TANK_Y, GuiTankGaugeSC.WIDTH, GuiTankGaugeSC.HEIGHT, mouseX, mouseY)) {
                FluidTank tank = machine.getTank(i);
                lines.add(Lang.tr(i < 2 ? "sc.gui.tank.input" : "sc.gui.tank.output", (i % 2) + 1));
                lines.add(GuiGaugeSC.fluidLabel(tank.getFluid(), tank.getCapacity()));
                lines.add(GuiTankGaugeSC.percentLine(tank.getFluid(), tank.getCapacity()));
                return lines;
            }
        }

        if (boil && GuiGaugeSC.isOver(BOIL_X, BOIL_Y, BOIL_W, BOIL_H + 20, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = boilRecipe();
            lines.add(Lang.tr("sc.gui.boil.title"));
            if (r != null) {
                String fuel = r.fluidInputB != null ? Lang.tr("sc.gui.boil.fuelmb", r.fluidInputB.amount, r.fluidInputB.getLocalizedName())
                        : r.inputs.length > 0 && r.inputs[0] != null ? r.inputs[0].getDisplayName() : "-";
                lines.add(Lang.tr("sc.gui.boil.run", r.fluidInputA == null ? 0 : r.fluidInputA.amount, fuel,
                        r.fluidOutputA == null ? 0 : r.fluidOutputA.amount, machine.effectiveTicks(r) / 20F));
            }
            lines.add(Lang.tr("sc.gui.boil.hint"));
            return lines;
        }
        if (refi && (GuiGaugeSC.isOver(CHEM_X, CAPTION_Y - 2, 5 * (REFI_BADGE_W + 2), 11, mouseX, mouseY)
                || GuiGaugeSC.isOver(REFI_TOWER_X, TANK_Y, REFI_CAT_X + 22 - REFI_TOWER_X, CHEM_FLASK_H, mouseX, mouseY))) {
            lines.add(Lang.tr("sc.gui.refi.title"));
            lines.add(Lang.tr(refiCatalyst() ? "sc.gui.refi.now.1" : "sc.gui.refi.now.0"));
            lines.add(Lang.tr("sc.gui.refi.hint"));
            return lines;
        }
        if (air && (GuiGaugeSC.isOver(CHEM_X, CAPTION_Y - 1, 2 * (AIR_BADGE_W + 2), 10, mouseX, mouseY)
                || GuiGaugeSC.isOver(CHEM_X + 33, TANK_Y, ELEC_CELL_W, CHEM_FLASK_H, mouseX, mouseY))) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr("sc.gui.air.title"));
            if (r != null) {
                String in = r.fluidInputA != null ? String.valueOf(r.fluidInputA.amount) : "0";
                String out = (r.fluidOutputA != null ? String.valueOf(r.fluidOutputA.amount) : "0")
                        + (r.fluidOutputB != null ? " + " + r.fluidOutputB.amount : "");
                lines.add(Lang.tr("sc.gui.elec.run", in, out, Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.air.hint"));
            return lines;
        }
        if (elec && GuiGaugeSC.isOver(CHEM_X, CAPTION_Y - 1, 3 * (ELEC_BADGE_W + 2), 10, mouseX, mouseY)
                || elec && GuiGaugeSC.isOver(CHEM_X + 33, TANK_Y, ELEC_CELL_W, CHEM_FLASK_H, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr("sc.gui.elec.title"));
            if (r != null) {
                lines.add(formulaOf(r));
                String in = r.fluidInputA != null ? String.valueOf(r.fluidInputA.amount) : "0";
                String out = (r.fluidOutputA != null ? String.valueOf(r.fluidOutputA.amount) : "0")
                        + (r.fluidOutputB != null ? " + " + r.fluidOutputB.amount : "");
                lines.add(Lang.tr("sc.gui.elec.run", in, out, Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.elec.hint"));
            return lines;
        }
        if (fill && GuiGaugeSC.isOver(SAW_X, CAPTION_Y - 1, SAW_W, FILL_ROW_Y + 12 - CAPTION_Y + 1, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.fill.title"));
            lines.add(Lang.tr("sc.gui.fill.hint"));
            return lines;
        }
        if (kiln && GuiGaugeSC.isOver(KILN_X, CAPTION_Y - 2, GuiBigSC.SCREEN_RIGHT - KILN_X, KILN_CURVE_Y + KILN_CURVE_H - CAPTION_Y + 2, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.kiln.title"));
            lines.add(Lang.tr("sc.gui.kiln.hint"));
            return lines;
        }
        if (station && GuiGaugeSC.isOver(ST_X, CAPTION_Y - 1, GuiBigSC.SCREEN_RIGHT - ST_X, ST_Y + ST_H - CAPTION_Y + 1, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.station.title"));
            lines.add(Lang.tr("sc.gui.station.hint"));
            return lines;
        }
        if (roll && GuiGaugeSC.isOver(ROLL_X, CAPTION_Y - 2, GuiBigSC.SCREEN_RIGHT - ROLL_X, ROLL_Y + ROLL_H - CAPTION_Y + 2, mouseX, mouseY)) {
            int k = rollMold();
            lines.add(Lang.tr("sc.gui.roll.title"));
            lines.add(k < 0 ? Lang.tr("sc.gui.roll.nomold") : Lang.tr("sc.gui.roll.now", Lang.tr("sc.gui.roll.mold." + k)));
            lines.add(Lang.tr("sc.gui.roll.hint"));
            return lines;
        }
        if (cent && GuiGaugeSC.isOver(CENT_X, CENT_Y, GuiBigSC.SCREEN_RIGHT - CENT_X, CENT_S, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.cent.title"));
            lines.add(Lang.tr("sc.gui.cent.hint"));
            return lines;
        }
        if (pack && GuiGaugeSC.isOver(PACK_X, PACK_Y, PACK_STAGE_X + PACK_STAGE_W - PACK_X, PACK_H, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr("sc.gui.pack.title"));
            if (r != null) {
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.pack.hint"));
            return lines;
        }
        if (sput && GuiGaugeSC.isOver(SAW_X, CAPTION_Y - 1, SAW_W, SAW_Y + 44 - CAPTION_Y + 1, mouseX, mouseY)) {
            int k = target();
            lines.add(Lang.tr("sc.gui.sput.title"));
            lines.add(k < 0 ? Lang.tr("sc.gui.sput.none") : Lang.tr("sc.gui.sput.now", Lang.tr("sc.gui.sput.el." + k)));
            lines.add(Lang.tr("sc.gui.sput.hint"));
            return lines;
        }
        if (sput && GuiGaugeSC.isOver(SAW_X, SPUT_WEAR_Y - 2, SAW_W, 14, mouseX, mouseY)) {
            net.minecraft.item.ItemStack tg = wire();
            lines.add(Lang.tr("sc.gui.sput.target.title"));
            lines.add(tg == null ? Lang.tr("sc.gui.sput.notarget")
                    : Lang.tr("sc.gui.sput.left", tg.getMaxDamage() - tg.getItemDamage(), tg.getMaxDamage()));
            return lines;
        }
        if (ion && GuiGaugeSC.isOver(SAW_X, CAPTION_Y - 1, SAW_W, SAW_Y + 44 - CAPTION_Y + 1, mouseX, mouseY)) {
            int d = dopant();
            lines.add(Lang.tr("sc.gui.ion.title"));
            lines.add(d < 0 ? Lang.tr("sc.gui.ion.nogas") : Lang.tr("sc.gui.ion.now", Lang.tr("sc.gui.ion.el." + d), d == 1 ? "p" : "n"));
            lines.add(Lang.tr("sc.gui.ion.hint"));
            return lines;
        }
        if (etch && GuiGaugeSC.isOver(CVD_BELL_X, TANK_Y, CVD_BELL_W, CVD_BELL_H, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            int ticks = machine.getCurrentRecipeTicks();
            boolean second = ticks > 0 && machine.getProgressTicks() * 2 >= ticks;
            lines.add(Lang.tr("sc.gui.etch.title"));
            lines.add(Lang.tr(second ? "sc.gui.etch.stage.1" : "sc.gui.etch.stage.0"));
            if (r != null) {
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.etch.hint"));
            return lines;
        }
        if (step && GuiGaugeSC.isOver(SAW_X, SAW_Y, MAP_X + STEP_W - SAW_X, STEP_H, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr("sc.gui.step.title"));
            if (r != null) {
                if (r.fluidInputA != null) {
                    lines.add(Lang.tr("sc.gui.cvd.run", String.valueOf(r.fluidInputA.amount)) + " " + r.fluidInputA.getLocalizedName());
                }
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.step.hint"));
            return lines;
        }
        if (step && GuiGaugeSC.isOver(SAW_X, MASK_Y, SAW_W, 7, mouseX, mouseY)) {
            net.minecraft.item.ItemStack mask = wire();
            lines.add(Lang.tr("sc.gui.step.mask.title"));
            lines.add(mask == null ? Lang.tr("sc.gui.step.nomask")
                    : Lang.tr("sc.gui.step.mask", mask.getMaxDamage() - mask.getItemDamage(), mask.getMaxDamage()));
            return lines;
        }
        if (coat && GuiGaugeSC.isOver(SAW_X, SAW_Y, STAGE_X + STAGE_W - SAW_X, COAT_H, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr("sc.gui.coat.title"));
            if (r != null) {
                if (r.fluidInputA != null) {
                    lines.add(Lang.tr("sc.gui.cvd.run", String.valueOf(r.fluidInputA.amount)) + " " + r.fluidInputA.getLocalizedName());
                }
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.coat.hint"));
            return lines;
        }
        if (oxid && GuiGaugeSC.isOver(SAW_X, CAPTION_Y - 1, SAW_W, OX_WAFER_Y + 10 - CAPTION_Y + 1, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr(oxidising() ? "sc.gui.oxid.title.0" : "sc.gui.oxid.title.1"));
            if (r != null) {
                if (r.fluidInputA != null) {
                    lines.add(Lang.tr("sc.gui.cvd.run", String.valueOf(r.fluidInputA.amount)) + " " + r.fluidInputA.getLocalizedName());
                }
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            lines.add(Lang.tr("sc.gui.oxid.hint"));
            return lines;
        }
        if (saw && GuiGaugeSC.isOver(SAW_X, SAW_Y, SAW_W, SAW_H + 20, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr(sk("title")));
            if (r != null) {
                if (r.fluidInputA != null) {
                    lines.add(Lang.tr("sc.gui.cvd.run", String.valueOf(r.fluidInputA.amount)) + " " + r.fluidInputA.getLocalizedName());
                }
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            return lines;
        }
        if (saw && GuiGaugeSC.isOver(SAW_X, WEAR_Y - 2, WATER_X - SAW_X - 4, 14, mouseX, mouseY)) {
            net.minecraft.item.ItemStack wire = wire();
            lines.add(Lang.tr(sk("wire.title")));
            lines.add(wire == null ? Lang.tr(sk("nowire"))
                    : Lang.tr(sk("left"), wire.getMaxDamage() - wire.getItemDamage(), wire.getMaxDamage()));
            return lines;
        }
        if (puller && GuiGaugeSC.isOver(FURNACE_X, FURNACE_Y, FURNACE_W, FURNACE_H, mouseX, mouseY)) {
            com.sc.machine.MachineRecipe r = shownRecipe();
            lines.add(Lang.tr("sc.gui.puller.title"));
            if (r != null) {
                if (r.fluidInputA != null) {
                    lines.add(Lang.tr("sc.gui.cvd.run", String.valueOf(r.fluidInputA.amount)) + " " + r.fluidInputA.getLocalizedName());
                }
                lines.add(Lang.tr("sc.gui.cvd.defect", Math.round(r.defectChance * 100), Math.max(1, machine.effectiveTicks(r) / 20)));
            }
            return lines;
        }
        if (heat() && (blast ? GuiGaugeSC.isOver(HEATBAR_X - 1, HEATBAR_Y - 10, WATER_X - HEATBAR_X - 4, 30, mouseX, mouseY)
                || GuiGaugeSC.isOver(THERMO_X, FURNACE_Y, 13, FURNACE_H, mouseX, mouseY)
                : step || ion ? GuiGaugeSC.isOver(SAW_X - 1, (step ? STEP_HEAT_Y : ION_HEAT_Y) - 1, SAW_W + 2, step ? 13 : 12, mouseX, mouseY)
                : GuiGaugeSC.isOver(PROGRESS_X - 1, HEAT_Y - 1, barW() + 2, HEAT_H + 2, mouseX, mouseY))) {
            lines.add(Lang.tr("sc.gui.heat"));
            lines.add(machine.getHeat() + " / " + TileEntityMachineSC.getHeatCapacity());
            lines.add(Lang.tr("sc.gui.heat.warning"));
            return lines;
        }

        if (GuiGaugeSC.isOver(GuiBigSC.UPG_LABEL_X, GuiBigSC.UPG_Y - 1, GuiBigSC.W - 16, 18, mouseX, mouseY)
                && !GuiBigSC.overUpgradeSlot(mouseX, mouseY, TileEntityMachineSC.UPGRADE_SLOTS)) {
            lines.add(Lang.tr("sc.gui.upgrades"));
            lines.add(Lang.tr("sc.gui.upgrades.hint"));
            return lines;
        }

        int ticks = machine.getCurrentRecipeTicks();
        if (ticks > 0 && GuiGaugeSC.isOver(PROGRESS_X - 1, PROGRESS_Y - 1, barW() + 2, PROGRESS_H + 2, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.progress"));
            lines.add(machine.getProgressTicks() * 100 / ticks + "%  (" + machine.getProgressTicks() + " / " + ticks + ")");
            return lines;
        }
        return null;
    }
}
