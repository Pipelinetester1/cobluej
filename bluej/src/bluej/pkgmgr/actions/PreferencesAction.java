/*
 This file is part of the BlueJ program. 
 Copyright (C) 1999-2010  Michael Kolling and John Rosenberg 
 
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
package bluej.pkgmgr.actions;

import javax.swing.JEditorPane;
import bluej.editor.moe.MoeActions;
import bluej.pkgmgr.PkgMgrFrame;

/**
 * "Preferences" command. Displays a dialog box in which user can set various
 * preferences as to how BlueJ should behave.
 * 
 * @author Davin McCall
 * @version $Id: PreferencesAction.java 6987 2010-01-12 04:17:59Z marionz $
 */

final public class PreferencesAction extends PkgMgrAction {
    
    static private PreferencesAction instance = null;
    
    /**
     * Factory method. This is the way to retrieve an instance of the class,
     * as the constructor is private.
     * @return an instance of the class.
     */
    static public PreferencesAction getInstance()
    {
        if(instance == null)
            instance = new PreferencesAction();
        return instance;
    }
    
    private PreferencesAction()
    {
        super("menu.tools.preferences");
    }
    
    public void actionPerformed(PkgMgrFrame pmf)
    {
        pmf.menuCall();
        pmf.showPreferences();
    }
}
