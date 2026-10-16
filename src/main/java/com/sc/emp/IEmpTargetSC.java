package com.sc.emp;

import com.sc.util.ArmorSuit;

import net.minecraft.entity.Entity;

/**
 * A non-player target the ПЭ-1 really works on (docs/plan-substrate-tech.md §4.6, rules ПБ-10..ПБ-14): the
 * Substrate's armoured mobs (EntityArmoredMobSC). Players keep their own path in EmpGunLogicSC / EmpSiphonSC
 * untouched (EmpSC.applyHit, the suit's charge) - this interface is asked only for a target that is NOT an
 * EntityPlayer, and only while dimension.empMobTargets (Point / Pulse) / dimension.siphonMobs (Siphon) are on.
 * Anything else living keeps the old answer: Slowness (EmpSC.applyNoArmour).
 * <p>
 * The same rules as against players: the durations of EmpMathSC by the piece's suit, the immunity after a
 * suppression, «Сердце комплекта» (a suppressed chestplate of an Exo / Singular mob drops its shield), the siphon
 * as a share of the FULL reserve a second by the chestplate's suit (6 / 5 / 4 / 3%). Pieces: armorType, 0 helmet ..
 * 3 boots. Server only unless noted.
 */
public interface IEmpTargetSC {

    /**
     * Some piece of the mod's armour is worn and still holds a reserve (false: Slowness, as for any mob). Both sides:
     * the Siphon's client check (EmpGunLogicSC.rightClick -> EmpSiphonSC.targetOf) asks it too, so an implementation
     * answers from synced data only (EntityArmoredMobSC: the worn equipment and MobArmorSC.FLAG_DOWN).
     */
    boolean empArmoured();

    /**
     * A Point / Pulse that got through the rules and the EMP screen: suppresses the worn pieces in `pieces` (immune
     * ones are skipped), syncs the look. @return what happened, as for a player (ticks per slot, immune slots)
     */
    EmpSC.HitResult empHit(Entity shooter, EmpMode mode, boolean upgraded, int[] pieces);

    /** Both sides (a client from the DataWatcher): the piece in slot `piece` is suppressed now. */
    boolean empSuppressed(int piece);

    /** Ticks of suppression / immunity left on the slot, 0 none (server). */
    int empSuppressedTicks(int piece);

    int empImmuneTicks(int piece);

    /** The suit the siphon rate and the readout go by (the chestplate's; without one the best piece), null none. Both sides (the equipment). */
    ArmorSuit empSuit();

    /** The Defense chip equivalent, 0..3 (-10/20/30% suppression, -20/30/40% siphon). */
    int empDefenseTier();

    /** Siphon: the full and the current reserve as EU (reserve units x the suit's EU a unit). */
    long empCapacityEu();

    long empChargeEu();

    /** Siphon: takes up to `eu` out of the reserve. @return EU taken */
    int empDrainEu(int eu);

    /** Once a second of a running stream: the mob's own recharge stops, the «drained» look for a moment. */
    void empStreamSecond(Entity shooter);
}
