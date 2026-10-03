package com.sc;

import java.util.Iterator;
import java.util.List;

import com.sc.tileentity.TileEntityFieldGeneratorSC;

import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import net.minecraft.world.ChunkPosition;
import net.minecraftforge.event.world.ExplosionEvent;

/**
 * Field Generator shields vs explosions (Forge event bus): blocks inside an active field are
 * taken off the explosion's destroy list, for TileEntityFieldGeneratorSC.EXPLOSION_COST EU per
 * explosion from that cluster's master. Entities still take the blast - the shield keeps the
 * base standing, it isn't armour.
 */
public class ShieldEventHandler {

    /** Galacticraft's modid (checked in its jar's mcmod.info / @Mod: "GalacticraftCore"). */
    public static final String GALACTICRAFT = "GalacticraftCore";

    /**
     * Made once in preInit (SCMod): with Galacticraft loaded, its suffocation event is handled too
     * (compat.GalacticraftSC) - without it that class is never loaded at all.
     */
    public ShieldEventHandler() {
        if (cpw.mods.fml.common.Loader.isModLoaded(GALACTICRAFT)) {
            net.minecraftforge.common.MinecraftForge.EVENT_BUS.register(new com.sc.compat.GalacticraftSC());
        }
    }

    /**
     * A player allowed to fly (the Exo flight function) takes no fall damage in vanilla and gets no
     * LivingFallEvent - without this the flight function was also free fall immunity. Charged as a
     * normal fall, softened by the boots like any other.
     */
    @SubscribeEvent
    public void onFlyableFall(net.minecraftforge.event.entity.player.PlayerFlyableFallEvent event) {
        net.minecraft.entity.player.EntityPlayer p = event.entityPlayer;
        if (p.worldObj.isRemote || p.capabilities.isCreativeMode || !p.getEntityData().getBoolean("scArmorFlight")) {
            return;
        }
        float distance = com.sc.item.ArmorLogicSC.fall(p, event.distance);
        net.minecraft.potion.PotionEffect jump = p.getActivePotionEffect(net.minecraft.potion.Potion.jump);
        int damage = (int) Math.ceil(distance - 3 - (jump == null ? 0 : jump.getAmplifier() + 1));
        if (damage > 0) {
            p.attackEntityFrom(net.minecraft.util.DamageSource.fall, damage);
        }
    }

    /** A player's death puts their searchlight's light out (Forge's death event is on this bus). */
    @SubscribeEvent
    public void onDeath(net.minecraftforge.event.entity.living.LivingDeathEvent event) {
        if (event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer && !event.entityLiving.worldObj.isRemote) {
            com.sc.item.ArmorLogicSC.clearLight((net.minecraft.entity.player.EntityPlayer) event.entityLiving);
        }
    }

    /** Quantum / Exo jump boots spend a little hydrogen a jump (ArmorLogicSC.jumped; the server sees the jump too). */
    @SubscribeEvent
    public void onJump(net.minecraftforge.event.entity.living.LivingEvent.LivingJumpEvent event) {
        if (event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer && !event.entityLiving.worldObj.isRemote) {
            com.sc.item.ArmorLogicSC.jumped((net.minecraft.entity.player.EntityPlayer) event.entityLiving);
        }
    }

    /** Armour boots soften falls (ArmorLogicSC.fall) - Forge's fall event is on this bus too. */
    @SubscribeEvent
    public void onFall(net.minecraftforge.event.entity.living.LivingFallEvent event) {
        if (event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer && !event.entityLiving.worldObj.isRemote) {
            event.distance = com.sc.item.ArmorLogicSC.fall((net.minecraft.entity.player.EntityPlayer) event.entityLiving, event.distance);
        }
    }

    /** The drills dig slowly without energy or overheated, slower in economy / area modes (DrillLogicSC.breakSpeed). */
    @SubscribeEvent
    public void onBreakSpeed(net.minecraftforge.event.entity.player.PlayerEvent.BreakSpeed event) {
        event.newSpeed = com.sc.item.DrillLogicSC.breakSpeed(event.entityPlayer, event.block, event.metadata, event.newSpeed);
    }

    /** Blocking with a lit energy blade (energy block): the hit is halved once more, paid in EU. */
    @SubscribeEvent
    public void onHurt(net.minecraftforge.event.entity.living.LivingHurtEvent event) {
        if (event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer && !event.entityLiving.worldObj.isRemote) {
            net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) event.entityLiving;
            event.ammount = com.sc.item.BladeLogicSC.onHurt(p, event.source, event.ammount);
            event.ammount = com.sc.item.ArmorLogicSC.argonLava(p, event.source, event.ammount);   // argon: lava hurts half
            event.ammount = com.sc.item.ArmorLogicSC.voidDamage(p, event.source, event.ammount);  // К9: the void hurts half
            event.ammount = com.sc.item.ArmorLogicSC.horizonHurt(p, event.source, event.ammount); // Н2: 30% into EU
            com.sc.item.ArmorLogicSC.anchorHit(p, event.source);                                  // П3: no knockback from this hit
        }
    }

    /** What a player throws is marked (the Singular magnet leaves it alone a moment, ArmorLogicSC.magnetPulls). */
    @SubscribeEvent
    public void onToss(net.minecraftforge.event.entity.item.ItemTossEvent event) {
        if (event.player != null && event.entityItem != null && !event.entityItem.worldObj.isRemote) {
            event.entityItem.getEntityData().setString(com.sc.item.ArmorLogicSC.TOSSED_BY, event.player.getUniqueID().toString());
        }
    }

    /** Full Exo set: the energy shield lets nothing through, explosion proofing stops explosions (ArmorLogicSC.exoStops). */
    @SubscribeEvent
    public void onAttacked(net.minecraftforge.event.entity.living.LivingAttackEvent event) {
        if (!(event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer)) {
            return;
        }
        net.minecraft.entity.player.EntityPlayer p = (net.minecraft.entity.player.EntityPlayer) event.entityLiving;
        // stuck in a block: the helmet's oxygen keeps the wearer breathing (paid once a second, ArmorLogicSC)
        if (com.sc.item.ArmorLogicSC.stopsSuffocation(p, event.source) || com.sc.item.ArmorLogicSC.witherStops(p, event.source)
                || com.sc.item.ArmorLogicSC.horizonStops(p, event.source) || com.sc.item.ArmorLogicSC.exoStops(p, event.source, event.ammount)) {
            event.setCanceled(true);       // К9 takes the wither off, Н2 swallows the projectile, the Exo shield stops the rest
        }
    }

    @SubscribeEvent
    public void onDetonate(ExplosionEvent.Detonate event) {
        // explosion-proof Exo players: off the list - no damage and no push
        if (!event.world.isRemote) {
            for (Iterator<net.minecraft.entity.Entity> it = event.getAffectedEntities().iterator(); it.hasNext(); ) {
                net.minecraft.entity.Entity e = it.next();
                if (e instanceof net.minecraft.entity.player.EntityPlayer
                        && (com.sc.item.ArmorLogicSC.explosionProof((net.minecraft.entity.player.EntityPlayer) e)
                        || com.sc.item.ArmorLogicSC.anchorExplosion((net.minecraft.entity.player.EntityPlayer) e, event.explosion))) {
                    it.remove();                    // П3: the anchor did the damage itself - no push
                }
            }
        }
        // Explosions that don't break blocks (creepers/ghasts with mobGriefing off) never cost EU.
        if (event.world.isRemote || !event.explosion.isSmoking) {
            return;
        }
        List<TileEntityFieldGeneratorSC> fields = TileEntityFieldGeneratorSC.activeFieldsIn(event.world);
        if (fields.isEmpty()) {
            return;
        }
        List<ChunkPosition> blocks = event.getAffectedBlocks();
        for (TileEntityFieldGeneratorSC field : fields) {
            if (!touches(field, event.world, blocks) || !field.pay(TileEntityFieldGeneratorSC.EXPLOSION_COST)) {
                continue;
            }
            for (Iterator<ChunkPosition> it = blocks.iterator(); it.hasNext(); ) {
                ChunkPosition p = it.next();
                if (field.fieldContains(p.chunkPosX + 0.5, p.chunkPosY + 0.5, p.chunkPosZ + 0.5)) {
                    it.remove();
                }
            }
            event.world.playSoundEffect(event.explosion.explosionX, event.explosion.explosionY, event.explosion.explosionZ,
                    "random.fizz", 1.0F, 0.8F);
            field.flash(event.explosion.explosionX, event.explosion.explosionY, event.explosion.explosionZ);
        }
    }

    // ---- field generator switches (TileEntityFieldGeneratorSC) ----

    /** "No spawning": what the field would push out doesn't spawn inside it (monsters already there are still pushed). */
    @SubscribeEvent
    public void onCheckSpawn(net.minecraftforge.event.entity.living.LivingSpawnEvent.CheckSpawn event) {
        if (event.world.isRemote) {
            return;
        }
        TileEntityFieldGeneratorSC f = TileEntityFieldGeneratorSC.fieldWith(event.world, TileEntityFieldGeneratorSC.F_NO_SPAWN,
                event.x, event.y + event.entityLiving.height / 2, event.z);
        if (f != null && f.targets(event.entityLiving)) {
            event.setResult(cpw.mods.fml.common.eventhandler.Event.Result.DENY);
        }
    }

    /** "No ender teleport": endermen (and other mobs teleporting like them) can't land inside the field. */
    @SubscribeEvent
    public void onEnderTeleport(net.minecraftforge.event.entity.living.EnderTeleportEvent event) {
        if (event.entityLiving.worldObj.isRemote || event.entityLiving instanceof net.minecraft.entity.player.EntityPlayer) {
            return;
        }
        if (TileEntityFieldGeneratorSC.fieldWith(event.entityLiving.worldObj, TileEntityFieldGeneratorSC.F_NO_ENDER,
                event.targetX, event.targetY + 1, event.targetZ) != null) {
            event.setCanceled(true);
        }
    }

    /** A private field: only its owner and access list may break or place blocks inside it or open its containers. */
    public static boolean privateFor(net.minecraft.world.World w, net.minecraft.entity.player.EntityPlayer p, int x, int y, int z) {
        if (w.isRemote || p == null) {
            return false;
        }
        TileEntityFieldGeneratorSC f = TileEntityFieldGeneratorSC.fieldWith(w, TileEntityFieldGeneratorSC.F_PRIVATE, x + 0.5, y + 0.5, z + 0.5);
        if (f == null || f.allowed(p)) {
            return false;
        }
        long now = w.getTotalWorldTime();
        if (now - p.getEntityData().getLong("scPrivateMsg") >= 20 || now < p.getEntityData().getLong("scPrivateMsg")) {
            p.getEntityData().setLong("scPrivateMsg", now);
            p.addChatComponentMessage(new net.minecraft.util.ChatComponentTranslation("sc.field.private", f.getOwner()));
        }
        return true;
    }

    @SubscribeEvent
    public void onBreak(net.minecraftforge.event.world.BlockEvent.BreakEvent event) {
        if (privateFor(event.world, event.getPlayer(), event.x, event.y, event.z)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onPlace(net.minecraftforge.event.world.BlockEvent.PlaceEvent event) {
        if (privateFor(event.world, event.player, event.x, event.y, event.z)) {
            event.setCanceled(true);
            return;
        }
        if (event.placedBlock == net.minecraft.init.Blocks.snow_layer || event.placedBlock == net.minecraft.init.Blocks.ice) {
            for (Object o : event.world.loadedTileEntityList) {   // every field, up or down: it remembers the player's own
                if (o instanceof TileEntityFieldGeneratorSC && ((TileEntityFieldGeneratorSC) o).isMaster()) {
                    ((TileEntityFieldGeneratorSC) o).playerPlacedAt(event.x, event.z);
                }
            }
        }
    }

    /** Under a rain shield lightning strikes no one (the bolt itself is put out on the world tick, CommonEventHandler). */
    @SubscribeEvent
    public void onLightning(net.minecraftforge.event.entity.EntityStruckByLightningEvent event) {
        net.minecraft.entity.Entity en = event.entity, bolt = event.lightning;
        // the bolt's place counts: one falling into a field strikes no one round it, even just outside
        if (!en.worldObj.isRemote && (TileEntityFieldGeneratorSC.rainShieldAt(en.worldObj, bolt.posX, bolt.posY, bolt.posZ) != null
                || TileEntityFieldGeneratorSC.rainShieldAt(en.worldObj, en.posX, en.posY, en.posZ) != null)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteract(net.minecraftforge.event.entity.player.PlayerInteractEvent event) {
        if (event.action != net.minecraftforge.event.entity.player.PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK) {
            return;
        }
        net.minecraft.tileentity.TileEntity te = event.world.getTileEntity(event.x, event.y, event.z);
        // any tile entity: containers, but also transformers, tanks, conduits (their settings and filters)
        if (te != null && !(te instanceof TileEntityFieldGeneratorSC)
                && privateFor(event.world, event.entityPlayer, event.x, event.y, event.z)) {
            event.setCanceled(true);
            return;
        }
        // fire and fluids aren't "placing a block" (no PlaceEvent): flint and steel, fire charges, buckets
        net.minecraft.item.ItemStack held = event.entityPlayer.getCurrentEquippedItem();
        if (held != null && (held.getItem() instanceof net.minecraft.item.ItemFlintAndSteel
                || held.getItem() instanceof net.minecraft.item.ItemFireball || held.getItem() instanceof net.minecraft.item.ItemBucket)) {
            net.minecraftforge.common.util.ForgeDirection d = net.minecraftforge.common.util.ForgeDirection.getOrientation(event.face);
            if (privateFor(event.world, event.entityPlayer, event.x + d.offsetX, event.y + d.offsetY, event.z + d.offsetZ)) {
                event.setCanceled(true);
            }
        }
    }

    /** Buckets used on air / through the private zone (FillBucketEvent covers filling and emptying). */
    @SubscribeEvent
    public void onBucket(net.minecraftforge.event.entity.player.FillBucketEvent event) {
        if (event.target == null || event.target.typeOfHit != net.minecraft.util.MovingObjectPosition.MovingObjectType.BLOCK) {
            return;
        }
        net.minecraftforge.common.util.ForgeDirection d = net.minecraftforge.common.util.ForgeDirection.getOrientation(event.target.sideHit);
        if (privateFor(event.world, event.entityPlayer, event.target.blockX, event.target.blockY, event.target.blockZ)
                || privateFor(event.world, event.entityPlayer, event.target.blockX + d.offsetX, event.target.blockY + d.offsetY,
                event.target.blockZ + d.offsetZ)) {
            event.setCanceled(true);
        }
    }

    /** Whether the blast would break a real (non-air) block inside the field - the list also holds air. */
    private static boolean touches(TileEntityFieldGeneratorSC field, net.minecraft.world.World world, List<ChunkPosition> blocks) {
        for (ChunkPosition p : blocks) {
            if (!world.isAirBlock(p.chunkPosX, p.chunkPosY, p.chunkPosZ)
                    && field.fieldContains(p.chunkPosX + 0.5, p.chunkPosY + 0.5, p.chunkPosZ + 0.5)) {
                return true;
            }
        }
        return false;
    }

    /** A private field zone: strangers can't hit or use frames, paintings, animals, carts and boats in it. */
    @SubscribeEvent
    public void onAttackEntity(net.minecraftforge.event.entity.player.AttackEntityEvent event) {
        if (com.sc.tileentity.TileEntityFieldGeneratorSC.guardsEntity(event.entityPlayer, event.target)) {
            event.setCanceled(true);
        }
    }

    @SubscribeEvent
    public void onInteractEntity(net.minecraftforge.event.entity.player.EntityInteractEvent event) {
        if (com.sc.tileentity.TileEntityFieldGeneratorSC.guardsEntity(event.entityPlayer, event.target)) {
            event.setCanceled(true);
        }
    }
}
