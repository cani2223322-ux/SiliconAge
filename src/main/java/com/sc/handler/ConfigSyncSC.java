package com.sc.handler;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import java.lang.reflect.Field;
import java.lang.reflect.Modifier;
import java.util.ArrayList;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.SCMod;
import com.sc.util.ConfigSC;
import com.sc.util.OreEntry;

import cpw.mods.fml.common.FMLCommonHandler;
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

/**
 * Server -> client: the server's ConfigSC (balance, ore generation...) on joining, so a client's
 * screens, tooltips, NEI and handbook show the server's numbers, not its own .cfg. Every public
 * static non-final primitive field of ConfigSC goes by name (reflection - a new option travels
 * with no change here), except the client's own preferences - fields marked {@link ClientOnly} (sounds, update
 * check; a new HUD / sound / effect option must carry it too); the ore settings too. The
 * client keeps its own values aside and puts them back when it leaves the server. Single player
 * (and the host of a LAN game) shares the fields with its own server: nothing is changed there.
 */
public final class ConfigSyncSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeConfig");

    /**
     * Marks a ConfigSC field as the client's own preference: never sent, never taken from the server. Every new
     * client-side option (HUD, sound, effects) gets it, or the server's value silently replaces the player's.
     */
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.FIELD)
    public @interface ClientOnly {
    }

    /** The same by name: the fields marked before {@link ClientOnly} existed (kept so they stay local regardless). */
    private static final String[] CLIENT_ONLY = {"machineSounds", "soundVolume", "updateCheck"};

    private static final byte BOOL = 0, INT = 1, FLOAT = 2, DOUBLE = 3, LONG = 4;

    /** Client: its own values while it plays on a server (null: none taken). */
    private static Map<String, Object> localValues;
    private static Map<OreEntry, ConfigSC.OreGenSettings> localOres;

    private ConfigSyncSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.CLIENT);
        FMLCommonHandler.instance().bus().register(new Events());
    }

    /** The fields that travel. */
    static List<Field> syncedFields() {
        List<Field> out = new ArrayList<Field>();
        for (Field f : ConfigSC.class.getDeclaredFields()) {
            int m = f.getModifiers();
            if (!Modifier.isPublic(m) || !Modifier.isStatic(m) || Modifier.isFinal(m) || typeOf(f) < 0) {
                continue;
            }
            if (!clientOnly(f)) {
                out.add(f);
            }
        }
        return out;
    }

    private static boolean clientOnly(Field f) {
        return f.isAnnotationPresent(ClientOnly.class) || clientOnly(f.getName());
    }

    private static boolean clientOnly(String name) {
        for (String n : CLIENT_ONLY) {
            if (n.equals(name)) {
                return true;
            }
        }
        return false;
    }

    private static byte typeOf(Field f) {
        Class<?> t = f.getType();
        return t == boolean.class ? BOOL : t == int.class ? INT : t == float.class ? FLOAT : t == double.class ? DOUBLE
                : t == long.class ? LONG : -1;
    }

    /** ConfigSC's private ore map (null if it isn't there any more). */
    @SuppressWarnings("unchecked")
    private static Map<OreEntry, ConfigSC.OreGenSettings> oreMap() {
        try {
            Field f = ConfigSC.class.getDeclaredField("ORE_SETTINGS");
            f.setAccessible(true);
            return (Map<OreEntry, ConfigSC.OreGenSettings>) f.get(null);
        } catch (Exception e) {
            return null;
        }
    }

    /** Client: back to its own values (left the server). */
    public static synchronized void restoreLocal() {
        if (localValues != null) {
            for (Field f : syncedFields()) {
                Object v = localValues.get(f.getName());
                if (v != null) {
                    try {
                        f.set(null, v);
                    } catch (Exception ignored) {
                        // left as the server had it
                    }
                }
            }
            localValues = null;
        }
        if (localOres != null) {
            Map<OreEntry, ConfigSC.OreGenSettings> map = oreMap();
            if (map != null) {
                map.putAll(localOres);              // over the server's, no clear(): a tooltip reading it meanwhile (the
                                                    // network thread runs this) never finds an ore missing
            }
            localOres = null;
        }
    }

    static synchronized void apply(Message msg) {
        if (localValues == null) {                       // the first packet of this connection: keep our own aside
            localValues = new HashMap<String, Object>();
            for (Field f : syncedFields()) {
                try {
                    localValues.put(f.getName(), f.get(null));
                } catch (Exception ignored) {
                    // not kept: not restored either
                }
            }
            Map<OreEntry, ConfigSC.OreGenSettings> map = oreMap();
            if (map != null) {
                localOres = new EnumMap<OreEntry, ConfigSC.OreGenSettings>(OreEntry.class);
                localOres.putAll(map);
            }
        }
        for (Map.Entry<String, Object> e : msg.values.entrySet()) {
            if (clientOnly(e.getKey())) {
                continue;                                   // a server of another version may still send it
            }
            try {
                Field f = ConfigSC.class.getField(e.getKey());
                if (clientOnly(f)) {
                    continue;                               // marked on this side, whatever the server thinks
                }
                int m = f.getModifiers();
                Byte type = msg.types.get(e.getKey());
                if (Modifier.isStatic(m) && !Modifier.isFinal(m) && type != null && typeOf(f) == type) {
                    f.set(null, e.getValue());
                }
            } catch (Exception ignored) {
                // an option this client's version doesn't have: skipped
            }
        }
        Map<OreEntry, ConfigSC.OreGenSettings> map = oreMap();
        if (map != null) {
            OreEntry[] all = OreEntry.values();
            for (Map.Entry<Integer, int[]> e : msg.ores.entrySet()) {
                if (e.getKey() >= 0 && e.getKey() < all.length) {
                    int[] v = e.getValue();
                    map.put(all[e.getKey()], new ConfigSC.OreGenSettings(v[0], v[1], v[2], v[3]));
                }
            }
        }
    }

    public static class Message implements IMessage {
        final Map<String, Object> values = new HashMap<String, Object>();
        final Map<String, Byte> types = new HashMap<String, Byte>();
        /** OreEntry ordinal -> minY, maxY, veinSize, veinsPerChunk. */
        final Map<Integer, int[]> ores = new HashMap<Integer, int[]>();

        public Message() {
        }

        /** The server's current values. */
        static Message ofServer() {
            Message msg = new Message();
            for (Field f : syncedFields()) {
                try {
                    msg.values.put(f.getName(), f.get(null));
                    msg.types.put(f.getName(), typeOf(f));
                } catch (Exception ignored) {
                    // not sent: the client keeps its own
                }
            }
            for (OreEntry ore : OreEntry.values()) {
                ConfigSC.OreGenSettings s = ConfigSC.settingsFor(ore);
                msg.ores.put(ore.ordinal(), new int[]{s.minY, s.maxY, s.veinSize, s.veinsPerChunk});
            }
            return msg;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
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
                values.put(name, v);
                types.put(name, type);
            }
            int o = buf.readUnsignedShort();
            for (int i = 0; i < o; i++) {
                int ordinal = buf.readUnsignedShort();
                ores.put(ordinal, new int[]{buf.readInt(), buf.readInt(), buf.readInt(), buf.readInt()});
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeShort(values.size());
            for (Map.Entry<String, Object> e : values.entrySet()) {
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
            buf.writeShort(ores.size());
            for (Map.Entry<Integer, int[]> e : ores.entrySet()) {
                buf.writeShort(e.getKey());
                for (int k = 0; k < 4; k++) {
                    buf.writeInt(e.getValue()[k]);
                }
            }
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            if (SCMod.proxy.playsOnRemoteServer()) {       // single player / LAN host: the fields are its server's already
                apply(msg);
            }
            return null;
        }
    }

    public static class Events {
        /** Server: a player joined - the server's numbers to them. */
        @SubscribeEvent
        public void onLogin(PlayerEvent.PlayerLoggedInEvent event) {
            if (event.player instanceof EntityPlayerMP) {
                CHANNEL.sendTo(Message.ofServer(), (EntityPlayerMP) event.player);
            }
        }

        /** Client: left the server - its own numbers back. */
        @SubscribeEvent
        public void onDisconnect(FMLNetworkEvent.ClientDisconnectionFromServerEvent event) {
            restoreLocal();
        }
    }
}
