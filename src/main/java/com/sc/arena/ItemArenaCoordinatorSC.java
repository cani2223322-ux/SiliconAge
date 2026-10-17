package com.sc.arena;

import java.util.List;

import com.sc.Reference;
import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.ruin.ItemRuinFinderSC;
import com.sc.ruin.StructureRuinStartSC;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE A (docs/plan-substrate-tech-f5.md §4.5, rule ПУ-12; proposal §9 «Координатор арены продаёт Картограф (уровень «Почётный»),
 * ещё он выпадает с любой элиты с шансом 2%»): «Координатор арены». A right click in the Substrate (server): the direction (the
 * Pelengator's eight points - ItemRuinFinderSC.DIRS, lang sc.finder.dir.*) and the distance to the nearest Breakdown Epicentre
 * (ArenaLayoutSC's arenas of SEARCH_REGIONS regions round the player's), its state (ready / a fight on / a key waiting) and its
 * coordinates into the chat; a region whose record is EMPTY (old ground: no arena) is passed over (the next nearest), and so is
 * one with no record yet whose ground was explored before or whose crossing holds a Dead Node already (its start will be empty).
 * Not used up; elsewhere - sc.arenaCoordinator.offworld. Sold by the Cartographer (16 КЧ + 1 Singularity Crumb, «Почётный», one;
 * package P's deal), dropped by an elite at MobDropsSC.ARENA_COORDINATOR_CHANCE (the coordinator's line). Stack 1, rare.
 * <p>
 * The chat keys are sc.arenaCoordinator.* (not sc.coordinator.*: those are the Space Bridge's Coordinator's - sc.coordinator.none is
 * taken). The direction is from the player's feet to the pit's middle (the crossing), north = -Z.
 * <p>
 * Fixed (the coordinator's): the class, the registry name, SEARCH_REGIONS. A's: the use, the icon (textures/items/arenaCoordinator.png),
 * the tooltip's .details / .howto.
 */
public class ItemArenaCoordinatorSC extends Item {

    /** Regions round the player's searched (each 4096 blocks). */
    public static final int SEARCH_REGIONS = 1;

    public ItemArenaCoordinatorSC() {
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".arenaCoordinator");
        setTextureName(Reference.ASSETS + ":arenaCoordinator");
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.rare;
    }

    /** ПУ-12 (server, never a FakePlayer): the nearest arena's reading into the chat. Not used up. */
    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player instanceof FakePlayer) {
            return stack;
        }
        if (!SubstrateSC.is(world)) {
            tell(player, EnumChatFormatting.GRAY, "sc.arenaCoordinator.offworld");
            return stack;
        }
        int px = MathHelper.floor_double(player.posX), pz = MathHelper.floor_double(player.posZ);
        int[] at = nearest(world, px, pz);
        if (at == null) {
            tell(player, EnumChatFormatting.GRAY, "sc.arenaCoordinator.none");
            return stack;
        }
        int x = ArenaLayoutSC.centre(at[0]), z = ArenaLayoutSC.centre(at[1]), dx = x - px, dz = z - pz;
        tell(player, EnumChatFormatting.AQUA, "sc.arenaCoordinator.arena",
                new ChatComponentTranslation("sc.finder.dir." + ItemRuinFinderSC.DIRS[ItemRuinFinderSC.direction(dx, dz)]),
                String.valueOf((int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz))), String.valueOf(x), String.valueOf(z));
        ArenaDataSC d = ArenaDataSC.get(world);
        ArenaDataSC.Arena a = d == null ? null : d.arenaAt(at[0], at[1]);
        if (a != null && a.state == ArenaDataSC.FIGHT) {
            tell(player, EnumChatFormatting.RED, "sc.arenaCoordinator.fight");
        } else if (a != null && a.keyIn) {
            tell(player, EnumChatFormatting.LIGHT_PURPLE, "sc.arenaCoordinator.keyed");
        }
        world.playSoundAtEntity(player, "random.click", 0.5F, 1.2F);
        return stack;
    }

    /**
     * ПУ-12: the crossing {i, j} of the nearest arena to (px, pz) of the Substrate world `world` among the regions within
     * SEARCH_REGIONS of the player's - an empty one passed over - or null. Server (the world test asks it too).
     */
    public static int[] nearest(World world, int px, int pz) {
        long seed = world.getSeed();
        ArenaDataSC d = ArenaDataSC.get(world);
        ChunkProviderSubstrateSC gen = ChunkProviderSubstrateSC.of(world);
        int span = SubstrateTerrainSC.GRID * ArenaLayoutSC.REGION;
        int r0 = ArenaLayoutSC.floorDiv(px, span), s0 = ArenaLayoutSC.floorDiv(pz, span);
        int[] best = null;
        double bestD = Double.MAX_VALUE;
        for (int ri = r0 - SEARCH_REGIONS; ri <= r0 + SEARCH_REGIONS; ri++) {
            for (int rj = s0 - SEARCH_REGIONS; rj <= s0 + SEARCH_REGIONS; rj++) {
                int[] c = ArenaLayoutSC.arenaOf(seed, ri, rj);
                if (c == null) {
                    continue;
                }
                double dx = ArenaLayoutSC.centre(c[0]) - (double) px, dz = ArenaLayoutSC.centre(c[1]) - (double) pz, dist = dx * dx + dz * dz;
                if (dist >= bestD || empty(world, d, gen, c[0], c[1])) {
                    continue;
                }
                bestD = dist;
                best = c;
            }
        }
        return best;
    }

    /** The arena at crossing (i, j) is or will be EMPTY: its record says so; none yet - old ground, or a Dead Node made there. */
    private static boolean empty(World world, ArenaDataSC d, ChunkProviderSubstrateSC gen, int i, int j) {
        ArenaDataSC.Arena a = d == null ? null : d.arenaAt(i, j);
        if (a != null) {
            return a.state == ArenaDataSC.EMPTY;
        }
        int x = ArenaLayoutSC.centre(i), z = ArenaLayoutSC.centre(j), r = ArenaLayoutSC.HALF, g = SubstrateTerrainSC.GRID >> 4;
        if (StructureRuinStartSC.exploredNoCreate(world, x - r, z - r, x + r, z + r)) {
            return true;                                        // never makes an empty region file for land far off
        }
        StructureRuinStartSC node = gen == null ? null : gen.nodes().startAt(i * g, j * g);
        return node != null && node.isSizeableStructure();
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.arenaCoordinator"), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.arenaCoordinator.details"), Lang.tr("sc.tooltip.arenaCoordinator.howto"));
    }
}
