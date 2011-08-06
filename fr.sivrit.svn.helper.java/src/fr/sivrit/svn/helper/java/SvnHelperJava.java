package fr.sivrit.svn.helper.java;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.core.runtime.IAdaptable;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.team.core.RepositoryProvider;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.WorkbenchException;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.ISvnHelper;
import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.repo.ProjectDeps;
import fr.sivrit.svn.helper.java.svn.CheckOut;
import fr.sivrit.svn.helper.java.svn.Crawler;
import fr.sivrit.svn.helper.java.svn.RemoteProject;
import fr.sivrit.svn.helper.java.svn.Switch;
import fr.sivrit.svn.helper.java.tools.ProjectUtils;

public class SvnHelperJava implements ISvnHelper {
    public final static String PLUGIN_ID = "fr.sivrit.svn.helper.java";

    @Override
    public boolean checkoutBranch(final ISVNRepositoryLocation repo, final SVNUrl[] svnUrls) {

        final Crawler crawler = new Crawler(true);

        final Set<RemoteProject> svnProjects;
        try {
            svnProjects = crawler.findProjects(svnUrls);
        } catch (final SVNException e) {
            e.printStackTrace();
            return false;
        }

        final Set<RemoteProject> toSwitch = new HashSet<RemoteProject>();
        final Set<RemoteProject> toCo = new HashSet<RemoteProject>();
        sortOut(svnProjects, toSwitch, toCo);

        final String title = svnUrls.length == 1 ? "checkoutBranch: " + svnUrls[0]
                : "checkoutBranches";
        String msg = "Projects found: " + svnProjects.size() + "\nWill switch: " + toSwitch.size()
                + "\nWill checkout: " + toCo.size();
        if (svnUrls.length > 1) {
            msg = "checkoutBranches: " + Arrays.asList(svnUrls) + "\n\n" + msg;
        }

        if (!MessageDialog.openQuestion(null, title, msg)) {
            return false;
        }

        // Checkout
        CheckOut.run(repo, toCo);

        // Switch
        Switch.run(toSwitch);

        return true;

    }

    private void sortOut(final Collection<RemoteProject> remotes,
            final Set<RemoteProject> toSwitch, final Set<RemoteProject> toCo) {

        final Set<ProjectDeps> workspace = findTeamWorkspaceProjects();

        for (final RemoteProject remote : remotes) {
            ProjectDeps local = null;
            for (final ProjectDeps existing : workspace) {
                if (ProjectDeps.doMatch(existing, remote)) {
                    local = existing;
                    break;
                }
            }

            if (local == null) {
                toCo.add(remote);
            } else {
                toSwitch.add(remote);
            }
        }
    }

    private Set<ProjectDeps> findTeamWorkspaceProjects() {
        final Set<ProjectDeps> result = new HashSet<ProjectDeps>();

        for (final ProjectDeps deps : ProjectUtils.findWorkspaceProjects()) {
            if (deps.getName() != null) {
                final IProject iproject = ProjectUtils.findWorkspaceProject(deps.getName());
                if (RepositoryProvider.getProvider(iproject, SVNProviderPlugin.getTypeId()) != null) {
                    result.add(deps);
                }
            }
        }

        return result;
    }

    @Override
    public boolean resolveDependencies(final SVNUrl[] svnUrl) {
        throw new UnsupportedOperationException("Todo");
    }

    @SuppressWarnings("restriction")
    @Override
    public boolean createWorkingSets(final SVNUrl[] urls) {
        // Find the projects to dispatch
        final Map<String, Collection<IProject>> newWS;
        try {
            newWS = resolveWorkingSetFromSvn(urls);
        } catch (final SVNException e) {
            e.printStackTrace();
            return false;
        }

        // Confirm the operation
        final StringBuilder msg = new StringBuilder("The following WorkingSets will be created:\n");
        for (final Entry<String, Collection<IProject>> entry : newWS.entrySet()) {
            msg.append(entry.getKey()).append(" - ").append(entry.getValue().size())
                    .append(" projects\n");
        }

        if (!MessageDialog.openQuestion(null, "Define Working Sets", msg.toString())) {
            return false;
        }

        // Show the Java perspective, the package explorer and witch to a view
        // by working sets.
        final IWorkbench workbench = PlatformUI.getWorkbench();
        try {
            workbench.showPerspective(JavaUI.ID_PERSPECTIVE, workbench.getActiveWorkbenchWindow());
        } catch (final WorkbenchException e) {
            e.printStackTrace();
            return false;
        }

        final PackageExplorerPart explo = PackageExplorerPart.openInActivePerspective();
        if (explo.getRootMode() == PackageExplorerPart.PROJECTS_AS_ROOTS) {
            explo.rootModeChanged(PackageExplorerPart.WORKING_SETS_AS_ROOTS);
        }

        // Create missing IWorkingSet and index all WS by name
        final Map<String, IWorkingSet> workingSets = createWorkingSets(newWS.keySet());

        // Remove the projects we will dispatch from existing WS
        final Set<IProject> allProjects = new HashSet<IProject>();
        for (final Collection<IProject> projects : newWS.values()) {
            allProjects.addAll(projects);
        }
        removeProjectsFromWorkingSets(workingSets.values(), allProjects);

        // Dispatch the projects
        final IWorkingSetManager wsManager = WorkbenchPlugin.getDefault().getWorkingSetManager();
        for (final Entry<String, Collection<IProject>> entry : newWS.entrySet()) {
            final String wsName = entry.getKey();
            final Collection<IProject> projects = entry.getValue();

            final IWorkingSet[] workingSet = new IWorkingSet[] { workingSets.get(wsName) };

            for (final IProject project : projects) {
                WorkbenchPlugin.log("Will add " + project.getName() + " to " + wsName);
                wsManager.addToWorkingSets(project, workingSet);
                // wsm.addActiveWorkingSet(workingSets.get(wsName));
            }

        }

        return true;
    }

    private void removeProjectsFromWorkingSets(final Collection<IWorkingSet> sets,
            final Set<IProject> projectsToRemove) {
        for (final IWorkingSet ws : sets) {
            removeProjectsFromWorkingSet(ws, projectsToRemove);
        }
    }

    private void removeProjectsFromWorkingSet(final IWorkingSet ws,
            final Set<IProject> projectsToRemove) {
        final IAdaptable[] content = ws.getElements();

        int lastElement = content.length - 1;
        int idx = 0;
        while (idx <= lastElement) {
            final IAdaptable item = content[idx];
            final IProject project = (IProject) item.getAdapter(IProject.class);
            if (projectsToRemove.contains(project)) {
                content[idx] = content[lastElement];
                content[lastElement] = null;
                lastElement -= 1;
            } else {
                idx += 1;
            }
        }

        final IAdaptable[] newContent = new IAdaptable[lastElement + 1];
        System.arraycopy(content, 0, newContent, 0, lastElement + 1);

        ws.setElements(newContent);
    }

    /**
     * Ensure working sets with the provided names do exists, and return all
     * working sets indexed by name
     * 
     * @param wsNames
     * @return
     */
    private Map<String, IWorkingSet> createWorkingSets(final Collection<String> wsNames) {
        @SuppressWarnings("restriction")
        final IWorkingSetManager wsManager = WorkbenchPlugin.getDefault().getWorkingSetManager();

        final Map<String, IWorkingSet> workingSets = new HashMap<String, IWorkingSet>();

        // index all existing IWorkingSet
        for (final IWorkingSet ws : wsManager.getWorkingSets()) {
            workingSets.put(ws.getName(), ws);
        }

        for (final String wsName : wsNames) {
            if (!workingSets.containsKey(wsName)) {
                final IWorkingSet ws = wsManager.createWorkingSet(wsName, new IAdaptable[0]);
                ws.setId("org.eclipse.jdt.ui.JavaWorkingSetPage");
                wsManager.addWorkingSet(ws);
                workingSets.put(wsName, ws);
            }
        }

        return workingSets;
    }

    /**
     * For each provided URL, return an entry with the last part of the URL as
     * key (expected to become a working set), and a collection of all projects
     * found below this URL in the repository as key. Only projects located in
     * the workspace are returned.
     * 
     * @param urls
     * @return
     * @throws SVNException
     */
    private Map<String, Collection<IProject>> resolveWorkingSetFromSvn(final SVNUrl[] urls)
            throws SVNException {
        final boolean useExclusions = Preferences.getApplyOnWokingSet();

        final Set<ProjectDeps> workspace = findTeamWorkspaceProjects();

        final Map<String, Collection<IProject>> newWS = new HashMap<String, Collection<IProject>>();

        for (final SVNUrl url : urls) {

            final String wsName = url.getLastPathSegment();
            final List<IProject> wsProjects = new ArrayList<IProject>();

            final Set<RemoteProject> svnProjects = new Crawler(useExclusions)
                    .findProjects(new SVNUrl[] { url });
            for (final RemoteProject svnProject : svnProjects) {
                for (final ProjectDeps local : workspace) {
                    if (ProjectDeps.doMatch(svnProject, local)) {
                        wsProjects.add(ProjectUtils.findWorkspaceProject(local.getName()));
                        break;
                    }
                }
            }

            if (!svnProjects.isEmpty()) {
                newWS.put(wsName, wsProjects);
            }
        }

        return newWS;
    }
}
