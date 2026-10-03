package com.sc.item;

import java.util.List;
import java.util.UUID;

import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.ArmorSuit;
import com.sc.util.ChipType;

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
 * An overheated suit (chips shut down, §16) switches its functions and the set bonuses off too;
 * so does emergency mode (Quantum / Exo without helium) - chips included, the gases' passives
 * (heavy water against radiation, argon putting fires out) still work.
 */
public final class ArmorLogicSC {

    private static final UUID KNOCKBACK_ID = UUID.fromString("5c1d7c64-8b0e-4c4a-9d57-3a0f1e3c5a11");
    private static final String STEP_FLAG = "scArmorStep", FLIGHT_FLAG = "scArmorFlight", HEAT_KEY = "scArmorHeat";
    /** Set while the night vision is ours (the helmet or the Sensor chip gave it): only then is it taken off. */
    private static final String NIGHT_VISION_FLAG = "SCNightVision";

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
        return fullSetOf(ArmorGasSC.wornSet(p));
    }

    /** fullSet for a set of pieces indexed HELMET..BOOTS (no player needed). */
    public static ArmorSuit fullSetOf(ItemStack[] worn) {
        ArmorSuit suit = null;
        for (int t = 0; t < 4; t++) {
            ItemStack s = worn == null || t >= worn.length ? null : worn[t];
            if (s == null || !(s.getItem() instanceof ItemArmorSC) || ItemArmorSC.chargeOf(s) <= 0
                    || (suit != null && suitOf(s) != suit)) {
                return null;
            }
            suit = suitOf(s);
        }
        return suit;
    }

    /**
     * The full set whose set bonuses work (Quantum knockback, cheaper absorbing; Exo food by EU):
     * none in emergency mode (Quantum / Exo without helium in the loop). Overheating is checked where
     * the bonus is given (setBonuses).
     */
    public static ArmorSuit bonusSetOf(ItemStack[] worn) {
        ArmorSuit set = fullSetOf(worn);
        return set == null || emergency(worn) ? null : set;
    }

    public static ArmorSuit bonusSet(EntityPlayer p) {
        return bonusSetOf(ArmorGasSC.wornSet(p));
    }

    /** The suit's chips (and functions) are shut down by heat. */
    static boolean overheated(EntityPlayer p) {
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
        if (!gasAllows(ArmorGasSC.wornSet(p), f)) {
            return false;                 // Quantum / Exo: no gas of its own, or emergency mode (no helium)
        }
        int need = f.euPerSecond > 0 ? (int) Math.ceil(f.euPerSecond * costMul(p)) : f == ArmorFeature.SOLAR || f.gasPowered() ? 0 : 1;
        if (f == ArmorFeature.FLIGHT && boostedFlight(p)) {
            need = 0;                     // the engine boost flies on hydrogen, not EU
        }
        return ItemArmorSC.chargeOf(s) >= need;
    }

    // ------------------------------------------------------------------ the strict rules: Quantum / Exo run on gases

    /** Quantum and Exo: every function on its own gas (ArmorFeature.gas()), and emergency mode without helium. Nano is soft. */
    public static boolean strict(ArmorSuit suit) {
        return suit != null && suit.atLeast(ArmorSuit.QUANTUM);         // Quantum, Exo, Singular
    }

    /** A piece of this suit is in emergency mode with the set `worn`: Quantum / Exo without helium in the loop (no chestplate too). */
    public static boolean pieceEmergency(ItemStack[] worn, ArmorSuit suit) {
        return strict(suit) && !heliumReady(worn);
    }

    /** Emergency mode: some worn Quantum / Exo piece and no helium in the loop - every function off but the HUD, iron-grade plating. */
    public static boolean emergency(ItemStack[] worn) {
        if (heliumReady(worn)) {
            return false;
        }
        for (int t = 0; t < 4; t++) {
            if (worn[t] != null && strict(suitOf(worn[t]))) {
                return true;
            }
        }
        return false;
    }

    /**
     * The gas the function is missing in the set `worn` (its piece: Quantum / Exo - any of its own
     * gas, helium: heliumReady; breathing needs oxygen in every suit), or null when it has it / needs none.
     */
    public static Gas missingGas(ItemStack[] worn, ArmorFeature f) {
        ItemStack s = worn[f.piece];
        Gas g = f.gas();
        if (s == null || g == null || !(strict(suitOf(s)) || f == ArmorFeature.AIR)) {
            return null;
        }
        boolean has = g == Gas.HELIUM ? heliumReady(worn) : ArmorGasSC.amountOf(worn, g) >= f.gasMin();   // the dash: a whole dash's worth
        return has ? null : g;
    }

    /** The gases let the function work: the HUD always; Quantum / Exo not in emergency mode and with its gas. */
    public static boolean gasAllows(ItemStack[] worn, ArmorFeature f) {
        if (f == ArmorFeature.HUD) {
            return true;
        }
        ItemStack s = worn[f.piece];
        if (s == null) {
            return false;
        }
        return !pieceEmergency(worn, suitOf(s)) && missingGas(worn, f) == null;
    }

    /** No player needed: switched on and allowed by the gases (the self-test; active() adds overheating and the EU). */
    public static boolean worksIn(ItemStack[] worn, ArmorFeature f) {
        return worn[f.piece] != null && ItemArmorSC.isEnabled(worn[f.piece], f) && gasAllows(worn, f);
    }

    /**
     * A working function spends its gas (Quantum / Exo only - the Nano suit runs its functions on
     * EU; helium goes through the loop's cooling, heavy water by the radiation): `mb` of it, the
     * part under a whole mB kept until it adds up.
     */
    public static void spendGas(EntityPlayer p, ArmorFeature f, float mb) {
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        Gas g = f.gas();
        if (g == null || g == Gas.HELIUM || mb <= 0 || worn[f.piece] == null || !strict(suitOf(worn[f.piece]))) {
            return;
        }
        ArmorGasSC.drainFraction(worn, g, mb * ArmorGasSC.gasUseMul(worn[f.piece]));   // Singular: 20% less (K10)
    }

    /** Spends a function's own rate (ArmorFeature.gasUse) for one second / one use; per-minute rates a 60th. */
    private static void spendGas(EntityPlayer p, ArmorFeature f) {
        spendGas(p, f, f.gasUseKind() == ArmorFeature.USE_MINUTE ? f.gasUse() / 60F : f.gasUse());
    }

    private static final String EMERGENCY_FLAG = "scEmergency";

    /** Once a second: entering emergency mode says so in chat, once (again only after leaving it). */
    private static void emergencyWarning(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (emergency(ArmorGasSC.wornSet(p))) {
            if (!data.getBoolean(EMERGENCY_FLAG)) {
                data.setBoolean(EMERGENCY_FLAG, true);
                warn(p, "sc.gas.warn.emergency", 600);       // and never more often than every 30 s
            }
        } else {
            data.removeTag(EMERGENCY_FLAG);
        }
    }

    /** A jump (Forge's LivingJumpEvent, server): the jump boots on hydrogen spend H2_JUMP. */
    public static void jumped(EntityPlayer p) {
        if (!p.worldObj.isRemote && active(p, ArmorFeature.JUMP)) {
            spendGas(p, ArmorFeature.JUMP);
        }
    }

    // ------------------------------------------------------------------ life support (gases, docs/plan-armor-gases.md)

    /** The engine boost is switched on (Quantum+ chestplate) - what it does still needs hydrogen. */
    public static boolean boosterOn(EntityPlayer p) {
        return active(p, ArmorFeature.BOOSTER);
    }

    /** Flight on hydrogen: the boost on and enough hydrogen for the next second. */
    public static boolean boostedFlight(EntityPlayer p) {
        return boosterOn(p) && ArmorGasSC.suitAmount(p, Gas.HYDROGEN) >= ArmorGasSC.H2_FLIGHT_PER_SECOND;
    }

    /**
     * Helium in the worn loop: what the Exo shield and the annihilation pulse need to switch on at
     * all - at least HELIUM_READY_PCT of the loop's volume (and never under 1 mB), not a last drop.
     */
    public static boolean heliumReady(ItemStack[] worn) {
        int cap = ArmorGasSC.capacityOf(worn, Gas.HELIUM);
        return cap > 0 && ArmorGasSC.amountOf(worn, Gas.HELIUM) >= heliumReadyMin(cap);
    }

    /** heliumReady: the share of the helium loop it needs, percent. */
    public static final int HELIUM_READY_PCT = 1;

    /** The least helium heliumReady accepts for a loop of `cap` mB. */
    public static int heliumReadyMin(int cap) {
        return Math.max(1, cap * HELIUM_READY_PCT / 100);
    }

    /**
     * Breathing on the helmet's oxygen this second (under water, inside a block, in space): the
     * function on and OXYGEN_PER_SECOND mB taken. No oxygen - no breathing. @return whether it breathed
     */
    public static boolean breathe(ItemStack[] worn) {
        ItemStack helmet = worn[ArmorGasSC.HELMET];
        return helmet != null && ItemArmorSC.isEnabled(helmet, ArmorFeature.AIR)
                && ArmorGasSC.drainExactUse(worn, Gas.OXYGEN, ArmorGasSC.OXYGEN_PER_SECOND);
    }

    /** The helmet's breathing can work right now (switched on, not overheated, charge, oxygen left). */
    public static boolean canBreathe(EntityPlayer p) {
        return active(p, ArmorFeature.AIR) && ArmorGasSC.suitAmount(p, Gas.OXYGEN) >= ArmorGasSC.OXYGEN_PER_SECOND;
    }

    /**
     * Galacticraft's airless worlds (compat.GalacticraftSC): the suit's oxygen instead of their
     * tanks, OXYGEN_PER_SECOND a second (their check comes every tick). @return true to cancel the suffocation
     */
    public static boolean breatheInSpace(EntityPlayer p) {
        if (p.worldObj.isRemote || !canBreathe(p)) {
            return false;
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - data.getLong(O2_SPACE_AT) >= 20 || now < data.getLong(O2_SPACE_AT)) {
            if (!breathe(ArmorGasSC.wornSet(p))) {
                return false;
            }
            data.setLong(O2_SPACE_AT, now);
            data.setBoolean(O2_USED, true);
        }
        return true;
    }

    /** Suffocating inside a block: the helmet's oxygen stops it (paid once a second in perSecond). */
    public static boolean stopsSuffocation(EntityPlayer p, net.minecraft.util.DamageSource src) {
        return src == net.minecraft.util.DamageSource.inWall && !p.worldObj.isRemote && canBreathe(p);
    }

    /** The ore scanner's reach: an Exo helmet with krypton sees half as far again. Used by the client's scanner. */
    public static int oreScanRadius(EntityPlayer p, int base) {
        ItemStack helmet = piece(p, 0);
        return ArmorSuit.exoClass(suitOf(helmet)) && ArmorGasSC.amount(helmet, Gas.KRYPTON) > 0
                ? Math.round(base * ArmorGasSC.KRYPTON_SCAN_MUL) : base;
    }

    private static final String O2_SPACE_AT = "scO2SpaceAt", O2_USED = "scO2Used", FUSION_ON = "scFusionOn",
            AIR_JUMPED = "scAirJumped", AIR_JUMP_AT = "scAirJumpAt", ARGON_AT = "scArgonAt", WARN_PREFIX = "scGasWarn_";
    /** The searchlight's light: where it stands (player data), and when it was last pointed at. */
    public static final String LIGHT_X = "scLightX", LIGHT_Y = "scLightY", LIGHT_Z = "scLightZ", LIGHT_DIM = "scLightDim",
            LIGHT_AT = "scLightAt", LIGHT_ON = "scLightOn", LIGHT_MOVED = "scLightMoved";
    /** ArmorNetSC action byte the client sends for the Quantum air jump (handled by airJump). */
    public static final byte AIR_JUMP_ACTION = 20;

    /** The fusion cell ran this second (the helium loop burns twice as fast meanwhile). */
    public static boolean fusionRunning(EntityPlayer p) {
        return p.getEntityData().getBoolean(FUSION_ON);
    }

    /** A warning in chat with a short sound, at most once in `cooldown` ticks per key. */
    public static void warn(EntityPlayer p, String key, int cooldown) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        long at = data.getLong(WARN_PREFIX + key);
        if (data.hasKey(WARN_PREFIX + key) && now - at < cooldown && now >= at) {
            return;
        }
        data.setLong(WARN_PREFIX + key, now);
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(key));
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null) {
            ((EntityPlayerMP) p).playerNetServerHandler.sendPacket(
                    new net.minecraft.network.play.server.S29PacketSoundEffect("note.pling", p.posX, p.posY, p.posZ, 0.7F, 0.6F));
        }
    }

    /** Lets the warning come again at once next time (the cause went away). */
    public static void rearm(EntityPlayer p, String key) {
        p.getEntityData().removeTag(WARN_PREFIX + key);
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
        return (bonusSet(p) == ArmorSuit.QUANTUM ? mul * 0.7F : mul) * regenMul(p);     // no set bonus in emergency mode
    }

    /**
     * Regeneration is running: switched on in the chestplate, combat mode, not overheated, charge left.
     * (Not active() - that asks costMul, which asks this.)
     */
    public static boolean regenOn(EntityPlayer p) {
        ItemStack chest = piece(p, 1);
        return chest != null && ItemArmorSC.isEnabled(chest, ArmorFeature.REGENERATION) && powerMode(p) == 2
                && !overheated(p) && ItemArmorSC.chargeOf(chest) > 0 && gasAllows(ArmorGasSC.wornSet(p), ArmorFeature.REGENERATION);
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
        if (p.onGround || p.capabilities.isFlying || p.isInWater() || p.isOnLadder()) {
            data.removeTag(AIR_JUMPED);                       // one air jump per time off the ground
        }
        if (p.worldObj.isRemote) {
            airJumpKey(p);
            softDescent(p);
            return;
        }
        flight(p);
        softDescent(p);
        argon(p);
        searchlight(p);
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

    // ------------------------------------------------------------------ hydrogen: the Quantum air jump

    private static java.lang.reflect.Field jumpingField;

    /** EntityLivingBase.isJumping (the jump key held, on the client). */
    private static boolean jumping(EntityPlayer p) {
        try {
            if (jumpingField == null) {
                jumpingField = cpw.mods.fml.relauncher.ReflectionHelper.findField(net.minecraft.entity.EntityLivingBase.class,
                        "isJumping", "field_70703_bu");
            }
            return jumpingField.getBoolean(p);
        } catch (Exception e) {
            return false;
        }
    }

    /**
     * Client, the player's own: the jump key pressed again in mid-air with the engine boost on, the
     * jump boots on and hydrogen enough - the server is asked for the air jump (airJump); it pays and
     * pushes the player up. Once per time off the ground.
     */
    private static void airJumpKey(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        boolean now = jumping(p);
        boolean was = data.getBoolean("scJumpHeld");
        data.setBoolean("scJumpHeld", now);
        if (!now || was || p.onGround || p.capabilities.isFlying || p.isInWater() || p.isOnLadder()
                || data.getBoolean(AIR_JUMPED) || p.fallDistance <= 0.1F && p.motionY > 0.1) {
            return;                                   // still rising from the ground jump: not yet
        }
        if (!airJumpReady(p)) {
            return;
        }
        data.setBoolean(AIR_JUMPED, true);
        com.sc.handler.ArmorNetSC.CHANNEL.sendToServer(new com.sc.handler.ArmorNetSC.Message(AIR_JUMP_ACTION, 0));
    }

    private static boolean airJumpReady(EntityPlayer p) {
        return boosterOn(p) && active(p, ArmorFeature.JUMP) && ArmorGasSC.suitAmount(p, Gas.HYDROGEN) >= ArmorGasSC.H2_AIR_JUMP;
    }

    /** Server, from the client's AIR_JUMP_ACTION: a second jump in mid-air for H2_AIR_JUMP mB of hydrogen. */
    public static void airJump(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (p.onGround || p.capabilities.isFlying || data.getBoolean(AIR_JUMPED)
                || (data.hasKey(AIR_JUMP_AT) && now - data.getLong(AIR_JUMP_AT) < 10 && now >= data.getLong(AIR_JUMP_AT))) {
            return;
        }
        if (!airJumpReady(p) || !ArmorGasSC.drainExactUse(ArmorGasSC.wornSet(p), Gas.HYDROGEN, ArmorGasSC.H2_AIR_JUMP)) {
            return;
        }
        data.setBoolean(AIR_JUMPED, true);
        data.setLong(AIR_JUMP_AT, now);
        PotionEffect jump = p.getActivePotionEffect(Potion.jump);
        p.motionY = 0.42 + (jump == null ? 0 : (jump.getAmplifier() + 1) * 0.1);
        p.fallDistance = 0;
        p.velocityChanged = true;
        p.worldObj.playSoundAtEntity(p, "fire.ignite", 0.5F, 1.8F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("cloud", p.posX, p.posY, p.posZ, 6, 0.2, 0.05, 0.2, 0.02);
        }
    }

    // ------------------------------------------------------------------ argon: putting fires out

    /**
     * Every tick, server. Burning (not in lava or a fire, not fire proof): the chestplate's argon
     * puts the wearer out at once for ARGON_EXTINGUISH mB.
     */
    private static void argon(EntityPlayer p) {
        if (!p.isBurning() || p.handleLavaMovement() || active(p, ArmorFeature.FIRE_PROOF) || fireHarmless(p)
                || p.worldObj.func_147470_e(p.boundingBox.contract(0.001, 0.001, 0.001))) {
            return;                                    // standing in fire / lava: it would light again at once
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (data.hasKey(ARGON_AT) && now - data.getLong(ARGON_AT) < 20 && now >= data.getLong(ARGON_AT)) {
            return;
        }
        if (ArmorGasSC.drainExactUse(ArmorGasSC.wornSet(p), Gas.ARGON, ArmorGasSC.ARGON_EXTINGUISH)) {
            data.setLong(ARGON_AT, now);
            p.extinguish();
            p.worldObj.playSoundAtEntity(p, "random.fizz", 0.7F, 1.4F);
        }
    }

    /** Fire and lava can't hurt the player anyway (creative, a fire resistance potion): no argon spent on them. */
    private static boolean fireHarmless(EntityPlayer p) {
        return p.capabilities.disableDamage || p.isPotionActive(Potion.fireResistance);
    }

    /** Lava hurting a Nano / Quantum wearer (no fire proofing on) with argon in the chestplate: half the damage. */
    public static float argonLava(EntityPlayer p, net.minecraft.util.DamageSource src, float amount) {
        if (src != net.minecraft.util.DamageSource.lava || p.worldObj.isRemote || active(p, ArmorFeature.FIRE_PROOF)
                || ArmorGasSC.suitAmount(p, Gas.ARGON) <= 0) {
            return amount;
        }
        return amount / 2F;
    }

    /** Exo fire proofing with argon: the fire round the wearer goes out. @return argon spent */
    private static int argonFireAround(EntityPlayer p, ItemStack[] worn) {
        int r = ArmorGasSC.ARGON_FIRE_RADIUS, spent = 0;
        int px = MathHelper.floor_double(p.posX), py = MathHelper.floor_double(p.posY), pz = MathHelper.floor_double(p.posZ);
        for (int dx = -r; dx <= r; dx++) {
            for (int dy = -1; dy <= r; dy++) {
                for (int dz = -r; dz <= r; dz++) {
                    if (p.worldObj.getBlock(px + dx, py + dy, pz + dz) != net.minecraft.init.Blocks.fire) {
                        continue;
                    }
                    if (!ArmorGasSC.drainExactUse(worn, Gas.ARGON, ArmorGasSC.ARGON_PER_FIRE)) {
                        return spent;
                    }
                    spent += ArmorGasSC.ARGON_PER_FIRE;
                    p.worldObj.setBlockToAir(px + dx, py + dy, pz + dz);
                    p.worldObj.playSoundEffect(px + dx + 0.5, py + dy + 0.5, pz + dz + 0.5, "random.fizz", 0.5F, 2.0F);
                }
            }
        }
        return spent;
    }

    // ------------------------------------------------------------------ krypton: the searchlight

    /**
     * Server, every tick: the helmet's searchlight on with krypton - an invisible light block
     * (BlockLightSC) SEARCHLIGHT_RANGE blocks ahead along the look, or just before the block the
     * look hits; aimed every 4 ticks, but moved only when the aim shifted by LIGHT_MOVE_DIST blocks
     * or LIGHT_MOVE_TICKS have passed since it last moved (fewer light updates). Only ever placed in
     * plain air (never in another mod's air, like Galacticraft's breathable one). The light puts
     * itself out once nobody points at it.
     */
    private static void searchlight(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        boolean on = com.sc.init.ModBlocks.lightSC != null && active(p, ArmorFeature.SEARCHLIGHT)
                && ArmorGasSC.suitAmount(p, Gas.KRYPTON) > 0 && !p.isPlayerSleeping();
        if (!on) {
            clearLight(p);
            return;
        }
        if (p.ticksExisted % 4 != 0) {
            return;
        }
        double ey = p.posY + 1.62;                     // server: posY is the feet
        Vec3 eye = Vec3.createVectorHelper(p.posX, ey, p.posZ);
        Vec3 look = p.getLookVec();
        int range = ArmorGasSC.SEARCHLIGHT_RANGE;
        Vec3 end = Vec3.createVectorHelper(p.posX + look.xCoord * range, ey + look.yCoord * range, p.posZ + look.zCoord * range);
        net.minecraft.util.MovingObjectPosition hit = p.worldObj.rayTraceBlocks(eye, end, true);
        int x, y, z;
        if (hit != null && hit.typeOfHit == net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            net.minecraftforge.common.util.ForgeDirection d = net.minecraftforge.common.util.ForgeDirection.getOrientation(hit.sideHit);
            x = hit.blockX + d.offsetX;
            y = hit.blockY + d.offsetY;
            z = hit.blockZ + d.offsetZ;
        } else {
            x = MathHelper.floor_double(end.xCoord);
            y = MathHelper.floor_double(end.yCoord);
            z = MathHelper.floor_double(end.zCoord);
        }
        long now = p.worldObj.getTotalWorldTime();
        int lx = data.getInteger(LIGHT_X), ly = data.getInteger(LIGHT_Y), lz = data.getInteger(LIGHT_Z);
        // the light still standing where it was put (it can be gone: replaced, a block placed there)
        boolean standing = data.getBoolean(LIGHT_ON) && data.getInteger(LIGHT_DIM) == p.dimension
                && p.worldObj.blockExists(lx, ly, lz) && p.worldObj.getBlock(lx, ly, lz) == com.sc.init.ModBlocks.lightSC;
        boolean keep = standing && !lightShouldMove(lx, ly, lz, x, y, z, now - data.getLong(LIGHT_MOVED));
        if (!keep) {
            clearLight(p);
            if (y < 1 || y > 254 || !p.worldObj.blockExists(x, y, z) || p.worldObj.getBlock(x, y, z) != net.minecraft.init.Blocks.air) {
                return;
            }
            p.worldObj.setBlock(x, y, z, com.sc.init.ModBlocks.lightSC, 0, 2);
            data.setBoolean(LIGHT_ON, true);
            data.setInteger(LIGHT_X, x);
            data.setInteger(LIGHT_Y, y);
            data.setInteger(LIGHT_Z, z);
            data.setInteger(LIGHT_DIM, p.dimension);
            data.setLong(LIGHT_MOVED, now);
        }
        data.setLong(LIGHT_AT, now);
    }

    /** The searchlight's light moves when the aim shifted this many blocks (any axis)... */
    public static final int LIGHT_MOVE_DIST = 2;
    /** ...or after this many ticks in one place when it shifted less. */
    public static final int LIGHT_MOVE_TICKS = 8;

    /** Pure: whether the light at (lx, ly, lz), put there `age` ticks ago, should go to the aim (x, y, z). */
    public static boolean lightShouldMove(int lx, int ly, int lz, int x, int y, int z, long age) {
        if (lx == x && ly == y && lz == z) {
            return false;
        }
        int d = Math.max(Math.abs(lx - x), Math.max(Math.abs(ly - y), Math.abs(lz - z)));
        return d >= LIGHT_MOVE_DIST || age >= LIGHT_MOVE_TICKS || age < 0;
    }

    /**
     * The player's searchlight light goes out (if it's still there) - in the world it was put in,
     * also when the player has just left that world (a portal: the stored dimension). Server only;
     * also called on logout and death (CommonEventHandler / ShieldEventHandler).
     */
    public static void clearLight(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (!data.getBoolean(LIGHT_ON)) {
            return;
        }
        data.removeTag(LIGHT_ON);
        if (p.worldObj == null || p.worldObj.isRemote || com.sc.init.ModBlocks.lightSC == null) {
            return;
        }
        int x = data.getInteger(LIGHT_X), y = data.getInteger(LIGHT_Y), z = data.getInteger(LIGHT_Z), dim = data.getInteger(LIGHT_DIM);
        net.minecraft.world.World w = dim == p.dimension ? p.worldObj : net.minecraftforge.common.DimensionManager.getWorld(dim);
        if (w != null && w.blockExists(x, y, z) && w.getBlock(x, y, z) == com.sc.init.ModBlocks.lightSC) {
            w.setBlock(x, y, z, net.minecraft.init.Blocks.air, 0, 2);
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
            if (!p.capabilities.isCreativeMode && p.capabilities.isFlying && !p.onGround
                    && flightCutByGas(ArmorGasSC.wornSet(p))) {
                // cut in mid-air by the gases: a few seconds of slowed fall (the client slows it, see softDescent)
                data.setInteger(DESCENT, SOFT_DESCENT_TICKS);
                warn(p, "sc.gas.warn.flightcut", 100);
            }
            if (!p.capabilities.isCreativeMode) {
                p.capabilities.allowFlying = false;
                p.capabilities.isFlying = false;
            }
            // in creative too: the Quantum's half speed is saved with the player and stayed for good
            setFlySpeed(p, VANILLA_FLY_SPEED);
            p.sendPlayerAbilities();
        }
        if (can) {
            // the Quantum chestplate flies at half the speed, the Exo one (and creative) as in creative;
            // the engine boost on hydrogen doubles it
            float speed = p.capabilities.isCreativeMode || ArmorSuit.exoClass(suitOf(piece(p, 1))) ? VANILLA_FLY_SPEED : QUANTUM_FLY_SPEED;
            if (!p.capabilities.isCreativeMode && boostedFlight(p)) {
                speed *= 2F;
            }
            if (Math.abs(p.capabilities.getFlySpeed() - speed) > 1e-4) {
                setFlySpeed(p, speed);
                p.sendPlayerAbilities();          // the client takes its fly speed from this packet
            }
        }
        if (can && p.capabilities.isFlying) {
            p.fallDistance = 0;           // vanilla adds up the descent while flying - landing turned it into fall damage
        }
    }

    /** Soft descent after the gases cut the flight in mid-air: how long (ticks), and the fastest fall meanwhile (motionY). */
    public static final int SOFT_DESCENT_TICKS = 100;
    public static final double SOFT_DESCENT_SPEED = -0.15;
    private static final String DESCENT = "scSoftDescent", WAS_FLYING = "scWasFlying";

    /**
     * The flight is cut by the gases, not by the wearer: the chestplate is worn with its flight
     * switched on, but the gases forbid it (hydrogen out, or emergency mode - helium under 1%).
     */
    public static boolean flightCutByGas(ItemStack[] worn) {
        ItemStack chest = worn[ArmorGasSC.CHEST];
        return chest != null && strict(suitOf(chest)) && ItemArmorSC.isEnabled(chest, ArmorFeature.FLIGHT)
                && !gasAllows(worn, ArmorFeature.FLIGHT);
    }

    /**
     * Every tick, both sides (the player's own client, the server). The gases cut the flight in
     * mid-air: for SOFT_DESCENT_TICKS the fall is slowed to SOFT_DESCENT_SPEED (the client, which
     * moves its player) and that landing does no damage (the server: fall distance kept at 0, and
     * fall() lets the landing through). It only limits falling - never lifts - and comes once per cut:
     * the server starts it in flight(), the client when its flight is taken away (abilities from the
     * server) with the same gas check. Over on the ground, in water / lava, on a ladder, flying again.
     */
    private static void softDescent(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        if (p.worldObj.isRemote) {
            boolean was = data.getBoolean(WAS_FLYING), now = p.capabilities.isFlying;
            data.setBoolean(WAS_FLYING, now);
            if (was && !now && !p.capabilities.allowFlying && !p.capabilities.isCreativeMode && !p.onGround
                    && flightCutByGas(ArmorGasSC.wornSet(p))) {
                data.setInteger(DESCENT, SOFT_DESCENT_TICKS);
            }
        }
        int left = data.getInteger(DESCENT);
        if (left <= 0) {
            return;
        }
        if (p.onGround || p.capabilities.isFlying || p.isInWater() || p.handleLavaMovement() || p.isOnLadder()) {
            data.removeTag(DESCENT);
            return;
        }
        if (left == 1) {
            data.removeTag(DESCENT);
        } else {
            data.setInteger(DESCENT, left - 1);
        }
        p.fallDistance = 0;
        if (p.worldObj.isRemote && p.motionY < SOFT_DESCENT_SPEED) {
            p.motionY = SOFT_DESCENT_SPEED;
        }
    }

    /** The soft descent is on (server: its landing does no damage). */
    public static boolean softDescending(EntityPlayer p) {
        return p.getEntityData().getInteger(DESCENT) > 0;
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
        NBTTagCompound data = p.getEntityData();
        emergencyWarning(p);
        if (active(p, ArmorFeature.NIGHT_VISION) && pay(p, ArmorFeature.NIGHT_VISION, ArmorFeature.NIGHT_VISION.euPerSecond)) {
            spendGas(p, ArmorFeature.NIGHT_VISION);                // Quantum / Exo: krypton
            p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 260, 0, true));
            data.setBoolean(NIGHT_VISION_FLAG, true);
            heat += ArmorFeature.NIGHT_VISION.heat;
        } else {
            ItemStack chest = piece(p, 1);
            boolean sensorChip = chest != null && chest.hasTagCompound() && !overheated(p) && !emergency(ArmorGasSC.wornSet(p))
                    && chest.getTagCompound().getCompoundTag("ChipsSC").hasKey(com.sc.util.ChipType.SENSOR.name());
            if (sensorChip) {
                // a running Sensor chip gives the same effect (CommonEventHandler) - it's ours too, left on
                data.setBoolean(NIGHT_VISION_FLAG, true);
            } else if (data.getBoolean(NIGHT_VISION_FLAG)) {
                // switched off or out of charge: only our own effect goes - a potion's / another mod's
                // (not ambient, or longer than ours) is left alone
                PotionEffect nv = p.getActivePotionEffect(Potion.nightVision);
                if (nv != null && nv.getIsAmbient() && nv.getDuration() <= 260) {
                    p.removePotionEffect(Potion.nightVision.id);
                }
                data.removeTag(NIGHT_VISION_FLAG);
            }
        }
        heat += data.getInteger(HEAT_KEY);
        data.removeTag(HEAT_KEY);
        heat += lifeSupport(p);
        // breathing on the helmet's oxygen: under water or stuck inside a block (no oxygen - no breathing)
        // only while it's needed: the air going down (not in creative, not with water breathing), able to suffocate
        boolean underwater = p.isInsideOfMaterial(Material.water) && p.getAir() < 300;
        boolean inWall = p.isEntityInsideOpaqueBlock() && !p.capabilities.disableDamage;
        if ((underwater || inWall) && canBreathe(p) && pay(p, ArmorFeature.AIR, ArmorFeature.AIR.euPerSecond)
                && breathe(ArmorGasSC.wornSet(p))) {
            if (underwater) {
                p.setAir(300);
            }
            data.setBoolean(O2_USED, true);
            heat += ArmorFeature.AIR.heat;
        }
        oxygenWarning(p);
        if (active(p, ArmorFeature.CLEANSE)) {
            for (Potion bad : new Potion[]{Potion.poison, Potion.wither, Potion.hunger, Potion.confusion, Potion.blindness}) {
                if (p.isPotionActive(bad) && !com.sc.radiation.RadiationSC.sicknessHolds(p, bad.id)
                        && ArmorGasSC.suitAmount(p, Gas.OXYGEN) > 0 && pay(p, ArmorFeature.CLEANSE, ArmorFeature.CLEANSE_COST)) {
                    p.removePotionEffect(bad.id);
                    spendGas(p, ArmorFeature.CLEANSE);               // oxygen, per effect
                }
            }
        }
        for (ArmorFeature f : new ArmorFeature[]{ArmorFeature.ORE_SCANNER, ArmorFeature.THERMAL}) {
            if (active(p, f) && pay(p, f, f.euPerSecond)) {         // the client draws them
                spendGas(p, f);                                      // krypton
                heat += f.heat;
            }
        }
        if (active(p, ArmorFeature.SOLAR) && !precipitationAt(p)
                && p.worldObj.canBlockSeeTheSky(MathHelper.floor_double(p.posX), MathHelper.floor_double(p.posY) + 2,
                MathHelper.floor_double(p.posZ))) {
            chargeSuit(p, solarPerSecond(p));
        }
        // regeneration (combat mode): heals while hurt, paid from the chestplate at the tripled rate
        if (regenOn(p) && p.getHealth() < p.getMaxHealth() && !p.isDead
                && pay(p, ArmorFeature.REGENERATION, ArmorFeature.REGENERATION.euPerSecond)) {
            p.heal(ArmorFeature.regenHeal(suitOf(piece(p, 1))));
            heat += ArmorFeature.REGENERATION.heat;
        }
        return heat + perSecondRest(p, mode);
    }

    /**
     * EU into all worn pieces at once: an even share each, what a full piece can't take goes to the
     * rest (the solar film, the fusion cell). @return EU taken
     */
    public static int chargeSuit(EntityPlayer p, int eu) {
        int left = eu;
        {
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
        return eu - left;
    }

    /** The second half of perSecond (kept apart only to keep the method short). @return heat */
    private static int perSecondRest(EntityPlayer p, int mode) {
        int heat = 0;
        if (active(p, ArmorFeature.CHARGER)) {
            heat += chargeWeapons(p);
        }
        if (p.ticksExisted % 200 == 0) {
            resendFlight(p);
        }
        if (ArmorGasSC.suitAmount(p, Gas.HYDROGEN) >= H2_WARN_REARM) {
            rearm(p, H2_WARN);                                    // refilled: the warning may come again
        }
        if (active(p, ArmorFeature.FLIGHT) && p.capabilities.isFlying && !p.capabilities.isCreativeMode) {
            if (boostedFlight(p)) {                               // the engine boost: hydrogen instead of EU
                ArmorGasSC.drainExactUse(ArmorGasSC.wornSet(p), Gas.HYDROGEN, ArmorGasSC.H2_FLIGHT_PER_SECOND);
                heat += ArmorFeature.FLIGHT.heat + ArmorFeature.BOOSTER.heat;
                hydrogenWarning(p, ArmorGasSC.H2_FLIGHT_PER_SECOND);
            } else if (pay(p, ArmorFeature.FLIGHT, ArmorFeature.FLIGHT.euPerSecond)) {
                spendGas(p, ArmorFeature.FLIGHT);                 // and a little hydrogen
                heat += ArmorFeature.FLIGHT.heat;
                if (strict(suitOf(piece(p, 1)))) {
                    hydrogenWarning(p, ArmorGasSC.H2_FLIGHT_BASE_PER_SECOND);
                }
            }
        }
        if (active(p, ArmorFeature.FIRE_PROOF) && (p.isBurning() || p.handleLavaMovement())) {
            // Exo with argon: half the EU, and the fire round the wearer goes out
            ItemStack[] worn = ArmorGasSC.wornSet(p);
            boolean argon = ArmorGasSC.amountOf(worn, Gas.ARGON) >= ArmorGasSC.ARGON_EXO_PER_SECOND;
            int eu = argon ? (int) Math.ceil(ArmorFeature.FIRE_PROOF.euPerSecond * ArmorGasSC.ARGON_EXO_EU_MUL)
                    : ArmorFeature.FIRE_PROOF.euPerSecond;
            if (pay(p, ArmorFeature.FIRE_PROOF, eu)) {
                // ARGON_EXO_PER_SECOND with the bonus (half the EU, the fire round put out) - it covers the
                // function's own ARGON_FIRE_PROOF_PER_SECOND; with less argon left just that
                argon = argon && ArmorGasSC.drainExactUse(worn, Gas.ARGON, ArmorGasSC.ARGON_EXO_PER_SECOND);
                if (!argon) {
                    spendGas(p, ArmorFeature.FIRE_PROOF);
                }
                p.addPotionEffect(new PotionEffect(Potion.fireResistance.id, 45, 0, true));
                p.extinguish();
                heat += ArmorFeature.FIRE_PROOF.heat;
                if (argon) {
                    argonFireAround(p, worn);
                }
            }
        }
        if (p.handleLavaMovement() && !active(p, ArmorFeature.FIRE_PROOF) && !fireHarmless(p) && ArmorGasSC.suitAmount(p, Gas.ARGON) > 0) {
            ArmorGasSC.drainFractionUse(ArmorGasSC.wornSet(p), Gas.ARGON, ArmorGasSC.ARGON_LAVA_PER_SECOND);   // half the lava's damage meanwhile
        }
        ArmorSuit legs = suitOf(piece(p, 2));
        if (active(p, ArmorFeature.SPEED) && p.isSprinting() && pay(p, ArmorFeature.SPEED, ArmorFeature.SPEED.euPerSecond)) {
            int level = Math.max(0, (ArmorSuit.exoClass(legs) ? 2 : 1) - (mode == 0 ? 1 : 0));
            p.addPotionEffect(new PotionEffect(Potion.moveSpeed.id, 30, level, true));
            spendGas(p, ArmorFeature.SPEED);                      // hydrogen while sprinting
            heat += ArmorFeature.SPEED.heat;
        }
        ArmorSuit boots = suitOf(piece(p, 3));
        if (active(p, ArmorFeature.JUMP) && pay(p, ArmorFeature.JUMP, ArmorFeature.JUMP.euPerSecond)) {
            int level = Math.max(0, (ArmorSuit.exoClass(boots) ? 2 : 1) - (mode == 0 ? 1 : 0));
            p.addPotionEffect(new PotionEffect(Potion.jump.id, 30, level, true));
            heat += ArmorFeature.JUMP.heat;
        }
        if (active(p, ArmorFeature.WATER_WALK) && isOnFluid(p)) {
            if (pay(p, ArmorFeature.WATER_WALK, ArmorFeature.WATER_WALK.euPerSecond)) {
                spendGas(p, ArmorFeature.WATER_WALK);             // argon while on the liquid
                heat += ArmorFeature.WATER_WALK.heat;
            }
        }
        setBonuses(p);
        return heat;
    }

    /**
     * Once a second, server: the gases' own systems - the Cryo Tank chip's tank volume, the fusion
     * cell, the oxygen regenerator chip, krypton for the searchlight / the Exo ore scanner.
     * (Helium cooling is in the heat step, CommonEventHandler; radiation's heavy water in RadiationSC.)
     * @return heat made
     */
    private static int lifeSupport(EntityPlayer p) {
        int heat = 0;
        NBTTagCompound data = p.getEntityData();
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        ItemStack chest = worn[ArmorGasSC.CHEST], helmet = worn[ArmorGasSC.HELMET];
        if (ArmorGasSC.applyCapacityBonus(worn)) {
            p.inventoryContainer.detectAndSendChanges();
        }
        // deuterium fusion cell (Exo chestplate): only with helium in the loop as well
        data.removeTag(FUSION_ON);
        if (active(p, ArmorFeature.FUSION_CELL)) {
            if (suitNeedsCharge(p) && fusionStep(worn)) {                // a full suit: the cell idles, no deuterium burnt for nothing
                chargeSuit(p, ArmorGasSC.FUSION_EU_PER_TICK * 20);
                heat += ArmorFeature.FUSION_CELL.heat;
                data.setBoolean(FUSION_ON, true);
            } else if (!heliumReady(worn) && ArmorGasSC.amountOf(worn, Gas.DEUTERIUM) > 0) {
                warn(p, "sc.gas.warn.fusion", 1200);
            }
        }
        // oxygen regenerator chip (Exo): under water the helmet's oxygen comes back, for EU
        int regen = ArmorGasSC.chipTier(chest, ChipType.OXYGEN_REGEN);
        if (regen > 0 && !overheated(p) && !emergency(worn) && ArmorSuit.exoClass(suitOf(chest)) && p.isInWater()
                && ArmorGasSC.capacityOf(worn, Gas.OXYGEN) > ArmorGasSC.amountOf(worn, Gas.OXYGEN)
                && ItemArmorSC.pay(chest, (int) Math.ceil(ArmorGasSC.OXYGEN_REGEN_EU * costMul(p)))) {
            ArmorGasSC.fillOf(worn, Gas.OXYGEN, ArmorGasSC.OXYGEN_REGEN_BASE + ArmorGasSC.OXYGEN_REGEN_PER_TIER * regen, false);
        }
        // krypton: the searchlight (the ore scanner, night vision and thermal pay their own, perSecond -
        // the Exo scanner's longer reach comes with the scanner's krypton)
        boolean light = active(p, ArmorFeature.SEARCHLIGHT) && data.getBoolean(LIGHT_ON);
        if (light && ArmorGasSC.amountOf(worn, Gas.KRYPTON) > 0) {
            ArmorGasSC.drainFractionUse(worn, Gas.KRYPTON, ArmorGasSC.KRYPTON_PER_MIN / 60F);
        }
        return heat;
    }

    /** Some worn piece can take more EU. */
    private static boolean suitNeedsCharge(EntityPlayer p) {
        for (int t = 0; t < 4; t++) {
            ItemStack s = piece(p, t);
            if (s != null && ItemArmorSC.chargeOf(s) < ItemArmorSC.capacityOf(s)) {
                return true;
            }
        }
        return false;
    }

    /** One second of the fusion cell: deuterium burnt, if there is deuterium and helium. @return whether it ran */
    public static boolean fusionStep(ItemStack[] worn) {
        float use = ArmorGasSC.FUSION_D_PER_TICK * 20 * ArmorGasSC.gasUseMul(worn, Gas.DEUTERIUM);   // Singular: 20% less
        if (!heliumReady(worn) || ArmorGasSC.amountOf(worn, Gas.DEUTERIUM) < (int) Math.ceil(use)) {
            return false;
        }
        ArmorGasSC.drainFraction(worn, Gas.DEUTERIUM, use);
        return true;
    }

    private static final String H2_WARN = "sc.gas.warn.hydrogen";
    /** Hydrogen to have again before "hydrogen low" may come again: twice the warning line of the boosted flight. */
    private static final int H2_WARN_REARM = 2 * ArmorGasSC.H2_FLIGHT_PER_SECOND * ArmorGasSC.H2_LOW_WARN_SECONDS;

    /** Flying on the suit at `perSecond` mB of hydrogen a second: "hydrogen low" once, when it lasts under H2_LOW_WARN_SECONDS. */
    private static void hydrogenWarning(EntityPlayer p, float perSecond) {
        if (ArmorGasSC.hydrogenLow(ArmorGasSC.suitAmount(p, Gas.HYDROGEN), perSecond)) {
            warn(p, H2_WARN, 20 * 60 * 10);
        }
    }

    /** "Oxygen: 30 seconds" once, while breathing on it; again after a refill. */
    private static void oxygenWarning(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        boolean used = data.getBoolean(O2_USED);
        data.removeTag(O2_USED);
        int left = ArmorGasSC.suitAmount(p, Gas.OXYGEN);
        if (left > 30 * ArmorGasSC.OXYGEN_PER_SECOND * 2) {
            rearm(p, "sc.gas.warn.oxygen");
        } else if (used && left <= 30 * ArmorGasSC.OXYGEN_PER_SECOND) {
            warn(p, "sc.gas.warn.oxygen", 20 * 60 * 10);
        }
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
                // and the drills (not mid-dig: it would restart the block), never below the chestplate's reserve
                took = ItemDrillSC.charge(s, Math.min(offer, DrillLogicSC.spare(chest)));
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
        ArmorSuit set = overheated(p) ? null : bonusSet(p);     // an overheated suit (or emergency mode) gives no set bonus
        // Exo: never hungry - the chestplate's energy stands in for food
        if (ArmorSuit.exoClass(set)) {                           // Exo and Singular
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
        if (!p.worldObj.isRemote && softDescending(p)) {
            p.getEntityData().removeTag(DESCENT);                 // the gases cut the flight: this landing is soft, once
            return 0F;
        }
        float left = dampFall(p, distance);
        // the engine boost: what the boots' EU didn't soften, a hydrogen burst does - no damage at all
        PotionEffect jump = p.getActivePotionEffect(Potion.jump);
        if (left - 3 - (jump == null ? 0 : jump.getAmplifier() + 1) > 0 && boosterOn(p)
                && ArmorGasSC.drainExactUse(ArmorGasSC.wornSet(p), Gas.HYDROGEN, ArmorGasSC.H2_SOFT_LANDING)) {
            p.worldObj.playSoundAtEntity(p, "fire.ignite", 0.6F, 1.5F);
            return 0F;
        }
        return left;
    }

    private static float dampFall(EntityPlayer p, float distance) {
        if (!active(p, ArmorFeature.FALL_DAMPING) || distance <= 3) {
            return distance;
        }
        ArmorSuit boots = suitOf(piece(p, 3));
        float share = ArmorSuit.exoClass(boots) ? 1F : boots == ArmorSuit.QUANTUM ? 0.75F : 0.5F;
        PotionEffect jump = p.getActivePotionEffect(Potion.jump);
        float points = distance - 3 - (jump == null ? 0 : jump.getAmplifier() + 1);   // vanilla already takes that off
        if (points <= 0) {
            return distance;
        }
        int want = (int) Math.ceil(points * share);
        int can = (int) (ItemArmorSC.chargeOf(piece(p, 3)) / Math.max(1F, ArmorFeature.FALL_COST_PER_POINT * costMul(p)));
        int absorbed = Math.min(want, can);
        pay(p, ArmorFeature.FALL_DAMPING, absorbed * ArmorFeature.FALL_COST_PER_POINT);
        if (absorbed > 0) {
            // Quantum / Exo: a hydrogen burst by the damage absorbed - 1 mB a point, at least 1
            spendGas(p, ArmorFeature.FALL_DAMPING, ArmorGasSC.fallDampingGas(absorbed));
        }
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
            if (ItemArmorSC.isEnabled(piece(p, 1), ArmorFeature.ANNIHILATION) && !heliumReady(ArmorGasSC.wornSet(p))) {
                p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.gas.nohelium"));
            }
            return;
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (now - data.getLong("scAnnihilated") < 20) {
            return;
        }
        if (!ArmorSuit.exoClass(fullSet(p))) {                       // a full Exo or Singular set
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.armor.annihilate.low"));
            return;
        }
        if (!heliumReady(ArmorGasSC.wornSet(p))) {                  // hard rule: no helium in the loop - no pulse
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.gas.nohelium"));
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
                || !ArmorSuit.exoClass(fullSet(p)) || p.capabilities.disableDamage || p.isEntityInvulnerable()) {
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
        if (!heliumReady(ArmorGasSC.wornSet(p))) {
            warn(p, "sc.gas.nohelium.shield", 600);             // hard rule: the energy shield needs the helium loop
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
        return !p.worldObj.isRemote && ArmorSuit.exoClass(fullSet(p)) && active(p, ArmorFeature.EXPLOSION_PROOF)
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
        Vec3 look = p.getLookVec();
        double len = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        if (len < 0.01) {
            return;                                        // straight up / down: no direction - nothing paid (it used to be)
        }
        if (!active(p, ArmorFeature.DASH)) {
            return;
        }
        // the engine boost: half as far again, on hydrogen instead of EU
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        boolean boost = boosterOn(p) && ArmorGasSC.drainExactUse(worn, Gas.HYDROGEN, ArmorGasSC.H2_DASH);
        if (!boost) {
            // no boost: the EU as before, and (Quantum / Exo) the same hydrogen all the same
            boolean h2 = strict(suitOf(piece(p, ArmorFeature.DASH.piece)));
            if (h2 && ArmorGasSC.amountOf(worn, Gas.HYDROGEN) < ArmorGasSC.H2_DASH || !pay(p, ArmorFeature.DASH, ArmorFeature.DASH_COST)) {
                return;
            }
            if (h2) {
                ArmorGasSC.drainExactUse(worn, Gas.HYDROGEN, ArmorGasSC.H2_DASH);
            }
        }
        double push = boost ? 1.8 * 1.5 : 1.8;
        data.setLong("scDashAt", now);
        p.motionX = look.xCoord / len * push;
        p.motionZ = look.zCoord / len * push;
        p.motionY = Math.max(p.motionY, 0.35);
        p.fallDistance = 0;
        p.velocityChanged = true;
        addHeat(p, ArmorFeature.DASH.heat);
        p.worldObj.playSoundAtEntity(p, "mob.ghast.fireball", 0.4F, 1.6F);
    }
}
