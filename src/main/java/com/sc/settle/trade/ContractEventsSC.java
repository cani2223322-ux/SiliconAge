package com.sc.settle.trade;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import com.sc.arena.ArenaLayoutSC;
import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.ruin.RuinLayoutSC;
import com.sc.settle.SettlementRegistrySC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraftforge.common.util.FakePlayer;

/**
 * ПД-1 (docs/plan-ideas-2026-10-26.md, «Живая доска контрактов», ★ A): the new contracts' events that aren't a kill - «Снять показания в
 * Мёртвом Узле» (READINGS: standing on a Dead Node's plaza READINGS_TICKS - FML's player tick, every READINGS_EVERY), «Вскрыть сейф
 * лагеря» (SAFE: BlockCampSC calls safeOpened when a camp's safe opens for a player), «Забрать тайник Сигнала» (SIGNAL: SignalCacheSC calls
 * signalCache when its cache is opened). Kills (the storm's, the local hunts) are EconomyEventsSC.killCounts'. SCMod registers INSTANCE on
 * FML's bus (the coordinator's).
 * <p>
 * READINGS: a real player in the Substrate within READINGS_REACH of a built Dead Node's centre (RuinLayoutSC.nodeNear, not an arena,
 * MapGenDeadNodeSC.covers) - his time there is counted in memory (by UUID: a second player has his own); at READINGS_TICKS a step, if
 * that crossing isn't read yet for this contract (ContractRec.extra «nodes»: {i, j} pairs). Leaving the plaza or another Node starts it
 * again. Only the player's own steps: the squad's credit is for kills alone (ПД-2).
 * <p>
 * Fixed (the coordinator's): the class, INSTANCE, the hooks' signatures. The bodies, the tick's handler - ПД-1's.
 */
public final class ContractEventsSC {

    public static final ContractEventsSC INSTANCE = new ContractEventsSC();
    /** READINGS: on a Dead Node's plaza this long (10 s), looked at every READINGS_EVERY ticks. TODO(balance) */
    public static final int READINGS_TICKS = 200, READINGS_EVERY = 20;
    /** READINGS: the plaza - this far across from the crossing's centre. TODO(balance) */
    public static final int READINGS_REACH = 10;
    /** «Срочная депеша»: a late one is noticed this often (a multiple of READINGS_EVERY). */
    private static final int URGENT_EVERY = 200;

    /** A player's time on a plaza: {i, j, ticks} (memory only). */
    private static final Map<UUID, int[]> ON_PLAZA = new HashMap<UUID, int[]>();

    private ContractEventsSC() {
    }

    /** Server (BlockCampSC, the safe at (x, y, z) has just opened for `p` - a real player with the camp's key): SAFE's step. */
    public static void safeOpened(EntityPlayer p, int x, int y, int z) {
        step(p, ContractTableSC.SAFE);
    }

    /** Server (SignalCacheSC, the Signal's cache taken by `p`): SIGNAL's step. */
    public static void signalCache(EntityPlayer p) {
        step(p, ContractTableSC.SIGNAL);
    }

    /** Server stopped: the players' time on a plaza (memory only). */
    public static void clearAll() {
        synchronized (ON_PLAZA) {
            ON_PLAZA.clear();
        }
    }

    /** One step of every open contract of goal `goal` of real player `p` (SAFE, SIGNAL). */
    private static void step(EntityPlayer p, int goal) {
        if (!(p instanceof EntityPlayerMP) || p instanceof FakePlayer || p.worldObj == null || p.worldObj.isRemote) {
            return;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = reg == null ? null : reg.playerIfAny(p.getUniqueID());
        if (rec == null) {
            return;
        }
        boolean changed = false;
        for (SettlementRegistrySC.ContractRec r : rec.open()) {
            ContractTableSC.Contract k = ContractTableSC.byKey(r.key);
            if (k != null && k.goal == goal && r.progress < r.target) {
                r.progress++;
                changed = true;
                EconomyEventsSC.say(p, k, r);
            }
        }
        if (changed) {
            reg.markDirty();
        }
    }

    // ------------------------------------------------------------------ READINGS

    /** FML: every READINGS_EVERY ticks of a real player in the Substrate with an open READINGS contract - the time on a Node's plaza. */
    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || !(e.player instanceof EntityPlayerMP) || e.player instanceof FakePlayer
                || e.player.worldObj == null || e.player.worldObj.isRemote || e.player.ticksExisted % READINGS_EVERY != 0) {
            return;
        }
        EntityPlayer p = e.player;
        UUID id = p.getUniqueID();
        if (!SubstrateSC.is(p.worldObj) || p.isDead) {
            forget(id);
            return;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec rec = reg == null ? null : reg.playerIfAny(id);
        if (rec != null && p.ticksExisted % URGENT_EVERY == 0 && TradeServerSC.expireUrgent(p, rec, p.worldObj.getWorldTime())) {
            reg.markDirty();                                    // «Срочная депеша» past sunset: lost, one line (not only at an Elder)
        }
        SettlementRegistrySC.ContractRec open = rec == null ? null : openReadings(rec);
        int x = MathHelper.floor_double(p.posX), z = MathHelper.floor_double(p.posZ);
        int[] node = open == null ? null : plaza(p, x, z);
        if (node == null) {
            forget(id);
            return;
        }
        int[] t;
        synchronized (ON_PLAZA) {
            t = ON_PLAZA.get(id);
            if (t == null || t[0] != node[0] || t[1] != node[1]) {
                t = new int[]{node[0], node[1], 0};
                ON_PLAZA.put(id, t);
            }
            t[2] += READINGS_EVERY;
        }
        if (t[2] < READINGS_TICKS || t[2] - READINGS_EVERY >= READINGS_TICKS) {
            return;                                             // not yet - or this stay already counted (once a stay)
        }
        if (readingsStep(open, node[0], node[1])) {
            reg.markDirty();
            ContractTableSC.Contract k = ContractTableSC.byKey(open.key);
            p.addChatComponentMessage(new ChatComponentTranslation("sc.contract.readings.step",
                    String.valueOf(node[0] * SubstrateTerrainSC.GRID), String.valueOf(node[1] * SubstrateTerrainSC.GRID))
                    .setChatStyle(new ChatStyle().setColor(EnumChatFormatting.DARK_AQUA)));
            if (k != null) {
                EconomyEventsSC.say(p, k, open);
            }
        }
    }

    /** The open READINGS contract of `rec` not done yet, or null. */
    private static SettlementRegistrySC.ContractRec openReadings(SettlementRegistrySC.PlayerRec rec) {
        for (SettlementRegistrySC.ContractRec r : rec.open()) {
            ContractTableSC.Contract k = ContractTableSC.byKey(r.key);
            if (k != null && k.goal == ContractTableSC.READINGS && r.progress < r.target) {
                return r;
            }
        }
        return null;
    }

    /** The crossing {i, j} whose built Dead Node's plaza `p` stands on at column (x, z) (not an arena), or null. Server. */
    static int[] plaza(EntityPlayer p, int x, int z) {
        int[] n = RuinLayoutSC.nodeNear(x, z);
        if (n == null) {
            return null;
        }
        double dx = p.posX - (n[0] * SubstrateTerrainSC.GRID + 0.5D), dz = p.posZ - (n[1] * SubstrateTerrainSC.GRID + 0.5D);
        if (dx * dx + dz * dz > (double) READINGS_REACH * READINGS_REACH || ArenaLayoutSC.isArena(p.worldObj.getSeed(), n[0], n[1])) {
            return null;
        }
        ChunkProviderSubstrateSC gen = ChunkProviderSubstrateSC.of(p.worldObj);
        return gen != null && gen.nodes().covers(x, z, 0) ? n : null;
    }

    /**
     * Contract `r`'s step for the readings of crossing (i, j): counted if that crossing isn't in its «nodes» yet. Pure on the record.
     * @return true if it moved
     */
    public static boolean readingsStep(SettlementRegistrySC.ContractRec r, int i, int j) {
        if (r == null || r.progress >= r.target) {
            return false;
        }
        int[] done = r.extra.getIntArray(ContractTableSC.X_NODES);
        for (int k = 0; k + 1 < done.length; k += 2) {
            if (done[k] == i && done[k + 1] == j) {
                return false;
            }
        }
        int[] more = new int[done.length + 2];
        System.arraycopy(done, 0, more, 0, done.length);
        more[done.length] = i;
        more[done.length + 1] = j;
        r.extra.setIntArray(ContractTableSC.X_NODES, more);
        r.progress++;
        return true;
    }

    private static void forget(UUID id) {
        synchronized (ON_PLAZA) {
            ON_PLAZA.remove(id);
        }
    }
}
