package com.sc.embassy;

import java.util.Arrays;
import java.util.List;

import com.sc.dim.SubstrateConfigSC;
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
 * <li>repair [id] - the «Достройка» now over the nearest made embassy (or record id): EmbassyRepairSC.repair(world, s, true).</li>
 * </ul>
 * Always about the server's Overworld: from there the sender's own column, from another world (or the console) the world's spawn.
 * The land is read through the biome layers (EmbassySiteSC) and the region files' headers only - list and where make and load
 * nothing; repair runs over loaded windows only. Lang sc.cmd.embassy.* (package D). Fixed (the coordinator's): the class, the name,
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
        return "/scembassy list [radius] | where | repair [id]";
    }

    @Override
    public int getRequiredPermissionLevel() {
        return 2;
    }

    @Override
    @SuppressWarnings("rawtypes")
    public List addTabCompletionOptions(ICommandSender sender, String[] args) {
        return args.length == 1 ? getListOfStringsMatchingLastWord(args, "list", "where", "repair") : null;
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
        for (EmbassySiteSC.Site s : sites) {
            if (shown == LIST_LINES) {
                sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.list.more", sites.size() - shown));
                break;
            }
            sender.addChatMessage(new ChatComponentTranslation(s.guaranteed ? "sc.cmd.embassy.list.lineGuar" : "sc.cmd.embassy.list.line",
                    s.x, s.z, s.radius, s.floorY, dist(s, o), state(w, s)));
            shown++;
        }
        if (sites.isEmpty()) {
            sender.addChatMessage(new ChatComponentTranslation("sc.cmd.embassy.list.empty"));
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
