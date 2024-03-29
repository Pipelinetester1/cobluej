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
package bluej.debugmgr;

import bluej.debugger.DebuggerObject;

import bluej.testmgr.record.*;


/**
 * Debugger interface implemented by classes interested in the result of an invocation.
 * All methods should be called on the GUI thread.
 *
 * @author  Michael Kolling
 * @author  Poul Henriksen
 * @version $Id: ResultWatcher.java 6671 2009-09-14 04:37:14Z davmac $
 */
public interface ResultWatcher
{
    /**
     * An invocation has completed - here is the result.
     * 
     * @param result   The invocation result object (null for a void result).
     * @param name     The name of the result. For a constructed object, this
     *                 is the name supplied by the user. Otherwise this is  the
     *                 literal "result", or null if the result is void type.
     * @param ir       The record for the completed invocation
     */
    void putResult(DebuggerObject result, String name, InvokerRecord ir);
	
    /**
     * An invocation has failed (compilation error) - here is the error message.
     */
    void putError(String message);
	
    /**
     * A runtime exception occurred - here is the exception text
     */
    void putException(String message);
    
    /**
     * The debug VM terminated. This may have been due to an explicit user action in
     * the UI, or the executing code called System.exit().
     */
    void putVMTerminated();
}
