package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.WeaponType;

import cpw.mods.fml.common.Optional;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IIcon;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;

/**
 * §7/§16: Ion Cutter / Pulse Emitter / Plasma Rifle. Charge is a plain NBT int (not vanilla
 * item damage - these aren't "damageable" tools, they're rechargeable batteries), capped at
 * the weapon's tier buffer (§9.2). Charged by sneak + right-click on any machine or generator,
 * which hands over what its own energy buffer holds - before that there was no way at all to
 * charge them, so every weapon only ever said "no charge" in survival.
 *
 * TODO(simplification): no real projectile entity/travel time - right-clicking does an instant
 * "hitscan": the look ray from the eyes is tested against every living entity's hitbox (grown
 * by AIM_MARGIN) within range, and the nearest one hit in line of sight takes the shot.
 */
@Optional.Interface(iface = "ic2.api.item.ISpecialElectricItem", modid = Reference.IC2_MODID)
public class ItemWeaponSC extends Item implements ic2.api.item.ISpecialElectricItem {

    /** Hitbox slack for the aim ray, in blocks - small mobs are still hittable without pixel aiming. */
    private static final double AIM_MARGIN = 0.3;

    private final WeaponType type;
    private IIcon icon;
    private Object ic2Manager;

    public ItemWeaponSC(WeaponType type) {
        this.type = type;
        setMaxStackSize(1);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    public WeaponType getType() {
        return type;
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":" + type.textureName);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }

    private static int getCharge(ItemStack stack) {
        return stack.hasTagCompound() ? stack.getTagCompound().getInteger("ChargeSC") : 0;
    }

    private static void setCharge(ItemStack stack, int charge) {
        if (!stack.hasTagCompound()) {
            stack.setTagCompound(new net.minecraft.nbt.NBTTagCompound());
        }
        stack.getTagCompound().setInteger("ChargeSC", charge);
    }

    /** The weapon's charge (ArmorElectricManagerSC, IC2), 0 for anything else. */
    public static int chargeOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemWeaponSC ? getCharge(stack) : 0;
    }

    public static int capacityOf(ItemStack stack) {
        return stack != null && stack.getItem() instanceof ItemWeaponSC ? ((ItemWeaponSC) stack.getItem()).type.tier.getBuffer() : 0;
    }

    /** Charges up to `max` EU (the type from the stack). @return EU actually taken. */
    public static int charge(ItemStack stack, int max) {
        return stack != null && stack.getItem() instanceof ItemWeaponSC ? charge(stack, ((ItemWeaponSC) stack.getItem()).type, max) : 0;
    }

    /** Spends up to `amount` EU. @return EU actually spent. */
    public static int discharge(ItemStack stack, int amount) {
        int spent = Math.max(0, Math.min(chargeOf(stack), amount));
        if (spent > 0) {
            setCharge(stack, getCharge(stack) - spent);
        }
        return spent;
    }

    @Override
    public boolean showDurabilityBar(ItemStack stack) {
        return true;
    }

    @Override
    public double getDurabilityForDisplay(ItemStack stack) {
        return 1.0 - Math.min(1.0, (double) getCharge(stack) / type.tier.getBuffer());
    }

    /** Creative tab: an empty and a fully charged weapon, like the drills. */
    @Override
    public void getSubItems(Item item, net.minecraft.creativetab.CreativeTabs tab, List list) {
        list.add(new ItemStack(item));
        ItemStack full = new ItemStack(item);
        setCharge(full, type.tier.getBuffer());
        list.add(full);
    }

    /** Like the drills and blades: the charge, the numbers on Shift, how to use and charge it on Ctrl. */
    @Override
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.tooltip.weapon.charge", getCharge(stack), type.tier.getBuffer()));
        switch (com.sc.util.TooltipSC.page()) {
            case 1:
                list.add("\u00a77" + Lang.tr("sc.tooltip.weapon.stats", type.damagePerHit, type.shotsPerUse, type.range));
                list.add("\u00a77" + Lang.tr("sc.tooltip.weapon.shots", type.euPerShot, getCharge(stack) / type.euPerShot, type.tier.name()));
                com.sc.util.TooltipSC.hintCtrl(list);
                break;
            case 2:
                com.sc.util.TooltipSC.wrap(list, Lang.tr("sc.tooltip.weapon.use", type.tier.name()), "\u00a77");
                break;
            default:
                com.sc.util.TooltipSC.hintShift(list);
        }
    }

    /** Sneak + right-click on a machine/generator: charge from its energy buffer. */
    @Override
    public boolean onItemUse(ItemStack stack, EntityPlayer player, World world, int x, int y, int z, int side,
                             float hitX, float hitY, float hitZ) {
        if (!player.isSneaking()) {
            return false;            // a plain right-click on a block still fires (onItemRightClick)
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityEnergyBase)) {
            return false;
        }
        if (!world.isRemote) {
            int room = type.tier.getBuffer() - getCharge(stack);
            if (!((TileEntityEnergyBase) te).canItemCharge(player)) {
                return true;
            }
            int moved = ((TileEntityEnergyBase) te).extractForItemCharging(room);
            setCharge(stack, getCharge(stack) + moved);
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.charged",
                    moved, getCharge(stack), type.tier.getBuffer()));
        }
        return true;
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote) {
            return stack;
        }
        int charge = getCharge(stack);
        if (charge < type.euPerShot) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.nocharge",
                    new ChatComponentTranslation(getUnlocalizedName() + ".name")));
            return stack;
        }
        for (int i = 0; i < type.shotsPerUse && charge >= type.euPerShot; i++) {
            Entity target = findTarget(world, player);
            if (target == null) {
                charge -= type.euPerShot;        // a miss still spends the shot
                continue;
            }
            // Burst rounds land in the same tick: without clearing the target's damage cooldown
            // (hurtResistantTime) only the first of the Pulse Emitter's 3 rounds ever counted.
            if (i > 0 && target instanceof EntityLivingBase) {
                target.hurtResistantTime = 0;
            }
            if (target.attackEntityFrom(DamageSource.causePlayerDamage(player), type.damagePerHit)) {
                charge -= type.euPerShot;
            } else if (i == 0) {
                break;                            // still invulnerable from an earlier click - don't waste EU
            } else {
                charge -= type.euPerShot;
            }
        }
        setCharge(stack, charge);
        return stack;
    }

    private Entity findTarget(World world, EntityPlayer player) {
        // Aim from the eyes along the look ray and test it against each candidate's hitbox. The old
        // feet-to-feet direction vs. an eye-level look vector missed close and lower targets that
        // the crosshair was right on.
        // Eye height as vanilla Item.getMovingObjectPositionFromPlayer computes it: a server-side
        // player's posY is its feet and getEyeHeight() alone is only 0.12 there.
        double eyeY = player.posY + 1.62D - player.yOffset + (player.getEyeHeight() - player.getDefaultEyeHeight());
        Vec3 eyes = Vec3.createVectorHelper(player.posX, eyeY, player.posZ);
        Vec3 look = player.getLookVec();
        Vec3 end = eyes.addVector(look.xCoord * type.range, look.yCoord * type.range, look.zCoord * type.range);
        AxisAlignedBB searchBox = player.boundingBox.expand(type.range, type.range, type.range);
        List entities = world.getEntitiesWithinAABBExcludingEntity(player, searchBox);
        Entity best = null;
        double bestDist = type.range;
        for (Object o : entities) {
            if (!(o instanceof EntityLivingBase)) {
                continue;
            }
            Entity candidate = (Entity) o;
            AxisAlignedBB hitbox = candidate.boundingBox.expand(AIM_MARGIN, AIM_MARGIN, AIM_MARGIN);
            Vec3 hitVec;
            if (hitbox.isVecInside(eyes)) {
                hitVec = eyes;
            } else {
                MovingObjectPosition hit = hitbox.calculateIntercept(eyes, end);
                if (hit == null) {
                    continue;
                }
                hitVec = hit.hitVec;
            }
            double dist = eyes.distanceTo(hitVec);
            if (dist > bestDist) {
                continue;
            }
            // Line of sight: it used to hit anything in the cone, straight through walls.
            // (The block raytrace moves its start vector, hence the copies.)
            if (hitVec != eyes && world.func_147447_a(copy(eyes), copy(hitVec), false, true, false) != null) {
                continue;
            }
            bestDist = dist;
            best = candidate;
        }
        return best;
    }

    private static Vec3 copy(Vec3 v) {
        return Vec3.createVectorHelper(v.xCoord, v.yCoord, v.zCoord);
    }

    /** Charges up to `max` EU into the weapon (energy storage slot). @return EU actually taken. */
    public static int charge(ItemStack stack, WeaponType type, int max) {
        int room = type.tier.getBuffer() - getCharge(stack);
        int taken = Math.max(0, Math.min(room, max));
        if (taken > 0) {
            setCharge(stack, getCharge(stack) + taken);
        }
        return taken;
    }

    public static void addCharge(ItemStack stack, WeaponType type, int amount) {
        setCharge(stack, Math.min(type.tier.getBuffer(), getCharge(stack) + amount));
    }

    // ---- IC2: charged by batboxes / MFE / MFSU and batteries through the suits' manager ----

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
        return type.tier.getBuffer();
    }

    @Override
    public int getTier(ItemStack stack) {
        return type.tier.toIc2Tier();
    }

    @Override
    public double getTransferLimit(ItemStack stack) {
        return type.tier.getVoltage();
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
