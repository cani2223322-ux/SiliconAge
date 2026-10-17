package com.sc.mob.ai;

import java.util.List;

import com.sc.energy.TileEntityEnergyBase;
import com.sc.mob.EntityNaniteSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.MathHelper;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.6): a nanite particle's flight - no path finding (the swarm rule), its own
 * steering and vanilla's collisions. Every RETHINK ticks it looks for prey: the nearest survival player within
 * EntityNaniteSC.SEEK, else the nearest machine with energy in it within MACHINE_SEEK (EntityNaniteSC.edible) - and circles
 * it close enough to feed (the drain itself is the entity's, once a second). With no prey the swarm keeps together: a particle
 * far from the swarm's middle flies back to it, the rest drift round it. Every particle circles at its own phase and radius, so
 * a swarm looks like a cloud. Server; the clients get the motion.
 */
public class EntityAINaniteSwarmSC extends EntityAIBase {

    /** How often it looks for prey (ticks); how far a particle may drift from its swarm's middle; the circle round prey. */
    public static final int RETHINK = 20;
    public static final double DRIFT = 4D, ORBIT_MIN = 0.6D, ORBIT_MAX = 1.6D;
    /** How fast its velocity turns to the wanted one (a share a tick). */
    private static final double STEER = 0.18D;

    private final EntityNaniteSC p;
    private EntityPlayer prey;
    private TileEntityEnergyBase food;
    private int think, wanderIn;
    private double wx, wy, wz;
    private final double phase, orbit;

    public EntityAINaniteSwarmSC(EntityNaniteSC p) {
        this.p = p;
        this.phase = p.getRNG().nextDouble() * Math.PI * 2D;
        this.orbit = ORBIT_MIN + p.getRNG().nextDouble() * (ORBIT_MAX - ORBIT_MIN);
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        return p.isEntityAlive();
    }

    @Override
    public boolean continueExecuting() {
        return p.isEntityAlive();
    }

    @Override
    public void updateTask() {
        if (--think <= 0) {
            think = RETHINK + p.getRNG().nextInt(10);
            prey = findPrey();
            food = prey == null ? findFood() : null;
        }
        double tx, ty, tz;
        double a = p.ticksExisted * 0.18D + phase;
        if (prey != null && prey.isEntityAlive() && !prey.capabilities.isCreativeMode && p.getDistanceSqToEntity(prey) < EntityNaniteSC.SEEK * EntityNaniteSC.SEEK * 2.25D) {
            tx = prey.posX + Math.cos(a) * orbit;
            tz = prey.posZ + Math.sin(a) * orbit;
            ty = prey.posY + prey.height * (0.35D + 0.4D * Math.sin(a * 0.7D + phase));
        } else if (food != null && !food.isInvalid()) {
            tx = food.xCoord + 0.5D + Math.cos(a) * orbit;
            tz = food.zCoord + 0.5D + Math.sin(a) * orbit;
            ty = food.yCoord + 0.8D + 0.4D * Math.sin(a * 0.7D);
        } else {
            prey = null;
            food = null;
            double dx = p.posX - p.midX(), dy = p.posY - p.midY(), dz = p.posZ - p.midZ();
            if (--wanderIn <= 0 || dx * dx + dy * dy + dz * dz > DRIFT * DRIFT * 4D) {
                wanderIn = 40 + p.getRNG().nextInt(40);
                wx = p.midX() + (p.getRNG().nextDouble() * 2D - 1D) * DRIFT;
                wy = p.midY() + (p.getRNG().nextDouble() * 2D - 1D) * 1.5D;
                wz = p.midZ() + (p.getRNG().nextDouble() * 2D - 1D) * DRIFT;
            }
            tx = wx;
            ty = wy;
            tz = wz;
        }
        steer(tx, ty, tz);
    }

    /** Turns the particle's velocity towards (x, y, z) at up to FLY_SPEED, a little jitter on top; it faces where it flies. */
    private void steer(double x, double y, double z) {
        double dx = x - p.posX, dy = y - p.posY, dz = z - p.posZ;
        double d = MathHelper.sqrt_double(dx * dx + dy * dy + dz * dz);
        double speed = d < 1D ? EntityNaniteSC.FLY_SPEED * Math.max(0.3D, d) : EntityNaniteSC.FLY_SPEED;
        double vx = d > 1.0E-3D ? dx / d * speed : 0D, vy = d > 1.0E-3D ? dy / d * speed : 0D, vz = d > 1.0E-3D ? dz / d * speed : 0D;
        p.motionX += (vx - p.motionX) * STEER + (p.getRNG().nextDouble() - 0.5D) * 0.03D;
        p.motionY += (vy - p.motionY) * STEER + (p.getRNG().nextDouble() - 0.5D) * 0.03D;
        p.motionZ += (vz - p.motionZ) * STEER + (p.getRNG().nextDouble() - 0.5D) * 0.03D;
        if (p.isCollidedHorizontally) {
            p.motionY += 0.05D;                                 // over the edge it bumped into
        }
        if (p.motionX * p.motionX + p.motionZ * p.motionZ > 1.0E-4D) {
            p.rotationYaw = p.renderYawOffset = (float) (Math.atan2(p.motionZ, p.motionX) * 180D / Math.PI) - 90F;
        }
    }

    /** The nearest survival player within SEEK (no sight needed: they smell the charge). */
    private EntityPlayer findPrey() {
        EntityPlayer best = null;
        double bestD = EntityNaniteSC.SEEK * EntityNaniteSC.SEEK;
        for (Object o : p.worldObj.playerEntities) {
            EntityPlayer pl = (EntityPlayer) o;
            if (!pl.isEntityAlive() || pl.capabilities.isCreativeMode) {
                continue;
            }
            double d = p.getDistanceSqToEntity(pl);
            if (d < bestD) {
                bestD = d;
                best = pl;
            }
        }
        return best;
    }

    /** The nearest machine with energy within MACHINE_SEEK. */
    private TileEntityEnergyBase findFood() {
        List<TileEntityEnergyBase> l = EntityNaniteSC.machinesNear(p.worldObj, p.posX, p.posY, p.posZ, EntityNaniteSC.MACHINE_SEEK);
        TileEntityEnergyBase best = null;
        double bestD = Double.MAX_VALUE;
        for (TileEntityEnergyBase te : l) {
            double d = te.getDistanceFrom(p.posX, p.posY, p.posZ);
            if (d < bestD) {
                bestD = d;
                best = te;
            }
        }
        return best;
    }
}
