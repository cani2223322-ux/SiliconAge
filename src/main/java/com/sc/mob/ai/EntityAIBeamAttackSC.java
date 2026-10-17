package com.sc.mob.ai;

import com.sc.hazard.EmpStormSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobCombatSC;
import com.sc.util.SoundsSC;
import com.sc.util.WeaponType;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.2, rule ПС-4): a mob with a mod gun in hand (Ф1: the Ion Cutter) shoots
 * an instant beam - no projectile entity. It aims AIM_TICKS first: a dotted line of sparks from its eyes to the target
 * that everyone near sees, the cue to step behind cover; then the beam (MobCombatSC.beamDamage, the mob's own damage
 * source) - one every RELOAD_TICKS. Only along a clear line of sight (checked once every SIGHT_CHECK ticks and once
 * more at the shot); losing sight drops the aim. Out of range or out of sight it walks up, like a skeleton; a target right next
 * to it gets the fist (MobCombatSC.meleeDamage via the mob's attackEntityAsMob).
 * <p>
 * Movement and look are its own (mutex 3): it stands in for the melee task while the mob holds a gun.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.1, rule ПС-10; package M - an extension only, the Ф1-Ф2 constructor behaves as
 * before): a constructor with its own aim (the Trooper's Plasma Rifle: a laser aim of 30 ticks), a burst (the Jammer's
 * Pulse Emitter: 3 rays 5 ticks apart), a range and the beam's colour; while it aims, such a mob shows IMobActionSC.ACT_AIM
 * (the telegraph every client draws, ПС-9). An armoured native's beam is times its damageMul() (a Commander's group,
 * ПС-21 - 1 for everyone else, so the Ф1-Ф2 numbers stay). Subclasses may walk their own way (move / stop) and set their
 * own mutex (the Jammer keeps its distance with another task).
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §5.5, §7.13, rule ПФ-9): in the Silica Haze its reach is EmpStormSC.sightRange (at most 24
 * blocks - «вражеские стрелки слепнут так же, как игрок»), asked at each sight check.
 */
public class EntityAIBeamAttackSC extends EntityAIBase {

    /**
     * The aim; from one shot to the next (ПС-4: a beam every 2 s, its last second the visible aim); how often it looks for
     * a clear line. Range in blocks. TODO(balance)
     */
    public static final int AIM_TICKS = 20, RELOAD_TICKS = 40, SIGHT_CHECK = 10;
    public static final double RANGE = 12D;
    /** Closer than this the fist (squared blocks, as vanilla's reach for a 0.6-wide mob). */
    private static final double FIST_SQ = 4D;
    /** The beam's colour (the cutter's ion blue), as reddust's «velocity». */
    private static final double R = 0.35D, G = 0.85D, B = 1.0D;

    protected final EntityCreature mob;
    private final double speed;
    /** Ф3: this task's aim, rays a burst and the ticks between them, range, colour; whether it shows ACT_AIM. */
    private final int aimTicks, burst, burstGap;
    private final double range, red, green, blue;
    private final boolean telegraph;
    protected EntityLivingBase target;
    private boolean seen;
    /** Ф4: the reach now (range, or less in the haze), renewed at each sight check. */
    private double reach;
    private int sightIn, aim, reload, fist, repath, burstLeft, burstIn;

    public EntityAIBeamAttackSC(EntityCreature mob, double speed) {
        this.mob = mob;
        this.speed = speed;
        this.aimTicks = AIM_TICKS;
        this.burst = 1;
        this.burstGap = 1;
        this.range = RANGE;
        this.red = R;
        this.green = G;
        this.blue = B;
        this.telegraph = false;
        setMutexBits(3);
    }

    /**
     * Ф3: a beam gun of its own - `aimTicks` of visible aim (IMobActionSC.ACT_AIM while it lasts, when the mob has the
     * telegraph), then `burst` rays `burstGap` ticks apart (each needs the sight), `range` blocks, the beam's colour 0xRRGGBB.
     */
    public EntityAIBeamAttackSC(EntityCreature mob, double speed, int aimTicks, int burst, int burstGap, double range, int rgb) {
        this.mob = mob;
        this.speed = speed;
        this.aimTicks = Math.max(1, aimTicks);
        this.burst = Math.max(1, burst);
        this.burstGap = Math.max(1, burstGap);
        this.range = range;
        this.red = Math.max(0.01D, (rgb >> 16 & 255) / 255D);           // reddust takes a red of 0 as 1
        this.green = (rgb >> 8 & 255) / 255D;
        this.blue = (rgb & 255) / 255D;
        this.telegraph = mob instanceof IMobActionSC;
        setMutexBits(3);
    }

    @Override
    public boolean shouldExecute() {
        EntityLivingBase t = mob.getAttackTarget();
        if (t == null || !t.isEntityAlive() || MobCombatSC.gun(mob) == null) {
            return false;
        }
        target = t;
        return true;
    }

    @Override
    public boolean continueExecuting() {
        return shouldExecute();
    }

    @Override
    public void startExecuting() {
        sightIn = 0;
        reach = range;
        aim = 0;
        repath = 0;
        burstLeft = 0;
    }

    @Override
    public void resetTask() {
        target = null;
        aim = 0;
        burstLeft = 0;
        seen = false;
        show(false);
        stop();
    }

    @Override
    public void updateTask() {
        if (target == null) {
            return;
        }
        if (--sightIn <= 0) {
            seen = mob.getEntitySenses().canSee(target);
            reach = EmpStormSC.sightRange(mob.worldObj, range);      // Ф4, ПФ-9: the haze
            sightIn = SIGHT_CHECK;
        }
        double d = mob.getDistanceSqToEntity(target);
        boolean inRange = d <= reach * reach;
        mob.getLookHelper().setLookPositionWithEntity(target, 30F, 30F);
        move(d, seen, inRange);
        if (fist > 0) {
            fist--;
        }
        if (d <= FIST_SQ && fist <= 0) {                     // right next to it: the fist (no aim needed)
            fist = MobCombatSC.MELEE_COOLDOWN;
            mob.swingItem();
            mob.attackEntityAsMob(target);
        }
        if (burstLeft > 0) {                                 // Ф3: the rest of a burst, burstGap apart
            if (--burstIn <= 0) {
                burstLeft--;
                burstIn = burstGap;
                ray();
                if (burstLeft == 0) {
                    reload = reloadTicks();
                }
            }
            return;
        }
        if (reload > 0) {
            reload--;
            return;
        }
        if (!seen || !inRange) {
            aim = 0;
            show(false);
            return;
        }
        aim++;
        if (aim == 1) {
            show(true);
            SoundsSC.play(mob.worldObj, mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ, "mob.beam.aim", 0.8F, 1F);
        }
        if (aim % 4 == 1) {
            line(6, false);
        }
        if (aim < aimTicks) {
            return;
        }
        aim = 0;
        show(false);
        int rays = burstCount();
        if (rays > 1) {
            burstLeft = rays - 1;                            // the first now, the rest burstGap apart; the reload after the last
            burstIn = burstGap;
        } else {
            reload = reloadTicks();                          // the next aim is the rest of the 2 s
        }
        ray();
    }

    /** One ray at the target - none if it stepped behind cover in the last moment. */
    private void ray() {
        if (target == null || !target.isEntityAlive() || !mob.canEntityBeSeen(target)) {
            return;
        }
        line(12, true);
        SoundsSC.play(mob.worldObj, mob.posX, mob.posY + mob.getEyeHeight(), mob.posZ, "mob.beam.shot", 1F, 1F);
        target.attackEntityFrom(DamageSource.causeMobDamage(mob), beamDamage(MobCombatSC.gun(mob)));
    }

    /** Ф3: the telegraph of the aim (only a mob built with the Ф3 constructor). */
    private void show(boolean on) {
        if (!telegraph || mob.worldObj.isRemote) {
            return;
        }
        IMobActionSC m = (IMobActionSC) mob;
        if (on) {
            m.setMobAction(IMobActionSC.ACT_AIM);
        } else if (m.mobAction() == IMobActionSC.ACT_AIM) {
            m.setMobAction(IMobActionSC.ACT_NONE);
        }
    }

    /**
     * Its walking while it shoots: closer until it sees the target in range, then it stands. A subclass may keep its own
     * distance (or leave the walking to another task).
     */
    protected void move(double distSq, boolean seen, boolean inRange) {
        if (seen && inRange) {
            mob.getNavigator().clearPathEntity();
        } else if (--repath <= 0) {
            mob.getNavigator().tryMoveToEntityLiving(target, speed);
            repath = 10;
        }
    }

    /** The task ends: it stops walking (a subclass that never walks leaves the path to its other task). */
    protected void stop() {
        mob.getNavigator().clearPathEntity();
    }

    /** Ф3: rays a burst (1 - a single shot; the Ф3 constructor's `burst`). A subclass may answer by the gun in hand. */
    protected int burstCount() {
        return burst;
    }

    /** One ray's damage: the gun's (MobCombatSC.beamDamage), times an armoured native's damageMul() (ПС-21). */
    protected float beamDamage(WeaponType gun) {
        float d = MobCombatSC.beamDamage(gun);
        return mob instanceof EntityArmoredMobSC ? d * ((EntityArmoredMobSC) mob).damageMul() : d;
    }

    /**
     * Ticks from a shot to the next aim: the rest of the 2 s (RELOAD_TICKS - AIM_TICKS). A subclass may make it longer
     * (Ф2: the dome guards' Plasma Rifle fires every 2..3 s - settle.npc.ai.EntityAIWardenBeamSC).
     */
    protected int reloadTicks() {
        return RELOAD_TICKS - AIM_TICKS;
    }

    /** Sparks from the mob's eyes to the target's chest, `n` points (server: sent to the players within 16 blocks of each). */
    private void line(int n, boolean shot) {
        if (!(mob.worldObj instanceof WorldServer) || target == null) {
            return;
        }
        WorldServer ws = (WorldServer) mob.worldObj;
        double x0 = mob.posX, y0 = mob.posY + mob.getEyeHeight() - 0.1D, z0 = mob.posZ;
        double x1 = target.posX, y1 = target.posY + target.height * 0.6D, z1 = target.posZ;
        for (int i = 1; i <= n; i++) {
            double k = i / (double) (n + 1);
            ws.func_147487_a("reddust", x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, z0 + (z1 - z0) * k, 0, red, green, blue, 1.0D);
        }
        if (shot) {
            ws.func_147487_a("fireworksSpark", x1, y1, z1, 6, 0.1D, 0.1D, 0.1D, 0.05D);
        }
    }
}
