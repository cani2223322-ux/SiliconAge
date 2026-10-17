package com.sc.emp;

/**
 * Подложка Ф4 (docs/plan-substrate-tech-f4.md §7.3; proposal §8 «ПЭ-1 бесполезен — урок, что он работает не на всё»): a
 * marker - the ПЭ-1 does nothing to this creature, not even the old Slowness (the Quartzite Colossus). Package N adds the one
 * branch to EmpGunLogicSC.fire (before the Slowness: the EU spent, no effect, the shooter hears sc.emp.hit.useless); the
 * Singular helmet's analyzer shows SingularSenseData.Analysis.EMP_USELESS and WAILA a line (MobAnalysisSC, WailaEntitySC -
 * package N). Shared core: only the coordinator edits this file.
 */
public interface IEmpUselessSC {
}
