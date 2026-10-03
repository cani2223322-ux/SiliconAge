package com.sc.client;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.lwjgl.opengl.GL11;

import com.sc.Reference;
import com.sc.item.ItemArmorSC;
import com.sc.util.ArmorFeature;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.Minecraft;
import net.minecraft.client.model.ModelBiped;
import net.minecraft.client.model.ModelRenderer;
import net.minecraft.client.renderer.OpenGlHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumAction;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ResourceLocation;

/**
 * The suits' worn model: the armour with its 3D add-on parts (backpack, pauldrons, orbs, fins,
 * knee plates...), then its lit parts drawn a second time full-bright from the suit's glow layer -
 * animated (a band of light running down it; Exo's colours flowing), or in the light colour picked
 * in the armour menu. How bright tells the charge: full - bright, under 15% - flickering, empty - a
 * slow red pulse. A piece whose own function is switched off in the armour menu (night vision,
 * speed / step assist / dash, jump / fall damping / water walk) has its lights out.
 * The add-on parts' boxes match tools/parts3d.py (their texture nets in the armour layers).
 */
@SideOnly(Side.CLIENT)
public class ModelArmorGlowSC extends ModelBiped {

    /** Full-size for helmet / chestplate / boots, the thinner one for leggings - vanilla's two armour models. */
    public static final ModelArmorGlowSC OUTER = new ModelArmorGlowSC(1.0F), INNER = new ModelArmorGlowSC(0.5F);

    public static final int FRAMES = 8;
    /** The light colours (ItemArmorSC.glowColor): 0 = the suit's own, then these (RGB). */
    public static final int[] COLORS = {0, 0xFF4040, 0xFF9A30, 0xFFE040, 0x50FF70, 0x40E0FF, 0x4A7CFF, 0xB060FF, 0xFF60C0, 0xFFFFFF};

    /** The functions that light each piece (by ItemArmor.armorType); a piece with none of them available is always lit. */
    private static final ArmorFeature[][] PIECE_FUNCTIONS = {
            {ArmorFeature.NIGHT_VISION},
            {},
            {ArmorFeature.SPEED, ArmorFeature.STEP_ASSIST, ArmorFeature.DASH},
            {ArmorFeature.JUMP, ArmorFeature.FALL_DAMPING, ArmorFeature.WATER_WALK}};

    private static final Map<String, ResourceLocation> TEXTURES = new HashMap<String, ResourceLocation>();

    /** Each suit's add-on parts (shown only for that suit). */
    private final Map<String, List<ModelRenderer>> parts = new HashMap<String, List<ModelRenderer>>();

    private ItemStack stack;
    /** The suit's name (which add-on parts), and its textures' name start (with the Singular colour scheme). */
    private String suit, tex;
    private int layer;

    private ModelArmorGlowSC(float scale) {
        super(scale);
        if (scale < 1F) {                                   // leggings: knee plates, every suit
            List<ModelRenderer> knees = new ArrayList<ModelRenderer>();
            knees.add(part(bipedRightLeg, 0, 0, false, -2F, 4F, -3.5F, 4, 3, 1, 0F));
            knees.add(part(bipedLeftLeg, 0, 0, true, -2F, 4F, -3.5F, 4, 3, 1, 0F));
            parts.put("nano", knees);
            parts.put("quantum", knees);
            parts.put("exo", knees);
            parts.put("singular", knees);
            return;
        }
        List<ModelRenderer> nano = new ArrayList<ModelRenderer>();
        nano.add(part(bipedBody, 32, 0, false, -3F, 1F, 3F, 6, 7, 2, 0F));             // battery pack
        nano.add(part(bipedRightArm, 32, 9, false, -4F, -4F, -2.5F, 5, 2, 5, 0.6F));    // shoulder pads
        nano.add(part(bipedLeftArm, 32, 9, true, -1F, -4F, -2.5F, 5, 2, 5, 0.6F));
        nano.add(part(bipedHead, 48, 0, false, 5F, -6F, -1.5F, 1, 2, 3, 0F));           // a lamp on the helmet
        parts.put("nano", nano);
        List<ModelRenderer> quantum = new ArrayList<ModelRenderer>();
        quantum.add(part(bipedRightArm, 32, 0, false, -5F, -4.5F, -3.5F, 6, 3, 7, 0F)); // gold pauldrons
        quantum.add(part(bipedLeftArm, 32, 0, true, -1F, -4.5F, -3.5F, 6, 3, 7, 0F));
        quantum.add(part(bipedBody, 58, 0, false, -1F, 1F, 3F, 2, 9, 1, 0F));           // the energy spine
        quantum.add(part(bipedHead, 32, 10, false, 5F, -5.5F, -1.5F, 1, 3, 3, 0F));     // ear discs
        quantum.add(part(bipedHead, 32, 10, true, -6F, -5.5F, -1.5F, 1, 3, 3, 0F));
        parts.put("quantum", quantum);
        List<ModelRenderer> exo = new ArrayList<ModelRenderer>();
        exo.add(part(bipedRightArm, 32, 0, false, -3F, -5F, -1.5F, 3, 3, 3, 0F));       // shoulder orbs
        exo.add(part(bipedLeftArm, 32, 0, true, 0F, -5F, -1.5F, 3, 3, 3, 0F));
        ModelRenderer fins = part(bipedHead, 44, 0, false, -3F, -11F, -3F, 1, 2, 6, 0F); // three fins on the helmet
        fins.addBox(-0.5F, -11F, -3F, 1, 2, 6);
        fins.addBox(2F, -11F, -3F, 1, 2, 6);
        exo.add(fins);
        exo.add(part(bipedRightLeg, 32, 8, false, -3F, 11.5F, -3F, 6, 1, 6, 0.5F));      // anti-grav rings
        exo.add(part(bipedLeftLeg, 32, 8, true, -3F, 11.5F, -3F, 6, 1, 6, 0.5F));
        exo.add(part(bipedBody, 56, 16, false, -1.5F, 1.5F, 3F, 3, 6, 1, 0F));          // radiator
        parts.put("exo", exo);
        // Singular: the Exo silhouette (its textures carry the parts' nets in the same places) - its own look is in the textures
        parts.put("singular", exo);
    }

    private ModelRenderer part(ModelRenderer parent, int u, int v, boolean mirror, float x, float y, float z,
                               int w, int h, int d, float inflate) {
        ModelRenderer r = new ModelRenderer(this, u, v);
        r.mirror = mirror;
        r.addBox(x, y, z, w, h, d, inflate);
        parent.addChild(r);
        return r;
    }

    /** Called from ItemArmorSC.getArmorModel: which piece, and the wearer's pose. */
    public static ModelArmorGlowSC forPiece(EntityLivingBase wearer, ItemStack stack, int slot) {
        ModelArmorGlowSC m = slot == 2 ? INNER : OUTER;
        m.stack = stack;
        m.suit = ((ItemArmorSC) stack.getItem()).getSuit().textureName;
        m.tex = ItemArmorSC.textureBase(stack);
        m.layer = slot == 2 ? 2 : 1;
        List<ModelRenderer> own = m.parts.get(m.suit);
        for (Map.Entry<String, List<ModelRenderer>> e : m.parts.entrySet()) {
            if (!e.getKey().equals(m.suit) && e.getValue() != own) {      // two suits may share one list (Exo / Singular)
                for (ModelRenderer r : e.getValue()) {
                    r.showModel = false;
                }
            }
        }
        List<ModelRenderer> mine = m.parts.get(m.suit);
        if (mine != null) {
            for (ModelRenderer r : mine) {
                r.showModel = true;
            }
        }
        m.bipedHead.showModel = slot == 0;
        m.bipedHeadwear.showModel = false;                   // the hat area of the layer holds the parts' nets
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

    private static ResourceLocation texture(String name) {
        ResourceLocation r = TEXTURES.get(name);
        if (r == null) {
            r = new ResourceLocation(Reference.ASSETS, "textures/models/armor/" + name + ".png");
            TEXTURES.put(name, r);
        }
        return r;
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
        int color = COLORS[Math.max(0, Math.min(COLORS.length - 1, ItemArmorSC.glowColor(stack)))];
        String tex;
        boolean lit = true;
        float r = 1F, g = 1F, b = 1F, a = 1F;
        if (color != 0) {
            tex = this.tex + "_gloww_" + layer;             // a chosen colour: the white glow, tinted, breathing
            float breath = 0.85F + 0.15F * (float) Math.sin(time * 0.15);
            r = (color >> 16 & 255) / 255F * breath;
            g = (color >> 8 & 255) / 255F * breath;
            b = (color & 255) / 255F * breath;
        } else {
            tex = this.tex + "_glow_" + layer + "_" + (int) (time / 3 % FRAMES);
        }
        float k = 1F;
        if (!functionsOn()) {
            lit = false;                                // lights out: the lit parts drawn dark
            r = g = b = 0.16F;
            k = 1F;
        } else if (charge <= 0F) {
            tex = this.tex + "_glowred_" + layer;           // empty: a slow red pulse
            r = g = b = 1F;
            a = 0.35F + 0.35F * (float) Math.sin(time * 0.12);
        } else if (charge < 0.15F) {                    // almost empty: flickering
            int n = (int) ((time * 7919L + entity.getEntityId() * 31L) % 10);
            k = n < 4 ? 0.25F : n < 6 ? 0.55F : 0.8F;
        } else {
            k = 0.6F + 0.4F * charge;
        }
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture(tex));
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
        GL11.glColor4f(r * k, g * k, b * k, a);
        super.render(entity, limbSwing, limbAmount, age, yaw, pitch, scale);
        GL11.glColor4f(1F, 1F, 1F, 1F);
        if (lit) {
            OpenGlHelper.setLightmapTextureCoords(OpenGlHelper.lightmapTexUnit, lastX, lastY);
        }
        GL11.glPopAttrib();
        // the renderer carries on with this piece's texture bound (a later pass may draw it again)
        Minecraft.getMinecraft().getTextureManager().bindTexture(texture(this.tex + "_layer_" + layer));
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
