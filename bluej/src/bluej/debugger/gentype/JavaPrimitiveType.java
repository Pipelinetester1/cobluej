/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2009  Michael Kolling and John Rosenberg 
 
 This program is free software; you can redistribute it and/or 
 modify it under the terms of the GNU General Public License 
 as published by the Free Software Foundation; either version 2 
 of the License, or (at your option) any later version. 
 
 This program is distributed in the hope that it will be useful, 
 but WITHOUT ANY WARRANTY; without even the implied warranty of 
 MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the 
 GNU General Public License for more details. 
 
 You should have received a copy of the GNU General Public License 
 along with this program; if not, write to the Free Software 
 Foundation, Inc., 51 Franklin Street, Fifth Floor, Boston, MA  02110-1301, USA. 
 
 This file is subject to the Classpath exception as provided in the  
 LICENSE.txt file that accompanied this code.
 */
package bluej.debugger.gentype;

import java.util.Map;

/**
 * Base class for primitive/built-in types.
 * 
 * @author Davin McCall
 */
public class JavaPrimitiveType
    extends JavaType
{
    private static JavaPrimitiveType [] primitiveTypes = new JavaPrimitiveType[JavaType.JT_MAX+1];
    private static String [] typeNames = { "void", "null", "boolean", "char",
            "byte", "short", "int", "long", "float", "double" };
    // note, the types above should be valid java types. So the type of null
    // is java.lang.Object.
    
    // each element represents a primitive type, and contains an array of
    // other types that this type can be assigned from
    private static int assignableFrom [][] = new int [JT_MAX+1][];
    {
        assignableFrom[JT_VOID]    = new int[] {};
        assignableFrom[JT_NULL]    = new int[] {};
        assignableFrom[JT_BOOLEAN] = new int[] { JT_BOOLEAN };
        assignableFrom[JT_CHAR]    = new int[] { JT_CHAR };
        assignableFrom[JT_BYTE]    = new int[] { JT_BYTE };
        assignableFrom[JT_SHORT]   = new int[] { JT_SHORT, JT_BYTE };
        assignableFrom[JT_INT]     = new int[] { JT_INT, JT_BYTE, JT_SHORT, JT_CHAR };
        assignableFrom[JT_LONG]    = new int[] { JT_LONG, JT_BYTE, JT_SHORT, JT_CHAR, JT_INT };
        assignableFrom[JT_FLOAT]   = new int[] { JT_FLOAT, JT_LONG, JT_BYTE, JT_SHORT, JT_CHAR, JT_INT, JT_LONG };
        assignableFrom[JT_DOUBLE]  = new int[] { JT_DOUBLE, JT_LONG, JT_BYTE, JT_SHORT, JT_CHAR, JT_INT, JT_LONG, JT_FLOAT };
    }
    
    // instance fields
    
    private int myIndex;
    
    /*
     * Private constructor. Use "getXXX" methods to get a primitive instance. 
     */
    protected JavaPrimitiveType(int index)
    {
        myIndex = index;
    }
    
    private static JavaPrimitiveType getType(int v)
    {
        if (primitiveTypes[v] == null)
            primitiveTypes[v] = new JavaPrimitiveType(v);
        
        return primitiveTypes[v];
    }
    
    /**
     * Obtain an instance of "void".
     */
    public static JavaPrimitiveType getVoid()
    {
        return getType(JT_VOID);
    }
    
    public static JavaPrimitiveType getNull()
    {
        return getType(JT_NULL);
    }
    
    public static JavaPrimitiveType getBoolean()
    {
        return getType(JT_BOOLEAN);
    }
    
    public static JavaPrimitiveType getByte()
    {
        return getType(JT_BYTE);
    }
    
    public static JavaPrimitiveType getChar()
    {
        return getType(JT_CHAR);
    }
    
    public static JavaPrimitiveType getShort()
    {
        return getType(JT_SHORT);
    }
    
    public static JavaPrimitiveType getInt()
    {
        return getType(JT_INT);
    }
    
    public static JavaPrimitiveType getLong()
    {
        return getType(JT_LONG);
    }
    
    public static JavaPrimitiveType getFloat()
    {
        return getType(JT_FLOAT);
    }
    
    public static JavaPrimitiveType getDouble()
    {
        return getType(JT_DOUBLE);
    }
    
    
    public String toString()
    {
        return typeNames[myIndex];
    }
    
    public String arrayComponentName()
    {
        // Simple lookup by index. It's not possible to have an array of
        // void or null types.
        return "!!ZCBSIJFD".substring(myIndex, myIndex + 1);
    }
    
    public boolean isAssignableFrom(JavaType o)
    {
        int [] assignables = assignableFrom[myIndex];
        for (int i = 0; i < assignables.length; i++) {
            if (o.typeIs(assignables[i]))
                return true;
        }
        return false;
    }
    
    public boolean couldHold(int n)
    {
        if (myIndex >= JT_INT)
            return true;
        
        if (myIndex == JT_BYTE)
            return n >= -128 && n <= 127;
        else if (myIndex == JT_CHAR)
            return n >=0 && n <= 65535;
        else if (myIndex == JT_SHORT)
            return n >= -32768 && n <= 32767;

        return false;
    }
    
    public boolean fitsType(int gtype)
    {
        if (myIndex == JT_CHAR)
            return (gtype != JT_BYTE && gtype != JT_SHORT);
        else
            return gtype >= myIndex;
    }
    
    public JavaType getErasedType()
    {
        return this;
    }

    /*
     * For primitive types, "isAssignableFromRaw" is equivalent to
     * "isAssignableFrom".
     */
    public boolean isAssignableFromRaw(JavaType t)
    {
        return isAssignableFrom(t);
    }

    public boolean isPrimitive()
    {
        return true;
    }
    
    public boolean isNumeric()
    {
        return myIndex >= JT_LOWEST_NUMERIC;
    }
    
    public boolean isIntegralType()
    {
        return myIndex >= JT_CHAR && myIndex <= JT_LONG;
    }
    
    public boolean typeIs(int v)
    {
        return myIndex == v;
    }
    
    public JavaType mapTparsToTypes(Map<String, ? extends GenTypeParameter> tparams)
    {
        return this;
    }
    
    public boolean equals(Object other)
    {
        if (other instanceof JavaType) {
            JavaType gto = (JavaType) other;
            return (gto.typeIs(myIndex));
        }
        else
            return false;
    }
    
    final protected int getMyIndex()
    {
        return myIndex;
    }
    
    @Override
    public GenTypeArray getArray()
    {
        return new GenTypeArray(this);
    }
    
    @Override
    public JavaType getCapture()
    {
        return this;
    }
    
    @Override
    public void getParamsFromTemplate(Map<String, GenTypeParameter> map,
            GenTypeParameter template)
    {
        
    }
    
    @Override
    public GenTypeSolid getLowerBound()
    {
        return null;
    }
    
    @Override
    public GenTypeSolid getUpperBound()
    {
        return null;
    }
    
    @Override
    public GenTypeSolid[] getUpperBounds()
    {
        return null;
    }
    
    @Override
    public String toTypeArgString(NameTransform nt)
    {
        return toString();
    }
    
    @Override
    public boolean contains(GenTypeParameter other)
    {
        // Not really defined
        return false;
    }
    
    @Override
    public boolean equals(GenTypeParameter other)
    {
        if (other instanceof JavaPrimitiveType) {
            JavaPrimitiveType otherP = (JavaPrimitiveType) other;
            return otherP.typeIs(myIndex);
        }
        return false;
    }
}
