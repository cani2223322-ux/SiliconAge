package com.sc.settle.client;

import com.sc.settle.SettleNetSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;

/**
 * PACKAGE T (docs/plan-substrate-tech-f2.md §9.7): the client side of SettleNetSC.ToClient (ClientProxy.settleMessage
 * hands every kind here): TRADE_STATE and TRADE_RESULT for the open GuiTradeSC. May be called off the client thread -
 * keep the data in a volatile field, the screen takes it in its own tick. Client only.
 * <p>
 * Each message bumps its counter after the field is written, so a screen that remembers the counter it last read
 * picks up every new one exactly once (and one that arrived before the screen opened - the state follows the open
 * window's packet - is there when it looks first). A message for another Tuner (a stale one) is the screen's to skip:
 * both carry "tuner", the entity id.
 */
@SideOnly(Side.CLIENT)
public final class TradeClientSC {

    private static volatile NBTTagCompound state, result;
    private static volatile int stateSeq, resultSeq;

    private TradeClientSC() {
    }

    public static void onMessage(int kind, NBTTagCompound data) {
        if (data == null) {
            return;
        }
        if (kind == SettleNetSC.TRADE_STATE) {
            state = data;
            stateSeq++;
        } else if (kind == SettleNetSC.TRADE_RESULT) {
            result = data;
            resultSeq++;
        }
    }

    /** The last TRADE_STATE (null none yet). */
    public static NBTTagCompound state() {
        return state;
    }

    public static int stateSeq() {
        return stateSeq;
    }

    /** The last TRADE_RESULT (null none yet). */
    public static NBTTagCompound result() {
        return result;
    }

    public static int resultSeq() {
        return resultSeq;
    }

    /** The screen of Tuner `tuner` closed: what it showed is not for the next one (a newer screen's data stays). */
    public static void forget(int tuner) {
        NBTTagCompound s = state, r = result;
        if (s != null && s.getInteger("tuner") == tuner) {
            state = null;
        }
        if (r != null && r.getInteger("tuner") == tuner) {
            result = null;
        }
    }
}
