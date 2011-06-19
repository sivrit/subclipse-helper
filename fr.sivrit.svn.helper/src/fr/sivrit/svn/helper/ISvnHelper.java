package fr.sivrit.svn.helper;

import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.svnclientadapter.SVNUrl;

/**
 * Interface defining the actual implementations to which all work will be
 * delegated
 */
public interface ISvnHelper {
    /**
     * @param repo
     * @param svnUrls
     * @return
     */
    boolean checkoutBranch(ISVNRepositoryLocation repo, SVNUrl[] svnUrls);

    /**
     * @param svnUrls
     * @return
     */
    boolean resolveDependencies(SVNUrl[] svnUrls);

    /**
     * @param urls
     * @return
     */
    boolean createWorkingSets(SVNUrl[] urls);
}
