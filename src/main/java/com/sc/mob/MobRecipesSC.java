package com.sc.mob;

import com.sc.init.ModItems;
import com.sc.machine.MachineRecipe;
import com.sc.machine.MachineType;
import com.sc.machine.RecipeRegistry;
import com.sc.util.Material;
import com.sc.util.SiliconMaterial;

import net.minecraft.item.ItemStack;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7): what the combat loot is good for before the traders come (Ф2) -
 * machine recipes of this mod only (RecipeRegistry), vanilla and mod items only. Called from SCMod.registerRecipes.
 * <p>
 * About a tenth of what the materials are worth (TODO(balance)). The plan's «2 tiny dusts a piece» can't be had: the
 * mod's tiny dusts exist only for the Centrifuge's traces (Ag, Ga, In, Nb, Hf, ash), not for titanium or copper. The
 * same value as whole dusts instead: four pieces - one dust (9 tiny / 4 = 2.25 a piece), the Transistor's 5% a piece
 * rolled once for the four (20%).
 * <ul>
 * <li>4 Shell Firmware -> 1 Copper Dust, 20% a Transistor (its traces and its controller chip);</li>
 * <li>4 Nano Armour Scrap -> 1 Titanium Concentrate (Nano is built of titanium and polymer plates: a chestplate holds
 * 2 titanium ingots - a tenth of that is about what four scraps of 40% Marauders give back).</li>
 * </ul>
 * The inputs are this mod's own items: no other recipe in the pack can match them (-Dsc.packcheck).
 */
public final class MobRecipesSC {

    /** Pieces a Crusher run takes; the Transistor's chance of a firmware run. */
    public static final int FIRMWARE_IN = 4, SCRAP_IN = 4;
    public static final float FIRMWARE_TRANSISTOR = 0.2F;
    private static final int TICKS = 200;          // as the Crusher's ores

    private MobRecipesSC() {
    }

    public static void init() {
        if (!(MobItemsSC.combatLoot instanceof ItemCombatLootSC)) {
            return;
        }
        ItemCombatLootSC loot = (ItemCombatLootSC) MobItemsSC.combatLoot;
        RecipeRegistry.register(new MachineRecipe(MachineType.CRUSHER,
                new ItemStack[]{loot.stackOf(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, FIRMWARE_IN)}, null, null,
                new ItemStack[]{ModItems.dust.stackOf(Material.COPPER)}, null, null,
                TICKS, 0F,
                new ItemStack[]{ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR)}, new float[]{FIRMWARE_TRANSISTOR}));
        RecipeRegistry.register(new MachineRecipe(MachineType.CRUSHER,
                new ItemStack[]{loot.stackOf(ItemCombatLootSC.LootKind.NANO_SCRAP, SCRAP_IN)}, null, null,
                new ItemStack[]{ModItems.dust.stackOf(Material.TITANIUM)}, null, null,
                TICKS, 0F));
    }

    /** How many of a kind one Crusher run takes (the tooltip). */
    public static int inputCount(ItemCombatLootSC.LootKind kind) {
        return kind == ItemCombatLootSC.LootKind.NANO_SCRAP ? SCRAP_IN : FIRMWARE_IN;
    }
}
