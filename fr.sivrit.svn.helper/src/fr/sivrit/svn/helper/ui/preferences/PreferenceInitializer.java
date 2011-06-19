package fr.sivrit.svn.helper.ui.preferences;

import org.eclipse.core.runtime.preferences.AbstractPreferenceInitializer;
import org.eclipse.jface.preference.IPreferenceStore;

import fr.sivrit.svn.helper.Preferences;
import fr.sivrit.svn.helper.ui.Activator;
import fr.sivrit.svn.helper.ui.SvnHelperProxy;

/**
 * Class used to initialize default preference values.
 */
public class PreferenceInitializer extends AbstractPreferenceInitializer {

    /*
     * (non-Javadoc)
     * 
     * @seeorg.eclipse.core.runtime.preferences.AbstractPreferenceInitializer#
     * initializeDefaultPreferences()
     */
    @Override
    public void initializeDefaultPreferences() {
        final IPreferenceStore store = Activator.getPlugin().getPreferenceStore();
        store.setDefault(Preferences.P_PREFERRED_IMPLEMENTATION,
                SvnHelperProxy.DEFAULT_IMPLEMENTATION);
        store.setDefault(Preferences.P_CRAWLER_PARALLEL_REQUEST_LIMIT, 8);
        store.setDefault(Preferences.P_EXCLUSIONS, "");
        store.setDefault(Preferences.P_EXCLUDE_WS, true);
        store.setDefault(Preferences.P_EXCLUDE_DEP, true);
        store.setDefault(Preferences.P_ENABLE_DEBUG, false);
    }
}
