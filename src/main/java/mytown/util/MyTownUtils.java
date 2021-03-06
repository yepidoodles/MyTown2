package mytown.util;

import cpw.mods.fml.common.registry.GameRegistry;
import mytown.MyTown;
import mytown.datasource.MyTownDatasource;
import mytown.entities.BlockWhitelist;
import mytown.entities.Town;
import mytown.entities.TownBlock;
import mytown.entities.flag.FlagType;
import mytown.proxies.DatasourceProxy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.item.EntityItem;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.entity.player.EntityPlayerMP;
import net.minecraft.inventory.Slot;
import net.minecraft.item.Item;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.network.play.server.S2FPacketSetSlot;
import net.minecraft.tileentity.TileEntity;
import net.minecraft.util.MathHelper;
import net.minecraft.util.MovingObjectPosition;
import net.minecraft.util.Vec3;
import net.minecraft.world.World;
import net.minecraftforge.common.DimensionManager;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

/**
 * Created by AfterWind on 9/9/2014.
 * Utils class for random useful things
 */
public class MyTownUtils {
    /**
     * Returns the town at the specified position or null if nothing found.
     */
    public static Town getTownAtPosition(int dim, int x, int z) {
        TownBlock block = getDatasource().getBlock(dim, x, z);
        if (block == null)
            return null;
        return block.getTown();
    }

    /**
     * Gets the town at the entity's position
     */
    protected static Town getTownFromEntity(Entity entity) {
        return getTownAtPosition(entity.dimension, entity.chunkCoordX, entity.chunkCoordZ);
    }

    /**
     * Returns the Flag name from the selector's Lore tag
     */
    public static String getFlagNameFromLore(EntityPlayer player) {
        ItemStack currentStack = player.inventory.getCurrentItem();
        NBTTagList lore = currentStack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
        String flagLore = lore.getStringTagAt(1);
        return flagLore.substring(8); // We use hacks in here
    }

    /**
     * Returns the Town name from the selector's Lore tag
     */
    public static String getTownNameFromLore(EntityPlayer player) {
        ItemStack currentStack = player.inventory.getCurrentItem();
        NBTTagList lore = currentStack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
        String flagLore = lore.getStringTagAt(2);
        return flagLore.substring(8);
    }

    /**
     * Gets the nearby tile entities of the specified tile entity and of the specified type
     */
    public static List<TileEntity> getNearbyTileEntity(TileEntity te, Class<? extends TileEntity> type) {
        List<TileEntity> result = new ArrayList<TileEntity>();
        int[] dx = {0, 1, 0, -1, 0, 0};
        int[] dy = {1, 0, -1, 0, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};

        for (int i = 0; i < 6; i++) {
            TileEntity found = te.getWorldObj().getTileEntity(te.xCoord + dx[i], te.yCoord + dy[i], te.zCoord + dz[i]);
            if (found != null && type.isAssignableFrom(found.getClass())) {
                MyTown.instance.log.info("Found tile entity " + found + " for class " + type.getName());
                result.add(found);
            }
        }
        return result;
    }

    /**
     * Transforms a box made out of actual coordinates to a list of all the chunks that this box is in
     */
    public static List<ChunkPos> getChunksInBox(int minX, int minZ, int maxX, int maxZ) {
        List<ChunkPos> list = new ArrayList<ChunkPos>();
        for (int i = minX >> 4; i <= maxX >> 4; i++) {
            for (int j = minZ >> 4; j <= maxZ >> 4; j++) {
                list.add(new ChunkPos(i, j));
            }
        }
        return list;
    }

    /**
     * Searches if the specified block is whitelisted in any town
     */
    public static boolean isBlockWhitelisted(int dim, int x, int y, int z, FlagType flagType) {
        Town town = getTownAtPosition(dim, x >> 4, z >> 4);
        if (town == null) return false;
        BlockWhitelist bw = town.getBlockWhitelist(dim, x, y, z, flagType);
        if (bw != null) {
            if (bw.isDeleted) {
                getDatasource().deleteBlockWhitelist(bw, town);
                return false;
            }
            return true;
        }
        return false;
    }

    /**
     * Gets all the blocks nearby the specified block position and returns only the positions which have a town in it
     * Not checking on the y axis!
     */
    public static List<BlockPos> getPositionNearby(BlockPos block) {
        List<BlockPos> list = new ArrayList<BlockPos>();
        int[] dx = {0, 1, 0, -1};
        int[] dz = {1, 0, -1, 0};

        for (int i = 0; i < 4; i++) {
            Town town = getTownAtPosition(block.dim, (block.x + dx[i]) >> 4, (block.z + dz[i]) >> 4);
            if (town != null) {
                list.add(new BlockPos(block.x + dx[i], block.y, block.z + dz[i], block.dim));
            }
        }
        return list;
    }

    /**
     * Gets all towns in a range
     */
    public static List<Town> getTownsInRange(int dim, int x, int z, int rangeX, int rangeZ) {
        List<Town> list = new ArrayList<Town>();
        for (int i = x - rangeX; i <= x + rangeX; i++) {
            for (int j = z - rangeZ; j <= z + rangeZ; j++) {
                Town town = getTownAtPosition(dim, i >> 4, j >> 4);
                if (town != null)
                    list.add(town);
            }
        }
        return list;
    }

    /**
     * Drops the specified itemstack in the worls as a EntityItem
     */
    public static void dropAsEntity(World world, int x, int y, int z, ItemStack itemStack) {
        if (itemStack == null) {
            return;
        }
        double f = 0.7D;
        double dx = world.rand.nextFloat() * f + (1.0D - f) * 0.5D;
        double dy = world.rand.nextFloat() * f + (1.0D - f) * 0.5D;
        double dz = world.rand.nextFloat() * f + (1.0D - f) * 0.5D;

        EntityItem entityItem = new EntityItem(world, x + dx, y + dy, z + dz, itemStack);
        //entityItem.field_145804_b = 10;
        world.spawnEntityInWorld(entityItem);
    }

    /**
     * Gets the position at which the player is looking
     */
    public static MovingObjectPosition getMovingObjectPositionFromPlayer(World p_77621_1_, EntityPlayer p_77621_2_, boolean p_77621_3_) {
        float f = 1.0F;
        float f1 = p_77621_2_.prevRotationPitch + (p_77621_2_.rotationPitch - p_77621_2_.prevRotationPitch) * f;
        float f2 = p_77621_2_.prevRotationYaw + (p_77621_2_.rotationYaw - p_77621_2_.prevRotationYaw) * f;
        double d0 = p_77621_2_.prevPosX + (p_77621_2_.posX - p_77621_2_.prevPosX) * (double) f;
        double d1 = p_77621_2_.prevPosY + (p_77621_2_.posY - p_77621_2_.prevPosY) * (double) f + (double) (p_77621_1_.isRemote ? p_77621_2_.getEyeHeight() - p_77621_2_.getDefaultEyeHeight() : p_77621_2_.getEyeHeight()); // isRemote check to revert changes to ray trace position due to adding the eye height clientside and player yOffset differences
        double d2 = p_77621_2_.prevPosZ + (p_77621_2_.posZ - p_77621_2_.prevPosZ) * (double) f;
        Vec3 vec3 = Vec3.createVectorHelper(d0, d1, d2);
        float f3 = MathHelper.cos(-f2 * 0.017453292F - (float) Math.PI);
        float f4 = MathHelper.sin(-f2 * 0.017453292F - (float) Math.PI);
        float f5 = -MathHelper.cos(-f1 * 0.017453292F);
        float f6 = MathHelper.sin(-f1 * 0.017453292F);
        float f7 = f4 * f5;
        float f8 = f3 * f5;
        double d3 = 5.0D;
        if (p_77621_2_ instanceof EntityPlayerMP) {
            d3 = ((EntityPlayerMP) p_77621_2_).theItemInWorldManager.getBlockReachDistance();
        }
        Vec3 vec31 = vec3.addVector((double) f7 * d3, (double) f6 * d3, (double) f8 * d3);
        return p_77621_1_.func_147447_a(vec3, vec31, p_77621_3_, !p_77621_3_, false);
    }


    public static void addSoftwareLibrary(File file) {
        try {
            Method method = URLClassLoader.class.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(ClassLoader.getSystemClassLoader(), file.toURI().toURL());
        } catch (Exception e) {
            MyTown.instance.log.error("Failed adding file " + file.getAbsolutePath() + " to classpath!");
        }
    }

    public static void injectBukkitBridge(File self, File pluginFolder) {
        MyTown.instance.log.info("Injecting PermissionsEx!");
        MyTown.instance.log.info("Trying to inject from " + pluginFolder.getAbsolutePath() + " to " + self.getAbsolutePath());
        try {
            ZipFile zip = new ZipFile(self);
            ZipEntry entry = zip.getEntry("PermissionsEx.jar");
            if (entry == null) {
                MyTown.instance.log.error("Mod doesn't contain PermissionsEx! If using MCPC, you need this!");
                zip.close();
                return;
            }
            InputStream stream = zip.getInputStream(entry);
            FileOutputStream outStream = new FileOutputStream(new File(pluginFolder, entry.getName()));
            byte[] tmp = new byte[4 * 1024];
            int size = 0;
            while ((size = stream.read(tmp)) != -1) {
                outStream.write(tmp, 0, size);
            }
            outStream.close();
            zip.close();
        } catch (Exception e) {
            MyTown.instance.log.error("Failed to inject PermissionsEx! ", e);
        }
        MyTown.instance.log.info("Injected PermissionsEx successfully!");
    }

    @SuppressWarnings("unchecked")
    public static void addURL(File file) {

        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class sysclass = URLClassLoader.class;

        try {
            Method method = sysclass.getDeclaredMethod("addURL", URL.class);
            method.setAccessible(true);
            method.invoke(sysloader, file.toURI().toURL());
            ClassLoader.getSystemClassLoader().loadClass("ru.tehkode.permissions.bukkit.PermissionsEx");
            MyTown.instance.log.info("Added PEX to the classpath. (" + file.toURI().toURL().toString() + ")");

        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    /**
     * Takes the amount of items specified.
     * Returns false if player doesn't have the items necessary
     */
    public static boolean takeItemFromPlayer(EntityPlayer player, String itemName, int amount) {
        String[] split = itemName.split(":");
        return takeItemFromPlayer(player, GameRegistry.findItem(split[0], split[1]), amount, split.length == 3 ? Integer.parseInt(split[2]) : -1);
    }

    /**
     * Takes a specified amount of the itemStack from the player's inventory.
     */
    public static boolean takeItemFromPlayer(EntityPlayer player, ItemStack itemStack, int amount) {
        return takeItemFromPlayer(player, itemStack.getItem(), amount, itemStack.getItemDamage());
    }

    /**
     * Takes the amount of items specified.
     * Returns false if player doesn't have the items necessary
     */
    public static boolean takeItemFromPlayer(EntityPlayer player, Item item, int amount, int meta) {
        List<Integer> slots = new ArrayList<Integer>();
        int itemSum = 0;
        for (int i = 0; i < player.inventory.mainInventory.length; i++) {
            ItemStack itemStack = player.inventory.mainInventory[i];
            if (itemStack == null)
                continue;
            if (itemStack.getItem() == item && (meta == -1 || itemStack.getItemDamage() == meta)) {
                slots.add(i);
                itemSum += itemStack.stackSize;
                if(itemSum >= amount)
                    break;
            }
        }

        if(itemSum < amount)
            return false;

        for(int i : slots) {
            if(player.inventory.mainInventory[i].stackSize >= amount) {
                player.inventory.decrStackSize(i, amount);
                Slot slot = player.openContainer.getSlotFromInventory(player.inventory, i);
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(new S2FPacketSetSlot(player.openContainer.windowId, slot.slotNumber, player.inventory.mainInventory[i]));
                return true;
            } else {
                int stackSize = player.inventory.mainInventory[i].stackSize;
                player.inventory.decrStackSize(i, stackSize);
                amount -= stackSize;
                Slot slot = player.openContainer.getSlotFromInventory(player.inventory, i);
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(new S2FPacketSetSlot(player.openContainer.windowId, slot.slotNumber, player.inventory.mainInventory[i]));
            }
        }
        return true;
    }

    /**
     * Takes the selector tool (for plots) from the player.
     */
    public static void takeSelectorToolFromPlayer(EntityPlayer player) {
        for(int i = 0; i < player.inventory.mainInventory.length; i++) {
            if (player.inventory.mainInventory[i] != null && player.inventory.mainInventory[i].getDisplayName().equals(Constants.EDIT_TOOL_NAME)) {
                player.inventory.decrStackSize(i, 1);
                return;
            }
        }
    }

    /**
     * Gives the amount of items specified.
     */
    public static void giveItemToPlayer(EntityPlayer player, String itemName, int amount) {
        String[] split = itemName.split(":");
        giveItemToPlayer(player, GameRegistry.findItem(split[0], split[1]), amount, split.length > 2 ? Integer.parseInt(split[2]) : 0);
    }

    /**
     * Gives the amount of items specified.
     */
    public static void giveItemToPlayer(EntityPlayer player, ItemStack itemStack, int amount) {
        giveItemToPlayer(player, itemStack.getItem(), amount, itemStack.getItemDamage());
    }

    /**
     * Gives the amount of items specified.
     */
    public static void giveItemToPlayer(EntityPlayer player, Item item, int amount, int meta) {
        for (int left = amount; left > 0; left -= 64) {
            ItemStack stack = new ItemStack(item, left > 64 ? 64 : left, meta);
            //stack = addToInventory(player.inventory, stack);
            int i = -1;
            for(int j = 0; j < player.inventory.mainInventory.length; j++) {
                if (player.inventory.mainInventory[j] != null && player.inventory.mainInventory[j].getItem() == item && player.inventory.mainInventory[j].getItemDamage() == meta &&
                        player.inventory.mainInventory[j].stackSize + stack.stackSize <= 64) {
                    i = j;
                    break;
                }
            }
            if(i == -1) {
                for(int j = 0; j < player.inventory.mainInventory.length; j++) {
                    if(player.inventory.mainInventory[j] == null) {
                        i = j;
                        break;
                    }
                }
                if(i != -1)
                    player.inventory.mainInventory[i] = stack;
            } else {
                player.inventory.mainInventory[i].stackSize += amount;
            }

            if (i == -1) {
                // Drop it on the ground if it fails to add to the inventory
                MyTownUtils.dropAsEntity(player.getEntityWorld(), (int) player.posX, (int) player.posY, (int) player.posZ, stack);
            } else {

                // get the actual inventory Slot:
                Slot slot = player.openContainer.getSlotFromInventory(player.inventory, i);
                // send S2FPacketSetSlot to the player with the new / changed stack (or null)
                ((EntityPlayerMP) player).playerNetServerHandler.sendPacket(new S2FPacketSetSlot(player.openContainer.windowId, slot.slotNumber, player.inventory.mainInventory[i]));
            }
        }
    }


    /**
     * Returns the item from a String that has this pattern: (modid):(unique_name)[:meta]
     */
    public static Item itemFromName(String itemName) {
        String[] split = itemName.split(":");
        return GameRegistry.findItem(split[0], split[1]);
    }

    /**
     * Returns the ItemStack from a String that has this pattern: (modid):(unique_name)[:meta]
     */
    public static ItemStack itemStackFromName(String itemName) {
        String[] split = itemName.split(":");

        return new ItemStack(GameRegistry.findItem(split[0], split[1]), 1, split.length > 2 ? Integer.parseInt(split[2]) : 0);
    }

    /**
     * Returns the unique identifier of given ItemStack
     */
    public static String nameFromItemStack(ItemStack itemStack) {
        String name = GameRegistry.findUniqueIdentifierFor(itemStack.getItem()).toString();
        if(itemStack.getItemDamage() != 0)
            name += ":" + itemStack.getItemDamage();
        return name;
    }


    /**
     * Returns whether or not the String can be parsed as an Integer
     */
    public static boolean tryParseInt(String value) {
        try {
            Integer.parseInt(value);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    /**
     * Returns whether or not the String can be parsed as an Float
     */
    public static boolean tryParseFloat(String value) {
        try {
            Float.parseFloat(value);
        } catch (NumberFormatException ex) {
            return false;
        }
        return true;
    }

    /**
     * Returns whether or not the String can be parsed as an Boolean
     */
    public static boolean tryParseBoolean(String value) {
        return value.equals("true") || value.equals("false");
    }

    /**
     * Returns the sum from n to m (Ex: n = 1, m = 3 => 1 + 2 + 3 = 6)
     */
    public static int sumFromNtoM(int n, int m) {
        int sum = 0;
        for(int i = n; i <= m; i++)
            sum += i;
        return sum;
    }

    /**
     * Searches for the class using the path. Example: "net.minecraft.block.Block"
     */
    public static boolean isClassLoaded(String classPath) {
        boolean value;
        try {
            value = Class.forName(classPath) != null;
        } catch (ClassNotFoundException ex) {
            value = false;
        }
        return value;
    }

    /**
     * Returns the first block from top to bottom that is considered not opaque
     */
    public static int getMaxHeightWithSolid(int dim, int x, int z) {
        World world = DimensionManager.getWorld(dim);
        int y = world.getActualHeight();
        while(!world.getBlock(x, y, z).getMaterial().isOpaque())
            y--;
        return y;
    }

    /**
     * Gets the datasource
     */
    public static MyTownDatasource getDatasource() {
        return DatasourceProxy.getDatasource();
    }
}
