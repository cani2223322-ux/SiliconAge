package com.sc.util;

import com.sc.item.ItemArmorSC;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.fluids.Fluid;
import net.minecraftforge.fluids.FluidRegistry;

/**
 * The energy suits' life support: gases kept in the pieces' own tanks (docs/plan-armor-gases.md).
 * Each gas lives in fixed pieces; its amount is the piece's NBT "Gas_<key>" (mB). The suit's total of
 * a gas is the sum over the worn pieces. Liquid helium: the chestplate holds the loop (tank, pump,
 * heat exchanger) - the other pieces only add radiators (+cooling, +a little volume), and without a
 * chestplate there is no helium cooling at all.
 *
 * Shared API for the armour logic, the HUD / K screen and the Armour Service Station.
 */
public final class ArmorGasSC {

    /** Piece slots as ItemArmor.armorType: 0 helmet, 1 chestplate, 2 leggings, 3 boots. */
    public static final int HELMET = 0, CHEST = 1, LEGS = 2, BOOTS = 3;

    public enum Gas {
        HELIUM("liquidhelium", 0x8FE3FF),
        OXYGEN("oxygen", 0xF2F4F8),
        HYDROGEN("hydrogen", 0xFF9A3C),
        ARGON("argon", 0xB27CFF),
        KRYPTON("krypton", 0xFFE14D),
        HEAVY_WATER("heavywater", 0x3C6CFF),
        DEUTERIUM("deuterium", 0x5CE07A);

        /** The mod's fluid name (ModFluids). */
        public final String fluid;
        /** HUD / screen colour. */
        public final int color;

        Gas(String fluid, int color) {
            this.fluid = fluid;
            this.color = color;
        }

        public String key() {
            return name().toLowerCase(java.util.Locale.ROOT);
        }

        public Fluid fluidOf() {
            return FluidRegistry.getFluid(fluid);
        }

        /** The gas a fluid is, or null. */
        public static Gas of(Fluid f) {
            if (f == null) {
                return null;
            }
            for (Gas g : values()) {
                if (g.fluid.equals(f.getName())) {
                    return g;
                }
            }
            return null;
        }
    }

    /**
     * Tank sizes, mB: [gas][suit NANO, QUANTUM, EXO][piece HELMET, CHEST, LEGS, BOOTS]. 0 - no tank.
     * Helium on the other pieces is the radiators' volume added to the chestplate's loop.
     */
    private static final int[][][] CAP = {
        /* HELIUM      */ {{0, 2000, 0, 0}, {1000, 6000, 1000, 1000}, {2000, 12000, 2000, 2000}},
        /* OXYGEN      */ {{2000, 0, 0, 0}, {4000, 0, 0, 0}, {8000, 0, 0, 0}},
        /* HYDROGEN    */ {{0, 0, 0, 0}, {0, 4000, 0, 2000}, {0, 8000, 0, 4000}},
        /* ARGON       */ {{0, 0, 0, 0}, {0, 1000, 0, 0}, {0, 2000, 0, 0}},
        /* KRYPTON     */ {{0, 0, 0, 0}, {1000, 0, 0, 0}, {1000, 0, 0, 0}},
        /* HEAVY_WATER */ {{0, 0, 0, 0}, {0, 0, 4000, 0}, {0, 0, 8000, 0}},
        /* DEUTERIUM   */ {{0, 0, 0, 0}, {0, 0, 0, 0}, {0, 4000, 0, 0}},
    };

    /** Helium cooling: each worn radiator piece (helmet, leggings, boots) adds this much cooling. */
    public static final float RADIATOR_BONUS = 0.15F;

    private ArmorGasSC() {
    }

    private static String nbtKey(Gas g) {
        return "Gas_" + g.key();
    }

    /** Base tank of `g` in this piece (no chips counted), mB. */
    public static int baseCapacity(ItemStack piece, Gas g) {
        if (piece == null || !(piece.getItem() instanceof ItemArmorSC)) {
            return 0;
        }
        ItemArmorSC a = (ItemArmorSC) piece.getItem();
        int t = a.armorType;
        if (t < 0 || t > 3) {
            return 0;
        }
        return CAP[g.ordinal()][a.getSuit().ordinal()][t];
    }

    /** The piece's tank of `g`, mB (the armour logic may raise it with chips through capacityBonus). */
    public static int capacity(ItemStack piece, Gas g) {
        int base = baseCapacity(piece, g);
        return base <= 0 ? 0 : (int) Math.min(Integer.MAX_VALUE, (long) base * (100 + capacityBonusPercent(piece)) / 100L);
    }

    /** Extra tank volume in percent from the piece's chips (the Cryo Tank chip): set by the armour logic, read here. */
    public static int capacityBonusPercent(ItemStack piece) {
        return piece != null && piece.hasTagCompound() ? piece.getTagCompound().getInteger("GasCapBonus") : 0;
    }

    public static int amount(ItemStack piece, Gas g) {
        if (piece == null || !piece.hasTagCompound()) {
            return 0;
        }
        return Math.max(0, Math.min(capacity(piece, g), piece.getTagCompound().getInteger(nbtKey(g))));
    }

    public static void setAmount(ItemStack piece, Gas g, int mb) {
        if (piece == null || capacity(piece, g) <= 0) {
            return;
        }
        if (!piece.hasTagCompound()) {
            piece.setTagCompound(new NBTTagCompound());
        }
        piece.getTagCompound().setInteger(nbtKey(g), Math.max(0, Math.min(capacity(piece, g), mb)));
    }

    /** Puts up to `mb` in; @return how much went in. */
    public static int fill(ItemStack piece, Gas g, int mb, boolean simulate) {
        int room = capacity(piece, g) - amount(piece, g);
        int put = Math.max(0, Math.min(room, mb));
        if (!simulate && put > 0) {
            setAmount(piece, g, amount(piece, g) + put);
        }
        return put;
    }

    /** Takes up to `mb` out; @return how much came out. */
    public static int drain(ItemStack piece, Gas g, int mb, boolean simulate) {
        int took = Math.max(0, Math.min(amount(piece, g), mb));
        if (!simulate && took > 0) {
            setAmount(piece, g, amount(piece, g) - took);
        }
        return took;
    }

    // ------------------------------------------------------------------ the worn suit

    /** The worn piece in slot `type` (0 helmet ... 3 boots), or null. */
    public static ItemStack worn(EntityPlayer p, int type) {
        ItemStack s = p == null ? null : p.inventory.armorInventory[3 - type];
        return s != null && s.getItem() instanceof ItemArmorSC ? s : null;
    }

    /** All of `g` in the worn suit, mB. Helium counts only with a chestplate on (no loop without it). */
    public static int suitAmount(EntityPlayer p, Gas g) {
        if (g == Gas.HELIUM && worn(p, CHEST) == null) {
            return 0;
        }
        int sum = 0;
        for (int t = 0; t < 4; t++) {
            sum += amount(worn(p, t), g);
        }
        return sum;
    }

    public static int suitCapacity(EntityPlayer p, Gas g) {
        if (g == Gas.HELIUM && worn(p, CHEST) == null) {
            return 0;
        }
        int sum = 0;
        for (int t = 0; t < 4; t++) {
            sum += capacity(worn(p, t), g);
        }
        return sum;
    }

    /** Takes `mb` of `g` from the worn suit (chestplate first for helium, then the radiators). @return taken. */
    public static int suitDrain(EntityPlayer p, Gas g, int mb, boolean simulate) {
        if (g == Gas.HELIUM && worn(p, CHEST) == null) {
            return 0;
        }
        int left = mb;
        int[] order = {CHEST, HELMET, LEGS, BOOTS};
        for (int t : order) {
            if (left <= 0) {
                break;
            }
            left -= drain(worn(p, t), g, left, simulate);
        }
        return mb - left;
    }

    /** Puts `mb` of `g` into the worn suit's tanks (helium: none without a chestplate). @return put. */
    public static int suitFill(EntityPlayer p, Gas g, int mb, boolean simulate) {
        if (g == Gas.HELIUM && worn(p, CHEST) == null) {
            return 0;
        }
        int left = mb;
        int[] order = {CHEST, HELMET, LEGS, BOOTS};
        for (int t : order) {
            if (left <= 0) {
                break;
            }
            left -= fill(worn(p, t), g, left, simulate);
        }
        return mb - left;
    }

    /** Helium cooling strength: 0 without a chestplate, else 1 + RADIATOR_BONUS per worn helmet / leggings / boots. */
    public static float coolingFactor(EntityPlayer p) {
        if (worn(p, CHEST) == null) {
            return 0F;
        }
        float f = 1F;
        for (int t : new int[]{HELMET, LEGS, BOOTS}) {
            if (worn(p, t) != null) {
                f += RADIATOR_BONUS;
            }
        }
        return f;
    }

    // ------------------------------------------------------------------ rates (the armour logic, read by the HUD too)

    /** Liquid helium: heat units one mB takes away (x coolingFactor x the Cryo Loop chip). */
    public static final float HELIUM_HEAT_PER_MB = 20F;
    /** Liquid helium: heat the pump can move per second, by the chestplate's suit (Nano, Quantum, Exo), x coolingFactor. */
    public static final int[] HELIUM_PUMP = {4, 8, 16};
    /** Liquid helium boiling away in the worn chestplate's loop, mB a minute (always, even at rest). */
    public static final float HELIUM_BOIL_PER_MIN = 1F;
    /** The fusion cell running: the loop burns helium this many times as fast. */
    public static final float FUSION_HELIUM_MUL = 2F;
    /** Oxygen: mB a second while breathing on it (under water, inside a block, in space). */
    public static final int OXYGEN_PER_SECOND = 1;
    /** The oxygen regenerator chip (Exo, under water): mB a second per chip tier, and the EU a second it costs. */
    public static final int OXYGEN_REGEN_PER_TIER = 1, OXYGEN_REGEN_BASE = 1, OXYGEN_REGEN_EU = 100;
    /** Hydrogen (engine boost): flight mB a second, a dash, an air jump, a soft landing. */
    public static final int H2_FLIGHT_PER_SECOND = 2, H2_DASH = 50, H2_AIR_JUMP = 20, H2_SOFT_LANDING = 30;
    /** Argon: putting the wearer out, mB a second while in lava (half the damage), Exo fire proofing: mB a second, mB per fire block put out round the wearer. */
    public static final int ARGON_EXTINGUISH = 100, ARGON_LAVA_PER_SECOND = 20, ARGON_EXO_PER_SECOND = 5, ARGON_PER_FIRE = 2;
    /** Argon, Exo fire proofing: the share of its EU it still costs; reach of the fire put out round the wearer. */
    public static final float ARGON_EXO_EU_MUL = 0.5F;
    public static final int ARGON_FIRE_RADIUS = 2;
    /** Krypton: the searchlight's / the boosted ore scanner's use, mB a minute; the searchlight's reach; the Exo scanner's reach multiplier. */
    public static final float KRYPTON_PER_MIN = 1F;
    public static final int SEARCHLIGHT_RANGE = 12;
    public static final float KRYPTON_SCAN_MUL = 1.5F;
    /** Heavy water: radiation protection added (percent points), mB a minute per radiation level reaching the wearer. */
    public static final int HEAVY_WATER_PCT = 20;
    public static final float HEAVY_WATER_PER_LEVEL_MIN = 1F;
    /** Deuterium fusion cell: EU a tick into the suit, deuterium mB a tick. */
    public static final int FUSION_EU_PER_TICK = 256;
    public static final float FUSION_D_PER_TICK = 0.1F;
    /** The Cryo Tank chip: extra tank volume, percent, per chip tier I / II / III. */
    public static final int[] CRYO_TANK_PCT = {50, 75, 100};
    /** The Cryo Loop chip: helium cools this much better (percent) per tier I / II / III. */
    public static final int[] CRYO_LOOP_PCT = {50, 75, 100};
    /** The Recuperator chip: the share of the boiled-off helium that comes back (percent) per tier. */
    public static final int[] RECUPERATOR_PCT = {30, 45, 60};
    /** First login after the update: worn pieces get this share of their helium and oxygen once. */
    public static final int STARTER_PCT = 25;
    /**
     * The strict rules (Quantum / Exo: every function on its own gas, ArmorFeature.gas()): what each
     * spends while it works. Hydrogen: flight without the boost mB a second, a jump, sprinting mB a
     * second, a fall softened; oxygen per effect cleansed; argon: fire proofing mB a second while
     * burning, walking on a liquid mB a second. Krypton (night vision, scanner, thermal) is KRYPTON_PER_MIN each.
     */
    public static final float H2_FLIGHT_BASE_PER_SECOND = 1F, H2_JUMP = 0.2F, H2_SPEED_PER_SECOND = 0.5F;
    public static final int H2_FALL_DAMPING = 10, O2_CLEANSE = 5, ARGON_FIRE_PROOF_PER_SECOND = 2;
    public static final float ARGON_WATER_WALK_PER_SECOND = 0.5F;

    /**
     * Old worlds, the strict rules: each Quantum / Exo piece of the set not given it yet gets
     * STARTER_PCT of its hydrogen tank, once (piece NBT "GasStartH2SC"). @return how many pieces got some
     */
    public static int giveHydrogenStarter(ItemStack[] w) {
        int given = 0;
        for (int t = 0; t < 4; t++) {
            ItemStack s = at(w, t);
            if (s == null || ((ItemArmorSC) s.getItem()).getSuit() == com.sc.util.ArmorSuit.NANO
                    || (s.hasTagCompound() && s.getTagCompound().getBoolean("GasStartH2SC"))) {
                continue;
            }
            if (!s.hasTagCompound()) {
                s.setTagCompound(new NBTTagCompound());
            }
            s.getTagCompound().setBoolean("GasStartH2SC", true);
            int cap = capacity(s, Gas.HYDROGEN);
            if (cap > 0 && fill(s, Gas.HYDROGEN, cap * STARTER_PCT / 100, false) > 0) {
                given++;
            }
        }
        return given;
    }

    // ------------------------------------------------------------------ a set of pieces (index = armorType), no player needed

    /** The worn suit as an array indexed HELMET..BOOTS (null where nothing of the mod is worn). */
    public static ItemStack[] wornSet(EntityPlayer p) {
        ItemStack[] w = new ItemStack[4];
        for (int t = 0; t < 4; t++) {
            w[t] = worn(p, t);
        }
        return w;
    }

    private static ItemStack at(ItemStack[] w, int t) {
        if (w == null || t < 0 || t >= w.length) {
            return null;
        }
        ItemStack s = w[t];
        return s != null && s.getItem() instanceof ItemArmorSC ? s : null;
    }

    /** Whether the set has any of the mod's armour pieces in it. */
    public static boolean anyPiece(ItemStack[] w) {
        for (int t = 0; t < 4; t++) {
            if (at(w, t) != null) {
                return true;
            }
        }
        return false;
    }

    /** suitAmount for a set of pieces. */
    public static int amountOf(ItemStack[] w, Gas g) {
        if (g == Gas.HELIUM && at(w, CHEST) == null) {
            return 0;
        }
        int sum = 0;
        for (int t = 0; t < 4; t++) {
            sum += amount(at(w, t), g);
        }
        return sum;
    }

    /** suitCapacity for a set of pieces. */
    public static int capacityOf(ItemStack[] w, Gas g) {
        if (g == Gas.HELIUM && at(w, CHEST) == null) {
            return 0;
        }
        int sum = 0;
        for (int t = 0; t < 4; t++) {
            sum += capacity(at(w, t), g);
        }
        return sum;
    }

    /** suitDrain for a set of pieces. */
    public static int drainOf(ItemStack[] w, Gas g, int mb, boolean simulate) {
        if (g == Gas.HELIUM && at(w, CHEST) == null) {
            return 0;
        }
        int left = mb;
        for (int t : new int[]{CHEST, HELMET, LEGS, BOOTS}) {
            if (left <= 0) {
                break;
            }
            left -= drain(at(w, t), g, left, simulate);
        }
        return mb - left;
    }

    /** suitFill for a set of pieces. */
    public static int fillOf(ItemStack[] w, Gas g, int mb, boolean simulate) {
        if (g == Gas.HELIUM && at(w, CHEST) == null) {
            return 0;
        }
        int left = mb;
        for (int t : new int[]{CHEST, HELMET, LEGS, BOOTS}) {
            if (left <= 0) {
                break;
            }
            left -= fill(at(w, t), g, left, simulate);
        }
        return mb - left;
    }

    /** Takes all of `mb` or nothing. */
    public static boolean drainExact(ItemStack[] w, Gas g, int mb) {
        if (mb <= 0) {
            return true;
        }
        if (drainOf(w, g, mb, true) < mb) {
            return false;
        }
        drainOf(w, g, mb, false);
        return true;
    }

    /** coolingFactor for a set of pieces. */
    public static float coolingFactorOf(ItemStack[] w) {
        if (at(w, CHEST) == null) {
            return 0F;
        }
        float f = 1F;
        for (int t : new int[]{HELMET, LEGS, BOOTS}) {
            if (at(w, t) != null) {
                f += RADIATOR_BONUS;
            }
        }
        return f;
    }

    /**
     * Spends a fraction of a mB: the part below one whole mB is kept in the gas's home piece (NBT
     * "GasFrac_<key>") until it adds up. @return whole mB taken now (0 also when the suit has none - check amountOf first)
     */
    public static int drainFraction(ItemStack[] w, Gas g, float mb) {
        ItemStack home = null;
        for (int t : new int[]{CHEST, HELMET, LEGS, BOOTS}) {
            if (baseCapacity(at(w, t), g) > 0) {
                home = at(w, t);
                break;
            }
        }
        if (home == null || mb <= 0 || (g == Gas.HELIUM && at(w, CHEST) == null)) {
            return 0;
        }
        if (!home.hasTagCompound()) {
            home.setTagCompound(new NBTTagCompound());
        }
        String key = "GasFrac_" + g.key();
        float frac = home.getTagCompound().getFloat(key) + mb;
        int whole = (int) frac;
        int took = whole > 0 ? drainOf(w, g, whole, false) : 0;
        home.getTagCompound().setFloat(key, amountOf(w, g) > 0 ? frac - whole : 0F);
        return took;
    }

    /** The tier (1-3) of a chip installed in this chestplate, 0 when there is none. */
    public static int chipTier(ItemStack chest, ChipType type) {
        if (chest == null || !chest.hasTagCompound() || !chest.getTagCompound().getCompoundTag("ChipsSC").hasKey(type.name())) {
            return 0;
        }
        return Math.max(1, Math.min(3, chest.getTagCompound().getCompoundTag("ChipsSC").getInteger(type.name())));
    }

    /** Extra tank volume (%) the Cryo Tank chip in this set's chestplate gives every worn piece. */
    public static int cryoTankBonus(ItemStack[] w) {
        int tier = chipTier(at(w, CHEST), ChipType.CRYO_TANK);
        return tier > 0 ? CRYO_TANK_PCT[tier - 1] : 0;
    }

    /**
     * Sets every piece's "GasCapBonus" to what the Cryo Tank chip gives (0 without it). The stored
     * gas is never rewritten here: a tank that shrank (the chip out, or the chestplate off) just
     * shows and gives no more than its new size (amount / drain / fill all cap at capacity), and the
     * extra is back with the chip - unless the piece was used meanwhile (setAmount writes at most
     * the capacity). @return whether anything changed
     */
    public static boolean applyCapacityBonus(ItemStack[] w) {
        int want = cryoTankBonus(w);
        boolean changed = false;
        for (int t = 0; t < 4; t++) {
            ItemStack s = at(w, t);
            if (s == null || capacityBonusPercent(s) == want) {
                continue;
            }
            if (!s.hasTagCompound()) {
                s.setTagCompound(new NBTTagCompound());
            }
            s.getTagCompound().setInteger("GasCapBonus", want);
            changed = true;
        }
        return changed;
    }

    /**
     * Old worlds: each piece of the set not given its start yet gets STARTER_PCT of its helium and
     * oxygen tanks, once (piece NBT "GasStartSC"). @return how many pieces got some
     */
    public static int giveStarter(ItemStack[] w) {
        int given = 0;
        for (int t = 0; t < 4; t++) {
            ItemStack s = at(w, t);
            if (s == null || (s.hasTagCompound() && s.getTagCompound().getBoolean("GasStartSC"))) {
                continue;
            }
            if (!s.hasTagCompound()) {
                s.setTagCompound(new NBTTagCompound());
            }
            s.getTagCompound().setBoolean("GasStartSC", true);
            boolean any = false;
            for (Gas g : new Gas[]{Gas.HELIUM, Gas.OXYGEN}) {
                int cap = capacity(s, g);
                if (cap > 0) {
                    any |= fill(s, g, cap * STARTER_PCT / 100, false) > 0;
                }
            }
            given += any ? 1 : 0;
        }
        return given;
    }

    /**
     * Liquid helium takes the heat the suit couldn't shed itself: up to the pump's rate
     * (HELIUM_PUMP x coolingFactor), HELIUM_HEAT_PER_MB x coolingFactor (x the Cryo Loop) heat per mB,
     * the Recuperator giving part of it back, the fusion cell doubling the use. Without a
     * chestplate or helium nothing happens. @return the heat taken away
     */
    public static int heliumCool(ItemStack[] w, int excessHeat, boolean fusion) {
        ItemStack chest = at(w, CHEST);
        if (chest == null || excessHeat <= 0 || amountOf(w, Gas.HELIUM) <= 0) {
            return 0;
        }
        float cf = coolingFactorOf(w);
        int suit = ((ItemArmorSC) chest.getItem()).getSuit().ordinal();
        int removed = Math.min(excessHeat, (int) (HELIUM_PUMP[suit] * cf));
        int loop = chipTier(chest, ChipType.CRYO_LOOP);
        float perMb = HELIUM_HEAT_PER_MB * cf * (loop > 0 ? 1F + CRYO_LOOP_PCT[loop - 1] / 100F : 1F);
        // what the tank holds limits it too (the fraction already spent counts as spent)
        int have = amountOf(w, Gas.HELIUM);
        removed = Math.min(removed, (int) (have * perMb));
        if (removed <= 0) {
            return 0;
        }
        drainFraction(w, Gas.HELIUM, heliumUse(chest, removed / perMb, fusion));
        return removed;
    }

    /** Helium boiling away in the worn loop this second (the Recuperator gives part back). */
    public static void heliumBoilOff(ItemStack[] w, boolean fusion) {
        ItemStack chest = at(w, CHEST);
        if (chest != null && amountOf(w, Gas.HELIUM) > 0) {
            drainFraction(w, Gas.HELIUM, heliumUse(chest, HELIUM_BOIL_PER_MIN / 60F, fusion));
        }
    }

    private static float heliumUse(ItemStack chest, float mb, boolean fusion) {
        int rec = chipTier(chest, ChipType.RECUPERATOR);
        float use = mb * (rec > 0 ? 1F - RECUPERATOR_PCT[rec - 1] / 100F : 1F);
        return fusion ? use * FUSION_HELIUM_MUL : use;
    }
}
