package com.sc.settle.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.manual.Lang;
import com.sc.settle.caravan.CaravanRunSC;
import com.sc.settle.caravan.EntityCaravanMasterSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.2, §8.4; rule ПН-13): the caravan master - a Tuner (RenderTunerSC: its Cartographer's
 * skin, the respirator's diodes, the mood lamp, the antenna) carrying a pack frame of two steel rails on its back, past the battery
 * pack, with two crates strapped in it (ModelPackSC; textures/entity/caravanMaster.png - tools/gen_caravan_tex.py). Full-bright on the
 * frame's left rail: the caravan's lamp by EntityCaravanMasterSC.DW_CARAVAN - turquoise on its way, red and blinking while ambushed,
 * green when it has arrived. The name over the head, «Караванщик» under it (the entity's name) instead of the profession.
 */
@SideOnly(Side.CLIENT)
public class RenderCaravanMasterSC extends RenderTunerSC {

    public static final ResourceLocation PACK = new ResourceLocation(Reference.ASSETS, "textures/entity/caravanMaster.png");
    /** The caravan's lamp by CaravanRunSC.WALKING / AMBUSHED / ARRIVED: the Tuners' turquoise, red, green. */
    public static final int[] LAMP = {0x48F0DC, 0xFF4040, 0x50FF70};

    private final ModelPackSC pack = new ModelPackSC();

    public RenderCaravanMasterSC() {
        super();
    }

    /** After the Tuner's own (its diodes, its mood lamp): the frame and the crates in the body's matrix, the caravan's lamp. */
    @Override
    protected void renderEquippedItems(EntityLiving e, float partial) {
        super.renderEquippedItems(e, partial);
        EntityCaravanMasterSC m = (EntityCaravanMasterSC) e;
        GL11.glPushMatrix();
        modelBipedMain.bipedBody.postRender(0.0625F);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        bindTexture(PACK);
        pack.renderPack(0.0625F);
        if (m.deathTime <= 0) {
            int st = Math.max(0, Math.min(LAMP.length - 1, m.caravanState()));
            float k = st == CaravanRunSC.AMBUSHED && (m.ticksExisted / 4) % 2 == 0 ? 0.35F : 1F;
            SettlementClientSC.lamp(pack.lamp, LAMP[st], k);
        }
        GL11.glPopMatrix();
    }

    /** The name and «Караванщик» instead of the profession. */
    @Override
    protected void passSpecialRender(EntityLivingBase e, double x, double y, double z) {
        EntityCaravanMasterSC m = (EntityCaravanMasterSC) e;
        if (m.deathTime > 0 || m == renderManager.livingPlayer || m.riddenByEntity != null) {
            return;
        }
        SettlementClientSC.label(renderManager, getFontRendererFromRenderManager(), m, x, y, z, SettlementClientSC.name(m), 0xFFFFFF,
                Lang.tr("entity.SiliconAge." + EntityCaravanMasterSC.NAME + ".name"));
    }

    /**
     * The frame and the crates on the body (64 x 32 layout drawn at 128 x 64 - tools/gen_caravan_tex.py paints exactly these boxes):
     * the rails beside the battery pack (z 5..6), the cross bars between them, the crates behind (z 6..10) - the big one low, the
     * copper-banded one over it, below the shoulders' line so the mood lamp and the antenna stay in sight.
     */
    @SideOnly(Side.CLIENT)
    public static class ModelPackSC extends ModelBase {

        final ModelRenderer railLeft, railRight, barTop, barBottom, crateLow, crateHigh;
        /** Full-bright, drawn by the renderer: the caravan's lamp on the left rail's top. */
        final ModelRenderer lamp;

        public ModelPackSC() {
            textureWidth = 64;
            textureHeight = 32;
            railLeft = box(0, 0, -4.5F, -1F, 5F, 1, 13, 1);
            railRight = box(0, 0, 3.5F, -1F, 5F, 1, 13, 1);
            barTop = box(0, 16, -3.5F, -1F, 5F, 7, 1, 1);
            barBottom = box(0, 16, -3.5F, 11F, 5F, 7, 1, 1);
            crateLow = box(8, 0, -4F, 6F, 6F, 8, 5, 4);
            crateHigh = box(32, 0, -3.5F, 1F, 6F, 7, 5, 4);
            lamp = box(56, 28, -4.5F, -3F, 5F, 1, 2, 1);
        }

        private ModelRenderer box(int u, int v, float x, float y, float z, int w, int h, int d) {
            ModelRenderer r = new ModelRenderer(this, u, v);
            r.addBox(x, y, z, w, h, d);
            return r;
        }

        /** Everything but the lamp, in the caller's matrix (the body's). */
        void renderPack(float s) {
            railLeft.render(s);
            railRight.render(s);
            barTop.render(s);
            barBottom.render(s);
            crateLow.render(s);
            crateHigh.render(s);
        }
    }
}
