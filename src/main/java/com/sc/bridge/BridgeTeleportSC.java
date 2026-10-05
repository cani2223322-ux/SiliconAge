package com.sc.bridge;

import net.minecraft.entity.Entity;
import net.minecraft.entity.EntityList;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.server.MinecraftServer;
import net.minecraft.world.Teleporter;
import net.minecraft.world.WorldServer;

/**
 * Moving an entity through the bridge: in the same world a plain move; into another dimension players go
 * through the server's own transfer with a Teleporter that only puts them at the point (never builds or
 * looks for a nether portal), other entities are re-created there (as vanilla's travelToDimension does,
 * without its portal search).
 */
public final class BridgeTeleportSC {

    private BridgeTeleportSC() {
    }

    /** A Teleporter that just sets the position. */
    public static class Exact extends Teleporter {
        private final double x, y, z;
        private final float yaw;

        public Exact(WorldServer w, double x, double y, double z, float yaw) {
            super(w);
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
        }

        @Override
        public void placeInPortal(Entity e, double px, double py, double pz, float f) {
            e.setLocationAndAngles(x, y, z, yaw, e.rotationPitch);
            e.motionX = e.motionY = e.motionZ = 0;
        }

        @Override
        public boolean placeInExistingPortal(Entity e, double px, double py, double pz, float f) {
            placeInPortal(e, px, py, pz, f);
            return true;
        }

        @Override
        public boolean makePortal(Entity e) {
            return true;
        }

        @Override
        public void removeStalePortalLocations(long time) {
        }
    }

    /**
     * Puts `e` at x y z of dimension `dim` (yaw: the way it faces after).
     * @return the entity now there (a non-player crossing dimensions is a new copy), or null if it failed
     */
    public static Entity teleport(Entity e, int dim, double x, double y, double z, float yaw) {
        if (e == null || e.worldObj == null || e.worldObj.isRemote || e.isDead) {
            return null;
        }
        e.fallDistance = 0;
        if (e.worldObj.provider.dimensionId == dim) {
            if (e instanceof EntityPlayerMP) {
                ((EntityPlayerMP) e).playerNetServerHandler.setPlayerLocation(x, y, z, yaw, e.rotationPitch);
            } else {
                e.setLocationAndAngles(x, y, z, yaw, e.rotationPitch);
                e.motionX = e.motionY = e.motionZ = 0;
                if (e instanceof net.minecraft.entity.EntityLivingBase) {
                    ((net.minecraft.entity.EntityLivingBase) e).setPositionAndUpdate(x, y, z);
                }
            }
            return e;
        }
        MinecraftServer server = MinecraftServer.getServer();
        WorldServer to = server == null ? null : server.worldServerForDimension(dim);
        if (to == null) {
            return null;
        }
        if (e instanceof EntityPlayerMP) {
            EntityPlayerMP p = (EntityPlayerMP) e;
            int from = p.dimension;
            server.getConfigurationManager().transferPlayerToDimension(p, dim, new Exact(to, x, y, z, yaw));
            if (from == 1) {
                // leaving the End the server's transfer neither places nor spawns the player (it expects the credits)
                p.setLocationAndAngles(x, y, z, yaw, p.rotationPitch);
                to.spawnEntityInWorld(p);
                to.updateEntityWithOptionalForce(p, false);
            }
            p.playerNetServerHandler.setPlayerLocation(x, y, z, yaw, p.rotationPitch);
            p.fallDistance = 0;
            return p;
        }
        WorldServer fromW = (WorldServer) e.worldObj;
        Entity copy = EntityList.createEntityByName(EntityList.getEntityString(e), to);
        if (copy == null) {
            return null;
        }
        // the copy takes the data first, then the old one is emptied: a chest / hopper minecart's setDead (removeEntity)
        // would otherwise spill its contents here while the copy carries them on
        copy.copyDataFrom(e, true);
        if (e instanceof net.minecraft.inventory.IInventory) {
            net.minecraft.inventory.IInventory inv = (net.minecraft.inventory.IInventory) e;
            for (int i = 0; i < inv.getSizeInventory(); i++) {
                inv.setInventorySlotContents(i, null);
            }
        }
        fromW.removeEntity(e);
        e.isDead = false;
        copy.dimension = dim;
        copy.setLocationAndAngles(x, y, z, yaw, e.rotationPitch);
        copy.motionX = copy.motionY = copy.motionZ = 0;
        copy.fallDistance = 0;
        to.spawnEntityInWorld(copy);
        e.isDead = true;
        fromW.resetUpdateEntityTick();
        to.resetUpdateEntityTick();
        return copy;
    }
}
