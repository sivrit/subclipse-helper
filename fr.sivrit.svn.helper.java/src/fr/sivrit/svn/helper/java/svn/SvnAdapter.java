package fr.sivrit.svn.helper.java.svn;

import java.io.BufferedReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.concurrent.BlockingDeque;
import java.util.concurrent.LinkedBlockingDeque;

import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.svnclientadapter.ISVNClientAdapter;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.ISVNStatus;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;

public final class SvnAdapter {
    // Creating many ISVNClientAdapter seems to cause trouble (RA layer
    // failures, svn server not found). Some resources might not get cleaned up.
    // So we will try to create a reasonable (for a certain value of reasonable)
    // number of them.
    private static final BlockingDeque<SvnAdapter> pool = new LinkedBlockingDeque<SvnAdapter>();

    public static SvnAdapter borrow() throws SVNException {
        final SvnAdapter adapter = pool.pollLast();
        if (adapter == null) {
            return new SvnAdapter();
        } else {
            return adapter;
        }
    }

    public static void release(final SvnAdapter adapter) {
        assert adapter != null;
        pool.offerLast(adapter);
    }

    private final ISVNClientAdapter adapter;

    private SvnAdapter() throws SVNException {
        adapter = SVNProviderPlugin.getPlugin().getSVNClient();
    }

    public ISVNDirEntry[] getList(final SVNUrl svnUrl) throws SVNClientException {
        return getList(svnUrl, SVNRevision.HEAD);
    }

    public ISVNDirEntry[] getList(final SVNUrl svnUrl, final SVNRevision rev)
            throws SVNClientException {
        return adapter.getList(svnUrl, rev, false);
    }

    public ISVNInfo getInfo(final SVNUrl svnUrl) throws SVNClientException {
        return adapter.getInfo(svnUrl);
    }

    public ISVNStatus getStatus(final File file) throws SVNClientException {
        return adapter.getSingleStatus(file);
    }

    public InputStream getContent(final SVNUrl svnUrl) throws SVNClientException {
        return adapter.getContent(svnUrl, SVNRevision.HEAD);
    }

    public String getStringContent(final SVNUrl svnUrl) throws SVNClientException {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(getContent(svnUrl)));
            final StringBuilder sb = new StringBuilder();
            String line = null;
            while ((line = reader.readLine()) != null) {
                sb.append(line).append('\n');
            }

            return sb.toString();
        } catch (final IOException e) {
            throw new SVNClientException(e);
        } finally {
            try {
                reader.close();
            } catch (final IOException e) {
                throw new SVNClientException(e);
            }
        }
    }
}
