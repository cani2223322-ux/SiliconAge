package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.settle.GuardAlertSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.npc.EntityWardenSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.4, §6.9; rules ПГ-1, ПГ-6): a dome guard on vanilla's biped - its
 * undersuit textures/entity/warden.png (64 x 64 drawn at 128 x 128, tools/gen_settlement_mobs_tex.py); the worn mod
 * armour draws itself (RenderBiped -> ItemArmorSC.getArmorModel -> ModelArmorGlowSC: the turquoise light, the
 * reserve, «down», ПЭ-1 from the DataWatcher). Over the armour (ModelWardenSC): the dome-coloured armband on the left
 * arm (a Senior: on both). Full-bright after the model: the alarm lamp on the band and a pip on the helmet - by
 * DW_ALERT: calm - a dim turquoise, watching - red blinking slowly («свечение мигает красным»), warning - fast orange-red
 * (the blade drawn, the alarm), attacking - steady red. The name and rank over the head, coloured by the alert.
 */
@SideOnly(Side.CLIENT)
public class RenderWardenSC extends RenderBiped {

    /** The lamp by GuardAlertSC.CALM..ATTACKING. */
    public static final int[] LAMP = {0x3CD2C8, 0xFF4040, 0xFF6020, 0xFF3030};
    /** The name's colour by the alert. */
    public static final int[] NAME = {0xFFFFFF, 0xFFC040, 0xFF8040, 0xFF5050};

    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/warden.png");

    private final ModelWardenSC model;

    public RenderWardenSC() {
        this(new ModelWardenSC());
    }

    private RenderWardenSC(ModelWardenSC model) {
        super(model, 0.5F);
        this.model = model;
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving e) {
        return SKIN;
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float partial) {
        model.secondBand.showModel = ((EntityWardenSC) e).kind() == NpcKindSC.SENIOR_WARDEN;
    }

    /** After the model, the armour and the held item: the alarm lamps, full-bright. */
    @Override
    protected void renderEquippedItems(EntityLiving e, float partial) {
        super.renderEquippedItems(e, partial);
        EntityWardenSC g = (EntityWardenSC) e;
        if (g.deathTime > 0) {
            return;
        }
        int a = alert(g);
        float k;
        switch (a) {
            case GuardAlertSC.WATCHING:
                k = (g.ticksExisted / 10) % 2 == 0 ? 1F : 0.25F;
                break;
            case GuardAlertSC.WARNING:
                k = (g.ticksExisted / 3) % 2 == 0 ? 1F : 0.2F;
                break;
            case GuardAlertSC.ATTACKING:
                k = 1F;
                break;
            default:
                k = 0.7F;
        }
        GL11.glPushMatrix();
        model.bipedLeftArm.postRender(0.0625F);
        SettlementClientSC.lamp(model.bandLamp, LAMP[a], k);
        GL11.glPopMatrix();
        GL11.glPushMatrix();
        model.bipedHead.postRender(0.0625F);
        SettlementClientSC.lamp(model.helmetPip, LAMP[a], k);
        GL11.glPopMatrix();
    }

    private static int alert(EntityWardenSC g) {
        return Math.max(GuardAlertSC.CALM, Math.min(GuardAlertSC.ATTACKING, g.alert()));
    }

    /** The name (coloured by the alert; a warning blinks it) and the rank instead of vanilla's tag. */
    @Override
    protected void passSpecialRender(EntityLivingBase e, double x, double y, double z) {
        EntityWardenSC g = (EntityWardenSC) e;
        if (g.deathTime > 0 || g == renderManager.livingPlayer || g.riddenByEntity != null) {
            return;
        }
        int a = alert(g);
        int c = a == GuardAlertSC.WARNING && (g.ticksExisted / 3) % 2 != 0 ? NAME[GuardAlertSC.CALM] : NAME[a];
        SettlementClientSC.label(renderManager, getFontRendererFromRenderManager(), g, x, y, z, SettlementClientSC.name(g), c,
                SettlementClientSC.kind(g));
    }

    /**
     * The guard's biped (64 x 64 texture): vanilla's boxes and the armbands, a little wider than the armour's arm (its
     * outer model inflates 1.0) so they show over it; the lamps are drawn by the renderer.
     */
    @SideOnly(Side.CLIENT)
    public static class ModelWardenSC extends ModelBiped {

        /** Over the armour's sleeve. */
        public static final float BAND_INFLATE = 1.3F;

        final ModelRenderer band, secondBand;
        /** Full-bright, drawn by the renderer: on the left band's outside (left arm), on the helmet's top (head). */
        final ModelRenderer bandLamp, helmetPip;

        public ModelWardenSC() {
            super(0F, 0F, 64, 64);
            bipedHeadwear.showModel = false;
            band = new ModelRenderer(this, 0, 32);
            band.addBox(-1F, 3F, -2F, 4, 2, 4, BAND_INFLATE);
            bipedLeftArm.addChild(band);
            secondBand = new ModelRenderer(this, 0, 32);
            secondBand.addBox(-3F, 3F, -2F, 4, 2, 4, BAND_INFLATE);
            bipedRightArm.addChild(secondBand);
            bandLamp = new ModelRenderer(this, 56, 60);
            bandLamp.addBox(4F, 3.5F, -0.5F, 1, 1, 1);
            helmetPip = new ModelRenderer(this, 56, 60);
            helmetPip.addBox(-0.5F, -9.7F, 1F, 1, 1, 1);
        }
    }
}
