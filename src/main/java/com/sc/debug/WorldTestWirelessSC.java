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
            placeRtg(over);
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
            radiationTest(over);
            DimensionManager.getWorld(0).setBlockToAir(8, 200, 8);
            DimensionManager.getWorld(0).setBlockToAir(48, 200, 8);
            DimensionManager.getWorld(0).setBlockToAir(8, 202, 8);
            DimensionManager.getWorld(-1).setBlockToAir(8, 100, 8);
            System.out.println("[SC-WORLDTEST] DONE");
        }
    }

    /** An RTG with two capsules at (30, 200, 30), open to the air. */
    private static void placeRtg(World w) {
        net.minecraft.item.ItemStack st = ModBlocks.generatorStack(com.sc.energy.GeneratorType.RTG, 1);
        w.setBlock(30, 200, 30, net.minecraft.block.Block.getBlockFromItem(st.getItem()), st.getItemDamage(), 3);
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(30, 200, 30);
        if (te instanceof com.sc.tileentity.TileEntityGeneratorSC) {
            com.sc.tileentity.TileEntityGeneratorSC g = (com.sc.tileentity.TileEntityGeneratorSC) te;
            g.setGeneratorType(com.sc.energy.GeneratorType.RTG);
            g.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.SLOT_FUEL, new net.minecraft.item.ItemStack(ModItems.isotopeCapsule));
            g.setInventorySlotContents(com.sc.tileentity.TileEntityGeneratorSC.SLOT_BLANKET, new net.minecraft.item.ItemStack(ModItems.isotopeCapsule));
        }
    }

    /**
     * A player 2 blocks from the RTG takes radiation and a dose; a lead wall between cuts it to
     * almost nothing; the full lead suit stops it and the dose falls.
     */
    private static void radiationTest(World w) {
        net.minecraftforge.common.util.FakePlayer p = net.minecraftforge.common.util.FakePlayerFactory.getMinecraft((net.minecraft.world.WorldServer) w);
        p.capabilities.isCreativeMode = false;
        p.setPosition(30.5, 200, 32.5);
        com.sc.radiation.RadiationSC.setDose(p, 0F);
        float open = com.sc.radiation.RadiationSC.levelAt(p);
        com.sc.radiation.RadiationSC.perSecond(p);
        float dose = com.sc.radiation.RadiationSC.doseOf(p);
        w.setBlock(30, 200, 31, ModBlocks.leadBlock);
        w.setBlock(30, 201, 31, ModBlocks.leadBlock);
        float walled = com.sc.radiation.RadiationSC.levelAt(p);
        w.setBlockToAir(30, 200, 31);
        w.setBlockToAir(30, 201, 31);
        for (int i = 0; i < 4; i++) {
            p.inventory.armorInventory[i] = new net.minecraft.item.ItemStack(ModItems.leadSuit[3 - i]);
        }
        com.sc.radiation.RadiationSC.perSecond(p);
        float after = com.sc.radiation.RadiationSC.doseOf(p);
        int prot = com.sc.radiation.RadiationSC.lastProtection(p);
        int parts = com.sc.radiation.LeadSuitSC.parts(p);
        for (int i = 0; i < 4; i++) {
            p.inventory.armorInventory[i] = null;
        }
        boolean ok = open > 0.8F && open < 1.2F && dose > 0.08F && walled < open * 0.05F && parts == 4 && prot == 100 && after < dose;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " radiation: RTG 2 bl. away " + open + ", dose " + dose
                + ", behind lead " + walled + ", lead suit (" + parts + " parts) protection " + prot + "%, dose then " + after);
        com.sc.radiation.RadiationSC.setDose(p, 0F);
        w.setBlockToAir(30, 200, 30);
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
        w.setBlockToAir(x, y, z);                      // a block left by an interrupted run: a fresh one, not the old tile
        w.setBlock(x, y, z, b, meta, 3);
        net.minecraft.tileentity.TileEntity te = w.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityWirelessSC)) {
            return null;
        }
        ((TileEntityWirelessSC) te).setPowerOn(true);
        return (TileEntityWirelessSC) te;
    }
}
