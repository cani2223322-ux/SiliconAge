package com.sc.emp;

import com.sc.item.BladeLogicSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.util.ConfigSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.world.World;

/**
 * PACKAGE E (docs/plan-pe1-tech.md): may the shooter use a ПЭ-1 on the target (PvP, private fields, other mods'
 * claims, friends), and the field generator's «ЭМИ-экран».
 */
public final class EmpRulesSC {

    /** The border search's step along the beam, blocks. */
    static final double SCREEN_STEP = 0.25;

    private EmpRulesSC() {
    }

    /**
     * Server: EmpNetSC.OK, or why not (EmpNetSC.NO_PVP / NO_CLAIM / NO_OWN / NO_DISABLED / NO_PLAYERS). `fire`: a real
     * shot (posts the AttackEntityEvent claims check) - false for the aim scan's preview (no events posted).
     * <p>
     * In this order: the gun off on the server; for a player target - players off in the config, the server's PvP
     * (BladeLogicSC.pvpBlocked: PvP off, a team without friendly fire), "one of us" (the same team, or one owns a
     * switched-on field with the other on its access list - either way round); then for anyone - the target, or the
     * shooter, inside a private field that doesn't let the shooter in; and on a real shot, other mods' claims - these two
     * only with weapons.pe1.respectClaims (off by default).
     */
    public static int refusal(EntityPlayer shooter, Entity target, boolean fire) {
        if (!ConfigSC.empEnabled) {
            return EmpNetSC.NO_DISABLED;
        }
        if (shooter == null || target == null) {
            return EmpNetSC.OK;
        }
        if (target == shooter) {
            return EmpNetSC.NO_OWN;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer t = (EntityPlayer) target;
            if (!ConfigSC.empAffectsPlayers) {
                return EmpNetSC.NO_PLAYERS;
            }
            if (BladeLogicSC.pvpBlocked(shooter, t)) {
                return EmpNetSC.NO_PVP;
            }
            if (friends(shooter, t)) {
                return EmpNetSC.NO_OWN;
            }
        }
        World w = shooter.worldObj;
        if (!ConfigSC.empRespectClaims) {
            return EmpNetSC.OK;                                 // works in someone else's claim too (weapons.pe1.respectClaims)
        }
        if (w != null && !w.isRemote
                && (TileEntityFieldGeneratorSC.privateRefusing(w, shooter, target.posX, target.posY + target.height / 2, target.posZ) != null
                || TileEntityFieldGeneratorSC.privateRefusing(w, shooter, shooter.posX, shooter.posY + shooter.height / 2, shooter.posZ) != null)) {
            return EmpNetSC.NO_CLAIM;
        }
        if (fire && !BladeLogicSC.claimed(shooter, target)) {
            return EmpNetSC.NO_CLAIM;                           // FTB Utilities, GriefPrevention... cancelled the attack
        }
        return EmpNetSC.OK;
    }

    /**
     * Подложка Ф3 (docs/plan-substrate-tech-f3.md §3.1, rules ПБ-17, ПБ-18): the same question for any shooter. A player - the
     * player's refusal above, exactly as before (PvP, claims, friends, weapons.pe1.respectClaims). A mob (EmpMobGunSC: a
     * Jammer's, the Commandant's) - none of the players' permissions apply to it: the gun off on the server (NO_DISABLED);
     * players off in the config, or the target in creative / a spectator (NO_PLAYERS); itself (NO_OWN); a hostile shooter (IMob)
     * never fires at a settlement's creature, a pet or another hostile native (NO_OWN); a settlement's guard only at hostiles
     * (IMob - else NO_OWN).
     * The EMP screen of a field is screenAbsorbs', after this.
     */
    public static int refusal(Entity shooter, Entity target, boolean fire) {
        if (shooter instanceof EntityPlayer) {
            return refusal((EntityPlayer) shooter, target, fire);
        }
        if (!ConfigSC.empEnabled) {
            return EmpNetSC.NO_DISABLED;
        }
        if (shooter == null || target == null) {
            return EmpNetSC.OK;
        }
        if (target == shooter) {
            return EmpNetSC.NO_OWN;
        }
        if (target instanceof EntityPlayer) {
            EntityPlayer t = (EntityPlayer) target;
            if (!ConfigSC.empAffectsPlayers || t.capabilities.disableDamage) {
                return EmpNetSC.NO_PLAYERS;
            }
        }
        if (shooter instanceof com.sc.settle.ISettlementGuardSC) {
            return target instanceof net.minecraft.entity.monster.IMob ? EmpNetSC.OK : EmpNetSC.NO_OWN;
        }
        if (target instanceof com.sc.settle.ISettlementMemberSC || EmpMobSC.owned(target)) {
            return EmpNetSC.NO_OWN;
        }
        if (shooter instanceof net.minecraft.entity.monster.IMob && target instanceof net.minecraft.entity.monster.IMob
                && target instanceof com.sc.mob.ISubstrateNativeSC) {
            return EmpNetSC.NO_OWN;                             // package A: no ПЭ-1 among the natives (a revenge target, a stray aim)
        }
        return EmpNetSC.OK;
    }

    /** "One of us": the same scoreboard team, or a field's owner and someone on its access list (either way round). */
    static boolean friends(EntityPlayer a, EntityPlayer b) {
        if (a.isOnSameTeam((EntityLivingBase) b)) {
            return true;
        }
        return a.worldObj != null && !a.worldObj.isRemote
                && TileEntityFieldGeneratorSC.accessFriends(a.worldObj, a.getCommandSenderName(), b.getCommandSenderName());
    }

    /**
     * Server: an active field with «ЭМИ-экран» covers the target but not the shooter, and has paid `eu`: the beam / stream
     * stops at its border. A field that can't pay doesn't stop it (the next one is asked). The field flashes there.
     * @return the point on the border to draw the beam to, or null (not absorbed)
     */
    public static double[] screenAbsorbs(EntityPlayer shooter, Entity target, int eu) {
        return screenAbsorbs((Entity) shooter, target, eu);
    }

    /**
     * Подложка Ф3 (§3.1): the same for any shooter - a mob's eyes are its posY + getEyeHeight() as a player's are (the body is
     * the player version's, moved here unchanged: the player path answers exactly as before).
     */
    public static double[] screenAbsorbs(Entity shooter, Entity target, int eu) {
        if (shooter == null || target == null || target.worldObj == null || target.worldObj.isRemote
                || shooter.worldObj != target.worldObj) {
            return null;
        }
        double sx = shooter.posX, sy = shooter.posY + shooter.getEyeHeight(), sz = shooter.posZ;
        double tx = target.posX, ty = target.posY + target.height / 2, tz = target.posZ;
        for (TileEntityFieldGeneratorSC f : TileEntityFieldGeneratorSC.activeFieldsIn(target.worldObj)) {
            if (!f.has(TileEntityFieldGeneratorSC.F_EMP_SCREEN) || !f.fieldContains(tx, ty, tz) || f.fieldContains(sx, sy, sz)) {
                continue;
            }
            if (eu > 0 && !f.pay(eu)) {
                continue;                                       // too little in the buffer: this one lets it through
            }
            f.markDirty();
            double[] at = borderPoint(f, sx, sy, sz, tx, ty, tz);
            f.flash(at[0], at[1], at[2]);
            return at;
        }
        return null;
    }

    /** The first point of the beam from (sx..) to (tx..) inside the field, by SCREEN_STEP; the target's point at worst. */
    private static double[] borderPoint(TileEntityFieldGeneratorSC f, double sx, double sy, double sz, double tx, double ty, double tz) {
        double dx = tx - sx, dy = ty - sy, dz = tz - sz;
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int steps = (int) Math.ceil(len / SCREEN_STEP);
        for (int i = 1; i < steps; i++) {
            double k = i / (double) steps;
            double x = sx + dx * k, y = sy + dy * k, z = sz + dz * k;
            if (f.fieldContains(x, y, z)) {
                return new double[]{x, y, z};
            }
        }
        return new double[]{tx, ty, tz};
    }
}
