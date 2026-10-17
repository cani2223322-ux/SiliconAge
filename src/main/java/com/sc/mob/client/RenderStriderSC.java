package com.sc.mob.client;

import java.util.Random;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.EntityStriderSC;
import com.sc.mob.IMobActionSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f4.md §7.8, §7.11, rules ПС-9, ПС-38): the Arc Strider - ModelStriderSC drawn x SCALE, and its
 * arc: a jagged full-bright blue-white line between the electrode balls, new every frame (a living discharge), bowing up; while
 * it charges the chain lightning (ACT_ARC) it swells - thicker, FILAMENTS more strands, the balls white - the cue to break the
 * line of sight or ready a Pulse. Collapsed by a Pulse (ACT_COLLAPSED) it lies on its side, the arc gone, the balls dark. Every
 * client draws it from the DataWatcher; the lightning's jumps themselves are the server's sparks.
 */
@SideOnly(Side.CLIENT)
public class RenderStriderSC extends RenderLiving {

    public static final float SCALE = 2F;
    /** The arc and the balls; the balls charging, collapsed. */
    public static final int ARC = 0x9FD8FF, BALL = 0x7FC8FF, BALL_HOT = 0xF0FAFF, BALL_DEAD = 0x303A44;
    private static final int SEGMENTS = 9, FILAMENTS = 3;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityStriderSC.NAME + ".png");

    private final ModelStriderSC model;
    private final Random jitter = new Random();

    public RenderStriderSC() {
        this(new ModelStriderSC());
    }

    private RenderStriderSC(ModelStriderSC model) {
        super(model, 1.0F);
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

    /** Collapsed: it lies on its side (its feet a little up, so the legs don't sink). */
    @Override
    protected void rotateCorpse(EntityLivingBase e, float age, float yaw, float pt) {
        super.rotateCorpse(e, age, yaw, pt);
        if (e.deathTime <= 0 && ((IMobActionSC) e).mobAction() == IMobActionSC.ACT_COLLAPSED) {
            GL11.glTranslatef(0F, 0.6F, 0F);
            GL11.glRotatef(80F, 0F, 0F, 1F);
        }
    }

    /** The balls and the arc (after the model, in the hub's space). */
    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        int act = ((IMobActionSC) e).mobAction();
        boolean collapsed = act == IMobActionSC.ACT_COLLAPSED || e.deathTime > 0;
        boolean charging = act == IMobActionSC.ACT_ARC;
        float t = e.ticksExisted + pt;
        GL11.glPushMatrix();
        model.hub.postRender(0.0625F);
        GL11.glPushMatrix();
        model.prongL.postRender(0.0625F);
        NoiseClientSC.lamp(model.ballL, collapsed ? BALL_DEAD : charging ? BALL_HOT : BALL, collapsed ? 1F : 0.8F + 0.2F * (float) Math.sin(t * 1.1F));
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        model.prongR.postRender(0.0625F);
        NoiseClientSC.lamp(model.ballR, collapsed ? BALL_DEAD : charging ? BALL_HOT : BALL, collapsed ? 1F : 0.8F + 0.2F * (float) Math.sin(t * 1.3F));
        GL11.glPopMatrix();
        if (!collapsed) {
            arc(charging, t, e.getEntityId());
        }
        GL11.glPopMatrix();
    }

    /** The arc between the balls in the hub's space (1/16 units), new jags every frame. */
    private void arc(boolean charging, float t, int seed) {
        float x = ModelStriderSC.PRONG_X / 16F, y = ModelStriderSC.PRONG_TIP_Y / 16F;
        jitter.setSeed((long) (t * 3F) * 31L + seed);
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_CURRENT_BIT | GL11.GL_LINE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        NoiseClientSC.brightOn();
        int strands = charging ? 1 + FILAMENTS : 1;
        float bow = charging ? 0.22F : 0.12F, jag = charging ? 0.06F : 0.035F;
        for (int s = 0; s < strands; s++) {
            for (int pass = 0; pass < 2; pass++) {
                GL11.glLineWidth(pass == 0 ? (charging ? 6F : 4F) : (charging ? 2.5F : 1.5F));
                Tessellator tes = Tessellator.instance;
                tes.startDrawing(GL11.GL_LINE_STRIP);
                tes.setColorRGBA_F(NoiseClientSC.r(ARC), NoiseClientSC.g(ARC), NoiseClientSC.b(ARC), pass == 0 ? 0.25F : 0.95F);
                float extra = s == 0 ? 0F : (s * 0.07F);
                for (int i = 0; i <= SEGMENTS; i++) {
                    float k = i / (float) SEGMENTS;
                    float mid = (float) Math.sin(k * Math.PI);
                    float jx = i == 0 || i == SEGMENTS ? 0F : (jitter.nextFloat() - 0.5F) * jag;
                    float jy = i == 0 || i == SEGMENTS ? 0F : (jitter.nextFloat() - 0.5F) * jag * 2F;
                    float jz = i == 0 || i == SEGMENTS ? 0F : (jitter.nextFloat() - 0.5F) * jag * 2F + (s % 2 == 0 ? extra : -extra) * mid;
                    tes.addVertex(-x + 2F * x * k + jx, y - (bow + extra) * mid + jy, jz);   // model space: up is -y
                }
                tes.draw();
            }
        }
        NoiseClientSC.brightOff();
        GL11.glPopAttrib();
    }
}
