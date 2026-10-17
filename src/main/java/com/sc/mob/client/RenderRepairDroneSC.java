package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityRepairDroneSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE M (docs/plan-substrate-tech-f3.md §4.4, rule ПС-9): the Repair Drone - ModelRepairDroneSC, its nozzle full-bright
 * yellow-green while it repairs, and the repair beam (IMobActionSC.ACT_REPAIR): a flickering line of light from the nozzle
 * to its Shell's chest (the Shell by EntityRepairDroneSC.DW_SHELL; nothing if that one isn't loaded on this client), a
 * little wobble along it so it reads as energy, not a wire. Every client draws it from the DataWatcher - no packets.
 */
@SideOnly(Side.CLIENT)
public class RenderRepairDroneSC extends RenderLiving {

    /** The beam's and the nozzle's colour; the nozzle at rest. */
    public static final int BEAM = 0xD8FF60, NOZZLE_IDLE = 0x6A7A30;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityRepairDroneSC.NAME + ".png");
    private static final int SEGMENTS = 8;

    private final ModelRepairDroneSC model;

    public RenderRepairDroneSC() {
        this(new ModelRepairDroneSC());
    }

    private RenderRepairDroneSC(ModelRepairDroneSC model) {
        super(model, 0.3F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float partial) {
        super.doRender(e, x, y, z, yaw, partial);
        EntityRepairDroneSC d = (EntityRepairDroneSC) e;
        if (d.deathTime > 0 || d.mobAction() != IMobActionSC.ACT_REPAIR) {
            return;
        }
        Entity s = d.watchedShell();
        if (s == null || s.isDead) {
            return;
        }
        double sx = s.lastTickPosX + (s.posX - s.lastTickPosX) * partial - RenderManager.renderPosX;
        double sy = s.lastTickPosY + (s.posY - s.lastTickPosY) * partial - RenderManager.renderPosY + s.height * 0.62D;
        double sz = s.lastTickPosZ + (s.posZ - s.lastTickPosZ) * partial - RenderManager.renderPosZ;
        beam(x, y + 0.3D, z, sx, sy, sz, d.ticksExisted + partial, d.getEntityId());
    }

    /** The lit nozzle (after the model, in its space). */
    @Override
    protected void renderEquippedItems(net.minecraft.entity.EntityLivingBase e, float partial) {
        super.renderEquippedItems(e, partial);
        if (e.deathTime > 0) {
            return;
        }
        boolean on = ((IMobActionSC) e).mobAction() == IMobActionSC.ACT_REPAIR;
        GL11.glPushMatrix();
        model.hull.postRender(0.0625F);
        RenderSubstrateMobSC.lamp(model.nozzle, on ? BEAM : NOZZLE_IDLE, on ? 0.75F + 0.25F * (float) Math.sin((e.ticksExisted + partial) * 0.8F) : 1F);
        GL11.glPopMatrix();
    }

    /** A wobbling full-bright line strip from (x0, y0, z0) to (x1, y1, z1), camera-relative. */
    private static void beam(double x0, double y0, double z0, double x1, double y1, double z1, float t, int seed) {
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        float r = (BEAM >> 16 & 255) / 255F, g = (BEAM >> 8 & 255) / 255F, b = (BEAM & 255) / 255F;
        for (int pass = 0; pass < 2; pass++) {
            GL11.glLineWidth(pass == 0 ? 4F : 1.5F);
            Tessellator tes = Tessellator.instance;
            tes.startDrawing(GL11.GL_LINE_STRIP);
            tes.setColorRGBA_F(r, g, b, pass == 0 ? 0.25F : 0.9F);
            for (int i = 0; i <= SEGMENTS; i++) {
                double k = i / (double) SEGMENTS;
                double w = i == 0 || i == SEGMENTS ? 0D : 0.06D * Math.sin(t * 0.9D + i * 1.7D + seed);
                tes.addVertex(x0 + (x1 - x0) * k + w, y0 + (y1 - y0) * k + w * 0.6D, z0 + (z1 - z0) * k - w);
            }
            tes.draw();
        }
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GL11.glPopAttrib();
    }
}
