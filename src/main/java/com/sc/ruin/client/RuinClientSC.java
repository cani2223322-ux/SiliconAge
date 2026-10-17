package com.sc.ruin.client;

import com.sc.mob.EntityMimicSC;
import com.sc.mob.EntityNaniteSC;
import com.sc.mob.EntitySentryDroneSC;
import com.sc.mob.client.RenderMimicSC;
import com.sc.mob.client.RenderNaniteSC;
import com.sc.mob.client.RenderSentryDroneSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.7): the client side of the ruins and their «noise» - the renderers of the
 * nanite particle (a glowing point with a tail), the feral sentry drone (its searchlight's cone) and the mimic machine (a
 * machine block while asleep, the machine on legs with a mouth awake). The ruin blocks need nothing past their icons.
 * Called from ClientProxy.preInit.
 */
@SideOnly(Side.CLIENT)
public final class RuinClientSC {

    private RuinClientSC() {
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityNaniteSC.class, new RenderNaniteSC());
        RenderingRegistry.registerEntityRenderingHandler(EntitySentryDroneSC.class, new RenderSentryDroneSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityMimicSC.class, new RenderMimicSC());
    }
}
