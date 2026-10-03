package com.sc.client;

import com.sc.manual.Lang;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;

import net.minecraft.client.Minecraft;
import net.minecraft.entity.player.EntityPlayer;

/**
 * Client side of the suit's life support (docs/plan-armor-gases.md): the gases' short and full
 * names, and an estimate of how fast each gas goes - sampled once a second from what the server
 * syncs into the worn pieces, over the last minute of game time (paused time doesn't count).
 * A refill or another suit starts the count again.
 */
public final class GasUiSC {

    /** Chemical labels, used when the lang file has no "sc.gas.<key>.short". */
    private static final String[] SHORT = {"He", "O2", "H2", "Ar", "Kr", "D2O", "D", "SM"};

    /** Samples: once a second, up to a minute back. */
    private static final int EVERY = 20, KEEP = 61, MIN_SPAN = 100;
    private static final long[][] TIME = new long[Gas.values().length][KEEP];
    private static final int[][] AMOUNT = new int[Gas.values().length][KEEP];
    private static final int[] COUNT = new int[Gas.values().length];
    private static final int[] HEAD = new int[Gas.values().length];
    private static final int[] LAST_CAP = new int[Gas.values().length];
    private static long lastSample = Long.MIN_VALUE;

    private GasUiSC() {
    }

    public static String shortName(Gas g) {
        return Lang.trOr("sc.gas." + g.key() + ".short", g.ordinal() < SHORT.length ? SHORT[g.ordinal()] : g.key());
    }

    public static String name(Gas g) {
        return Lang.trOr("sc.gas." + g.key(), shortName(g));
    }

    /** Called every client tick (ArmorClientSC). */
    public static void tick(Minecraft mc) {
        EntityPlayer p = mc.thePlayer;
        if (p == null || mc.theWorld == null) {
            return;
        }
        long now = mc.theWorld.getTotalWorldTime();
        if (now < lastSample) {                       // another world: its clock
            reset();
        }
        if (lastSample != Long.MIN_VALUE && now - lastSample < EVERY) {
            return;
        }
        lastSample = now;
        for (Gas g : Gas.values()) {
            int i = g.ordinal();
            int cap = ArmorGasSC.suitCapacity(p, g);
            int amt = ArmorGasSC.suitAmount(p, g);
            if (cap != LAST_CAP[i] || cap <= 0 || COUNT[i] > 0 && amt > AMOUNT[i][(HEAD[i] + KEEP - 1) % KEEP]) {
                COUNT[i] = 0;                         // other tanks, or a refill: start over
                LAST_CAP[i] = cap;
            }
            if (cap <= 0) {
                continue;
            }
            TIME[i][HEAD[i]] = now;
            AMOUNT[i][HEAD[i]] = amt;
            HEAD[i] = (HEAD[i] + 1) % KEEP;
            COUNT[i] = Math.min(KEEP, COUNT[i] + 1);
        }
    }

    private static void reset() {
        for (int i = 0; i < COUNT.length; i++) {
            COUNT[i] = 0;
            LAST_CAP[i] = 0;
        }
        lastSample = Long.MIN_VALUE;
    }

    /** Use of `g`, mB a minute, over the samples kept; -1 while there are too few to tell. */
    public static float perMinute(Gas g) {
        int i = g.ordinal();
        if (COUNT[i] < 2) {
            return -1F;
        }
        int newest = (HEAD[i] + KEEP - 1) % KEEP;
        int oldest = (HEAD[i] + KEEP - COUNT[i]) % KEEP;
        long span = TIME[i][newest] - TIME[i][oldest];
        if (span < MIN_SPAN) {
            return -1F;
        }
        int used = AMOUNT[i][oldest] - AMOUNT[i][newest];
        return Math.max(0, used) * 1200F / span;
    }

    /** "x mB/min, enough for ~y min" for the screen. */
    public static String usage(EntityPlayer p, Gas g) {
        float rate = perMinute(g);
        if (rate < 0) {
            return Lang.tr("sc.lifegui.rate.wait");
        }
        if (rate < 0.05F) {
            return Lang.tr("sc.lifegui.rate.none");
        }
        String r = rate >= 10 ? String.valueOf(Math.round(rate)) : String.format(java.util.Locale.ROOT, "%.1f", rate);
        int amount = ArmorGasSC.suitAmount(p, g);
        int minutes = (int) Math.min(Integer.MAX_VALUE, amount / rate);
        String left = minutes >= 120 ? Lang.tr("sc.lifegui.left.h", minutes / 60) : Lang.tr("sc.lifegui.left.min", minutes);
        return Lang.tr("sc.lifegui.rate", r, left);
    }

    /** Blinking state for a low tank (shared by the HUD and the screen). */
    public static boolean blinkOff() {
        return System.currentTimeMillis() / 400L % 2L == 0L;
    }
}
