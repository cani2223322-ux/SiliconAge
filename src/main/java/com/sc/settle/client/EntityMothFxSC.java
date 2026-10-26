package com.sc.settle.client;

import com.sc.Reference;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.particle.EntityFX;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;
import net.minecraft.world.EnumSkyBlock;
import net.minecraft.world.World;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A, part 2): a moth of the Scribe Canyons at night - a client particle
 * (never an entity, never sent): it flutters (the texture's two wing frames, textures/particles/moth.png 32 x 16), drifts and, once in a
 * while, picks the nearest light of LIGHT_MIN or more within SEEK blocks and circles it. Lives LIFE_MIN..LIFE_MAX ticks. Client only.
 */
@SideOnly(Side.CLIENT)
public class EntityMothFxSC extends EntityFX {

    public static final ResourceLocation TEXTURE = new ResourceLocation(Reference.ASSETS, "textures/particles/moth.png");
    public static final int LIFE_MIN = 300, LIFE_MAX = 600, LIGHT_MIN = 8, SEEK = 6, SEEK_EVERY = 40;

    private double tx, ty, tz;
    private boolean target;

    public EntityMothFxSC(World w, double x, double y, double z) {
        super(w, x, y, z, 0D, 0D, 0D);
        particleMaxAge = LIFE_MIN + rand.nextInt(LIFE_MAX - LIFE_MIN + 1);
        particleScale = 0.8F + rand.nextFloat() * 0.4F;
        particleGravity = 0F;
        noClip = false;
        motionX = (rand.nextDouble() - 0.5D) * 0.05D;
        motionY = 0D;
        motionZ = (rand.nextDouble() - 0.5D) * 0.05D;
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
        if (particleAge % SEEK_EVERY == 1) {
            seek();
        }
        if (target) {
            double dx = tx - posX, dy = ty - posY, dz = tz - posZ, d = Math.max(0.3D, Math.sqrt(dx * dx + dy * dy + dz * dz));
            motionX += dx / d * 0.01D + (rand.nextDouble() - 0.5D) * 0.03D;
            motionY += dy / d * 0.01D + (rand.nextDouble() - 0.5D) * 0.03D;
            motionZ += dz / d * 0.01D + (rand.nextDouble() - 0.5D) * 0.03D;
        } else {
            motionX += (rand.nextDouble() - 0.5D) * 0.02D;
            motionY += (rand.nextDouble() - 0.5D) * 0.015D;
            motionZ += (rand.nextDouble() - 0.5D) * 0.02D;
        }
        motionX *= 0.9D;
        motionY *= 0.9D;
        motionZ *= 0.9D;
        moveEntity(motionX, motionY, motionZ);
        particleAlpha = particleAge < 20 ? particleAge / 20F : particleAge > particleMaxAge - 20 ? (particleMaxAge - particleAge) / 20F : 1F;
    }

    /** The nearest block lit LIGHT_MIN or more (by its block light) within SEEK - the moth's target; none - it drifts. */
    private void seek() {
        int x0 = MathHelper.floor_double(posX), y0 = MathHelper.floor_double(posY), z0 = MathHelper.floor_double(posZ);
        double best = Double.MAX_VALUE;
        target = false;
        for (int i = 0; i < 24; i++) {
            int x = x0 + rand.nextInt(2 * SEEK + 1) - SEEK, y = y0 + rand.nextInt(SEEK + 1) - SEEK / 2, z = z0 + rand.nextInt(2 * SEEK + 1) - SEEK;
            if (!worldObj.blockExists(x, y, z) || worldObj.getSavedLightValue(EnumSkyBlock.Block, x, y, z) < LIGHT_MIN) {
                continue;
            }
            double d = (x - posX) * (x - posX) + (y - posY) * (y - posY) + (z - posZ) * (z - posZ);
            if (d < best) {
                best = d;
                tx = x + 0.5D;
                ty = y + 0.8D;
                tz = z + 0.5D;
                target = true;
            }
        }
    }

    @Override
    public int getFXLayer() {
        return 0;
    }

    @Override
    public void renderParticle(Tessellator t, float partial, float rx, float rz, float ryz, float rxy, float rxz) {
        int frame = (particleAge / 3) % 2;                      // the wings: up, down
        float u0 = frame * 0.5F;
        EntityDroneFxSC.drawOwn(t, TEXTURE, this, partial, rx, rz, ryz, rxy, rxz, u0, u0 + 0.5F, 0F, 1F, 0.1F * particleScale, particleAlpha);
    }
}
