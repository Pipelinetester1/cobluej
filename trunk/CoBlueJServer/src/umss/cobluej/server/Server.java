package umss.cobluej.server;

/**
 * CoBlueJ Server model.
 *
 * @author paolocastro
 */
public class Server {
    
    /**
     * Server's port number.
     */
    private int port;

    /**
     * Server's id name.
     */
    private String id;

    /**
     * Default port.
     */
    private final int PORT = 5555;

    /**
     * Default ID.
     */
    private final String ID = "CoBlueJServer";

    /**
     * Creates an instance of CoBlueJ Server, using the default
     * port and id.
     */
    public Server() {
        this.port = PORT;
        this.id = ID;
    }

    /**
     * Get the Server's port number.
     * @return Port.
     */
    public int getPort() {
        return this.port;
    }

    /**
     * Get the Server's id string.
     * @return Id.
     */
    public String getId() {
        return this.id;
    }
}
