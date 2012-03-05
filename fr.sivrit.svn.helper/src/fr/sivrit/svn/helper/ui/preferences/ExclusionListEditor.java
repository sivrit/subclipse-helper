package fr.sivrit.svn.helper.ui.preferences;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Properties;
import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.core.runtime.IStatus;
import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.ListEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.List;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.eclipse.swt.widgets.Shell;

import fr.sivrit.svn.helper.ISvnHelper;
import fr.sivrit.svn.helper.Logger;
import fr.sivrit.svn.helper.Preferences;

public class ExclusionListEditor extends ListEditor {

    public ExclusionListEditor(final String name, final String labelText, final Composite parent) {
        super(name, labelText, parent);
    }

    @Override
    protected String createList(final String[] items) {
        return Preferences.concatenateExclusions(items);
    }

    @Override
    protected String getNewInputObject() {
        final String val = editRegExpDialog("");
        return val == null ? "default" : val;
    }

    /**
     * Open a dialog to input/edit a java regexp (its syntax will be validated)
     * 
     * @param defaultValue
     *            value initially displayed
     * @return the value defined by the user, or null if canceled
     */
    private final String editRegExpDialog(final String defaultValue) {
        final InputDialog dlg = new InputDialog(getShell(), "Edit filter", "Enter a java regexp",
                defaultValue, new IInputValidator() {
                    @Override
                    public String isValid(final String newText) {
                        try {
                            Pattern.compile(newText);
                        } catch (final PatternSyntaxException pse) {
                            return pse.getMessage();
                        }
                        return null;
                    }
                });

        if (dlg.open() == Window.OK) {
            // User clicked OK; update the label with the input
            return dlg.getValue();
        } else {
            return null;
        }
    }

    @Override
    protected String[] parseString(final String stringList) {
        return Preferences.parseExclusions(stringList);
    }

    @Override
    protected void doFillIntoGrid(final Composite parent, final int numColumns) {
        super.doFillIntoGrid(parent, numColumns);

        final List list = getListControl(parent);

        list.addMouseListener(new MouseListener() {
            @Override
            public void mouseUp(final MouseEvent e) {
            }

            @Override
            public void mouseDown(final MouseEvent e) {
            }

            @Override
            public void mouseDoubleClick(final MouseEvent e) {
                final int selection = list.getSelectionIndex();
                if (selection >= 0) {
                    final String val = list.getItem(selection);
                    final String newVal = editRegExpDialog(val);
                    if (newVal != null) {
                        list.setItem(selection, newVal);
                    }
                }
            }
        });

        // TJ fev 2012 : save and load exclusions to/from properties file
        Menu menu = parent.getMenu();
        if (menu == null) {
            menu = new Menu(parent.getShell(), SWT.POP_UP);
        }

        final MenuItem saveItem = new MenuItem(menu, SWT.PUSH);
        saveItem.setText("Save Exclusion List...");
        saveItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                final String filename = openFileDialog(parent.getShell(), true);

                final Properties properties = new Properties();
                int i = 0;
                for (String regex : list.getItems()) {
                    if (!regex.isEmpty()) {
                        properties.setProperty("rule" + i++, regex);
                    }
                }

                try {
                    final OutputStream out = new FileOutputStream(filename);
                    try {
                        properties.store(out, "SVN Helper Exclusions");
                        out.flush();
                    } finally {
                        out.close();
                    }
                } catch (FileNotFoundException e) {
                    Logger.log(IStatus.ERROR, ISvnHelper.PLUGIN_ID, e);
                } catch (IOException e) {
                    Logger.log(IStatus.ERROR, ISvnHelper.PLUGIN_ID, e);
                }
            }
        });

        final MenuItem loadItem = new MenuItem(menu, SWT.PUSH);
        loadItem.setText("Load Exclusion List...");
        loadItem.addSelectionListener(new SelectionAdapter() {
            @Override
            public void widgetSelected(final SelectionEvent event) {
                final String filename = openFileDialog(parent.getShell(), false);

                final Properties properties = new Properties();

                try {
                    final InputStream in = new FileInputStream(filename);
                    try {
                        properties.load(in);
                    } finally {
                        in.close();
                    }

                } catch (FileNotFoundException e) {
                    Logger.log(IStatus.ERROR, ISvnHelper.PLUGIN_ID, e);
                } catch (IOException e) {
                    Logger.log(IStatus.ERROR, ISvnHelper.PLUGIN_ID, e);
                }

                list.removeAll();
                for (Object value : properties.values()) {
                    list.add((String) value);
                }
            }
        });

        list.setMenu(menu);
    }

    private static String openFileDialog(final Shell shell, final boolean saveDialog) {
        final FileDialog dialog = new FileDialog(shell, saveDialog ? SWT.SAVE : SWT.OPEN);

        final String[] filterNames;
        final String[] filterExtensions;
        final String platform = SWT.getPlatform();
        if (platform.equals("win32") || platform.equals("wpf")) {
            filterNames = new String[] { "Exclusion Files", "All Files (*.*)" };
            filterExtensions = new String[] { "*.exclusions", "*.*" };
        } else {
            filterNames = new String[] { "Exclusion Files", "All Files (*)" };
            filterExtensions = new String[] { "*.exclusions", "*" };
        }

        dialog.setFilterNames(filterNames);
        dialog.setFilterExtensions(filterExtensions);
        dialog.setFileName("svn_helper.exclusions");

        return dialog.open();
    }
}
