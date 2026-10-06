package com.sc.handler;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import com.sc.Reference;
import com.sc.ShieldEventHandler;
import com.sc.conduit.ConduitKind;
import com.sc.conduit.ConduitMode;
import com.sc.energy.CableType;
import com.sc.energy.Tier;
import com.sc.energy.TileEntityEnergyBase;
import com.sc.tileentity.TileEntityConduitBundleSC;
import com.sc.util.NetViewScanSC;
import com.sc.util.NetViewScanSC.Snapshot;

import cpw.mods.fml.common.Loader;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.world.World;
import net.minecraftforge.common.util.ForgeDirection;

/**
 * Server -> client: the network overview (wrench, sneak + right-click on a cable). The server walks
 * the clicked cable's network (NetViewScanSC), reads what the cables and blocks on it say - IC2's
 * measured flow when IC2 routes, an estimate of the mod's own net's tick otherwise - and sends the
 * snapshot to that player only. The client keeps it in `received` for NetViewRendererSC.
 */
public final class NetViewNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeNetView");

    private static final NetViewScanSC.Clicks CLICKS = new NetViewScanSC.Clicks();

    /** Client: the last snapshot heard, taken by the renderer (handed over between threads). */
    public static volatile Snapshot received;

    private NetViewNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.CLIENT);
    }

    /**
     * The wrench's click on a cable (server side). @return true when the click was taken (the
     * cable's side mode is not cycled); false when the bundle has no cable.
     */
    public static boolean click(EntityPlayer player, World world, int x, int y, int z) {
        if (world.isRemote || !(player instanceof EntityPlayerMP)) {
            return false;
        }
        TileEntity te = world.getTileEntity(x, y, z);
        if (!(te instanceof TileEntityConduitBundleSC) || ((TileEntityConduitBundleSC) te).getCable() == null) {
            return false;
        }
        EntityPlayerMP p = (EntityPlayerMP) player;
        int what = CLICKS.click(p, world.getTotalWorldTime());
        if (what == NetViewScanSC.CLICK_IGNORE) {
            return true;
        }
        if (ShieldEventHandler.privateFieldAgainst(world, p, x, y, z) != null) {
            p.addChatComponentMessage(new ChatComponentTranslation("sc.netview.noaccess"));
            return true;
        }
        Snapshot s;
        if (what == NetViewScanSC.CLICK_PING) {
            s = new Snapshot();
            s.flags = NetViewScanSC.S_PING;
        } else {
            s = build(p, world, (TileEntityConduitBundleSC) te);
            if ((s.flags & NetViewScanSC.S_TRUNCATED) != 0) {
                p.addChatComponentMessage(new ChatComponentTranslation("sc.netview.truncated",
                        String.valueOf(NetViewScanSC.MAX_CABLES), String.valueOf(NetViewScanSC.MAX_ENDPOINTS)));
            }
            if ((s.flags & NetViewScanSC.S_HIDDEN) != 0) {
                p.addChatComponentMessage(new ChatComponentTranslation("sc.netview.hidden"));
            }
        }
        s.dim = world.provider.dimensionId;
        s.ox = x;
        s.oy = y;
        s.oz = z;
        CHANNEL.sendTo(new Message(s), p);
        return true;
    }

    // ------------------------------------------------------------------ the world side of the walk

    private static boolean refused(World w, EntityPlayer p, int x, int y, int z) {
        return ShieldEventHandler.privateFieldAgainst(w, p, x, y, z) != null;
    }

    private static TileEntityConduitBundleSC bundle(World w, long pos) {
        int x = NetViewScanSC.unpackX(pos), y = NetViewScanSC.unpackY(pos), z = NetViewScanSC.unpackZ(pos);
        if (!w.blockExists(x, y, z)) {
            return null;
        }
        TileEntity te = w.getTileEntity(x, y, z);
        return te instanceof TileEntityConduitBundleSC ? (TileEntityConduitBundleSC) te : null;
    }

    /** The bundle's own rules (linksTo / connectorAt: open sides, same cable, loaded chunks only); private fields cut the walk. */
    private static final class WorldGraph implements NetViewScanSC.Graph {
        final World w;
        final EntityPlayer p;
        final Set<Long> hidden = new HashSet<Long>();

        WorldGraph(World w, EntityPlayer p) {
            this.w = w;
            this.p = p;
        }

        @Override
        public void links(long cable, List<Long> out) {
            walk(cable, out, true);
        }

        @Override
        public void endpoints(long cable, List<Long> out) {
            walk(cable, out, false);
        }

        private void walk(long cable, List<Long> out, boolean links) {
            TileEntityConduitBundleSC b = bundle(w, cable);
            if (b == null) {
                return;
            }
            for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
                if (links ? !b.linksTo(ConduitKind.CABLE, d) : !b.connectorAt(ConduitKind.CABLE, d)) {
                    continue;
                }
                int nx = b.xCoord + d.offsetX, ny = b.yCoord + d.offsetY, nz = b.zCoord + d.offsetZ;
                long n = NetViewScanSC.pack(nx, ny, nz);
                if (hidden.contains(n)) {
                    continue;
                }
                if (refused(w, p, nx, ny, nz)) {
                    hidden.add(n);
                    continue;
                }
                out.add(n);
            }
        }
    }

    // ------------------------------------------------------------------ snapshot

    static Snapshot build(EntityPlayer p, World w, TileEntityConduitBundleSC start) {
        WorldGraph g = new WorldGraph(w, p);
        NetViewScanSC.Scan scan = NetViewScanSC.scan(g, NetViewScanSC.pack(start.xCoord, start.yCoord, start.zCoord),
                NetViewScanSC.MAX_CABLES, NetViewScanSC.MAX_ENDPOINTS);
        Snapshot s = new Snapshot();
        s.flags = (scan.truncated ? NetViewScanSC.S_TRUNCATED : 0) | (g.hidden.isEmpty() ? 0 : NetViewScanSC.S_HIDDEN);
        s.hidden = Math.min(0xFFFF, g.hidden.size());
        int n = scan.cables.size(), m = scan.endpoints.size();
        TileEntityConduitBundleSC[] cables = new TileEntityConduitBundleSC[n];
        s.cables(n);
        for (int i = 0; i < n; i++) {
            long c = scan.cables.get(i);
            cables[i] = bundle(w, c);
            s.cx[i] = NetViewScanSC.unpackX(c);
            s.cy[i] = NetViewScanSC.unpackY(c);
            s.cz[i] = NetViewScanSC.unpackZ(c);
            CableType t = cables[i] == null ? null : cables[i].getCable();
            s.ctype[i] = (byte) (t == null ? start.getCable().ordinal() : t.ordinal());
        }
        s.endpoints(m);
        TileEntity[] tiles = new TileEntity[m];
        int[] role = new int[m], offer = new int[m], demand = new int[m];
        for (int e = 0; e < m; e++) {
            long pos = scan.endpoints.get(e);
            s.ex[e] = NetViewScanSC.unpackX(pos);
            s.ey[e] = NetViewScanSC.unpackY(pos);
            s.ez[e] = NetViewScanSC.unpackZ(pos);
            tiles[e] = w.getTileEntity(s.ex[e], s.ey[e], s.ez[e]);
            s.epct[e] = -1;
            s.etier[e] = -1;
            describe(s, e, tiles[e], scan.endpointCables.get(e), cables, role, offer, demand);
        }
        boolean ic2 = Loader.isModLoaded(Reference.IC2_MODID) && Ic2.ready();
        if (ic2) {
            s.flags |= NetViewScanSC.S_MEASURED;
            for (int i = 0; i < n; i++) {
                s.cflow[i] = cables[i] == null ? 0 : Ic2.flow(cables[i]);
            }
            for (int e = 0; e < m; e++) {
                int out = (role[e] & NetViewScanSC.R_OUT) != 0 && tiles[e] != null ? Ic2.out(tiles[e]) : 0;
                int in = (role[e] & NetViewScanSC.R_IN) != 0 && tiles[e] != null ? Ic2.in(tiles[e]) : 0;
                s.erate[e] = out > 0 ? out : in;
                s.gen += out;
                s.cons += in;
            }
            s.loss = Math.max(0, s.gen - s.cons);
        } else {
            CableType type = start.getCable();
            NetViewScanSC.Flow f = NetViewScanSC.estimate(scan, role, offer, demand, type.lossPerBlock, type.maxThroughput());
            System.arraycopy(f.cableFlow, 0, s.cflow, 0, n);
            for (int e = 0; e < m; e++) {
                s.erate[e] = f.given[e] > 0 ? f.given[e] : f.taken[e];
                s.gen += f.given[e];
                s.cons += f.taken[e];
            }
            s.loss = f.loss;
        }
        // bottlenecks: cables below the strongest source's packet, the ones a source overvolts, and the ones at their rating
        for (int e = 0; e < m; e++) {
            if ((role[e] & NetViewScanSC.R_OUT) != 0 && s.etier[e] > s.maxSourceTier) {
                s.maxSourceTier = s.etier[e];
            }
        }
        for (int i = 0; i < n; i++) {
            CableType t = CableType.values()[s.ctype[i]];
            if (s.maxSourceTier > t.tier.ordinal()) {
                s.cflags[i] |= NetViewScanSC.C_BOTTLENECK;
            }
        }
        for (int e = 0; e < m; e++) {
            if ((role[e] & NetViewScanSC.R_OUT) == 0 || s.etier[e] < 0) {
                continue;
            }
            for (int c : scan.endpointCables.get(e)) {
                if (s.etier[e] > CableType.values()[s.ctype[c]].tier.ordinal()) {
                    s.cflags[c] |= NetViewScanSC.C_OVERVOLT;
                }
            }
        }
        for (int i = 0; i < n; i++) {
            if (NetViewScanSC.level(s.cflow[i], CableType.values()[s.ctype[i]].maxThroughput(), s.cflags[i]) >= NetViewScanSC.L_HIGH) {
                s.bottlenecks++;
            }
        }
        s.bottlenecks = Math.min(0xFFFF, s.bottlenecks);
        return s;
    }

    /** Kind, tier, role on this network, and what it offers / wants (the estimate's input). */
    private static void describe(Snapshot s, int e, TileEntity te, int[] at, TileEntityConduitBundleSC[] cables,
                                 int[] role, int[] offer, int[] demand) {
        if (te instanceof TileEntityEnergyBase) {
            TileEntityEnergyBase t = (TileEntityEnergyBase) te;
            for (int c : at) {
                TileEntityConduitBundleSC b = cables[c];
                if (b == null) {
                    continue;
                }
                ForgeDirection side = dirTo(b, s.ex[e], s.ey[e], s.ez[e]);
                if (side == ForgeDirection.UNKNOWN) {
                    continue;
                }
                ForgeDirection face = side.getOpposite();
                ConduitMode mode = b.mode(ConduitKind.CABLE, side);
                if (t.isEnergySource() && t.isOutputFace(face) && mode.extracts(ConduitKind.CABLE)
                        && b.redstoneAllows(ConduitKind.CABLE, side)) {
                    role[e] |= NetViewScanSC.R_OUT;
                }
                if (t.isEnergySink() && t.acceptsFrom(face) && mode.inserts(ConduitKind.CABLE)) {
                    role[e] |= NetViewScanSC.R_IN;
                }
            }
            int kind;
            if (te instanceof com.sc.tileentity.TileEntityTransformerSC) {
                kind = NetViewScanSC.K_TRANSFORMER;
            } else if (te instanceof com.sc.tileentity.TileEntityEnergyStorageSC
                    || t.isEnergySource() && t.isEnergySink() && !(te instanceof com.sc.tileentity.TileEntityGeneratorSC)) {
                kind = NetViewScanSC.K_STORAGE;
            } else if (t.isEnergySource()) {
                kind = NetViewScanSC.K_SOURCE;
            } else {
                kind = NetViewScanSC.K_CONSUMER;
            }
            s.ekind[e] = (byte) kind;
            boolean out = (role[e] & NetViewScanSC.R_OUT) != 0;
            Tier tier = out || kind != NetViewScanSC.K_CONSUMER ? t.outputTier() : t.inputTier();
            s.etier[e] = (byte) tier.ordinal();
            s.erole[e] = (byte) role[e];
            if (kind == NetViewScanSC.K_STORAGE && t.getMaxEnergyStored() > 0) {
                s.stored += t.getEnergyStored();
                s.capacity += t.getMaxEnergyStored();
                s.epct[e] = (int) Math.min(100L, 100L * t.getEnergyStored() / t.getMaxEnergyStored());
            }
            if (out) {
                int packets = Math.max(1, t.packetsPerTick());
                offer[e] = packets > 1 ? Math.min(t.getEnergyStored(), t.outputTier().getVoltage() * packets) : t.offerableEnergy();
            }
            if ((role[e] & NetViewScanSC.R_IN) != 0) {
                int cap = t.acceptsAnyVoltage() ? Integer.MAX_VALUE : t.inputTier().getVoltage();
                if (te instanceof com.sc.tileentity.TileEntityMachineSC && ((com.sc.tileentity.TileEntityMachineSC) te).getProgressTicks() > 0) {
                    demand[e] = Math.min(cap, ((com.sc.tileentity.TileEntityMachineSC) te).effectiveEuPerTick());   // steady state while it runs
                } else {
                    demand[e] = Math.max(0, Math.min(cap, t.demandedEnergy()));
                }
            }
        } else if (te != null && Loader.isModLoaded(Reference.IC2_MODID)) {
            s.ekind[e] = NetViewScanSC.K_FOREIGN;
            role[e] = Ic2.role(te);
            s.erole[e] = (byte) role[e];
            s.etier[e] = (byte) Ic2.tier(te);
        } else {
            s.ekind[e] = NetViewScanSC.K_FOREIGN;
        }
    }

    private static ForgeDirection dirTo(TileEntity from, int x, int y, int z) {
        for (ForgeDirection d : ForgeDirection.VALID_DIRECTIONS) {
            if (from.xCoord + d.offsetX == x && from.yCoord + d.offsetY == y && from.zCoord + d.offsetZ == z) {
                return d;
            }
        }
        return ForgeDirection.UNKNOWN;
    }

    /** IC2's side: only touched when IC2 is loaded (its classes resolve lazily). */
    private static final class Ic2 {
        static boolean ready() {
            return ic2.api.energy.EnergyNet.instance != null;
        }

        private static ic2.api.energy.NodeStats stats(TileEntity te) {
            try {
                return ic2.api.energy.EnergyNet.instance.getNodeStats(te);
            } catch (RuntimeException ex) {
                return null;                                  // not on IC2's net (yet)
            }
        }

        static int flow(TileEntity te) {
            ic2.api.energy.NodeStats st = stats(te);
            return st == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.round(Math.max(st.getEnergyIn(), st.getEnergyOut())));
        }

        static int out(TileEntity te) {
            ic2.api.energy.NodeStats st = stats(te);
            return st == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.round(st.getEnergyOut()));
        }

        static int in(TileEntity te) {
            ic2.api.energy.NodeStats st = stats(te);
            return st == null ? 0 : (int) Math.min(Integer.MAX_VALUE, Math.round(st.getEnergyIn()));
        }

        static int role(TileEntity te) {
            return (te instanceof ic2.api.energy.tile.IEnergySource ? NetViewScanSC.R_OUT : 0)
                    | (te instanceof ic2.api.energy.tile.IEnergySink ? NetViewScanSC.R_IN : 0);
        }

        /** Our tier ordinal of an IC2 block (its source tier if it gives, else its sink tier), -1 unknown. */
        static int tier(TileEntity te) {
            try {
                if (te instanceof ic2.api.energy.tile.IEnergySource) {
                    return Tier.fromIc2Tier(((ic2.api.energy.tile.IEnergySource) te).getSourceTier()).ordinal();
                }
                if (te instanceof ic2.api.energy.tile.IEnergySink) {
                    return Tier.fromIc2Tier(((ic2.api.energy.tile.IEnergySink) te).getSinkTier()).ordinal();
                }
            } catch (RuntimeException ex) {
                // a foreign block misbehaving - its tier stays unknown
            }
            return -1;
        }
    }

    // ------------------------------------------------------------------ message

    public static class Message implements IMessage {
        public Snapshot snapshot;

        public Message() {
        }

        public Message(Snapshot snapshot) {
            this.snapshot = snapshot;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            snapshot = NetViewScanSC.decode(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            NetViewScanSC.encode(buf, snapshot);
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            received = msg.snapshot;
            return null;
        }
    }
}
