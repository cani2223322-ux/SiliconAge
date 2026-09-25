package com.sc.conduit;

import com.sc.item.ItemItemFilterSC;

import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraftforge.oredict.OreDictionary;

/**
 * Item filter settings, kept in the filter item's NBT (Ender IO style): a list of example items
 * and switches - blacklist (instead of whitelist), ignore metadata, match NBT and match by ore
 * dictionary (the last two on the advanced filter only). An empty whitelist lets nothing through,
 * an empty blacklist everything; a connector with no filter lets everything through.
 */
public final class ItemFilterSC {

    public static final int BASIC_SLOTS = 5, ADVANCED_SLOTS = 10;
    public static final String BLACKLIST = "Blacklist", IGNORE_META = "IgnoreMeta", MATCH_NBT = "MatchNbt", ORE_DICT = "OreDict";

    private ItemFilterSC() {
    }

    public static boolean isFilter(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemItemFilterSC;
    }

    public static boolean isAdvanced(ItemStack filter) {
        return filter.getItemDamage() == 1;
    }

    public static int slots(ItemStack filter) {
        return isAdvanced(filter) ? ADVANCED_SLOTS : BASIC_SLOTS;
    }

    private static NBTTagCompound tag(ItemStack filter) {
        if (!filter.hasTagCompound()) {
            filter.setTagCompound(new NBTTagCompound());
        }
        return filter.getTagCompound();
    }

    public static boolean flag(ItemStack filter, String key) {
        return filter.hasTagCompound() && filter.getTagCompound().getBoolean(key);
    }

    public static void setFlag(ItemStack filter, String key, boolean value) {
        tag(filter).setBoolean(key, value);
    }

    public static ItemStack[] entries(ItemStack filter) {
        ItemStack[] out = new ItemStack[slots(filter)];
        if (!filter.hasTagCompound()) {
            return out;
        }
        NBTTagList list = filter.getTagCompound().getTagList("Items", 10);
        for (int i = 0; i < list.tagCount(); i++) {
            NBTTagCompound e = list.getCompoundTagAt(i);
            int slot = e.getInteger("Slot");
            if (slot >= 0 && slot < out.length) {
                out[slot] = ItemStack.loadItemStackFromNBT(e);
            }
        }
        return out;
    }

    public static void setEntry(ItemStack filter, int slot, ItemStack example) {
        ItemStack[] all = entries(filter);
        if (slot < 0 || slot >= all.length) {
            return;
        }
        if (example != null) {
            example = example.copy();
            example.stackSize = 1;
            if (!isAdvanced(filter)) {
                example.setTagCompound(null);      // only the advanced filter can match NBT
            }
        }
        all[slot] = example;
        NBTTagList list = new NBTTagList();
        for (int i = 0; i < all.length; i++) {
            if (all[i] != null) {
                NBTTagCompound e = new NBTTagCompound();
                e.setInteger("Slot", i);
                all[i].writeToNBT(e);
                list.appendTag(e);
            }
        }
        tag(filter).setTag("Items", list);
    }

    /** Whether `candidate` gets past this filter (no filter at all: yes). */
    public static boolean passes(ItemStack filter, ItemStack candidate) {
        if (!isFilter(filter) || candidate == null) {
            return true;
        }
        boolean black = flag(filter, BLACKLIST);
        for (ItemStack e : entries(filter)) {
            if (e != null && matches(filter, e, candidate)) {
                return !black;
            }
        }
        return black;                 // on no list: a blacklist lets it through, a whitelist doesn't
    }

    private static boolean matches(ItemStack filter, ItemStack example, ItemStack c) {
        boolean advanced = isAdvanced(filter);
        if (advanced && flag(filter, ORE_DICT) && shareOre(example, c)) {
            return true;
        }
        if (example.getItem() != c.getItem()) {
            return false;
        }
        if (!flag(filter, IGNORE_META) && example.getItemDamage() != c.getItemDamage()) {
            return false;
        }
        return !(advanced && flag(filter, MATCH_NBT)) || ItemStack.areItemStackTagsEqual(example, c);
    }

    private static boolean shareOre(ItemStack a, ItemStack b) {
        int[] ia = OreDictionary.getOreIDs(a), ib = OreDictionary.getOreIDs(b);
        for (int x : ia) {
            for (int y : ib) {
                if (x == y) {
                    return true;
                }
            }
        }
        return false;
    }
}
