package com.sc.tileentity;

import com.sc.bridge.BridgeMathSC;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;

/**
 * A Singularity Capacitor: 500 million EU for the bridge's burst. It takes no energy of its own - the
 * controller charges it from the bridge's energy ports and empties it at an opening. The item keeps the charge.
 */
public class TileEntityBridgeCapacitorSC extends TileEntity implements IBridgePartSC {

    private long energy;
    private final BridgeLinkSC link = new BridgeLinkSC();

    @Override
    public boolean canUpdate() {
        return false;
    }

    public long getEnergy() {
        return energy;
    }

    public void setEnergy(long eu) {
        long v = Math.max(0, Math.min(BridgeMathSC.CAPACITOR_EU, eu));
        if (v != energy) {
            energy = v;
            if (worldObj != null && !worldObj.isRemote) {
                worldObj.markTileEntityChunkModified(xCoord, yCoord, zCoord, this);
            }
        }
    }

    @Override
    public int[] controllerPos() {
        return link.get();
    }

    @Override
    public void link(int[] pos) {
        if (link.set(pos) && worldObj != null) {
            markDirty();
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        energy = Math.max(0, Math.min(BridgeMathSC.CAPACITOR_EU, nbt.getLong("BridgeEU")));
        link.read(nbt);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setLong("BridgeEU", energy);
        link.write(nbt);
    }

    @Override
    public Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        link.write(nbt);
        return new S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 0, nbt);
    }

    @Override
    public void onDataPacket(NetworkManager net, S35PacketUpdateTileEntity pkt) {
        link.read(pkt.func_148857_g());
    }
}
