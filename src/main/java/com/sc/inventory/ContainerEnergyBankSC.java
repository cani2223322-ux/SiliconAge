package com.sc.inventory;

import com.sc.energy.EnergyBankMathSC;
import com.sc.energy.Tier;
import com.sc.tileentity.TileEntityEnergyBankSC;

import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.InventoryPlayer;
import net.minecraft.inventory.Container;

/**
 * ОМ-7 (docs/plan-ideas-2026-10-26.md): the Energy Bank controller's screen, server side - no slots; the charge, the capacity (two longs
 * as four ints), the structure's state and the HISTORY samples of the inflow / outflow go by IntSyncSC: 0-1 the energy, 2-3 the
 * capacity, 4 the side, 5-6 the cells, 7-8 the ports IN / OUT, 9-10 the flow now, 11..70 the inflow, 71..130 the outflow (0 - the last
 * second), then the reason not formed and its block (131..134), the ports per tier (135..142: IN | OUT << 16).
 */
public class ContainerEnergyBankSC extends Container {

    private static final int H = EnergyBankMathSC.HISTORY, IN0 = 11, OUT0 = IN0 + H, BAD0 = OUT0 + H, TIER0 = BAD0 + 4,
            COUNT = TIER0 + Tier.values().length;

    private final TileEntityEnergyBankSC bank;
    private final IntSyncSC sync = new IntSyncSC(COUNT);

    public ContainerEnergyBankSC(InventoryPlayer playerInv, TileEntityEnergyBankSC bank) {
        this.bank = bank;
    }

    public TileEntityEnergyBankSC bank() {
        return bank;
    }

    @Override
    public boolean canInteractWith(EntityPlayer player) {
        return bank != null && !bank.isInvalid() && player.getDistanceSq(bank.xCoord + 0.5D, bank.yCoord + 0.5D, bank.zCoord + 0.5D) <= 64D;
    }

    @Override
    public void detectAndSendChanges() {
        super.detectAndSendChanges();
        int[] v = new int[COUNT];
        long eu = bank.energy(), cap = bank.capacity();
        v[0] = (int) eu;
        v[1] = (int) (eu >>> 32);
        v[2] = (int) cap;
        v[3] = (int) (cap >>> 32);
        v[4] = bank.size();
        v[5] = bank.cellsSmall();
        v[6] = bank.cellsBig();
        v[7] = bank.portsIn();
        v[8] = bank.portsOut();
        v[9] = bank.nowIn();
        v[10] = bank.nowOut();
        for (int i = 0; i < H; i++) {
            v[IN0 + i] = bank.histIn(i);
            v[OUT0 + i] = bank.histOut(i);
        }
        int[] at = bank.badAt();
        v[BAD0] = bank.badReason();
        v[BAD0 + 1] = at[0];
        v[BAD0 + 2] = at[1];
        v[BAD0 + 3] = at[2];
        for (int t = 0; t < Tier.values().length; t++) {
            v[TIER0 + t] = bank.portTier(t);
        }
        sync.send(this, crafters, v);
    }

    @Override
    public void updateProgressBar(int property, int half) {
        int id = sync.receive(property, half);
        if (id < 0) {
            return;
        }
        if (id >= IN0 && id < BAD0) {
            int ago = id < OUT0 ? id - IN0 : id - OUT0;
            bank.setHistoryClient(ago, sync.value(IN0 + ago), sync.value(OUT0 + ago));
        } else if (id >= TIER0) {
            bank.setPortTierClient(id - TIER0, sync.value(id));
        } else {
            long eu = (sync.value(0) & 0xFFFFFFFFL) | (long) sync.value(1) << 32;
            long cap = (sync.value(2) & 0xFFFFFFFFL) | (long) sync.value(3) << 32;
            bank.setClient(eu, cap, sync.value(4), sync.value(5), sync.value(6), sync.value(7), sync.value(8), sync.value(9), sync.value(10),
                    sync.value(BAD0), sync.value(BAD0 + 1), sync.value(BAD0 + 2), sync.value(BAD0 + 3));
        }
    }
}
