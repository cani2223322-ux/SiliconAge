package com.sc.mob;

import com.sc.arena.ArenaWorldSC;
import com.sc.emp.EmpMode;
import com.sc.emp.IEmpReactiveSC;
import com.sc.finale.FinaleNetSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityLiving;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.DamageSource;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE B (docs/plan-substrate-tech-f5.md §5.2, rule ПС-52; proposal §9 «4 Катушки удержания питают его щит лучами ... Каждую
 * выключают Точкой ПЭ-1 на 15 с или ломают: у катушки запас 100, выстрелы его сажают»): a Containment Coil - one of the four on the
 * arena's pedestals (ArenaLayoutSC.coilSpot), placed by the Warden's summon, bound to it (DW_BOSS - the clients draw its beam to the
 * boss's chest while it is LIT). Not a creature: no AI, no gravity, no knockback, never despawns, not IMob (nothing pushes it, the
 * census leaves it out), a native (ISubstrateNativeSC: the bridge never takes it). States: LIT - powers the shield; DARK - a ПЭ-1 Point
 * (empStruck, mode POINT: WardenRulesSC.COIL_OFF_TICKS - a Pulse does nothing to it, false: the old Slowness, harmless); BROKEN -
 * its reserve (COIL_RESERVE) shot / hit away (attackEntityFrom takes the blow off the reserve, never its health). The Warden lights
 * every coil again when its window ends (B). Its fight ends (the Warden gone, the fight stale - ArenaWorldSC.fightCurrent) - it
 * leaves the world.
 * <p>
 * Fixed (the coordinator's): the class, NAME (pool id 23, no spawn egg), the World constructor, the DataWatcher slots, the states,
 * IEmpReactiveSC. B's: the bodies, the look (RenderContainmentCoilSC: a copper coil on a stand, its blue core, the beam; dark - grey,
 * sparks; broken - smoke).
 * <p>
 * Package B:
 * <ul>
 * <li>its own clock (server): a DARK coil lights again COIL_OFF_TICKS after the last Point - unless its Warden holds it dark (the
 * window is open; «Удержание» is over - spent, dark for good, ПС-53); a Point into a dark coil starts its 15 s again;</li>
 * <li>a blow - a creature's, a projectile's, an explosion's (the world's own: suffocation, falling, drowning, cactus leave it
 * alone) - takes its reserve as vanilla's hurt frames let it (a burst within 10 ticks counts only the strongest blow); the reserve
 * gone - BROKEN, smoke, until the window's end lights it again with a full reserve; /kill (outOfWorld) takes it away - its Warden
 * puts a new one on the pedestal while «Удержание» lasts;</li>
 * <li>its Warden links it (link: the reference and DW_BOSS) when it finds it on its pedestal - after a reload too; every 100 ticks
 * (and on its first tick) it asks fightCurrent and leaves a stale fight (setDead, nothing dropped);</li>
 * <li>every change of state is a FinaleNetSC.BOSS_FX (WardenFightSC.FX_COIL_DARK / FX_COIL_BROKEN / FX_COIL_LIT - BossClientSC's
 * fixed numbers) to the players within FX_RANGE, and a sound. NBT scFight
 * {ri, rj, id, k} and scCoil {s: state, d: dark ticks left, r: reserve, x: spent}.</li>
 * </ul>
 */
public class EntityContainmentCoilSC extends EntityLiving implements IEmpReactiveSC, ISubstrateNativeSC {

    public static final String NAME = "containmentCoil";
    /** DataWatcher (fixed): its Warden's entity id (int, -1 none), the state (byte), the reserve x10 (int), its index 0..3 (byte). */
    public static final int DW_BOSS = 12, DW_STATE = 13, DW_RESERVE = 14, DW_INDEX = 15;
    public static final int LIT = 0, DARK = 1, BROKEN = 2;
    /** Entity NBT: its fight {ri, rj, id, k: index}, the state's timer (B's). */
    public static final String NBT_FIGHT = "scFight";
    /** Entity NBT (B's): the state, the dark ticks left, the reserve, spent. */
    public static final String NBT_STATE = "scCoil";
    /** The BOSS_FX reach (FinaleNetSC: «to the players within 96 of it»). */
    public static final double FX_RANGE = 96D;
    /** Its size; where its beam leaves it (blocks over its feet - RenderContainmentCoilSC's emitter). */
    public static final float WIDTH = 1.0F, HEIGHT = 2.0F, BEAM_Y = 1.95F;
    /** Vanilla's hurt frames (ticks): a blow within half of them counts only what it adds to the last one. */
    private static final int HURT_FRAMES = 20;

    /** Its fight (an unbound coil - made by a command - just stands: it powers nothing). */
    private int arenaRi, arenaRj, index;
    private long fightId;
    private boolean bound;
    /** Server: world time a DARK coil lights again (0 - none); spent - «Удержание» is over, dark for good; the reserve. */
    private long darkUntil;
    private boolean spent;
    private float reserve = WardenRulesSC.COIL_RESERVE;
    /** Server, not saved: its Warden (link). */
    private EntityBreakdownWardenSC boss;

    public EntityContainmentCoilSC(World world) {
        super(world);
        setSize(WIDTH, HEIGHT);
        isImmuneToFire = true;
        ignoreFrustumCheck = true;                              // its beam is drawn with it: the coil off screen, the Warden on it
        func_110163_bv();                                       // persistent
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_BOSS, Integer.valueOf(-1));
        dataWatcher.addObject(DW_STATE, Byte.valueOf((byte) LIT));
        dataWatcher.addObject(DW_RESERVE, Integer.valueOf(Math.round(WardenRulesSC.COIL_RESERVE * 10)));
        dataWatcher.addObject(DW_INDEX, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(20D);
        getEntityAttribute(SharedMonsterAttributes.knockbackResistance).setBaseValue(1D);
    }

    /** Both sides: LIT / DARK / BROKEN. */
    public int coilState() {
        return dataWatcher.getWatchableObjectByte(DW_STATE);
    }

    /** Both sides: its Warden's entity id (-1 none). */
    public int bossId() {
        return dataWatcher.getWatchableObjectInt(DW_BOSS);
    }

    /** Both sides: its pedestal's index 0..3. */
    public int coilIndex() {
        return dataWatcher.getWatchableObjectByte(DW_INDEX);
    }

    /** Both sides: the reserve left (units). */
    public float reserveShown() {
        return dataWatcher.getWatchableObjectInt(DW_RESERVE) / 10F;
    }

    // ------------------------------------------------------------------ its fight (the Warden calls these, server)

    /** Server, before it joins the world (EntityBreakdownWardenSC.placeCoil): coil `k` of arena (ri, rj)'s fight `id`. */
    public void bindTo(int ri, int rj, long id, int k) {
        arenaRi = ri;
        arenaRj = rj;
        fightId = id;
        index = ((k % WardenRulesSC.COILS) + WardenRulesSC.COILS) % WardenRulesSC.COILS;
        bound = true;
        dataWatcher.updateObject(DW_INDEX, Byte.valueOf((byte) index));
    }

    /** It belongs to that fight (the Warden looking for its coils). */
    public boolean ofFight(int ri, int rj, long id) {
        return bound && arenaRi == ri && arenaRj == rj && fightId == id;
    }

    /** Server: its pedestal's index. */
    public int index() {
        return index;
    }

    /** Server (its Warden found it): the clients' beam goes to that Warden now (null - none). */
    public void link(EntityBreakdownWardenSC warden) {
        boss = warden;
        int id = warden == null ? -1 : warden.getEntityId();
        if (bossId() != id) {
            dataWatcher.updateObject(DW_BOSS, Integer.valueOf(id));
        }
    }

    /** Server: its linked Warden while it is in the world (not dead, not gone with its chunk), else null. */
    public EntityBreakdownWardenSC boss() {
        return boss != null && !boss.isDead && worldObj.getEntityByID(boss.getEntityId()) == boss ? boss : null;
    }

    /** Server: it powers the shield now (LIT, not spent). */
    public boolean lit() {
        return coilState() == LIT && !spent;
    }

    /** Server: «Удержание» is over - dark for good (ПС-53: «катушки гаснут насовсем»). */
    public void spend() {
        if (spent) {
            return;
        }
        spent = true;
        darkUntil = 0L;
        if (coilState() == LIT) {
            setState(DARK);
            fx(WardenFightSC.FX_COIL_DARK);
            SoundsSC.play(worldObj, posX, posY + 1D, posZ, "power.off", 1F, 0.5F);
        }
    }

    public boolean spent() {
        return spent;
    }

    /** Server (its Warden's window ended, ПС-52): lit again - a broken one with its full reserve. Nothing for a spent coil. */
    public void relight() {
        if (spent || isDead || worldObj.isRemote) {
            return;
        }
        boolean was = coilState() != LIT;
        darkUntil = 0L;
        if (reserve < WardenRulesSC.COIL_RESERVE) {
            setReserve(WardenRulesSC.COIL_RESERVE);           // «сломанные — с полным запасом» (a scratched one is topped up too)
        }
        if (was) {
            setState(LIT);
            fx(WardenFightSC.FX_COIL_LIT);
            SoundsSC.play(worldObj, posX, posY + 1D, posZ, "power.on", 1F, 0.6F);
        }
    }

    /** Server: its Warden keeps it dark now (the window is open), or it is spent. */
    private boolean held() {
        EntityBreakdownWardenSC b = boss();
        return spent || b != null && b.coilsHeld();
    }

    // ------------------------------------------------------------------ ПЭ-1, blows

    /** ПС-52: a Point darkens it for COIL_OFF_TICKS (a dark one: 15 s from now). A Pulse, a broken or spent coil - nothing. */
    @Override
    public boolean empStruck(Entity shooter, EmpMode mode, double x, double y, double z) {
        if (worldObj.isRemote || mode != EmpMode.POINT || spent || isDead || coilState() == BROKEN) {
            return false;                                       // a Pulse: the old Slowness - harmless to a coil
        }
        darkUntil = Math.max(darkUntil, worldObj.getTotalWorldTime() + WardenRulesSC.COIL_OFF_TICKS);
        if (coilState() == LIT) {
            setState(DARK);
            fx(WardenFightSC.FX_COIL_DARK);
            SoundsSC.play(worldObj, posX, posY + 1D, posZ, "power.off", 1F, 0.7F);
        }
        return true;
    }

    /**
     * ПС-52: a blow takes its reserve (never its health); 0 - BROKEN. A creature's, a projectile's or an explosion's blow only (the
     * world's own leaves it alone); outOfWorld (/kill) takes it away.
     */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        if (worldObj.isRemote || isDead || src == null) {
            return false;
        }
        if (src == DamageSource.outOfWorld) {
            setDead();
            return true;
        }
        if (!(amount > 0F) || coilState() == BROKEN || isEntityInvulnerable()
                || src.getEntity() == null && src.getSourceOfDamage() == null && !src.isExplosion()) {
            return false;
        }
        float take;
        if (hurtResistantTime > HURT_FRAMES / 2) {             // vanilla's hurt frames: only what the stronger blow adds
            if (amount <= lastDamage) {
                return false;
            }
            take = amount - lastDamage;
        } else {
            take = amount;
            hurtResistantTime = HURT_FRAMES;
            worldObj.setEntityState(this, (byte) 2);            // the clients' red flash and the hurt sound
        }
        lastDamage = amount;
        setReserve(reserve - take);
        if (reserve <= 0F) {
            setState(BROKEN);
            darkUntil = 0L;
            fx(WardenFightSC.FX_COIL_BROKEN);
            SoundsSC.play(worldObj, posX, posY + 1D, posZ, "power.off", 1.2F, 0.4F);
            worldObj.playSoundEffect(posX, posY + 1D, posZ, "random.fizz", 1F, 0.6F);
        }
        return true;
    }

    private void setState(int s) {
        if (coilState() != s) {
            dataWatcher.updateObject(DW_STATE, Byte.valueOf((byte) s));
        }
    }

    private void setReserve(float r) {
        reserve = Math.max(0F, Math.min(WardenRulesSC.COIL_RESERVE, r));
        dataWatcher.updateObject(DW_RESERVE, Integer.valueOf(Math.round(reserve * 10)));
    }

    /** Server: a BOSS_FX at the coil's emitter to the players near. */
    private void fx(int kind) {
        if (worldObj.isRemote) {
            return;
        }
        NBTTagCompound t = new NBTTagCompound();
        t.setInteger("k", kind);
        t.setDouble("x", posX);
        t.setDouble("y", posY + BEAM_Y);
        t.setDouble("z", posZ);
        FinaleNetSC.sendAround(worldObj, posX, posY, posZ, FX_RANGE, FinaleNetSC.BOSS_FX, t);
    }

    // ------------------------------------------------------------------ not a creature: it stays where it was put

    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        motionX = motionY = motionZ = 0D;
        if (worldObj.isRemote) {
            clientFx();
            return;
        }
        if (bound && (ticksExisted <= 1 || ticksExisted % 100 == 0) && !ArenaWorldSC.fightCurrent(worldObj, arenaRi, arenaRj, fightId)) {
            setDead();                                          // ПС-56: its fight was reset or won - it leaves the world (on its first tick too)
            return;
        }
        if (coilState() == DARK && darkUntil > 0L && worldObj.getTotalWorldTime() >= darkUntil && !held()) {
            relight();                                          // its 15 s are over
        }
        if (boss != null && boss() == null) {
            link(null);                                         // its Warden died or went with its chunk: no beam
        }
    }

    /** No gravity, no walking (an explosion's push is zeroed the same tick). */
    @Override
    public void moveEntityWithHeading(float strafe, float forward) {
        motionX = motionY = motionZ = 0D;
    }

    @Override
    public void addVelocity(double x, double y, double z) {
    }

    @Override
    public void knockBack(Entity e, float f, double x, double z) {
    }

    @Override
    public boolean isPushedByWater() {
        return false;
    }

    @Override
    protected void fall(float distance) {
    }

    @Override
    public void applyEntityCollision(Entity e) {
    }

    @Override
    protected void collideWithEntity(Entity e) {
    }

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean canBePushed() {
        return false;
    }

    @Override
    protected boolean isAIEnabled() {
        return false;
    }

    /** No look-about of vanilla's old AI: a machine. */
    @Override
    protected void updateEntityActionState() {
    }

    @Override
    protected boolean canTriggerWalking() {
        return false;
    }

    @Override
    public boolean canBreatheUnderwater() {
        return true;
    }

    @Override
    protected String getLivingSound() {
        return null;
    }

    @Override
    protected String getHurtSound() {
        return "random.anvil_land";
    }

    @Override
    protected String getDeathSound() {
        return null;
    }

    @Override
    protected float getSoundVolume() {
        return 0.4F;
    }

    @Override
    protected float getSoundPitch() {
        return 1.6F + rand.nextFloat() * 0.2F;
    }

    /** Nothing ever drops (a coil only goes by a command, or with its fight). */
    @Override
    protected void dropFewItems(boolean recentlyHit, int looting) {
    }

    @Override
    protected void dropEquipment(boolean recentlyHit, int looting) {
    }

    // ------------------------------------------------------------------ the clients' particles (ПС-9)

    private void clientFx() {
        int s = coilState();
        if (s == DARK && rand.nextInt(6) == 0) {               // a dark coil sparks now and then
            worldObj.spawnParticle("fireworksSpark", posX + (rand.nextDouble() - 0.5D) * 0.6D, posY + 0.6D + rand.nextDouble() * 1.2D,
                    posZ + (rand.nextDouble() - 0.5D) * 0.6D, (rand.nextDouble() - 0.5D) * 0.1D, 0.05D, (rand.nextDouble() - 0.5D) * 0.1D);
        } else if (s == BROKEN && rand.nextInt(2) == 0) {       // a broken one smokes
            worldObj.spawnParticle(rand.nextInt(4) == 0 ? "largesmoke" : "smoke", posX + (rand.nextDouble() - 0.5D) * 0.5D,
                    posY + 1.2D + rand.nextDouble() * 0.6D, posZ + (rand.nextDouble() - 0.5D) * 0.5D, 0D, 0.04D, 0D);
        } else if (s == LIT && rand.nextInt(8) == 0) {          // a lit one: blue motes round its core
            double a = rand.nextDouble() * Math.PI * 2D;
            worldObj.spawnParticle("reddust", posX + Math.cos(a) * 0.45D, posY + 0.5D + rand.nextDouble() * 1.2D, posZ + Math.sin(a) * 0.45D,
                    0.35D, 0.78D, 1D);
        }
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        if (bound) {
            NBTTagCompound f = new NBTTagCompound();
            f.setInteger("ri", arenaRi);
            f.setInteger("rj", arenaRj);
            f.setLong("id", fightId);
            f.setInteger("k", index);
            tag.setTag(NBT_FIGHT, f);
        }
        NBTTagCompound s = new NBTTagCompound();
        s.setByte("s", (byte) coilState());
        long now = worldObj == null ? 0L : worldObj.getTotalWorldTime();
        s.setInteger("d", darkUntil > 0L ? (int) Math.max(1L, Math.min(Integer.MAX_VALUE, darkUntil - now)) : 0);
        s.setFloat("r", reserve);
        s.setBoolean("x", spent);
        tag.setTag(NBT_STATE, s);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        bound = tag.hasKey(NBT_FIGHT);
        NBTTagCompound f = tag.getCompoundTag(NBT_FIGHT);
        arenaRi = f.getInteger("ri");
        arenaRj = f.getInteger("rj");
        fightId = f.getLong("id");
        index = ((f.getInteger("k") % WardenRulesSC.COILS) + WardenRulesSC.COILS) % WardenRulesSC.COILS;
        dataWatcher.updateObject(DW_INDEX, Byte.valueOf((byte) index));
        if (tag.hasKey(NBT_STATE)) {
            NBTTagCompound s = tag.getCompoundTag(NBT_STATE);
            int st = s.getByte("s");
            setState(st == DARK || st == BROKEN ? st : LIT);
            int d = s.getInteger("d");
            long now = worldObj == null ? 0L : worldObj.getTotalWorldTime();   // the timer goes on from where it stood
            darkUntil = d > 0 ? now + d : 0L;
            setReserve(s.hasKey("r") ? s.getFloat("r") : WardenRulesSC.COIL_RESERVE);
            spent = s.getBoolean("x");
            if (coilState() == DARK && darkUntil == 0L && !spent) {
                darkUntil = now + 1L;                           // dark with no timer left: lit again at once
            }
        }
    }

    /** Server (the world test): the world tick a DARK coil lights again (0 - none). */
    public long darkUntil() {
        return darkUntil;
    }

    /** Server: a new coil at (x, y, z), «born in Si-14» (the Warden binds it, then puts it into the world). */
    public static EntityContainmentCoilSC create(WorldServer w, double x, double y, double z) {
        EntityContainmentCoilSC c = new EntityContainmentCoilSC(w);
        c.setLocationAndAngles(x, y, z, 0F, 0F);
        BornInSubstrateSC.mark(c);
        return c;
    }
}
