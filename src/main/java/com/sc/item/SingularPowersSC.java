package com.sc.item;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.UUID;
import java.util.WeakHashMap;
import java.util.concurrent.ConcurrentLinkedQueue;

import com.sc.util.ArmorFeature;
import com.sc.util.ArmorGasSC;
import com.sc.util.ArmorGasSC.Gas;
import com.sc.util.SingularCooldowns;
import com.sc.util.SingularLevel;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityOwnable;
import net.minecraft.entity.IProjectile;
import net.minecraft.entity.boss.IBossDisplayData;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.item.EntityXPOrb;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.passive.EntityHorse;
import net.minecraft.entity.passive.EntityTameable;
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
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.DimensionManager;

/**
 * The Singular suit's key functions of stage 2b (docs/plan-singular-armor.md §3-§5): Н10 gravity
 * press, Н8 gravity grab, Н4 time slowing, Н3 black hole, Н17 gravity dome, К1 "Singularity" mode -
 * and the К1 multipliers the other Singular functions ask (singularBoost, rangeMul, costMul).
 *
 * Server only. The key's network message only queues the press (the network thread must not touch
 * the world); worldTick runs it and every effect that lasts (pinned mobs, the held mob, the fields)
 * on the server thread. Nothing here breaks blocks.
 */
public final class SingularPowersSC {

    /** Player entity data: К1's boost and weakness end ticks, its state (for the chat lines), Н4's end tick, the key's last press. */
    static final String BOOST_END = "scSingBoostEnd", WEAK_END = "scSingWeakEnd", BOOST_STATE = "scSingBoostState",
            SLOW_END = "scTimeSlowEnd", KEY_AT = "scSingKeyAt";
    /** BOOST_END, WEAK_END, SLOW_END - for SingularCooldowns.syncAll (the client's HUD after a relog). */
    public static final String[] STATE_KEYS = {BOOST_END, WEAK_END, SLOW_END};
    /** Persisted with the player (stage 3's task "30 mobs by the black hole"): mobs the black hole has killed. */
    public static final String HOLE_KILLS = "scHoleKills";
    /** Entity data of a projectile Н4 has slowed (only once). */
    private static final String SLOWED = "scTimeSlowed";

    private static final int KIND_SLOW = 0, KIND_HOLE = 1, KIND_DOME = 2;

    /** Mobs pinned by Н10 and the tick they're let go. */
    private static final Map<EntityLivingBase, Long> PINNED = new WeakHashMap<EntityLivingBase, Long>();
    /** Н8: the mob each player holds. */
    private static final Map<UUID, Grab> GRABS = new HashMap<UUID, Grab>();
    /** Н4, Н3, Н17 while they last. */
    private static final List<Field> FIELDS = new ArrayList<Field>();
    /** Key presses from the network thread, run on the server thread. */
    private static final ConcurrentLinkedQueue<Object[]> PENDING = new ConcurrentLinkedQueue<Object[]>();

    private SingularPowersSC() {
    }

    private static final class Grab {
        final EntityPlayer p;
        final EntityLiving e;
        final long end;

        Grab(EntityPlayer p, EntityLiving e, long end) {
            this.p = p;
            this.e = e;
            this.end = end;
        }
    }

    private static final class Field {
        final int kind;
        final World world;
        final EntityPlayer owner;
        final double x, y, z, r;
        final long start, end;
        final List<EntityFireball> slowed = new ArrayList<EntityFireball>();

        Field(int kind, World world, EntityPlayer owner, double x, double y, double z, double r, long start, long end) {
            this.kind = kind;
            this.world = world;
            this.owner = owner;
            this.x = x;
            this.y = y;
            this.z = z;
            this.r = r;
            this.start = start;
            this.end = end;
        }
    }

    // ================================================================== К1: the multipliers

    /** Pure: the К1 multiplier at world tick `now` - BOOST_MUL while boosted, WEAK_MUL while weakened after it, 1 otherwise. */
    public static float boostAt(long now, long boostEnd, long weakEnd) {
        return now < boostEnd ? ArmorFeature.BOOST_MUL : now < weakEnd ? ArmorFeature.WEAK_MUL : 1F;
    }

    /**
     * К1 "Singularity": the multiplier of the Singular functions' effect for this player (2 boosted,
     * 0.5 weakened, 1 normally): the flight speed, Н2's share of damage, through rangeMul the phase
     * dash, the press and the black hole. Server (the client doesn't know it - it gets the results).
     */
    public static float singularBoost(EntityPlayer p) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote) {
            return 1F;
        }
        NBTTagCompound d = p.getEntityData();
        return boostAt(p.worldObj.getTotalWorldTime(), d.getLong(BOOST_END), d.getLong(WEAK_END));
    }

    /** Pure: what a reach is multiplied by at the boost `boost` (x2 -> x1.5, x0.5 -> x0.75). */
    public static float rangeMul(float boost) {
        return 1F + (boost - 1F) * 0.5F;
    }

    /** Pure: the gas / EU multiplier at the boost `boost` - double while boosted, as usual otherwise. */
    public static float costMulFor(float boost) {
        return boost > 1F ? 2F : 1F;
    }

    /** К1: the Singular functions' gas and EU multiplier for this player. */
    public static float costMul(EntityPlayer p) {
        return costMulFor(singularBoost(p));
    }

    /** К1: the suit heats twice as hard while boosted. */
    public static float heatMul(EntityPlayer p) {
        return costMul(p);
    }

    /** `mb` at this player's К1 multiplier, whole mB. */
    public static int scaled(EntityPlayer p, int mb) {
        return (int) Math.ceil(mb * costMul(p));
    }

    /** Н4 is running for this player (server). */
    public static boolean timeSlowActive(EntityPlayer p) {
        return p != null && p.worldObj != null && !p.worldObj.isRemote
                && p.getEntityData().getLong(SLOW_END) > p.worldObj.getTotalWorldTime();
    }

    // ================================================================== the keys

    /** From the network thread: the key of a Singular key function was pressed - queued for the server thread. */
    public static void key(EntityPlayerMP p, ArmorFeature f) {
        if (p != null && f != null) {
            PENDING.add(new Object[]{p, f});
        }
    }

    private static void runKey(EntityPlayerMP p, ArmorFeature f) {
        if (p.isDead || p.getHealth() <= 0) {
            return;
        }
        NBTTagCompound data = p.getEntityData();
        long now = p.worldObj.getTotalWorldTime();
        String k = KEY_AT + f.ordinal();
        if (data.hasKey(k) && now - data.getLong(k) < 4 && now >= data.getLong(k)) {
            return;                                             // one press, two messages
        }
        data.setLong(k, now);
        if (f == ArmorFeature.GRAV_GRAB && GRABS.containsKey(p.getUniqueID())) {
            release(GRABS.get(p.getUniqueID()), true);          // the second press throws (no cooldown check)
            return;
        }
        if (!ready(p, f)) {
            return;
        }
        switch (f) {
            case GRAV_PRESS:
                press(p);
                break;
            case GRAV_GRAB:
                grab(p);
                break;
            case TIME_SLOW:
                timeSlow(p);
                break;
            case BLACK_HOLE:
                blackHole(p);
                break;
            case GRAV_DOME:
                dome(p);
                break;
            case SINGULARITY:
                singularity(p);
                break;
            default:
                break;
        }
    }

    static IChatComponent name(ArmorFeature f) {
        return new ChatComponentTranslation("sc.armorfn." + f.name().toLowerCase(Locale.ROOT));
    }

    /** Worn, open at the piece's level and branch, able to work, off cooldown - or a chat line why not. */
    private static boolean ready(EntityPlayer p, ArmorFeature f) {
        ItemStack s = ArmorLogicSC.piece(p, f.piece);
        if (s == null || !f.availableIn(ArmorLogicSC.suitOf(s), f.piece)) {
            ArmorLogicSC.warnArgs(p, "sc.armor.sing.unavailable", 40, name(f));
            return false;
        }
        if (!SingularLevel.unlocked(p, f, s)) {
            ArmorLogicSC.warnArgs(p, "sc.armorkey.locked", 40, name(f), String.valueOf(SingularLevel.requiredLevel(f)));
            return false;
        }
        if (!SingularLevel.branchAllowed(p, f, s)) {
            ArmorLogicSC.warnArgs(p, SingularLevel.branchChoice(s, SingularLevel.requiredLevel(f)) == SingularLevel.BRANCH_NONE
                    ? "sc.armor.sing.branch.choose" : "sc.armor.sing.branch", 40, name(f));
            return false;
        }
        if (!ArmorLogicSC.active(p, f)) {
            ArmorLogicSC.warnArgs(p, f.needsFullSet() ? "sc.armor.sing.unavailable.set" : "sc.armor.sing.unavailable", 40, name(f));
            return false;
        }
        int left = SingularCooldowns.get(p, f);
        if (left > 0) {
            ArmorLogicSC.cooldownWarn(p, f, left);
            return false;
        }
        return true;
    }

    /**
     * The suit holds the gases (`mb` of each of `gases`), `eu` in the function's piece and a share
     * `share` of the whole suit's charge - all at the К1 multiplier; or a chat line with the first
     * thing missing.
     */
    private static boolean affordable(EntityPlayer p, ArmorFeature f, Gas[] gases, int[] mb, int eu, float share) {
        float mul = costMul(p);
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        for (int i = 0; i < gases.length; i++) {
            int need = (int) Math.ceil(mb[i] * mul);
            if (ArmorGasSC.amountOf(worn, gases[i]) < need) {
                ArmorLogicSC.warnArgs(p, "sc.armor.sing.nogas", 40, name(f), String.valueOf(need),
                        new ChatComponentTranslation("sc.gas." + gases[i].key()));
                return false;
            }
        }
        if (eu > 0) {
            int need = (int) Math.ceil(eu * ArmorLogicSC.costMul(p) * mul * SingularLevel.euMul(ArmorLogicSC.piece(p, f.piece)));
            if (ItemArmorSC.chargeOf(ArmorLogicSC.piece(p, f.piece)) < need) {
                ArmorLogicSC.warnArgs(p, "sc.armor.sing.noeu", 40, name(f), String.valueOf(need));
                return false;
            }
        }
        if (share > 0 && !ArmorLogicSC.canPaySuitShare(p, share * mul)) {
            ArmorLogicSC.warnArgs(p, "sc.armor.sing.nocharge", 40, name(f), Math.round(share * mul * 100) + "%");
            return false;
        }
        return true;
    }

    /** Pays what affordable() checked. */
    private static void payAll(EntityPlayer p, ArmorFeature f, Gas[] gases, int[] mb, int eu, float share) {
        float mul = costMul(p);
        ItemStack[] worn = ArmorGasSC.wornSet(p);
        for (int i = 0; i < gases.length; i++) {
            ArmorGasSC.drainExact(worn, gases[i], (int) Math.ceil(mb[i] * mul));
        }
        if (eu > 0) {
            ArmorLogicSC.pay(p, f, eu);                         // x the power mode and К1
        }
        if (share > 0) {
            ArmorLogicSC.paySuitShare(p, share * mul);
        }
        SingularProgressSC.keyUsed(p, f);                       // ОЧ5: points for the use
    }

    /** The private field covering the entity that refuses this player, or null: Н8 doesn't take its mobs, Н3 its items. */
    private static com.sc.tileentity.TileEntityFieldGeneratorSC guardField(EntityPlayer p, Entity e) {
        return com.sc.ShieldEventHandler.privateFieldAgainst(e.worldObj, p, MathHelper.floor_double(e.posX),
                MathHelper.floor_double(e.posY + e.height / 2), MathHelper.floor_double(e.posZ));
    }

    private static WorldServer ws(World w) {
        return w instanceof WorldServer ? (WorldServer) w : null;
    }

    private static void particles(World w, String name, double x, double y, double z, int count, double spread, double speed) {
        WorldServer s = ws(w);
        if (s != null) {
            s.func_147487_a(name, x, y, z, count, spread, spread, spread, speed);
        }
    }

    // ================================================================== Н10 gravity press

    /** Н10: every hostile mob within PRESS_RADIUS (x1.5 boosted) pinned to the ground PRESS_TICKS - no moving, no jumping. */
    private static void press(EntityPlayerMP p) {
        ArmorFeature f = ArmorFeature.GRAV_PRESS;
        Gas[] g = {Gas.HELIUM, Gas.DEUTERIUM};
        int[] mb = {ArmorFeature.SING_HE_PRESS, ArmorFeature.SING_D_PRESS};
        if (!affordable(p, f, g, mb, ArmorFeature.PRESS_EU, 0F)) {
            return;
        }
        payAll(p, f, g, mb, ArmorFeature.PRESS_EU, 0F);
        double r = ArmorFeature.PRESS_RADIUS * rangeMul(singularBoost(p));
        long end = p.worldObj.getTotalWorldTime() + ArmorFeature.PRESS_TICKS;
        int n = 0;
        for (EntityLivingBase e : ArmorLogicSC.mobsAround(p, r)) {
            PINNED.put(e, end);
            e.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, ArmorFeature.PRESS_TICKS, 4));
            e.motionX = 0;
            e.motionZ = 0;
            e.motionY = -0.8;
            e.velocityChanged = true;
            particles(p.worldObj, "largesmoke", e.posX, e.posY + 0.2, e.posZ, 6, 0.3, 0.02);
            n++;
        }
        SingularCooldowns.set(p, f, ArmorFeature.PRESS_COOLDOWN);
        ArmorLogicSC.addHeat(p, f.heat);
        particles(p.worldObj, "portal", p.posX, p.posY + 0.3, p.posZ, 80, r / 2, 0.4);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "random.anvil_land", 0.7F, 0.5F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.press", String.valueOf(n)));
    }

    /** Whether Н10 holds this mob down now. */
    public static boolean pinned(EntityLivingBase e) {
        Long end = PINNED.get(e);
        return end != null && e.worldObj != null && end > e.worldObj.getTotalWorldTime();
    }

    /** LivingJumpEvent (any living, server): a pinned mob's jump goes nowhere. */
    public static void onJump(EntityLivingBase e) {
        if (e != null && !e.worldObj.isRemote && !PINNED.isEmpty() && pinned(e)) {
            e.motionY = 0;
        }
    }

    private static void pinTick(EntityLivingBase e) {
        e.motionX = 0;
        e.motionZ = 0;
        if (!e.onGround) {
            e.motionY = Math.min(e.motionY, -0.5);             // flying ones come down too
        } else if (e.motionY > 0) {
            e.motionY = 0;
        }
    }

    // ================================================================== Н8 gravity grab

    /**
     * The nearest entity the player looks at within `range` that `pick` accepts - not behind a block.
     * Server: the eyes are 1.62 above the feet.
     */
    static Entity lookEntity(EntityPlayer p, double range, boolean livingOnly, boolean grabbable) {
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + (p.worldObj.isRemote ? 0 : 1.62), p.posZ);
        Vec3 look = p.getLookVec();
        Vec3 end = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition block = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        double best = block != null && block.hitVec != null ? eye.distanceTo(block.hitVec) : range;
        Entity found = null;
        List list = p.worldObj.getEntitiesWithinAABBExcludingEntity(p,
                p.boundingBox.addCoord(look.xCoord * range, look.yCoord * range, look.zCoord * range).expand(1, 1, 1));
        for (Object o : list) {
            Entity e = (Entity) o;
            if (!e.isEntityAlive() || (livingOnly && !(e instanceof EntityLivingBase)) || (grabbable && !grabbable(e, p))) {
                continue;
            }
            AxisAlignedBB bb = e.boundingBox.expand(0.3, 0.3, 0.3);
            double d;
            if (bb.isVecInside(eye)) {
                d = 0;
            } else {
                MovingObjectPosition hit = bb.calculateIntercept(eye, end);
                if (hit == null) {
                    continue;
                }
                d = eye.distanceTo(hit.hitVec);
            }
            if (d < best) {
                best = d;
                found = e;
            }
        }
        return found;
    }

    /** Н8 takes a mob or an animal - not a player, not a boss. */
    static boolean grabbable(Entity e) {
        return e instanceof EntityLiving && !(e instanceof IBossDisplayData) && e.isEntityAlive();
    }

    /**
     * СБ-3: Н8 for this player - not a mob a player rides (a horse, a pig with its rider), not someone else's
     * tamed animal (a wolf, an ocelot, a horse).
     */
    public static boolean grabbable(Entity e, EntityPlayer by) {
        if (!grabbable(e)) {
            return false;
        }
        if (e.riddenByEntity instanceof EntityPlayer) {
            return false;
        }
        String owner = ownerOf(e);
        return owner == null || owner.isEmpty() || (by != null && (owner.equals(by.getUniqueID().toString())
                || owner.equalsIgnoreCase(by.getCommandSenderName())));
    }

    /** A tamed animal's owner (1.7.10: the UUID string, an old save's name), or null when it has none. */
    public static String ownerOf(Entity e) {
        if (e instanceof EntityTameable) {
            EntityTameable t = (EntityTameable) e;
            return t.isTamed() ? t.func_152113_b() : null;
        }
        if (e instanceof IEntityOwnable) {
            return ((IEntityOwnable) e).func_152113_b();
        }
        if (e instanceof EntityHorse) {
            EntityHorse h = (EntityHorse) e;
            return h.isTame() ? h.func_152119_ch() : null;
        }
        return null;
    }

    /** A mob's name for chat: its name tag, or its kind (translated on the player's side). */
    static IChatComponent entityName(Entity e) {
        if (e instanceof EntityLiving && ((EntityLiving) e).hasCustomNameTag()) {
            return new ChatComponentText(((EntityLiving) e).getCustomNameTag());
        }
        String s = EntityList.getEntityString(e);
        return s == null ? new ChatComponentText(e.getCommandSenderName()) : new ChatComponentTranslation("entity." + s + ".name");
    }

    /** Н8, the first press: the mob looked at within GRAB_RANGE floats GRAB_HOLD blocks in front of the wearer. */
    private static void grab(EntityPlayerMP p) {
        ArmorFeature f = ArmorFeature.GRAV_GRAB;
        Entity t = lookEntity(p, ArmorFeature.GRAB_RANGE, true, true);
        if (t == null) {
            ArmorLogicSC.warnArgs(p, "sc.armor.grab.none", 20);
            return;
        }
        com.sc.tileentity.TileEntityFieldGeneratorSC fg = guardField(p, t);
        if (fg != null) {
            ArmorLogicSC.warnArgs(p, "sc.field.private", 20, fg.getOwner());
            return;
        }
        if (!BladeLogicSC.claimed(p, t)) {                      // another mod's claim refused it
            ArmorLogicSC.warnArgs(p, "sc.armor.grab.none", 20);
            return;
        }
        Gas[] g = {Gas.HELIUM};
        int[] mb = {ArmorFeature.SING_HE_GRAB};
        if (!affordable(p, f, g, mb, ArmorFeature.GRAB_EU, 0F)) {
            return;
        }
        payAll(p, f, g, mb, ArmorFeature.GRAB_EU, 0F);
        EntityLiving e = (EntityLiving) t;
        if (e.ridingEntity != null) {
            e.mountEntity(null);
        }
        if (e.riddenByEntity != null) {
            e.riddenByEntity.mountEntity(null);
        }
        GRABS.put(p.getUniqueID(), new Grab(p, e, p.worldObj.getTotalWorldTime() + ArmorFeature.GRAB_TICKS));
        ArmorLogicSC.addHeat(p, f.heat);
        p.worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "mob.endermen.portal", 0.6F, 0.7F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.grab", entityName(e)));
    }

    /** Every tick while held: the mob is drawn to the point in front of the eyes; time out - thrown; lost - dropped. */
    private static void grabTick(Grab g, long now) {
        EntityPlayer p = g.p;
        EntityLiving e = g.e;
        if (p.isDead || !e.isEntityAlive() || e.worldObj != p.worldObj || p.getDistanceSqToEntity(e) > 16 * 16
                || !SingularLevel.isSingular(ArmorGasSC.worn(p, ArmorGasSC.CHEST))) {      // СБ-5: the chestplate taken off - let go
            release(g, false);
            return;
        }
        if (now >= g.end) {
            release(g, true);
            return;
        }
        Vec3 look = p.getLookVec();
        double tx = p.posX + look.xCoord * ArmorFeature.GRAB_HOLD;
        double ty = p.posY + 1.62 + look.yCoord * ArmorFeature.GRAB_HOLD - e.height / 2;
        double tz = p.posZ + look.zCoord * ArmorFeature.GRAB_HOLD;
        e.motionX = clamp((tx - e.posX) * 0.35, 1.0);
        e.motionY = clamp((ty - e.posY) * 0.35, 1.0) + 0.08;   // against its own gravity
        e.motionZ = clamp((tz - e.posZ) * 0.35, 1.0);
        e.fallDistance = 0;
        e.velocityChanged = true;
    }

    private static double clamp(double v, double max) {
        return Math.max(-max, Math.min(max, v));
    }

    /** The held mob let go: thrown along the look (`toss`) or just dropped; the cooldown starts. */
    private static void release(Grab g, boolean toss) {
        GRABS.remove(g.p.getUniqueID());
        EntityLiving e = g.e;
        if (toss && e.isEntityAlive() && e.worldObj == g.p.worldObj) {
            Vec3 look = g.p.getLookVec();
            e.motionX = look.xCoord * ArmorFeature.GRAB_THROW_SPEED;
            e.motionY = look.yCoord * ArmorFeature.GRAB_THROW_SPEED + 0.2;
            e.motionZ = look.zCoord * ArmorFeature.GRAB_THROW_SPEED;
            e.velocityChanged = true;
            e.worldObj.playSoundEffect(e.posX, e.posY, e.posZ, "mob.ghast.fireball", 0.5F, 1.2F);
        }
        SingularCooldowns.set(g.p, ArmorFeature.GRAV_GRAB, ArmorFeature.GRAB_COOLDOWN);
    }

    /** The mob this player holds with Н8, or null. */
    public static EntityLiving held(EntityPlayer p) {
        Grab g = p == null ? null : GRABS.get(p.getUniqueID());
        return g == null ? null : g.e;
    }

    // ================================================================== Н4 time slowing

    private static boolean projectile(Entity e) {
        return e instanceof EntityArrow || e instanceof EntityFireball || e instanceof EntityThrowable || e instanceof IProjectile;
    }

    /** EntityArrow.inGround (private in 1.7.10): null when it could not be found. */
    private static java.lang.reflect.Field arrowInGround;
    private static boolean arrowInGroundLooked;

    /** СБ-6: an arrow stuck in a block - not a flying projectile, the dome and time slowing leave it alone. */
    public static boolean stuckInGround(Entity e) {
        if (!(e instanceof EntityArrow)) {
            return false;
        }
        if (!arrowInGroundLooked) {
            arrowInGroundLooked = true;
            try {
                arrowInGround = cpw.mods.fml.relauncher.ReflectionHelper.findField(EntityArrow.class, "inGround", "field_70254_i");
            } catch (RuntimeException ex) {
                arrowInGround = null;
            }
        }
        if (arrowInGround != null) {
            try {
                return arrowInGround.getBoolean(e);
            } catch (IllegalAccessException ex) {
                return false;
            }
        }
        return false;
    }

    /** Test hook (СБ-6): marks an arrow stuck in a block. @return whether the field was there */
    public static boolean setStuckForTest(EntityArrow a, boolean stuck) {
        stuckInGround(a);
        if (arrowInGround == null) {
            return false;
        }
        try {
            arrowInGround.setBoolean(a, stuck);
            return true;
        } catch (IllegalAccessException ex) {
            return false;
        }
    }

    /** Н4: SLOW_TICKS of every mob and every projectile not the wearer's within SLOW_RADIUS at SLOW_FACTOR; О3. */
    private static void timeSlow(EntityPlayerMP p) {
        ArmorFeature f = ArmorFeature.TIME_SLOW;
        Gas[] g = {Gas.KRYPTON, Gas.HELIUM, Gas.SINGULAR_MATTER};
        int[] mb = {ArmorFeature.SING_KR_SLOW, ArmorFeature.SING_HE_SLOW, ArmorFeature.SING_SM_SLOW};
        if (!affordable(p, f, g, mb, 0, ArmorFeature.SLOW_CHARGE)) {
            return;
        }
        payAll(p, f, g, mb, 0, ArmorFeature.SLOW_CHARGE);
        long now = p.worldObj.getTotalWorldTime();
        p.getEntityData().setLong(SLOW_END, now + ArmorFeature.SLOW_TICKS);
        SingularCooldowns.sendState(p, SingularCooldowns.P_SLOW, now + ArmorFeature.SLOW_TICKS);
        FIELDS.add(new Field(KIND_SLOW, p.worldObj, p, p.posX, p.posY, p.posZ, ArmorFeature.SLOW_RADIUS, now, now + ArmorFeature.SLOW_TICKS));
        aggro(p);
        SingularCooldowns.set(p, f, ArmorFeature.SLOW_COOLDOWN);
        ArmorLogicSC.addHeat(p, f.heat);
        particles(p.worldObj, "portal", p.posX, p.posY + 1, p.posZ, 120, 3.0, 0.8);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 1.0F, 0.4F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.timeslow"));
    }

    private static void slowTick(Field fld, long now) {
        EntityPlayer p = fld.owner;
        if (p == null || p.isDead || p.worldObj != fld.world) {
            return;
        }
        double r = fld.r;
        boolean refresh = (now - fld.start) % 10 == 0;
        boolean sync = (now - fld.start) % 5 == 0;
        List list = fld.world.getEntitiesWithinAABBExcludingEntity(p, p.boundingBox.expand(r, r, r));
        for (Object o : list) {
            Entity e = (Entity) o;
            if (e.isDead || e.getDistanceSqToEntity(p) > r * r) {
                continue;
            }
            if (e instanceof EntityLivingBase && !(e instanceof EntityPlayer)) {
                EntityLivingBase l = (EntityLivingBase) e;
                if (refresh) {
                    l.addPotionEffect(new PotionEffect(Potion.moveSlowdown.id, 25, 4));    // -75%: about x0.2 on foot
                }
                if (!l.onGround) {                                                     // flying, falling, knocked back
                    l.motionX *= 0.6;
                    l.motionZ *= 0.6;
                    l.motionY *= 0.6;
                }
            } else if (projectile(e) && !stuckInGround(e) && !ArmorLogicSC.shooterIs(e, p)) {
                NBTTagCompound d = e.getEntityData();
                if (!d.getBoolean(SLOWED)) {
                    d.setBoolean(SLOWED, true);
                    float k = ArmorFeature.SLOW_FACTOR;
                    e.motionX *= k;
                    e.motionY *= k;
                    e.motionZ *= k;
                    if (e instanceof EntityFireball) {
                        EntityFireball fb = (EntityFireball) e;
                        fb.accelerationX *= k;
                        fb.accelerationY *= k;
                        fb.accelerationZ *= k;
                        d.setDouble(SLOWED + "X", fb.accelerationX);  // slowFinish restores only an untouched one
                        d.setDouble(SLOWED + "Y", fb.accelerationY);
                        d.setDouble(SLOWED + "Z", fb.accelerationZ);
                        fld.slowed.add(fb);
                    }
                    e.velocityChanged = true;
                } else {
                    // slow motion: most of the gravity taken back (arrows 0.05 a tick, thrown things 0.03)
                    if (e instanceof EntityArrow && ((EntityArrow) e).arrowShake == 0) {
                        e.motionY += 0.05 * 0.96;
                    } else if (e instanceof EntityThrowable) {
                        e.motionY += 0.03 * 0.96;
                    }
                    if (sync) {
                        e.velocityChanged = true;
                    }
                }
            }
        }
    }

    private static void slowFinish(Field fld) {
        float back = 1F / ArmorFeature.SLOW_FACTOR;
        for (EntityFireball fb : fld.slowed) {
            if (!fb.isDead) {
                NBTTagCompound d = fb.getEntityData();
                if (fb.accelerationX == d.getDouble(SLOWED + "X") && fb.accelerationY == d.getDouble(SLOWED + "Y")
                        && fb.accelerationZ == d.getDouble(SLOWED + "Z")) {    // hit back meanwhile: already at full speed
                    fb.accelerationX *= back;
                    fb.accelerationY *= back;
                    fb.accelerationZ *= back;
                }
                d.removeTag(SLOWED);
                d.removeTag(SLOWED + "X");
                d.removeTag(SLOWED + "Y");
                d.removeTag(SLOWED + "Z");
            }
        }
    }

    /** О3: the hostile mobs within AGGRO_RADIUS turn on the player. */
    static void aggro(EntityPlayer p) {
        if (p.capabilities.disableDamage) {
            return;                                             // creative: mobs wouldn't keep it anyway
        }
        double r = ArmorFeature.AGGRO_RADIUS;
        List list = p.worldObj.getEntitiesWithinAABB(EntityLiving.class, p.boundingBox.expand(r, r, r));
        for (Object o : list) {
            EntityLiving e = (EntityLiving) o;
            if (!(e instanceof IMob) || !e.isEntityAlive() || e.getDistanceSqToEntity(p) > r * r) {
                continue;
            }
            e.setAttackTarget(p);
            e.setRevengeTarget(p);
            if (e instanceof EntityCreature) {
                ((EntityCreature) e).setTarget(p);
            }
        }
    }

    // ================================================================== Н3 black hole

    /** Where the black hole opens: the first block along the look (half a block before it) or HOLE_RANGE away. */
    private static Vec3 holePoint(EntityPlayer p) {
        Vec3 eye = Vec3.createVectorHelper(p.posX, p.posY + 1.62, p.posZ);
        Vec3 look = p.getLookVec();
        double range = ArmorFeature.HOLE_RANGE;
        Vec3 end = eye.addVector(look.xCoord * range, look.yCoord * range, look.zCoord * range);
        MovingObjectPosition hit = p.worldObj.rayTraceBlocks(Vec3.createVectorHelper(eye.xCoord, eye.yCoord, eye.zCoord),
                Vec3.createVectorHelper(end.xCoord, end.yCoord, end.zCoord));
        if (hit != null && hit.hitVec != null) {
            return hit.hitVec.addVector(-look.xCoord * 0.5, -look.yCoord * 0.5, -look.zCoord * 0.5);
        }
        return end;
    }

    /** Н3: HOLE_TICKS of a point that pulls mobs and items within HOLE_RADIUS (x1.5 boosted), then collapses; О3. */
    private static void blackHole(EntityPlayerMP p) {
        ArmorFeature f = ArmorFeature.BLACK_HOLE;
        Gas[] g = {Gas.DEUTERIUM, Gas.HELIUM, Gas.SINGULAR_MATTER};
        int[] mb = {ArmorFeature.SING_D_HOLE, ArmorFeature.SING_HE_HOLE, ArmorFeature.SING_SM_HOLE};
        if (!affordable(p, f, g, mb, 0, ArmorFeature.HOLE_CHARGE)) {
            return;
        }
        payAll(p, f, g, mb, 0, ArmorFeature.HOLE_CHARGE);
        Vec3 at = holePoint(p);
        long now = p.worldObj.getTotalWorldTime();
        double r = ArmorFeature.HOLE_RADIUS * rangeMul(singularBoost(p));
        FIELDS.add(new Field(KIND_HOLE, p.worldObj, p, at.xCoord, at.yCoord, at.zCoord, r, now, now + ArmorFeature.HOLE_TICKS));
        aggro(p);
        SingularCooldowns.set(p, f, ArmorFeature.HOLE_COOLDOWN);
        ArmorLogicSC.addHeat(p, f.heat);
        p.worldObj.playSoundEffect(at.xCoord, at.yCoord, at.zCoord, "mob.endermen.portal", 2.0F, 0.3F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.blackhole"));
    }

    /** Pure: one hit of the black hole on a mob `d` from its centre (radius r) at `age` of `dur` ticks - 0 outside the core (r/2). */
    public static float holeDamage(double d, double r, long age, int dur) {
        double core = 1 - d / (r * 0.5);
        if (core <= 0 || dur <= 0) {
            return 0F;
        }
        return Math.max(1F, (float) (ArmorFeature.HOLE_DAMAGE * core * (0.5 + Math.min(1.0, (double) age / dur))));
    }

    private static void pull(Entity e, double dx, double dy, double dz, double d, double r, double base, double max) {
        double s = base + (1 - d / r) * 0.12;
        e.motionX += dx / d * s;
        e.motionY += dy / d * s + 0.04;                         // against gravity
        e.motionZ += dz / d * s;
        double v = Math.sqrt(e.motionX * e.motionX + e.motionY * e.motionY + e.motionZ * e.motionZ);
        if (v > max) {
            e.motionX *= max / v;
            e.motionY *= max / v;
            e.motionZ *= max / v;
        }
        e.fallDistance = 0;
    }

    private static void hurt(EntityPlayer owner, EntityLivingBase e, float dmg) {
        boolean byOwner = owner != null && !owner.isDead && owner.worldObj == e.worldObj;
        boolean alive = e.isEntityAlive();
        e.attackEntityFrom(byOwner ? DamageSource.causePlayerDamage(owner) : DamageSource.magic, dmg);
        if (byOwner && alive && (e.getHealth() <= 0 || e.isDead)) {
            NBTTagCompound data = owner.getEntityData();
            NBTTagCompound persisted = data.getCompoundTag(EntityPlayer.PERSISTED_NBT_TAG);
            persisted.setInteger(HOLE_KILLS, persisted.getInteger(HOLE_KILLS) + 1);    // stage 3's task
            data.setTag(EntityPlayer.PERSISTED_NBT_TAG, persisted);
        }
    }

    private static void holeTick(Field fld, long now) {
        long age = now - fld.start;
        double r = fld.r;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(fld.x - r, fld.y - r, fld.z - r, fld.x + r, fld.y + r, fld.z + r);
        List list = fld.world.getEntitiesWithinAABB(Entity.class, box);
        boolean sync = age % 5 == 0;
        for (Object o : list) {
            Entity e = (Entity) o;
            if (e.isDead || e instanceof EntityPlayer) {
                continue;
            }
            double dx = fld.x - e.posX, dy = fld.y - (e.posY + e.height / 2), dz = fld.z - e.posZ;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (d > r) {
                continue;
            }
            if (e instanceof EntityLivingBase && e instanceof IMob) {
                if (d > 0.3) {
                    pull(e, dx, dy, dz, d, r, 0.05, 0.6);
                }
                if (age % 10 == 0) {
                    float dmg = holeDamage(d, r, age, ArmorFeature.HOLE_TICKS);
                    if (dmg > 0) {
                        hurt(fld.owner, (EntityLivingBase) e, dmg);
                    }
                }
            } else if (e instanceof EntityItem || e instanceof EntityXPOrb) {
                if (guardField(fld.owner, e) != null) {
                    continue;                                   // someone else's private field
                }
                if (d < 0.8) {
                    e.setPosition(fld.x, fld.y - e.height / 2, fld.z);
                    e.motionX = e.motionY = e.motionZ = 0;
                } else {
                    pull(e, dx, dy, dz, d, r, 0.08, 0.8);
                }
            } else {
                continue;
            }
            if (sync) {
                e.velocityChanged = true;
            }
        }
        if (age % 4 == 0) {
            particles(fld.world, "portal", fld.x, fld.y, fld.z, 40, 1.0, 1.2);
            particles(fld.world, "largesmoke", fld.x, fld.y, fld.z, 3, 0.15, 0.0);
        }
        if (age % 40 == 20) {
            fld.world.playSoundEffect(fld.x, fld.y, fld.z, "portal.portal", 0.6F, 0.5F);
        }
    }

    /** The collapse: a blow to the mobs within HOLE_COLLAPSE_RADIUS (scaled with the hole), every item in sight (not in another's private field) at the point; no blocks touched. */
    private static void holeFinish(Field fld) {
        double rc = ArmorFeature.HOLE_COLLAPSE_RADIUS * fld.r / ArmorFeature.HOLE_RADIUS;
        double r = fld.r;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(fld.x - r, fld.y - r, fld.z - r, fld.x + r, fld.y + r, fld.z + r);
        for (Object o : fld.world.getEntitiesWithinAABB(Entity.class, box)) {
            Entity e = (Entity) o;
            if (e.isDead || e instanceof EntityPlayer) {
                continue;
            }
            double d = e.getDistance(fld.x, fld.y, fld.z);
            if (e instanceof EntityLivingBase && e instanceof IMob && d <= rc) {
                ((EntityLivingBase) e).hurtResistantTime = 0;
                hurt(fld.owner, (EntityLivingBase) e, ArmorFeature.HOLE_COLLAPSE_DAMAGE);
            } else if ((e instanceof EntityItem || e instanceof EntityXPOrb) && d <= r && guardField(fld.owner, e) == null
                    && fld.world.func_147447_a(Vec3.createVectorHelper(fld.x, fld.y, fld.z),
                    Vec3.createVectorHelper(e.posX, e.posY + e.height / 2, e.posZ), false, true, false) == null) {   // not through walls
                e.setPosition(fld.x, fld.y - e.height / 2, fld.z);
                e.motionX = e.motionY = e.motionZ = 0;
                e.velocityChanged = true;
            }
        }
        particles(fld.world, "hugeexplosion", fld.x, fld.y, fld.z, 1, 0, 0);
        particles(fld.world, "portal", fld.x, fld.y, fld.z, 150, 1.5, 2.0);
        fld.world.playSoundEffect(fld.x, fld.y, fld.z, "random.explode", 1.2F, 0.5F);
    }

    // ================================================================== Н17 gravity dome

    /** Н17: DOME_TICKS of a dome of DOME_RADIUS round where the wearer stood: mobs pushed out, projectiles stopped. */
    private static void dome(EntityPlayerMP p) {
        ArmorFeature f = ArmorFeature.GRAV_DOME;
        Gas[] g = {Gas.HELIUM, Gas.DEUTERIUM};
        int[] mb = {ArmorFeature.SING_HE_DOME, ArmorFeature.SING_D_DOME};
        if (!affordable(p, f, g, mb, 0, ArmorFeature.DOME_CHARGE)) {
            return;
        }
        payAll(p, f, g, mb, 0, ArmorFeature.DOME_CHARGE);
        long now = p.worldObj.getTotalWorldTime();
        FIELDS.add(new Field(KIND_DOME, p.worldObj, p, p.posX, p.posY + 1, p.posZ, ArmorFeature.DOME_RADIUS, now, now + ArmorFeature.DOME_TICKS));
        SingularCooldowns.set(p, f, ArmorFeature.DOME_COOLDOWN);
        ArmorLogicSC.addHeat(p, f.heat);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.endermen.portal", 1.0F, 1.6F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.dome"));
    }

    private static void domeTick(Field fld, long now) {
        double r = fld.r, reach = r + 1.5;
        AxisAlignedBB box = AxisAlignedBB.getBoundingBox(fld.x - reach, fld.y - reach, fld.z - reach, fld.x + reach, fld.y + reach, fld.z + reach);
        for (Object o : fld.world.getEntitiesWithinAABB(Entity.class, box)) {
            Entity e = (Entity) o;
            if (e.isDead || e instanceof EntityPlayer) {
                continue;
            }
            double dx = e.posX - fld.x, dy = e.posY + e.height / 2 - fld.y, dz = e.posZ - fld.z;
            double d = Math.sqrt(dx * dx + dy * dy + dz * dz);
            if (e instanceof EntityLivingBase && e instanceof IMob && e.isEntityAlive()) {
                if (d < r + 0.5) {
                    double h = Math.sqrt(dx * dx + dz * dz);
                    if (h < 0.01) {
                        dx = 1;
                        h = 1;
                    }
                    e.motionX = dx / h * 0.6;
                    e.motionZ = dz / h * 0.6;
                    e.motionY = Math.max(e.motionY, 0.2);
                    e.velocityChanged = true;
                }
            } else if (projectile(e) && d < r + 1 && !stuckInGround(e) && !ArmorLogicSC.shooterIs(e, fld.owner)) {
                particles(fld.world, "smoke", e.posX, e.posY, e.posZ, 6, 0.1, 0.02);
                e.setDead();                                    // stopped at the dome's wall
            }
        }
        if ((now - fld.start) % 5 == 0) {
            java.util.Random rnd = fld.world.rand;
            for (int i = 0; i < 24; i++) {
                double a = rnd.nextDouble() * Math.PI * 2, b = rnd.nextDouble() * Math.PI / 2;     // the upper half
                particles(fld.world, "witchMagic", fld.x + Math.cos(a) * Math.cos(b) * r, fld.y - 1 + Math.sin(b) * r,
                        fld.z + Math.sin(a) * Math.cos(b) * r, 1, 0, 0);
            }
        }
    }

    // ================================================================== К1 "Singularity"

    /** К1 (full set): BOOST_TICKS of double effect (and double use and heat), then WEAK_TICKS of half effect. */
    private static void singularity(EntityPlayerMP p) {
        ArmorFeature f = ArmorFeature.SINGULARITY;
        Gas[] g = {Gas.SINGULAR_MATTER, Gas.DEUTERIUM};
        int[] mb = {ArmorFeature.SING_SM_BOOST, ArmorFeature.SING_D_BOOST};
        if (!affordable(p, f, g, mb, 0, ArmorFeature.BOOST_CHARGE)) {
            return;
        }
        payAll(p, f, g, mb, 0, ArmorFeature.BOOST_CHARGE);
        ArmorLogicSC.addHeat(p, f.heat);                        // before the boost: +100, not doubled
        long now = p.worldObj.getTotalWorldTime();
        NBTTagCompound data = p.getEntityData();
        data.setLong(BOOST_END, now + ArmorFeature.BOOST_TICKS);
        data.setLong(WEAK_END, now + ArmorFeature.BOOST_TICKS + ArmorFeature.WEAK_TICKS);
        data.setInteger(BOOST_STATE, 1);
        SingularCooldowns.sendState(p, SingularCooldowns.P_BOOST, now + ArmorFeature.BOOST_TICKS);
        SingularCooldowns.sendState(p, SingularCooldowns.P_WEAK, now + ArmorFeature.BOOST_TICKS + ArmorFeature.WEAK_TICKS);
        SingularCooldowns.set(p, f, ArmorFeature.BOOST_COOLDOWN);
        particles(p.worldObj, "portal", p.posX, p.posY + 1, p.posZ, 150, 1.0, 1.5);
        p.worldObj.playSoundEffect(p.posX, p.posY, p.posZ, "mob.wither.spawn", 0.5F, 1.6F);
        p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.singularity"));
    }

    /** Server, once a second: К1's boost turning into weakness and the weakness ending, told in chat; a little aura while boosted. */
    static void second(EntityPlayer p) {
        NBTTagCompound data = p.getEntityData();
        int state = data.getInteger(BOOST_STATE);
        if (state == 0) {
            return;
        }
        float b = singularBoost(p);
        if (b > 1F) {
            particles(p.worldObj, "portal", p.posX, p.posY + 1, p.posZ, 12, 0.5, 0.6);
        } else if (state == 1 && b < 1F) {
            data.setInteger(BOOST_STATE, 2);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.singularity.weak"));
        } else if (b == 1F) {
            data.removeTag(BOOST_STATE);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.armor.singularity.end"));
        }
    }

    // ================================================================== the server tick

    /** Every world's tick (server, end): the queued key presses, pinned mobs, held mobs, the fields. */
    public static void worldTick(World w) {
        for (Object[] k; (k = PENDING.poll()) != null; ) {
            runKey((EntityPlayerMP) k[0], (ArmorFeature) k[1]);
        }
        long now = w.getTotalWorldTime();
        if (!PINNED.isEmpty()) {
            for (Iterator<Map.Entry<EntityLivingBase, Long>> it = PINNED.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<EntityLivingBase, Long> en = it.next();
                EntityLivingBase e = en.getKey();
                if (e == null || e.isDead || now >= en.getValue()) {
                    it.remove();
                } else if (e.worldObj == w) {
                    pinTick(e);
                }
            }
        }
        if (!GRABS.isEmpty()) {
            for (Grab g : new ArrayList<Grab>(GRABS.values())) {
                if (g.p.worldObj == w || g.p.isDead) {
                    grabTick(g, now);
                }
            }
        }
        for (Iterator<Field> it = FIELDS.iterator(); it.hasNext(); ) {
            Field fld = it.next();
            if (fld.world != w) {
                if (DimensionManager.getWorld(fld.world.provider.dimensionId) != fld.world) {
                    it.remove();                                // its world was unloaded (or the server it ran on stopped)
                }
                continue;
            }
            if (now >= fld.end) {
                it.remove();
                if (fld.kind == KIND_SLOW) {
                    slowFinish(fld);
                } else if (fld.kind == KIND_HOLE) {
                    holeFinish(fld);
                }
                continue;
            }
            if (fld.kind == KIND_SLOW) {
                slowTick(fld, now);
            } else if (fld.kind == KIND_HOLE) {
                holeTick(fld, now);
            } else {
                domeTick(fld, now);
            }
        }
    }

    /** The player left: the held mob is dropped. */
    public static void logout(EntityPlayer p) {
        if (p != null && !p.worldObj.isRemote) {
            Grab g = GRABS.get(p.getUniqueID());
            if (g != null) {
                release(g, false);
            }
        }
    }

    /**
     * The player entity remade (a death, leaving the End): К1's boost and weakness go on with the new one - dying
     * doesn't end the weakness. Н4 stays with the old body (its field stops), so SLOW_END isn't copied.
     */
    public static void copyState(EntityPlayer from, EntityPlayer to) {
        if (from == null || to == null) {
            return;
        }
        NBTTagCompound o = from.getEntityData(), n = to.getEntityData();
        if (o.hasKey(BOOST_END)) {
            n.setLong(BOOST_END, o.getLong(BOOST_END));
        }
        if (o.hasKey(WEAK_END)) {
            n.setLong(WEAK_END, o.getLong(WEAK_END));
        }
        if (o.hasKey(BOOST_STATE)) {
            n.setInteger(BOOST_STATE, o.getInteger(BOOST_STATE));
        }
    }

    /** The server stopped: no field, held mob, pinned mob or queued key of its worlds is kept. */
    public static void clearAll() {
        PENDING.clear();
        PINNED.clear();
        GRABS.clear();
        FIELDS.clear();
    }

    /** How many fields (time slowing, black holes, domes) run now - the self-test. */
    public static int fieldCount() {
        return FIELDS.size();
    }
}
