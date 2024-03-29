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
 
import java.awt.Color;
import java.awt.Graphics;
import java.awt.Graphics2D;
import java.awt.Rectangle;
import java.awt.Shape;
import java.awt.Transparency;
import java.awt.image.BufferedImage;

import javax.swing.text.Element;
import javax.swing.text.Position;
import javax.swing.text.Segment;

import bluej.Config;

/**
 * A view for the NaviView component.
 * 
 * @author Davin McCall
 */
public class NaviviewView extends BlueJSyntaxView
{
    private static final boolean SCOPE_HIGHLIGHTING = true;
    private static final boolean HIGHLIGHT_METHODS_ONLY = true;
    private static final boolean SYNTAX_COLOURING = false;
    
    // MacOS font rendering at small sizes seems to be vastly different
    // Use 2^3 for MacOS Tiger/Leopard
    // Use 2^2 for MacOS Snow Leopard
    // Use 2^1 for everything else
    private static final int DARKEN_AMOUNT = Config.isMacOS() ? (Config.isMacOSSnowLeopard() ? 2 : 3) : 1;
    
    public NaviviewView(Element elem)
    {
        super(elem, 0);
    }
    
    @Override
    protected void paintTaggedLine(Segment line, int lineIndex, Graphics g,
            int x, int y, MoeSyntaxDocument document, Color def,
            Element lineElement)
    {
        // Painting at such a small font size means the font appears very light.
        // To get around this problem, we paint into a temporary image, then darken
        // the text, and finally copy the temporary image to the output Graphics.
        
        int lineHeight = metrics.getHeight();
        Rectangle clipBounds = g.getClipBounds();
        BufferedImage img;
        if (g instanceof Graphics2D) {
            img = ((Graphics2D)g).getDeviceConfiguration()
                .createCompatibleImage(clipBounds.width, lineHeight, Transparency.TRANSLUCENT);
        }
        else {
            img = new BufferedImage(clipBounds.width, lineHeight, BufferedImage.TYPE_INT_ARGB);
        }
        
        Graphics2D imgG = img.createGraphics();
        imgG.setFont(g.getFont());
        imgG.setColor(g.getColor());
        
        if (SYNTAX_COLOURING) {
            if (document.getParser() != null) {
                super.paintTaggedLine(line, lineIndex, imgG, x - clipBounds.x,
                        metrics.getAscent(), document, def, lineElement);
            } else {
                paintPlainLine(lineIndex, imgG, x - clipBounds.x, metrics.getAscent());
            }
        } else {
            paintPlainLine(lineIndex, imgG, x - clipBounds.x, metrics.getAscent());
        }

        // Filter the image - adjust alpha channel to darken the image.
        for (int iy = 0; iy < img.getHeight(); iy++) {
            for (int ix = 0; ix < img.getWidth(); ix++) {
                int rgb = img.getRGB(ix, iy);
                Color c = new Color(rgb, true);
                int red = c.getRed();
                int green = c.getGreen();
                int blue = c.getBlue();
                int alpha = c.getAlpha();

                // Make it more opaque
                alpha = darken(alpha);
                img.setRGB(ix, iy, new Color(red, green, blue, alpha).getRGB());
            }
        }

        g.drawImage(img, clipBounds.x, y - metrics.getAscent(), null);
    }
    
    @Override
    public void paint(Graphics g, Shape a)
    {
        Rectangle bounds = a.getBounds();
        Rectangle clip = g.getClipBounds();
        if (clip == null) {
            clip = a.getBounds();
        }
        
        if (SCOPE_HIGHLIGHTING) {
            // Scope highlighting
            MoeSyntaxDocument document = (MoeSyntaxDocument)getDocument();
            if (document.getParser() != null) {
                int spos = viewToModel(bounds.x, clip.y, a, new Position.Bias[1]);
                int epos = viewToModel(bounds.x, clip.y + clip.height - 1, a, new Position.Bias[1]);

                Element map = getElement();
                int firstLine = map.getElementIndex(spos);
                int lastLine = map.getElementIndex(epos);
                paintScopeMarkers(g, document, a, firstLine, lastLine,
                        HIGHLIGHT_METHODS_ONLY, true);
            }
        }

        super.paint(g, a);
    }
    
    private int darken(int c)
    {
        c = c << DARKEN_AMOUNT;
        if(c>255) c = 255;
        return c;
    }

}
