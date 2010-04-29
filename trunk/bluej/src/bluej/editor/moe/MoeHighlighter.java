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
package bluej.editor.moe;

import java.awt.Color;
import javax.swing.text.DefaultHighlighter;
import javax.swing.text.JTextComponent;

import bluej.Config;

/**
 * The MoeHighlighter class provides the editor with the necessary 
 * HighlightPainters for highlighting founds and selects
 *
 * @author  Marion Zalk
 */
public class MoeHighlighter extends DefaultHighlighter {

    protected HighlightPainter selectPainter ;
    protected HighlightPainter highlightPainter;
    protected HighlightPainter borderPainter;
    
    public MoeHighlighter(JTextComponent comp) 
    {
        super();
        Color temp=Config.getHighlightColour();
        final Color highlight = new Color(temp.getRed(),temp.getGreen(),temp.getBlue());
        selectPainter=new MoeHighlighterPainter(Config.getSelectionColour()); 
        highlightPainter=new MoeHighlighterPainter(highlight);
        borderPainter=new MoeBorderHighlighterPainter(Color.BLACK);
        install(comp);
    }

}
