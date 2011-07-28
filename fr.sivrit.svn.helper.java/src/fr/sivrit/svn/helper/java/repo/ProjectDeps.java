package fr.sivrit.svn.helper.java.repo;

import java.util.HashSet;
import java.util.Set;

public class ProjectDeps {
    public static boolean doMatch(final ProjectDeps a, final ProjectDeps b) {
        if (a.name != null && b.name != null && a.name.equals(b.name)) {
            return true;
        } else {
            return a.plugin != null && b.plugin != null && a.plugin.equals(b.plugin);
        }
    }

    private String name;
    private String plugin;
    private final Set<String> projectDeps = new HashSet<String>();
    private final Set<String> pluginDeps = new HashSet<String>();

    public ProjectDeps() {
        super();
    }

    public ProjectDeps(final String name, final String plugin) {
        super();
        this.name = name;
        this.plugin = plugin;
    }

    public ProjectDeps(final String name, final String plugin, final Set<String> projectDeps,
            final Set<String> pluginDeps) {
        super();
        this.name = name;
        this.plugin = plugin;
        this.projectDeps.addAll(projectDeps);
        this.pluginDeps.addAll(pluginDeps);
    }

    public String getName() {
        return name;
    }

    public void setName(final String name) {
        this.name = name;
    }

    public String getPlugin() {
        return plugin;
    }

    public void setPlugin(final String plugin) {
        this.plugin = plugin;
    }

    public Set<String> getProjectDeps() {
        return projectDeps;
    }

    public Set<String> getPluginDeps() {
        return pluginDeps;
    }
}
