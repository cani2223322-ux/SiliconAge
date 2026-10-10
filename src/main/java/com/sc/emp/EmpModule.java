package com.sc.emp;

/**
 * ПЭ-1 modules (docs/plan-pe1.md §5): one of each kind fits; installed ones are bits of the gun's
 * "EmpMods" NBT, the item is ItemEmpModuleSC with meta = ordinal. Ordinals are saved - only ever append.
 */
public enum EmpModule {

    /** Ёмкость: buffer x2 (600 000 -> 1 200 000 EU). */
    CAPACITY,
    /** Фокус: Point range +EmpMathSC.FOCUS_POINT_BONUS, Siphon +EmpMathSC.FOCUS_SIPHON_BONUS (16 -> 24). */
    FOCUS,
    /** Охлаждение: the gun heats 30% less. */
    COOLING,
    /** Эффективный сифон: siphon loss 25% -> 10%. */
    EFFICIENT,
    /** Широкий канал: siphon up to IV, 8 192 EU/t. */
    WIDE;

    public int bit() {
        return 1 << ordinal();
    }

    /** Lang / texture key part: "capacity", "focus", "cooling", "efficient", "wide". */
    public String key() {
        return name().toLowerCase(java.util.Locale.ROOT);
    }

    /** The module of a meta / ordinal, or null. */
    public static EmpModule of(int ordinal) {
        EmpModule[] v = values();
        return ordinal >= 0 && ordinal < v.length ? v[ordinal] : null;
    }
}
