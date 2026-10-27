package com.sc.embassy;

import java.util.List;

import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.SquadSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraftforge.common.util.FakePlayer;

/**
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §6, the user's choice 6 of 2026-10-26, rules ЛП-13..ЛП-15): the core's side
 * of the embassy's own air defence («ПВО посольства») - who is admitted, who flies under an admitted mate's cover, the 7700 pass ledger and
 * the chat lines. The FIRE is the Vehicles add-on's (its guns and missiles, from the emplacements' guns - AirfieldInfoSC.adPosts, its
 * radio): it asks these through EmbassyAirApiSC. Written whole by the coordinator. Server only (a client: false / 0 / nothing).
 * <ul>
 * <li><b>admitted</b> (ЛП-13): «Знакомый» (EmbassyAirRulesSC.ADMIT_LEVEL) or higher by the player's «Доверие», and not this embassy's
 * «Враг» - «Чужак», «Нарушитель», «Враг» are not; a player with no record is a «Чужак» (not admitted);</li>
 * <li><b>cover</b> (ЛП-14): a non-admitted player is no target while an admitted squad mate of his (SquadSC.mates: the core's ПД-2
 * «звено») is online in the same world within ESCORT_RANGE of him - the add-on checks the passengers and the crew itself (any admitted
 * one on board admits the machine);</li>
 * <li><b>the 7700 pass</b> (ЛП-15): an emergency squawk holds the fire for PASS_SECONDS once a game day a player an embassy (LocalRec.extra
 * EmbassyAirRulesSC.LEDGER "X" {d: the day of the last pass, u: the world time the hold ends}) - asked again while one runs: PASS_RUNNING,
 * after it today: PASS_USED;</li>
 * <li><b>the warning</b> (the review's): one a player an embassy a game day (LocalRec.extra LEDGER "X" {w: its day, e: the world time
 * its WARN_SECONDS end}) - back into the zone after it, the same day: no new seconds, the fire at once (requestWarn);</li>
 * <li><b>a player's base</b> (the review's): no fire over it (EmbassyAirfieldSC.playerBaseAt) - holdFire, EmbassyAirApiSC.adZoneAt.</li>
 * </ul>
 * The chat lines (EmbassyAirApiSC.AD_*) are the core's lang (sc.airfield.ad.<AD_KEYS>): the add-on shows the same text in its radio.
 */
public final class EmbassyAirDefenceSC {

    /** EmbassyAirApiSC.requestPass's answers (the same numbers; append only). */
    public static final int PASS_GRANTED = 0, PASS_RUNNING = 1, PASS_USED = 2, PASS_NO_FIELD = 3, PASS_ADMITTED = 4;
    /** The chat lines (EmbassyAirApiSC.AD_* - the same numbers; append only) and their lang keys sc.airfield.ad.<key>. */
    public static final int AD_WARN = 0, AD_FIRE = 1, AD_PASS = 2, AD_PASS_USED = 3, AD_PASS_OVER = 4, AD_LEFT = 5;
    public static final String[] AD_KEYS = {"warn", "fire", "pass", "passUsed", "passOver", "left"};

    private EmbassyAirDefenceSC() {
    }

    /** ЛП-13: `p` is admitted at embassy `s` (its level from «Знакомый» on, not its «Враг»). Server. */
    public static boolean admitted(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (p == null || s == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer) {
            return false;
        }
        SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
        SettlementRegistrySC.PlayerRec pr = r == null ? null : r.playerIfAny(p.getUniqueID());
        if (pr == null) {
            return false;                                       // never met the Tuners: a «Чужак»
        }
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        boolean enemy = l != null && l.enemy(p.worldObj.getTotalWorldTime());
        return EmbassyAirRulesSC.admitted(ReputationSC.localLevel(pr.reputation, enemy), enemy);
    }

    /**
     * ЛП-14: an admitted squad mate of `p` (online, alive, in the same world, within EmbassyAirRulesSC.ESCORT_RANGE) - `p` flies under his
     * cover; null: none (or `p` himself is admitted - ask admitted first). Server.
     */
    public static EntityPlayerMP escortOf(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (p == null || s == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer) {
            return null;
        }
        double r2 = (double) EmbassyAirRulesSC.ESCORT_RANGE * EmbassyAirRulesSC.ESCORT_RANGE;
        List<?> all = p.worldObj.playerEntities;
        for (Object o : all) {
            if (!(o instanceof EntityPlayerMP) || o == p || o instanceof FakePlayer) {
                continue;
            }
            EntityPlayerMP m = (EntityPlayerMP) o;
            if (m.isEntityAlive() && m.getDistanceSqToEntity(p) <= r2 && SquadSC.mates(p, m) && admitted(m, s)) {
                return m;
            }
        }
        return null;
    }

    /**
     * `p`'s machine is no target of embassy `s` now: admitted, under a mate's cover, a 7700 pass running, or (the review's) over a player's
     * base (EmbassyAirfieldSC.playerBaseAt at `p`'s column - the machine's). Server.
     */
    public static boolean holdFire(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        return admitted(p, s) || escortOf(p, s) != null || passUntil(p, s) > (p == null || p.worldObj == null ? 0L : p.worldObj.getTotalWorldTime())
                || p != null && EmbassyAirfieldSC.playerBaseAt(p.worldObj, p.posX, p.posZ);
    }

    /**
     * The review's: `p` is warned by embassy `s`'s air defence - the first warning of the game day there starts his WARN_SECONDS to turn
     * away (the ledger written, the registry dirty); asked again the same day (out of the zone and back, another machine) - that day's end,
     * already past once it ran out: no new seconds. @return the world time his turn-away ends (0: no field, a FakePlayer, a client, or he
     * is admitted - he needs none). Server.
     */
    public static long requestWarn(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (p == null || s == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer || EmbassyAirfieldSC.fieldOf(s) == null
                || admitted(p, s)) {
            return 0L;
        }
        long now = p.worldObj.getTotalWorldTime(), today = SettlementSC.day(p.worldObj);
        SettlementRegistrySC.LocalRec l = s.local(p.getUniqueID());
        NBTTagCompound x = l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
        long day = x.hasKey(EmbassyAirRulesSC.WARN_DAY) ? x.getLong(EmbassyAirRulesSC.WARN_DAY) : -1L;
        long end = EmbassyAirRulesSC.warnEnd(day, x.getLong(EmbassyAirRulesSC.WARN_END), today, now);
        if (day != today) {
            x.setLong(EmbassyAirRulesSC.WARN_DAY, today);
            x.setLong(EmbassyAirRulesSC.WARN_END, end);
            l.extra.setTag(EmbassyAirRulesSC.LEDGER, x);
            SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
            if (r != null) {
                r.markDirty();
            }
        }
        return end;
    }

    /** The world time `p`'s warning at `s` today ends (0: none given him there today). Server. */
    public static long warnUntil(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        SettlementRegistrySC.LocalRec l = p == null || s == null || p.worldObj == null ? null : s.localIfAny(p.getUniqueID());
        NBTTagCompound x = l == null ? null : l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
        return x == null || !x.hasKey(EmbassyAirRulesSC.WARN_DAY) || x.getLong(EmbassyAirRulesSC.WARN_DAY) != SettlementSC.day(p.worldObj) ? 0L
                : x.getLong(EmbassyAirRulesSC.WARN_END);
    }

    /**
     * ЛП-15: `p` squawks 7700 in embassy `s`'s air defence zone: a pass of PASS_SECONDS if none was given him there today (PASS_GRANTED -
     * the ledger written, the registry dirty), else PASS_RUNNING / PASS_USED; PASS_ADMITTED - he needs none; PASS_NO_FIELD - `s` has no
     * airfield. Server.
     */
    public static int requestPass(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        if (p == null || s == null || p.worldObj == null || p.worldObj.isRemote || p instanceof FakePlayer || EmbassyAirfieldSC.fieldOf(s) == null) {
            return PASS_NO_FIELD;
        }
        if (admitted(p, s)) {
            return PASS_ADMITTED;
        }
        long now = p.worldObj.getTotalWorldTime(), today = SettlementSC.day(p.worldObj);
        SettlementRegistrySC.LocalRec l = s.local(p.getUniqueID());
        NBTTagCompound x = l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER);
        if (x.getLong(EmbassyAirRulesSC.PASS_UNTIL) > now) {
            return PASS_RUNNING;
        }
        long last = x.hasKey(EmbassyAirRulesSC.PASS_DAY) ? x.getLong(EmbassyAirRulesSC.PASS_DAY) : -1L;
        if (!EmbassyAirRulesSC.passAvailable(last, today)) {
            return PASS_USED;
        }
        x.setLong(EmbassyAirRulesSC.PASS_DAY, today);
        x.setLong(EmbassyAirRulesSC.PASS_UNTIL, now + EmbassyAirRulesSC.PASS_SECONDS * 20L);
        l.extra.setTag(EmbassyAirRulesSC.LEDGER, x);
        SettlementRegistrySC r = SettlementRegistrySC.get(p.worldObj);
        if (r != null) {
            r.markDirty();
        }
        return PASS_GRANTED;
    }

    /** The world time `p`'s 7700 pass at `s` ends (0: none given; in the past: over). Server. */
    public static long passUntil(EntityPlayer p, SettlementRegistrySC.Settlement s) {
        SettlementRegistrySC.LocalRec l = p == null || s == null ? null : s.localIfAny(p.getUniqueID());
        return l == null ? 0L : l.extra.getCompoundTag(EmbassyAirRulesSC.LEDGER).getLong(EmbassyAirRulesSC.PASS_UNTIL);
    }

    /**
     * Chat line `line` (AD_*) of embassy `s` to `callsign` (the add-on's board name; null - the pilot's name): sc.airfield.ad.<key> with
     * {0: the embassy's name, 1: the callsign, 2: the seconds (WARN_SECONDS / PASS_SECONDS), 3: the level that admits («Знакомый»)}.
     * Red for the warning and the fire, yellow for the rest. Pure but for the names.
     */
    public static IChatComponent line(int line, SettlementRegistrySC.Settlement s, String callsign) {
        int i = line >= 0 && line < AD_KEYS.length ? line : AD_WARN;
        int secs = i == AD_PASS ? EmbassyAirRulesSC.PASS_SECONDS : EmbassyAirRulesSC.WARN_SECONDS;
        IChatComponent name = s == null ? new ChatComponentTranslation("sc.airfield.unknown") : SettlementSC.displayName(s);
        ChatComponentTranslation c = new ChatComponentTranslation("sc.airfield.ad." + AD_KEYS[i], name, callsign == null ? "" : callsign,
                String.valueOf(secs), new ChatComponentTranslation(ReputationSC.levelKey(EmbassyAirRulesSC.ADMIT_LEVEL)));
        c.setChatStyle(new ChatStyle().setColor(i == AD_WARN || i == AD_FIRE ? EnumChatFormatting.RED : EnumChatFormatting.YELLOW));
        return c;
    }

    /** Sends line(...) to `p` (never a FakePlayer); `callsign` null - his own name. Server. */
    public static void tell(EntityPlayer p, SettlementRegistrySC.Settlement s, int line, String callsign) {
        if (p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && ((EntityPlayerMP) p).playerNetServerHandler != null) {
            p.addChatComponentMessage(line(line, s, callsign == null ? p.getCommandSenderName() : callsign));
        }
    }
}
