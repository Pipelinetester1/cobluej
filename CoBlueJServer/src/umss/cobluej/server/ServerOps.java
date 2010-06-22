package umss.cobluej.server;

import java.rmi.RemoteException;
import java.rmi.server.UnicastRemoteObject;
import java.util.logging.Logger;
import umss.cobluej.common.IServerOps;

/**
 * Server Operations.
 *
 * @author paolocastro
 */
public class ServerOps extends UnicastRemoteObject implements IServerOps {

    private final Logger logger = Logger.getLogger(ServerOps.class.getName());
    /**
     * Server model.
     */
    private Server server;

    public ServerOps(Server server) throws RemoteException {
        this.server = server;
        logger.fine("Server Operations initialized.");
    }
}
