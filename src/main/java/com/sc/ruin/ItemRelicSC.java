package com.sc.ruin;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModItems;
import com.sc.manual.Lang;
import com.sc.settle.CrumbLimitSC;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagCompound;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.ChatStyle;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;
import net.minecraftforge.common.util.FakePlayer;

/**
 * PACKAGE C (docs/plan-substrate-tech-f4.md §8.7, rules ПЗ-7, ПХ-3, ПТ-17): «Реликвии» - meta (fixed, append only):
 * <ul>
 * <li>CAMP_KEY «Ключ лагеря» - the Warlord's (100%); its NBT KEY_NBT {x, y, z} the safe of its camp - only that safe opens with
 * it (and only with the camp's field down); spent opening it;</li>
 * <li>KEY_FRAGMENT «Фрагмент Ключа Пробоя» - the Vault's Sentinel's (100%); four make the Breakdown Key (the boss's console -
 * the next phase); stack 4;</li>
 * <li>SEALED_CRUMB «Опечатанная Крупица» - every world source of a Singularity Crumb but the boss (the ruins' and Vaults'
 * chests, the Sentinel 25%, «Затмение» 30%): a right click opens it into a Crumb if CrumbLimitSC.take allows today, else it
 * stays sealed («лимит на сегодня исчерпан» - try tomorrow).</li>
 * </ul>
 * Fixed (the coordinator's): the class, the metas and names, the NBT key; the rest C's.
 * <p>
 * The Sealed Crumb opens in any world (the limit is the player's, by UUID, over every world - CrumbLimitSC); the server
 * decides, a FakePlayer never opens one (no player behind it, no limit to count). The key's safe: campKey / safeOf (the
 * Warlord's drop - package M - builds it from its NBT scCampSafe with campKeyFor).
 */
public class ItemRelicSC extends Item {

    public static final int CAMP_KEY = 0, KEY_FRAGMENT = 1, SEALED_CRUMB = 2;
    public static final String[] NAMES = {"campKey", "keyFragment", "sealedCrumb"};
    /** The camp key's NBT: its safe's position. */
    public static final String KEY_NBT = "scCamp";
    /** The Warlord's entity NBT (getEntityData, set by the camp - MapGenCampSC / RuinWorldSC): its camp's safe, int[3]. */
    public static final String WARLORD_SAFE_NBT = "scCampSafe";
    /** How many Fragments make the Breakdown Key (the next phase) - its stack. */
    public static final int FRAGMENTS = 4;

    private IIcon[] icons;

    public ItemRelicSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".relic");
    }

    public static String nameOf(int meta) {
        return NAMES[meta >= 0 && meta < NAMES.length ? meta : 0];
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + nameOf(stack.getItemDamage());
    }

    @Override
    public int getItemStackLimit(ItemStack stack) {
        return stack.getItemDamage() == CAMP_KEY ? 1 : stack.getItemDamage() == KEY_FRAGMENT ? FRAGMENTS : 64;
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return stack.getItemDamage() == KEY_FRAGMENT ? EnumRarity.rare : EnumRarity.uncommon;
    }

    // ------------------------------------------------------------------ the camp's key

    /** A camp's key to the safe at (x, y, z) (null before init). */
    public static ItemStack campKey(int x, int y, int z) {
        ItemStack s = RelicItemsSC.relic(CAMP_KEY, 1);
        if (s != null) {
            NBTTagCompound t = new NBTTagCompound();
            t.setInteger("x", x);
            t.setInteger("y", y);
            t.setInteger("z", z);
            NBTTagCompound tag = new NBTTagCompound();
            tag.setTag(KEY_NBT, t);
            s.setTagCompound(tag);
        }
        return s;
    }

    /** ПЗ-7 / §7.2: the key a Warlord drops - its camp's safe from its NBT (WARLORD_SAFE_NBT); a Warlord of no camp: a key to nothing. */
    public static ItemStack campKeyFor(Entity warlord) {
        int[] safe = warlord == null ? null : warlord.getEntityData().getIntArray(WARLORD_SAFE_NBT);
        if (safe == null || safe.length != 3) {
            return RelicItemsSC.relic(CAMP_KEY, 1);
        }
        return campKey(safe[0], safe[1], safe[2]);
    }

    /** The safe {x, y, z} a camp key opens, or null (not a key, a key to nothing). */
    public static int[] safeOf(ItemStack s) {
        if (s == null || s.getItem() != RelicItemsSC.relic || s.getItemDamage() != CAMP_KEY || !s.hasTagCompound()
                || !s.getTagCompound().hasKey(KEY_NBT)) {
            return null;
        }
        NBTTagCompound t = s.getTagCompound().getCompoundTag(KEY_NBT);
        return new int[]{t.getInteger("x"), t.getInteger("y"), t.getInteger("z")};
    }

    // ------------------------------------------------------------------ the Sealed Crumb (ПТ-17)

    @Override
    public ItemStack onItemRightClick(ItemStack stack, World world, EntityPlayer player) {
        if (stack.getItemDamage() != SEALED_CRUMB || world.isRemote || player instanceof FakePlayer || ModItems.singularCrumb == null) {
            return stack;
        }
        if (CrumbLimitSC.take(player, 1) < 1) {
            tell(player, "sc.crumb.limit", EnumChatFormatting.RED, String.valueOf(CrumbLimitSC.DAY_CAP));
            return stack;
        }
        if (!player.capabilities.isCreativeMode) {
            stack.stackSize--;
        }
        ItemStack crumb = new ItemStack(ModItems.singularCrumb);
        if (!player.inventory.addItemStackToInventory(crumb) || crumb.stackSize > 0) {
            player.dropPlayerItemWithRandomChoice(crumb, false);
        }
        player.inventoryContainer.detectAndSendChanges();
        world.playSoundAtEntity(player, "random.orb", 0.5F, 0.6F);
        int used = CrumbLimitSC.DAY_CAP - CrumbLimitSC.left(player);
        tell(player, "sc.crumb.opened", EnumChatFormatting.LIGHT_PURPLE, String.valueOf(used), String.valueOf(CrumbLimitSC.DAY_CAP));
        return stack;
    }

    private static void tell(EntityPlayer p, String key, EnumChatFormatting colour, Object... args) {
        ChatComponentTranslation m = new ChatComponentTranslation(key, args);
        m.setChatStyle(new ChatStyle().setColor(colour));
        p.addChatComponentMessage(m);
    }

    // ------------------------------------------------------------------ client

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        icons = new IIcon[NAMES.length];
        for (int m = 0; m < NAMES.length; m++) {
            icons[m] = register.registerIcon(Reference.ASSETS + ":relic_" + NAMES[m]);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[meta >= 0 && meta < icons.length ? meta : 0];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int m = 0; m < NAMES.length; m++) {
            list.add(new ItemStack(item, 1, m));
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        int meta = stack.getItemDamage();
        String k = "sc.tooltip.relic." + nameOf(meta);
        TooltipSC.wrap(list, Lang.tr(k), "§7");
        int[] safe = safeOf(stack);
        if (safe != null) {
            list.add("§6" + Lang.tr("sc.tooltip.relic.campKey.at", safe[0], safe[1], safe[2]));
        }
        String howto = meta == SEALED_CRUMB ? Lang.tr(k + ".howto", CrumbLimitSC.DAY_CAP)
                : meta == KEY_FRAGMENT ? Lang.tr(k + ".howto", FRAGMENTS) : Lang.tr(k + ".howto");
        TooltipSC.more(list, Lang.tr(k + ".details"), howto);
    }
}
