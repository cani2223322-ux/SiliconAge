package com.sc.mob.client;

import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityContainmentCoilSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.2, §5.8): a Containment Coil - ModelContainmentCoilSC x SCALE (about 2 blocks); its
 * glass core lit blue while it is LIT (dim grey dark, black broken), and its beam: while LIT, a crackling blue-white ribbon from its
 * emitter (EntityContainmentCoilSC.BEAM_Y) to its Warden's chest (DW_BOSS - the entity on this client; none found - no beam). The
 * coil ignores the frustum check (EntityContainmentCoilSC: ignoreFrustumCheck), so the beam is drawn with the coil off screen too.
 * The sparks of a dark coil and the smoke of a broken one are the entity's own particles.
 */
@SideOnly(Side.CLIENT)
public class RenderContainmentCoilSC extends RenderLiving {

    /** The model is 24 px tall: x1.3 - its emitter at 1.95 blocks (EntityContainmentCoilSC.BEAM_Y). */
    public static final float SCALE = 1.3F;
    /** The core lit / dark / broken; the beam's glow and its white heart. */
    public static final int CORE_LIT = 0x5AC8FF, CORE_DARK = 0x3A4048, CORE_BROKEN = 0x141418, BEAM = 0x6EC8FF, BEAM_HEART = 0xE6F6FF;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityContainmentCoilSC.NAME + ".png");

    private final ModelContainmentCoilSC model;
    private final Random jitter = new Random();

    public RenderContainmentCoilSC() {
        this(new ModelContainmentCoilSC());
    }

    private RenderContainmentCoilSC(ModelContainmentCoilSC model) {
        super(model, 0.6F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float pt) {
        super.preRenderCallback(e, pt);
        GL11.glScalef(SCALE, SCALE, SCALE);
    }

    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        if (!(e instanceof EntityContainmentCoilSC)) {
            return;
        }
        int s = ((EntityContainmentCoilSC) e).coilState();
        float age = e.ticksExisted + pt;
        int rgb = s == EntityContainmentCoilSC.LIT ? CORE_LIT : s == EntityContainmentCoilSC.BROKEN ? CORE_BROKEN : CORE_DARK;
        float k = s == EntityContainmentCoilSC.LIT ? 0.75F + 0.25F * (float) Math.sin(age * 0.4F) : 1F;
        RenderSubstrateMobSC.lamp(model.core, rgb, k);
        if (s == EntityContainmentCoilSC.LIT) {
            RenderSubstrateMobSC.lamp(model.cap, BEAM_HEART, 0.55F + 0.2F * (float) Math.sin(age * 0.7F));
        }
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float pt) {
        super.doRender(e, x, y, z, yaw, pt);
        if (e instanceof EntityContainmentCoilSC && ((EntityContainmentCoilSC) e).coilState() == EntityContainmentCoilSC.LIT && e.deathTime <= 0) {
            Entity boss = e.worldObj.getEntityByID(((EntityContainmentCoilSC) e).bossId());
            if (boss != null && !boss.isDead) {
                beam(e, boss, x, y + EntityContainmentCoilSC.BEAM_Y, z, pt);
            }
        }
    }

    /** The beam to the Warden's chest (camera-relative: the camera is the origin), its kinks moving a few times a second. */
    private void beam(Entity coil, Entity boss, double x, double y, double z, float pt) {
        double bx = boss.lastTickPosX + (boss.posX - boss.lastTickPosX) * pt - RenderManager.renderPosX;
        double by = boss.lastTickPosY + (boss.posY - boss.lastTickPosY) * pt - RenderManager.renderPosY + boss.height * 0.6D;
        double bz = boss.lastTickPosZ + (boss.posZ - boss.lastTickPosZ) * pt - RenderManager.renderPosZ;
        double[] a = {x, y, z}, b = {bx, by, bz};
        float age = coil.ticksExisted + pt;
        float flicker = 0.75F + 0.25F * (float) Math.sin(age * 2.3F);
        long seed = coil.getEntityId() * 7919L + (coil.ticksExisted / 3);
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        try {
            GL11.glDisable(GL11.GL_TEXTURE_2D);
            GL11.glDisable(GL11.GL_LIGHTING);
            GL11.glDisable(GL11.GL_CULL_FACE);
            GL11.glEnable(GL11.GL_BLEND);
            GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
            GL11.glDepthMask(false);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            Tessellator t = Tessellator.instance;
            t.startDrawingQuads();
            crackle(t, a, b, 0.32F, BEAM, 0.22F * flicker, seed, 0.18D);
            crackle(t, a, b, 0.12F, BEAM, 0.7F * flicker, seed, 0.18D);
            crackle(t, a, b, 0.04F, BEAM_HEART, 0.9F, seed, 0.18D);
            t.draw();
        } finally {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
            GL11.glPopAttrib();
            GL11.glColor4f(1F, 1F, 1F, 1F);
        }
    }

    /** A jagged camera-facing ribbon from a to b, `jag` blocks off the line at its joints (fixed by the seed). */
    private void crackle(Tessellator t, double[] a, double[] b, float width, int rgb, float alpha, long seed, double jag) {
        jitter.setSeed(seed);
        double dx = b[0] - a[0], dy = b[1] - a[1], dz = b[2] - a[2];
        double len = Math.sqrt(dx * dx + dy * dy + dz * dz);
        int n = Math.max(2, Math.min(24, (int) (len / 1.5D)));
        double px = a[0], py = a[1], pz = a[2];
        for (int i = 1; i <= n; i++) {
            double f = i / (double) n;
            double qx = a[0] + dx * f, qy = a[1] + dy * f, qz = a[2] + dz * f;
            if (i < n) {
                qx += (jitter.nextDouble() - 0.5D) * 2D * jag;
                qy += (jitter.nextDouble() - 0.5D) * 2D * jag;
                qz += (jitter.nextDouble() - 0.5D) * 2D * jag;
            }
            ribbon(t, px, py, pz, qx, qy, qz, width, rgb, alpha);
            px = qx;
            py = qy;
            pz = qz;
        }
    }

    /** One straight piece of a ribbon facing the camera (at the origin). */
    static void ribbon(Tessellator t, double ax, double ay, double az, double bx, double by, double bz, float width, int rgb, float alpha) {
        double dx = bx - ax, dy = by - ay, dz = bz - az;
        double mx = (ax + bx) / 2D, my = (ay + by) / 2D, mz = (az + bz) / 2D;
        double sx = dy * mz - dz * my, sy = dz * mx - dx * mz, sz = dx * my - dy * mx;   // d x (mid - eye), the eye at 0
        double l = Math.sqrt(sx * sx + sy * sy + sz * sz);
        if (l < 1.0E-6D) {
            return;
        }
        double k = width / 2D / l;
        sx *= k;
        sy *= k;
        sz *= k;
        t.setColorRGBA_I(rgb, (int) (Math.max(0F, Math.min(1F, alpha)) * 255));
        t.addVertex(ax - sx, ay - sy, az - sz);
        t.addVertex(ax + sx, ay + sy, az + sz);
        t.addVertex(bx + sx, by + sy, bz + sz);
        t.addVertex(bx - sx, by - sy, bz - sz);
    }
}
