package com.sc.util;

import com.sc.energy.Tier;

/** §7/§16: Ion Cutter, Pulse Emitter, Plasma Rifle - damage/EU-per-shot/range all §16's TODO-filled numbers. */
public enum WeaponType {

    ION_CUTTER("ionCutter", Tier.LV, 5, 20, 1, 4),
    PULSE_EMITTER("pulseEmitter", Tier.MV, 4, 60, 3, 12),
    PLASMA_RIFLE("plasmaRifle", Tier.HV, 12, 150, 1, 16);

    public final String textureName;
    public final Tier tier;
    public final int damagePerHit;
    public final int euPerShot;
    /** Pulse Emitter fires a 3-round burst per §7's flavour text - each round rolls its own hit check. */
    public final int shotsPerUse;
    public final int range;

    WeaponType(String textureName, Tier tier, int damagePerHit, int euPerShot, int shotsPerUse, int range) {
        this.textureName = textureName;
        this.tier = tier;
        this.damagePerHit = damagePerHit;
        this.euPerShot = euPerShot;
        this.shotsPerUse = shotsPerUse;
        this.range = range;
    }
}
