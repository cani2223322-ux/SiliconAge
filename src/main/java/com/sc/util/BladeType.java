package com.sc.util;

import com.sc.energy.Tier;

/**
 * The energy blades, one per suit (styled like it, and its set bonus goes with it). Like
 * IC2's nano saber: switched off it is only a hilt, switched on it hits hard and costs EU per hit
 * and a little every second in hand. Heat is the blade's own (not the suit's): capacity and
 * cooling per tier, as for the suits.
 *
 * TODO(not in the design doc): all the numbers are this mod's own.
 */
public enum BladeType {

    NANO(ArmorSuit.NANO, Tier.MV, 100000, 4, 12, 200, 10, 3),
    QUANTUM(ArmorSuit.QUANTUM, Tier.HV, 1000000, 5, 20, 500, 20, 4),
    EXO(ArmorSuit.EXO, Tier.EV, 4000000, 6, 30, 1000, 40, 5),
    /** docs/plan-singular-tools.md (appended - ordinals and registry names are saved): only made from an Exo blade in the Singular station. */
    SINGULAR(ArmorSuit.SINGULAR, Tier.SV, 16000000, 7, 40, 1600, 60, 6);

    /** The suit it belongs to (its set bonus). */
    public final ArmorSuit suit;
    public final Tier chargeTier;
    public final int maxCharge;
    /** Damage with the blade off (just the hilt) and on. */
    public final int offDamage, onDamage;
    public final int euPerHit;
    /** EU per second while switched on and in hand. */
    public final int idlePerSecond;
    /** Level of the Looting its looting mode puts on it. */
    public final int looting;
    public final int heatCapacity;
    public final int heatDissipation;

    BladeType(ArmorSuit suit, Tier chargeTier, int maxCharge, int offDamage, int onDamage, int euPerHit, int idlePerSecond, int looting) {
        this.suit = suit;
        this.chargeTier = chargeTier;
        this.maxCharge = maxCharge;
        this.offDamage = offDamage;
        this.onDamage = onDamage;
        this.euPerHit = euPerHit;
        this.idlePerSecond = idlePerSecond;
        this.looting = looting;
        this.heatCapacity = 100 << ordinal();   // 100, 200, 400, 800 (Singular) - like the suits
        this.heatDissipation = 2 << ordinal();  // 2, 4, 8, 16
    }

    /** Exo or above: the Singular blade keeps every Exo rule (absolute damage, the chestplate feeding it). */
    public boolean exoClass() {
        return ordinal() >= EXO.ordinal();
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
