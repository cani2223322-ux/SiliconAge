package com.sc.client;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorFeature;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * The suits' worn model: the armour as usual, then its lit parts drawn a second time full-bright
 * from the suit's glow layer. How bright tells the charge: full - bright, under 15% - flickering,
 * empty - a slow red pulse. A piece whose own function is switched off in the armour menu (night
 * vision, speed / step assist / dash, jump / fall damping / water walk) has its lights out.
 */
@SideOnly(Side.CLIENT)
public class ModelArmorGlowSC extends ModelBiped {

    /** Full-size for helmet / chestplate / boots, the thinner one for leggings - vanilla's two armour models. */
    public static final ModelArmorGlowSC OUTER = new ModelArmorGlowSC(1.0F), INNER = new ModelArmorGlowSC(0.5F);

    /** The functions that light each piece (by ItemArmor.armorType); a piece with none of them available is always lit. */
    private static final ArmorFeature[][] PIECE_FUNCTIONS = {
            {ArmorFeature.NIGHT_VISION},
            {},
            {ArmorFeature.SPEED, ArmorFeature.STEP_ASSIST, ArmorFeature.DASH},
            {ArmorFeature.JUMP, ArmorFeature.FALL_DAMPING, ArmorFeature.WATER_WALK}};

    private ItemStack stack;
    private String suit;
    private int layer;

    private ModelArmorGlowSC(float scale) {
        super(scale);
    }

    /** Called from ItemArmorSC.getArmorModel: which piece, and the wearer's pose. */
    public static ModelArmorGlowSC forPiece(EntityLivingBase wearer, ItemStack stack, int slot) {
        ModelArmorGlowSC m = slot == 2 ? INNER : OUTER;
        m.stack = stack;
        m.suit = ((ItemArmorSC) stack.getItem()).getSuit().textureName;
        m.layer = slot == 2 ? 2 : 1;
        m.bipedHead.showModel = slot == 0;
        m.bipedHeadwear.showModel = slot == 0;
        m.bipedBody.showModel = slot == 1 || slot == 2;
        m.bipedRightArm.showModel = slot == 1;
        m.bipedLeftArm.showModel = slot == 1;
        m.bipedRightLeg.showModel = slot == 2 || slot == 3;
        m.bipedLeftLeg.showModel = slot == 2 || slot == 3;
        m.isSneak = wearer.isSneaking();
        m.isRiding = wearer.isRiding();
        m.isChild = wearer.isChild();
        ItemStack held = wearer.getHeldItem();
        m.heldItemRight = held != null ? 1 : 0;
        m.aimedBow = false;
        if (wearer instanceof EntityPlayer && held != null && ((EntityPlayer) wearer).getItemInUseCount() > 0) {
            EnumAction action = held.getItemUseAction();
            if (action == EnumAction.block) {
                m.heldItemRight = 3;
            } else if (action == EnumAction.bow) {
                m.aimedBow = true;
            }
        }
        return m;
    }

    @Override
    public void render(Entity entity, float limbSwing, float limbAmount, float age, float yaw, float pitch, float scale) {
        super.render(entity, limbSwing, limbAmount, age, yaw, pitch, scale);
        if (stack == null || GL11.glGetInteger(GL11.GL_DEPTH_FUNC) == GL11.GL_EQUAL) {
            return;                                     // the enchantment glint pass draws this model again: leave it alone
        }
        int capacity = ItemArmorSC.capacityOf(stack);
        float charge = capacity <= 0 ? 0F : (float) ItemArmorSC.chargeOf(stack) / capacity;
        long time = entity.worldObj == null ? 0 : entity.worldObj.getTotalWorldTime();
        boolean red = false, lit = true;
        float r = 1F, g = 1F, b = 1F, a = 1F;
        if (!functionsOn()) {
            lit = false;                                // lights out: the lit parts drawn dark
            r = g = b = 0.16F;
        } else if (charge <= 0F) {
            red = true;                                 // empty: a slow red pulse
            a = 0.35F + 0.35F * (float) Math.sin((time + age % 1F) * 0.12);
        } else if (charge < 0.15F) {                    // almost empty: flickering
            int n = (int) ((time * 7919L + entity.getEntityId() * 31L) % 10);
            float k = n < 4 ? 0.25F : n < 6 ? 0.55F : 0.8F;
            r = g = b = k;
        } else {
            float k = 0.6F + 0.4F * charge;
            r = g = b = k;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(Reference.ASSETS,
                "textures/models/armor/" + suit + (red ? "_glowred_" : "_glow_") + layer + ".png"));
        GL11.glPushAttrib(GL11.GL_ENABLE_BIT | GL11.GL_COLOR_BUFFER_BIT | GL11.GL_LIGHTING_BIT | GL11.GL_DEPTH_BUFFER_BIT);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glEnable(GL11.GL_ALPHA_TEST);
        GL11.glAlphaFunc(GL11.GL_GREATER, 0.004F);
        GL11.glDepthFunc(GL11.GL_LEQUAL);
        float lastX = OpenGlHelper.lastBrightnessX, lastY = OpenGlHelper.lastBrightnessY;
        if (lit) {
            GL11.glDisable(GL11.GL_LIGHTING);
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, 240F, 240F);
        }
        GL11.glColor4f(r, g, b, a);
        super.render(entity, limbSwing, limbAmount, age, yaw, pitch, scale);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        if (lit) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
        GL11.glPopAttrib();
        // the renderer carries on with this piece's texture bound (a later pass may draw it again)
        Minecraft.getMinecraft().getTextureManager().bindTexture(new ResourceLocation(
                ((ItemArmorSC) stack.getItem()).getArmorTexture(stack, entity, layer == 2 ? 2 : 0, null)));
    }

    /** Is the piece's own function (any of them available to it) switched on? */
    private boolean functionsOn() {
        ItemArmorSC item = (ItemArmorSC) stack.getItem();
        boolean any = false;
        for (ArmorFeature f : PIECE_FUNCTIONS[item.armorType]) {
            if (f.availableIn(item.getSuit(), item.armorType)) {
                any = true;
                if (ItemArmorSC.isEnabled(stack, f)) {
                    return true;
                }
            }
        }
        return !any;
    }
}
