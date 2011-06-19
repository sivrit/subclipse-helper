package fr.sivrit.svn.helper.ui.actions;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Map.Entry;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.tigris.subversion.subclipse.core.ISVNRemoteFolder;
import org.tigris.subversion.subclipse.core.ISVNRepositoryLocation;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.ui.SvnHelperProxy;

public class CheckoutAction implements IObjectActionDelegate {
    private final Collection<ISVNRemoteFolder> selectedItems = new ArrayList<ISVNRemoteFolder>();

    @Override
    public void setActivePart(final IAction action, final IWorkbenchPart targetPart) {
    }

    @Override
    public void run(final IAction action) {
        assert !selectedItems.isEmpty();

        final Map<ISVNRepositoryLocation, Collection<SVNUrl>> urlsPerLoc = new HashMap<ISVNRepositoryLocation, Collection<SVNUrl>>();
        for (final ISVNRemoteFolder folder : selectedItems) {
            final ISVNRepositoryLocation repoLoc = folder.getRepository();
            final SVNUrl url = folder.getUrl();

            Collection<SVNUrl> urls = urlsPerLoc.get(repoLoc);
            if (urls == null) {
                urls = new ArrayList<SVNUrl>();
                urlsPerLoc.put(repoLoc, urls);
            }
            urls.add(url);
        }

        for (final Entry<ISVNRepositoryLocation, Collection<SVNUrl>> entry : urlsPerLoc.entrySet()) {
            final Collection<SVNUrl> urls = entry.getValue();
            final SVNUrl[] urlArray = urls.toArray(new SVNUrl[urls.size()]);
            SvnHelperProxy.getImplementation().checkoutBranch(entry.getKey(), urlArray);
        }
    }

    @Override
    public void selectionChanged(final IAction action, final ISelection selection) {
        selectedItems.clear();
        if (selection instanceof IStructuredSelection) {
            final IStructuredSelection structSelection = (IStructuredSelection) selection;
            for (final Object folder : structSelection.toArray()) {
                selectedItems.add((ISVNRemoteFolder) folder);
            }
        }
        action.setEnabled(!selectedItems.isEmpty());
    }
}
