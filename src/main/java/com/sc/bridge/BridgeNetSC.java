package com.sc.bridge;

import com.sc.tileentity.TileEntityBridgeControllerSC;

import cpw.mods.fml.common.network.ByteBufUtils;
import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.tileentity.TileEntity;

/**
 * The Bridge Controller's screen talks to the server here: client -> server an action (with the target's
 * numbers or a bookmark's name), server -> client the controller's state (an NBT the screen reads, sent as
 * an answer to the screen's request twice a second). The server only listens to a player near the controller.
 */
public final class BridgeNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeBridge");
    /** The screen's state request. */
    public static final int A_REQUEST = 1;
    /** How near (blocks) the player must be. */
    public static final int REACH = 24;

    private BridgeNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(ActionHandler.class, Action.class, 0, Side.SERVER);
        CHANNEL.registerMessage(StateHandler.class, State.class, 1, Side.CLIENT);
        CHANNEL.registerMessage(FarHandler.class, Far.class, 2, Side.SERVER);
        CHANNEL.registerMessage(FarStateHandler.class, FarState.class, 3, Side.CLIENT);
        CHANNEL.registerMessage(HudHandler.class, Hud.class, 4, Side.CLIENT);
        CHANNEL.registerMessage(BirthHandler.class, Birth.class, 5, Side.CLIENT);
        CHANNEL.registerMessage(SoftLandHandler.class, SoftLand.class, 6, Side.CLIENT);
        CHANNEL.registerMessage(CollapseHandler.class, Collapse.class, 7, Side.CLIENT);
        CHANNEL.registerMessage(ArriveHandler.class, Arrive.class, 8, Side.CLIENT);
    }

    /** A remote / the armour / a coordinator: one command (BridgeFarSC.F_*), no distance limit - the server checks the link. */
    public static void sendFar(int src, int slot, int action, int[] values, String text) {
        CHANNEL.sendToServer(new Far(src, slot, action, values, text));
    }

    public static void send(int x, int y, int z, int action, int[] values, String text) {
        CHANNEL.sendToServer(new Action(x, y, z, action, values, text));
    }

    public static class Action implements IMessage {
        public int x, y, z, action;
        public int[] values = new int[0];
        public String text = "";

        public Action() {
        }

        public Action(int x, int y, int z, int action, int[] values, String text) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.action = action;
            this.values = values == null ? new int[0] : values;
            this.text = text == null ? "" : text;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            action = buf.readInt();
            int n = Math.min(16, buf.readByte() & 0xFF);
            values = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = buf.readInt();
            }
            text = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(action);
            buf.writeByte(Math.min(16, values.length));
            for (int i = 0; i < values.length && i < 16; i++) {
                buf.writeInt(values[i]);
            }
            ByteBufUtils.writeUTF8String(buf, text.length() > 64 ? text.substring(0, 64) : text);
        }
    }

    public static class ActionHandler implements IMessageHandler<Action, IMessage> {
        @Override
        public IMessage onMessage(Action msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (!p.worldObj.blockExists(msg.x, msg.y, msg.z)
                    || p.getDistanceSq(msg.x + 0.5, msg.y + 0.5, msg.z + 0.5) > REACH * REACH) {
                return null;
            }
            TileEntity te = p.worldObj.getTileEntity(msg.x, msg.y, msg.z);
            if (!(te instanceof TileEntityBridgeControllerSC)) {
                return null;
            }
            TileEntityBridgeControllerSC c = (TileEntityBridgeControllerSC) te;
            if (msg.action != A_REQUEST) {
                c.action(p, msg.action, msg.values, msg.text);
            }
            return new State(msg.x, msg.y, msg.z, c.writeState(p));
        }
    }

    public static class State implements IMessage {
        public int x, y, z;
        public NBTTagCompound state;

        public State() {
        }

        public State(int x, int y, int z, NBTTagCompound state) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.state = state;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            state = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            ByteBufUtils.writeTag(buf, state);
        }
    }

    public static class StateHandler implements IMessageHandler<State, IMessage> {
        @Override
        public IMessage onMessage(State msg, MessageContext ctx) {
            com.sc.SCMod.proxy.bridgeState(msg.x, msg.y, msg.z, msg.state);
            return null;
        }
    }

    // ------------------------------------------------------------------ stage 2: remotes, armour, the HUD

    /** Client -> server: a remote in the hand (src 0), the helmet's link `slot` (src 1) or the held coordinator (src 2). */
    public static class Far implements IMessage {
        public int src, slot, action;
        public int[] values = new int[0];
        public String text = "";

        public Far() {
        }

        public Far(int src, int slot, int action, int[] values, String text) {
            this.src = src;
            this.slot = slot;
            this.action = action;
            this.values = values == null ? new int[0] : values;
            this.text = text == null ? "" : text;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            src = buf.readByte();
            slot = buf.readByte();
            action = buf.readByte();
            int n = Math.min(16, buf.readByte() & 0xFF);
            values = new int[n];
            for (int i = 0; i < n; i++) {
                values[i] = buf.readInt();
            }
            text = ByteBufUtils.readUTF8String(buf);
            if (text.length() > 80) {
                text = text.substring(0, 80);
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(src);
            buf.writeByte(slot);
            buf.writeByte(action);
            buf.writeByte(Math.min(16, values.length));
            for (int i = 0; i < values.length && i < 16; i++) {
                buf.writeInt(values[i]);
            }
            ByteBufUtils.writeUTF8String(buf, text.length() > 80 ? text.substring(0, 80) : text);
        }
    }

    public static class FarHandler implements IMessageHandler<Far, IMessage> {
        @Override
        public IMessage onMessage(Far msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            NBTTagCompound out = BridgeFarSC.handle(p, msg.src, msg.slot, msg.action, msg.values, msg.text);
            return out == null ? null : new FarState(out);
        }
    }

    /** Server -> client: what a remote's screen / the «Мост» tab shows. */
    public static class FarState implements IMessage {
        public NBTTagCompound state;

        public FarState() {
        }

        public FarState(NBTTagCompound state) {
            this.state = state;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            state = ByteBufUtils.readTag(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            ByteBufUtils.writeTag(buf, state);
        }
    }

    public static class FarStateHandler implements IMessageHandler<FarState, IMessage> {
        @Override
        public IMessage onMessage(FarState msg, MessageContext ctx) {
            com.sc.SCMod.proxy.bridgeFarState(msg.state);
            return null;
        }
    }

    /**
     * Server -> client: the opener's HUD line (the bridge's name, time left / total, stability, kind; open false - gone);
     * stage 3: the ring's heat (%), the seconds to a fold for a shortage (-1 none) and what is short.
     */
    public static class Hud implements IMessage {
        public boolean open;
        public String name = "", shortWhat = "";
        public int left, total, stability, kind, heat, warn = -1;

        public Hud() {
        }

        public Hud(boolean open, String name, int left, int total, int stability, int kind, int heat, int warn, String shortWhat) {
            this.open = open;
            this.name = name == null ? "" : name;
            this.left = left;
            this.total = total;
            this.stability = stability;
            this.kind = kind;
            this.heat = heat;
            this.warn = warn;
            this.shortWhat = shortWhat == null ? "" : shortWhat;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            open = buf.readBoolean();
            name = ByteBufUtils.readUTF8String(buf);
            left = buf.readInt();
            total = buf.readInt();
            stability = buf.readByte();
            kind = buf.readByte();
            heat = buf.readByte();
            warn = buf.readByte();
            shortWhat = ByteBufUtils.readUTF8String(buf);
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeBoolean(open);
            ByteBufUtils.writeUTF8String(buf, name.length() > 32 ? name.substring(0, 32) : name);
            buf.writeInt(left);
            buf.writeInt(total);
            buf.writeByte(stability);
            buf.writeByte(kind);
            buf.writeByte(Math.max(0, Math.min(100, heat)));
            buf.writeByte(Math.max(-1, Math.min(100, warn)));
            ByteBufUtils.writeUTF8String(buf, shortWhat.length() > 32 ? shortWhat.substring(0, 32) : shortWhat);
        }
    }

    public static class HudHandler implements IMessageHandler<Hud, IMessage> {
        @Override
        public IMessage onMessage(Hud msg, MessageContext ctx) {
            BridgeHudDataSC.set(msg.open, msg.name, msg.left, msg.total, msg.stability, msg.kind);
            BridgeHudDataSC.setStage3(msg.heat, msg.warn, msg.shortWhat);
            return null;
        }
    }

    /** Server -> clients near an end: §11 the singularity is born here (a flash, an implosion, then the vortex). */
    public static class Birth implements IMessage {
        public double x, y, z;
        public int kind;

        public Birth() {
        }

        public Birth(double x, double y, double z, int kind) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
            kind = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeByte(kind);
        }
    }

    public static class BirthHandler implements IMessageHandler<Birth, IMessage> {
        @Override
        public IMessage onMessage(Birth msg, MessageContext ctx) {
            BridgeHudDataSC.birth(msg.x, msg.y, msg.z, msg.kind);
            return null;
        }
    }

    /** Server -> the player who came out of an end in the air: §7б the soft landing, `ticks` long (BridgeSoftLandSC). */
    public static class SoftLand implements IMessage {
        public int ticks;

        public SoftLand() {
        }

        public SoftLand(int ticks) {
            this.ticks = ticks;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            ticks = buf.readShort();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeShort(ticks);
        }
    }

    public static class SoftLandHandler implements IMessageHandler<SoftLand, IMessage> {
        @Override
        public IMessage onMessage(SoftLand msg, MessageContext ctx) {
            com.sc.SCMod.proxy.bridgeSoftLand(msg.ticks);
            return null;
        }
    }
    /** Server -> clients near an end that closes: ВП7 its vortex shrinks to a point and pops (drawn by the client alone). */
    public static class Collapse implements IMessage {
        public double x, y, z;
        public int kind, size, axis, stability;
        public boolean ringless;

        public Collapse() {
        }

        public Collapse(double x, double y, double z, int kind, int size, int axis, boolean ringless, int stability) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.kind = kind;
            this.size = size;
            this.axis = axis;
            this.ringless = ringless;
            this.stability = stability;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readDouble();
            y = buf.readDouble();
            z = buf.readDouble();
            kind = buf.readByte();
            size = buf.readByte();
            axis = buf.readByte();
            ringless = buf.readBoolean();
            stability = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeDouble(x);
            buf.writeDouble(y);
            buf.writeDouble(z);
            buf.writeByte(kind);
            buf.writeByte(size);
            buf.writeByte(axis);
            buf.writeBoolean(ringless);
            buf.writeByte(Math.max(0, Math.min(100, stability)));
        }
    }

    public static class CollapseHandler implements IMessageHandler<Collapse, IMessage> {
        @Override
        public IMessage onMessage(Collapse msg, MessageContext ctx) {
            com.sc.SCMod.proxy.bridgeCollapse(msg.x, msg.y, msg.z, msg.kind, msg.size, msg.axis, msg.ringless, msg.stability);
            return null;
        }
    }

    /** Server -> the player who just came through: ВП11 a flash at the screen's edges (green Ground / blue Space) and a trail. */
    public static class Arrive implements IMessage {
        public int kind;

        public Arrive() {
        }

        public Arrive(int kind) {
            this.kind = kind;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            kind = buf.readByte();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeByte(kind);
        }
    }

    public static class ArriveHandler implements IMessageHandler<Arrive, IMessage> {
        @Override
        public IMessage onMessage(Arrive msg, MessageContext ctx) {
            com.sc.SCMod.proxy.bridgeArrive(msg.kind);
            return null;
        }
    }
}
