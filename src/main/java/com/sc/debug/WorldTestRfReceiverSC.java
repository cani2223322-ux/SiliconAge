package com.sc.debug;

import cofh.api.energy.IEnergyReceiver;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.ChunkPosition;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The world test's stand-in for another mod's RF machine: takes every RF it is given and counts it.
 * Never registered and never a block of its own - put straight into the chunk's tile map beside the
 * converter. Its class implements CoFH's IEnergyReceiver, so it is only ever touched (through the
 * static helpers) when ForeignEnergySC.rfApi() said the RF API is there.
 */
public class WorldTestRfReceiverSC extends TileEntity implements IEnergyReceiver {

    private long total;

    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int maxReceive, boolean simulate) {
        if (!simulate && maxReceive > 0) {
            total += maxReceive;
        }
        return Math.max(0, maxReceive);
    }

    @Override
    public int getEnergyStored(ForgeDirection from) {
        return 0;
    }

    @Override
    public int getMaxEnergyStored(ForgeDirection from) {
        return Integer.MAX_VALUE;
    }

    @Override
    public boolean canConnectEnergy(ForgeDirection from) {
        return true;
    }

    /** Puts a receiver at (x, y, z) (the block there made air) - the chunk's map, not the world's tick list. */
    @SuppressWarnings("unchecked")
    static TileEntity place(World w, int x, int y, int z) {
        w.setBlockToAir(x, y, z);
        WorldTestRfReceiverSC te = new WorldTestRfReceiverSC();
        te.setWorldObj(w);
        te.xCoord = x;
        te.yCoord = y;
        te.zCoord = z;
        w.getChunkFromBlockCoords(x, z).chunkTileEntityMap.put(new ChunkPosition(x & 15, y, z & 15), te);
        return te;
    }

    static void remove(World w, int x, int y, int z) {
        Object te = w.getChunkFromBlockCoords(x, z).chunkTileEntityMap.remove(new ChunkPosition(x & 15, y, z & 15));
        if (te instanceof TileEntity) {
            ((TileEntity) te).invalidate();
        }
    }

    static long total(TileEntity te) {
        return te instanceof WorldTestRfReceiverSC ? ((WorldTestRfReceiverSC) te).total : -1;
    }

    /** A neighbour pushing `rf` RF into the converter's face `from`, through the RF interface. @return RF taken */
    static int push(TileEntity converter, ForgeDirection from, int rf) {
        return converter instanceof IEnergyReceiver ? ((IEnergyReceiver) converter).receiveEnergy(from, rf, false) : -1;
    }
}
