package com.sc.machine;

import java.util.ArrayList;
import java.util.List;

import com.sc.util.OreEntry;

import net.minecraft.init.Blocks;
import net.minecraft.item.ItemStack;

/**
 * What the Exo Drilling Rig brings up from the deep: the vanilla ores and the mod's own, each with
 * a weight. A lens of a mod ore in the rig multiplies that ore's weight (x5 for one lens, x9 for
 * two...); the rig's filter can leave ores out altogether.
 */
public final class ExoOreTableSC {

    /** The lens tint of each mod ore (OreEntry order) - the ore texture's own colour. */
    public static final int[] LENS_COLORS = {0xB6B3AF, 0xD2AC37, 0x44352C, 0x93959A, 0x794F27, 0xB15931, 0x868689, 0x492B29,
            0x8C8E93, 0x342539, 0x5B4F2E, 0xA5A6A9, 0xA05A37, 0xBAB1B2, 0xBCBFBC, 0xBBA2D4};
    public static final int LENS_BOOST = 4;

    public static final class Entry {
        public final ItemStack ore;
        public final int weight;
        /** The OreEntry a lens can boost, or -1 for a vanilla ore. */
        public final int lens;

        Entry(ItemStack ore, int weight, int lens) {
            this.ore = ore;
            this.weight = weight;
            this.lens = lens;
        }
    }

    private static List<Entry> entries, foreign;

    /**
     * Other mods' ores (the ore dictionary's "ore..." names, one block each), weight 8 - what the
     * deep scan module adds. Empty without such mods. Worked out once, when first asked (in game,
     * after every mod has registered its ores).
     */
    public static List<Entry> foreign() {
        if (foreign == null) {
            List<Entry> l = new ArrayList<Entry>();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (Entry e : entries()) {
                seen.add(key(e.ore));
            }
            for (String name : net.minecraftforge.oredict.OreDictionary.getOreNames()) {
                if (!name.startsWith("ore")) {
                    continue;
                }
                for (ItemStack s : net.minecraftforge.oredict.OreDictionary.getOres(name)) {
                    if (s == null || s.getItem() == null) {
                        continue;
                    }
                    String id = String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(s.getItem()));
                    if (id.startsWith("minecraft:") || id.startsWith(com.sc.Reference.MODID + ":")) {
                        continue;
                    }
                    int meta = s.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE ? 0 : s.getItemDamage();
                    ItemStack one = new ItemStack(s.getItem(), 1, meta);
                    if (seen.add(key(one))) {
                        l.add(new Entry(one, 8, -1));
                    }
                    break;                              // one block per ore name
                }
            }
            foreign = l;
        }
        return foreign;
    }

    private static String key(ItemStack s) {
        return net.minecraft.item.Item.itemRegistry.getNameForObject(s.getItem()) + "@" + s.getItemDamage();
    }

    private ExoOreTableSC() {
    }

    public static List<Entry> entries() {
        if (entries == null) {
            List<Entry> l = new ArrayList<Entry>();
            l.add(new Entry(new ItemStack(Blocks.coal_ore), 40, -1));
            l.add(new Entry(new ItemStack(Blocks.iron_ore), 30, -1));
            l.add(new Entry(new ItemStack(Blocks.gold_ore), 10, -1));
            l.add(new Entry(new ItemStack(Blocks.redstone_ore), 16, -1));
            l.add(new Entry(new ItemStack(Blocks.lapis_ore), 8, -1));
            l.add(new Entry(new ItemStack(Blocks.quartz_ore), 12, -1));
            l.add(new Entry(new ItemStack(Blocks.diamond_ore), 4, -1));
            l.add(new Entry(new ItemStack(Blocks.emerald_ore), 2, -1));
            for (OreEntry o : OreEntry.values()) {
                // the mod's ores by how common they are in the world: vein size
                l.add(new Entry(new ItemStack(com.sc.init.ModBlocks.oreSC, 1, o.meta()), o.defaultVeinSize * 4, o.meta()));
            }
            entries = l;
        }
        return entries;
    }
}
