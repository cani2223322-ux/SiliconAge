package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.common.Optional;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemArmor;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.ISpecialArmor;

/**
 * §6/§16 armor, electric like IC2's nano/quantum suits: each piece stores EU ("ChargeSC") and
 * protects only while charged - every point of damage it absorbs costs ArmorSuit.euPerDamage,
 * and an empty piece gives no protection at all. No durability: the charge bar replaces it.
 * Charged in the energy storage slot, by sneak + right-click on a machine / generator, or (with
 * IC2) in any IC2 charger of the suit's tier or higher (ISpecialElectricItem, own manager).
 * §16's Heat/chip system is handled by CommonEventHandler reading this item's NBT chip list;
 * chips draw their power from the chestplate.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemArmorSC extends ItemArmor implements ISpecialArmor, ic2.api.item.ISpecialElectricItem {

    private static final String CHARGE = "ChargeSC";

    private final ArmorSuit suit;
    private final String pieceName;
    private IIcon icon;
    private Object ic2Manager;

    public ItemArmorSC(ArmorSuit suit, int armorType, String pieceName) {
        super(suit.material, 0, armorType);
        this.suit = suit;
        this.pieceName = pieceName;
        setMaxDamage(0);          // wear is paid in EU, not durability
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".armor." + suit.name().toLowerCase(java.util.Locale.ROOT) + "." + pieceName);
    }

    public ArmorSuit getSuit() {
        return suit;
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":armor" + cap(suit.textureName) + cap(pieceName));
    }

    private static String cap(String s) {
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    @Override
    public IIcon getIconFromDamageForRenderPass(int damage, int pass) {
        return icon;
    }

    /**
     * Worn-armor texture on the player model. Vanilla's default builds the path from the
     * ArmorMaterial's name, which for an EnumHelper-added material ("NANO_SC") points at a
     * vanilla path that doesn't exist - equipped pieces rendered as a missing texture. Layer 2
     * is the leggings sheet, layer 1 covers helmet/chestplate/boots, same split as vanilla.
     */
    @Override
    public String getArmorTexture(ItemStack stack, Entity entity, int slot, String type) {
        return Reference.ASSETS + ":textures/models/armor/" + suit.textureName + "_layer_" + (slot == 2 ? 2 : 1) + ".png";
    }

    /** The worn model: the armour, then its lit parts full-bright by the charge (ModelArmorGlowSC). */
    @Override
    @cpw.mods.fml.relauncher.SideOnly(cpw.mods.fml.relauncher.Side.CLIENT)
    public net.minecraft.client.model.ModelBiped getArmorModel(EntityLivingBase wearer, ItemStack stack, int slot) {
        return com.sc.client.ModelArmorGlowSC.forPiece(wearer, stack, slot);
    }

    /** §16: up to 5 installed chips (one per ChipType), read/written by ItemArmorChipSC/CommonEventHandler. */
    public static NBTTagCompound chipsTag(ItemStack armorStack) {
        if (!armorStack.hasTagCompound()) {
            armorStack.setTagCompound(new NBTTagCompound());
        }
        NBTTagCompound root = armorStack.getTagCompound();
        if (!root.hasKey("ChipsSC")) {
            root.setTag("ChipsSC", new NBTTagCompound());
        }
        return root.getCompoundTag("ChipsSC");
    }

    // ---- charge ----

    public static int chargeOf(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger(CHARGE) : 0;
    }

    public static int capacityOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmorSC ? ((ItemArmorSC) stack.getItem()).suit.maxCharge : 0;
    }

    public static void setCharge(ItemStack stack, int charge) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        stack.getTagCompound().setInteger(CHARGE, Math.max(0, Math.min(capacityOf(stack), charge)));
    }

    /** Charges up to `max` EU. @return EU actually taken. */
    public static int charge(ItemStack stack, int max) {
        int taken = Math.max(0, Math.min(capacityOf(stack) - chargeOf(stack), max));
        if (taken > 0) {
            setCharge(stack, chargeOf(stack) + taken);
        }
        return taken;
    }

    /** Spends up to `amount` EU. @return EU actually spent. */
    public static int discharge(ItemStack stack, int amount) {
        int spent = Math.max(0, Math.min(chargeOf(stack), amount));
        if (spent > 0) {
            setCharge(stack, chargeOf(stack) - spent);
        }
        return spent;
    }

    // ---- built-in functions (ArmorFeature), each switched on / off in the piece's NBT ----

    /** Whether this piece has the function and it is switched on (a fresh piece: its default). */
    public static boolean isEnabled(ItemStack stack, com.sc.util.ArmorFeature f) {
        if (stack == null || !(stack.getItem() instanceof ItemArmorSC)) {
            return false;
        }
        ItemArmorSC item = (ItemArmorSC) stack.getItem();
        if (!f.availableIn(item.suit, item.armorType)) {
            return false;
        }
        boolean toggled = stack.hasTagCompound() && (stack.getTagCompound().getInteger("FnToggled") & (1 << f.ordinal())) != 0;
        return f.onByDefault != toggled;
    }

    public static void setEnabled(ItemStack stack, com.sc.util.ArmorFeature f, boolean on) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        int bits = stack.getTagCompound().getInteger("FnToggled");
        bits = on != f.onByDefault ? bits | (1 << f.ordinal()) : bits & ~(1 << f.ordinal());
        stack.getTagCompound().setInteger("FnToggled", bits);
    }

    /** Spends `amount` EU if the piece has it all. */
    public static boolean pay(ItemStack stack, int amount) {
        if (amount <= 0) {
            return true;
        }
        if (chargeOf(stack) < amount) {
            return false;
        }
        discharge(stack, amount);
        return true;
    }

    /** The suit's power mode (kept on the chestplate): 0 economy, 1 normal, 2 combat. */
    /** The light colour picked in the armour menu (0 = the suit's own; ModelArmorGlowSC.COLORS). */
    public static int glowColor(ItemStack piece) {
        return piece != null && piece.hasTagCompound() ? piece.getTagCompound().getInteger("GlowColorSC") : 0;
    }

    public static void setGlowColor(ItemStack piece, int color) {
        if (!piece.hasTagCompound()) {
            piece.setTagCompound(new NBTTagCompound());
        }
        piece.getTagCompound().setInteger("GlowColorSC", Math.max(0, Math.min(GLOW_COLORS - 1, color)));
    }

    /** How many light colours there are (the suit's own + 9). */
    public static final int GLOW_COLORS = 10;

    public static int powerMode(ItemStack chest) {
        return chest != null && chest.hasTagCompound() && chest.getTagCompound().hasKey("PowerMode")
                ? Math.max(0, Math.min(2, chest.getTagCompound().getInteger("PowerMode"))) : 1;
    }

    public static void setPowerMode(ItemStack chest, int mode) {
        if (!chest.hasTagCompound()) {
            chest.setTagCompound(new NBTTagCompound());
        }
        chest.getTagCompound().setInteger("PowerMode", Math.max(0, Math.min(2, mode)));
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.tooltip.armor.charge", chargeOf(stack), suit.maxCharge));
        if (!powered(stack)) {
            list.add(Lang.tr("sc.tooltip.armor.empty"));
        }
        java.util.List<String> names = new java.util.ArrayList<String>();
        java.util.List<Boolean> on = new java.util.ArrayList<Boolean>();
        int lit = 0;
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            if (f.availableIn(suit, armorType)) {
                names.add(Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT)));
                on.add(isEnabled(stack, f));
                lit += isEnabled(stack, f) ? 1 : 0;
            }
        }
        switch (com.sc.util.TooltipSC.page()) {
            case 1:
                list.add(Lang.tr("sc.tooltip.functions", lit, names.size()));
                com.sc.util.TooltipSC.pairs(list, names, on);
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.howto", suit.chargeTier.name())
                        + " " + Lang.tr("sc.tooltip.armor.keys"), "\u00a77");
                break;
            default:
                list.add(Lang.tr("sc.tooltip.functions", lit, names.size()));
                com.sc.util.TooltipSC.hintShift(list);
        }
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (double) chargeOf(stack) / suit.maxCharge;
    }

    /** Creative tab: an empty and a fully charged piece, like IC2. */
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
        ItemStack full = new ItemStack(item);
        setCharge(full, suit.maxCharge);
        list.add(full);
    }

    /**
     * Pieces from before the armor went electric may still carry vanilla wear - clear it. A chestplate
     * in the inventory (not worn - onUpdate only runs for the main inventory; worn, CommonEventHandler
     * cools it) cools once a second too, and its chips come back on at half heat.
     */
    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (stack.getItemDamage() != 0) {
            stack.setItemDamage(0);
        }
        if (world.isRemote || armorType != 1 || entity.ticksExisted % 20 != 0 || !stack.hasTagCompound()) {
            return;
        }
        NBTTagCompound root = stack.getTagCompound();
        int heat = root.getInteger("HeatSC");
        if (heat > 0) {
            heat = Math.max(0, heat - suit.heatDissipation);
            root.setInteger("HeatSC", heat);
        }
        if (root.getBoolean("ChipsOffSC") && heat * 100 / suit.heatCapacity <= 50) {
            root.setBoolean("ChipsOffSC", false);
        }
    }

    @Override
    public void onArmorTick(World world, EntityPlayer player, ItemStack stack) {
        if (stack.getItemDamage() != 0) {
            stack.setItemDamage(0);
        }
    }

    /** Sneak + right-click on a machine / generator: charge from its energy buffer (as weapons do). */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyBase)) {
            return false;
        }
        if (!world.isRemote) {
            int moved = ((TileEntityEnergyBase) te).extractForItemCharging(suit.maxCharge - chargeOf(stack));
            charge(stack, moved);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.charged",
                    moved, chargeOf(stack), suit.maxCharge));
        }
        return true;
    }

    // ---- ISpecialArmor: protection paid in EU ----

    private boolean powered(ItemStack stack) {
        return chargeOf(stack) >= suit.euPerDamage;
    }

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source, double damage, int slot) {
        if (source.isUnblockable() || !powered(armor)) {
            return new ArmorProperties(0, 0, 0);
        }
        // Forge works in 1/25ths of a damage point here, so the EU left buys 25 x charge / cost.
        int absorbMax = (int) Math.min(Integer.MAX_VALUE, 25.0 * chargeOf(armor) / suit.euPerDamage);
        return new ArmorProperties(0, damageReduceAmount / 25.0, absorbMax);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
        return powered(armor) ? damageReduceAmount : 0;
    }

    /** Absorbed damage costs EU - less for a full Quantum set and in combat mode (ArmorLogicSC). */
    @Override
    public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) {
        float mul = entity instanceof EntityPlayer ? com.sc.item.ArmorLogicSC.absorbCostMul((EntityPlayer) entity) : 1F;
        discharge(stack, (int) Math.ceil(damage * suit.euPerDamage * mul));
    }

    // ---- IC2: charged by batboxes / MFE / MFSU / charge pads through our own manager ----

    @Override
    public boolean canProvideEnergy(ItemStack stack) {
        return false;
    }

    @Override
    public Item getChargedItem(ItemStack stack) {
        return this;
    }

    @Override
    public Item getEmptyItem(ItemStack stack) {
        return this;
    }

    @Override
    public double getMaxCharge(ItemStack stack) {
        return suit.maxCharge;
    }

    @Override
    public int getTier(ItemStack stack) {
        return suit.chargeTier.toIc2Tier();
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return suit.chargeTier.getVoltage();
    }

    @Override
    @Optional.Method(modid = Reference.IC2_MODID)
    public ic2.api.item.IElectricItemManager getManager(ItemStack stack) {
        if (ic2Manager == null) {
            ic2Manager = new ArmorElectricManagerSC();
        }
        return (ic2.api.item.IElectricItemManager) ic2Manager;
    }
}
