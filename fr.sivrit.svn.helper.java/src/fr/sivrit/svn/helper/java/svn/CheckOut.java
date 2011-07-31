package fr.sivrit.svn.helper.java.svn;

import java.lang.reflect.InvocationTargetException;
import java.util.Collection;

import org.tigris.subversion.subclipse.core.ISVNRemoteFolder;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.subclipse.core.resources.RemoteFolder;
import org.tigris.subversion.subclipse.ui.actions.CheckoutAsProjectAction;
import org.tigris.subversion.svnclientadapter.SVNRevision;

public class CheckOut {
    public static void run(final ISVNRepositoryLocation repo, final Collection<RemoteProject> todo) {

        final ISVNRemoteFolder[] foldersArray = new ISVNRemoteFolder[todo.size()];
        int i = 0;
        for (final RemoteProject project : todo) {
            foldersArray[i++] = new RemoteFolder(repo, project.getUrl(), SVNRevision.HEAD);
        }

        final CheckoutAsProjectAction checkoutAction = new CheckoutAsProjectAction(foldersArray,
                null, null);
        try {
            checkoutAction.execute(null);
        } catch (final InvocationTargetException e) {
            e.printStackTrace();
        } catch (final InterruptedException e) {
            e.printStackTrace();
        }
    }
}
