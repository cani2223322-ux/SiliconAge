package com.sc.settle.npc;

import java.util.List;

import com.sc.inventory.ContainerTradeSC;
import com.sc.mob.ISubstrateNativeSC;
import com.sc.settle.GuardAlertSC;
import com.sc.settle.ISettlementMemberSC;
import com.sc.settle.ISettlementNpcSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.npc.ai.EntityAIStrayWanderSC;
import com.sc.settle.npc.ai.EntityAITunerDaySC;
import com.sc.settle.npc.ai.EntityAITunerFleeSC;
import com.sc.settle.npc.ai.EntityAITunerHomeSC;
import com.sc.settle.npc.ai.EntityAITunerServeSC;
import com.sc.settle.npc.ai.NpcLinkSC;
import com.sc.settle.trade.TradeServerSC;

import net.minecraft.entity.EntityCreature;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.IEntityLivingData;
import net.minecraft.entity.INpc;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE N (docs/plan-substrate-tech-f2.md §6.1-§6.3, rules ПЖ-1..ПЖ-7): «Наладчик» (Tuner), a settlement's peaceful
 * technician - EntityCreature + INpc + ISettlementNpcSC + ISubstrateNativeSC (not EntityVillager, EntityAgeable or
 * IMob: see ISettlementNpcSC). Never despawns, never leashed, never bred. Registered by the coordinator in
 * MobEntitiesSC (pool id ID_TUNER = 2, "SiliconAge.tuner").
 * <p>
 * Fixed for everyone (the coordinator's): the DataWatcher slots (DW_KIND, DW_NAME, DW_MOOD; 20..23 stay free - a
 * Tuner wears no armour of the mod), the NBT (ISettlementMemberSC.NBT {"id", "slot"}, "scKind", "scName"), setKind,
 * bindTo.
 * <p>
 * Package N's (here and in settle.npc.ai): the look is the client's (RenderTunerSC: ModelBiped 1.9 high and thin, the
 * faded turquoise overall, the respirator's two diode eyes in the profession's colour, the pack with the mood lamp).
 * The day (ПЖ-4, by getWorldTime() % 24000, NpcLinkSC.phase): by its machine (the slot's post) until 11 500, on the
 * plaza by the core (P_PLAZA, spread round it by slot) until 13 500, at home (the slot's home) at night; a hostile
 * (IMob) within FLEE_RADIUS - it runs to the core. The home area is the dome (radius - 2; set again every time the
 * record is found - vanilla doesn't save it) with EntityAIMoveTowardsRestriction. The mood lamp (ПЖ-7, DW_MOOD): calm /
 * alarm (a guard of its dome near is watching, warning or fighting; it was hurt) / an enemy near; a mood holds
 * MOOD_HOLD ticks at least. A player trading with it (his open ContainerTradeSC is this Tuner's): it stops and faces
 * him, so the window doesn't close behind its back. Right-click: the «Терминал обмена» (TradeServerSC.open); sneaking -
 * one chat line: the name, the profession, the player's «Доверие» and level here. Players' blows are package C's
 * (SettlementProtectSC cancels them in the dome and the belt) - nothing here. A name tag can't be put on it (the click
 * is the trade's). Its death: SettlementRegistrySC.slotDied (the Clone Capsule's timer, package C). From a spawn egg: a
 * stray - a random trading profession and name, no dome (it trades at the base prices: package T).
 * <p>
 * Ф3, package S (docs/plan-substrate-tech-f3.md §6.1, rule ПЖ-8): the Power Engineer, the Armourer and the Salvager are
 * kinds like the others (their posts the plan's - the Armourer's at the guard square's armour station; their looks
 * RenderTunerSC's - the coil on the pack, Nano pieces over the overall, the scrap bag); a spawn egg may make any of the
 * seven traders (TRADERS); their sneaking right-click adds a line of what they do for a player (HINTED).
 * <p>
 * Ф6, package T (docs/plan-substrate-tech-f6.md §8.6, rules ПЖ-11, ПЖ-12, ПЖ-14): the Dispatcher («Диспетчер прокола») keeps to his
 * post the whole day and sleeps there (POSTED: no plaza at dusk, no house at night - an embassy's Dispatcher Office at the Vokzal, the
 * Substrate dock's one inside its gate; his home area round the post is NpcLinkSC.setHome's); the Quartermaster («Интендант») works
 * like any tradesman (his post at the supply depots, the plaza, a house). An embassy's Elder is called «Посол» (ПЖ-14) - in the
 * sneaking line too (NpcKindSC.langKey(embassy): the record's size here; the clients' nameplate and WAILA ask the world -
 * settle.client.SettlementClientSC.kind), and he tells the player of the embassy's ceiling of «Доверие» (sc.settle.tuner.hint.ambassador).
 * Both new kinds are HINTED; neither comes from a spawn egg (TRADERS is the Substrate's seven: they belong to their posts).
 */
public class EntityTunerSC extends EntityCreature implements INpc, ISettlementNpcSC, ISubstrateNativeSC {

    public static final String NAME = "tuner";
    /** DataWatcher (fixed): the kind (byte, NpcKindSC ordinal), the name code (int, NpcNamesSC), the mood (byte, MOOD_*). */
    public static final int DW_KIND = 24, DW_NAME = 25, DW_MOOD = 26;
    /** The mood lamp: green - calm, yellow - alarm, red - an enemy near. */
    public static final int MOOD_CALM = 0, MOOD_ALERT = 1, MOOD_DANGER = 2;

    /** §6.3: HP 20, a villager's speed 0.5; the path search's reach (NpcNavSC walks farther goals in legs). TODO(balance) */
    public static final double HP = 20D, SPEED = 0.5D, FOLLOW = 24D;
    /** Walking and running, multiples of SPEED (a villager walks at 0.6). */
    public static final double WALK = 0.6D, RUN = 0.9D;
    /** ПЖ-4: a hostile (IMob) this close - it runs to the core; within CORE_SAFE of the core it stops. */
    public static final int FLEE_RADIUS = 12, CORE_SAFE = 5;
    /** ПЖ-4 (NpcLinkSC's clock, here for the handbook): by its machine until WORK_UNTIL, on the plaza until PLAZA_UNTIL, then home. */
    public static final int WORK_UNTIL = NpcLinkSC.WORK_UNTIL, PLAZA_UNTIL = NpcLinkSC.PLAZA_UNTIL;
    /** ПЖ-7: «тревога» while a guard of its dome within ALARM_NEAR shows anything but calm; a mood holds MOOD_HOLD ticks at least. */
    public static final int ALARM_NEAR = 24, MOOD_HOLD = 60;
    /** How often it looks round (the danger, the guards' alarm, a customer), ticks. */
    public static final int THINK = 10;
    /** A spawn egg's Tuner: one of the trading professions (the Elder belongs to a settlement) - Ф3's three too. */
    public static final NpcKindSC[] TRADERS = {NpcKindSC.TECHNOLOGIST, NpcKindSC.METALLURGIST, NpcKindSC.GAS_TECH, NpcKindSC.CARTOGRAPHER,
            NpcKindSC.POWER_ENGINEER, NpcKindSC.ARMOURER, NpcKindSC.SALVAGER};
    /**
     * Ф3 (ПЖ-8): the professions whose sneaking right-click adds a second line - what they do for a player (sc.settle.tuner.hint.<key>);
     * Ф6 (ПЖ-11, ПЖ-12): the Dispatcher and the Quartermaster appended.
     */
    public static final NpcKindSC[] HINTED = {NpcKindSC.POWER_ENGINEER, NpcKindSC.ARMOURER, NpcKindSC.SALVAGER, NpcKindSC.DISPATCHER,
            NpcKindSC.QUARTERMASTER};
    /** Ф6 (ПЖ-11): the kinds that keep to their post all day and night (no plaza, no house): the Dispatcher. */
    public static final NpcKindSC[] POSTED = {NpcKindSC.DISPATCHER};
    /** The plaza at dusk: slot i stands PLAZA_SPREAD + i % 3 blocks off the point, at i x the golden angle round it. */
    public static final int PLAZA_SPREAD = 2;

    protected int settlementId = -1, slot = -1;
    /** The core column {x, z} of the record bound to (ISettlementMemberSC.boundCore), null unknown. */
    protected int[] boundCore;
    private final NpcLinkSC link;
    /** Server, refreshed every THINK ticks: the nearest hostile within FLEE_RADIUS, the nearest player trading with it. */
    private EntityLivingBase danger;
    private EntityPlayer customer;
    private long moodUntil;

    public EntityTunerSC(World world) {
        super(world);
        setSize(0.6F, 1.9F);
        link = new NpcLinkSC(this);
        getNavigator().setAvoidsWater(true);
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAITunerFleeSC(this));
        tasks.addTask(2, new EntityAITunerServeSC(this));
        tasks.addTask(3, new EntityAITunerDaySC(this));
        tasks.addTask(4, new EntityAITunerHomeSC(this, WALK));
        tasks.addTask(5, new EntityAIStrayWanderSC(this, WALK));
        tasks.addTask(6, new EntityAIWatchClosest(this, EntityPlayer.class, 6F));
        tasks.addTask(7, new EntityAILookIdle(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_KIND, Byte.valueOf((byte) NpcKindSC.ELDER.ordinal()));
        dataWatcher.addObject(DW_NAME, Integer.valueOf(0));
        dataWatcher.addObject(DW_MOOD, Byte.valueOf((byte) MOOD_CALM));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(HP);
        getEntityAttribute(SharedMonsterAttributes.movementSpeed).setBaseValue(SPEED);
        getEntityAttribute(SharedMonsterAttributes.followRange).setBaseValue(FOLLOW);
    }

    @Override
    protected boolean isAIEnabled() {
        return true;
    }

    // ------------------------------------------------------------------ ISettlementMemberSC

    @Override
    public int settlementId() {
        return settlementId;
    }

    @Override
    public int slot() {
        return slot;
    }

    @Override
    public int[] boundCore() {
        return boundCore;
    }

    @Override
    public NpcKindSC kind() {
        return NpcKindSC.of(dataWatcher.getWatchableObjectByte(DW_KIND));
    }

    public void setKind(NpcKindSC k) {
        dataWatcher.updateObject(DW_KIND, Byte.valueOf((byte) (k == null || k.guard ? NpcKindSC.ELDER : k).ordinal()));
    }

    @Override
    public int nameCode() {
        return dataWatcher.getWatchableObjectInt(DW_NAME);
    }

    public void setNameCode(int code) {
        dataWatcher.updateObject(DW_NAME, Integer.valueOf(code));
    }

    public int mood() {
        return dataWatcher.getWatchableObjectByte(DW_MOOD);
    }

    public void setMood(int m) {
        if (mood() != m) {
            dataWatcher.updateObject(DW_MOOD, Byte.valueOf((byte) m));
        }
    }

    @Override
    public void bindTo(SettlementRegistrySC.Settlement s, int slot) {
        link.forget();
        if (s == null || slot < 0 || slot >= s.slots.size()) {
            settlementId = -1;
            this.slot = -1;
            boundCore = null;
            return;
        }
        settlementId = s.id;
        this.slot = slot;
        boundCore = new int[]{s.x, s.z};
        SettlementRegistrySC.Slot sl = s.slots.get(slot);
        setKind(sl.kindOf());
        setNameCode(sl.nameCode());
        NpcLinkSC.setHome(this, s);
    }

    /** A spawn egg / a spawner: a stray - a random trading profession and name, no dome. */
    @Override
    public IEntityLivingData onSpawnWithEgg(IEntityLivingData data) {
        data = super.onSpawnWithEgg(data);
        if (!worldObj.isRemote && settlementId < 0) {
            setKind(TRADERS[rand.nextInt(TRADERS.length)]);
            if (nameCode() == 0) {
                setNameCode(NpcNamesSC.random(rand));
            }
        }
        return data;
    }

    // ------------------------------------------------------------------ the AI's questions (server)

    /** The link to its record (the AI tasks). */
    public NpcLinkSC link() {
        return link;
    }

    /** Bound to a settlement (link().get() is still null while its record is missing). */
    public boolean isBound() {
        return settlementId >= 0;
    }

    /** The nearest hostile within FLEE_RADIUS (refreshed every THINK ticks), or null. */
    public EntityLivingBase danger() {
        return danger != null && danger.isEntityAlive() ? danger : null;
    }

    /** The nearest player whose open trade window is this Tuner's, or null. */
    public EntityPlayer customer() {
        return customer != null && customer.isEntityAlive() && customer.openContainer instanceof ContainerTradeSC
                && ((ContainerTradeSC) customer.openContainer).getTuner() == this ? customer : null;
    }

    /**
     * ПЖ-4: where it should be now - its machine by day, its place on the plaza at dusk, its home at night (missing
     * points: the plaza, then the core); null for a stray. Ф6 (ПЖ-11): a POSTED kind (the Dispatcher) - its post at every hour.
     */
    public int[] dayGoal() {
        SettlementRegistrySC.Settlement s = link.get();
        if (s == null) {
            return null;
        }
        SettlementRegistrySC.Slot sl = link.slot();
        int ph = NpcLinkSC.phase(worldObj.getWorldTime());
        int[] posted = posted(kind()) ? NpcLinkSC.post(sl) : null;
        if (posted != null) {
            return posted;                                      // the Dispatcher: his desk by day, by dusk and by night
        }
        int[] p = ph == NpcLinkSC.PHASE_WORK ? NpcLinkSC.post(sl) : ph == NpcLinkSC.PHASE_HOME ? NpcLinkSC.home(sl) : null;
        if (p == null) {
            p = plazaSpot(s, slot);
        }
        return p != null ? p : NpcLinkSC.core(s);
    }

    /** Ф6 (ПЖ-11): kind `k` keeps to its post all day and night (POSTED). Pure. */
    public static boolean posted(NpcKindSC k) {
        for (NpcKindSC p : POSTED) {
            if (p == k) {
                return true;
            }
        }
        return false;
    }

    /** Slot `i`'s place on the plaza (P_PLAZA, else the guard square), round the point by the golden angle; null none. Pure. */
    public static int[] plazaSpot(SettlementRegistrySC.Settlement s, int i) {
        int[] p = NpcLinkSC.point(s, SettlementRegistrySC.P_PLAZA);
        if (p == null) {
            p = NpcLinkSC.point(s, SettlementRegistrySC.P_SQUARE);
        }
        if (p == null) {
            return null;
        }
        int n = Math.max(0, i);
        double a = n * 2.39996D;
        int r = PLAZA_SPREAD + n % 3;
        return new int[]{p[0] + (int) Math.round(Math.cos(a) * r), p[1], p[2] + (int) Math.round(Math.sin(a) * r)};
    }

    /** Server: a look round every THINK ticks (each Tuner on its own tick), then vanilla's AI. Out of the Substrate: removed (ПВ-8). */
    @Override
    protected void updateAITasks() {
        if ((ticksExisted + getEntityId()) % THINK == 0) {
            if (NpcLinkSC.exiled(this)) {
                return;
            }
            think();
        }
        super.updateAITasks();
    }

    private void think() {
        long now = worldObj.getTotalWorldTime();
        danger = nearestHostile();
        customer = findCustomer();
        int m = danger != null ? MOOD_DANGER : getAITarget() != null || alarmNear() ? MOOD_ALERT : MOOD_CALM;
        int cur = mood();
        if (m >= cur || now >= moodUntil || moodUntil - now > MOOD_HOLD) {     // up at once, down after the hold
            setMood(m);
        }
        if (m > MOOD_CALM && m >= cur) {
            moodUntil = now + MOOD_HOLD;
        }
    }

    private EntityLivingBase nearestHostile() {
        double r = FLEE_RADIUS;
        List<?> l = worldObj.getEntitiesWithinAABB(IMob.class, boundingBox.expand(r, r / 2D, r));
        EntityLivingBase best = null;
        double bd = r * r;
        for (Object o : l) {
            if (!(o instanceof EntityLivingBase) || o instanceof ISettlementMemberSC) {
                continue;
            }
            EntityLivingBase e = (EntityLivingBase) o;
            double d = getDistanceSqToEntity(e);
            if (e.isEntityAlive() && d < bd) {
                bd = d;
                best = e;
            }
        }
        return best;
    }

    /** A guard of the same dome within ALARM_NEAR is watching, warning or fighting. */
    private boolean alarmNear() {
        List<?> l = worldObj.getEntitiesWithinAABB(EntityWardenSC.class, boundingBox.expand(ALARM_NEAR, 8D, ALARM_NEAR));
        for (Object o : l) {
            EntityWardenSC g = (EntityWardenSC) o;
            if (g.isEntityAlive() && g.settlementId() == settlementId && g.alert() != GuardAlertSC.CALM) {
                return true;
            }
        }
        return false;
    }

    private EntityPlayer findCustomer() {
        EntityPlayer best = null;
        double bd = Double.MAX_VALUE;
        for (Object o : worldObj.playerEntities) {
            EntityPlayer p = (EntityPlayer) o;
            if (p.openContainer instanceof ContainerTradeSC && ((ContainerTradeSC) p.openContainer).getTuner() == this) {
                double d = getDistanceSqToEntity(p);
                if (d < bd) {
                    bd = d;
                    best = p;
                }
            }
        }
        return best;
    }

    // ------------------------------------------------------------------ never despawns, never on a lead

    @Override
    protected boolean canDespawn() {
        return false;
    }

    @Override
    public boolean allowLeashing() {
        return false;
    }

    /**
     * ПЖ-5: right-click - the «Терминал обмена» (TradeServerSC.open: its refusals are package T's); sneaking - the
     * standing line. Always taken (true): a name tag or another item's use on it never goes through. A FakePlayer: nothing.
     */
    @Override
    protected boolean interact(EntityPlayer p) {
        if (!isEntityAlive()) {
            return false;
        }
        if (worldObj.isRemote) {
            return true;
        }
        if (!(p instanceof EntityPlayerMP) || p instanceof FakePlayer) {
            return false;
        }
        if (p.isSneaking()) {
            tellStanding(p);
        } else {
            TradeServerSC.open((EntityPlayerMP) p, this);
        }
        return true;
    }

    /** «Тантал-3, Технолог. Ваше «Доверие»: 120 (Знакомый)» - every word translated by the reader's client. */
    private void tellStanding(EntityPlayer p) {
        SettlementRegistrySC r = SettlementRegistrySC.get(worldObj);
        SettlementRegistrySC.PlayerRec rec = r == null ? null : r.playerIfAny(p.getUniqueID());
        int rep = rec == null ? 0 : rec.reputation;
        SettlementRegistrySC.Settlement s = link.get();
        SettlementRegistrySC.LocalRec l = s == null ? null : s.localIfAny(p.getUniqueID());
        int level = ReputationSC.localLevel(rep, l != null && l.enemy(worldObj.getTotalWorldTime()));
        boolean embassy = SettlementSC.isEmbassy(s);
        IChatComponent msg = new ChatComponentTranslation(s == null ? "sc.settle.tuner.info.stray" : "sc.settle.tuner.info",
                NpcNamesSC.name(nameCode()), new ChatComponentTranslation(kind().langKey(embassy)), String.valueOf(rep),
                new ChatComponentTranslation("sc.settle.rep." + ReputationSC.LEVEL_KEYS[level]));
        msg.getChatStyle().setColor(EnumChatFormatting.AQUA);
        p.addChatComponentMessage(msg);
        for (NpcKindSC k : HINTED) {                            // Ф3, Ф6: what the new professions do for a player
            if (kind() == k) {
                IChatComponent hint = new ChatComponentTranslation("sc.settle.tuner.hint." + k.key);
                hint.getChatStyle().setColor(EnumChatFormatting.GRAY);
                p.addChatComponentMessage(hint);
            }
        }
        if (embassy && kind() == NpcKindSC.ELDER) {             // Ф6 (ПД-7): the Ambassador on the embassy's ceiling
            IChatComponent hint = new ChatComponentTranslation("sc.settle.tuner.hint.ambassador", String.valueOf(ReputationSC.EMBASSY_CEILING));
            hint.getChatStyle().setColor(EnumChatFormatting.GRAY);
            p.addChatComponentMessage(hint);
        }
    }

    /** The Clone Capsule's timer starts (package C respawns it). */
    @Override
    public void onDeath(DamageSource src) {
        super.onDeath(src);
        if (!worldObj.isRemote && dead) {
            NpcLinkSC.died(this);
        }
    }

    // ------------------------------------------------------------------ sounds: vanilla's for now (the «modem» trills later)

    @Override
    protected String getLivingSound() {
        return customer() != null ? "mob.villager.haggle" : mood() == MOOD_DANGER ? "mob.villager.no" : "mob.villager.idle";
    }

    @Override
    protected String getHurtSound() {
        return "mob.villager.hit";
    }

    @Override
    protected String getDeathSound() {
        return "mob.villager.death";
    }

    /** A higher, thinner voice than the Marauder's (0.8). */
    @Override
    protected float getSoundPitch() {
        return 1.25F + (rand.nextFloat() - rand.nextFloat()) * 0.1F;
    }

    @Override
    public int getTalkInterval() {
        return 240;
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        NBTTagCompound m = new NBTTagCompound();
        m.setInteger("id", settlementId);
        m.setInteger("slot", slot);
        if (boundCore != null) {
            m.setIntArray("core", boundCore.clone());
        }
        tag.setTag(ISettlementMemberSC.NBT, m);
        tag.setByte("scKind", (byte) kind().ordinal());
        tag.setInteger("scName", nameCode());
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        if (tag.hasKey(ISettlementMemberSC.NBT)) {
            NBTTagCompound m = tag.getCompoundTag(ISettlementMemberSC.NBT);
            settlementId = m.getInteger("id");
            slot = m.getInteger("slot");
            int[] c = m.getIntArray("core");
            boundCore = c.length == 2 ? c : null;
        }
        setKind(NpcKindSC.of(tag.getByte("scKind")));
        setNameCode(tag.getInteger("scName"));
        link.forget();
    }
}
