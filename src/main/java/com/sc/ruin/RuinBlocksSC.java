package com.sc.ruin;

import com.sc.block.BlockRuinSC;
import com.sc.block.ItemBlockRuinSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §2.2): the ruins' blocks, registered from ModBlocks.init (preInit, after
 * SettlementBlocksSC). The registry name is what worlds keep - fixed here: "ruinSC" (BlockRuinSC, metas there). Registered
 * always, the dimension off too. Loot chests are vanilla's (Blocks.chest, filled by the pieces). Shared core: only the
 * coordinator edits this file.
 */
public final class RuinBlocksSC {

    public static Block ruin;

    private RuinBlocksSC() {
    }

    public static void init() {
        ruin = new BlockRuinSC();
        GameRegistry.registerBlock(ruin, ItemBlockRuinSC.class, "ruinSC");
    }

    /** `n` ruin blocks of meta `meta` (null before init). */
    public static ItemStack stack(int meta, int n) {
        return ruin == null ? null : new ItemStack(ruin, n, meta);
    }
}
