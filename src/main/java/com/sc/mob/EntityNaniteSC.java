package com.sc.mob;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.dim.SubstrateSC;
import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.energy.GeneratorType;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.init.ModItems;
import com.sc.item.ItemArmorSC;
import com.sc.mob.ai.EntityAINaniteSwarmSC;
import com.sc.tileentity.TileEntityGeneratorSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;
import com.sc.util.SiliconMaterial;

import cpw.mods.fml.common.eventhandler.Event;
import net.minecraft.block.Block;
import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraft.world.chunk.Chunk;
import net.minecraftforge.event.ForgeEventFactory;

/**
 * PACKAGE X (docs/plan-substrate-tech-f3.md §4.6): one particle of a «Рой нанитов» (Nanite Swarm) - 2 HP, no armour, flies
 * without path finding. A swarm is 6..10 particles of one swarm id, never more than 12 to a swarm and to a 32 x 32 area.
 * Together they pull up to 2% of the charge of every piece of the mod's armour a player near them wears, a second, and the
 * energy of the machines within 4 blocks - only in the Substrate, never from a settlement's core, a crack, a fumarole or an
 * endless source. A particle fed 10 s splits, once. A ПЭ-1 Pulse burns every particle of the swarm within 5 of the hit, a Point the
 * one it hit (IEmpReactiveSC); area blows and fire hurt them; arrows do 20%. Only the swarm's first particle counts in the
 * census. Drops (ПТ-1, each particle's - TODO(balance)): Nanite Dust 50%, a Transistor 4%, a Memory Chip 1%.
 * <p>
 * Fixed (the coordinator's, docs/plan-substrate-tech-f3.md §4): the class name, NAME (pool id in MobEntitiesSC), the World
 * constructor (EntityList), HP, the static factory below.
 * <p>
 * The swarm: its id (NBT scSwarm, long; DataWatcher DW_SWARM - the id's low bits for the clients), the state (DW_STATE, NBT
 * scSwarmFlags: STATE_FIRST - the particle the census counts, STATE_SPLIT - it split already). Every MATES_EVERY ticks a
 * particle counts its mates (the same id within MATES blocks): the swarm's size for the drain's share, its middle for the
 * flight, and the first particle - none left (it died, its chunk unloaded): the lowest entity id takes over; two: the higher
 * one gives up. An egg's particle is a swarm of its own.
 * <p>
 * Ф4, package S (docs/plan-substrate-tech-f4.md §6.3, ПН-9): «Эхо моста» - the particles that slipped through a bridge without
 * the Portal Shield (spawnEcho, BridgeEchoSC): a swarm of their own at the arrival, not «born in Si-14» (no loot), draining
 * nothing outside the Substrate (feed's rule); NBT NBT_ECHO (long, the world total time they fade at) - then sparks and gone.
 * <p>
 * The drain, once a second a particle (staggered by the entity id): ARMOUR_DRAIN / (the swarm's size) of the capacity of
 * each worn ItemArmorSC piece of every survival player within ARMOUR_REACH (ItemArmorSC.discharge), and MACHINE_DRAIN / (the
 * size) of the buffer of every TileEntityEnergyBase within MACHINE_REACH, the swarm's whole at most MACHINE_DRAIN_CAP EU a
 * second a machine (extractForItemCharging) - so the whole swarm close by takes the full share a second, a few stragglers
 * less. Nothing outside the Substrate; never a settlement's core, never the Creative Generator. No damage. A second with
 * anything taken is a second fed; fed SPLIT_TICKS it splits off one more particle of its swarm (the caps allowing), once (the
 * new one never splits).
 */
public class EntityNaniteSC extends EntitySubstrateMobSC implements IEmpReactiveSC, IFactionLootSC {

    public static final String NAME = "nanite";
    public static final double HP = 2D;
    /** A new swarm's size; the caps: a swarm, the particles of a 32 x 32 area round a point (any swarm). */
    public static final int SWARM_MIN = 6, SWARM_MAX = 10, SWARM_CAP = 12, AREA = 32;
    /**
     * The drain (the whole swarm, a second, TODO(balance)): the share of a worn piece's capacity, of a machine's buffer; a
     * machine's cap (EU a second); how near.
     */
    public static final float ARMOUR_DRAIN = 0.02F, MACHINE_DRAIN = 0.02F;
    public static final int MACHINE_DRAIN_CAP = 2000;
    public static final double ARMOUR_REACH = 2D, MACHINE_REACH = 4D;
    /** Fed this long (ticks, counted by the second) it splits, once. TODO(balance) */
    public static final int SPLIT_TICKS = 200;
    /** An arrow's (any projectile's) share of the damage; a Pulse burns the swarm this far round the hit. */
    public static final float ARROW_MUL = 0.2F;
    public static final double PULSE_BURN_RADIUS = 5D;
    /** ПТ-1, each particle's drop. TODO(balance) */
    public static final float DUST_CHANCE = 0.5F, TRANSISTOR_CHANCE = 0.04F, MEMORY_CHIP_CHANCE = 0.01F;
    /** The flight: top speed (blocks a tick), how far it smells prey (a player, a powered machine), its mates are counted this far. */
    public static final double FLY_SPEED = 0.28D, SEEK = 16D, MACHINE_SEEK = 8D, MATES = 16D;
    public static final int MATES_EVERY = 10, XP = 1;

    /** DataWatcher (§2.4): the swarm's id (int: the long's low bits), the state (byte). */
    public static final int DW_SWARM = 20, DW_STATE = 21;
    public static final int STATE_FIRST = 1, STATE_SPLIT = 2;
    public static final String NBT_SWARM = "scSwarm", NBT_FLAGS = "scSwarmFlags";
    /** Ф4, ПН-9: an echo particle's fade time (world total time; BridgeEchoSC.ECHO_NBT). */
    public static final String NBT_ECHO = "scEcho";

    private long swarm;
    /** Ф4, ПН-9: the world total time an echo particle fades at; 0 - not an echo's. */
    private long echoUntil;
    /** Seconds fed (in memory: a reload starts it again). */
    private int fed;
    /** The last count: mates (itself included), their middle (none yet: its own place). */
    private int mates = 1;
    private double midX, midY, midZ;
    private boolean counted;

    public EntityNaniteSC(World world) {
        super(world);
        setSize(0.35F, 0.35F);
        experienceValue = XP;
        tasks.addTask(0, new EntityAINaniteSwarmSC(this));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(SEEK);
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_SWARM, Integer.valueOf(0));
        dataWatcher.addObject(DW_STATE, Byte.valueOf((byte) 0));
    }

    /**
     * PACKAGE X: a new swarm of `count` particles round (x, y, z) (SpawnDirectorSC calls it; server): one swarm id, marked
     * «born in Si-14», within the caps (12 to a swarm and to a 32 x 32 area). @return how many particles appeared
     */
    public static int spawnSwarm(World w, double x, double y, double z, int count, Random rand) {
        if (w == null || w.isRemote) {
            return 0;
        }
        int room = Math.min(Math.min(count, SWARM_CAP), SWARM_CAP - inArea(w, x, z));
        if (room <= 0) {
            return 0;
        }
        long id = rand.nextLong();
        if (id == 0L) {
            id = 1L;
        }
        int made = 0;
        for (int k = 0; k < room; k++) {
            EntityNaniteSC e = new EntityNaniteSC(w);
            for (int t = 0; t < 4; t++) {
                boolean last = t == 3;
                double px = last ? x : x + rand.nextGaussian() * 0.8D, py = y + 0.5D + (last ? 0D : rand.nextDouble() * 1.5D);
                double pz = last ? z : z + rand.nextGaussian() * 0.8D;
                e.setLocationAndAngles(px, py, pz, rand.nextFloat() * 360F, 0F);
                if (w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() && !w.isAnyLiquid(e.boundingBox)) {
                    break;
                }
            }
            if (ForgeEventFactory.canEntitySpawn(e, w, (float) e.posX, (float) e.posY, (float) e.posZ) == Event.Result.DENY) {
                continue;
            }
            e.setSwarm(id, made == 0 ? STATE_FIRST : 0);
            BornInSubstrateSC.mark(e);
            if (w.spawnEntityInWorld(e)) {
                made++;
            }
        }
        return made;
    }

    /**
     * Ф4, package S (ПН-9, BridgeEchoSC): `count` echo particles round (x, y, z) in any world - one new swarm, NOT «born in
     * Si-14» (no loot), within the caps (12 to a swarm and to a 32 x 32 area) and other mods' CheckSpawn; each fades `lifeTicks`
     * later (NBT_ECHO). Server. @return how many particles appeared
     */
    public static int spawnEcho(World w, double x, double y, double z, int count, int lifeTicks, Random rand) {
        if (w == null || w.isRemote || count <= 0) {
            return 0;
        }
        int room = Math.min(Math.min(count, SWARM_CAP), SWARM_CAP - inArea(w, x, z));
        long id = rand.nextLong() | 1L, fade = w.getTotalWorldTime() + Math.max(1, lifeTicks);
        int made = 0;
        for (int k = 0; k < room; k++) {
            EntityNaniteSC e = new EntityNaniteSC(w);
            for (int t = 0; t < 4; t++) {
                boolean last = t == 3;
                double px = last ? x : x + rand.nextGaussian() * 0.8D, py = y + 0.5D + (last ? 0D : rand.nextDouble() * 1.5D);
                double pz = last ? z : z + rand.nextGaussian() * 0.8D;
                e.setLocationAndAngles(px, py, pz, rand.nextFloat() * 360F, 0F);
                if (w.getCollidingBoundingBoxes(e, e.boundingBox).isEmpty() && !w.isAnyLiquid(e.boundingBox)) {
                    break;
                }
            }
            if (ForgeEventFactory.canEntitySpawn(e, w, (float) e.posX, (float) e.posY, (float) e.posZ) == Event.Result.DENY) {
                continue;
            }
            e.setSwarm(id, made == 0 ? STATE_FIRST : 0);
            e.echoUntil = fade;
            if (w.spawnEntityInWorld(e)) {
                made++;
            }
        }
        return made;
    }

    /** Ф4, ПН-9: an echo particle (it fades). */
    public boolean echo() {
        return echoUntil > 0L;
    }

    /** The nanite particles (any swarm) in the AREA x AREA columns round (x, z). */
    public static int inArea(World w, double x, double z) {
        double h = AREA / 2D;
        return w.getEntitiesWithinAABB(EntityNaniteSC.class, AxisAlignedBB.getBoundingBox(x - h, 0D, z - h, x + h, 256D, z + h)).size();
    }

    // ------------------------------------------------------------------ the swarm

    /** Server: its swarm and state (the factory, a split). */
    public void setSwarm(long id, int state) {
        swarm = id;
        dataWatcher.updateObject(DW_SWARM, Integer.valueOf((int) (id ^ id >>> 32)));
        setState(state);
    }

    public long swarm() {
        return swarm;
    }

    /** Both sides: DW_STATE. */
    public int state() {
        return dataWatcher.getWatchableObjectByte(DW_STATE);
    }

    private void setState(int s) {
        if (state() != s) {
            dataWatcher.updateObject(DW_STATE, Byte.valueOf((byte) s));
        }
    }

    public boolean first() {
        return (state() & STATE_FIRST) != 0;
    }

    /** The swarm's size at the last count (itself included). */
    public int swarmSize() {
        return mates;
    }

    /** The swarm's middle at the last count (the flight's cohesion); before the first count - its own place. */
    public double midX() {
        return counted ? midX : posX;
    }

    public double midY() {
        return counted ? midY : posY;
    }

    public double midZ() {
        return counted ? midZ : posZ;
    }

    /** The live particles of its swarm within MATES blocks, itself included (server). */
    public List<EntityNaniteSC> mates() {
        List<EntityNaniteSC> out = new ArrayList<EntityNaniteSC>();
        for (Object o : worldObj.getEntitiesWithinAABB(EntityNaniteSC.class, boundingBox.expand(MATES, MATES, MATES))) {
            EntityNaniteSC n = (EntityNaniteSC) o;
            if (n.swarm == swarm && n.isEntityAlive()) {
                out.add(n);
            }
        }
        if (!out.contains(this)) {
            out.add(this);
        }
        return out;
    }

    /** ПС-7: only the swarm's first particle takes a place under the caps. */
    @Override
    public boolean censusCounts() {
        return first();
    }

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (worldObj.isRemote || isDead) {
            return;
        }
        if (echoUntil > 0L && worldObj.getTotalWorldTime() >= echoUntil) {
            fade();                                             // Ф4, ПН-9: an echo's time is up
            return;
        }
        if (swarm == 0L) {                                      // an egg's particle: a swarm of its own
            setSwarm(rand.nextLong() | 1L, STATE_FIRST);
        }
        int phase = ticksExisted + getEntityId();
        if (phase % MATES_EVERY == 0 || !counted) {
            count();
        }
        if (phase % 20 == 0) {
            feed();
        }
    }

    /** Counts the mates: the size, the middle, the first particle (exactly one where the swarm is loaded). */
    private void count() {
        List<EntityNaniteSC> all = mates();
        mates = all.size();
        double sx = 0D, sy = 0D, sz = 0D;
        EntityNaniteSC lowest = this, lowestFirst = null;
        for (EntityNaniteSC n : all) {
            sx += n.posX;
            sy += n.posY;
            sz += n.posZ;
            if (n.getEntityId() < lowest.getEntityId()) {
                lowest = n;
            }
            if (n.first() && (lowestFirst == null || n.getEntityId() < lowestFirst.getEntityId())) {
                lowestFirst = n;
            }
        }
        midX = sx / mates;
        midY = sy / mates;
        midZ = sz / mates;
        counted = true;
        if (lowestFirst == null && lowest == this) {
            setState(state() | STATE_FIRST);                    // nobody counts for the swarm: the lowest id takes it
        } else if (lowestFirst != null && lowestFirst != this && first()) {
            setState(state() & ~STATE_FIRST);                   // two firsts (a split swarm met again): the higher gives up
        }
    }

    /** One second of feeding (the class comment). */
    private void feed() {
        if (!SubstrateSC.is(worldObj)) {
            return;
        }
        int n = Math.max(1, mates);
        boolean ate = false;
        for (Object o : worldObj.getEntitiesWithinAABB(EntityPlayer.class, boundingBox.expand(ARMOUR_REACH, ARMOUR_REACH, ARMOUR_REACH))) {
            EntityPlayer p = (EntityPlayer) o;
            if (!p.isEntityAlive() || p.capabilities.isCreativeMode || getDistanceSqToEntity(p) > ARMOUR_REACH * ARMOUR_REACH) {
                continue;
            }
            for (int i = 0; i < 4; i++) {
                ItemStack s = p.inventory.armorInventory[i];
                if (s != null && s.getItem() instanceof ItemArmorSC) {
                    int amount = (int) Math.max(1L, (long) (ItemArmorSC.capacityOf(s) * (double) ARMOUR_DRAIN / n));
                    if (ItemArmorSC.discharge(s, amount) > 0) {
                        ate = true;
                        sparks(p.posX, p.posY + p.height * 0.5D, p.posZ);
                    }
                }
            }
        }
        for (TileEntityEnergyBase te : machinesNear(worldObj, posX, posY, posZ, MACHINE_REACH)) {
            int want = (int) (Math.min((double) MACHINE_DRAIN_CAP, te.getMaxEnergyStored() * (double) MACHINE_DRAIN) / n);
            if (want > 0 && te.extractForItemCharging(want) > 0) {
                ate = true;
                sparks(te.xCoord + 0.5D, te.yCoord + 0.5D, te.zCoord + 0.5D);
            }
        }
        if (ate && ++fed * 20 >= SPLIT_TICKS && (state() & STATE_SPLIT) == 0) {
            split();
        }
    }

    /**
     * Splits off one more particle of its swarm (the caps and LivingSpawnEvent.CheckSpawn allowing): both marked «split» -
     * neither splits again.
     */
    private void split() {
        fed = 0;
        setState(state() | STATE_SPLIT);
        if (mates().size() >= SWARM_CAP || inArea(worldObj, posX, posZ) >= SWARM_CAP) {
            return;
        }
        EntityNaniteSC e = new EntityNaniteSC(worldObj);
        e.setLocationAndAngles(posX + (rand.nextDouble() - 0.5D) * 0.4D, posY, posZ + (rand.nextDouble() - 0.5D) * 0.4D, rand.nextFloat() * 360F, 0F);
        e.setSwarm(swarm, STATE_SPLIT);
        e.echoUntil = echoUntil;                                // Ф4: an echo's split fades with it
        if (BornInSubstrateSC.isBorn(this)) {
            BornInSubstrateSC.mark(e);
        }
        if (ForgeEventFactory.canEntitySpawn(e, worldObj, (float) e.posX, (float) e.posY, (float) e.posZ) == Event.Result.DENY) {
            return;                                     // a mod's (or a settlement's) spawn ban holds for a split too; no retry
        }
        worldObj.spawnEntityInWorld(e);
        playSound("mob.silverfish.step", 0.4F, 1.8F);
    }

    /**
     * The machines a swarm feeds on within `r` of (x, y, z): every TileEntityEnergyBase with energy in it, but a settlement's
     * core and the Creative Generator (an endless source). Only loaded chunks are looked at. Server; the AI's prey too.
     */
    public static List<TileEntityEnergyBase> machinesNear(World w, double x, double y, double z, double r) {
        List<TileEntityEnergyBase> out = new ArrayList<TileEntityEnergyBase>();
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), ri = (int) Math.ceil(r);
        for (int chX = (bx - ri) >> 4; chX <= (bx + ri) >> 4; chX++) {
            for (int chZ = (bz - ri) >> 4; chZ <= (bz + ri) >> 4; chZ++) {
                if (!w.getChunkProvider().chunkExists(chX, chZ)) {
                    continue;                                   // never loads a chunk
                }
                Chunk c = w.getChunkFromChunkCoords(chX, chZ);
                for (Object o : c.chunkTileEntityMap.values()) {
                    TileEntity te = (TileEntity) o;
                    if (edible(te) && te.getDistanceFrom(x, y, z) <= r * r) {
                        out.add((TileEntityEnergyBase) te);
                    }
                }
            }
        }
        return out;
    }

    /** A machine the swarm may feed on: the mod's energy block with EU in it, not a settlement's core, not an endless source. */
    public static boolean edible(TileEntity te) {
        return te instanceof TileEntityEnergyBase && !te.isInvalid() && !(te instanceof TileEntitySettlementCoreSC)
                && !(te instanceof TileEntityGeneratorSC && ((TileEntityGeneratorSC) te).getGeneratorType() == GeneratorType.CREATIVE)
                && ((TileEntityEnergyBase) te).getEnergyStored() > 0;
    }

    /** Ф4, ПН-9: an echo particle fades - a spark burst, gone (no death: no drop, no XP). */
    private void fade() {
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + 0.2D, posZ, 6, 0.15D, 0.15D, 0.15D, 0.05D);
        }
        playSound("random.fizz", 0.3F, 1.8F);
        setDead();
    }

    private void sparks(double x, double y, double z) {
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("reddust", x, y, z, 3, 0.2D, 0.2D, 0.2D, 0D);
        }
    }

    // ------------------------------------------------------------------ damage, ПБ-20

    /** Arrows (any projectile) do ARROW_MUL: a bow is no tool against a swarm. */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (src != null && src.isProjectile()) {
            amount *= ARROW_MUL;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** Nanites never bite. */
    @Override
    public boolean attackEntityAsMob(Entity target) {
        return false;
    }

    /** A Point burns this particle; a Pulse every particle of its swarm within PULSE_BURN_RADIUS of the hit. */
    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || isDead || getHealth() <= 0F || mode == null || mode == EmpMode.SIPHON) {
            return false;
        }
        DamageSource src = shooter instanceof EntityPlayer ? DamageSource.causePlayerDamage((EntityPlayer) shooter)
                : shooter instanceof EntityLivingBase ? DamageSource.causeMobDamage((EntityLivingBase) shooter) : DamageSource.generic;
        if (mode == EmpMode.POINT) {
            burn(src);
            return true;
        }
        double r = PULSE_BURN_RADIUS;
        for (Object o : worldObj.getEntitiesWithinAABB(EntityNaniteSC.class, AxisAlignedBB.getBoundingBox(x - r, y - r, z - r, x + r, y + r, z + r))) {
            EntityNaniteSC n = (EntityNaniteSC) o;
            if (n.swarm == swarm && n.getDistanceSq(x, y, z) <= r * r) {
                n.burn(src);
            }
        }
        if (isEntityAlive()) {
            burn(src);                                          // the one hit, wherever its middle is
        }
        return true;
    }

    /** Burnt out by a ПЭ-1: dead on the spot, `src` its killer. */
    private void burn(DamageSource src) {
        if (!isEntityAlive()) {
            return;
        }
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("smoke", posX, posY + 0.2D, posZ, 4, 0.1D, 0.1D, 0.1D, 0.01D);
        }
        hurtResistantTime = 0;
        attackEntityFrom(src, getHealth() + 100F);
        if (isEntityAlive()) {                                  // something cancelled the blow: burnt all the same
            setHealth(0F);
            onDeath(src);
        }
    }

    /** The first particle dies: the lowest-id mate counts for the swarm at once. */
    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && first()) {
            EntityNaniteSC next = null;
            for (EntityNaniteSC n : mates()) {
                if (n != this && (next == null || n.getEntityId() < next.getEntityId())) {
                    next = n;
                }
            }
            if (next != null) {
                next.setState(next.state() | STATE_FIRST);
            }
            setState(state() & ~STATE_FIRST);
        }
    }

    /** ПТ-1, each particle: Nanite Dust, now and then a Transistor or a Memory Chip. */
    @Override
    public void factionLoot(List<ItemStack> out, Random rand) {
        if (rand.nextFloat() < DUST_CHANCE) {
            ItemStack s = MobItemsSC.loot(ItemCombatLootSC.LootKind.NANITE_DUST, 1);
            if (s != null) {
                out.add(s);
            }
        }
        if (ModItems.siliconMaterial != null) {
            if (rand.nextFloat() < TRANSISTOR_CHANCE) {
                out.add(ModItems.siliconMaterial.stackOf(SiliconMaterial.TRANSISTOR));
            }
            if (rand.nextFloat() < MEMORY_CHIP_CHANCE) {
                out.add(ModItems.siliconMaterial.stackOf(SiliconMaterial.MEMORY_CHIP));
            }
        }
    }

    // ------------------------------------------------------------------ flying (as vanilla's EntityFlying), no pushing

    @Override
    protected void fall(float distance) {
    }

    @Override
    protected void updateFallState(double dy, boolean onGround) {
    }

    @Override
    public boolean isOnLadder() {
        return false;
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean doesEntityNotTriggerPressurePlate() {
        return true;
    }

    /** A swarm overlaps and lets everything through. */
    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected void collideWithEntity(Entity e) {
    }

    /** No gravity: the motion its AI gives it, the air's drag; in water a heavier drag. */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        moveEntity(motionX, motionY, motionZ);
        double drag = isInWater() || handleLavaMovement() ? 0.8D : 0.91D;
        motionX *= drag;
        motionY *= drag;
        motionZ *= drag;
        prevLimbSwingAmount = limbSwingAmount;
        limbSwingAmount = 0F;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setLong(NBT_SWARM, swarm);
        tag.setByte(NBT_FLAGS, (byte) state());
        if (echoUntil > 0L) {
            tag.setLong(NBT_ECHO, echoUntil);
        }
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey(NBT_SWARM)) {
            setSwarm(tag.getLong(NBT_SWARM), tag.getByte(NBT_FLAGS));
        }
        echoUntil = tag.getLong(NBT_ECHO);
    }

    // ------------------------------------------------------------------ sounds: a dry rustle (vanilla's), the first particle only

    @Override
    protected String getLivingSound() {
        return first() ? "mob.silverfish.say" : null;
    }

    @Override
    public int getTalkInterval() {
        return 160;
    }

    @Override
    protected String getHurtSound() {
        return "mob.silverfish.hit";
    }

    @Override
    protected String getDeathSound() {
        return "random.fizz";
    }

    @Override
    protected float getSoundVolume() {
        return 0.3F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.9F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    protected void func_145780_a(int x, int y, int z, Block block) {
    }
}
