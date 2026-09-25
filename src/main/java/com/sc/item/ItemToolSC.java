package com.sc.item;

import com.sc.Reference;
import com.sc.init.ModCreativeTab;
import com.sc.util.SCToolType;

import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.item.Item;
import net.minecraft.util.IIcon;

/** One instance per SCToolType (§13.5) - see SCToolType's javadoc for why these aren't metadata variants. */
public class ItemToolSC extends Item {

    private final SCToolType type;
    private IIcon icon;

    public ItemToolSC(SCToolType type) {
        this.type = type;
        setMaxDamage(type.durability);
        setNoRepair();          // worn moulds/photomasks can't be merged back to full by the vanilla repair recipe
        setMaxStackSize(1);
        setCreativeTab(ModCreativeTab.TAB);
        setUnlocalizedName(Reference.ASSETS + "." + type.name().toLowerCase(java.util.Locale.ROOT));
    }

    public SCToolType getType() {
        return type;
    }

    @Override
    public void registerIcons(IIconRegister register) {
        icon = register.registerIcon(Reference.ASSETS + ":" + type.textureName);
    }

    @Override
    public IIcon getIconFromDamage(int damage) {
        return icon;
    }
}
