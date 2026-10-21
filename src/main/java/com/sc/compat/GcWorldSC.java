package com.sc.compat;

import micdoodle8.mods.galacticraft.api.world.IAtmosphericGas;
import micdoodle8.mods.galacticraft.api.world.IGalacticraftWorldProvider;

import net.minecraft.world.WorldProvider;

/**
 * ГН-5 (audit 2026-10-20, ★ A): Galacticraft's worlds as the wind turbine sees them, in a class of its own - it holds
 * Galacticraft's API types, so it is loaded only behind Loader.isModLoaded("GalacticraftCore"), as GcOpsSC is.
 */
public final class GcWorldSC {

    private GcWorldSC() {
    }

    /**
     * A Galacticraft world without air (the Moon, asteroids): no breathable atmosphere and none of its gases at all - no wind
     * there. A world with some atmosphere (Mars, Venus) has wind; not Galacticraft's - false; a misbehaving provider - false.
     */
    public static boolean airless(WorldProvider p) {
        if (!(p instanceof IGalacticraftWorldProvider)) {
            return false;
        }
        try {
            IGalacticraftWorldProvider gc = (IGalacticraftWorldProvider) p;
            if (gc.hasBreathableAtmosphere()) {
                return false;
            }
            for (IAtmosphericGas g : IAtmosphericGas.values()) {
                if (gc.isGasPresent(g)) {
                    return false;
                }
            }
            return true;
        } catch (RuntimeException e) {
            return false;
        }
    }
}
