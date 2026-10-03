package com.sc.bridge;

/**
 * The client's copy of the opener's portal (BridgeNetSC.Hud) - common code, so the message handler touches no
 * client class: «Мост «Имя»: портал открыт · NN с · стабильность NN%» (client/BridgeHudSC draws it).
 */
public final class BridgeHudDataSC {

    private BridgeHudDataSC() {
    }

    public static volatile boolean open;
    public static volatile String name = "";
    public static volatile int left, total, stability, kind;
    /** System time of the last message (the line goes when they stop - another world, the server gone). */
    public static volatile long at;

    public static void set(boolean o, String n, int l, int t, int s, int k) {
        open = o;
        name = n == null ? "" : n;
        left = l;
        total = t;
        stability = s;
        kind = k;
        at = System.currentTimeMillis();
    }

    /** Stage 3: the ring's heat (%), seconds to a fold for a shortage (-1 none), what is short. */
    public static volatile int heat, warn = -1;
    public static volatile String shortWhat = "";

    public static void setStage3(int h, int w, String what) {
        heat = h;
        warn = w;
        shortWhat = what == null ? "" : what;
    }

    /** §11 births heard (x, y, z, kind) - the client's tick takes them (the network thread spawns nothing). */
    public static final java.util.concurrent.ConcurrentLinkedQueue<double[]> BIRTHS = new java.util.concurrent.ConcurrentLinkedQueue<double[]>();

    public static void birth(double x, double y, double z, int kind) {
        if (BIRTHS.size() < 16) {
            BIRTHS.add(new double[]{x, y, z, kind});
        }
    }

    /** Shown: open and heard from within the last 3 s. */
    public static boolean shown() {
        return open && System.currentTimeMillis() - at < 3000;
    }
}
