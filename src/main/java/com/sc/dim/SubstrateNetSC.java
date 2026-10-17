package com.sc.dim;

import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.SCMod;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.FMLLog;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.FMLNetworkEvent;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraftforge.common.DimensionManager;

/**
 * Server -> client on joining (docs/plan-substrate-tech.md §2.2, rule ПК-4): the server's Substrate ids and rules.
 * <ul>
 * <li><b>rules</b> - every public static non-final primitive field of SubstrateConfigSC, by name (a new rule travels
 * with no change here): a client on a remote server takes the server's and gets its own back on leaving (single
 * player and the LAN host share the fields with their server - nothing changes there);</li>
 * <li><b>ids</b> - never applied, only compared. A client whose own dimension id differs, and that doesn't know the
 * server's id at all, registers the server's id with its own provider type (what Forge's DimensionMessageHandler
 * does) so entering doesn't crash - for that session only: leaving the server unregisters it again
 * (forgetSessionDim); any difference is one line in the log and one in the chat
 * (client/SubstrateIdsClientSC): the biomes can't be fixed that way - the configs must match (README).</li>
 * </ul>
 * Only the coordinator edits this file.
 */
public final class SubstrateNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeDim");

    private static final byte BOOL = 0, INT = 1, FLOAT = 2, DOUBLE = 3, LONG = 4;

    /** Client: its own rules while it plays on a server (null: none taken). */
    private static Map<String, Object> localRules;
    /** Client: the ids differ from the server's - the chat line still to show (SubstrateIdsClientSC takes it). */
    private static volatile String pendingWarning;
    /**
     * Client: the server's dimension id compareIds registered here for the session (null: none). DimensionManager is
     * static and shared with the integrated server - left behind, a single player world started next would load a
     * second «Substrate» under that id, and a third server's dimension of that id would get our provider.
     */
    private static Integer sessionDim;

    private SubstrateNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.CLIENT);
        com.sc.settle.SettleNetSC.register(CHANNEL);          // Ф2: the settlements' messages, discriminators 1 and 2
        com.sc.hazard.HazardNetSC.register(CHANNEL);          // Ф4: the EMP storm's state and pulses, discriminator 3
        com.sc.finale.FinaleNetSC.register(CHANNEL);          // Ф5: the Lithographers' Signal, the Warden's effects, discriminator 4
        FMLCommonHandler.instance().bus().register(new Events());
    }

    /** The rules: public static non-final primitives of SubstrateConfigSC (the ids are private - never among them). Public for the self-test. */
    public static List<Field> ruleFields() {
        List<Field> out = new ArrayList<Field>();
        for (Field f : SubstrateConfigSC.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (Modifier.isPublic(m) && Modifier.isStatic(m) && !Modifier.isFinal(m) && typeOf(f) >= 0) {
                out.add(f);
            }
        }
        return out;
    }

    private static byte typeOf(Field f) {
        Class<?> t = f.getType();
        return t == boolean.class ? BOOL : t == int.class ? INT : t == float.class ? FLOAT : t == double.class ? DOUBLE
                : t == long.class ? LONG : -1;
    }

    /** Client (any thread): the ids warning not shown yet, taken once. */
    public static String takeWarning() {
        String w = pendingWarning;
        pendingWarning = null;
        return w;
    }

    /** Client: left the server - its own rules back. */
    public static synchronized void restoreLocal() {
        if (localRules == null) {
            return;
        }
        for (Field f : ruleFields()) {
            Object v = localRules.get(f.getName());
            if (v != null) {
                try {
                    f.set(null, v);
                } catch (Exception ignored) {
                    // left as the server had it
                }
            }
        }
        localRules = null;
        com.sc.manual.BookContent.invalidate();                 // the handbook's numbers (bridge price, trophies) are the rules'
    }

    static synchronized void apply(Message msg) {
        if (localRules == null) {
            localRules = new HashMap<String, Object>();
            for (Field f : ruleFields()) {
                try {
                    localRules.put(f.getName(), f.get(null));
                } catch (Exception ignored) {
                    // not kept: not restored either
                }
            }
        }
        for (Map.Entry<String, Object> e : msg.rules.entrySet()) {
            try {
                Field f = SubstrateConfigSC.class.getField(e.getKey());
                int m = f.getModifiers();
                Byte type = msg.types.get(e.getKey());
                if (Modifier.isStatic(m) && !Modifier.isFinal(m) && type != null && typeOf(f) == type) {
                    f.set(null, e.getValue());
                }
            } catch (Exception ignored) {
                // a rule this client's version doesn't have: skipped
            }
        }
        com.sc.manual.BookContent.invalidate();
    }

    /**
     * Client: the server's ids against ours. @return the warning for the chat, or null when they agree. Registers the
     * server's dimension id with our provider type when this client has no such dimension (no crash on entering).
     */
    static String compareIds(Message msg) {
        if (!msg.active) {
            return null;                                        // the server has no Substrate: nothing to enter
        }
        boolean mine = SubstrateSC.active();
        StringBuilder diff = new StringBuilder();
        if (!mine) {
            diff.append("off here (").append(SubstrateSC.offReason()).append(")");
        } else {
            if (SubstrateSC.dimId() != msg.dim) {
                diff.append("dimensionId ").append(SubstrateSC.dimId()).append(" / server ").append(msg.dim).append("; ");
            }
            int[] mineIds = biomeIds(), theirs = msg.biomes();
            if (!java.util.Arrays.equals(mineIds, theirs)) {
                diff.append("biomes ").append(java.util.Arrays.toString(mineIds)).append(" / server ").append(java.util.Arrays.toString(theirs))
                        .append("; ");
            }
            if (SubstrateSC.dimId() != msg.dim && !DimensionManager.isDimensionRegistered(msg.dim)) {
                try {
                    DimensionManager.registerDimension(msg.dim, SubstrateSC.providerId());
                    synchronized (SubstrateNetSC.class) {
                        sessionDim = Integer.valueOf(msg.dim);
                    }
                    diff.append("the server's dimension ").append(msg.dim).append(" registered here for this session; ");
                } catch (Exception ex) {
                    diff.append("the server's dimension ").append(msg.dim).append(" couldn't be registered here; ");
                }
            }
        }
        if (diff.length() == 0) {
            return null;
        }
        FMLLog.warning("[Silicon Age] Si-14 Substrate ids differ from the server's: %s- the section \"dimension\" of config/SiliconAge.cfg must match the server's", diff);
        return diff.toString();
    }

    /** Client: left the server - the dimension compareIds registered for it goes (only while it is still ours). */
    public static synchronized void forgetSessionDim() {
        if (sessionDim == null) {
            return;
        }
        int d = sessionDim.intValue();
        sessionDim = null;
        try {
            if (d != SubstrateSC.dimId() && DimensionManager.isDimensionRegistered(d) && DimensionManager.getProviderType(d) == SubstrateSC.providerId()) {
                DimensionManager.unregisterDimension(d);
            }
        } catch (RuntimeException ex) {
            FMLLog.warning("[Silicon Age] Si-14 Substrate: the server's dimension %d couldn't be unregistered here (%s)", d, ex);
        }
    }

    /** This side's biome ids by BiomeSubstrateSC kind (-1 for one that isn't made). */
    static int[] biomeIds() {
        int[] out = new int[BiomeSubstrateSC.KINDS];
        for (int k = 0; k < out.length; k++) {
            BiomeSubstrateSC b = SubstrateSC.active() ? SubstrateSC.biome(k) : null;
            out[k] = b == null || b.kind != k ? -1 : b.biomeID;
        }
        return out;
    }

    public static class Message implements IMessage {
        boolean active;
        int dim, provider, biomePlateaus, biomeCanyons;
        /** Ф4: the Ridges', the Oxide Fields', the Rift's ids - after the rules on the wire (a client of Ф1..Ф3 stops reading before them). */
        int biomeRidges = -1, biomeOxide = -1, biomeRift = -1;

        /** The server's biome ids by kind (-1: not sent - a server of Ф1..Ф3). */
        int[] biomes() {
            return new int[]{biomePlateaus, biomeCanyons, biomeRidges, biomeOxide, biomeRift};
        }
        final Map<String, Object> rules = new HashMap<String, Object>();
        final Map<String, Byte> types = new HashMap<String, Byte>();

        public Message() {
        }

        /** The server's ids and rules now. */
        static Message ofServer() {
            Message m = new Message();
            m.active = SubstrateSC.active();
            if (m.active) {
                m.dim = SubstrateSC.dimId();
                m.provider = SubstrateSC.providerId();
                int[] ids = biomeIds();
                m.biomePlateaus = ids[BiomeSubstrateSC.PLATEAUS];
                m.biomeCanyons = ids[BiomeSubstrateSC.CANYONS];
                m.biomeRidges = ids[BiomeSubstrateSC.RIDGES];
                m.biomeOxide = ids[BiomeSubstrateSC.OXIDE];
                m.biomeRift = ids[BiomeSubstrateSC.RIFT];
            }
            for (Field f : ruleFields()) {
                try {
                    m.rules.put(f.getName(), f.get(null));
                    m.types.put(f.getName(), typeOf(f));
                } catch (Exception ignored) {
                    // not sent: the client keeps its own
                }
            }
            return m;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            active = buf.readBoolean();
            dim = buf.readInt();
            provider = buf.readInt();
            biomePlateaus = buf.readUnsignedByte();
            biomeCanyons = buf.readUnsignedByte();
            int n = buf.readUnsignedShort();
            for (int i = 0; i < n; i++) {
                String name = ByteBufUtils.readUTF8String(buf);
                byte type = buf.readByte();
                Object v;
                switch (type) {
                    case BOOL: v = buf.readBoolean(); break;
                    case INT: v = buf.readInt(); break;
                    case FLOAT: v = buf.readFloat(); break;
                    case DOUBLE: v = buf.readDouble(); break;
                    case LONG: v = buf.readLong(); break;
                    default: return;                            // an unknown type: the rest can't be read
                }
                rules.put(name, v);
                types.put(name, type);
            }
            if (buf.readableBytes() >= 3) {                     // Ф4's biomes (a server of Ф1..Ф3 sends none: -1)
                biomeRidges = buf.readUnsignedByte();
                biomeOxide = buf.readUnsignedByte();
                biomeRift = buf.readUnsignedByte();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(active);
            buf.writeInt(dim);
            buf.writeInt(provider);
            buf.writeByte(biomePlateaus);
            buf.writeByte(biomeCanyons);
            buf.writeShort(rules.size());
            for (Map.Entry<String, Object> e : rules.entrySet()) {
                ByteBufUtils.writeUTF8String(buf, e.getKey());
                byte type = types.get(e.getKey());
                buf.writeByte(type);
                Object v = e.getValue();
                switch (type) {
                    case BOOL: buf.writeBoolean((Boolean) v); break;
                    case INT: buf.writeInt((Integer) v); break;
                    case FLOAT: buf.writeFloat((Float) v); break;
                    case DOUBLE: buf.writeDouble((Double) v); break;
                    default: buf.writeLong((Long) v); break;
                }
            }
            buf.writeByte(biomeRidges);                         // Ф4: after the rules (see biomeRidges)
            buf.writeByte(biomeOxide);
            buf.writeByte(biomeRift);
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            if (SCMod.proxy.playsOnRemoteServer()) {           // single player / LAN host: the server's fields are ours already
                apply(msg);
                String w = compareIds(msg);
                if (w != null) {
                    pendingWarning = w;
                }
            }
            return null;
        }
    }

    public static class Events {
        /** Server: a player joined - the Substrate's ids and rules to them. */
        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP && !(event.player instanceof net.minecraftforge.common.util.FakePlayer)) {
                CHANNEL.sendTo(Message.ofServer(), (EntityPlayerMP) event.player);
            }
        }

        /** Client: left the server - its own rules back, the server's dimension id (if compareIds registered it) gone. */
        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            restoreLocal();
            forgetSessionDim();
        }
    }
}
