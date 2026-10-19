package com.sc.embassy;

import com.sc.settle.SettlementRegistrySC;

import net.minecraft.nbt.NBTTagCompound;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §2.5, rules ПЯ-6..ПЯ-13): a player's flights as the server keeps them - in his record
 * of the global registry (PlayerRec.extra EXTRA "R", by UUID: the LAN friend has his own; it outlives a death and DIM-1414 deleted).
 * Pure accessors of the compound, written whole by the coordinator; package R (FlightServerSC, FlightSC) writes through them, package
 * D tests them, RosterSC.visitors (the coordinator's) notes the embassies seen. Mark the registry dirty after a change.
 * <ul>
 * <li>INTRO - the introductory flight taken (once a player);</li>
 * <li>FROM - the embassy (settlement id) of the last flight out: HOME goes there (-1 none);</li>
 * <li>SEEN - the embassies the player has been to (their belt): OTHER goes to one of them;</li>
 * <li>BACK {e: embassy id, u: world time it runs out} - the return half of a round-trip ticket;</li>
 * <li>TALON {r: ride, t: target, s: settlement sold at, c: chips paid, d: game day, g: payer's UUID if a guarantor paid} - a ride
 * paid and not flown yet (one a player): the next window at that settlement carries him; one older than today goes back to its payer
 * at the next look (FlightSC - «неиспользованный возвращается на рассвете»);</li>
 * <li>RESCUED - the death (PlayerRec.deathAt) a rescue flight was taken for (one a death);</li>
 * <li>GUARANTEE {f: the friend's UUID, s: the embassy id, a: world time} - the last guarantee a guarantor gave (its penalty's watch);
 * GUARANTEE_PAID - that guarantee's penalty taken (once).</li>
 * </ul>
 */
public final class EmbassyTicketSC {

    /** Package R's key of PlayerRec.extra. */
    public static final String EXTRA = "R";
    public static final String INTRO = "intro", FROM = "from", SEEN = "seen", BACK = "back", TALON = "talon", RESCUED = "rescued",
            GUARANTEE = "guar", GUARANTEE_PAID = "guarPaid";

    private EmbassyTicketSC() {
    }

    /** A paid ride not flown yet. */
    public static final class Talon {
        public int ride, target, settlement, chips;
        public long day;
        /** The guarantor's UUID (he paid, he gets it back), "" - the passenger paid himself. */
        public String payer = "";

        public Talon() {
        }

        public Talon(int ride, int target, int settlement, int chips, long day, String payer) {
            this.ride = ride;
            this.target = target;
            this.settlement = settlement;
            this.chips = chips;
            this.day = day;
            this.payer = payer == null ? "" : payer;
        }

        NBTTagCompound write() {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("r", ride);
            t.setInteger("t", target);
            t.setInteger("s", settlement);
            t.setInteger("c", chips);
            t.setLong("d", day);
            if (payer.length() > 0) {
                t.setString("g", payer);
            }
            return t;
        }

        static Talon read(NBTTagCompound t) {
            Talon k = new Talon();
            k.ride = t.getInteger("r");
            k.target = t.getInteger("t");
            k.settlement = t.getInteger("s");
            k.chips = t.getInteger("c");
            k.day = t.getLong("d");
            k.payer = t.getString("g");
            return k;
        }

        /** Paid on an earlier game day than `today`: it goes back to its payer. */
        public boolean stale(long today) {
            return day < today;
        }
    }

    /** The player's flight compound (made on first ask - mark the registry dirty after writing). */
    public static NBTTagCompound of(SettlementRegistrySC.PlayerRec p) {
        NBTTagCompound c = p.extra.getCompoundTag(EXTRA);
        if (!p.extra.hasKey(EXTRA, 10)) {
            p.extra.setTag(EXTRA, c);
        }
        return c;
    }

    // ------------------------------------------------------------------ the introductory flight, where from, where seen

    public static boolean introUsed(SettlementRegistrySC.PlayerRec p) {
        return p != null && p.extra.getCompoundTag(EXTRA).getBoolean(INTRO);
    }

    public static void useIntro(SettlementRegistrySC.PlayerRec p) {
        of(p).setBoolean(INTRO, true);
    }

    /** The embassy of the last flight out (-1 none). */
    public static int from(SettlementRegistrySC.PlayerRec p) {
        NBTTagCompound c = p == null ? null : p.extra.getCompoundTag(EXTRA);
        return c == null || !c.hasKey(FROM) ? -1 : c.getInteger(FROM);
    }

    public static void setFrom(SettlementRegistrySC.PlayerRec p, int embassyId) {
        of(p).setInteger(FROM, embassyId);
    }

    /** The embassies the player has been to. */
    public static int[] seen(SettlementRegistrySC.PlayerRec p) {
        return p == null ? new int[0] : p.extra.getCompoundTag(EXTRA).getIntArray(SEEN);
    }

    public static boolean hasSeen(SettlementRegistrySC.PlayerRec p, int embassyId) {
        for (int id : seen(p)) {
            if (id == embassyId) {
                return true;
            }
        }
        return false;
    }

    /** Notes embassy `embassyId` seen. @return whether it is new */
    public static boolean see(SettlementRegistrySC.PlayerRec p, int embassyId) {
        if (p == null || hasSeen(p, embassyId)) {
            return false;
        }
        int[] a = seen(p), b = new int[a.length + 1];
        System.arraycopy(a, 0, b, 0, a.length);
        b[a.length] = embassyId;
        of(p).setIntArray(SEEN, b);
        return true;
    }

    // ------------------------------------------------------------------ the return half (ПЯ-12)

    /** The return half's embassy (-1 none). */
    public static int backEmbassy(SettlementRegistrySC.PlayerRec p) {
        NBTTagCompound b = p == null ? null : p.extra.getCompoundTag(EXTRA).getCompoundTag(BACK);
        return b == null || !b.hasKey("e") ? -1 : b.getInteger("e");
    }

    /** The world time the return half runs out (0 none). */
    public static long backUntil(SettlementRegistrySC.PlayerRec p) {
        return p == null ? 0L : p.extra.getCompoundTag(EXTRA).getCompoundTag(BACK).getLong("u");
    }

    public static void setBack(SettlementRegistrySC.PlayerRec p, int embassyId, long until) {
        NBTTagCompound b = new NBTTagCompound();
        b.setInteger("e", embassyId);
        b.setLong("u", until);
        of(p).setTag(BACK, b);
    }

    public static void clearBack(SettlementRegistrySC.PlayerRec p) {
        of(p).removeTag(BACK);
    }

    // ------------------------------------------------------------------ the talon (ПЯ-6)

    /** The paid ride waiting (null none). */
    public static Talon talon(SettlementRegistrySC.PlayerRec p) {
        NBTTagCompound c = p == null ? null : p.extra.getCompoundTag(EXTRA);
        return c == null || !c.hasKey(TALON, 10) ? null : Talon.read(c.getCompoundTag(TALON));
    }

    public static void setTalon(SettlementRegistrySC.PlayerRec p, Talon t) {
        if (t == null) {
            of(p).removeTag(TALON);
        } else {
            of(p).setTag(TALON, t.write());
        }
    }

    // ------------------------------------------------------------------ the rescue (ПЯ-7)

    /** The death time a rescue was taken for (-1 none). */
    public static long rescuedAt(SettlementRegistrySC.PlayerRec p) {
        NBTTagCompound c = p == null ? null : p.extra.getCompoundTag(EXTRA);
        return c == null || !c.hasKey(RESCUED) ? -1L : c.getLong(RESCUED);
    }

    public static void setRescued(SettlementRegistrySC.PlayerRec p, long deathAt) {
        of(p).setLong(RESCUED, deathAt);
    }

    // ------------------------------------------------------------------ the guarantee (ПЯ-13)

    /** The guarantor's last guarantee: {friend UUID, embassy id, world time}, or null. */
    public static NBTTagCompound guarantee(SettlementRegistrySC.PlayerRec p) {
        NBTTagCompound c = p == null ? null : p.extra.getCompoundTag(EXTRA);
        return c == null || !c.hasKey(GUARANTEE, 10) ? null : c.getCompoundTag(GUARANTEE);
    }

    public static void setGuarantee(SettlementRegistrySC.PlayerRec p, String friend, int embassyId, long at) {
        NBTTagCompound g = new NBTTagCompound();
        g.setString("f", friend == null ? "" : friend);
        g.setInteger("s", embassyId);
        g.setLong("a", at);
        NBTTagCompound c = of(p);
        c.setTag(GUARANTEE, g);
        c.removeTag(GUARANTEE_PAID);
    }

    public static boolean guaranteePaid(SettlementRegistrySC.PlayerRec p) {
        return p != null && p.extra.getCompoundTag(EXTRA).getBoolean(GUARANTEE_PAID);
    }

    public static void payGuarantee(SettlementRegistrySC.PlayerRec p) {
        of(p).setBoolean(GUARANTEE_PAID, true);
    }
}
