package fr.sivrit.svn.helper.ui;

import org.eclipse.core.runtime.CoreException;
import org.eclipse.core.runtime.IConfigurationElement;
import org.eclipse.core.runtime.IExtensionRegistry;
import org.eclipse.core.runtime.Platform;

import fr.sivrit.svn.helper.ISvnHelper;
import fr.sivrit.svn.helper.Preferences;

public final class SvnHelperProxy {
    public final static String DEFAULT_IMPLEMENTATION = "scala";

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
                .getConfigurationElementsFor("fr.sivrit.svn.helper.impl");
        for (final IConfigurationElement configurationElement : configurationElements) {
            final String implementationName = configurationElement.getAttribute("name");
            if (targetName == null || targetName.equalsIgnoreCase(implementationName)) {
                try {
                    final ISvnHelper client = (ISvnHelper) configurationElement
                            .createExecutableExtension("class");

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
}
