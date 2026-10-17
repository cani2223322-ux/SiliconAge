package com.sc.mob.ai;

import com.sc.mob.EntityEclipseSC;
import com.sc.mob.IMobActionSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;

import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.MinecraftForge;
import net.minecraftforge.event.entity.living.EnderTeleportEvent;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.4, rule ПС-34): «Мерцание» - «Затмение» blinks up to BLINK_DISTANCE blocks once in
 * BLINK_MIN..BLINK_MAX ticks: while it fights - to its target's side or back (closing the distance from afar); while it flees -
 * away from the nearest player. BLINK_CUE_TICKS before it goes it flickers where it stands (IMobActionSC.ACT_BLINK - every
 * client draws it), then it is at the new place in a burst of crimson motes at both ends. The new place: a loaded column, solid
 * ground, two free blocks, no liquid - and never inside a field with F_NO_ENDER (a settlement's dome, a player's field with the
 * switch on: TileEntityFieldGeneratorSC.fieldWith) nor out of one; other mods may cancel it (Forge's EnderTeleportEvent, as an
 * Enderman's). Movement its own for the cue (mutex 3); a suppressed chestplate - no blink.
 */
public class EntityAIEclipseBlinkSC extends EntityAIBase {

    /** Spots tried for one blink; how far down / up from the wanted height a floor is looked for. */
    private static final int TRIES = 10, DOWN = 6, UP = 3;

    private final EntityEclipseSC mob;
    private int readyAt, ticks;
    private boolean running;
    private double toX, toY, toZ;

    public EntityAIEclipseBlinkSC(EntityEclipseSC mob) {
        this.mob = mob;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        if (mob.ticksExisted < readyAt || mob.mobArmor().suppressed(1) || mob.mobAction() == IMobActionSC.ACT_WINDUP) {
            return false;
        }
        EntityLivingBase t = mob.getAttackTarget();
        EntityPlayer near = mob.fleeing() ? mob.worldObj.getClosestVulnerablePlayerToEntity(mob, EntityEclipseSC.FLEE_DISTANCE) : null;
        if (!mob.fleeing() && (t == null || !t.isEntityAlive() || mob.getDistanceSqToEntity(t) < 9D)) {
            return false;                                         // nothing to close on, or it is right there already
        }
        if (mob.fleeing() && near == null) {
            return false;                                         // nobody to run from
        }
        double[] at = pick(mob.fleeing() ? near : t, mob.fleeing());
        if (at == null) {
            readyAt = mob.ticksExisted + 20;                      // no place now: look again in a second
            return false;
        }
        toX = at[0];
        toY = at[1];
        toZ = at[2];
        return true;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        running = true;
        readyAt = mob.ticksExisted + EntityEclipseSC.BLINK_MIN + mob.getRNG().nextInt(EntityEclipseSC.BLINK_MAX - EntityEclipseSC.BLINK_MIN + 1);
        mob.getNavigator().clearPathEntity();
        mob.setMobAction(IMobActionSC.ACT_BLINK);
        mob.playSound("mob.endermen.stare", 0.6F, 1.6F);
    }

    @Override
    public boolean continueExecuting() {
        return running && mob.isEntityAlive();
    }

    @Override
    public boolean isInterruptible() {
        return false;
    }

    @Override
    public void resetTask() {
        if (mob.mobAction() == IMobActionSC.ACT_BLINK) {
            mob.setMobAction(IMobActionSC.ACT_NONE);
        }
        running = false;
    }

    @Override
    public void updateTask() {
        if (!running) {
            return;
        }
        mob.getNavigator().clearPathEntity();
        if (mob.mobArmor().suppressed(1)) {
            running = false;                                      // a Point in the chest: it stays
            return;
        }
        if (++ticks < EntityEclipseSC.BLINK_CUE_TICKS) {
            return;
        }
        running = false;
        go();
    }

    /** Server: the blink itself (the place checked again: the world may have changed in the cue). */
    private void go() {
        World w = mob.worldObj;
        if (!standable(w, MathHelper.floor_double(toX), MathHelper.floor_double(toY), MathHelper.floor_double(toZ)) || noEnder(w, toX, toY, toZ)) {
            return;
        }
        EnderTeleportEvent ev = new EnderTeleportEvent(mob, toX, toY, toZ, 0F);
        if (MinecraftForge.EVENT_BUS.post(ev)) {
            return;
        }
        double fx = mob.posX, fy = mob.posY, fz = mob.posZ;
        mob.setPositionAndUpdate(ev.targetX, ev.targetY, ev.targetZ);
        mob.fallDistance = 0F;
        if (w instanceof WorldServer) {
            WorldServer ws = (WorldServer) w;
            ws.func_147487_a("reddust", fx, fy + 1D, fz, 24, 0.3D, 0.6D, 0.3D, 0D);
            ws.func_147487_a("portal", fx, fy + 1D, fz, 20, 0.3D, 0.6D, 0.3D, 0.6D);
            ws.func_147487_a("reddust", ev.targetX, ev.targetY + 1D, ev.targetZ, 24, 0.3D, 0.6D, 0.3D, 0D);
        }
        w.playSoundEffect(fx, fy, fz, "mob.endermen.portal", 0.8F, 0.6F);
        mob.playSound("mob.endermen.portal", 0.8F, 0.6F);
    }

    /**
     * A place for a blink: hunting - at `from`'s side or back, 1.5..3 blocks off it, within BLINK_DISTANCE of the mob; fleeing -
     * BLINK_DISTANCE away from `from` (more or less). Null - none of TRIES fits.
     */
    private double[] pick(Entity from, boolean flee) {
        World w = mob.worldObj;
        for (int i = 0; i < TRIES; i++) {
            double x, z;
            if (flee) {
                double ang = Math.atan2(mob.posZ - from.posZ, mob.posX - from.posX) + (mob.getRNG().nextDouble() - 0.5D) * 1.2D;
                double r = EntityEclipseSC.BLINK_DISTANCE * (0.7D + mob.getRNG().nextDouble() * 0.3D);
                x = mob.posX + Math.cos(ang) * r;
                z = mob.posZ + Math.sin(ang) * r;
            } else {
                // round its back: the facing of yaw is (-sin, cos), so its back is the angle yaw - 90 (+-100: the sides too)
                double ang = Math.toRadians(from.rotationYaw - 90D + (mob.getRNG().nextDouble() - 0.5D) * 200D);
                double r = 1.5D + mob.getRNG().nextDouble() * 1.5D;
                x = from.posX + Math.cos(ang) * r;
                z = from.posZ + Math.sin(ang) * r;
                double dx = x - mob.posX, dz = z - mob.posZ, d = Math.sqrt(dx * dx + dz * dz);
                if (d > EntityEclipseSC.BLINK_DISTANCE) {               // too far: as far as a blink goes, towards it
                    x = mob.posX + dx / d * EntityEclipseSC.BLINK_DISTANCE;
                    z = mob.posZ + dz / d * EntityEclipseSC.BLINK_DISTANCE;
                }
            }
            int bx = MathHelper.floor_double(x), bz = MathHelper.floor_double(z), by0 = MathHelper.floor_double(flee ? mob.posY : from.posY);
            for (int y = by0 + UP; y >= by0 - DOWN; y--) {
                if (standable(w, bx, y, bz)) {
                    double tx = bx + 0.5D, tz = bz + 0.5D;
                    if (!noEnder(w, tx, y, tz) && !noEnder(w, mob.posX, mob.posY, mob.posZ)) {
                        return new double[]{tx, y, tz};
                    }
                    break;
                }
            }
        }
        return null;
    }

    /** Feet at (x, y, z): a loaded column, solid ground under, two free blocks, no liquid. */
    private static boolean standable(World w, int x, int y, int z) {
        if (y < 1 || y >= w.getHeight() - 2 || !w.blockExists(x, y, z)) {
            return false;
        }
        Block under = w.getBlock(x, y - 1, z);
        if (!under.getMaterial().isSolid() || under.getMaterial().isLiquid()) {
            return false;
        }
        Block a = w.getBlock(x, y, z), b = w.getBlock(x, y + 1, z);
        return !a.getMaterial().blocksMovement() && !a.getMaterial().isLiquid() && !b.getMaterial().blocksMovement() && !b.getMaterial().isLiquid();
    }

    /** A field with F_NO_ENDER covers the point (a dome - never blink in or out). */
    private static boolean noEnder(World w, double x, double y, double z) {
        return TileEntityFieldGeneratorSC.fieldWith(w, TileEntityFieldGeneratorSC.F_NO_ENDER, x, y + 1D, z) != null;
    }
}
