package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.World;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A, part 2): a Tuner drone over a workshop's showcase - a client particle
 * (never an entity, never sent): it hangs HOVER_MIN..HOVER_MAX over its showcase, sways, blinks its turquoise diode (the texture's two
 * frames, textures/particles/tunerDrone.png 32 x 16), now and then «solders» (a spark under it), lives LIFE ticks. Full-bright.
 * AmbientLifeClientSC places them. Client only.
 */
@SideOnly(Side.CLIENT)
public class EntityDroneFxSC extends EntityFX {

    public static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/particles/tunerDrone.png");
    /** EffectRenderer's batch texture, bound again after an own-texture quad (drawOwn). */
    private static final ResourceLocation PARTICLES = new ResourceLocation("textures/particle/particles.png");
    /** Its life (ticks); the hover over the showcase (blocks). */
    public static final int LIFE = 600;
    public static final double HOVER_MIN = 3D, HOVER_MAX = 5D;

    private final double cx, cy, cz, phase;

    public EntityDroneFxSC(World w, double x, double y, double z) {
        super(w, x, y, z, 0D, 0D, 0D);
        cx = x;
        cy = y;
        cz = z;
        phase = rand.nextDouble() * Math.PI * 2D;
        particleMaxAge = LIFE;
        particleScale = 1.4F;
        noClip = true;
        particleGravity = 0F;
        motionX = motionY = motionZ = 0D;
    }

    /** The showcase's block the drone hangs over (AmbientLifeClientSC's grouping). */
    public int groupX() {
        return (int) Math.floor(cx) >> 3;
    }

    public int groupZ() {
        return (int) Math.floor(cz) >> 3;
    }

    @Override
    public void onUpdate() {
        prevPosX = posX;
        prevPosY = posY;
        prevPosZ = posZ;
        if (particleAge++ >= particleMaxAge) {
            setDead();
            return;
        }
        double t = particleAge * 0.03D + phase;
        setPosition(cx + Math.sin(t) * 1.2D, cy + Math.sin(t * 1.7D) * 0.25D, cz + Math.cos(t * 0.8D) * 1.2D);
        if (rand.nextInt(60) == 0) {                            // «solders»: a spark under it
            worldObj.spawnParticle("fireworksSpark", posX, posY - 0.25D, posZ, (rand.nextDouble() - 0.5D) * 0.05D, -0.05D,
                    (rand.nextDouble() - 0.5D) * 0.05D);
        }
        particleAlpha = particleAge < 20 ? particleAge / 20F : particleAge > particleMaxAge - 20 ? (particleMaxAge - particleAge) / 20F : 1F;
    }

    @Override
    public int getFXLayer() {
        return 0;
    }

    @Override
    public void renderParticle(Tessellator t, float partial, float rx, float rz, float ryz, float rxy, float rxz) {
        int frame = (particleAge / 10) % 2;                     // the diode: on, off
        float u0 = frame * 0.5F, u1 = u0 + 0.5F;
        drawOwn(t, TEXTURE, this, partial, rx, rz, ryz, rxy, rxz, u0, u1, 0F, 1F, 0.1F * particleScale, particleAlpha);
    }

    @Override
    public int getBrightnessForRender(float partial) {
        return 0xF000F0;
    }

    @Override
    public float getBrightness(float partial) {
        return 1F;
    }

    /**
     * A particle's quad with its own texture inside EffectRenderer's batch of particles.png: the batch drawn, ours bound and drawn, the
     * batch's texture bound again and a new batch started.
     */
    static void drawOwn(Tessellator t, ResourceLocation tex, EntityFX fx, float partial, float rx, float rz, float ryz, float rxy, float rxz,
                        float u0, float u1, float v0, float v1, float size, float alpha) {
        t.draw();
        Minecraft.getMinecraft().getTextureManager().bindTexture(tex);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.004F);
        t.startDrawingQuads();
        t.setBrightness(fx.getBrightnessForRender(partial));
        float x = (float) (fx.prevPosX + (fx.posX - fx.prevPosX) * partial - interpPosX);
        float y = (float) (fx.prevPosY + (fx.posY - fx.prevPosY) * partial - interpPosY);
        float z = (float) (fx.prevPosZ + (fx.posZ - fx.prevPosZ) * partial - interpPosZ);
        t.setColorRGBA_F(1F, 1F, 1F, alpha);
        t.addVertexWithUV(x - rx * size - ryz * size, y - rz * size, z - rxy * size - rxz * size, u1, v1);
        t.addVertexWithUV(x - rx * size + ryz * size, y + rz * size, z - rxy * size + rxz * size, u1, v0);
        t.addVertexWithUV(x + rx * size + ryz * size, y + rz * size, z + rxy * size + rxz * size, u0, v0);
        t.addVertexWithUV(x + rx * size - ryz * size, y - rz * size, z + rxy * size - rxz * size, u0, v1);
        t.draw();
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.1F);
        Minecraft.getMinecraft().getTextureManager().bindTexture(PARTICLES);
        t.startDrawingQuads();
    }
}
