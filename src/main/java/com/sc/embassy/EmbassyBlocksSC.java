package com.sc.embassy;

import com.sc.block.BlockEmbassyRingSC;
import com.sc.block.ItemBlockEmbassyRingSC;
import com.sc.tileentity.TileEntityEmbassyRingSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §2.2): the embassies' own block, registered from ModBlocks.init (preInit, after
 * ArenaBlocksSC). Registry and tile ids are what worlds keep - fixed here: "embassyRingSC" (BlockEmbassyRingSC, package R; one meta),
 * tile "SiliconAge.embassyRing" (TileEntityEmbassyRingSC). The rest of an embassy is the settlements' blocks (the core, the showcases
 * - BlockShowcase2SC's Ф6 metas GRAVITY_COIL, CRYO_TANK, DISPATCH_CONSOLE -, the dock's, the trim) and vanilla's. Registered always, the
 * embassies off too. Shared core: only the coordinator edits this file.
 */
public final class EmbassyBlocksSC {

    public static Block ring;

    private EmbassyBlocksSC() {
    }

    public static void init() {
        ring = new BlockEmbassyRingSC();
        GameRegistry.registerBlock(ring, ItemBlockEmbassyRingSC.class, "embassyRingSC");
        GameRegistry.registerTileEntity(TileEntityEmbassyRingSC.class, "SiliconAge.embassyRing");
    }
}
