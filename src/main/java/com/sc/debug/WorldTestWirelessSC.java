package com.sc.debug;

import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.init.ModItems;
import com.sc.item.ItemEntangledCrystalSC;
import com.sc.tileentity.TileEntityWirelessSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

/**
 * A live check of wireless energy in a running world (-Dsc.worldtest=true, dedicated test server):
 * a linked HV transmitter / receiver in the Overworld, a quantum pair Overworld -> Nether; after
 * a few seconds the receiving ends must hold energy. The blocks are removed afterwards.
 */
public class WorldTestWirelessSC {

    private int ticks;
    private TileEntityWirelessSC tx, rx, qa, qb;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        World over = DimensionManager.getWorld(0), nether = DimensionManager.getWorld(-1);
        if (over == null || nether == null) {
            return;
        }
        if (ticks == 40) {
            tx = place(over, ModBlocks.wirelessTx, Tier.HV.ordinal(), 8, 200, 8);
            rx = place(over, ModBlocks.wirelessRx, Tier.HV.ordinal(), 48, 200, 8);
            qa = place(over, ModBlocks.quantumTranslator, 0, 8, 202, 8);
            qb = place(nether, ModBlocks.quantumTranslator, 0, 8, 100, 8);
            if (tx == null || rx == null || qa == null || qb == null) {
                System.out.println("[SC-WORLDTEST] FAIL could not place the blocks");
                return;
            }
            TileEntityWirelessSC.link(tx, rx);
            tx.setEnergyStoredClient(tx.getMaxEnergyStored());
            qa.setEnergyStoredClient(qa.getMaxEnergyStored());
            qa.setInventorySlotContents(TileEntityWirelessSC.SLOT_CRYSTAL, ItemEntangledCrystalSC.half(ModItems.entangledCrystal, 777L, 1));
            qb.setInventorySlotContents(TileEntityWirelessSC.SLOT_CRYSTAL, ItemEntangledCrystalSC.half(ModItems.entangledCrystal, 777L, 2));
            qb.toggleRole();                                   // the Nether end takes
        }
        if (ticks == 160 && tx != null) {
            boolean a = rx.getEnergyStored() > 0 && tx.getLossPct() == 5
                    && (tx.getStatus() == TileEntityWirelessSC.ST_OK || tx.getStatus() == TileEntityWirelessSC.ST_IDLE);
            boolean d = qb.getEnergyStored() > 0
                    && (qa.getStatus() == TileEntityWirelessSC.ST_OK || qa.getStatus() == TileEntityWirelessSC.ST_NO_ENERGY);
            System.out.println("[SC-WORLDTEST] " + (a ? "PASS" : "FAIL") + " wireless A: receiver " + rx.getEnergyStored()
                    + " EU, status tx " + tx.getStatus() + " rx " + rx.getStatus() + ", loss " + tx.getLossPct() + "%");
            System.out.println("[SC-WORLDTEST] " + (d ? "PASS" : "FAIL") + " quantum Overworld -> Nether: nether end " + qb.getEnergyStored()
                    + " EU, status " + qa.getStatus() + " / " + qb.getStatus());
            wrenchTest(over);
            DimensionManager.getWorld(0).setBlockToAir(8, 200, 8);
            DimensionManager.getWorld(0).setBlockToAir(48, 200, 8);
            DimensionManager.getWorld(0).setBlockToAir(8, 202, 8);
            DimensionManager.getWorld(-1).setBlockToAir(8, 100, 8);
            System.out.println("[SC-WORLDTEST] DONE");
        }
    }

    /** The quantum wrench in Dismantle mode: a wireless block goes whole; a cable + pipe bundle one part per click. */
    private static void wrenchTest(World w) {
        net.minecraftforge.common.util.FakePlayer p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft((net.minecraft.world.WorldServer) w);
        p.capabilities.isCreativeMode = true;
        net.minecraft.item.ItemStack wrench = new net.minecraft.item.ItemStack(ModItems.WRENCHES.get(ModItems.WRENCHES.size() - 1));
        wrench.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        wrench.getTagCompound().setInteger("WrenchMode", com.sc.item.ItemWrenchSC.MODE_DISMANTLE);
        wrench.getItem().onItemUseFirst(wrench, p, w, 48, 200, 8, 1, 0.5F, 0.5F, 0.5F);
        boolean wl = w.isAirBlock(48, 200, 8);
        w.setBlock(12, 200, 8, ModBlocks.conduitBundle, 0, 3);
        com.sc.tileentity.TileEntityConduitBundleSC c = (com.sc.tileentity.TileEntityConduitBundleSC) w.getTileEntity(12, 200, 8);
        c.addPart(com.sc.conduit.ConduitKind.CABLE, 0);
        c.addPart(com.sc.conduit.ConduitKind.PIPE, 0);
        wrench.getItem().onItemUseFirst(wrench, p, w, 12, 200, 8, 1, 0.5F, 0.5F, 0.5F);
        boolean one = w.getTileEntity(12, 200, 8) instanceof com.sc.tileentity.TileEntityConduitBundleSC
                && ((com.sc.tileentity.TileEntityConduitBundleSC) w.getTileEntity(12, 200, 8)).has(com.sc.conduit.ConduitKind.PIPE)
                && !((com.sc.tileentity.TileEntityConduitBundleSC) w.getTileEntity(12, 200, 8)).has(com.sc.conduit.ConduitKind.CABLE);
        wrench.getItem().onItemUseFirst(wrench, p, w, 12, 200, 8, 1, 0.5F, 0.5F, 0.5F);
        boolean gone = w.isAirBlock(12, 200, 8);
        System.out.println("[SC-WORLDTEST] " + (wl && one && gone ? "PASS" : "FAIL") + " wrench dismantle: wireless " + wl
                + ", bundle one part " + one + ", bundle gone " + gone);
        w.setBlockToAir(12, 200, 8);
    }

    private static TileEntityWirelessSC place(World w, net.minecraft.block.Block b, int meta, int x, int y, int z) {
        w.setBlock(x, y, z, b, meta, 3);
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityWirelessSC)) {
            return null;
        }
        ((TileEntityWirelessSC) te).setPowerOn(true);
        return (TileEntityWirelessSC) te;
    }
}
