package com.sc.embassy;

import com.sc.block.BlockAirfieldSC;
import com.sc.block.BlockEmbassyRingSC;
import com.sc.block.ItemBlockAirfieldSC;
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
 * <p>
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §2.4): "embassyAirfieldSC" (BlockAirfieldSC: the runway's paving, the
 * shoulder, the apron, the markings and the lights, a meta each; no tile) - registered always, the add-on absent too (a world made
 * with it keeps loading without it).
 */
public final class EmbassyBlocksSC {

    public static Block ring;
    /** The airfield's surface, markings and lights (BlockAirfieldSC). */
    public static Block airfield;

    private EmbassyBlocksSC() {
    }

    public static void init() {
        ring = new BlockEmbassyRingSC();
        GameRegistry.registerBlock(ring, ItemBlockEmbassyRingSC.class, "embassyRingSC");
        GameRegistry.registerTileEntity(TileEntityEmbassyRingSC.class, "SiliconAge.embassyRing");
        airfield = new BlockAirfieldSC();
        GameRegistry.registerBlock(airfield, ItemBlockAirfieldSC.class, "embassyAirfieldSC");
    }
}
