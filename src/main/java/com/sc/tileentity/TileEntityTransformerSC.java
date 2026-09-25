package com.sc.tileentity;

import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Transformer between two neighbouring tiers (LV-MV, MV-HV, HV-EV), like IC2's: the front face
 * is the high-voltage side, the other five faces the low-voltage side.
 * Step-down (default): takes the high voltage in through the front and gives it out of the
 * other faces at the low voltage. Step-up: takes the low voltage in on the five faces and gives
 * it out of the front at the high voltage. Like IC2's own transformer it gives out one packet of
 * its output voltage per tick (a step-down MV-HV: 128 EU/t, a step-up: 512 EU/t), from a buffer
 * of 8 low-voltage packets, without loss - offering more at once is what IC2 reads as a higher
 * voltage and blows the machines up with. Feeding a side a higher voltage than it is rated for
 * blows the transformer up.
 */
public class TileEntityTransformerSC extends TileEntityEnergyBase {

    private Tier low = Tier.LV;
    private ForgeDirection facing = ForgeDirection.SOUTH;
    private boolean stepUp;

    public TileEntityTransformerSC() {
        super(Tier.MV);
    }

    /** Sets the low tier; the high one is the tier above it (the base tier, shown as the block's tier). */
    public void setLowTier(Tier lowTier) {
        Tier[] tiers = Tier.values();
        low = tiers[Math.min(lowTier.ordinal(), tiers.length - 2)];
        setTier(tiers[low.ordinal() + 1]);
    }

    public Tier getLowTier() {
        return low;
    }

    public Tier getHighTier() {
        return getTier();
    }

    public ForgeDirection getFacing() {
        return facing;
    }

    public void setFacing(ForgeDirection facing) {
        this.facing = facing;
        refreshEnergyNet();
    }

    public boolean isStepUp() {
        return stepUp;
    }

    public void setStepUp(boolean stepUp) {
        this.stepUp = stepUp;
        refreshEnergyNet();
    }

    // ---- tiers and faces ----

    @Override
    public Tier inputTier() {
        return stepUp ? low : getHighTier();
    }

    @Override
    public Tier outputTier() {
        return stepUp ? getHighTier() : low;
    }

    /** A small pass-through buffer: two ticks' worth of the high voltage. */
    @Override
    public int getMaxEnergyStored() {
        return getHighTier().getVoltage() * 2;
    }

    @Override
    public boolean isEnergySink() {
        return true;
    }

    @Override
    public boolean isEnergySource() {
        return true;
    }

    /** Nothing to do between packets - the energy net drives it. */
    @Override
    public boolean canUpdate() {
        return false;
    }

    @Override
    public boolean acceptsFrom(ForgeDirection side) {
        return stepUp ? side != facing : side == facing;
    }

    @Override
    public ForgeDirection[] outputFaces() {
        if (stepUp) {
            return new ForgeDirection[]{facing};
        }
        ForgeDirection[] out = new ForgeDirection[5];
        int i = 0;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (d != facing) {
                out[i++] = d;
            }
        }
        return out;
    }

    // ---- NBT ----

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        setLowTier(Tier.values()[Math.max(0, Math.min(Tier.values().length - 2, nbt.getInteger("LowTier")))]);
        facing = ForgeDirection.getOrientation(nbt.getInteger("Facing"));
        if (facing == ForgeDirection.UNKNOWN) {
            facing = ForgeDirection.SOUTH;
        }
        stepUp = nbt.getBoolean("StepUp");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("LowTier", low.ordinal());
        nbt.setInteger("Facing", facing.ordinal());
        nbt.setBoolean("StepUp", stepUp);
    }

    /** Facing and mode reach the client, so the front and its arrow render right. */
    @Override
    public net.minecraft.network.Packet getDescriptionPacket() {
        NBTTagCompound nbt = new NBTTagCompound();
        writeToNBT(nbt);
        return new net.minecraft.network.play.server.S35PacketUpdateTileEntity(xCoord, yCoord, zCoord, 1, nbt);
    }

    @Override
    public void onDataPacket(net.minecraft.network.NetworkManager net, net.minecraft.network.play.server.S35PacketUpdateTileEntity pkt) {
        readFromNBT(pkt.func_148857_g());
        if (worldObj != null) {
            worldObj.markBlockForUpdate(xCoord, yCoord, zCoord);
        }
    }
}
