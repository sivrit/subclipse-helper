package fr.sivrit.svn.helper;

import org.eclipse.core.runtime.ILog;
import org.eclipse.core.runtime.Status;

import fr.sivrit.svn.helper.ui.Activator;

public abstract class Logger {
    private Logger() {
    }

    public static void log(final int severity, final String plugin, final String message) {
        final ILog log = Activator.getPlugin().getLog();
        log.log(new Status(severity, plugin, message));
    }

    public static void log(final int severity, final String plugin, final Throwable ex) {
        log(severity, plugin, ex.getMessage(), ex);
    }

    public static void log(final int severity, final String plugin, final String message,
            final Throwable ex) {
        final ILog log = Activator.getPlugin().getLog();
        log.log(new Status(severity, plugin, message, ex));
    }
}
