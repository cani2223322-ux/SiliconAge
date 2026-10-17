package com.sc.arena;

import com.sc.block.BlockArenaSC;
import com.sc.block.ItemBlockArenaSC;
import com.sc.tileentity.TileEntityBreakdownConsoleSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §2.2, §4.2): the Breakdown Epicentre's blocks, registered from ModBlocks.init
 * (preInit, after CampBlocksSC). Registry and tile ids are what worlds keep - fixed here: "arenaSC" (BlockArenaSC, package A) with
 * the metas below, tile "SiliconAge.breakdownConsole" (TileEntityBreakdownConsoleSC - meta CONSOLE only). Every meta is unbreakable
 * (as the docks: hardness -1, blast-proof, no drop, no pick block), hidden in NEI and blacklisted for AE2's Spatial IO (package A,
 * SettlementCompatSC) - the arena stays whole for the next fight. The Breakdown cracks the Warden opens in the floor are the Ф4
 * vents' arcCrack (SubstrateWildBlocksSC, meta ARC_CRACK) - closed again when the fight ends. Registered always, the dimension off
 * too. Metas and names fixed (append only). Shared core: only the coordinator edits this file.
 */
public final class ArenaBlocksSC {

    /** arenaSC: the Breakdown Console (TE), a coil's pedestal, a column's block, the floor's plating, the wall's plating, a lamp (light). */
    public static final int CONSOLE = 0, PEDESTAL = 1, COLUMN = 2, FLOOR = 3, WALL = 4, LAMP = 5;
    public static final String[] NAMES = {"breakdownConsole", "coilPedestal", "arenaColumn", "arenaFloor", "arenaWall", "arenaLamp"};
    /** The lamp's and the console's light. TODO(balance) */
    public static final int LAMP_LIGHT = 12, CONSOLE_LIGHT = 7;

    public static Block arena;

    private ArenaBlocksSC() {
    }

    public static void init() {
        arena = new BlockArenaSC();
        GameRegistry.registerBlock(arena, ItemBlockArenaSC.class, "arenaSC");
        GameRegistry.registerTileEntity(TileEntityBreakdownConsoleSC.class, "SiliconAge.breakdownConsole");
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    /** `b` / `meta` is the arena's floor plating (a crack closes back to it). */
    public static boolean isFloor(Block b, int meta) {
        return b != null && b == arena && meta == FLOOR;
    }

    /** `n` arena blocks of meta `meta` (null before init). */
    public static ItemStack stack(int meta, int n) {
        return arena == null ? null : new ItemStack(arena, n, meta);
    }
}
