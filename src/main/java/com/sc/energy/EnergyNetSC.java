package com.sc.energy;

import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.WeakHashMap;

import com.sc.tileentity.TileEntityConduitBundleSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;
import net.minecraftforge.event.world.WorldEvent;

/**
 * Fallback energy network used when IC2 is NOT loaded (design doc §9.4: "иначе — собственный
 * EnergyNet с идентичным поведением"). Under IC2 the cables are IC2 conductors and IC2 routes;
 * this class then stays inert.
 *
 * Built the Ender IO way: cables of one type joined through their open sides form a network
 * (different cable types never join, so every network has one voltage tier and one rating).
 * Everything touching the network is sorted into
 * - suppliers: a tile whose output face touches it (generators, a storage's front...),
 * - consumers: a tile that takes energy through a face touching it (machines),
 * - buffers: a tile touching it with both kinds of face (a storage fed and drained by the same
 *   network) - it soaks up what the consumers leave and covers what the suppliers can't.
 * Each tick: the suppliers feed the consumers, any surplus charges the buffers, any shortfall is
 * drawn from the buffers. Consumers share evenly (EnergySplitSC); the whole network moves at
 * most the cable's rating (volts x amps, §9.1) per tick, line loss included; line loss is
 * lossPerBlock for every cable between the nearest supplier and the consumer. A supplier above
 * the cable's tier blows the cable next to it up (§9.3), a network above a consumer's input tier
 * blows the consumer up (receiveEnergy).
 *
 * Networks are cached and rebuilt only when something changes (a cable, a side mode, a
 * neighbouring block, a tile loading or unloading: see invalidate()).
 *
 * Tiles also trade directly, without a cable, as they do under IC2: a source's output face
 * touching a tile that takes energy on that side sends it one packet of the source's output
 * voltage per tick at most (shared with whatever the source also sends into cables), without
 * line loss; a packet above the receiver's input tier blows it up, just like off a cable.
 */
public final class EnergyNetSC {

    private static final EnergyNetSC INSTANCE = new EnergyNetSC();

    private static final int MAX_NETWORK_CABLES = 8192;

    private final Set<TileEntityEnergyBase> tiles = Collections.newSetFromMap(new WeakHashMap<TileEntityEnergyBase, Boolean>());
    private final Set<TileEntityConduitBundleSC> cables = Collections.newSetFromMap(new WeakHashMap<TileEntityConduitBundleSC, Boolean>());
    private final Map<World, List<Network>> networks = new WeakHashMap<World, List<Network>>();
    /** Tiles touching other energy tiles face to face (direct trade, no cable). */
    private final Map<World, List<Direct>> directs = new WeakHashMap<World, List<Direct>>();
    private int version;
    private final Map<World, Integer> builtVersion = new WeakHashMap<World, Integer>();

    private EnergyNetSC() {
    }

    public static EnergyNetSC instance() {
        return INSTANCE;
    }

    /** Something that changes a network's shape happened - rebuild before the next tick. */
    public void invalidate() {
        version++;
    }

    public void addTile(TileEntityEnergyBase tile) {
        tiles.add(tile);
        invalidate();
    }

    public void removeTile(TileEntityEnergyBase tile) {
        tiles.remove(tile);
        invalidate();
    }

    public void addCable(TileEntityConduitBundleSC cable) {
        cables.add(cable);
        invalidate();
    }

    public void removeCable(TileEntityConduitBundleSC cable) {
        cables.remove(cable);
        invalidate();
    }

    public int tileCount() {
        return tiles.size();
    }

    /** Called once per world tick (server side only) by CommonEventHandler - no-op under IC2. */
    public void serverTick(World world) {
        if (cables.isEmpty() && tiles.isEmpty()) {
            return;
        }
        Integer built = builtVersion.get(world);
        if (built == null || built != version) {
            networks.put(world, build(world));
            directs.put(world, buildDirect(world));
            builtVersion.put(world, version);
        }
        // what each tile has already sent this tick, over all networks: a storage or transformer
        // touching two networks still gives out one packet per tick, not one per network
        Map<TileEntityEnergyBase, Integer> sent = new HashMap<TileEntityEnergyBase, Integer>();
        List<Network> nets = networks.get(world);
        if (nets != null) {
            for (Network net : nets) {
                if (!net.tick(world, sent)) {
                    invalidate();         // something blew up or went away - rebuild next tick
                }
            }
        }
        List<Direct> direct = directs.get(world);
        if (direct != null) {
            for (Direct d : direct) {
                if (!d.tick(sent)) {
                    invalidate();
                }
            }
        }
    }

    /**
     * The cached networks hold the world's tiles, and so the world itself - a WeakHashMap keyed
     * on that world could never let go of it. Dropped here when the world unloads (Forge bus,
     * registered in SCMod.preInit).
     */
    @SubscribeEvent
    public void onWorldUnload(WorldEvent.Unload event) {
        networks.remove(event.world);
        directs.remove(event.world);
        builtVersion.remove(event.world);
    }

    // ------------------------------------------------------------------ direct contact

    /** Every loaded energy tile of the world with the energy tiles right next to it. */
    private List<Direct> buildDirect(World world) {
        List<Direct> out = new ArrayList<Direct>();
        for (TileEntityEnergyBase tile : new ArrayList<TileEntityEnergyBase>(tiles)) {
            if (tile == null || tile.isInvalid() || tile.getWorldObj() != world) {
                continue;
            }
            Direct d = null;
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                int nx = tile.xCoord + dir.offsetX, ny = tile.yCoord + dir.offsetY, nz = tile.zCoord + dir.offsetZ;
                if (!world.blockExists(nx, ny, nz)) {
                    continue;
                }
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileEntityEnergyBase && te != tile) {
                    if (d == null) {
                        d = new Direct(tile);
                    }
                    d.faces.add(dir);
                    d.neighbours.add((TileEntityEnergyBase) te);
                }
            }
            if (d != null) {
                out.add(d);
            }
        }
        return out;
    }

    /** One tile and its energy neighbours; whether it gives to them is checked every tick (faces, fusion ignition...). */
    static final class Direct {
        final TileEntityEnergyBase tile;
        final List<ForgeDirection> faces = new ArrayList<ForgeDirection>();
        final List<TileEntityEnergyBase> neighbours = new ArrayList<TileEntityEnergyBase>();

        Direct(TileEntityEnergyBase tile) {
            this.tile = tile;
        }

        /** @return false when something exploded or went away (the caller rebuilds). */
        boolean tick(Map<TileEntityEnergyBase, Integer> sent) {
            if (tile.isInvalid()) {
                return false;
            }
            if (!tile.isEnergySource()) {
                return true;
            }
            int offer = Network.offerOf(tile, sent);
            if (offer <= 0) {
                return true;
            }
            int n = faces.size();
            int[] demand = new int[n];
            boolean any = false;
            for (int k = 0; k < n; k++) {
                TileEntityEnergyBase sink = neighbours.get(k);
                if (sink.isInvalid()) {
                    return false;
                }
                ForgeDirection face = faces.get(k);
                // direct contact never overvolts: a sink below the source's voltage just gets nothing
                // (blocks placed side by side in worlds from before direct transfer would blow up on load)
                if (tile.isOutputFace(face) && sink.isEnergySink() && sink.acceptsFrom(face.getOpposite())
                        && sink.inputTier().excessTiersOf(tile.outputTier()) <= 0) {
                    demand[k] = Math.max(0, sink.demandedEnergy());
                    any |= demand[k] > 0;
                }
            }
            if (!any) {
                return true;
            }
            int[] give = EnergySplitSC.split(offer, demand, new int[n]);
            int voltage = tile.outputTier().getVoltage();
            int spent = 0;
            boolean ok = true;
            for (int k = 0; k < n && ok; k++) {
                if (give[k] <= 0) {
                    continue;
                }
                TileEntityEnergyBase sink = neighbours.get(k);
                int accepted = sink.receiveEnergy(faces.get(k).getOpposite(), voltage, give[k], false);
                if (sink.isInvalid()) {
                    spent += give[k];       // overvolted - it's gone, the packet with it
                    ok = false;
                } else if (accepted > 0) {
                    spent += accepted;
                }
            }
            if (spent > 0) {
                tile.removeEnergy(spent);
                Integer already = sent.get(tile);
                sent.put(tile, (already == null ? 0 : already) + spent);
            }
            return ok;
        }
    }

    // ------------------------------------------------------------------ building

    private List<Network> build(World world) {
        List<Network> out = new ArrayList<Network>();
        Set<TileEntityConduitBundleSC> seen = new HashSet<TileEntityConduitBundleSC>();
        for (TileEntityConduitBundleSC start : new ArrayList<TileEntityConduitBundleSC>(cables)) {
            if (start.isInvalid() || start.getWorldObj() != world || start.getCable() == null || seen.contains(start)) {
                continue;
            }
            out.add(walk(world, start, seen));
        }
        return out;
    }

    /** Collects one network: its cables (with links) and every tile touching it. */
    private Network walk(World world, TileEntityConduitBundleSC start, Set<TileEntityConduitBundleSC> seen) {
        CableType type = start.getCable();
        Network net = new Network(type);
        Map<TileEntityConduitBundleSC, Integer> index = new HashMap<TileEntityConduitBundleSC, Integer>();
        ArrayDeque<TileEntityConduitBundleSC> queue = new ArrayDeque<TileEntityConduitBundleSC>();
        queue.add(start);
        seen.add(start);
        index.put(start, 0);
        net.cables.add(start);
        net.links.add(new ArrayList<Integer>());
        Map<TileEntityEnergyBase, Endpoint> endpoints = new LinkedHashMap<TileEntityEnergyBase, Endpoint>();
        while (!queue.isEmpty() && net.cables.size() < MAX_NETWORK_CABLES) {
            TileEntityConduitBundleSC cable = queue.poll();
            int ci = index.get(cable);
            for (ForgeDirection dir : ForgeDirection.VALID_DIRECTIONS) {
                if (!cable.cableOpen(dir)) {
                    continue;
                }
                int nx = cable.xCoord + dir.offsetX, ny = cable.yCoord + dir.offsetY, nz = cable.zCoord + dir.offsetZ;
                if (!world.blockExists(nx, ny, nz)) {
                    continue;                 // never load a chunk just to look into it
                }
                TileEntity te = world.getTileEntity(nx, ny, nz);
                if (te instanceof TileEntityConduitBundleSC) {
                    TileEntityConduitBundleSC other = (TileEntityConduitBundleSC) te;
                    if (other.getCable() != type || !other.cableOpen(dir.getOpposite())) {
                        continue;
                    }
                    Integer oi = index.get(other);
                    if (oi == null) {
                        oi = net.cables.size();
                        index.put(other, oi);
                        net.cables.add(other);
                        net.links.add(new ArrayList<Integer>());
                        seen.add(other);
                        queue.add(other);
                    }
                    net.links.get(ci).add(oi);
                } else if (te instanceof TileEntityEnergyBase) {
                    TileEntityEnergyBase tile = (TileEntityEnergyBase) te;
                    Endpoint ep = endpoints.get(tile);
                    if (ep == null) {
                        ep = new Endpoint(tile);
                        endpoints.put(tile, ep);
                    }
                    ep.touch(dir.getOpposite(), ci);
                }
            }
        }
        for (Endpoint ep : endpoints.values()) {
            ep.netCables = net.cables;
        }
        net.endpoints.addAll(endpoints.values());
        net.measure();
        return net;
    }

    /** A tile touching the network: the faces it touches it with and the cables at those faces. */
    static final class Endpoint {
        final TileEntityEnergyBase tile;
        final List<ForgeDirection> faces = new ArrayList<ForgeDirection>();
        final List<Integer> cablesAt = new ArrayList<Integer>();
        /** Cables between this tile and the nearest supplier (for its line loss). */
        int distance;

        Endpoint(TileEntityEnergyBase tile) {
            this.tile = tile;
        }

        void touch(ForgeDirection face, int cable) {
            faces.add(face);
            cablesAt.add(cable);
        }

        /** Set when the network is built: the cables the faces touch (for their connector modes). */
        List<TileEntityConduitBundleSC> netCables;

        /** The cable connector a face of this tile touches, and its side as the cable sees it. */
        private boolean connectorAllows(int k, boolean out) {
            TileEntityConduitBundleSC cable = netCables.get(cablesAt.get(k));
            ForgeDirection side = faces.get(k).getOpposite();
            com.sc.conduit.ConduitMode m = cable.mode(com.sc.conduit.ConduitKind.CABLE, side);
            return out ? m.extracts(com.sc.conduit.ConduitKind.CABLE) && cable.redstoneAllows(com.sc.conduit.ConduitKind.CABLE, side)
                    : m.inserts(com.sc.conduit.ConduitKind.CABLE);
        }

        /** The face energy comes out of into this network (the tile's output face, the connector letting it out), or null. */
        ForgeDirection outFace() {
            if (!tile.isEnergySource()) {
                return null;
            }
            for (int k = 0; k < faces.size(); k++) {
                if (tile.isOutputFace(faces.get(k)) && connectorAllows(k, true)) {
                    return faces.get(k);
                }
            }
            return null;
        }

        /** The face energy from this network goes in through (the connector letting it in), or null. */
        ForgeDirection inFace() {
            if (!tile.isEnergySink()) {
                return null;
            }
            for (int k = 0; k < faces.size(); k++) {
                if (tile.acceptsFrom(faces.get(k)) && connectorAllows(k, false)) {
                    return faces.get(k);
                }
            }
            return null;
        }
    }

    // ------------------------------------------------------------------ one network

    static final class Network {
        final CableType type;
        final List<TileEntityConduitBundleSC> cables = new ArrayList<TileEntityConduitBundleSC>();
        final List<List<Integer>> links = new ArrayList<List<Integer>>();
        final List<Endpoint> endpoints = new ArrayList<Endpoint>();

        Network(CableType type) {
            this.type = type;
        }

        /** Every endpoint's cable distance to the nearest supplier-capable tile (multi-source BFS). */
        void measure() {
            int[] dist = new int[cables.size()];
            java.util.Arrays.fill(dist, Integer.MAX_VALUE);
            ArrayDeque<Integer> queue = new ArrayDeque<Integer>();
            for (Endpoint ep : endpoints) {
                if (ep.outFace() != null) {
                    for (int c : ep.cablesAt) {
                        if (dist[c] != 1) {
                            dist[c] = 1;
                            queue.add(c);
                        }
                    }
                }
            }
            while (!queue.isEmpty()) {
                int c = queue.poll();
                for (int n : links.get(c)) {
                    if (dist[n] > dist[c] + 1) {
                        dist[n] = dist[c] + 1;
                        queue.add(n);
                    }
                }
            }
            for (Endpoint ep : endpoints) {
                int best = Integer.MAX_VALUE;
                for (int c : ep.cablesAt) {
                    best = Math.min(best, dist[c]);
                }
                ep.distance = best == Integer.MAX_VALUE ? 0 : best;
            }
        }

        /** @return false when something exploded (the caller rebuilds). */
        boolean tick(World world, Map<TileEntityEnergyBase, Integer> sent) {
            List<Endpoint> suppliers = new ArrayList<Endpoint>();
            List<Endpoint> consumers = new ArrayList<Endpoint>();
            List<Endpoint> buffers = new ArrayList<Endpoint>();
            for (Endpoint ep : endpoints) {
                if (ep.tile.isInvalid()) {
                    return false;
                }
                boolean out = ep.outFace() != null, in = ep.inFace() != null;
                if (out && in) {
                    buffers.add(ep);
                } else if (out) {
                    suppliers.add(ep);
                } else if (in) {
                    consumers.add(ep);
                }
            }
            // §9.3: a supplier above the cable's tier burns out the cable it feeds into (no blast).
            for (Endpoint ep : concat(suppliers, buffers)) {
                if (ep.tile.offerableEnergy() > 0 && type.tier.excessTiersOf(ep.tile.outputTier()) > 0) {
                    ForgeDirection out = ep.outFace();
                    if (out == null) {
                        continue;
                    }
                    TileEntityConduitBundleSC cable = cables.get(ep.cablesAt.get(ep.faces.indexOf(out)));
                    ExplosionLogic.burnCableIfOvervolted(cable, type.tier, ep.tile.outputTier());
                    return false;
                }
            }
            int capacity = type.maxThroughput();
            int[] used = {0};
            if (!move(suppliers, consumers, capacity, used, sent)) {
                return false;
            }
            if (!move(suppliers, buffers, capacity, used, sent)) {   // surplus charges the buffers
                return false;
            }
            return move(buffers, consumers, capacity, used, sent);   // shortfall is drawn from them
        }

        /** What a tile can still give this tick: one packet of its output voltage in all, whatever it touches. */
        private static int offerOf(TileEntityEnergyBase tile, Map<TileEntityEnergyBase, Integer> sent) {
            Integer already = sent.get(tile);
            int room = tile.outputTier().getVoltage() - (already == null ? 0 : already);
            return Math.max(0, Math.min(tile.offerableEnergy(), room));
        }

        /** One supply -> demand pass within what's left of the network's rating. */
        private boolean move(List<Endpoint> from, List<Endpoint> to, int capacity, int[] used,
                             Map<TileEntityEnergyBase, Integer> sent) {
            if (from.isEmpty() || to.isEmpty() || used[0] >= capacity) {
                return true;
            }
            int[] offer = new int[from.size()];
            long supply = 0;
            Tier voltage = null;
            for (int i = 0; i < offer.length; i++) {
                offer[i] = offerOf(from.get(i).tile, sent);
                supply += offer[i];
                if (offer[i] > 0 && (voltage == null || from.get(i).tile.outputTier().ordinal() > voltage.ordinal())) {
                    voltage = from.get(i).tile.outputTier();
                }
            }
            if (supply <= 0) {
                return true;
            }
            int budget = (int) Math.min(supply, capacity - used[0]);
            int[] demand = new int[to.size()];
            int[] loss = new int[to.size()];
            for (int i = 0; i < demand.length; i++) {
                demand[i] = Math.max(0, to.get(i).tile.demandedEnergy());
                loss[i] = type.lossPerBlock * to.get(i).distance;
            }
            int[] give = EnergySplitSC.split(budget, demand, loss);
            int spent = 0;
            for (int i = 0; i < give.length; i++) {
                if (give[i] <= 0) {
                    continue;
                }
                Endpoint ep = to.get(i);
                int accepted = ep.tile.receiveEnergy(ep.inFace(), voltage.getVoltage(), give[i], false);
                if (ep.tile.isInvalid()) {
                    spent += give[i] + loss[i];     // overvolted - it's gone, the packet with it
                    takeFrom(from, offer, spent, sent);
                    return false;
                }
                if (accepted > 0) {
                    spent += accepted + loss[i];
                }
            }
            takeFrom(from, offer, spent, sent);
            used[0] += spent;
            return true;
        }

        private static void takeFrom(List<Endpoint> from, int[] offer, int total, Map<TileEntityEnergyBase, Integer> sent) {
            int[] taken = EnergySplitSC.draw(total, offer);
            for (int i = 0; i < taken.length; i++) {
                if (taken[i] > 0) {
                    TileEntityEnergyBase tile = from.get(i).tile;
                    tile.removeEnergy(taken[i]);
                    Integer already = sent.get(tile);
                    sent.put(tile, (already == null ? 0 : already) + taken[i]);
                }
            }
        }

        private static List<Endpoint> concat(List<Endpoint> a, List<Endpoint> b) {
            List<Endpoint> all = new ArrayList<Endpoint>(a);
            all.addAll(b);
            return all;
        }
    }
}
