package com.sc.mob.client;

import com.sc.Reference;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.8): a Substrate mob on vanilla's biped - its skin
 * textures/entity/<name>.png (the 64 x 32 layout, drawn at 128 x 64 like the mod's armour). The worn mod armour draws
 * itself (RenderBiped.shouldRenderPass -> ForgeHooksClient.getArmorModel -> ItemArmorSC.getArmorModel ->
 * ModelArmorGlowSC, which takes the mob's charge, «down», suppression and siphon from its DataWatcher); the item in
 * hand goes vanilla's way; crouching at a trace shows (RenderBiped takes isSneaking). The Lineman's skin is mostly
 * transparent - the empty shell (the alpha test cuts it): only its frame, the cable and the diode where no piece covers it.
 */
@SideOnly(Side.CLIENT)
public class RenderSubstrateMobSC extends RenderBiped {

    private final ResourceLocation skin;

    public RenderSubstrateMobSC(ModelBiped model, float shadow, String name) {
        super(model, shadow);
        skin = new ResourceLocation(Reference.ASSETS, "textures/entity/" + name + ".png");
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving e) {
        return skin;
    }
}
