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

    public static final int BOTH = 0, QUARRY = 1, EXO = 2;

    public enum Kind {
        /** x1.4 speed, x1.6 energy per module (up to 4). */
        SPEED("quarrySpeed", 4),
        /** One fortune level per module (up to V). */
        FORTUNE("quarryFortune", 5),
        SILK("quarrySilk", 1, 0, QUARRY),
        /** Ore comes out as crushed ore, like from the Crusher. */
        CRUSH("quarryCrush", 1),
        /** Crushed ore is washed too (water in the quarry's tank), like in the Ore Washer. */
        WASH("quarryWash", 1),
        PUMP("quarryPump", 1, 0, QUARRY),
        MAGNET("quarryMagnet", 1, 0, QUARRY),
        /** +8 blocks of area size per module (up to 4). */
        RADIUS("quarryRadius", 4, 0, QUARRY),
        SILENT("quarrySilent", 1),
        AUTOSTOP("quarryAutostop", 1),
        // ---- appended (the item's metadata is the ordinal) ----
        /** Cobblestone, stone, dirt, gravel, sand, netherrack go straight to nothing (1 EU an item). */
        TRASH("quarryTrash", 1),
        /** Crushed / washed ore through the Centrifuge's recipes: dust and its trace byproducts. */
        CENTRIFUGE("quarryCentrifuge", 1, 3, BOTH),
        /** An ore found: the whole vein round it too, up to 64 blocks, even past the area. */
        VEIN("quarryVein", 1, 2, QUARRY),
        /** Two blocks at a time: twice as fast, the head wears twice as fast. */
        DOUBLE("quarryDouble", 1, 3, QUARRY),
        /** Lava and water next to a dug block turn to stone - the pit doesn't flood. */
        FLUID_GUARD("quarryFluidGuard", 1, 0, QUARRY),
        /** Leaves what players build: planks, glass, wool, bricks, doors, rails, torches... */
        GENTLE("quarryGentle", 1, 0, QUARRY),
        /** Its function mends the drill head for energy - the quarry pauses until the head is whole. */
        REPAIR("quarryRepair", 1, 2, QUARRY),
        /** -25% energy a block, 20% slower. */
        ECONOMY("quarryEconomy", 1, 1, BOTH),
        /** Exo rig: +1 lens slot each (up to 8 lenses). */
        RESONATOR("quarryResonator", 4, 0, EXO),
        /** Exo rig: lenses x7 instead of x5, a haul costs 50% more. */
        STABILIZER("quarryStabilizer", 1, 0, EXO),
        /** Exo rig: other mods' ores (ore dictionary "ore...") come up too - useless without such mods. */
        DEEP_SCAN("quarryDeepScan", 1, 0, EXO),
        /** +32 000 mB to every compartment of the pump's tank (up to 4). */
        TANK("quarryTank", 4, 0, QUARRY),
        /** The pump follows a fluid it finds through the whole lake or pool, past the area (range by tier). */
        FLUID_VEIN("quarryFluidVein", 1, 0, QUARRY);

        public final String textureName;
        public final int max;
        /** The lowest quarry tier (0 LV .. 3 EV) it goes into; the Exo rig takes every tier's. */
        public final int minTier;
        /** BOTH, QUARRY only, or EXO (the drilling rig) only. */
        public final int scope;

        Kind(String textureName, int max) {
            this(textureName, max, 0, BOTH);
        }

        Kind(String textureName, int max, int minTier, int scope) {
            this.textureName = textureName;
            this.max = max;
            this.minTier = minTier;
            this.scope = scope;
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
        Kind k = kindOf(stack);
        if (k.minTier > 0) {
            list.add("§e" + Lang.tr("sc.quarrymodule.tooltip.tier", com.sc.energy.Tier.values()[k.minTier].name()));
        }
        if (k.scope != BOTH) {
            list.add("§e" + Lang.tr(k.scope == EXO ? "sc.quarrymodule.tooltip.exoonly" : "sc.quarrymodule.tooltip.quarryonly"));
        }
        com.sc.util.TooltipSC.more(list, Lang.tr("sc.quarrymodule.tooltip." + k.name().toLowerCase(java.util.Locale.ROOT)),
                Lang.tr("sc.quarrymodule.tooltip.slot", k.max));
        String working = com.sc.inventory.GuiQuarrySC.moduleWorkingLine(stack);   // tooltips are client-only; NEI draws them too
        if (working != null) {
            list.add(working);
        }
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
