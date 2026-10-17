package com.sc.settle;

/**
 * Подложка Ф2 (docs/idea-dimension-substrate.md §5 «Размеры поселений», docs/plan-substrate-tech-f2.md §2.3): the three
 * sizes. The ordinal is saved (SettlementRegistrySC.Settlement.size): append only. Numbers of the proposal; the City's
 * Captain and Commandant come in Ф3 (their slots are added to the cities then). Lang: sc.settle.size.<key>.
 * Shared core: only the coordinator edits this file.
 */
public enum SettlementSizeSC {

    /** «Пост»: dome 24, 3..5 Tuners, 2 Sentries, no dock. */
    OUTPOST("outpost", 24, 24, 3, 5, 2, 0, false),
    /** «Посёлок»: dome 40, 8..12 Tuners, 3 Sentries + 2 Senior Wardens, a dock at the gate. */
    SETTLEMENT("settlement", 40, 40, 8, 12, 3, 2, true),
    /** «Город» (one to 3..4 Settlements): dome 56..64, 15..20 Tuners, 4 Sentries + 3 Senior Wardens, a dock. */
    CITY("city", 56, 64, 15, 20, 4, 3, true);

    public final String key;
    public final int radiusMin, radiusMax, tunersMin, tunersMax, sentries, seniors;
    /** A «Причал» (dock with its beacon and protected zone) stands outside the gate. */
    public final boolean dock;

    SettlementSizeSC(String key, int radiusMin, int radiusMax, int tunersMin, int tunersMax, int sentries, int seniors, boolean dock) {
        this.key = key;
        this.radiusMin = radiusMin;
        this.radiusMax = radiusMax;
        this.tunersMin = tunersMin;
        this.tunersMax = tunersMax;
        this.sentries = sentries;
        this.seniors = seniors;
        this.dock = dock;
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
