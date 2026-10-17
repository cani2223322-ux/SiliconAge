package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.emp.EmpModule;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.util.ChipType;
import com.sc.util.SiliconMaterial;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.6, rules ПУ-2, ПУ-5; TODO(balance) all of it): what the ruins hold - only
 * this mod's items (and none of its machines, armour or weapons):
 * <ul>
 * <li>a Dead Node's chest (NODE_CHESTS_MIN..MAX a Node): ROLLS_MIN..ROLLS_MAX rolls of NODE (Controllers, Transistors, Memory
 * Chips, Nano Scrap, Shell Firmware), and a Singularity Crumb on NODE_CRUMB_CHANCE of the chests - it doesn't count towards a
 * player's daily crumbs (a chest knows no player);</li>
 * <li>a lithograph's chest (LITHO_CHESTS_MIN..MAX a hall): the rolls of LITHO (GaAs wafers, Transistors, Silicon wafers), a
 * random armour chip (tier I) on LITHO_CHIP_CHANCE of the chests, a random ПЭ-1 module on LITHO_MODULE_CHANCE - never the ПЭ-1
 * itself;</li>
 * <li>a mimic machine (its faction drop, ПТ-1): MIMIC_ROLLS_MIN..MIMIC_ROLLS_MAX rolls of LITHO, and with
 * MIMIC_COMPONENT_CHANCE one more component (a Controller, a Transistor or a Memory Chip).</li>
 * </ul>
 * A roll picks an entry by its weight and gives MIN..MAX of it. The tables are public for the handbook (BookWorldSC) and the
 * self-test: {weight, min, max} by entry, the entries' stacks by the same index (NODE_ITEMS, LITHO_ITEMS).
 */
public final class RuinLootSC {

    public static final int ROLLS_MIN = 3, ROLLS_MAX = 6;
    /** Chests: a Dead Node's (ComponentNodePlazaSC), a lithograph's hall's (ComponentLitHallSC). */
    public static final int NODE_CHESTS_MIN = 1, NODE_CHESTS_MAX = 3, LITHO_CHESTS_MIN = 2, LITHO_CHESTS_MAX = 4;
    /** Per chest. */
    public static final float NODE_CRUMB_CHANCE = 0.03F, LITHO_CHIP_CHANCE = 0.05F, LITHO_MODULE_CHANCE = 0.02F;
    /** A mimic's drop: rolls of the lithograph's table, the extra component's chance. */
    public static final int MIMIC_ROLLS_MIN = 1, MIMIC_ROLLS_MAX = 2;
    public static final float MIMIC_COMPONENT_CHANCE = 0.25F;

    /** The Dead Node's table: Controllers 1..2, Transistors 2..6, Memory Chips 1..3, Nano Scrap 1..4, Shell Firmware 1..3. */
    public static final int[][] NODE = {{2, 1, 2}, {5, 2, 6}, {3, 1, 3}, {4, 1, 4}, {4, 1, 3}};
    /** The lithograph's table: GaAs wafers 1..3, Transistors 2..8, Silicon wafers 2..6. */
    public static final int[][] LITHO = {{3, 1, 3}, {5, 2, 8}, {4, 2, 6}};

    private RuinLootSC() {
    }

    /** NODE's entries (null before the items exist). */
    public static ItemStack[] nodeItems() {
        return new ItemStack[]{silicon(SiliconMaterial.CONTROLLER), silicon(SiliconMaterial.TRANSISTOR), silicon(SiliconMaterial.MEMORY_CHIP),
                MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1), MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, 1)};
    }

    /** LITHO's entries. */
    public static ItemStack[] lithoItems() {
        return new ItemStack[]{silicon(SiliconMaterial.GAAS_WAFER), silicon(SiliconMaterial.TRANSISTOR), silicon(SiliconMaterial.SI_WAFER)};
    }

    /** A Dead Node chest's contents (server; `rand` - the chest's own). */
    public static List<ItemStack> nodeChest(Random rand) {
        List<ItemStack> out = rolls(NODE, nodeItems(), ROLLS_MIN + rand.nextInt(ROLLS_MAX - ROLLS_MIN + 1), rand);
        if (rand.nextFloat() < NODE_CRUMB_CHANCE && ModItems.singularCrumb != null) {
            out.add(new ItemStack(ModItems.singularCrumb));
        }
        return out;
    }

    /** A lithograph chest's contents. */
    public static List<ItemStack> lithoChest(Random rand) {
        List<ItemStack> out = rolls(LITHO, lithoItems(), ROLLS_MIN + rand.nextInt(ROLLS_MAX - ROLLS_MIN + 1), rand);
        if (rand.nextFloat() < LITHO_CHIP_CHANCE && ModItems.armorChip != null) {
            ChipType[] types = ChipType.values();
            out.add(ModItems.armorChip.stackOf(types[rand.nextInt(types.length)], 1));
        }
        if (rand.nextFloat() < LITHO_MODULE_CHANCE && ModItems.empModule != null) {
            EmpModule[] mods = EmpModule.values();
            out.add(ModItems.empModule.stackOf(mods[rand.nextInt(mods.length)]));
        }
        return out;
    }

    /** ПТ-1: a mimic machine's faction drop into `out`. */
    public static void mimicLoot(List<ItemStack> out, Random rand) {
        out.addAll(rolls(LITHO, lithoItems(), MIMIC_ROLLS_MIN + rand.nextInt(MIMIC_ROLLS_MAX - MIMIC_ROLLS_MIN + 1), rand));
        if (rand.nextFloat() < MIMIC_COMPONENT_CHANCE) {
            SiliconMaterial[] c = {SiliconMaterial.CONTROLLER, SiliconMaterial.TRANSISTOR, SiliconMaterial.MEMORY_CHIP};
            ItemStack s = silicon(c[rand.nextInt(c.length)]);
            if (s != null) {
                out.add(s);
            }
        }
    }

    /** Fills a Dead Node chest (empty slots at random). */
    public static void fillNode(IInventory inv, Random rand) {
        fill(inv, nodeChest(rand), rand);
    }

    /** Fills a lithograph chest. */
    public static void fillLitho(IInventory inv, Random rand) {
        fill(inv, lithoChest(rand), rand);
    }

    // ------------------------------------------------------------------ helpers

    private static List<ItemStack> rolls(int[][] table, ItemStack[] items, int n, Random rand) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        int sum = 0;
        for (int[] e : table) {
            sum += e[0];
        }
        for (int k = 0; k < n && sum > 0; k++) {
            int roll = rand.nextInt(sum), i = 0;
            while (roll >= table[i][0]) {
                roll -= table[i][0];
                i++;
            }
            int count = table[i][1] + rand.nextInt(table[i][2] - table[i][1] + 1);
            if (items[i] != null) {
                ItemStack s = items[i].copy();
                s.stackSize = count;
                out.add(s);
            }
        }
        return out;
    }

    /** Each stack into a random empty slot (vanilla's chests do so); what doesn't fit is lost. */
    private static void fill(IInventory inv, List<ItemStack> stacks, Random rand) {
        int size = inv.getSizeInventory();
        for (ItemStack s : stacks) {
            for (int t = 0; t < 2 * size; t++) {
                int slot = rand.nextInt(size);
                if (inv.getStackInSlot(slot) == null) {
                    inv.setInventorySlotContents(slot, s);
                    break;
                }
            }
        }
    }

    private static ItemStack silicon(SiliconMaterial m) {
        return ModItems.siliconMaterial == null ? null : ModItems.siliconMaterial.stackOf(m);
    }
}
