package com.sc.settle;

/**
 * Подложка Ф2 (docs/plan-substrate-tech-f2.md §2.4): a settlement's own creature - a Tuner (ISettlementNpcSC) or a guard
 * (ISettlementGuardSC). It knows its settlement and slot (the registry's SettlementRegistrySC.Slot), kept in its NBT
 * under NBT {"id", "slot"}. An unbound one (a spawn egg's) has settlement -1: it trades at the base prices and belongs
 * to no dome. Shared core: only the coordinator edits this file.
 */
public interface ISettlementMemberSC {

    /** The entity NBT compound: {"id": settlement id, "slot": slot index, "core": {x, z} of its core}. */
    String NBT = "scSettle";

    /** The settlement's id, -1 unbound (server; a client answers -1). */
    int settlementId();

    /**
     * The core column {x, z} of the record it was bound to (bindTo), null unknown - unbound, or saved before it was kept
     * (server). An id met twice - a registry file lost and made again, a new settlement under an old number - is told
     * apart by it: a record whose core stands elsewhere is not this creature's (NpcLinkSC, the census).
     */
    int[] boundCore();

    /** The slot in the settlement's roster, -1 unbound (server). */
    int slot();

    /** The profession / rank. Both sides (the DataWatcher). */
    NpcKindSC kind();

    /** The name «Тантал-3» as NpcNamesSC.code(element, number); 0 none. Both sides (the DataWatcher). */
    int nameCode();

    /**
     * Server, before spawnEntityInWorld: becomes the slot's creature - its kind, name, home area and (a guard) its
     * equipment and reserve. `s` null: unbound (a spawn egg), `slot` ignored.
     */
    void bindTo(SettlementRegistrySC.Settlement s, int slot);
}
