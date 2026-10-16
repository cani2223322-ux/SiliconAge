package com.sc.mob.client;

import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.9): the Substrate mobs' renderers (RenderBiped on ModelBiped - the worn
 * mod armour draws itself through ItemArmorSC.getArmorModel), called from ClientProxy.preInit.
 */
@SideOnly(Side.CLIENT)
public final class MobClientSC {

    private MobClientSC() {
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityLinemanSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, EntityLinemanSC.NAME));
        RenderingRegistry.registerEntityRenderingHandler(EntityMarauderSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, EntityMarauderSC.NAME));
    }
}
