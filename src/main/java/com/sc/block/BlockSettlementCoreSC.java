package com.sc.block;

import java.util.ArrayList;
import java.util.Random;
import java.util.UUID;

import com.sc.Reference;
import com.sc.settle.ReputationSC;
import com.sc.settle.SettlementProtectSC;
import com.sc.settle.SettlementRegistrySC;
import com.sc.settle.SettlementSC;
import com.sc.tileentity.TileEntitySettlementCoreSC;

import cpw.mods.fml.relauncher.Side;
import cpw.mods.fml.relauncher.SideOnly;
import net.minecraft.block.Block;
import net.minecraft.block.material.Material;
import net.minecraft.client.renderer.texture.IIconRegister;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.ChatComponentTranslation;
import net.minecraft.util.EnumChatFormatting;
import net.minecraft.util.IIcon;
import net.minecraft.world.Explosion;
import net.minecraft.world.IBlockAccess;
import net.minecraft.world.World;

/**
 * PACKAGE C (docs/plan-substrate-tech-f2.md §5.1, rule ПП-6): «Ядро поселения» - its own block (not
 * BlockFieldGeneratorSC: no screen, no drop, no item in any tab), placed only by the settlement generator (package S).
 * Unbreakable (hardness -1, a huge blast resistance, no mob may destroy it - the wither, the dragon), drops nothing;
 * hidden in NEI and on AE2's spatial blacklist (SettlementCompatSC). Its tile is TileEntitySettlementCoreSC. Package C
 * writes the icon, the light, the particles, a right-click's chat line (the settlement's name, size and your
 * standing), the TooltipSC tooltip of its ItemBlock.
 * <p>
 * Not even creative breaks it: SettlementProtectSC cancels its BreakEvent anywhere, and removedByPlayer refuses as the
 * second lock; an explosion leaves it (onBlockExploded does nothing - a mod's blast that ignores the resistance too).
 * The one exception: a core of no settlement (/give, a command) outside every settlement - a creative player takes it
 * away (SettlementProtectSC.takenAway); creative's pick-block gives no core.
 * The look: a dark casing with the turquoise of the Tuners (textures settlementCore_side / _top, tools/
 * gen_settlement_core_tex.py, pulsing), turquoise motes round it and now and then a glyph drawn into it.
 */
public class BlockSettlementCoreSC extends Block {

    /** The motes' colour (reddust's r, g, b): the shell's turquoise, TileEntitySettlementCoreSC.SHELL_RGB. */
    private static final double MOTE_R = 0.24D, MOTE_G = 0.82D, MOTE_B = 0.78D;

    private IIcon top, side;

    public BlockSettlementCoreSC() {
        super(Material.iron);
        setBlockName(Reference.ASSETS + ".settlementCore");
        setBlockTextureName(Reference.ASSETS + ":settlementCore");
        setBlockUnbreakable();
        setResistance(6000000F);
        setLightLevel(10F / 15F);
        setStepSound(soundTypeMetal);
    }

    @Override
    public boolean hasTileEntity(int meta) {
        return true;
    }

    @Override
    public TileEntity createTileEntity(World world, int meta) {
        return new TileEntitySettlementCoreSC();
    }

    @Override
    public ArrayList<ItemStack> getDrops(World world, int x, int y, int z, int meta, int fortune) {
        return new ArrayList<ItemStack>();
    }

    @Override
    public boolean canEntityDestroy(IBlockAccess world, int x, int y, int z, Entity entity) {
        return false;
    }

    @Override
    public boolean canDropFromExplosion(Explosion explosion) {
        return false;
    }

    /** No blast removes it, whatever it does with the resistance. */
    @Override
    public void onBlockExploded(World world, int x, int y, int z, Explosion explosion) {
    }

    /**
     * The second lock after SettlementProtectSC's BreakEvent: never removed by a player, creative too (a command still
     * can) - but a core of no settlement standing outside every settlement, by a creative player (takenAway).
     */
    @Override
    public boolean removedByPlayer(World world, EntityPlayer player, int x, int y, int z, boolean willHarvest) {
        return SettlementProtectSC.takenAway(player, world, x, y, z, this) && super.removedByPlayer(world, player, x, y, z, willHarvest);
    }

    /** Creative's middle click: no core - one put anywhere would hold an eternal private field there. */
    @Override
    public ItemStack getPickBlock(net.minecraft.util.MovingObjectPosition target, World world, int x, int y, int z) {
        return null;
    }

    // ------------------------------------------------------------------ the right-click: one chat line

    /**
     * «Купол «Ниобий-4» - Посёлок. Ваше «Доверие»: 120 (Знакомый)», and for a local «Враг» how long it lasts. A core of
     * no settlement says so. At most a line a second (SettlementProtectSC.say). Always true: the held item isn't used.
     */
    @Override
    public boolean onBlockActivated(World w, int x, int y, int z, EntityPlayer p, int face, float hx, float hy, float hz) {
        if (w.isRemote) {
            return true;
        }
        TileEntity te = w.getTileEntity(x, y, z);
        int id = te instanceof TileEntitySettlementCoreSC ? ((TileEntitySettlementCoreSC) te).settlementId() : -1;
        SettlementRegistrySC r = SettlementSC.registry(w);
        SettlementRegistrySC.Settlement s = r == null || id < 0 ? null : r.byId(id);
        if (s == null) {
            SettlementProtectSC.say(p, SettlementProtectSC.colour(new ChatComponentTranslation("sc.settle.core.none"),
                    EnumChatFormatting.DARK_AQUA), false);
            return true;
        }
        UUID u = p.getUniqueID();
        SettlementRegistrySC.PlayerRec pr = r.playerIfAny(u);
        int rep = pr == null ? 0 : pr.reputation;
        SettlementRegistrySC.LocalRec l = s.localIfAny(u);
        long now = w.getTotalWorldTime();
        boolean enemy = l != null && l.enemy(now);
        int level = ReputationSC.localLevel(rep, enemy);
        boolean said = SettlementProtectSC.say(p, SettlementProtectSC.colour(new ChatComponentTranslation("sc.settle.core.info",
                SettlementSC.displayName(s), new ChatComponentTranslation(s.sizeKind().langKey()), String.valueOf(rep),
                new ChatComponentTranslation(ReputationSC.levelKey(level))), EnumChatFormatting.DARK_AQUA), false);
        if (said && enemy) {
            long minutes = Math.max(1L, (l.enemyUntil - now + 1199L) / 1200L);
            SettlementProtectSC.say(p, SettlementProtectSC.colour(new ChatComponentTranslation("sc.settle.enemy.left",
                    SettlementSC.displayName(s), String.valueOf(minutes)), EnumChatFormatting.RED), true);
        }
        return true;
    }

    // ------------------------------------------------------------------ the look

    @Override
    @SideOnly(Side.CLIENT)
    public void registerBlockIcons(IIconRegister register) {
        side = register.registerIcon(Reference.ASSETS + ":settlementCore_side");
        top = register.registerIcon(Reference.ASSETS + ":settlementCore_top");
        blockIcon = side;
    }

    @Override
    @SideOnly(Side.CLIENT)
    public IIcon getIcon(int face, int meta) {
        return face <= 1 ? top : side;
    }

    /** Turquoise motes round the core, now and then a glyph drawn into it (as an enchanting table draws them). */
    @Override
    @SideOnly(Side.CLIENT)
    public void randomDisplayTick(World world, int x, int y, int z, Random rand) {
        for (int i = 0; i < 2; i++) {
            double a = rand.nextDouble() * Math.PI * 2D, d = 0.6D + rand.nextDouble() * 0.3D;
            world.spawnParticle("reddust", x + 0.5D + Math.cos(a) * d, y + 0.1D + rand.nextDouble() * 1.1D, z + 0.5D + Math.sin(a) * d,
                    MOTE_R, MOTE_G, MOTE_B);
        }
        if (rand.nextInt(3) == 0) {
            // the enchantment glyph flies from (point + v) to the point: started round the core, it ends on its top
            world.spawnParticle("enchantmenttable", x + 0.5D, y + 1.05D, z + 0.5D,
                    rand.nextDouble() * 3D - 1.5D, 0.2D + rand.nextDouble(), rand.nextDouble() * 3D - 1.5D);
        }
    }
}
