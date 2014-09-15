package mytown.util;

import mytown.MyTown;
import mytown.datasource.MyTownDatasource;
import mytown.entities.Block;
import mytown.entities.Town;
import mytown.proxies.DatasourceProxy;
import net.minecraft.entity.Entity;
import net.minecraft.entity.player.EntityPlayer;
import net.minecraft.item.ItemStack;
import net.minecraft.nbt.NBTTagList;
import net.minecraft.tileentity.TileEntity;

import java.io.BufferedInputStream;
import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.lang.reflect.Method;
import java.net.URL;
import java.net.URLClassLoader;
import java.util.ArrayList;
import java.util.List;

/**
 * Created by AfterWind on 9/9/2014.
 * Utils class for random useful things
 */
public class Utils {

    /**
     * Returns the town at the specified position or null if nothing found.
     *
     * @param dim
     * @param x
     * @param z
     * @return
     */
    public static Town getTownAtPosition(int dim, int x, int z) {
        Block block = getDatasource().getBlock(dim, x, z);
        if(block == null)
            return null;
        return block.getTown();
    }

    /**
     * Gets the town at the entity's position
     *
     * @param entity
     * @return
     */
    protected static Town getTownFromEntity(Entity entity) {
        return  getTownAtPosition(entity.dimension, entity.chunkCoordX, entity.chunkCoordZ);
    }

    /**
     * Returns the Flag name from the selector's Lore tag
     *
     * @param player
     * @return
     */
    public static String getFlagNameFromLore(EntityPlayer player) {
        ItemStack currentStack = player.inventory.getCurrentItem();
        NBTTagList lore = currentStack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
        String flagLore = lore.getStringTagAt(2); // Second in line
        return flagLore.substring(8); // We use hacks in here
    }

    public static String getTownNameFromLore(EntityPlayer player) {
        ItemStack currentStack = player.inventory.getCurrentItem();
        NBTTagList lore = currentStack.getTagCompound().getCompoundTag("display").getTagList("Lore", 8);
        String flagLore = lore.getStringTagAt(3);
        return flagLore.substring(8);
    }

    public static void saveUrl(final String filename, final String urlString)
            throws IOException {
        BufferedInputStream in = null;
        FileOutputStream fout = null;
        try {
            in = new BufferedInputStream(new URL(urlString).openStream());
            fout = new FileOutputStream(filename);

            final byte data[] = new byte[1024];
            int count;
            while ((count = in.read(data, 0, 1024)) != -1) {
                fout.write(data, 0, count);
            }
        } finally {
            if (in != null) {
                in.close();
            }
            if (fout != null) {
                fout.close();
            }
        }
    }

    public static void addFile(File file) {
        URLClassLoader sysloader = (URLClassLoader) ClassLoader.getSystemClassLoader();
        Class<?> sysclass = URLClassLoader.class;

        try {
            URL u = file.toURL();
            Method method = sysclass.getDeclaredMethod("addURL", new Class[]{URL.class});
            method.setAccessible(true);
            method.invoke(sysloader, u);
        } catch (Throwable t) {
            t.printStackTrace();
        }
    }

    public static List<TileEntity> getNearbyTileEntity(TileEntity te, Class<? extends TileEntity> type) {
        List<TileEntity> result = new ArrayList<TileEntity>();
        int[] dx = {0, 1, 0, -1, 0, 0};
        int[] dy = {1, 0, -1, 0, 0, 0};
        int[] dz = {0, 0, 0, 0, 1, -1};

        for(int i = 0; i < 6; i++) {
            TileEntity found = te.getWorldObj().getTileEntity(te.xCoord + dx[i], te.yCoord + dy[i], te.zCoord + dz[i]);
            if(found != null && type.isAssignableFrom(found.getClass())) {
                MyTown.instance.log.info("Found tile entity " + found + " for class " + type.getName());
                result.add(found);
            }
        }
        return result;
    }

    public static List<ChunkPos> getChunksInBox(int minX, int minZ, int maxX, int maxZ) {
        List<ChunkPos> list = new ArrayList<ChunkPos>();
        for(int i = minX >> 4; i <= maxX >> 4; i++) {
            for(int j = minZ >> 4; j <= maxZ >> 4; j++) {
                list.add(new ChunkPos(i, j));
            }
        }
        return list;
    }


    /**
     * Gets the datasource
     *
     * @return
     */
    public static MyTownDatasource getDatasource() {return DatasourceProxy.getDatasource(); }
}
