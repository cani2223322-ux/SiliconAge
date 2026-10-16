package com.sc.dim.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * PACKAGE W (docs/plan-substrate-tech.md §3.6): the Substrate's client side - fog events if the provider's colours
 * aren't enough, block renderers. Called from ClientProxy.preInit.
 * <p>
 * Ф1: nothing to register. The sky, fog, twilight and star colours are WorldProviderSubstrateSC's (EntityRenderer
 * takes the fog from getFogColor and blends in the sky colour itself); the druse draws as vanilla's crossed quads.
 * The fog's density (the Silica Haze) and the Rift's violet fog come in Ф4 as EntityViewRenderEvent handlers here.
 */
@SideOnly(Side.CLIENT)
public final class SubstrateClientSC {

    private SubstrateClientSC() {
    }

    public static void register() {
    }
}
