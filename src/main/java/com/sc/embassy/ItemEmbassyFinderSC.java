package com.sc.embassy;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IChatComponent;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE T (docs/plan-substrate-tech-f6.md §8.4, rule ПО-26; proposal §8 «Как найти»): «Пеленгатор посольства» - a right click in the
 * Overworld: the direction (eight points, ItemRuinFinderSC.DIRS - lang sc.finder.dir.*), the distance and the name of the nearest
 * embassy within SEARCH (EmbassySiteSC.nearest - the pure layout: no chunk loaded; a site whose start is empty, old land, passed
 * over - MapGenEmbassySC.startAt / EmbassySiteSC.explored) into the chat; a made one by its record's name, an unmade one «сигнал
 * без позывного»; nothing within SEARCH - «тишина в эфире». In the Substrate and elsewhere - «здесь нет сигнала посольств». Never
 * used up; a reading at most every COOLDOWN ticks a player (a held button repeats). Server decides; stack 1. Recipe: EmbassyRecipesSC.
 * <p>
 * Fixed (the coordinator's): the class, SEARCH, COOLDOWN, the registry name (EmbassyItemsSC). The rest T's; its tooltip keys are D's
 * (sc.tooltip.embassyFinder, .details - %s the reach, .howto).
 * <p>
 * T's details: heard() is the one answer «which embassy is on the air from here» - EmbassyHintSC's first-login line asks it too. It
 * walks EmbassySiteSC.near (nearest first) and takes the first that is or will be a city: a record at the site's core (made - its
 * name); else no start made there yet and its ground not explored (it will be built when someone comes - «без позывного»); a start
 * already made without a record is an empty one (old land) - passed over, and so is a site on explored ground or under a player's
 * field zone (its start will be empty). The embassies' records within the reach are heard too, nearest of both wins: a city made
 * stays on the air when the numbers of the section embassy or the world's spawn changed since (as its beam does - EmbassyBeamSC).
 * With the embassies off (embassy.embassies, or the Substrate off) the air is silent. The direction is from the player's feet
 * to the site's core, north = -Z. The cooldown is by UUID (two players by LAN each his own), in world ticks; a clock that went back (a
 * new world in the same game) never blocks.
 */
public class ItemEmbassyFinderSC extends Item {

    /** How far it hears (blocks), how often it answers a player (ticks). TODO(balance) */
    public static final int SEARCH = 6000, COOLDOWN = 40;

    /** The last reading's world time by player (server thread). */
    private static final Map<UUID, Long> LAST = new HashMap<UUID, Long>();

    public ItemEmbassyFinderSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".embassyFinder");
        setTextureName(Reference.ASSETS + ":embassyFinder");
    }

    /** What the finder hears: the embassy's site, its record (null - not made yet: «без позывного»). */
    public static final class Heard {
        public final EmbassySiteSC.Site site;
        public final SettlementRegistrySC.Settlement record;

        Heard(EmbassySiteSC.Site site, SettlementRegistrySC.Settlement record) {
            this.site = site;
            this.record = record;
        }

        /** The chat's name: the record's «Посольство „Тантал-1“», null for an unmade one. */
        public IChatComponent name() {
            return record == null ? null : SettlementSC.displayName(record);
        }
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player instanceof FakePlayer) {
            return stack;
        }
        long now = world.getTotalWorldTime();
        Long last = LAST.get(player.getUniqueID());
        if (last != null && now >= last.longValue() && now - last.longValue() < COOLDOWN) {
            return stack;                                       // a held button: one reading in COOLDOWN
        }
        LAST.put(player.getUniqueID(), Long.valueOf(now));
        if (world.provider == null || world.provider.dimensionId != EmbassySiteSC.DIM || !(world instanceof WorldServer)) {
            tell(player, EnumChatFormatting.GRAY, new ChatComponentTranslation("sc.finder.embassy.offworld"));
            return stack;
        }
        int px = MathHelper.floor_double(player.posX), pz = MathHelper.floor_double(player.posZ);
        Heard h = heard((WorldServer) world, px, pz);
        if (h == null) {
            tell(player, EnumChatFormatting.GRAY, new ChatComponentTranslation("sc.finder.embassy.none", String.valueOf(SEARCH)));
            return stack;
        }
        tell(player, EnumChatFormatting.AQUA, reading(h, px, pz));
        world.playSoundAtEntity(player, "random.click", 0.5F, 1.4F);
        return stack;
    }

    /**
     * The nearest embassy on the air within SEARCH of (x, z) of the server's Overworld `w` - made, or to be made on unexplored ground
     * (see the class comment) - or null. Reads the biome layers of the regions round (cached after): server thread.
     */
    public static Heard heard(WorldServer w, int x, int z) {
        if (w == null || !SettlementSC.embassiesGenerate() || !EmbassySiteSC.worldFits(w)) {
            return null;
        }
        // the near rings first: an answer there is the nearest of all (the far regions' biomes are read only when it isn't)
        for (int reach : new int[]{SEARCH / 3, 2 * SEARCH / 3, SEARCH}) {
            Heard h = heard(w, x, z, reach);
            if (h != null) {
                return h;
            }
        }
        return null;
    }

    /** heard() within `reach`. */
    private static Heard heard(WorldServer w, int x, int z, int reach) {
        SettlementRegistrySC reg = SettlementRegistrySC.get(w);
        MapGenEmbassySC gen = EmbassySC.mapGen(w);
        Heard made = null;                                      // the nearest record within the reach
        double best = (double) reach * reach;
        for (SettlementRegistrySC.Settlement e : reg == null ? new ArrayList<SettlementRegistrySC.Settlement>() : reg.embassies()) {
            if (e.dim == EmbassySiteSC.DIM && e.distSq(x, z) <= best) {
                best = e.distSq(x, z);
                made = new Heard(EmbassySiteSC.ofRecord(e), e);
            }
        }
        for (EmbassySiteSC.Site s : EmbassySiteSC.near(w, x, z, reach)) {
            if (made != null && s.distSq(x, z) >= best) {
                break;                                          // the record is nearer than every site left
            }
            SettlementRegistrySC.Settlement rec = reg == null ? null : reg.atCentre(EmbassySiteSC.DIM, s.x, s.z);
            if (rec != null && SettlementSC.isEmbassy(rec)) {
                return new Heard(s, rec);
            }
            StructureEmbassyStartSC st = gen == null ? null : gen.startAt(w, s.chunkX(), s.chunkZ());
            if (st != null) {
                if (st.isSizeableStructure()) {
                    return new Heard(s, null);                  // built, its record lost (RosterSC.restore makes it again): no call sign yet
                }
                continue;                                       // an empty start: old land, no city there
            }
            if (!EmbassySiteSC.explored(w, s) && !EmbassySiteSC.zoneNear(w, s)) {
                return new Heard(s, null);                      // not made yet: it will be when someone comes
            }
        }
        return made;
    }

    /** The finder's line for `h` heard from (x, z): «Посольство „…“: северо-восток, 2400 блоков» (unmade: «без позывного»). */
    public static IChatComponent reading(Heard h, int x, int z) {
        int dx = h.site.x - x, dz = h.site.z - z;
        ChatComponentTranslation dir = new ChatComponentTranslation("sc.finder.dir." + ItemRuinFinderSC.DIRS[ItemRuinFinderSC.direction(dx, dz)]);
        String dist = String.valueOf(distance(dx, dz));
        IChatComponent name = h.name();
        return name != null ? new ChatComponentTranslation("sc.finder.embassy", name, dir, dist)
                : new ChatComponentTranslation("sc.finder.embassy.nameless", dir, dist);
    }

    /** Whole blocks across from an offset. Pure. */
    public static int distance(int dx, int dz) {
        return (int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz));
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, IChatComponent m) {
        m.getChatStyle().setColor(colour);
        p.addChatComponentMessage(m);
    }

    /** Server stopped: the cooldowns go (the next world's clock is another). EmbassyHintSC.clearAll calls it. */
    public static void clearAll() {
        LAST.clear();
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.embassyFinder"), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.embassyFinder.details", SEARCH), Lang.tr("sc.tooltip.embassyFinder.howto"));
    }
}
