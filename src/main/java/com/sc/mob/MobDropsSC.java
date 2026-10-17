package com.sc.mob;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.emp.ItemEmpGunSC;
import com.sc.hazard.EmpStormSC;
import com.sc.item.ItemArmorSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.WeaponType;

import net.minecraft.enchantment.EnchantmentHelper;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingDropsEvent;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7, rules ПТ-1..ПТ-5, ПС-5): what a Substrate mob leaves, decided in
 * LivingDropsEvent (MobEventsSC) - the vanilla equipment drop is off (chance 0 on every slot, and dropEquipment itself:
 * Looting would still drop worn pieces at chance 0), so this is all there is:
 * <ol>
 * <li>the Marauder's bag - always, whoever killed it, flag or not, doMobLoot or not (a player's things may be in it);</li>
 * <li>rule ПТ-1: the rest only for a mob born in Si-14 (BornInSubstrateSC), dying in the Substrate (by its provider), whose
 * trophy player (killerOf: the most damage, at least 25%, online, a real EntityPlayerMP - never a FakePlayer) exists.
 * Eggs, spawners, mob grinders: experience only. And only with the gamerule doMobLoot on - Forge posts
 * LivingDropsEvent past vanilla's own doMobLoot check, so it is asked here (off: no loot, no roll, no bad luck);</li>
 * <li>the faction drops (IFactionLootSC of the mob's class);</li>
 * <li>one trophy roll (ПТ-4): the categories by what it wears and holds - a worn piece of the mod's armour, a blade, a gun
 * (TrophyTableSC by level) - their sum times Looting, elite, gearDropMultiplier and the bad-luck factor; a hit picks the
 * category by the chances, the piece at random among the worn ones;</li>
 * <li>the bad-luck counter of that level (ПТ-5, the player's persisted NBT: survives death) back to 0 on a trophy, +1
 * otherwise;</li>
 * <li>the trophy lies 15 s as its player's (EntityItem's owner, cleared by MobEventsSC; the lock's end in the item's
 * NBT, so it outlives an unloaded chunk and a restart).</li>
 * </ol>
 * The decisions are public for the tests: qualifies (ПТ-1), trophyChance, rollTrophy.
 * <p>
 * Ф3 (docs/plan-substrate-tech-f3.md §4.10, rules ПТ-13..ПТ-15): all of the above for every hostile native
 * (ISubstrateNativeSC and IMob - the swarm's particles, the drones and the mimic of package X too, through their
 * IFactionLootSC); the trophy roll only for an armoured one (EntityArmoredMobSC). A fourth category: a ПЭ-1 in hand (a
 * Jammer carrier) - TrophyTableSC.META_PE1 at TrophyTableSC.PE1 (a ПЭ-1М: META_PE1M, the boss's - Ф5).
 * <p>
 * Ф4 (docs/plan-substrate-tech-f4.md §9.1, §9.2, rules ПТ-16, ПТ-18..ПТ-20):
 * <ul>
 * <li>the faction drops for a peaceful native too (lootNative: ISubstrateNativeSC with IFactionLootSC, not IMob - the Wafer
 * Crab's Natural Wafer), never for a settlement's creature (ISettlementMemberSC); the trophy roll stays the armoured ones';</li>
 * <li>the bags always: the Marauder's, and any hostile native's that has a public takeBag() (the Scrapper Crab, package N -
 * looked up once per class, ПТ-19: a dead player's things are in it);</li>
 * <li>a class's own trophy categories (ITrophyCategoriesSC: the Warlord's Quantum piece 2% and Exo chestplate 0.5%, a Sentinel
 * carrier's ПЭ-1 10%);</li>
 * <li>an EMP storm in the world (EmpStormSC.active) - the trophy chance x1.5 (TrophyTableSC.STORM_MUL; with an elite at most
 * x2.5, ПТ-4);</li>
 * <li>the Sealed Crumbs of the Sentinel and «Затмение» x2 in the Singular Emission - crumbChance (their own factionLoot asks).</li>
 * </ul>
 * Ф5 (docs/plan-substrate-tech-f5.md §5.6, §4.6; the coordinator's lines): a class that decides its own drops (IOwnDropsSC - the
 * Breakdown Warden's personal loot) gets the event and every player's damage share instead of all of the above; an elite (an elite
 * modifier, or one of its kind - the Executioner, the Warlord, the Sentinel, «Затмение») with a qualifying kill drops an Arena
 * Coordinator on ARENA_COORDINATOR_CHANCE (ПС-23).
 */
public final class MobDropsSC {

    /** ПТ-2: the share of the damage the trophy's player must have dealt; ticks the trophy stays theirs (15 s). */
    public static final float KILLER_SHARE = 0.25F;
    public static final int OWNER_TICKS = 300;
    /** Ф1's faction drops (the mobs' factionLoot): the Lineman's Firmware and 0..TRANSISTOR_MAX Transistors, the Marauder's scrap. */
    public static final int FIRMWARE = 1, TRANSISTOR_MAX = 2;
    public static final float SCRAP_CHANCE = 0.4F;
    /** Ф2 (rule ПТ-10): the Marauder's Stolen Cargo, rolled on its own after the scrap. TODO(balance) */
    public static final float CARGO_CHANCE = 0.05F;
    /** Ф4: the standard categories' places in the array the roll goes by (ITrophyCategoriesSC adds its own from OWN on). */
    public static final int C_PIECE = 0, C_BLADE = 1, C_GUN = 2, C_PE1 = 3, OWN = 4;
    /** Ф5 (ПС-23; proposal §9 «ещё он выпадает с любой элиты с шансом 2%»): an elite's Arena Coordinator. TODO(balance) */
    public static final float ARENA_COORDINATOR_CHANCE = 0.02F;

    /** Ф4, ПТ-19: a class's public takeBag() (null - none; looked up once a class). */
    private static final Map<Class<?>, Method> BAGS = new HashMap<Class<?>, Method>();

    private MobDropsSC() {
    }

    /**
     * Ф4: whose drops are decided here - a hostile native (ISubstrateNativeSC and IMob), or a peaceful one with faction drops
     * (IFactionLootSC: the Wafer Crab) that is no settlement's creature.
     */
    public static boolean lootNative(Entity e) {
        return MobCombatSC.hostileNative(e)
                || e instanceof ISubstrateNativeSC && e instanceof IFactionLootSC && !(e instanceof ISettlementMemberSC);
    }

    /** Ф4, ПТ-20: a Sealed Crumb's chance from the Sentinel or «Затмение» in world of `mob` - x2 while the Singular Emission runs. */
    public static float crumbChance(Entity mob, float chance) {
        return mob != null && EmpStormSC.emission(mob.worldObj) ? Math.min(1F, chance * com.sc.hazard.StormRulesSC.EMISSION_CRUMB_MUL) : chance;
    }

    /** LivingDropsEvent of a hostile Substrate native (server). */
    public static void onDrops(LivingDropsEvent event) {
        if (!lootNative(event.entityLiving) || event.entityLiving.worldObj.isRemote) {
            return;
        }
        EntityLivingBase mob = event.entityLiving;
        if (mob instanceof IOwnDropsSC) {                            // Ф5: the Breakdown Warden's personal loot (package B)
            Map<java.util.UUID, Float> shares = MobEventsSC.damageShares(mob);
            MobEventsSC.forget(mob);
            ((IOwnDropsSC) mob).ownDrops(event, shares, mob.worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot"));
            return;
        }
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (mob instanceof EntityMarauderSC) {
            out.addAll(((EntityMarauderSC) mob).takeBag());
        } else {
            out.addAll(bagOf(mob));                                  // Ф4, ПТ-19: the Scrapper's - always, whoever killed it
        }
        EntityPlayerMP killer = MobEventsSC.killerOf(mob);
        MobEventsSC.forget(mob);
        ItemStack trophy = null;
        if (qualifies(mob, killer) && mob.worldObj.getGameRules().getGameRuleBooleanValue("doMobLoot")) {
            if (mob instanceof IFactionLootSC) {
                ((IFactionLootSC) mob).factionLoot(out, mob.getRNG());
            }
            if (mob instanceof EntityArmoredMobSC) {                 // ПТ-15: the trophy roll is the armoured ones' only
                trophy = rollTrophy((EntityArmoredMobSC) mob, killer, mob.getRNG());
                if (((EntityArmoredMobSC) mob).executeImmune() && mob.getRNG().nextFloat() < ARENA_COORDINATOR_CHANCE) {
                    out.add(com.sc.finale.FinaleItemsSC.arenaCoordinator(1));   // Ф5, ПС-23: an elite's 2%
                }
            }
        }
        for (ItemStack s : out) {
            if (s != null && s.stackSize > 0) {
                event.drops.add(drop(mob, s));
            }
        }
        if (trophy != null) {
            EntityItem e = drop(mob, trophy);
            e.func_145797_a(killer.getCommandSenderName());          // its player's for OWNER_TICKS (ПТ-2)
            MobEventsSC.lockTrophy(e, mob.worldObj.getTotalWorldTime() + OWNER_TICKS);
            event.drops.add(e);
        }
    }

    /** ПТ-1: born in Si-14, dying in the Substrate, the trophy player a real one. */
    public static boolean qualifies(Entity mob, EntityPlayer killer) {
        return mob != null && BornInSubstrateSC.isBorn(mob) && SubstrateSC.is(mob.worldObj)
                && killer instanceof EntityPlayerMP && !(killer instanceof FakePlayer);
    }

    /**
     * ПТ-4: the chance of this mob's single trophy roll for `killer` now (0 with nothing to take). By the categories it
     * wears / holds, Looting of the killer's hand, the elite flag, gearDropMultiplier, the killer's bad luck at the mob's
     * level.
     */
    public static float trophyChance(EntityArmoredMobSC mob, EntityPlayer killer) {
        float sum = sum(categories(mob));
        if (sum <= 0F) {
            return 0F;
        }
        ArmorSuit lvl = level(mob);
        int luck = killer == null ? 1 : TrophyTableSC.badLuckFactor(dry(killer)[lvl.ordinal()], lvl);
        return TrophyTableSC.chance(sum, killer == null ? 0 : EnchantmentHelper.getLootingModifier(killer), mob.mobArmor().elite,
                EmpStormSC.active(mob.worldObj), SubstrateConfigSC.gearDropMultiplier, luck);    // Ф4, ПТ-20: the storm x1.5
    }

    /**
     * ПТ-4, ПТ-5: the one roll; updates the killer's bad-luck counter of the mob's level (not while trophies are off in the
     * config). @return the trophy, or null
     */
    public static ItemStack rollTrophy(EntityArmoredMobSC mob, EntityPlayer killer, Random rand) {
        float[] c = categories(mob);
        if (sum(c) <= 0F || SubstrateConfigSC.gearDropMultiplier <= 0F) {
            return null;
        }
        float chance = trophyChance(mob, killer);
        ArmorSuit lvl = level(mob);
        int[] dry = dry(killer);
        if (rand.nextFloat() >= chance) {
            dry[lvl.ordinal()] = Math.min(Integer.MAX_VALUE - 1, dry[lvl.ordinal()]) + 1;
            saveDry(killer, dry);
            return null;
        }
        dry[lvl.ordinal()] = 0;
        saveDry(killer, dry);
        int meta;
        int cat = TrophyTableSC.pickCategory(c, rand.nextFloat());
        switch (cat) {
            case C_PIECE:
                meta = randomPiece(mob, rand);
                break;
            case C_BLADE:
                meta = MobCombatSC.blade(mob) == null ? -1 : TrophyTableSC.bladeMeta(MobCombatSC.blade(mob));
                break;
            case C_GUN:
                meta = MobCombatSC.gun(mob) == null ? -1 : TrophyTableSC.weaponMeta(MobCombatSC.gun(mob));
                break;
            case C_PE1:
                meta = ItemEmpGunSC.upgraded(mob.getHeldItem()) ? TrophyTableSC.META_PE1M : TrophyTableSC.META_PE1;
                break;
            default:
                meta = cat >= OWN && mob instanceof ITrophyCategoriesSC ? ((ITrophyCategoriesSC) mob).trophyMeta(cat, rand) : -1;
                break;
        }
        return meta < 0 ? null : MobItemsSC.trophyStack(meta);
    }

    /**
     * The categories' chances: {a worn piece, the blade in hand, the gun in hand, a ПЭ-1 in hand} (ПТ-9 by level; Ф3, ПТ-14:
     * the ПЭ-1's own TrophyTableSC.PE1); Ф4 (ПТ-16) - a class with its own categories changes or extends them
     * (ITrophyCategoriesSC). Public for the tests.
     */
    public static float[] categories(EntityArmoredMobSC mob) {
        ArmorSuit suit = mob.mobArmor().suit();
        BladeType blade = MobCombatSC.blade(mob);
        WeaponType gun = MobCombatSC.gun(mob);
        float[] c = {
                suit == null ? 0F : TrophyTableSC.PIECE[suit.ordinal()],
                blade == null ? 0F : TrophyTableSC.BLADE[blade.ordinal()],
                gun == null ? 0F : TrophyTableSC.WEAPON[gun.ordinal()],
                MobCombatSC.holdsPe1(mob) ? TrophyTableSC.PE1 : 0F};
        if (mob instanceof ITrophyCategoriesSC) {
            float[] own = ((ITrophyCategoriesSC) mob).trophyCategories(c);
            return own == null ? new float[0] : own;
        }
        return c;
    }

    /** Ф4, ПТ-19: the bag of a native with a public takeBag() (the Scrapper Crab's, package N), emptied; none - an empty list. */
    @SuppressWarnings("unchecked")
    private static List<ItemStack> bagOf(EntityLivingBase mob) {
        Class<?> k = mob.getClass();
        Method m;
        synchronized (BAGS) {
            if (BAGS.containsKey(k)) {
                m = BAGS.get(k);
            } else {
                m = null;
                try {
                    Method found = k.getMethod("takeBag");
                    if (List.class.isAssignableFrom(found.getReturnType())) {
                        m = found;
                    }
                } catch (NoSuchMethodException ex) {
                    // no bag on this class
                }
                BAGS.put(k, m);
            }
        }
        List<ItemStack> out = new ArrayList<ItemStack>();
        if (m == null) {
            return out;
        }
        try {
            Object got = m.invoke(mob);
            if (got instanceof List) {
                for (Object o : (List<Object>) got) {
                    if (o instanceof ItemStack) {
                        out.add((ItemStack) o);
                    }
                }
            }
        } catch (Exception ex) {
            cpw.mods.fml.common.FMLLog.warning("[Silicon Age] Si-14: the bag of %s couldn't be taken (%s)", k.getName(), ex);
        }
        return out;
    }

    private static float sum(float[] c) {
        float s = 0F;
        for (float v : c) {
            s += Math.max(0F, v);
        }
        return s;
    }

    /** The mob's level for the bad-luck counter: its suit, else its blade's, else Nano. */
    private static ArmorSuit level(EntityArmoredMobSC mob) {
        ArmorSuit suit = mob.mobArmor().suit();
        if (suit != null) {
            return suit;
        }
        BladeType blade = MobCombatSC.blade(mob);
        return blade != null ? blade.suit : ArmorSuit.NANO;
    }

    /** A random worn piece of the mod's armour as a trophy meta, -1 none. */
    private static int randomPiece(EntityArmoredMobSC mob, Random rand) {
        int[] types = new int[4];
        int n = 0;
        for (int t = 0; t < 4; t++) {
            if (MobArmorSC.modPiece(mob, t) != null) {
                types[n++] = t;
            }
        }
        if (n == 0) {
            return -1;
        }
        int t = types[rand.nextInt(n)];
        ItemStack s = MobArmorSC.modPiece(mob, t);
        return TrophyTableSC.armorMeta(((ItemArmorSC) s.getItem()).getSuit(), t);
    }

    // ------------------------------------------------------------------ ПТ-5: the bad-luck counters (int[4] by level)

    /** The player's counters (a copy; zeros for a new player). */
    public static int[] dry(EntityPlayer p) {
        int[] out = new int[4];
        if (p == null) {
            return out;
        }
        NBTTagCompound data = p.getEntityData().getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        int[] have = data.getIntArray(TrophyTableSC.DRY_NBT);
        System.arraycopy(have, 0, out, 0, Math.min(have.length, out.length));
        return out;
    }

    private static void saveDry(EntityPlayer p, int[] dry) {
        if (p == null) {
            return;
        }
        NBTTagCompound root = p.getEntityData();
        NBTTagCompound data = root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
        data.setIntArray(TrophyTableSC.DRY_NBT, dry);
        root.setTag(EntityPlayer.PERSISTED_NBT_TAG, data);
    }

    /** An EntityItem at the mob, as Entity.entityDropItem makes it (half a block up, a short pickup delay). */
    private static EntityItem drop(Entity mob, ItemStack s) {
        EntityItem e = new EntityItem(mob.worldObj, mob.posX, mob.posY + 0.5D, mob.posZ, s);
        e.delayBeforeCanPickup = 10;
        return e;
    }
}
