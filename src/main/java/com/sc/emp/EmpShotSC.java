package com.sc.emp;

import java.util.List;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * ПЭ-1 (package A): the aim ray and the small pure decisions the gun takes - why a use can't start, which siphon
 * receiver gets the energy, what one stream step moves. The pure part has no world and no player: the self-test
 * (package F, SelfTestEmpSC) calls it directly.
 */
public final class EmpShotSC {

    /** Hitbox slack for the aim ray, blocks (as ItemWeaponSC.AIM_MARGIN). */
    public static final double AIM_MARGIN = 0.3;

    /** startRefusal: may a use start. */
    public static final int START_OK = 0, START_DISABLED = 1, START_MODE_OFF = 2, START_OVERHEAT = 3, START_NO_CHARGE = 4,
            START_COOLDOWN = 5;

    private EmpShotSC() {
    }

    // ------------------------------------------------------------------ pure

    /**
     * Why the gun refuses to start a charge-up / a stream (checked in this order), START_OK when it may: the server's
     * config, the mode switched off in the K menu, the gun overheated, too little EU for the mode's price
     * (`cost`), the Pulse cooldown (`cooldownTicks` > 0; 0 for the other modes). Nothing is spent on a refusal.
     */
    public static int startRefusal(boolean configOn, boolean modeOn, boolean overheated, int charge, int cost, int cooldownTicks) {
        if (!configOn) {
            return START_DISABLED;
        }
        if (!modeOn) {
            return START_MODE_OFF;
        }
        if (overheated) {
            return START_OVERHEAT;
        }
        if (charge < cost) {
            return START_NO_CHARGE;
        }
        return cooldownTicks > 0 ? START_COOLDOWN : START_OK;
    }

    /** The message of a startRefusal code (null for START_OK). */
    public static String startKey(int code) {
        switch (code) {
            case START_DISABLED:
                return "sc.emp.refuse." + EmpNetSC.NO_DISABLED;
            case START_MODE_OFF:
                return "sc.emp.modeoff";
            case START_OVERHEAT:
                return "sc.emp.overheat";
            case START_NO_CHARGE:
                return "sc.emp.nocharge";
            case START_COOLDOWN:
                return "sc.emp.cooldown";
            default:
                return null;
        }
    }

    /**
     * The siphon's receiver: the first of `order` (EmpMathSC.RECV_*) that is switched on and has room, or -1
     * («Некуда принимать»). `enabled` / `room` are indexed by the receiver.
     */
    public static int pickReceiver(int[] order, boolean[] enabled, long[] room) {
        for (int r : order) {
            if (r >= 0 && r < enabled.length && r < room.length && enabled[r] && room[r] > 0) {
                return r;
            }
        }
        return -1;
    }

    /**
     * One stream step: `rate` EU at most, no more than the target holds (`available`). `vent` (изменено 2026-10-11, the
     * default «Предел»): the target loses all of it, the receivers (`room` in all) get what fits after the loss, the rest
     * is vented. Not `vent` («пока приёмник не полон»): no more is taken than the room can hold after the loss.
     * @return {EU taken from the target, EU that reach the receivers, EU vented}
     */
    public static int[] streamStep(int rate, long available, long room, int lossPct, boolean vent) {
        long taken = Math.max(0L, Math.min(rate, available));
        int loss = Math.max(0, Math.min(100, lossPct));
        long free = Math.max(0L, room);
        if (vent) {
            int arrived = EmpMathSC.delivered((int) taken, loss);
            int delivered = (int) Math.min(free, arrived);
            return new int[]{(int) taken, delivered, arrived - delivered};
        }
        if (free <= 0) {
            return new int[]{0, 0, 0};
        }
        if (loss < 100) {
            long byRoom = (free * 100 + (100 - loss) - 1) / (100 - loss);     // taken whose delivery just fills the room
            taken = Math.min(taken, byRoom);
        }
        int arrived = EmpMathSC.delivered((int) taken, loss);
        int delivered = (int) Math.min(free, arrived);
        return new int[]{(int) taken, delivered, arrived - delivered};
    }

    /** "Пока моя броня не наберёт N%": reached once `charge` is at least `limitPct` (1..100) of `cap` (other limits / no armour: never). */
    public static boolean limitReached(int limitPct, long charge, long cap) {
        return limitPct > EmpMathSC.LIMIT_VENT && limitPct <= 100 && cap > 0 && charge * 100 >= (long) limitPct * cap;
    }

    /** The stream stops when the receivers are full only with the «пока приёмник не полон» limit; otherwise it vents. */
    public static boolean stopsWhenFull(int limit) {
        return limit == EmpMathSC.LIMIT_FULL;
    }

    /** Charge in %, 0..100 (0 for no capacity). */
    public static int pctOf(long charge, long cap) {
        return cap <= 0 ? 0 : (int) Math.max(0L, Math.min(100L, charge * 100 / cap));
    }

    /**
     * Right-click still held since the last use ended without a release (a shot, a cancel, a stream cut): the client
     * repeats the click every 4 ticks while the button is down, `gap` ticks apart at most.
     */
    public static boolean heldOn(long lastActivity, long now, int gap) {
        return lastActivity > 0 && now >= lastActivity && now - lastActivity <= gap;
    }

    /** Where on the hitbox a hit landed: 0 feet .. 1 top. */
    public static double heightShare(double hitY, double minY, double maxY) {
        return maxY <= minY ? 0.5 : Math.max(0D, Math.min(1D, (hitY - minY) / (maxY - minY)));
    }

    /** Ticks -> whole seconds, rounded up. */
    public static int seconds(int ticks) {
        return (Math.max(0, ticks) + 19) / 20;
    }

    // ------------------------------------------------------------------ the aim ray (both sides)

    /** What the ray met: an entity (or null) and the point - on its hitbox, or where a miss ends (a block or the range). */
    public static final class Hit {
        public final Entity entity;
        public final Vec3 at;

        public Hit(Entity entity, Vec3 at) {
            this.entity = entity;
            this.at = at;
        }
    }

    /** The eyes as vanilla's own ray takes them (server: posY is the feet; client: yOffset already in posY). */
    public static Vec3 eyes(EntityPlayer p) {
        double y = p.posY + 1.62D - p.yOffset + (p.getEyeHeight() - p.getDefaultEyeHeight());
        return Vec3.createVectorHelper(p.posX, y, p.posZ);
    }

    /**
     * Hitscan as ItemWeaponSC.findTarget: from the eyes along the look, the nearest living hitbox (+AIM_MARGIN)
     * before the first block within `range`.
     */
    public static Hit aim(World w, EntityPlayer p, double range) {
        Vec3 eyes = eyes(p);
        Vec3 look = p.getLookVec();
        Vec3 end = eyes.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition block = w.func_147447_a(copy(eyes), copy(end), false, true, false);
        Vec3 stop = block != null && block.hitVec != null ? block.hitVec : end;
        double best = eyes.distanceTo(stop);
        Entity found = null;
        Vec3 foundAt = stop;
        List entities = w.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(range, range, range));
        for (Object o : entities) {
            if (!(o instanceof EntityLivingBase) || ((Entity) o).isDead) {
                continue;
            }
            Entity e = (Entity) o;
            AxisAlignedBB box = e.boundingBox.expand(AIM_MARGIN, AIM_MARGIN, AIM_MARGIN);
            Vec3 at;
            if (box.isVecInside(eyes)) {
                at = eyes;
            } else {
                MovingObjectPosition hit = box.calculateIntercept(eyes, end);
                if (hit == null) {
                    continue;
                }
                at = hit.hitVec;
            }
            double d = eyes.distanceTo(at);
            if (d <= best) {
                best = d;
                found = e;
                foundAt = at;
            }
        }
        return new Hit(found, foundAt);
    }

    /** The middle of an entity's hitbox. */
    public static Vec3 centre(Entity e) {
        AxisAlignedBB b = e.boundingBox;
        return Vec3.createVectorHelper((b.minX + b.maxX) / 2, (b.minY + b.maxY) / 2, (b.minZ + b.maxZ) / 2);
    }

    /** `to` is within `range` of the eyes and some of it (the middle or the head) is not behind a block. */
    public static boolean inSight(World w, EntityPlayer from, Entity to, double range) {
        Vec3 eyes = eyes(from);
        Vec3 mid = centre(to);
        if (eyes.distanceTo(mid) > range + to.width) {
            return false;
        }
        if (w.func_147447_a(copy(eyes), copy(mid), false, true, false) == null) {
            return true;
        }
        Vec3 head = Vec3.createVectorHelper(mid.xCoord, to.boundingBox.maxY - 0.2, mid.zCoord);
        return w.func_147447_a(copy(eyes), head, false, true, false) == null;
    }

    private static Vec3 copy(Vec3 v) {
        return Vec3.createVectorHelper(v.xCoord, v.yCoord, v.zCoord);
    }
}
