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
        if (!com.sc.util.SingularLevel.unlocked(p, f, s)) {
            return false;                 // a Singular function above the piece's level (creative: all open)
        }
        if (f.offOnLowCharge() && lowCharge(s)) {
            return false;                 // О2: under 10% charge the flight, the event horizon and the anchor are off
        }
        if (f.needsFullSet() && fullSet(p) != ArmorSuit.SINGULAR) {
            return false;                 // К9, К1, К2: all four Singular pieces
        }
        if (!com.sc.util.SingularLevel.branchAllowed(p, f, s)) {
            return false;                 // Р2: the other side of the piece's branch, or no branch chosen yet (creative: both)
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

    /** О2: the piece holds under SING_LOW_CHARGE of its capacity. */
    public static boolean lowCharge(ItemStack s) {
        return lowCharge(ItemArmorSC.chargeOf(s), ItemArmorSC.capacityOf(s));
    }

    /** Pure: under SING_LOW_CHARGE of `cap`. */
    public static boolean lowCharge(int charge, int cap) {
        return cap > 0 && (long) charge * 100 < (long) cap * Math.round(ArmorFeature.SING_LOW_CHARGE * 100);
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
        heat = Math.round(heat * SingularPowersSC.heatMul(p));      // К1: the boost heats twice as hard
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
        float mul = costMul(p) * (f.minSuit == ArmorSuit.SINGULAR ? SingularPowersSC.costMul(p) : 1F);   // К1: x2 for the Singular ones
        mul *= com.sc.util.SingularLevel.euMul(s);                     // Р3 / Р4: -5% a level of the piece (+10% in a synced set)
        return s != null && ItemArmorSC.pay(s, (int) Math.ceil(eu * mul));
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
        stabilizer(p);                                         // both sides: the client moves the player
        antigravity(p);
        if (p.worldObj.isRemote) {
            airJumpKey(p);
            softDescent(p);
            gravFlightClient(p);
            return;
        }
        flight(p);
        softDescent(p);
        argon(p);
        searchlight(p);
        if (shieldActive(p) && p.ticksExisted % 2 == 0) {
            shield(p);
        }
        if (active(p, ArmorFeature.EVENT_HORIZON)) {
            horizonScan(p);
        }
        if (p.ticksExisted % 2 == 0 && active(p, ArmorFeature.MAGNET)) {
            magnet(p);
        }
        if (p.posY < 0 && !p.isDead) {
            voidRescue(p);
        }
        if (p.ticksExisted % SingularSensesSC.ANALYZE_EVERY == 0) {
            SingularSensesSC.analyzer(p);                      // Ш5: what the wearer looks at
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
        return antigravJumpReady(p) || boostedJumpReady(p);
    }

    /** The Quantum / Exo air jump: the engine boost and the jump boots, on hydrogen. */
    private static boolean boostedJumpReady(EntityPlayer p) {
        return boosterOn(p) && active(p, ArmorFeature.JUMP) && ArmorGasSC.suitAmount(p, Gas.HYDROGEN) >= ArmorGasSC.H2_AIR_JUMP;
    }

    /** Б3's air jump (Singular boots): hydrogen and the boots' EU for it - it takes the place of the boosted one (one jump either way). */
    private static boolean antigravJumpReady(EntityPlayer p) {
        ItemStack boots = piece(p, 3);
        return active(p, ArmorFeature.ANTIGRAV) && ArmorGasSC.suitAmount(p, Gas.HYDROGEN) >= ArmorFeature.SING_H2_AIR_JUMP
                && ItemArmorSC.chargeOf(boots) >= (int) Math.ceil(ArmorFeature.ANTIGRAV_AIR_JUMP_EU * costMul(p));
    }

    /** Server, from the client's AIR_JUMP_ACTION: a second jump in mid-air for H2_AIR_JUMP mB of hydrogen. */
    public static void airJump(EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (p.onGround || p.capabilities.isFlying || data.getBoolean(AIR_JUMPED)
                || (data.hasKey(AIR_JUMP_AT) && now - data.getLong(AIR_JUMP_AT) < 10 && now >= data.getLong(AIR_JUMP_AT))) {
            return;
        }
        if (antigravJumpReady(p)) {                      // Б3: the Singular boots' own jump (plain numbers, no K10 discount)
            ItemStack[] worn = ArmorGasSC.wornSet(p);
            if (!ArmorGasSC.drainExact(worn, Gas.HYDROGEN, ArmorFeature.SING_H2_AIR_JUMP)) {
                return;
            }
            pay(p, ArmorFeature.ANTIGRAV, ArmorFeature.ANTIGRAV_AIR_JUMP_EU);
            data.removeTag(SLOW_FALL);
        } else if (!boostedJumpReady(p) || !ArmorGasSC.drainExactUse(ArmorGasSC.wornSet(p), Gas.HYDROGEN, ArmorGasSC.H2_AIR_JUMP)) {
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

    /** Н1 gravitational flight works (the Singular chestplate; it takes the place of the Exo flight while it does). */
    public static boolean gravFlightOn(EntityPlayer p) {
        return active(p, ArmorFeature.GRAV_FLIGHT);
    }

    private static final String FAST_FLAG = "scGravFlySpeed";

    /** Exo chestplate: survival flight while switched on and charged, paid for while flying. Н1 flies x3 as fast. */
    private static void flight(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        boolean grav = gravFlightOn(p);
        boolean can = grav || active(p, ArmorFeature.FLIGHT);
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
            if (grav) {
                // Н1: x3 the Exo flight, in creative too; К1 doubles it (halves it while weakened)
                speed = VANILLA_FLY_SPEED * ArmorFeature.GRAV_FLIGHT_SPEED_MUL * SingularPowersSC.singularBoost(p);
            } else if (!p.capabilities.isCreativeMode && boostedFlight(p)) {
                speed *= 2F;
            }
            if (Math.abs(p.capabilities.getFlySpeed() - speed) > 1e-4) {
                setFlySpeed(p, speed);
                p.sendPlayerAbilities();          // the client takes its fly speed from this packet
            }
            if (grav) {
                data.setBoolean(FAST_FLAG, true);
            } else {
                data.removeTag(FAST_FLAG);
            }
        } else if (data.getBoolean(FAST_FLAG)) {
            // Н1 off in creative (where FLIGHT_FLAG never gets set): the x3 speed mustn't stay saved with the player
            data.removeTag(FAST_FLAG);
            setFlySpeed(p, VANILLA_FLY_SPEED);
            p.sendPlayerAbilities();
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
        if (chest == null || !strict(suitOf(chest))) {
            return false;
        }
        boolean exo = ItemArmorSC.isEnabled(chest, ArmorFeature.FLIGHT) && !gasAllows(worn, ArmorFeature.FLIGHT);
        // Н1: no helium (emergency mode) or, О2, under 10% charge - cut the same way, with the soft descent
        boolean grav = ItemArmorSC.isEnabled(chest, ArmorFeature.GRAV_FLIGHT)
                && (!gasAllows(worn, ArmorFeature.GRAV_FLIGHT) || lowCharge(chest));
        return exo || grav;
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

    /**
     * The Quantum / Exo shield works: switched on and able - and, in a Singular chestplate, not
     * replaced by Н2 (the event horizon working takes over both the turning back of projectiles and
     * the full set's energy shield, so the two never act on one hit).
     */
    public static boolean shieldActive(EntityPlayer p) {
        return active(p, ArmorFeature.SHIELD) && !active(p, ArmorFeature.EVENT_HORIZON);
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

    static boolean shooterIs(Entity e, EntityPlayer p) {
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
        // Ш8 (Singular helmet): it takes the night vision over - on in the dark, off in bright light (no glare)
        boolean sight = active(p, ArmorFeature.CLEAR_SIGHT) && pay(p, ArmorFeature.CLEAR_SIGHT, ArmorFeature.CLEAR_SIGHT.euPerSecond);
        if (sight) {
            ArmorGasSC.drainFraction(ArmorGasSC.wornSet(p), Gas.KRYPTON, ArmorFeature.SING_KR_SIGHT_PER_MIN / 60F * SingularPowersSC.costMul(p));
            heat += ArmorFeature.CLEAR_SIGHT.heat;
        }
        boolean nightVision = sight ? !brightAtEyes(p)
                : active(p, ArmorFeature.NIGHT_VISION) && pay(p, ArmorFeature.NIGHT_VISION, ArmorFeature.NIGHT_VISION.euPerSecond);
        if (nightVision) {
            if (!sight) {
                spendGas(p, ArmorFeature.NIGHT_VISION);            // Quantum / Exo: krypton
                heat += ArmorFeature.NIGHT_VISION.heat;
            }
            p.addPotionEffect(new PotionEffect(Potion.nightVision.id, 260, 0, true));
            data.setBoolean(NIGHT_VISION_FLAG, true);
        } else {
            ItemStack chest = piece(p, 1);
            boolean sensorChip = !sight && chest != null && chest.hasTagCompound() && !overheated(p) && !emergency(ArmorGasSC.wornSet(p))
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
        int collected = data.getInteger(HEAT_KEY);                   // already doubled by addHeat during К1
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
        if (p.isPotionActive(Potion.wither)) {
            witherOff(p);                                            // К9: cheaper than the cleanse, so first
        }
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
        int own = heat + perSecondRest(p, mode) + singularSecond(p);
        return Math.round(own * SingularPowersSC.heatMul(p)) + collected;   // К1: x2 while boosted
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
        if (gravFlightOn(p) && p.capabilities.isFlying && !p.capabilities.isCreativeMode) {
            // Н1: helium and EU, no hydrogen
            if (pay(p, ArmorFeature.GRAV_FLIGHT, ArmorFeature.GRAV_FLIGHT.euPerSecond)) {
                ArmorGasSC.drainFraction(ArmorGasSC.wornSet(p), Gas.HELIUM, ArmorFeature.SING_HE_FLIGHT_PER_SECOND * SingularPowersSC.costMul(p));
                heat += ArmorFeature.GRAV_FLIGHT.heat;
            }
        } else if (active(p, ArmorFeature.FLIGHT) && p.capabilities.isFlying && !p.capabilities.isCreativeMode) {
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
        updateAnchor(p);
    }

    // ------------------------------------------------------------------ events

    /** Boots soften a fall: Nano half, Quantum three quarters, Exo all of it - paid per point. @return the new fall distance */
    public static float fall(EntityPlayer p, float distance) {
        if (!p.worldObj.isRemote && softDescending(p)) {
            p.getEntityData().removeTag(DESCENT);                 // the gases cut the flight: this landing is soft, once
            return 0F;
        }
        if (!p.worldObj.isRemote && p.getEntityData().getBoolean(RESCUE_LANDING)) {
            p.getEntityData().removeTag(RESCUE_LANDING);          // Б4: the landing after the void rescue is soft
            return 0F;
        }
        if (!p.worldObj.isRemote && p.getEntityData().getBoolean(SLOW_FALL)) {
            return 0F;                                            // Б3 held the fall: no damage
        }
        float result = dampFall(p, distance);
        // the engine boost: what the boots' EU didn't soften, a hydrogen burst does - no damage at all
        if (fallPoints(p, result) > 0 && boosterOn(p)
                && ArmorGasSC.drainExactUse(ArmorGasSC.wornSet(p), Gas.HYDROGEN, ArmorGasSC.H2_SOFT_LANDING)) {
            p.worldObj.playSoundAtEntity(p, "fire.ignite", 0.6F, 1.5F);
            result = 0F;
        }
        if (!p.worldObj.isRemote) {
            gravityStrike(p, fallPoints(p, distance) - fallPoints(p, result));   // Б1 (+ С2: the higher the fall, the harder)
        }
        return result;
    }

    /** The fall damage a fall of `distance` blocks does (vanilla: 3 blocks free, a jump boost takes its level more off). */
    public static float fallPoints(EntityPlayer p, float distance) {
        PotionEffect jump = p.getActivePotionEffect(Potion.jump);
        return Math.max(0F, distance - 3 - (jump == null ? 0 : jump.getAmplifier() + 1));
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
        if (!shieldActive(p)) {
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

    // ================================================================== the Singular suit's own functions (stage 2a)
    // Plan docs/plan-singular-armor.md §3-§5. Their gases are spent at the plan's numbers with the plain
    // ArmorGasSC.drain* (the K10 "-20%" is only for the Exo legacy functions).

    private static final String SLOW_FALL = "scSlowFall", STAB_AT = "scStabAt", RESCUE_LANDING = "scRescueLanding",
            SAFE_PREFIX = "scSafe", PHASE_AT = "scPhaseAt";
    /** Item entity data: who threw it (UUID) - the magnet leaves it alone a moment (ItemTossEvent, ShieldEventHandler). */
    public static final String TOSSED_BY = "scTossedBy";
    private static java.lang.reflect.Field inWebField;

    /** С3 (no phase dash cooldown while time is slowed): whether the wearer's time slowing (Н4) is running (server). */
    public static boolean timeSlowActive(EntityPlayer p) {
        return SingularPowersSC.timeSlowActive(p);
    }

    // ------------------------------------------------------------------ Н1 gravitational flight: no inertia (client)

    /**
     * The player's own client, every tick after it moved: flying on Н1 with no movement key the
     * player stops at once, and without jump / sneak hangs in place (the x3 speed comes from the
     * abilities the server sends, flight()).
     */
    private static void gravFlightClient(EntityPlayer p) {
        if (!p.capabilities.isFlying || p.ridingEntity != null || !gravFlightOn(p)) {
            return;
        }
        if (p.moveForward == 0F && p.moveStrafing == 0F) {
            p.motionX = 0;
            p.motionZ = 0;
        }
        if (!jumping(p) && !p.isSneaking()) {
            p.motionY = 0;
        }
    }

    // ------------------------------------------------------------------ Н7 magnet

    /** Server, every other tick: items and experience orbs within MAGNET_RADIUS fly to the wearer (what they threw a second ago stays). */
    private static void magnet(EntityPlayer p) {
        double r = ArmorFeature.MAGNET_RADIUS;
        List list = p.worldObj.getEntitiesWithinAABB(Entity.class, p.boundingBox.expand(r, r, r));
        String me = p.getUniqueID().toString();
        for (Object o : list) {
            Entity e = (Entity) o;
            if (e.isDead || !(e instanceof net.minecraft.entity.item.EntityItem || e instanceof net.minecraft.entity.item.EntityXPOrb)) {
                continue;
            }
            if (e instanceof net.minecraft.entity.item.EntityItem) {
                net.minecraft.entity.item.EntityItem item = (net.minecraft.entity.item.EntityItem) e;
                if (!magnetPulls(me, item.getEntityData().getString(TOSSED_BY), item.age)) {
                    continue;
                }
            }
            double dx = p.posX - e.posX, dy = p.posY + 0.5 - e.posY, dz = p.posZ - e.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > r || d < 0.3) {
                continue;
            }
            double v = Math.min(0.45, 0.15 + d * 0.05);
            e.motionX = dx / d * v;
            e.motionY = dy / d * v + 0.04;                 // against the item's own gravity
            e.motionZ = dz / d * v;
            e.velocityChanged = true;
        }
    }

    /** Pure: the magnet pulls an item thrown by `thrower` (UUID, "" none) at age `age` for the player `me`. */
    public static boolean magnetPulls(String me, String thrower, int age) {
        return !(me.equals(thrower) && age < ArmorFeature.MAGNET_THROWN_GRACE);
    }

    // ------------------------------------------------------------------ П3 gravitational anchor

    private static final UUID ANCHOR_ID = UUID.fromString("7a2e4c90-3d1b-4f6e-a8c5-91b0d2e4f733");

    /** П3 can stop a push now: working, heavy water for one. */
    public static boolean anchorReady(EntityPlayer p) {
        return active(p, ArmorFeature.GRAV_ANCHOR)
                && ArmorGasSC.amountOf(ArmorGasSC.wornSet(p), Gas.HEAVY_WATER) >= (int) Math.ceil(ArmorFeature.SING_D2O_ANCHOR);
    }

    /** The anchor's full knockback resistance on while it can stop a push, off otherwise (once a second, and at each hit). */
    public static void updateAnchor(EntityPlayer p) {
        anchorModifier(p, anchorReady(p));
    }

    private static void anchorModifier(EntityPlayer p, boolean on) {
        IAttributeInstance kb = p.getEntityAttribute(SharedMonsterAttributes.knockbackResistance);
        AttributeModifier mod = kb.getModifier(ANCHOR_ID);
        if (on && mod == null) {
            kb.applyModifier(new AttributeModifier(ANCHOR_ID, "SC Singular anchor", 1.0, 0));
        } else if (!on && mod != null) {
            kb.removeModifier(mod);
        }
    }

    /**
     * A hit that would knock the wearer back (LivingHurtEvent, a source with an entity): П3 pays its
     * heavy water and keeps the knockback resistance on for it; with none left it goes off - the hit
     * pushes as usual.
     */
    public static void anchorHit(EntityPlayer p, net.minecraft.util.DamageSource src) {
        if (p.worldObj.isRemote || src.getEntity() == null && src.getSourceOfDamage() == null) {
            return;
        }
        boolean ready = anchorReady(p);
        anchorModifier(p, ready);                               // on for this hit (the knockback comes after it) - or off: it pushes
        if (ready && !p.getEntityData().getBoolean(ANCHOR_BLAST)) {
            ArmorGasSC.drainFraction(ArmorGasSC.wornSet(p), Gas.HEAVY_WATER, ArmorFeature.SING_D2O_ANCHOR);
        }
    }

    /** Set while anchorExplosion does the blast's damage itself (already paid for). */
    private static final String ANCHOR_BLAST = "scAnchorBlast";

    /**
     * An explosion about to hurt and push the wearer (ExplosionEvent.Detonate): П3 takes the player
     * off the list - no push, no knockback packet - and does the blast's damage itself, the way the
     * explosion counts it. @return whether it did (the caller removes the player from the list)
     */
    public static boolean anchorExplosion(EntityPlayer p, net.minecraft.world.Explosion ex) {
        if (p.worldObj.isRemote || !anchorReady(p)) {
            return false;
        }
        double size = ex.explosionSize;                         // already doubled when Detonate fires
        double rel = p.getDistance(ex.explosionX, ex.explosionY, ex.explosionZ) / size;
        if (rel > 1.0 || size <= 0) {
            return false;
        }
        ArmorGasSC.drainFraction(ArmorGasSC.wornSet(p), Gas.HEAVY_WATER, ArmorFeature.SING_D2O_ANCHOR);
        double density = p.worldObj.getBlockDensity(Vec3.createVectorHelper(ex.explosionX, ex.explosionY, ex.explosionZ), p.boundingBox);
        double d = (1.0 - rel) * density;
        anchorModifier(p, true);
        p.getEntityData().setBoolean(ANCHOR_BLAST, true);
        try {
            p.attackEntityFrom(net.minecraft.util.DamageSource.setExplosionSource(ex), (int) ((d * d + d) / 2.0 * 8.0 * size + 1.0));
        } finally {
            p.getEntityData().removeTag(ANCHOR_BLAST);
        }
        return true;
    }

    // ------------------------------------------------------------------ П7 stabilizer

    /**
     * Every tick, both sides. Cobwebs and soul sand slow the wearer no more: the web's slow-down for
     * the next move is cleared, soul sand's x0.4 per block undone (the client, which moves its
     * player). The server notes when it happened - argon is paid for those seconds (singularSecond).
     */
    private static void stabilizer(EntityPlayer p) {
        if (!active(p, ArmorFeature.STABILIZER)) {
            return;
        }
        AxisAlignedBB bb = p.boundingBox.contract(0.001, 0.001, 0.001);
        boolean web = p.worldObj.isMaterialInBB(bb, Material.web);
        int soul = 0;
        for (int x = MathHelper.floor_double(bb.minX); x <= MathHelper.floor_double(bb.maxX); x++) {
            for (int y = MathHelper.floor_double(bb.minY); y <= MathHelper.floor_double(bb.maxY); y++) {
                for (int z = MathHelper.floor_double(bb.minZ); z <= MathHelper.floor_double(bb.maxZ); z++) {
                    soul += p.worldObj.getBlock(x, y, z) == net.minecraft.init.Blocks.soul_sand ? 1 : 0;
                }
            }
        }
        if (!web && soul == 0) {
            return;
        }
        if (web) {
            try {
                if (inWebField == null) {
                    inWebField = cpw.mods.fml.relauncher.ReflectionHelper.findField(Entity.class, "isInWeb", "field_70134_J");
                }
                inWebField.setBoolean(p, false);
            } catch (Exception e) {
                // another mapping: the web slows as usual
            }
        }
        if (p.worldObj.isRemote && soul > 0 && soul < 8) {
            double undo = Math.pow(0.4, soul);
            p.motionX /= undo;
            p.motionZ /= undo;
        }
        if (!p.worldObj.isRemote) {
            p.getEntityData().setLong(STAB_AT, p.worldObj.getTotalWorldTime());
        }
    }

    // ------------------------------------------------------------------ Б3 antigravity

    /** Б3 holds the fall from this many blocks of free fall on (over a jump, under the first fall damage). */
    public static final float ANTIGRAV_START = 3F;

    /**
     * Every tick, both sides. After ANTIGRAV_START blocks of free fall Б3 takes the fall over until
     * the ground (or water, a ladder, flying): the client caps the fall at ANTIGRAV_FALL_SPEED, the
     * server keeps the fall distance at 0 - that landing does no damage. Sneaking lets the wearer
     * fall freely (a dive for the gravity strike).
     */
    private static void antigravity(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        boolean air = !p.onGround && !p.capabilities.isFlying && !p.isInWater() && !p.handleLavaMovement() && !p.isOnLadder()
                && p.ridingEntity == null;
        if (!air || p.isSneaking() || !active(p, ArmorFeature.ANTIGRAV)) {
            data.removeTag(SLOW_FALL);
            return;
        }
        if (!data.getBoolean(SLOW_FALL)) {
            if (p.fallDistance < ANTIGRAV_START) {
                return;
            }
            data.setBoolean(SLOW_FALL, true);
        }
        p.fallDistance = 0;
        if (p.worldObj.isRemote && p.motionY < ArmorFeature.ANTIGRAV_FALL_SPEED) {
            p.motionY = ArmorFeature.ANTIGRAV_FALL_SPEED;
        }
        if (!p.worldObj.isRemote) {
            data.setLong(SLOW_FALL + "At", p.worldObj.getTotalWorldTime());
        }
    }

    /** Б3 is holding the wearer's fall (server: that landing does no damage). */
    public static boolean slowFalling(EntityPlayer p) {
        return p.getEntityData().getBoolean(SLOW_FALL);
    }

    // ------------------------------------------------------------------ Б1 gravitational strike

    /**
     * Б1 (Singular boots, level 2): the fall damage the suit just took off a landing (`points`) hits
     * every hostile mob within STRIKE_RADIUS - STRIKE_DAMAGE_PER_POINT a point - and throws them back;
     * a point costs SING_H2_STRIKE_PER_POINT hydrogen (as many points as there is hydrogen for) and
     * STRIKE_HEAT_PER_POINT heat. С2: a higher fall gives more points - a harder blow.
     */
    public static void gravityStrike(EntityPlayer p, float points) {
        if (points < 1F || !active(p, ArmorFeature.GRAV_STRIKE)) {
            return;
        }
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        int pts = Math.min((int) points, (int) (ArmorGasSC.amountOf(worn, Gas.HYDROGEN) / ArmorFeature.SING_H2_STRIKE_PER_POINT));
        if (pts <= 0) {
            return;
        }
        ArmorGasSC.drainFraction(worn, Gas.HYDROGEN, pts * ArmorFeature.SING_H2_STRIKE_PER_POINT);
        float damage = pts * ArmorFeature.STRIKE_DAMAGE_PER_POINT;
        double r = ArmorFeature.STRIKE_RADIUS;
        for (net.minecraft.entity.EntityLivingBase e : mobsAround(p, r)) {
            e.attackEntityFrom(net.minecraft.util.DamageSource.causePlayerDamage(p), damage);
            push(p, e, 0.6 + Math.min(20, pts) * 0.04, 0.35);
        }
        addHeat(p, pts * ArmorFeature.STRIKE_HEAT_PER_POINT);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.explode", 0.6F, 0.7F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("largeexplode", p.posX, p.posY, p.posZ, 3, 1.5, 0.1, 1.5, 0);
        }
    }

    /** Living hostile mobs (IMob) within `r` of the player. */
    static List<net.minecraft.entity.EntityLivingBase> mobsAround(EntityPlayer p, double r) {
        List<net.minecraft.entity.EntityLivingBase> out = new java.util.ArrayList<net.minecraft.entity.EntityLivingBase>();
        List list = p.worldObj.getEntitiesWithinAABB(net.minecraft.entity.EntityLivingBase.class, p.boundingBox.expand(r, r, r));
        for (Object o : list) {
            net.minecraft.entity.EntityLivingBase e = (net.minecraft.entity.EntityLivingBase) o;
            if (e != p && e instanceof net.minecraft.entity.monster.IMob && e.isEntityAlive() && p.getDistanceSqToEntity(e) <= r * r) {
                out.add(e);
            }
        }
        return out;
    }

    /** Throws `e` away from the player: `speed` sideways, `up` upwards. */
    static void push(EntityPlayer p, Entity e, double speed, double up) {
        double dx = e.posX - p.posX, dz = e.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.01) {
            dx = 1;
            d = 1;
        }
        e.motionX += dx / d * speed;
        e.motionZ += dz / d * speed;
        e.motionY = Math.max(e.motionY, up);
        e.velocityChanged = true;
    }

    // ------------------------------------------------------------------ Б4 rescue from the void

    /**
     * Server, once a second, the boots' rescue switched on: the place the wearer stands safely on
     * (on the ground, on a solid block, out of water and lava, above y 1) is remembered per dimension.
     */
    private static void rememberSafe(EntityPlayer p) {
        if (!p.onGround || p.posY < 1 || p.isInWater() || p.handleLavaMovement() || p.ridingEntity != null) {
            return;
        }
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.boundingBox.minY - 0.1), z = MathHelper.floor_double(p.posZ);
        Block under = p.worldObj.getBlock(x, y, z);
        if (!under.getMaterial().isSolid() || under.getMaterial() == Material.lava) {
            return;
        }
        NBTTagCompound pos = new NBTTagCompound();
        pos.setDouble("x", p.posX);
        pos.setDouble("y", p.posY);
        pos.setDouble("z", p.posZ);
        p.getEntityData().setTag(SAFE_PREFIX + p.dimension, pos);
    }

    /**
     * Server, every tick under y 0. Б4 (Singular boots): fallen into the void, the wearer is put back
     * on the last safe place of this dimension (none: the world spawn's top block) for SING_HE_VOID_RESCUE
     * helium and VOID_RESCUE_CHARGE of the suit's charge; that landing does no damage; cooldown
     * VOID_RESCUE_COOLDOWN, heat VOID_RESCUE_HEAT. Not while flying (creative under the bedrock).
     */
    private static void voidRescue(EntityPlayer p) {
        if (p.capabilities.isFlying || !(p instanceof EntityPlayerMP) || !active(p, ArmorFeature.VOID_RESCUE)) {
            return;
        }
        String name = "sc.armorfn." + ArmorFeature.VOID_RESCUE.name().toLowerCase(java.util.Locale.ROOT);
        int left = com.sc.util.SingularCooldowns.get(p, ArmorFeature.VOID_RESCUE);
        if (left > 0) {
            cooldownWarn(p, ArmorFeature.VOID_RESCUE, left);
            return;
        }
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        if (ArmorGasSC.amountOf(worn, Gas.HELIUM) < ArmorFeature.SING_HE_VOID_RESCUE
                || !canPaySuitShare(p, ArmorFeature.VOID_RESCUE_CHARGE)) {
            warnArgs(p, "sc.armor.voidrescue.cant", 100, new net.minecraft.util.ChatComponentTranslation(name));
            return;
        }
        double x, y, z;
        NBTTagCompound pos = p.getEntityData().getCompoundTag(SAFE_PREFIX + p.dimension);
        boolean saved = pos.hasKey("y") && p.worldObj.getCollidingBoundingBoxes(p, boxAt(pos.getDouble("x"), pos.getDouble("y"), pos.getDouble("z"))).isEmpty();
        if (saved) {
            x = pos.getDouble("x");
            y = pos.getDouble("y");
            z = pos.getDouble("z");
        } else {
            net.minecraft.util.ChunkCoordinates spawn = p.worldObj.getSpawnPoint();
            x = spawn.posX + 0.5;
            z = spawn.posZ + 0.5;
            y = Math.max(1, p.worldObj.getTopSolidOrLiquidBlock(spawn.posX, spawn.posZ)) + 0.1;
        }
        ArmorGasSC.drainExact(worn, Gas.HELIUM, ArmorFeature.SING_HE_VOID_RESCUE);
        paySuitShare(p, ArmorFeature.VOID_RESCUE_CHARGE);
        if (p.ridingEntity != null) {
            p.mountEntity(null);
        }
        p.motionX = p.motionY = p.motionZ = 0;
        p.fallDistance = 0;
        ((EntityPlayerMP) p).setPositionAndUpdate(x, y, z);
        p.getEntityData().setBoolean(RESCUE_LANDING, true);
        com.sc.util.SingularCooldowns.set(p, ArmorFeature.VOID_RESCUE, ArmorFeature.VOID_RESCUE_COOLDOWN);
        addHeat(p, ArmorFeature.VOID_RESCUE_HEAT);
        p.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 1.0F, 0.6F);
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(saved ? "sc.armor.voidrescue" : "sc.armor.voidrescue.spawn"));
    }

    /** A player-sized box with its feet at (x, y, z). */
    static AxisAlignedBB boxAt(double x, double y, double z) {
        return AxisAlignedBB.getBoundingBox(x - 0.3, y, z - 0.3, x + 0.3, y + 1.8, z + 0.3);
    }

    // ------------------------------------------------------------------ the whole suit's charge, a share at once (Б4; stage 2b: Н3, Н4, К1)

    /** EU a share `frac` of the worn suit's capacity is (all worn pieces of the mod). */
    public static long suitShare(EntityPlayer p, float frac) {
        long cap = 0;
        for (int t = 0; t < 4; t++) {
            cap += ItemArmorSC.capacityOf(piece(p, t));
        }
        return (long) Math.ceil(cap * (double) frac);
    }

    public static boolean canPaySuitShare(EntityPlayer p, float frac) {
        long have = 0;
        for (int t = 0; t < 4; t++) {
            have += ItemArmorSC.chargeOf(piece(p, t));
        }
        return have >= suitShare(p, frac);
    }

    /** Takes `frac` of the suit's capacity from its pieces (chestplate first), all or nothing. @return whether it was paid */
    public static boolean paySuitShare(EntityPlayer p, float frac) {
        if (!canPaySuitShare(p, frac)) {
            return false;
        }
        long need = suitShare(p, frac);
        for (int t : new int[]{1, 0, 2, 3}) {
            ItemStack s = piece(p, t);
            if (s != null && need > 0) {
                need -= ItemArmorSC.discharge(s, (int) Math.min(Integer.MAX_VALUE, need));
            }
        }
        return true;
    }

    // ------------------------------------------------------------------ К9 wither and the void

    /** К9: the wither effect off for SING_O2_WITHER oxygen. @return whether it was taken off */
    public static boolean witherOff(EntityPlayer p) {
        if (p.worldObj.isRemote || !p.isPotionActive(Potion.wither) || !active(p, ArmorFeature.WITHER_VOID)
                || !ArmorGasSC.drainExact(ArmorGasSC.wornSet(p), Gas.OXYGEN, (int) Math.ceil(ArmorFeature.SING_O2_WITHER))) {
            return false;
        }
        p.removePotionEffect(Potion.wither.id);
        return true;
    }

    /** К9 against the damage itself (LivingAttackEvent): a wither hit takes the effect off and does nothing. @return cancel */
    public static boolean witherStops(EntityPlayer p, net.minecraft.util.DamageSource src) {
        return src == net.minecraft.util.DamageSource.wither && witherOff(p);
    }

    /** К9 (LivingHurtEvent): the void hurts the full Singular set half as much. */
    public static float voidDamage(EntityPlayer p, net.minecraft.util.DamageSource src, float amount) {
        if (src != net.minecraft.util.DamageSource.outOfWorld || p.worldObj.isRemote || !active(p, ArmorFeature.WITHER_VOID)) {
            return amount;
        }
        return amount * ArmorFeature.VOID_DAMAGE_MUL;
    }

    // ------------------------------------------------------------------ Ш8 night vision without glare

    /** The light at the wearer's eyes (sky and blocks, 0..15) is at least CLEAR_SIGHT_BRIGHT. Server: posY is the feet. */
    private static boolean brightAtEyes(EntityPlayer p) {
        int x = MathHelper.floor_double(p.posX), y = MathHelper.floor_double(p.posY + 1.62), z = MathHelper.floor_double(p.posZ);
        return y >= 0 && y < 256 && p.worldObj.getBlockLightValue(x, y, z) >= ArmorFeature.CLEAR_SIGHT_BRIGHT;
    }

    // ------------------------------------------------------------------ Н11 heat vent

    /**
     * The suit's heat has just reached 100% (CommonEventHandler, before the chips shut down). Н11
     * (Singular chestplate, level 3): a wave throws the mobs within HEAT_VENT_RADIUS back and the
     * caller takes HEAT_VENT_SHARE of the heat off instead of the overheat - for SING_AR_HEAT_VENT
     * argon, cooldown HEAT_VENT_COOLDOWN. On cooldown or without the argon: the usual overheat.
     * @return whether it vented
     */
    public static boolean heatVent(EntityPlayer p) {
        if (p.worldObj.isRemote || !active(p, ArmorFeature.HEAT_VENT) || !com.sc.util.SingularCooldowns.ready(p, ArmorFeature.HEAT_VENT)
                || !ArmorGasSC.drainExact(ArmorGasSC.wornSet(p), Gas.ARGON, ArmorFeature.SING_AR_HEAT_VENT)) {
            return false;
        }
        for (net.minecraft.entity.EntityLivingBase e : mobsAround(p, ArmorFeature.HEAT_VENT_RADIUS)) {
            push(p, e, 1.2, 0.45);
            e.setFire(3);
        }
        com.sc.util.SingularCooldowns.set(p, ArmorFeature.HEAT_VENT, ArmorFeature.HEAT_VENT_COOLDOWN);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.fizz", 1.0F, 0.5F);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("flame", p.posX, p.posY + 1, p.posZ, 40, 1.5, 0.6, 1.5, 0.15);
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("cloud", p.posX, p.posY + 1, p.posZ, 20, 1.0, 0.6, 1.0, 0.1);
        }
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.armor.heatvent"));
        return true;
    }

    /** Pure: the heat left after a vent. */
    public static int ventedHeat(int heat) {
        return heat - Math.round(heat * ArmorFeature.HEAT_VENT_SHARE);
    }

    // ------------------------------------------------------------------ Н2 event horizon

    /** Server, every tick: projectiles coming at the wearer within 3 blocks are swallowed (helium and heat each). */
    private static void horizonScan(EntityPlayer p) {
        double r = 3;
        List list = p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, r, r));
        for (Object o : list) {
            Entity e = (Entity) o;
            if (!e.isDead && incomingProjectile(p, e) && !swallow(p, e)) {
                return;                                     // out of helium
            }
        }
    }

    /** One projectile into the horizon: SING_HE_PER_PROJECTILE helium, the function's heat. @return whether it was swallowed */
    private static boolean swallow(EntityPlayer p, Entity e) {
        if (!ArmorGasSC.drainExact(ArmorGasSC.wornSet(p), Gas.HELIUM, ArmorFeature.SING_HE_PER_PROJECTILE)) {
            return false;
        }
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("portal", e.posX, e.posY, e.posZ, 12, 0.2, 0.2, 0.2, 0.5);
        }
        e.setDead();
        addHeat(p, ArmorFeature.EVENT_HORIZON.heat);
        p.worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "mob.endermen.portal", 0.4F, 1.8F);
        return true;
    }

    /** A projectile's hit (LivingAttackEvent) the scan missed: Н2 swallows it. @return cancel the attack */
    public static boolean horizonStops(EntityPlayer p, net.minecraft.util.DamageSource src) {
        if (p.worldObj.isRemote || !src.isProjectile() || !active(p, ArmorFeature.EVENT_HORIZON)) {
            return false;
        }
        Entity proj = src.getSourceOfDamage();
        if (proj == null || proj == p) {
            return false;
        }
        return swallow(p, proj);
    }

    /**
     * Any other damage (LivingHurtEvent; not the void, not hunger): Н2 turns HORIZON_SHARE of it into
     * EU for the suit (HORIZON_EU_PER_POINT a point) for SING_HE_PER_DAMAGE_POINT helium per point of
     * the hit. @return the damage left
     */
    public static float horizonHurt(EntityPlayer p, net.minecraft.util.DamageSource src, float amount) {
        if (p.worldObj.isRemote || amount <= 0 || src == net.minecraft.util.DamageSource.outOfWorld
                || src == net.minecraft.util.DamageSource.starve || !active(p, ArmorFeature.EVENT_HORIZON)) {
            return amount;
        }
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        float he = amount * ArmorFeature.SING_HE_PER_DAMAGE_POINT;
        if (ArmorGasSC.amountOf(worn, Gas.HELIUM) < (int) Math.ceil(he)) {
            return amount;
        }
        ArmorGasSC.drainFraction(worn, Gas.HELIUM, he);
        float taken = amount * horizonShare(SingularPowersSC.singularBoost(p));   // К1: 60% while boosted
        SingularProgressSC.horizonAbsorbed(p, taken);                            // ОЧ3, the task "absorb 300 with Н2"
        chargeSuit(p, (int) Math.min(Integer.MAX_VALUE, Math.round(taken * (double) ArmorFeature.HORIZON_EU_PER_POINT)));
        return amount - taken;
    }

    // ------------------------------------------------------------------ П1 phase dash

    /** A place the player's body can stand (feet at x, y, z). */
    public interface SpaceCheck {
        boolean free(double x, double y, double z);
    }

    /** The phase dash's path is checked every this many blocks. */
    public static final double PHASE_STEP = 0.25;

    /**
     * Pure: how far (blocks) along the direction (lx, ly, lz) a body at (x, y, z) can go - the last
     * step before the first place it doesn't fit, up to `range`. Never through a wall: the whole
     * path is walked, so nothing beyond the first obstacle is reached.
     */
    public static double phaseDistance(SpaceCheck c, double x, double y, double z, double lx, double ly, double lz, double range) {
        double len = Math.sqrt(lx * lx + ly * ly + lz * lz);
        if (len < 1e-6) {
            return 0;
        }
        lx /= len;
        ly /= len;
        lz /= len;
        double best = 0;
        for (int i = 1; i * PHASE_STEP <= range + 1e-9; i++) {
            double d = i * PHASE_STEP;
            if (!c.free(x + lx * d, y + ly * d, z + lz * d)) {
                break;
            }
            best = d;
        }
        return best;
    }

    /**
     * П1 (Singular leggings, level 2, on its key): the wearer jumps through space up to
     * PHASE_DASH_RANGE blocks along the look - on the ground looking down, along the ground - and
     * stops before the first wall (no lava, the body has to fit). SING_H2_PHASE hydrogen +
     * PHASE_DASH_EU, cooldown PHASE_DASH_COOLDOWN (С3: none while time is slowed), heat PHASE_DASH.heat.
     */
    public static void phaseDash(final EntityPlayerMP p) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (data.hasKey(PHASE_AT) && now - data.getLong(PHASE_AT) < 4 && now >= data.getLong(PHASE_AT)) {
            return;                                             // one press, two messages
        }
        if (!active(p, ArmorFeature.PHASE_DASH)) {
            return;
        }
        boolean slow = timeSlowActive(p);
        int left = com.sc.util.SingularCooldowns.get(p, ArmorFeature.PHASE_DASH);
        if (left > 0 && !slow) {
            cooldownWarn(p, ArmorFeature.PHASE_DASH, left);
            return;
        }
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        int eu = (int) Math.ceil(ArmorFeature.PHASE_DASH_EU * costMul(p) * SingularPowersSC.costMul(p) * com.sc.util.SingularLevel.euMul(piece(p, 2)));
        int h2 = SingularPowersSC.scaled(p, ArmorFeature.SING_H2_PHASE);                    // К1: x2
        if (ArmorGasSC.amountOf(worn, Gas.HYDROGEN) < h2 || ItemArmorSC.chargeOf(piece(p, 2)) < eu) {
            warn(p, "sc.armor.phase.cant", 40);
            return;
        }
        Vec3 look = p.getLookVec();
        double ly = p.onGround && look.yCoord < 0 ? 0 : look.yCoord;
        SpaceCheck space = new SpaceCheck() {
            @Override
            public boolean free(double x, double y, double z) {
                AxisAlignedBB bb = boxAt(x, y, z);
                return y > 0 && y < 255 && p.worldObj.blockExists(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z))
                        && p.worldObj.getCollidingBoundingBoxes(p, bb).isEmpty() && !p.worldObj.isMaterialInBB(bb, Material.lava);
            }
        };
        double range = ArmorFeature.PHASE_DASH_RANGE * SingularPowersSC.rangeMul(SingularPowersSC.singularBoost(p));   // К1: x1.5
        double dist = phaseDistance(space, p.posX, p.boundingBox.minY, p.posZ, look.xCoord, ly, look.zCoord, range);
        if (dist < 1.0) {
            warn(p, "sc.armor.phase.blocked", 20);
            return;
        }
        double len = Math.sqrt(look.xCoord * look.xCoord + ly * ly + look.zCoord * look.zCoord);
        double x = p.posX + look.xCoord / len * dist, y = p.boundingBox.minY + ly / len * dist, z = p.posZ + look.zCoord / len * dist;
        ItemArmorSC.pay(piece(p, 2), eu);
        ArmorGasSC.drainExact(worn, Gas.HYDROGEN, h2);
        data.setLong(PHASE_AT, now);
        if (p.worldObj instanceof net.minecraft.world.WorldServer) {
            ((net.minecraft.world.WorldServer) p.worldObj).func_147487_a("portal", p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.8, 0.3, 0.6);
        }
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 0.8F, 1.4F);
        if (p.ridingEntity != null) {
            p.mountEntity(null);
        }
        p.fallDistance = 0;
        p.setPositionAndUpdate(x, y, z);
        p.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 0.8F, 1.6F);
        if (!slow) {
            com.sc.util.SingularCooldowns.set(p, ArmorFeature.PHASE_DASH, ArmorFeature.PHASE_DASH_COOLDOWN);
        }
        addHeat(p, ArmorFeature.PHASE_DASH.heat);
        SingularProgressSC.dashed(p);                       // ОЧ5 and the task "50 phase dashes"
    }

    // ------------------------------------------------------------------ cooldown / chat helpers

    /** "<function>: cooldown N s" in chat, at most every 2 s. */
    static void cooldownWarn(EntityPlayer p, ArmorFeature f, int ticksLeft) {
        warnArgs(p, "sc.armor.cooldown", 40, new net.minecraft.util.ChatComponentTranslation("sc.armorfn." + f.name().toLowerCase(java.util.Locale.ROOT)),
                String.valueOf((ticksLeft + 19) / 20));
    }

    /** warn() with arguments (the key's throttle as warn's). */
    static void warnArgs(EntityPlayer p, String key, int cooldown, Object... args) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        long at = data.getLong(WARN_PREFIX + key);
        if (data.hasKey(WARN_PREFIX + key) && now - at < cooldown && now >= at) {
            return;
        }
        data.setLong(WARN_PREFIX + key, now);
        p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation(key, args));
    }

    // ------------------------------------------------------------------ once a second, server: what the Singular functions spend

    /** The Singular functions' second: the magnet, the stabilizer, the slow fall, the safe place. @return heat */
    private static int singularSecond(EntityPlayer p) {
        int heat = 0;
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        long now = p.worldObj.getTotalWorldTime();
        NBTTagCompound data = p.getEntityData();
        float boostCost = SingularPowersSC.costMul(p);                     // К1: x2 gas while boosted
        if (active(p, ArmorFeature.MAGNET) && pay(p, ArmorFeature.MAGNET, ArmorFeature.MAGNET.euPerSecond)) {
            ArmorGasSC.drainFraction(worn, Gas.HELIUM, ArmorFeature.SING_HE_MAGNET_PER_MIN / 60F * boostCost);
            heat += ArmorFeature.MAGNET.heat;
        }
        if (data.hasKey(STAB_AT) && now - data.getLong(STAB_AT) < 20 && now >= data.getLong(STAB_AT)) {
            ArmorGasSC.drainFraction(worn, Gas.ARGON, ArmorFeature.SING_AR_STABILIZER_PER_SECOND * boostCost);   // only while it worked
        }
        String slowAt = SLOW_FALL + "At";
        if (data.hasKey(slowAt) && now - data.getLong(slowAt) < 20 && now >= data.getLong(slowAt)
                && pay(p, ArmorFeature.ANTIGRAV, ArmorFeature.ANTIGRAV.euPerSecond)) {
            ArmorGasSC.drainFraction(worn, Gas.HYDROGEN, ArmorFeature.SING_H2_SLOWFALL_PER_SECOND * boostCost);
            heat += ArmorFeature.ANTIGRAV.heat;
        }
        if (ItemArmorSC.isEnabled(piece(p, 3), ArmorFeature.VOID_RESCUE)) {
            rememberSafe(p);
        }
        heat += SingularSensesSC.second(p);                 // stage 2b: Ш1 scanner, Ш2 threat sense, К2 resonance
        SingularPowersSC.second(p);                         // К1: the boost / weakness turning over
        SingularProgressSC.second(p);                       // stage 3: level points, task counters, Р4 sync
        return heat;
    }

    /** Pure: Н2's share of damage turned into EU at the К1 multiplier `boost` (x2 boosted: 60%, weakened: 15%). */
    public static float horizonShare(float boost) {
        return Math.min(0.6F, ArmorFeature.HORIZON_SHARE * boost);
    }
}
