package com.sc.embassy.client;

import com.sc.embassy.EmbassyNetSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Подложка Ф6 (docs/plan-substrate-tech-f6.md §2.7): the client side of EmbassyNetSC.ToClient (ClientProxy.embassyMessage hands every
 * kind here): BEAMS to EmbassyBeamClientSC (package S), FLIGHT_FX to RingClientSC (package R). May be called off the client thread -
 * the packages' classes only queue it. Client only. Shared core: only the coordinator edits this file.
 */
@SideOnly(Side.CLIENT)
public final class EmbassyClientSC {

    private EmbassyClientSC() {
    }

    public static void onMessage(int kind, NBTTagCompound data) {
        if (data == null) {
            return;
        }
        if (kind == EmbassyNetSC.BEAMS) {
            EmbassyBeamClientSC.onBeams(data);
        } else if (kind == EmbassyNetSC.FLIGHT_FX) {
            RingClientSC.onFx(data);
        }
    }
}
