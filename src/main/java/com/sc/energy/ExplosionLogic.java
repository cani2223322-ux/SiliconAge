package com.sc.energy;

import java.util.ArrayList;
import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.world.World;

/**
 * Overvoltage / overcurrent explosion logic, per design doc 01_recipes.md §9.3.
 * The powers below are the doc's defaults; the server tunes them in ConfigSC's "energy" category:
 * explosions off (the block stays and only fizzles), a power multiplier, and whether an
 * overheat blast breaks the blocks around.
 */
public final class ExplosionLogic {

    /** Base explosion power added on top of excessTiers (§9.3: "Сила взрыва = 2 + excessTiers"), before ConfigSC.explosionPower. */
    public static final float EXPLOSION_BASE_POWER = 2.0F;

    /** Fixed power for heat-related explosions (§13.3) - deliberately NOT excessTiers-based;
     *  overheating is a thermal event, not a voltage-tier event, so it gets its own constant. */
    public static final float HEAT_EXPLOSION_POWER = 3.0F;

    private ExplosionLogic() {
    }

    /**
     * Call before actually delivering a packet to a receiver of the given tier. Returns true
     * if the packet overvolted the receiver (caller should not deliver the energy in that case):
     * it exploded and is gone, or - explosions off in the config - it stays and refuses the packet
     * with smoke and a hiss (receiver.isInvalid() tells the two apart).
     */
    public static boolean checkOvervoltageAndExplode(TileEntity receiver, Tier receiverTier, Tier packetTier) {
        int excessTiers = receiverTier.excessTiersOf(packetTier);
        if (excessTiers <= 0) {
            return false;
        }
        explode(receiver, EXPLOSION_BASE_POWER + excessTiers, false);   // the receiver goes, the blocks around it stay
        return true;
    }

    /**
     * An overloaded cable (a source above its tier, or IC2 burning it): only the cable burns out -
     * smoke and a hiss, no blast, so nothing around it breaks (IC2's cables do the same). The
     * bundle's pipe and tube stay. @return true if it burned (the caller stops delivering)
     */
    public static boolean burnCableIfOvervolted(com.sc.tileentity.TileEntityConduitBundleSC bundle, Tier cableTier, Tier packetTier) {
        if (cableTier.excessTiersOf(packetTier) <= 0) {
            return false;
        }
        burnCable(bundle);
        return true;
    }

    public static void burnCable(com.sc.tileentity.TileEntityConduitBundleSC bundle) {
        World world = bundle.getWorldObj();
        if (world == null || world.isRemote) {
            return;
        }
        int x = bundle.xCoord, y = bundle.yCoord, z = bundle.zCoord;
        bundle.removePart(com.sc.conduit.ConduitKind.CABLE);
        if (bundle.isEmpty()) {
            world.setBlockToAir(x, y, z);
        }
        fizzle(world, x, y, z);
    }

    /** Smoke and a hiss at a block - a burnt cable, or an overload with explosions off. */
    private static void fizzle(World world, int x, int y, int z) {
        world.playSoundEffect(x + 0.5, y + 0.5, z + 0.5, "random.fizz", 1.0F, 0.6F);
        if (world instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) world).func_147487_a("largesmoke", x + 0.5, y + 0.5, z + 0.5, 12, 0.25, 0.25, 0.25, 0.01);
        }
    }

    public static void explodeFromOverheat(TileEntity te) {
        explode(te, HEAT_EXPLOSION_POWER, com.sc.util.ConfigSC.overheatBreaksBlocks);
    }

    /**
     * @param breakBlocks false: the blast still hurts and knocks back, but breaks no block around (overvoltage)
     * Explosions off in the config: the block stays, smoke and a hiss instead (at most once a second each).
     */
    private static void explode(TileEntity te, float power, boolean breakBlocks) {
        World world = te.getWorldObj();
        if (world == null || world.isRemote) {
            return;
        }
        if (!com.sc.util.ConfigSC.explosions) {
            if ((world.getTotalWorldTime() + te.xCoord + te.yCoord + te.zCoord) % 20 == 0) {
                fizzle(world, te.xCoord, te.yCoord, te.zCoord);
            }
            return;
        }
        power *= com.sc.util.ConfigSC.explosionPower;
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
            // what breakBlock throws out (contents, a smelter's xp) waits until the blast has hit
            // everything around - spawned before it, the blast would destroy it on the spot
            AxisAlignedBB box = AxisAlignedBB.getBoundingBox(te.xCoord - 1, te.yCoord - 1, te.zCoord - 1,
                    te.xCoord + 2, te.yCoord + 2, te.zCoord + 2);
            List before = world.getEntitiesWithinAABB(Entity.class, box);
            world.setBlockToAir(te.xCoord, te.yCoord, te.zCoord);
            List<Entity> held = new ArrayList<Entity>();
            for (Object o : world.getEntitiesWithinAABB(Entity.class, box)) {
                if ((o instanceof EntityItem || o instanceof EntityXPOrb) && !before.contains(o)) {
                    ((Entity) o).setDead();
                    held.add((Entity) o);
                }
            }
            world.createExplosion(null, x, y, z, power, breakBlocks);
            for (Entity e : held) {
                Entity copy;
                if (e instanceof EntityItem) {
                    ItemStack stack = ((EntityItem) e).getEntityItem();
                    if (stack == null) {
                        continue;
                    }
                    EntityItem item = new EntityItem(world, e.posX, e.posY, e.posZ, stack.copy());
                    item.delayBeforeCanPickup = ((EntityItem) e).delayBeforeCanPickup;
                    copy = item;
                } else {
                    copy = new EntityXPOrb(world, e.posX, e.posY, e.posZ, ((EntityXPOrb) e).getXpValue());
                }
                copy.motionX = e.motionX;
                copy.motionY = e.motionY;
                copy.motionZ = e.motionZ;
                world.spawnEntityInWorld(copy);
            }
            return;
        }
        world.createExplosion(null, x, y, z, power, breakBlocks);
    }
}
