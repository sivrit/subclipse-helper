package fr.sivrit.svn.helper.java;

import java.util.Arrays;
import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

import org.eclipse.core.resources.IProject;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.team.core.RepositoryProvider;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.SVNException;
import org.tigris.subversion.subclipse.core.SVNProviderPlugin;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.ISvnHelper;
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

    @Override
    public boolean resolveDependencies(final SVNUrl[] svnUrl) {
        throw new UnsupportedOperationException("Todo");
    }

    @Override
    public boolean createWorkingSets(final SVNUrl[] urls) {
        throw new UnsupportedOperationException("Todo");
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

}
