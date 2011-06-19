package fr.sivrit.svn.helper.java;

import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.ISvnHelper;

public class SvnHelperJava implements ISvnHelper {

    @Override
    public boolean checkoutBranch(final ISVNRepositoryLocation repo, final SVNUrl[] svnUrl) {
        throw new UnsupportedOperationException("Todo");
    }

    @Override
    public boolean resolveDependencies(final SVNUrl[] svnUrl) {
        throw new UnsupportedOperationException("Todo");
    }

    @Override
    public boolean createWorkingSets(final SVNUrl[] urls) {
        throw new UnsupportedOperationException("Todo");
    }
}
