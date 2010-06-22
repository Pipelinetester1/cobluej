package umss.cobluej.server;

import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.logging.Level;
import java.util.logging.Logger;

/**
 * Server Publisher is the class that publishes the CoBlueJ Server.
 * 
 * @author paolocastro
 */
public class ServerPublisher {

    private final Logger logger = Logger.getLogger(ServerPublisher.class.getName());

    /**
     * Server to publish.
     */
    private Server server;

    /**
     * Server Operations.
     */
    private ServerOps serverOps;

    /**
     * RMI Registry.
     */
    private Registry registry;

    /**
     * Singleton instance.
     */
    private static ServerPublisher instance;

    /**
     * Server Publisher constructor.
     */
    private ServerPublisher() {
        this.server = new Server(); // propmt the user?
    }

    /**
     * Get Singleton instace of Server Publisher.
     * 
     * @return singleton.
     */
    public static ServerPublisher getInstance() {
        if (instance == null) {
            instance = new ServerPublisher();
        }

        return instance;
    }

    /**
     * Publish the Server Operations.
     */
    public void publish() {
        if (registry == null) {
            try {
                serverOps = new ServerOps(server);
                registry = LocateRegistry.createRegistry(server.getPort());
                registry.rebind(server.getId(), serverOps);
                logger.fine(String.format("Server Operations published as '%s' at port %d.",
                        server.getId(), server.getPort()));
            } catch (RemoteException e) {
            }
        } else {
            logger.warning("The Server has already been published.");
        }
    }

    public void unpublish() {
        if (registry != null) {
            try {
                registry.unbind(server.getId());
            } catch (RemoteException ex) {
                logger.severe("Failed to unpublished the server operations; " + ex.getMessage());
            } catch (NotBoundException ex) {
                logger.severe("Server's operations are not published; " + ex.getMessage());
            } catch (Exception ex) {
                logger.severe("Unhandled exception thrown; " + ex.getMessage());
            }
        } else {
            logger.warning("The Server hasn't been published yet.");
        }
    }
}
