package mytown.protection.segment;

import mytown.entities.flag.FlagType;
import mytown.protection.segment.getter.Getters;
import mytown.util.MyTownUtils;
import mytown.util.exceptions.ConditionException;
import net.minecraft.item.ItemStack;

/**
 * Created by AfterWind on 1/1/2015.
 * A part of the protection that protects against a specific thing.
 */
public class Segment {
    public Class<?> theClass;
    public FlagType flag;
    public Object denialValue;
    public Getters getters;
    public String[] conditionString;

    public Segment(Class<?> theClass, Getters getters, FlagType flag, Object denialValue, String conditionString) {
        this.theClass = theClass;
        this.getters = getters;
        this.flag = flag;
        this.denialValue = denialValue;
        if(conditionString != null)
            this.conditionString = conditionString.split(" ");
    }

    public boolean checkCondition(Object object) {

        if(conditionString == null)
            return true;

        //MyTown.instance.log.info("Checking condition: " + StringUtils.join(conditionString, " "));
        boolean current;

        /*
            This is very important when checking as the ItemStack that is passed doesn't
            have the methods and fields needed only the Item itself does.

            Block, TileEntity and Entity should be left unchanged as their classes should be mod defined
         */

        Object instance;
        if(object instanceof ItemStack) {
            instance = ((ItemStack) object).getItem();
        } else {
            instance = object;
        }
        for(int i = 0; i < conditionString.length; i += 4) {

            // Get the boolean value of each part of the condition.
            if(MyTownUtils.tryParseBoolean(conditionString[i + 2])) {
                boolean value = (Boolean) getters.getValue(conditionString[i], Boolean.class, instance, object);
                if (conditionString[i + 1].equals("==")) {
                    current = value == Boolean.parseBoolean(conditionString[i + 2]);
                } else if(conditionString[i + 1].equals("!=")) {
                    current = value != Boolean.parseBoolean(conditionString[i + 2]);
                } else {
                    throw new ConditionException("[Segment: " + this.theClass.getName() + "] The element number " + (i / 4) + 1 + " has an invalid condition!");
                }
            } else if(MyTownUtils.tryParseInt(conditionString[i + 2])) {
                int value = (Integer) getters.getValue(conditionString[i], Integer.class, instance, object);
                if(conditionString[i+1].equals("==")) {
                    current = value == Integer.parseInt(conditionString[i + 2]);
                } else if(conditionString[i + 1].equals("!=")) {
                    current = value != Integer.parseInt(conditionString[i + 2]);
                } else if(conditionString[i+1].equals("<")) {
                    current = value < Integer.parseInt(conditionString[i + 2]);
                } else if(conditionString[i+1].equals(">")) {
                    current = value > Integer.parseInt(conditionString[i + 2]);
                } else {
                    throw new ConditionException("[Segment: "+ this.theClass.getName() +"] The element number " + (i / 4) + 1 + " has an invalid condition!");
                }
            } else if(MyTownUtils.tryParseFloat(conditionString[i + 2])) {
                float value = (Integer) getters.getValue(conditionString[i], Integer.class, instance, object);
                if(conditionString[i+1].equals("==")) {
                    current = value == Float.parseFloat(conditionString[i + 2]);
                } else if(conditionString[i + 1].equals("!=")) {
                    current = value != Float.parseFloat(conditionString[i + 2]);
                } else if(conditionString[i+1].equals("<")) {
                    current = value < Float.parseFloat(conditionString[i + 2]);
                } else if(conditionString[i+1].equals(">")) {
                    current = value > Float.parseFloat(conditionString[i + 2]);
                } else {
                    throw new ConditionException("[Segment: "+ this.theClass.getName() +"] The element number " + ((i/4)+1) + " has an invalid condition!");
                }
            } else if(conditionString[i + 2].startsWith("'") && conditionString[i+2].endsWith("'")){
                String value = (String) getters.getValue(conditionString[i], String.class, instance, object);
                if(conditionString[i + 1].equals("==")) {
                    current = value.equals(conditionString[i+2].substring(1, conditionString[i+2].length() - 1));
                } else if(conditionString[i + 1].equals("!=")) {
                    current = !value.equals(conditionString[i+2].substring(1, conditionString[i+2].length() - 1));
                } else {
                    throw new ConditionException("[Segment: "+ this.theClass.getName() +"] The element number " + ((i/4)+1) + " has an invalid condition!");
                }
            } else {
                throw new ConditionException("[Segment: "+ this.theClass.getName() +"] The element with number " + ((i/4)+1) + " has an invalid type to be checked against!");
            }

            if(conditionString.length <= i + 3 || current && conditionString[i + 3].equals("OR") || !current && conditionString[i + 3].equals("AND"))
                return current;

            if(!conditionString[i + 3].equals("OR") && !conditionString[i + 3].equals("AND"))
                throw new ConditionException("[Segment: "+ this.theClass.getName()  +"] Invalid condition element: " + conditionString[i + 3]);
        }
        return false;
    }

    /**
     * Gets the range of the area of effect of this thing, or 0 if none is specified.
     */
    public int getRange(Object object) {
        return getters.hasValue("range") ? (Integer) getters.getValue("range", Integer.class, object, object) : 0;    }
}
