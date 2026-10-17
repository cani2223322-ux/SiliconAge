package com.sc.lore;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.finale.FinaleItemsSC;
import com.sc.init.ModItems;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.util.SiliconMaterial;

import net.minecraft.inventory.IInventory;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE J (docs/plan-substrate-tech-f5.md §6.2, rule ПЛ-4; TODO(balance) all of it): the one chest under a Lithographers' bridge
 * wreck's raised side (ComponentWreckSC) - only this mod's items:
 * <ul>
 * <li>a Lithographer's Journal - JournalSC.roll(SRC_WRECK): №9 or №10, in every wreck (WRECK_CHANCE 1);</li>
 * <li>Nano Scrap SCRAP_MIN..SCRAP_MAX, Shell Firmware FIRMWARE_MIN..FIRMWARE_MAX - what the burnt ring's machines left;</li>
 * <li>on COMPONENT_CHANCE one component: a Controller or a Transistor.</li>
 * </ul>
 * The chest's Random is its own (the wreck's seed): every window that holds it fills it the same. fill - also the Signal's cache
 * (SignalCacheSC): each stack into a random empty slot, as vanilla's chests.
 */
public final class WreckLootSC {

    /** The scrap, the firmware, the component's chance. TODO(balance) */
    public static final int SCRAP_MIN = 2, SCRAP_MAX = 4, FIRMWARE_MIN = 1, FIRMWARE_MAX = 3;
    public static final float COMPONENT_CHANCE = 0.30F;

    private WreckLootSC() {
    }

    /** A wreck chest's contents (server; `rand` - the chest's own). */
    public static List<ItemStack> chest(Random rand) {
        List<ItemStack> out = new ArrayList<ItemStack>();
        add(out, FinaleItemsSC.journal(JournalSC.roll(JournalSC.SRC_WRECK, rand)));
        add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, SCRAP_MIN + rand.nextInt(SCRAP_MAX - SCRAP_MIN + 1)));
        add(out, MobItemsSC.loot(ItemCombatLootSC.LootKind.SHELL_FIRMWARE, FIRMWARE_MIN + rand.nextInt(FIRMWARE_MAX - FIRMWARE_MIN + 1)));
        if (rand.nextFloat() < COMPONENT_CHANCE) {
            add(out, silicon(rand.nextBoolean() ? SiliconMaterial.CONTROLLER : SiliconMaterial.TRANSISTOR, 1));
        }
        return out;
    }

    /** Fills a wreck chest. */
    public static void fillWreck(IInventory inv, Random rand) {
        fill(inv, chest(rand), rand);
    }

    /** Each stack into a random empty slot (vanilla's chests do so); what doesn't fit is lost. */
    public static void fill(IInventory inv, List<ItemStack> stacks, Random rand) {
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

    /** `n` of silicon material `m` (null before the items exist). */
    static ItemStack silicon(SiliconMaterial m, int n) {
        if (ModItems.siliconMaterial == null || n <= 0) {
            return null;
        }
        ItemStack s = ModItems.siliconMaterial.stackOf(m);
        if (s != null) {
            s.stackSize = n;
        }
        return s;
    }

    static void add(List<ItemStack> out, ItemStack s) {
        if (s != null) {
            out.add(s);
        }
    }
}
