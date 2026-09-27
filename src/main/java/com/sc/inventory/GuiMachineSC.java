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
    /** The Chemical Reactor's own screen: its recipe as a formula, tank + tank -> flask -> tank. */
    private final boolean chem;
    private static final int CHEM_X = 76, CHEM_IN2 = 32, CHEM_FLASK = 64, CHEM_OUT = 97, CHEM_FLASK_W = 30, CHEM_FLASK_H = 50;
    /** The CVD Chamber's own screen: its recipe as a formula, tank -> the Siemens bell jar <- tank. */
    private final boolean cvd;
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
        for (int i = 0; i < tankUsed.length; i++) {
            tankUsed[i] = RecipeRegistry.usesTank(machine.getMachineType(), i);
        }
        withTanks = ContainerMachineSC.tightSlots(machine.getMachineType());
        washer = machine.getMachineType() == com.sc.machine.MachineType.ORE_WASHER;
        blast = machine.getMachineType() == com.sc.machine.MachineType.BLAST_FURNACE;
        chem = machine.getMachineType() == com.sc.machine.MachineType.CHEM_REACTOR;
        cvd = machine.getMachineType() == com.sc.machine.MachineType.CVD_CHAMBER;
        ownTanks = chem ? new int[]{0, 1, 2} : cvd ? new int[]{0, 1} : null;
        ownTankX = chem ? new int[]{CHEM_X, CHEM_X + CHEM_IN2, CHEM_X + CHEM_OUT} : cvd ? new int[]{CHEM_X, CVD_TANK2_X} : null;
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
            if (washer || blast) {
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
        if (washer || blast || ownTanks != null) {
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
        if (heat() && !blast) {
            GuiHoloSC.bar(x + PROGRESS_X, y + HEAT_Y, barW(), HEAT_H, (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity(),
                    12, 0xFFFF5A3C);
        }
        int rx = rightX();
        drawRect(x + rx - 4, y + GuiBigSC.SCREEN_Y + 6, x + rx - 3, y + GuiBigSC.SCREEN_Y + GuiBigSC.SCREEN_H - 6, 0xFF1E3444);

        GuiGaugeSC.bind(mc, TEXTURE);
        if (cvd) {                                                      // the bell jar between the two gas tanks
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
            GuiSceneSC.furnace(x + FURNACE_X, y + FURNACE_Y, FURNACE_W, FURNACE_H, t, machine.getStatus() == MachineStatus.PROCESSING, h);
            GuiSceneSC.thermometer(x + THERMO_X, y + FURNACE_Y, FURNACE_H, h, (float) TileEntityMachineSC.HEAT_RESUME / TileEntityMachineSC.getHeatCapacity());
            GuiSceneSC.heatBar(x + HEATBAR_X, y + HEATBAR_Y, HEATBAR_W, HEATBAR_H, h,
                    (float) TileEntityMachineSC.HEAT_RESUME / TileEntityMachineSC.getHeatCapacity());
        } else if (washer) {                                            // the washing tub: its water is the tank
            FluidTank water = machine.getTank(0);
            float level = water.getCapacity() > 0 ? (float) water.getFluidAmount() / water.getCapacity() : 0F;
            float t = mc.theWorld == null ? 0F : mc.theWorld.getTotalWorldTime() + partialTicks;
            GuiSceneSC.washer(x + TUB_X, y + TUB_Y, TUB_W, TUB_H, t, machine.getStatus() == MachineStatus.PROCESSING, level);
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

        GuiEnergyGaugeSC.draw(x + GuiBigSC.GAUGE_X, y + GuiBigSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiBigSC.GAUGE_H,
                (float) machine.getEnergyStored() / Math.max(1, machine.getMaxEnergyStored()));

        // Tanks last: drawing a fluid switches to the blocks atlas.
        if (washer || blast) {
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
        if (chem) {
            drawChemText();
            drawUpgradeLine();
            return;
        }
        if (cvd) {
            drawCvdText();
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
        String second = blast ? Lang.tr("sc.gui.blast.sinks", machine.upgradeCount(UpgradeType.HEAT_SINK),
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
        fit(Lang.tr("sc.gui.holo.smelting"), rx, CAPTION_Y, WATER_X - rx - 4, GuiHoloSC.CYAN & 0xFFFFFF);
        FluidTank tank = machine.getTank(0);
        String name = tank.getFluid() != null ? tank.getFluid().getLocalizedName() : recipeFluidName();
        TextFitSC.drawCentered(fontRendererObj, name, WATER_X, TANK_LABEL_Y - 1, GuiTankGaugeSC.WIDTH - 7, GuiHoloSC.LABEL, false, guiLeft, guiTop);
        int heat = machine.getHeat(), cap = TileEntityMachineSC.getHeatCapacity(), resume = TileEntityMachineSC.HEAT_RESUME;
        fit(Lang.tr("sc.gui.blast.heat", heat, cap), HEATBAR_X, HEATBAR_Y - 9, HEATBAR_W, 0xFFAA5A);
        small(String.valueOf(resume), HEATBAR_X + HEATBAR_W * resume / cap - 3, HEATBAR_Y + 6, 0xF0C450);
        small(String.valueOf(cap), HEATBAR_X + HEATBAR_W - 9, HEATBAR_Y + 6, 0xE65A5A);
        int sinks = machine.upgradeCount(UpgradeType.HEAT_SINK);
        String line;
        if (machine.getStatus() == MachineStatus.OVERHEATED) {
            line = Lang.tr("sc.gui.blast.paused", resume, Math.max(1, (heat - resume) / 2 / 20));
        } else if (machine.getStatus() == MachineStatus.PROCESSING) {
            line = Lang.tr("sc.gui.blast.topause", Math.max(0, (cap - heat) * (sinks + 1) / 20));
        } else {
            line = heat > 0 ? Lang.tr("sc.gui.blast.cooling", heat / 2 / 20 + 1) : Lang.tr("sc.gui.blast.cold");
        }
        fit(line, HEATBAR_X, HEATBAR_Y + 13, WATER_X - HEATBAR_X - 4, GuiHoloSC.VALUE);
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

        if (GuiGaugeSC.isOver(GuiBigSC.GAUGE_X, GuiBigSC.GAUGE_Y, GuiBigSC.GAUGE_W, GuiBigSC.GAUGE_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(machine.getEnergyStored() + " / " + machine.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.usage", machine.effectiveEuPerTick()));
            lines.add(Lang.tr("sc.gui.input", machine.inputTier().name(), machine.inputTier().getVoltage()));
            return lines;
        }

        boolean overTub = washer && GuiGaugeSC.isOver(TUB_X, TUB_Y, TUB_W, TUB_H, mouseX, mouseY);
        boolean overOwnTank = (washer || blast)
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

        if (heat() && (blast ? GuiGaugeSC.isOver(HEATBAR_X - 1, HEATBAR_Y - 10, WATER_X - HEATBAR_X - 4, 30, mouseX, mouseY)
                || GuiGaugeSC.isOver(THERMO_X, FURNACE_Y, 13, FURNACE_H, mouseX, mouseY)
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
