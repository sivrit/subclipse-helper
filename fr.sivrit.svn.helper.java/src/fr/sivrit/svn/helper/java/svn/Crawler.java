package fr.sivrit.svn.helper.java.svn;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.tools.ProjectUtils;

public class Crawler {
    private final static Pattern[] NO_EXCLUSIONS = new Pattern[0];

    private final Pattern[] exclusions;
    private final Set<SVNUrl> excludedUrls = Collections.synchronizedSet(new HashSet<SVNUrl>());
    private final Set<SVNUrl> alreadyChecked = Collections.synchronizedSet(new HashSet<SVNUrl>());

    private final SvnClient client = SvnClient.createClient();

    public Crawler(final boolean useExclusions) {
        if (useExclusions) {
            final String[] exclusionsStr = Preferences.getExclusions();
            exclusions = new Pattern[exclusionsStr.length];
            for (int i = 0; i < exclusionsStr.length; i++) {
                exclusions[i] = Pattern.compile(exclusionsStr[i]);
            }
        } else {
            exclusions = NO_EXCLUSIONS;
        }
    }

    public Set<RemoteProject> findProjects(final SVNUrl[] urls) throws SVNException {
        return parallelFindProjects(urls);
    }

    public Set<RemoteProject> parallelFindProjects(final SVNUrl[] urls) throws SVNException {
        final Set<RemoteProject> result = Collections.synchronizedSet(new HashSet<RemoteProject>());

        final AtomicInteger liveActors = new AtomicInteger(0);

        final int maxThreads = Preferences.getMaxCrawlerRequests();
        final ExecutorService executor = Executors.newFixedThreadPool(maxThreads);

        for (final SVNUrl url : urls) {
            executor.execute(new CrawlerRunnable(result, executor, liveActors, url));
        }

        while (liveActors.get() != 0) {
            synchronized (this) {
                try {
                    this.wait(1000);
                } catch (final InterruptedException e) {
                    Thread.interrupted();
                }
            }
        }

        executor.shutdownNow();

        return result;
    }

    private boolean isExcluded(final SVNUrl url) {
        final String urlString = url.toString();

        for (final Pattern pattern : exclusions) {
            if (pattern.matcher(urlString).matches()) {
                excludedUrls.add(url);
                return true;
            }
        }

        return false;
    }

    private boolean isAreadyChecked(final SVNUrl url) throws SVNException, SVNClientException {
        return !alreadyChecked.add(url);
    }

    private class CrawlerRunnable implements Runnable {
        private final Set<RemoteProject> result;
        private final ExecutorService executor;
        private final SVNUrl url;
        private final SvnFolderEntry entry;
        private final AtomicInteger liveActors;

        public CrawlerRunnable(final Set<RemoteProject> result, final ExecutorService executor,
                final AtomicInteger liveActors, final SVNUrl url) throws SVNException {
            super();
            this.result = result;
            this.executor = executor;
            this.url = url;
            this.entry = null;
            this.liveActors = liveActors;

            liveActors.incrementAndGet();
        }

        public CrawlerRunnable(final Set<RemoteProject> result, final ExecutorService executor,
                final AtomicInteger liveActors, final SVNUrl url, final SvnFolderEntry entry)
                throws SVNException {
            super();
            this.result = result;
            this.executor = executor;
            this.url = url;
            this.entry = entry;
            this.liveActors = liveActors;

            liveActors.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                final SvnNode node;
                if (entry == null) {
                    node = client.fetch(url);
                    if (!node.isDir) {
                        return;
                    }
                } else {
                    final boolean isDir = entry.isDir;
                    final long version = entry.version;
                    assert isDir : url.toString();
                    node = client.fetch(url, version, isDir);

                }

                assert node.isDir : url.toString();

                final RemoteProject project = identifyProject(node.children);
                if (project == null) {
                    for (final SvnFolderEntry child : node.children) {
                        if (child.isDir) {
                            final SVNUrl newUrl = url.appendPath(child.name);
                            if (!isExcluded(newUrl) && !isAreadyChecked(newUrl)) {
                                executor.execute(new CrawlerRunnable(result, executor, liveActors,
                                        newUrl, child));
                            }
                        }
                    }
                } else {
                    result.add(project);
                }

            } catch (final SVNClientException e) {
                e.printStackTrace();
            } catch (final SVNException e) {
                e.printStackTrace();
            } finally {
                liveActors.decrementAndGet();

                synchronized (Crawler.this) {
                    Crawler.this.notify();
                }
            }
        }

        private RemoteProject identifyProject(final Collection<SvnFolderEntry> entries)
                throws SVNClientException, SVNException {
            for (final SvnFolderEntry entry : entries) {
                if (!entry.isDir && ".project".equals(entry.name)) {
                    final SVNUrl entryURL = url.appendPath(entry.name);
                    final SvnNode node = client.fetch(entryURL, entry.version, entry.isDir);
                    assert !node.isDir : entryURL.toString();

                    final RemoteProject project = new RemoteProject(url);
                    fillProjectInfo(project, node);
                    fillPluginInfo(project, entries);
                    return project;
                }
            }

            return null;
        }

        private void fillProjectInfo(final RemoteProject project, final SvnNode node)
                throws SVNClientException {
            try {
                project.setName(ProjectUtils.findProjectName(node.content));
            } catch (final IOException e) {
                throw new SVNClientException(e);
            }
        }

        private void fillPluginInfo(final RemoteProject project,
                final Collection<SvnFolderEntry> entries) throws SVNClientException, SVNException {
            for (final SvnFolderEntry entry : entries) {
                if (entry.isDir && "META-INF".equals(entry.name)) {
                    final SVNUrl metaInfUrl = url.appendPath(entry.name);
                    final SvnNode metaInf = client.fetch(metaInfUrl, entry.version, entry.isDir);
                    assert metaInf.isDir : metaInfUrl.toString();

                    for (final SvnFolderEntry metaEntry : metaInf.children) {
                        if (!metaEntry.isDir && "MANIFEST.MF".equals(metaEntry.name)) {
                            final SVNUrl manifestUrl = metaInfUrl.appendPath(metaEntry.name);
                            final SvnNode manifest = client.fetch(manifestUrl, metaEntry.version,
                                    metaEntry.isDir);
                            assert !manifest.isDir : manifestUrl.toString();

                            project.setPlugin(ProjectUtils.findPluginName(manifest.content));
                            ProjectUtils.fillFromManifest(project,
                                    ProjectUtils.splitLines(manifest.content));
                        }
                    }
                }
            }
        }
    }
}
