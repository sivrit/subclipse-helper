package fr.sivrit.svn.helper.ui.preferences;

import java.util.regex.Pattern;
import java.util.regex.PatternSyntaxException;

import org.eclipse.jface.dialogs.IInputValidator;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.preference.ListEditor;
import org.eclipse.jface.window.Window;
import org.eclipse.swt.events.MouseEvent;
import org.eclipse.swt.events.MouseListener;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.List;

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
    }
}
