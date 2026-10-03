package com.sc.item;

import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorSuit;
import com.sc.util.SingularLevel;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.util.MathHelper;

/**
 * Stage 3 of the Singular suit: where the level points (plan §6 ОЧ1-ОЧ7) and the task counters (Р5)
 * come from - the server's second of the suit (spent gas, gravity flight, absorbed damage, the
 * Nether, exploring, singular matter poured), kills, the key functions - and the Р3 protection bonus.
 * The points go to every worn Singular piece (SingularLevel.awardWorn), the counters to the player's
 * persisted data (SingularLevel.store); the client gets the counters every few seconds (LevelMessage).
 */
public final class SingularProgressSC {

    /** Store keys of the accumulated parts below a whole point; the kill cap's minute and its points. */
    private static final String GAS_ACC = "gasAcc", FLY_ACC = "flyAcc", ABS_ACC = "absAcc", KILL_MIN = "killMin", KILL_PTS = "killPts";
    /** Entity data (not persisted): damage absorbed since the last second; the last position while in gravity flight. */
    private static final String ABS_PENDING = "scLvAbs", FLY_X = "scLvFx", FLY_Y = "scLvFy", FLY_Z = "scLvFz", FLY_DIM = "scLvFdim";
    /** A jump longer than this in one second is a teleport, not flight. */
    public static final double MAX_FLY_PER_SECOND = 150;
    /** Nether task: the suit's heat under this percent. */
    public static final int NETHER_HEAT_PCT = 50;
    /** The counters go to the client every this many seconds while a Singular piece is worn. */
    private static final int SYNC_EVERY = 5;

    private SingularProgressSC() {
    }

    // ------------------------------------------------------------------ once a second, server

    /** The suit's second for the levels (from ArmorLogicSC.singularSecond). */
    public static void second(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        ItemStack[] w = ArmorGasSC.wornSet(p);
        NBTTagCompound data = p.getEntityData();
        if (SingularLevel.updateSync(w)) {
            p.inventoryContainer.detectAndSendChanges();
        }
        if (!SingularLevel.wearsSingular(p)) {
            data.removeTag(FLY_DIM);
            data.removeTag(ABS_PENDING);
            return;
        }
        NBTTagCompound s = SingularLevel.store(p, true);
        int pts = 0;
        // ОЧ1 / Р5 "spend 2 000 mB": what the drain helpers noted on the pieces
        int spent = ArmorGasSC.takePending(w, ArmorGasSC.SPENT_PENDING);
        if (spent > 0) {
            s.setInteger(SingularLevel.C_GAS, satAdd(s.getInteger(SingularLevel.C_GAS), spent));
            long acc = (long) s.getInteger(GAS_ACC) + spent;
            pts += (int) (acc / SingularLevel.GAS_MB_PER_POINT);
            s.setInteger(GAS_ACC, (int) (acc % SingularLevel.GAS_MB_PER_POINT));
        }
        // Р5 "pour 5 000 mB of singular matter": by hand or in the station, counted once the piece is worn
        int sm = ArmorGasSC.takePending(w, ArmorGasSC.SM_PENDING);
        if (sm > 0) {
            s.setInteger(SingularLevel.C_SM, satAdd(s.getInteger(SingularLevel.C_SM), sm));
        }
        // ОЧ2 / Р5 "fly 5 km": gravity flight, the distance since the last second
        if (ArmorLogicSC.gravFlightOn(p) && p.capabilities.isFlying) {
            if (data.hasKey(FLY_DIM) && data.getInteger(FLY_DIM) == p.dimension) {
                double dx = p.posX - data.getDouble(FLY_X), dy = p.posY - data.getDouble(FLY_Y), dz = p.posZ - data.getDouble(FLY_Z);
                double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
                if (d > 0 && d <= MAX_FLY_PER_SECOND) {
                    s.setDouble(SingularLevel.C_FLY, s.getDouble(SingularLevel.C_FLY) + d);
                    double acc = s.getDouble(FLY_ACC) + d;
                    int whole = SingularLevel.wholePoints(acc, SingularLevel.FLIGHT_BLOCKS_PER_POINT);
                    pts += whole;
                    s.setDouble(FLY_ACC, acc - whole * (double) SingularLevel.FLIGHT_BLOCKS_PER_POINT);
                }
            }
            data.setDouble(FLY_X, p.posX);
            data.setDouble(FLY_Y, p.posY);
            data.setDouble(FLY_Z, p.posZ);
            data.setInteger(FLY_DIM, p.dimension);
        } else {
            data.removeTag(FLY_DIM);
        }
        // ОЧ3: damage the armour and Н2 took this second
        float abs = data.getFloat(ABS_PENDING);
        data.removeTag(ABS_PENDING);
        if (abs > 0) {
            float acc = s.getFloat(ABS_ACC) + abs;
            int whole = SingularLevel.wholePoints(acc, 1);
            pts += whole;
            s.setFloat(ABS_ACC, acc - whole);
        }
        // Р5 "10 min in the Nether with the heat under 50%": the Singular chestplate's heat
        ItemStack chest = ArmorGasSC.worn(p, ArmorGasSC.CHEST);
        if (p.worldObj.provider.isHellWorld && SingularLevel.isSingular(chest) && heatPercent(chest) < NETHER_HEAT_PCT) {
            s.setInteger(SingularLevel.C_NETHER, satAdd(s.getInteger(SingularLevel.C_NETHER), 1));
        }
        // ОЧ7: a biome / a dimension first visited in the suit
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        net.minecraft.world.biome.BiomeGenBase biome = p.worldObj.getBiomeGenForCoords(x, z);
        if (biome != null && addOnce(s, SingularLevel.C_BIOMES, biome.biomeID, 256)) {
            pts += SingularLevel.BIOME_POINTS;
        }
        if (addOnce(s, SingularLevel.C_DIMS, p.dimension, 256)) {
            pts += SingularLevel.DIMENSION_POINTS;
        }
        award(p, pts);
        if (p.ticksExisted % (20 * SYNC_EVERY) < 20) {
            sync(p);
        }
    }

    private static int satAdd(int a, int b) {
        return (int) Math.max(0, Math.min(Integer.MAX_VALUE, (long) a + b));
    }

    /** Heat of a chestplate in percent of its suit's limit. */
    public static int heatPercent(ItemStack chest) {
        if (chest == null || !(chest.getItem() instanceof ItemArmorSC) || !chest.hasTagCompound()) {
            return 0;
        }
        ArmorSuit suit = ((ItemArmorSC) chest.getItem()).getSuit();
        return suit.heatCapacity <= 0 ? 0 : chest.getTagCompound().getInteger("HeatSC") * 100 / suit.heatCapacity;
    }

    /** Pure: adds `v` to the int list `key` once (at most `max` kept). @return whether it was new */
    public static boolean addOnce(NBTTagCompound s, String key, int v, int max) {
        int[] old = s.getIntArray(key);
        for (int o : old) {
            if (o == v) {
                return false;
            }
        }
        if (old.length >= max) {
            return false;
        }
        int[] now = java.util.Arrays.copyOf(old, old.length + 1);
        now[old.length] = v;
        s.setIntArray(key, now);
        return true;
    }

    /** Points to every worn Singular piece; the inventory sent when any took some. */
    private static void award(EntityPlayer p, int pts) {
        if (pts > 0 && SingularLevel.awardWorn(p, pts) > 0) {
            p.inventoryContainer.detectAndSendChanges();
        }
    }

    /** Server: the task counters to the player's client (also at login). */
    public static void sync(EntityPlayer p) {
        if (p instanceof EntityPlayerMP && ((EntityPlayerMP) p).playerNetServerHandler != null) {
            NBTTagCompound persisted = SingularLevel.persisted(p, false);
            com.sc.handler.ArmorNetSC.CHANNEL.sendTo(new com.sc.handler.ArmorNetSC.LevelMessage(SingularLevel.taskValues(persisted),
                    SingularLevel.explored(p, false), SingularLevel.explored(p, true)), (EntityPlayerMP) p);
        }
    }

    // ------------------------------------------------------------------ events

    /** ОЧ3: points of damage a worn Singular piece absorbed (ItemArmorSC.damageArmor). */
    public static void absorbed(EntityPlayer p, float points) {
        if (p != null && !p.worldObj.isRemote && points > 0) {
            NBTTagCompound data = p.getEntityData();
            data.setFloat(ABS_PENDING, data.getFloat(ABS_PENDING) + points);
        }
    }

    /** Н2 took `taken` of a hit: ОЧ3 and Р5 "absorb 300 damage with Н2". */
    public static void horizonAbsorbed(EntityPlayer p, float taken) {
        if (p == null || p.worldObj.isRemote || taken <= 0) {
            return;
        }
        NBTTagCompound s = SingularLevel.store(p, true);
        s.setFloat(SingularLevel.C_H2ABS, s.getFloat(SingularLevel.C_H2ABS) + taken);
        absorbed(p, taken);
    }

    /** ОЧ5: a Singular key function used (after its payment). */
    public static void keyUsed(EntityPlayer p, ArmorFeature f) {
        if (p != null && !p.worldObj.isRemote) {
            award(p, SingularLevel.keyPoints(f));
        }
    }

    /** П1 phase dash done: ОЧ5 and Р5 "50 phase dashes". */
    public static void dashed(EntityPlayer p) {
        if (p == null || p.worldObj.isRemote) {
            return;
        }
        NBTTagCompound s = SingularLevel.store(p, true);
        s.setInteger(SingularLevel.C_DASHES, satAdd(s.getInteger(SingularLevel.C_DASHES), 1));
        keyUsed(p, ArmorFeature.PHASE_DASH);
    }

    /** A hostile mob or a boss. */
    public static boolean countsAsMob(EntityLivingBase e) {
        return e != null && !(e instanceof EntityPlayer)
                && (e instanceof net.minecraft.entity.monster.IMob || e instanceof net.minecraft.entity.boss.IBossDisplayData);
    }

    /** LivingDeathEvent: ОЧ4 (5 a mob, 500 a boss, mobs capped at 200 a minute) and the kill tasks - the killer in the suit. */
    public static void onDeath(EntityLivingBase victim, DamageSource src) {
        if (victim == null || victim.worldObj.isRemote || src == null || !countsAsMob(victim)) {
            return;
        }
        Entity killer = src.getEntity();
        if (!(killer instanceof EntityPlayerMP) || killer instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        EntityPlayer p = (EntityPlayer) killer;
        if (!SingularLevel.wearsSingular(p)) {
            return;
        }
        boolean boss = victim instanceof net.minecraft.entity.boss.IBossDisplayData;
        NBTTagCompound s = SingularLevel.store(p, true);
        s.setInteger(SingularLevel.C_KILLS, satAdd(s.getInteger(SingularLevel.C_KILLS), 1));
        if (victim instanceof net.minecraft.entity.boss.EntityWither) {
            s.setInteger(SingularLevel.C_WITHER, satAdd(s.getInteger(SingularLevel.C_WITHER), 1));
        } else if (victim instanceof net.minecraft.entity.boss.EntityDragon) {
            s.setInteger(SingularLevel.C_DRAGON, satAdd(s.getInteger(SingularLevel.C_DRAGON), 1));
        }
        long minute = p.worldObj.getTotalWorldTime() / 1200L;
        if (s.getLong(KILL_MIN) != minute) {
            s.setLong(KILL_MIN, minute);
            s.setInteger(KILL_PTS, 0);
        }
        int pts = SingularLevel.killPoints(boss, s.getInteger(KILL_PTS));
        if (!boss) {
            s.setInteger(KILL_PTS, s.getInteger(KILL_PTS) + pts);
        }
        award(p, pts);
        if (boss) {
            sync(p);
        }
    }

    /**
     * Р3 / Р4: the extra protection of the worn Singular pieces' levels against the damage the plating
     * lets through (unblockable: magic, wither, burning, falling...; not the void, not hunger). @return the damage left
     */
    public static float protect(EntityPlayer p, DamageSource src, float amount) {
        if (p == null || p.worldObj.isRemote || amount <= 0 || src == null || !src.isUnblockable()
                || src == DamageSource.outOfWorld || src == DamageSource.starve) {
            return amount;
        }
        int pct = SingularLevel.protectionPercent(ArmorGasSC.wornSet(p));
        return pct <= 0 ? amount : amount * (1F - Math.min(90, pct) / 100F);
    }
}
