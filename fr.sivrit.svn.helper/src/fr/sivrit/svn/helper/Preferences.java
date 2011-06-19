package fr.sivrit.svn.helper;

import org.eclipse.ui.plugin.AbstractUIPlugin;

import fr.sivrit.svn.helper.ui.Activator;

/**
 * Constant definitions for plug-in preferences
 */
public final class Preferences {
    // I dare anyone to put 5 consecutive '\' in a regexp matching an URL
    private final static String separator = "\\\\\\\\\\";

    /** '\'s must be escaped in regexps, so we need twice as many */
    private final static String regexp = separator + separator;

    private Preferences() {
        throw new UnsupportedOperationException();
    }

    /**
     * Used to decide which implementation contributed to
     * fr.sivrit.svn.helper.impl should have priority
     */
    public static final String P_PREFERRED_IMPLEMENTATION = "preferredImplementation";

    /**
     * When we browse the svn repository, how many parallel request we allow
     */
    public static final String P_CRAWLER_PARALLEL_REQUEST_LIMIT = "crawlerMaxRequests";

    /**
     * Collections of java regular expression. Any svn URL matching them will be
     * ignored when browsing the svn repository
     */
    public static final String P_EXCLUSIONS = "pathExclusions";

    /**
     * Whether {@link #P_EXCLUSIONS} is used when creating working sets (we may
     * not want to checkout them automatically, but this is no reason not to
     * sort them when they're already there)
     */
    public static final String P_EXCLUDE_WS = "applyExclusionsOnWS";

    /**
     * Whether {@link #P_EXCLUSIONS} is used when looking for dependencies
     */
    public static final String P_EXCLUDE_DEP = "applyExclusionsOnDep";

    /**
     * Used to enable debug output
     */
    public static final String P_ENABLE_DEBUG = "enableDebugInformation";

    public static String getPreferredImplementation() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getString(P_PREFERRED_IMPLEMENTATION);
    }

    public static String concatenateExclusions(final String[] items) {
        if (items == null) {
            return null;
        }

        final StringBuilder buffer = new StringBuilder();
        for (final String item : items) {
            if (buffer.length() != 0) {
                buffer.append(separator);
            }
            buffer.append(item);
        }
        return buffer.toString();
    }

    public static String[] parseExclusions(final String stringList) {
        return stringList == null ? new String[0] : stringList.split(regexp);
    }

    public static String[] getExclusions() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return parseExclusions(plugin.getPreferenceStore().getString(P_EXCLUSIONS));
    }

    public static boolean getApplyOnWokingSet() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_EXCLUDE_WS);
    }

    public static boolean getApplyOnDependencies() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_EXCLUDE_DEP);
    }

    public static int getMaxCrawlerRequests() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        final int value = plugin.getPreferenceStore().getInt(P_CRAWLER_PARALLEL_REQUEST_LIMIT);

        return Math.min(1, value);
    }
    
    public static boolean enableDebugInformation() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_ENABLE_DEBUG);
    }
}
