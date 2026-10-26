package com.sc.mob.client;

import com.sc.mob.EntityWantedSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;

/**
 * ПД-4 (docs/plan-ideas-2026-10-26.md, «Розыск», ★ A - the client's own): the wanted Renegade's render (RenderWantedSC: the Trooper's or
 * the Executioner's look by its suit, the name «Хром-7 „Резчик“» and both modifiers over its head from EntityWantedSC.DW_WANTED, in this
 * client's language). ClientProxy calls register() (the coordinator's); everything in it is ПД-4's.
 */
@SideOnly(Side.CLIENT)
public final class WantedClientSC {

    private WantedClientSC() {
    }

    /** ClientProxy.preInit. */
    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityWantedSC.class, new RenderWantedSC());
    }
}
