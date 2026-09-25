package com.sc.client;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

import org.lwjgl.input.Keyboard;
import org.lwjgl.opengl.GL11;

import com.sc.handler.ArmorNetSC;
import com.sc.init.ModBlocks;
import com.sc.item.ArmorLogicSC;
import com.sc.item.ItemArmorSC;
import com.sc.manual.Lang;
import com.sc.util.ArmorFeature;
import com.sc.util.ArmorSuit;

import cpw.mods.fml.client.registry.ClientRegistry;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.TickEvent;
import net.minecraft.block.Block;
import net.minecraft.client.Minecraft;
import net.minecraft.client.renderer.Tessellator;
import net.minecraft.client.settings.KeyBinding;
import net.minecraft.entity.EntityLivingBase;
import net.minecraft.entity.monster.IMob;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.util.AxisAlignedBB;
import net.minecraft.util.MathHelper;
import net.minecraftforge.client.event.RenderGameOverlayEvent;
import net.minecraftforge.client.event.RenderWorldLastEvent;
import net.minecraftforge.oredict.OreDictionary;

/**
 * The suits on the client: the keys (armour settings, dash), the helmet's HUD (charge of every
 * piece, heat, power mode, set bonus) and what the helmet shows through walls - living things
 * (thermal, Exo) and ores (scanner: this mod's ores for Quantum, every ore for Exo).
 */
public class ArmorClientSC {

    public static final KeyBinding KEY_ARMOR = new KeyBinding("key.sc.armor", Keyboard.KEY_K, "key.categories.sc");
    public static final KeyBinding KEY_DASH = new KeyBinding("key.sc.dash", Keyboard.KEY_R, "key.categories.sc");

    private static final int SCAN_RADIUS = 8, SCAN_EVERY = 40, THERMAL_RANGE = 24;
    private final List<int[]> ores = new ArrayList<int[]>();
    private int scanTimer;
    private net.minecraft.world.World scannedWorld;
    /** Whether a block / meta is an ore (by the ore dictionary) - asked once each, not 4 900 times a scan. */
    private final java.util.Map<Block, java.util.Map<Integer, Boolean>> oreCache = new java.util.HashMap<Block, java.util.Map<Integer, Boolean>>();

    public static void register() {
        ClientRegistry.registerKeyBinding(KEY_ARMOR);
        ClientRegistry.registerKeyBinding(KEY_DASH);
        ArmorKeyBindsSC.load(cpw.mods.fml.common.Loader.instance().getConfigDir());
        ArmorClientSC instance = new ArmorClientSC();
        cpw.mods.fml.common.FMLCommonHandler.instance().bus().register(instance);   // ClientTickEvent
        net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(instance);       // overlay, world render
    }

    // ---- keys ----

    @SubscribeEvent
    public void onClientTick(TickEvent.ClientTickEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.phase != TickEvent.Phase.END || mc.thePlayer == null) {
            return;
        }
        if (KEY_ARMOR.isPressed() && mc.currentScreen == null) {
            mc.displayGuiScreen(new GuiArmorSC());
        }
        if (KEY_DASH.isPressed() && mc.currentScreen == null && ArmorLogicSC.active(mc.thePlayer, ArmorFeature.DASH)) {
            ArmorNetSC.CHANNEL.sendToServer(new ArmorNetSC.Message(ArmorNetSC.DASH, 0));
        }
        if (mc.currentScreen == null && mc.inGameHasFocus) {
            ArmorKeyBindsSC.tick(mc);                       // each function's own key
        } else {
            ArmorKeyBindsSC.markAllDown();
        }
        if (scannedWorld != mc.theWorld) {                      // another world / dimension / server
            ores.clear();
            scanTimer = 0;
            scannedWorld = mc.theWorld;
        }
        if (ArmorLogicSC.active(mc.thePlayer, ArmorFeature.ORE_SCANNER)) {
            if (--scanTimer <= 0) {
                scanTimer = SCAN_EVERY;
                scanOres(mc.thePlayer);
            }
        } else {
            ores.clear();
            scanTimer = 0;
        }
    }

    // ---- HUD ----

    @SubscribeEvent
    public void onOverlay(RenderGameOverlayEvent.Post event) {
        Minecraft mc = Minecraft.getMinecraft();
        if (event.type != RenderGameOverlayEvent.ElementType.ALL || mc.thePlayer == null || mc.currentScreen != null
                || mc.gameSettings.showDebugInfo) {
            return;
        }
        EntityPlayer p = mc.thePlayer;
        int y = 4;
        if (ArmorLogicSC.active(p, ArmorFeature.HUD)) {
            y = drawSuit(mc, p, y);
        }
        // the energy blade's own heat, under the suit's lines (shown with or without the helmet)
        ItemStack blade = com.sc.item.BladeLogicSC.held(p);
        if (blade != null) {
            int heat = com.sc.item.ItemBladeSC.heatPercent(blade);
            boolean hot = com.sc.item.ItemBladeSC.overheated(blade);
            mc.fontRenderer.drawStringWithShadow(Lang.tr(hot ? "sc.bladehud.overheat" : "sc.bladehud.heat", heat), 4, y > 4 ? y + 4 : y,
                    hot ? 0xFF4040 : heat > 80 ? 0xFFA040 : 0xB0B0B0);
        }
        // the drill's own heat, the same place
        ItemStack drill = com.sc.item.DrillLogicSC.held(p);
        if (drill != null) {
            int heat = com.sc.item.ItemDrillSC.heatPercent(drill);
            boolean hot = com.sc.item.ItemDrillSC.overheated(drill);
            mc.fontRenderer.drawStringWithShadow(Lang.tr(hot ? "sc.drillhud.overheat" : "sc.drillhud.heat", heat), 4, y > 4 ? y + 4 : y,
                    hot ? 0xFF4040 : heat > 80 ? 0xFFA040 : 0xB0B0B0);
        }
        GL11.glColor4f(1F, 1F, 1F, 1F);
    }

    /** The helmet's HUD: set, charge of every piece, heat, power mode. @return the y under it */
    private static int drawSuit(Minecraft mc, EntityPlayer p, int y) {
        ArmorSuit set = ArmorLogicSC.fullSet(p);
        if (set != null) {
            mc.fontRenderer.drawStringWithShadow(Lang.tr("sc.armorhud.set." + set.name().toLowerCase(Locale.ROOT)), 4, y, 0x80FF80);
            y += 10;
        }
        for (int type = 0; type < 4; type++) {
            ItemStack s = ArmorLogicSC.piece(p, type);
            if (s == null) {
                continue;
            }
            int cap = ItemArmorSC.capacityOf(s);
            int pct = cap <= 0 ? 0 : (int) ((long) ItemArmorSC.chargeOf(s) * 100 / cap);
            int color = pct > 50 ? 0x60FF60 : pct > 15 ? 0xFFD040 : 0xFF5050;
            mc.fontRenderer.drawStringWithShadow(Lang.tr("sc.armorhud.piece." + type) + ": " + pct + "%", 4, y, color);
            y += 10;
        }
        ItemStack chest = ArmorLogicSC.piece(p, 1);
        if (chest != null) {
            ArmorSuit suit = ArmorLogicSC.suitOf(chest);
            int heat = chest.hasTagCompound() ? chest.getTagCompound().getInteger("HeatSC") * 100 / suit.heatCapacity : 0;
            boolean off = chest.hasTagCompound() && chest.getTagCompound().getBoolean("ChipsOffSC");
            mc.fontRenderer.drawStringWithShadow(Lang.tr(off ? "sc.armorhud.overheat" : "sc.armorhud.heat", heat), 4, y,
                    off ? 0xFF4040 : heat > 80 ? 0xFFA040 : 0xB0B0B0);
            y += 10;
            mc.fontRenderer.drawStringWithShadow(Lang.tr("sc.armorgui.mode." + ArmorLogicSC.powerMode(p)), 4, y, 0xB0B0B0);
            y += 10;
            if (ArmorLogicSC.regenOn(p)) {
                mc.fontRenderer.drawStringWithShadow(Lang.tr("sc.armorhud.regen"), 4, y, 0xFF7090);
                y += 10;
            }
        }
        return y;
    }

    // ---- through walls ----

    private void scanOres(EntityPlayer p) {
        ores.clear();
        boolean all = ArmorLogicSC.suitOf(ArmorLogicSC.piece(p, 0)) == ArmorSuit.EXO;
        int cx = MathHelper.floor_double(p.posX), cy = MathHelper.floor_double(p.posY), cz = MathHelper.floor_double(p.posZ);
        for (int x = cx - SCAN_RADIUS; x <= cx + SCAN_RADIUS; x++) {
            for (int y = Math.max(0, cy - SCAN_RADIUS); y <= Math.min(255, cy + SCAN_RADIUS); y++) {
                for (int z = cz - SCAN_RADIUS; z <= cz + SCAN_RADIUS && ores.size() < 512; z++) {
                    Block b = p.worldObj.getBlock(x, y, z);
                    if (b == ModBlocks.oreSC || (all && isOreCached(b, p.worldObj.getBlockMetadata(x, y, z)))) {
                        ores.add(new int[]{x, y, z});
                    }
                }
            }
        }
    }

    private boolean isOreCached(Block b, int meta) {
        java.util.Map<Integer, Boolean> byMeta = oreCache.get(b);
        if (byMeta == null) {
            byMeta = new java.util.HashMap<Integer, Boolean>();
            oreCache.put(b, byMeta);
        }
        Boolean known = byMeta.get(meta);
        if (known == null) {
            known = isOre(b, meta);
            byMeta.put(meta, known);
        }
        return known;
    }

    private static boolean isOre(Block b, int meta) {
        Item item = Item.getItemFromBlock(b);
        if (item == null) {
            return false;
        }
        for (int id : OreDictionary.getOreIDs(new ItemStack(item, 1, meta))) {
            if (OreDictionary.getOreName(id).startsWith("ore")) {
                return true;
            }
        }
        return false;
    }

    @SubscribeEvent
    public void onWorldRender(RenderWorldLastEvent event) {
        Minecraft mc = Minecraft.getMinecraft();
        EntityPlayer p = mc.thePlayer;
        if (p == null) {
            return;
        }
        boolean thermal = ArmorLogicSC.active(p, ArmorFeature.THERMAL);
        if (!thermal && ores.isEmpty()) {
            return;
        }
        double px = p.lastTickPosX + (p.posX - p.lastTickPosX) * event.partialTicks;
        double py = p.lastTickPosY + (p.posY - p.lastTickPosY) * event.partialTicks;
        double pz = p.lastTickPosZ + (p.posZ - p.lastTickPosZ) * event.partialTicks;
        GL11.glPushMatrix();
        GL11.glTranslated(-px, -py, -pz);
        GL11.glDisable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_LIGHTING);
        GL11.glDisable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_BLEND);
        GL11.glBlendFunc(GL11.GL_SRC_ALPHA, GL11.GL_ONE_MINUS_SRC_ALPHA);
        GL11.glLineWidth(2F);
        Tessellator t = Tessellator.instance;
        t.startDrawing(GL11.GL_LINES);                        // every outline in one draw
        for (int[] o : ores) {
            box(t, AxisAlignedBB.getBoundingBox(o[0], o[1], o[2], o[0] + 1, o[1] + 1, o[2] + 1), 1F, 0.8F, 0.2F);
        }
        if (thermal) {
            AxisAlignedBB area = p.boundingBox.expand(THERMAL_RANGE, THERMAL_RANGE, THERMAL_RANGE);
            for (Object o : p.worldObj.getEntitiesWithinAABB(EntityLivingBase.class, area)) {
                EntityLivingBase e = (EntityLivingBase) o;
                if (e == p || e.isDead) {
                    continue;
                }
                double dx = (e.posX - e.lastTickPosX) * event.partialTicks - (e.posX - e.lastTickPosX);
                double dy = (e.posY - e.lastTickPosY) * event.partialTicks - (e.posY - e.lastTickPosY);
                double dz = (e.posZ - e.lastTickPosZ) * event.partialTicks - (e.posZ - e.lastTickPosZ);
                AxisAlignedBB b = e.boundingBox.getOffsetBoundingBox(dx, dy, dz);
                if (e instanceof IMob) {
                    box(t, b, 1F, 0.25F, 0.2F);
                } else {
                    box(t, b, 0.3F, 1F, 0.4F);
                }
            }
        }
        t.draw();
        GL11.glColor4f(1F, 1F, 1F, 1F);
        GL11.glLineWidth(1F);
        GL11.glEnable(GL11.GL_DEPTH_TEST);
        GL11.glEnable(GL11.GL_TEXTURE_2D);
        GL11.glDisable(GL11.GL_BLEND);
        GL11.glPopMatrix();
    }

    private static void box(Tessellator t, AxisAlignedBB b, float r, float g, float bl) {
        t.setColorRGBA_F(r, g, bl, 0.9F);
        double[][] c = {{b.minX, b.minY, b.minZ}, {b.maxX, b.minY, b.minZ}, {b.maxX, b.minY, b.maxZ}, {b.minX, b.minY, b.maxZ},
                {b.minX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.minZ}, {b.maxX, b.maxY, b.maxZ}, {b.minX, b.maxY, b.maxZ}};
        int[][] edges = {{0, 1}, {1, 2}, {2, 3}, {3, 0}, {4, 5}, {5, 6}, {6, 7}, {7, 4}, {0, 4}, {1, 5}, {2, 6}, {3, 7}};
        for (int[] e : edges) {
            t.addVertex(c[e[0]][0], c[e[0]][1], c[e[0]][2]);
            t.addVertex(c[e[1]][0], c[e[1]][1], c[e[1]][2]);
        }
    }
}
