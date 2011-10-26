package fr.sivrit.svn.helper.ui;

import java.util.ArrayList;
import java.util.Collection;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import fr.sivrit.svn.helper.ISvnHelper;
import fr.sivrit.svn.helper.Preferences;

public final class SvnHelperProxy {
    public final static String DEFAULT_IMPLEMENTATION = "java";
    public final static String EXTENSION_POINT = "fr.sivrit.svn.helper.impl";
    public final static String EXTENSION_CLASS = "class";
    public final static String EXTENSION_NAME = "name";
    public final static String EXTENSION_DESCRIPTION = "description";

    private static ISvnHelper implementation;

    private SvnHelperProxy() {
        super();
    }

    public synchronized static void resetImplementation() {
        implementation = null;
    }

    public synchronized static ISvnHelper getImplementation() {
        if (implementation == null) {
            implementation = findImplementation(Preferences.getPreferredImplementation());
            if (implementation == null) {
                implementation = findImplementation(DEFAULT_IMPLEMENTATION);
                if (implementation == null) {
                    implementation = findImplementation(null);
                }
            }
        }

        if (implementation == null) {
            throw new UnsupportedOperationException("No SvnHelper plugin could be instantiated");
        }
        return implementation;
    }

    private static ISvnHelper findImplementation(final String targetName) {
        final IExtensionRegistry pluginRegistry = Platform.getExtensionRegistry();
        final IConfigurationElement[] configurationElements = pluginRegistry
                .getConfigurationElementsFor(EXTENSION_POINT);
        for (final IConfigurationElement configurationElement : configurationElements) {
            final String implementationName = configurationElement.getAttribute(EXTENSION_NAME);
            if (targetName == null || targetName.equalsIgnoreCase(implementationName)) {
                try {
                    final ISvnHelper client = (ISvnHelper) configurationElement
                            .createExecutableExtension(EXTENSION_CLASS);

                    if (Preferences.enableDebugInformation()) {
                        System.out.println("Using " + client.getClass());
                    }

                    return client;
                } catch (final CoreException e) {
                    e.printStackTrace();
                }
            }
        }

        return null;
    }

    /**
     * @return an array of pairs {"implementation name",
     *         "implementation description or name"}
     */
    public static String[][] listAvailableImplementations() {
        final Collection<String[]> result = new ArrayList<String[]>();

        final IExtensionRegistry pluginRegistry = Platform.getExtensionRegistry();
        final IConfigurationElement[] configurationElements = pluginRegistry
                .getConfigurationElementsFor(EXTENSION_POINT);
        for (final IConfigurationElement configurationElement : configurationElements) {
            final String name = configurationElement.getAttribute(EXTENSION_NAME);
            final String description = configurationElement.getAttribute(EXTENSION_DESCRIPTION);
            result.add(new String[] { name, description == null ? name : description });
        }

        return result.toArray(new String[result.size()][]);
    }
}
