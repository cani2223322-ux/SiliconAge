package com.sc.inventory;

import java.util.List;

import net.minecraft.inventory.Container;
import net.minecraft.inventory.ICrafting;

/**
 * Full 32-bit GUI field sync over vanilla's window-property packet. S31PacketWindowProperty
 * carries its value as a short, so anything above 32767 arrived wrapped: an HV machine's
 * 51200 EU buffer read as -14336, EV buffers and the Fusion Reactor's 1,000,000 EU ignition
 * charge were garbage. Each value now travels as two properties - low 16 bits on id*2, high
 * 16 bits on id*2+1, always sent together and in that order - and receive() hands back the
 * reassembled value once the high half lands.
 */
final class IntSyncSC {

    private final int[] last;
    private final int[] lowHalf;
    private boolean everSent;

    IntSyncSC(int count) {
        last = new int[count];
        lowHalf = new int[count];
    }

    int count() {
        return last.length;
    }

    /** Sends every value that changed since the last call (everything on the first call). */
    void send(Container container, List<?> crafters, int[] values) {
        for (int i = 0; i < last.length; i++) {
            int value = values[i];
            if (everSent && value == last[i]) {
                continue;
            }
            for (Object o : crafters) {
                ICrafting crafting = (ICrafting) o;
                crafting.sendProgressBarUpdate(container, i * 2, value & 0xFFFF);
                crafting.sendProgressBarUpdate(container, i * 2 + 1, value >>> 16);
            }
            last[i] = value;
        }
        everSent = true;
    }

    /** @return the value index whose full value is now `value()`, or -1 while only its low half has arrived. */
    int receive(int id, int data) {
        int index = id / 2;
        if (index < 0 || index >= last.length) {
            return -1;
        }
        if (id % 2 == 0) {
            lowHalf[index] = data & 0xFFFF;
            return -1;
        }
        last[index] = ((data & 0xFFFF) << 16) | lowHalf[index];
        return index;
    }

    int value(int index) {
        return last[index];
    }
}
