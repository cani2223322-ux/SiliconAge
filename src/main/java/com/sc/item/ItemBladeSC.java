package com.sc.item;

import java.util.List;
import java.util.UUID;

import com.google.common.collect.HashMultimap;
import com.google.common.collect.Multimap;
import com.sc.Reference;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.BladeFeature;
import com.sc.util.BladeForm;
import com.sc.util.BladeType;
import com.sc.util.SingularScheme;
import com.sc.util.ToolLevelSC;

import cpw.mods.fml.common.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.EnumAction;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Energy blades (Nano / Quantum / Exo / Singular), styled after the suits, working like IC2's nano saber:
 * off it is a hilt (a weak hit, no EU), on it hits for full damage at EU per hit and a little per
 * second in hand. Right-click blocks (sword style), sneak + right-click switches the blade, and so
 * does its switch / key in the armour screen (K, "Blade" tab) with the rest of its functions
 * (BladeFeature). Its heat is its own, not the suit's; overheated it goes dark until it has cooled
 * to half. Charged like the suits: energy storage slot, sneak + right-click on a machine, the
 * chestplate's weapon charger, or an IC2 charger of its tier or higher.
 * The Singular blade (docs/plan-singular-tools.md) adds levels, branches, forms and the colour scheme
 * (ToolLevelSC, NBT): its icon follows the scheme, which it takes from the worn Singular chestplate.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemBladeSC extends Item implements ic2.api.item.ISpecialElectricItem {

    private static final String CHARGE = "ChargeSC", TOGGLED = "FnToggled", HEAT = "HeatSC", OVERHEAT = "OverheatSC";
    private static final UUID DAMAGE_ID = UUID.fromString("7a3e1c52-4b9d-4f0e-a6c1-2d5e8f9b0c31");

    private final BladeType type;
    private IIcon iconOff, iconOn;
    /** Singular: an off / on icon per colour scheme (SingularScheme ordinal). */
    private IIcon[] schemeOff, schemeOn;
    /** Singular: an off / on icon per form and colour scheme ([BladeForm ordinal][SingularScheme ordinal]). */
    private IIcon[][] formOff, formOn;
    private Object ic2Manager;

    public ItemBladeSC(BladeType type) {
        this.type = type;
        setMaxStackSize(1);
        setMaxDamage(0);
        setFull3D();
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".blade." + type.key());
    }

    public BladeType getType() {
        return type;
    }

    public static BladeType typeOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemBladeSC ? ((ItemBladeSC) stack.getItem()).type : null;
    }

    // ---- NBT: charge, switches, heat ----

    private static NBTTagCompound tag(ItemStack stack) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new NBTTagCompound());
        }
        return stack.getTagCompound();
    }

    public static int chargeOf(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger(CHARGE) : 0;
    }

    public static int capacityOf(ItemStack stack) {
        BladeType t = typeOf(stack);
        return t == null ? 0 : t.maxCharge;
    }

    public static void setCharge(ItemStack stack, int charge) {
        tag(stack).setInteger(CHARGE, Math.max(0, Math.min(capacityOf(stack), charge)));
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

    /** Whether this blade has the function and it is switched on (a fresh blade: its default). */
    public static boolean isEnabled(ItemStack stack, BladeFeature f) {
        BladeType t = typeOf(stack);
        if (t == null || !f.availableIn(t)) {
            return false;
        }
        boolean toggled = stack.hasTagCompound() && (stack.getTagCompound().getInteger(TOGGLED) & (1 << f.ordinal())) != 0;
        return f.onByDefault != toggled;
    }

    public static void setEnabled(ItemStack stack, BladeFeature f, boolean on) {
        int bits = tag(stack).getInteger(TOGGLED);
        bits = on != f.onByDefault ? bits | (1 << f.ordinal()) : bits & ~(1 << f.ordinal());
        stack.getTagCompound().setInteger(TOGGLED, bits);
        if (f == BladeFeature.LOOTING) {
            syncLooting(stack);
        }
    }

    /**
     * The looting mode is a real Looting enchantment on the blade (vanilla then applies it to mob
     * drops, and the blade gets the glint and the tooltip line): the tier's level while the mode is
     * on, none while off. @return whether the NBT changed
     */
    public static boolean syncLooting(ItemStack stack) {
        BladeType t = typeOf(stack);
        if (t == null) {
            return false;
        }
        int want = isEnabled(stack, BladeFeature.LOOTING) ? t.looting : 0;
        int id = net.minecraft.enchantment.Enchantment.looting.effectId;
        net.minecraft.nbt.NBTTagList list = stack.getEnchantmentTagList();
        int have = 0;
        if (list != null) {
            for (int i = 0; i < list.tagCount(); i++) {
                if (list.getCompoundTagAt(i).getShort("id") == id) {
                    have = list.getCompoundTagAt(i).getShort("lvl");
                }
            }
        }
        if (have == want) {
            return false;
        }
        if (list != null) {
            for (int i = list.tagCount() - 1; i >= 0; i--) {
                if (list.getCompoundTagAt(i).getShort("id") == id) {
                    list.removeTag(i);
                }
            }
            if (list.tagCount() == 0) {
                stack.getTagCompound().removeTag("ench");
            }
        }
        if (want > 0) {
            stack.addEnchantment(net.minecraft.enchantment.Enchantment.looting, want);
        }
        return true;
    }

    public static int heatOf(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger(HEAT) : 0;
    }

    public static boolean overheated(ItemStack stack) {
        return stack != null && stack.hasTagCompound() && stack.getTagCompound().getBoolean(OVERHEAT);
    }

    public static int heatPercent(ItemStack stack) {
        BladeType t = typeOf(stack);
        return t == null ? 0 : heatOf(stack) * 100 / t.heatCapacity;
    }

    /**
     * Heat up (or cool down, negative): capped at the capacity; at 100% the blade overheats and
     * stays dark until it is back to half.
     */
    public static void addHeat(ItemStack stack, int heat) {
        BladeType t = typeOf(stack);
        if (t == null || heat == 0) {
            return;
        }
        int h = Math.max(0, Math.min(t.heatCapacity, heatOf(stack) + heat));
        NBTTagCompound nbt = tag(stack);
        nbt.setInteger(HEAT, h);
        if (h >= t.heatCapacity) {
            nbt.setBoolean(OVERHEAT, true);
        } else if (h * 2 <= t.heatCapacity) {
            nbt.removeTag(OVERHEAT);
        }
    }

    /** Switched on and not overheated - lit, whatever the charge (an empty blade switches itself off). */
    public static boolean isLit(ItemStack stack) {
        return isEnabled(stack, BladeFeature.BLADE) && !overheated(stack);
    }

    /** The Singular blade's form (NBT "SingForm"; none / unknown / not a Singular blade: SWORD). Client-safe. */
    public static BladeForm formOf(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound() || !stack.getTagCompound().hasKey(BladeForm.NBT)) {
            return BladeForm.DEFAULT;
        }
        return BladeForm.of(stack.getTagCompound().getInteger(BladeForm.NBT));
    }

    public static void setForm(ItemStack stack, BladeForm form) {
        if (stack != null) {
            tag(stack).setInteger(BladeForm.NBT, (form == null ? BladeForm.DEFAULT : form).ordinal());
        }
    }

    // ---- look ----

    @Override
    public void registerIcons(IIconRegister register) {
        String base = Reference.ASSETS + ":blade" + Character.toUpperCase(type.key().charAt(0)) + type.key().substring(1);
        if (type == BladeType.SINGULAR) {                  // bladeSingular_a / bladeSingularOn_a ... _k
            SingularScheme[] all = SingularScheme.values();
            schemeOff = new IIcon[all.length];
            schemeOn = new IIcon[all.length];
            for (SingularScheme s : all) {
                schemeOff[s.ordinal()] = register.registerIcon(base + "_" + s.key());
                schemeOn[s.ordinal()] = register.registerIcon(base + "On_" + s.key());
            }
            iconOff = schemeOff[SingularScheme.DEFAULT.ordinal()];
            iconOn = schemeOn[SingularScheme.DEFAULT.ordinal()];
            BladeForm[] forms = BladeForm.values();         // bladeSingular_<form>_<k> / bladeSingularOn_<form>_<k>
            formOff = new IIcon[forms.length][all.length];
            formOn = new IIcon[forms.length][all.length];
            for (BladeForm f : forms) {
                for (SingularScheme s : all) {
                    formOff[f.ordinal()][s.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + formIconName(f, s, false));
                    formOn[f.ordinal()][s.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + formIconName(f, s, true));
                }
            }
            return;
        }
        iconOff = register.registerIcon(base);
        iconOn = register.registerIcon(base + "On");
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return iconOff;
    }

    /** Pure: the Singular blade's texture for a form, a scheme and lit / off - "bladeSingular[On]_<form>_<scheme>". */
    public static String formIconName(BladeForm form, SingularScheme scheme, boolean lit) {
        return "bladeSingular" + (lit ? "On" : "") + "_" + (form == null ? BladeForm.DEFAULT : form).key()
                + "_" + (scheme == null ? SingularScheme.DEFAULT : scheme).key();
    }

    /** The icon for a stack (its lit state, scheme and - Singular - form; `holder` may be null: the stored form). */
    private IIcon iconFor(ItemStack stack, EntityPlayer holder) {
        boolean lit = isLit(stack);
        if (formOff != null) {
            int k = ToolLevelSC.schemeOf(stack).ordinal();
            int f = (holder != null ? BladeSingularSC.effectiveForm(holder, stack) : formOf(stack)).ordinal();
            return lit ? formOn[f][k] : formOff[f][k];
        }
        return lit ? iconOn : iconOff;
    }

    /** Inventory / dropped / framed: no holder known - the stored form. */
    @Override
    public IIcon getIconIndex(ItemStack stack) {
        return iconFor(stack, null);
    }

    @Override
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
    }

    /** In a player's hand: the form that counts for them (one their blade no longer opens shows as the sword). */
    @Override
    public IIcon getIcon(ItemStack stack, int pass, EntityPlayer player, ItemStack usingItem, int useRemaining) {
        return iconFor(stack, player);
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - (double) chargeOf(stack) / type.maxCharge;
    }

    @Override
    public int getItemEnchantability() {
        return 0;
    }

    /** Creative tab: an empty and a fully charged blade, like IC2. */
    @Override
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
        ItemStack full = new ItemStack(item);
        setCharge(full, type.maxCharge);
        list.add(full);
    }

    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.tooltip.armor.charge", chargeOf(stack), type.maxCharge));
        String state = Lang.tr(overheated(stack) ? "sc.tooltip.state.hot" : isLit(stack) ? "sc.tooltip.state.on" : "sc.tooltip.state.off");
        list.add(Lang.tr("sc.tooltip.blade.state", state, isLit(stack) ? type.onDamage : type.offDamage, heatPercent(stack)));
        boolean sing = type == BladeType.SINGULAR;
        if (sing) {
            singularSummary(stack, list);
        }
        switch (com.sc.util.TooltipSC.page()) {
            case 1: {
                java.util.List<String> names = new java.util.ArrayList<String>();
                java.util.List<Boolean> on = new java.util.ArrayList<Boolean>();
                int total = 0, lit = 0, locked = 0;
                for (BladeFeature f : BladeFeature.values()) {
                    if (f == BladeFeature.BLADE || !f.availableIn(type)) {
                        continue;
                    }
                    if (!BladeLogicSC.unlocked(player, stack, f)) {
                        locked++;                           // Singular: opens at a higher level
                        continue;
                    }
                    total++;
                    lit += isEnabled(stack, f) ? 1 : 0;
                    if (f == BladeFeature.LOOTING && isEnabled(stack, f)) {
                        continue;                           // the game's own "Looting V" line says it
                    }
                    names.add(Lang.tr("sc.bladefn." + f.key()));
                    on.add(isEnabled(stack, f));
                }
                list.add(Lang.tr("sc.tooltip.functions", lit, total));
                com.sc.util.TooltipSC.pairs(list, names, on);
                if (locked > 0) {
                    list.add("\u00a78" + Lang.tr("sc.tooltip.tool.sing.locked", locked));
                }
                list.add("\u00a77" + Lang.tr("sc.tooltip.blade.stats", type.euPerHit, type.idlePerSecond, type.chargeTier.name()));
                if (sing) {
                    BladeForm form = BladeSingularSC.effectiveForm(player, stack);
                    list.add("\u00a7d" + Lang.tr("sc.tooltip.tool.sing.formattack", Lang.tr(form.langKey()),
                            Lang.tr(BladeFeature.formAttackKey(form))));
                    singularDetails(list, true);
                    int b = ToolLevelSC.branchOf(stack);
                    if (b != ToolLevelSC.BRANCH_NONE) {        // as the drill: what the chosen branch gives
                        com.sc.util.TooltipSC.wrap(list, Lang.tr(ToolLevelSC.branchLangKey(stack, b) + ".desc"), "\u00a77");
                    }
                }
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            }
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.blade.howto", type.chargeTier.name()), "\u00a77");
                if (sing) {
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.tool.sing.blade.keys"), "\u00a7d");
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.tool.sing.station"), "\u00a77");
                }
                break;
            default:
                com.sc.util.TooltipSC.hintShift(list);
        }
    }

    /** Singular (blade and drill): the scheme, the level with its points, the branch, the blade's form. */
    static void singularSummary(ItemStack stack, List list) {
        list.add(Lang.tr("sc.tooltip.armor.scheme", Lang.tr(ToolLevelSC.schemeOf(stack).langKey())));
        int lvl = ToolLevelSC.levelOf(stack);
        list.add("\u00a7d" + Lang.tr("sc.tooltip.armor.singular.level", lvl, ToolLevelSC.MAX));
        if (lvl < ToolLevelSC.MAX) {
            int pts = ToolLevelSC.points(stack), need = ToolLevelSC.threshold(stack);
            list.add(ToolLevelSC.pointsFull(stack)
                    ? "\u00a7a" + Lang.tr("sc.tooltip.armor.singular.points.ready", pts, need)
                    : "\u00a77" + Lang.tr("sc.tooltip.armor.singular.points", pts, need));
        }
        int b = ToolLevelSC.branchOf(stack);
        if (b != ToolLevelSC.BRANCH_NONE) {
            list.add(Lang.tr("sc.tooltip.tool.sing.branch", Lang.tr(ToolLevelSC.branchLangKey(stack, b))));
        } else if (ToolLevelSC.branchPending(stack)) {
            list.add("\u00a7e" + Lang.tr("sc.tooltip.tool.sing.branch.pending"));
        }
        if (ToolLevelSC.isBlade(stack)) {
            list.add(Lang.tr("sc.tooltip.tool.sing.form", Lang.tr(formOf(stack).langKey())));
        }
    }

    /** Singular, Shift page: the Exo legacy (-20% EU), where the points and the gases come from. */
    static void singularDetails(List list, boolean blade) {
        com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.tool.sing.legacy", Math.round((1F - ToolLevelSC.LEGACY_MUL) * 100)), "\u00a7d");
        com.sc.util.TooltipSC.wrap(list, Lang.tr(blade ? "sc.tooltip.tool.sing.blade.points" : "sc.tooltip.tool.sing.drill.points"), "\u00a77");
        com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.tool.sing.gas"), "\u00a77");
    }

    // ---- fighting ----

    /** The tooltip / vanilla hit: the hilt's damage when off (on, the blade's own attack takes over). */
    @Override
    public Multimap getAttributeModifiers(ItemStack stack) {
        Multimap map = HashMultimap.create();
        // vanilla's weapon id (field_111210_e): the tooltip reads it as the weapon's damage. Lit, the blade's
        // own attack deals exactly onDamage, so that's shown; off, a vanilla hit adds the player's own 1.
        int dmg = isLit(stack) ? type.onDamage : type.offDamage - 1;
        map.put(SharedMonsterAttributes.attackDamage.getAttributeUnlocalizedName(),
                new AttributeModifier(field_111210_e, "SC blade", dmg, 0));
        return map;
    }

    /** A lit blade hits by its own rules (ArmorLogic-style costs, armour pierce, execute); off - a vanilla hit. */
    @Override
    public boolean onLeftClickEntity(ItemStack stack, EntityPlayer player, Entity entity) {
        if (!isLit(stack)) {
            return false;
        }
        if (!player.worldObj.isRemote) {
            BladeLogicSC.attack(player, stack, entity);
        }
        return true;
    }

    /** Server: the Singular spear / whip hit further than the client sends hits (BladeSingularSC.swing). */
    @Override
    public boolean onEntitySwing(EntityLivingBase entity, ItemStack stack) {
        if (type == BladeType.SINGULAR && !entity.worldObj.isRemote && entity instanceof EntityPlayer) {
            BladeSingularSC.swing((EntityPlayer) entity, stack);
        }
        return false;
    }

    @Override
    public EnumAction getItemUseAction(ItemStack stack) {
        return EnumAction.block;
    }

    @Override
    public int getMaxItemUseDuration(ItemStack stack) {
        return 72000;
    }

    /**
     * Right-click: block. Sneak + right-click: switch the blade on / off - done by the client the
     * same way as its key (shown over the hotbar, sent to the server), so nothing happens here on the server.
     */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (player.isSneaking()) {
            if (world.isRemote) {
                com.sc.SCMod.proxy.toggleBlade();
            }
            return stack;
        }
        player.setItemInUse(stack, getMaxItemUseDuration(stack));
        return stack;
    }

    /** Sneak + right-click on a machine / generator: charge from its energy buffer (as the suits do). */
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
            int moved = ((TileEntityEnergyBase) te).extractForItemCharging(type.maxCharge - chargeOf(stack));
            charge(stack, moved);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.charged", moved, chargeOf(stack), type.maxCharge));
        }
        return true;
    }

    // ---- cutting edge: cobweb, leaves, wool, vines, grass ----

    private static boolean cuttable(Block block) {
        Material m = block.getMaterial();
        return block == Blocks.web || m == Material.leaves || m == Material.cloth || m == Material.vine
                || m == Material.plants || m == Material.gourd;
    }

    private static boolean cutting(ItemStack stack) {
        return isLit(stack) && isEnabled(stack, BladeFeature.CUTTING_EDGE) && chargeOf(stack) >= BladeFeature.CUT_COST;
    }

    @Override
    public float getDigSpeed(ItemStack stack, Block block, int meta) {
        if (cuttable(block)) {
            return cutting(stack) ? 100F : block == Blocks.web ? 15F : 1.5F;   // off: like any sword
        }
        return 1F;
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack) {
        return block == Blocks.web;
    }

    /** Leaves, vines and grass drop themselves, as with shears. */
    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        if (player.worldObj.isRemote || !cutting(stack)) {
            return false;
        }
        Block block = player.worldObj.getBlock(x, y, z);
        if (!(block instanceof net.minecraftforge.common.IShearable)) {
            return false;
        }
        net.minecraftforge.common.IShearable target = (net.minecraftforge.common.IShearable) block;
        if (!target.isShearable(stack, player.worldObj, x, y, z)) {
            return false;
        }
        List<ItemStack> drops = target.onSheared(stack, player.worldObj, x, y, z, 0);
        for (ItemStack drop : drops) {
            net.minecraft.entity.item.EntityItem item = new net.minecraft.entity.item.EntityItem(player.worldObj,
                    x + 0.5, y + 0.5, z + 0.5, drop);
            item.delayBeforeCanPickup = 10;
            player.worldObj.spawnEntityInWorld(item);
        }
        BladeLogicSC.pay(player, stack, ToolLevelSC.legacyCost(stack, BladeFeature.CUT_COST));
        player.worldObj.setBlockToAir(x, y, z);
        return true;
    }

    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayer && cuttable(block) && cutting(stack)) {
            BladeLogicSC.pay((EntityPlayer) entity, stack, ToolLevelSC.legacyCost(stack, BladeFeature.CUT_COST));
        }
        return true;
    }

    // ---- once a second: cooling, the lit blade's upkeep ----

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote && entity.ticksExisted % 20 == 7 && !(held && entity instanceof EntityPlayer && ((EntityPlayer) entity).isUsingItem())) {
            syncLooting(stack);                     // fresh blades (on by default) and upgraded ones (the new level)
        }
        if (!world.isRemote && entity instanceof EntityPlayer && entity.ticksExisted % 20 == 0) {
            BladeLogicSC.perSecond((EntityPlayer) entity, stack, slot, held);
        }
        // Singular: the worn Singular chestplate's colour scheme, once a second (not mid-block: that would drop it)
        if (type == BladeType.SINGULAR && !world.isRemote && entity instanceof EntityPlayer && entity.ticksExisted % 20 == 11
                && !(held && ((EntityPlayer) entity).isUsingItem())) {
            ToolLevelSC.syncScheme((EntityPlayer) entity, stack);
        }
    }

    // ---- IC2: charged by batboxes / MFE / MFSU through the suits' manager ----

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
        return type.maxCharge;
    }

    @Override
    public int getTier(ItemStack stack) {
        return type.chargeTier.toIc2Tier();
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return type.chargeTier.getVoltage();
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
