package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.mob.EntityOverheatedSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.EntityLivingBase;

/**
 * PACKAGE M (docs/plan-substrate-tech-f4.md §7.1, rule ПС-31): the Overheated - its scorched undersuit (textures/entity/
 * overheated.png: soot-black plating cracked with molten orange) under the burnt-out Quantum. After the model its skin is drawn
 * once more, additively and full-bright in a red-hot orange, breathing slowly - the dark plating adds next to nothing, the
 * cracks and joints glow through the armour's gaps; quenched (EntityOverheatedSC.wet - the steam is the entity's own) the glow
 * all but goes out.
 */
@SideOnly(Side.CLIENT)
public class RenderOverheatedSC extends RenderSubstrateMobSC {

    /** The glow's tint and strength: breathing between GLOW_MIN and GLOW_MIN + GLOW_SWING; quenched GLOW_WET. */
    public static final int GLOW_RGB = 0xFF7A30;
    public static final float GLOW_MIN = 0.45F, GLOW_SWING = 0.3F, GLOW_WET = 0.12F;

    public RenderOverheatedSC() {
        super(new ModelBiped(), 0.5F, EntityOverheatedSC.NAME);
    }

    @Override
    protected void renderModel(EntityLivingBase e, float swing, float amount, float age, float yaw, float pitch, float scale) {
        super.renderModel(e, swing, amount, age, yaw, pitch, scale);
        if (e.isInvisible() || e.deathTime > 0) {
            return;
        }
        boolean wet = e instanceof EntityOverheatedSC && ((EntityOverheatedSC) e).wet();
        float k = wet ? GLOW_WET : GLOW_MIN + GLOW_SWING * (0.5F + 0.5F * (float) Math.sin(age * 0.12F));
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthMask(false);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glColor4f((GLOW_RGB >> 16 & 255) / 255F * k, (GLOW_RGB >> 8 & 255) / 255F * k, (GLOW_RGB & 255) / 255F * k, 1F);
        mainModel.render(e, swing, amount, age, yaw, pitch, scale);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GL11.glPopAttrib();
    }
}
