package fr.sivrit.svn.helper.ui.actions;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.jface.action.IAction;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.ui.IObjectActionDelegate;
import org.eclipse.ui.IWorkbenchPart;
import org.tigris.subversion.subclipse.core.ISVNRemoteFolder;
import org.tigris.subversion.svnclientadapter.SVNUrl;

import fr.sivrit.svn.helper.ui.SvnHelperProxy;

public class CreateWorkingSetsAction implements IObjectActionDelegate {
    private final Collection<ISVNRemoteFolder> selectedItems = new ArrayList<ISVNRemoteFolder>();

    @Override
    public void setActivePart(final IAction action, final IWorkbenchPart targetPart) {
    }

    @Override
    public void run(final IAction action) {
        assert !selectedItems.isEmpty();

        final SVNUrl[] urls = new SVNUrl[selectedItems.size()];
        int i = 0;
        for (final ISVNRemoteFolder folder : selectedItems) {
            urls[i++] = folder.getUrl();
        }
        SvnHelperProxy.getImplementation().createWorkingSets(urls);
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
