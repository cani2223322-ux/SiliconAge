package com.sc.mob;

import com.sc.util.ArmorSuit;

import net.minecraft.item.ItemArmor;

/**
 * PACKAGE A (docs/plan-substrate-tech.md §4.3): the mob armour's arithmetic, pure - MobArmorSC uses it, the self-test
 * checks it, the handbook prints from it. Signatures fixed; A may add more.
 */
public final class MobArmorMathSC {

    /** Points at which a blow is fully stopped (vanilla's 4% a point). */
    public static final int FULL_POINTS = 25;
    /** ПБ-12: EU one siphon stream may give the gun out of a mob. */
    public static final int MOB_SIPHON_STREAM_CAP = 50000;
    /** ПБ-7: units a second a Lineman / Marauder takes back standing at a Copper Trace. TODO(balance) */
    public static final float TRACE_RECHARGE_PER_SECOND = 1F;
    /**
     * A siphon stream's second keeps the mob «drained» this long (its recharge waits, FLAG_DRAINED on): the stream
     * renews it every 20 ticks, a little slack for the shooter's tick jitter - the player's EmpSC.streamSecond holds 25 too.
     */
    public static final int STREAM_HOLD_TICKS = 25;
    /** MobArmorSC.serverTick: the DataWatcher refreshed this often (the ends of suppression / immunity / a stream). */
    public static final int SYNC_EVERY = 10;
    /** A reserve below this is 0 (float leftovers of absorb / drain must not keep a suit «up» with 0 EU in it). */
    public static final float RESERVE_EPSILON = 0.001F;

    private MobArmorMathSC() {
    }

    /** The share of a blockable blow the armour stops while the reserve lasts: min(1, points / 25). */
    public static float absorbShare(int points) {
        return Math.max(0F, Math.min(1F, points / (float) FULL_POINTS));
    }

    /**
     * The reserve that blockable blows of `damage` in all run dry on `points`: each blow puts absorbShare(points) of
     * itself on the reserve, so damage x that share - a Nano helmet (3 points) 12% of it, the full Nano suit (20) 80%.
     * The Lineman's and the Marauder's RESERVE_MIN..MAX are this damage.
     */
    public static float reserveFor(float damage, int points) {
        return Math.max(0F, damage) * absorbShare(points);
    }

    /**
     * ПБ-2: a blockable blow of `amount` on `points` with `reserve` left. @return {to the health, off the reserve} -
     * what the reserve can't cover goes to the health.
     */
    public static float[] absorb(float amount, int points, float reserve) {
        if (amount <= 0F || reserve <= 0F) {
            return new float[]{Math.max(0F, amount), 0F};
        }
        float stopped = Math.min(reserve, amount * absorbShare(points));
        return new float[]{amount - stopped, stopped};
    }

    /** ПБ-4: the shield takes `amount` off `reserve`. @return {what goes on, off the reserve} */
    public static float[] shield(float amount, float reserve) {
        if (amount <= 0F || reserve <= 0F) {
            return new float[]{Math.max(0F, amount), 0F};
        }
        float taken = Math.min(reserve, amount);
        return new float[]{amount - taken, taken};
    }

    /** The iron armour's points of the slot (a suppressed piece, the emergency mode): 2 / 6 / 5 / 2. */
    public static int ironPoints(int armorType) {
        return ItemArmor.ArmorMaterial.IRON.getDamageReductionAmount(armorType);
    }

    /**
     * A mod piece's points on a mob: its own, or - suppressed by a ПЭ-1, or its suit in the emergency mode - iron's for
     * the slot (never better than its own, as ItemArmorSC.suppressedPoints for a player).
     */
    public static int piecePoints(int own, int armorType, boolean ironGrade) {
        return ironGrade ? Math.min(own, ironPoints(armorType)) : own;
    }

    /** ПБ-7: the mob's own recharge may run - `delayTicks` since the last damage, no siphon stream drawing. */
    public static boolean mayRegen(long now, long lastDamageAt, int delayTicks, long streamUntil) {
        return streamUntil <= now && now - lastDamageAt >= Math.max(0, delayTicks);
    }

    /** ПБ-5: only Quantum / Exo / Singular have an emergency mode (no helium); Nano never. */
    public static boolean hasEmergencyMode(ArmorSuit suit) {
        return suit != null && suit != ArmorSuit.NANO;
    }

    /** ПБ-4: the shield is up. */
    public static boolean shieldUp(ArmorSuit chest, boolean helium, boolean chestSuppressed, float reserve) {
        return chest != null && chest.atLeast(ArmorSuit.EXO) && helium && !chestSuppressed && reserve > 0F;
    }

    /** ПБ-11: reserve units as EU, by the suit's EU a unit (ArmorSuit.euPerDamage), and back. */
    public static long unitsToEu(float units, ArmorSuit suit) {
        return suit == null || units <= 0F ? 0L : (long) (units * suit.euPerDamage);
    }

    public static float euToUnits(long eu, ArmorSuit suit) {
        return suit == null || eu <= 0L ? 0F : eu / (float) suit.euPerDamage;
    }
}
