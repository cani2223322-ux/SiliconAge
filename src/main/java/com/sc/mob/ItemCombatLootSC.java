package com.sc.mob;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;
import com.sc.util.TooltipSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/**
 * PACKAGE M (docs/plan-substrate-tech.md §5.7, docs/plan-substrate.md §7): «Боевая добыча» - what the Substrate's
 * factions drop under rule ПТ-1 (born in Si-14, died there, a real player's kill). Meta = LootKind ordinal: appended
 * only, worlds keep it. Until the settlements' traders come (Ф2) it has a modest use: the Crusher (MobRecipesSC). Scrap
 * never turns back into armour (ПТ-8).
 */
public class ItemCombatLootSC extends Item {

    /** The kinds, in meta order (append only). `key` - the lang / texture name (loot_<key>.png). */
    public enum LootKind {
        /** «Прошивка оболочки» - the Lineman's (100%). */
        SHELL_FIRMWARE("shellFirmware"),
        /** «Лом брони Нано» - the Marauder's (40%). */
        NANO_SCRAP("nanoScrap");

        public final String key;

        LootKind(String key) {
            this.key = key;
        }

        public static LootKind of(int meta) {
            LootKind[] all = values();
            return all[meta >= 0 && meta < all.length ? meta : 0];
        }
    }

    private IIcon[] icons;

    public ItemCombatLootSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".combatLoot");
    }

    public ItemStack stackOf(LootKind kind, int count) {
        return new ItemStack(this, count, kind.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + LootKind.of(stack.getItemDamage()).key;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void registerIcons(IIconRegister register) {
        LootKind[] all = LootKind.values();
        icons = new IIcon[all.length];
        for (LootKind k : all) {
            icons[k.ordinal()] = register.registerIcon(Reference.ASSETS + ":loot_" + k.key);
        }
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[LootKind.of(meta).ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (LootKind k : LootKind.values()) {
            list.add(new ItemStack(item, 1, k.ordinal()));
        }
    }

    /**
     * sc.tooltip.loot.<key>: what it is; Shift - where it comes from; Ctrl - what it's good for now (the Crusher's
     * numbers from MobRecipesSC: how many go in, the bonus chance in %).
     */
    @Override
    @SideOnly(Side.CLIENT)
    @SuppressWarnings({"rawtypes", "unchecked"})
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        LootKind kind = LootKind.of(stack.getItemDamage());
        String k = "sc.tooltip.loot." + kind.key;
        TooltipSC.wrap(list, Lang.tr(k), "§7");
        TooltipSC.more(list, Lang.tr(k + ".details"),
                Lang.tr(k + ".howto", MobRecipesSC.inputCount(kind), Math.round(MobRecipesSC.FIRMWARE_TRANSISTOR * 100F)));
    }
}
