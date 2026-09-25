package com.sc.energy;

import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;

/**
 * Overvoltage / overcurrent explosion logic, per design doc 01_recipes.md §9.3.
 * All the numbers here are the doc's TODO-filled defaults ("по аналогии", not yet confirmed
 * by the original spec author) and are meant to move into Forge Configuration once step 1
 * is wired up to ConfigSC - see the TODO on EXPLOSION_BASE_POWER below.
 */
public final class ExplosionLogic {

    /** Base explosion power added on top of excessTiers (§9.3: "Сила взрыва = 2 + excessTiers"). */
    // TODO(config): move to ConfigSC once the energy config category is added.
    public static final float EXPLOSION_BASE_POWER = 2.0F;

    /** Fixed power for heat-related explosions (§13.3) - deliberately NOT excessTiers-based;
     *  overheating is a thermal event, not a voltage-tier event, so it gets its own constant. */
    public static final float HEAT_EXPLOSION_POWER = 3.0F;

    private ExplosionLogic() {
    }

    /**
     * Call before actually delivering a packet to a receiver of the given tier. Returns true
     * if the packet overvolted the receiver and an explosion was triggered (caller should not
     * deliver the energy in that case - the receiver is gone).
     */
    public static boolean checkOvervoltageAndExplode(TileEntity receiver, Tier receiverTier, Tier packetTier) {
        int excessTiers = receiverTier.excessTiersOf(packetTier);
        if (excessTiers <= 0) {
            return false;
        }
        explode(receiver, EXPLOSION_BASE_POWER + excessTiers);
        return true;
    }

    public static void explodeFromOverheat(TileEntity te) {
        explode(te, HEAT_EXPLOSION_POWER);
    }

    private static void explode(TileEntity te, float power) {
        World world = te.getWorldObj();
        if (world == null || world.isRemote) {
            return;
        }
        double x = te.xCoord + 0.5D;
        double y = te.yCoord + 0.5D;
        double z = te.zCoord + 0.5D;
        if (te instanceof com.sc.tileentity.TileEntityConduitBundleSC) {
            // only the cable burns out of a conduit bundle - its pipe and tube are left to the
            // blast itself, which drops them like any other block it breaks
            com.sc.tileentity.TileEntityConduitBundleSC bundle = (com.sc.tileentity.TileEntityConduitBundleSC) te;
            bundle.removePart(com.sc.conduit.ConduitKind.CABLE);
            if (bundle.isEmpty()) {
                world.setBlockToAir(te.xCoord, te.yCoord, te.zCoord);
            }
        } else {
            world.setBlockToAir(te.xCoord, te.yCoord, te.zCoord);
        }
        world.createExplosion(null, x, y, z, power, true);
    }
}
