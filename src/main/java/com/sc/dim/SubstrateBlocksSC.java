package com.sc.dim;

import com.sc.block.BlockCrystalDruseSC;
import com.sc.block.BlockSubstrateOreSC;
import com.sc.block.BlockSubstrateRockSC;
import com.sc.block.ItemBlockSubstrateOreSC;
import com.sc.block.ItemBlockSubstrateRockSC;
import com.sc.util.OreEntry;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;
import net.minecraftforge.oredict.OreDictionary;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.4): the Substrate's world blocks, registered from ModBlocks.init
 * (preInit, the coordinator's call). Registry names are what worlds keep - fixed here: "substrateRock",
 * "substrateOre", "crystalDruse".
 * <p>
 * «Порода Подложки» (rock) holds only opaque full cubes - in 1.7.10 isOpaqueCube / renderAsNormalBlock don't depend
 * on the meta; anything see-through or shaped is a block of its own (the druse). The metas are fixed (package M's
 * Lineman recharges at a COPPER_TRACE / TRACE_NODE; the self-test and the handbook name them).
 * <p>
 * The blocks are registered always - with the dimension off too (a world that has them keeps loading).
 */
public final class SubstrateBlocksSC {

    /** Metas of `rock`. */
    public static final int CRYSTALLITE = 0, OXIDE_VIOLET = 1, OXIDE_BLUE = 2, OXIDE_GOLD = 3, OXIDE_GREEN = 4, COPPER_TRACE = 5,
            TRACE_NODE = 6, QUARTZ_TURF = 7;
    /** Metas Ф1 registers (SETTLEMENT_BRICK = 8 comes with the settlements, Ф2). */
    public static final int ROCK_METAS = 8;
    /** The rock's metas by name (lang keys tile.siliconage.substrateRock.<name>.name, sc.tooltip.substrateRock.<name>). Append only. */
    public static final String[] ROCK_NAMES = {"crystallite", "oxideViolet", "oxideBlue", "oxideGold", "oxideGreen", "copperTrace",
            "traceNode", "quartzTurf"};

    /** The rock (metas above), the ores (meta = OreEntry ordinal, dropping ModBlocks.oreSC of the same meta), the druse. */
    public static Block rock, ore, druse;

    private SubstrateBlocksSC() {
    }

    /** ModBlocks.init: W registers the blocks and their harvest levels (the ore dictionary: registerOreDict, later). */
    public static void init() {
        rock = new BlockSubstrateRockSC();
        GameRegistry.registerBlock(rock, ItemBlockSubstrateRockSC.class, "substrateRock");
        ore = new BlockSubstrateOreSC();
        GameRegistry.registerBlock(ore, ItemBlockSubstrateOreSC.class, "substrateOre");
        druse = new BlockCrystalDruseSC();
        GameRegistry.registerBlock(druse, BlockCrystalDruseSC.ItemDruse.class, "crystalDruse");
    }

    /**
     * The Substrate's ores under the same ore<Name> as oreSC - so the drill's vein mining, the quarry's ore filter and
     * the helmet's ore scanner know them. From SubstrateRecipesSC.init, i.e. after ModBlocks.registerOreDict: oreSC stays
     * the first stack of every name (what the handbook and NEI show for "oreChalcopyrite").
     */
    static void registerOreDict() {
        for (OreEntry o : OreEntry.values()) {
            OreDictionary.registerOre("ore" + Character.toUpperCase(o.oreName.charAt(0)) + o.oreName.substring(1), new ItemStack(ore, 1, o.meta()));
        }
    }

    /** `b` / `meta` is a Copper Trace a Lineman can recharge at. */
    public static boolean isTrace(Block b, int meta) {
        return b != null && b == rock && (meta == COPPER_TRACE || meta == TRACE_NODE);
    }

    /** The rock's name for a meta (an unknown one: Crystallite's). */
    public static String rockName(int meta) {
        return ROCK_NAMES[meta >= 0 && meta < ROCK_NAMES.length ? meta : CRYSTALLITE];
    }

    /** `n` of the rock of `meta`. */
    public static ItemStack rockStack(int meta, int n) {
        return new ItemStack(rock, n, meta);
    }
}
