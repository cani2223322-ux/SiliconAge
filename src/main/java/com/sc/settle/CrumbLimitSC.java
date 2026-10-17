package com.sc.settle;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §9.4, rule ПТ-17; proposal §10 «Не больше 3 Крупиц в сутки на игрока из всех
 * источников мира, кроме босса»): the Singularity Crumbs a player may still get from the Substrate today. Every source of the
 * world but the boss asks take() before it gives one: a Sealed Crumb opened (the ruins' and Vaults' chests, the Sentinel's and
 * the Eclipse's drops - ItemRelicSC, package C), the Elder's weekly sale (TradeServerSC, package C). Per player by UUID
 * (two LAN friends have their own), the game day of SettlementSC.day; kept in the settlements' registry
 * (SettlementRegistrySC.PlayerRec.extra "K": {d: day, n: crumbs that day}) - global storage, so the bridge's other worlds and a
 * deleted DIM-1414 leave it alone. Server only; a FakePlayer never gets one. Shared core: only the coordinator edits this file.
 */
public final class CrumbLimitSC {

    /** ПТ-17: Crumbs a player a game day from all the world's sources but the boss. TODO(balance) */
    public static final int DAY_CAP = 3;
    /** The PlayerRec.extra key, and its fields. */
    public static final String EXTRA = "K", DAY = "d", COUNT = "n";

    private CrumbLimitSC() {
    }

    /** Pure: how many of `want` may be given with `usedToday` given already under `cap`. */
    public static int allowed(int usedToday, int want, int cap) {
        return Math.max(0, Math.min(want, cap - Math.max(0, usedToday)));
    }

    /** Crumbs the player may still get today (0 on a client, for a FakePlayer, without the registry). */
    public static int left(EntityPlayer p) {
        SettlementRegistrySC.PlayerRec rec = rec(p, false);
        if (rec == null) {
            return p == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer ? 0 : DAY_CAP;
        }
        return allowed(usedToday(rec, SettlementSC.day(p.worldObj)), DAY_CAP, DAY_CAP);
    }

    /**
     * Server: `want` Crumbs asked for the player - @return how many may be given (0..want), counted as given today. The caller
     * gives exactly that many (or keeps the source sealed for the rest).
     */
    public static int take(EntityPlayer p, int want) {
        if (want <= 0) {
            return 0;
        }
        SettlementRegistrySC.PlayerRec rec = rec(p, true);
        if (rec == null) {
            return 0;
        }
        long day = SettlementSC.day(p.worldObj);
        int used = usedToday(rec, day);
        int n = allowed(used, want, DAY_CAP);
        if (n > 0) {
            NBTTagCompound c = new NBTTagCompound();
            c.setLong(DAY, day);
            c.setInteger(COUNT, used + n);
            rec.extra.setTag(EXTRA, c);
            SettlementRegistrySC r = SettlementSC.registry(p.worldObj);
            if (r != null) {
                r.markDirty();
            }
        }
        return n;
    }

    /** The Crumbs counted on `day` in the record (0 on another day). */
    static int usedToday(SettlementRegistrySC.PlayerRec rec, long day) {
        NBTTagCompound c = rec.extra.getCompoundTag(EXTRA);
        return c.hasKey(DAY) && c.getLong(DAY) == day ? Math.max(0, c.getInteger(COUNT)) : 0;
    }

    private static SettlementRegistrySC.PlayerRec rec(EntityPlayer p, boolean make) {
        if (p == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer) {
            return null;
        }
        SettlementRegistrySC r = SettlementSC.registry(p.worldObj);
        if (r == null) {
            return null;
        }
        return make ? r.player(p.getUniqueID()) : r.playerIfAny(p.getUniqueID());
    }
}
