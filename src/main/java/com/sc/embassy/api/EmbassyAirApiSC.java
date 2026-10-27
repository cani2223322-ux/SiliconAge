package com.sc.embassy.api;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import com.sc.embassy.EmbassyAirDefenceSC;
import com.sc.embassy.EmbassyAirRulesSC;
import com.sc.embassy.EmbassyAirServiceSC;
import com.sc.embassy.EmbassyAirfieldSC;
import com.sc.embassy.EmbassyBlocksSC;
import com.sc.embassy.EmbassyRulesSC;
import com.sc.embassy.EmbassyTicketSC;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementBlocksSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import cpw.mods.fml.common.FMLLog;
import net.minecraft.block.Block;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * «Лётное поле посольства» - the PUBLIC API of the Tuners' Embassies' airfields for the Silicon Age: Vehicles add-on (docs/plan-embassy-
 * airfield-core.md §5, docs/idea-embassy-airfield.md §9). The add-on compiles against the core and talks to the embassies ONLY through
 * this package (EmbassyAirApiSC, AirfieldInfoSC, IEmbassyAirHookSC); the core never names a class of the add-on (it finds it by
 * Loader.isModLoaded("SiliconAgeVehicles") alone). IMC would not do: the add-on asks during play. Written whole by the coordinator; fixed
 * for API_VERSION 1 - methods and codes are only appended (a change of meaning raises the version; the add-on checks it and its
 * ReferenceSVA.CORE_DEP asks for the core's version with it).
 * <p>
 * Everything is the server's (the server thread): on a client - empty lists, null, false, -1. A FakePlayer is never served (pay / pass /
 * first landing refuse it). Coordinates are world blocks; a player's «уровень» is the core's ReputationSC level at that embassy (its
 * «Враг» status wins). The embassies' world is the Overworld (dimension 0): another world has none.
 * <ul>
 * <li><b>where</b>: airfields, airfield(id), airfieldAt (the protected zone holds the column), domeAt, neutralAt (the players' air defence
 * holds fire there - the level disc, the field, the approach corridors; any height; never over a player's base), isRunwaySurface /
 * isEmbassyBlock (the add-on's runway
 * flag and its ILS obstacle() white list), revision (moves on whenever an airfield is made or renewed - its virtual airports again);</li>
 * <li><b>who</b>: levelOf, knows (the player has been there or the embassy is near - EmbassyTicketSC's «seen»), hasTalon (a flight paid there
 * today or the return half of a flight from there still valid - free parking);</li>
 * <li><b>services</b>: quote / pay by EmbassyAirRulesSC.SERVICE_KEYS (the core checks «Враг», the level, the caps and takes the chips -
 * PAY_*), firstLanding (the «Лётная книжка»: 2 chips and +5 «Доверия» once an embassy);</li>
 * <li><b>the embassy's air defence</b> (the user's choice 6): adRadius / adZoneAt (256 across from the core, any height - never over a
 * player's base: EmbassyAirfieldSC.playerBaseAt; AirfieldInfoSC.inAdZone is the bare circle - ask adZoneAt), the emplacements' guns
 * (AirfieldInfoSC.adPosts), admitted («Знакомый» and higher, not its «Враг»), escortOf (an admitted squad mate near - no target),
 * requestPass / passUntil (7700: one PASS_SECONDS hold-fire a player an embassy a game day), requestWarn / warnUntil (the warning: one a
 * player an embassy a game day), holdFire (any of these, or over a player's base), adLine / tell (the core's chat lines - the add-on's
 * radio shows the same). The add-on does the rest: only machines IN THE AIR with nobody admitted on board (its passengers and crew asked
 * one by one), never a player on foot or flying in armour, never anything on the ground; the warning (AD_WARN, requestWarn for each one
 * on board) and its WARN_SECONDS to turn away before the fire (AD_FIRE).</li>
 * </ul>
 * The rules of the warning for the add-on (the review's - else a landing inside the warning, or out of the zone and back, would be a way
 * past the guns): the seconds are to turn AWAY - a warned machine that closes on the field or descends towards it meanwhile is fired on
 * at once; a machine's seconds end at the EARLIEST of its riders' requestWarn (one warned already today makes it a target at once:
 * AD_FIRE without a new AD_WARN).
 */
public final class EmbassyAirApiSC {

    /** The API's version (the add-on refuses to talk to a core of another one - and the core to a hook of a newer one). */
    public static final int API_VERSION = 1;
    /** The add-on's mod id (Loader.isModLoaded). */
    public static final String ADDON_MODID = EmbassyAirfieldSC.ADDON;

    /** pay / quote: paid; the embassy's «Враг»; the level; a cap; not enough chips; no airfield there; an unknown service or amount. */
    public static final int PAY_OK = EmbassyAirRulesSC.PAY_OK, PAY_ENEMY = EmbassyAirRulesSC.PAY_ENEMY, PAY_LEVEL = EmbassyAirRulesSC.PAY_LEVEL,
            PAY_LIMIT = EmbassyAirRulesSC.PAY_LIMIT, PAY_CHIPS = EmbassyAirRulesSC.PAY_CHIPS, PAY_NO_FIELD = EmbassyAirRulesSC.PAY_NO_FIELD,
            PAY_UNKNOWN = EmbassyAirRulesSC.PAY_UNKNOWN;
    /** requestPass: given now; one running; used today; no airfield; admitted (needs none). */
    public static final int PASS_GRANTED = EmbassyAirDefenceSC.PASS_GRANTED, PASS_RUNNING = EmbassyAirDefenceSC.PASS_RUNNING,
            PASS_USED = EmbassyAirDefenceSC.PASS_USED, PASS_NO_FIELD = EmbassyAirDefenceSC.PASS_NO_FIELD, PASS_ADMITTED = EmbassyAirDefenceSC.PASS_ADMITTED;
    /** adLine / tell: the warning (leave the zone; how to be admitted), the fire opened, the 7700 pass given / refused (used today) / over, the zone left. */
    public static final int AD_WARN = EmbassyAirDefenceSC.AD_WARN, AD_FIRE = EmbassyAirDefenceSC.AD_FIRE, AD_PASS = EmbassyAirDefenceSC.AD_PASS,
            AD_PASS_USED = EmbassyAirDefenceSC.AD_PASS_USED, AD_PASS_OVER = EmbassyAirDefenceSC.AD_PASS_OVER, AD_LEFT = EmbassyAirDefenceSC.AD_LEFT;
    /** The Dispatcher's tab: at most this many of the hook's rows; its one action button's key. */
    public static final int TAB_ROWS = 8;
    public static final String TAB_ACTION_SERVICE = "service";

    private static volatile IEmbassyAirHookSC hook;
    /** id -> the info made of the record's current field (made again when fieldOf gives another Field). Server thread. */
    private static final Map<Integer, AirfieldInfoSC> INFO = new HashMap<Integer, AirfieldInfoSC>();

    private EmbassyAirApiSC() {
    }

    // ================================================================== the hook

    /**
     * The add-on's hook (its init; one - a second replaces the first). Refused (false, logged): null, or built for a newer API_VERSION than
     * this core's.
     */
    public static boolean registerHook(IEmbassyAirHookSC h) {
        if (h == null) {
            return false;
        }
        int v;
        try {
            v = h.apiVersion();
        } catch (Throwable t) {
            FMLLog.warning("[Silicon Age] Embassy airfields: the hook %s failed to answer its API version (%s) - refused", h.getClass().getName(), t);
            return false;
        }
        if (v > API_VERSION) {
            FMLLog.warning("[Silicon Age] Embassy airfields: the hook %s wants API %d, this core has %d - refused (update Silicon Age)",
                    h.getClass().getName(), v, API_VERSION);
            return false;
        }
        hook = h;
        FMLLog.info("[Silicon Age] Embassy airfields: hook %s registered (API %d)", h.getClass().getName(), v);
        return true;
    }

    /** The add-on's hook, or null (no add-on: the core's own services only). */
    public static IEmbassyAirHookSC hook() {
        return hook;
    }

    /** The core: an airfield was made or renewed (EmbassyAirfieldSC.changed) - the hook hears it. Never throws. */
    public static void fireChanged(World w, int revision) {
        synchronized (INFO) {
            INFO.clear();
        }
        IEmbassyAirHookSC h = hook;
        if (h != null) {
            try {
                h.airfieldsChanged(w, revision);
            } catch (Throwable t) {
                FMLLog.warning("[Silicon Age] Embassy airfields: the hook's airfieldsChanged threw %s", t);
            }
        }
    }

    // ================================================================== where

    /** Every airfield of world `w` (the Overworld's embassies with one; a client, another world - empty). */
    public static List<AirfieldInfoSC> airfields(World w) {
        List<AirfieldInfoSC> out = new ArrayList<AirfieldInfoSC>();
        SettlementRegistrySC r = embassyRegistry(w);
        EmbassyAirfieldSC.Index ix = r == null ? null : EmbassyAirfieldSC.index(r);
        for (int k = 0; ix != null && k < ix.size(); k++) {
            SettlementRegistrySC.Settlement s = ix.get(k);
            AirfieldInfoSC i = s.dim == w.provider.dimensionId ? info(s) : null;
            if (i != null) {
                out.add(i);
            }
        }
        return out;
    }

    /** Embassy `id`'s airfield in `w` (null: none, no such embassy, another world). */
    public static AirfieldInfoSC airfield(World w, int id) {
        SettlementRegistrySC r = registry(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        return s == null || s.dim != w.provider.dimensionId ? null : info(s);
    }

    /** The airfield whose protected zone holds the column (x, z) of `w` (null: none). */
    public static AirfieldInfoSC airfieldAt(World w, int x, int z) {
        SettlementRegistrySC.Settlement s = SettlementSC.airfieldAt(w, x + 0.5D, z + 0.5D);
        return s == null ? null : info(s);
    }

    /** The embassy (its id) whose dome holds the column (any height; -1: none) - the auto-tower refuses a landing there. */
    public static int domeAt(World w, double x, double z) {
        SettlementRegistrySC r = registry(w);
        if (r == null) {
            return -1;
        }
        for (SettlementRegistrySC.Settlement s : r.candidates(w.provider.dimensionId, (int) Math.floor(x), (int) Math.floor(z))) {
            if (SettlementSC.isEmbassy(s) && s.distSq(x, z) <= (double) s.radius * s.radius) {
                return s.id;
            }
        }
        return -1;
    }

    /**
     * The players' air defence holds fire at (x, y, z) - any height over an airfield embassy's level disc, its field and its approach
     * corridors (the add-on's TileEntityLauncherSVA: `prot || neutralAt`) - but never over a player's base (EmbassyAirfieldSC.playerBaseAt:
     * a base in a corridor keeps its own air defence). The embassy's OWN air defence is not bound by it. Cheap off the embassies' land (an
     * index of the airfields, no list made; another world - false at once).
     */
    public static boolean neutralAt(World w, double x, double y, double z) {
        SettlementRegistrySC r = embassyRegistry(w);
        EmbassyAirfieldSC.Index ix = r == null ? null : EmbassyAirfieldSC.index(r);
        if (ix == null || ix.size() == 0) {
            return false;
        }
        int bx = (int) Math.floor(x), bz = (int) Math.floor(z), dim = w.provider.dimensionId;
        for (int k = 0; k < ix.size(); k++) {
            EmbassyAirfieldSC.Field f = ix.near(k, dim, x, z) ? EmbassyAirfieldSC.fieldOf(ix.get(k)) : null;
            if (f != null && f.neutral(bx, bz)) {
                return !EmbassyAirfieldSC.playerBaseAt(w, x, z);
            }
        }
        return false;
    }

    /** Block `b` of `meta` is an embassy runway's surface (the paving, its white marks, its edge, threshold and end lights). Both sides. */
    public static boolean isRunwaySurface(Block b, int meta) {
        return b != null && b == EmbassyBlocksSC.airfield && AirfieldInfoSC.runwayMeta(meta);
    }

    /** Block `b` is one of an embassy's own (the airfield's surface, the showcases, the trim, the core...) - no ILS obstacle. Both sides. */
    public static boolean isEmbassyBlock(Block b) {
        return b != null && (b == EmbassyBlocksSC.airfield || b == EmbassyBlocksSC.ring || SettlementBlocksSC.unbreakable(b));
    }

    /** Moves on whenever an airfield is made or renewed (the add-on's virtual airports are made again when it changes). */
    public static int revision(World w) {
        return EmbassyAirfieldSC.revision();
    }

    // ================================================================== who

    /** `p`'s level at embassy `id` (ReputationSC.ENEMY .. HONOURED; -1: no such embassy, a client; a player never met: STRANGER). */
    public static int levelOf(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        if (s == null) {
            return -1;
        }
        SettlementRegistrySC.PlayerRec pr = registry(p.worldObj).playerIfAny(p.getUniqueID());
        SettlementRegistrySC.LocalRec l = s.localIfAny(p.getUniqueID());
        boolean enemy = l != null && l.enemy(p.worldObj.getTotalWorldTime());
        return pr == null ? (enemy ? ReputationSC.ENEMY : ReputationSC.STRANGER) : ReputationSC.localLevel(pr.reputation, enemy);
    }

    /** `p` knows embassy `id` (has been there - EmbassyTicketSC's «seen»: the MFD, the ND and the map show it; or is within 1024 of it). */
    public static boolean knows(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        if (s == null) {
            return false;
        }
        double near = com.sc.embassy.EmbassyBeamSC.BEAM_VIEW;
        return s.distSq(p.posX, p.posZ) <= near * near
                || EmbassyTicketSC.hasSeen(registry(p.worldObj).playerIfAny(p.getUniqueID()), id);
    }

    /** `p` holds a flight paid at embassy `id` today, or the valid return half of a flight from it - its parking is free. */
    public static boolean hasTalon(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        SettlementRegistrySC.PlayerRec pr = s == null ? null : registry(p.worldObj).playerIfAny(p.getUniqueID());
        if (pr == null) {
            return false;
        }
        EmbassyTicketSC.Talon t = EmbassyTicketSC.talon(pr);
        if (t != null && t.settlement == id && !t.stale(SettlementSC.day(p.worldObj))) {
            return true;
        }
        return EmbassyTicketSC.backEmbassy(pr) == id && EmbassyRulesSC.backValid(EmbassyTicketSC.backUntil(pr), p.worldObj.getTotalWorldTime());
    }

    // ================================================================== services

    /**
     * What `amount` (the service's measure: game days, EU, mB, % of wear, pieces - EmbassyAirRulesSC.UNIT) of service `serviceKey`
     * (EmbassyAirRulesSC.SERVICE_KEYS) costs `p` at embassy `id`: chips >= 0, or -PAY_* (refused). `free` - the add-on's own free case
     * (parking with hasTalon, the day's first tow, the first chart). Nothing changes.
     */
    public static int quote(EntityPlayer p, int id, String serviceKey, long amount, boolean free) {
        SettlementRegistrySC.Settlement s = served(p, id);
        int service = EmbassyAirRulesSC.service(serviceKey);
        if (s == null || service < 0) {
            return -(s == null ? PAY_NO_FIELD : PAY_UNKNOWN);
        }
        return EmbassyAirServiceSC.quote(p, s, service, amount, free);
    }

    /** Pays: the core checks «Враг», the level, the caps, takes the chips and counts the day's amount - PAY_OK, or why not (nothing taken). */
    public static int pay(EntityPlayer p, int id, String serviceKey, long amount, boolean free) {
        SettlementRegistrySC.Settlement s = served(p, id);
        int service = EmbassyAirRulesSC.service(serviceKey);
        if (s == null || service < 0) {
            return s == null ? PAY_NO_FIELD : PAY_UNKNOWN;
        }
        return EmbassyAirServiceSC.pay(p, s, service, amount, free);
    }

    /**
     * The add-on saw `p`'s first landing at embassy `id` (the transponder on, his machine stopped on a stand): the «Лётная книжка» - 2 chips
     * (within the day's 64) and +5 «Доверия» (under the embassy's 599) once an embassy a player. True: given now.
     */
    public static boolean firstLanding(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = served(p, id);
        return s != null && EmbassyAirServiceSC.firstLanding(p, s);
    }

    // ================================================================== the embassy's air defence

    /** The air defence zone's radius across from the core (any height). TODO(balance) */
    public static int adRadius() {
        return EmbassyAirRulesSC.AD_RADIUS;
    }

    /** The seconds a warned machine has to turn away before the fire. TODO(balance) */
    public static int warnSeconds() {
        return EmbassyAirRulesSC.WARN_SECONDS;
    }

    /**
     * The embassy (its id) whose air defence zone holds (x, z) - the nearest core if two (-1: none; over a player's base -
     * EmbassyAirfieldSC.playerBaseAt - none either: the embassy never fires over it). Cheap off the embassies' land.
     */
    public static int adZoneAt(World w, double x, double z) {
        SettlementRegistrySC r = embassyRegistry(w);
        EmbassyAirfieldSC.Index ix = r == null ? null : EmbassyAirfieldSC.index(r);
        int best = -1, dim = ix == null ? 0 : w.provider.dimensionId;
        double bestD = Double.MAX_VALUE;
        for (int k = 0; ix != null && k < ix.size(); k++) {
            SettlementRegistrySC.Settlement s = ix.get(k);
            EmbassyAirfieldSC.Field f = ix.near(k, dim, x, z) ? EmbassyAirfieldSC.fieldOf(s) : null;
            double d = f == null ? Double.MAX_VALUE : s.distSq(x, z);
            if (f != null && f.inAdZone(x, z) && d < bestD) {
                best = s.id;
                bestD = d;
            }
        }
        return best >= 0 && EmbassyAirfieldSC.playerBaseAt(w, x, z) ? -1 : best;
    }

    /** `p` is admitted by embassy `id`'s air defence («Знакомый» or higher, not its «Враг»). */
    public static boolean admitted(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s != null && EmbassyAirDefenceSC.admitted(p, s);
    }

    /** An admitted squad mate of `p` near him (his cover: `p` is no target), or null. */
    public static EntityPlayerMP escortOf(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s == null ? null : EmbassyAirDefenceSC.escortOf(p, s);
    }

    /** 7700: a hold-fire pass for `p` at embassy `id` - PASS_*. */
    public static int requestPass(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s == null || p instanceof FakePlayer ? PASS_NO_FIELD : EmbassyAirDefenceSC.requestPass(p, s);
    }

    /** The world time `p`'s 7700 pass at `id` ends (0: none; past: over). */
    public static long passUntil(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s == null ? 0L : EmbassyAirDefenceSC.passUntil(p, s);
    }

    /**
     * The review's: the warning of `p` (each one on board) at embassy `id` - the first of the game day there gives WARN_SECONDS to turn away
     * from now, a later one that day keeps that day's end (out of the zone and back: no new seconds - past it, the fire at once). The add-on
     * calls it as it sends AD_WARN; a machine's seconds end at the earliest of its riders'. @return the world time the turn-away ends (0: no
     * such embassy, no field, a FakePlayer, or `p` admitted - he needs none)
     */
    public static long requestWarn(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s == null ? 0L : EmbassyAirDefenceSC.requestWarn(p, s);
    }

    /** The world time `p`'s warning at `id` today ends (0: none today; past: over - no new seconds today). */
    public static long warnUntil(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s == null ? 0L : EmbassyAirDefenceSC.warnUntil(p, s);
    }

    /**
     * `p` (pilot, passenger or crew) makes his machine no target of embassy `id` now: admitted, under cover, his pass running, or over a
     * player's base (the review's).
     */
    public static boolean holdFire(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        return s != null && EmbassyAirDefenceSC.holdFire(p, s);
    }

    /** The core's chat line `line` (AD_*) of embassy `id` to board `callsign` - the add-on's radio shows the same (null: no such embassy). */
    public static IChatComponent adLine(World w, int id, int line, String callsign) {
        SettlementRegistrySC r = registry(w);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        return s == null || !SettlementSC.isEmbassy(s) ? null : EmbassyAirDefenceSC.line(line, s, callsign);
    }

    /** Sends adLine to `p`'s chat (never a FakePlayer); `callsign` null - his name. */
    public static void tell(EntityPlayer p, int id, int line, String callsign) {
        SettlementRegistrySC.Settlement s = embassy(p, id);
        if (s != null) {
            EmbassyAirDefenceSC.tell(p, s, line, callsign);
        }
    }

    // ================================================================== inside

    /** The server's registry for an Overworld-like world `w` (null: a client, no world). */
    private static SettlementRegistrySC registry(World w) {
        return w == null || w.isRemote || w.provider == null ? null : SettlementRegistrySC.get(w);
    }

    /** registry(w) of the embassies' world only (the review's: a launcher on the Moon or in the Substrate asks nothing more). */
    private static SettlementRegistrySC embassyRegistry(World w) {
        return w == null || w.provider == null || w.provider.dimensionId != SettlementSC.EMBASSY_DIM ? null : registry(w);
    }

    /** Embassy `id` in `p`'s world (null: a client, none, another world). */
    private static SettlementRegistrySC.Settlement embassy(EntityPlayer p, int id) {
        SettlementRegistrySC r = p == null ? null : registry(p.worldObj);
        SettlementRegistrySC.Settlement s = r == null ? null : r.byId(id);
        return s != null && SettlementSC.isEmbassy(s) && s.dim == p.worldObj.provider.dimensionId ? s : null;
    }

    /** embassy() with an airfield, `p` a real player (null otherwise). */
    private static SettlementRegistrySC.Settlement served(EntityPlayer p, int id) {
        SettlementRegistrySC.Settlement s = p instanceof FakePlayer ? null : embassy(p, id);
        return s == null || EmbassyAirfieldSC.fieldOf(s) == null ? null : s;
    }

    /** Record `s`'s info (null: no field) - cached while its Field stays the same. Server thread. */
    static AirfieldInfoSC info(SettlementRegistrySC.Settlement s) {
        EmbassyAirfieldSC.Field f = EmbassyAirfieldSC.fieldOf(s);
        if (f == null) {
            return null;
        }
        synchronized (INFO) {
            AirfieldInfoSC i = INFO.get(Integer.valueOf(s.id));
            if (i == null || i.field() != f) {
                i = new AirfieldInfoSC(s, f);
                INFO.put(Integer.valueOf(s.id), i);
            }
            return i;
        }
    }

    /** Server stopped: the infos go (the hook stays - the add-on registered it once, in its init). */
    public static void clearAll() {
        synchronized (INFO) {
            INFO.clear();
        }
    }
}
