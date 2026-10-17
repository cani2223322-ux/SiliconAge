package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.mob.IMobActionSC;
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
 * <p>
 * Ф3, package S (docs/plan-substrate-tech-f3.md §6.1, §6.4, §6.5): the Captain and the Commandant wear their own
 * undersuits (warden_captain.png, warden_commandant.png - tools/gen_settlement3_tex.py), the armband on both arms and a
 * cloak over the armour's back - the Captain's in the dome's turquoise, the Commandant's near-black with the dome's trim,
 * and his epaulettes; their suits (Exo with helium, the Singular at its highest level in the settlement's colours) draw
 * themselves. The Captain's wind-up (DW_ACTION = IMobActionSC.ACT_WINDUP, 20 ticks before his heavy blow) - a band of
 * light round his blade's wrist swelling from orange to white, flickering faster as the blow comes.
 */
@SideOnly(Side.CLIENT)
public class RenderWardenSC extends RenderBiped {

    /** The lamp by GuardAlertSC.CALM..ATTACKING. */
    public static final int[] LAMP = {0x3CD2C8, 0xFF4040, 0xFF6020, 0xFF3030};
    /** The name's colour by the alert. */
    public static final int[] NAME = {0xFFFFFF, 0xFFC040, 0xFF8040, 0xFF5050};

    private static final ResourceLocation SKIN = new ResourceLocation(Reference.ASSETS, "textures/entity/warden.png");
    /** Ф3: the chiefs' undersuits. */
    private static final ResourceLocation SKIN_CAPTAIN = new ResourceLocation(Reference.ASSETS, "textures/entity/warden_captain.png"),
            SKIN_COMMANDANT = new ResourceLocation(Reference.ASSETS, "textures/entity/warden_commandant.png");
    /** Ф3: the wind-up's light, from its start to the blow (mixed by the wind-up's flicker). */
    public static final int WINDUP_FROM = 0xFF9A30, WINDUP_TO = 0xFFF4D8;

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
        NpcKindSC k = ((EntityWardenSC) e).kind();
        return k == NpcKindSC.CAPTAIN ? SKIN_CAPTAIN : k == NpcKindSC.COMMANDANT ? SKIN_COMMANDANT : SKIN;
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float partial) {
        NpcKindSC k = ((EntityWardenSC) e).kind();
        model.secondBand.showModel = k == NpcKindSC.SENIOR_WARDEN || k.chief();
        model.cloak.showModel = k.chief();
        model.epaulettes.showModel = model.epauletteLeft.showModel = k == NpcKindSC.COMMANDANT;
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
        if (g.mobAction() == IMobActionSC.ACT_WINDUP) {        // Ф3: the Captain's blade charging for the heavy blow
            int t = g.ticksExisted;
            float pulse = 0.6F + 0.4F * (float) Math.abs(Math.sin((t + partial) * 0.9D));
            GL11.glPushMatrix();
            model.bipedRightArm.postRender(0.0625F);
            SettlementClientSC.lamp(model.windup, mix(WINDUP_FROM, WINDUP_TO, (t / 2) % 2 == 0 ? 0.7F : 0.2F), pulse);
            GL11.glPopMatrix();
        }
    }

    /** RGB a..b at t (0..1). */
    private static int mix(int a, int b, float t) {
        int r = (int) ((a >> 16 & 255) + ((b >> 16 & 255) - (a >> 16 & 255)) * t);
        int g = (int) ((a >> 8 & 255) + ((b >> 8 & 255) - (a >> 8 & 255)) * t);
        int bl = (int) ((a & 255) + ((b & 255) - (a & 255)) * t);
        return r << 16 | g << 8 | bl;
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
     * outer model inflates 1.0) so they show over it; the lamps are drawn by the renderer. Ф3: the chiefs' cloak behind
     * the armour's back (UV 16, 32), the Commandant's epaulettes over its shoulders (UV 36, 32), the wind-up's wrist band
     * (full-bright, the renderer's) - the offsets match tools/gen_settlement3_tex.py.
     */
    @SideOnly(Side.CLIENT)
    public static class ModelWardenSC extends ModelBiped {

        /** Over the armour's sleeve. */
        public static final float BAND_INFLATE = 1.3F;

        /** Over the armour's back and shoulders: the cloak hangs this far behind the body (the armour's chest inflates 1.0). */
        public static final float CLOAK_Z = 3.1F, EPAULETTE_INFLATE = 1.35F, WINDUP_INFLATE = 1.45F;

        final ModelRenderer band, secondBand;
        /** Full-bright, drawn by the renderer: on the left band's outside (left arm), on the helmet's top (head). */
        final ModelRenderer bandLamp, helmetPip;
        /** Ф3: the chiefs' cloak (body), the Commandant's epaulettes (both arms); the wind-up's band (right arm, full-bright). */
        final ModelRenderer cloak, epaulettes, epauletteLeft, windup;

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
            cloak = new ModelRenderer(this, 16, 32);
            cloak.addBox(-4.5F, -0.5F, CLOAK_Z, 9, 14, 1);
            bipedBody.addChild(cloak);
            epaulettes = new ModelRenderer(this, 36, 32);
            epaulettes.addBox(-3F, -2F, -2F, 4, 1, 4, EPAULETTE_INFLATE);
            bipedRightArm.addChild(epaulettes);
            epauletteLeft = new ModelRenderer(this, 36, 32);
            epauletteLeft.mirror = true;
            epauletteLeft.addBox(-1F, -2F, -2F, 4, 1, 4, EPAULETTE_INFLATE);
            bipedLeftArm.addChild(epauletteLeft);
            windup = new ModelRenderer(this, 56, 60);
            windup.addBox(-3F, 7.5F, -2F, 4, 2, 4, WINDUP_INFLATE);
        }
    }
}
