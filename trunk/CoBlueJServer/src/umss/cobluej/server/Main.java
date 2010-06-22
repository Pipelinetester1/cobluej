package umss.cobluej.server;

/**
 *
 * @author paolocastro
 */
public class Main {

    /**
     * Main class for the CoBlueJ Server.
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        ServerPublisher publisher = ServerPublisher.getInstance();
        publisher.publish();
    }

}
