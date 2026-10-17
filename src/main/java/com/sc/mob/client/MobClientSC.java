package com.sc.mob.client;

import com.sc.mob.EntityExecutionerSC;
import com.sc.mob.EntityHollowShellSC;
import com.sc.mob.EntityJammerSC;
import com.sc.mob.EntityLinemanSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.EntityTrooperSC;

import cpw.mods.fml.client.registry.RenderingRegistry;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.9): the Substrate mobs' renderers (RenderBiped on ModelBiped - the worn
 * mod armour draws itself through ItemArmorSC.getArmorModel), called from ClientProxy.preInit.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.1): package M's mobs - the Trooper, the Jammer (its antenna horns), the Hollow Shell,
 * the Executioner (larger, the helium tank) on the biped with their telegraph poses (RenderSubstrateMobSC.lean), the Repair
 * Drone on its own model with the repair beam. Package X's «noise» is ruin.client.RuinClientSC's.
 */
@SideOnly(Side.CLIENT)
public final class MobClientSC {

    private MobClientSC() {
    }

    public static void register() {
        RenderingRegistry.registerEntityRenderingHandler(EntityLinemanSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, EntityLinemanSC.NAME));
        RenderingRegistry.registerEntityRenderingHandler(EntityMarauderSC.class, new RenderSubstrateMobSC(new ModelBiped(), 0.5F, EntityMarauderSC.NAME));
        // Подложка Ф3
        RenderingRegistry.registerEntityRenderingHandler(EntityTrooperSC.class, new RenderTrooperSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityJammerSC.class, new RenderJammerSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityHollowShellSC.class, new RenderHollowShellSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityRepairDroneSC.class, new RenderRepairDroneSC());
        RenderingRegistry.registerEntityRenderingHandler(EntityExecutionerSC.class, new RenderExecutionerSC());
    }
}
