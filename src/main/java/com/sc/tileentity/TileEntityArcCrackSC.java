package com.sc.tileentity;

import java.util.List;

import com.sc.conduit.ConduitKind;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.mob.BreakdownDamageSC;

import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * PACKAGE W (docs/plan-substrate-tech-f4.md §4.3, rule ПА-5; proposal §3 «Пробойная трещина»): a Breakdown crack - 32 EU/t for
 * nothing (EU_PER_TICK, LV), only into a cable of the mod beside it (outputFaces / EnergyNetSC; IC2's net through the base
 * class) - «для аванпоста на месте». Within HAZARD_RADIUS once a second: a player wearing the mod's armour loses
 * DRAIN_TENTHS_PCT / 10 % of every worn piece's charge (ItemArmorSC.discharge), anyone else alive takes HAZARD_DAMAGE of «Пробой»
 * (BreakdownDamageSC.WORLD). Never charges items, never gives to a swarm (canItemCharge / extractForItemCharging: nothing), takes
 * nothing in. Fixed (the coordinator's): the class, the registry id "SiliconAge.arcCrack", the numbers, the refusals below; the
 * rest W's.
 * <p>
 * W: the output faces are those with a conduit bundle holding a cable (or an old world's cable block, turning into one) beside
 * them - looked at on the first tick, when a neighbour changes (BlockSubstrateVentSC) and once in FACES_EVERY ticks; the array
 * handed out stays the same object until the faces change (EnergyNetSC keys its cache by it), and a change re-announces the tile
 * to IC2 (refreshEnergyNet: IC2 caches the faces a source emits on). A machine or another mod's cable put straight against the
 * crack gets nothing. The buffer fills by EU_PER_TICK only while a cable is there. «In the mod's armour» means a worn piece gave
 * charge this second: a suit run flat protects no more - its wearer takes the «Пробой» as anyone (creative players: nothing).
 */
public class TileEntityArcCrackSC extends TileEntityEnergyBase {

    /** ПА-5: the crack's output, the hazard's reach and bite (1 damage a second or -0.5% charge a piece a second). */
    public static final int EU_PER_TICK = 32, HAZARD_RADIUS = 2, HAZARD_DAMAGE = 1, DRAIN_TENTHS_PCT = 5;
    /** How often the faces are looked at without a neighbour's word (ticks). */
    public static final int FACES_EVERY = 100;

    private static final ForgeDirection[] NONE = new ForgeDirection[0];
    /** The faces with a cable of the mod beside them, as a bit set (-1: never looked at) and as the array handed out. */
    private int faceBits = -1;
    private ForgeDirection[] faces = NONE;
    private boolean facesDue = true;

    public TileEntityArcCrackSC() {
        super(Tier.LV);
    }

    @Override
    public boolean canUpdate() {
        return true;
    }

    @Override
    public void updateEntity() {
        if (worldObj == null || worldObj.isRemote) {
            return;
        }
        long now = worldObj.getTotalWorldTime();
        int phase = xCoord * 7 + yCoord * 3 + zCoord * 13 & 15;
        if (facesDue || (now + phase) % FACES_EVERY == 0) {
            facesDue = false;
            lookAtFaces();
        }
        if (faces.length > 0) {
            addEnergy(EU_PER_TICK);
        }
        if ((now + phase) % 20 == 0) {
            hazard();
        }
    }

    /** BlockSubstrateVentSC: a neighbour changed - the faces are looked at on the next tick. */
    public void neighbourChanged() {
        facesDue = true;
    }

    private void lookAtFaces() {
        int bits = 0, n = 0;
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (cableAt(d)) {
                bits |= 1 << d.ordinal();
                n++;
            }
        }
        if (bits == faceBits) {
            return;
        }
        boolean announce = faceBits != -1 || bits != 0;
        ForgeDirection[] out = n == 0 ? NONE : new ForgeDirection[n];
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if ((bits & 1 << d.ordinal()) != 0) {
                out[--n] = d;
            }
        }
        faceBits = bits;
        faces = out;
        if (announce) {
            refreshEnergyNet();                         // IC2 caches the faces a source emits on
        }
    }

    /** A cable of the mod beside face `d` (never loads a chunk). */
    private boolean cableAt(ForgeDirection d) {
        int x = xCoord + d.offsetX, y = yCoord + d.offsetY, z = zCoord + d.offsetZ;
        if (y < 0 || y > 255 || !worldObj.blockExists(x, y, z)) {
            return false;
        }
        TileEntity te = worldObj.getTileEntity(x, y, z);
        return te instanceof TileEntityConduitBundleSC && ((TileEntityConduitBundleSC) te).has(ConduitKind.CABLE) || te instanceof TileEntityCableSC;
    }

    /** ПА-5: once a second within HAZARD_RADIUS of the crack's top - the armour's charge, or a «Пробой». */
    @SuppressWarnings("unchecked")
    private void hazard() {
        double cx = xCoord + 0.5D, top = yCoord + 1.0D, cz = zCoord + 0.5D, reach = HAZARD_RADIUS + 0.5D;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(cx - reach, top - reach, cz - reach, cx + reach, top + reach, cz + reach);
        for (EntityLivingBase e : (List<EntityLivingBase>) worldObj.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            if (!e.isEntityAlive() || e.getDistanceSq(cx, top, cz) > reach * reach) {
                continue;
            }
            if (e instanceof EntityPlayer) {
                EntityPlayer p = (EntityPlayer) e;
                if (p.capabilities.isCreativeMode) {
                    continue;
                }
                if (TileEntitySubstrateVentSC.drainArmour(p, DRAIN_TENTHS_PCT, 1000)) {
                    sparks(e);
                    continue;                           // the suit took it
                }
            }
            if (e.attackEntityFrom(BreakdownDamageSC.WORLD, HAZARD_DAMAGE)) {
                sparks(e);
            }
        }
    }

    private void sparks(EntityLivingBase e) {
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("magicCrit", e.posX, e.posY + e.height * 0.5D, e.posZ, 6, 0.2D, 0.3D, 0.2D, 0.1D);
        }
    }

    /** A source - but only through the faces with a cable (outputFaces). */
    @Override
    public boolean isEnergySource() {
        return true;
    }

    @Override
    public ForgeDirection[] outputFaces() {
        return faces;
    }

    /** Nothing goes in. */
    @Override
    public boolean isEnergySink() {
        return false;
    }

    /** ПА-5, proposal §8 (the swarm «не питается от трещин»): nobody charges from it but by a cable. */
    @Override
    public boolean canItemCharge(EntityPlayer p) {
        return false;
    }

    @Override
    public int extractForItemCharging(int max) {
        return 0;
    }
}
