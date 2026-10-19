package com.sc.embassy;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.settle.gen.SettlementLayoutSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE S (docs/plan-substrate-tech-f6.md §6.4, rule ПЕ-12; ПЛ-8's beam for the Overworld): the server side of the embassies' beams -
 * a turquoise column over each embassy's ring seen from BEAM_VIEW on the horizon. To every real player in the Overworld:
 * EmbassyNetSC.BEAMS {list: [{x, y, z}]} - the embassies within SEND_REACH of him (EmbassySiteSC.near: made ones - their ring - and the
 * unmade ones whose ground is not explored - the site's ring point; empty starts never), on his login / arrival in the Overworld, every
 * RESEND_TICKS while he moves (only when the list changed), and to everyone there when a new record is made (recordMade - package G's
 * MapGenEmbassySC calls it). Never to a FakePlayer. Registered by SCMod on FML's bus.
 * Fixed (the coordinator's): the class, INSTANCE, the numbers' names, recordMade. Bodies S's:
 * <ul>
 * <li>beams(w, x, z) - the list (pure of any player, the self-test's): with the embassies working there (SettlementSC.governs) the
 * records of the Overworld's embassies within SEND_REACH (their point "ring"); while new ones are made (embassiesGenerate) the
 * sites of EmbassySiteSC.near with no record at their core, no start (a start without a record is an empty one - old land, ПЕ-4),
 * their ground not explored (EmbassySiteSC.explored - remembered once found: explored land stays explored; «not explored» is
 * believed for RECHECK_TICKS - only a start, looked for first, can end it) and no player's field zone over it (zoneNear), at their
 * ring's point (ringOf - EmbassyLayoutSC.plan's geometry, no plan made); sorted by position - the same set is the same list;</li>
 * <li>each player's last list (by UUID; two players by LAN each their own) - a list is sent only when it differs, or forced on the
 * login, the arrival in the Overworld, the respawn there; leaving the Overworld forgets it (the client drops its beams itself);</li>
 * <li>recordMade comes in the middle of a chunk's generation (package G's start): it only marks every player of the world - his
 * next tick looks again (no biome reads, no explored checks inside the generation).</li>
 * </ul>
 */
public final class EmbassyBeamSC {

    public static final EmbassyBeamSC INSTANCE = new EmbassyBeamSC();
    /** The beams a client is told of (blocks), how often a moving player's list is looked at again (ticks); the client draws within BEAM_VIEW. */
    public static final int SEND_REACH = 2048, RESEND_TICKS = 600, BEAM_VIEW = 1024;

    /** What each player was last told (the list's key), by UUID. */
    private static final Map<UUID, String> SENT = new HashMap<UUID, String>();
    /** Players to look at again on their next tick (a new record). */
    private static final Set<UUID> REFRESH = new HashSet<UUID>();
    /** Sites (their core's column) found explored - an unmade one there only ever gets an empty start - for this server's run. */
    private static final Set<Long> EXPLORED = new HashSet<Long>();
    /** Sites found not explored -> the world time that verdict is believed till (a chunk of it made makes its start first). */
    private static final Map<Long, Long> UNEXPLORED = new HashMap<Long, Long>();
    /** How long a «not explored» stands (ticks): ten of a moving player's looks. */
    private static final int RECHECK_TICKS = RESEND_TICKS * 10;

    private EmbassyBeamSC() {
    }

    @SubscribeEvent
    public void onLoggedIn(PlayerEvent.PlayerLoggedInEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            update((EntityPlayerMP) e.player, true);
        }
    }

    /** Came into the Overworld - his list now; went out of it - forgotten (his client drops the beams). */
    @SubscribeEvent
    public void onChangedDimension(PlayerEvent.PlayerChangedDimensionEvent e) {
        if (!(e.player instanceof EntityPlayerMP)) {
            return;
        }
        if (e.player.dimension == EmbassySiteSC.DIM) {
            update((EntityPlayerMP) e.player, true);
        } else {
            forget(e.player);
        }
    }

    /** A respawn may bring him into the Overworld from another world (no dimension change event then). */
    @SubscribeEvent
    public void onRespawn(PlayerEvent.PlayerRespawnEvent e) {
        if (e.player instanceof EntityPlayerMP) {
            if (e.player.dimension == EmbassySiteSC.DIM) {
                update((EntityPlayerMP) e.player, true);
            } else {
                forget(e.player);
            }
        }
    }

    @SubscribeEvent
    public void onLoggedOut(PlayerEvent.PlayerLoggedOutEvent e) {
        forget(e.player);
    }

    @SubscribeEvent
    public void onPlayerTick(TickEvent.PlayerTickEvent e) {
        if (e.phase != TickEvent.Phase.END || e.side != Side.SERVER || !(e.player instanceof EntityPlayerMP) || e.player instanceof FakePlayer
                || e.player.dimension != EmbassySiteSC.DIM) {
            return;
        }
        EntityPlayerMP p = (EntityPlayerMP) e.player;
        boolean asked = !REFRESH.isEmpty() && REFRESH.remove(p.getUniqueID());
        if (asked || (p.worldObj.getTotalWorldTime() + (p.getEntityId() & 0x3FF)) % RESEND_TICKS == 0L) {
            update(p, false);
        }
    }

    /** Package G: a new embassy record in world `w` - every player there hears of its beam (on his next tick). */
    public static void recordMade(World w, SettlementRegistrySC.Settlement s) {
        if (w == null || w.isRemote) {
            return;
        }
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayerMP && !(o instanceof FakePlayer)) {
                REFRESH.add(((EntityPlayer) o).getUniqueID());
            }
        }
    }

    /** Server stopped. */
    public static void clearAll() {
        SENT.clear();
        REFRESH.clear();
        EXPLORED.clear();
        UNEXPLORED.clear();
    }

    // ------------------------------------------------------------------ the lists

    /** `p`'s list now - sent when it differs from the last one he was told of (or `force`). */
    static void update(EntityPlayerMP p, boolean force) {
        if (p instanceof FakePlayer || p.playerNetServerHandler == null || !(p.worldObj instanceof WorldServer)
                || p.dimension != EmbassySiteSC.DIM) {
            return;
        }
        List<int[]> list = beams((WorldServer) p.worldObj, p.posX, p.posZ);
        String key = key(list);
        UUID id = p.getUniqueID();
        if (!force && key.equals(SENT.get(id))) {
            return;
        }
        SENT.put(id, key);
        NBTTagList l = new NBTTagList();
        for (int[] b : list) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("x", b[0]);
            t.setInteger("y", b[1]);
            t.setInteger("z", b[2]);
            l.appendTag(t);
        }
        NBTTagCompound data = new NBTTagCompound();
        data.setTag("list", l);
        EmbassyNetSC.send(p, EmbassyNetSC.BEAMS, data);
    }

    private static void forget(EntityPlayer p) {
        if (p != null) {
            SENT.remove(p.getUniqueID());
            REFRESH.remove(p.getUniqueID());
        }
    }

    /**
     * ПЕ-12: the beams a player at (x, z) of the Overworld `w` is told of - {x, y, z} of each ring (see the class comment), sorted.
     * Server thread (EmbassySiteSC.near reads the biomes of the regions round once - cached).
     */
    public static List<int[]> beams(WorldServer w, double x, double z) {
        List<int[]> out = new ArrayList<int[]>();
        if (w == null || w.provider == null || w.provider.dimensionId != EmbassySiteSC.DIM || !SettlementSC.governs(w)) {
            return out;
        }
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        double r2 = (double) SEND_REACH * SEND_REACH;
        if (reg != null) {
            for (SettlementRegistrySC.Settlement s : reg.embassies()) {
                int[] ring = s.point(EmbassyLayoutSC.P_RING);
                if (s.dim == EmbassySiteSC.DIM && ring != null && s.distSq(x, z) <= r2) {
                    out.add(ring.clone());
                }
            }
        }
        if (SettlementSC.embassiesGenerate()) {
            MapGenEmbassySC gen = EmbassySC.mapGen(w);
            for (EmbassySiteSC.Site site : EmbassySiteSC.near(w, (int) Math.floor(x), (int) Math.floor(z), SEND_REACH)) {
                if (reg != null && reg.atCentre(EmbassySiteSC.DIM, site.x, site.z) != null) {
                    continue;                                   // made: its record's ring is in the list
                }
                if (gen != null && gen.startAt(w, site.chunkX(), site.chunkZ()) != null) {
                    continue;                                   // a start without a record: an empty one (old land)
                }
                Long k = Long.valueOf(((long) site.x << 32) ^ ((long) site.z & 0xFFFFFFFFL));
                if (EXPLORED.contains(k)) {
                    continue;
                }
                Long until = UNEXPLORED.get(k);
                long now = w.getTotalWorldTime();
                if (until == null || now >= until.longValue() || now < until.longValue() - RECHECK_TICKS) {
                    if (EmbassySiteSC.explored(w, site)) {
                        UNEXPLORED.remove(k);
                        EXPLORED.add(k);                        // it will only ever get an empty start
                        continue;
                    }
                    UNEXPLORED.put(k, Long.valueOf(now + RECHECK_TICKS));
                }
                if (EmbassySiteSC.zoneNear(w, site)) {
                    continue;                                   // a player's field over its land: its start would be empty now
                }
                out.add(ringOf(site));
            }
        }
        Collections.sort(out, new Comparator<int[]>() {
            @Override
            public int compare(int[] a, int[] b) {
                return a[0] != b[0] ? (a[0] < b[0] ? -1 : 1) : a[2] != b[2] ? (a[2] < b[2] ? -1 : 1) : (a[1] < b[1] ? -1 : a[1] > b[1] ? 1 : 0);
            }
        });
        return out;
    }

    /** An unmade site's ring point - EmbassyLayoutSC.plan's (the ring's middle: RING_BACK past the beacon on the main street, RING_DY over the floor). */
    public static int[] ringOf(EmbassySiteSC.Site site) {
        int g = EmbassyLayoutSC.gateDir(site.seed), a = site.radius + EmbassyLayoutSC.VOKZAL_OUT + EmbassyLayoutSC.RING_BACK;
        return new int[]{site.x + a * SettlementLayoutSC.DIR_X[g], site.floorY + EmbassyLayoutSC.RING_DY, site.z + a * SettlementLayoutSC.DIR_Z[g]};
    }

    /** A list's key: the same beams, the same key. */
    static String key(List<int[]> list) {
        StringBuilder b = new StringBuilder();
        for (int[] r : list) {
            b.append(r[0]).append(',').append(r[1]).append(',').append(r[2]).append(';');
        }
        return b.toString();
    }
}
