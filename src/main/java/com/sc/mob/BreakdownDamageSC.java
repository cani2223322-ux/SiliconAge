package com.sc.mob;

import net.minecraft.entity.Entity;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EntityDamageSource;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §3, rules ПБ-15, ПБ-16): «Пробой» (Breakdown) - the damage of the Substrate's
 * heavy blows: the Exo Executioner's «Рассечение», the Dome Captain's heavy blow, later «Затмение», the ion geysers and the
 * boss's core reset (Ф4-Ф5). It bypasses the armour (setDamageBypassesArmor: vanilla's and ISpecialArmor's plating stop
 * none of it) and - the documented exception - every energy shield: the player's full Exo / Singular set (ArmorLogicSC.exoStops
 * lets it through, nothing else of that method changed) and a mob's (MobArmorSC.shield). It is never a player's: no player
 * weapon deals it, the blade's armour-breaking, absolute damage and «Казнь» stay BladeLogicSC's (ПБ-9).
 * <p>
 * Two damage types, so the death message's arguments always match: TYPE (a mob's blow - EntityDamageSource, the attacker
 * named: death.attack.scBreakdown = "%1$s ... %2$s") and TYPE_WORLD (the world's: no attacker - death.attack.scBreakdownWorld,
 * .player when vanilla knows who hit last). Shared core: only the coordinator edits this file.
 */
public final class BreakdownDamageSC {

    /** The damage types (and the death messages death.attack.<type>). */
    public static final String TYPE = "scBreakdown", TYPE_WORLD = "scBreakdownWorld";
    /** The world's «Пробой» (a geyser, an arena's discharge - Ф4, Ф5): no attacker. */
    public static final DamageSource WORLD = new DamageSource(TYPE_WORLD).setDamageBypassesArmor();

    private BreakdownDamageSC() {
    }

    /** A mob's heavy blow, `attacker` named (its kill counts as that mob's; the guards know who hit whom). */
    public static DamageSource of(Entity attacker) {
        return attacker == null ? WORLD : new EntityDamageSource(TYPE, attacker).setDamageBypassesArmor();
    }

    /** `src` is «Пробой» (either type). */
    public static boolean is(DamageSource src) {
        return src != null && (TYPE.equals(src.damageType) || TYPE_WORLD.equals(src.damageType));
    }
}
