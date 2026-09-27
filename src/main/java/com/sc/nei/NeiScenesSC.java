package com.sc.nei;

import com.sc.init.ModFluids;
import com.sc.init.ModItems;
import com.sc.inventory.GuiSceneSC;
import com.sc.inventory.GuiTankGaugeSC;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;

import net.minecraft.client.gui.Gui;
import net.minecraft.item.ItemStack;
import net.minecraftforge.fluids.FluidStack;

/**
 * The machines' own scenes (GuiSceneSC) for their NEI pages: each recipe shows the process
 * running, sized to the band between its tanks, with the recipe's own colours where the screen
 * takes them from the tanks (fluids, the resist, the gases).
 */
final class NeiScenesSC {

    private NeiScenesSC() {
    }

    private static int col(FluidStack f) {
        return f == null || f.getFluid() == null ? 0 : GuiTankGaugeSC.colourOf(f.getFluid()) | 0xFF000000;
    }

    /** @return false for a machine with no scene of its own (the caller draws the arrow) */
    static boolean draw(MachineType type, MachineRecipe r, int x, int y, int w, int h, float t, float p) {
        if (w < 24) {
            return false;
        }
        switch (type) {
            case CRUSHER:
                GuiSceneSC.crusher(x, y, w, h, t, true, p);
                return true;
            case ORE_WASHER:
                GuiSceneSC.washer(x, y, w, h, t, true, 0.6F);
                return true;
            case BLAST_FURNACE:
                GuiSceneSC.furnace(x, y, w, h, t, true, 0.6F);
                return true;
            case CHEM_REACTOR:
                GuiSceneSC.flask(x, y, w, h, t, true, col(r.fluidInputA), col(r.fluidInputB), col(r.fluidOutputA), p);
                return true;
            case CVD_CHAMBER:
                GuiSceneSC.bell(x, y, w, h, t, true, p, col(r.fluidInputA), col(r.fluidInputB));
                return true;
            case CZOCHRALSKI_PULLER:
            case CZOCHRALSKI_PULLER_EV:
                GuiSceneSC.puller(x, y, w, h, t, true, p, 0.5F);
                return true;
            case WIRE_SAW:
                GuiSceneSC.wireSaw(x, y, w, h, t, true, p);
                return true;
            case OXIDATION_FURNACE: {
                int n = r.outputs.length > 0 && r.outputs[0] != null ? r.outputs[0].stackSize : 1;
                boolean gas = r.fluidInputA != null;
                GuiSceneSC.tubeFurnace(x, y, w, h, t, true, p, n, gas, gas);
                return true;
            }
            case PHOTORESIST_COATER:
                GuiSceneSC.spinCoater(x, y, w, h, t, true, p, col(r.fluidInputA));
                return true;
            case STEPPER:
            case STEPPER_EV:
                GuiSceneSC.stepperColumn(x, y, w, h, t, true, p);
                return true;
            case ETCHING_BATH:
                GuiSceneSC.etchBaths(x, y, w, h, t, true, p, col(r.fluidInputA), col(r.fluidInputB));
                return true;
            case ION_IMPLANTER:
                GuiSceneSC.beamLine(x, y, w, h, t, true, p);
                return true;
            case SPUTTERER:
                GuiSceneSC.sputterChamber(x, y, w, h, t, true, p, sputterMetal(r));
                return true;
            case DICING_SAW:
                GuiSceneSC.dicingSaw(x, y, w, h, t, true, p);
                return true;
            case PACKAGER: {
                int pins = 3, dies = 1;
                for (ItemStack s : r.inputs) {
                    if (s == null) {
                        continue;
                    }
                    if (s.getItem() == ModItems.leadFrame16) {
                        pins = 16;
                    } else if (s.getItem() == ModItems.leadFrame40) {
                        pins = 40;
                    } else if (s.getItem() != ModItems.leadFrame3 && s.getItem() != ModItems.compound) {
                        dies = s.stackSize;
                    }
                }
                GuiSceneSC.packager(x, y, w, h, t, true, p, pins, dies);
                return true;
            }
            case CENTRIFUGE:
                GuiSceneSC.centrifuge(x, y, w, h, t, true, p, 0);
                return true;
            case CHLOR_ALKALI_ELECTROLYZER:
                GuiSceneSC.electrolysisCell(x, y, w, h, t, true, col(r.fluidOutputB != null ? r.fluidOutputB : r.fluidOutputA),
                        col(r.fluidOutputA));
                return true;
            case AIR_SEPARATOR:
                GuiSceneSC.airColumn(x, y, w, h, t, true, p);
                return true;
            case REFINERY:
                GuiSceneSC.refineryTower(x, y, w, h, t, true,
                        r.fluidOutputA != null && r.fluidOutputA.getFluid() == ModFluids.photoresist ? 1 : 2);
                return true;
            case ROLLING_MACHINE:
                GuiSceneSC.rollingMill(x, y, w, h, t, true, p, 0);
                return true;
            case UPGRADE_STATION_MV:
            case UPGRADE_STATION_HV:
            case UPGRADE_STATION_EV: {
                boolean chip = r.outputs.length > 0 && r.outputs[0] != null && r.outputs[0].getItem() instanceof com.sc.item.ItemArmorChipSC;
                GuiSceneSC.upgradeBench(x, y, w, h, t, true, p, chip ? 0xFF4AA8E8 : 0xFF4A8AD8, chip ? 0xFFE8C850 : 0xFF9A5AE0, chip);
                return true;
            }
            case KILN:
                GuiSceneSC.kiln(x, y, w, h, t, true, p, 0xFF6A6A72, 0xFFB08A6A);
                return true;
            case FLUID_CELL_FILLER:
                GuiSceneSC.cellFiller(x, y, w, h, t, true, p, col(r.fluidInputA));
                return true;
            case BOILER_LV:
            case BOILER_MV:
                GuiSceneSC.boiler(x, y, w, h, t, true, r.fluidInputB == null);
                return true;
            default:
                return false;
        }
    }

    private static int sputterMetal(MachineRecipe r) {
        for (ItemStack s : r.inputs) {
            if (s != null && s.getItem() instanceof com.sc.item.ItemToolSC) {
                com.sc.util.SCToolType k = ((com.sc.item.ItemToolSC) s.getItem()).getType();
                return k == com.sc.util.SCToolType.SPUTTER_TARGET_ALUMINIUM ? 0xFFC8D0DC
                        : k == com.sc.util.SCToolType.SPUTTER_TARGET_TUNGSTEN ? 0xFF8A8A9A : 0xFFD8844A;
            }
        }
        return 0xFFD8844A;
    }

    /** No scene: a holo arrow of chevrons, lit one by one with the progress. */
    static void arrow(int x, int y, int w, int h, float p) {
        GuiSceneSC.frame(x, y, w, h);
        int cy = y + h / 2, n = Math.max(3, (w - 8) / 7), lit = (int) (p * (n + 1));
        for (int i = 0; i < n; i++) {
            int cx = x + 4 + i * 7, c = i < lit ? 0xFF6EE6FF : 0xFF1E3444;
            for (int k = 0; k < 4; k++) {
                Gui.drawRect(cx + k, cy - 4 + k, cx + k + 2, cy - 3 + k, c);
                Gui.drawRect(cx + k, cy + 3 - k, cx + k + 2, cy + 4 - k, c);
            }
        }
    }
}
