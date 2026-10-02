package com.sc.util;

/** §16: armor chip categories - "не более 1 чипа каждого типа на костюм", enforced by ItemArmorChipSC. */
public enum ChipType {

    SENSOR("chipSensor"),
    POWER("chipPower"),
    DEFENSE("chipDefense"),
    MOBILITY("chipMobility"),
    UTILITY("chipUtility"),
    // life support (docs/plan-armor-gases.md), appended - metadata is ordinal x 3 + tier.
    // TODO(textures): no icons of their own yet - they borrow the old chips' pictures.
    CRYO_LOOP("chipDefense"),      // helium cools 50 / 75 / 100% better (more heat per mB)
    CRYO_TANK("chipUtility"),      // every gas tank of the worn suit +50 / 75 / 100% (piece NBT GasCapBonus)
    OXYGEN_REGEN("chipSensor"),    // Exo: under water the helmet's oxygen is refilled for EU
    RECUPERATOR("chipPower");      // 30 / 45 / 60% of the helium that boils away comes back

    public final String textureName;

    ChipType(String textureName) {
        this.textureName = textureName;
    }

    /** A life-support chip: no potion effect, works through the suit's gases (ArmorLogicSC). */
    public boolean isGasChip() {
        return ordinal() >= CRYO_LOOP.ordinal();
    }
}
