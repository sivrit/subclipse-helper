package fr.sivrit.svn.helper.java.svn;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import org.eclipse.core.runtime.IStatus;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Logger;
import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.SvnHelperJava;

public class Cache {
    private final String DIR = "DIR";
    private final String FILE = "FILE";

    public static final class CachedEntry {
        public final SVNUrl url;
        public final String content;
        public final Map<String, Long> children;
        public final boolean isDir;

        private CachedEntry(final SVNUrl url, final Map<String, Long> children) {
            super();
            this.url = url;
            this.content = null;
            this.children = children;
            this.isDir = true;
        }

        private CachedEntry(final SVNUrl url, final String content) {
            super();
            this.url = url;
            this.content = content;
            this.children = Collections.emptyMap();
            this.isDir = false;
        }
    }

    private final String cacheLoc;

    public Cache() {
        String location = Preferences.getCacheFolder();
        if (location != null && !location.endsWith(File.separator)) {
            location += File.separator;
        }
        cacheLoc = location;
    }

    private File getPath(final SVNUrl url, final long version) {
        final String host = url.getHost();
        final String port = Integer.toString(url.getPort());

        final StringBuilder path = new StringBuilder(cacheLoc);
        path.append(host).append('$').append(port).append(File.separator);
        path.append(version).append(File.separator);

        for (final String segment : url.getPathSegments()) {
            path.append('$').append(segment);
        }

        return new File(path.toString());
    }

    public CachedEntry fetch(final SvnClient svn, final SVNUrl url, final long version)
            throws SVNClientException {
        if (cacheLoc != null) {
            final File file = getPath(url, version);
            if (file.exists()) {
                return load(url, file);
            }
        }

        return cacheFault(svn, url);
    }

    private CachedEntry cacheFault(final SvnClient svn, final SVNUrl url) throws SVNClientException {
        final ISVNInfo info = svn.getInfo(url);
        final long version = info.getLastChangedRevision().getNumber();

        final CachedEntry entry;
        if (info.getNodeKind() == SVNNodeKind.DIR) {
            final ISVNDirEntry[] content = svn.getList(url);
            final Map<String, Long> entries = new HashMap<String, Long>();
            for (final ISVNDirEntry isvnDirEntry : content) {
                entries.put(isvnDirEntry.getPath(), isvnDirEntry.getLastChangedRevision()
                        .getNumber());
            }

            entry = new CachedEntry(url, entries);
        } else {
            assert info.getNodeKind() == SVNNodeKind.FILE : url.toString();
            final String content = svn.getStringContent(url);
            entry = new CachedEntry(url, content);
        }

        if (cacheLoc != null) {
            store(entry, version);
        }

        return entry;
    }

    private synchronized void store(final CachedEntry entry, final long version) {
        final File file = getPath(entry.url, version);
        new File(file.getParent()).mkdirs();

        Writer writer = null;
        try {
            if (file.createNewFile()) {
                writer = new OutputStreamWriter(new FileOutputStream(file),
                        Charset.forName("UTF-8"));
                if (entry.isDir) {
                    writer.write(DIR);
                    writer.write('\n');
                    for (final Map.Entry<String, Long> item : entry.children.entrySet()) {
                        writer.write(item.getValue().toString());
                        writer.write('$');
                        writer.write(item.getKey());
                        writer.write('\n');
                    }
                } else {
                    writer.write(FILE);
                    writer.write('\n');
                    writer.write(entry.content);
                }
            }
        } catch (final IOException e) {
            Logger.log(IStatus.ERROR, SvnHelperJava.PLUGIN_ID, e);
        } finally {
            try {
                if (writer != null) {
                    writer.close();
                } else {
                    file.delete();
                }
            } catch (final IOException e) {
                Logger.log(IStatus.ERROR, SvnHelperJava.PLUGIN_ID, e);
            }
        }

    }

    private synchronized CachedEntry load(final SVNUrl url, final File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                    Charset.forName("UTF-8")));
            final String type = reader.readLine();
            if (DIR.equalsIgnoreCase(type)) {
                return loadDir(url, reader);
            } else if (FILE.equalsIgnoreCase(type)) {
                return loadFile(url, reader);
            } else {
                throw new IllegalStateException(type + " read in " + file.toString());
            }

        } catch (final IOException e) {
            Logger.log(IStatus.ERROR, SvnHelperJava.PLUGIN_ID, e);
            return null;
        } finally {
            try {
                if (reader != null) {
                    reader.close();
                }
            } catch (final IOException e) {
                Logger.log(IStatus.ERROR, SvnHelperJava.PLUGIN_ID, e);
            }
        }
    }

    private CachedEntry loadDir(final SVNUrl url, final BufferedReader reader)
            throws NumberFormatException, IOException {
        final Map<String, Long> entries = new HashMap<String, Long>();
        String line = null;
        while ((line = reader.readLine()) != null) {
            final int sep = line.indexOf('$');
            final Long version = Long.parseLong(line.substring(0, sep));
            final String name = line.substring(sep + 1);
            entries.put(name, version);
        }

        return new CachedEntry(url, Collections.unmodifiableMap(entries));
    }

    private CachedEntry loadFile(final SVNUrl url, final BufferedReader reader) throws IOException {
        final StringBuilder content = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            content.append(line).append('\n');
        }

        return new CachedEntry(url, content.toString());
    }
}
