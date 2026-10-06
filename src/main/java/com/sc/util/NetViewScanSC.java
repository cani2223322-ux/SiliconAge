package com.sc.util;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.WeakHashMap;

import io.netty.buffer.ByteBuf;

/**
 * Network overview (wrench, sneak + right-click on a cable): the world-free part, so the self-test
 * can check it - the bounded walk over a cable graph, the flow estimate for the mod's own net
 * (EnergyNetSC's three passes, without IC2), load levels, the click limiter and the snapshot sent
 * to the client. The world side is in handler/NetViewNetSC, the drawing in client/NetViewRendererSC.
 */
public final class NetViewScanSC {

    public static final int MAX_CABLES = 4096, MAX_ENDPOINTS = 512;
    /** The overview closes after this long, or once the player is this far from the clicked cable. */
    public static final int LIFE_TICKS = 600, HIDE_RANGE = 64;
    /** Blocks drawn around the player, endpoint labels only this close. */
    public static final int CULL_RANGE = 96, LABEL_RANGE = 16;
    /** Ticks between two scans for one player; clicks closer than CLICK_QUIET are one held button. */
    public static final int SCAN_INTERVAL = 20, CLICK_QUIET = 8;

    // endpoint kinds (sent as bytes - append only)
    public static final int K_SOURCE = 0, K_CONSUMER = 1, K_STORAGE = 2, K_TRANSFORMER = 3, K_FOREIGN = 4;
    // an endpoint's role on this network
    public static final int R_OUT = 1, R_IN = 2;
    // cable flags
    public static final int C_BOTTLENECK = 1, C_OVERVOLT = 2;
    // snapshot flags (sent - append only)
    public static final int S_TRUNCATED = 1, S_MEASURED = 2, S_HIDDEN = 4, S_PING = 8;
    // load levels
    public static final int L_LOW = 0, L_MID = 1, L_HIGH = 2, L_OVER = 3;
    // what a click does (Clicks.click)
    public static final int CLICK_IGNORE = 0, CLICK_PING = 1, CLICK_SCAN = 2;

    private NetViewScanSC() {
    }

    // ------------------------------------------------------------------ positions

    /** x, z: 26 bits each, y: 12 bits. */
    public static long pack(int x, int y, int z) {
        return ((long) x & 0x3FFFFFFL) << 38 | ((long) z & 0x3FFFFFFL) << 12 | (y & 0xFFFL);
    }

    public static int unpackX(long p) {
        return (int) (p >> 38);
    }

    public static int unpackY(long p) {
        return (int) (p & 0xFFFL);
    }

    public static int unpackZ(long p) {
        return (int) (p << 26 >> 38);
    }

    // ------------------------------------------------------------------ the walk

    /** A cable graph: cables and endpoints by packed position. */
    public interface Graph {
        /** Cables of the same network next to `cable`. */
        void links(long cable, List<Long> out);

        /** Energy blocks `cable` is plugged into. */
        void endpoints(long cable, List<Long> out);
    }

    public static final class Scan {
        public final List<Long> cables = new ArrayList<Long>();
        public final List<int[]> links = new ArrayList<int[]>();
        public final List<Long> endpoints = new ArrayList<Long>();
        /** Per endpoint: the cables (indices) it touches. */
        public final List<int[]> endpointCables = new ArrayList<int[]>();
        public boolean truncated;
    }

    /** Breadth-first from `start`, at most maxCables cables / maxEndpoints endpoints (truncated if more). */
    public static Scan scan(Graph g, long start, int maxCables, int maxEndpoints) {
        Scan s = new Scan();
        Map<Long, Integer> index = new HashMap<Long, Integer>();
        Map<Long, List<Integer>> touch = new LinkedHashMap<Long, List<Integer>>();
        List<List<Integer>> links = new ArrayList<List<Integer>>();
        ArrayDeque<Long> queue = new ArrayDeque<Long>();
        index.put(start, 0);
        s.cables.add(start);
        links.add(new ArrayList<Integer>());
        queue.add(start);
        List<Long> buf = new ArrayList<Long>();
        while (!queue.isEmpty()) {
            long c = queue.poll();
            int ci = index.get(c);
            buf.clear();
            g.links(c, buf);
            for (Long n : buf) {
                Integer ni = index.get(n);
                if (ni == null) {
                    if (s.cables.size() >= maxCables) {
                        s.truncated = true;
                        continue;
                    }
                    ni = s.cables.size();
                    index.put(n, ni);
                    s.cables.add(n);
                    links.add(new ArrayList<Integer>());
                    queue.add(n);
                }
                if (!links.get(ci).contains(ni)) {
                    links.get(ci).add(ni);
                }
            }
            buf.clear();
            g.endpoints(c, buf);
            for (Long e : buf) {
                List<Integer> at = touch.get(e);
                if (at == null) {
                    if (touch.size() >= maxEndpoints) {
                        s.truncated = true;
                        continue;
                    }
                    at = new ArrayList<Integer>(2);
                    touch.put(e, at);
                }
                if (!at.contains(ci)) {
                    at.add(ci);
                }
            }
        }
        for (List<Integer> l : links) {
            s.links.add(toArray(l));
        }
        for (Map.Entry<Long, List<Integer>> e : touch.entrySet()) {
            s.endpoints.add(e.getKey());
            s.endpointCables.add(toArray(e.getValue()));
        }
        return s;
    }

    private static int[] toArray(List<Integer> l) {
        int[] a = new int[l.size()];
        for (int i = 0; i < a.length; i++) {
            a[i] = l.get(i);
        }
        return a;
    }

    // ------------------------------------------------------------------ flow estimate (the mod's own net)

    public static final class Flow {
        /** Per endpoint: EU/t it gave (R_OUT) plus EU/t it took (R_IN) - one of them for a source / consumer. */
        public int[] given, taken;
        public int[] cableFlow;
        public long loss;
    }

    /**
     * EnergyNetSC's tick on numbers: suppliers feed consumers, surplus charges buffers (both roles),
     * shortfall is drawn from them; all within `capacity`, loss = lossPerBlock x cables from the
     * nearest supplier (buffer). Each delivery (with its loss) is laid on the shortest path back.
     */
    public static Flow estimate(Scan s, int[] role, int[] offer, int[] demand, int lossPerBlock, int capacity) {
        int m = s.endpoints.size();
        List<Integer> sup = new ArrayList<Integer>(), con = new ArrayList<Integer>(), buf = new ArrayList<Integer>();
        for (int i = 0; i < m; i++) {
            boolean out = (role[i] & R_OUT) != 0, in = (role[i] & R_IN) != 0;
            (out && in ? buf : out ? sup : in ? con : new ArrayList<Integer>()).add(i);
        }
        Flow f = new Flow();
        f.given = new int[m];
        f.taken = new int[m];
        f.cableFlow = new int[s.cables.size()];
        int[] parS = new int[s.cables.size()], parB = new int[s.cables.size()];
        int[] distS = distances(s, sup, parS), distB = distances(s, buf, parB);
        int[] left = offer.clone(), want = demand.clone();
        int[] used = {0};
        pass(s, f, sup, con, left, want, distS, parS, lossPerBlock, capacity, used);
        pass(s, f, sup, buf, left, want, distS, parS, lossPerBlock, capacity, used);
        pass(s, f, buf, con, left, want, distB, parB, lossPerBlock, capacity, used);
        return f;
    }

    /** Cable distances from the seed endpoints' cables (1 = at the seed), with the parent towards it (-1 at a seed). */
    private static int[] distances(Scan s, List<Integer> seeds, int[] parent) {
        int[] dist = new int[s.cables.size()];
        Arrays.fill(dist, Integer.MAX_VALUE);
        Arrays.fill(parent, -1);
        ArrayDeque<Integer> q = new ArrayDeque<Integer>();
        for (int e : seeds) {
            for (int c : s.endpointCables.get(e)) {
                if (dist[c] != 1) {
                    dist[c] = 1;
                    q.add(c);
                }
            }
        }
        while (!q.isEmpty()) {
            int c = q.poll();
            for (int n : s.links.get(c)) {
                if (dist[n] > dist[c] + 1) {
                    dist[n] = dist[c] + 1;
                    parent[n] = c;
                    q.add(n);
                }
            }
        }
        return dist;
    }

    private static void pass(Scan s, Flow f, List<Integer> from, List<Integer> to, int[] left, int[] want,
                             int[] dist, int[] parent, int lossPerBlock, int capacity, int[] used) {
        if (from.isEmpty() || to.isEmpty() || used[0] >= capacity) {
            return;
        }
        int[] offer = new int[from.size()];
        long supply = 0;
        for (int i = 0; i < offer.length; i++) {
            offer[i] = Math.max(0, left[from.get(i)]);
            supply += offer[i];
        }
        if (supply <= 0) {
            return;
        }
        int budget = (int) Math.min(supply, capacity - used[0]);
        int[] dem = new int[to.size()], loss = new int[to.size()], near = new int[to.size()];
        for (int i = 0; i < dem.length; i++) {
            int e = to.get(i), best = Integer.MAX_VALUE;
            near[i] = -1;
            for (int c : s.endpointCables.get(e)) {
                if (dist[c] < best) {
                    best = dist[c];
                    near[i] = c;
                }
            }
            dem[i] = near[i] < 0 || best == Integer.MAX_VALUE ? 0 : Math.max(0, want[e]);
            loss[i] = lossPerBlock * (best == Integer.MAX_VALUE ? 0 : best);
        }
        int[] give = com.sc.energy.EnergySplitSC.split(budget, dem, loss);
        int spent = 0;
        for (int i = 0; i < give.length; i++) {
            if (give[i] <= 0) {
                continue;
            }
            int e = to.get(i), amount = give[i] + loss[i];
            spent += amount;
            f.taken[e] += give[i];
            want[e] -= give[i];
            f.loss += loss[i];
            for (int c = near[i]; c >= 0; c = parent[c]) {
                f.cableFlow[c] += amount;
            }
        }
        int[] drawn = com.sc.energy.EnergySplitSC.draw(spent, offer);
        for (int i = 0; i < drawn.length; i++) {
            f.given[from.get(i)] += drawn[i];
            left[from.get(i)] -= drawn[i];
        }
        used[0] += spent;
    }

    // ------------------------------------------------------------------ load

    /** Green below half the rating, yellow below 90%, red at 90% or a bottleneck, pulsing red past it or overvolted. */
    public static int level(int flow, int capacity, int cableFlags) {
        if ((cableFlags & C_OVERVOLT) != 0 || capacity > 0 && flow > capacity) {
            return L_OVER;
        }
        if ((cableFlags & C_BOTTLENECK) != 0) {
            return L_HIGH;
        }
        if (capacity <= 0) {
            return L_LOW;
        }
        long pm = (long) flow * 1000L / capacity;
        return pm >= 900 ? L_HIGH : pm >= 500 ? L_MID : L_LOW;
    }

    // ------------------------------------------------------------------ clicks

    /** Per player: last click and last scan (world ticks). A held button clicks every 4 ticks - one press only. */
    public static final class Clicks {
        private final Map<Object, long[]> at = new WeakHashMap<Object, long[]>();

        public int click(Object who, long now) {
            long[] a = at.get(who);
            if (a == null) {
                a = new long[]{Long.MIN_VALUE / 2, Long.MIN_VALUE / 2};
                at.put(who, a);
            }
            long lastClick = a[0];
            a[0] = now;                                     // sliding: a held button never clicks again
            if (now >= lastClick && now - lastClick < CLICK_QUIET) {
                return CLICK_IGNORE;
            }
            if (now >= a[1] && now - a[1] < SCAN_INTERVAL) {
                return CLICK_PING;                          // too soon for a scan: only hides what's shown
            }
            a[1] = now;
            return CLICK_SCAN;
        }
    }

    // ------------------------------------------------------------------ snapshot

    public static final class Snapshot {
        public int dim, ox, oy, oz, flags;
        public int n;
        public int[] cx, cy, cz, cflow;
        public byte[] ctype, cflags;
        public int m;
        public int[] ex, ey, ez, erate, epct;
        /** Kind, tier ordinal (-1 unknown), role bits. */
        public byte[] ekind, etier, erole;
        public long gen, cons, loss, stored, capacity;
        public int maxSourceTier = -1, bottlenecks, hidden;

        public void cables(int count) {
            n = count;
            cx = new int[count];
            cy = new int[count];
            cz = new int[count];
            cflow = new int[count];
            ctype = new byte[count];
            cflags = new byte[count];
        }

        public void endpoints(int count) {
            m = count;
            ex = new int[count];
            ey = new int[count];
            ez = new int[count];
            erate = new int[count];
            epct = new int[count];
            ekind = new byte[count];
            etier = new byte[count];
            erole = new byte[count];
        }

        public boolean hasCable(int x, int y, int z) {
            for (int i = 0; i < n; i++) {
                if (cx[i] == x && cy[i] == y && cz[i] == z) {
                    return true;
                }
            }
            return false;
        }
    }

    /** Positions relative to the clicked cable (shorts: a walk of 4096 cables never gets further). */
    public static void encode(ByteBuf b, Snapshot s) {
        b.writeInt(s.dim);
        b.writeInt(s.ox);
        b.writeInt(s.oy);
        b.writeInt(s.oz);
        b.writeShort(s.flags);
        if ((s.flags & S_PING) != 0) {
            return;
        }
        b.writeShort(s.n);
        for (int i = 0; i < s.n; i++) {
            b.writeShort(s.cx[i] - s.ox);
            b.writeShort(s.cy[i] - s.oy);
            b.writeShort(s.cz[i] - s.oz);
            b.writeByte(s.ctype[i]);
            b.writeByte(s.cflags[i]);
            b.writeInt(s.cflow[i]);
        }
        b.writeShort(s.m);
        for (int i = 0; i < s.m; i++) {
            b.writeShort(s.ex[i] - s.ox);
            b.writeShort(s.ey[i] - s.oy);
            b.writeShort(s.ez[i] - s.oz);
            b.writeByte(s.ekind[i]);
            b.writeByte(s.etier[i]);
            b.writeByte(s.erole[i]);
            b.writeInt(s.erate[i]);
            b.writeByte(s.epct[i]);
        }
        b.writeLong(s.gen);
        b.writeLong(s.cons);
        b.writeLong(s.loss);
        b.writeLong(s.stored);
        b.writeLong(s.capacity);
        b.writeByte(s.maxSourceTier);
        b.writeShort(s.bottlenecks);
        b.writeShort(s.hidden);
    }

    public static Snapshot decode(ByteBuf b) {
        Snapshot s = new Snapshot();
        s.dim = b.readInt();
        s.ox = b.readInt();
        s.oy = b.readInt();
        s.oz = b.readInt();
        s.flags = b.readUnsignedShort();
        if ((s.flags & S_PING) != 0) {
            s.cables(0);
            s.endpoints(0);
            return s;
        }
        s.cables(Math.min(MAX_CABLES, b.readUnsignedShort()));
        for (int i = 0; i < s.n; i++) {
            s.cx[i] = s.ox + b.readShort();
            s.cy[i] = s.oy + b.readShort();
            s.cz[i] = s.oz + b.readShort();
            s.ctype[i] = b.readByte();
            s.cflags[i] = b.readByte();
            s.cflow[i] = b.readInt();
        }
        s.endpoints(Math.min(MAX_ENDPOINTS, b.readUnsignedShort()));
        for (int i = 0; i < s.m; i++) {
            s.ex[i] = s.ox + b.readShort();
            s.ey[i] = s.oy + b.readShort();
            s.ez[i] = s.oz + b.readShort();
            s.ekind[i] = b.readByte();
            s.etier[i] = b.readByte();
            s.erole[i] = b.readByte();
            s.erate[i] = b.readInt();
            s.epct[i] = b.readByte();
        }
        s.gen = b.readLong();
        s.cons = b.readLong();
        s.loss = b.readLong();
        s.stored = b.readLong();
        s.capacity = b.readLong();
        s.maxSourceTier = b.readByte();
        s.bottlenecks = b.readUnsignedShort();
        s.hidden = b.readUnsignedShort();
        return s;
    }

    /** 1234 -> "1234", 45678 -> "45.7k", 3200000 -> "3.2M" (HUD and labels). */
    public static String compact(long v) {
        long a = Math.abs(v);
        if (a < 10000) {
            return Long.toString(v);
        }
        if (a < 1000000L) {
            return String.format(java.util.Locale.ROOT, "%.1fk", v / 1000.0);
        }
        return String.format(java.util.Locale.ROOT, "%.1fM", v / 1000000.0);
    }
}
