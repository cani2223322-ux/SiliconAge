package com.sc.settle.caravan;

import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.caravan.ai.EntityAICaravanEscortSC;
import com.sc.settle.caravan.ai.EntityAICaravanGuardTargetSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.settle.npc.ai.EntityAIWardenBeamSC;

import net.minecraft.entity.ai.EntityAIAttackOnCollide;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.2, rule ПН-13): a caravan's guard - a Sentry (EntityWardenSC: the mod's armour for real,
 * a fair fight with players - killing one is a guard's death for «Доверие», ПГ-8), bound to no settlement; K replaces the patrol AI with
 * the escort's (keep within 6 of the master, fight the ambushers and any hostile within 16 of it - the Ф2 beam / blade tasks stay),
 * never despawns while the caravan runs, leaves the world with it (the «казённая» armour - no drop, as every guard's).
 * <p>
 * Fixed (the coordinator's): the class, NAME (pool id 25, no spawn egg), the World constructor, the DataWatcher slot (DW_CARAVAN 28 -
 * its master's entity id + 1 for the clients, 0 none: an int), the NBT "scCaravan".
 * <p>
 * Package K's: «bound to no settlement» is the roster's - it is its caravan's target settlement's guard by id and core (join) with no
 * slot (-1: the census and slotDied leave it alone), so the Ф2 rules hold as for the dome's own: a player's blow in the belt is
 * HIT_WARDEN, a ПЭ-1 Point POINT_WARDEN, its death by a player KILL_WARDEN - -60 and «Враг» of that settlement (SettlementProtectSC,
 * EntityWardenSC.empHit, unchanged). Dressed as a Sentry (EntityWardenSC.equip: Nano 4/4 lit turquoise, a blade or an Ion Cutter, the
 * reserve, HP 40), a name drawn at random. The AI (the warden's cleared in the constructor - no patrol, no post, no leash to the core):
 * swimming; the beam (EntityAIWardenBeamSC) and the blade (vanilla's AttackOnCollide - MobCombatSC's blows), as every guard's; the escort
 * (EntityAICaravanEscortSC: its side of the master, within KEEP of it); looking about; the target (EntityAICaravanGuardTargetSC: what
 * comes within 16 of the master - the ambush, any hostile, who hits it or the master, the target settlement's offender). No home area
 * (hasHome false). Its first tick and every CLAIM_TICKS it asks CaravanSC.claim: not its caravan's guard any more - it leaves
 * (EntityCaravanMasterSC.sparks, setDead: the armour goes with it).
 */
public class EntityCaravanGuardSC extends EntityWardenSC {

    public static final String NAME = "caravanGuard";
    public static final int DW_CARAVAN = 28;
    public static final String NBT = "scCaravan";

    /** The caravan's id (scCaravan); 0 none (a /summon) - it leaves on its first tick. */
    private long caravanId;
    /** Server, set by its caravan (CaravanRunSC.claim): its side of the master, -1 left / 1 right. */
    private int side = 1;

    public EntityCaravanGuardSC(World world) {
        super(world);
        stepHeight = EntityCaravanMasterSC.STEP_HEIGHT;
        tasks.taskEntries.clear();                              // the patrol, the posts, the pads and the leash are a dome's
        targetTasks.taskEntries.clear();
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIWardenBeamSC(this, CHASE));
        tasks.addTask(2, new EntityAIAttackOnCollide(this, CHASE, true));
        tasks.addTask(3, new EntityAICaravanEscortSC(this));
        tasks.addTask(4, new EntityAIWatchClosest(this, EntityPlayer.class, 8F));
        tasks.addTask(5, new EntityAILookIdle(this));
        targetTasks.addTask(1, new EntityAICaravanGuardTargetSC(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_CARAVAN, Integer.valueOf(0));
    }

    // ------------------------------------------------------------------ the caravan (server)

    /**
     * Server, before spawnEntityInWorld (CaravanRunSC): a guard of caravan `id` coming to `s` - its guard by id and core with no slot,
     * dressed as a Sentry, a name, on the master's `side`.
     */
    void join(SettlementRegistrySC.Settlement s, long id, int side) {
        settlementId = s.id;
        slot = -1;
        boundCore = new int[]{s.x, s.z};
        link().forget();
        setKind(NpcKindSC.SENTRY);
        equip(NpcKindSC.SENTRY);
        setNameCode(NpcNamesSC.random(rand));
        caravanId = id;
        this.side = side;
    }

    /** The caravan's id (scCaravan), 0 none. */
    public long caravanId() {
        return caravanId;
    }

    /** Its side of the master (-1 left, 1 right). Server. */
    public int side() {
        return side;
    }

    void setSide(int side) {
        this.side = side < 0 ? -1 : 1;
    }

    /** Its master's entity id, -1 none (DW_CARAVAN). Both sides. */
    public int masterEntityId() {
        return dataWatcher.getWatchableObjectInt(DW_CARAVAN) - 1;
    }

    void setMasterEntityId(int id) {
        int v = id < 0 ? 0 : id + 1;
        if (dataWatcher.getWatchableObjectInt(DW_CARAVAN) != v) {
            dataWatcher.updateObject(DW_CARAVAN, Integer.valueOf(v));
        }
    }

    /** It walks the plateau with its master: no home area (no pull back to the dome, no leash). */
    @Override
    public boolean hasHome() {
        return false;
    }

    /** Server: a stale caravan's guard (or one of none) leaves the world on its first tick; asked again every CLAIM_TICKS. */
    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote && !isDead && getHealth() > 0F
                && (ticksExisted <= 1 || ticksExisted % EntityCaravanMasterSC.CLAIM_TICKS == 0) && !CaravanSC.claim(this, caravanId)) {
            leave();
        }
    }

    /** ПН-16: «into the dome» with its caravan - the sparks, gone; the «казённая» armour with it (setDead drops nothing). Server. */
    public void leave() {
        if (worldObj.isRemote || isDead) {
            return;
        }
        EntityCaravanMasterSC.sparks(this);
        setDead();
    }

    // ------------------------------------------------------------------ NBT

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setLong(NBT, caravanId);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        caravanId = tag.getLong(NBT);
    }
}
