package com.sc.settle.caravan;

import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.caravan.ai.EntityAICaravanWalkSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.NpcNamesSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.SharedMonsterAttributes;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentText;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE K (docs/plan-substrate-tech-f5.md §8.2, rule ПН-13): a caravan's master - a Tuner (EntityTunerSC: INpc, a settler - the
 * dome's and its belt's protection from players holds, the trade window doesn't open: the master trades at home), bound to no
 * settlement (no roster slot: its death never empties one), with a pack frame and crates (its own render or RenderTunerSC with a
 * layer - K's). K replaces the Tuner's day AI with the caravan's (walking to the gate by the move helper - no long path search,
 * stepHeight for the terraces; standing still while ambushed; fleeing behind its guards), never despawns while its caravan runs,
 * leaves the world when it ends (CaravanSC).
 * <p>
 * Fixed (the coordinator's): the class, NAME (pool id 24, no spawn egg), the World constructor, the DataWatcher slot (DW_CARAVAN 27:
 * byte - CaravanSC's state for the clients: 0 walking, 1 ambushed, 2 arrived), the NBT "scCaravan" (the caravan's id - a stale one
 * after a restart leaves the world on its first tick).
 * <p>
 * Package K's: «bound to no settlement» is the roster's - it is its caravan's target settlement's creature by id and core (join) with
 * no slot (-1: the census and slotDied leave it alone), so ПЖ-2 and the Tuners' alarm hold as for the dome's own. A Cartographer's
 * skin (LOOK - the routes are his trade; RenderCaravanMasterSC adds the frame, the crates and the caravan's lamp), MASTER_HP, a name
 * drawn at random. The AI (the Tuner's cleared in the constructor): swimming, the caravan's walk (EntityAICaravanWalkSC: the route's
 * waypoints by the move helper; behind its guards while ambushed or a hostile is near), looking at players. No home area (hasHome
 * false: it walks the plateau). Right-click: one line - its name and the caravan's target. Its first tick and every CLAIM_TICKS it asks
 * CaravanSC.claim: not its caravan's master any more (a restart, the caravan over while its chunk slept, a /summon) - it leaves
 * (leave: the Tuners' turquoise sparks, no death, nothing dropped).
 */
public class EntityCaravanMasterSC extends EntityTunerSC {

    public static final String NAME = "caravanMaster";
    public static final int DW_CARAVAN = 27;
    public static final String NBT = "scCaravan";

    /** ПН-13: tougher than a dome's Tuner (20) - an ambush is a fight, not one shot. TODO(balance) */
    public static final double MASTER_HP = 40D;
    /** The caravan's walk, a multiple of EntityTunerSC.SPEED (0.25 - its guards' chase, 0.3, keeps up). TODO(balance) */
    public static final double CARAVAN_WALK = 0.5D;
    /** ПН-13: the terraces' steps of 2 walked up without the path search. */
    public static final float STEP_HEIGHT = 2.0F;
    /** How often it asks whether its caravan still runs (and on its first tick). */
    public static final int CLAIM_TICKS = 20;
    /** Its skin and diodes: a Cartographer's. */
    public static final NpcKindSC LOOK = NpcKindSC.CARTOGRAPHER;

    /** The caravan's id (scCaravan); 0 none (a /summon) - it leaves on its first tick. */
    private long caravanId;

    public EntityCaravanMasterSC(World world) {
        super(world);
        stepHeight = STEP_HEIGHT;
        tasks.taskEntries.clear();                              // the Tuner's day, trade and flight to the core are a dome's
        targetTasks.taskEntries.clear();
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAICaravanWalkSC(this));
        tasks.addTask(2, new EntityAIWatchClosest(this, EntityPlayer.class, 6F));
        tasks.addTask(3, new EntityAILookIdle(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_CARAVAN, Byte.valueOf((byte) 0));
    }

    @Override
    protected void applyEntityAttributes() {
        super.applyEntityAttributes();
        getEntityAttribute(SharedMonsterAttributes.maxHealth).setBaseValue(MASTER_HP);
    }

    // ------------------------------------------------------------------ the caravan (server)

    /**
     * Server, before spawnEntityInWorld (CaravanRunSC): the master of caravan `id` coming to `s` - its creature by id and core with no
     * slot, a Cartographer's look, a name, full health.
     */
    void join(SettlementRegistrySC.Settlement s, long id) {
        settlementId = s.id;
        slot = -1;
        boundCore = new int[]{s.x, s.z};
        link().forget();
        setKind(LOOK);
        setNameCode(NpcNamesSC.random(rand));
        setHealth(getMaxHealth());
        caravanId = id;
    }

    /** The caravan's id (scCaravan), 0 none. */
    public long caravanId() {
        return caravanId;
    }

    /** The caravan's state for the clients (CaravanRunSC.WALKING / AMBUSHED / ARRIVED). Both sides. */
    public int caravanState() {
        return dataWatcher.getWatchableObjectByte(DW_CARAVAN);
    }

    void setCaravanState(int st) {
        if (caravanState() != st) {
            dataWatcher.updateObject(DW_CARAVAN, Byte.valueOf((byte) st));
        }
    }

    /** It walks the plateau: no home area (RandomPositionGenerator and NpcNavSC's legs never pull it back to its dome). */
    @Override
    public boolean hasHome() {
        return false;
    }

    /** Server: a stale caravan's master (or one of none) leaves the world on its first tick; asked again every CLAIM_TICKS. */
    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote && !isDead && getHealth() > 0F && (ticksExisted <= 1 || ticksExisted % CLAIM_TICKS == 0)
                && !CaravanSC.claim(this, caravanId)) {
            leave();
        }
    }

    /** ПН-16: «into the dome» - the Tuners' sparks, gone (no death: nothing drops, no slot's timer). Server. */
    public void leave() {
        if (worldObj.isRemote || isDead) {
            return;
        }
        sparks(this);
        setDead();
    }

    /** The Tuners' turquoise sparks round `e` and the power-off hum: a caravan's creature going «into the dome». Server. */
    static void sparks(Entity e) {
        if (e.worldObj instanceof WorldServer) {
            WorldServer ws = (WorldServer) e.worldObj;
            ws.func_147487_a("fireworksSpark", e.posX, e.posY + e.height * 0.5D, e.posZ, 16, 0.3D, 0.5D, 0.3D, 0.05D);
            for (int i = 0; i < 10; i++) {                      // a turquoise column where it stood
                ws.func_147487_a("reddust", e.posX + (e.worldObj.rand.nextDouble() - 0.5D) * 0.6D, e.posY + i * 0.25D,
                        e.posZ + (e.worldObj.rand.nextDouble() - 0.5D) * 0.6D, 0, 0.24D, 0.82D, 0.78D, 1.0D);
            }
        }
        SoundsSC.play(e.worldObj, e.posX, e.posY + 1D, e.posZ, "power.off", 0.6F, 1.2F);
    }

    /** Right-click: «Тантал-3, караванщик. Цель: купол «Ниобий-4». Торгую только дома.» Always taken (no name tag); a FakePlayer: nothing. */
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
        SettlementRegistrySC.Settlement s = link().get();
        IChatComponent where = s == null ? new ChatComponentText("?") : SettlementSC.displayName(s);
        IChatComponent msg = new ChatComponentTranslation("sc.caravan.master.info", NpcNamesSC.name(nameCode()), where);
        msg.getChatStyle().setColor(EnumChatFormatting.AQUA);
        p.addChatComponentMessage(msg);
        return true;
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
