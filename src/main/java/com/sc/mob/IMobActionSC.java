package com.sc.mob;

/**
 * Подложка Ф3 (docs/plan-substrate-tech-f3.md §2.4, rule ПС-9 «предупреждение»): what one of our creatures is winding up
 * right now - the telegraph every client sees and draws (a beam's aim, the blue ПЭ-1 barrel, a wind-up's raised blade, the
 * Trooper's dash cue, a Shell's reboot, a mimic asleep). Kept in a DataWatcher byte: EntityArmoredMobSC and
 * EntitySubstrateMobSC slot 25 (DW_ACTION), EntityWardenSC slot 27. The server sets it when a wind-up starts and back to
 * ACT_NONE when it ends or breaks off; the DataWatcher sends only the changes. Values fixed - append only.
 * Shared core: only the coordinator edits this file.
 */
public interface IMobActionSC {

    /** Nothing / a beam gun's visible aim / a heavy blow's or «Рассечение»'s wind-up / the Trooper's 0.5 s dash cue / a ПЭ-1 Point
     * charging (the barrel turns blue) / a ПЭ-1 Pulse's 3 s hum / a Hollow Shell's reboot / after a dash (its back open) / a mimic
     * asleep as a machine / a Commander's order / a Repair Drone's beam on its Shell / a sentry drone's searchlight. */
    int ACT_NONE = 0, ACT_AIM = 1, ACT_WINDUP = 2, ACT_DASH = 3, ACT_EMP_POINT = 4, ACT_EMP_PULSE = 5, ACT_REBOOT = 6, ACT_RECOVER = 7,
            ACT_DORMANT = 8, ACT_ORDER = 9, ACT_REPAIR = 10, ACT_SEARCHLIGHT = 11;

    /** Both sides (the DataWatcher). */
    int mobAction();

    /** Server. */
    void setMobAction(int action);
}
