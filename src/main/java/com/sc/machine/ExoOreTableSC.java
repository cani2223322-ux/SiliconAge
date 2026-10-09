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
    /** The share (%) of the hauls all other mods' ores take together, however many there are. */
    public static final int FOREIGN_SHARE = 25;

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
     * Other mods' ores (the ore dictionary's "ore..." names, one block each) - what the deep scan
     * module adds. Together they weigh FOREIGN_SHARE % of the whole table, split evenly (at least 1
     * each), so a big modpack doesn't crowd out the rest. Empty without such mods. Worked out once,
     * when first asked (in game, after every mod has registered its ores).
     */
    public static List<Entry> foreign() {
        if (foreign == null) {
            List<ItemStack> l = new ArrayList<ItemStack>();
            java.util.Set<String> seen = new java.util.HashSet<String>();
            for (Entry e : entries()) {
                seen.add(key(e.ore));
            }
            for (String name : net.minecraftforge.oredict.OreDictionary.getOreNames()) {
                if (!isOreName(name)) {
                    continue;
                }
                for (ItemStack s : net.minecraftforge.oredict.OreDictionary.getOres(name)) {
                    if (s == null || !isBlock(s.getItem())) {
                        continue;                       // only blocks: no "ore..." items
                    }
                    String id = String.valueOf(net.minecraft.item.Item.itemRegistry.getNameForObject(s.getItem()));
                    if (id.startsWith("minecraft:") || id.startsWith(com.sc.Reference.MODID + ":")) {
                        continue;
                    }
                    int meta = s.getItemDamage() == net.minecraftforge.oredict.OreDictionary.WILDCARD_VALUE ? 0 : s.getItemDamage();
                    ItemStack one = new ItemStack(s.getItem(), 1, meta);
                    if (seen.add(key(one))) {
                        l.add(one);
                    }
                    break;                              // one block per ore name
                }
            }
            int[] w = foreignWeights(baseWeight(), l.size());
            List<Entry> out = new ArrayList<Entry>();
            for (int i = 0; i < l.size(); i++) {
                out.add(new Entry(l.get(i), w[i], -1));
            }
            foreign = out;
        }
        return foreign;
    }

    /** The table's own weight: the vanilla and mod ores together. */
    public static int baseWeight() {
        int t = 0;
        for (Entry e : entries()) {
            t += e.weight;
        }
        return t;
    }

    /**
     * Weights for n foreign ores so they sum to FOREIGN_SHARE % of (base + theirs): the total split
     * evenly, the remainder going one each to the first ones; at least 1 each.
     */
    public static int[] foreignWeights(int base, int n) {
        int[] w = new int[Math.max(0, n)];
        if (n <= 0) {
            return w;
        }
        int total = Math.round(base * (float) FOREIGN_SHARE / (100 - FOREIGN_SHARE));
        for (int i = 0; i < n; i++) {
            w[i] = Math.max(1, total / n + (i < total % n ? 1 : 0));
        }
        return w;
    }

    /** An ore dictionary ore name: "ore" + a capital letter (oreCopper yes; oreberry, ore, oreganoSeed no). */
    public static boolean isOreName(String name) {
        return name != null && name.length() > 3 && name.startsWith("ore") && name.charAt(3) >= 'A' && name.charAt(3) <= 'Z';
    }

    /** The item places a block (an ItemBlock whose block isn't air). */
    static boolean isBlock(net.minecraft.item.Item item) {
        if (!(item instanceof net.minecraft.item.ItemBlock)) {
            return false;
        }
        net.minecraft.block.Block b = net.minecraft.block.Block.getBlockFromItem(item);
        return b != null && b != Blocks.air;
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
