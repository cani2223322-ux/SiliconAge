package com.sc.settle;

import com.sc.block.BlockDockSC;
import com.sc.block.BlockSettlementDecorSC;
import com.sc.block.BlockShowcase2SC;
import com.sc.block.BlockShowcaseSC;

import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.event.FMLInterModComms;
import net.minecraft.block.Block;
import net.minecraft.item.ItemStack;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.7, rule ПК-11): other mods and the settlements. AE2: the core, the
 * showcase machines, the dock's blocks and the trim (the settlements' metal, coils, lead glass) go to AE2's spatial IO
 * blacklist by IMC ("blacklist-block-spatial" with the block's ItemStack, every meta; sent in init, always - the blocks
 * exist with the dimension off too). Only those - blocks no player has: the player's own Field Generator, machines and
 * metal are never blacklisted (and so neither is the settlements' brick or the ground under them: the Substrate rock's).
 * Spatial IO moves the creatures in its box too - a bound one out of the Substrate is removed (NpcLinkSC.exiled). A
 * missing AE2: IMC to an absent mod is dropped by FML. Called from SettlementSC.init. Ф3 (docs/plan-substrate-tech-f3.md §6.3,
 * ПК-16): the second showcase block (showcase2SC, the Power Engineer's and the Salvager's workshops) too.
 * <p>
 * The key checked against the pack's AE2 (appliedenergistics2-rv3-beta-6): appeng.core.IMCHandler registers
 * "blacklist-block-spatial" -> IMCBlackListSpatial, which takes the message's ItemStack, Block.getBlockFromItem of its
 * item and blacklists that Block in the movable registry (spatial IO leaves it where it stands). AE2 keys by the Block
 * - one message a block would do; one a meta is sent anyway, as the plan asks (a later AE2 keying by meta is covered).
 */
public final class SettlementCompatSC {

    /** AE2's mod id and the IMC key (checked in its jar, see the class comment). */
    public static final String AE2 = "appliedenergistics2", AE2_SPATIAL_BLACKLIST = "blacklist-block-spatial";

    private SettlementCompatSC() {
    }

    public static void sendImc() {
        int sent = blacklist(SettlementBlocksSC.core, 1) + blacklist(SettlementBlocksSC.showcase, BlockShowcaseSC.NAMES.length)
                + blacklist(SettlementBlocksSC.dock, BlockDockSC.NAMES.length)
                + blacklist(SettlementBlocksSC.decor, BlockSettlementDecorSC.NAMES.length) + blacklist(SettlementBlocksSC.glass, 1)
                + blacklist(SettlementBlocksSC.showcase2, BlockShowcase2SC.NAMES.length);   // Ф3 (ПК-16): the second showcase block
        if (Loader.isModLoaded(AE2)) {
            FMLLog.info("[Silicon Age] Si-14 settlements: %d stacks of the core, showcases, dock and trim sent to AE2's spatial IO blacklist (%s)",
                    sent, AE2_SPATIAL_BLACKLIST);
        }
    }

    /** One IMC a meta 0..metas-1 of `b`. @return how many went (none for a block not registered) */
    private static int blacklist(Block b, int metas) {
        if (b == null) {
            return 0;
        }
        int n = 0;
        for (int m = 0; m < Math.max(1, metas); m++) {
            if (FMLInterModComms.sendMessage(AE2, AE2_SPATIAL_BLACKLIST, new ItemStack(b, 1, m))) {
                n++;
            }
        }
        return n;
    }
}
