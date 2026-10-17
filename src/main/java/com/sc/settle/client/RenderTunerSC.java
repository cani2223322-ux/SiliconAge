package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.settle.NpcKindSC;
import com.sc.settle.npc.EntityTunerSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.entity.RenderBiped;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.2, §6.9; rules ПЖ-6, ПЖ-7): a Tuner on vanilla's biped, drawn thin and
 * 1.9 high (SCALE_XZ, SCALE_Y), its skin textures/entity/tuner_<kind>.png (a 64 x 64 layout drawn at 128 x 128:
 * vanilla's biped in the upper half, the extra parts below - tools/gen_settlement_mobs_tex.py). The extra parts
 * (ModelTunerSC): the respirator, the pack with the lamp's stalk; by profession - the Elder's mantle and cloak «in the
 * field's colour», the Technologist's loupe over the right eye, the Metallurgist's apron, the Gas Technician's two
 * cylinders, the Cartographer's antenna. Full-bright after the model (renderEquippedItems: the parts' own matrices):
 * the two diode eyes in the profession's colour (the armour's light colours, ModelArmorGlowSC.COLORS) and the mood
 * lamp - on the stalk and a pip on the chest strap - green / yellow / red (DW_MOOD; «враг рядом» blinks). The name
 * and profession over the head (SettlementClientSC.label).
 */
@SideOnly(Side.CLIENT)
public class RenderTunerSC extends RenderBiped {

    /** Thin and 1.9 high (the biped is 2.0). */
    public static final float SCALE_XZ = 0.88F, SCALE_Y = 0.95F;
    /**
     * The diode eyes by NpcKindSC ordinal (ModelArmorGlowSC.COLORS): the Elder the dome's turquoise, the Technologist
     * blue, the Metallurgist orange, the Gas Technician violet, the Cartographer yellow.
     */
    public static final int[] DIODES = {0x40E0FF, 0x4A7CFF, 0xFF9A30, 0xB060FF, 0xFFE040};
    /** The mood lamp: calm, alarm, an enemy near. */
    public static final int[] MOOD = {0x50FF70, 0xFFE040, 0xFF4040};

    private final ModelTunerSC model;
    private final ResourceLocation[] skins = new ResourceLocation[NpcKindSC.values().length];

    public RenderTunerSC() {
        this(new ModelTunerSC());
    }

    private RenderTunerSC(ModelTunerSC model) {
        super(model, 0.45F);
        this.model = model;
        for (NpcKindSC k : NpcKindSC.values()) {
            skins[k.ordinal()] = new ResourceLocation(Reference.ASSETS, "textures/entity/tuner_" + (k.guard ? NpcKindSC.ELDER.key : k.key) + ".png");
        }
    }

    @Override
    protected ResourceLocation getEntityTexture(EntityLiving e) {
        return skins[((EntityTunerSC) e).kind().ordinal()];
    }

    @Override
    protected void preRenderCallback(EntityLivingBase e, float partial) {
        model.setKind(((EntityTunerSC) e).kind());
        GL11.glScalef(SCALE_XZ, SCALE_Y, SCALE_XZ);
    }

    /** After the model and the held item: the diodes and the mood lamp, full-bright. */
    @Override
    protected void renderEquippedItems(EntityLiving e, float partial) {
        super.renderEquippedItems(e, partial);
        EntityTunerSC t = (EntityTunerSC) e;
        if (t.deathTime > 0) {
            return;
        }
        NpcKindSC k = t.kind();
        int diode = DIODES[Math.min(DIODES.length - 1, k.ordinal())];
        GL11.glPushMatrix();
        model.bipedHead.postRender(0.0625F);
        SettlementClientSC.lamp(model.eyes, diode, 1F);
        GL11.glPopMatrix();
        int m = Math.max(0, Math.min(MOOD.length - 1, t.mood()));
        float k1 = 1F;
        if (m == EntityTunerSC.MOOD_DANGER && (t.ticksExisted / 4) % 2 == 0) {
            k1 = 0.35F;                                         // «враг рядом»: a fast blink
        }
        GL11.glPushMatrix();
        model.bipedBody.postRender(0.0625F);
        SettlementClientSC.lamp(model.lamp, MOOD[m], k1);
        if (k == NpcKindSC.CARTOGRAPHER) {
            SettlementClientSC.lamp(model.antennaTip, diode, 0.6F + 0.4F * (float) Math.abs(Math.sin(t.ticksExisted * 0.1D)));
        }
        GL11.glPopMatrix();
    }

    /** The name and the profession instead of vanilla's tag. */
    @Override
    protected void passSpecialRender(EntityLivingBase e, double x, double y, double z) {
        EntityTunerSC t = (EntityTunerSC) e;
        if (t.deathTime > 0 || t == renderManager.livingPlayer || t.riddenByEntity != null) {
            return;
        }
        SettlementClientSC.label(renderManager, getFontRendererFromRenderManager(), t, x, y, z, SettlementClientSC.name(t), 0xFFFFFF,
                SettlementClientSC.kind(t));
    }

    /**
     * The Tuner's biped: vanilla's boxes (64 x 64 texture: the upper half is the usual skin, its hat area unused) and
     * its own parts below - the box sizes and texture offsets match tools/gen_settlement_mobs_tex.py.
     */
    @SideOnly(Side.CLIENT)
    public static class ModelTunerSC extends ModelBiped {

        /** On the body / head - shown for every kind. */
        final ModelRenderer pack, stalk, mask;
        /** By profession. */
        final ModelRenderer collar, cloak, loupe, apron, cylinders, antenna;
        /** Full-bright, drawn by the renderer (never in render()): the diode eyes (head), the lamp and the chest pip (body), the antenna's tip (body). */
        final ModelRenderer eyes, lamp, antennaTip;

        public ModelTunerSC() {
            super(0F, 0F, 64, 64);
            bipedHeadwear.showModel = false;
            pack = part(bipedBody, 0, 32, -3F, 1F, 2F, 6, 8, 3);
            stalk = part(bipedBody, 20, 32, 2F, -2F, 4F, 1, 3, 1);
            mask = part(bipedHead, 24, 32, -2.5F, -3.5F, -5F, 5, 3, 1);
            collar = part(bipedBody, 0, 44, -4.5F, -0.5F, -2.5F, 9, 2, 5);
            cloak = part(bipedBody, 36, 32, -4F, -0.5F, 2F, 8, 14, 1);
            loupe = part(bipedHead, 40, 52, -4F, -6.5F, -6F, 3, 3, 2);
            apron = part(bipedBody, 0, 52, -3.5F, 3F, -3F, 7, 10, 1);
            cylinders = part(bipedBody, 16, 52, -3.5F, 0F, 5F, 3, 9, 3);
            cylinders.addBox(0.5F, 0F, 5F, 3, 9, 3);
            antenna = part(bipedBody, 28, 52, -3F, -8F, 4F, 1, 9, 1);
            eyes = loose(-3F, -5F, -4.3F, 2, 1, 1);
            eyes.addBox(1F, -5F, -4.3F, 2, 1, 1);
            lamp = loose(1.5F, -4F, 4F, 2, 2, 2);
            lamp.addBox(2F, 2F, -2.5F, 1, 1, 1);                 // the pip on the chest strap
            antennaTip = loose(-3.5F, -10F, 3.5F, 2, 2, 2);
        }

        private ModelRenderer part(ModelRenderer parent, int u, int v, float x, float y, float z, int w, int h, int d) {
            ModelRenderer r = new ModelRenderer(this, u, v);
            r.addBox(x, y, z, w, h, d);
            parent.addChild(r);
            return r;
        }

        /** A part the renderer draws itself in its parent's matrix (not a child: render() never draws it). */
        private ModelRenderer loose(float x, float y, float z, int w, int h, int d) {
            ModelRenderer r = new ModelRenderer(this, 56, 60);
            r.addBox(x, y, z, w, h, d);
            return r;
        }

        /** The profession's parts on, the others off. */
        void setKind(NpcKindSC k) {
            collar.showModel = cloak.showModel = k == NpcKindSC.ELDER;
            loupe.showModel = k == NpcKindSC.TECHNOLOGIST;
            apron.showModel = k == NpcKindSC.METALLURGIST;
            cylinders.showModel = k == NpcKindSC.GAS_TECH;
            antenna.showModel = k == NpcKindSC.CARTOGRAPHER;
        }
    }
}
