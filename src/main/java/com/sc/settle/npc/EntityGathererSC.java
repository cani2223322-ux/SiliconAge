package com.sc.settle.npc;

import com.sc.settle.GathererSC;
import com.sc.settle.NpcKindSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.npc.ai.EntityAIGathererWalkSC;
import com.sc.util.SoundsSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.ai.EntityAILookIdle;
import net.minecraft.entity.ai.EntityAISwimming;
import net.minecraft.entity.ai.EntityAIWatchClosest;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.entity.projectile.EntityArrow;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.DamageSource;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ПД-5 (docs/plan-ideas-2026-10-26.md, «Ожившие поселения», ★ A, part 1): «Собиратель» - a Settlement's or a City's Tuner who walks
 * out by day to a druse or a fumarole while a player is within 96 blocks (com.sc.settle.GathererSC sends it and takes it back), gathers
 * and comes home; a hostile on it - a call for help in the chat with the direction; saved - +10 «Доверия» and a small gift (once a game
 * day a settlement). Like the caravan's master (EntityCaravanMasterSC): a Tuner (INpc, a settler) bound by id and core to its home with
 * no roster slot (-1), the Tuner's dome AI replaced by its own; never hurt by a player anywhere (not only in the dome and its belt);
 * no trade window.
 * <p>
 * Its walk (EntityAIGathererWalkSC): out to the spot by the move helper (no long path search), GATHER_TICKS gathering there (the arm
 * swinging, the sparks), home to the gate; under the dome on its way home - it goes in (the Tuners' sparks, no death). A hostile within
 * GathererSC.DANGER - it runs home. Its outing is GathererSC's memory: a stale one (a restart, the outing over) leaves on its first tick
 * (asked again every CLAIM_TICKS).
 * <p>
 * Fixed (the coordinator's): the class, NAME (pool id MobEntitiesSC.ID_GATHERER 27, no spawn egg), the World constructor, the look LOOK
 * (a Gas Technician's skin - RenderGathererSC adds the sack, ПД-5's), the DataWatcher slot DW_GATHER 27 (byte: GathererSC's state for
 * the clients - 0 out, 1 gathering, 2 home, 3 attacked), the NBT key NBT ("scGatherer": its outing's id - a stale one leaves the world on
 * its first tick). The AI, the state, the rescue - ПД-5's.
 */
public class EntityGathererSC extends EntityTunerSC {

    public static final String NAME = "gatherer";
    public static final int DW_GATHER = 27;
    public static final String NBT = "scGatherer";
    /** The states DW_GATHER carries. */
    public static final int OUT = 0, GATHERING = 1, HOME = 2, ATTACKED = 3;
    /** Its skin and diodes: a Gas Technician's (fumaroles are his trade). */
    public static final NpcKindSC LOOK = NpcKindSC.GAS_TECH;
    /** The terraces climbed by its step; how often it asks for its outing. */
    public static final float STEP_HEIGHT = 2F;
    public static final int CLAIM_TICKS = 20;

    /** Its outing's id (scGatherer); 0 none - it leaves on its first tick. */
    private long outingId;
    /** Server: the ticks gathered at the spot. */
    public int gathered;

    public EntityGathererSC(World world) {
        super(world);
        stepHeight = STEP_HEIGHT;
        tasks.taskEntries.clear();                              // the Tuner's day, trade and flight to the core are a dome's
        targetTasks.taskEntries.clear();
        tasks.addTask(0, new EntityAISwimming(this));
        tasks.addTask(1, new EntityAIGathererWalkSC(this));
        tasks.addTask(2, new EntityAIWatchClosest(this, EntityPlayer.class, 6F));
        tasks.addTask(3, new EntityAILookIdle(this));
    }

    @Override
    protected void entityInit() {
        super.entityInit();
        dataWatcher.addObject(DW_GATHER, Byte.valueOf((byte) OUT));
    }

    /** GathererSC's state for the clients (OUT / GATHERING / HOME / ATTACKED). Both sides. */
    public int gatherState() {
        return dataWatcher.getWatchableObjectByte(DW_GATHER);
    }

    /** Server. */
    public void setGatherState(int st) {
        if (gatherState() != st) {
            dataWatcher.updateObject(DW_GATHER, Byte.valueOf((byte) st));
        }
    }

    /** It walks the plateau: no home area. */
    @Override
    public boolean hasHome() {
        return false;
    }

    /** Server, before spawnEntityInWorld (GathererSC.send): a settler of `s` with no slot, the Gas Technician's look, a name, outing `id`. */
    public void join(SettlementRegistrySC.Settlement s, long id) {
        settlementId = s.id;
        slot = -1;
        boundCore = new int[]{s.x, s.z};
        link().forget();
        setKind(LOOK);
        setNameCode(NpcNamesSC.random(rand));
        setHealth(getMaxHealth());
        outingId = id;
        setGatherState(OUT);
    }

    /** Its outing's id (scGatherer). */
    public long outingId() {
        return outingId;
    }

    /** ПД-5: never hurt by a player or his arrow, anywhere (the hostiles still can). */
    @Override
    public boolean attackEntityFrom(DamageSource src, float amount) {
        Entity by = src == null ? null : src.getEntity();
        if (by instanceof EntityPlayer || src != null && src.getSourceOfDamage() instanceof EntityArrow
                && ((EntityArrow) src.getSourceOfDamage()).shootingEntity instanceof EntityPlayer) {
            return false;
        }
        return super.attackEntityFrom(src, amount);
    }

    /** Server: a stale Gatherer (or one of none) leaves the world on its first tick; asked again every CLAIM_TICKS. */
    @Override
    public void onLivingUpdate() {
        super.onLivingUpdate();
        if (!worldObj.isRemote && !isDead && getHealth() > 0F && (ticksExisted <= 1 || ticksExisted % CLAIM_TICKS == 0)
                && GathererSC.claim(this, outingId) == null) {
            leave();
        }
    }

    /** «Into the dome» - the Tuners' sparks, gone (no death: nothing drops). Server. */
    public void leave() {
        if (worldObj.isRemote || isDead) {
            return;
        }
        if (worldObj instanceof WorldServer) {
            ((WorldServer) worldObj).func_147487_a("fireworksSpark", posX, posY + height * 0.5D, posZ, 16, 0.3D, 0.5D, 0.3D, 0.05D);
        }
        SoundsSC.play(worldObj, posX, posY + 1D, posZ, "power.off", 0.6F, 1.2F);
        GathererSC.done(outingId);
        setDead();
    }

    /** Right-click: «Тантал-3 • Собиратель - идёт к друзе». No trade. A FakePlayer: nothing. */
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
        GathererSC.Outing o = GathererSC.outing(this);
        IChatComponent to = new ChatComponentTranslation(o != null && o.druse ? "sc.gatherer.spot.druse" : "sc.gatherer.spot.fumarole");
        IChatComponent msg = new ChatComponentTranslation("sc.gatherer.line", NpcNamesSC.name(nameCode()), to);
        msg.getChatStyle().setColor(EnumChatFormatting.AQUA);
        p.addChatComponentMessage(msg);
        return true;
    }

    @Override
    public void writeEntityToNBT(NBTTagCompound tag) {
        super.writeEntityToNBT(tag);
        tag.setLong(NBT, outingId);
    }

    @Override
    public void readEntityFromNBT(NBTTagCompound tag) {
        super.readEntityFromNBT(tag);
        outingId = tag.getLong(NBT);
    }
}
