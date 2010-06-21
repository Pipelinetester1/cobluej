/*
 * To change this template, choose Tools | Templates
 * and open the template in the editor.
 */

package umss.cobluej.extension;

import bluej.extensions.*;

/**
 *
 * @author paolocastro
 */
public class CoBlueJ extends Extension {

    private String name;
    private String description;
    private String version;

    public CoBlueJ() {
        this.name = "CoBlueJ";
        this.description = "This extension makes BlueJ a collaborative tool.";
        this.version = "0.1.0.0";
    }

    @Override
    public boolean isCompatible() {
        return true;
    }

    @Override
    public void startup(BlueJ bluej) {
        throw new UnsupportedOperationException("Not supported yet.");
    }

    @Override
    public String getName() {
        return name;
    }

    @Override
    public String getVersion() {
        return version;
    }
}