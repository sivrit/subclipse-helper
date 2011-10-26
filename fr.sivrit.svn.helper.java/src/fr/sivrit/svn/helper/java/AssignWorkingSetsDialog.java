package fr.sivrit.svn.helper.java;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

import org.apache.commons.lang.StringUtils;
import org.eclipse.jface.dialogs.IDialogConstants;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Combo;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.IWorkingSet;
import org.eclipse.ui.IWorkingSetManager;
import org.eclipse.ui.internal.WorkbenchPlugin;
import org.tigris.subversion.svnclientadapter.SVNUrl;

public class AssignWorkingSetsDialog extends MessageDialog {
    private final SVNUrl[] urls;
    private String[] ws;

    public AssignWorkingSetsDialog(final Shell parentShell, final String dialogTitle,
            final String dialogMessage, final SVNUrl[] urls) {
        super(parentShell, dialogTitle, null, dialogMessage, QUESTION, new String[] {
                IDialogConstants.YES_LABEL, IDialogConstants.NO_LABEL }, 0);

        this.urls = urls;

        setShellStyle(getShellStyle() | SWT.SHEET);
    }

    @Override
    protected Control createCustomArea(Composite parent) {
        final Composite composite = new Composite(parent, SWT.NONE);

        @SuppressWarnings("restriction")
        final IWorkingSetManager wsManager = WorkbenchPlugin.getDefault().getWorkingSetManager();
        final Set<String> workingSets = new HashSet<String>();
        for (final IWorkingSet ws : wsManager.getWorkingSets()) {
            workingSets.add(ws.getName());
        }
        for (final SVNUrl url : urls) {
            workingSets.add(url.getLastPathSegment());
        }

        final String[] comboContent = workingSets.toArray(new String[workingSets.size()]);
        Arrays.sort(comboContent);

        ws = new String[urls.length];

        composite.setLayout(new GridLayout(2, false));
        for (int i = 0; i < urls.length; i++) {
            final SVNUrl url = urls[i];

            final Label label = new Label(composite, SWT.LEFT);
            label.setText(url.toString());
            // detailsLabel.setFont(parent.getFont());
            // final GridData data = new GridData();
            // label.setLayoutData(data);

            final Combo combo = new Combo(composite, SWT.DROP_DOWN);
            combo.setItems(comboContent);
            combo.setText(url.getLastPathSegment());
            ws[i] = url.getLastPathSegment();

            final int comboIndex = i;
            combo.addModifyListener(new ModifyListener() {
                @Override
                public void modifyText(ModifyEvent e) {
                    final String newText = combo.getText();
                    if (StringUtils.isNotBlank(newText)) {
                        ws[comboIndex] = newText;
                    }
                }
            });
        }

        return composite;
    }

    public String[] getWs() {
        return ws;
    }
}
