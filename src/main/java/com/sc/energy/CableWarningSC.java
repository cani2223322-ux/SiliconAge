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

    /**
     * A placed source's output went up (a Transformer upgrade put into a storage): warns the player
     * if a cable at an output face now carries less than it gives.
     */
    public static void outputRaised(TileEntityEnergyBase src, EntityPlayer player) {
        World world = src.getWorldObj();
        if (world == null || world.isRemote || player == null || !src.isEnergySource()) {
            return;
        }
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (!src.isOutputFace(d)) {
                continue;
            }
            TileEntity n = world.getTileEntity(src.xCoord + d.offsetX, src.yCoord + d.offsetY, src.zCoord + d.offsetZ);
            if (n instanceof TileEntityConduitBundleSC && ((TileEntityConduitBundleSC) n).getCable() != null) {
                Tier cable = ((TileEntityConduitBundleSC) n).getCable().tier;
                if (cable.excessTiersOf(src.outputTier()) > 0) {
                    player.addChatComponentMessage(new ChatComponentTranslation("sc.cable.warn.raised", src.outputTier().name(), cable.name()));
                    return;
                }
            }
        }
    }

    /**
     * ГН-3 (audit 2026-10-20): the weakest cable at an output face of `src` (the mod's bundle, its side open, or an old cable
     * block) that carries less than `out`, or null - none would burn. A reactor gives on every face lit or not, so this is asked
     * before lighting too. Works on the client (the screens' checklists).
     */
    public static Tier weakestCableAt(TileEntityEnergyBase src, Tier out) {
        World world = src == null ? null : src.getWorldObj();
        if (world == null || out == null) {
            return null;
        }
        Tier weakest = null;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            int x = src.xCoord + d.offsetX, y = src.yCoord + d.offsetY, z = src.zCoord + d.offsetZ;
            if (!src.isOutputFace(d) || !world.blockExists(x, y, z)) {
                continue;
            }
            TileEntity n = world.getTileEntity(x, y, z);
            Tier t = null;
            if (n instanceof TileEntityConduitBundleSC) {
                TileEntityConduitBundleSC b = (TileEntityConduitBundleSC) n;
                if (b.getCable() != null && b.cableOpen(d.getOpposite())) {
                    t = b.getCable().tier;
                }
            } else if (n instanceof com.sc.tileentity.TileEntityCableSC && ((com.sc.tileentity.TileEntityCableSC) n).getCableType() != null) {
                t = ((com.sc.tileentity.TileEntityCableSC) n).getCableType().tier;
            }
            if (t != null && t.excessTiersOf(out) > 0 && (weakest == null || t.ordinal() < weakest.ordinal())) {
                weakest = t;
            }
        }
        return weakest;
    }

    /** ГН-3: the players this near hear a lit reactor's weak cable. */
    public static final int IGNITION_WARN_RANGE = 16;

    /**
     * ГН-3: a reactor was just lit (server) - a cable at an output face weaker than its output will burn out as the energy goes
     * out: the players within IGNITION_WARN_RANGE are told (a warning only; FakePlayers never).
     */
    public static void ignitionCheck(TileEntityEnergyBase src) {
        World world = src == null ? null : src.getWorldObj();
        if (world == null || world.isRemote) {
            return;
        }
        Tier out = src.outputTier();
        Tier weak = weakestCableAt(src, out);
        if (weak == null) {
            return;
        }
        double r2 = IGNITION_WARN_RANGE * IGNITION_WARN_RANGE;
        for (Object o : world.playerEntities) {
            if (o instanceof EntityPlayer && !(o instanceof net.minecraftforge.common.util.FakePlayer)
                    && ((EntityPlayer) o).getDistanceSq(src.xCoord + 0.5, src.yCoord + 0.5, src.zCoord + 0.5) <= r2) {
                ((EntityPlayer) o).addChatComponentMessage(new ChatComponentTranslation("sc.chat.gen.cableweak", out.name(), weak.name(), out.name()));
            }
        }
    }

    private static void warn(EntityPlayer p, Tier source, Tier cable) {
        p.addChatComponentMessage(new ChatComponentTranslation("sc.cable.warn",
                source.name(), source.getVoltage(), cable.name(), cable.getVoltage()));
    }
}
