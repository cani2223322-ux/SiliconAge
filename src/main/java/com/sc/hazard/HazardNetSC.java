package com.sc.hazard;

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
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §2.5): the world events' messages on the Substrate's channel "SiliconAgeDim"
 * (SubstrateNetSC.CHANNEL; 0 - its ids / rules, 1 and 2 - the settlements'): DISC_TO_CLIENT 3, a kind and an NBT compound,
 * as SettleNetSC's (the package that owns a kind owns its contents). Kinds (fixed, append only), all package S's, handled by
 * SCMod.proxy.hazardMessage (ClientProxy -> hazard.client.StormClientSC):
 * <ul>
 * <li>STORM_STATE {p: phase (StormStateSC), t: ticks until it ends, w: the warning's length} - on every phase change to everyone
 * in the Substrate, and to a player arriving there or joining in it;</li>
 * <li>STORM_PULSE {} - a pulse went off (the flash and the crackle; who it hit, the server already told by EmpSC / chat).</li>
 * </ul>
 * Never sent to a FakePlayer. Shared core: only the coordinator edits this file.
 */
public final class HazardNetSC {

    public static final int DISC_TO_CLIENT = 3;
    /** To a client. Ф4 §5.5-§5.6: HAZE_STATE {on, t: ticks left}, EMISSION_STATE {on, t} - the same moments as STORM_STATE. */
    public static final int STORM_STATE = 1, STORM_PULSE = 2, HAZE_STATE = 3, EMISSION_STATE = 4;

    private static SimpleNetworkWrapper channel;

    private HazardNetSC() {
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

    /** True for a player who can get a message (a real, connected one). */
    public static boolean reachable(EntityPlayer p) {
        return p instanceof EntityPlayerMP && !(p instanceof FakePlayer) && ((EntityPlayerMP) p).playerNetServerHandler != null;
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
            SCMod.proxy.hazardMessage(msg.kind, msg.data);
            return null;
        }
    }
}
