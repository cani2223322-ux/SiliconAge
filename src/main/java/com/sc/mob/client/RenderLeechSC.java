package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityLeechSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.client.renderer.entity.RenderManager;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.6, §7.11, rule ПС-9): the Current Leech - ModelLeechSC. Coiled (DW_PREY) it is
 * drawn at its prey's own interpolated place (the server moves it there every tick, but its position packets come only every
 * few ticks - drawn at its own it would lag a step behind the legs it is wound round); the prongs glow copper-blue then, brighter
 * as it drinks, dim cyan at rest; stunned (ACT_STUNNED) they are dark.
 */
@SideOnly(Side.CLIENT)
public class RenderLeechSC extends RenderLiving {

    /** The prongs: drinking, at rest. */
    public static final int PRONG_ON = 0x7FE6FF, PRONG_IDLE = 0x2E6F80, PRONG_OFF = 0x202020;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityLeechSC.NAME + ".png");

    private final ModelLeechSC model;

    public RenderLeechSC() {
        this(new ModelLeechSC());
    }

    private RenderLeechSC(ModelLeechSC model) {
        super(model, 0.4F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float pt) {
        EntityLeechSC l = (EntityLeechSC) e;
        if (l.coiled()) {
            Entity prey = l.worldObj.getEntityByID(l.preyId());
            if (prey != null && !prey.isDead) {
                x = prey.lastTickPosX + (prey.posX - prey.lastTickPosX) * pt - RenderManager.renderPosX;
                y = prey.lastTickPosY + (prey.posY - prey.lastTickPosY) * pt - RenderManager.renderPosY;
                z = prey.lastTickPosZ + (prey.posZ - prey.lastTickPosZ) * pt - RenderManager.renderPosZ;
                if (prey instanceof EntityPlayer) {
                    y -= prey.yOffset;                          // a client's own player stands yOffset under its posY (others: 0)
                }
            }
        }
        super.doRender(e, x, y, z, yaw, pt);
    }

    /** The prongs' glow (after the model, in its space - the head is a root part). */
    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        if (e.deathTime > 0) {
            return;
        }
        EntityLeechSC l = (EntityLeechSC) e;
        int act = l.mobAction();
        int rgb = act == IMobActionSC.ACT_STUNNED ? PRONG_OFF : l.coiled() ? PRONG_ON : PRONG_IDLE;
        float k = l.coiled() ? 0.75F + 0.25F * (float) Math.sin((e.ticksExisted + pt) * 0.9F) : 1F;
        GL11.glPushMatrix();
        model.head.postRender(0.0625F);
        NoiseClientSC.lamp(model.prongs, rgb, k);
        GL11.glPopMatrix();
    }
}
