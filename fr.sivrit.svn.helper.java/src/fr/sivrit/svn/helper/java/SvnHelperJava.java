package fr.sivrit.svn.helper.java;

import java.lang.reflect.InvocationTargetException;
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
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.SubMonitor;
import org.eclipse.jdt.internal.ui.packageview.PackageExplorerPart;
import org.eclipse.jdt.ui.JavaUI;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.dialogs.ProgressMonitorDialog;
import org.eclipse.jface.operation.IRunnableWithProgress;
import org.eclipse.swt.widgets.Shell;
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
import org.tigris.subversion.svnclientadapter.ISVNStatus;
import org.tigris.subversion.svnclientadapter.SVNClientException;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.ISvnHelper;
import fr.sivrit.svn.helper.Logger;
import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.repo.ProjectDeps;
import fr.sivrit.svn.helper.java.svn.CheckOut;
import fr.sivrit.svn.helper.java.svn.Crawler;
import fr.sivrit.svn.helper.java.svn.RemoteProject;
import fr.sivrit.svn.helper.java.svn.SvnAdapter;
import fr.sivrit.svn.helper.java.svn.Switch;
import fr.sivrit.svn.helper.java.tools.ProjectUtils;

@SuppressWarnings("restriction")
public class SvnHelperJava implements ISvnHelper {
    public final static String PLUGIN_ID = "fr.sivrit.svn.helper.java";

    @Override
    public boolean checkoutBranch(final ISVNRepositoryLocation repo, final SVNUrl[] svnUrls) {

        final Set<RemoteProject> svnProjects = new HashSet<RemoteProject>();

        final Set<RemoteProject> toSwitch = new HashSet<RemoteProject>();
        final Set<RemoteProject> toCo = new HashSet<RemoteProject>();

        final ProgressMonitorDialog progress = new ProgressMonitorDialog(null);
        try {
            progress.run(true, true, new IRunnableWithProgress() {

                @Override
                public void run(final IProgressMonitor monitor) throws InvocationTargetException,
                        InterruptedException {
                    final SubMonitor subMonitor = SubMonitor.convert(monitor);

                    subMonitor.setWorkRemaining(10);
                    subMonitor.setTaskName("Looking for projects...");

                    final Crawler crawler = new Crawler(subMonitor.newChild(9,
                            SubMonitor.SUPPRESS_BEGINTASK), true);
                    try {
                        svnProjects.addAll(crawler.findProjects(svnUrls));

                        subMonitor.setTaskName("Comparing projects to workspace...");
                        sortOut(svnProjects, toSwitch, toCo,
                                subMonitor.newChild(1, SubMonitor.SUPPRESS_BEGINTASK));
                    } catch (final SVNException e) {
                        throw new InvocationTargetException(e);
                    } catch (final SVNClientException e) {
                        throw new InvocationTargetException(e);
                    }
                }
            });
        } catch (InvocationTargetException e) {
            Logger.log(IStatus.ERROR, SvnHelperJava.PLUGIN_ID, e);
            return false;
        } catch (InterruptedException e) {
            Logger.log(IStatus.ERROR, SvnHelperJava.PLUGIN_ID, e);
            return false;
        } finally {
            SvnAdapter.clearPool();
        }

        final String title = svnUrls.length == 1 ? "Pull branch " + svnUrls[0]
                : "Pull multiple branches";
        String msg = "Projects found: " + svnProjects.size() + "\nProjects to switch: "
                + toSwitch.size() + "\nProjects to checkout: " + toCo.size();
        if (svnUrls.length > 1) {
            msg = "Pulling branches: " + Arrays.asList(svnUrls) + "\n\n" + msg;
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
            final Set<RemoteProject> toSwitch, final Set<RemoteProject> toCo,
            final SubMonitor subMonitor) throws SVNException, SVNClientException {

        subMonitor.setWorkRemaining(2);

        final SvnAdapter svn = SvnAdapter.borrow();
        try {
            final Set<ProjectDeps> workspace = findTeamWorkspaceProjects(subMonitor.newChild(1));

            subMonitor.setWorkRemaining(remotes.size());
            for (final RemoteProject remote : remotes) {
                subMonitor.subTask("Processing project " + remote.getName() + "...");

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
                    final IProject iproj = ProjectUtils.findWorkspaceProject(local.getName());
                    assert iproj != null : local.getName();

                    final ISVNStatus status = svn.getStatus(iproj.getLocation().toFile());

                    if (!remote.getUrl().equals(status.getUrl())) {
                        toSwitch.add(remote);
                    }
                }

                subMonitor.worked(1);
            }
        } finally {
            SvnAdapter.release(svn);
        }
    }

    private Set<ProjectDeps> findTeamWorkspaceProjects(final SubMonitor subMonitor) {
        final Set<ProjectDeps> result = new HashSet<ProjectDeps>();

        subMonitor.subTask("Gathering projects from workspace...");
        subMonitor.setWorkRemaining(2);

        final Collection<ProjectDeps> projects = ProjectUtils.findWorkspaceProjects(subMonitor
                .newChild(1));

        subMonitor.subTask("Filtering SVN projects...");
        subMonitor.setWorkRemaining(projects.size());
        for (final ProjectDeps deps : projects) {
            if (deps.getName() != null) {
                final IProject iproject = ProjectUtils.findWorkspaceProject(deps.getName());
                if (RepositoryProvider.getProvider(iproject, SVNProviderPlugin.getTypeId()) != null) {
                    result.add(deps);
                }
            }

            subMonitor.worked(1);
        }

        return result;
    }

    @Override
    public boolean resolveDependencies(final SVNUrl[] svnUrl) {
        throw new UnsupportedOperationException("Todo");
    }

    @Override
    public boolean createWorkingSets(final SVNUrl[] urls) {
        // Map URLs to WorkingSets
        final String[] wsNames;
        if (Preferences.getByPassWorkingSetsMapping()) {
            wsNames = new String[urls.length];
            for (int i = 0; i < urls.length; i++) {
                wsNames[i] = urls[i].getLastPathSegment();

            }
        } else {
            final Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
            final AssignWorkingSetsDialog dialog = new AssignWorkingSetsDialog(shell,
                    "Define Working Sets",
                    "Please map the selected URLs to existing or new WorkingSets.", urls);
            if (dialog.open() != 0) {
                return false;
            }
            wsNames = dialog.getWs();
        }

        // Find the projects to dispatch
        final Map<String, Collection<IProject>> newWS;
        try {
            newWS = resolveWorkingSetFromSvn(urls, wsNames);
        } catch (final SVNException e) {
            e.printStackTrace();
            return false;
        } catch (InvocationTargetException e) {
            e.printStackTrace();
            return false;
        } catch (InterruptedException e) {
            e.printStackTrace();
            return false;
        }

        // Confirm the operation
        if (Preferences.getConfirmWorkingSetsCreation()) {
            final StringBuilder msg = new StringBuilder(
                    "The following WorkingSets will be defined:\n");
            for (final Entry<String, Collection<IProject>> entry : newWS.entrySet()) {
                msg.append(entry.getKey()).append(" - ").append(entry.getValue().size())
                        .append(" projects\n");
            }
            if (!MessageDialog.openQuestion(null, "Define Working Sets", msg.toString())) {
                return false;
            }
        }

        if (Preferences.getSwitchPerspective()) {
            // Show the Java perspective, the package explorer and witch to a
            // view by working sets.
            final IWorkbench workbench = PlatformUI.getWorkbench();
            try {
                workbench.showPerspective(JavaUI.ID_PERSPECTIVE,
                        workbench.getActiveWorkbenchWindow());
            } catch (final WorkbenchException e) {
                e.printStackTrace();
                return false;
            }

            final PackageExplorerPart explo = PackageExplorerPart.openInActivePerspective();
            if (explo.getRootMode() == PackageExplorerPart.PROJECTS_AS_ROOTS) {
                explo.rootModeChanged(PackageExplorerPart.WORKING_SETS_AS_ROOTS);
            }
        }

        // Create missing IWorkingSet and index all WS by name
        final Map<String, IWorkingSet> workingSets = createWorkingSets(new HashSet<String>(
                Arrays.asList(wsNames)));

        if (Preferences.getTransferFromWorkingSets()) {
            // Remove the projects we will dispatch from existing WS
            final Set<IProject> allProjects = new HashSet<IProject>();
            for (final Collection<IProject> projects : newWS.values()) {
                allProjects.addAll(projects);
            }
            removeProjectsFromWorkingSets(workingSets.values(), allProjects);
        }

        // Dispatch the projects
        final IWorkingSetManager wsManager = WorkbenchPlugin.getDefault().getWorkingSetManager();
        for (final Entry<String, Collection<IProject>> entry : newWS.entrySet()) {
            final String wsName = entry.getKey();
            final Collection<IProject> projects = entry.getValue();

            final IWorkingSet[] workingSet = new IWorkingSet[] { workingSets.get(wsName) };

            for (final IProject project : projects) {
                wsManager.addToWorkingSets(project, workingSet);
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
     * @throws InvocationTargetException
     * @throws InterruptedException
     */
    private Map<String, Collection<IProject>> resolveWorkingSetFromSvn(final SVNUrl[] urls,
            final String[] wsNames) throws SVNException, InvocationTargetException,
            InterruptedException {
        assert urls.length == wsNames.length;

        final boolean useExclusions = Preferences.getApplyOnWokingSet();

        final Map<String, Collection<IProject>> newWS = new HashMap<String, Collection<IProject>>();

        final ProgressMonitorDialog progress = new ProgressMonitorDialog(null);
        progress.run(true, true, new IRunnableWithProgress() {
            @Override
            public void run(final IProgressMonitor monitor) throws InvocationTargetException,
                    InterruptedException {
                final SubMonitor subMonitor = SubMonitor.convert(monitor);

                subMonitor.setWorkRemaining(10);
                final Set<ProjectDeps> workspace = findTeamWorkspaceProjects(subMonitor.newChild(1));

                subMonitor.setWorkRemaining(urls.length);

                for (int i = 0; i < urls.length; i++) {
                    final SVNUrl url = urls[i];
                    final String wsName = wsNames[i];

                    subMonitor.setTaskName("Resolving projects in " + url.toString() + "...");

                    final List<IProject> wsProjects = new ArrayList<IProject>();

                    final Set<RemoteProject> svnProjects;
                    try {
                        svnProjects = new Crawler(subMonitor.newChild(1,
                                SubMonitor.SUPPRESS_BEGINTASK), useExclusions)
                                .findProjects(new SVNUrl[] { url });
                    } catch (final SVNException e) {
                        throw new InvocationTargetException(e);
                    } finally {
                        SvnAdapter.clearPool();
                    }

                    for (final RemoteProject svnProject : svnProjects) {
                        for (final ProjectDeps local : workspace) {
                            if (ProjectDeps.doMatch(svnProject, local)) {
                                wsProjects.add(ProjectUtils.findWorkspaceProject(local.getName()));
                                break;
                            }
                        }
                    }

                    if (!svnProjects.isEmpty()) {
                        final Collection<IProject> preExistingProjects = newWS.get(wsName);
                        if (preExistingProjects == null) {
                            newWS.put(wsName, wsProjects);
                        } else {
                            preExistingProjects.addAll(wsProjects);
                        }
                    }
                }
            }
        });

        return newWS;
    }
}
