package com.sc.finale.client;

import com.sc.finale.FinaleNetSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.nbt.NBTTagCompound;

/**
 * Подложка Ф5 (docs/plan-substrate-tech-f5.md §2.5): the client's side of FinaleNetSC - each kind to its package's client class
 * (SIGNAL_STATE - package J's lore.client.SignalClientSC, BOSS_FX - package B's mob.client.BossClientSC), and the journals' screen
 * (ClientProxy.openJournal - package J's lore.client.JournalClientSC). Any thread (the handlers keep to volatile fields / their own
 * queues). Shared core: only the coordinator edits this file.
 */
@SideOnly(Side.CLIENT)
public final class FinaleClientSC {

    private FinaleClientSC() {
    }

    /** ClientProxy.finaleMessage. */
    public static void onMessage(int kind, NBTTagCompound data) {
        switch (kind) {
            case FinaleNetSC.SIGNAL_STATE:
                com.sc.lore.client.SignalClientSC.onState(data);
                break;
            case FinaleNetSC.BOSS_FX:
                com.sc.mob.client.BossClientSC.onFx(data);
                break;
            default:
                break;                                          // a kind of a newer server: skipped
        }
    }
}
