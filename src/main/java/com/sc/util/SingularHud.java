package com.sc.util;

import java.util.ArrayList;
import java.util.List;

import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.item.ItemStack;

/**
 * М3 (docs/plan-singular-armor.md §6): what the cooldown HUD shows - pure rules, no client classes
 * (the drawing is client.SingularHudSC). One icon per Singular key function the player has (its piece
 * worn, open at the level, on the chosen side of its branch; the set ones with all four Singular
 * pieces): while it cools down, a few seconds after it's ready again («готов»), while the suit lacks
 * the gas the key would ask for (only for a function that's switched on and has a key - or fires on
 * its own); К1's boost / weakness and Н4's slowing while they run.
 */
public final class SingularHud {

    /** The functions, in the HUD's order. */
    public static final ArmorFeature[] FEATURES = {ArmorFeature.PHASE_DASH, ArmorFeature.GRAV_PRESS, ArmorFeature.GRAV_GRAB,
        ArmorFeature.TIME_SLOW, ArmorFeature.BLACK_HOLE, ArmorFeature.GRAV_DOME, ArmorFeature.SINGULARITY, ArmorFeature.VOID_RESCUE,
        ArmorFeature.HEAT_VENT};
    /** An icon's state. */
    public static final int HIDDEN = 0, COOLING = 1, READY = 2, NOGAS = 3, BOOST = 4, WEAK = 5, RUNNING = 6;
    /** «готов» stays this many ticks after the cooldown ends. */
    public static final int READY_TICKS = 60;

    private SingularHud() {
    }

    /** Fires by itself, not by a key (Б4 the void rescue, Н11 the heat vent). */
    public static boolean automatic(ArmorFeature f) {
        return f == ArmorFeature.VOID_RESCUE || f == ArmorFeature.HEAT_VENT;
    }

    /** The gases a use asks for, in the order SingularPowersSC / ArmorLogicSC check them. */
    public static Gas[] gases(ArmorFeature f) {
        switch (f) {
            case PHASE_DASH: return new Gas[]{Gas.HYDROGEN};
            case GRAV_PRESS: return new Gas[]{Gas.HELIUM, Gas.DEUTERIUM};
            case GRAV_GRAB: return new Gas[]{Gas.HELIUM};
            case TIME_SLOW: return new Gas[]{Gas.KRYPTON, Gas.HELIUM, Gas.SINGULAR_MATTER};
            case BLACK_HOLE: return new Gas[]{Gas.DEUTERIUM, Gas.HELIUM, Gas.SINGULAR_MATTER};
            case GRAV_DOME: return new Gas[]{Gas.HELIUM, Gas.DEUTERIUM};
            case SINGULARITY: return new Gas[]{Gas.SINGULAR_MATTER, Gas.DEUTERIUM};
            case VOID_RESCUE: return new Gas[]{Gas.HELIUM};
            case HEAT_VENT: return new Gas[]{Gas.ARGON};
            default: return new Gas[0];
        }
    }

    /** mB of each of gases(f) a use takes (before К1's x2). */
    public static int[] amounts(ArmorFeature f) {
        switch (f) {
            case PHASE_DASH: return new int[]{ArmorFeature.SING_H2_PHASE};
            case GRAV_PRESS: return new int[]{ArmorFeature.SING_HE_PRESS, ArmorFeature.SING_D_PRESS};
            case GRAV_GRAB: return new int[]{ArmorFeature.SING_HE_GRAB};
            case TIME_SLOW: return new int[]{ArmorFeature.SING_KR_SLOW, ArmorFeature.SING_HE_SLOW, ArmorFeature.SING_SM_SLOW};
            case BLACK_HOLE: return new int[]{ArmorFeature.SING_D_HOLE, ArmorFeature.SING_HE_HOLE, ArmorFeature.SING_SM_HOLE};
            case GRAV_DOME: return new int[]{ArmorFeature.SING_HE_DOME, ArmorFeature.SING_D_DOME};
            case SINGULARITY: return new int[]{ArmorFeature.SING_SM_BOOST, ArmorFeature.SING_D_BOOST};
            case VOID_RESCUE: return new int[]{ArmorFeature.SING_HE_VOID_RESCUE};
            case HEAT_VENT: return new int[]{ArmorFeature.SING_AR_HEAT_VENT};
            default: return new int[0];
        }
    }

    /** The whole cooldown, ticks (the sweep's scale). */
    public static int cooldownTicks(ArmorFeature f) {
        switch (f) {
            case PHASE_DASH: return ArmorFeature.PHASE_DASH_COOLDOWN;
            case GRAV_PRESS: return ArmorFeature.PRESS_COOLDOWN;
            case GRAV_GRAB: return ArmorFeature.GRAB_COOLDOWN;
            case TIME_SLOW: return ArmorFeature.SLOW_COOLDOWN;
            case BLACK_HOLE: return ArmorFeature.HOLE_COOLDOWN;
            case GRAV_DOME: return ArmorFeature.DOME_COOLDOWN;
            case SINGULARITY: return ArmorFeature.BOOST_COOLDOWN;
            case VOID_RESCUE: return ArmorFeature.VOID_RESCUE_COOLDOWN;
            case HEAT_VENT: return ArmorFeature.HEAT_VENT_COOLDOWN;
            default: return 1;
        }
    }

    /** The first gas the worn suit lacks for one use at the К1 multiplier `mul` (client: its synced tanks), null: enough. */
    public static Gas missingGas(ItemStack[] worn, ArmorFeature f, float mul) {
        Gas[] g = gases(f);
        int[] mb = amounts(f);
        for (int i = 0; i < g.length; i++) {
            if (ArmorGasSC.amountOf(worn, g[i]) < (int) Math.ceil(mb[i] * mul)) {
                return g[i];
            }
        }
        return null;
    }

    /** The player has the function: its Singular piece worn, open at the piece's level and branch (creative: all), a set one with all four Singular pieces. */
    public static boolean has(ItemStack[] worn, ArmorFeature f, boolean creative) {
        ItemStack piece = worn == null || f.piece >= worn.length ? null : worn[f.piece];
        if (!SingularLevel.isSingular(piece) || !f.availableIn(ArmorSuit.SINGULAR, f.piece)) {
            return false;
        }
        if (f.needsFullSet()) {
            for (int t = 0; t < 4; t++) {
                if (t >= worn.length || !SingularLevel.isSingular(worn[t])) {
                    return false;
                }
            }
        }
        return SingularProfiles.allowed(f, piece, creative);
    }

    /**
     * Pure: the icon's state at world tick `now`. `end` its cooldown's end tick (0: none), `gasShort`
     * the suit lacks gas for it, `wanted` it's on and has a key (or fires by itself); `activeEnd` /
     * `weakEnd` К1's boost and weakness ends (SINGULARITY) or Н4's slowing (TIME_SLOW, activeEnd).
     */
    public static int stateOf(ArmorFeature f, boolean has, long end, long now, boolean gasShort, boolean wanted, long activeEnd, long weakEnd) {
        if (!has) {
            return HIDDEN;
        }
        if (f == ArmorFeature.SINGULARITY && activeEnd > now) {
            return BOOST;
        }
        if (f == ArmorFeature.SINGULARITY && weakEnd > now) {
            return WEAK;
        }
        if (f == ArmorFeature.TIME_SLOW && activeEnd > now) {
            return RUNNING;
        }
        if (end > now) {
            return COOLING;
        }
        if (gasShort && wanted) {
            return NOGAS;
        }
        if (end > 0 && now - end < READY_TICKS) {
            return gasShort ? NOGAS : READY;
        }
        return HIDDEN;
    }

    /** Pure: the indexes of the shown icons, in order, from their states. */
    public static List<Integer> shown(int[] states) {
        List<Integer> out = new ArrayList<Integer>();
        for (int i = 0; states != null && i < states.length; i++) {
            if (states[i] != HIDDEN) {
                out.add(i);
            }
        }
        return out;
    }

    /** Pure: the sweep's share still dark - ticks left of `total`, 0..1. */
    public static float sweep(long end, long now, int total) {
        if (end <= now || total <= 0) {
            return 0F;
        }
        return Math.min(1F, (end - now) / (float) total);
    }

    /** Pure: "18" seconds under a minute, "2:33" from a minute, from ticks (rounded up). */
    public static String time(int ticks, String secFormat) {
        int s = (Math.max(0, ticks) + 19) / 20;
        if (s < 60) {
            return String.format(java.util.Locale.ROOT, secFormat, s);
        }
        return s / 60 + ":" + (s % 60 < 10 ? "0" : "") + s % 60;
    }
}
