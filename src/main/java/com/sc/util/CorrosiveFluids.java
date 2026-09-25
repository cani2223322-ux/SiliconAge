package com.sc.util;

import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

/**
 * Registry of fluid names that count as "агрессивная химия" per §12.3 - only PTFE Pipe can
 * carry these; any other pipe corrodes and breaks after sustained contact (see
 * TileEntityPipeSC). Filled by ModFluids.init() (it used to stay empty, so no pipe ever
 * corroded and the manual's PTFE warning described a mechanic that didn't exist).
 */
public final class CorrosiveFluids {

    private static final Set<String> NAMES = new HashSet<String>();

    private CorrosiveFluids() {
    }

    public static void register(String fluidName) {
        NAMES.add(fluidName);
    }

    public static boolean isCorrosive(String fluidName) {
        return NAMES.contains(fluidName);
    }

    public static Set<String> all() {
        return Collections.unmodifiableSet(NAMES);
    }
}
