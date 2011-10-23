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
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;

import org.eclipse.core.runtime.IStatus;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Logger;
import fr.sivrit.svn.helper.java.SvnHelperJava;

public class CachedSvnClient extends SvnClient {
    private final String DIR = "DIR";
    private final String FILE = "FILE";

    private final String cacheLoc;

    protected CachedSvnClient(String cacheLoc) {
        super();
        this.cacheLoc = cacheLoc;
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

    @Override
    public SvnNode fetch(SVNUrl url, long version, boolean isDir) throws SVNClientException,
            SVNException {
        final File file = getPath(url, version);
        if (file.exists()) {
            return load(url, version, file);
        } else {
            final SvnNode result = super.fetch(url, version, isDir);
            store(result, version);
            return result;
        }
    }

    private synchronized void store(final SvnNode entry, final long version) {
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
                    for (final SvnFolderEntry item : entry.children) {
                        writer.write(item.isDir ? DIR : FILE);
                        writer.write('$');
                        writer.write(Long.toString(item.version));
                        writer.write('$');
                        writer.write(item.name);
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

    private synchronized SvnNode load(final SVNUrl url, final long version, final File file) {
        BufferedReader reader = null;
        try {
            reader = new BufferedReader(new InputStreamReader(new FileInputStream(file),
                    Charset.forName("UTF-8")));
            final String type = reader.readLine();
            if (DIR.equalsIgnoreCase(type)) {
                return loadDir(url, version, reader);
            } else if (FILE.equalsIgnoreCase(type)) {
                return loadFile(url, version, reader);
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

    private SvnNode loadDir(final SVNUrl url, final long version, final BufferedReader reader)
            throws NumberFormatException, IOException {
        final Collection<SvnFolderEntry> entries = new ArrayList<SvnFolderEntry>();
        String line = null;
        while ((line = reader.readLine()) != null) {
            final int sep1 = line.indexOf('$');
            final String type = line.substring(0, sep1);

            line = line.substring(sep1 + 1);

            final int sep2 = line.indexOf('$');
            final long entryVersion = Long.parseLong(line.substring(0, sep2));
            final String name = line.substring(sep2 + 1);

            entries.add(new SvnFolderEntry(name, entryVersion, DIR.equalsIgnoreCase(type)));
        }

        return new SvnNode(url, version, Collections.unmodifiableCollection(entries));
    }

    private SvnNode loadFile(final SVNUrl url, final long version, final BufferedReader reader)
            throws IOException {
        final StringBuilder content = new StringBuilder();
        String line = null;
        while ((line = reader.readLine()) != null) {
            content.append(line).append('\n');
        }

        return new SvnNode(url, version, content.toString());
    }
}
