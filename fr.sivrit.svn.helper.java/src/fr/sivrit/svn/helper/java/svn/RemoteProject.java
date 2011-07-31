package fr.sivrit.svn.helper.java.svn;

import java.util.Set;

import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.java.repo.ProjectDeps;

public class RemoteProject extends ProjectDeps {
    private SVNUrl url;

    public RemoteProject(final SVNUrl url) {
        super();
        this.url = url;
    }

    public RemoteProject(final String name, final String plugin, final Set<String> projectDeps,
            final Set<String> pluginDeps) {
        super(name, plugin, projectDeps, pluginDeps);
    }

    public RemoteProject(final String name, final String plugin) {
        super(name, plugin);
    }

    public SVNUrl getUrl() {
        return url;
    }

    public void setUrl(final SVNUrl url) {
        this.url = url;
    }
}
