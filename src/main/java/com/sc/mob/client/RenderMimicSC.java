package com.sc.mob.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.block.BlockMachineSC;
import com.sc.machine.MachineType;
import com.sc.mob.EntityMimicSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.client.model.ModelBase;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.client.renderer.entity.Render;
import net.minecraft.client.renderer.texture.TextureMap;
import net.minecraft.entity.Entity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.ResourceLocation;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §8.7): the mimic machine (EntityMimicSC).
 * <ul>
 * <li>Asleep: the mod's own machine block - a Crusher or an Electric Furnace (DW_DISGUISE: BlockMachineSC's icons, its front
 * where the mimic looks, square to the grid), drawn exactly as a placed block is in hand: no shadow, nothing else;</li>
 * <li>awake: the same machine «on legs» - four hydraulic legs lift it LEG blocks, its upper half a lid hinged at the back
 * that opens into a mouth (wide with every bite - the swing - and a little all the time), steel teeth on both edges, a
 * dark-violet maw and two violet eyes under the lid (full-bright). The legs walk with its pace; it tips over when it dies,
 * flashes red when hurt.</li>
 * </ul>
 * The machine's halves are RenderBlocks.renderBlockAsItem with locked bounds (the icons cut in half as the block is); the legs,
 * teeth, maw and eyes - a model on textures/entity/mimic.png (64 x 64, tools/gen_ruins_tex.py).
 */
@SideOnly(Side.CLIENT)
public class RenderMimicSC extends Render {

    private static final ResourceLocation TEX = new ResourceLocation(Reference.ASSETS, "textures/entity/mimic.png");
    /** How high the legs lift the machine (blocks); the mouth's idle and full opening (degrees). */
    private static final float LEG = 0.375F, JAW_IDLE = 6F, JAW_BITE = 48F;

    private final ModelMimic model = new ModelMimic();

    public RenderMimicSC() {
        shadowSize = 0.5F;
    }

    @Override
    protected ResourceLocation getEntityTexture(Entity e) {
        return TEX;
    }

    @Override
    public void doRender(Entity e, double x, double y, double z, float yaw, float pt) {
        EntityMimicSC m = (EntityMimicSC) e;
        MachineType type = m.disguise() == EntityMimicSC.DISGUISE_FURNACE ? MachineType.ELECTRIC_FURNACE : MachineType.CRUSHER;
        Block block = BlockMachineSC.blockFor(type);
        int meta = BlockMachineSC.metaFor(type);
        boolean asleep = m.dormant();
        shadowSize = asleep ? 0F : 0.5F;
        float body = m.prevRenderYawOffset + (m.renderYawOffset - m.prevRenderYawOffset) * pt;
        GL11.glPushMatrix();
        try {
            GL11.glTranslated(x, y, z);
            GL11.glRotatef(-body, 0F, 1F, 0F);                  // frame: +Z its front, Y up, its feet at 0
            if (m.deathTime > 0) {
                float f = MathHelper.sqrt_float(Math.min(1F, (m.deathTime + pt - 1F) / 20F * 1.6F));
                GL11.glRotatef(f * 90F, 0F, 0F, 1F);
            }
            boolean hurt = m.hurtTime > 0 || m.deathTime > 0;
            if (asleep) {
                bindTexture(TextureMap.locationBlocksTexture);
                GL11.glTranslatef(0F, 0.5F, 0F);
                machine(block, meta, 0D, 1D, hurt);
                return;
            }
            float swing = m.getSwingProgress(pt);
            float jaw = JAW_IDLE + 2F * MathHelper.sin((m.ticksExisted + pt) * 0.25F) + JAW_BITE * MathHelper.sin(swing * (float) Math.PI);
            float walk = m.limbSwing - m.limbSwingAmount * (1F - pt);
            float amount = Math.min(1F, m.prevLimbSwingAmount + (m.limbSwingAmount - m.prevLimbSwingAmount) * pt);
            // the legs, the maw, the teeth, the eyes (the model's own frame: a living entity's)
            bindEntityTexture(e);
            GL11.glPushMatrix();
            GL11.glRotatef(180F, 0F, 1F, 0F);
            GL11.glScalef(-1F, -1F, 1F);
            GL11.glTranslatef(0F, -1.5F, 0F);
            if (hurt) {
                GL11.glColor4f(1F, 0.55F, 0.55F, 1F);
            }
            GL11.glEnable(GL11.GL_ALPHA_TEST);                  // the teeth's gaps are cut out
            model.pose(walk, amount, jaw);
            model.render(0.0625F);
            GL11.glColor4f(1F, 1F, 1F, 1F);
            GL11.glPopMatrix();
            // the machine: its lower half on the legs, its upper half the lid, hinged at the back
            bindTexture(TextureMap.locationBlocksTexture);
            GL11.glPushMatrix();
            GL11.glTranslatef(0F, LEG + 0.5F, 0F);
            machine(block, meta, 0D, 0.5D, hurt);
            GL11.glPopMatrix();
            GL11.glPushMatrix();
            GL11.glTranslatef(0F, LEG + 0.5F, -0.5F);
            GL11.glRotatef(-jaw, 1F, 0F, 0F);
            GL11.glTranslatef(0F, 0F, 0.5F);
            machine(block, meta, 0.5D, 1D, hurt);
            GL11.glPopMatrix();
        } finally {
            GL11.glPopMatrix();
        }
    }

    /**
     * The machine block's slice y0..y1 (of a block), as a placed block looks in hand, its middle at the current origin and its
     * front (BlockMachineSC's side 3) towards +Z; red-tinted when hurt.
     */
    private void machine(Block block, int meta, double y0, double y1, boolean hurt) {
        GL11.glPushMatrix();
        GL11.glRotatef(-90F, 0F, 1F, 0F);                       // renderBlockAsItem turns it a quarter round first
        if (hurt) {
            field_147909_c.useInventoryTint = false;
            GL11.glColor4f(1F, 0.55F, 0.55F, 1F);
        }
        field_147909_c.overrideBlockBounds(0D, y0, 0D, 1D, y1, 1D);
        field_147909_c.renderBlockAsItem(block, meta, 1F);
        field_147909_c.unlockBlockBounds();
        field_147909_c.useInventoryTint = true;
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glPopMatrix();
    }

    /**
     * The mimic's own parts (64 x 64): four legs at the machine's corners, the maw on the lower half's top, its teeth along the
     * front and the sides, the lid's teeth and the eyes (full-bright) turning with the lid on its back hinge.
     */
    @SideOnly(Side.CLIENT)
    public static class ModelMimic extends ModelBase {

        private final ModelRenderer[] legs = new ModelRenderer[4];
        private final ModelRenderer maw, teeth, teethLeft, teethRight, lid, eyes;

        public ModelMimic() {
            textureWidth = 64;
            textureHeight = 64;
            for (int k = 0; k < 4; k++) {
                legs[k] = new ModelRenderer(this, 0, 0);
                legs[k].addBox(-1.5F, 0F, -1.5F, 3, 6, 3);
                legs[k].setRotationPoint((k & 1) == 0 ? -5.5F : 5.5F, 18F, (k & 2) == 0 ? -5.5F : 5.5F);
            }
            maw = new ModelRenderer(this, 0, 10);
            maw.addBox(-7F, -0.05F, -7F, 14, 1, 14);
            maw.setRotationPoint(0F, 10F, 0F);
            teeth = new ModelRenderer(this, 0, 26);
            teeth.addBox(-7F, -2F, -7.6F, 14, 2, 1);
            teeth.setRotationPoint(0F, 10F, 0F);
            teethLeft = new ModelRenderer(this, 0, 30);
            teethLeft.addBox(-7.6F, -2F, -6.5F, 1, 2, 13);
            teethLeft.setRotationPoint(0F, 10F, 0F);
            teethRight = new ModelRenderer(this, 0, 30);
            teethRight.mirror = true;
            teethRight.addBox(6.6F, -2F, -6.5F, 1, 2, 13);
            teethRight.setRotationPoint(0F, 10F, 0F);
            lid = new ModelRenderer(this, 32, 26);                  // the lid's teeth, hanging from its front edge
            lid.addBox(-7F, 0F, -15.6F, 14, 2, 1);
            lid.setRotationPoint(0F, 10F, 8F);
            eyes = new ModelRenderer(this, 16, 0);
            eyes.addBox(-4F, 0.3F, -14.8F, 2, 1, 1);
            eyes.addBox(2F, 0.3F, -14.8F, 2, 1, 1);
            eyes.setRotationPoint(0F, 10F, 8F);
        }

        /** The walk (diagonal pairs) and the lid's opening (degrees). */
        void pose(float walk, float amount, float jawDeg) {
            for (int k = 0; k < 4; k++) {
                boolean pair = ((k & 1) ^ (k >> 1)) == 0;
                legs[k].rotateAngleX = MathHelper.cos(walk * 0.6662F + (pair ? 0F : (float) Math.PI)) * 1.1F * amount;
            }
            float a = -jawDeg * (float) Math.PI / 180F;
            lid.rotateAngleX = a;
            eyes.rotateAngleX = a;
        }

        void render(float scale) {
            for (ModelRenderer l : legs) {
                l.render(scale);
            }
            maw.render(scale);
            teeth.render(scale);
            teethLeft.render(scale);
            teethRight.render(scale);
            lid.render(scale);
            float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
            eyes.render(scale);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
    }
}
