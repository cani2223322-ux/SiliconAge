package com.sc.ruin;

import java.util.List;

import com.sc.Reference;
import com.sc.dim.ChunkProviderSubstrateSC;
import com.sc.dim.SubstrateSC;
import com.sc.dim.SubstrateTerrainSC;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.MathHelper;
import net.minecraft.world.World;
import net.minecraft.world.WorldServer;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.7, rule ПХ-6; proposal §7 «Картограф | 10 КЧ + 1 Фокусирующая линза | Пеленгатор
 * руин (ближайшая литография или Хранилище) | Партнёр | 1»): a right click in the Substrate - the direction (eight points of the
 * compass) and the distance to the nearest abandoned lithograph (RuinLayoutSC.lithoSite) or Vault (VaultLayoutSC.nearest) within
 * SEARCH_BLOCKS, whichever is nearer, into the chat; USES uses (damage), then it burns out. Server decides; nothing in another
 * world. Fixed (the coordinator's): the class, USES, SEARCH_BLOCKS; the rest C's.
 * <p>
 * It looks at the pure layout - the lithographs of the plateau cells within LITHO_CELLS of the player's (SEARCH_BLOCKS x 4) and
 * the Vaults of the 4 x 4 blocks of crossings within SEARCH_BLOCKS of his - and passes over a site whose structure was made
 * empty or whose ground was explored before (Ф3 / Ф4: no ruin there - its start, made later, will be empty; gone). A reading
 * costs a use; «nothing near» costs none. The direction is from the player's feet, north = -Z (n, ne, e, se, s, sw, w, nw -
 * lang sc.finder.dir.*).
 */
public class ItemRuinFinderSC extends Item {

    /** ПХ-6: uses before it burns out; how far it looks (Vault blocks of 4 x 4 crossings each way; lithograph cells: x 4). TODO(balance) */
    public static final int USES = 8, SEARCH_BLOCKS = 2;
    /** Plateau cells round the player's searched for a lithograph (8: as far as the Vaults' blocks). */
    public static final int LITHO_CELLS = SEARCH_BLOCKS * VaultLayoutSC.BLOCK;
    /** The eight points of the compass, clockwise from north (lang sc.finder.dir.<key>). */
    public static final String[] DIRS = {"n", "ne", "e", "se", "s", "sw", "w", "nw"};

    public ItemRuinFinderSC() {
        setMaxStackSize(1);
        setMaxDamage(USES);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".ruinFinder");
        setTextureName(Reference.ASSETS + ":ruinFinder");
    }

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (world.isRemote || player instanceof FakePlayer) {
            return stack;
        }
        if (!SubstrateSC.is(world)) {
            tell(player, EnumChatFormatting.GRAY, "sc.finder.offworld");
            return stack;
        }
        int px = MathHelper.floor_double(player.posX), pz = MathHelper.floor_double(player.posZ);
        int[] at = reading(world, px, pz);
        if (at == null) {
            tell(player, EnumChatFormatting.GRAY, "sc.finder.none");
            return stack;
        }
        int dx = at[1] - px, dz = at[2] - pz;
        ChatComponentTranslation m = new ChatComponentTranslation(at[0] == VAULT ? "sc.finder.vault" : "sc.finder.litho",
                new ChatComponentTranslation("sc.finder.dir." + DIRS[direction(dx, dz)]),
                String.valueOf((int) Math.round(Math.sqrt(dx * (double) dx + dz * (double) dz))),
                String.valueOf(at[1]), String.valueOf(at[2]));            // ПМ-3: and where - X, Z
        m.setChatStyle(new ChatStyle().setColor(EnumChatFormatting.AQUA));
        player.addChatComponentMessage(m);
        world.playSoundAtEntity(player, "random.click", 0.5F, 1.6F);
        if (!player.capabilities.isCreativeMode) {
            if (stack.getItemDamage() + 1 >= USES) {
                player.renderBrokenItemStack(stack);            // the USES-th reading was its last
                stack.stackSize--;
            } else {
                stack.setItemDamage(stack.getItemDamage() + 1);
            }
        }
        return stack;
    }

    /** reading's kinds. */
    public static final int LITHOGRAPH = 0, VAULT = 1;
    /**
     * ПМ-3 (audit 2026-10-20): a ruin nearer than this to the player is passed over - standing in a lithograph or a Vault (looted
     * as like as not) the finder points to the next one.
     */
    public static final int SKIP_NEAR = 48;

    /**
     * ПХ-6: the reading at (px, pz) of the Substrate world `world` - {LITHOGRAPH or VAULT, x, z} of the nearer of the nearest
     * lithograph (its hall's middle) and the nearest Vault (its crossing), or null when neither is within reach. Server (the
     * world test asks it too).
     */
    public static int[] reading(World world, int px, int pz) {
        long seed = world.getSeed();
        ChunkProviderSubstrateSC gen = generator(world);
        // the nearest lithograph
        int[] litho = null;
        double lithoD = Double.MAX_VALUE;
        int cx0 = CampLayoutSC.floorDiv(px, SubstrateTerrainSC.GRID), cz0 = CampLayoutSC.floorDiv(pz, SubstrateTerrainSC.GRID);
        for (int cx = cx0 - LITHO_CELLS; cx <= cx0 + LITHO_CELLS; cx++) {
            for (int cz = cz0 - LITHO_CELLS; cz <= cz0 + LITHO_CELLS; cz++) {
                RuinLayoutSC.LithoSite s = RuinLayoutSC.lithoSite(seed, cx, cz);
                if (s == null) {
                    continue;
                }
                double d = dist2(px, pz, s.x, s.z);
                int r = RuinLayoutSC.HALL_HALF;                 // MapGenLithographSC.getStructureStart's box
                if (d < (double) SKIP_NEAR * SKIP_NEAR || d >= lithoD || gen != null
                        && gone(world, gen.lithographs().startAt(s.x >> 4, s.z >> 4), s.x - r, s.z - r, s.x + r, s.z + r)) {
                    continue;
                }
                lithoD = d;
                litho = new int[]{s.x, s.z};
            }
        }
        // the nearest Vault
        int[] vault = null;
        double vaultD = Double.MAX_VALUE;
        int span = SubstrateTerrainSC.GRID * VaultLayoutSC.BLOCK;
        int bi0 = CampLayoutSC.floorDiv(px, span), bj0 = CampLayoutSC.floorDiv(pz, span);
        for (int bi = bi0 - SEARCH_BLOCKS; bi <= bi0 + SEARCH_BLOCKS; bi++) {
            for (int bj = bj0 - SEARCH_BLOCKS; bj <= bj0 + SEARCH_BLOCKS; bj++) {
                int[] v = VaultLayoutSC.vaultOf(seed, bi, bj);
                if (v == null) {
                    continue;
                }
                int x = v[0] * SubstrateTerrainSC.GRID, z = v[1] * SubstrateTerrainSC.GRID, r = RuinLayoutSC.NODE_HALF;
                double d = dist2(px, pz, x, z);
                if (d < (double) SKIP_NEAR * SKIP_NEAR || d >= vaultD || gen != null && gone(world, gen.vaults().startAt(x >> 4, z >> 4), x - r, z - r, x + r, z + r)) {
                    continue;                                   // the box: MapGenVaultSC.getStructureStart's
                }
                vaultD = d;
                vault = new int[]{x, z};
            }
        }
        if (litho == null && vault == null) {
            return null;
        }
        boolean toVault = vault != null && vaultD <= lithoD;
        int[] at = toVault ? vault : litho;
        return new int[]{toVault ? VAULT : LITHOGRAPH, at[0], at[1]};
    }

    /** Pure: the compass point 0..7 (n, ne, e, se, s, sw, w, nw) of the offset (dx, dz) - north is -Z. */
    public static int direction(int dx, int dz) {
        if (dx == 0 && dz == 0) {
            return 0;
        }
        double deg = Math.toDegrees(Math.atan2(dx, -dz));      // 0 north, 90 east
        return ((int) Math.round(deg / 45D) % 8 + 8) % 8;
    }

    private static double dist2(int ax, int az, int bx, int bz) {
        double dx = bx - ax, dz = bz - az;
        return dx * dx + dz * dz;
    }

    /**
     * No ruin at the site: its start was made empty (explored ground), or - not made yet - a chunk of its box [x0..x1] x [z0..z1]
     * is on disk or loaded already (old land: getStructureStart will make it empty when the player comes). exploredNoCreate:
     * never makes an empty region file for land far off. Server.
     */
    private static boolean gone(World w, StructureRuinStartSC st, int x0, int z0, int x1, int z1) {
        if (st != null) {
            return !st.isSizeableStructure();
        }
        return StructureRuinStartSC.exploredNoCreate(w, x0, z0, x1, z1);
    }

    /** The Substrate's own generator behind a server world (null if another mod wrapped it). */
    static ChunkProviderSubstrateSC generator(World w) {
        if (!(w instanceof WorldServer) || ((WorldServer) w).theChunkProviderServer == null) {
            return null;
        }
        Object p = ((WorldServer) w).theChunkProviderServer.currentChunkProvider;
        return p instanceof ChunkProviderSubstrateSC ? (ChunkProviderSubstrateSC) p : null;
    }

    private static void tell(EntityPlayer p, EnumChatFormatting colour, String key) {
        ChatComponentTranslation m = new ChatComponentTranslation(key);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.ruinFinder"), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.ruinFinder.details"), Lang.tr("sc.tooltip.ruinFinder.howto", USES - stack.getItemDamage()));
    }
}
