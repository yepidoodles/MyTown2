package mytown.util;

import net.minecraft.util.EnumChatFormatting;

public class Constants {
    public static final String VERSION = "@VERSION@";
    public static final String MODID = "MyTown2";
    public static final String MODNAME = "MyTown 2";
    //public static final String DEPENDENCIES = "after:*;required-after:Forge;required-after:MyEssentials-Core";
    public static final String DEPENDENCIES = "required-after:Forge;required-after:MyEssentials-Core;after:WorldEdit;before:ForgeEssentials;";
    public static String CONFIG_FOLDER = "";

    public static final String EDIT_TOOL_NAME = EnumChatFormatting.BLUE + "Selector"; // TODO: Get localization for it, maybe?
    public static final String EDIT_TOOL_DESCRIPTION_PLOT = EnumChatFormatting.DARK_AQUA + "Select 2 blocks to make a plot.";
    public static final String EDIT_TOOL_DESCRIPTION_BLOCK_WHITELIST = EnumChatFormatting.DARK_AQUA + "Select block for bypassing protection.";

    public static final String SIGN_SHOP_NAME = EnumChatFormatting.BLUE + "Shop Sign";
    public static final String SIGN_ID_TEXT = "" + EnumChatFormatting.ITALIC + EnumChatFormatting.GRAY + "ID: ";

    public static final String PLOT_SELL_NAME = EnumChatFormatting.BLUE + "Plot Sell";
    public static final String PLOT_SELL_IDENTIFIER = EnumChatFormatting.DARK_BLUE + "Plot Sale";
}