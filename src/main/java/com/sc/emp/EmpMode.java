package com.sc.emp;

/**
 * ПЭ-1 «Подавитель электроники» (docs/plan-pe1.md §1): the gun's three modes. Ordinals are saved
 * (gun NBT "EmpMode", the "EmpOff" bit mask, key binds) - only ever append.
 */
public enum EmpMode {

    /** Точка: one piece (the zone hit), 1 s charge-up, 50 000 EU, range 32. */
    POINT(110, 215, 255),
    /** Импульс: the whole suit, 3 s charge-up (ПЭ-1М 2 s), 150 000 EU, 20 s cooldown, range 32. */
    PULSE(186, 132, 255),
    /** Сифон: hold right-click, a stream pulls the target suit's EU to the shooter; 5 000 EU a start, range 16 (Focus 24). */
    SIPHON(130, 236, 100);

    /** The capsule's / beam's colour (§10). */
    public final int r, g, b;

    EmpMode(int r, int g, int b) {
        this.r = r;
        this.g = g;
        this.b = b;
    }

    /** 0xRRGGBB. */
    public int rgb() {
        return (r << 16) | (g << 8) | b;
    }

    /** Lang / config / sound key part: "point", "pulse", "siphon". */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The mode of a saved ordinal; anything out of range: POINT. */
    public static EmpMode of(int ordinal) {
        EmpMode[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : POINT;
    }
}
