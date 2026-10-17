package com.sc.settle.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * PACKAGE S (docs/plan-substrate-tech-f2.md §4.6): the settlements' blocks on the client. Nothing to register in Ф2:
 * a showcase is a plain cube wearing the real machine's icons (BlockShowcaseSC.getIcon, turned by its tile's facing,
 * which the description packet brings), its particles and sounds come from its tile's client tick
 * (TileEntityShowcaseSC); the Settlement Brick is a meta of the Substrate's rock; the core's dome needs nothing here
 * either - FieldRendererSC is found by TileEntitySettlementCoreSC's superclass. A TESR (a showcase with moving parts,
 * Ф3's workshops) would be bound here (ClientRegistry.bindTileEntitySpecialRenderer). Called from ClientProxy.preInit.
 */
@SideOnly(Side.CLIENT)
public final class SettlementBlocksClientSC {

    private SettlementBlocksClientSC() {
    }

    public static void register() {
        // nothing in Ф2 (the class comment)
    }
}
