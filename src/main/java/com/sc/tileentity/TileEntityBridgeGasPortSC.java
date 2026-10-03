package com.sc.tileentity;

import com.sc.bridge.BridgeMathSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.network.NetworkManager;
import net.minecraft.network.Packet;
import net.minecraft.network.play.server.S35PacketUpdateTileEntity;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidStack;
import net.minecraftforge.fluids.FluidTankInfo;
import net.minecraftforge.fluids.IFluidHandler;

/**
 * The bridge's gas port: pipes, buckets and cells fill (and empty) the controller's shared tanks through
 * it - liquid helium, argon, krypton, deuterium, heavy water, singular matter. Not linked to a controller,
 * it takes nothing. Every gas port beyond the first makes the tanks 50% bigger.
 */
public class TileEntityBridgeGasPortSC extends TileEntity implements IBridgePartSC, IFluidHandler {

    private final BridgeLinkSC link = new BridgeLinkSC();

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

    private TileEntityBridgeControllerSC ctrl() {
        TileEntityBridgeControllerSC c = worldObj == null || worldObj.isRemote ? null : link.controller(worldObj);
        return c != null && c.countsPart(xCoord, yCoord, zCoord) ? c : null;
    }

    private static int tankOf(Fluid f) {
        Gas g = Gas.of(f);
        return g == null ? -1 : BridgeMathSC.tankOf(g);
    }

    @Override
    public int fill(ForgeDirection from, FluidStack resource, boolean doFill) {
        TileEntityBridgeControllerSC c = ctrl();
        int t = resource == null ? -1 : tankOf(resource.getFluid());
        return c == null || t < 0 ? 0 : c.fillTank(t, resource.amount, doFill);
    }

    @Override
    public FluidStack drain(ForgeDirection from, FluidStack resource, boolean doDrain) {
        TileEntityBridgeControllerSC c = ctrl();
        int t = resource == null ? -1 : tankOf(resource.getFluid());
        if (c == null || t < 0) {
            return null;
        }
        int n = c.drainTank(t, resource.amount, doDrain);
        return n <= 0 ? null : new FluidStack(resource.getFluid(), n);
    }

    /** Any gas: the fullest tank. */
    @Override
    public FluidStack drain(ForgeDirection from, int maxDrain, boolean doDrain) {
        TileEntityBridgeControllerSC c = ctrl();
        if (c == null) {
            return null;
        }
        int best = -1;
        for (int i = 0; i < BridgeMathSC.GASES.length; i++) {
            if (c.tankAmount(i) > 0 && (best < 0 || c.tankAmount(i) > c.tankAmount(best))) {
                best = i;
            }
        }
        Fluid f = best < 0 ? null : BridgeMathSC.GASES[best].fluidOf();
        if (f == null) {
            return null;
        }
        int n = c.drainTank(best, maxDrain, doDrain);
        return n <= 0 ? null : new FluidStack(f, n);
    }

    @Override
    public boolean canFill(ForgeDirection from, Fluid fluid) {
        return tankOf(fluid) >= 0 && (worldObj == null || worldObj.isRemote || ctrl() != null);
    }

    @Override
    public boolean canDrain(ForgeDirection from, Fluid fluid) {
        return tankOf(fluid) >= 0;
    }

    @Override
    public FluidTankInfo[] getTankInfo(ForgeDirection from) {
        TileEntityBridgeControllerSC c = ctrl();
        FluidTankInfo[] out = new FluidTankInfo[BridgeMathSC.GASES.length];
        for (int i = 0; i < out.length; i++) {
            Fluid f = BridgeMathSC.GASES[i].fluidOf();
            int amount = c == null ? 0 : c.tankAmount(i);
            out[i] = new FluidTankInfo(f == null || amount <= 0 ? null : new FluidStack(f, amount), c == null ? 0 : c.tankCapacity(i));
        }
        return out;
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
