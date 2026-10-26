package com.sc.init;

import cpw.mods.fml.common.registry.GameRegistry;
import net.minecraft.item.ItemStack;

/**
 * The proposal of 2026-10-26 (docs/plan-ideas-2026-10-26.md): its new blocks and tiles, registered from ModBlocks.init (preInit, after
 * EmbassyBlocksSC). Registry and tile names are what worlds keep - fixed here (append only):
 * <ul>
 * <li>"relay" - block.BlockRelaySC (ПД-7): «Ретранслятор» (meta 0) and the post's «Мачта поста» (meta 1); tile "SiliconAge.relay";</li>
 * <li>"dataPort" - block.BlockDataPortSC (ОМ-5): «Порт данных» in a reactor's wall; tile "SiliconAge.dataPort";</li>
 * <li>"energyBank" - block.BlockEnergyBankSC (ОМ-7): the Energy Bank's casing, glass, controller, cells and ports (metas); tiles
 * "SiliconAge.energyBank" (the controller) and "SiliconAge.energyBankPort".</li>
 * </ul>
 * ОМ-4's EUV Lithograph is a machine (MachineType.EUV_LITHOGRAPH on ModBlocks.machineSC3). Each block gets its TooltipSC tooltip, textures,
 * NEI «how to get» page (or NEI hiding - the MAST) and handbook line from its own item's implementer. Shared core: only the coordinator
 * edits this file.
 */
public final class IdeaBlocksSC {

    public static com.sc.block.BlockRelaySC relay;
    public static com.sc.block.BlockDataPortSC dataPort;
    public static com.sc.block.BlockEnergyBankSC energyBank;

    private IdeaBlocksSC() {
    }

    public static void init() {
        relay = new com.sc.block.BlockRelaySC();
        GameRegistry.registerBlock(relay, com.sc.block.ItemBlockRelaySC.class, "relay");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityRelaySC.class, "SiliconAge.relay");
        dataPort = new com.sc.block.BlockDataPortSC();
        GameRegistry.registerBlock(dataPort, "dataPort");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityDataPortSC.class, "SiliconAge.dataPort");
        energyBank = new com.sc.block.BlockEnergyBankSC();
        GameRegistry.registerBlock(energyBank, com.sc.block.ItemBlockEnergyBankSC.class, "energyBank");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityEnergyBankSC.class, "SiliconAge.energyBank");
        GameRegistry.registerTileEntity(com.sc.tileentity.TileEntityEnergyBankPortSC.class, "SiliconAge.energyBankPort");
    }

    /** The Relay as an item (null before init). */
    public static ItemStack relay(int n) {
        return relay == null ? null : new ItemStack(relay, n, com.sc.block.BlockRelaySC.RELAY);
    }

    /** `n` Data Ports (null before init). */
    public static ItemStack dataPort(int n) {
        return dataPort == null ? null : new ItemStack(dataPort, n, 0);
    }

    /** `n` Energy Bank blocks of meta `meta` (BlockEnergyBankSC; null before init). */
    public static ItemStack energyBank(int meta, int n) {
        return energyBank == null ? null : new ItemStack(energyBank, n, meta);
    }
}
