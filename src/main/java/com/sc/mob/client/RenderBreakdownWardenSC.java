package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.emp.ItemEmpGunSC;
import com.sc.mob.EntityBreakdownWardenSC;
import com.sc.mob.IMobActionSC;
import com.sc.mob.MobCombatSC;
import com.sc.mob.WardenRulesSC;
import com.sc.util.BladeType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.RenderLiving;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.boss.BossStatus;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.1, §5.8, rules ПС-9, ПС-50): the Breakdown Warden - ModelBreakdownWardenSC x SCALE
 * (3.5 blocks), the boss bar (BossStatus.setBossStatus in doRender, as vanilla's Wither: only while it is drawn), and over the model:
 * <ul>
 * <li>the reactor's core and the visor full-bright - the core pale gold in «Удержание» (the Lithographers' light), violet-white in
 * «Пробой», red in «Перегрузка», flaring while the core winds up (FLAG_RESET_ARMED);</li>
 * <li>the coils' shield (FLAG_SHIELDED) - a faint blue shell round the whole figure; the window (FLAG_WINDOW) - none;</li>
 * <li>the highlighted piece (DW_HIGHLIGHT) glowing white, pulsing - the helmet, the chestplate (the torso), the leggings (the hips,
 * the legs, the skirt), the boots; SET_ALL - the whole figure faintly blue (the set suppressed);</li>
 * <li>the overheat (FLAG_OVERHEAT) - the chestplate white-hot, orange at the edges, flickering;</li>
 * <li>the Singular Blade in the upper right hand, the ПЭ-1М in the upper left (the held stack; without it the blade is the held one and
 * the left hand is empty), the left hand blue while a Point charges (ACT_EMP_POINT).</li>
 * </ul>
 * The glows are the model's own parts drawn again additively (the texture kept, so the plates' lines glow), never a new shape.
 */
@SideOnly(Side.CLIENT)
public class RenderBreakdownWardenSC extends RenderLiving {

    /** The model is 32 px tall: x1.75 - 3.5 blocks (WardenRulesSC.HEIGHT). */
    public static final float SCALE = WardenRulesSC.HEIGHT / 2F;
    /** The core by phase, the visor, the highlight, the shield, the overheat, a Point charging. */
    public static final int CORE_HOLD = 0xF0E2B4, CORE_BREAKDOWN = 0xD2B4FF, CORE_OVERLOAD = 0xFF5A32, VISOR = 0xF0E2B4, HIGHLIGHT = 0xFFFFFF,
            SHIELD = 0x78C8FF, SET = 0x5AA0FF, HOT = 0xFFD2A0, CHARGE = 0x5AA0FF;
    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/" + EntityBreakdownWardenSC.NAME + ".png");
    private static final float PX = 0.0625F;

    private final ModelBreakdownWardenSC model;
    /** The blade drawn in the upper right hand while it holds the ПЭ-1М (the client's own stack, made once). */
    private ItemStack blade;

    public RenderBreakdownWardenSC() {
        this(new ModelBreakdownWardenSC());
    }

    private RenderBreakdownWardenSC(ModelBreakdownWardenSC model) {
        super(model, 1.2F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return SKIN;
    }

    /** ПС-50: the bar - set every frame it is drawn (vanilla's Wither does the same). */
    @Override
    public void doRender(EntityLiving e, double x, double y, double z, float yaw, float pt) {
        if (e instanceof EntityBreakdownWardenSC) {
            BossStatus.setBossStatus((EntityBreakdownWardenSC) e, true);
        }
        super.doRender(e, x, y, z, yaw, pt);
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float pt) {
        super.preRenderCallback(e, pt);
        GL11.glScalef(SCALE, SCALE, SCALE);
    }

    /** The model, then its glows in the same pose (the shield's shell, the highlight, the overheat). */
    @Override
    protected void renderModel(EntityLivingBase e, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        super.renderModel(e, limbSwing, limbAmount, age, yaw, pitch, scale);
        if (!(e instanceof EntityBreakdownWardenSC) || e.isInvisible() || e.deathTime > 0) {
            return;
        }
        EntityBreakdownWardenSC b = (EntityBreakdownWardenSC) e;
        bindEntityTexture(e);
        float pulse = 0.5F + 0.5F * (float) Math.sin(age * 0.35F);
        if (b.phaseFlag(EntityBreakdownWardenSC.FLAG_SHIELDED)) {           // the coils' shield: a shell round it all
            GL11.glPushMatrix();
            GL11.glTranslatef(0F, 0.5F, 0F);
            GL11.glScalef(1.08F, 1.04F, 1.08F);
            GL11.glTranslatef(0F, -0.5F, 0F);
            glowAll(SHIELD, 0.12F + 0.06F * pulse, scale);
            GL11.glPopMatrix();
        }
        int h = b.highlight();
        if (h == EntityBreakdownWardenSC.SET_ALL) {
            glowAll(SET, 0.25F + 0.15F * pulse, scale);
        } else if (h >= 0 && h <= 3) {
            float a = 0.45F + 0.45F * pulse;
            switch (h) {
                case 0:
                    glow(model.head, HIGHLIGHT, a, scale);
                    break;
                case 1:
                    glow(model.body, HIGHLIGHT, a, scale);
                    break;
                case 2:
                    glow(model.hips, HIGHLIGHT, a, scale);
                    glow(model.legR, HIGHLIGHT, a * 0.8F, scale);
                    glow(model.legL, HIGHLIGHT, a * 0.8F, scale);
                    for (ModelRenderer c : model.cables) {
                        glow(c, HIGHLIGHT, a * 0.6F, scale);
                    }
                    break;
                default:
                    glowChild(model.legR, model.footR, HIGHLIGHT, a, scale);
                    glowChild(model.legL, model.footL, HIGHLIGHT, a, scale);
                    break;
            }
        }
        if (b.phaseFlag(EntityBreakdownWardenSC.FLAG_OVERHEAT)) {           // white-hot
            float flicker = 0.7F + 0.3F * (float) Math.sin(age * 1.7F);
            glow(model.body, HOT, flicker, scale);
            glow(model.padR, CORE_OVERLOAD, 0.5F * flicker, scale);
            glow(model.padL, CORE_OVERLOAD, 0.5F * flicker, scale);
        }
    }

    /** The core, the visor, the hands' items and the Point's charge-up (after the model, in its space). */
    @Override
    protected void renderEquippedItems(EntityLivingBase e, float pt) {
        super.renderEquippedItems(e, pt);
        if (!(e instanceof EntityBreakdownWardenSC) || e.deathTime > 0) {
            return;
        }
        EntityBreakdownWardenSC b = (EntityBreakdownWardenSC) e;
        float age = e.ticksExisted + pt;
        int phase = b.phase();
        int core = phase == WardenRulesSC.PHASE_OVERLOAD ? CORE_OVERLOAD : phase == WardenRulesSC.PHASE_BREAKDOWN ? CORE_BREAKDOWN : CORE_HOLD;
        float k = b.phaseFlag(EntityBreakdownWardenSC.FLAG_RESET_ARMED) ? 0.6F + 0.4F * (float) Math.abs(Math.sin(age * 0.9F))
                : 0.8F + 0.2F * (float) Math.sin(age * 0.2F);
        if (b.phaseFlag(EntityBreakdownWardenSC.FLAG_RESET_ARMED)) {
            core = 0xFFFFFF;                                    // the core winds up: white
        }
        GL11.glPushMatrix();
        model.body.postRender(PX);
        RenderSubstrateMobSC.lamp(model.core, core, k);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        model.head.postRender(PX);
        RenderSubstrateMobSC.lamp(model.visor, VISOR, 0.85F);
        GL11.glPopMatrix();
        ItemStack held = e.getHeldItem();
        boolean gun = ItemEmpGunSC.isGun(held);
        ItemStack inRight = gun ? blade() : held;
        if (inRight != null) {
            item(e, model.armUR, inRight, true);
        }
        if (gun) {
            item(e, model.armUL, held, false);
            if (((IMobActionSC) e).mobAction() == IMobActionSC.ACT_EMP_POINT) {
                GL11.glPushMatrix();
                model.armUL.postRender(PX);
                RenderSubstrateMobSC.lamp(model.handUL, CHARGE, 0.6F + 0.4F * (float) Math.sin(age * 1.2F));
                GL11.glPopMatrix();
            }
        }
    }

    private ItemStack blade() {
        if (blade == null) {
            blade = MobCombatSC.litBlade(BladeType.SINGULAR);
        }
        return blade;
    }

    /** A held stack in one of the upper hands - vanilla's RenderBiped placement (the left one mirrored across). */
    private void item(EntityLivingBase e, ModelRenderer arm, ItemStack s, boolean right) {
        if (s == null || s.getItem() == null) {
            return;
        }
        GL11.glPushMatrix();
        arm.postRender(PX);
        GL11.glTranslatef(right ? -PX : PX, 0.4375F + PX * 1.5F, PX);
        if (s.getItem().isFull3D()) {
            float f = 0.625F;
            if (s.getItem().shouldRotateAroundWhenRendering()) {
                GL11.glRotatef(180F, 0F, 0F, 1F);
                GL11.glTranslatef(0F, -0.125F, 0F);
            }
            GL11.glTranslatef(0F, 0.1875F, 0F);
            GL11.glScalef(f, -f, f);
            GL11.glRotatef(-100F, 1F, 0F, 0F);
            GL11.glRotatef(right ? 45F : -45F, 0F, 1F, 0F);
        } else {
            float f = 0.375F;
            GL11.glTranslatef(right ? 0.25F : -0.25F, 0.1875F, -0.1875F);
            GL11.glScalef(f, f, f);
            GL11.glRotatef(right ? 60F : -60F, 0F, 0F, 1F);
            GL11.glRotatef(-90F, 1F, 0F, 0F);
            GL11.glRotatef(right ? 20F : -20F, 0F, 0F, 1F);
        }
        int passes = s.getItem().requiresMultipleRenderPasses() ? s.getItem().getRenderPasses(s.getItemDamage()) : 1;
        for (int i = 0; i < passes; i++) {
            int c = s.getItem().getColorFromItemStack(s, i);
            GL11.glColor4f((c >> 16 & 255) / 255F, (c >> 8 & 255) / 255F, (c & 255) / 255F, 1F);
            renderManager.itemRenderer.renderItem(e, s, i);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    // ------------------------------------------------------------------ the glows: a part again, additive, full-bright

    private void glowAll(int rgb, float alpha, float scale) {
        begin(rgb, alpha);
        model.body.render(scale);
        model.head.render(scale);
        model.armUR.render(scale);
        model.armUL.render(scale);
        model.armLR.render(scale);
        model.armLL.render(scale);
        model.hips.render(scale);
        model.legR.render(scale);
        model.legL.render(scale);
        for (ModelRenderer c : model.cables) {
            c.render(scale);
        }
        end();
    }

    private void glow(ModelRenderer part, int rgb, float alpha, float scale) {
        begin(rgb, alpha);
        part.render(scale);
        end();
    }

    /** A child part glowing alone (the boots under the legs): its parent's transform, then the child. */
    private void glowChild(ModelRenderer parent, ModelRenderer child, int rgb, float alpha, float scale) {
        GL11.glPushMatrix();
        parent.postRender(scale);
        begin(rgb, alpha);
        child.render(scale);
        end();
        GL11.glPopMatrix();
    }

    private float lastX, lastY;

    private void begin(int rgb, float alpha) {
        lastX = OpenGlHelper.lastBrightnessX;
        lastY = OpenGlHelper.lastBrightnessY;
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_DEPTH_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_CURRENT_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        GL11.glDepthMask(false);
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        GL11.glColor4f((rgb >> 16 & 255) / 255F, (rgb >> 8 & 255) / 255F, (rgb & 255) / 255F, Math.max(0F, Math.min(1F, alpha)));
    }

    private void end() {
        OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        GL11.glPopAttrib();
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }
}
