package com.sc.util;

import com.sc.energy.Tier;

import net.minecraft.item.ItemArmor;
import net.minecraftforge.common.util.EnumHelper;

/**
 * The armor suits (Nano, Quantum, Exo, and the appended Singular) from §6/§16 - damage reduction per piece, in ArmorMaterial's index order
 * (helmet, chestplate, leggings, boots - ItemArmor.armorType). Nano is vanilla Diamond exactly
 * (§16: "Nano ≈ уровень Diamond-брони", 20 points = 80% reduction).
 *
 * TODO(design doc §16 says Quantum/Exo = +50%/+100% of Nano): taken literally that's 30/40
 * points, but Forge's ArmorProperties gives each plain ItemArmor damageReduceAmount/25 of the
 * damage and caps the sum at 100% - anything >= 25 points is total invulnerability (the old
 * 29/40 made Quantum/Exo wearers unkillable by mobs, arrows, explosions and PvP). Scaled onto
 * what's left below that cap instead: Quantum 22 (88%), Exo 24 (96%).
 *
 * Electric like IC2's nano/quantum suits: each piece holds EU and only protects while it has
 * charge; every point of damage it absorbs costs euPerDamage. TODO(not in the design doc):
 * per piece Nano 100k EU / 500 per damage / MV, Quantum 1M / 2000 / HV, Exo 4M / 4000 / EV -
 * about 200 / 500 / 1000 absorbed hearts-halves per full charge (IC2 nano is 1M / 5000).
 */
public enum ArmorSuit {

    NANO("nano", 33, new int[]{3, 8, 6, 3}, 10, 100000, 500, Tier.MV, 100, 2),
    QUANTUM("quantum", 40, new int[]{3, 9, 7, 3}, 14, 1000000, 2000, Tier.HV, 200, 1),
    EXO("exo", 50, new int[]{4, 9, 7, 4}, 18, 4000000, 4000, Tier.EV, 400, 2),
    /**
     * The 4th suit (docs/plan-singular-armor.md, appended - ordinals are saved): 5/10/8/5 (28 points:
     * over Forge's 25-point cap, so a charged suit stops everything blockable - like the full Exo set's
     * energy shield), 64M EU a piece charged at SV, heat 1000. Every Exo function (K10 "Exo legacy")
     * on 20% less gas (ArmorGasSC.gasUseMul). Its levels and own functions come in later stages.
     */
    SINGULAR("singular", 66, new int[]{5, 10, 8, 5}, 22, 64000000, 8000, Tier.SV, 1000, 2);

    /** EU a chip draws from the chestplate per second, per chip tier (TODO: not in the design doc). */
    public static final int CHIP_EU_PER_TIER_SECOND = 20;

    public final String textureName;
    public final ItemArmor.ArmorMaterial material;
    /** §16 Heat: suit-level heat capacity (Nano=100, Quantum=200, Exo=400). */
    public final int heatCapacity;
    /**
     * Heat the suit sheds by itself per second: Nano 2, Quantum 1, Exo 2. Quantum and Exo carry the
     * helium loop (docs/plan-armor-gases.md) - their working heat is the loop's job, so the passive
     * part is small (was 4 / 8 before the life support).
     */
    public final int heatDissipation;
    /** EU one piece holds. */
    public final int maxCharge;
    /** EU spent per point of damage a piece absorbs. */
    public final int euPerDamage;
    /** Charging tier: IC2 chargers must be at least this; EU/t accepted = its voltage. */
    public final Tier chargeTier;

    ArmorSuit(String textureName, int durabilityFactor, int[] reductionAmounts, int enchantability,
              int maxCharge, int euPerDamage, Tier chargeTier, int heatCapacity, int heatDissipation) {
        this.textureName = textureName;
        this.material = EnumHelper.addArmorMaterial(name() + "_SC", durabilityFactor, reductionAmounts, enchantability);
        this.heatCapacity = heatCapacity;       // 100, 200, 400, 1000
        this.heatDissipation = heatDissipation;
        this.maxCharge = maxCharge;
        this.euPerDamage = euPerDamage;
        this.chargeTier = chargeTier;
    }

    /** This suit is `other` or one above it. */
    public boolean atLeast(ArmorSuit other) {
        return other == null || ordinal() >= other.ordinal();
    }

    /** Exo or above (the Singular suit keeps every Exo function and bonus, K10). Null-safe. */
    public static boolean exoClass(ArmorSuit s) {
        return s != null && s.atLeast(EXO);
    }

    /** The suit has colour schemes (SingularScheme, kept in the piece's NBT): the icon and the worn texture follow it. */
    public boolean hasSchemes() {
        return this == SINGULAR;
    }
}
