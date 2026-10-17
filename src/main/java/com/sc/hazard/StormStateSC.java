package com.sc.hazard;

import net.minecraft.world.World;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §5.1, rules ПФ-1..ПФ-4): the EMP storm's phase as both sides see it - the
 * server's truth lives in StormDataSC (DIM-1414/data/SCSi14Weather.dat, package S); a client keeps the copy the last
 * HazardNetSC.STORM_STATE gave it, here, for the sky (WorldProviderSubstrateSC tints its sky and fog by skyFactor), the
 * scan lines, the Wireweed's sparks and the HUD line. No client class is touched: common code may read it on either side.
 * <p>
 * Phases (fixed): CALM; WARNING - the 30 s before a storm (the domes' siren, the sky turning violet); STORM - the pulses.
 * <p>
 * The same for the two other weathers of Ф4 (§5.5, §5.6): the Silica Haze (HAZE_STATE - hazeFactor: the fog thickens over
 * HAZE_FADE_TICKS and thins again; package W's SubstrateClientSC draws it) and the Singular Emission (EMISSION_STATE - in the
 * Rift: the HUD line, a tint of W's fog).
 * Shared core: only the coordinator edits this file.
 */
public final class StormStateSC {

    public static final int CALM = 0, WARNING = 1, STORM = 2;
    /** A client's tint fades in over the warning, out over this many ticks after the storm. */
    public static final int FADE_OUT_TICKS = 100;

    /** Client: the phase the server last sent, the client's world time it ends at, the time it was set; the last pulse's. */
    private static volatile int clientPhase = CALM;
    private static volatile long clientEndsAt, clientSetAt, clientPulseAt = Long.MIN_VALUE / 2, clientCalmSince = Long.MIN_VALUE / 2;
    /** The warning's length the server sent (ticks) - the tint grows over it. */
    private static volatile int clientWarnTicks = 600;
    /** The haze's fog grows / thins over this many ticks. */
    public static final int HAZE_FADE_TICKS = 200;
    /** Client: the haze - on, the client time it ends at, since when it's on / off; the emission - on, its end. */
    private static volatile boolean clientHaze, clientEmission;
    private static volatile long clientHazeEndsAt, clientHazeSince = Long.MIN_VALUE / 2, clientEmissionEndsAt;

    private StormStateSC() {
    }

    /**
     * Client (any thread - the message handler): the server's phase, the ticks until it ends and the warning's length.
     * `now` - the client world's total time when it came.
     */
    public static void clientSet(int phase, int ticksLeft, int warnTicks, long now) {
        if (clientPhase != CALM && phase == CALM) {
            clientCalmSince = now;
        }
        clientPhase = phase;
        clientEndsAt = now + Math.max(0, ticksLeft);
        clientSetAt = now;
        clientWarnTicks = Math.max(1, warnTicks);
    }

    /** Client: a pulse just went off (the flash, the crackle). */
    public static void clientPulse(long now) {
        clientPulseAt = now;
    }

    /** Client: the haze on (`ticksLeft` more) or off, at client time `now`. */
    public static void clientHaze(boolean on, int ticksLeft, long now) {
        if (on != clientHaze) {
            clientHazeSince = now;
        }
        clientHaze = on;
        clientHazeEndsAt = now + Math.max(0, ticksLeft);
    }

    /** Client: the Singular Emission on (`ticksLeft` more) or off. */
    public static void clientEmission(boolean on, int ticksLeft, long now) {
        clientEmission = on;
        clientEmissionEndsAt = now + Math.max(0, ticksLeft);
    }

    /** Client: left the world / the server - calm again, no tint, no haze. */
    public static void clientReset() {
        clientPhase = CALM;
        clientEndsAt = 0L;
        clientCalmSince = Long.MIN_VALUE / 2;
        clientPulseAt = Long.MIN_VALUE / 2;
        clientHaze = false;
        clientHazeSince = Long.MIN_VALUE / 2;
        clientEmission = false;
    }

    /** Client: the haze's share now, 0..1 (thickening after it began, thinning after it ended) - W's fog density. */
    public static float hazeFactor(World w, float partialTicks) {
        if (w == null) {
            return 0F;
        }
        double since = w.getTotalWorldTime() + partialTicks - clientHazeSince;
        if (since < 0D) {
            return clientHaze ? 1F : 0F;
        }
        double f = Math.min(1D, since / HAZE_FADE_TICKS);
        return (float) (clientHaze ? f : 1D - f);
    }

    /** Client: the Singular Emission runs (the Rift's HUD line, W's fog tint). */
    public static boolean clientEmission() {
        return clientEmission;
    }

    /** Client: ticks left of the haze / the emission (0 when off). */
    public static int clientHazeLeft(long now) {
        return clientHaze ? (int) Math.max(0L, clientHazeEndsAt - now) : 0;
    }

    public static int clientEmissionLeft(long now) {
        return clientEmission ? (int) Math.max(0L, clientEmissionEndsAt - now) : 0;
    }

    public static int clientPhase() {
        return clientPhase;
    }

    /** Client: ticks left of the phase now (0 calm or past it). */
    public static int clientTicksLeft(long now) {
        return clientPhase == CALM ? 0 : (int) Math.max(0L, clientEndsAt - now);
    }

    /** Client: ticks since the last pulse (large when none). */
    public static long clientSincePulse(long now) {
        return now - clientPulseAt;
    }

    /**
     * Client: how far the sky has turned to the storm's violet, 0..1 - growing over the warning, 1 in the storm, fading for
     * FADE_OUT_TICKS after it. `w` - the client's world (its total time), any world gives 0 when calm long enough.
     */
    public static float skyFactor(World w, float partialTicks) {
        if (w == null) {
            return 0F;
        }
        double now = w.getTotalWorldTime() + partialTicks;
        int phase = clientPhase;
        if (phase == STORM) {
            return 1F;
        }
        if (phase == WARNING) {
            double left = clientEndsAt - now;
            return (float) Math.max(0D, Math.min(1D, 1D - left / clientWarnTicks));
        }
        double since = now - clientCalmSince;
        return since < 0D || since >= FADE_OUT_TICKS ? 0F : (float) (1D - since / FADE_OUT_TICKS);
    }
}
