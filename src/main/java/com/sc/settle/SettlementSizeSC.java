package com.sc.settle;

/**
 * Подложка Ф2 (docs/idea-dimension-substrate.md §5 «Размеры поселений», docs/plan-substrate-tech-f2.md §2.3): the three
 * sizes. The ordinal is saved (SettlementRegistrySC.Settlement.size): append only. Numbers of the proposal. Ф3
 * (docs/plan-substrate-tech-f3.md §6.2): the City's Captain and Commandant, a Settlement's Captain at CAPTAIN_PCT (by its
 * seed) - a Ф2 record gets their slots appended (RosterSC, Settlement.version). Lang: sc.settle.size.<key>.
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §2.4, rule ПЕ-5): EMBASSY, ordinal 3 - the Tuners' Embassy in the Overworld (dome 88..96,
 * 18..22 Tuners, 6 Sentries + 4 Senior Wardens + the Captain + the Commandant, the Vokzal for its dock). The Substrate's generator
 * never picks it (SettlementLayoutSC.SIZE_WEIGHTS holds three; RosterSC.sizeOf never answers it); tables of the Substrate's sizes
 * (the raids', the caravans') stay three long - substrate() tells them apart. Shared core: only the coordinator edits this file.
 */
public enum SettlementSizeSC {

    /** «Пост»: dome 24, 3..5 Tuners, 2 Sentries, no dock. */
    OUTPOST("outpost", 24, 24, 3, 5, 2, 0, false, 0, false),
    /** «Посёлок»: dome 40, 8..12 Tuners, 3 Sentries + 2 Senior Wardens (Ф3: a Captain at 30%), a dock at the gate. */
    SETTLEMENT("settlement", 40, 40, 8, 12, 3, 2, true, 30, false),
    /** «Город» (one to 3..4 Settlements): dome 56..64, 15..20 Tuners, 4 Sentries + 3 Senior Wardens (Ф3: + the Captain, the Commandant), a dock. */
    CITY("city", 56, 64, 15, 20, 4, 3, true, 100, true),
    // ---- Ф6 (appended)
    /** «Посольство Наладчиков» (the Overworld): dome 88..96, 18..22 Tuners, 6 Sentries + 4 Senior Wardens + the Captain + the Commandant, the Vokzal. */
    EMBASSY("embassy", 88, 96, 18, 22, 6, 4, true, 100, true);

    /** The sizes the Substrate's generator makes (OUTPOST, SETTLEMENT, CITY) - the tables indexed by a Substrate size are this long. */
    public static final int SUBSTRATE_SIZES = 3;

    public final String key;
    public final int radiusMin, radiusMax, tunersMin, tunersMax, sentries, seniors;
    /** A «Причал» (dock with its beacon and protected zone) stands outside the gate. */
    public final boolean dock;
    /** Ф3 (ПГ-11, ПГ-12): the chance of a Captain, %, by the settlement's seed (captain()); a Commandant (the City). TODO(balance) */
    public final int captainPct;
    public final boolean commandant;

    SettlementSizeSC(String key, int radiusMin, int radiusMax, int tunersMin, int tunersMax, int sentries, int seniors, boolean dock,
                     int captainPct, boolean commandant) {
        this.key = key;
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
        this.tunersMin = tunersMin;
        this.tunersMax = tunersMax;
        this.sentries = sentries;
        this.seniors = seniors;
        this.dock = dock;
        this.captainPct = captainPct;
        this.commandant = commandant;
    }

    /** Ф3: a settlement of this size and seed has a Captain (pure: the same answer for the plan and for a Ф2 record's upgrade). */
    public boolean captain(long seed) {
        return captainPct >= 100 || captainPct > 0 && (int) (((seed >>> 23) & 0x7FFFFFFFL) % 100) < captainPct;
    }

    /** The dome's radius for a settlement of this seed: radiusMin..radiusMax (pure). */
    public int radius(long seed) {
        int span = radiusMax - radiusMin + 1;
        return radiusMin + (int) (((seed >>> 17) & 0x7FFFFFFFL) % span);
    }

    /** The guards of this size, both ranks. */
    public int guards() {
        return sentries + seniors;
    }

    /** Ф6: one of the Substrate's own sizes (not the Overworld's EMBASSY). */
    public boolean substrate() {
        return ordinal() < SUBSTRATE_SIZES;
    }

    /** The size of an ordinal (an unknown one: OUTPOST). */
    public static SettlementSizeSC of(int ordinal) {
        SettlementSizeSC[] all = values();
        return all[ordinal >= 0 && ordinal < all.length ? ordinal : 0];
    }

    /** sc.settle.size.<key>. */
    public String langKey() {
        return "sc.settle.size." + key;
    }
}
