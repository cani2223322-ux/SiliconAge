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
import com.sc.util.SingularScheme;
import com.sc.util.ToolLevelSC;

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
 * Electric drills (Nano / Quantum / Exo / Singular), styled after the suits and IC2's drills: pickaxe +
 * shovel, EU per block, no durability, a small battery - worn energy armour feeds it and its
 * weapon charger tops it up. Functions (DrillFeature: area modes, silk touch / fortune, vein,
 * autosmelt, laser, link...) are switched and bound in the armour screen (K, "Drill" tab); the
 * breaking itself is DrillLogicSC. Right-click places a torch; sneak + right-click charges from a
 * machine or links a chest. Its heat is its own.
 * The Singular drill (docs/plan-singular-tools.md) adds levels, branches, the black hole mode and the
 * colour scheme (ToolLevelSC, NBT): its icon follows the scheme, taken from the worn Singular chestplate.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemDrillSC extends Item implements ic2.api.item.ISpecialElectricItem {

    private static final String CHARGE = "ChargeSC", TOGGLED = "FnToggled", HEAT = "HeatSC", OVERHEAT = "OverheatSC";
    private static final Set<String> TOOLS = ImmutableSet.of("pickaxe", "shovel");

    private final DrillType type;
    private IIcon icon;
    /** Singular: an icon per colour scheme (SingularScheme ordinal). */
    private IIcon[] schemeIcons;
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

    /**
     * Whether this drill has the function and it is switched on (a fresh drill: its default). The black hole is
     * its mode: on while "SingHole" holds a size. The Singular functions' level: DrillLogicSC.unlocked / on.
     */
    public static boolean isEnabled(ItemStack stack, DrillFeature f) {
        DrillType t = typeOf(stack);
        if (t == null || !f.availableIn(t)) {
            return false;
        }
        if (f == DrillFeature.BLACK_HOLE) {
            return blackHoleSize(stack) > 0;
        }
        boolean toggled = stack.hasTagCompound() && (stack.getTagCompound().getInteger(TOGGLED) & (1 << f.ordinal())) != 0;
        return f.onByDefault != toggled;
    }

    public static void setEnabled(ItemStack stack, DrillFeature f, boolean on) {
        if (f == DrillFeature.BLACK_HOLE) {                // the mode: on - the smallest zone unless one is set, off - none
            if (!on) {
                setBlackHoleSize(stack, 0);
            } else if (blackHoleSize(stack) == 0) {
                setBlackHoleSize(stack, com.sc.util.DrillZoneSC.HOLE_SIZES[1]);
            }
            return;
        }
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

    /** Singular, NBT "SingHole": the black hole mode's zone - 0 (off), 5, 9 or 12. Client-safe. */
    public static final String HOLE = "SingHole", HOLE_DEPTH = "SingHoleDepth";

    public static int blackHoleSize(ItemStack stack) {
        int v = stack != null && stack.hasTagCompound() ? stack.getTagCompound().getInteger(HOLE) : 0;
        return v == 5 || v == 9 || v == 12 ? v : 0;
    }

    /** Singular, NBT "SingHoleDepth": the black hole's depth - 1, or 3 (a tunnel). Client-safe. */
    public static int tunnelDepth(ItemStack stack) {
        return stack != null && stack.hasTagCompound() && stack.getTagCompound().getInteger(HOLE_DEPTH) == 3 ? 3 : 1;
    }

    /** Sets the black hole's size (0 / 5 / 9 / 12; anything else: off). */
    public static void setBlackHoleSize(ItemStack stack, int size) {
        tag(stack).setInteger(HOLE, size == 5 || size == 9 || size == 12 ? size : 0);
    }

    /** Sets the black hole's depth (3: a tunnel, anything else: 1). */
    public static void setTunnelDepth(ItemStack stack, int depth) {
        tag(stack).setInteger(HOLE_DEPTH, depth == 3 ? 3 : 1);
    }

    /** Singular, NBT: blocks dug towards the next level point ("SingDigCnt"), units towards the next crumb ("SingCrumbCnt"). */
    public static final String DIG_COUNT = "SingDigCnt", CRUMB_COUNT = "SingCrumbCnt", STONE_COUNT = "SingStoneCnt";

    public static int digCounter(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? Math.max(0, stack.getTagCompound().getInteger(DIG_COUNT)) : 0;
    }

    public static void setDigCounter(ItemStack stack, int n) {
        tag(stack).setInteger(DIG_COUNT, Math.max(0, n));
    }

    public static int crumbCounter(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? Math.max(0, stack.getTagCompound().getInteger(CRUMB_COUNT)) : 0;
    }

    public static void setCrumbCounter(ItemStack stack, int n) {
        tag(stack).setInteger(CRUMB_COUNT, Math.max(0, n));
    }

    /** Natural stone dug towards the next crumb unit (ItemSingularCrumbSC.STONE_DIV of it make one). */
    public static int stoneCounter(ItemStack stack) {
        return stack != null && stack.hasTagCompound() ? Math.max(0, stack.getTagCompound().getInteger(STONE_COUNT)) : 0;
    }

    public static void setStoneCounter(ItemStack stack, int n) {
        tag(stack).setInteger(STONE_COUNT, Math.max(0, n));
    }

    /** Blocks counted towards the next singularity crumb, 0..CRUMB_BLOCKS (the HUD). Client-safe. */
    public static int crumbProgress(ItemStack stack) {
        return Math.min(ItemSingularCrumbSC.CRUMB_BLOCKS, crumbCounter(stack));
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
        String base = Reference.ASSETS + ":drill" + Character.toUpperCase(type.key().charAt(0)) + type.key().substring(1);
        if (type == DrillType.SINGULAR) {                  // drillSingular_a ... _k
            SingularScheme[] all = SingularScheme.values();
            schemeIcons = new IIcon[all.length];
            for (SingularScheme s : all) {
                schemeIcons[s.ordinal()] = register.registerIcon(base + "_" + s.key());
            }
            icon = schemeIcons[SingularScheme.DEFAULT.ordinal()];
            return;
        }
        icon = register.registerIcon(base);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    @Override
    public IIcon getIconIndex(ItemStack stack) {
        return schemeIcons != null ? schemeIcons[ToolLevelSC.schemeOf(stack).ordinal()] : icon;
    }

    @Override
    public IIcon getIcon(ItemStack stack, int pass) {
        return getIconIndex(stack);
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
        int funnel = DrillLogicSC.funnelRadius(player, stack) * 2 + 1;
        String mode = isEnabled(stack, DrillFeature.VEIN) ? Lang.tr("sc.drillfn.vein")
                : funnel > 1 ? funnel + "x" + funnel
                : isEnabled(stack, DrillFeature.TUNNEL) ? Lang.tr("sc.drillfn.tunnel")
                : isEnabled(stack, DrillFeature.AREA_5X5) ? "5x5" : isEnabled(stack, DrillFeature.AREA_3X3) ? "3x3" : "1x1";
        if (isEnabled(stack, DrillFeature.SILK)) {
            mode += ", " + Lang.tr("sc.drillfn.silk");
        } else if (isEnabled(stack, DrillFeature.FORTUNE) && type.fortune > 0) {
            mode += ", " + Lang.tr("sc.drillfn.fortune") + " " + Lang.tr("enchantment.level." + type.fortune);
        }
        list.add(Lang.tr(overheated(stack) ? "sc.tooltip.drill.statehot" : "sc.tooltip.drill.state", mode, heatPercent(stack)));
        boolean sing = type == DrillType.SINGULAR;
        if (sing) {
            ItemBladeSC.singularSummary(stack, list);
            int hole = DrillLogicSC.holeSize(player, stack);
            list.add(hole > 0 ? Lang.tr("sc.tooltip.tool.sing.hole", hole, hole, tunnelDepth(stack)) : Lang.tr("sc.tooltip.tool.sing.hole.off"));
            if (hole > 0) {
                list.add("\u00a77" + Lang.tr("sc.tooltip.drill.sing.crumbs", crumbProgress(stack), ItemSingularCrumbSC.CRUMB_BLOCKS));
            }
        }
        switch (com.sc.util.TooltipSC.page()) {
            case 1: {
                java.util.List<String> names = new java.util.ArrayList<String>();
                java.util.List<Boolean> on = new java.util.ArrayList<Boolean>();
                int lit = 0;
                java.util.List<String> locked = new java.util.ArrayList<String>();
                for (DrillFeature f : DrillFeature.values()) {
                    if (!f.availableIn(type)) {
                        continue;
                    }
                    if (!DrillLogicSC.unlocked(player, stack, f)) {      // not opened by the Singular level yet
                        locked.add(Lang.tr("sc.tooltip.drill.sing.lockitem", Lang.tr("sc.drillfn." + f.key()), f.singLevel()));
                        continue;
                    }
                    boolean fnOn = DrillLogicSC.on(player, stack, f);
                    names.add(Lang.tr("sc.drillfn." + f.key()));
                    on.add(fnOn);
                    lit += fnOn ? 1 : 0;
                }
                list.add(Lang.tr("sc.tooltip.functions", lit, names.size()));
                com.sc.util.TooltipSC.pairs(list, names, on);
                if (!locked.isEmpty()) {
                    StringBuilder sb = new StringBuilder();
                    for (String l : locked) {
                        sb.append(sb.length() > 0 ? ", " : "").append(l);
                    }
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.drill.sing.locked", sb.toString()), "\u00a78");
                }
                list.add("\u00a77" + Lang.tr("sc.tooltip.drill.stats", type.euPerBlock, type.harvestLevel));
                if (DrillFeature.AUTOSMELT.availableIn(type)) {
                    list.add("\u00a77" + Lang.tr("sc.tooltip.drill.smeltnoxp"));   // a furnace's XP isn't given
                }
                if (sing) {
                    ItemBladeSC.singularDetails(list, false);
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.drill.sing.hole", DrillFeature.HOLE_BLOCKS_PER_MB,
                            DrillFeature.BLACK_HOLE.cooldownTicks() / 20), "\u00a7d");
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.drill.sing.crumbinfo", ItemSingularCrumbSC.CRUMB_BLOCKS,
                            ItemSingularCrumbSC.ORE_MUL, DrillFeature.BLOCKS_PER_POINT, ItemSingularCrumbSC.STONE_DIV), "\u00a77");
                    int b = ToolLevelSC.branchOf(stack);
                    if (b != ToolLevelSC.BRANCH_NONE) {
                        com.sc.util.TooltipSC.wrap(list, Lang.tr(ToolLevelSC.branchLangKey(stack, b) + ".desc"), "\u00a77");
                    }
                }
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            }
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.drill.howto", type.chargeTier.name()), "\u00a77");
                if (sing) {
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.tool.sing.drill.keys"), "\u00a7d");
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.drill.sing.controls"), "\u00a7d");
                    com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.tool.sing.station"), "\u00a77");
                }
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

    /** Singular: Shift + right-click in the air (or on a block that took nothing) - the black hole's depth 1 <-> 3. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (type == DrillType.SINGULAR && player.isSneaking() && !world.isRemote) {
            DrillLogicSC.toggleDepth(player);
        }
        return stack;
    }

    // ---- once a second: cooling ----

    @Override
    public void onUpdate(ItemStack stack, World world, Entity entity, int slot, boolean held) {
        if (!world.isRemote && entity.ticksExisted % 20 == 0 && heatOf(stack) > 0
                && !(entity instanceof EntityPlayer && DrillLogicSC.digging((EntityPlayer) entity, stack))) {
            int h = heatOf(stack);
            if (entity instanceof EntityPlayer && DrillLogicSC.dumpsHeat((EntityPlayer) entity, stack)) {
                ArmorLogicSC.addHeat((EntityPlayer) entity, h);   // full Singular suit: what heat is left in the drill goes into the suit
                addHeat(stack, -h);
            } else {
                addHeat(stack, -type.heatDissipation);
            }
        }
        // Singular: the worn Singular chestplate's colour scheme, once a second (not while digging: the block would restart)
        if (type == DrillType.SINGULAR && !world.isRemote && entity instanceof EntityPlayer && entity.ticksExisted % 20 == 11
                && !DrillLogicSC.digging((EntityPlayer) entity, stack)) {
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
