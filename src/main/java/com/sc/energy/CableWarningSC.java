package com.sc.energy;

import com.sc.tileentity.TileEntityConduitBundleSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * A chat warning when a cable ends up next to a source that gives more than the cable carries
 * (it would burn out as soon as the source gives energy): on placing the cable, and on placing
 * the source (storage, charge pad, generator, transformer) next to a cable.
 */
public final class CableWarningSC {

    private CableWarningSC() {
    }

    /** A cable was just put at (x, y, z). */
    public static void cablePlaced(World world, int x, int y, int z, EntityPlayer player) {
        if (world.isRemote || player == null) {
            return;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityConduitBundleSC) || ((TileEntityConduitBundleSC) te).getCable() == null) {
            return;
        }
        Tier cable = ((TileEntityConduitBundleSC) te).getCable().tier;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            TileEntity n = world.getTileEntity(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
            if (n instanceof TileEntityEnergyBase) {
                TileEntityEnergyBase src = (TileEntityEnergyBase) n;
                if (src.isEnergySource() && src.isOutputFace(d.getOpposite()) && cable.excessTiersOf(src.outputTier()) > 0) {
                    warn(player, src.outputTier(), cable);
                    return;
                }
            }
        }
    }

    /** A source was just put at (x, y, z) (its facing already set). */
    public static void sourcePlaced(World world, int x, int y, int z, EntityLivingBase placer) {
        if (world.isRemote || !(placer instanceof EntityPlayer)) {
            return;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyBase) || !((TileEntityEnergyBase) te).isEnergySource()) {
            return;
        }
        TileEntityEnergyBase src = (TileEntityEnergyBase) te;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (!src.isOutputFace(d)) {
                continue;
            }
            TileEntity n = world.getTileEntity(x + d.offsetX, y + d.offsetY, z + d.offsetZ);
            if (n instanceof TileEntityConduitBundleSC && ((TileEntityConduitBundleSC) n).getCable() != null) {
                Tier cable = ((TileEntityConduitBundleSC) n).getCable().tier;
                if (cable.excessTiersOf(src.outputTier()) > 0) {
                    warn((EntityPlayer) placer, src.outputTier(), cable);
                    return;
                }
            }
        }
    }

    private static void warn(EntityPlayer p, Tier source, Tier cable) {
        p.addChatComponentMessage(new ChatComponentTranslation("sc.cable.warn",
                source.name(), source.getVoltage(), cable.name(), cable.getVoltage()));
    }
}
