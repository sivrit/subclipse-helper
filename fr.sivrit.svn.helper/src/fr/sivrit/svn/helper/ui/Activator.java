package fr.sivrit.svn.helper.ui;

import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.osgi.framework.BundleContext;

public class Activator extends AbstractUIPlugin {
    // The shared instance
    private static Activator plugin;

    public static Activator getPlugin() {
        synchronized (Activator.class) {
            return plugin;
        }
    }

    public Activator() {
        super();
    }

    @Override
    public void start(final BundleContext context) throws Exception {
        super.start(context);
        synchronized (Activator.class) {
            plugin = this;
        }
    }

    @Override
    public void stop(final BundleContext context) throws Exception {
        synchronized (Activator.class) {
            plugin = null;
        }
        super.stop(context);
    }
}
