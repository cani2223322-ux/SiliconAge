package com.sc.dim;

import com.sc.block.BlockSingularGlassSC;
import com.sc.block.BlockSubstrateFloraSC;
import com.sc.block.BlockSubstrateVentSC;
import com.sc.block.ItemBlockSubstrateWildSC;
import com.sc.tileentity.TileEntityArcCrackSC;
import com.sc.tileentity.TileEntitySubstrateVentSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §2.2, §3.3): the new biomes' blocks, registered from ModBlocks.init (preInit, after
 * RuinBlocksSC). Registry and tile ids are what worlds keep - fixed here: "singularGlass" (the Rift's floor and the puncture
 * pillar), "substrateFlora" (Wireweed, the diode moss), "substrateVent" (the helium fumarole, the Breakdown crack, the ion
 * geyser, the cracked RTG - unbreakable, no drop); tiles "SiliconAge.substrateVent" (fumarole, geyser, RTG) and
 * "SiliconAge.arcCrack" (the crack - an energy source of its own: 32 EU/t into a neighbouring cable). The monocrystal of the
 * geodes is the rock's meta 9 (SubstrateBlocksSC.MONOCRYSTAL). Metas and names fixed (append only); the blocks are package W's.
 * Registered always, the dimension off too. Shared core: only the coordinator edits this file.
 */
public final class SubstrateWildBlocksSC {

    /** singularGlass: the glass, the pillar's glowing core. */
    public static final int GLASS = 0, PILLAR_CORE = 1;
    public static final String[] GLASS_NAMES = {"singularGlass", "pillarCore"};
    /** substrateFlora: Wireweed (sparks in a storm), the diode moss (light 3). */
    public static final int WIREWEED = 0, DIODE_MOSS = 1;
    public static final String[] FLORA_NAMES = {"wireweed", "diodeMoss"};
    /** substrateVent: the helium fumarole, the Breakdown crack, the ion geyser, the cracked RTG. */
    public static final int FUMAROLE = 0, ARC_CRACK = 1, ION_GEYSER = 2, CRACKED_RTG = 3;
    public static final String[] VENT_NAMES = {"fumarole", "arcCrack", "ionGeyser", "crackedRtg"};

    public static Block glass, flora, vent;

    private SubstrateWildBlocksSC() {
    }

    public static void init() {
        glass = new BlockSingularGlassSC();
        GameRegistry.registerBlock(glass, ItemBlockSubstrateWildSC.class, "singularGlass");
        flora = new BlockSubstrateFloraSC();
        GameRegistry.registerBlock(flora, ItemBlockSubstrateWildSC.class, "substrateFlora");
        vent = new BlockSubstrateVentSC();
        GameRegistry.registerBlock(vent, ItemBlockSubstrateWildSC.class, "substrateVent");
        GameRegistry.registerTileEntity(TileEntitySubstrateVentSC.class, "SiliconAge.substrateVent");
        GameRegistry.registerTileEntity(TileEntityArcCrackSC.class, "SiliconAge.arcCrack");
    }

    /** The names of a block's metas (an unknown block: none). */
    public static String[] namesOf(Block b) {
        return b == glass ? GLASS_NAMES : b == flora ? FLORA_NAMES : b == vent ? VENT_NAMES : new String[0];
    }

    /** `n` of `b` of meta `meta` (null before init). */
    public static ItemStack stack(Block b, int meta, int n) {
        return b == null ? null : new ItemStack(b, n, meta);
    }
}
