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
 * A type for which we know the text representation, but not the structure. Ie.
 * a type that the user has supplied in a text box, and which we haven't yet
 * parsed or performed equivalent magic with.<p>
 * 
 * Most operations on this type fail with an UnsupportedOperationException.
 * 
 * @author Davin McCall
 * @version $Id: TextType.java 6938 2009-12-15 03:26:24Z davmac $
 */
public class TextType extends JavaType
{
    private String text;
    
    public TextType(String text)
    {
        this.text = text;
    }
    
    /* (non-Javadoc)
     * @see java.lang.Object#toString()
     */
    public String toString()
    {
        return text;
    }
    
    public String arrayComponentName()
    {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see bluej.debugger.gentype.GenType#isPrimitive()
     */
    public boolean isPrimitive()
    {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see bluej.debugger.gentype.GenType#getErasedType()
     */
    public JavaType getErasedType()
    {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see bluej.debugger.gentype.GenType#isAssignableFrom(bluej.debugger.gentype.GenType)
     */
    public boolean isAssignableFrom(JavaType t)
    {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see bluej.debugger.gentype.GenType#isAssignableFromRaw(bluej.debugger.gentype.GenType)
     */
    public boolean isAssignableFromRaw(JavaType t)
    {
        throw new UnsupportedOperationException();
    }

    /* (non-Javadoc)
     * @see bluej.debugger.gentype.GenType#mapTparsToTypes(java.util.Map)
     */
    public JavaType mapTparsToTypes(Map<String, ? extends GenTypeParameter> tparams)
    {
        throw new UnsupportedOperationException();
    }

    public boolean isNumeric()
    {
        throw new UnsupportedOperationException();
    }
    
    public boolean isIntegralType()
    {
        throw new UnsupportedOperationException();
    }
    
    public boolean couldHold(int n)
    {
        throw new UnsupportedOperationException();
    }
    
    public boolean typeIs(int v)
    {
        throw new UnsupportedOperationException();
    }
    
    public GenTypeClass asClass()
    {
        throw new UnsupportedOperationException();
    }
    
    // methods from GenTypeParameterizable
    
    public void getParamsFromTemplate(Map map, GenTypeParameter template)
    {
        throw new UnsupportedOperationException();
    }

    public boolean contains(GenTypeParameter other)
    {
        throw new UnsupportedOperationException();
    }
    
    public GenTypeSolid getLowerBound()
    {
        throw new UnsupportedOperationException();
    }
    
    public boolean equals(GenTypeParameter other)
    {
        if (other == null) {
            return false;
        }
        
        if (other == this) {
            return true;
        }
        
        throw new UnsupportedOperationException();
    }

    public String toTypeArgString(NameTransform nt)
    {
        // throw new UnsupportedOperationException();
    	
    	// Text types are generally typed in by the user, and require
    	// no transformation.
    	return text;
    }
    
    public GenTypeSolid [] getUpperBounds()
    {
        throw new UnsupportedOperationException();
    }
    
    public GenTypeSolid getUpperBound()
    {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public JavaType getCapture()
    {
        throw new UnsupportedOperationException();
    }
    
    @Override
    public GenTypeArray getArray()
    {
        throw new UnsupportedOperationException();
    }
}
