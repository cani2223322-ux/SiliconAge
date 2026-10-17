package com.sc.mob.ai;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpShotSC;
import com.sc.emp.ItemEmpGunSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.world.World;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §3.2, rule ПБ-17): a mob with a ПЭ-1 in hand (ItemEmpGunSC held) fires it at its
 * attack target by EmpMobGunSC's rules - a Point every 8..15 s after its 1 s charge-up, a Pulse after 3 s of hum (a hostile:
 * only at a group of players; a settlement's guard - the Commandant: at any hostile Shell it fights). The charge-up breaks
 * off when the sight is lost, the target goes, or a piece of the shooter is suppressed; the shot needs the sight at its end.
 * The Jammer (package M) and the Commandant (package S) add it to their tasks; it holds no movement bits (mutex 0) - the mob
 * keeps walking and looking as its other tasks say, and its look is turned to the target only while it charges.
 * <p>
 * Fixed: the constructor. The rhythm: after a shot the next one (either mode) waits EmpMobGunSC.pointDelay (8..15 s), a
 * Pulse besides the gun's own cooldown (EmpMathSC.PULSE_COOLDOWN, 20 s, as a player's); a charge-up that broke off may start
 * again after RETRY_TICKS - every new one is warned again. Idle, it looks for a shot (the sight, the rules) once in
 * SIGHT_CHECK ticks; while charging it checks the sight as often and breaks off once the target was out of it longer than
 * EmpMathSC.PULSE_LOS_GRACE. State per task (per mob): two carriers never share a charge-up. Server only (vanilla runs the
 * AI on the server).
 */
public class EntityAIEmpShooterSC extends EntityAIBase {

    /** Ticks between two looks for a shot while idle, and between two sight checks while charging (§13: not more often). */
    public static final int SIGHT_CHECK = 10;
    /** A broken charge-up: the next one no sooner than this, ticks. TODO(balance) */
    public static final int RETRY_TICKS = 40;

    protected final EntityCreature mob;
    /** The modes it may use; a ПЭ-1М's numbers (the boss, Ф5). */
    protected final boolean point, pulse, upgraded;
    /** Its reach, blocks (EmpMobGunSC.RANGE; the Commandant's: its dome's radius + 8). */
    protected final double range;

    /** The charge-up under way: its target and mode (target null - none), its ticks so far, ticks the target was out of sight. */
    private Entity target;
    private EmpMode mode;
    private int ticks, lost;
    /** World time of the next shot (either mode), of the next Pulse (the gun's cooldown), of the next idle look for a shot. */
    private long nextShotAt, nextPulseAt, nextLookAt;

    public EntityAIEmpShooterSC(EntityCreature mob, boolean point, boolean pulse, boolean upgraded, double range) {
        this.mob = mob;
        this.point = point;
        this.pulse = pulse;
        this.upgraded = upgraded;
        this.range = range;
        setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        World w = mob.worldObj;
        if (w == null || w.isRemote || !point && !pulse) {
            return false;
        }
        long now = w.getTotalWorldTime();
        if (now < nextShotAt || now < nextLookAt) {
            return false;
        }
        nextLookAt = now + SIGHT_CHECK;
        EntityLivingBase t = mob.getAttackTarget();
        if (t == null || !t.isEntityAlive() || !mob.isEntityAlive() || !EmpMobGunSC.allowed() || !armed()
                || EmpMobGunSC.shooterSuppressed(mob)) {
            return false;
        }
        EmpMode m = pick(t, now);
        if (m == null || EmpMobGunSC.refusal(mob, t, m) != EmpNetSC.OK || !EmpShotSC.inSight(w, mob, t, range)) {
            return false;                                       // a creative player, a pet, behind cover: no charge-up at all
        }
        target = t;
        mode = m;
        return true;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        lost = 0;
        EmpMobGunSC.startCharge(mob, target, mode);
        look();
    }

    @Override
    public boolean continueExecuting() {
        return target != null;
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        World w = mob.worldObj;
        long now = w.getTotalWorldTime();
        if (!target.isEntityAlive() || target.worldObj != w || mob.getAttackTarget() != target || !mob.isEntityAlive()
                || !EmpMobGunSC.allowed() || !armed() || EmpMobGunSC.shooterSuppressed(mob)) {
            breakOff(now);                                      // the target went (or another one), the gun's gone, a piece suppressed
            return;
        }
        ticks++;
        look();
        int charge = EmpMobGunSC.chargeTicks(mode, upgraded);
        if (ticks % SIGHT_CHECK == 0 && ticks < charge) {       // the end's sight is fire()'s own: cover saves
            if (EmpShotSC.inSight(w, mob, target, range)) {
                lost = 0;
            } else if ((lost += SIGHT_CHECK) > EmpMathSC.PULSE_LOS_GRACE) {
                breakOff(now);
                return;
            }
        }
        EmpMobGunSC.chargeTick(mob, target, mode, ticks);
        if (ticks >= charge) {
            Entity t = target;
            EmpMode m = mode;
            target = null;
            EmpMobGunSC.fire(mob, t, m, upgraded, range);
            nextShotAt = now + EmpMobGunSC.pointDelay(mob.getRNG());
            if (m == EmpMode.PULSE) {
                nextPulseAt = now + EmpMathSC.PULSE_COOLDOWN;
            }
        }
    }

    @Override
    public void resetTask() {
        if (target != null) {
            breakOff(mob.worldObj == null ? 0L : mob.worldObj.getTotalWorldTime());
        }
    }

    /** It holds the ПЭ-1 (the Jammer carrier, the Commandant; a subclass may answer otherwise). */
    protected boolean armed() {
        return ItemEmpGunSC.isGun(mob.getHeldItem());
    }

    /** The mode for `t` now: a Pulse where EmpMobGunSC wants one and the cooldown allows, else a Point; null - no shot. */
    protected EmpMode pick(EntityLivingBase t, long now) {
        if (pulse && now >= nextPulseAt && EmpMobGunSC.pulseWanted(mob, t)) {
            return EmpMode.PULSE;
        }
        return point && EmpMobGunSC.pointWanted(mob, t) ? EmpMode.POINT : null;
    }

    private void breakOff(long now) {
        target = null;
        EmpMobGunSC.cancelCharge(mob);
        nextShotAt = Math.max(nextShotAt, now + RETRY_TICKS);
    }

    private void look() {
        if (target != null) {
            mob.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        }
    }
}
