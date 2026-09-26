package com.sc.item;

import java.util.List;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.manual.Lang;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;

/** A quarry's drill head: its speed, the hardest block it takes, and how many blocks it lasts (Exo: for ever, at +50% energy). */
public class ItemDrillHeadSC extends Item {

    public enum Kind {
        STEEL("drillHeadSteel", 1, 30F, 2000),
        TUNGSTEN("drillHeadTungsten", 2, 100F, 8000),
        DIAMOND("drillHeadDiamond", 4, 100F, 32000),
        EXO("drillHeadExo", 8, 100F, 0);

        public final String name;
        public final int blocksPerSecond;
        /** Blocks harder than this are left (obsidian is 50). */
        public final float maxHardness;
        public final int life;

        Kind(String name, int blocksPerSecond, float maxHardness, int life) {
            this.name = name;
            this.blocksPerSecond = blocksPerSecond;
            this.maxHardness = maxHardness;
            this.life = life;
        }
    }

    public final Kind kind;

    public ItemDrillHeadSC(Kind kind) {
        this.kind = kind;
        setMaxStackSize(1);
        setMaxDamage(kind.life);
        setNoRepair();
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + kind.name);
        setTextureName(Reference.ASSETS + ":" + kind.name);
    }

    @Override
    @SideOnly(Side.CLIENT)
    public void addInformation(ItemStack stack, EntityPlayer player, List list, boolean advanced) {
        list.add(Lang.tr("sc.drillhead.speed", kind.blocksPerSecond));
        list.add(Lang.tr(kind.maxHardness < 50 ? "sc.drillhead.noobsidian" : "sc.drillhead.all"));
        if (kind.life > 0) {
            list.add(Lang.tr("sc.drillhead.life", kind.life - stack.getItemDamage(), kind.life));
        } else {
            list.add(Lang.tr("sc.drillhead.forever"));
        }
    }
}
