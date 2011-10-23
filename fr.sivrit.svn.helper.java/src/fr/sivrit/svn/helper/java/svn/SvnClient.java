package fr.sivrit.svn.helper.java.svn;

import java.io.File;
import java.util.ArrayList;
import java.util.Collection;

import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Preferences;

public class SvnClient {
    public static SvnClient createClient() {
        final String location = Preferences.getCacheFolder();
        if (location == null) {
            return new SvnClient();
        } else {
            final String cacheDir = location.endsWith(File.separator) ? location : location
                    + File.separator;
            return new CachedSvnClient(cacheDir);
        }
    }

    protected SvnClient() {
        super();
    }

    public SvnNode fetch(final SVNUrl url) throws SVNClientException, SVNException {
        final SvnAdapter svn = SvnAdapter.create();

        final ISVNInfo info = svn.getInfo(url);
        final long version = info.getLastChangedRevision().getNumber();

        return fetch(url, version, info.getNodeKind() == SVNNodeKind.DIR);
    }

    public SvnNode fetch(final SVNUrl url, final long version, final boolean isDir)
            throws SVNClientException, SVNException {
        final SvnAdapter svn = SvnAdapter.create();

        if (isDir) {
            final ISVNDirEntry[] content = svn.getList(url);
            final Collection<SvnFolderEntry> entries = new ArrayList<SvnFolderEntry>();
            for (final ISVNDirEntry isvnDirEntry : content) {
                entries.add(new SvnFolderEntry(isvnDirEntry));
            }

            return new SvnNode(url, version, entries);
        } else {
            final String content = svn.getStringContent(url);
            return new SvnNode(url, version, content);
        }
    }
}
