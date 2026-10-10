package com.sc.emp;

import net.minecraft.nbt.NBTTagCompound;

/**
 * One player's ПЭ-1 state (docs/plan-pe1.md §2-§4, §7): every time is an absolute world tick
 * (World.getTotalWorldTime - the same in every dimension, and synced to clients), 0 = none.
 * The server keeps it in the player's entity data (EmpSC.NBT): it survives a logout (no escaping a
 * suppression by relogging) and is gone after death (Forge doesn't copy entity data to the respawned player).
 * The client keeps a copy per entity id (EmpSC.clientState), filled by EmpNetSC.StateMessage.
 */
public final class EmpStateSC {

    /** Suppressed until, per armour slot (0 helmet .. 3 boots). */
    public final long[] until = new long[4];
    /**
     * Immune until, per slot: set together with `until` (= until + immunity), so the immunity follows the
     * suppression's end with no tick watching it; a reboot moves both. Immune = until <= now < immuneUntil.
     */
    public final long[] immuneUntil = new long[4];
    /** The helium boil: multiplier (2 / 3, 0 none) and its end. */
    public int boilMul;
    public long boilUntil;
    /** The siphon stream on this player is running until (refreshed every stream second): boil at the stream rate, "Энергия утекает!". */
    public long streamUntil;
    /** Entity id of whoever drains this player (the HUD arrow). */
    public int drainBy;
    /** The last Point / Pulse heat hit (diminishing heat window). */
    public long lastHeatHit;
    /** «ЭМИ-наведение» (Sensor chip): aimed at until, by entity id. */
    public long aimedUntil;
    public int aimedBy;
    /** Emergency reboot: usable again at. */
    public long rebootReadyAt;

    public boolean suppressed(int piece, long now) {
        return piece >= 0 && piece < 4 && until[piece] > now;
    }

    public boolean immune(int piece, long now) {
        return piece >= 0 && piece < 4 && immuneUntil[piece] > now && until[piece] <= now;
    }

    public boolean anySuppressed(long now) {
        for (int i = 0; i < 4; i++) {
            if (until[i] > now) {
                return true;
            }
        }
        return false;
    }

    public boolean anyImmune(long now) {
        for (int i = 0; i < 4; i++) {
            if (immuneUntil[i] > now && until[i] <= now) {
                return true;
            }
        }
        return false;
    }

    public boolean streaming(long now) {
        return streamUntil > now;
    }

    /** The boil multiplier now (1 = none). */
    public int boilMulAt(long now) {
        return boilUntil > now && boilMul > 1 ? boilMul : 1;
    }

    /** Nothing left to remember or show. */
    public boolean idle(long now) {
        return !anySuppressed(now) && !anyImmune(now) && boilUntil <= now && streamUntil <= now && aimedUntil <= now
                && rebootReadyAt <= now && (lastHeatHit == 0 || now - lastHeatHit >= EmpMathSC.DIMINISH_WINDOW);   // 0 = never hit
    }

    public NBTTagCompound write() {
        NBTTagCompound t = new NBTTagCompound();
        for (int i = 0; i < 4; i++) {
            t.setLong("u" + i, until[i]);
            t.setLong("i" + i, immuneUntil[i]);
        }
        t.setInteger("bm", boilMul);
        t.setLong("bu", boilUntil);
        t.setLong("su", streamUntil);
        t.setInteger("db", drainBy);
        t.setLong("lh", lastHeatHit);
        t.setLong("au", aimedUntil);
        t.setInteger("ab", aimedBy);
        t.setLong("rr", rebootReadyAt);
        return t;
    }

    public static EmpStateSC read(NBTTagCompound t) {
        EmpStateSC s = new EmpStateSC();
        if (t == null) {
            return s;
        }
        for (int i = 0; i < 4; i++) {
            s.until[i] = t.getLong("u" + i);
            s.immuneUntil[i] = t.getLong("i" + i);
        }
        s.boilMul = t.getInteger("bm");
        s.boilUntil = t.getLong("bu");
        s.streamUntil = t.getLong("su");
        s.drainBy = t.getInteger("db");
        s.lastHeatHit = t.getLong("lh");
        s.aimedUntil = t.getLong("au");
        s.aimedBy = t.getInteger("ab");
        s.rebootReadyAt = t.getLong("rr");
        return s;
    }

    /** A copy (the client mirror is replaced whole, never edited in place by the network thread). */
    public EmpStateSC copy() {
        return read(write());
    }
}
