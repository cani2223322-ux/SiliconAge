package com.sc.settle.client;

import com.sc.settle.caravan.EntityCaravanMasterSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.4): the caravan's client side - registered by ClientProxy.preInit (the coordinator's
 * line): the master's and the guards' renderers if K gives them their own (else RenderingRegistry falls back to the Tuner's and the
 * warden's renderers by the class - they already look right), the master's pack and crates, the guards' turquoise lamps. Fixed: the
 * class, register; the body K's. Client only.
 * <p>
 * Package K's: the master gets its own (RenderCaravanMasterSC - the Tuner's with the frame, the crates and the caravan's lamp); the
 * guards keep the warden's by the class (RenderManager finds EntityWardenSC's: the Sentry's Nano with the dome's turquoise light, the
 * alarm lamp, the name and rank).
 */
@SideOnly(Side.CLIENT)
public final class CaravanClientSC {

    private CaravanClientSC() {
    }

    /** ClientProxy.preInit. K's body. */
    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityCaravanMasterSC.class, new RenderCaravanMasterSC());
    }
}
