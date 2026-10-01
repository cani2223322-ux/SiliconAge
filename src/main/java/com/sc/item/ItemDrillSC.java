package com.sc.item;

import java.util.List;
import java.util.Set;

import com.google.common.collect.ImmutableSet;
import com.sc.Reference;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.DrillFeature;
import com.sc.util.DrillType;

import cpw.mods.fml.common.Optional;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.inventory.IInventory;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * Electric drills (Nano / Quantum / Exo), styled after the suits and IC2's drills: pickaxe +
 * shovel, EU per block, no durability, a small battery - worn energy armour feeds it and its
 * weapon charger tops it up. Functions (DrillFeature: area modes, silk touch / fortune, vein,
 * autosmelt, laser, link...) are switched and bound in the armour screen (K, "Drill" tab); the
 * breaking itself is DrillLogicSC. Right-click places a torch; sneak + right-click charges from a
 * machine or links a chest. Its heat is its own.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemDrillSC extends Item implements ic2.api.item.ISpecialElectricItem {

    private static final String CHARGE = "ChargeSC", TOGGLED = "FnToggled", HEAT = "HeatSC", OVERHEAT = "OverheatSC";
    private static final Set<String> TOOLS = ImmutableSet.of("pickaxe", "shovel");

    private final DrillType type;
    private IIcon icon;
    private Object ic2Manager;

    public ItemDrillSC(DrillType type) {
        this.type = type;
        setMaxStackSize(1);
        setMaxDamage(0);
        setFull3D();
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".drill." + type.key());
    }

    public DrillType getType() {
        return type;
    }

    public static DrillType typeOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemDrillSC ? ((ItemDrillSC) stack.getItem()).type : null;
    }

    // ---- NBT: charge, switches, heat, the linked chest ----

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
        DrillType t = typeOf(stack);
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

    /** Whether this drill has the function and it is switched on (a fresh drill: its default). */
    public static boolean isEnabled(ItemStack stack, DrillFeature f) {
        DrillType t = typeOf(stack);
        if (t == null || !f.availableIn(t)) {
            return false;
        }
        boolean toggled = stack.hasTagCompound() && (stack.getTagCompound().getInteger(TOGGLED) & (1 << f.ordinal())) != 0;
        return f.onByDefault != toggled;
    }

    public static void setEnabled(ItemStack stack, DrillFeature f, boolean on) {
        int bits = tag(stack).getInteger(TOGGLED);
        bits = on != f.onByDefault ? bits | (1 << f.ordinal()) : bits & ~(1 << f.ordinal());
        stack.getTagCompound().setInteger(TOGGLED, bits);
    }

    public static int heatOf(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger(HEAT) : 0;
    }

    public static boolean overheated(ItemStack stack) {
        return stack != null && stack.hasTagCompound() && stack.getTagCompound().getBoolean(OVERHEAT);
    }

    public static int heatPercent(ItemStack stack) {
        DrillType t = typeOf(stack);
        return t == null ? 0 : heatOf(stack) * 100 / t.heatCapacity;
    }

    /** Heat up (or cool, negative): at 100% the drill stops until it is back to half. */
    public static void addHeat(ItemStack stack, int heat) {
        DrillType t = typeOf(stack);
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

    /** The linked chest {x, y, z, dimension}, or null. */
    public static int[] link(ItemStack stack) {
        if (stack == null || !stack.hasTagCompound() || !stack.getTagCompound().hasKey("LinkX")) {
            return null;
        }
        NBTTagCompound n = stack.getTagCompound();
        return new int[]{n.getInteger("LinkX"), n.getInteger("LinkY"), n.getInteger("LinkZ"), n.getInteger("LinkDim")};
    }

    // ---- look ----

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":drill" + Character.toUpperCase(type.key().charAt(0)) + type.key().substring(1));
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
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

    /** Creative tab: an empty and a fully charged drill, like IC2. */
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
        String mode = isEnabled(stack, DrillFeature.VEIN) ? Lang.tr("sc.drillfn.vein")
                : isEnabled(stack, DrillFeature.TUNNEL) ? Lang.tr("sc.drillfn.tunnel")
                : isEnabled(stack, DrillFeature.AREA_5X5) ? "5x5" : isEnabled(stack, DrillFeature.AREA_3X3) ? "3x3" : "1x1";
        if (isEnabled(stack, DrillFeature.SILK)) {
            mode += ", " + Lang.tr("sc.drillfn.silk");
        } else if (isEnabled(stack, DrillFeature.FORTUNE) && type.fortune > 0) {
            mode += ", " + Lang.tr("sc.drillfn.fortune") + " " + Lang.tr("enchantment.level." + type.fortune);
        }
        list.add(Lang.tr(overheated(stack) ? "sc.tooltip.drill.statehot" : "sc.tooltip.drill.state", mode, heatPercent(stack)));
        switch (com.sc.util.TooltipSC.page()) {
            case 1: {
                java.util.List<String> names = new java.util.ArrayList<String>();
                java.util.List<Boolean> on = new java.util.ArrayList<Boolean>();
                int lit = 0;
                for (DrillFeature f : DrillFeature.values()) {
                    if (f.availableIn(type)) {
                        names.add(Lang.tr("sc.drillfn." + f.key()));
                        on.add(isEnabled(stack, f));
                        lit += isEnabled(stack, f) ? 1 : 0;
                    }
                }
                list.add(Lang.tr("sc.tooltip.functions", lit, names.size()));
                com.sc.util.TooltipSC.pairs(list, names, on);
                list.add("\u00a77" + Lang.tr("sc.tooltip.drill.stats", type.euPerBlock, type.harvestLevel));
                if (DrillFeature.AUTOSMELT.availableIn(type)) {
                    list.add("\u00a77" + Lang.tr("sc.tooltip.drill.smeltnoxp"));   // a furnace's XP isn't given
                }
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            }
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.drill.howto", type.chargeTier.name()), "\u00a77");
                break;
            default:
                com.sc.util.TooltipSC.hintShift(list);
        }
    }

    // ---- digging: what and how fast (the energy check is DrillLogicSC.breakSpeed) ----

    /** What the drill is made for: pickaxe / shovel blocks, and stone, metal, earth, sand, snow, clay, ice, glass. */
    public static boolean suits(Block block, int meta) {
        if (block.isToolEffective("pickaxe", meta) || block.isToolEffective("shovel", meta)) {
            return true;
        }
        Material m = block.getMaterial();
        return m == Material.rock || m == Material.iron || m == Material.anvil || m == Material.ground || m == Material.grass
                || m == Material.sand || m == Material.snow || m == Material.craftedSnow || m == Material.clay
                || m == Material.ice || m == Material.packedIce || m == Material.glass || m == Material.redstoneLight;
    }

    @Override
    public float getDigSpeed(ItemStack stack, Block block, int meta) {
        return suits(block, meta) ? type.speed : 1F;
    }

    @Override
    public Set<String> getToolClasses(ItemStack stack) {
        return TOOLS;
    }

    @Override
    public int getHarvestLevel(ItemStack stack, String toolClass) {
        return TOOLS.contains(toolClass) ? type.harvestLevel : -1;
    }

    @Override
    public boolean canHarvestBlock(Block block, ItemStack stack) {
        return suits(block, 0) && block.getHarvestLevel(0) <= type.harvestLevel;
    }

    /** The drill breaks the block (and the area / vein / tunnel) itself. */
    @Override
    public boolean onBlockStartBreak(ItemStack stack, int x, int y, int z, EntityPlayer player) {
        return !player.worldObj.isRemote && DrillLogicSC.dig(player, stack, x, y, z);
    }

    /** Only reached when the vanilla harvest ran (a block with a tile entity, or out of charge): pay for it. */
    @Override
    public boolean onBlockDestroyed(ItemStack stack, World world, Block block, int x, int y, int z, EntityLivingBase entity) {
        if (!world.isRemote && entity instanceof EntityPlayer && block.getBlockHardness(world, x, y, z) != 0
                && !overheated(stack) && !((EntityPlayer) entity).capabilities.isCreativeMode) {
            DrillLogicSC.pay((EntityPlayer) entity, stack, type.euPerBlock);
        }
        return true;
    }

    // ---- right-click: a torch; sneak: charge from a machine / link a chest ----

    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        TileEntity te = world.getTileEntity(x, y, z);
        if (player.isSneaking()) {
            if (te instanceof TileEntityEnergyBase) {
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
            if (te instanceof IInventory && DrillFeature.LINK.availableIn(type)) {
                if (!world.isRemote) {
                    NBTTagCompound n = tag(stack);
                    n.setInteger("LinkX", x);
                    n.setInteger("LinkY", y);
                    n.setInteger("LinkZ", z);
                    n.setInteger("LinkDim", world.provider.dimensionId);
                    player.addChatComponentMessage(new ChatComponentTranslation("sc.drill.linked", x, y, z));
                }
                return true;
            }
            return false;
        }
        return isEnabled(stack, DrillFeature.TORCH) && DrillLogicSC.placeTorch(player, world, x, y, z, side, hitX, hitY, hitZ);
    }

    // ---- once a second: cooling ----

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote && entity.ticksExisted % 20 == 0 && heatOf(stack) > 0
                && !(entity instanceof EntityPlayer && DrillLogicSC.digging((EntityPlayer) entity, stack))) {
            addHeat(stack, -type.heatDissipation);
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
