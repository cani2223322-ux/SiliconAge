package com.sc.mob;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.init.ModItems;
import com.sc.manual.Lang;
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

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7, rules ПТ-6, ПТ-7): «Трофейный <предмет>» - a piece of armour or a weapon
 * taken off a Substrate mob, dead: no charge, no functions, it can't be worn or fired. It is an item of its own on
 * purpose - the player's armour code never sees it, no NBT trick can make a real piece of it. Restoring it (the
 * settlement's Armourer, or one's own armour service station: a real item with 0 EU, no gases, chips or modules; a
 * Singular one only at the Singular station, level 1) comes in Ф3 - the tooltip says so in the future tense.
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

    /** «Нерабочий трофей: <the real name>»; Shift - what it is and where it came from; Ctrl - its restoring (Ф3). */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        ItemStack real = realStack(stack.getItemDamage());
        TooltipSC.wrap(list, Lang.tr("sc.tooltip.trophy", real == null ? "?" : real.getDisplayName()), "§7");
        TooltipSC.more(list, Lang.tr("sc.tooltip.trophy.details"), Lang.tr("sc.tooltip.trophy.howto"));
    }
}
