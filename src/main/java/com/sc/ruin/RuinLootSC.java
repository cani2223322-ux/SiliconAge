package com.sc.ruin;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.emp.EmpModule;
import com.sc.finale.FinaleItemsSC;
import com.sc.init.ModItems;
import com.sc.lore.JournalSC;
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
 * Chips, Nano Scrap, Shell Firmware), and a Sealed Crumb on NODE_CRUMB_CHANCE of the chests (Ф4, ПТ-17: a chest knows no
 * player - the Crumb opens into a Singularity Crumb in the hand of one who has some left of today's three, ItemRelicSC);</li>
 * <li>a lithograph's chest (LITHO_CHESTS_MIN..MAX a hall): the rolls of LITHO (GaAs wafers, Transistors, Silicon wafers), a
 * random armour chip (tier I) on LITHO_CHIP_CHANCE of the chests, a random ПЭ-1 module on LITHO_MODULE_CHANCE - never the ПЭ-1
 * itself;</li>
 * <li>a mimic machine (its faction drop, ПТ-1): MIMIC_ROLLS_MIN..MIMIC_ROLLS_MAX rolls of LITHO, and with
 * MIMIC_COMPONENT_CHANCE one more component (a Controller, a Transistor or a Memory Chip).</li>
 * </ul>
 * A roll picks an entry by its weight and gives MIN..MAX of it. The tables are public for the handbook (BookWorldSC) and the
 * self-test: {weight, min, max} by entry, the entries' stacks by the same index (NODE_ITEMS, LITHO_ITEMS).
 * <p>
 * Ф4 (package C, docs/plan-substrate-tech-f4.md §8.4, §8.6, §9.4, rules ПЗ-7, ПХ-2, ПХ-5, ПТ-17; TODO(balance) all of it):
 * <ul>
 * <li>the Renegades' safe (BlockCampSC - the camp's key, its field down): CAMP_ROLLS_MIN..MAX rolls of CAMP - what the
 * Tuners pay chips for (Nano and Quantum Scrap, Shell Firmware, Transistors, a settlement's Stolen Cargo) -, a ПЭ-1 module on
 * CAMP_MODULE_CHANCE, an armour chip on CAMP_CHIP_CHANCE, a Sealed Crumb on CAMP_CRUMB_CHANCE;</li>
 * <li>a Vault's chest (VAULT_CHESTS_MIN..MAX a bunker): ROLLS_MIN..ROLLS_MAX rolls of VAULT (Controllers, Memory Chips, GaAs
 * wafers, Transistors), a ПЭ-1 module on VAULT_MODULE_CHANCE, a Sealed Crumb on VAULT_CRUMB_CHANCE.</li>
 * </ul>
 * Every Crumb of the world but the boss's comes Sealed (ПХ-5): the daily three are counted when one is opened.
 * <p>
 * Ф5 (package J, docs/plan-substrate-tech-f5.md §6.1, rule ПЛ-2): a chest of the ruins may hold a Lithographer's Journal - one
 * JournalSC.roll of its source from the chest's own Random, after its rolls: a Dead Node's chest (fillNode - SRC_NODE: №1, №2), a
 * lithograph's (lithoChest - SRC_LITHO: №3, №4), a Vault's (vaultChest - SRC_VAULT: №5..№8). nodeChest itself stays without one: a
 * raid's reward rolls it too (RaidRunSC), and №1, №2 are the Nodes' own.
 */
public final class RuinLootSC {

    public static final int ROLLS_MIN = 3, ROLLS_MAX = 6;
    /** Chests: a Dead Node's (ComponentNodePlazaSC), a lithograph's hall's (ComponentLitHallSC). */
    public static final int NODE_CHESTS_MIN = 1, NODE_CHESTS_MAX = 3, LITHO_CHESTS_MIN = 2, LITHO_CHESTS_MAX = 4;
    /** Per chest. */
    public static final float NODE_CRUMB_CHANCE = 0.03F, LITHO_CHIP_CHANCE = 0.05F, LITHO_MODULE_CHANCE = 0.02F;
    /** Ф4: the camp's safe - rolls, its extras' chances. */
    public static final int CAMP_ROLLS_MIN = 4, CAMP_ROLLS_MAX = 7;
    public static final float CAMP_MODULE_CHANCE = 0.05F, CAMP_CHIP_CHANCE = 0.03F, CAMP_CRUMB_CHANCE = 0.05F;
    /** Ф4: a Vault's chests (ComponentVaultBunkerSC) and their extras' chances. */
    public static final int VAULT_CHESTS_MIN = 1, VAULT_CHESTS_MAX = 2;
    public static final float VAULT_MODULE_CHANCE = 0.10F, VAULT_CRUMB_CHANCE = 0.10F;
    /** A mimic's drop: rolls of the lithograph's table, the extra component's chance. */
    public static final int MIMIC_ROLLS_MIN = 1, MIMIC_ROLLS_MAX = 2;
    public static final float MIMIC_COMPONENT_CHANCE = 0.25F;

    /** The Dead Node's table: Controllers 1..2, Transistors 2..6, Memory Chips 1..3, Nano Scrap 1..4, Shell Firmware 1..3. */
    public static final int[][] NODE = {{2, 1, 2}, {5, 2, 6}, {3, 1, 3}, {4, 1, 4}, {4, 1, 3}};
    /** The lithograph's table: GaAs wafers 1..3, Transistors 2..8, Silicon wafers 2..6. */
    public static final int[][] LITHO = {{3, 1, 3}, {5, 2, 8}, {4, 2, 6}};
    /** Ф4, the camp's safe: Nano Scrap 2..5, Quantum Scrap 1..3, Shell Firmware 2..4, Transistors 2..6, a Stolen Cargo. */
    public static final int[][] CAMP = {{4, 2, 5}, {4, 1, 3}, {3, 2, 4}, {3, 2, 6}, {1, 1, 1}};
    /** Ф4, a Vault's chest: Controllers 1..3, Memory Chips 2..4, GaAs wafers 1..3, Transistors 2..6. */
    public static final int[][] VAULT = {{3, 1, 3}, {4, 2, 4}, {4, 1, 3}, {2, 2, 6}};

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

    /** CAMP's entries. */
    public static ItemStack[] campItems() {
        return new ItemStack[]{MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, 1), MobItemsSC.loot(ItemCombatLootSC.LootKind.QUANTUM_SCRAP, 1),
                MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, 1), silicon(SiliconMaterial.TRANSISTOR),
                MobItemsSC.loot(ItemCombatLootSC.LootKind.STOLEN_CARGO, 1)};
    }

    /** VAULT's entries. */
    public static ItemStack[] vaultItems() {
        return new ItemStack[]{silicon(SiliconMaterial.CONTROLLER), silicon(SiliconMaterial.MEMORY_CHIP), silicon(SiliconMaterial.GAAS_WAFER),
                silicon(SiliconMaterial.TRANSISTOR)};
    }

    /** A Dead Node chest's contents (server; `rand` - the chest's own). Ф4: its Crumb comes Sealed (ПТ-17). */
    public static List<ItemStack> nodeChest(Random rand) {
        List<ItemStack> out = rolls(NODE, nodeItems(), ROLLS_MIN + rand.nextInt(ROLLS_MAX - ROLLS_MIN + 1), rand);
        if (rand.nextFloat() < NODE_CRUMB_CHANCE) {
            add(out, RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1));
        }
        return out;
    }

    /** Ф4 (ПЗ-7): what the Renegades' safe spills when its camp's key opens it. */
    public static List<ItemStack> campSafe(Random rand) {
        List<ItemStack> out = rolls(CAMP, campItems(), CAMP_ROLLS_MIN + rand.nextInt(CAMP_ROLLS_MAX - CAMP_ROLLS_MIN + 1), rand);
        if (rand.nextFloat() < CAMP_MODULE_CHANCE) {
            add(out, randomModule(rand));
        }
        if (rand.nextFloat() < CAMP_CHIP_CHANCE && ModItems.armorChip != null) {
            ChipType[] types = ChipType.values();
            out.add(ModItems.armorChip.stackOf(types[rand.nextInt(types.length)], 1));
        }
        if (rand.nextFloat() < CAMP_CRUMB_CHANCE) {
            add(out, RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1));
        }
        return out;
    }

    /** Ф4 (ПХ-2, ПХ-5): a Vault chest's contents. */
    public static List<ItemStack> vaultChest(Random rand) {
        List<ItemStack> out = rolls(VAULT, vaultItems(), ROLLS_MIN + rand.nextInt(ROLLS_MAX - ROLLS_MIN + 1), rand);
        if (rand.nextFloat() < VAULT_MODULE_CHANCE) {
            add(out, randomModule(rand));
        }
        if (rand.nextFloat() < VAULT_CRUMB_CHANCE) {
            add(out, RelicItemsSC.relic(ItemRelicSC.SEALED_CRUMB, 1));
        }
        journal(out, JournalSC.SRC_VAULT, rand);              // Ф5 (ПЛ-2): №5..№8
        return out;
    }

    /** Fills a Vault chest. */
    public static void fillVault(IInventory inv, Random rand) {
        fill(inv, vaultChest(rand), rand);
    }

    /** A lithograph chest's contents. */
    public static List<ItemStack> lithoChest(Random rand) {
        List<ItemStack> out = rolls(LITHO, lithoItems(), ROLLS_MIN + rand.nextInt(ROLLS_MAX - ROLLS_MIN + 1), rand);
        if (rand.nextFloat() < LITHO_CHIP_CHANCE && ModItems.armorChip != null) {
            ChipType[] types = ChipType.values();
            out.add(ModItems.armorChip.stackOf(types[rand.nextInt(types.length)], 1));
        }
        if (rand.nextFloat() < LITHO_MODULE_CHANCE) {
            add(out, randomModule(rand));
        }
        journal(out, JournalSC.SRC_LITHO, rand);              // Ф5 (ПЛ-2): №3, №4
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

    /** Fills a Dead Node chest (empty slots at random). Ф5: and, on its chance, a Journal №1 / №2 (not nodeChest's - raids roll that). */
    public static void fillNode(IInventory inv, Random rand) {
        List<ItemStack> out = nodeChest(rand);
        journal(out, JournalSC.SRC_NODE, rand);               // Ф5 (ПЛ-2)
        fill(inv, out, rand);
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

    /** A random ПЭ-1 module (never the ПЭ-1 itself), null before the items exist. */
    private static ItemStack randomModule(Random rand) {
        if (ModItems.empModule == null) {
            return null;
        }
        EmpModule[] mods = EmpModule.values();
        return ModItems.empModule.stackOf(mods[rand.nextInt(mods.length)]);
    }

    private static void add(List<ItemStack> out, ItemStack s) {
        if (s != null) {
            out.add(s);
        }
    }

    /** Ф5 (package J, ПЛ-2): one roll of `source`'s journal (JournalSC.roll) into `out` - nothing on a miss or before the items exist. */
    private static void journal(List<ItemStack> out, int source, Random rand) {
        int n = JournalSC.roll(source, rand);
        if (n > 0) {
            add(out, FinaleItemsSC.journal(n));
        }
    }
}
