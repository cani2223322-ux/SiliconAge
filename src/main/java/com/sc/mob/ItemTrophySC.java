package com.sc.mob;

import java.util.List;

import com.sc.Reference;
import com.sc.block.BlockArmorStationSC;
import com.sc.bridge.BridgeMathSC;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModItems;
import com.sc.manual.Lang;
import com.sc.settle.ReputationSC;
import com.sc.settle.trade.TradeOfferSC;
import com.sc.settle.trade.TradeTableSC;
import com.sc.util.ArmorSuit;
import com.sc.util.BladeType;
import com.sc.util.TooltipSC;
import com.sc.util.WeaponType;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.EnumRarity;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;
import net.minecraft.world.World;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7, rules ПТ-6, ПТ-7): «Трофейный <предмет>» - a piece of armour or a weapon
 * taken off a Substrate mob, dead: no charge, no functions, it can't be worn or fired. It is an item of its own on
 * purpose - the player's armour code never sees it, no NBT trick can make a real piece of it. Restoring it (Ф3, package T,
 * TrophyRestoreSC: the settlement's Armourer, or one's own armour service station - a right-click with it in hand, sneaking
 * too: doesSneakBypassUse lets that click reach the station; a real item with 0 EU, no gases, chips or modules; a Singular
 * one only at the Singular station, level 1) - the tooltip's Ctrl page says where and for what, from TrophyRestoreSC's
 * prices and TradeTableSC (the Salvager buys the Nano pieces).
 * <p>
 * Metas fixed by TrophyTableSC (0..15 armour, 16..19 blades, 20..22 guns, 23 ПЭ-1, 24 ПЭ-1М). The icon is the real
 * item's own (whatever it's retextured to) with a second pass on top - trophy_overlay.png, a tag and scratches. Each meta
 * has its own name (item.siliconage.trophy.<key>.name: the Russian genders); the creative tab and NEI show only the
 * metas that already drop (TrophyTableSC.SHOWN).
 */
public class ItemTrophySC extends Item {

    private IIcon overlay;

    public ItemTrophySC() {
        setHasSubtypes(true);
        setMaxStackSize(1);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".trophy");
    }

    public ItemStack stackOf(int meta) {
        return new ItemStack(this, 1, meta);
    }

    /** The real item a trophy meta stands for (its icon, its name in the tooltip; the self-test checks all 25), or null. */
    public static ItemStack realStack(int meta) {
        if (meta >= 0 && meta < TrophyTableSC.META_BLADE) {
            Item[] pieces = ModItems.ARMOR.get(ArmorSuit.values()[meta / 4]);
            return pieces == null || pieces[meta % 4] == null ? null : new ItemStack(pieces[meta % 4]);
        }
        if (meta >= TrophyTableSC.META_BLADE && meta < TrophyTableSC.META_WEAPON) {
            Item blade = ModItems.BLADES.get(BladeType.values()[meta - TrophyTableSC.META_BLADE]);
            return blade == null ? null : new ItemStack(blade);
        }
        if (meta >= TrophyTableSC.META_WEAPON && meta < TrophyTableSC.META_PE1) {
            Item gun = ModItems.WEAPONS.get(WeaponType.values()[meta - TrophyTableSC.META_WEAPON]);
            return gun == null ? null : new ItemStack(gun);
        }
        if ((meta == TrophyTableSC.META_PE1 || meta == TrophyTableSC.META_PE1M) && ModItems.empGun != null) {
            return new ItemStack(ModItems.empGun, 1, meta == TrophyTableSC.META_PE1M ? com.sc.emp.ItemEmpGunSC.META_UPGRADED
                    : com.sc.emp.ItemEmpGunSC.META_BASIC);
        }
        return null;
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + TrophyTableSC.key(stack.getItemDamage());
    }

    @Override
    public EnumRarity getRarity(ItemStack stack) {
        return EnumRarity.uncommon;
    }

    // ------------------------------------------------------------------ look: the real item + the overlay

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        overlay = register.registerIcon(Reference.ASSETS + ":trophy_overlay");
        itemIcon = overlay;                     // an unknown meta: just the tag
    }

    @Override
    @SideOnly(Side.CLIENT)
    public boolean requiresMultipleRenderPasses() {
        return true;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        ItemStack real = realStack(meta);
        IIcon icon = real == null ? null : real.getItem().getIconFromDamage(real.getItemDamage());
        return icon != null ? icon : overlay;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamageForRenderPass(int meta, int pass) {
        return pass == 0 ? getIconFromDamage(meta) : overlay;
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (int meta : TrophyTableSC.SHOWN) {
            list.add(new ItemStack(item, 1, meta));
        }
    }

    /**
     * Ф3 (ПТ-11): a sneaking right-click with a trophy at an armour service station (the Singular one is a subclass) still
     * reaches the block - its restoring (TrophyRestoreSC.onStationClick). Anywhere else sneaking works as usual.
     */
    @Override
    public boolean doesSneakBypassUse(World world, int x, int y, int z, EntityPlayer player) {
        return world != null && world.getBlock(x, y, z) instanceof BlockArmorStationSC;
    }

    /**
     * «Нерабочий трофей: <the real name>»; Shift - what it is and where it came from; Ctrl - where and for what it is
     * restored (TrophyRestoreSC's prices: the Armourer, one's own station, the Singular station) and the Salvager's price.
     */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        ItemStack real = realStack(stack.getItemDamage());
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.trophy", real == null ? "?" : real.getDisplayName()), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.trophy.details"), howto(stack));
    }

    /** The Ctrl page: the restoring's price where it can be done (present tense), the Salvager's offer if he buys it. */
    @SideOnly(Side.CLIENT)
    private static String howto(ItemStack stack) {
        int meta = stack.getItemDamage();
        TrophyRestoreSC.Price armourer = TrophyRestoreSC.price(meta, TrophyRestoreSC.WHERE_ARMOURER);
        TrophyRestoreSC.Price station = TrophyRestoreSC.price(meta, TrophyRestoreSC.WHERE_STATION);
        TrophyRestoreSC.Price singular = TrophyRestoreSC.price(meta, TrophyRestoreSC.WHERE_SINGULAR_STATION);
        String text;
        if (armourer != null && station != null) {
            text = Lang.tr("sc.tooltip.trophy.howto", Lang.tr(ReputationSC.levelKey(armourer.level)),
                    Lang.tr("sc.restore.chips", armourer.chips) + " + " + parts(armourer.parts), parts(station.parts), num(station.eu));
        } else if (singular != null && TrophyRestoreSC.singular(meta)) {
            text = Lang.tr("sc.tooltip.trophy.howto.singular", parts(singular.parts), num(singular.eu));
        } else {
            text = Lang.tr("sc.tooltip.trophy.howto.none");
        }
        TradeOfferSC sale = TradeTableSC.salvagerSale(stack);
        if (sale != null && sale.out != null) {
            text += "\n" + Lang.tr("sc.tooltip.trophy.salvager", sale.out.count);
        }
        return text;
    }

    /** «2 × Контроллер, 2 × Чип памяти». */
    @SideOnly(Side.CLIENT)
    private static String parts(TradeOfferSC.Ingredient[] parts) {
        StringBuilder b = new StringBuilder();
        for (TradeOfferSC.Ingredient i : parts) {
            ItemStack s = i.display();
            b.append(b.length() == 0 ? "" : ", ").append(Lang.tr("sc.restore.part", i.count, s != null ? s.getDisplayName() : i.ore));
        }
        return b.toString();
    }

    /** "10 000" in Russian, "10,000" in English (the handbook's way). */
    @SideOnly(Side.CLIENT)
    private static String num(long v) {
        String s = BridgeMathSC.group(v);
        return "ru".equals(Lang.trOr("sc.book.lang", "en")) ? s : s.replace(' ', ',');
    }
}
