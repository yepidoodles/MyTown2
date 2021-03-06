package mytown.protection;

import cpw.mods.fml.common.FMLCommonHandler;
import cpw.mods.fml.common.eventhandler.EventPriority;
import cpw.mods.fml.common.eventhandler.SubscribeEvent;
import cpw.mods.fml.common.gameevent.*;
import cpw.mods.fml.common.gameevent.PlayerEvent;
import cpw.mods.fml.relauncher.Side;
import mytown.MyTown;
import mytown.config.Config;
import mytown.datasource.MyTownUniverse;
import mytown.entities.*;
import mytown.entities.flag.FlagType;
import mytown.protection.segment.SegmentEntity;
import mytown.proxies.DatasourceProxy;
import mytown.proxies.LocalizationProxy;
import mytown.util.BlockPos;
import mytown.util.Formatter;
import mytown.util.MyTownUtils;
import net.minecraft.block.Block;
import net.minecraft.block.ITileEntityProvider;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.init.Blocks;
import net.minecraft.item.ItemBlock;
import net.minecraft.item.ItemStack;
import net.minecraft.network.play.server.S23PacketBlockChange;
import net.minecraft.server.MinecraftServer;
import net.minecraft.tileentity.TileEntity;
import net.minecraftforge.common.DimensionManager;
import net.minecraftforge.common.util.FakePlayer;
import net.minecraftforge.event.entity.living.LivingAttackEvent;
import net.minecraftforge.event.entity.player.*;
import net.minecraftforge.event.world.BlockEvent;

import java.util.*;

/**
 * Created by AfterWind on 1/1/2015.
 * Handles all the protections
 */
public class Protections {

    private static Protections instance;
    public static Protections getInstance() {
        if(instance == null)
            instance = new Protections();
        return instance;
    }

    public Map<TileEntity, Boolean> checkedTileEntities = new HashMap<TileEntity, Boolean>();
    public Map<Entity, Boolean> checkedEntities = new HashMap<Entity, Boolean>();

    public Map<TileEntity, Resident> ownedTileEntities = new HashMap<TileEntity, Resident>();
    public int activePlacementThreads = 0;

    public int maximalRange = 0;

    private List<Protection> protections = new ArrayList<Protection>();

    // ---- All the counters/tickers for preventing check every tick ----
    private int tickerMap = 20;
    private int tickerMapStart = 20;
    private int tickerWhitelist = 600;
    private int tickerWhitelistStart = 600;
    private int itemPickupCounter = 0;

    // ---- Utility methods for accessing protections ----

    public void addProtection(Protection prot) { protections.add(prot); }
    public void removeProtection(Protection prot) { protections.remove(prot); }
    public List<Protection> getProtections() { return this.protections; }

    public void reset() {
        checkedEntities = new HashMap<Entity, Boolean>();
        checkedTileEntities = new HashMap<TileEntity, Boolean>();
        protections = new ArrayList<Protection>();
    }

    public Resident getOwnerForTileEntity(TileEntity te) {
        return this.ownedTileEntities.get(te);
    }


    // ---- Main ticking method ----

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void tick(TickEvent.WorldTickEvent ev) {
        if (ev.side == Side.CLIENT)
            return;
        // Map updates
        if (tickerMap == 0) {

            for (Map.Entry<Entity, Boolean> entry : checkedEntities.entrySet()) {
                checkedEntities.put(entry.getKey(), false);
            }
            for (Iterator<Map.Entry<TileEntity, Boolean>> it = checkedTileEntities.entrySet().iterator(); it.hasNext(); ) {
                Map.Entry<TileEntity, Boolean> entry = it.next();
                if (entry.getKey().isInvalid())
                    it.remove();
                else
                    entry.setValue(false);
            }
            tickerMap = MinecraftServer.getServer().worldServers.length * tickerMapStart;
        } else {
            tickerMap--;
        }

        // TODO: Add a command to clean up the block whitelist table periodically
        if (tickerWhitelist == 0) {
            for (Town town : MyTownUniverse.getInstance().getTownsMap().values())
                for (BlockWhitelist bw : town.getWhitelists()) {
                    if (!ProtectionUtils.isBlockWhitelistValid(bw)) {
                        DatasourceProxy.getDatasource().deleteBlockWhitelist(bw, town);
                    }
                }
            tickerWhitelist = MinecraftServer.getServer().worldServers.length * tickerWhitelistStart;
        } else {
            tickerWhitelist--;
        }


        // Entity check
        // TODO: Rethink this system a couple million times before you come up with the best algorithm :P
        for (Entity entity : (List<Entity>) ev.world.loadedEntityList) {
            // Player check, every tick
            Town town = MyTownUtils.getTownAtPosition(entity.dimension, entity.chunkCoordX, entity.chunkCoordZ);

            if (entity instanceof EntityPlayer && !(entity instanceof FakePlayer)) {
                Resident res = DatasourceProxy.getDatasource().getOrMakeResident(entity);

                if (town != null && !town.checkPermission(res, FlagType.enter, false, entity.dimension, (int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ))) {
                    res.protectionDenial(FlagType.enter.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(town.getOwnersAtPosition(entity.dimension, (int) Math.floor(entity.posX), (int) Math.floor(entity.posY), (int) Math.floor(entity.posZ)))));
                    res.knockbackPlayer();
                }
                
            } else {
                // Other entity checks
                for (Protection prot : protections) {
                    if(prot.isEntityTracked(entity.getClass())) {
                        if ((checkedEntities.get(entity) == null || !checkedEntities.get(entity)) && prot.checkEntity(entity)) {
                            //MyTown.instance.log.info("Entity " + entity.toString() + " was ATOMICALLY DISINTEGRATED!");
                            checkedEntities.remove(entity);
                            entity.setDead();
                        }
                        checkedEntities.put(entity, true);
                    }
                }
            }
        }

        // TileEntity check
        if(activePlacementThreads == 0) {
            for (TileEntity te : (Iterable<TileEntity>) ev.world.loadedTileEntityList) {
                //MyTown.instance.log.info("Checking tile: " + te.toString());
                for (Protection prot : protections) {
                    if ((checkedTileEntities.get(te) == null || !checkedTileEntities.get(te)) && prot.isTileTracked(te.getClass())) {
                        if (prot.checkTileEntity(te)) {
                            MyTownUtils.dropAsEntity(te.getWorldObj(), te.xCoord, te.yCoord, te.zCoord, new ItemStack(te.getBlockType(), 1, te.getBlockMetadata()));
                            te.getWorldObj().setBlock(te.xCoord, te.yCoord, te.zCoord, Blocks.air);
                            te.invalidate();
                            MyTown.instance.log.info("TileEntity " + te.toString() + " was ATOMICALLY DISINTEGRATED!");
                        }
                        checkedTileEntities.put(te, true);
                    }
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerAttackEntityEvent(AttackEntityEvent ev) {
        if(ev.entity.worldObj.isRemote)
            return;
        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.target.dimension, ev.target.chunkCoordX, ev.target.chunkCoordZ);
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.entityPlayer);
        if (block == null) {
            // Bypass for fakePlayers
            if(ev.entityPlayer instanceof FakePlayer && (Boolean)Wild.getInstance().getValue(FlagType.allowFakePlayers))
                return;

            if(!Wild.getInstance().checkPermission(res, FlagType.protectedEntities, true)) {
                for(Protection prot : protections) {
                    if(prot.isEntityProtected(ev.target.getClass())) {
                        ev.setCanceled(true);
                        res.sendMessage(FlagType.protectedEntities.getLocalizedProtectionDenial());
                    }
                }
            }
        } else {
            // Bypass for fakePlayers
            if(ev.entityPlayer instanceof FakePlayer && (Boolean)block.getTown().getValueAtCoords(ev.target.dimension, (int) Math.floor(ev.target.posX), (int) Math.floor(ev.target.posY), (int)Math.floor(ev.target.posZ), FlagType.allowFakePlayers))
                return;

            if (!block.getTown().checkPermission(res, FlagType.protectedEntities, true, ev.target.dimension, (int) Math.floor(ev.target.posX), (int) Math.floor(ev.target.posY), (int) Math.floor(ev.target.posZ))) {
                for (Protection prot : protections) {
                    if (prot.isEntityProtected(ev.target.getClass())) {
                        ev.setCanceled(true);
                        res.protectionDenial(FlagType.protectedEntities.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(ev.target.dimension, (int) Math.floor(ev.target.posX), (int) Math.floor(ev.target.posY), (int) Math.floor(ev.target.posZ)))));
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onBlockPlacement(BlockEvent.PlaceEvent ev) {
        if(ev.world.isRemote)
            return;
        if(onAnyBlockPlacement(ev.player, ev.itemInHand, ev.placedBlock, ev.world.provider.dimensionId, ev.x, ev.y, ev.z))
            ev.setCanceled(true);
    }

    @SubscribeEvent
    public void onMultiBlockPlacement(BlockEvent.MultiPlaceEvent ev) {
        if(ev.world.isRemote)
            return;
        if(onAnyBlockPlacement(ev.player, ev.itemInHand, ev.placedBlock, ev.world.provider.dimensionId, ev.x, ev.y, ev.z))
            ev.setCanceled(true);
    }

    /**
     * Checks against any type of block placement
     */
    public boolean onAnyBlockPlacement(EntityPlayer player, ItemStack itemInHand, Block blockType, int dimensionId, int x, int y, int z) {
        TownBlock block = DatasourceProxy.getDatasource().getBlock(dimensionId, x >> 4, z >> 4);
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(player);


        if (block == null) {
            // Bypass for fakePlayers
            if(player instanceof FakePlayer && (Boolean)Wild.getInstance().getValue(FlagType.allowFakePlayers))
                return false;

            if (!Wild.getInstance().checkPermission(res, FlagType.modifyBlocks, false)) {
                res.sendMessage(FlagType.modifyBlocks.getLocalizedProtectionDenial());
                return true;
            } else {
                // If it has permission, then check nearby
                List<Town> nearbyTowns = MyTownUtils.getTownsInRange(dimensionId, x, z, Config.placeProtectionRange, Config.placeProtectionRange);
                for (Town t : nearbyTowns) {
                    if (!t.checkPermission(res, FlagType.modifyBlocks, false)) {
                        res.protectionDenial(FlagType.modifyBlocks.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(t.getOwnersAtPosition(dimensionId, x, y, z))));
                        return true;
                    }
                }
            }
        } else {
            if(player instanceof FakePlayer && (Boolean)block.getTown().getValueAtCoords(dimensionId, x, y, z, FlagType.allowFakePlayers))
                return false;

            if (!block.getTown().checkPermission(res, FlagType.modifyBlocks, false, dimensionId, x, y, z)) {
                res.protectionDenial(FlagType.modifyBlocks.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(dimensionId, x, y, z))));
                return true;
            } else {
                // If it has permission, then check nearby
                List<Town> nearbyTowns = MyTownUtils.getTownsInRange(dimensionId, x, z, Config.placeProtectionRange, Config.placeProtectionRange);
                for (Town t : nearbyTowns) {
                    if (block.getTown() != t && !t.checkPermission(res, FlagType.modifyBlocks, false)) {
                        res.protectionDenial(FlagType.modifyBlocks.getLocalizedProtectionDenial(), Formatter.formatOwnerToString(t.getMayor()));
                        return true;
                    }
                }
            }

            if (res.hasTown(block.getTown()) && blockType instanceof ITileEntityProvider) {
                if(itemInHand != null){
                    TileEntity te = ((ITileEntityProvider) blockType).createNewTileEntity(DimensionManager.getWorld(dimensionId), itemInHand.getItemDamage());
                    if (te != null) {
                        Class<? extends TileEntity> clsTe = te.getClass();
                        ProtectionUtils.addToBlockWhitelist(clsTe, dimensionId, x, y, z, block.getTown());
                    }
                }
            }
        }
        if(blockType instanceof ITileEntityProvider) {
            if(itemInHand != null){
                TileEntity te = ((ITileEntityProvider) blockType).createNewTileEntity(DimensionManager.getWorld(dimensionId), itemInHand.getItemDamage());
                if (te != null && ProtectionUtils.isTileEntityOwnable(te.getClass())) {
                    ThreadPlacementCheck thread = new ThreadPlacementCheck(res, x, y, z, dimensionId);
                    activePlacementThreads++;
                    thread.start();
                }
            }
        }

        return false;
    }

    @SubscribeEvent
    public void onEntityInteract(EntityInteractEvent ev) {
        if(ev.entity.worldObj.isRemote)
            return;
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.entityPlayer);
        ItemStack currStack = ev.entityPlayer.getHeldItem();
        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.target.dimension, ev.target.chunkCoordX, ev.target.chunkCoordZ);

        if(ev.entityPlayer instanceof FakePlayer) {
            if(block == null) {
                if((Boolean)Wild.getInstance().getValue(FlagType.allowFakePlayers))
                    return;
            } else {
                if((Boolean)block.getTown().getValueAtCoords(ev.target.dimension, (int) Math.floor(ev.target.posX), (int) Math.floor(ev.target.posY), (int) Math.floor(ev.target.posZ), FlagType.allowFakePlayers))
                    return;
            }
        }

        for (Protection prot : protections) {
            if (prot.checkEntityRightClick(currStack, res, ev.target)) {
                ev.setCanceled(true);
                return;
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onPlayerInteract(PlayerInteractEvent ev) {
        if (ev.entityPlayer.worldObj.isRemote)
            return;

        //MyTown.instance.log.info("Fired on " + (ev.entityPlayer.worldObj.isRemote ? "client" : "server"));
        //MyTown.instance.log.info("Info: " + ev.x + ", " + ev.y + ", " + ev.z + ", " + ev.face + ", " + ev.action.toString() + ", " + ev.entityPlayer.getDisplayName());

        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.entityPlayer);
        if (res == null) {
            return;
        }

        int x = ev.x, y = ev.y, z = ev.z;
        ItemStack currentStack = ev.entityPlayer.inventory.getCurrentItem();

        if(ev.world.getBlock(x, y, z) == Blocks.air) {
            x = (int) Math.floor(ev.entityPlayer.posX);
            y = (int) Math.floor(ev.entityPlayer.posY);
            z = (int) Math.floor(ev.entityPlayer.posZ);
        }

        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.world.provider.dimensionId, x >> 4, z >> 4);
        if(ev.entityPlayer instanceof FakePlayer) {
            if(block == null) {
                if((Boolean)Wild.getInstance().getValue(FlagType.allowFakePlayers))
                    return;
            } else {
                if((Boolean)block.getTown().getValueAtCoords(ev.world.provider.dimensionId, x, y, z, FlagType.allowFakePlayers))
                    return;
            }
        }

        /*
        // Testing stuff, please ignore
        if( true) {
             try {
                 Thread.sleep(5000);
             } catch (Exception e) {
                 e.printStackTrace();
             }
            ev.setCanceled(true);
            return;
        }
        */

        // Item usage check here
        if (currentStack != null && !(currentStack.getItem() instanceof ItemBlock)) {
            for (Protection protection : protections) {
                if (ev.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK && protection.checkItem(currentStack, res, new BlockPos(x, y, z, ev.world.provider.dimensionId), ev.face) ||
                        ev.action == PlayerInteractEvent.Action.RIGHT_CLICK_AIR && protection.checkItem(currentStack, res)) {
                    ev.setCanceled(true);
                    return;
                }
            }
        }


        // Activate and access check here
        if(ev.action == PlayerInteractEvent.Action.RIGHT_CLICK_BLOCK || ev.action == PlayerInteractEvent.Action.LEFT_CLICK_BLOCK) {
            for (Protection protection : protections) {
                if (protection.checkBlockInteraction(res, new BlockPos(x, y, z, ev.world.provider.dimensionId), ev.action)) {
                    // Update the blocks so that it's synced with the player.
                    S23PacketBlockChange packet = new S23PacketBlockChange(x, y, z, ev.world);
                    packet.field_148884_e = ev.world.getBlockMetadata(x, y, z);
                    FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().sendPacketToAllPlayers(packet);
                    packet = new S23PacketBlockChange(x, y - 1, z, ev.world);
                    packet.field_148884_e = ev.world.getBlockMetadata(x, y - 1, z);
                    FMLCommonHandler.instance().getMinecraftServerInstance().getConfigurationManager().sendPacketToAllPlayers(packet);

                    ev.setCanceled(true);
                    return;
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent(priority = EventPriority.HIGHEST)
    public void onPlayerBreaksBlock(BlockEvent.BreakEvent ev) {
        if(ev.world.isRemote)
            return;
        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.world.provider.dimensionId, ev.x >> 4, ev.z >> 4);
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.getPlayer());
        if (block == null) {
            if(ev.getPlayer() instanceof FakePlayer && (Boolean)Wild.getInstance().getValue(FlagType.allowFakePlayers))
                return;

            if (!Wild.getInstance().checkPermission(res, FlagType.modifyBlocks, false)) {
                res.sendMessage(FlagType.modifyBlocks.getLocalizedProtectionDenial());
                ev.setCanceled(true);
            }
        } else {
            Town town = block.getTown();
            if(ev.getPlayer() instanceof FakePlayer && (Boolean)town.getValueAtCoords(ev.world.provider.dimensionId, ev.x, ev.y, ev.z, FlagType.allowFakePlayers))
                return;

            if (!town.checkPermission(res, FlagType.modifyBlocks, false, ev.world.provider.dimensionId, ev.x, ev.y, ev.z)) {
                res.protectionDenial(FlagType.modifyBlocks.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(town.getOwnersAtPosition(ev.world.provider.dimensionId, ev.x, ev.y, ev.z))));
                ev.setCanceled(true);
                return;
            }

            if (ev.block instanceof ITileEntityProvider) {
                TileEntity te = ((ITileEntityProvider) ev.block).createNewTileEntity(ev.world, ev.blockMetadata);
                if(te != null)
                    ProtectionUtils.removeFromWhitelist(te.getClass(), ev.world.provider.dimensionId, ev.x, ev.y, ev.z, town);
            }
        }

        if (ev.block instanceof ITileEntityProvider) {
            TileEntity te = ((ITileEntityProvider) ev.block).createNewTileEntity(ev.world, ev.blockMetadata);
            if(te != null && ProtectionUtils.isTileEntityOwnable(te.getClass())) {
                te = ev.world.getTileEntity(ev.x, ev.y, ev.z);
                ownedTileEntities.remove(te);
                MyTown.instance.log.info("Removed te " + te.toString());
            }

        }
    }

    @SuppressWarnings("unchecked")
    @SubscribeEvent
    public void onItemPickup(EntityItemPickupEvent ev) {
        if(ev.entity.worldObj.isRemote)
            return;
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.entityPlayer);
        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.entityPlayer.dimension, ev.entityPlayer.chunkCoordX, ev.entityPlayer.chunkCoordZ);
        if (block != null) {
            if (!block.getTown().checkPermission(res, FlagType.pickupItems, false, ev.item.dimension, (int) Math.floor(ev.item.posX), (int) Math.floor(ev.item.posY), (int) Math.floor(ev.item.posZ))) {
                if (itemPickupCounter == 0) {
                    res.protectionDenial(FlagType.pickupItems.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(ev.item.dimension, (int) Math.floor(ev.item.posX), (int) Math.floor(ev.item.posY), (int) Math.floor(ev.item.posZ)))));
                    itemPickupCounter = 100;
                } else
                    itemPickupCounter--;
                ev.setCanceled(true);
            }
        } else {
            if(!Wild.getInstance().checkPermission(res, FlagType.pickupItems, false)) {
                if (itemPickupCounter == 0) {
                    res.sendMessage(FlagType.pickupItems.getLocalizedProtectionDenial());
                    itemPickupCounter = 100;
                } else
                    itemPickupCounter--;
                ev.setCanceled(true);
            }
        }
    }

    @SubscribeEvent
    public void onLivingAttack(LivingAttackEvent ev) {
        if(ev.entity.worldObj.isRemote)
            return;
        if(ev.entityLiving instanceof EntityPlayer) {
            TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.entityLiving.dimension, ev.entityLiving.chunkCoordX, ev.entityLiving.chunkCoordZ);
            Resident target = DatasourceProxy.getDatasource().getOrMakeResident(ev.entityLiving);
            // If the entity that "shot" the source of damage is a Player (ex. an arrow shot by player)
            if(ev.source.getEntity() != null && ev.source.getEntity() instanceof EntityPlayer) {
                Resident source = DatasourceProxy.getDatasource().getOrMakeResident(ev.source.getEntity());
                if(block != null) {
                    if(!block.getTown().checkPermission(source, FlagType.pvp, false, ev.entityLiving.dimension, (int) Math.floor(ev.entityLiving.posX), (int) Math.floor(ev.entityLiving.posY), (int) Math.floor(ev.entityLiving.posZ))) {
                        ev.setCanceled(true);
                        source.protectionDenial(FlagType.pvp.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(ev.entityLiving.dimension, (int) Math.floor(ev.entityLiving.posX), (int) Math.floor(ev.entityLiving.posY), (int) Math.floor(ev.entityLiving.posZ)))));
                    }
                } else {
                    if(!Wild.getInstance().checkPermission(source, FlagType.pvp, false)) {
                        ev.setCanceled(true);
                        source.sendMessage(FlagType.pvp.getLocalizedProtectionDenial());
                    }
                }
            // If the entity that "shot" the source of damage is null or not a player check for specified entities that can bypass pvp flag
            } else if(ev.source.getSourceOfDamage() != null && ProtectionUtils.canEntityTrespassPvp(ev.source.getSourceOfDamage().getClass())) {
                Resident owner = null;
                for(Protection prot : protections) {
                    for(SegmentEntity segment : prot.segmentsEntities) {
                        if(segment.theClass.isAssignableFrom(ev.source.getSourceOfDamage().getClass())) {
                            owner = segment.getOwner(ev.source.getSourceOfDamage());
                        }
                    }
                }
                if (block != null) {
                    if(owner == null) {
                        if (!(Boolean)block.getTown().getValueAtCoords(ev.entityLiving.dimension, (int) Math.floor(ev.entityLiving.posX), (int) Math.floor(ev.entityLiving.posY), (int) Math.floor(ev.entityLiving.posZ), FlagType.pvp)) {
                            ev.setCanceled(true);
                            target.sendMessage(FlagType.pvp.getLocalizedTownNotification());
                        }
                    } else {
                        if (!block.getTown().checkPermission(owner, FlagType.pvp, false, ev.entityLiving.dimension, (int) Math.floor(ev.entityLiving.posX), (int) Math.floor(ev.entityLiving.posY), (int) Math.floor(ev.entityLiving.posZ))) {
                            ev.setCanceled(true);
                            owner.protectionDenial(FlagType.pvp.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(ev.entityLiving.dimension, (int) Math.floor(ev.entityLiving.posX), (int) Math.floor(ev.entityLiving.posY), (int) Math.floor(ev.entityLiving.posZ)))));
                        }
                    }
                } else {
                    if(owner == null) {
                        if (!(Boolean) Wild.getInstance().getValue(FlagType.pvp)) {
                            ev.setCanceled(true);
                            target.sendMessage(FlagType.pvp.getLocalizedTownNotification());
                        }
                    } else {
                        if(!Wild.getInstance().checkPermission(owner, FlagType.pvp, false)) {
                            ev.setCanceled(true);
                            owner.sendMessage(FlagType.pvp.getLocalizedProtectionDenial());
                        }
                    }
                }
            }
        }
    }

    @SubscribeEvent
    public void onBucketFill(FillBucketEvent ev) {
        if(ev.entity.worldObj.isRemote)
            return;
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.entityPlayer);
        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.world.provider.dimensionId, ev.target.blockX >> 4, ev.target.blockZ >> 4);
        if(block == null) {
            if(ev.entityPlayer instanceof FakePlayer && (Boolean)Wild.getInstance().getValue(FlagType.allowFakePlayers))
                return;

            if(!Wild.getInstance().checkPermission(res, FlagType.useItems, false)) {
                res.sendMessage(FlagType.useItems.getLocalizedProtectionDenial());
                ev.setCanceled(true);
            }
        } else {
            if(ev.entityPlayer instanceof FakePlayer && (Boolean)block.getTown().getValueAtCoords(ev.world.provider.dimensionId, ev.target.blockX, ev.target.blockY, ev.target.blockZ, FlagType.allowFakePlayers))
                return;

            if(!block.getTown().checkPermission(res, FlagType.useItems, false, ev.world.provider.dimensionId, ev.target.blockX, ev.target.blockY, ev.target.blockZ)) {
                res.protectionDenial(FlagType.useItems.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(ev.world.provider.dimensionId, ev.target.blockX, ev.target.blockY, ev.target.blockZ))));
                ev.setCanceled(true);
            }
        }
    }


    // Fired AFTER the teleport
    @SubscribeEvent
    public void onPlayerChangedDimension(PlayerEvent.PlayerChangedDimensionEvent ev) {
        TownBlock block = DatasourceProxy.getDatasource().getBlock(ev.player.dimension, ev.player.chunkCoordX, ev.player.chunkCoordZ);
        Resident res = DatasourceProxy.getDatasource().getOrMakeResident(ev.player);
        if(block != null) {
            if(!block.getTown().checkPermission(res, FlagType.enter, false, ev.player.dimension, (int) Math.floor(ev.player.posX), (int) Math.floor(ev.player.posY), (int) Math.floor(ev.player.posZ))) {
                // Because of badly coded teleportation code by Mojang we can only send the player back to spawn. :I
                res.respawnPlayer();
                res.protectionDenial(FlagType.enter.getLocalizedProtectionDenial(), LocalizationProxy.getLocalization().getLocalization("mytown.notification.town.owners", Formatter.formatResidentsToString(block.getTown().getOwnersAtPosition(ev.player.dimension, (int) Math.floor(ev.player.posX), (int) Math.floor(ev.player.posY), (int) Math.floor(ev.player.posZ)))));
            }
        }
    }
}
