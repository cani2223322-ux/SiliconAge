package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.creativetab.CreativeTabs;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.IIcon;

/** Quarry modules, one metadata per Kind - they go into the quarry's upgrade slots; the Functions tab switches what they add. */
public class ItemQuarryModuleSC extends Item {

    public enum Kind {
        /** x1.4 speed, x1.6 energy per module (up to 4). */
        SPEED("quarrySpeed", 4),
        /** One fortune level per module (up to V). */
        FORTUNE("quarryFortune", 5),
        SILK("quarrySilk", 1),
        /** Ore comes out as crushed ore, like from the Crusher. */
        CRUSH("quarryCrush", 1),
        /** Crushed ore is washed too (water in the quarry's tank), like in the Ore Washer. */
        WASH("quarryWash", 1),
        PUMP("quarryPump", 1),
        MAGNET("quarryMagnet", 1),
        /** +8 blocks of area size per module (up to 4). */
        RADIUS("quarryRadius", 4),
        SILENT("quarrySilent", 1),
        AUTOSTOP("quarryAutostop", 1);

        public final String textureName;
        public final int max;

        Kind(String textureName, int max) {
            this.textureName = textureName;
            this.max = max;
        }

        public static Kind byMeta(int meta) {
            Kind[] v = values();
            return v[meta >= 0 && meta < v.length ? meta : 0];
        }
    }

    private IIcon[] icons;

    public ItemQuarryModuleSC() {
        setHasSubtypes(true);
        setMaxDamage(0);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + ".quarryModule");
    }

    public static Kind kindOf(ItemStack s) {
        return Kind.byMeta(s.getItemDamage());
    }

    public ItemStack stackOf(Kind kind) {
        return new ItemStack(this, 1, kind.ordinal());
    }

    @Override
    public String getUnlocalizedName(ItemStack stack) {
        return super.getUnlocalizedName() + "." + kindOf(stack).name().toLowerCase(java.util.Locale.ROOT);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.quarrymodule.tooltip." + kindOf(stack).name().toLowerCase(java.util.Locale.ROOT)));
        list.add("§7" + Lang.tr("sc.quarrymodule.tooltip.slot", kindOf(stack).max));
    }

    @Override
    public void registerIcons(IIconRegister register) {
        Kind[] kinds = Kind.values();
        icons = new IIcon[kinds.length];
        for (Kind k : kinds) {
            icons[k.ordinal()] = register.registerIcon(Reference.ASSETS + ":" + k.textureName);
        }
    }

    @Override
    public IIcon getIconFromDamage(int meta) {
        return icons == null ? null : icons[Kind.byMeta(meta).ordinal()];
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void getSubItems(Item item, CreativeTabs tab, List list) {
        for (Kind k : Kind.values()) {
            list.add(new ItemStack(item, 1, k.ordinal()));
        }
    }
}
