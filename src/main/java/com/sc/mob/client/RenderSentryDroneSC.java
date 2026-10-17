package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntitySentryDroneSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.7): the feral sentry drone (EntitySentryDroneSC) - a squat rust-orange hull on
 * four arms with spinning rotors, a searchlight's lens in front (full-bright: dim amber at rest, white-hot while it shines),
 * the Ion Cutter's barrel slung under it, a bent antenna; it bobs a little as it hangs in the air. While the searchlight is on
 * (the telegraph ACT_SEARCHLIGHT, its target in the DataWatcher) every client draws the light's cone from the lens to the
 * target's chest - additive, a pale beam that widens - the cue to take cover. Skin: textures/entity/sentryDrone.png (64 x 32,
 * tools/gen_ruins_tex.py).
 */
@SideOnly(Side.CLIENT)
public class RenderSentryDroneSC extends RenderLiving {

    private static final ResourceLocation TEX = new ResourceLocation(Reference.ASSETS, "textures/entity/sentryDrone.png");
    /** The cone: its radius at the target, at the lens; its segments; its light (apex, base alpha). */
    private static final double CONE_BASE = 0.75D, CONE_TIP = 0.06D;
    private static final int CONE_SIDES = 12;
    private static final float CONE_ALPHA = 0.32F, CONE_FAR_ALPHA = 0.04F;

    public RenderSentryDroneSC() {
        super(new ModelSentryDrone(), 0.3F);
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }

    /** A slow bob in the air. */
    @Override
    protected void preRenderCallback(EntityLivingBase e, float pt) {
        GL11.glTranslatef(0F, MathHelper.sin((e.ticksExisted + pt) * 0.12F + e.getEntityId()) * 0.04F, 0F);
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float pt) {
        super.doRender(e, x, y, z, yaw, pt);
        EntitySentryDroneSC d = (EntitySentryDroneSC) e;
        if (d.mobAction() != IMobActionSC.ACT_SEARCHLIGHT || d.lightTarget() < 0) {
            return;
        }
        Entity t = d.worldObj.getEntityByID(d.lightTarget());
        if (t == null) {
            return;
        }
        float body = d.prevRenderYawOffset + (d.renderYawOffset - d.prevRenderYawOffset) * pt;
        double fx = -MathHelper.sin(body * (float) Math.PI / 180F), fz = MathHelper.cos(body * (float) Math.PI / 180F);
        double[] a = {x + fx * 0.38D, y + 0.28D, z + fz * 0.38D};
        double[] b = {t.lastTickPosX + (t.posX - t.lastTickPosX) * pt - RenderManager.renderPosX,
                t.lastTickPosY + (t.posY - t.lastTickPosY) * pt - RenderManager.renderPosY + t.height * 0.6D,
                t.lastTickPosZ + (t.posZ - t.lastTickPosZ) * pt - RenderManager.renderPosZ};
        cone(a, b);
    }

    /** The searchlight's cone from `a` (the lens) to `b` (the target), in render space. */
    private static void cone(double[] a, double[] b) {
        double ax = b[0] - a[0], ay = b[1] - a[1], az = b[2] - a[2];
        double len = Math.sqrt(ax * ax + ay * ay + az * az);
        if (len < 0.2D) {
            return;
        }
        ax /= len;
        ay /= len;
        az /= len;
        // two unit vectors across the axis
        double[] u = Math.abs(ay) < 0.9D ? cross(ax, ay, az, 0D, 1D, 0D) : cross(ax, ay, az, 1D, 0D, 0D);
        double[] v = cross(ax, ay, az, u[0], u[1], u[2]);
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);   // additive: light
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            for (int i = 0; i < CONE_SIDES; i++) {
                double a0 = Math.PI * 2D * i / CONE_SIDES, a1 = Math.PI * 2D * (i + 1) / CONE_SIDES;
                double c0 = Math.cos(a0), s0 = Math.sin(a0), c1 = Math.cos(a1), s1 = Math.sin(a1);
                t.setColorRGBA_F(1F, 0.96F, 0.78F, CONE_ALPHA);
                t.addVertex(a[0] + (u[0] * c0 + v[0] * s0) * CONE_TIP, a[1] + (u[1] * c0 + v[1] * s0) * CONE_TIP, a[2] + (u[2] * c0 + v[2] * s0) * CONE_TIP);
                t.addVertex(a[0] + (u[0] * c1 + v[0] * s1) * CONE_TIP, a[1] + (u[1] * c1 + v[1] * s1) * CONE_TIP, a[2] + (u[2] * c1 + v[2] * s1) * CONE_TIP);
                t.setColorRGBA_F(1F, 0.92F, 0.62F, CONE_FAR_ALPHA);
                t.addVertex(b[0] + (u[0] * c1 + v[0] * s1) * CONE_BASE, b[1] + (u[1] * c1 + v[1] * s1) * CONE_BASE, b[2] + (u[2] * c1 + v[2] * s1) * CONE_BASE);
                t.addVertex(b[0] + (u[0] * c0 + v[0] * s0) * CONE_BASE, b[1] + (u[1] * c0 + v[1] * s0) * CONE_BASE, b[2] + (u[2] * c0 + v[2] * s0) * CONE_BASE);
            }
            t.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopAttrib();
        }
    }

    /** The unit cross product of (a) and (b). */
    private static double[] cross(double ax, double ay, double az, double bx, double by, double bz) {
        double x = ay * bz - az * by, y = az * bx - ax * bz, z = ax * by - ay * bx;
        double l = Math.sqrt(x * x + y * y + z * z);
        return l < 1.0E-9D ? new double[]{1D, 0D, 0D} : new double[]{x / l, y / l, z / l};
    }

    /**
     * The drone's model (64 x 32): the hull, the lens' housing and its glass (drawn full-bright, white-hot while the light is
     * on), the cutter's barrel, the antenna, four arms with a rotor each (spinning).
     */
    @SideOnly(Side.CLIENT)
    public static class ModelSentryDrone extends ModelBase {

        private final ModelRenderer hull, housing, glass, barrel, antenna;
        private final ModelRenderer[] arms = new ModelRenderer[4], rotors = new ModelRenderer[4];

        public ModelSentryDrone() {
            textureWidth = 64;
            textureHeight = 32;
            hull = new ModelRenderer(this, 0, 0);
            hull.addBox(-4F, -2.5F, -4F, 8, 5, 8);
            hull.setRotationPoint(0F, 19.5F, 0F);
            housing = new ModelRenderer(this, 0, 13);
            housing.addBox(-1.5F, -1.5F, -2F, 3, 3, 2);
            housing.setRotationPoint(0F, 19.5F, -4F);
            glass = new ModelRenderer(this, 0, 19);
            glass.addBox(-1F, -1F, -2.3F, 2, 2, 1);
            glass.setRotationPoint(0F, 19.5F, -4F);
            barrel = new ModelRenderer(this, 12, 13);
            barrel.addBox(-0.5F, -0.5F, -6F, 1, 1, 6);
            barrel.setRotationPoint(0F, 22.5F, 0F);
            antenna = new ModelRenderer(this, 28, 13);
            antenna.addBox(-0.5F, -4F, -0.5F, 1, 4, 1);
            antenna.setRotationPoint(2.5F, 17F, 2.5F);
            antenna.rotateAngleZ = 0.3F;
            for (int k = 0; k < 4; k++) {
                float a = (float) (Math.PI / 4D + k * Math.PI / 2D);
                arms[k] = new ModelRenderer(this, 32, 0);
                arms[k].addBox(4F, -0.5F, -0.5F, 5, 1, 1);
                arms[k].setRotationPoint(0F, 18.5F, 0F);
                arms[k].rotateAngleY = a;
                rotors[k] = new ModelRenderer(this, 32, 4);
                rotors[k].addBox(-2.5F, -0.5F, -2.5F, 5, 1, 5);
                rotors[k].setRotationPoint(9F * MathHelper.cos(a), 17.5F, -9F * MathHelper.sin(a));
            }
        }

        @Override
        public void render(Entity e, float swing, float amount, float age, float headYaw, float pitch, float scale) {
            setRotationAngles(swing, amount, age, headYaw, pitch, scale, e);
            hull.render(scale);
            housing.render(scale);
            barrel.render(scale);
            antenna.render(scale);
            for (int k = 0; k < 4; k++) {
                arms[k].render(scale);
                rotors[k].render(scale);
            }
            boolean on = e instanceof EntitySentryDroneSC && ((EntitySentryDroneSC) e).mobAction() == IMobActionSC.ACT_SEARCHLIGHT;
            float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            if (on) {
                GL11.glColor4f(1F, 1F, 1F, 1F);
            } else {
                GL11.glColor4f(0.75F, 0.55F, 0.35F, 1F);
            }
            glass.render(scale);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }

        @Override
        public void setRotationAngles(float swing, float amount, float age, float headYaw, float pitch, float scale, Entity e) {
            for (int k = 0; k < 4; k++) {
                rotors[k].rotateAngleY = age * 1.1F + k * 0.8F;
            }
            float tilt = pitch * (float) Math.PI / 180F * 0.5F;
            housing.rotateAngleX = tilt;
            glass.rotateAngleX = tilt;
        }
    }
}
