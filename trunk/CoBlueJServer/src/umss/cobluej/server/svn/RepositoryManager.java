package umss.cobluej.server.svn;

import java.util.logging.Level;
import java.util.logging.Logger;
import org.tmatesoft.svn.core.SVNException;
import org.tmatesoft.svn.core.SVNURL;
import org.tmatesoft.svn.core.internal.io.dav.DAVRepositoryFactory;
import org.tmatesoft.svn.core.io.SVNRepository;
import org.tmatesoft.svn.core.io.SVNRepositoryFactory;

/**
 *
 * @author paolocastro
 */
public class RepositoryManager {

    private static final Logger logger = Logger.getLogger(RepositoryManager.class.getName());

    private String path;
    private SVNRepository repository;

    public RepositoryManager(String path) {
        this.path = path;
    }

    public void create() {
        try {
            DAVRepositoryFactory.setup();
            repository = SVNRepositoryFactory.create(SVNURL.parseURIDecoded(path));
            logger.log(Level.FINE, "SVN Repository created: {0}", repository.getLocation().toString());
        } catch (SVNException e) {
            logger.severe(e.getMessage());
        }
    }

    public SVNRepository getRepository() {
        return repository;
    }
}
