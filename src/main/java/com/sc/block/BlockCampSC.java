package com.sc.block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import com.sc.Reference;
import com.sc.mob.ItemCombatLootSC;
import com.sc.mob.MobItemsSC;
import com.sc.ruin.CampBlocksSC;
import com.sc.ruin.ItemRelicSC;
import com.sc.ruin.RuinLootSC;
import com.sc.ruin.RuinWorldSC;
import com.sc.tileentity.TileEntityFieldGeneratorSC;
import com.sc.tileentity.TileEntityStolenFieldSC;
import com.sc.tileentity.TileEntityStolenSolarSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.3, §8.4, rules ПЗ-4..ПЗ-7): the camp's own blocks (CampBlocksSC metas) -
 * <ul>
 * <li>STOLEN_SOLAR - «Краденая солнечная панель»: by day / at night feeds the camp's Stolen Field (TileEntityStolenSolarSC),
 * nothing in an EMP storm; breaks (drops Nano Scrap - never a working panel);</li>
 * <li>STOLEN_PAD - «Краденая зарядная плита»: the Executioner beside it gets 1 unit of reserve a second (package M asks
 * CampBlocksSC.isStolenPad); breaks, drops scrap;</li>
 * <li>CAMP_SAFE / CAMP_SAFE_OPEN - «Сейф Отступников»: unbreakable; opens (a right click) only with the camp's key in hand and its
 * field down - then it spills the camp's loot (RuinLootSC) and stays open until the camp comes back (RuinWorldSC).</li>
 * </ul>
 * Fixed (the coordinator's): the class, the metas, the tile of STOLEN_SOLAR; the rest C's.
 * <p>
 * The safe (ПЗ-7): no hardness (-1: no pick, no piston), a blast never moves it, AE2's spatial IO leaves it (SettlementCompatSC);
 * a right click - without its own camp's key in the hand «заперт» (sc.camp.safe.locked); a Stolen Field up over it «под
 * полем» (sc.camp.safe.powered); else the key is spent, the loot spills over the safe (RuinLootSC.campSafe), the block turns
 * CAMP_SAFE_OPEN (sc.camp.safe.opened) until RuinWorldSC brings the camp back. Server decides; a FakePlayer never opens it (a
 * machine has no key of its own to turn). An open safe says so. No Silk Touch for any meta (none of them is a real machine).
 * Not in a tab (found, not made); NEI shows the panel and the pad, hides the safe (package D). Icons camp_<name>.png, the panel's
 * and the pad's sides camp_side.png (tools/gen_camps_tex.py).
 */
public class BlockCampSC extends Block {

    /** What a panel and the pad give broken: Nano Scrap MIN..MAX. TODO(balance) */
    public static final int SCRAP_MIN = 1, SCRAP_MAX = 2;
    /** The safe never moves (bedrock's). */
    public static final float SAFE_RESISTANCE = 6000000F;

    private IIcon[] icons;
    private IIcon side;

    public BlockCampSC() {
        super(Material.iron);
        setHardness(3F);
        setResistance(20F);
        setStepSound(soundTypeMetal);
        setBlockName(Reference.ASSETS + ".camp");
        setHarvestLevel("pickaxe", 1);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return meta == CampBlocksSC.STOLEN_SOLAR;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return meta == CampBlocksSC.STOLEN_SOLAR ? new TileEntityStolenSolarSC() : null;
    }

    private static boolean safe(int meta) {
        return meta == CampBlocksSC.CAMP_SAFE || meta == CampBlocksSC.CAMP_SAFE_OPEN;
    }

    @Override
    public float getBlockHardness(World world, int x, int y, int z) {
        int m = world.getBlockMetadata(x, y, z);
        return safe(m) ? -1F : super.getBlockHardness(world, x, y, z);
    }

    @Override
    public float getExplosionResistance(Entity exploder, World world, int x, int y, int z, double ex, double ey, double ez) {
        return safe(world.getBlockMetadata(x, y, z)) ? SAFE_RESISTANCE : super.getExplosionResistance(exploder, world, x, y, z, ex, ey, ez);
    }

    @Override
    public int damageDropped(int meta) {
        return meta;
    }

    /** Scrap, never the block: a stolen panel or pad is wrecked getting it out; the safe gives nothing (it can't be broken). */
    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return drops(meta, world.rand);
    }

    /** What a block of `meta` gives broken (public for the self-test and the handbook). */
    public static ArrayList<ItemStack> drops(int meta, Random rand) {
        ArrayList<ItemStack> out = new ArrayList<ItemStack>();
        if (!safe(meta)) {
            ItemStack s = MobItemsSC.loot(ItemCombatLootSC.LootKind.NANO_SCRAP, SCRAP_MIN + rand.nextInt(SCRAP_MAX - SCRAP_MIN + 1));
            if (s != null) {
                out.add(s);
            }
        }
        return out;
    }

    @Override
    public boolean canSilkHarvest(World world, EntityPlayer player, int x, int y, int z, int meta) {
        return false;
    }

    // ------------------------------------------------------------------ the safe (ПЗ-7)

    /** safeAnswer: not a safe / opened already / no key of its own in the hand / its camp's dome up / it opens. */
    public static final int SAFE_NONE = -1, SAFE_EMPTY = 0, SAFE_LOCKED = 1, SAFE_POWERED = 2, SAFE_OPENS = 3;

    @Override
    public boolean onBlockActivated(World world, int x, int y, int z, EntityPlayer player, int sideHit, float hx, float hy, float hz) {
        if (!safe(world.getBlockMetadata(x, y, z))) {
            return false;
        }
        if (world.isRemote) {
            return true;
        }
        if (player instanceof FakePlayer) {
            return false;                                       // a machine has no key of its own to turn
        }
        ItemStack held = player.getCurrentEquippedItem();
        switch (safeAnswer(world, x, y, z, held)) {
            case SAFE_EMPTY:
                tell(player, "sc.camp.safe.empty", EnumChatFormatting.GRAY);
                return true;
            case SAFE_LOCKED:
                tell(player, "sc.camp.safe.locked", EnumChatFormatting.RED);
                return true;
            case SAFE_POWERED:
                tell(player, "sc.camp.safe.powered", EnumChatFormatting.GOLD);
                return true;
            default:
                break;
        }
        if (!player.capabilities.isCreativeMode) {
            held.stackSize--;
            if (held.stackSize <= 0) {
                player.inventory.setInventorySlotContents(player.inventory.currentItem, null);
            }
        }
        openSafe(world, x, y, z);
        tell(player, "sc.camp.safe.opened", EnumChatFormatting.GREEN);
        return true;
    }

    /** ПЗ-7: what the safe at (x, y, z) answers to `held` (server; the world test asks it without a player). */
    public static int safeAnswer(World world, int x, int y, int z, ItemStack held) {
        if (world.getBlock(x, y, z) != CampBlocksSC.camp || !safe(world.getBlockMetadata(x, y, z))) {
            return SAFE_NONE;
        }
        if (world.getBlockMetadata(x, y, z) == CampBlocksSC.CAMP_SAFE_OPEN) {
            return SAFE_EMPTY;
        }
        int[] key = ItemRelicSC.safeOf(held);
        if (key == null || key[0] != x || key[1] != y || key[2] != z) {
            return SAFE_LOCKED;
        }
        return poweredOver(world, x, y, z) ? SAFE_POWERED : SAFE_OPENS;
    }

    /** ПЗ-7: the safe at (x, y, z) opens (the key already spent): CAMP_SAFE_OPEN, the camp's loot spilt over it. @return the loot */
    public static List<ItemStack> openSafe(World world, int x, int y, int z) {
        world.setBlockMetadataWithNotify(x, y, z, CampBlocksSC.CAMP_SAFE_OPEN, 3);
        List<ItemStack> loot = RuinLootSC.campSafe(world.rand);
        spill(world, x, y, z, loot);
        world.playSoundEffect(x + 0.5D, y + 0.5D, z + 0.5D, "random.door_open", 1F, 0.6F);
        RuinWorldSC.safeOpened(world, x, y, z);
        return loot;
    }

    /** A Stolen Field Generator's dome stands over (x, y, z) - its camp's or any other's (server). */
    public static boolean poweredOver(World world, int x, int y, int z) {
        for (TileEntityFieldGeneratorSC f : TileEntityFieldGeneratorSC.activeFieldsIn(world)) {
            if (f instanceof TileEntityStolenFieldSC && f.fieldContains(x + 0.5D, y + 0.5D, z + 0.5D)) {
                return true;
            }
        }
        return false;
    }

    /** The loot out over the safe's top, gently. */
    private static void spill(World world, int x, int y, int z, List<ItemStack> loot) {
        for (ItemStack s : loot) {
            EntityItem e = new EntityItem(world, x + 0.5D, y + 1.1D, z + 0.5D, s);
            e.motionX = (world.rand.nextDouble() - 0.5D) * 0.12D;
            e.motionY = 0.2D;
            e.motionZ = (world.rand.nextDouble() - 0.5D) * 0.12D;
            e.delayBeforeCanPickup = 10;
            world.spawnEntityInWorld(e);
        }
    }

    private static void tell(EntityPlayer p, String key, EnumChatFormatting colour) {
        ChatComponentTranslation m = new ChatComponentTranslation(key);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    // ------------------------------------------------------------------ client

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        icons = new IIcon[CampBlocksSC.CAMP_NAMES.length];
        for (int m = 0; m < icons.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":camp_" + CampBlocksSC.CAMP_NAMES[m]);
        }
        side = register.registerIcon(Reference.ASSETS + ":camp_side");
        blockIcon = icons[0];
    }

    /** The panel and the pad: their face on top, the stolen casing round the sides and under; the safe the same all round. */
    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int s, int meta) {
        if (icons == null) {
            return null;
        }
        int m = meta >= 0 && meta < icons.length ? meta : 0;
        return s != 1 && (m == CampBlocksSC.STOLEN_SOLAR || m == CampBlocksSC.STOLEN_PAD) ? side : icons[m];
    }

    /** All the metas - NEI lists them (D hides the safe); no creative tab: found, not made. */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"unchecked", "rawtypes"})
    public void getSubBlocks(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < CampBlocksSC.CAMP_NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }
}
