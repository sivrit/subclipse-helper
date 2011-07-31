package fr.sivrit.svn.helper.java.svn;

import java.io.IOException;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.regex.Pattern;

import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.svnclientadapter.ISVNDirEntry;
import org.tigris.subversion.svnclientadapter.ISVNInfo;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNNodeKind;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.tools.ProjectUtils;

public class Crawler {
    private final static Pattern[] NO_EXCLUSIONS = new Pattern[0];

    private final Pattern[] exclusions;
    private final Set<SVNUrl> excludedUrls = Collections.synchronizedSet(new HashSet<SVNUrl>());
    private final Set<SVNUrl> badUrls = Collections.synchronizedSet(new HashSet<SVNUrl>());
    private final Set<SVNUrl> alreadyChecked = Collections.synchronizedSet(new HashSet<SVNUrl>());

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
            executor.execute(new CrawlerRunnable(result, executor, url, liveActors));
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
        if (!isDir(url)) {
            badUrls.add(url);
            return true;
        }

        return !alreadyChecked.add(url);
    }

    private boolean isDir(final SVNUrl url) throws SVNException, SVNClientException {
        final ISVNInfo info = SvnClient.create().getInfo(url);
        return info.getNodeKind() == SVNNodeKind.DIR;
    }

    private class CrawlerRunnable implements Runnable {
        private final Set<RemoteProject> result;
        private final ExecutorService executor;
        private final SVNUrl url;
        private final AtomicInteger liveActors;

        private final SvnClient svn;

        public CrawlerRunnable(final Set<RemoteProject> result, final ExecutorService executor,
                final SVNUrl url, final AtomicInteger liveActors) throws SVNException {
            super();
            this.result = result;
            this.executor = executor;
            this.url = url;
            this.liveActors = liveActors;

            this.svn = SvnClient.create();

            liveActors.incrementAndGet();
        }

        @Override
        public void run() {
            try {
                final ISVNDirEntry[] entries = svn.getList(url);

                final RemoteProject project = identifyProject(entries);
                if (project == null) {
                    for (final ISVNDirEntry entry : entries) {
                        if (entry.getNodeKind() == SVNNodeKind.DIR) {
                            final SVNUrl newUrl = url.appendPath(entry.getPath());

                            if (!isExcluded(newUrl) && !isAreadyChecked(newUrl)) {
                                executor.execute(new CrawlerRunnable(result, executor, newUrl,
                                        liveActors));
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

        private RemoteProject identifyProject(final ISVNDirEntry[] entries)
                throws SVNClientException {
            for (final ISVNDirEntry entry : entries) {
                if (entry.getNodeKind() == SVNNodeKind.FILE && ".project".equals(entry.getPath())) {
                    final RemoteProject project = new RemoteProject(url);
                    fillProjectInfo(project, url.appendPath(entry.getPath()));
                    fillPluginInfo(project, entries);
                    return project;
                }
            }

            return null;
        }

        private void fillProjectInfo(final RemoteProject project, final SVNUrl projectFile)
                throws SVNClientException {
            try {
                project.setName(ProjectUtils.findProjectName(svn.getContent(projectFile)));
            } catch (final IOException e) {
                throw new SVNClientException(e);
            }
        }

        private void fillPluginInfo(final RemoteProject project, final ISVNDirEntry[] entries)
                throws SVNClientException {
            for (final ISVNDirEntry entry : entries) {
                if (entry.getNodeKind() == SVNNodeKind.DIR && "META-INF".equals(entry.getPath())) {
                    final SVNUrl metaInfUrl = url.appendPath(entry.getPath());
                    final ISVNDirEntry[] metaInfEntries = svn.getList(metaInfUrl);

                    for (final ISVNDirEntry metaEntry : metaInfEntries) {
                        if (metaEntry.getNodeKind() == SVNNodeKind.FILE
                                && "MANIFEST.MF".equals(metaEntry.getPath())) {
                            fillPluginInfo(project, metaInfUrl.appendPath(metaEntry.getPath()));
                        }
                    }
                }
            }
        }

        private void fillPluginInfo(final RemoteProject project, final SVNUrl manifestFile)
                throws SVNClientException {
            final String content = svn.getStringContent(manifestFile);
            project.setPlugin(ProjectUtils.findPluginName(content));
            ProjectUtils.fillFromManifest(project, ProjectUtils.splitLines(content));
        }
    }
}
