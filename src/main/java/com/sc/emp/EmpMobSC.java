package com.sc.emp;

import com.sc.dim.SubstrateConfigSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.MobArmorMathSC;
import com.sc.util.ArmorSuit;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.INpc;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;

/**
 * PACKAGE A (docs/plan-substrate-tech.md §4.3, rules ПБ-10..ПБ-13): ПЭ-1 on the Substrate's armoured mobs - the
 * mob branch's decisions in one place, so EmpGunLogicSC and EmpSiphonSC keep their player paths exactly as they were
 * and only ask here where a target is not a player.
 * <p>
 * A «mob target» is a living IEmpTargetSC that isn't an EntityPlayer (EntityArmoredMobSC and its kin). Point / Pulse
 * need dimension.empMobTargets, the Siphon dimension.siphonMobs - rules of the server (SubstrateNetSC brings them to
 * a client, so the client's own checks agree). Flags off, or anything else living: the old answer, Slowness
 * (EmpSC.applyNoArmour). The mob's own state is MobArmorSC behind IEmpTargetSC.
 */
public final class EmpMobSC {

    private EmpMobSC() {
    }

    /** A living IEmpTargetSC that isn't a player (no rule asked: a running stream asks siphonOn itself). */
    public static boolean mob(Entity e) {
        return e instanceof IEmpTargetSC && e instanceof EntityLivingBase && !(e instanceof EntityPlayer);
    }

    /** Point / Pulse really work on it now: the rule is on and its armour still holds a reserve (else: Slowness). */
    public static boolean pointable(Entity e) {
        return SubstrateConfigSC.empMobTargets && mob(e) && ((IEmpTargetSC) e).empArmoured();
    }

    /**
     * Both sides: the Siphon takes it as a target - the rule is on and it wears the mod's armour (an empty reserve
     * still counts, as for a player: the stream then says «броня цели пуста» instead of «нет цели»).
     */
    public static boolean siphonable(Entity e) {
        return SubstrateConfigSC.siphonMobs && mob(e) && ((IEmpTargetSC) e).empSuit() != null;
    }

    /** The Siphon on mobs is still allowed (checked every stream second). */
    public static boolean siphonOn() {
        return SubstrateConfigSC.siphonMobs;
    }

    /** The aim readout shows its suit and state (either ПЭ-1 rule on). */
    public static boolean readable(Entity e) {
        return (SubstrateConfigSC.empMobTargets || SubstrateConfigSC.siphonMobs) && mob(e);
    }

    /**
     * Ф3 (docs/plan-substrate-tech-f3.md §3.5, §3.6, ПБ-20): unarmoured Substrate technology a Point / Pulse does something of
     * its own to (IEmpReactiveSC: the drones, the nanite swarm, the mimic) - only while dimension.empMobTargets is on (off:
     * Slowness, as before). Server (a client knows the rule too).
     */
    public static boolean reactive(Entity e) {
        return SubstrateConfigSC.empMobTargets && e instanceof IEmpReactiveSC && e instanceof EntityLivingBase && !(e instanceof EntityPlayer);
    }

    /**
     * Ф3, ПБ-19, server: a Point / Pulse that got through the rules and the field's EMP screen meets `e`'s personal EMP screen
     * (the Jammer's, a «Экранированный» elite's) - if it is up, it takes the shot and is down for EliteModSC.SCREEN_RECOVER_TICKS.
     * Only for a mob target the ПЭ-1 really works on (pointable: a «down» suit's screen has no power either). @return taken
     */
    public static boolean screenTakes(Entity e) {
        return pointable(e) && e instanceof IArmoredMobSC && ((IArmoredMobSC) e).mobArmor().screenTakes();
    }

    /**
     * Ф3, ПБ-19, server: the Siphon's start or a stream's second meets a raised personal screen (dimension.siphonMobs - the
     * stream's own rule): the screen drops, the stream stops (EmpSiphonSC). @return taken
     */
    public static boolean siphonScreenTakes(Entity e) {
        return mob(e) && e instanceof IArmoredMobSC && ((IArmoredMobSC) e).mobArmor().screenTakes();
    }

    /** Ф3, ПБ-19: whole seconds until `e`'s personal screen is up again (the shooter's «sc.emp.hit.mob.screen»). */
    public static int screenSeconds(Entity e) {
        if (!(e instanceof IArmoredMobSC) || e.worldObj == null) {
            return 0;
        }
        long left = ((IArmoredMobSC) e).mobArmor().screenDownUntil - e.worldObj.getTotalWorldTime();
        return EmpShotSC.seconds((int) Math.max(0L, Math.min(Integer.MAX_VALUE, left)));
    }

    /**
     * ПБ-10: a Pulse never hits a villager (INpc) or somebody's pet among the mob targets - EmpNetSC.NO_OWN, asked
     * next to EmpRulesSC.refusal, before any EU is spent. EmpNetSC.OK otherwise (and for everything outside the
     * mob branch: the old behaviour). A settlement's Tuner (Ф2, ПЖ-2) is refused in every mode: it wears no armour and
     * would only be slowed. 2026-10-18, ПГ-20: a dome's guard (ISettlementGuardSC - INpc, yet in the mod's armour for real) is
     * no villager here - the Point, the Pulse and the Siphon all work on it (each use provokes: EntityWardenSC), and what the
     * Siphon draws out of it is vented, never delivered (ventsAll: its suit is charged free at the dome's plates - a farm
     * otherwise). Server and client alike (no settlement asked).
     */
    public static int refusal(Entity target, EmpMode mode) {
        if (target instanceof com.sc.settle.ISettlementNpcSC) {
            return EmpNetSC.NO_OWN;
        }
        if (mode == EmpMode.PULSE && (pointable(target) || reactive(target))
                && (target instanceof INpc && !(target instanceof com.sc.settle.ISettlementGuardSC) || owned(target))) {
            return EmpNetSC.NO_OWN;                         // Ф3, §3.6: the reactive ones by the same rule
        }
        return EmpNetSC.OK;
    }

    /**
     * ПГ-20: the Siphon's draw out of `target` reaches no receiver - all of it is vented (sparks, the HUD's «в искры»): a dome's
     * guard, whose reserve the dome's plates fill for nothing (it comes back from the Clone Capsule charged).
     */
    public static boolean ventsAll(Entity target) {
        return target instanceof com.sc.settle.ISettlementGuardSC;
    }

    /** A tamed thing: an owner's UUID (or the owner entity) is set. */
    static boolean owned(Entity e) {
        if (!(e instanceof IEntityOwnable)) {
            return false;
        }
        IEntityOwnable o = (IEntityOwnable) e;
        String id = o.func_152113_b();
        return id != null && id.length() > 0 || o.getOwner() != null;
    }

    /** The shield is up now (Exo / Singular with helium): EmpGunLogicSC tells the shooter when a Point put it out. */
    static boolean shieldUp(Entity e) {
        return e instanceof IArmoredMobSC && ((IArmoredMobSC) e).mobArmor().shieldUp();
    }

    /** Some piece is suppressed now (the siphon's loss is 0 against a suppressed suit, as for a player). */
    static boolean anySuppressed(IEmpTargetSC t) {
        for (int i = 0; i < 4; i++) {
            if (t.empSuppressed(i)) {
                return true;
            }
        }
        return false;
    }

    /**
     * The name for a chat line: its custom name, else the entity's lang key - the reader's client translates it (not
     * func_145748_c_: that translates on the server, and a dedicated one knows only en_US). The shooter's chat, the
     * Marauder's shouts.
     */
    public static IChatComponent name(Entity e) {
        if (e instanceof EntityLiving && ((EntityLiving) e).hasCustomNameTag()) {
            return new ChatComponentText(((EntityLiving) e).getCustomNameTag());
        }
        String s = EntityList.getEntityString(e);
        return s == null ? new ChatComponentText(e.getCommandSenderName()) : new ChatComponentTranslation("entity." + s + ".name");
    }

    /**
     * The aim readout of a mob target (EmpGunLogicSC.aimScan, as readTarget for a player): its suit, the suppressed /
     * immune slots with the longest times, and - with the shooter's Sensor chip - the reserve as the charge %.
     */
    static void read(EntityPlayer shooter, IEmpTargetSC t, EmpNetSC.AimMessage m) {
        ArmorSuit suit = t.empSuit();
        for (int i = 0; i < 4; i++) {
            int sup = t.empSuppressedTicks(i), imm = t.empImmuneTicks(i);
            if (sup > 0) {
                m.slots |= 1 << i;
                m.suppressedTicks = Math.max(m.suppressedTicks, sup);
            } else if (imm > 0) {
                m.slots |= 1 << (4 + i);
                m.immuneTicks = Math.max(m.immuneTicks, imm);
            }
        }
        m.suit = suit == null ? -1 : suit.ordinal();
        m.chargePct = suit != null && EmpSC.hasSensorChip(shooter) ? EmpShotSC.pctOf(t.empChargeEu(), t.empCapacityEu()) : -1;
    }

    // ------------------------------------------------------------------ ПБ-12: a mob gives one stream at most MOB_SIPHON_STREAM_CAP

    /**
     * EU the stream may still deliver from `target` to the receivers: unlimited for a player; for a mob what is left
     * of MOB_SIPHON_STREAM_CAP (its MobArmorSC counts per stream; another IEmpTargetSC - by this Use's own count).
     */
    static long streamRoomLeft(Entity target, EmpGunLogicSC.Use u) {
        if (!mob(target)) {
            return Long.MAX_VALUE;
        }
        if (target instanceof IArmoredMobSC) {
            return ((IArmoredMobSC) target).mobArmor().streamRoomEu();
        }
        return Math.max(0L, MobArmorMathSC.MOB_SIPHON_STREAM_CAP - u.received);
    }

    /** `eu` reached the receivers out of a mob's stream: counted against its cap. */
    static void streamDelivered(Entity target, int eu) {
        if (eu > 0 && mob(target) && target instanceof IArmoredMobSC) {
            ((IArmoredMobSC) target).mobArmor().streamDrawnEu += eu;
        }
    }
}
