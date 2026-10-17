package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.client.ModelArmorGlowSC;
import com.sc.mob.EntityArmoredMobSC;
import com.sc.mob.IArmoredMobSC;
import com.sc.mob.MobArmorSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;
import net.minecraftforge.client.event.RenderLivingEvent;
import net.minecraftforge.common.MinecraftForge;

/**
 * PACKAGE A (docs/plan-substrate-tech-f3.md §5.3, rule ПС-25): the client side of the armoured mobs that isn't a renderer of
 * one class - an elite's name over its head («Командир · Штурмовик», each client in its own language, within 32 blocks;
 * RenderLivingEvent.Specials.Post, drawn by RenderEliteNameSC), the personal EMP screen's bubble, the telegraphs drawn the
 * same for every mob (IMobActionSC). Called from ClientProxy.preInit.
 * <p>
 * The bubble (ПБ-19): while MobArmorSC.FLAG_SCREEN is up (the Jammer's screen, a «Экранированный» elite's), an ellipsoid round
 * the mob of a blue honeycomb (textures/misc/emp_screen.png, tools/gen_mob_screen_tex.py), full-bright and additive, slowly
 * turning and breathing - drawn after the mob (RenderLivingEvent.Post), only within ModelArmorGlowSC.MOB_GLOW_RANGE of the
 * camera; it goes out the moment the screen takes a shot (the flag's sync) and comes back with it 30 s later. Of the
 * telegraphs this class draws none: the ПЭ-1's blue barrel is RenderEmpGunSC's, the rest the mobs' own renderers'.
 */
@SideOnly(Side.CLIENT)
public final class MobArmorClientSC {

    private static final ResourceLocation SCREEN_TEX = new ResourceLocation(Reference.ASSETS, "textures/misc/emp_screen.png");
    /** The bubble's colour (the screen's blue), its strength (0..1) and how far it stands off the mob's hitbox, blocks. */
    private static final int SCREEN_RGB = 0x5AAEFF;
    private static final float SCREEN_ALPHA = 0.30F, SCREEN_PAD_SIDE = 0.55F, SCREEN_PAD_UP = 0.3F;
    /** The ellipsoid's rings and segments; the honeycomb's repeats round it and from pole to pole. */
    private static final int LAT = 10, LON = 20;
    private static final float REPEAT_U = 3F, REPEAT_V = 2F;

    private static boolean registered;

    private MobArmorClientSC() {
    }

    public static void register() {
        if (!registered) {
            registered = true;
            MinecraftForge.EVENT_BUS.register(new MobArmorClientSC());
        }
    }

    /** ПС-25: an elite's name, after vanilla's name tag (if any). */
    @SubscribeEvent
    public void onSpecials(RenderLivingEvent.Specials.Post e) {
        if (e.entity instanceof EntityArmoredMobSC) {
            RenderEliteNameSC.draw((EntityArmoredMobSC) e.entity, e.renderer, e.x, e.y, e.z);
        }
    }

    /** ПБ-19: the personal EMP screen's bubble, after the mob is drawn. */
    @SubscribeEvent
    public void onRendered(RenderLivingEvent.Post e) {
        EntityLivingBase m = e.entity;
        if (!(m instanceof IArmoredMobSC) || m.isInvisible() || !MobArmorSC.clientFlag(m, MobArmorSC.FLAG_SCREEN)) {
            return;
        }
        RenderManager rm = RenderManager.instance;
        double range = ModelArmorGlowSC.MOB_GLOW_RANGE;
        if (rm.livingPlayer == null || m.getDistanceSqToEntity(rm.livingPlayer) > range * range) {
            return;
        }
        bubble(m, e.x, e.y, e.z);
    }

    private static void bubble(EntityLivingBase m, double x, double y, double z) {
        float rx = m.width * 0.5F + SCREEN_PAD_SIDE, ry = m.height * 0.5F + SCREEN_PAD_UP;
        float secs = (Minecraft.getSystemTime() % 600000L) / 1000F + m.getEntityId() * 0.37F;
        float alpha = SCREEN_ALPHA * (0.8F + 0.2F * (float) Math.sin(secs * 2.6F));
        float scroll = secs * 0.08F % 1F;
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT
                | GL11.GL_CURRENT_BIT);
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y + m.height * 0.5, z);
            GL11.glRotatef(secs * 9F % 360F, 0F, 1F, 0F);
            Minecraft.getMinecraft().getTextureManager().bindTexture(SCREEN_TEX);
            GL11.glEnable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glDisable(GL11.GL_ALPHA_TEST);                 // the film is fainter than vanilla's 0.1 cut
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive: light
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            t.setColorRGBA_I(SCREEN_RGB, Math.max(0, Math.min(255, (int) (alpha * 255))));
            for (int i = 0; i < LAT; i++) {
                double th0 = Math.PI * i / LAT, th1 = Math.PI * (i + 1) / LAT;
                float v0 = REPEAT_V * i / LAT + scroll, v1 = REPEAT_V * (i + 1) / LAT + scroll;
                for (int j = 0; j < LON; j++) {
                    double ph0 = 2 * Math.PI * j / LON, ph1 = 2 * Math.PI * (j + 1) / LON;
                    float u0 = REPEAT_U * j / LON, u1 = REPEAT_U * (j + 1) / LON;
                    vertex(t, rx, ry, th0, ph0, u0, v0);
                    vertex(t, rx, ry, th1, ph0, u0, v1);
                    vertex(t, rx, ry, th1, ph1, u1, v1);
                    vertex(t, rx, ry, th0, ph1, u1, v0);
                }
            }
            t.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopMatrix();
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A point of the ellipsoid: `th` from the top pole, `ph` round it. */
    private static void vertex(Tessellator t, float rx, float ry, double th, double ph, float u, float v) {
        double s = Math.sin(th);
        t.addVertexWithUV(rx * s * Math.cos(ph), ry * Math.cos(th), rx * s * Math.sin(ph), u, v);
    }
}
