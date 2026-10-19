package com.sc.settle;

import com.sc.dim.SubstrateConfigSC;
import com.sc.dim.SubstrateSC;
import com.sc.settle.npc.EntityTunerSC;
import com.sc.settle.npc.EntityWardenSC;
import com.sc.settle.npc.NpcNamesSC;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityCreature;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;

/**
 * Подложка Ф2 «Поселения» (docs/plan-substrate-tech-f2.md): the shared numbers and the questions every package asks -
 * is this point a settlement's, what day is it, whose creature is this. The settlements themselves are in
 * SettlementRegistrySC; the packages' logic in their own classes (S - settle.gen, C - SettlementProtectSC and the core,
 * N - settle.npc, T - settle.trade, B - settle.travel). Server-side questions answer «no» on a client and outside the
 * Substrate (the world is recognised by its provider: SubstrateSC.is). Shared core: only the coordinator edits this
 * file.
 * <p>
 * Ф6 (docs/plan-substrate-tech-f6.md §0, rule ПП-14): the Tuners' Embassies are settlements of the same registry in the Overworld
 * (SettlementSizeSC.EMBASSY, record.dim 0). Everything that guarded «the Substrate only» for a settlement's protection, its core's
 * roster, its creatures' exile now asks governs(w): the Substrate, or the Overworld while dimension's embassy.embassies is on. The
 * Substrate's own events stay the Substrate's (raids, caravans, the EMP storm, the economy's kills, the capsule, the death mark).
 */
public final class SettlementSC {

    /** ПЖ-2: the Tuners are untouchable by players this far past the dome's edge too. */
    public static final int BELT = 24;
    /** ПП-7: the protected cylinder reaches this far under the floor (the foundation; deeper, the mines are free). */
    public static final int FOUNDATION = 6;
    /** ПР-1: the dock's protected zone round its beacon, across; and how far under / over the beacon it reaches. */
    public static final int DOCK_ZONE = 5, DOCK_BELOW = 3, DOCK_ABOVE = 16;
    /** ПП-11: no hostile spawns this close past a dome's edge (proposal §12: 32..48). TODO(balance) */
    public static final int SPAWN_MARGIN = 40;
    /** ПП-13: the Clone Capsule works only with no player this close to the core. */
    public static final int RESPAWN_CLEAR = 48;
    /** ПД-2: a hostile born in Si-14 killed this close past a dome's edge earns that player «Доверие». */
    public static final int KILL_REACH = 48;
    /** A game day (the Overworld's clock: DerivedWorldInfo) and a week of them. */
    public static final long DAY = 24000L;
    public static final int WEEK_DAYS = 7;
    /** ПП-6: the core field's owner is OWNER_PREFIX + id + OWNER_SUFFIX - never a Minecraft name ([A-Za-z0-9_] only). */
    public static final String OWNER_PREFIX = "[Поселение-", OWNER_SUFFIX = "]";

    private SettlementSC() {
    }

    /** SCMod.init, after SubstrateSC.init: the structure names (always - a saved world must load), the IMC. */
    public static void init() {
        com.sc.settle.gen.MapGenSettlementSC.registerStructures();
        SettlementCompatSC.sendImc();
    }

    /** New settlements generate: the world is on and dimension.villages (the ones made already stay either way). */
    public static boolean generates() {
        return SubstrateSC.active() && SubstrateConfigSC.villages;
    }

    // ------------------------------------------------------------------ Ф6: which worlds have settlements (ПП-14)

    /** The Overworld's id - the only world an embassy stands in (ПЕ-1). */
    public static final int EMBASSY_DIM = 0;

    /**
     * ПП-14: world `w` holds settlements whose protection, core roster and creatures work - the Substrate, or the Overworld while
     * embassy.embassies is on (its embassies). Both sides answer the same (a client's world is told by its provider and dimension
     * id); the server-side questions below still answer «no» on a client.
     */
    public static boolean governs(World w) {
        return w != null && w.provider != null && (SubstrateSC.is(w) || w.provider.dimensionId == EMBASSY_DIM && SubstrateConfigSC.embassies);
    }

    /** ПЕ-1: new embassies generate - the Substrate on (the flights go there) and embassy.embassies (those made stay either way). */
    public static boolean embassiesGenerate() {
        return SubstrateSC.active() && SubstrateConfigSC.embassies;
    }

    /** Record `s` is a Tuners' Embassy (SettlementSizeSC.EMBASSY in the Overworld), not a Substrate settlement. Pure. */
    public static boolean isEmbassy(SettlementRegistrySC.Settlement s) {
        return s != null && s.size == SettlementSizeSC.EMBASSY.ordinal();
    }

    /** Record `s` stands in the Substrate (the bridge's targets, the known docks, the raids and caravans go by this). */
    public static boolean inSubstrate(SettlementRegistrySC.Settlement s) {
        return s != null && SubstrateSC.active() && SubstrateSC.isDim(s.dim);
    }

    // ------------------------------------------------------------------ names

    public static String ownerName(int id) {
        return OWNER_PREFIX + id + OWNER_SUFFIX;
    }

    /** A field owner that is a settlement's core (ownerName's form). */
    public static boolean isSettlementOwner(String owner) {
        return owner != null && owner.startsWith(OWNER_PREFIX) && owner.endsWith(OWNER_SUFFIX);
    }

    /** «Купол «Ниобий-4»» - translated by each client (sc.settle.name); Ф6: an embassy «Посольство „Тантал-1“» (sc.embassy.name). */
    public static IChatComponent displayName(SettlementRegistrySC.Settlement s) {
        return new ChatComponentTranslation(isEmbassy(s) ? "sc.embassy.name" : "sc.settle.name",
                NpcNamesSC.name(NpcNamesSC.code(s.nameElement, s.nameNumber)));
    }

    // ------------------------------------------------------------------ time

    /** The game day (world time / 24000: dawn starts a day - the shops restock, the board renews). */
    public static long day(World w) {
        return w == null ? 0L : Math.max(0L, w.getWorldTime()) / DAY;
    }

    public static long week(World w) {
        return day(w) / WEEK_DAYS;
    }

    /** Ticks to the next dawn (the restock timer the trade screen shows). */
    public static long ticksToDawn(World w) {
        return w == null ? DAY : DAY - Math.max(0L, w.getWorldTime()) % DAY;
    }

    // ------------------------------------------------------------------ where (server)

    /** The server's registry; null on a client. */
    public static SettlementRegistrySC registry(World w) {
        return SettlementRegistrySC.get(w);
    }

    /** ПП-7: the settlement protecting the point (server; the Substrate, Ф6 the Overworld's embassies - governs), or null. */
    public static SettlementRegistrySC.Settlement protecting(World w, double x, double y, double z) {
        SettlementRegistrySC r = governed(w);
        return r == null ? null : r.protecting(w.provider.dimensionId, x, y, z);
    }

    /** ПЖ-2: the settlement whose dome or belt holds the column (server, a world it governs), or null. */
    public static SettlementRegistrySC.Settlement beltAt(World w, double x, double z) {
        SettlementRegistrySC r = governed(w);
        return r == null ? null : r.inBelt(w.provider.dimensionId, x, z);
    }

    /** ПР-1: the settlement whose dock zone holds the point (server, a world it governs; Ф6: an embassy's Vokzal - its own zone), or null. */
    public static SettlementRegistrySC.Settlement dockZoneAt(World w, double x, double y, double z) {
        SettlementRegistrySC r = governed(w);
        return r == null ? null : r.dockZoneAt(w.provider.dimensionId, x, y, z);
    }

    /**
     * ПП-8 (TileEntityFieldGeneratorSC.strangerZoneOwner): the owner «[Поселение-N]» of a settlement a player's field zone
     * `zone` would overlap, or null. `self` - the asking field (a settlement's own core is never refused).
     */
    public static String fieldOverlapOwner(World w, AxisAlignedBB zone, TileEntity self) {
        if (zone == null || self instanceof com.sc.tileentity.TileEntitySettlementCoreSC) {
            return null;
        }
        SettlementRegistrySC r = governed(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.overlapping(w.provider.dimensionId, zone);
        return s == null ? null : s.owner();
    }

    /**
     * ПР-2 (the bridge's foreignField / plan / enter): an end may not open at (x, y, z) - inside a settlement's protected
     * cylinder, and not in its dock's zone (the dock is where the bridge lands). Silent; a world it governs (Ф6: an embassy's
     * cylinder too - its Vokzal outside the dome takes a bridge's end, exactly on its beacon: TravelRulesSC.DOCK_SNAP).
     */
    public static boolean refusesBridgeEnd(World w, int x, int y, int z) {
        SettlementRegistrySC r = governed(w);
        if (r == null) {
            return false;
        }
        int d = w.provider.dimensionId;
        return r.protecting(d, x + 0.5D, y + 0.5D, z + 0.5D) != null && r.dockZoneAt(d, x + 0.5D, y + 0.5D, z + 0.5D) == null;
    }

    /** The registry for a server world whose settlements work (governs: the Substrate, the Overworld with embassies on), else null. */
    private static SettlementRegistrySC governed(World w) {
        return w == null || w.isRemote || !governs(w) ? null : SettlementRegistrySC.get(w);
    }

    // ------------------------------------------------------------------ who

    /** The settlement id of a settlement's creature, -1 for anything else (or an unbound one). */
    public static int settlementOf(Entity e) {
        return e instanceof ISettlementMemberSC ? ((ISettlementMemberSC) e).settlementId() : -1;
    }

    /** A new creature for a slot of `kind` (not spawned, not bound: the caller binds it - ISettlementMemberSC.bindTo). */
    public static EntityCreature newMember(World w, NpcKindSC kind) {
        if (kind.guard) {
            EntityWardenSC g = new EntityWardenSC(w);
            g.setKind(kind);
            return g;
        }
        EntityTunerSC t = new EntityTunerSC(w);
        t.setKind(kind);
        return t;
    }
}
