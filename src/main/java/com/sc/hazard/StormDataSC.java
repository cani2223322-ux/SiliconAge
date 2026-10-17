package com.sc.hazard;

import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.world.World;
import net.minecraft.world.WorldSavedData;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §5.1, rule ПФ-1): the EMP storm's timers - DIM-1414/data/SCSi14Weather.dat (the
 * Substrate's perWorldStorage: its weather, not the bridge's other worlds'). World time throughout (getWorldTime: the
 * Overworld's clock, as SettlementSC.day), so a storm whose window passed while nobody was there is simply over. The fields
 * (fixed): the last game day rolled (StormRulesSC.rolls - never twice), the storm being or coming (its start and end; none:
 * end <= start), the next pulse, the pulses so far, a storm started by hand (the world test, an operator); Ф4 §5.5-§5.6: the
 * Silica Haze's day roll and window, the Singular Emission's week roll and window. Package S reads and writes them
 * (EmpStormSC); a data class of the coordinator's.
 */
public class StormDataSC extends WorldSavedData {

    public static final String NAME = "SCSi14Weather";

    /** The last game day whose roll was made (-1 none yet). */
    public long rolledDay = -1L;
    /** The storm: world time it starts (its warning StormRulesSC.WARN_TICKS before) and ends; none when end <= start. */
    public long startAt, endAt;
    /** World time of the next pulse while the storm runs. */
    public long nextPulseAt;
    /** Pulses of this storm so far. */
    public int pulses;
    /** Started by hand (a test, an operator), not by the day's roll. */
    public boolean forced;
    /** Ф4 §5.5: the Silica Haze - the last day rolled, its start and end (world time; none: end <= start). */
    public long hazeRolledDay = -1L, hazeStartAt, hazeEndAt;
    /** Ф4 §5.6: the Singular Emission - the last game week rolled (SettlementSC.week), its start and end. */
    public long emissionRolledWeek = -1L, emissionStartAt, emissionEndAt;

    public StormDataSC(String name) {
        super(name);
    }

    /** The storm data of world `w` (its own storage); null on a client or with no storage. */
    public static StormDataSC get(World w) {
        if (w == null || w.isRemote || w.perWorldStorage == null) {
            return null;
        }
        StormDataSC d = (StormDataSC) w.perWorldStorage.loadData(StormDataSC.class, NAME);
        if (d == null) {
            d = new StormDataSC(NAME);
            w.perWorldStorage.setData(NAME, d);
        }
        return d;
    }

    /** A storm is set (coming, running or just past). */
    public boolean hasStorm() {
        return endAt > startAt;
    }

    /** The phase at world time `now` (StormStateSC). */
    public int phase(long now) {
        return hasStorm() ? StormRulesSC.phaseAt(startAt, endAt, now) : StormStateSC.CALM;
    }

    /** The haze is on at world time `now`. */
    public boolean hazeAt(long now) {
        return hazeEndAt > hazeStartAt && now >= hazeStartAt && now < hazeEndAt;
    }

    /** The emission runs at world time `now`. */
    public boolean emissionAt(long now) {
        return emissionEndAt > emissionStartAt && now >= emissionStartAt && now < emissionEndAt;
    }

    /** No storm any more. */
    public void clearStorm() {
        startAt = 0L;
        endAt = 0L;
        nextPulseAt = 0L;
        pulses = 0;
        forced = false;
        markDirty();
    }

    @Override
    public void readFromNBT(NBTTagCompound nbt) {
        rolledDay = nbt.hasKey("Rolled") ? nbt.getLong("Rolled") : -1L;
        startAt = nbt.getLong("Start");
        endAt = nbt.getLong("End");
        nextPulseAt = nbt.getLong("Pulse");
        pulses = nbt.getInteger("Pulses");
        forced = nbt.getBoolean("Forced");
        hazeRolledDay = nbt.hasKey("HazeRolled") ? nbt.getLong("HazeRolled") : -1L;
        hazeStartAt = nbt.getLong("HazeStart");
        hazeEndAt = nbt.getLong("HazeEnd");
        emissionRolledWeek = nbt.hasKey("EmRolled") ? nbt.getLong("EmRolled") : -1L;
        emissionStartAt = nbt.getLong("EmStart");
        emissionEndAt = nbt.getLong("EmEnd");
    }

    @Override
    public void writeToNBT(NBTTagCompound nbt) {
        nbt.setLong("Rolled", rolledDay);
        nbt.setLong("Start", startAt);
        nbt.setLong("End", endAt);
        nbt.setLong("Pulse", nextPulseAt);
        nbt.setInteger("Pulses", pulses);
        nbt.setBoolean("Forced", forced);
        nbt.setLong("HazeRolled", hazeRolledDay);
        nbt.setLong("HazeStart", hazeStartAt);
        nbt.setLong("HazeEnd", hazeEndAt);
        nbt.setLong("EmRolled", emissionRolledWeek);
        nbt.setLong("EmStart", emissionStartAt);
        nbt.setLong("EmEnd", emissionEndAt);
    }
}
