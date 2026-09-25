package com.sc.init;

import com.sc.item.ItemArmorChipSC;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;
import com.sc.util.Material;
import com.sc.util.SiliconMaterial;

import net.minecraft.item.ItemStack;

/**
 * Step 11 (§6): Upgrade Station recipes - registered as their own file since Upgrade Station
 * (all 3 tiers) was added back in step 6 (§14) with an empty recipe list, deliberately deferred
 * until armor/chips existed (step 8). Chip tier I and the base Nano corpus are plain crafting-
 * table recipes, registered in ModRecipesCrafting instead.
 */
public final class ModRecipesUpgradeStation {

    private ModRecipesUpgradeStation() {
    }

    public static void init() {
        registerChipUpgrades();
        registerSuitUpgrades();
        registerBladeUpgrades();
        registerDrillUpgrades();
    }

    /** The drills climb like the suits: Nano -> Quantum (HV) -> Exo (EV); charge and switches come along. */
    private static void registerDrillUpgrades() {
        ItemStack nano = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.NANO));
        ItemStack quantum = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.QUANTUM));
        ItemStack exo = new ItemStack(ModItems.DRILLS.get(com.sc.util.DrillType.EXO));
        // Quantum drill = Nano drill + W-Ti Plate x2 (the bit) + Tungsten Barrel (the spindle) -> Upgrade Station (HV).
        station(MachineType.UPGRADE_STATION_HV,
                new ItemStack[]{nano, copy(ModItems.component("wTiPlate"), 2), new ItemStack(ModItems.component("wBarrel"))},
                null, null, new ItemStack[]{quantum}, null, null, 400, 0f);
        // Exo drill = Quantum drill + Nb3Sn Plate x2 + He-Loop Module -> Upgrade Station (EV), as the Exo suit.
        station(MachineType.UPGRADE_STATION_EV,
                new ItemStack[]{quantum.copy(), copy(ModItems.component("nb3SnPlate"), 2), new ItemStack(ModItems.component("heLoopModule"))},
                null, null, new ItemStack[]{exo}, null, null, 500, 0f);
    }

    /** The energy blades climb like the suits: Nano -> Quantum (HV) -> Exo (EV); charge and switches come along. */
    private static void registerBladeUpgrades() {
        ItemStack nano = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.NANO));
        ItemStack quantum = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.QUANTUM));
        ItemStack exo = new ItemStack(ModItems.BLADES.get(com.sc.util.BladeType.EXO));
        // Quantum blade = Nano blade + W-Ti Plate x2 + Quartz Emitter (the brighter edge) -> Upgrade Station (HV).
        station(MachineType.UPGRADE_STATION_HV,
                new ItemStack[]{nano, copy(ModItems.component("wTiPlate"), 2), new ItemStack(ModItems.component("quartzEmitter"))},
                null, null, new ItemStack[]{quantum}, null, null, 400, 0f);
        // Exo blade = Quantum blade + Nb3Sn Plate x2 + He-Loop Module -> Upgrade Station (EV), as the Exo suit.
        station(MachineType.UPGRADE_STATION_EV,
                new ItemStack[]{quantum.copy(), copy(ModItems.component("nb3SnPlate"), 2), new ItemStack(ModItems.component("heLoopModule"))},
                null, null, new ItemStack[]{exo}, null, null, 500, 0f);
    }

    private static void registerChipUpgrades() {
        ItemStack die = ModItems.siliconMaterial.stackOf(SiliconMaterial.DIE);
        ItemStack lensOrSensor = new ItemStack(ModItems.component("lens"));
        ItemStack hfo2Die = new ItemStack(ModItems.component("hfo2Die"));
        ItemStack ndComponent = ModItems.ingot.stackOf(Material.NEODYMIUM);

        for (ChipType type : ChipType.values()) {
            ItemStack tier1 = new ItemStack(ModItems.armorChip, 1, ItemArmorChipSC.metaFor(type, 1));
            ItemStack tier2 = new ItemStack(ModItems.armorChip, 1, ItemArmorChipSC.metaFor(type, 2));
            ItemStack tier3 = new ItemStack(ModItems.armorChip, 1, ItemArmorChipSC.metaFor(type, 3));

            // Chip тир II: tier I + extra Die + Lens/Sensor -> Upgrade Station (MV), §6.
            station(MachineType.UPGRADE_STATION_MV,
                    new ItemStack[]{tier1, die.copy(), lensOrSensor.copy()}, null, null,
                    new ItemStack[]{tier2}, null, null, 300, 0f);

            // Chip тир III: tier II + HfO2-Die + Nd-component -> Upgrade Station (HV), §6.
            station(MachineType.UPGRADE_STATION_HV,
                    new ItemStack[]{tier2, hfo2Die.copy(), ndComponent.copy()}, null, null,
                    new ItemStack[]{tier3}, null, null, 300, 0f);
        }
    }

    private static void registerSuitUpgrades() {
        String[] pieces = {"helmet", "chestplate", "leggings", "boots"};
        for (int i = 0; i < pieces.length; i++) {
            ItemStack nanoPiece = new ItemStack(ModItems.ARMOR.get(ArmorSuit.NANO)[i]);
            ItemStack quantumPiece = new ItemStack(ModItems.ARMOR.get(ArmorSuit.QUANTUM)[i]);
            ItemStack exoPiece = new ItemStack(ModItems.ARMOR.get(ArmorSuit.EXO)[i]);

            // Quantum Suit (part) = Nano Suit (part) + W-Ti Plate x2 + Ceramic Package x2 -> Upgrade Station (HV), §6.
            station(MachineType.UPGRADE_STATION_HV,
                    new ItemStack[]{
                            nanoPiece,
                            copy(ModItems.component("wTiPlate"), 2),
                            copy(ModItems.component("ceramicPackage"), 2)
                    }, null, null,
                    new ItemStack[]{quantumPiece}, null, null, 400, 0f);

            // Exo Suit (part) = Quantum Suit (part) + Nb3Sn Plate x2 + He-Loop Module -> Upgrade Station (EV), §6.
            station(MachineType.UPGRADE_STATION_EV,
                    new ItemStack[]{
                            quantumPiece.copy(),
                            copy(ModItems.component("nb3SnPlate"), 2),
                            new ItemStack(ModItems.component("heLoopModule"))
                    }, null, null,
                    new ItemStack[]{exoPiece}, null, null, 500, 0f);
        }
    }

    /**
     * Registers an Upgrade Station recipe on `lowest` AND every higher station tier: the HV/EV
     * stations are crafted from the one below, so a recipe known only to its own tier vanished
     * as soon as the player upgraded the station (chip II only on MV, suits/blades split HV/EV).
     */
    private static void station(MachineType lowest, ItemStack[] inputs, net.minecraftforge.fluids.FluidStack fluidA,
                                net.minecraftforge.fluids.FluidStack fluidB, ItemStack[] outputs,
                                net.minecraftforge.fluids.FluidStack fluidOutA, net.minecraftforge.fluids.FluidStack fluidOutB,
                                int ticks, float defect) {
        MachineType[] tiers = {MachineType.UPGRADE_STATION_MV, MachineType.UPGRADE_STATION_HV, MachineType.UPGRADE_STATION_EV};
        boolean reached = false;
        for (MachineType tier : tiers) {
            reached |= tier == lowest;
            if (reached) {
                RecipeRegistry.register(new MachineRecipe(tier, inputs, fluidA, fluidB, outputs, fluidOutA, fluidOutB, ticks, defect));
            }
        }
    }

    private static ItemStack copy(net.minecraft.item.Item item, int count) {
        return new ItemStack(item, count);
    }
}
