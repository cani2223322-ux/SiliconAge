package com.sc.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.BladeFeature;
import com.sc.util.BladeForm;
import com.sc.util.SingularLevel;
import com.sc.util.ToolGasSC;
import com.sc.util.ToolLevelSC;

import net.minecraft.block.material.Material;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.attributes.AttributeModifier;
import net.minecraft.entity.ai.attributes.IAttributeInstance;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.potion.Potion;
import net.minecraft.potion.PotionEffect;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * The Singular blade's own functions (docs/plan-singular-tools.md §2), all on the server:
 * - forms (BladeForm, Shift + wheel): the normal hit's multiplier, the scythe's arc, the spear's / whip's
 *   longer reach (a ray on the swing - the client only sends hits within its own 3 blocks), and FORM_ATTACK -
 *   the form's special: sword - the Exo wave, scythe «Жатва», spear «Пронзание», whip «Захват», shield «Таран»,
 *   singular «Коллапс» (a 2 s pull, then a blast that breaks no blocks);
 * - key functions: gravity pull, rift step (Duelist 5: three in a row), gravity tether;
 * - passives: cascade, sheath, chain cut; while blocking - perfect parry, charged strike (a block held
 *   CHARGE_TICKS charges the next hit; its argon is paid when that hit lands), event horizon, gravity shield,
 *   riposte (Guardian 3: the gravity shield's first 3 s of a block free; the shield form blocks for no EU);
 * - kill points («Жатва душ»: 5 a mob, 200 a minute at most, 500 a boss), Guardian 5 «Последний шанс».
 * What a player's blade does between ticks (cascade, bonuses for the next hit, the block's start, rift
 * charges) is kept here per player - never in the blade's NBT, which the client must not see change
 * mid-block (BladeLogicSC). Gases come from the worn armour (ToolGasSC), whole mB only (no tool NBT).
 * Area blows respect private fields and claims (BladeLogicSC.claimed) and never touch the player's own
 * pets or villagers (BladeLogicSC.fair).
 */
public final class BladeSingularSC {

    private static final UUID SHEATH_ID = UUID.fromString("5c1e9a7d-2f4b-4d8e-9b3a-6e0f1c2d3b47");
    /** Player entity data: the minute of the kill cap and the mob points earned in it. */
    private static final String KILL_MIN = "scBladeKillMin", KILL_PTS = "scBladeKillPts", LAST_CHANCE_AT = "scBladeLastChance";

    /** К-5/К-6: player data that outlives a death (a respawn copies only this compound). */
    private static NBTTagCompound persisted(EntityPlayer p) {
        NBTTagCompound root = p.getEntityData();
        if (!root.hasKey(EntityPlayer.PERSISTED_NBT_TAG)) {
            root.setTag(EntityPlayer.PERSISTED_NBT_TAG, new NBTTagCompound());
        }
        return root.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
    }
    /** Ticks between two blows from the spear's / whip's reach (a held swing calls it every tick). */
    private static final int REACH_GAP = 4;

    /** A player's Singular blade between ticks (server). */
    static final class State {
        int cascadeTarget = -1, cascadeStacks;
        long cascadeAt;
        boolean blocking, parryUsed, chargeCued, horizonDry, shieldDry;
        /** К-3: the last block let go, and whether this block got the Guardian's free shield seconds. */
        long blockEndedAt = -1000;
        boolean shieldFree;
        long blockStart;
        float absorbed;
        long parryUntil, chargedUntil, riposteUntil;
        float riposte;
        int horizon;
        int riftUsed;
        long riftFirst, reachAt = -100;
        int pendingPoints;
        /** A form step was refused mid-block: the held stack is sent again when the block ends. */
        boolean resendOnRelease;
    }

    /** One normal hit's Singular part (BladeLogicSC.attack): multiplier, added damage, what it spends. */
    static final class Hit {
        BladeForm form = BladeForm.SWORD;
        float mul = 1F, add;
        boolean parry, charged, riposte, cascade;
        int nextStacks;

        float damage(float base) {
            return base * mul + add;
        }
    }

    /** «Коллапс» while it pulls. */
    private static final class Collapse {
        final World world;
        final EntityPlayer owner;
        final ItemStack blade;
        final double x, y, z, r;
        final long end;
        final float damage;
        /** Entity id -> whether a blow on it is allowed (asked once: BladeLogicSC.claimed). */
        final Map<Integer, Boolean> allowed = new HashMap<Integer, Boolean>();

        Collapse(World world, EntityPlayer owner, ItemStack blade, double x, double y, double z, double r, long end, float damage) {
            this.world = world;
            this.owner = owner;
            this.blade = blade;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.end = end;
            this.damage = damage;
        }
    }

    private static final class Tether {
        final EntityPlayer p;
        final EntityLivingBase target;
        final long end;

        Tether(EntityPlayer p, EntityLivingBase target, long end) {
            this.p = p;
            this.target = target;
            this.end = end;
        }
    }

    private static final Map<UUID, State> STATES = new HashMap<UUID, State>();
    /** Key presses and form changes from the network thread, run on the server thread. */
    private static final ConcurrentLinkedQueue<Object[]> PENDING = new ConcurrentLinkedQueue<Object[]>();
    /** How many of PENDING are each player's (at most QUEUE_MAX: more is a flood, dropped on the network thread). */
    private static final java.util.concurrent.ConcurrentHashMap<UUID, java.util.concurrent.atomic.AtomicInteger> QUEUED =
            new java.util.concurrent.ConcurrentHashMap<UUID, java.util.concurrent.atomic.AtomicInteger>();
    static final int QUEUE_MAX = 8;
    /** Player data: the world tick of the last Shift + wheel step (the blade's form / the drill's mode). */
    static final String WHEEL_AT = "scToolWheelAt";
    /** Ticks at least between two Shift + wheel steps on the server (the client keeps a little more). */
    public static final int WHEEL_GAP = 2;
    private static final List<Collapse> COLLAPSES = new ArrayList<Collapse>();
    private static final Map<UUID, Tether> TETHERS = new HashMap<UUID, Tether>();

    private BladeSingularSC() {
    }

    static State state(EntityPlayer p) {
        State s = STATES.get(p.getUniqueID());
        if (s == null) {
            s = new State();
            STATES.put(p.getUniqueID(), s);
        }
        return s;
    }

    /** The player left: their state, tether and pending points are dropped (points owed mid-block are lost). */
    public static void forget(EntityPlayer p) {
        if (p != null) {
            STATES.remove(p.getUniqueID());
            TETHERS.remove(p.getUniqueID());
            QUEUED.remove(p.getUniqueID());
        }
    }

    // ================================================================== the queue and the server tick

    /** From the network thread: a BladeFeature (key) or an Integer (form wheel delta). */
    static void queue(EntityPlayerMP p, Object what) {
        if (p == null || what == null) {
            return;
        }
        java.util.concurrent.atomic.AtomicInteger n = QUEUED.get(p.getUniqueID());
        if (n == null) {
            java.util.concurrent.atomic.AtomicInteger fresh = new java.util.concurrent.atomic.AtomicInteger();
            n = QUEUED.putIfAbsent(p.getUniqueID(), fresh);
            if (n == null) {
                n = fresh;
            }
        }
        if (n.incrementAndGet() > QUEUE_MAX) {
            n.decrementAndGet();                            // a flood of packets: the rest is dropped
            return;
        }
        PENDING.add(new Object[]{p, what});
    }

    /**
     * From the network thread: any other tool task (the drill's keys / mode, the tools' branch choice) - run on the
     * server thread in the same queue, with the same per-player cap.
     */
    public static void queueTask(EntityPlayerMP p, Runnable task) {
        queue(p, task);
    }

    /** Pure: a step at world tick `now` is allowed after one at `last` (`had`: there was one) - `gap` ticks apart, or time went back. */
    public static boolean gapOk(long now, long last, boolean had, int gap) {
        return !had || now < last || now - last >= gap;
    }

    /** Server thread: a Shift + wheel step (form / mode) may go now - and the time is stamped if so. */
    static boolean wheelReady(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        if (!gapOk(now, data.getLong(WHEEL_AT), data.hasKey(WHEEL_AT), WHEEL_GAP)) {
            return false;
        }
        data.setLong(WHEEL_AT, now);
        return true;
    }

    /** The server stopped: nothing of its players' blades is kept. */
    public static void clearAll() {
        PENDING.clear();
        QUEUED.clear();
        COLLAPSES.clear();
        STATES.clear();
        TETHERS.clear();
    }

    /** Server tick (BladeEventsSC): the queued keys, the collapses, the tethers. */
    public static void serverTick() {
        for (Object[] k; (k = PENDING.poll()) != null; ) {
            EntityPlayer p = (EntityPlayer) k[0];
            java.util.concurrent.atomic.AtomicInteger n = QUEUED.get(p.getUniqueID());
            if (n != null && n.decrementAndGet() <= 0) {
                QUEUED.remove(p.getUniqueID(), n);
            }
            if (p.isDead || p.worldObj == null || p.worldObj.isRemote || p.getHealth() <= 0) {
                continue;
            }
            if (k[1] instanceof BladeFeature) {
                BladeLogicSC.runAction(p, (BladeFeature) k[1]);
            } else if (k[1] instanceof Integer) {
                BladeLogicSC.runCycleForm(p, ((Integer) k[1]).intValue());
            } else if (k[1] instanceof Runnable) {
                ((Runnable) k[1]).run();
            }
        }
        for (Iterator<Collapse> it = COLLAPSES.iterator(); it.hasNext(); ) {
            Collapse c = it.next();
            if (c.owner.isDead || c.owner.worldObj != c.world) {
                it.remove();                                // died or left: it fizzles
                continue;
            }
            long now = c.world.getTotalWorldTime();
            if (now >= c.end) {
                it.remove();
                collapseBlast(c);
            } else {
                collapseTick(c, now);
            }
        }
        for (Iterator<Map.Entry<UUID, Tether>> it = TETHERS.entrySet().iterator(); it.hasNext(); ) {
            if (!tetherTick(it.next().getValue())) {
                it.remove();
            }
        }
    }

    // ================================================================== small helpers

    private static IChatComponent name(BladeFeature f) {
        return new ChatComponentTranslation("sc.bladefn." + f.key());
    }

    /** The form that counts: one the blade's level / branch no longer opens is a sword. */
    public static BladeForm effectiveForm(EntityPlayer p, ItemStack blade) {
        if (!ToolLevelSC.isBlade(blade)) {
            return BladeForm.SWORD;
        }
        BladeForm f = ItemBladeSC.formOf(blade);
        return f.open(p, blade) ? f : BladeForm.SWORD;
    }

    /** Guardian / shield form: blocking spends no EU (energy block, deflect). */
    static boolean blockFree(EntityPlayer p, ItemStack blade) {
        return effectiveForm(p, blade) == BladeForm.SHIELD;
    }

    private static float onDamage(ItemStack blade) {
        return ItemBladeSC.typeOf(blade).onDamage;
    }

    /** Someone else's private field covers the entity, or it's a player PvP spares: no pushing, pulling or slowing it (silent). */
    private static boolean guarded(EntityPlayer p, Entity e) {
        return BladeLogicSC.pvpBlocked(p, e) || com.sc.ShieldEventHandler.privateFieldAgainst(e.worldObj, p, MathHelper.floor_double(e.posX),
                MathHelper.floor_double(e.posY + e.height / 2), MathHelper.floor_double(e.posZ)) != null;
    }

    /** The living thing looked at within `range` (not behind a block) that may be hit, or null. */
    private static Entity target(EntityPlayer p, double range) {
        Entity e = SingularPowersSC.lookEntity(p, range, true, false);
        return e != null && BladeLogicSC.fair(p, e) && BladeLogicSC.claimed(p, e) ? e : null;
    }

    private static double eyeDistance(EntityPlayer p, Entity e) {
        double ex = p.posX, ey = p.posY + p.getEyeHeight(), ez = p.posZ;
        AxisAlignedBB b = e.boundingBox;
        double dx = ex - Math.max(b.minX, Math.min(b.maxX, ex));
        double dy = ey - Math.max(b.minY, Math.min(b.maxY, ey));
        double dz = ez - Math.max(b.minZ, Math.min(b.maxZ, ez));
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }

    /** The player's body fits with its feet at x, y, z (no lava). */
    private static boolean fits(EntityPlayer p, double x, double y, double z) {
        AxisAlignedBB bb = ArmorLogicSC.boxAt(x, y, z);
        return y > 0 && y < 255 && p.worldObj.blockExists(MathHelper.floor_double(x), MathHelper.floor_double(y), MathHelper.floor_double(z))
                && p.worldObj.getCollidingBoundingBoxes(p, bb).isEmpty() && !p.worldObj.isMaterialInBB(bb, Material.lava);
    }

    private static void spark(World w, String name, double x, double y, double z, int count, double spread, double speed) {
        if (w instanceof WorldServer) {
            ((WorldServer) w).func_147487_a(name, x, y, z, count, spread, spread, spread, speed);
        }
    }

    private static void line(EntityPlayer p, Entity a, Entity b, String particle) {
        BladeLogicSC.particles(p, Vec3.createVectorHelper(a.posX, a.posY + a.height / 2, a.posZ),
                Vec3.createVectorHelper(b.posX, b.posY + b.height / 2, b.posZ), particle, 2);
    }

    // ================================================================== every tick (BladeLogicSC.tick, server)

    static void tick(EntityPlayer p) {
        ItemStack blade = BladeLogicSC.held(p);
        boolean sing = ToolLevelSC.isBlade(blade);
        State s = sing ? state(p) : STATES.get(p.getUniqueID());
        long now = p.worldObj.getTotalWorldTime();
        if (s != null) {
            boolean blocking = sing && p.isBlocking();
            if (blocking && !s.blocking) {
                blockStarted(s, now);
            } else if (!blocking && s.blocking) {
                blockEnded(p, s, now);
            }
            s.blocking = blocking;
            if (blocking) {
                blockTick(p, blade, s, now);
            }
            if (sing && s.pendingPoints > 0 && !p.isUsingItem()) {
                award(p, blade, s.pendingPoints);
                s.pendingPoints = 0;
            }
            if (s.resendOnRelease && !p.isUsingItem()) {
                s.resendOnRelease = false;
                BladeLogicSC.resendHeld(p);
            }
        }
        if (p.ticksExisted % 20 == 13) {
            second(p, s, sing ? blade : null, now);
        }
    }

    private static void blockStarted(State s, long now) {
        s.blockStart = now;
        // К-3: the free seconds only after as long without a block - re-raising it no longer keeps the shield free
        s.shieldFree = now - s.blockEndedAt >= BladeFeature.GUARDIAN_FREE_TICKS || now < s.blockEndedAt;
        s.absorbed = 0;
        s.parryUsed = false;
        s.chargeCued = false;
        s.horizonDry = false;
        s.shieldDry = false;
    }

    /** The block let go: a long one charges the next hit; the parry's and riposte's bonuses wait BONUS_KEEP from now. */
    private static void blockEnded(EntityPlayer p, State s, long now) {
        s.blockEndedAt = now;
        if (now - s.blockStart >= BladeFeature.CHARGE_TICKS && BladeLogicSC.active(p, BladeFeature.CHARGED_STRIKE)) {
            s.chargedUntil = now + BladeFeature.BONUS_KEEP;
        }
        if (s.parryUntil > now) {
            s.parryUntil = now + BladeFeature.BONUS_KEEP;
        }
        if (s.riposte > 0) {
            s.riposteUntil = now + BladeFeature.BONUS_KEEP;
        }
    }

    private static void blockTick(EntityPlayer p, ItemStack blade, State s, long now) {
        long age = now - s.blockStart;
        if (!s.chargeCued && age >= BladeFeature.CHARGE_TICKS && BladeLogicSC.active(p, BladeFeature.CHARGED_STRIKE)) {
            s.chargeCued = true;                            // the charge is ready: let go and strike
            p.worldObj.playSoundAtEntity(p, "random.orb", 0.6F, 0.5F);
            spark(p.worldObj, "magicCrit", p.posX, p.posY + 1.2, p.posZ, 15, 0.4, 0.1);
        }
        if (age % 20 == 0) {                                // the block's seconds: helium for the horizon and the shield
            if (!s.horizonDry && BladeLogicSC.active(p, BladeFeature.EVENT_HORIZON)) {
                BladeFeature f = BladeFeature.EVENT_HORIZON;
                if (!ToolGasSC.drainExact(p, f.gas(), f.gasMb())) {
                    s.horizonDry = true;
                    ToolGasSC.noGasMessage(p, f.gas(), f.gasMb());
                }
            }
            if (!s.shieldDry && BladeLogicSC.active(p, BladeFeature.GRAV_SHIELD)) {
                BladeFeature f = BladeFeature.GRAV_SHIELD;
                boolean free = s.shieldFree && age < BladeFeature.GUARDIAN_FREE_TICKS
                        && ToolLevelSC.hasBranch(p, blade, ToolLevelSC.BLADE_GUARDIAN, ToolLevelSC.BRANCH_LEVEL);
                if (!free && !ToolGasSC.drainExact(p, f.gas(), f.gasMb())) {
                    s.shieldDry = true;
                    ToolGasSC.noGasMessage(p, f.gas(), f.gasMb());
                } else {
                    BladeLogicSC.heat(p, blade, f.heat);
                }
            }
        }
        if (now % 2 == 0 && shieldWorks(p)) {
            double r = BladeFeature.GRAV_SHIELD_RADIUS;
            for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, r, r))) {
                Entity e = (Entity) o;
                if (BladeLogicSC.fair(p, e) && !(e instanceof IBossDisplayData) && p.getDistanceSqToEntity(e) <= r * r && !guarded(p, e)) {
                    ArmorLogicSC.push(p, e, 0.5, 0.15);
                }
            }
            if (now % 10 == 0) {
                spark(p.worldObj, "portal", p.posX, p.posY + 1, p.posZ, 12, 1.2, 0.4);
            }
        }
    }

    /** Blocking with the event horizon working (on, open, its helium paid this second). */
    static boolean horizonWorks(EntityPlayer p) {
        State s = STATES.get(p.getUniqueID());
        return s != null && s.blocking && !s.horizonDry && BladeLogicSC.active(p, BladeFeature.EVENT_HORIZON);
    }

    /** Blocking with the gravity shield working. */
    static boolean shieldWorks(EntityPlayer p) {
        State s = STATES.get(p.getUniqueID());
        return s != null && s.blocking && !s.shieldDry && BladeLogicSC.active(p, BladeFeature.GRAV_SHIELD);
    }

    /** The event horizon swallows a projectile: one more charge for the next wave. */
    static void absorb(EntityPlayer p, ItemStack blade, Entity e) {
        State s = state(p);
        s.horizon = Math.min(BladeFeature.HORIZON_MAX, s.horizon + 1);
        spark(p.worldObj, "portal", e.posX, e.posY, e.posZ, 15, 0.2, 0.5);
        p.worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "mob.endermen.portal", 0.4F, 1.8F);
        e.setDead();
        BladeLogicSC.heat(p, blade, BladeFeature.EVENT_HORIZON.heat);
    }

    /** The wave's multiplier from the stored projectiles - they are spent. Not a Singular blade / none stored: 1. */
    static float takeHorizon(EntityPlayer p) {
        State s = STATES.get(p.getUniqueID());
        if (s == null || s.horizon <= 0) {
            return 1F;
        }
        float m = BladeFeature.horizonMul(s.horizon);
        s.horizon = 0;
        return m;
    }

    /** How many projectiles the event horizon holds for the next wave (server; the self-test, the UI could ask). */
    public static int horizonCharges(EntityPlayer p) {
        State s = p == null ? null : STATES.get(p.getUniqueID());
        return s == null ? 0 : s.horizon;
    }

    /** BladeLogicSC.onHurt while blocking: `amount` before, `left` after the energy block (vanilla then halves: (1 + left) / 2). */
    static void blocked(EntityPlayer p, ItemStack blade, float amount, float left) {
        if (!ToolLevelSC.isBlade(blade)) {
            return;
        }
        float taken = amount - (1F + left) * 0.5F;
        if (taken <= 0) {
            return;
        }
        State s = state(p);
        s.absorbed += taken;
        if (s.absorbed > BladeFeature.RIPOSTE_MIN && BladeLogicSC.active(p, BladeFeature.RIPOSTE)) {
            s.riposte = Math.min(BladeFeature.RIPOSTE_MAX, s.absorbed * BladeFeature.RIPOSTE_SHARE);   // К-4
            s.riposteUntil = p.worldObj.getTotalWorldTime() + BladeFeature.BONUS_KEEP;    // renewed when the block ends
        }
    }

    // ================================================================== once a second

    private static void second(EntityPlayer p, State s, ItemStack heldBlade, long now) {
        sheath(p);
        if (heldBlade != null && BladeLogicSC.active(p, BladeFeature.HUNTER_SENSE)) {
            BladeFeature f = BladeFeature.HUNTER_SENSE;
            if (!ToolGasSC.drainExact(p, f.gas(), f.gasMb())) {
                ToolGasSC.noGasMessage(p, f.gas(), f.gasMb());   // the client stops drawing with no krypton
            }
        }
        if (s != null && s.riftUsed > 0 && now - s.riftFirst > BladeFeature.RIFT_CHAIN_TICKS) {
            s.riftUsed = 0;
        }
    }

    /** The sheath: a Singular blade switched off on the hotbar - +10% speed, and it charges slowly from the worn chestplate. */
    private static void sheath(EntityPlayer p) {
        ItemStack found = null;
        for (int i = 0; i < 9; i++) {
            ItemStack st = p.inventory.mainInventory[i];
            if (ToolLevelSC.isBlade(st) && !ItemBladeSC.isEnabled(st, BladeFeature.BLADE) && ItemBladeSC.isEnabled(st, BladeFeature.SHEATH)
                    && BladeLogicSC.unlocked(p, st, BladeFeature.SHEATH)) {
                found = st;
                break;
            }
        }
        IAttributeInstance speed = p.getEntityAttribute(SharedMonsterAttributes.movementSpeed);
        AttributeModifier have = speed == null ? null : speed.getModifier(SHEATH_ID);
        if (found == null) {
            if (have != null) {
                speed.removeModifier(have);
            }
            return;
        }
        if (speed != null && have == null) {
            speed.applyModifier(new AttributeModifier(SHEATH_ID, "SC blade sheath", BladeFeature.SHEATH_SPEED, 2));
        }
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        if (chest == null || (p.isUsingItem() && p.getCurrentEquippedItem() == found)) {
            return;
        }
        int room = ItemBladeSC.capacityOf(found) - ItemBladeSC.chargeOf(found);
        int moved = Math.min(Math.min(room, BladeFeature.SHEATH_CHARGE), ItemArmorSC.chargeOf(chest));
        if (moved > 0) {
            ItemBladeSC.charge(found, ItemArmorSC.discharge(chest, moved));
        }
    }

    // ================================================================== the normal hit

    /** Before a normal hit lands (BladeLogicSC.attack): the form, the cascade, the bonuses waiting for it. */
    static Hit beforeHit(EntityPlayer p, ItemStack blade, Entity target) {
        Hit h = new Hit();
        BladeForm form = effectiveForm(p, blade);
        if (form == BladeForm.SINGULAR && !ToolGasSC.has(p, Gas.SINGULAR_MATTER, BladeFeature.SINGULAR_SM_PER_HIT)) {
            ToolGasSC.noGasMessage(p, Gas.SINGULAR_MATTER, BladeFeature.SINGULAR_SM_PER_HIT);
            form = BladeForm.SWORD;                         // no singular matter: a sword's hit
        }
        h.form = form;
        h.mul = BladeFeature.formMul(form);
        if (!(target instanceof EntityLivingBase)) {
            return h;
        }
        State s = state(p);
        long now = p.worldObj.getTotalWorldTime();
        if (BladeLogicSC.active(p, BladeFeature.CASCADE)) {
            int stacks = s.cascadeTarget == target.getEntityId() && now - s.cascadeAt <= BladeFeature.CASCADE_RESET && now >= s.cascadeAt
                    ? s.cascadeStacks : 0;
            boolean duelist = ToolLevelSC.hasBranch(p, blade, ToolLevelSC.BLADE_DUELIST, ToolLevelSC.BRANCH_LEVEL);
            h.mul *= BladeFeature.cascadeMul(stacks, duelist);
            h.cascade = true;
            h.nextStacks = Math.min(100, stacks + 1);
        }
        if (s.parryUntil > now) {
            h.mul *= BladeFeature.PARRY_MUL;
            h.parry = true;
        }
        if (s.chargedUntil > now && BladeLogicSC.active(p, BladeFeature.CHARGED_STRIKE)) {
            BladeFeature f = BladeFeature.CHARGED_STRIKE;
            if (ToolGasSC.has(p, f.gas(), f.gasMb())) {
                h.mul *= BladeFeature.CHARGED_MUL;
                h.charged = true;
            } else {
                s.chargedUntil = 0;
                ToolGasSC.noGasMessage(p, f.gas(), f.gasMb());
            }
        }
        if (s.riposte > 0 && s.riposteUntil > now && BladeLogicSC.active(p, BladeFeature.RIPOSTE)) {
            h.add = s.riposte;
            h.riposte = true;
        }
        return h;
    }

    /** The normal hit landed for `damage`: what it spends, the cascade, the charged throw, the scythe's arc, the chain. */
    static void afterHit(EntityPlayer p, ItemStack blade, Entity target, Hit h, float damage) {
        State s = state(p);
        long now = p.worldObj.getTotalWorldTime();
        if (h.form == BladeForm.SINGULAR) {
            ToolGasSC.drainExact(p, Gas.SINGULAR_MATTER, BladeFeature.SINGULAR_SM_PER_HIT);
        }
        if (h.parry) {
            s.parryUntil = 0;
        }
        if (h.riposte) {
            s.riposte = 0;
            s.riposteUntil = 0;
        }
        if (h.cascade) {
            s.cascadeTarget = target.getEntityId();
            s.cascadeStacks = h.nextStacks;
            s.cascadeAt = now;
        }
        if (h.charged) {
            s.chargedUntil = 0;
            ToolGasSC.drainExact(p, BladeFeature.CHARGED_STRIKE.gas(), BladeFeature.CHARGED_STRIKE.gasMb());
            BladeLogicSC.heat(p, blade, BladeFeature.CHARGED_STRIKE.heat);
            if (!(target instanceof IBossDisplayData)) {
                float yaw = p.rotationYaw * (float) Math.PI / 180F;
                target.motionX += -MathHelper.sin(yaw) * BladeFeature.CHARGED_PUSH;
                target.motionZ += MathHelper.cos(yaw) * BladeFeature.CHARGED_PUSH;
                target.motionY = Math.max(target.motionY, BladeFeature.CHARGED_UP);
                target.velocityChanged = true;
            }
            p.worldObj.playSoundAtEntity(target, "random.explode", 0.5F, 1.6F);
            spark(p.worldObj, "largeexplode", target.posX, target.posY + target.height / 2, target.posZ, 1, 0, 0);
        }
        if (h.form == BladeForm.SCYTHE) {
            scytheArc(p, blade, target);
        }
        if (target instanceof EntityLivingBase && BladeLogicSC.active(p, BladeFeature.CHAIN_CUT)) {
            chain(p, blade, target, damage);
        }
    }

    /** The scythe: everything else in the half circle of SCYTHE_RADIUS in front takes the scythe's blow. */
    private static void scytheArc(EntityPlayer p, ItemStack blade, Entity main) {
        double r = BladeFeature.SCYTHE_RADIUS;
        float damage = onDamage(blade) * BladeFeature.SCYTHE_MUL * BladeLogicSC.areaMul(p, blade);
        Vec3 look = p.getLookVec();
        double lx = look.xCoord, lz = look.zCoord, len = Math.sqrt(lx * lx + lz * lz);
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, 1.5, r))) {
            Entity e = (Entity) o;
            if (e == main) {
                continue;
            }
            double dx = e.posX - p.posX, dz = e.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
            if (d > r + e.width / 2 || (d > 0.5 && len > 0.01 && (dx * lx + dz * lz) / (d * len) < 0)) {
                continue;                                   // behind: the arc is the front half
            }
            if (!BladeLogicSC.fresh(e)) {
                continue;                                   // still in its hit frames: not one arc per click
            }
            BladeLogicSC.areaHit(p, blade, e, damage);
        }
        for (int i = -4; i <= 4; i++) {
            double a = Math.atan2(lz, lx) + i * Math.PI / 8;
            spark(p.worldObj, "crit", p.posX + Math.cos(a) * 2.2, p.posY + 1, p.posZ + Math.sin(a) * 2.2, 2, 0.1, 0.05);
        }
        p.worldObj.playSoundAtEntity(p, "mob.irongolem.throw", 0.6F, 1.6F);
    }

    /** Chain cut: the blow jumps on to CHAIN_TARGETS more within CHAIN_RANGE of the last one, a quarter weaker each time. */
    private static void chain(EntityPlayer p, ItemStack blade, Entity first, float damage) {
        BladeFeature f = BladeFeature.CHAIN_CUT;
        if (!ToolGasSC.has(p, f.gas(), f.gasMb())) {
            return;                                         // quietly a plain hit
        }
        List<Entity> done = new ArrayList<Entity>();
        done.add(first);
        Entity from = first;
        float area = BladeLogicSC.areaMul(p, blade);
        double r = BladeFeature.CHAIN_RANGE;
        int jumps = 0;
        for (int i = 1; i <= BladeFeature.CHAIN_TARGETS; i++) {
            Entity next = null;
            double best = r * r;
            for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, from.boundingBox.expand(r, r, r))) {
                Entity e = (Entity) o;
                double d = e.getDistanceSqToEntity(from);
                if (d <= best && !done.contains(e) && BladeLogicSC.fair(p, e) && BladeLogicSC.fresh(e)) {   // not in its hit frames
                    best = d;
                    next = e;
                }
            }
            if (next == null) {
                break;
            }
            done.add(next);
            if (BladeLogicSC.areaHit(p, blade, next, damage * BladeFeature.chainMul(i) * area)) {
                line(p, from, next, "witchMagic");
                from = next;
                jumps++;
            }
        }
        if (jumps > 0) {
            ToolGasSC.drainExact(p, f.gas(), f.gasMb());
            BladeLogicSC.heat(p, blade, f.heat);
            p.worldObj.playSoundAtEntity(p, "mob.blaze.hit", 0.5F, 1.8F);
        }
    }

    /**
     * Item.onEntitySwing (ItemBladeSC, server): the spear and the whip reach further than the client sends
     * hits - the swing's ray finds a mob between the client's reach and the form's and hits it.
     */
    static void swing(EntityPlayer p, ItemStack stack) {
        if (p.worldObj.isRemote || stack == null || stack != p.getCurrentEquippedItem() || !ToolLevelSC.isBlade(stack) || !ItemBladeSC.isLit(stack)) {
            return;
        }
        double reach = BladeFeature.formReach(effectiveForm(p, stack));
        double own = p.capabilities.isCreativeMode ? 6 : BladeFeature.VANILLA_REACH;
        if (reach <= own) {
            return;
        }
        State s = state(p);
        long now = p.worldObj.getTotalWorldTime();
        if (now - s.reachAt < REACH_GAP && now >= s.reachAt) {
            return;
        }
        Entity e = SingularPowersSC.lookEntity(p, reach, true, false);
        if (e == null || !BladeLogicSC.fair(p, e) || eyeDistance(p, e) <= own - 0.25 || !BladeLogicSC.claimed(p, e)) {
            return;                                         // within the client's reach its own hit comes
        }
        s.reachAt = now;
        BladeLogicSC.attack(p, stack, e);
    }

    // ================================================================== blocking: perfect parry (LivingAttackEvent)

    /** A blockable hit within PARRY_WINDOW of raising the block: none of it, slowness round, the next hit x2. @return cancel it */
    public static boolean parry(EntityPlayer p, DamageSource src) {
        if (p == null || p.worldObj.isRemote || src == null || src.isUnblockable() || !p.isBlocking()) {
            return false;
        }
        // К-2: a blow from someone (a mob, a shooter's arrow) that would land - not lava, fire or a cactus, and not
        // a hit the damage immunity right after another one would ignore anyway
        if (src.getEntity() == null || p.hurtResistantTime > p.maxHurtResistantTime / 2) {
            return false;
        }
        ItemStack blade = BladeLogicSC.held(p);
        BladeFeature f = BladeFeature.PERFECT_PARRY;
        if (!ToolLevelSC.isBlade(blade) || !BladeLogicSC.active(p, f)) {
            return false;
        }
        State s = state(p);
        long now = p.worldObj.getTotalWorldTime();
        if (!s.blocking) {                                  // raised this very tick (the player's tick comes later)
            blockStarted(s, now);
            s.blocking = true;
        }
        if (s.parryUsed || now - s.blockStart > BladeFeature.PARRY_WINDOW) {
            return false;
        }
        if (!ToolGasSC.drainExact(p, f.gas(), f.gasMb())) {
            ToolGasSC.noGasMessage(p, f.gas(), f.gasMb());
            return false;
        }
        s.parryUsed = true;
        s.parryUntil = now + BladeFeature.BONUS_KEEP;      // renewed when the block ends
        double r = BladeFeature.PARRY_SLOW_RADIUS;
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, r, r))) {
            Entity e = (Entity) o;
            if (BladeLogicSC.fair(p, e) && p.getDistanceSqToEntity(e) <= r * r && !guarded(p, e)) {
                ((EntityLivingBase) e).addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, BladeFeature.PARRY_SLOW_TICKS,
                        BladeFeature.PARRY_SLOW_AMP));
            }
        }
        BladeLogicSC.heat(p, blade, f.heat);
        p.worldObj.playSoundAtEntity(p, "random.anvil_land", 0.5F, 1.8F);
        spark(p.worldObj, "crit", p.posX, p.posY + 1.2, p.posZ, 25, 0.6, 0.3);
        return true;
    }

    // ================================================================== kills and the last chance (LivingDeathEvent)

    /** «Жатва душ»: a hostile mob (5, at most 200 a minute) or a boss (500) killed with the Singular blade in hand. */
    public static void killed(EntityLivingBase victim, DamageSource src) {
        if (victim == null || victim.worldObj.isRemote || src == null || !SingularProgressSC.countsAsMob(victim)) {
            return;
        }
        Entity killer = src.getEntity();
        if (!(killer instanceof EntityPlayerMP) || killer instanceof net.minecraftforge.common.util.FakePlayer) {
            return;
        }
        EntityPlayer p = (EntityPlayer) killer;
        ItemStack blade = BladeLogicSC.held(p);
        if (!ToolLevelSC.isBlade(blade)) {
            return;
        }
        boolean boss = victim instanceof IBossDisplayData;
        NBTTagCompound data = persisted(p);             // К-5: the minute's cap survives a death
        long minute = p.worldObj.getTotalWorldTime() / 1200L;
        if (data.getLong(KILL_MIN) != minute) {
            data.setLong(KILL_MIN, minute);
            data.setInteger(KILL_PTS, 0);
        }
        int pts = SingularLevel.killPoints(boss, data.getInteger(KILL_PTS));
        if (!boss) {
            data.setInteger(KILL_PTS, data.getInteger(KILL_PTS) + pts);
        }
        if (pts <= 0) {
            return;
        }
        if (p.isUsingItem()) {
            state(p).pendingPoints += pts;                  // not the NBT mid-block: added when the block ends
        } else {
            award(p, blade, pts);
        }
    }

    private static void award(EntityPlayer p, ItemStack blade, int pts) {
        boolean was = ToolLevelSC.pointsFull(blade);
        if (ToolLevelSC.addPoints(blade, pts) > 0 && !was && ToolLevelSC.pointsFull(blade)) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.sing.ready",
                    new ChatComponentTranslation(blade.getItem().getUnlocalizedName(blade) + ".name")));
            p.worldObj.playSoundAtEntity(p, "random.levelup", 0.6F, 1.4F);
        }
    }

    /** Guardian, level 5 «Последний шанс»: a lethal hit leaves 1 health instead, once in 5 minutes, LAST_CHANCE_SM mB. @return cancel the death */
    public static boolean lastChance(EntityPlayer p, DamageSource src) {
        if (p == null || p.worldObj.isRemote || src == DamageSource.outOfWorld) {
            return false;
        }
        ItemStack blade = BladeLogicSC.held(p);
        if (!ToolLevelSC.isBlade(blade) || !ToolLevelSC.hasBranch(p, blade, ToolLevelSC.BLADE_GUARDIAN, ToolLevelSC.BRANCH_PERK_LEVEL)
                || ToolLevelSC.cooldownLeft(blade, BladeFeature.LAST_CHANCE_KEY, p.worldObj) > 0) {
            return false;
        }
        long now = p.worldObj.getTotalWorldTime();
        NBTTagCompound data = persisted(p);
        if (data.hasKey(LAST_CHANCE_AT) && now >= data.getLong(LAST_CHANCE_AT)
                && now - data.getLong(LAST_CHANCE_AT) < ToolLevelSC.cooldownTicks(p, BladeFeature.LAST_CHANCE_COOLDOWN)) {
            return false;                                   // К-6: the player's cooldown too - a second blade doesn't reset it
        }
        if (!ToolGasSC.drainExact(p, Gas.SINGULAR_MATTER, BladeFeature.LAST_CHANCE_SM)) {
            ToolGasSC.noGasMessage(p, Gas.SINGULAR_MATTER, BladeFeature.LAST_CHANCE_SM);
            return false;
        }
        if (p.isUsingItem()) {
            BladeLogicSC.stopBlock(p);                      // the cooldown changes the blade's NBT
        }
        p.setHealth(1F);
        p.hurtResistantTime = p.maxHurtResistantTime;
        ToolLevelSC.setCooldown(blade, BladeFeature.LAST_CHANCE_KEY, p.worldObj, ToolLevelSC.cooldownTicks(p, BladeFeature.LAST_CHANCE_COOLDOWN));
        data.setLong(LAST_CHANCE_AT, now);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.blade.lastchance"));
        p.worldObj.playSoundAtEntity(p, "mob.zombie.unfect", 1F, 1.6F);
        spark(p.worldObj, "witchMagic", p.posX, p.posY + 1, p.posZ, 60, 0.6, 0.3);
        spark(p.worldObj, "portal", p.posX, p.posY + 1, p.posZ, 60, 0.6, 1.0);
        return true;
    }

    // ================================================================== key functions

    /** BladeLogicSC.runAction: the Singular key functions. */
    static void action(EntityPlayer p, ItemStack blade, BladeFeature f) {
        switch (f) {
            case GRAV_PULL:
                gravPull(p, blade);
                break;
            case RIFT_STEP:
                riftStep(p, blade);
                break;
            case GRAV_TETHER:
                tether(p, blade);
                break;
            case FORM_ATTACK:
                formAttack(p, blade);
                break;
            default:
                break;
        }
    }

    /** On, lit, the gap since the last key, off cooldown, the gas and the EU there - or why not. Nothing paid yet. */
    private static boolean ready(EntityPlayer p, ItemStack blade, BladeFeature f, IChatComponent what, int eu, Gas gas, int mb) {
        if (!BladeLogicSC.active(p, f) || !BladeLogicSC.actionGap(p)) {
            return false;
        }
        int left = ToolLevelSC.cooldownLeft(blade, f.key(), p.worldObj);
        if (left > 0) {
            ArmorLogicSC.warnArgs(p, "sc.armor.cooldown", 40, what, String.valueOf((left + 19) / 20));
            return false;
        }
        if (gas != null && mb > 0 && !ToolGasSC.has(p, gas, mb)) {
            ToolGasSC.noGasMessage(p, gas, mb);
            return false;
        }
        if (!BladeLogicSC.canPay(p, blade, eu)) {
            ArmorLogicSC.warnArgs(p, "sc.blade.low", 40, String.valueOf(eu));
            return false;
        }
        return true;
    }

    /** Pays what ready() checked, starts the cooldown (the full Singular suit: -25%), heats. */
    private static void spend(EntityPlayer p, ItemStack blade, BladeFeature f, int cooldown, int eu, Gas gas, int mb, int heat) {
        if (p.isUsingItem()) {
            BladeLogicSC.stopBlock(p);                      // the blade's NBT changes: the block goes on both sides
        }
        BladeLogicSC.pay(p, blade, eu);
        if (gas != null && mb > 0) {
            ToolGasSC.drainExact(p, gas, mb);
        }
        if (cooldown > 0) {
            ToolLevelSC.setCooldown(blade, f.key(), p.worldObj, ToolLevelSC.cooldownTicks(p, cooldown));
        }
        BladeLogicSC.markAction(p);
        BladeLogicSC.heat(p, blade, heat);
    }

    private static void noTarget(EntityPlayer p) {
        ArmorLogicSC.warnArgs(p, "sc.blade.notarget", 20);
    }

    /** Gravity pull: the target looked at within 12 is pulled in front of the player and takes a blow (bosses only the blow). */
    private static void gravPull(EntityPlayer p, ItemStack blade) {
        BladeFeature f = BladeFeature.GRAV_PULL;
        if (!ready(p, blade, f, name(f), BladeFeature.GRAV_PULL_COST, f.gas(), f.gasMb())) {
            return;
        }
        Entity e = target(p, BladeFeature.GRAV_PULL_RANGE);
        if (e == null) {
            noTarget(p);
            return;
        }
        spend(p, blade, f, f.cooldownTicks(), BladeFeature.GRAV_PULL_COST, f.gas(), f.gasMb(), f.heat);
        Entity from = e;
        if (!(e instanceof IBossDisplayData) && !guarded(p, e)) {
            line(p, p, e, "portal");
            pullTo(p, e, 1.6);
        }
        e.hurtResistantTime = 0;
        BladeLogicSC.hit(p, blade, e, onDamage(blade));
        p.worldObj.playSoundAtEntity(from, "mob.endermen.portal", 0.7F, 0.6F);
    }

    /** `e` set down `dist` blocks in front of the player (where its body fits), or flung there. */
    private static void pullTo(EntityPlayer p, Entity e, double dist) {
        Vec3 look = p.getLookVec();
        double len = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        if (len < 0.01) {
            return;
        }
        double x = p.posX + look.xCoord / len * dist, y = p.boundingBox.minY, z = p.posZ + look.zCoord / len * dist;
        AxisAlignedBB bb = e.boundingBox.getOffsetBoundingBox(x - e.posX, y - e.boundingBox.minY, z - e.posZ);
        if (e.ridingEntity == null && e.riddenByEntity == null && p.worldObj.getCollidingBoundingBoxes(e, bb).isEmpty()
                && !p.worldObj.isMaterialInBB(bb, Material.lava)) {
            double ny = y + (e.posY - e.boundingBox.minY);
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).playerNetServerHandler.setPlayerLocation(x, ny, z, e.rotationYaw, e.rotationPitch);
            } else if (e instanceof EntityLivingBase) {
                ((EntityLivingBase) e).setPositionAndUpdate(x, ny, z);
            } else {
                e.setPosition(x, ny, z);
            }
            e.motionX = e.motionY = e.motionZ = 0;
            e.fallDistance = 0;
            e.velocityChanged = true;
            return;
        }
        double dx = x - e.posX, dy = y - e.posY, dz = z - e.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 0.1) {
            double v = Math.min(2.0, 0.3 + d * 0.2);
            e.motionX = dx / d * v;
            e.motionY = Math.max(0.3, dy / d * v + 0.2);
            e.motionZ = dz / d * v;
            e.fallDistance = 0;
            e.velocityChanged = true;
        }
    }

    /** Rift step: behind the target looked at within 16, facing it, and a blow x1.5. Duelist 5: three in a row before the cooldown. */
    private static void riftStep(EntityPlayer p, ItemStack blade) {
        BladeFeature f = BladeFeature.RIFT_STEP;
        if (!ready(p, blade, f, name(f), BladeFeature.RIFT_COST, f.gas(), f.gasMb())) {
            return;
        }
        Entity e = target(p, BladeFeature.RIFT_RANGE);
        if (e == null) {
            noTarget(p);
            return;
        }
        double dx = e.posX - p.posX, dz = e.posZ - p.posZ, d = Math.sqrt(dx * dx + dz * dz);
        if (d < 0.01) {
            float yaw = p.rotationYaw * (float) Math.PI / 180F;
            dx = -MathHelper.sin(yaw);
            dz = MathHelper.cos(yaw);
            d = 1;
        }
        double off = e.width / 2 + 0.9;
        double x = e.posX + dx / d * off, z = e.posZ + dz / d * off, y = e.boundingBox.minY;
        if (!fits(p, x, y, z)) {
            y += 1;
            if (!fits(p, x, y, z)) {
                ArmorLogicSC.warnArgs(p, "sc.blade.rift.blocked", 20);
                return;
            }
        }
        State s = state(p);
        long now = p.worldObj.getTotalWorldTime();
        int cooldown = f.cooldownTicks();
        if (ToolLevelSC.hasBranch(p, blade, ToolLevelSC.BLADE_DUELIST, ToolLevelSC.BRANCH_PERK_LEVEL)) {
            if (s.riftUsed == 0 || now - s.riftFirst > BladeFeature.RIFT_CHAIN_TICKS) {
                s.riftUsed = 0;
                s.riftFirst = now;
            }
            s.riftUsed++;
            if (s.riftUsed < BladeFeature.RIFT_CHARGES) {
                cooldown = 0;
            } else {
                s.riftUsed = 0;
            }
        }
        spend(p, blade, f, cooldown, BladeFeature.RIFT_COST, f.gas(), f.gasMb(), f.heat);
        spark(p.worldObj, "portal", p.posX, p.posY + 1, p.posZ, 30, 0.3, 0.8);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 0.8F, 1.2F);
        double tx = e.posX - x, tz = e.posZ - z;
        double ty = (e.boundingBox.minY + e.height * 0.6) - (y + 1.62);
        float yaw = (float) (Math.atan2(tz, tx) * 180 / Math.PI) - 90F;
        float pitch = (float) (-Math.atan2(ty, Math.sqrt(tx * tx + tz * tz)) * 180 / Math.PI);
        if (p.ridingEntity != null) {
            p.mountEntity(null);
        }
        p.fallDistance = 0;
        if (p instanceof EntityPlayerMP) {
            ((EntityPlayerMP) p).playerNetServerHandler.setPlayerLocation(x, y, z, yaw, pitch);
        } else {
            p.setPositionAndUpdate(x, y, z);
        }
        p.worldObj.playSoundEffect(x, y, z, "mob.endermen.portal", 0.8F, 1.6F);
        e.hurtResistantTime = 0;
        BladeLogicSC.hit(p, blade, e, onDamage(blade) * BladeFeature.RIFT_MUL);
    }

    /** Gravity tether: the target looked at within 16 can't get further than 6 blocks for 5 s (not a boss). */
    private static void tether(EntityPlayer p, ItemStack blade) {
        BladeFeature f = BladeFeature.GRAV_TETHER;
        if (!ready(p, blade, f, name(f), BladeFeature.TETHER_COST, f.gas(), f.gasMb())) {
            return;
        }
        Entity e = target(p, BladeFeature.TETHER_RANGE);
        if (!(e instanceof EntityLivingBase) || e instanceof IBossDisplayData || guarded(p, e)) {
            noTarget(p);
            return;
        }
        spend(p, blade, f, f.cooldownTicks(), BladeFeature.TETHER_COST, f.gas(), f.gasMb(), f.heat);
        TETHERS.put(p.getUniqueID(), new Tether(p, (EntityLivingBase) e, p.worldObj.getTotalWorldTime() + BladeFeature.TETHER_TICKS));
        line(p, p, e, "witchMagic");
        p.worldObj.playSoundAtEntity(e, "mob.endermen.portal", 0.6F, 0.5F);
    }

    /** One tick of a tether. @return whether it holds on */
    private static boolean tetherTick(Tether t) {
        EntityLivingBase e = t.target;
        EntityPlayer p = t.p;
        if (p.isDead || e.isDead || !e.isEntityAlive() || e.worldObj != p.worldObj || p.worldObj.getTotalWorldTime() >= t.end
                || guarded(p, e)) {
            return false;                                // walked into someone's private field: let go
        }
        double dx = p.posX - e.posX, dy = p.posY - e.posY, dz = p.posZ - e.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        double leash = BladeFeature.TETHER_LEASH;
        if (d > leash * 3) {
            return false;                                   // teleported away: it snaps
        }
        if (d > leash) {
            double v = Math.min(1.0, (d - leash) * 0.35 + 0.1);
            e.motionX = dx / d * v;
            e.motionZ = dz / d * v;
            e.motionY = Math.max(e.motionY, dy / d * v);
            e.fallDistance = 0;
            e.velocityChanged = true;
        }
        if (p.worldObj.getTotalWorldTime() % 5 == 0) {
            line(p, p, e, "witchMagic");
        }
        return true;
    }

    // ================================================================== the forms' special attacks (FORM_ATTACK)

    private static void formAttack(EntityPlayer p, ItemStack blade) {
        BladeForm form = effectiveForm(p, blade);
        if (form == BladeForm.SWORD) {
            BladeLogicSC.waveAs(p, BladeFeature.FORM_ATTACK);   // the sword: the Exo wave (its own EU, the half-second gap)
            return;
        }
        int eu = BladeFeature.formAttackEu(form);
        Gas gas = BladeFeature.formAttackGas(form);
        int mb = BladeFeature.formAttackGasMb(form);
        if (!ready(p, blade, BladeFeature.FORM_ATTACK, new ChatComponentTranslation(BladeFeature.formAttackKey(form)), eu, gas, mb)) {
            return;
        }
        switch (form) {
            case SCYTHE:
                harvest(p, blade, form, eu);
                break;
            case SPEAR:
                pierce(p, blade, form, eu);
                break;
            case WHIP:
                grab(p, blade, form, eu);
                break;
            case SHIELD:
                ram(p, blade, form, eu);
                break;
            case SINGULAR:
                collapse(p, blade, form, eu, gas, mb);
                break;
            default:
                break;
        }
    }

    private static void spendForm(EntityPlayer p, ItemStack blade, BladeForm form, int eu, Gas gas, int mb) {
        spend(p, blade, BladeFeature.FORM_ATTACK, BladeFeature.formAttackCooldown(form), eu, gas, mb, BladeFeature.formAttackHeat(form));
    }

    /** Scythe «Жатва»: a spin - everything within HARVEST_RADIUS round the player takes a full blow and is thrown back. */
    private static void harvest(EntityPlayer p, ItemStack blade, BladeForm form, int eu) {
        spendForm(p, blade, form, eu, null, 0);
        double r = BladeFeature.HARVEST_RADIUS;
        float damage = onDamage(blade) * BladeLogicSC.areaMul(p, blade);
        for (Object o : p.worldObj.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, 1.5, r))) {
            Entity e = (Entity) o;
            double dx = e.posX - p.posX, dz = e.posZ - p.posZ;
            if (Math.sqrt(dx * dx + dz * dz) <= r + e.width / 2 && BladeLogicSC.areaHit(p, blade, e, damage)) {
                ArmorLogicSC.push(p, e, 0.4, 0.2);
            }
        }
        for (int i = 0; i < 24; i++) {
            double a = i * Math.PI / 12;
            spark(p.worldObj, "crit", p.posX + Math.cos(a) * 3, p.posY + 1, p.posZ + Math.sin(a) * 3, 2, 0.1, 0.05);
        }
        p.worldObj.playSoundAtEntity(p, "mob.irongolem.throw", 1F, 1.2F);
        p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.5F, 1.8F);
    }

    /** Spear «Пронзание»: a dash up to PIERCE_RANGE along the look (along the ground when looking down), through everything on the way. */
    private static void pierce(final EntityPlayer p, ItemStack blade, BladeForm form, int eu) {
        Vec3 look = p.getLookVec();
        double ly = p.onGround && look.yCoord < 0 ? 0 : look.yCoord;
        ArmorLogicSC.SpaceCheck space = new ArmorLogicSC.SpaceCheck() {
            @Override
            public boolean free(double x, double y, double z) {
                return fits(p, x, y, z);
            }
        };
        double dist = ArmorLogicSC.phaseDistance(space, p.posX, p.boundingBox.minY, p.posZ, look.xCoord, ly, look.zCoord, BladeFeature.PIERCE_RANGE);
        if (dist < 1.0) {
            ArmorLogicSC.warnArgs(p, "sc.blade.dash.blocked", 20);
            return;
        }
        spendForm(p, blade, form, eu, null, 0);
        double len = Math.sqrt(look.xCoord * look.xCoord + ly * ly + look.zCoord * look.zCoord);
        double x = p.posX + look.xCoord / len * dist, y = p.boundingBox.minY + ly / len * dist, z = p.posZ + look.zCoord / len * dist;
        Vec3 start = Vec3.createVectorHelper(p.posX, p.boundingBox.minY + 1, p.posZ);
        Vec3 end = Vec3.createVectorHelper(x, y + 1, z);
        float damage = onDamage(blade) * BladeFeature.PIERCE_MUL * BladeLogicSC.areaMul(p, blade);
        for (Entity e : BladeLogicSC.along(p, start, end)) {
            BladeLogicSC.areaHit(p, blade, e, damage);
        }
        BladeLogicSC.particles(p, start, end, "magicCrit", 2);
        if (p.ridingEntity != null) {
            p.mountEntity(null);
        }
        p.fallDistance = 0;
        p.setPositionAndUpdate(x, y, z);
        p.worldObj.playSoundAtEntity(p, "mob.enderdragon.wings", 0.6F, 1.8F);
    }

    /** Whip «Захват»: the mob looked at within GRAB_RANGE is pulled to the player; no mob - the player is pulled to the block looked at. */
    private static void grab(EntityPlayer p, ItemStack blade, BladeForm form, int eu) {
        double range = BladeFeature.GRAB_RANGE;
        Entity e = target(p, range);
        if (e != null && !(e instanceof IBossDisplayData) && !guarded(p, e)) {
            spendForm(p, blade, form, eu, null, 0);
            double dx = p.posX - e.posX, dy = p.posY - e.posY, dz = p.posZ - e.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > 0.1) {
                double v = Math.min(2.2, 0.3 + d * 0.16);
                e.motionX = dx / d * v;
                e.motionY = Math.max(0.35, dy / d * v + 0.25);
                e.motionZ = dz / d * v;
                e.fallDistance = 0;
                e.velocityChanged = true;
            }
            line(p, p, e, "crit");
            p.worldObj.playSoundAtEntity(p, "random.bow", 0.8F, 0.6F);
            return;
        }
        Vec3 eye = BladeLogicSC.eyes(p);
        Vec3 look = p.getLookVec();
        Vec3 far = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition hit = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord), far);
        if (hit == null || hit.typeOfHit != MovingObjectPosition.MovingObjectType.BLOCK || hit.hitVec == null) {
            noTarget(p);
            return;
        }
        spendForm(p, blade, form, eu, null, 0);
        double dx = hit.hitVec.xCoord - p.posX, dy = hit.hitVec.yCoord - (p.posY + 1), dz = hit.hitVec.zCoord - p.posZ;
        double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
        if (d > 0.5) {
            double v = Math.min(2.2, 0.4 + d * 0.14);
            p.motionX = dx / d * v;
            p.motionY = dy / d * v + 0.35;
            p.motionZ = dz / d * v;
            p.fallDistance = 0;
            p.velocityChanged = true;
        }
        BladeLogicSC.particles(p, eye, hit.hitVec, "crit", 1);
        p.worldObj.playSoundAtEntity(p, "random.bow", 0.8F, 0.5F);
    }

    /** Shield «Таран»: a rush RAM_RANGE forward, everything on the way takes half a blow and is thrown aside. */
    private static void ram(EntityPlayer p, ItemStack blade, BladeForm form, int eu) {
        Vec3 look = p.getLookVec();
        double len = Math.sqrt(look.xCoord * look.xCoord + look.zCoord * look.zCoord);
        if (len < 0.01) {
            return;                                         // straight up / down: nowhere to rush
        }
        double lx = look.xCoord / len, lz = look.zCoord / len;
        spendForm(p, blade, form, eu, null, 0);
        Vec3 start = Vec3.createVectorHelper(p.posX, p.boundingBox.minY + 0.9, p.posZ);
        Vec3 end = start.addVector(lx * BladeFeature.RAM_RANGE, 0, lz * BladeFeature.RAM_RANGE);
        MovingObjectPosition wall = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(start.xCoord, start.yCoord, start.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        if (wall != null && wall.hitVec != null) {
            end = wall.hitVec;
        }
        float damage = onDamage(blade) * BladeFeature.RAM_MUL * BladeLogicSC.areaMul(p, blade);
        for (Entity e : BladeLogicSC.along(p, start, end)) {
            if (BladeLogicSC.areaHit(p, blade, e, damage) && !(e instanceof IBossDisplayData)) {
                e.motionX += lx * 1.6;
                e.motionZ += lz * 1.6;
                e.motionY = Math.max(e.motionY, 0.5);
                e.velocityChanged = true;
            }
        }
        p.motionX = lx * 1.5;
        p.motionZ = lz * 1.5;
        p.motionY = Math.max(p.motionY, 0.25);
        p.fallDistance = 0;
        p.velocityChanged = true;
        BladeLogicSC.particles(p, start, end, "largesmoke", 1);
        p.worldObj.playSoundAtEntity(p, "random.anvil_land", 0.6F, 0.8F);
    }

    /** Singular «Коллапс»: COLLAPSE_TICKS of pulling everything within 8 (Destroyer 5: 12) to a point ahead, then a blast - no blocks broken. */
    private static void collapse(EntityPlayer p, ItemStack blade, BladeForm form, int eu, Gas gas, int mb) {
        spendForm(p, blade, form, eu, gas, mb);
        Vec3 start = BladeLogicSC.eyes(p);
        Vec3 at = BladeLogicSC.beamEnd(p, start, BladeFeature.COLLAPSE_AT);
        Vec3 look = p.getLookVec();
        at = at.addVector(-look.xCoord * 0.5, -look.yCoord * 0.5, -look.zCoord * 0.5);   // out of the wall it hit
        double r = ToolLevelSC.hasBranch(p, blade, ToolLevelSC.BLADE_DESTROYER, ToolLevelSC.BRANCH_PERK_LEVEL)
                ? BladeFeature.COLLAPSE_RADIUS_DESTROYER : BladeFeature.COLLAPSE_RADIUS;
        float damage = onDamage(blade) * BladeFeature.COLLAPSE_MUL * BladeLogicSC.areaMul(p, blade);
        COLLAPSES.add(new Collapse(p.worldObj, p, blade, at.xCoord, at.yCoord, at.zCoord, r,
                p.worldObj.getTotalWorldTime() + BladeFeature.COLLAPSE_TICKS, damage));
        p.worldObj.playSoundEffect(at.xCoord, at.yCoord, at.zCoord, "mob.endermen.portal", 2F, 0.3F);
    }

    private static boolean allowed(Collapse c, Entity e) {
        Boolean ok = c.allowed.get(e.getEntityId());
        if (ok == null) {
            ok = Boolean.valueOf(BladeLogicSC.fair(c.owner, e) && BladeLogicSC.claimed(c.owner, e));
            c.allowed.put(e.getEntityId(), ok);
        }
        return ok.booleanValue();
    }

    private static List<Entity> inCollapse(Collapse c) {
        List<Entity> out = new ArrayList<Entity>();
        double r = c.r;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(c.x - r, c.y - r, c.z - r, c.x + r, c.y + r, c.z + r);
        for (Object o : c.world.getEntitiesWithinAABB(EntityLivingBase.class, box)) {
            Entity e = (Entity) o;
            if (e != c.owner && e.isEntityAlive() && e.getDistance(c.x, c.y, c.z) <= r && allowed(c, e)) {
                out.add(e);
            }
        }
        return out;
    }

    private static void collapseTick(Collapse c, long now) {
        for (Entity e : inCollapse(c)) {
            if (e instanceof IBossDisplayData || guarded(c.owner, e)) {
                continue;
            }
            double dx = c.x - e.posX, dy = c.y - (e.posY + e.height / 2), dz = c.z - e.posZ, d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d < 0.4) {
                continue;
            }
            double s = 0.06 + (1 - d / c.r) * 0.12;
            e.motionX += dx / d * s;
            e.motionY += dy / d * s + 0.04;                 // against gravity
            e.motionZ += dz / d * s;
            double v = Math.sqrt(e.motionX * e.motionX + e.motionY * e.motionY + e.motionZ * e.motionZ);
            if (v > 0.7) {
                e.motionX *= 0.7 / v;
                e.motionY *= 0.7 / v;
                e.motionZ *= 0.7 / v;
            }
            e.fallDistance = 0;
            if (now % 4 == 0) {
                e.velocityChanged = true;
            }
        }
        if (now % 4 == 0) {
            spark(c.world, "portal", c.x, c.y, c.z, 40, 1.0, 1.4);
            spark(c.world, "largesmoke", c.x, c.y, c.z, 3, 0.15, 0.0);
        }
        if (now % 20 == 0) {
            c.world.playSoundEffect(c.x, c.y, c.z, "portal.portal", 0.6F, 0.5F);
        }
    }

    /** The blast: full damage at the centre, half at the edge, everything thrown out. No blocks are touched. */
    private static void collapseBlast(Collapse c) {
        for (Entity e : inCollapse(c)) {
            double d = e.getDistance(c.x, c.y, c.z);
            e.hurtResistantTime = 0;
            BladeLogicSC.hit(c.owner, c.blade, e, (float) (c.damage * (1 - 0.5 * Math.min(1, d / c.r))));
            if (!(e instanceof IBossDisplayData) && !guarded(c.owner, e)) {
                double dx = e.posX - c.x, dz = e.posZ - c.z, h = Math.max(0.3, Math.sqrt(dx * dx + dz * dz));
                e.motionX += dx / h * 1.2;
                e.motionZ += dz / h * 1.2;
                e.motionY = Math.max(e.motionY, 0.6);
                e.velocityChanged = true;
            }
        }
        spark(c.world, "hugeexplosion", c.x, c.y, c.z, 1, 0, 0);
        spark(c.world, "portal", c.x, c.y, c.z, 150, 1.5, 2.0);
        c.world.playSoundEffect(c.x, c.y, c.z, "random.explode", 1.5F, 0.6F);
    }

    /** How many collapses run now (self-test / debug). */
    public static int collapseCount() {
        return COLLAPSES.size();
    }
}
