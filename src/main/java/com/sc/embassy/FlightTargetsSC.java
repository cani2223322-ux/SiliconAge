package com.sc.embassy;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.bridge.BridgeMathSC;
import com.sc.dim.SubstrateSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SettlementSizeSC;
import com.sc.settle.gen.SettlementLayoutSC;
import com.sc.settle.npc.NpcNamesSC;

/**
 * PACKAGE R (docs/plan-substrate-tech-f6.md §7.2, §7.5, rules ПЯ-1, ПЯ-4..ПЯ-9, ПР-8): where each ride is sold and where it goes -
 * the server's answers from the global registry (the screen's rows, the purchase, the carry ask the same).
 * <ul>
 * <li>place: an embassy (its Vokzal's Dispatchers sell INTRO, FIRST, DOCK, RESCUE - out into the Substrate), a Substrate dock with a
 * Dispatcher (a Settlement's, a City's, «Первый причал»'s: HOME and BACK; a City's also OTHER), else nothing (an unbound
 * Dispatcher of a spawn egg, a record gone);</li>
 * <li>«Первый причал» (ПЯ-5): its record; none and the Substrate's region (0, 0) never opened - the pure plan's dock
 * (SettlementLayoutSC.plan of site(seed, 0, 0): the carry generates the chunk and the record comes with it - TARGET_FIRST_PURE; not
 * with the settlements off - dimension.villages: nothing would be built there); the region opened before Ф2 (no record will ever be
 * made there) - the dock nearest (0, 0); neither - NO_TARGET («Канал не найден»);</li>
 * <li>DOCK - a dock the player knows in the Substrate (PlayerRec.knownDocks: not «Первый причал» - FIRST goes there, not an
 * embassy's); RESCUE - the death mark in the Substrate, scattered by RESCUE_SCATTER; HOME / BACK / OTHER - an embassy's Vokzal
 * (its dock's beacon). Every point is a beacon + 1 (SafeSpotSC finds the place round it).</li>
 * </ul>
 * Server thread only (the pure plan's cache).
 */
public final class FlightTargetsSC {

    /** The places a Dispatcher sells at (FLIGHT_STATE "place"). */
    public static final int PLACE_NONE = -1, PLACE_EMBASSY = 0, PLACE_DOCK = 1;
    /** No target (a ride listed closed: no dock known, no mark, no embassy to go back to). */
    public static final int NO_TARGET = -1;
    /** A ride's rows on one screen at most (the known docks, the embassies seen). */
    public static final int MAX_ROWS = 24;
    /** SafeSpotSC's search round a target point (it loads the chunks). */
    public static final int SAFE_RADIUS = 8;

    /** The pure plan of «Первый причал» and the world seed it is of. */
    private static SettlementLayoutSC.Plan purePlan;
    private static long pureSeed;

    private FlightTargetsSC() {
    }

    // ------------------------------------------------------------------ where each ride is sold

    /** The place `s` is to the flights (PLACE_*). Pure but for SubstrateSC's id. */
    public static int place(SettlementRegistrySC.Settlement s) {
        if (s == null || !s.hasDock) {
            return PLACE_NONE;
        }
        if (SettlementSC.isEmbassy(s)) {
            return s.dim == SettlementSC.EMBASSY_DIM ? PLACE_EMBASSY : PLACE_NONE;
        }
        return SettlementSC.inSubstrate(s) && s.sizeKind().substrate() ? PLACE_DOCK : PLACE_NONE;
    }

    /** ПЯ-1, ПЯ-8, ПЯ-9: ride `ride` is sold at `s` (its Dispatcher's screen lists it). */
    public static boolean sells(SettlementRegistrySC.Settlement s, int ride) {
        int place = place(s);
        if (place == PLACE_EMBASSY) {
            return EmbassyRulesSC.outbound(ride);
        }
        if (place == PLACE_DOCK) {
            return ride == EmbassyRulesSC.RIDE_HOME || ride == EmbassyRulesSC.RIDE_BACK
                    || ride == EmbassyRulesSC.RIDE_OTHER && s.size == SettlementSizeSC.CITY.ordinal();
        }
        return false;
    }

    /** The rides sold at `s`, in their order. */
    public static List<Integer> rides(SettlementRegistrySC.Settlement s) {
        List<Integer> out = new ArrayList<Integer>();
        for (int r = 0; r < EmbassyRulesSC.RIDE_KEYS.length; r++) {
            if (sells(s, r)) {
                out.add(Integer.valueOf(r));
            }
        }
        return out;
    }

    // ------------------------------------------------------------------ the targets

    /** ПЯ-5: «Первый причал»'s target now - its record's id, TARGET_FIRST_PURE, the dock nearest (0, 0), or NO_TARGET. */
    public static int firstTarget(SettlementRegistrySC reg) {
        if (reg == null || !SubstrateSC.active()) {
            return NO_TARGET;
        }
        int dim = SubstrateSC.dimId();
        SettlementRegistrySC.Settlement f = reg.first(dim);
        if (f != null && f.hasDock) {
            return f.id;
        }
        if (!EmbassySiteSC.regionFileExists(dim, 0, 0) && SettlementSC.generates()) {
            return EmbassyRulesSC.TARGET_FIRST_PURE;            // never opened: the carry makes it (the chunk and its record)
        }
        List<SettlementRegistrySC.Settlement> near = reg.nearestDocks(dim, 0, 0, 1, -1);
        return near.isEmpty() ? NO_TARGET : near.get(0).id;  // opened before Ф2: the dock nearest the world's middle
    }

    /** The pure plan of «Первый причал» of world seed `seed` (cached: a few milliseconds once a server), null none. */
    public static SettlementLayoutSC.Plan firstPure(long seed) {
        if (purePlan == null || pureSeed != seed) {
            SettlementLayoutSC.Site site = SettlementLayoutSC.site(seed, 0, 0);
            purePlan = site == null ? null : SettlementLayoutSC.plan(seed, site);
            pureSeed = seed;
        }
        return purePlan;
    }

    /** The docks `rec` knows that DOCK may go to (and those `also` knows - a guarantor's, null none), in the order learnt. */
    public static List<SettlementRegistrySC.Settlement> knownDocks(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec,
                                                                   SettlementRegistrySC.PlayerRec also) {
        List<SettlementRegistrySC.Settlement> out = new ArrayList<SettlementRegistrySC.Settlement>();
        for (SettlementRegistrySC.PlayerRec r : new SettlementRegistrySC.PlayerRec[]{rec, also}) {
            if (r == null) {
                continue;
            }
            for (Integer id : r.knownDocks) {
                SettlementRegistrySC.Settlement s = reg.byId(id.intValue());
                if (dockTarget(s) && !out.contains(s) && out.size() < MAX_ROWS) {
                    out.add(s);
                }
            }
        }
        return out;
    }

    /** `s` is a dock DOCK flies to: a Substrate settlement's, not «Первый причал» (FIRST's), not an embassy's. */
    public static boolean dockTarget(SettlementRegistrySC.Settlement s) {
        return s != null && s.hasDock && !s.first && SettlementSC.inSubstrate(s) && !SettlementSC.isEmbassy(s);
    }

    /** ПЯ-9: the embassies OTHER may go to - those `rec` has been to, but the one he flew out of (HOME goes there). */
    public static List<SettlementRegistrySC.Settlement> otherEmbassies(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec) {
        List<SettlementRegistrySC.Settlement> out = new ArrayList<SettlementRegistrySC.Settlement>();
        int from = EmbassyTicketSC.from(rec);
        for (int id : EmbassyTicketSC.seen(rec)) {
            SettlementRegistrySC.Settlement s = reg.byId(id);
            if (id != from && embassy(s) && out.size() < MAX_ROWS) {
                out.add(s);
            }
        }
        return out;
    }

    /** `s` is an embassy a flight home can land at (its Vokzal's beacon). */
    public static boolean embassy(SettlementRegistrySC.Settlement s) {
        return s != null && SettlementSC.isEmbassy(s) && s.hasDock;
    }

    /** HOME: the embassy `rec` flew out of, if it still stands (-1 none). */
    public static int homeTarget(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec) {
        int id = EmbassyTicketSC.from(rec);
        return id >= 0 && embassy(reg.byId(id)) ? id : NO_TARGET;
    }

    /** BACK: the return half's embassy while it is good at `now` (-1 none). */
    public static int backTarget(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec, long now) {
        int id = EmbassyTicketSC.backEmbassy(rec);
        return id >= 0 && EmbassyRulesSC.backValid(EmbassyTicketSC.backUntil(rec), now) && embassy(reg.byId(id)) ? id : NO_TARGET;
    }

    /** RESCUE: `rec`'s death mark in the Substrate may be flown to at `now` (fresh, not rescued yet). */
    public static boolean rescueOpen(SettlementRegistrySC.PlayerRec rec, long now) {
        return rec != null && rec.hasDeath && SubstrateSC.isDim(rec.deathDim)
                && EmbassyRulesSC.rescuable(true, rec.deathAt, now, EmbassyTicketSC.rescuedAt(rec));
    }

    /**
     * Talon `k`'s landing (the passenger's record `rec`): {dim, x, y, z} - a beacon + 1 (the column's middle), the death mark scattered
     * by RESCUE_SCATTER; null - the channel is lost (the record gone, the Substrate off). `seed` - the world seed (the pure plan).
     */
    public static double[] point(SettlementRegistrySC reg, SettlementRegistrySC.PlayerRec rec, EmbassyTicketSC.Talon k, long seed, Random rand) {
        if (reg == null || k == null || !SubstrateSC.active()) {
            return null;
        }
        int sub = SubstrateSC.dimId();
        switch (k.ride) {
            case EmbassyRulesSC.RIDE_INTRO:
            case EmbassyRulesSC.RIDE_FIRST: {
                SettlementRegistrySC.Settlement f = reg.first(sub);
                if (f == null && k.target >= 0) {
                    f = reg.byId(k.target);                     // the dock nearest (0, 0) sold for a region opened before Ф2
                }
                if (f != null && f.hasDock && SettlementSC.inSubstrate(f)) {
                    return beacon(f);
                }
                if (k.target == EmbassyRulesSC.TARGET_FIRST_PURE) {
                    SettlementLayoutSC.Plan p = firstPure(seed);
                    return p == null || !p.dock ? null : new double[]{sub, p.dockX + 0.5D, p.dockY + 1, p.dockZ + 0.5D};
                }
                return null;
            }
            case EmbassyRulesSC.RIDE_DOCK: {
                SettlementRegistrySC.Settlement s = reg.byId(k.target);
                return dockTarget(s) ? beacon(s) : null;
            }
            case EmbassyRulesSC.RIDE_RESCUE: {
                if (rec == null || !SubstrateSC.isDim(rec.deathDim)) {
                    return null;
                }
                int[] off = BridgeMathSC.scatterOffset(rand == null ? new Random() : rand, EmbassyRulesSC.RESCUE_SCATTER);
                return new double[]{rec.deathDim, rec.deathX + off[0] + 0.5D, rec.deathY, rec.deathZ + off[1] + 0.5D};
            }
            case EmbassyRulesSC.RIDE_HOME:
            case EmbassyRulesSC.RIDE_BACK:
            case EmbassyRulesSC.RIDE_OTHER: {
                SettlementRegistrySC.Settlement s = reg.byId(k.target);
                return embassy(s) ? beacon(s) : null;
            }
            default:
                return null;
        }
    }

    /** {dim, x, y, z} over a record's beacon (+ 1). */
    private static double[] beacon(SettlementRegistrySC.Settlement s) {
        return new double[]{s.dim, s.dockX + 0.5D, s.dockY + 1, s.dockZ + 0.5D};
    }

    // ------------------------------------------------------------------ names (the screen)

    /**
     * The name code of a ride's target (NpcNamesSC.code; 0 - a mark or none) and whether it is an embassy's: {code, embassy 0/1}.
     * The pure «Первый причал» - its plan's name (the record will get the same).
     */
    public static int[] name(SettlementRegistrySC reg, int ride, int target, long seed) {
        if (target == EmbassyRulesSC.TARGET_FIRST_PURE) {
            SettlementLayoutSC.Plan p = firstPure(seed);
            return new int[]{p == null ? 0 : NpcNamesSC.code(p.nameElement, p.nameNumber), 0};
        }
        SettlementRegistrySC.Settlement s = target >= 0 && reg != null ? reg.byId(target) : null;
        if (s == null) {
            return new int[]{0, 0};
        }
        return new int[]{NpcNamesSC.code(s.nameElement, s.nameNumber), SettlementSC.isEmbassy(s) ? 1 : 0};
    }

    /** Server stopped: the pure plan goes (the next world has another seed). */
    public static void clearAll() {
        purePlan = null;
        pureSeed = 0L;
    }
}
