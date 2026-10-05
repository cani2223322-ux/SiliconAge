package com.sc.debug;

import com.sc.energy.ForeignEnergySC;
import com.sc.energy.Tier;
import com.sc.init.ModBlocks;
import com.sc.tileentity.TileEntityEnergyConverterSC;
import com.sc.tileentity.TileEntityEnergyStorageSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The Energy Converter in a running world (-Dsc.worldtest=true): an MV energy storage on its west face
 * feeds it EU, an RF receiver (WorldTestRfReceiverSC, the RF API's) on its east face takes RF - EU -> RF
 * must arrive at 1 EU = 4 RF less 5 %; then the other way: RF pushed into the east face, X -> EU, an
 * empty storage on the south face must get the EU at 4 RF = 0.95 EU. Without the RF API: SKIP.
 */
public class WorldTestConverterSC {

    private static final int X = 120, Y = 200, Z = 100;
    private int ticks;
    private TileEntityEnergyConverterSC conv;
    private TileEntityEnergyStorageSC source, sink;
    private TileEntity receiver;
    private long euToX0, euFromX0;
    private double xMade0, xUsed0;
    private long pushed;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END) {
            return;
        }
        ticks++;
        World w = DimensionManager.getWorld(0);
        if (w == null) {
            return;
        }
        if (ticks == 60) {
            if (!ForeignEnergySC.rfApi()) {
                System.out.println("[SC-WORLDTEST] SKIP converter: no RF API in this game (the converter is EU only here)");
                return;
            }
            place(w);
        }
        if (conv == null) {
            return;
        }
        if (ticks == 170) {
            checkEuToRf();
            reverse();
        }
        if (ticks > 172 && ticks <= 272) {
            pushed += WorldTestRfReceiverSC.push(conv, ForgeDirection.EAST, 2000);
        }
        if (ticks == 280) {
            checkRfToEu();
            clean(w);
            conv = null;
        }
    }

    private void place(World w) {
        w.setBlock(X - 1, Y, Z, ModBlocks.energyStorageSC, Tier.MV.ordinal(), 3);
        w.setBlock(X, Y, Z, ModBlocks.energyConverter, 0, 3);
        w.setBlock(X, Y, Z + 1, ModBlocks.energyStorageSC, Tier.MV.ordinal(), 3);
        TileEntity a = w.getTileEntity(X - 1, Y, Z), c = w.getTileEntity(X, Y, Z), b = w.getTileEntity(X, Y, Z + 1);
        if (!(a instanceof TileEntityEnergyStorageSC) || !(c instanceof TileEntityEnergyConverterSC) || !(b instanceof TileEntityEnergyStorageSC)) {
            System.out.println("[SC-WORLDTEST] FAIL converter: could not place the blocks (" + a + ", " + c + ", " + b + ")");
            return;
        }
        source = (TileEntityEnergyStorageSC) a;
        sink = (TileEntityEnergyStorageSC) b;
        conv = (TileEntityEnergyConverterSC) c;
        source.setStorageTier(Tier.MV);
        source.setFacing(ForgeDirection.EAST);                      // its output into the converter
        source.setPowerOn(true);
        source.setEnergyStoredClient(source.getMaxEnergyStored());
        sink.setStorageTier(Tier.MV);
        sink.setFacing(ForgeDirection.SOUTH);                       // its north face (the converter's side) takes
        sink.setPowerOn(false);                                     // not yet
        receiver = WorldTestRfReceiverSC.place(w, X + 1, Y, Z);
        conv.setPowerOn(true);
        for (int s = 0; s < 6; s++) {
            conv.setMode(s, TileEntityEnergyConverterSC.MODE_OFF);
        }
        conv.setMode(ForgeDirection.WEST.ordinal(), TileEntityEnergyConverterSC.MODE_IN);
        conv.setBuf(ForgeDirection.WEST.ordinal(), TileEntityEnergyConverterSC.BUF_EU);
        conv.setMode(ForgeDirection.EAST.ordinal(), TileEntityEnergyConverterSC.MODE_OUT);
        conv.setBuf(ForgeDirection.EAST.ordinal(), TileEntityEnergyConverterSC.BUF_AUTO);   // Авто: the neighbour takes RF
        conv.switchPair(ForeignEnergySC.Kind.RF.ordinal());
        conv.setDirection(TileEntityEnergyConverterSC.DIR_EU_TO_X);
        conv.neighbourChanged();
    }

    private void checkEuToRf() {
        long got = WorldTestRfReceiverSC.total(receiver);
        double ratio = conv.totalEuToX > 0 ? conv.totalXMade / conv.totalEuToX : 0;
        boolean auto = conv.getOutKind(ForgeDirection.EAST.ordinal()) == TileEntityEnergyConverterSC.OUT_X;
        boolean ok = got > 0 && conv.totalEuToX > 0 && Math.abs(ratio - 3.8) < 1e-6 && got <= conv.totalXMade + 1e-6
                && source.getEnergyStored() < source.getMaxEnergyStored() && auto;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " converter EU -> RF: storage gave "
                + (source.getMaxEnergyStored() - source.getEnergyStored()) + " EU, converted " + conv.totalEuToX + " EU into "
                + Math.round(conv.totalXMade) + " RF (x" + ratio + ", 4 less 5 % = 3.8), the RF machine got " + got
                + " RF, the east face (Auto) sends RF: " + auto);
    }

    /** RF -> EU: RF pushed into the east face, the EU out of the south face into the empty storage. */
    private void reverse() {
        euToX0 = conv.totalEuToX;
        euFromX0 = conv.totalEuFromX;
        xMade0 = conv.totalXMade;
        xUsed0 = conv.totalXUsed;
        conv.setMode(ForgeDirection.WEST.ordinal(), TileEntityEnergyConverterSC.MODE_OFF);
        conv.setMode(ForgeDirection.EAST.ordinal(), TileEntityEnergyConverterSC.MODE_IN);
        conv.setBuf(ForgeDirection.EAST.ordinal(), TileEntityEnergyConverterSC.BUF_X);
        conv.setMode(ForgeDirection.SOUTH.ordinal(), TileEntityEnergyConverterSC.MODE_OUT);
        conv.setBuf(ForgeDirection.SOUTH.ordinal(), TileEntityEnergyConverterSC.BUF_EU);
        conv.setDirection(TileEntityEnergyConverterSC.DIR_X_TO_EU);
        source.setPowerOn(false);
        sink.setPowerOn(true);
    }

    private void checkRfToEu() {
        long made = conv.totalEuFromX - euFromX0;
        double used = conv.totalXUsed - xUsed0;
        double ratio = used > 0 ? made / used : 0;
        boolean ok = pushed > 0 && made > 0 && Math.abs(ratio - 0.95 / 4) < 1e-6 && sink.getEnergyStored() > 0 && conv.totalEuToX == euToX0
                && pushed <= (long) (100 * 128 * 4) + 1;
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " converter RF -> EU: " + pushed + " RF taken in (at most 512 RF/t at MV), "
                + Math.round(used) + " RF made " + made + " EU (x" + ratio + ", 0.95 / 4 = 0.2375), the storage on the south face got "
                + sink.getEnergyStored() + " EU; no EU -> RF meanwhile (" + (conv.totalEuToX == euToX0) + ", made " + Math.round(conv.totalXMade - xMade0) + ")");
    }

    private void clean(World w) {
        WorldTestRfReceiverSC.remove(w, X + 1, Y, Z);
        w.setBlockToAir(X - 1, Y, Z);
        w.setBlockToAir(X, Y, Z);
        w.setBlockToAir(X, Y, Z + 1);
        System.out.println("[SC-WORLDTEST] DONE converter");
    }
}
