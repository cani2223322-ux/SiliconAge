package com.sc.inventory;

import java.util.ArrayList;
import java.util.List;

import com.sc.Reference;
import com.sc.machine.MachineStatus;
import com.sc.machine.RecipeRegistry;
import com.sc.manual.Lang;
import com.sc.tileentity.TileEntityMachineSC;

import net.minecraft.client.gui.inventory.GuiContainer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.fluids.FluidTank;

/**
 * Generic GUI for every machine (§13), holo-screen style: the sheet's steel panel and the player's
 * inventory, and above them one big dark screen (GuiHoloSC) holding the machine's slots in cyan
 * pockets, a segmented progress bar with the status under it, and on the right what the machine
 * does (a caption and its animated pictogram) with either the numbers (progress, energy use, input
 * voltage) or its fluid tanks - only the ones this machine uses or that hold something - and, for
 * heat-capable machines, a heat bar. The energy gauge (GuiEnergyGaugeSC) stands to the right of
 * the screen, the upgrade slots on their own side panel. Every gauge has a hover tooltip with the
 * actual numbers.
 *
 * All of these read the TileEntity directly; ContainerMachineSC is what keeps the client's copy
 * of those fields current (see its detectAndSendChanges).
 */
public class GuiMachineSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");

    /** The holo screen over the sheet's old slot and gauge area (the title bar above, the inventory's divider below). */
    private static final int SCREEN_X = 6, SCREEN_Y = 17, SCREEN_W = 142, SCREEN_H = 62;
    /** Slot rows (ContainerMachineSC places the slots here). */
    public static final int SLOT_X = 12, IN_Y = 22, OUT_Y = 58;
    /** The progress bar between the rows (NEI: a click opens this machine's recipes), the status under it. */
    public static final int PROGRESS_X = 12, PROGRESS_Y = 42, PROGRESS_W = 52, PROGRESS_H = 5;
    private static final int STATUS_Y = 48;
    /** The right-hand block: caption and pictogram, then numbers or tanks, heat at the bottom. */
    private static final int RIGHT_X = 72, CAPTION_Y = 21, PICTO_X = 130, PICTO_Y = 19, ROWS_Y = 37;
    private static final int TANK_Y = 37, TANK_W = 11, TANK_GAP = 12;
    private static final int HEAT_X = 74, HEAT_Y = 73, HEAT_W = 70, HEAT_H = 3;
    /** The energy gauge (GuiEnergyGaugeSC) right of the screen. */
    private static final int GAUGE_X = 150, GAUGE_Y = 16, GAUGE_W = 22, GAUGE_H = 54;

    /**
     * Upgrade side panel (IC2 style): its own little window right next to the main sheet, top
     * edge level with it, 4 slots in a column with vanilla's 7 px margin all round.
     */
    public static final int PANEL_X = 176, PANEL_W = 32, PANEL_Y = 0, PANEL_H = 86;
    public static final int UPGRADE_X = PANEL_X + 8, UPGRADE_Y = PANEL_Y + 8;

    private final TileEntityMachineSC machine;

    /** For NEI: which machine's recipe page the progress-bar click should open. */
    public com.sc.machine.MachineType getMachineType() {
        return machine.getMachineType();
    }
    /** Which of the four tanks any recipe of this machine type ever uses - fixed per type. */
    private final boolean[] tankUsed = new boolean[ContainerMachineSC.TANK_COUNT];
    /** Tanks shown this frame (used by the type or holding fluid), left to right. */
    private final int[] shownTanks = new int[ContainerMachineSC.TANK_COUNT];
    private int shownCount;

    public GuiMachineSC(InventoryPlayer playerInv, TileEntityMachineSC machine) {
        super(new ContainerMachineSC(playerInv, machine));
        this.machine = machine;
        for (int i = 0; i < tankUsed.length; i++) {
            tankUsed[i] = RecipeRegistry.usesTank(machine.getMachineType(), i);
        }
        xSize = PANEL_X + PANEL_W;
        ySize = 166;
    }

    private boolean heat() {
        return machine.getMachineType().heatCapable;
    }

    private void collectTanks() {
        shownCount = 0;
        for (int i = 0; i < ContainerMachineSC.TANK_COUNT; i++) {
            if (tankUsed[i] || machine.getTank(i).getFluidAmount() > 0) {
                shownTanks[shownCount++] = i;
            }
        }
    }

    private int tankH() {
        return (heat() ? HEAT_Y - 3 : SCREEN_Y + SCREEN_H - 2) - TANK_Y;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGaugeSC.bind(mc, TEXTURE);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, 176, ySize);
        drawUpgradePanel(x, y);

        collectTanks();
        // the sheet's old wells and pockets (slots, tanks, heat) go under plain steel first
        drawRect(x + 5, y + 16, x + 172, y + 80, 0xFFB9C1CC);
        GuiHoloSC.screen(x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        for (int i = 0; i < 3; i++) {
            GuiHoloSC.slot(x + SLOT_X + i * 18, y + IN_Y, false);
            GuiHoloSC.slot(x + SLOT_X + i * 18, y + OUT_Y,
                    machine.getStackInSlot(TileEntityMachineSC.INPUT_SLOTS + i) != null);
        }
        int ticks = machine.getCurrentRecipeTicks();
        float progress = ticks > 0 ? (float) machine.getProgressTicks() / ticks : 0F;
        GuiHoloSC.bar(x + PROGRESS_X, y + PROGRESS_Y, PROGRESS_W, PROGRESS_H, progress, 10,
                machine.getStatus() == MachineStatus.PROCESSING ? 0xFFFF8C1E : 0xFF6E7C8C);
        for (int i = 0; i < 3; i++) {                           // little arrows: the way items go
            int ax = x + SLOT_X + i * 18 + 6;
            drawRect(ax, y + 40, ax + 4, y + 41, GuiHoloSC.CYAN_DIM);
            drawRect(ax + 1, y + 41, ax + 3, y + 42, GuiHoloSC.CYAN_DIM);
        }

        GuiGaugeSC.bind(mc, TEXTURE);
        drawProcessDisplay(x + PICTO_X, y + PICTO_Y);

        if (heat()) {
            GuiHoloSC.bar(x + HEAT_X, y + HEAT_Y, HEAT_W, HEAT_H, (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity(),
                    14, 0xFFFF5A3C);
        }

        GuiEnergyGaugeSC.draw(x + GAUGE_X, y + GAUGE_Y, GAUGE_W, GAUGE_H,
                (float) machine.getEnergyStored() / Math.max(1, machine.getMaxEnergyStored()));

        // Tanks last: drawFluid() switches to the blocks atlas.
        for (int k = 0; k < shownCount; k++) {
            int i = shownTanks[k];
            FluidTank tank = machine.getTank(i);
            GuiTankGaugeSC.drawCompact(mc, x + RIGHT_X + 2 + k * TANK_GAP, y + TANK_Y, TANK_W, tankH(), tank.getFluid(),
                    tank.getCapacity(), false);
        }
        GuiHoloSC.glint(x + SCREEN_X, y + SCREEN_Y, SCREEN_W, SCREEN_H);
        GuiGaugeSC.bind(mc, TEXTURE);
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fit(machine.getMachineType().localizedName(), 8, 5, titleRoom(machine.getMachineType().tier), GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, machine.getMachineType().tier, 176 - 6, 3);
        MachineStatus status = machine.getStatus();
        fit(status.localized(), PROGRESS_X, STATUS_Y, 58, statusColor(status));
        fit(Lang.tr("sc.gui.holo.process." + processKind(machine.getMachineType())), RIGHT_X, CAPTION_Y, PICTO_X - RIGHT_X - 2,
                GuiHoloSC.CYAN & 0xFFFFFF);
        // the numbers, right of the tanks (or the whole block without tanks)
        int rx = RIGHT_X + 2 + (shownCount > 0 ? shownCount * TANK_GAP + 2 : 0);
        int room = SCREEN_X + SCREEN_W - 3 - rx;
        if (room >= 26) {
            int ticks = machine.getCurrentRecipeTicks();
            List<String> rows = new ArrayList<String>();
            rows.add(Lang.tr("sc.gui.holo.progress", ticks > 0 ? machine.getProgressTicks() * 100 / ticks + "%" : "-"));
            rows.add(Lang.tr("sc.gui.holo.energy", machine.effectiveEuPerTick()));
            rows.add(Lang.tr("sc.gui.holo.input", machine.inputTier().name(), machine.inputTier().getVoltage()));
            if (heat()) {
                rows.add(Lang.tr("sc.gui.holo.heat", machine.getHeat() * 100 / Math.max(1, TileEntityMachineSC.getHeatCapacity())));
            }
            int bottom = heat() ? HEAT_Y - 3 : SCREEN_Y + SCREEN_H - 2;      // with heat, its bar says it (and the tooltip)
            for (int i = 0; i < rows.size() && ROWS_Y + i * 9 + 8 <= bottom; i++) {
                fit(rows.get(i), rx, ROWS_Y + i * 9, room, i == 0 ? GuiHoloSC.VALUE : GuiHoloSC.LABEL);
            }
        }
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
     * The side panel with the four upgrade slot pockets, in the main sheet's steel style: the
     * dark outline (its left edge shared with the sheet's right one), light top / left bevel,
     * dark right / bottom bevel, and the sheet's steel slot pockets.
     */
    private void drawUpgradePanel(int x, int y) {
        int x0 = x + PANEL_X - 1, y0 = y + PANEL_Y, x1 = x + PANEL_X + PANEL_W, y1 = y0 + PANEL_H;
        drawRect(x0, y0, x1, y1, GuiGaugeSC.OUTLINE);
        drawRect(x0 + 1, y0 + 1, x1 - 1, y1 - 1, GuiGaugeSC.PANEL);
        drawRect(x0 + 1, y0 + 1, x1 - 2, y0 + 2, GuiGaugeSC.BEVEL_LIGHT);   // top
        drawRect(x0 + 1, y0 + 1, x0 + 2, y1 - 2, GuiGaugeSC.BEVEL_LIGHT);   // left
        drawRect(x1 - 2, y0 + 2, x1 - 1, y1 - 1, GuiGaugeSC.BEVEL_DARK);    // right
        drawRect(x0 + 2, y1 - 2, x1 - 1, y1 - 1, GuiGaugeSC.BEVEL_DARK);    // bottom
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
        GuiGaugeSC.bind(mc, TEXTURE);
        for (int i = 0; i < TileEntityMachineSC.UPGRADE_SLOTS; i++) {
            drawTexturedModalRect(x + UPGRADE_X - 1, y + UPGRADE_Y - 1 + i * 18, GuiGaugeSC.SPR_STEEL_SLOT_U, GuiGaugeSC.SPR_STEEL_SLOT_V, 18, 18);
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
            org.lwjgl.opengl.GL11.glColor4f(0.45F, 0.45F, 0.45F, 1F);
        }
        GuiGaugeSC.drawBlended(this, x, y, u, v, 16, 16);
        org.lwjgl.opengl.GL11.glColor4f(1F, 1F, 1F, 1F);
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

    /** Room for the title: up to the tier plate at the right of the title bar. */
    private int titleRoom(com.sc.energy.Tier tier) {
        return 176 - 6 - (fontRendererObj.getStringWidth(tier.name()) + 4) - 4 - 8;
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

    private static boolean overUpgradeSlot(int mouseX, int mouseY) {
        for (int i = 0; i < TileEntityMachineSC.UPGRADE_SLOTS; i++) {
            if (GuiGaugeSC.isOver(UPGRADE_X - 1, UPGRADE_Y - 1 + i * 18, 18, 18, mouseX, mouseY)) {
                return true;
            }
        }
        return false;
    }

    /** @return the lines to show for whatever gauge the cursor is over, or null for none. */
    private List<String> tooltipAt(int mouseX, int mouseY) {
        List<String> lines = new ArrayList<String>();

        if (GuiGaugeSC.isOver(GAUGE_X, GAUGE_Y, GAUGE_W, GAUGE_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(machine.getEnergyStored() + " / " + machine.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.usage", machine.effectiveEuPerTick()));
            lines.add(Lang.tr("sc.gui.input", machine.inputTier().name(), machine.inputTier().getVoltage()));
            return lines;
        }

        for (int k = 0; k < shownCount; k++) {
            int i = shownTanks[k];
            if (GuiGaugeSC.isOver(RIGHT_X + 2 + k * TANK_GAP, TANK_Y, TANK_W, tankH(), mouseX, mouseY)) {
                FluidTank tank = machine.getTank(i);
                lines.add(Lang.tr(i < 2 ? "sc.gui.tank.input" : "sc.gui.tank.output", (i % 2) + 1));
                lines.add(GuiGaugeSC.fluidLabel(tank.getFluid(), tank.getCapacity()));
                lines.add(GuiTankGaugeSC.percentLine(tank.getFluid(), tank.getCapacity()));
                return lines;
            }
        }

        if (heat() && GuiGaugeSC.isOver(HEAT_X - 1, HEAT_Y - 1, HEAT_W + 2, HEAT_H + 2, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.heat"));
            lines.add(machine.getHeat() + " / " + TileEntityMachineSC.getHeatCapacity());
            lines.add(Lang.tr("sc.gui.heat.warning"));
            return lines;
        }

        if (GuiGaugeSC.isOver(PANEL_X, PANEL_Y, PANEL_W, PANEL_H, mouseX, mouseY) && !overUpgradeSlot(mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.upgrades"));
            lines.add(Lang.tr("sc.gui.upgrades.hint"));
            return lines;
        }

        int ticks = machine.getCurrentRecipeTicks();
        if (ticks > 0 && GuiGaugeSC.isOver(PROGRESS_X - 1, PROGRESS_Y - 1, PROGRESS_W + 2, PROGRESS_H + 2, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.progress"));
            lines.add(machine.getProgressTicks() * 100 / ticks + "%  (" + machine.getProgressTicks() + " / " + ticks + ")");
            return lines;
        }
        return null;
    }
}
