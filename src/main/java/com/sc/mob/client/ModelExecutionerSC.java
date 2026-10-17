package com.sc.mob.client;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.5): the Exo Executioner's biped (64 x 32, textures/entity/executioner.png)
 * with the helium tank on its back - a frosted cylinder-ish box behind the Exo chestplate (which inflates the body by 1:
 * the tank starts just past it) and its valve on top, on the hat layer's UV (the hat itself is off). Everything else is
 * the worn Exo.
 */
@SideOnly(Side.CLIENT)
public class ModelExecutionerSC extends ModelBiped {

    final ModelRenderer tank, valve;

    public ModelExecutionerSC() {
        super(0F, 0F, 64, 32);
        bipedHeadwear.showModel = false;
        tank = new ModelRenderer(this, 32, 0);
        tank.setTextureSize(64, 32);
        tank.addBox(-2F, 1.5F, 3.2F, 4, 7, 3);
        bipedBody.addChild(tank);
        valve = new ModelRenderer(this, 48, 0);
        valve.setTextureSize(64, 32);
        valve.addBox(-1F, -0.5F, 3.7F, 2, 2, 2);
        bipedBody.addChild(valve);
    }
}
