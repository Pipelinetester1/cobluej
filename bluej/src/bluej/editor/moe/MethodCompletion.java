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
package bluej.editor.moe;

import java.util.Collections;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

import bluej.debugger.gentype.GenTypeParameter;
import bluej.debugger.gentype.JavaType;
import bluej.debugger.gentype.MethodReflective;
import bluej.pkgmgr.JavadocResolver;

/**
 * Possible code completion for a method.
 * 
 * @author Davin McCall
 */
public class MethodCompletion extends AssistContent
{
    private MethodReflective method;
    private JavadocResolver javadocResolver;
    private Map<String,GenTypeParameter> typeArgs;
    
    /**
     * Construct a new method completion
     * @param method    The method to represent
     * @param typeArgs   The type arguments (may be null if there are none)
     * @param javadocResolver  The javadoc resolver to use
     */
    public MethodCompletion(MethodReflective method,
            Map<String,GenTypeParameter> typeArgs,
            JavadocResolver javadocResolver)
    {
        this.method = method;
        this.typeArgs = typeArgs;
        this.javadocResolver = javadocResolver;
    }
    
    @Override
    public String getDeclaringClass()
    {
        String dname = method.getDeclaringType().getName();
        dname = dname.replace('$', '.');
        return dname;
    }

    @Override
    public String getDisplayName()
    {
        String displayName = method.getName() + "(";
        List<JavaType> paramTypes = method.getParamTypes();
        for (Iterator<JavaType> i = paramTypes.iterator(); i.hasNext(); ) {
            JavaType paramType = convertToSolid(i.next());
            displayName += paramType.toString(true);
            if (i.hasNext()) {
                displayName += ", ";
            }
        }
        displayName += ")";
        
        return displayName;
    }

    @Override
    public String getCompletionText()
    {
        return method.getName() + "(";
    }
    
    @Override
    public String getCompletionTextSel()
    {
        List<JavaType> paramTypes = method.getParamTypes();
        if (! paramTypes.isEmpty()) {
            List<String> paramNames = method.getParamNames();
            if (paramNames == null || paramNames.isEmpty()) {
                return buildParam(1, paramTypes.get(0), null);
            }
            else {
                return buildParam(1, paramTypes.get(0), paramNames.get(0));
            }
        }
        return "";
    }
    
    @Override
    public String getCompletionTextPost()
    {
        String r = ")";
        List<JavaType> paramTypes = method.getParamTypes();
        if (paramTypes.size() > 1) {
            String paramStr = "";
            List<String> paramNames = method.getParamNames();
            paramNames = (paramNames == null) ? Collections.<String>emptyList() : paramNames;
            Iterator<JavaType> ti = paramTypes.iterator();
            Iterator<String> ni = paramNames.iterator();
            ti.next();
            if (ni.hasNext()) ni.next();
            int i = 2;
            while (ti.hasNext()) {
                String name = ni.hasNext() ? ni.next() : null;
                paramStr += ", " + buildParam(i++, ti.next(), name);
            }
            r = paramStr + r;
        }
        
        return r;
    }
    
    @Override
    public String getReturnType()
    {
        return convertToSolid(method.getReturnType()).toString(true);
    }

    @Override
    public String getJavadoc()
    {
        String jd = method.getJavaDoc();
        if (jd == null && javadocResolver != null) {
            javadocResolver.getJavadoc(method);
            jd = method.getJavaDoc();
        }
        return jd;
    }
    
    private JavaType convertToSolid(JavaType type)
    {
        if (! type.isPrimitive()) {
            if (typeArgs != null) {
                type = type.mapTparsToTypes(typeArgs);
                type = type.getUpperBound();
            }
            else {
                type = type.getErasedType();
            }
        }
        return type;
    }
    
    private static String buildParam(int pnum, JavaType paramType, String paramName)
    {
        if (paramName != null) {
            return "_" + paramName + "_";
        }
        else {
            return "_" + paramType.toString(true) + "_";
        }
    }
}
