package com.sc.settle;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockSettlementCoreSC;
import com.sc.block.BlockSettlementDecorSC;
import com.sc.block.BlockShowcaseSC;
import com.sc.block.ItemBlockDockSC;
import com.sc.block.ItemBlockSettlementCoreSC;
import com.sc.block.ItemBlockSettlementDecorSC;
import com.sc.block.ItemBlockShowcaseSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.tileentity.TileEntityShowcaseSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §2.2): the settlements' blocks, registered from ModBlocks.init (preInit,
 * after SubstrateBlocksSC). Registry and tile ids are what worlds keep - fixed here: "settlementCore" (package C),
 * "showcaseSC" (package S), "dockSC" (package B), the trim "settlementDecor" and "settlementGlass" (the settlements' metal,
 * coils and lead glass: BlockSettlementDecorSC); tiles "SiliconAge.settlementCore", "SiliconAge.showcase". The
 * Settlement Brick is the Substrate rock's meta 8 (SubstrateBlocksSC.SETTLEMENT_BRICK, package S). Registered always,
 * the dimension off too (a world that has them keeps loading). None is in a creative tab; NEI hides them (package D).
 * Shared core: only the coordinator edits this file.
 */
public final class SettlementBlocksSC {

    public static Block core, showcase, dock, decor, glass;

    private SettlementBlocksSC() {
    }

    public static void init() {
        core = new BlockSettlementCoreSC();
        GameRegistry.registerBlock(core, ItemBlockSettlementCoreSC.class, "settlementCore");
        GameRegistry.registerTileEntity(TileEntitySettlementCoreSC.class, "SiliconAge.settlementCore");
        showcase = new BlockShowcaseSC();
        GameRegistry.registerBlock(showcase, ItemBlockShowcaseSC.class, "showcaseSC");
        GameRegistry.registerTileEntity(TileEntityShowcaseSC.class, "SiliconAge.showcase");
        dock = new BlockDockSC();
        GameRegistry.registerBlock(dock, ItemBlockDockSC.class, "dockSC");
        decor = new BlockSettlementDecorSC();
        GameRegistry.registerBlock(decor, ItemBlockSettlementDecorSC.class, "settlementDecor");
        glass = new BlockSettlementDecorSC.Glass();
        GameRegistry.registerBlock(glass, ItemBlockSettlementDecorSC.class, "settlementGlass");
    }

    /** A showcase machine of meta `meta` (BlockShowcaseSC). */
    public static ItemStack showcaseStack(int meta) {
        return new ItemStack(showcase, 1, meta);
    }

    /**
     * The trim that looks like a block of metal `m` (BlockSettlementDecorSC's meta; a metal the palette lacks: steel's),
     * null before init.
     */
    public static ItemStack trim(com.sc.util.Material m) {
        int meta = BlockSettlementDecorSC.metaOf(m);
        return decor == null ? null : new ItemStack(decor, 1, meta >= 0 ? meta : BlockSettlementDecorSC.STEEL);
    }

    /**
     * `b` is one of the settlements' unbreakable blocks (the core, a showcase, a dock block, the trim) - nobody breaks
     * them (package C), a creative player only one standing outside every settlement (SettlementProtectSC.takenAway).
     */
    public static boolean unbreakable(Block b) {
        return b != null && (b == core || b == showcase || b == dock || b == decor || b == glass);
    }
}
