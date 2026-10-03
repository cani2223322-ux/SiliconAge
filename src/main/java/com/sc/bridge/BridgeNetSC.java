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
}
