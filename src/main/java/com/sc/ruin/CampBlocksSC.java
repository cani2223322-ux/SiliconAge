package com.sc.ruin;

import com.sc.block.BlockCampSC;
import com.sc.block.BlockStolenFieldSC;
import com.sc.block.ItemBlockCampSC;
import com.sc.tileentity.TileEntityStolenFieldSC;
import com.sc.tileentity.TileEntityStolenSolarSC;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §2.2, §8.3): the camps' blocks, registered from ModBlocks.init (preInit, after
 * SubstrateWildBlocksSC). Registry and tile ids are what worlds keep - fixed here: "stolenField" (BlockStolenFieldSC, tile
 * "SiliconAge.stolenField" - TileEntityStolenFieldSC), "campSC" (BlockCampSC: the stolen solar panel - tile
 * "SiliconAge.stolenSolar", TileEntityStolenSolarSC -, the stolen charge pad, the camp's safe shut and open). The vault plating,
 * the vault lamp and the camp's tarp are metas 4..6 of the ruins' block (ruinSC, BlockRuinSC - package C appends them). Metas and
 * names fixed (append only); the blocks are package C's. Registered always, the dimension off too. Shared core: only the
 * coordinator edits this file.
 */
public final class CampBlocksSC {

    /** campSC: the stolen solar panel, the stolen charge pad (the Executioner +1 unit a second beside it), the safe shut / open. */
    public static final int STOLEN_SOLAR = 0, STOLEN_PAD = 1, CAMP_SAFE = 2, CAMP_SAFE_OPEN = 3;
    public static final String[] CAMP_NAMES = {"stolenSolar", "stolenPad", "campSafe", "campSafeOpen"};
    /** stolenField's one meta's name (its item and tooltip). */
    public static final String[] FIELD_NAMES = {"stolenField"};

    public static Block stolenField, camp;

    private CampBlocksSC() {
    }

    public static void init() {
        stolenField = new BlockStolenFieldSC();
        GameRegistry.registerBlock(stolenField, ItemBlockCampSC.class, "stolenField");
        GameRegistry.registerTileEntity(TileEntityStolenFieldSC.class, "SiliconAge.stolenField");
        camp = new BlockCampSC();
        GameRegistry.registerBlock(camp, ItemBlockCampSC.class, "campSC");
        GameRegistry.registerTileEntity(TileEntityStolenSolarSC.class, "SiliconAge.stolenSolar");
    }

    /** The names of a block's metas (an unknown block: none). */
    public static String[] namesOf(Block b) {
        return b == stolenField ? FIELD_NAMES : b == camp ? CAMP_NAMES : new String[0];
    }

    /** `b` / `meta` is the stolen charge pad (the Executioner's recharge - package M asks). */
    public static boolean isStolenPad(Block b, int meta) {
        return b != null && b == camp && meta == STOLEN_PAD;
    }

    /** `n` of `b` of meta `meta` (null before init). */
    public static ItemStack stack(Block b, int meta, int n) {
        return b == null ? null : new ItemStack(b, n, meta);
    }
}
