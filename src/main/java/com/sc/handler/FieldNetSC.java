package com.sc.handler;

import com.sc.tileentity.TileEntityFieldGeneratorSC;

import cpw.mods.fml.common.network.ByteBufUtils;
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
 * Client -> server: the field generator's access list (a player name to add or remove), the Zone
 * tab's whole shape (ZONE: range, height, offsets, anchor, shape, a set point) and its colours
 * (RGB: target, 0xRRGGBB) - the screen's buttons only carry a number
 * (ContainerFieldGeneratorSC.enchantItem). The server checks that the player stands at the master
 * and owns it (or the field has no owner yet) for the list, is allowed on the field for the rest.
 */
public final class FieldNetSC {

    public static final SimpleNetworkWrapper CHANNEL = NetworkRegistry.INSTANCE.newSimpleChannel("SiliconAgeField");

    public static final byte ADD = 0, REMOVE = 1, ZONE = 2, RGB = 3;

    private FieldNetSC() {
    }

    public static void init() {
        CHANNEL.registerMessage(Handler.class, Message.class, 0, Side.SERVER);
    }

    public static class Message implements IMessage {
        public int x, y, z;
        public byte action;
        public String name = "";
        public int[] data = new int[0];

        public Message() {
        }

        public Message(TileEntityFieldGeneratorSC te, byte action, int... data) {
            this(te, action, "");
            this.data = data;
        }

        public Message(TileEntityFieldGeneratorSC te, byte action, String name) {
            this.x = te.xCoord;
            this.y = te.yCoord;
            this.z = te.zCoord;
            this.action = action;
            this.name = name == null ? "" : name;
        }

        @Override
        public void fromBytes(ByteBuf buf) {
            x = buf.readInt();
            y = buf.readInt();
            z = buf.readInt();
            action = buf.readByte();
            name = ByteBufUtils.readUTF8String(buf);
            int n = Math.min(16, buf.readUnsignedByte());
            data = new int[n];
            for (int i = 0; i < n; i++) {
                data[i] = buf.readInt();
            }
        }

        @Override
        public void toBytes(ByteBuf buf) {
            buf.writeInt(x);
            buf.writeInt(y);
            buf.writeInt(z);
            buf.writeByte(action);
            ByteBufUtils.writeUTF8String(buf, name.length() > 32 ? name.substring(0, 32) : name);
            int n = Math.min(16, data.length);
            buf.writeByte(n);
            for (int i = 0; i < n; i++) {
                buf.writeInt(data[i]);
            }
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
            if (!(te instanceof TileEntityFieldGeneratorSC)) {
                return null;
            }
            TileEntityFieldGeneratorSC f = (TileEntityFieldGeneratorSC) te;
            if (!f.isMaster() || !f.isUseableByPlayer(p)) {
                return null;
            }
            if (msg.action == ZONE || msg.action == RGB) {
                if (!f.allowed(p)) {
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.field.noaccess", f.getOwner()));
                } else if (msg.action == ZONE && msg.data.length >= 10) {
                    int[] d = msg.data;
                    if (!f.setZone(d[0], d[1], d[2], d[3], d[4], d[5], d[6], d[7], d[8], d[9])) {
                        p.addChatComponentMessage(new ChatComponentTranslation("sc.field.zone.toofar",
                                TileEntityFieldGeneratorSC.MAX_LINK_DISTANCE));
                    }
                } else if (msg.action == RGB && msg.data.length >= 2) {
                    f.setRgb(msg.data[0], msg.data[1]);
                }
                return null;
            }
            if (!f.getOwner().isEmpty() && !f.isOwner(p)) {
                p.addChatComponentMessage(new ChatComponentTranslation("sc.field.owneronly", f.getOwner()));
                return null;
            }
            if (msg.action == ADD) {
                if (!f.addAccess(msg.name)) {
                    p.addChatComponentMessage(new ChatComponentTranslation("sc.field.access.cant"));
                }
            } else if (msg.action == REMOVE) {
                f.removeAccess(msg.name);
            }
            return null;
        }
    }
}
