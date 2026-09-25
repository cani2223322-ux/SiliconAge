package com.sc.client;

import com.sc.Reference;
import com.sc.init.ModFluids;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraftforge.client.event.TextureStitchEvent;
import net.minecraftforge.fluids.Fluid;

/**
 * Attaches the animated still/flow textures (textures/blocks/fluids/) to the fluids this mod
 * registered. A fluid without icons is fine inside our own machines, but other mods' tanks and
 * pipes (and NEI) render Fluid.getIcon() directly and would crash or draw garbage on null - so
 * this is not cosmetic. Fluids another mod owns (IC2 hydrogen, Railcraft steam, ...) are left
 * alone: they're that mod's to texture.
 */
public class FluidTextureHandler {

    @SubscribeEvent
    public void onTextureStitch(TextureStitchEvent.Pre event) {
        TextureMap map = event.map;
        if (map.getTextureType() != 0) {       // 0 = blocks atlas, where fluid icons live
            return;
        }
        for (Fluid fluid : ModFluids.OWNED) {
            String base = Reference.ASSETS + ":fluids/" + fluid.getName();
            fluid.setIcons(map.registerIcon(base + "_still"), map.registerIcon(base + "_flow"));
        }
    }
}
