package com.sc.finale;

import com.sc.SCMod;
import com.sc.settle.SettleNetSC;

import cpw.mods.fml.common.network.simpleimpl.IMessage;
import cpw.mods.fml.common.network.simpleimpl.IMessageHandler;
import cpw.mods.fml.common.network.simpleimpl.MessageContext;
import cpw.mods.fml.common.network.simpleimpl.SimpleNetworkWrapper;
import cpw.mods.fml.relauncher.Side;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §2.5): the finale's messages on the Substrate's channel "SiliconAgeDim"
 * (SubstrateNetSC.CHANNEL; 0 - its ids / rules, 1 and 2 - the settlements', 3 - the weather's): DISC_TO_CLIENT 4, a kind and an NBT
 * compound, as SettleNetSC's (the package that owns a kind owns its contents). Kinds (fixed, append only), handled by
 * SCMod.proxy.finaleMessage (ClientProxy -> finale.client.FinaleClientSC, which hands each to its package's client class):
 * <ul>
 * <li>SIGNAL_STATE (package J) {on, x, z, y, t: ticks left} - the Lithographers' Signal began / ended: to everyone in the Substrate,
 * and to a player arriving or joining there while it is on (the beam on the horizon - lore.client.SignalClientSC);</li>
 * <li>BOSS_FX (package B) {k: BossClientSC.FX_*, x, y, z} - a one-off effect of the Breakdown Warden's fight to the players within
 * 96 of it (mob.client.BossClientSC.onFx).</li>
 * </ul>
 * Never sent to a FakePlayer. No message to the server: the journals open on the client alone, every other action is a click the
 * server sees. Shared core: only the coordinator edits this file.
 */
public final class FinaleNetSC {

    public static final int DISC_TO_CLIENT = 4;
    public static final int SIGNAL_STATE = 1, BOSS_FX = 2;

    private static SimpleNetworkWrapper channel;

    private FinaleNetSC() {
    }

    /** SubstrateNetSC.init: on its channel (preInit, both sides). */
    public static void register(SimpleNetworkWrapper ch) {
        channel = ch;
        ch.registerMessage(ToClientHandler.class, ToClient.class, DISC_TO_CLIENT, Side.CLIENT);
    }

    /** Server -> `p` (nothing for a FakePlayer or a player without a connection). */
    public static void send(EntityPlayerMP p, int kind, NBTTagCompound data) {
        if (channel == null || p == null || p instanceof FakePlayer || p.playerNetServerHandler == null) {
            return;
        }
        channel.sendTo(new ToClient(kind, data), p);
    }

    /** Server -> every real player in world `w`. */
    public static void sendToWorld(World w, int kind, NBTTagCompound data) {
        if (w == null || w.isRemote) {
            return;
        }
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayerMP) {
                send((EntityPlayerMP) o, kind, data);
            }
        }
    }

    /** Server -> every real player in `w` within `radius` of (x, y, z). */
    public static void sendAround(World w, double x, double y, double z, double radius, int kind, NBTTagCompound data) {
        if (w == null || w.isRemote) {
            return;
        }
        for (Object o : w.playerEntities) {
            if (o instanceof EntityPlayerMP && ((EntityPlayer) o).getDistanceSq(x, y, z) <= radius * radius) {
                send((EntityPlayerMP) o, kind, data);
            }
        }
    }

    public static class ToClient extends SettleNetSC.Payload {
        public ToClient() {
        }

        public ToClient(int kind, NBTTagCompound data) {
            super(kind, data);
        }
    }

    public static class ToClientHandler implements IMessageHandler<ToClient, IMessage> {
        @Override
        public IMessage onMessage(ToClient msg, MessageContext ctx) {
            SCMod.proxy.finaleMessage(msg.kind, msg.data);
            return null;
        }
    }
}
