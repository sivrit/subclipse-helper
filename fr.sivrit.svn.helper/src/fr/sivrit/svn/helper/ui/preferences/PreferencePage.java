package fr.sivrit.svn.helper.ui.preferences;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.ComboFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;

import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.ui.Activator;
import fr.sivrit.svn.helper.ui.SvnHelperProxy;

/**
 * This class represents a preference page that is contributed to the
 * Preferences dialog. By subclassing <samp>FieldEditorPreferencePage</samp>, we
 * can use the field support built into JFace that allows us to create a page
 * that is small and knows how to save, restore and apply itself.
 * <p>
 * This page is used to modify preferences only. They are stored in the
 * preference store that belongs to the main plug-in class. That way,
 * preferences can be accessed directly via the preference store.
 */

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {

    public PreferencePage() {
        super(GRID);
        setPreferenceStore(Activator.getPlugin().getPreferenceStore());
        setDescription("Configuration for customs svn operations");
    }

    /**
     * Creates the field editors. Field editors are abstractions of the common
     * GUI blocks needed to manipulate various types of preferences. Each field
     * editor knows how to save and restore itself.
     */
    @Override
    public void createFieldEditors() {
        addField(new ComboFieldEditor(Preferences.P_PREFERRED_IMPLEMENTATION,
                "Preferred implementation name", SvnHelperProxy.listAvailableImplementations(),
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(Preferences.P_ENABLE_DEBUG, "Enable debug output",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(Preferences.P_IGNORE_NATURELESS_PROJECTS,
                "Ignore projects without a nature", getFieldEditorParent()));

        addField(new IntegerFieldEditor(Preferences.P_CRAWLER_PARALLEL_REQUEST_LIMIT,
                "Maximum number of simultaneous SVN requests", getFieldEditorParent()));

        addField(new ExclusionListEditor(Preferences.P_EXCLUSIONS,
                "Exclude URLs matching the following (java) regexps:", getFieldEditorParent()));

        addField(new BooleanFieldEditor(Preferences.P_EXCLUDE_DEP,
                "Apply exclusions to Dependency Lookup", getFieldEditorParent()));

        addField(new BooleanFieldEditor(Preferences.P_EXCLUDE_WS,
                "Apply exclusions to Working Sets", getFieldEditorParent()));
        addField(new BooleanFieldEditor(Preferences.P_SWITCH_PERSPECTIVE,
                "Switch to the Java perspective when defining Working Sets", getFieldEditorParent()));
        addField(new BooleanFieldEditor(Preferences.P_TRANSFER_FROM_WS,
                "Remove projects from existing Working Sets before adding them to new ones",
                getFieldEditorParent()));

        addField(new BooleanFieldEditor(Preferences.P_BYPASS_WS_MAPPING,
                "Do not ask for the name of Working Sets, deduce them from their URL",
                getFieldEditorParent()));
        addField(new BooleanFieldEditor(Preferences.P_CONFIRM_WS_CREATION,
                "Confirm Working Sets creation after project discovery", getFieldEditorParent()));

        addField(new StringFieldEditor(Preferences.P_CACHE_FOLDER,
                "Folder where the cache will be stored", getFieldEditorParent()));

    }

    /*
     * (non-Javadoc)
     *
     * @see
     * org.eclipse.ui.IWorkbenchPreferencePage#init(org.eclipse.ui.IWorkbench)
     */
    @Override
    public void init(final IWorkbench workbench) {
        SvnHelperProxy.resetImplementation();
    }
}