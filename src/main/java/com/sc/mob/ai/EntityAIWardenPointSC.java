package com.sc.mob.ai;

import com.sc.emp.EmpMathSC;
import com.sc.emp.EmpMobGunSC;
import com.sc.emp.EmpMode;
import com.sc.emp.EmpNetSC;
import com.sc.emp.EmpShotSC;
import com.sc.hazard.EmpStormSC;
import com.sc.item.ItemArmorSC;
import com.sc.mob.EntityBreakdownWardenSC;

import net.minecraft.entity.ai.EntityAIBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.1, rules ПС-55, ПБ-17): the Breakdown Warden's ПЭ-1М - a Point at the player with the
 * strongest armour it can see (not its melee target: the one whose suit the Point does the most to), in every phase, by
 * EmpMobGunSC's rules as any mob's ПЭ-1 (EntityAIEmpShooterSC's rhythm): once in EmpMobGunSC.pointDelay (8..15 s) after its 1 s
 * charge-up (ACT_EMP_POINT - the barrel turns blue, the sound, «ЭМИ-наведение» on a player with a Sensor chip); the line of sight
 * at its end (cover saves), the immunity and the field's EMP screen are EmpMobGunSC.fire's; a suppressed Warden doesn't fire. The
 * ПЭ-1М's numbers (upgraded). No Pulse here (the silencing pulse is its own - EntityAIWardenSilenceSC). Never while a telegraph
 * of the fight's is up (WardenFightSC.busy - one wind-up shown at a time): one coming up during the charge (the wave's call, the
 * siren, the heat - they go by the clock) breaks the charge off, so the Point never fires with its warning overdrawn (ПБ-17); the
 * press and the silencing pulse wait for the Point's charge. Looks for a shot once in SIGHT_CHECK; mutex 0 - the
 * Warden keeps walking and striking. Only with the mobs' ПЭ-1 allowed and the ПЭ-1М in hand. Server only.
 */
public class EntityAIWardenPointSC extends EntityAIBase {

    /** Ticks between two looks for a shot, and between two sight checks while charging (§13). */
    public static final int SIGHT_CHECK = EntityAIEmpShooterSC.SIGHT_CHECK, RETRY_TICKS = EntityAIEmpShooterSC.RETRY_TICKS;
    /** An armour score: a piece of the mod's - this, its suit's step on top; anything else - its own points. */
    public static final int MOD_PIECE = 100, SUIT_STEP = 25;

    private final EntityBreakdownWardenSC mob;
    private final double range;
    private EntityPlayer target;
    private int ticks, lost;
    private long nextShotAt, nextLookAt;

    public EntityAIWardenPointSC(EntityBreakdownWardenSC mob, double range) {
        this.mob = mob;
        this.range = range;
        setMutexBits(0);
    }

    @Override
    public boolean shouldExecute() {
        World w = mob.worldObj;
        if (w == null || w.isRemote) {
            return false;
        }
        long now = w.getTotalWorldTime();
        if (now < nextShotAt || now < nextLookAt) {
            return false;
        }
        nextLookAt = now + SIGHT_CHECK;
        if (!mob.isEntityAlive() || !EmpMobGunSC.allowed() || !mob.holdsPe1m() || EmpMobGunSC.shooterSuppressed(mob) || mob.fight().busy()) {
            return false;
        }
        target = strongest(w);
        return target != null;
    }

    @Override
    public void startExecuting() {
        ticks = 0;
        lost = 0;
        EmpMobGunSC.startCharge(mob, target, EmpMode.POINT);
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
        if (!target.isEntityAlive() || target.worldObj != w || !mob.isEntityAlive() || !EmpMobGunSC.allowed() || !mob.holdsPe1m()
                || EmpMobGunSC.shooterSuppressed(mob) || mob.fight().busy()) {
            breakOff(now);                                      // ПБ-17: a telegraph of the fight's came up over the charge - no shot unseen
            return;
        }
        ticks++;
        look();
        int charge = EmpMobGunSC.chargeTicks(EmpMode.POINT, true);
        if (ticks % SIGHT_CHECK == 0 && ticks < charge) {
            if (EmpShotSC.inSight(w, mob, target, reach(w))) {
                lost = 0;
            } else if ((lost += SIGHT_CHECK) > EmpMathSC.PULSE_LOS_GRACE) {
                breakOff(now);
                return;
            }
        }
        EmpMobGunSC.chargeTick(mob, target, EmpMode.POINT, ticks);
        if (ticks >= charge) {
            EntityPlayer t = target;
            target = null;
            EmpMobGunSC.fire(mob, t, EmpMode.POINT, true, reach(w));
            nextShotAt = now + EmpMobGunSC.pointDelay(mob.getRNG());
        }
    }

    @Override
    public void resetTask() {
        if (target != null) {
            breakOff(mob.worldObj == null ? 0L : mob.worldObj.getTotalWorldTime());
        }
    }

    /** Ф4, ПФ-9: the reach in the Silica Haze. */
    private double reach(World w) {
        return EmpStormSC.sightRange(w, range);
    }

    /** ПС-55: the real player in reach and in sight, the rules letting the Point through, with the strongest armour (ties: the nearest). */
    private EntityPlayer strongest(World w) {
        EntityPlayer best = null;
        int bestScore = -1;
        double bestD = Double.MAX_VALUE, r = reach(w);
        for (Object o : w.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p instanceof FakePlayer || !p.isEntityAlive() || p.capabilities.disableDamage) {
                continue;
            }
            double d = p.getDistanceSqToEntity(mob);
            if (d > (r + 2D) * (r + 2D)) {
                continue;
            }
            int s = armourScore(p);
            if (s < bestScore || s == bestScore && d >= bestD) {
                continue;
            }
            if (EmpMobGunSC.refusal(mob, p, EmpMode.POINT) != EmpNetSC.OK || !EmpShotSC.inSight(w, mob, p, r)) {
                continue;
            }
            best = p;
            bestScore = s;
            bestD = d;
        }
        return best;
    }

    /** How strong `p`'s armour is: each piece of the mod's MOD_PIECE + its suit's step, other armour its points (pure but for the slots). */
    public static int armourScore(EntityPlayer p) {
        int s = 0;
        for (int i = 0; i < p.inventory.armorInventory.length; i++) {
            ItemStack a = p.inventory.armorInventory[i];
            if (a == null) {
                continue;
            }
            if (a.getItem() instanceof ItemArmorSC) {
                s += MOD_PIECE + SUIT_STEP * ((ItemArmorSC) a.getItem()).getSuit().ordinal();
            } else if (a.getItem() instanceof ItemArmor) {
                s += ((ItemArmor) a.getItem()).damageReduceAmount;
            }
        }
        return s;
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
