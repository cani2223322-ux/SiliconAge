package com.sc.emp;

import java.util.Random;

import com.sc.dim.SubstrateConfigSC;
import com.sc.mob.IMobActionSC;
import com.sc.util.ConfigSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §3.2, rules ПБ-17, ПБ-18): the ПЭ-1 in a mob's hands - a Jammer carrier's (one
 * in 50), the City Commandant's (at the hostiles only), later the Sentinel's and the boss's. The same rules as a player's
 * shot, with the warnings the proposal asks for (§8 «ПЭ-1 в руках мобов»):
 * <ul>
 * <li>a Point at most once in POINT_EVERY_MIN..MAX ticks, after POINT_WARN_TICKS of charge-up (the barrel turns blue -
 * IMobActionSC.ACT_EMP_POINT, the sound emp.charge); a Pulse after PULSE_CHARGE_TICKS of hum heard PULSE_HUM_RANGE off
 * (ACT_EMP_PULSE) - a hostile only when PULSE_GROUP_MIN players stand within PULSE_GROUP_RADIUS of its target (pulseWorth);</li>
 * <li>the shot needs the line of sight at its end (cover saves - EmpShotSC.inSight for any shooter), one target at a time;
 * EmpRulesSC.refusal(Entity, ...) (config, creative, settlement creatures, pets; a guard only at IMob) and EmpMobSC.refusal
 * (a Pulse spares the INpc and pets); the field's EMP screen (EmpRulesSC.screenAbsorbs(Entity, ...)) - the settlement core
 * pays for it too; a mob's personal screen (MobArmorSC.screenTakes);</li>
 * <li>on a player: EmpSC.applyHit - the player's own durations, immunity (20 s), heat and boil, the «Страж» reflection of a
 * Pulse (EmpArmorSC.guardReflects - back on the shooter if it is an IEmpTargetSC), «ЭМИ-наведение» while it charges
 * (EmpSC.markAimed); on a mob: IEmpTargetSC.empHit / IEmpReactiveSC.empStruck; anything else - Slowness;</li>
 * <li>the beam, the sparks and the absorbed flash as FxMessage with the mob's entity id as the shooter (EmpFxSC draws a
 * non-player's beam from its eyes already); the mob's own gun costs nothing (no EU, no heat - a mob's PE-1 isn't charged);</li>
 * <li>a suppressed shooter doesn't fire (any piece suppressed: the charge-up breaks off); allowed() - weapons.pe1.enabled,
 * affectsPlayers and dimension.mobsUseEmp - else no mob holds a ПЭ-1 at all (the carrier's Ion Cutter instead).</li>
 * </ul>
 * Fixed signatures (packages M - the Jammer, S - the Commandant - call them through EntityAIEmpShooterSC); the bodies are A's.
 * Stateless: the charge-up's clock is the AI task's (EntityAIEmpShooterSC), so two carriers aiming at two players - or at the
 * same one - never share anything. Server only.
 */
public final class EmpMobGunSC {

    /** ПБ-17: a Point's charge-up; the gap between two Points; a Pulse's charge-up (its hum's range: EmpMathSC.PULSE_HUM_RANGE). TODO(balance) */
    public static final int POINT_WARN_TICKS = 20, POINT_EVERY_MIN = 160, POINT_EVERY_MAX = 300, PULSE_CHARGE_TICKS = 60,
            PULSE_HUM_RANGE = EmpMathSC.PULSE_HUM_RANGE;
    /** ПБ-17: a hostile's Pulse only at a group - this many players within PULSE_GROUP_RADIUS of the target (it counted). TODO(balance) */
    public static final int PULSE_GROUP_MIN = 2;
    public static final double PULSE_GROUP_RADIUS = 5D;
    /** A mob's reach with it: the player's (EmpMathSC.RANGE_POINT / RANGE_PULSE, 32); the Commandant's own is larger (package S). */
    public static final int RANGE = EmpMathSC.RANGE_POINT;
    /** Every armour slot (a Pulse, a reflected Pulse). */
    private static final int[] ALL_PIECES = {0, 1, 2, 3};

    private EmpMobGunSC() {
    }

    /** ПБ-17: mobs may hold and fire a ПЭ-1 at all now (the server's config). */
    public static boolean allowed() {
        return ConfigSC.empEnabled && ConfigSC.empAffectsPlayers && SubstrateConfigSC.mobsUseEmp;
    }

    /** ПБ-17 (pure): a hostile's Pulse is worth it - `playersNear` of the target (it counted) are a group. */
    public static boolean pulseWorth(int playersNear) {
        return playersNear >= PULSE_GROUP_MIN;
    }

    /** ПБ-17 (pure but for the dice): ticks to the next Point, POINT_EVERY_MIN..POINT_EVERY_MAX. */
    public static int pointDelay(Random rand) {
        return POINT_EVERY_MIN + rand.nextInt(POINT_EVERY_MAX - POINT_EVERY_MIN + 1);
    }

    /** ПБ-17 (pure): the charge-up of `mode` in a mob's hands - a Point 1 s, a Pulse 3 s (a ПЭ-1М's: the player's 2 s); 0 - no such shot. */
    public static int chargeTicks(EmpMode mode, boolean upgraded) {
        if (mode == EmpMode.PULSE) {
            return upgraded ? EmpMathSC.CHARGE_PULSE_M : PULSE_CHARGE_TICKS;
        }
        return mode == EmpMode.POINT ? POINT_WARN_TICKS : 0;
    }

    /**
     * Server: may `shooter` fire `mode` at `target` now - EmpNetSC.OK or the reason (EmpRulesSC.refusal for a mob, then
     * EmpMobSC.refusal; NO_DISABLED while !allowed()). No EU, no event posted.
     */
    public static int refusal(EntityLivingBase shooter, Entity target, EmpMode mode) {
        if (!allowed() || mode == null || mode == EmpMode.SIPHON) {
            return EmpNetSC.NO_DISABLED;                        // a mob has no Siphon (ПБ-17)
        }
        int r = EmpRulesSC.refusal((Entity) shooter, target, true);
        return r != EmpNetSC.OK ? r : EmpMobSC.refusal(target, mode);
    }

    /**
     * ПБ-17, server: a Pulse is the right shot at `target` - a settlement's guard (the Commandant) at any armoured hostile the
     * ПЭ-1 works on; a hostile only at a player standing in a group (pulseWorth of playersNear). The Point is for the rest.
     */
    public static boolean pulseWanted(EntityLivingBase shooter, Entity target) {
        if (shooter instanceof com.sc.settle.ISettlementGuardSC) {
            return target instanceof IMob && (EmpMobSC.pointable(target) || EmpMobSC.reactive(target));
        }
        return target instanceof EntityPlayer && pulseWorth(playersNear(target));
    }

    /**
     * ПБ-17, server: a Point is worth firing at `target` - a guard only at a hostile the ПЭ-1 does something to (an armoured
     * one, the reactive technology), a hostile at whatever it fights (a player without the mod's armour gets the Slowness).
     */
    public static boolean pointWanted(EntityLivingBase shooter, Entity target) {
        if (shooter instanceof com.sc.settle.ISettlementGuardSC) {
            return target instanceof IMob && (EmpMobSC.pointable(target) || EmpMobSC.reactive(target));
        }
        return target instanceof EntityLivingBase;
    }

    /** ПБ-17, server: the players a Pulse at `target` is weighed by - alive, not in creative, within PULSE_GROUP_RADIUS (it counted). */
    public static int playersNear(Entity target) {
        if (target == null || target.worldObj == null) {
            return 0;
        }
        int n = 0;
        double r2 = PULSE_GROUP_RADIUS * PULSE_GROUP_RADIUS;
        for (Object o : target.worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.isEntityAlive() && !p.capabilities.disableDamage && p.getDistanceSqToEntity(target) <= r2) {
                n++;
            }
        }
        return n;
    }

    /** Server: some piece of the shooter's own armour is suppressed now - it doesn't fire (ПБ-17). */
    public static boolean shooterSuppressed(EntityLivingBase shooter) {
        if (!(shooter instanceof IEmpTargetSC)) {
            return false;
        }
        IEmpTargetSC t = (IEmpTargetSC) shooter;
        for (int i = 0; i < 4; i++) {
            if (t.empSuppressed(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Server: the charge-up starts - the telegraph (IMobActionSC.ACT_EMP_POINT / ACT_EMP_PULSE), the sound, «ЭМИ-наведение» on
     * a player target. EntityAIEmpShooterSC calls it once, then fire() after the charge-up.
     */
    public static void startCharge(EntityLivingBase shooter, Entity target, EmpMode mode) {
        if (shooter == null || shooter.worldObj == null || shooter.worldObj.isRemote || mode == null) {
            return;
        }
        if (shooter instanceof IMobActionSC) {
            ((IMobActionSC) shooter).setMobAction(mode == EmpMode.PULSE ? IMobActionSC.ACT_EMP_PULSE : IMobActionSC.ACT_EMP_POINT);
        }
        World w = shooter.worldObj;
        if (mode == EmpMode.PULSE) {
            SoundsSC.play(w, shooter.posX, shooter.posY, shooter.posZ, EmpSC.SND_PULSE_HUM, PULSE_HUM_RANGE / 16F, 0.8F);
        } else {
            SoundsSC.play(w, shooter.posX, shooter.posY, shooter.posZ, EmpSC.SND_CHARGE, 0.6F, 1F);
        }
        aimed(shooter, target);
    }

    /**
     * Server, every tick of a running charge-up (`ticks` since its start): the Pulse's hum grows once a second (as the
     * player's), «ЭМИ-наведение» is renewed on a player target as the player's aim scan renews it.
     */
    public static void chargeTick(EntityLivingBase shooter, Entity target, EmpMode mode, int ticks) {
        if (shooter == null || shooter.worldObj == null || shooter.worldObj.isRemote || ticks <= 0) {
            return;
        }
        if (mode == EmpMode.PULSE && ticks % 20 == 0) {
            SoundsSC.play(shooter.worldObj, shooter.posX, shooter.posY, shooter.posZ, EmpSC.SND_PULSE_HUM, PULSE_HUM_RANGE / 16F,
                    0.8F + ticks / 100F);
        }
        if (ticks % EmpMathSC.AIM_SCAN_TICKS == 0) {
            aimed(shooter, target);
        }
    }

    /** Server: the charge-up broke off (lost sight, suppressed, target gone): the telegraph back to ACT_NONE, the hum stops. */
    public static void cancelCharge(EntityLivingBase shooter) {
        telegraphOff(shooter);                                  // the hum is a second's sound each: nothing more plays
    }

    /**
     * Server: the shot after the charge-up (Point / Pulse; `upgraded` - a ПЭ-1М, the boss's) within `range` blocks. The line
     * of sight, the rules, the screens, then the hit as described above. @return what it did to a mod-armoured target (null -
     * refused, out of sight or range, absorbed, or a target without such armour; HitResult.screened - a mob's personal
     * screen took it)
     */
    public static EmpSC.HitResult fire(EntityLivingBase shooter, Entity target, EmpMode mode, boolean upgraded, double range) {
        if (shooter == null || target == null || shooter.worldObj == null || shooter.worldObj.isRemote
                || target.worldObj != shooter.worldObj || mode == null || mode == EmpMode.SIPHON) {
            return null;
        }
        telegraphOff(shooter);
        World w = shooter.worldObj;
        if (!allowed() || shooter.isDead || target.isDead || shooterSuppressed(shooter)
                || !EmpShotSC.inSight(w, shooter, target, range)) {
            return null;                                        // cover saves: no shot at all, nothing to see
        }
        if (refusal(shooter, target, mode) != EmpNetSC.OK) {
            return null;
        }
        SoundsSC.play(w, shooter.posX, shooter.posY, shooter.posZ, mode == EmpMode.PULSE ? EmpSC.SND_PULSE : EmpSC.SND_SHOT, 1F, 1F);
        int beam = mode == EmpMode.PULSE ? EmpNetSC.FX_PULSE : EmpNetSC.FX_POINT;
        double[] border = EmpRulesSC.screenAbsorbs((Entity) shooter, target, EmpMathSC.screenCost(mode));
        if (border != null) {
            fx(shooter, beam, target.getEntityId(), border[0], border[1], border[2]);
            fx(shooter, EmpNetSC.FX_ABSORBED, target.getEntityId(), border[0], border[1], border[2]);
            return null;
        }
        Vec3 at = EmpShotSC.centre(target);                     // a mob aims at the middle: a Point lands on the chestplate
        fx(shooter, beam, target.getEntityId(), at.xCoord, at.yCoord, at.zCoord);
        if (mode == EmpMode.PULSE && target instanceof EntityPlayer && EmpArmorSC.guardReflects((EntityPlayer) target, shooter.getRNG())) {
            fx(shooter, EmpNetSC.FX_REFLECTED, target.getEntityId(), shooter.posX, shooter.boundingBox.minY + shooter.height / 2, shooter.posZ);
            reflected(shooter, target, upgraded);
            return null;
        }
        int[] pieces = mode == EmpMode.POINT
                ? EmpMathSC.pointPieces(EmpMathSC.ZONE_AUTO, EmpShotSC.heightShare(at.yCoord, target.boundingBox.minY, target.boundingBox.maxY))
                : ALL_PIECES;
        if (target instanceof EntityPlayer) {
            // the player's own path: durations by its suit, immunity, heat, the boil, «Ваша броня подавлена ЭМИ», the sync
            EmpSC.HitResult r = EmpSC.applyHit((EntityPlayer) target, mode, upgraded, pieces);
            if (r.anySuppressed()) {
                suppressedFx(shooter, target);
            }
            return r;
        }
        if (EmpMobSC.pointable(target)) {
            if (EmpMobSC.screenTakes(target)) {
                fx(shooter, EmpNetSC.FX_ABSORBED, target.getEntityId(), at.xCoord, at.yCoord, at.zCoord);   // ПБ-19
                EmpSC.HitResult r = new EmpSC.HitResult();
                r.screened = true;
                return r;
            }
            EmpSC.HitResult r = ((IEmpTargetSC) target).empHit(shooter, mode, upgraded, pieces);
            if (r.anySuppressed()) {
                suppressedFx(shooter, target);
            }
            return r;
        }
        if (EmpMobSC.reactive(target) && ((IEmpReactiveSC) target).empStruck(shooter, mode, at.xCoord, at.yCoord, at.zCoord)) {
            suppressedFx(shooter, target);                      // ПБ-20: a drone falls, a swarm burns, a mimic wakes up
            return null;
        }
        if (target instanceof EntityLivingBase) {
            EmpSC.applyNoArmour((EntityLivingBase) target);
        }
        return null;
    }

    /** Server stopped: nothing kept (A's transient state, if any). */
    public static void clearAll() {
        // stateless: the charge-ups live in their AI tasks, which go with their mobs
    }

    // ------------------------------------------------------------------ helpers

    /** «Страж» turned the Pulse back: an armoured shooter takes it (its personal screen first); any other - nothing. */
    private static void reflected(EntityLivingBase shooter, Entity target, boolean upgraded) {
        if (!EmpMobSC.pointable(shooter)) {
            return;                                             // a shooter without the mod's armour: nothing happens (as a player's)
        }
        if (EmpMobSC.screenTakes(shooter)) {
            Vec3 c = EmpShotSC.centre(shooter);
            fx(shooter, EmpNetSC.FX_ABSORBED, shooter.getEntityId(), c.xCoord, c.yCoord, c.zCoord);
            return;
        }
        EmpSC.HitResult back = ((IEmpTargetSC) shooter).empHit(target, EmpMode.PULSE, upgraded, ALL_PIECES);
        if (back.anySuppressed()) {
            suppressedFx(shooter, shooter);
        }
    }

    /** «ЭМИ-наведение» on a player target with a Sensor chip (the player's aim scan marks only those - the HUD shows only them). */
    private static void aimed(EntityLivingBase shooter, Entity target) {
        if (target instanceof EntityPlayer && EmpSC.hasSensorChip((EntityPlayer) target)) {
            EmpSC.markAimed((EntityPlayer) target, shooter);
        }
    }

    /** The ПЭ-1 telegraph back to ACT_NONE - only if it is still ours (another wind-up of the mob is left alone). */
    private static void telegraphOff(EntityLivingBase shooter) {
        if (shooter instanceof IMobActionSC && shooter.worldObj != null && !shooter.worldObj.isRemote) {
            IMobActionSC a = (IMobActionSC) shooter;
            int now = a.mobAction();
            if (now == IMobActionSC.ACT_EMP_POINT || now == IMobActionSC.ACT_EMP_PULSE) {
                a.setMobAction(IMobActionSC.ACT_NONE);
            }
        }
    }

    private static void suppressedFx(EntityLivingBase shooter, Entity target) {
        SoundsSC.play(target.worldObj, target.posX, target.posY, target.posZ, EmpSC.SND_SUPPRESS, 0.8F, 1F);
        Vec3 c = EmpShotSC.centre(target);
        fx(shooter, EmpNetSC.FX_SUPPRESSED, target.getEntityId(), c.xCoord, c.yCoord, c.zCoord);
    }

    /** An effect to everyone around the shooter - the mob's entity id as the shooter's (EmpGunLogicSC.fx for any entity). */
    private static void fx(Entity shooter, int kind, int targetId, double x, double y, double z) {
        EmpNetSC.sendFx(shooter.worldObj, new EmpNetSC.FxMessage(kind, shooter.getEntityId(), targetId, x, y, z),
                shooter.posX, shooter.posY, shooter.posZ);
    }
}
