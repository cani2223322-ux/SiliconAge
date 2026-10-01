package com.sc.handler;

import com.sc.tileentity.TileEntityQuarrySC;

import cpw.mods.fml.common.network.NetworkRegistry;
import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import io.netty.buffer.ByteBuf;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;

/**
 * Client -> server: a quarry screen's button or slider (an action and a value - colours and
 * sizes don't fit the vanilla button packet's one byte). The server checks the player stands at
 * the quarry and owns it.
 */
public final class QuarryNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeQuarry");

    private QuarryNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.SERVER);
    }

    public static void send(TileEntityQuarrySC te, int action, int value) {
        CHANNEL.sendToServer(new Message(te.xCoord, te.yCoord, te.zCoord, action, value));
    }

    public static class Message implements IMessage {
        public int x, y, z, action, value;

        public Message() {
        }

        public Message(int x, int y, int z, int action, int value) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.action = action;
            this.value = value;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            action = buf.readInt();
            value = buf.readInt();
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeInt(action);
            buf.writeInt(value);
        }
    }

    public static class Handler implements IMessageHandler<Message, IMessage> {
        @Override
        public IMessage onMessage(Message msg, MessageContext ctx) {
            EntityPlayerMP p = ctx.getServerHandler().playerEntity;
            if (!p.worldObj.blockExists(msg.x, msg.y, msg.z)) {
                return null;
            }
            TileEntity te = p.worldObj.getTileEntity(msg.x, msg.y, msg.z);
            if (!(te instanceof TileEntityQuarrySC)) {
                return null;
            }
            TileEntityQuarrySC q = (TileEntityQuarrySC) te;
            if (!q.isUseableByPlayer(p)) {
                return null;
            }
            // every message comes from the screen: the player must have this very quarry's screen open
            if (!(p.openContainer instanceof com.sc.inventory.ContainerQuarrySC)
                    || ((com.sc.inventory.ContainerQuarrySC) p.openContainer).getQuarry() != q) {
                return null;
            }
            if (!q.allowed(p)) {
                p.addChatComponentMessage(new ChatComponentTranslation("sc.quarry.owneronly", q.getOwner()));
                return null;
            }
            q.action(p, msg.action, msg.value);
            return null;
        }
    }
}
