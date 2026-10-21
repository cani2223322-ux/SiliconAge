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
                list.add("\u00a77" + Lang.tr("sc.tooltip.weapon.stats", type.damagePerHit, type.shotsPerUse, type.range, reloadSeconds(type)));
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
        // ОР-5 (audit 2026-10-20): a short reload - a held right-click fires at the weapon's own rate (no EU, no shot before it)
        net.minecraft.nbt.NBTTagCompound pd = player.getEntityData();
        String shotKey = SHOT_KEY + type.name();
        long now = world.getTotalWorldTime();
        if (pd.hasKey(shotKey) && coolingDown(pd.getLong(shotKey), now, type.cooldownTicks())) {
            return stack;
        }
        int charge = getCharge(stack);
        if (charge < type.euPerShot) {
            player.addChatComponentMessage(new ChatComponentTranslation("sc.chat.weapon.nocharge",
                    new ChatComponentTranslation(getUnlocalizedName() + ".name")));
            return stack;
        }
        int before = charge;
        Entity asked = null, hitLast = null;
        for (int i = 0; i < type.shotsPerUse && charge >= type.euPerShot; i++) {
            Entity target = findTarget(world, player);
            if (target == null) {
                charge -= type.euPerShot;        // a miss still spends the shot
                continue;
            }
            hitLast = target;
            // A shot is a player's attack like a left-click: AttackEntityEvent once per target, so a private
            // field / another mod's claim can refuse it - no damage, no EU spent
            if (target != asked) {
                if (!BladeLogicSC.claimed(player, target)) {
                    break;
                }
                asked = target;
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
        if (charge < before) {                            // ОР-5: fired (a hit or a miss) - the reload, the sound, the beam
            pd.setLong(shotKey, now);
            shotEffects(world, player, hitLast);
        }
        return stack;
    }

    /** ОР-5: the player's entity data key (not persisted) of the weapon type's last shot, by WeaponType name. */
    private static final String SHOT_KEY = "scWeaponShot.";
    /** ОР-5: the beam's particles a block and at most. */
    private static final int BEAM_PER_BLOCK = 2, BEAM_MAX = 48;

    /** ОР-5 ★: the type's reload in seconds for the tooltip and the handbook ("0,5", "1", "1,5" - the language's decimal mark). */
    public static String reloadSeconds(WeaponType t) {
        float s = t.cooldownTicks() / 20F;
        return s == Math.round(s) ? String.valueOf(Math.round(s))
                : String.format(java.util.Locale.ROOT, "%.1f", s).replace(".", Lang.tr("sc.num.decimal"));
    }

    /** ОР-5, pure: a shot at `last` keeps the weapon reloading at `now` (a world time turned back - no). */
    public static boolean coolingDown(long last, long now, int cooldown) {
        return now >= last && now - last < cooldown;
    }

    /**
     * ОР-5: the shot's sound at the eyes and a beam of the type's colour from the muzzle to the target's chest, or on a miss to the
     * first solid block on the look line (or the end of the range); sparks on a hit. Server; nothing for a FakePlayer.
     */
    private void shotEffects(World world, EntityPlayer player, Entity hit) {
        if (!(world instanceof net.minecraft.world.WorldServer) || player instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        double eyeY = player.posY + 1.62D - player.yOffset + (player.getEyeHeight() - player.getDefaultEyeHeight());
        Vec3 look = player.getLookVec();
        double x0 = player.posX + look.xCoord * 0.4, y0 = eyeY - 0.15 + look.yCoord * 0.4, z0 = player.posZ + look.zCoord * 0.4;
        float pitch = type == WeaponType.ION_CUTTER ? 1.9F : type == WeaponType.PULSE_EMITTER ? 1.4F : 0.9F;
        com.sc.util.SoundsSC.play(world, player.posX, eyeY, player.posZ, "weapon.shot", 0.8F, pitch);
        double x1, y1, z1;
        if (hit != null) {
            x1 = hit.posX;
            y1 = hit.posY + hit.height * 0.6D;
            z1 = hit.posZ;
        } else {
            Vec3 eyes = Vec3.createVectorHelper(player.posX, eyeY, player.posZ);
            Vec3 end = eyes.addVector(look.xCoord * type.range, look.yCoord * type.range, look.zCoord * type.range);
            MovingObjectPosition wall = world.func_147447_a(copy(eyes), copy(end), false, true, false);
            Vec3 to = wall != null && wall.hitVec != null ? wall.hitVec : end;
            x1 = to.xCoord;
            y1 = to.yCoord;
            z1 = to.zCoord;
        }
        net.minecraft.world.WorldServer ws = (net.minecraft.world.WorldServer) world;
        int rgb = type.beamColor();
        double red = Math.max(0.01D, (rgb >> 16 & 255) / 255D), green = (rgb >> 8 & 255) / 255D, blue = (rgb & 255) / 255D;
        double len = Math.sqrt((x1 - x0) * (x1 - x0) + (y1 - y0) * (y1 - y0) + (z1 - z0) * (z1 - z0));
        int n = (int) Math.max(2, Math.min(BEAM_MAX, Math.round(len * BEAM_PER_BLOCK)));
        for (int i = 1; i <= n; i++) {
            double k = i / (double) (n + 1);
            ws.func_147487_a("reddust", x0 + (x1 - x0) * k, y0 + (y1 - y0) * k, z0 + (z1 - z0) * k, 0, red, green, blue, 1.0D);
        }
        if (hit != null) {
            ws.func_147487_a("fireworksSpark", x1, y1, z1, 6, 0.1D, 0.1D, 0.1D, 0.05D);
        }
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
