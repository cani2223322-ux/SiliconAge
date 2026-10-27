package com.sc.embassy;

import java.util.Arrays;
import java.util.List;

import com.sc.dim.SubstrateConfigSC;
import com.sc.embassy.api.EmbassyAirApiSC;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;

import net.minecraft.command.CommandBase;
import net.minecraft.command.ICommandSender;
import net.minecraft.server.MinecraftServer;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChunkCoordinates;
import net.minecraft.util.IChatComponent;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;

/**
 * PACKAGE G (docs/plan-substrate-tech-f6.md §5.7, rules ПЕ-10, ПК-28): /scembassy (alias /scemb), for operators (level 2):
 * <ul>
 * <li>list [radius] - the embassies within radius (default LIST_DEFAULT, at most LIST_MAX) of the sender: grid and guaranteed, each
 * with its x z, radius, floor, distance and state - made (its record's id), unexplored, empty (old land) - and the share of the
 * regions that got one: the TODO(balance) of embassyChance on the user's seed (a pure read: no chunk is loaded or made);</li>
 * <li>where - the guaranteed embassy and the nearest one;</li>
 * <li>repair [id] - the «Достройка» now over the nearest made embassy (or record id): EmbassyRepairSC.repair(world, s, true);</li>
 * <li>airfield [id] - «Лётное поле посольства» (docs/plan-embassy-airfield-core.md §4.6): a REPORT of the nearest made embassy's (or
 * record id's) airfield - its kind, side, ICAO, runway numbers, ILS ends, protected zone's box, the air defence's zone and emplacements,
 * the fields' revision; a record without "X" - none (old embassies are never fitted with one: no retrofit, user's decision 4).</li>
 * </ul>
 * list's lines say each embassy's airfield too (variant): a record's (or a built start's) decision - kind, side, the runway's numbers,
 * its ILS ends; «no airfield» without "X"; an unexplored site - what EmbassyAirSiteSC.choose decides for it now (marked; the biome
 * layers only), and a count of the variants (the FULL share - TODO(balance) of the plan: >= 60 % on the twelve seeds).
 * Always about the server's Overworld: from there the sender's own column, from another world (or the console) the world's spawn.
 * The land is read through the biome layers (EmbassySiteSC) and the region files' headers only - list, where and airfield make and
 * load nothing; repair runs over loaded windows only. Lang sc.cmd.embassy.* (package D). Fixed (the coordinator's): the class, the name,
 * the alias, the level. Bodies G's.
 */
public class CommandEmbassySC extends CommandBase {

    public static final int LIST_DEFAULT = 4000, LIST_MAX = 16000;
    /** list's lines at most (the rest counted in one line). */
    public static final int LIST_LINES = 24;

    @Override
    public String getCommandName() {
        return "scembassy";
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List getCommandAliases() {
        return Arrays.asList("scemb");
    }

    @Override
    public String getCommandUsage(ICommandSender sender) {
        return "/scembassy list [radius] | where | repair [id] | airfield [id]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "list", "where", "repair", "airfield") : null;
    }

    @Override
    public void processCommand(ICommandSender sender, String[] args) {
        String sub = args.length > 0 ? args[0] : "";
        if ("list".equals(sub) && args.length <= 2) {
            list(sender, args.length == 2 ? parseIntBounded(sender, args[1], 1, LIST_MAX) : LIST_DEFAULT);
        } else if ("where".equals(sub) && args.length == 1) {
            where(sender);
        } else if ("repair".equals(sub) && args.length <= 2) {
            repair(sender, args.length == 2 ? parseIntWithMin(sender, args[1], 1) : -1);
        } else if ("airfield".equals(sub) && args.length <= 2) {
            airfield(sender, args.length == 2 ? parseIntWithMin(sender, args[1], 1) : -1);
        } else {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.usage"));
        }
    }

    // ------------------------------------------------------------------ list

    private static void list(ICommandSender sender, int reach) {
        WorldServer w = overworld(sender);
        if (w == null) {
            return;
        }
        int[] o = origin(sender, w);
        int grid = EmbassySiteSC.grid();
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.list.head", reach, o[0], o[1], grid,
                SubstrateConfigSC.embassyChance + "%"));
        List<EmbassySiteSC.Site> sites = EmbassySiteSC.near(w, o[0], o[1], reach);
        int shown = 0;
        int[] kinds = new int[EmbassyAirfieldSC.FULL + 1];     // «Лётное поле»: the variants (NONE: no airfield) of the made and the unexplored
        int bothIls = 0;
        for (EmbassySiteSC.Site s : sites) {
            int[] at = new int[1];
            int[] d = airOf(w, s, at);
            if (at[0] != AIR_EMPTY) {
                int k = kindOf(d);
                kinds[k]++;
                bothIls += k == EmbassyAirfieldSC.FULL && (d[2] & BOTH_ILS) == BOTH_ILS ? 1 : 0;
            }
            if (shown == LIST_LINES) {
                sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.list.more", sites.size() - shown));
                shown++;                                        // said once; the rest only counted
            }
            if (shown >= LIST_LINES) {
                continue;
            }
            IChatComponent line = new ChatComponentTranslation(s.guaranteed ? "sc.cmd.embassy.list.lineGuar" : "sc.cmd.embassy.list.line",
                    s.x, s.z, s.radius, s.floorY, dist(s, o), state(w, s));
            if (at[0] != AIR_EMPTY) {
                line.appendText(" ").appendSibling(airLine(d, s.seed));
                if (at[0] == AIR_WOULD) {
                    line.appendText(" ").appendSibling(EmbassyAirfieldSC.buildsNow() ? new ChatComponentTranslation("sc.cmd.embassy.air.would")
                            : new ChatComponentTranslation("sc.cmd.embassy.air.wouldOff",
                            SubstrateConfigSC.AIRFIELD_MODES[SubstrateConfigSC.embassyAirfield()]));
                }
            }
            sender.addChatMessage(line);
            shown++;
        }
        if (sites.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.list.empty"));
        }
        int counted = kinds[EmbassyAirfieldSC.NONE] + kinds[EmbassyAirfieldSC.HELI] + kinds[EmbassyAirfieldSC.SHORT] + kinds[EmbassyAirfieldSC.FULL];
        if (counted > 0) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.air.share", kinds[EmbassyAirfieldSC.FULL],
                    pct(kinds[EmbassyAirfieldSC.FULL], counted), kinds[EmbassyAirfieldSC.SHORT], kinds[EmbassyAirfieldSC.HELI],
                    kinds[EmbassyAirfieldSC.NONE], bothIls));
        }
        // the share of the regions round (their boxes meet the radius's square): rolled - the chance alone; stand - the land fits too
        long seed = w.getSeed();
        int regions = 0, rolled = 0, stand = 0;
        for (int rx = EmbassySiteSC.region(o[0] - reach, grid); rx <= EmbassySiteSC.region(o[0] + reach, grid); rx++) {
            for (int rz = EmbassySiteSC.region(o[1] - reach, grid); rz <= EmbassySiteSC.region(o[1] + reach, grid); rz++) {
                regions++;
                rolled += EmbassySiteSC.rolls(seed, rx, rz, SubstrateConfigSC.embassyChance) ? 1 : 0;
                stand += EmbassySiteSC.site(w, rx, rz) != null ? 1 : 0;
            }
        }
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.list.share", regions, rolled, pct(rolled, regions), stand,
                pct(stand, regions)));
    }

    // ------------------------------------------------------------------ where

    private static void where(ICommandSender sender) {
        WorldServer w = overworld(sender);
        if (w == null) {
            return;
        }
        int[] o = origin(sender, w);
        EmbassySiteSC.Site g = EmbassySiteSC.guaranteed(w);
        if (g != null) {
            int sx = w.getWorldInfo().getSpawnX(), sz = w.getWorldInfo().getSpawnZ();
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.where.guaranteed", g.x, g.z, (int) Math.sqrt(g.distSq(sx, sz)),
                    dist(g, o), state(w, g)));
        } else {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.where.noGuaranteed"));
        }
        EmbassySiteSC.Site n = EmbassySiteSC.nearest(w, o[0], o[1], LIST_DEFAULT);
        if (n != null) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.where.nearest", n.x, n.z, dist(n, o),
                    new ChatComponentTranslation("sc.finder.dir." + ItemRuinFinderSC.DIRS[ItemRuinFinderSC.direction(n.x - o[0], n.z - o[1])]),
                    state(w, n)));
        } else {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.where.noNearest", LIST_DEFAULT));
        }
    }

    // ------------------------------------------------------------------ repair

    private static void repair(ICommandSender sender, int id) {
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer w = srv == null ? null : srv.worldServerForDimension(EmbassySiteSC.DIM);
        SettlementRegistrySC reg = w == null ? null : SettlementRegistrySC.get(w);
        if (reg == null) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.repair.none"));
            return;
        }
        SettlementRegistrySC.Settlement s = null;
        if (id > 0) {
            s = reg.byId(id);
            if (!SettlementSC.isEmbassy(s) || s.dim != EmbassySiteSC.DIM) {
                sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.repair.noId", id));
                return;
            }
        } else {
            int[] o = origin(sender, w);
            for (SettlementRegistrySC.Settlement e : reg.embassies()) {
                if (e.dim == EmbassySiteSC.DIM && (s == null || e.distSq(o[0], o[1]) < s.distSq(o[0], o[1]))) {
                    s = e;
                }
            }
            if (s == null) {
                sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.repair.none"));
                return;
            }
        }
        if (EmbassyRepairSC.startOf(w, s) == null) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.repair.noStart", s.id));
            return;
        }
        int n = EmbassyRepairSC.repair(w, s, true);
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.repair.done", SettlementSC.displayName(s), s.id, n,
                EmbassyRepairSC.windows(s).size()));
    }

    // ------------------------------------------------------------------ airfield (a report only: no retrofit)

    private static void airfield(ICommandSender sender, int id) {
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer w = srv == null ? null : srv.worldServerForDimension(EmbassySiteSC.DIM);
        SettlementRegistrySC reg = w == null ? null : SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement s = null;
        if (reg != null && id > 0) {
            s = reg.byId(id);
            if (!SettlementSC.isEmbassy(s) || s.dim != EmbassySiteSC.DIM) {
                sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.repair.noId", id));
                return;
            }
        } else if (reg != null) {
            int[] o = origin(sender, w);
            for (SettlementRegistrySC.Settlement e : reg.embassies()) {
                if (e.dim == EmbassySiteSC.DIM && (s == null || e.distSq(o[0], o[1]) < s.distSq(o[0], o[1]))) {
                    s = e;
                }
            }
        }
        if (s == null) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.none"));
            return;
        }
        IChatComponent name = SettlementSC.displayName(s);
        EmbassyAirfieldSC.Field f = EmbassyAirfieldSC.fieldOf(s);
        if (f == null) {                                        // no "X" (made before, or without the add-on), or NONE: never fitted later
            sender.addChatMessage(new ChatComponentTranslation(EmbassyAirfieldSC.decisionOf(s) == null ? "sc.cmd.embassy.airfield.noX"
                    : "sc.cmd.embassy.airfield.kindNone", name, s.id));
            return;
        }
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.head", name, s.id, kindName(f.kind), side(f.side),
                EmbassyAirfieldSC.icao(s.nameElement, s.nameNumber), f.version));
        if (f.runway != null) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.runway", numbers(f.gateDir), f.endHigh - f.endLow + 1,
                    f.runway[3] - f.runway[1] + 1, xz(f.block(f.endLow, f.axis, 0)), xz(f.block(f.endHigh, f.axis, 0)), ilsEnds(f.gateDir, f.ils)));
        } else {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.noRunway"));
        }
        int[] z = f.zoneBox();
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.zone", z[0], z[1], z[2], z[3],
                f.floorY - SettlementSC.FOUNDATION));
        StringBuilder posts = new StringBuilder();
        for (int i = 0; i < f.aaPosts.length; i++) {
            int[] b = f.aaBlock(i);
            posts.append(i == 0 ? "" : "; ").append(b[0]).append(' ').append(b[1]).append(' ').append(b[2]);
        }
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.ad", EmbassyAirfieldSC.AD_RADIUS, f.coreX, f.coreZ,
                f.aaPosts.length, posts.toString()));
        sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.airfield.rev", EmbassyAirfieldSC.revision(), EmbassyAirApiSC.API_VERSION));
    }

    // ------------------------------------------------------------------ the airfield's variant (list)

    /** airOf's states: a made embassy's (its record's "X", or its built start's "Air"), an unexplored site's (choose now), an empty one's. */
    private static final int AIR_MADE = 0, AIR_WOULD = 1, AIR_EMPTY = 2;
    private static final int BOTH_ILS = EmbassyAirfieldSC.ILS_LOW | EmbassyAirfieldSC.ILS_HIGH;

    /**
     * Site `s`'s airfield decision {kind, side, ils, version} (null: none - no "X", no "Air") and its state into `state[0]` (AIR_*): a
     * record's "X" (made), its built start's ground piece's "Air" (made - the record lost), an unexplored site's - what
     * EmbassyAirSiteSC.choose decides now by the biome layers, whatever embassy.airfield says (AIR_WOULD); an empty start or explored
     * ground - none (AIR_EMPTY: no embassy will stand there). Loads and makes nothing. Server.
     */
    private static int[] airOf(WorldServer w, EmbassySiteSC.Site s, int[] state) {
        state[0] = AIR_MADE;
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement rec = reg == null ? null : reg.atCentre(EmbassySiteSC.DIM, s.x, s.z);
        if (rec != null) {
            return EmbassyAirfieldSC.decisionOf(rec);
        }
        MapGenEmbassySC m = EmbassySC.mapGen(w);
        StructureEmbassyStartSC st = m == null ? null : m.startAt(w, s.chunkX(), s.chunkZ());
        if (st != null && st.isSizeableStructure()) {
            ComponentEmbGroundSC g = ComponentEmbGroundSC.of(st);
            return g == null ? null : g.airDecision();
        }
        if (st != null || EmbassySiteSC.explored(w, s)) {
            state[0] = AIR_EMPTY;
            return null;
        }
        state[0] = AIR_WOULD;
        int[] c = EmbassyAirSiteSC.choose(w, s);
        return c == null || c.length < 3 ? null : new int[]{c[0], c[1] < 0 ? -1 : 1, c[2], EmbassyAirfieldSC.VERSION};
    }

    /** A decision's kind (NONE for none or a bad one). */
    private static int kindOf(int[] d) {
        return d == null || d.length < 3 || d[0] < EmbassyAirfieldSC.NONE || d[0] > EmbassyAirfieldSC.FULL ? EmbassyAirfieldSC.NONE : d[0];
    }

    /** list's variant: «• поле: полное, сторона +1, ВПП 36/18, ILS: 36, 18» (HELI: no runway), «• без лётного поля». */
    private static IChatComponent airLine(int[] d, long siteSeed) {
        int k = kindOf(d);
        if (k == EmbassyAirfieldSC.NONE) {
            return new ChatComponentTranslation("sc.cmd.embassy.air.none");
        }
        if (k == EmbassyAirfieldSC.HELI) {
            return new ChatComponentTranslation("sc.cmd.embassy.air.lineHeli", kindName(k), side(d[1]));
        }
        int gate = EmbassyLayoutSC.gateDir(siteSeed);
        return new ChatComponentTranslation("sc.cmd.embassy.air.line", kindName(k), side(d[1]), numbers(gate),
                ilsEnds(gate, k == EmbassyAirfieldSC.FULL ? d[2] : 0));
    }

    private static IChatComponent kindName(int kind) {
        return new ChatComponentTranslation("sc.airfield.kind." + EmbassyAirfieldSC.KIND_KEYS[Math.max(0, Math.min(EmbassyAirfieldSC.FULL, kind))]);
    }

    private static String side(int side) {
        return side < 0 ? "-1" : "+1";
    }

    /** The runway's numbers «low/high» (the low end's painted number first - a landing over it flies towards +a), e.g. «36/18». */
    private static String numbers(int gateDir) {
        return EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(gateDir, true)) + "/" + EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(gateDir, false));
    }

    /** The ends with an ILS (bits ILS_LOW, ILS_HIGH) by their numbers, «36, 18»; none - «нет». */
    private static Object ilsEnds(int gateDir, int ils) {
        StringBuilder b = new StringBuilder();
        if ((ils & EmbassyAirfieldSC.ILS_LOW) != 0) {
            b.append(EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(gateDir, true)));
        }
        if ((ils & EmbassyAirfieldSC.ILS_HIGH) != 0) {
            b.append(b.length() > 0 ? ", " : "").append(EmbassyAirfieldSC.number(EmbassyAirfieldSC.heading(gateDir, false)));
        }
        return b.length() > 0 ? b.toString() : new ChatComponentTranslation("sc.cmd.embassy.air.noIls");
    }

    /** "x z" of a block {x, y, z}. */
    private static String xz(int[] b) {
        return b[0] + " " + b[2];
    }

    // ------------------------------------------------------------------ helpers

    /** The server's Overworld if embassies may stand there (else the sender is told why and null). An «off» line when they are off. */
    private static WorldServer overworld(ICommandSender sender) {
        MinecraftServer srv = MinecraftServer.getServer();
        WorldServer w = srv == null ? null : srv.worldServerForDimension(EmbassySiteSC.DIM);
        if (w == null || !EmbassySiteSC.worldFits(w)) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.none"));
            return null;
        }
        if (!SettlementSC.embassiesGenerate()) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.off"));
        }
        return w;
    }

    /** {x, z}: the sender's column in the Overworld; from another world, the console or a command block elsewhere - the spawn. */
    private static int[] origin(ICommandSender sender, WorldServer w) {
        World sw = sender instanceof MinecraftServer ? null : sender.getEntityWorld();
        if (sw != null && sw.provider != null && sw.provider.dimensionId == EmbassySiteSC.DIM) {
            ChunkCoordinates c = sender.getPlayerCoordinates();
            if (c != null) {
                return new int[]{c.posX, c.posZ};
            }
        }
        return new int[]{w.getWorldInfo().getSpawnX(), w.getWorldInfo().getSpawnZ()};
    }

    /** A site's state: made (its record's id), empty (old land: a start without a city, or its ground explored), unexplored. */
    private static IChatComponent state(WorldServer w, EmbassySiteSC.Site s) {
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        SettlementRegistrySC.Settlement rec = reg == null ? null : reg.atCentre(EmbassySiteSC.DIM, s.x, s.z);
        if (rec != null) {
            return new ChatComponentTranslation("sc.cmd.embassy.state.made", rec.id);
        }
        MapGenEmbassySC m = EmbassySC.mapGen(w);
        StructureEmbassyStartSC st = m == null ? null : m.startAt(w, s.chunkX(), s.chunkZ());
        if (st != null && st.isSizeableStructure()) {
            return new ChatComponentTranslation("sc.cmd.embassy.state.made", st.settlementId());
        }
        return st != null || EmbassySiteSC.explored(w, s) ? new ChatComponentTranslation("sc.cmd.embassy.state.empty")
                : new ChatComponentTranslation("sc.cmd.embassy.state.unexplored");
    }

    private static int dist(EmbassySiteSC.Site s, int[] o) {
        return (int) Math.round(Math.sqrt(s.distSq(o[0], o[1])));
    }

    /** "a of b" as a whole percent. */
    private static String pct(int a, int b) {
        return (b <= 0 ? 0 : Math.round(a * 100F / b)) + "%";
    }
}
