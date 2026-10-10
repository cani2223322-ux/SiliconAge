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
     * shooter, inside a private field that doesn't let the shooter in; and on a real shot, other mods' claims.
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
