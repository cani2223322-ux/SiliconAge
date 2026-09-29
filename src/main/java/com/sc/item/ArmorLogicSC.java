package com.sc.item;

import java.util.List;
import java.util.UUID;

import com.sc.util.ArmorFeature;
import com.sc.util.ArmorSuit;

import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.entity.projectile.EntityFireball;
import net.minecraft.entity.projectile.EntityThrowable;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraft.util.Vec3;

/**
 * What the suits' functions (ArmorFeature) do, run for every player: movement helpers each tick
 * on both sides (the client moves the player itself), the rest once a second on the server, plus
 * the full-set bonuses:
 * - Nano set: functions cost a quarter less energy,
 * - Quantum set: absorbing damage costs 30% less energy, a 50% chance not to be knocked back,
 * - Exo set: no need to eat at all - the chestplate's energy keeps the food bar full.
 * Power modes (chestplate): economy halves function costs and weakens speed / jump, combat makes
 * them cost half as much again but absorbing damage 20% cheaper and the shield reach twice as far.
 * An overheated suit (chips shut down, §16) switches its functions and the set bonuses off too.
 */
public final class ArmorLogicSC {

    private static final UUID KNOCKBACK_ID = UUID.fromString("5c1d7c64-8b0e-4c4a-9d57-3a0f1e3c5a11");
    private static final String STEP_FLAG = "scArmorStep", FLIGHT_FLAG = "scArmorFlight", HEAT_KEY = "scArmorHeat";

    private ArmorLogicSC() {
    }

    // ------------------------------------------------------------------ what's worn

    /** The worn piece of that type (0 helmet .. 3 boots), or null. */
    public static ItemStack piece(EntityPlayer p, int armorType) {
        ItemStack s = p.inventory.armorInventory[3 - armorType];
        return s != null && s.getItem() instanceof ItemArmorSC ? s : null;
    }

    public static ArmorSuit suitOf(ItemStack piece) {
        return piece == null ? null : ((ItemArmorSC) piece.getItem()).getSuit();
    }

    /** The suit of a full set (all four pieces of one suit, each with some charge), or null. */
    public static ArmorSuit fullSet(EntityPlayer p) {
        ArmorSuit suit = null;
        for (int t = 0; t < 4; t++) {
            ItemStack s = piece(p, t);
            if (s == null || ItemArmorSC.chargeOf(s) <= 0 || (suit != null && suitOf(s) != suit)) {
                return null;
            }
            suit = suitOf(s);
        }
        return suit;
    }

    private static boolean overheated(EntityPlayer p) {
        ItemStack chest = piece(p, 1);
        return chest != null && chest.hasTagCompound() && chest.getTagCompound().getBoolean("ChipsOffSC");
    }

    /**
     * Worn, switched on, the suit isn't overheated, and the piece holds enough for the function's
     * next payment - a nearly empty piece used to keep flying (or showing ores) for free. The solar
     * film costs nothing, so it works with an empty helmet too (and charges it).
     */
    public static boolean active(EntityPlayer p, ArmorFeature f) {
        ItemStack s = piece(p, f.piece);
        if (s == null || !ItemArmorSC.isEnabled(s, f) || (f != ArmorFeature.HUD && overheated(p))) {
            return false;
        }
        int need = f.euPerSecond > 0 ? (int) Math.ceil(f.euPerSecond * costMul(p)) : f == ArmorFeature.SOLAR ? 0 : 1;
        return ItemArmorSC.chargeOf(s) >= need;
    }

    /** Heat from things that happen between the once-a-second checks (dash, shield), collected per player. */
    public static void addHeat(EntityPlayer p, int heat) {
        NBTTagCompound data = p.getEntityData();
        data.setInteger(HEAT_KEY, data.getInteger(HEAT_KEY) + heat);
    }

    public static int powerMode(EntityPlayer p) {
        return ItemArmorSC.powerMode(piece(p, 1));
    }

    /** Energy multiplier for functions: power mode, and the Nano set's saving; regeneration triples it. */
    public static float costMul(EntityPlayer p) {
        float mode = powerMode(p) == 0 ? 0.5F : powerMode(p) == 2 ? 1.5F : 1F;
        return (fullSet(p) == ArmorSuit.NANO ? mode * 0.75F : mode) * regenMul(p);
    }

    /** Energy multiplier for absorbing damage: the Quantum set and combat mode make it cheaper; regeneration triples it. */
    public static float absorbCostMul(EntityPlayer p) {
        float mul = powerMode(p) == 2 ? 0.8F : 1F;
        return (fullSet(p) == ArmorSuit.QUANTUM ? mul * 0.7F : mul) * regenMul(p);
    }

    /**
     * Regeneration is running: switched on in the chestplate, combat mode, not overheated, charge left.
     * (Not active() - that asks costMul, which asks this.)
     */
    public static boolean regenOn(EntityPlayer p) {
        ItemStack chest = piece(p, 1);
        return chest != null && ItemArmorSC.isEnabled(chest, ArmorFeature.REGENERATION) && powerMode(p) == 2
                && !overheated(p) && ItemArmorSC.chargeOf(chest) > 0;
    }

    /** x3 on everything the suit spends while regeneration runs (functions, absorbing damage, chips). */
    public static float regenMul(EntityPlayer p) {
        return regenOn(p) ? ArmorFeature.REGEN_COST_MUL : 1F;
    }

    /** Pays for a function from its own piece. */
    public static boolean pay(EntityPlayer p, ArmorFeature f, int eu) {
        ItemStack s = piece(p, f.piece);
        return s != null && ItemArmorSC.pay(s, (int) Math.ceil(eu * costMul(p)));
    }

    // ------------------------------------------------------------------ every tick, both sides

    public static void tick(EntityPlayer p) {
        if (p.worldObj.isRemote && !p.isClientWorld()) {
            return;                       // other players on this client: their own client moves them
        }
        // step assist
        boolean step = active(p, ArmorFeature.STEP_ASSIST);
        NBTTagCompound data = p.getEntityData();
        if (step) {
            p.stepHeight = 1.0F;
            data.setBoolean(STEP_FLAG, true);
        } else if (data.getBoolean(STEP_FLAG)) {
            p.stepHeight = 0.5F;
            data.removeTag(STEP_FLAG);
        }
        // walking on water and lava
        if (active(p, ArmorFeature.WATER_WALK) && !p.isSneaking() && !p.capabilities.isFlying) {
            walkOnFluid(p);
        }
        if (p.worldObj.isRemote) {
            return;
        }
        flight(p);
        if (active(p, ArmorFeature.SHIELD) && p.ticksExisted % 2 == 0) {
            shield(p);
        }
        if (active(p, ArmorFeature.FIRE_PROOF) && (p.isBurning() || p.handleLavaMovement()) && !p.isPotionActive(Potion.fireResistance)) {
            p.addPotionEffect(new PotionEffect(Potion.fireResistance.id, 45, 0, true));   // at once, not up to a second late
        }
    }

    /** After a dimension change the client has reset its abilities - tell it again it may fly. */
    public static void resendFlight(EntityPlayer p) {
        if (p.getEntityData().getBoolean(FLIGHT_FLAG)) {
            p.sendPlayerAbilities();
        }
    }

    private static void walkOnFluid(EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        int below = MathHelper.floor_double(p.boundingBox.minY - 0.05);
        Block under = p.worldObj.getBlock(x, below, z);
        Block feet = p.worldObj.getBlock(x, MathHelper.floor_double(p.boundingBox.minY + 0.1), z);
        if (feet.getMaterial().isLiquid()) {
            p.motionY = Math.max(p.motionY, 0.12);      // surfaced back up
            p.fallDistance = 0;
        } else if (under.getMaterial().isLiquid() && p.motionY < 0) {
            p.motionY = 0;
            p.onGround = true;
            p.fallDistance = 0;
        }
    }

    /** Exo chestplate: survival flight while switched on and charged, paid for while flying. */
    private static void flight(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        boolean can = active(p, ArmorFeature.FLIGHT);
        if (can && !p.capabilities.allowFlying) {
            p.capabilities.allowFlying = true;
            data.setBoolean(FLIGHT_FLAG, true);
            p.sendPlayerAbilities();
        } else if (!can && data.getBoolean(FLIGHT_FLAG)) {
            data.removeTag(FLIGHT_FLAG);
            if (!p.capabilities.isCreativeMode) {
                p.capabilities.allowFlying = false;
                p.capabilities.isFlying = false;
                setFlySpeed(p, VANILLA_FLY_SPEED);
                p.sendPlayerAbilities();
            }
        }
        if (can && !p.capabilities.isCreativeMode) {
            // the Quantum chestplate flies at half the speed, the Exo one as in creative
            float speed = suitOf(piece(p, 1)) == ArmorSuit.EXO ? VANILLA_FLY_SPEED : QUANTUM_FLY_SPEED;
            if (Math.abs(p.capabilities.getFlySpeed() - speed) > 1e-4) {
                setFlySpeed(p, speed);
                p.sendPlayerAbilities();          // the client takes its fly speed from this packet
            }
        }
        if (can && p.capabilities.isFlying) {
            p.fallDistance = 0;           // vanilla adds up the descent while flying - landing turned it into fall damage
        }
    }

    /** Creative / Exo flight speed, and the Quantum chestplate's slower one. */
    public static final float VANILLA_FLY_SPEED = 0.05F, QUANTUM_FLY_SPEED = 0.025F;

    /** PlayerCapabilities.setFlySpeed is client-only - the field is set directly on the server. */
    private static void setFlySpeed(EntityPlayer p, float speed) {
        cpw.mods.fml.relauncher.ReflectionHelper.setPrivateValue(net.minecraft.entity.player.PlayerCapabilities.class,
                p.capabilities, speed, "flySpeed", "field_75096_f");
    }

    /** Arrows and fireballs coming at the player are turned back (combat mode: twice the reach). */
    private static void shield(EntityPlayer p) {
        double r = powerMode(p) == 2 ? 6 : 3;
        AxisAlignedBB box = p.boundingBox.expand(r, r, r);
        List list = p.worldObj.getEntitiesWithinAABBExcludingEntity(p, box);
        for (Object o : list) {
            Entity e = (Entity) o;
            if (!incomingProjectile(p, e)) {
                continue;
            }
            if (!pay(p, ArmorFeature.SHIELD, ArmorFeature.SHIELD_COST)) {
                return;
            }
            addHeat(p, ArmorFeature.SHIELD.heat);
            reflect(p, e);
        }
    }

    /** An arrow / fireball / thrown thing on its way to the player, not theirs, not turned back already. */
    public static boolean incomingProjectile(EntityPlayer p, Entity e) {
        boolean projectile = e instanceof EntityArrow || e instanceof EntityFireball || e instanceof EntityThrowable;
        if (!projectile || e.getEntityData().getBoolean("scDeflected") || shooterIs(e, p)) {
            return false;
        }
        Vec3 toPlayer = Vec3.createVectorHelper(p.posX - e.posX, p.posY + 1 - e.posY, p.posZ - e.posZ);
        return toPlayer.xCoord * e.motionX + toPlayer.yCoord * e.motionY + toPlayer.zCoord * e.motionZ > 0;   // not flying away
    }

    /** Sends a projectile back the way it came (the suit's shield, the blade's deflect). */
    public static void reflect(EntityPlayer p, Entity e) {
        e.motionX = -e.motionX;
        e.motionY = -e.motionY;
        e.motionZ = -e.motionZ;
        if (e instanceof EntityFireball) {
            EntityFireball f = (EntityFireball) e;
            f.accelerationX = -f.accelerationX;
            f.accelerationY = -f.accelerationY;
            f.accelerationZ = -f.accelerationZ;
        }
        e.getEntityData().setBoolean("scDeflected", true);
        e.velocityChanged = true;
        p.worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "random.fizz", 0.5F, 1.8F);
    }

    private static boolean shooterIs(Entity e, EntityPlayer p) {
        if (e instanceof EntityArrow) {
            return ((EntityArrow) e).shootingEntity == p;
        }
        if (e instanceof EntityFireball) {
            return ((EntityFireball) e).shootingEntity == p;
        }
        return e instanceof EntityThrowable && ((EntityThrowable) e).getThrower() == p;
    }

    // ------------------------------------------------------------------ once a second, server

    /** Runs the functions that act once a second. @return the heat they made (added to the suit's heat) */
    public static int perSecond(EntityPlayer p) {
        int heat = 0;
        int mode = powerMode(p);
        if (active(p, ArmorFeature.NIGHT_VISION) && pay(p, ArmorFeature.NIGHT_VISION, ArmorFeature.NIGHT_VISION.euPerSecond)) {
            p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 260, 0, true));
            heat += ArmorFeature.NIGHT_VISION.heat;
        } else {
            PotionEffect nv = p.getActivePotionEffect(Potion.nightVision);
            if (nv != null && nv.getIsAmbient() && nv.getDuration() <= 260) {
                p.removePotionEffect(Potion.nightVision.id);      // ours - switched off or out of charge
            }
        }
        NBTTagCompound data = p.getEntityData();
        heat += data.getInteger(HEAT_KEY);
        data.removeTag(HEAT_KEY);
        if (active(p, ArmorFeature.AIR) && p.isInsideOfMaterial(Material.water) && p.getAir() < 300
                && pay(p, ArmorFeature.AIR, ArmorFeature.AIR.euPerSecond)) {
            p.setAir(300);
            heat += ArmorFeature.AIR.heat;
        }
        if (active(p, ArmorFeature.CLEANSE)) {
            for (Potion bad : new Potion[]{Potion.poison, Potion.wither, Potion.hunger, Potion.confusion, Potion.blindness}) {
                if (p.isPotionActive(bad) && !com.sc.radiation.RadiationSC.sicknessHolds(p, bad.id)
                        && pay(p, ArmorFeature.CLEANSE, ArmorFeature.CLEANSE_COST)) {
                    p.removePotionEffect(bad.id);
                }
            }
        }
        for (ArmorFeature f : new ArmorFeature[]{ArmorFeature.ORE_SCANNER, ArmorFeature.THERMAL}) {
            if (active(p, f) && pay(p, f, f.euPerSecond)) {         // the client draws them
                heat += f.heat;
            }
        }
        if (active(p, ArmorFeature.SOLAR) && !precipitationAt(p)
                && p.worldObj.canBlockSeeTheSky(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY) + 2,
                MathHelper.floor_double(p.posZ))) {
            // all worn pieces at once: an even share each, what a full piece can't take goes to the rest
            int left = solarPerSecond(p);
            while (left > 0) {
                java.util.List<ItemStack> needy = new java.util.ArrayList<ItemStack>();
                for (int t : new int[]{1, 0, 2, 3}) {               // chestplate first for the odd EU
                    ItemStack s = piece(p, t);
                    if (s != null && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s)) {
                        needy.add(s);
                    }
                }
                if (needy.isEmpty()) {
                    break;
                }
                int share = left / needy.size(), extra = left % needy.size();
                for (int i = 0; i < needy.size(); i++) {
                    left -= ItemArmorSC.charge(needy.get(i), share + (i < extra ? 1 : 0));
                }
            }
        }
        // regeneration (combat mode): heals while hurt, paid from the chestplate at the tripled rate
        if (regenOn(p) && p.getHealth() < p.getMaxHealth() && !p.isDead
                && pay(p, ArmorFeature.REGENERATION, ArmorFeature.REGENERATION.euPerSecond)) {
            p.heal(ArmorFeature.REGEN_HEAL[suitOf(piece(p, 1)).ordinal()]);
            heat += ArmorFeature.REGENERATION.heat;
        }
        if (active(p, ArmorFeature.CHARGER)) {
            heat += chargeWeapons(p);
        }
        if (p.ticksExisted % 200 == 0) {
            resendFlight(p);
        }
        if (active(p, ArmorFeature.FLIGHT) && p.capabilities.isFlying && !p.capabilities.isCreativeMode) {
            if (pay(p, ArmorFeature.FLIGHT, ArmorFeature.FLIGHT.euPerSecond)) {
                heat += ArmorFeature.FLIGHT.heat;
            }
        }
        if (active(p, ArmorFeature.FIRE_PROOF) && (p.isBurning() || p.handleLavaMovement())
                && pay(p, ArmorFeature.FIRE_PROOF, ArmorFeature.FIRE_PROOF.euPerSecond)) {
            p.addPotionEffect(new PotionEffect(Potion.fireResistance.id, 45, 0, true));
            p.extinguish();
            heat += ArmorFeature.FIRE_PROOF.heat;
        }
        ArmorSuit legs = suitOf(piece(p, 2));
        if (active(p, ArmorFeature.SPEED) && p.isSprinting() && pay(p, ArmorFeature.SPEED, ArmorFeature.SPEED.euPerSecond)) {
            int level = Math.max(0, (legs == ArmorSuit.EXO ? 2 : 1) - (mode == 0 ? 1 : 0));
            p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 30, level, true));
            heat += ArmorFeature.SPEED.heat;
        }
        ArmorSuit boots = suitOf(piece(p, 3));
        if (active(p, ArmorFeature.JUMP) && pay(p, ArmorFeature.JUMP, ArmorFeature.JUMP.euPerSecond)) {
            int level = Math.max(0, (boots == ArmorSuit.EXO ? 2 : 1) - (mode == 0 ? 1 : 0));
            p.addPotionEffect(new PotionEffect(Potion.jump.id, 30, level, true));
            heat += ArmorFeature.JUMP.heat;
        }
        if (active(p, ArmorFeature.WATER_WALK) && isOnFluid(p)) {
            if (pay(p, ArmorFeature.WATER_WALK, ArmorFeature.WATER_WALK.euPerSecond)) {
                heat += ArmorFeature.WATER_WALK.heat;
            }
        }
        setBonuses(p);
        return heat;
    }

    /** Rain or snow falls where the player stands (a desert, savanna or the Nether stays dry in any weather). */
    private static boolean precipitationAt(EntityPlayer p) {
        if (!p.worldObj.isRaining()) {
            return false;
        }
        net.minecraft.world.biome.BiomeGenBase biome = p.worldObj.getBiomeGenForCoords(MathHelper.floor_double(p.posX),
                MathHelper.floor_double(p.posZ));
        return biome != null && (biome.canSpawnLightningBolt() || biome.getEnableSnow());
    }

    private static boolean isOnFluid(EntityPlayer p) {
        Block under = p.worldObj.getBlock(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.boundingBox.minY - 0.05),
                MathHelper.floor_double(p.posZ));
        return under.getMaterial().isLiquid();
    }

    /**
     * EU the solar film gives the suit this second: SOLAR_PER_SECOND by day, half at night, and an
     * Energy (Power) chip in the chestplate adds a quarter per chip tier - while the chips run (not overheated).
     */
    public static int solarPerSecond(EntityPlayer p) {
        int eu = p.worldObj.isDaytime() ? ArmorFeature.SOLAR_PER_SECOND : ArmorFeature.SOLAR_PER_SECOND / 2;
        ItemStack chest = piece(p, 1);
        if (chest != null && !overheated(p)) {
            NBTTagCompound chips = ItemArmorSC.chipsTag(chest);
            String power = com.sc.util.ChipType.POWER.name();
            if (chips.hasKey(power)) {
                int tier = Math.max(1, Math.min(3, chips.getInteger(power)));
                eu = eu * (4 + tier) / 4;                         // +25% per tier: x1.25 / x1.5 / x1.75
            }
        }
        return eu;
    }

    /** Chestplate energy into the mod's weapons in the inventory. @return heat */
    private static int chargeWeapons(EntityPlayer p) {
        ItemStack chest = piece(p, 1);
        boolean any = false;
        for (ItemStack s : p.inventory.mainInventory) {
            if (s == null || ItemArmorSC.chargeOf(chest) <= 0) {
                continue;
            }
            int offer = Math.min(ItemArmorSC.chargeOf(chest), 10000);
            int took;
            if (s.getItem() instanceof ItemWeaponSC) {
                took = ItemWeaponSC.charge(s, ((ItemWeaponSC) s.getItem()).getType(), offer);
            } else if (s.getItem() instanceof ItemBladeSC && !(p.isUsingItem() && s == p.getCurrentEquippedItem())) {
                took = ItemBladeSC.charge(s, offer);                  // the energy blades too (not mid-block: it would drop the block)
            } else if (s.getItem() instanceof ItemDrillSC && !DrillLogicSC.digging(p, s)) {
                took = ItemDrillSC.charge(s, offer);                  // and the drills (not mid-dig: it would restart the block)
            } else {
                continue;
            }
            if (took > 0) {
                ItemArmorSC.discharge(chest, took);
                any = true;
            }
        }
        return any ? ArmorFeature.CHARGER.heat : 0;
    }

    private static void setBonuses(EntityPlayer p) {
        ArmorSuit set = overheated(p) ? null : fullSet(p);      // an overheated suit gives no set bonus
        // Exo: never hungry - the chestplate's energy stands in for food
        if (set == ArmorSuit.EXO) {
            ItemStack chest = piece(p, 1);
            while (p.getFoodStats().getFoodLevel() < 20 && ItemArmorSC.pay(chest, ArmorFeature.FOOD_POINT_COST)) {
                p.getFoodStats().addStats(1, 0.6F);
            }
            if (p.isPotionActive(Potion.hunger)) {
                p.removePotionEffect(Potion.hunger.id);
            }
        }
        // Quantum: knockback halved
        IAttributeInstance kb = p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        AttributeModifier mod = kb.getModifier(KNOCKBACK_ID);
        if (set == ArmorSuit.QUANTUM && mod == null) {
            kb.applyModifier(new AttributeModifier(KNOCKBACK_ID, "SC Quantum set", 0.5, 0));
        } else if (set != ArmorSuit.QUANTUM && mod != null) {
            kb.removeModifier(mod);
        }
    }

    // ------------------------------------------------------------------ events

    /** Boots soften a fall: Nano half, Quantum three quarters, Exo all of it - paid per point. @return the new fall distance */
    public static float fall(EntityPlayer p, float distance) {
        if (!active(p, ArmorFeature.FALL_DAMPING) || distance <= 3) {
            return distance;
        }
        ArmorSuit boots = suitOf(piece(p, 3));
        float share = boots == ArmorSuit.EXO ? 1F : boots == ArmorSuit.QUANTUM ? 0.75F : 0.5F;
        PotionEffect jump = p.getActivePotionEffect(Potion.jump);
        float points = distance - 3 - (jump == null ? 0 : jump.getAmplifier() + 1);   // vanilla already takes that off
        if (points <= 0) {
            return distance;
        }
        int want = (int) Math.ceil(points * share);
        int can = (int) (ItemArmorSC.chargeOf(piece(p, 3)) / Math.max(1F, ArmorFeature.FALL_COST_PER_POINT * costMul(p)));
        int absorbed = Math.min(want, can);
        pay(p, ArmorFeature.FALL_DAMPING, absorbed * ArmorFeature.FALL_COST_PER_POINT);
        return distance - absorbed;
    }

    /**
     * Exo chestplate, on its key only: every hostile mob within 7 blocks dies on the spot, and every
     * worn piece gives up 95% of its charge. Needs all four pieces worn and each at least 95% full;
     * once a second at most. Bosses (dragon, wither) only take a heavy normal blow, and a mob that
     * refuses the blow (immune to it) stays alive.
     */
    public static void annihilate(EntityPlayerMP p) {
        if (!active(p, ArmorFeature.ANNIHILATION)) {
            return;
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - data.getLong("scAnnihilated") < 20) {
            return;
        }
        if (fullSet(p) != ArmorSuit.EXO) {
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.armor.annihilate.low"));
            return;
        }
        for (int t = 0; t < 4; t++) {                               // all four Exo pieces worn, each 95%+ full
            ItemStack s = piece(p, t);
            if (s == null || ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s) * ArmorFeature.ANNIHILATION_MIN_CHARGE) {
                p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.armor.annihilate.low"));
                return;
            }
        }
        data.setLong("scAnnihilated", now);
        long spent = 0;
        for (int t = 0; t < 4; t++) {
            ItemStack s = piece(p, t);
            if (s != null) {
                spent += ItemArmorSC.discharge(s, (int) (ItemArmorSC.chargeOf(s) * ArmorFeature.ANNIHILATION_DRAIN));
            }
        }
        double r = ArmorFeature.ANNIHILATION_RADIUS;
        int killed = 0;
        List list = p.worldObj.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, p.boundingBox.expand(r, r, r));
        for (Object o : list) {
            net.minecraft.entity.EntityLivingBase e = (net.minecraft.entity.EntityLivingBase) o;
            if (!(e instanceof net.minecraft.entity.monster.IMob) || !e.isEntityAlive() || p.getDistanceSqToEntity(e) > r * r) {
                continue;
            }
            e.hurtResistantTime = 0;
            if (e instanceof net.minecraft.entity.boss.IBossDisplayData) {
                e.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(p), ArmorFeature.ANNIHILATION_BOSS_DAMAGE);
            } else {
                net.minecraft.util.DamageSource src = net.minecraft.util.DamageSource.causePlayerDamage(p)
                        .setDamageBypassesArmor().setDamageIsAbsolute();
                e.attackEntityFrom(src, e.getMaxHealth() * 10 + 1000);   // the player gets the kill: drops and experience; a refused blow stays refused
            }
            if (!e.isEntityAlive()) {
                killed++;
            }
        }
        addHeat(p, ArmorFeature.ANNIHILATION.heat);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.explode", 1.2F, 1.6F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("hugeexplosion", p.posX, p.posY + 1, p.posZ, 1, 0, 0, 0, 0);
        }
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.armor.annihilate", killed, spent));
    }

    // ------------------------------------------------------------------ full Exo set: nothing gets through

    /**
     * An attack on a player (Forge LivingAttackEvent, before anything happens). Full Exo set only:
     * - explosion proofing (chestplate): an explosion does nothing, a flat EU price;
     * - the energy shield switched on: no damage gets through at all (only the void still kills),
     *   each point stopped paid from the chestplate. What a single blow already paid for is not paid
     *   again for the next ten ticks (lava / fire hit every tick, vanilla's own invulnerability window).
     * @return true to cancel the attack
     */
    public static boolean exoStops(EntityPlayer p, net.minecraft.util.DamageSource src, float amount) {
        if (p.worldObj.isRemote || src == net.minecraft.util.DamageSource.outOfWorld || amount <= 0
                || fullSet(p) != ArmorSuit.EXO || p.capabilities.disableDamage || p.isEntityInvulnerable()) {
            return false;
        }
        if (src == com.sc.radiation.RadiationSC.DAMAGE || src == net.minecraft.util.DamageSource.wither) {
            return false;                                  // radiation is the radiation shield's job, not the energy shield's
        }
        if (src.isExplosion() && active(p, ArmorFeature.EXPLOSION_PROOF)
                && pay(p, ArmorFeature.EXPLOSION_PROOF, ArmorFeature.EXPLOSION_PROOF_COST)) {
            addHeat(p, ArmorFeature.EXPLOSION_PROOF.heat);
            return true;
        }
        if (!active(p, ArmorFeature.SHIELD)) {
            return false;
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        float covered = now - data.getLong("scExoShieldAt") < 10 ? data.getFloat("scExoShieldAmt") : 0;
        if (amount <= covered) {
            return true;                                   // this blow is already paid for
        }
        int eu = (int) Math.ceil((amount - covered) * ArmorFeature.EXO_SHIELD_EU_PER_POINT * absorbCostMul(p));
        if (!ItemArmorSC.pay(piece(p, 1), eu)) {
            return false;                                  // out of charge: the armour takes it as usual
        }
        data.setLong("scExoShieldAt", now);
        data.setFloat("scExoShieldAmt", amount);
        addHeat(p, ArmorFeature.SHIELD.heat);
        return true;
    }

    /** An explosion about to push / hurt entities: a player it can't touch (explosion proofing) is taken off its list. */
    public static boolean explosionProof(EntityPlayer p) {
        return !p.worldObj.isRemote && fullSet(p) == ArmorSuit.EXO && active(p, ArmorFeature.EXPLOSION_PROOF)
                && pay(p, ArmorFeature.EXPLOSION_PROOF, ArmorFeature.EXPLOSION_PROOF_COST)
                && addHeatAnd(p, ArmorFeature.EXPLOSION_PROOF.heat);
    }

    private static boolean addHeatAnd(EntityPlayer p, int heat) {
        addHeat(p, heat);
        return true;
    }

    /** Ticks between two dashes. */
    public static final int DASH_COOLDOWN = 10;

    /** Exo leggings: a burst forward. */
    public static void dash(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - data.getLong("scDashAt") < DASH_COOLDOWN && data.hasKey("scDashAt")) {
            return;                                        // two keys on one press, or a client sending it every tick
        }
        if (!active(p, ArmorFeature.DASH) || !pay(p, ArmorFeature.DASH, ArmorFeature.DASH_COST)) {
            return;
        }
        data.setLong("scDashAt", now);
        Vec3 look = p.getLookVec();
        double len = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        if (len < 0.01) {
            return;
        }
        p.motionX = look.xCoord / len * 1.8;
        p.motionZ = look.zCoord / len * 1.8;
        p.motionY = Math.max(p.motionY, 0.35);
        p.fallDistance = 0;
        p.velocityChanged = true;
        addHeat(p, ArmorFeature.DASH.heat);
        p.worldObj.playSoundAtEntity(p, "mob.ghast.fireball", 0.4F, 1.6F);
    }
}
