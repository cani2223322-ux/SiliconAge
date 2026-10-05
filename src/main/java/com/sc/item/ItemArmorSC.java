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
    /** Suits with colour schemes (Singular): an icon per scheme, by SingularScheme ordinal. */
    private IIcon[] schemeIcons;
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
        if (suit.hasSchemes()) {                         // armorSingularHelmet_a ... _k: one per colour scheme
            com.sc.util.SingularScheme[] all = com.sc.util.SingularScheme.values();
            schemeIcons = new IIcon[all.length];
            for (com.sc.util.SingularScheme s : all) {
                schemeIcons[s.ordinal()] = register.registerIcon(Reference.ASSETS + ":armor" + cap(suit.textureName) + cap(pieceName) + "_" + s.key());
            }
            icon = schemeIcons[com.sc.util.SingularScheme.DEFAULT.ordinal()];
            return;
        }
        icon = register.registerIcon(Reference.ASSETS + ":armor" + cap(suit.textureName) + cap(pieceName));
    }

    /** The icon of this stack: its colour scheme's (Singular), else the suit's one icon. */
    private IIcon iconOf(ItemStack stack) {
        if (schemeIcons != null) {
            return schemeIcons[com.sc.util.SingularScheme.of(stack).ordinal()];
        }
        return icon;
    }

    @Override
    public IIcon getIconIndex(ItemStack stack) {
        return iconOf(stack);
    }

    @Override
    public IIcon getIcon(ItemStack stack, int pass) {
        return iconOf(stack);
    }

    /**
     * The worn textures' name start (models/armor/<base>_layer_N.png and the glow layers): the suit's
     * texture name, with the colour scheme for the Singular suit ("singular_a").
     */
    public static String textureBase(ItemStack stack) {
        ArmorSuit s = ((ItemArmorSC) stack.getItem()).suit;
        return s.hasSchemes() ? s.textureName + "_" + com.sc.util.SingularScheme.of(stack).key() : s.textureName;
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
        return Reference.ASSETS + ":textures/models/armor/" + textureBase(stack) + "_layer_" + (slot == 2 ? 2 : 1) + ".png";
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
        // clipped: after the config lowered the capacity a piece may hold more than it can
        return stack != null && stack.hasTagCompound() ? Math.min(stack.getTagCompound().getInteger(CHARGE), capacityOf(stack)) : 0;
    }

    public static int capacityOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemArmorSC ? ((ItemArmorSC) stack.getItem()).cap() : 0;
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
        return f.onByDefault != toggled(stack.getTagCompound(), f.ordinal());
    }

    public static void setEnabled(ItemStack stack, com.sc.util.ArmorFeature f, boolean on) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        setToggled(stack.getTagCompound(), f.ordinal(), on != f.onByDefault);
    }

    /**
     * The functions switched away from their default: bits 0..31 in "FnToggled" (as always - old
     * pieces keep their switches as they are), 32..63 in "FnToggled2" (the Singular functions pushed
     * ArmorFeature past 32). Nothing to migrate: a piece without "FnToggled2" has every function from
     * 32 on at its default.
     */
    public static final String TOGGLED = "FnToggled", TOGGLED2 = "FnToggled2";

    /** Whether bit `ordinal` is set in the piece's switch fields (null tag: no). */
    public static boolean toggled(NBTTagCompound tag, int ordinal) {
        if (tag == null || ordinal < 0 || ordinal >= 64) {
            return false;
        }
        int bits = tag.getInteger(ordinal < 32 ? TOGGLED : TOGGLED2);
        return (bits & (1 << (ordinal & 31))) != 0;
    }

    /** Sets or clears bit `ordinal` (0..63) of the piece's switch fields; the second field is only written once it's needed. */
    public static void setToggled(NBTTagCompound tag, int ordinal, boolean set) {
        if (tag == null || ordinal < 0 || ordinal >= 64) {
            return;
        }
        String key = ordinal < 32 ? TOGGLED : TOGGLED2;
        int bits = tag.getInteger(key);
        bits = set ? bits | (1 << (ordinal & 31)) : bits & ~(1 << (ordinal & 31));
        if (bits == 0 && key.equals(TOGGLED2)) {
            tag.removeTag(TOGGLED2);
        } else {
            tag.setInteger(key, bits);
        }
    }

    /**
     * Shift page of a Singular piece (plan §6 "Отображение"): the points towards the next level (or
     * «готово»), the next level's tasks with their progress, what opens next, the branches (chestplate)
     * and the level bonuses.
     */
    private void levelLines(ItemStack stack, EntityPlayer player, List list) {
        int lvl = com.sc.util.SingularLevel.levelOf(stack);
        if (lvl < com.sc.util.SingularLevel.MAX) {
            int pts = com.sc.util.SingularLevel.points(stack), need = com.sc.util.SingularLevel.threshold(lvl);
            list.add(com.sc.util.SingularLevel.pointsFull(stack)
                    ? "\u00a7a" + Lang.tr("sc.tooltip.armor.singular.points.ready", pts, need)
                    : "\u00a7d" + Lang.tr("sc.tooltip.armor.singular.points", pts, need));
            int next = lvl + 1;
            boolean done = com.sc.util.SingularLevel.taskDone(player, next);
            list.add((done ? "\u00a7a" : "\u00a7d") + Lang.tr(done ? "sc.tooltip.armor.singular.tasks.done" : "sc.tooltip.armor.singular.tasks", next));
            for (int i = 0; i < com.sc.util.SingularLevel.TASKS; i++) {
                int[] pr = com.sc.util.SingularLevel.taskProgress(player, next, i);
                boolean ok = pr[0] >= pr[1];
                com.sc.util.TooltipSC.wrap(list, (ok ? "+ " : "- ") + Lang.tr("sc.tooltip.armor.singular.task." + next + "." + i, pr[0], pr[1]),
                        ok ? "\u00a7a" : "\u00a77");
            }
            StringBuilder opens = new StringBuilder();
            for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
                if (f.availableIn(suit, armorType) && com.sc.util.SingularLevel.requiredLevel(f) == next) {
                    opens.append(opens.length() > 0 ? ", " : "").append(Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT)));
                }
            }
            if (opens.length() > 0) {
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.singular.opens", next, opens.toString()), "\u00a77");
            }
        } else {
            list.add("\u00a7a" + Lang.tr("sc.tooltip.armor.singular.max"));
        }
        if (armorType == 1) {
            for (int bl : new int[]{3, 5}) {
                String a = Lang.tr("sc.armorfn." + com.sc.util.SingularLevel.branchFeature(bl, 1).name().toLowerCase(java.util.Locale.ROOT));
                String b = Lang.tr("sc.armorfn." + com.sc.util.SingularLevel.branchFeature(bl, 2).name().toLowerCase(java.util.Locale.ROOT));
                int c = com.sc.util.SingularLevel.branchChoice(stack, bl);
                String state = c == 1 ? a : c == 2 ? b
                        : Lang.tr(lvl >= bl ? "sc.tooltip.armor.singular.branch.pick" : "sc.tooltip.armor.singular.branch.later", a, b);
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.singular.branch", bl, state), c > 0 ? "\u00a7b" : "\u00a77");
            }
        }
        if (lvl > 1) {
            boolean sync = com.sc.util.SingularLevel.synced(stack);
            com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.singular.bonus",
                    com.sc.util.SingularLevel.bonusPercent(lvl, sync, com.sc.util.SingularLevel.TANK_PCT),
                    com.sc.util.SingularLevel.bonusPercent(lvl, sync, com.sc.util.SingularLevel.PROTECT_PCT),
                    com.sc.util.SingularLevel.bonusPercent(lvl, sync, com.sc.util.SingularLevel.EU_PCT))
                    + (sync ? " " + Lang.tr("sc.tooltip.armor.singular.sync") : ""), "\u00a7b");
        }
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
        list.add(Lang.tr("sc.tooltip.armor.charge", chargeOf(stack), cap()));
        if (suit.hasSchemes()) {
            list.add(Lang.tr("sc.tooltip.armor.scheme", Lang.tr(com.sc.util.SingularScheme.of(stack).langKey())));
        }
        if (suit == ArmorSuit.SINGULAR) {
            list.add(Lang.tr("sc.tooltip.armor.singular.level", com.sc.util.SingularLevel.levelOf(stack), com.sc.util.SingularLevel.MAX));
        }
        if (!powered(stack)) {
            list.add(Lang.tr("sc.tooltip.armor.empty"));
        }
        java.util.List<String> names = new java.util.ArrayList<String>();
        java.util.List<Boolean> on = new java.util.ArrayList<Boolean>();
        int lit = 0;
        for (com.sc.util.ArmorFeature f : com.sc.util.ArmorFeature.values()) {
            if (f.availableIn(suit, armorType)) {
                String n = Lang.tr("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT));
                if (!com.sc.util.SingularLevel.unlocked(player, f, stack)) {    // opens at a higher level
                    n += " \u00a78" + Lang.tr("sc.tooltip.armor.singular.at", com.sc.util.SingularLevel.requiredLevel(f));
                }
                names.add(n);
                on.add(isEnabled(stack, f));
                lit += isEnabled(stack, f) ? 1 : 0;
            }
        }
        switch (com.sc.util.TooltipSC.page()) {
            case 1:
                list.add(Lang.tr("sc.tooltip.functions", lit, names.size()));
                com.sc.util.TooltipSC.pairs(list, names, on);
                gasLines(stack, list);
                if (suit == ArmorSuit.SINGULAR) {               // K10: the Exo functions on less gas; stage 3: the level
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.singular.legacy",
                            Math.round((1F - com.sc.util.ArmorGasSC.SINGULAR_GAS_MUL) * 100)), "\u00a7d");
                    levelLines(stack, player, list);
                }
                if (ArmorLogicSC.strict(suit)) {
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.strict"), "§6");
                    // С-3: the helium loop is in the chestplate - a piece worn without one is always in emergency mode
                    com.sc.util.TooltipSC.wrap(list, Lang.tr(armorType == 1 ? "sc.tooltip.armor.loop.chest" : "sc.tooltip.armor.loop"), "§7");
                }
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.howto", suit.chargeTier.name())
                        + " " + Lang.tr("sc.tooltip.armor.keys"), "\u00a77");
                if (suit.hasSchemes()) {
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.singular.more"), "\u00a77");
                }
                if (suit == ArmorSuit.EXO) {                     // \u0411-1: the way to the Singular suit
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.armor.exo.convert"), "\u00a7d");
                }
                break;
            default:
                list.add(Lang.tr("sc.tooltip.functions", lit, names.size()));
                com.sc.util.TooltipSC.hintShift(list);
        }
    }

    /** Shift page: the gases inside - one line for each gas this piece has a tank of (helium on the radiator pieces too). */
    private static void gasLines(ItemStack stack, List list) {
        boolean head = false;
        for (com.sc.util.ArmorGasSC.Gas g : com.sc.util.ArmorGasSC.Gas.values()) {
            int cap = com.sc.util.ArmorGasSC.capacity(stack, g);
            if (cap <= 0) {
                continue;
            }
            if (!head) {
                list.add(Lang.tr("sc.tooltip.armor.gases"));
                head = true;
            }
            String name = Lang.trOr("sc.gas." + g.key(), g.key());
            list.add(Lang.tr("sc.tooltip.armor.gas", name, com.sc.util.ArmorGasSC.amount(stack, g), cap));
        }
        int bonus = com.sc.util.ArmorGasSC.capacityBonusPercent(stack);
        if (head && bonus > 0) {
            list.add(Lang.tr("sc.tooltip.armor.gasbonus", bonus));
        }
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (double) chargeOf(stack) / cap();
    }

    /** Creative tab: an empty and a fully charged piece, like IC2; the Singular suit also charged in each other colour scheme. */
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
        ItemStack full = new ItemStack(item);
        setCharge(full, cap());
        list.add(full);
        if (suit.hasSchemes()) {
            for (com.sc.util.SingularScheme s : com.sc.util.SingularScheme.values()) {
                if (s != com.sc.util.SingularScheme.DEFAULT) {
                    ItemStack v = full.copy();
                    com.sc.util.SingularScheme.setScheme(v, s);
                    list.add(v);
                }
            }
        }
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
        if (!world.isRemote) {
            com.sc.util.SingularLevel.clearSync(stack);      // СБ-2: not worn (main inventory) - no set bonus (Р4)
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
            if (!((TileEntityEnergyBase) te).canItemCharge(player)) {
                return true;
            }
            int moved = ((TileEntityEnergyBase) te).extractForItemCharging(cap() - chargeOf(stack));
            charge(stack, moved);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.charged",
                    moved, chargeOf(stack), cap()));
        }
        return true;
    }

    /** A piece's capacity (x the config's armorCapacity). */
    public int cap() {
        return com.sc.util.ConfigSC.scale(suit.maxCharge, com.sc.util.ConfigSC.armorCapacity, 1);
    }

    /** EU a point of absorbed damage costs (x the config's armorDamageCost). */
    private double damageCost() {
        return Math.max(1.0, suit.euPerDamage * (double) com.sc.util.ConfigSC.armorDamageCost);
    }

    // ---- ISpecialArmor: protection paid in EU ----

    private boolean powered(ItemStack stack) {
        return chargeOf(stack) >= damageCost();
    }

    @Override
    public ArmorProperties getProperties(EntityLivingBase player, ItemStack armor, DamageSource source, double damage, int slot) {
        if (source.isUnblockable() || !powered(armor)) {
            return new ArmorProperties(0, 0, 0);
        }
        // Forge works in 1/25ths of a damage point here, so the EU left buys 25 x charge / cost.
        int absorbMax = (int) Math.min(Integer.MAX_VALUE, 25.0 * chargeOf(armor) / damageCost());
        return new ArmorProperties(0, protection(player) / 25.0, absorbMax);
    }

    @Override
    public int getArmorDisplay(EntityPlayer player, ItemStack armor, int slot) {
        return powered(armor) ? protection(player) : 0;
    }

    /** The plating's armour points worn by `wearer`: in emergency mode (Quantum / Exo, no helium) only as good as iron. */
    private int protection(EntityLivingBase wearer) {
        if (wearer instanceof EntityPlayer) {
            return protectionIn(com.sc.util.ArmorGasSC.wornSet((EntityPlayer) wearer));
        }
        return damageReduceAmount;
    }

    /** Armour points of this piece with the set `worn` (no player needed): iron's 2 / 6 / 5 / 2 in emergency mode. */
    public int protectionIn(ItemStack[] worn) {
        return ArmorLogicSC.pieceEmergency(worn, suit) ? ArmorMaterial.IRON.getDamageReductionAmount(armorType) : damageReduceAmount;
    }

    /** Absorbed damage costs EU - less for a full Quantum set and in combat mode (ArmorLogicSC). */
    @Override
    public void damageArmor(EntityLivingBase entity, ItemStack stack, DamageSource source, int damage, int slot) {
        float mul = entity instanceof EntityPlayer ? com.sc.item.ArmorLogicSC.absorbCostMul((EntityPlayer) entity) : 1F;
        discharge(stack, (int) Math.ceil(damage * damageCost() * mul));
        if (entity instanceof EntityPlayer && suit == ArmorSuit.SINGULAR) {
            SingularProgressSC.absorbed((EntityPlayer) entity, damage);     // ОЧ3: a point a damage point absorbed
        }
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
        return cap();
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
