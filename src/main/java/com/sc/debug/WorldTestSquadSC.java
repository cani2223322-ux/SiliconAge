package com.sc.debug;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.mojang.authlib.GameProfile;
import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.mob.BornInSubstrateSC;
import com.sc.mob.EntityMarauderSC;
import com.sc.mob.MobEventsSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SquadSC;
import com.sc.settle.trade.ContractTableSC;
import com.sc.settle.trade.EconomyEventsSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.DamageSource;
import net.minecraft.world.WorldServer;
import net.minecraftforge.event.entity.living.LivingDeathEvent;

/**
 * ПД-2 (ИГ-2 А of the audit 2026-11-04; docs/plan-ideas-2026-10-26.md, the Substrate's region k = 2: X 22048, Z 20000), -Dsc.worldtest=true:
 * a squad of two bots and a stranger. The bots are StationPlayers - real EntityPlayerMP (SquadSC.mates and MobEventsSC's log never count a
 * FakePlayer), never spawned; for the kill's moment only they stand in the server's player list (MobEventsSC.killerOf / participants look
 * there) and are taken out of it at once. SquadSC.join - mates / matesOf; a Marauder (never spawned, «born» in the Substrate) with a damage
 * log of its own (MobEventsSC.restoreLog): the killer 80% and his mate 15% - both credited (SquadSC.contractCredits), the killing's event
 * (EconomyEventsSC.onDeath, called directly) moves both mates' «Убить Мародёров» and not the stranger's 5%; the mate's 9% - the killer alone;
 * a stranger's 40% - never. SKIP while the Substrate or the rule dimension.squads is off. The squad is left, the contracts cleared.
 */
public final class WorldTestSquadSC {

    static final int START = WorldTestContractsSC.START + 60;
    private static final int X = 20000 + 2 * 1024, Z = 20000, Y = 120;
    private int ticks;
    private boolean done;

    @SubscribeEvent
    public void onTick(TickEvent.ServerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || done) {
            return;
        }
        if (++ticks < START) {
            return;
        }
        done = true;
        try {
            run();
        } catch (Throwable t) {
            print(false, "exception " + t);
            t.printStackTrace();
        }
    }

    @SuppressWarnings("unchecked")
    private void run() {
        if (!SubstrateSC.active()) {
            System.out.println("[SC-WORLDTEST] SKIP squad: the dimension is off (" + SubstrateSC.offReason() + ")");
            return;
        }
        if (!SubstrateConfigSC.squads) {
            System.out.println("[SC-WORLDTEST] SKIP squad: the rule dimension.squads is off");
            return;
        }
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer w = srv.worldServerForDimension(SubstrateSC.dimId());
        SettlementRegistrySC reg = w == null ? null : SettlementRegistrySC.get(w);
        if (w == null || reg == null || srv.getConfigurationManager() == null) {
            print(false, "the Substrate's world, the registry or the player list not there");
            return;
        }
        WorldTestArmourerSC.StationPlayer a = bot(w, "lead", 0), b = bot(w, "mate", 2), c = bot(w, "stranger", 4);
        List<Object> list = srv.getConfigurationManager().playerEntityList;
        EntityMarauderSC mob = new EntityMarauderSC(w);                     // never spawned
        mob.setLocationAndAngles(X + 0.5D, Y, Z + 6.5D, 0F, 0F);
        BornInSubstrateSC.mark(mob);
        long day = SettlementSC.day(w);
        try {
            for (WorldTestArmourerSC.StationPlayer p : new WorldTestArmourerSC.StationPlayer[]{a, b, c}) {
                SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
                SquadSC.leave(reg, p.getUniqueID(), p.getCommandSenderName());      // an interrupted run's squad
                pr.contracts.clear();
                pr.rollDay(day - 1);
                pr.rollDay(day);
            }
            boolean joined = SquadSC.join(reg, a.getUniqueID(), a.getCommandSenderName(), b.getUniqueID(), b.getCommandSenderName(), w.getTotalWorldTime());
            boolean mates = joined && SquadSC.mates(a, b) && SquadSC.mates(b, a) && !SquadSC.mates(a, c) && SquadSC.matesOf(a.getUniqueID()).contains(b.getUniqueID())
                    && !SquadSC.matesOf(a.getUniqueID()).contains(c.getUniqueID()) && SquadSC.squadOf(reg.playerIfAny(a.getUniqueID())) != 0L;
            print(mates, "ПД-2 the squad of two bots: join " + joined + ", mates both ways, the stranger not, matesOf, the squad's id " + mates);
            list.add(a);
            list.add(b);
            list.add(c);
            // 1. the killer 80%, his mate 15%, a stranger 5%
            String one = kill(reg, mob, a, b, c, 80F, 15F, 5F, true, true, false);
            // 2. the mate's 9%: below SquadSC.SHARE - the killer alone
            String two = kill(reg, mob, a, b, c, 91F, 9F, 0F, true, false, false);
            // 3. a stranger's 40%: never a mate
            String three = kill(reg, mob, a, b, c, 60F, 0F, 40F, true, false, false);
            boolean ok = one.startsWith("ok") && two.startsWith("ok") && three.startsWith("ok");
            print(ok, "ПД-2 the share of the damage (" + (int) (SquadSC.SHARE * 100F) + "% for a mate, killer " + (int) (com.sc.mob.MobDropsSC.KILLER_SHARE * 100F)
                    + "%+): 80 / 15 / 5 " + one + "; 91 / 9 / 0 " + two + "; 60 / 0 / 40 " + three);
        } finally {
            list.remove(a);
            list.remove(b);
            list.remove(c);
            MobEventsSC.forget(mob);
            for (WorldTestArmourerSC.StationPlayer p : new WorldTestArmourerSC.StationPlayer[]{a, b, c}) {
                SquadSC.leave(reg, p.getUniqueID(), p.getCommandSenderName());
                SettlementRegistrySC.PlayerRec pr = reg.playerIfAny(p.getUniqueID());
                if (pr != null) {
                    pr.contracts.clear();
                }
                p.inventory.clearInventory(null, -1);
            }
            reg.markDirty();
        }
    }

    /**
     * One kill of `mob` with the damage log a / b / c (a the killer): who SquadSC.contractCredits names and whose «Убить Мародёров» the
     * death's event moves (EconomyEventsSC.onDeath). @return "ok (...)" or "BAD (...)"
     */
    private static String kill(SettlementRegistrySC reg, EntityMarauderSC mob, EntityPlayerMP a, EntityPlayerMP b, EntityPlayerMP c, float da, float db,
                               float dc, boolean wantA, boolean wantB, boolean wantC) {
        Map<UUID, Float> log = new HashMap<UUID, Float>();
        log.put(a.getUniqueID(), Float.valueOf(da));
        if (db > 0F) {
            log.put(b.getUniqueID(), Float.valueOf(db));
        }
        if (dc > 0F) {
            log.put(c.getUniqueID(), Float.valueOf(dc));
        }
        MobEventsSC.restoreLog(mob, log, da + db + dc);
        int[] before = new int[3];
        EntityPlayerMP[] ps = {a, b, c};
        for (int i = 0; i < 3; i++) {
            before[i] = contract(reg, ps[i]).progress;
        }
        List<String> credited = new ArrayList<String>();
        boolean[] got = new boolean[3];
        for (EntityPlayerMP p : SquadSC.contractCredits(mob, MobEventsSC.killerOf(mob))) {
            credited.add(p.getCommandSenderName());
            for (int i = 0; i < 3; i++) {
                got[i] |= p == ps[i];
            }
        }
        EconomyEventsSC.INSTANCE.onDeath(new LivingDeathEvent(mob, DamageSource.causePlayerDamage(a)));
        int[] moved = new int[3];
        for (int i = 0; i < 3; i++) {
            moved[i] = contract(reg, ps[i]).progress - before[i];
        }
        boolean[] want = {wantA, wantB, wantC};
        boolean ok = true;
        for (int i = 0; i < 3; i++) {
            ok &= got[i] == want[i] && moved[i] == (want[i] ? 1 : 0);
        }
        MobEventsSC.forget(mob);
        return (ok ? "ok" : "BAD") + " (credited " + credited + ", the contracts moved " + moved[0] + "/" + moved[1] + "/" + moved[2] + ")";
    }

    /** The player's open «Убить Мародёров» (one made if none - the record as a take makes it). */
    private static SettlementRegistrySC.ContractRec contract(SettlementRegistrySC reg, EntityPlayerMP p) {
        SettlementRegistrySC.PlayerRec pr = reg.player(p.getUniqueID());
        for (SettlementRegistrySC.ContractRec r : pr.contracts) {
            if (!r.done && ContractTableSC.KILL_MARAUDERS.equals(r.key)) {
                return r;
            }
        }
        SettlementRegistrySC.ContractRec r = new SettlementRegistrySC.ContractRec();
        r.key = ContractTableSC.KILL_MARAUDERS;
        ContractTableSC.Contract k = ContractTableSC.byKey(r.key);
        r.target = k == null ? 10 : k.target;
        r.takenDay = SettlementSC.day(p.worldObj);
        pr.contracts.add(r);
        return r;
    }

    private static WorldTestArmourerSC.StationPlayer bot(WorldServer w, String name, int dx) {
        WorldTestArmourerSC.StationPlayer p = new WorldTestArmourerSC.StationPlayer(w, new GameProfile(UUID.nameUUIDFromBytes(("sc-pd2-" + name).getBytes()),
                "[SCTestSquad" + Character.toUpperCase(name.charAt(0)) + name.substring(1) + "]"));
        p.setLocationAndAngles(X + dx + 0.5D, Y, Z + 0.5D, 0F, 0F);
        return p;
    }

    private static void print(boolean ok, String what) {
        System.out.println("[SC-WORLDTEST] " + (ok ? "PASS" : "FAIL") + " squad: " + what);
    }
}
