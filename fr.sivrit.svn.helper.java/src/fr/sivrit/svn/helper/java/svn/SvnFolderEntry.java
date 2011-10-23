package fr.sivrit.svn.helper.java.svn;

import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;

public final class SvnFolderEntry {
    final public String name;
    final public long version;
    final public boolean isDir;

    public SvnFolderEntry(String name, long version, boolean isDir) {
        super();
        this.name = name;
        this.version = version;
        this.isDir = isDir;
    }

    public SvnFolderEntry(final ISVNDirEntry isvnDirEntry) {
        super();
        this.name = isvnDirEntry.getPath();
        this.version = isvnDirEntry.getLastChangedRevision().getNumber();
        this.isDir = isvnDirEntry.getNodeKind() == SVNNodeKind.DIR;
    }

}
