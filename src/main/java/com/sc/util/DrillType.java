package com.sc.util;

import com.sc.energy.Tier;

/**
 * The three electric drills, one per suit (styled like it; its set bonus goes with it). Pickaxe +
 * shovel, EU per block, no durability. The batteries are small on purpose: worn energy armour feeds
 * the drill (DrillLogicSC.pay) and its weapon charger tops it up - the drill is meant to be used
 * with the suit. Heat is the drill's own, as for the blades.
 *
 * TODO(not in the design doc): all the numbers are this mod's own.
 */
public enum DrillType {

    NANO(ArmorSuit.NANO, Tier.MV, 10000, 12F, 50, 3, 0),
    QUANTUM(ArmorSuit.QUANTUM, Tier.HV, 40000, 24F, 150, 4, 3),
    EXO(ArmorSuit.EXO, Tier.EV, 100000, 48F, 300, 5, 5);

    /** The suit it belongs to (its set bonus). */
    public final ArmorSuit suit;
    public final Tier chargeTier;
    public final int maxCharge;
    /** Dig speed on what it's made for (diamond pickaxe: 8). */
    public final float speed;
    public final int euPerBlock;
    /** Pickaxe / shovel harvest level (diamond: 3). */
    public final int harvestLevel;
    /** Fortune level of its fortune function (0: it has none). */
    public final int fortune;
    public final int heatCapacity;
    public final int heatDissipation;

    DrillType(ArmorSuit suit, Tier chargeTier, int maxCharge, float speed, int euPerBlock, int harvestLevel, int fortune) {
        this.suit = suit;
        this.chargeTier = chargeTier;
        this.maxCharge = maxCharge;
        this.speed = speed;
        this.euPerBlock = euPerBlock;
        this.harvestLevel = harvestLevel;
        this.fortune = fortune;
        this.heatCapacity = 100 << ordinal();   // 100, 200, 400 - like the suits and blades
        this.heatDissipation = 2 << ordinal();  // 2, 4, 8
    }

    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }
}
