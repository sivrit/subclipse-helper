package fr.sivrit.svn.helper.java.svn;

import java.lang.reflect.InvocationTargetException;
import java.util.Set;

import org.eclipse.core.resources.IResource;
import org.eclipse.core.runtime.IProgressMonitor;
import org.eclipse.core.runtime.IStatus;
import org.eclipse.core.runtime.Status;
import org.eclipse.core.runtime.jobs.Job;
import org.tigris.subversion.subclipse.core.ISVNCoreConstants;
import org.tigris.subversion.subclipse.ui.operations.SwitchOperation;
import org.tigris.subversion.svnclientadapter.SVNRevision;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.java.SvnHelperJava;
import fr.sivrit.svn.helper.java.tools.ProjectUtils;

public class Switch {
    public static void run(final Set<RemoteProject> toSwitch) {

        final int len = toSwitch.size();
        final SVNUrl[] urls = new SVNUrl[len];
        final IResource[] projects = new IResource[len];

        int i = 0;
        for (final RemoteProject remoteProject : toSwitch) {
            urls[i] = remoteProject.getUrl();
            projects[i] = ProjectUtils.findWorkspaceProject(remoteProject.getName());
            assert projects[i] != null : remoteProject.getName();

            i += 1;
        }

        callSwitchOperation(urls, projects, SVNRevision.HEAD);
    }

    private static void callSwitchOperation(final SVNUrl[] svnUrls, final IResource[] projects,
            final SVNRevision svnRevision) {
        final SwitchOperation switchOperation = new SwitchOperation(null, projects, svnUrls,
                svnRevision);
        switchOperation.setDepth(Preferences.isUseWorkingCopy() ? ISVNCoreConstants.DEPTH_UNKNOWN
                : ISVNCoreConstants.DEPTH_INFINITY);

        switchOperation.setSetDepth(Preferences.isSetDepth());
        switchOperation.setForce(Preferences.isForce());
        switchOperation.setIgnoreAncestry(Preferences.isIgnoreAncestry());
        switchOperation.setIgnoreExternals(Preferences.isIgnoreExtenals());

        final Job job = new Job("SwitchOperation job") {
            @Override
            public IStatus run(final IProgressMonitor monitor) {
                monitor.beginTask("Some nice progress message here ...", 100);

                try {
                    switchOperation.run(monitor);
                } catch (final InvocationTargetException e) {
                    return new Status(Status.ERROR, SvnHelperJava.PLUGIN_ID, e.getMessage(), e);

                } catch (final InterruptedException e) {
                    return new Status(Status.ERROR, SvnHelperJava.PLUGIN_ID, e.getMessage(), e);
                }

                monitor.done();
                return Status.OK_STATUS;
            }
        };

        job.schedule();
    }
}
