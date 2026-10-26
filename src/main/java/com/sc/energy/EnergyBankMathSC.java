package com.sc.energy;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md, «Сингулярный энергобанк», ★ A): the Energy Bank's numbers and pure decisions - the coordinator's
 * names and signatures, ОМ-7's bodies and TODO(balance) numbers.
 * <ul>
 * <li>a hollow cube of SIZE_MIN..SIZE_MAX (odd: 3, 5, 7) a side: its edges casing, its faces casing / glass / ports / one controller, its
 * whole inside cells (4 or 16 billion EU each - CELL_EU) - any mix;</li>
 * <li>the capacity is the cells' sum (a long: a 7 x 7 x 7 of 125 sixteen-billion cells holds 2 trillion), x ConfigSC.storageCapacity;</li>
 * <li>a port (LV..SV, its tier by meta) moves at most its tier's voltage x PORT_AMPS a tick, in or out (its mode);</li>
 * <li>the controller keeps HISTORY seconds of the inflow and the outflow (EU a tick, averaged over a second) - the screen's graph.</li>
 * </ul>
 */
public final class EnergyBankMathSC {

    /** The cube's side: 3..7, odd. */
    public static final int SIZE_MIN = 3, SIZE_MAX = 7;
    /** A cell's EU by kind (BlockEnergyBankSC.CELL_4 / CELL_16). TODO(balance) */
    public static final long[] CELL_EU = {4000000000L, 16000000000L};
    /** A port moves its tier's voltage x this a tick. TODO(balance) */
    public static final int PORT_AMPS = 4;
    /** The graph's length, seconds. */
    public static final int HISTORY = 60;

    private EnergyBankMathSC() {
    }

    /** Pure: a side of `n` blocks makes a bank (odd, SIZE_MIN..SIZE_MAX). */
    public static boolean validSize(int n) {
        return n >= SIZE_MIN && n <= SIZE_MAX && (n & 1) == 1;
    }

    /** Pure: the cells inside a cube of side `n` - (n - 2) cubed; 0 for a size that isn't one. */
    public static int cellSlots(int n) {
        return validSize(n) ? (n - 2) * (n - 2) * (n - 2) : 0;
    }

    /** Pure: the capacity of `small` 4-billion and `big` 16-billion cells (before ConfigSC.storageCapacity). */
    public static long capacity(int small, int big) {
        return Math.max(0, small) * CELL_EU[0] + Math.max(0, big) * CELL_EU[1];
    }

    /** Pure: what a port of tier `t` moves a tick at most. */
    public static long portRate(Tier t) {
        return t == null ? 0L : (long) t.getVoltage() * PORT_AMPS;
    }

    // ------------------------------------------------------------------ ОМ-7: the structure's check

    /** A block's kind for check(): not a bank block, then BlockEnergyBankSC's metas (a port: PORT_FIRST + the tier's ordinal). */
    public static final int K_NONE = -1, K_CASING = 0, K_GLASS = 1, K_CONTROLLER = 2, K_CELL_4 = 3, K_CELL_16 = 4, K_PORT_FIRST = 5,
            K_PORT_END = K_PORT_FIRST + Tier.values().length;
    /** check()'s answer: good, else a reason (badReason) at a block (badIndex). */
    public static final int OK = -1, R_EDGE = 1, R_FACE = 2, R_INSIDE = 3, R_SIZE = 4, R_CONTROLLERS = 5;
    /** The reasons' lines: "sc.gui.bank.reason." + REASON_KEYS[reason]. */
    public static final String[] REASON_KEYS = {null, "edge", "face", "inside", "size", "controllers"};

    /** The blocks of a cube by index x + n * (y + n * z) - an array (the self-test) or the world, read as asked (the controller). */
    public abstract static class Kinds {
        public abstract int at(int index);
    }

    /** Pure: is `kind` a port's. */
    public static boolean isPort(int kind) {
        return kind >= K_PORT_FIRST && kind < K_PORT_END;
    }

    /** Pure: how many of x, y, z lie on the cube's sides (0 inside, 1 a face, 2-3 an edge / a corner). */
    public static int boundaries(int n, int x, int y, int z) {
        return (x == 0 || x == n - 1 ? 1 : 0) + (y == 0 || y == n - 1 ? 1 : 0) + (z == 0 || z == n - 1 ? 1 : 0);
    }

    /** Pure: check() of an array of kinds (index x + n * (y + n * z)). */
    public static int check(int n, final int[] kinds) {
        return check(n, new Kinds() {
            @Override
            public int at(int index) {
                return kinds != null && index >= 0 && index < kinds.length ? kinds[index] : K_NONE;
            }
        });
    }

    /**
     * Pure: is the cube of side `n` a bank - the edges casing, the faces casing / glass / ports / exactly one controller, the whole inside
     * cells (any mix). OK, else code(index, reason) of the first wrong block in index order (read in that order and no further).
     */
    public static int check(int n, Kinds k) {
        if (!validSize(n)) {
            return code(0, R_SIZE);
        }
        int controllers = 0;
        for (int z = 0; z < n; z++) {
            for (int y = 0; y < n; y++) {
                for (int x = 0; x < n; x++) {
                    int i = x + n * (y + n * z), b = boundaries(n, x, y, z), kind = k.at(i);
                    if (b >= 2) {
                        if (kind != K_CASING) {
                            return code(i, R_EDGE);
                        }
                    } else if (b == 1) {
                        if (kind == K_CONTROLLER) {
                            if (++controllers > 1) {
                                return code(i, R_CONTROLLERS);
                            }
                        } else if (kind != K_CASING && kind != K_GLASS && !isPort(kind)) {
                            return code(i, R_FACE);
                        }
                    } else if (kind != K_CELL_4 && kind != K_CELL_16) {
                        return code(i, R_INSIDE);
                    }
                }
            }
        }
        return controllers == 1 ? OK : code(0, R_CONTROLLERS);
    }

    public static int code(int index, int reason) {
        return index * 8 + reason;
    }

    public static int badIndex(int code) {
        return code < 0 ? -1 : code >> 3;
    }

    public static int badReason(int code) {
        return code < 0 ? 0 : code & 7;
    }

    /** Pure: the capacity with the server's storageCapacity multiplier (no overflow: at most 2 x 10^12 x 10). */
    public static long scaledCapacity(int small, int big, float mul) {
        return (long) (capacity(small, big) * (double) Math.max(0F, mul));
    }
}
