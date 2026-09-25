package com.sc.tileentity;

import com.sc.conduit.ConduitKind;
import com.sc.util.PipeType;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTank;

/**
 * A fluid pipe from before conduit bundles, as saved in old worlds. On its first server tick it
 * replaces its block with a conduit bundle holding the same pipe and the fluid that was in it.
 */
public class TileEntityPipeSC extends TileEntity {

    private PipeType pipeType = PipeType.COPPER;
    private FluidStack fluid;

    public PipeType getPipeType() {
        return pipeType;
    }

    public void setPipeType(PipeType pipeType) {
        this.pipeType = pipeType;
    }

    @Override
    public void updateEntity() {
        if (worldObj != null && !worldObj.isRemote) {
            TileEntityConduitBundleSC.convertLegacy(worldObj, xCoord, yCoord, zCoord, ConduitKind.PIPE, pipeType.ordinal(), fluid);
        }
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        super.readFromNBT(nbt);
        PipeType[] types = PipeType.values();
        int ordinal = nbt.getInteger("PipeType");
        pipeType = types[ordinal >= 0 && ordinal < types.length ? ordinal : 0];
        FluidTank tank = new FluidTank(pipeType.throughput);
        tank.readFromNBT(nbt.getCompoundTag("Tank"));
        fluid = tank.getFluid();
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        super.writeToNBT(nbt);
        nbt.setInteger("PipeType", pipeType.ordinal());
        FluidTank tank = new FluidTank(pipeType.throughput);
        tank.setFluid(fluid);
        nbt.setTag("Tank", tank.writeToNBT(new NBTTagCompound()));
    }
}
