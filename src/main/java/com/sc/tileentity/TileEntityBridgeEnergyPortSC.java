package com.sc.tileentity;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * The bridge's energy port: takes EU from any line up to SV (EV...SV is what it is for) into its buffer;
 * the controller moves it on into the Singularity Capacitors and pays the hold of an open portal from it.
 * It follows the controller's power switch (placed off, like every energy block): off - or not linked to a
 * controller - it takes nothing.
 */
public class TileEntityBridgeEnergyPortSC extends TileEntityEnergyBase implements IBridgePartSC {

    private final BridgeLinkSC link = new BridgeLinkSC();

    public TileEntityBridgeEnergyPortSC() {
        super(Tier.SV);
    }

    @Override
    public boolean canUpdate() {
        return false;
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

    public boolean isLinked() {
        return link.get() != null;
    }

    private boolean takes() {
        return switchedOn() && isLinked();
    }

    @Override
    public int demandedEnergy() {
        return takes() ? super.demandedEnergy() : 0;
    }

    @Override
    public int receiveEnergy(ForgeDirection from, int voltage, int amount, boolean simulate) {
        return takes() ? super.receiveEnergy(from, voltage, amount, simulate) : 0;
    }

    /** The controller's draw. @return EU taken */
    public int take(int max) {
        return max <= 0 ? 0 : removeEnergy(max);
    }

    /** Self-test / world test: fill the buffer. */
    public void putForTest(int eu) {
        addEnergy(eu);
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        link.read(nbt);
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
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
