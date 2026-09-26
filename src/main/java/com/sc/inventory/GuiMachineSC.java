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
 * Generic GUI for every machine (§13). The background texture bakes in the slot pockets and the
 * recessed wells; everything live is drawn on top of them here - the progress arrow (inputs row
 * down to outputs row), the four fluid tanks (2 inputs with blue rims + 2 outputs with amber
 * rims, drawn with the fluid's own texture; tanks this machine never uses are hatched out),
 * heat for heat-capable machines, and the energy buffer - each with a hover tooltip carrying
 * the actual numbers, since a bare bar can't tell a player whether "nearly full" means 90 EU
 * or 9000. A tier plate sits at the right of the title and the status line is coloured by
 * state, so a stalled machine reads as one at a glance.
 *
 * All of these read the TileEntity directly; ContainerMachineSC is what keeps the client's copy
 * of those fields current (see its detectAndSendChanges).
 */
public class GuiMachineSC extends GuiContainer {

    private static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/gui/guiMachine.png");

    // Between the middle input and middle output slot, pointing down the way items travel.
    public static final int PROGRESS_X = 44, PROGRESS_Y = 35;
    /** The process display baked into the sheet between the slots and the tanks. */
    private static final int DISPLAY_X = 80, DISPLAY_Y = 30;
    private static final int PROGRESS_SIZE = 16;
    // Gauges start below the title row's divider (y 15), so a long machine name has the whole
    // top row to itself - at y 13 the tank and energy rims sat inside the title's line, and seven
    // English / eight Russian names ran straight into them.
    private static final int TANK_X = 104, TANK_Y = 17, TANK_W = 8, TANK_H = 44, TANK_GAP = 12;
    private static final int ENERGY_X = 152, ENERGY_Y = 17, ENERGY_W = 10, ENERGY_H = 44;
    // The status line owns y 72 (it used to be drawn at y 60, straight across the output slots);
    // heat sits on the same row, right of the longest status text (108 px).
    private static final int STATUS_X = 8, STATUS_Y = 72;
    private static final int HEAT_X = 120, HEAT_Y = 73, HEAT_W = 42, HEAT_H = 5;

    /**
     * Upgrade side panel (IC2 style): its own little window right next to the main sheet, top
     * edge level with it, 4 slots in a column with vanilla's 7 px margin all round.
     */
    public static final int PANEL_X = 176, PANEL_W = 32, PANEL_Y = 0, PANEL_H = 86;
    public static final int UPGRADE_X = PANEL_X + 8, UPGRADE_Y = PANEL_Y + 8;

    private final TileEntityMachineSC machine;

    /** For NEI: which machine's recipe page the progress-arrow click should open. */
    public com.sc.machine.MachineType getMachineType() {
        return machine.getMachineType();
    }
    /** Which of the four tanks any recipe of this machine type ever uses - fixed per type. */
    private final boolean[] tankUsed = new boolean[ContainerMachineSC.TANK_COUNT];

    public GuiMachineSC(InventoryPlayer playerInv, TileEntityMachineSC machine) {
        super(new ContainerMachineSC(playerInv, machine));
        this.machine = machine;
        for (int i = 0; i < tankUsed.length; i++) {
            tankUsed[i] = RecipeRegistry.usesTank(machine.getMachineType(), i);
        }
        xSize = PANEL_X + PANEL_W;
        ySize = 166;
    }

    @Override
    protected void drawGuiContainerBackgroundLayer(float partialTicks, int mouseX, int mouseY) {
        GuiGaugeSC.bind(mc, TEXTURE);
        int x = (width - xSize) / 2;
        int y = (height - ySize) / 2;
        drawTexturedModalRect(x, y, 0, 0, 176, ySize);
        drawUpgradePanel(x, y);

        drawProcessDisplay(x + DISPLAY_X, y + DISPLAY_Y);

        int ticks = machine.getCurrentRecipeTicks();
        if (ticks > 0) {
            GuiGaugeSC.drawSpriteDown(this, x + PROGRESS_X, y + PROGRESS_Y, GuiGaugeSC.SPR_ARROW_U, GuiGaugeSC.SPR_ARROW_V,
                    PROGRESS_SIZE, PROGRESS_SIZE, (float) machine.getProgressTicks() / ticks);
        }

        if (machine.getMachineType().heatCapable) {
            GuiGaugeSC.drawSpriteHorizontal(this, x + HEAT_X, y + HEAT_Y, GuiGaugeSC.SPR_HEAT_U, GuiGaugeSC.SPR_HEAT_V,
                    HEAT_W, HEAT_H, (float) machine.getHeat() / TileEntityMachineSC.getHeatCapacity());
        }

        GuiGaugeSC.drawSpriteVertical(this, x + ENERGY_X, y + ENERGY_Y, GuiGaugeSC.SPR_ENERGY_U, GuiGaugeSC.SPR_ENERGY_V,
                ENERGY_W, ENERGY_H, (float) machine.getEnergyStored() / Math.max(1, machine.getMaxEnergyStored()));

        // Tanks last: drawFluid() switches to the blocks atlas, so the sheet is rebound per tank.
        for (int i = 0; i < ContainerMachineSC.TANK_COUNT; i++) {
            int tx = x + TANK_X + i * TANK_GAP;
            FluidTank tank = machine.getTank(i);
            if (!tankUsed[i] && tank.getFluidAmount() == 0) {
                drawTexturedModalRect(tx, y + TANK_Y, GuiGaugeSC.SPR_UNUSED_U, GuiGaugeSC.SPR_UNUSED_V, TANK_W, TANK_H);
                continue;
            }
            GuiGaugeSC.drawFluid(mc, tx, y + TANK_Y, TANK_W, TANK_H, tank.getFluid(), tank.getCapacity());
            GuiGaugeSC.bind(mc, TEXTURE);
            GuiGaugeSC.drawBlended(this, tx, y + TANK_Y, GuiGaugeSC.SPR_GLASS_U, GuiGaugeSC.SPR_GLASS_V, TANK_W, TANK_H);
        }
    }

    @Override
    protected void drawGuiContainerForegroundLayer(int mouseX, int mouseY) {
        fontRendererObj.drawString(machine.getMachineType().localizedName(), 8, 5, GuiGaugeSC.TITLE_COLOR);
        GuiGaugeSC.drawTierBadge(fontRendererObj, machine.getMachineType().tier, 176 - 6, 3);
        MachineStatus status = machine.getStatus();
        fontRendererObj.drawString(status.localized(), STATUS_X, STATUS_Y, statusColor(status));
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
        super.drawScreen(mouseX, mouseY, partialTicks);
        List<String> tooltip = tooltipAt(mouseX - guiLeft, mouseY - guiTop);
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

    private static int statusColor(MachineStatus status) {
        switch (status) {
            case PROCESSING: return 0x2E7D32;
            case OUTPUT_FULL: return 0x9A6200;
            case NO_POWER:
            case OVERHEATED: return 0xB02418;
            default: return 0x606060;
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

        if (GuiGaugeSC.isOver(ENERGY_X, ENERGY_Y, ENERGY_W, ENERGY_H, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.energy"));
            lines.add(machine.getEnergyStored() + " / " + machine.getMaxEnergyStored() + " EU");
            lines.add(Lang.tr("sc.gui.usage", machine.effectiveEuPerTick()));
            lines.add(Lang.tr("sc.gui.input", machine.inputTier().name(), machine.inputTier().getVoltage()));
            return lines;
        }

        for (int i = 0; i < ContainerMachineSC.TANK_COUNT; i++) {
            if (GuiGaugeSC.isOver(TANK_X + i * TANK_GAP, TANK_Y, TANK_W, TANK_H, mouseX, mouseY)) {
                FluidTank tank = machine.getTank(i);
                lines.add(Lang.tr(i < 2 ? "sc.gui.tank.input" : "sc.gui.tank.output", (i % 2) + 1));
                if (!tankUsed[i] && tank.getFluidAmount() == 0) {
                    lines.add(Lang.tr("sc.gui.tank.unused"));
                    return lines;
                }
                lines.add(GuiGaugeSC.fluidLabel(tank.getFluid(), tank.getCapacity()));
                return lines;
            }
        }

        if (machine.getMachineType().heatCapable
                && GuiGaugeSC.isOver(HEAT_X, HEAT_Y, HEAT_W, HEAT_H, mouseX, mouseY)) {
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
        if (ticks > 0 && GuiGaugeSC.isOver(PROGRESS_X, PROGRESS_Y, PROGRESS_SIZE, PROGRESS_SIZE, mouseX, mouseY)) {
            lines.add(Lang.tr("sc.gui.progress"));
            lines.add(machine.getProgressTicks() * 100 / ticks + "%  (" + machine.getProgressTicks() + " / " + ticks + ")");
            return lines;
        }
        return null;
    }
}
