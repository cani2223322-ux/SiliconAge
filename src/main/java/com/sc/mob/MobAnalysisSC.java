package com.sc.mob;

import com.sc.emp.EmpMobSC;
import com.sc.emp.IEmpReactiveSC;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularSenseData.Analysis;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §7.2, rule ПИ-2): the Singular helmet's analyzer (Ш5, SingularSensesSC) on a
 * Substrate mob - its armour packed into the Analysis flags (SingularSenseData.Analysis: WEAK_EMP, EMERGENCY, ELITE, SHIELD,
 * DOWN, SCREEN, EMP_REACTS, the suit and the reserve's % in their bits). Pure but for reading the mob; the self-test checks
 * the packing (pack / suitOf / reserveOf round trip). The player armour code only calls flags().
 * <p>
 * The same readings serve WAILA (compat.WailaEntitySC, from the client's DataWatcher): modPieces, elite, reactiveKey.
 */
public final class MobAnalysisSC {

    private MobAnalysisSC() {
    }

    /** Server: the Ф3 flags of `e` (0 for anything that isn't a Substrate mob, and for a disguised mimic). */
    public static int flags(EntityLivingBase e) {
        if (e == null || e instanceof EntityPlayer || e instanceof IDisguisedSC && ((IDisguisedSC) e).disguised()) {
            return 0;                                   // ПИ-3: a mimic asleep tells nothing - not even that it is a mob
        }
        int f = 0;
        if (e instanceof IArmoredMobSC) {
            MobArmorSC a = ((IArmoredMobSC) e).mobArmor();
            ArmorSuit s = a.suit();
            int pct = s == null || a.reserveMax <= 0F ? -1 : Math.round(100F * Math.max(0F, a.reserve) / a.reserveMax);
            f |= pack(s, pct);
            if (s != null) {
                f |= (EmpMobSC.pointable(e) ? Analysis.WEAK_EMP : 0) | (a.emergency ? Analysis.EMERGENCY : 0)
                        | (a.shieldUp() ? Analysis.SHIELD : 0) | (a.down() ? Analysis.DOWN : 0) | (a.screenUp() ? Analysis.SCREEN : 0);
            }
            if (elite(e)) {
                f |= Analysis.ELITE;
            }
        }
        if (EmpMobSC.reactive(e)) {
            f |= Analysis.EMP_REACTS;
        }
        return f;
    }

    /**
     * Pure: the suit (null none) and the reserve's % (0..100; below 0 - unknown) in the Analysis bits SUIT_SHIFT.. (ordinal + 1)
     * and RESERVE_SHIFT.. (RESERVE_UNKNOWN); every other bit 0 - OR it into the flags.
     */
    public static int pack(ArmorSuit suit, int reservePct) {
        int code = suit == null ? 0 : suit.ordinal() + 1;
        int pct = reservePct < 0 ? Analysis.RESERVE_UNKNOWN : Math.min(100, reservePct);
        return (code & Analysis.SUIT_MASK) << Analysis.SUIT_SHIFT | (pct & Analysis.RESERVE_MASK) << Analysis.RESERVE_SHIFT;
    }

    /** Pure: the suit packed into `flags`, null - none (or a code from a newer version). */
    public static ArmorSuit suitOf(int flags) {
        int code = (flags >>> Analysis.SUIT_SHIFT) & Analysis.SUIT_MASK;
        ArmorSuit[] all = ArmorSuit.values();
        return code >= 1 && code <= all.length ? all[code - 1] : null;
    }

    /** Pure: the reserve's % packed into `flags` (0..100), -1 - unknown. */
    public static int reserveOf(int flags) {
        int pct = (flags >>> Analysis.RESERVE_SHIFT) & Analysis.RESERVE_MASK;
        return pct > 100 ? -1 : pct;
    }

    // ------------------------------------------------------------------ readings both sides share (WAILA, the analyzer's lines)

    /** Both sides: how many of the mod's armour pieces `e` wears (the worn stacks are synced). */
    public static int modPieces(EntityLivingBase e) {
        int n = 0;
        for (int i = 0; i < 4; i++) {
            if (MobArmorSC.modPiece(e, i) != null) {
                n++;
            }
        }
        return n;
    }

    /**
     * Both sides: an elite - a modifier (EntityArmoredMobSC.DW_ELITE), an elite of its kind (the Executioner), or the flag
     * (MobArmorSC.elite on the server, FLAG_ELITE on a client).
     */
    public static boolean elite(EntityLivingBase e) {
        if (e instanceof EntityArmoredMobSC && ((EntityArmoredMobSC) e).executeImmune()) {
            return true;
        }
        if (!(e instanceof IArmoredMobSC)) {
            return false;
        }
        return e.worldObj != null && e.worldObj.isRemote ? MobArmorSC.clientFlag(e, MobArmorSC.FLAG_ELITE) : ((IArmoredMobSC) e).mobArmor().elite;
    }

    /**
     * What a ПЭ-1 does to a reactive one (ПБ-20), the tail of its lang key: "drop" (the drones fall), "burn" (a swarm's
     * particle), "wake" (the mimic); null - not reactive technology, or one of another kind (a generic line).
     */
    public static String reactiveKey(Entity e) {
        if (!(e instanceof IEmpReactiveSC)) {
            return null;
        }
        if (e instanceof EntityRepairDroneSC || e instanceof EntitySentryDroneSC) {
            return "drop";
        }
        if (e instanceof EntityNaniteSC) {
            return "burn";
        }
        return e instanceof EntityMimicSC ? "wake" : null;
    }
}
