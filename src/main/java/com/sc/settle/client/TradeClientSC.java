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
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §7.2, package R - this file is R's in Ф6): FLIGHT_STATE and FLIGHT_RESULT of a Dispatcher's
 * «Рейсы» tab, kept the same way (their own fields and counters; both carry "tuner" too).
 * <p>
 * «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §8, package T): AIR_STATE and AIR_RESULT of a Dispatcher's «Лётное
 * поле» tab (only at an embassy with a field), kept the same way again (their own fields and counters; both carry "tuner").
 */
@SideOnly(Side.CLIENT)
public final class TradeClientSC {

    private static volatile NBTTagCompound state, result, flightState, flightResult, airState, airResult;
    private static volatile int stateSeq, resultSeq, flightStateSeq, flightResultSeq, airStateSeq, airResultSeq;

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
        } else if (kind == SettleNetSC.FLIGHT_STATE) {
            flightState = data;
            flightStateSeq++;
        } else if (kind == SettleNetSC.FLIGHT_RESULT) {
            flightResult = data;
            flightResultSeq++;
        } else if (kind == SettleNetSC.AIR_STATE) {
            airState = data;
            airStateSeq++;
        } else if (kind == SettleNetSC.AIR_RESULT) {
            airResult = data;
            airResultSeq++;
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

    /** Ф6: the last FLIGHT_STATE (null none yet). */
    public static NBTTagCompound flightState() {
        return flightState;
    }

    public static int flightStateSeq() {
        return flightStateSeq;
    }

    /** Ф6: the last FLIGHT_RESULT (null none yet). */
    public static NBTTagCompound flightResult() {
        return flightResult;
    }

    public static int flightResultSeq() {
        return flightResultSeq;
    }

    /** «Лётное поле»: the last AIR_STATE (null none yet). */
    public static NBTTagCompound airState() {
        return airState;
    }

    public static int airStateSeq() {
        return airStateSeq;
    }

    /** «Лётное поле»: the last AIR_RESULT (null none yet). */
    public static NBTTagCompound airResult() {
        return airResult;
    }

    public static int airResultSeq() {
        return airResultSeq;
    }

    /** The screen of Tuner `tuner` closed: what it showed is not for the next one (a newer screen's data stays). */
    public static void forget(int tuner) {
        NBTTagCompound s = state, r = result, fs = flightState, fr = flightResult, as = airState, ar = airResult;
        if (s != null && s.getInteger("tuner") == tuner) {
            state = null;
        }
        if (r != null && r.getInteger("tuner") == tuner) {
            result = null;
        }
        if (fs != null && fs.getInteger("tuner") == tuner) {
            flightState = null;
        }
        if (fr != null && fr.getInteger("tuner") == tuner) {
            flightResult = null;
        }
        if (as != null && as.getInteger("tuner") == tuner) {
            airState = null;
        }
        if (ar != null && ar.getInteger("tuner") == tuner) {
            airResult = null;
        }
    }
}
