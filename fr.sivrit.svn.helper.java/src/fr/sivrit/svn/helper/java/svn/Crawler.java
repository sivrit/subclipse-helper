package fr.sivrit.svn.helper.java.svn;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.svn.Cache.CachedEntry;
import fr.sivrit.svn.helper.java.tools.ProjectUtils;

public class Crawler {
    private final static Pattern[] NO_EXCLUSIONS = new Pattern[0];

    private final Pattern[] exclusions;
    private final Set<SVNUrl> excludedUrls = Collections.synchronizedSet(new HashSet<SVNUrl>());
    private final Set<SVNUrl> alreadyChecked = Collections.synchronizedSet(new HashSet<SVNUrl>());

    private final Cache cache = new Cache();

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
            executor.execute(new CrawlerRunnable(result, executor, url, null, liveActors));
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
        private Long version;
        private final AtomicInteger liveActors;

        private final SvnClient svn;

        public CrawlerRunnable(final Set<RemoteProject> result, final ExecutorService executor,
                final SVNUrl url, final Long version, final AtomicInteger liveActors)
                throws SVNException {
            super();
            this.result = result;
            this.executor = executor;
            this.url = url;
            this.version = version;
            this.liveActors = liveActors;

            this.svn = SvnClient.create();

            liveActors.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                if (version == null) {
                    version = svn.getInfo(url).getLastChangedRevision().getNumber();
                }

                final CachedEntry entry = cache.fetch(svn, url, version);
                assert entry.isDir : url.toString();

                final RemoteProject project = identifyProject(entry.children);
                if (project == null) {
                    for (final Map.Entry<String, Long> child : entry.children.entrySet()) {
                        final SVNUrl newUrl = url.appendPath(child.getKey());
                        final CachedEntry childEntry = cache.fetch(svn, newUrl, child.getValue());
                        if (childEntry.isDir) {
                            if (!isExcluded(newUrl) && !isAreadyChecked(newUrl)) {
                                executor.execute(new CrawlerRunnable(result, executor, newUrl,
                                        child.getValue(), liveActors));
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

        private RemoteProject identifyProject(final Map<String, Long> entries)
                throws SVNClientException {
            for (final Map.Entry<String, Long> entry : entries.entrySet()) {
                if (".project".equals(entry.getKey())) {
                    final SVNUrl entryURL = url.appendPath(entry.getKey());
                    final CachedEntry content = cache.fetch(svn, entryURL, entry.getValue());
                    if (!content.isDir) {
                        final RemoteProject project = new RemoteProject(url);
                        fillProjectInfo(project, content);
                        fillPluginInfo(project, entries);
                        return project;
                    }
                }
            }

            return null;
        }

        private void fillProjectInfo(final RemoteProject project, final CachedEntry content)
                throws SVNClientException {
            try {
                project.setName(ProjectUtils.findProjectName(content.content));
            } catch (final IOException e) {
                throw new SVNClientException(e);
            }
        }

        private void fillPluginInfo(final RemoteProject project, final Map<String, Long> entries)
                throws SVNClientException {
            for (final Map.Entry<String, Long> entry : entries.entrySet()) {
                if ("META-INF".equals(entry.getKey())) {
                    final SVNUrl metaInfUrl = url.appendPath(entry.getKey());
                    final CachedEntry content = cache.fetch(svn, metaInfUrl, entry.getValue());
                    if (content.isDir) {
                        for (final Map.Entry<String, Long> metaEntry : content.children.entrySet()) {
                            if ("MANIFEST.MF".equals(metaEntry.getKey())) {
                                final SVNUrl manifestUrl = metaInfUrl
                                        .appendPath(metaEntry.getKey());
                                final CachedEntry manifest = cache.fetch(svn, manifestUrl,
                                        metaEntry.getValue());
                                if (!manifest.isDir) {
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
    }
}
