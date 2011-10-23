package fr.sivrit.svn.helper.java.svn;

import java.util.Collection;
import java.util.Collections;

import org.tigris.subversion.svnclientadapter.SVNUrl;

public final class SvnNode {
    public final SVNUrl url;
    public final String content;
    public final Collection<SvnFolderEntry> children;
    public final boolean isDir;
    public final long version;

    public SvnNode(final SVNUrl url, final long version, final Collection<SvnFolderEntry> children) {
        super();
        this.url = url;
        this.content = null;
        this.children = children;
        this.isDir = true;
        this.version = version;
    }

    public SvnNode(final SVNUrl url, final long version, final String content) {
        super();
        this.url = url;
        this.content = content;
        this.children = Collections.emptySet();
        this.isDir = false;
        this.version = version;
    }
}
