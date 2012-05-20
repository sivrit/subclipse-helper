package fr.sivrit.svn.helper;

import org.apache.commons.lang.StringUtils;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.tigris.subversion.svnclientadapter.SVNUrl;

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
     * If <code>true</code>, {@link ISvnHelper#createWorkingSets(SVNUrl[])} will
     * switch to the Java perspective and show the package explorer.
     */
    public static final String P_SWITCH_PERSPECTIVE = "switchPerspective";

    /**
     * If <code>true</code>, {@link ISvnHelper#createWorkingSets(SVNUrl[])} will
     * remove the projects it does dispatch from all existing Working Sets,
     * effectively moving them from the old Working Sets to the new one.
     * Otherwise, the projects will appear in multiple Working Sets.
     */
    public static final String P_TRANSFER_FROM_WS = "transferFromWorkingSets";

    /**
     * If <code>true</code>, when defining working sets, a confirmation will be
     * asked after projects discovery.
     */
    public static final String P_CONFIRM_WS_CREATION = "confirmWorkingSets";

    /**
     * If <code>true</code>, working sets names will be set automatically from
     * their URL in the repository.
     */
    public static final String P_BYPASS_WS_MAPPING = "bypassWorkingSetsMapping";

    /**
     * Whether {@link #P_EXCLUSIONS} is used when looking for dependencies
     */
    public static final String P_EXCLUDE_DEP = "applyExclusionsOnDep";

    /**
     * Used to enable debug output
     */
    public static final String P_ENABLE_DEBUG = "enableDebugInformation";

    /**
     * Where to store the cache. NULL => no cache
     */
    public static final String P_CACHE_FOLDER = "cacheFolder";

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

        return Math.max(1, value);
    }

    public static boolean enableDebugInformation() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_ENABLE_DEBUG);
    }

    public static boolean getSwitchPerspective() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_SWITCH_PERSPECTIVE);
    }

    public static boolean getTransferFromWorkingSets() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_TRANSFER_FROM_WS);
    }

    public static boolean getByPassWorkingSetsMapping() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_BYPASS_WS_MAPPING);
    }

    public static boolean getConfirmWorkingSetsCreation() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_CONFIRM_WS_CREATION);
    }

    public static String getCacheFolder() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        final String result = plugin.getPreferenceStore().getString(P_CACHE_FOLDER);
        return StringUtils.isBlank(result) ? null : StringUtils.trim(result);
    }

    // Experimental configuration for switching
    public static final String P_SETDEPTH = "setDepth";
    public static final String P_DEPTH_IS_WORKINGCOPY = "workingCopy";
    public static final String P_FORCE = "force";
    public static final String P_IGNORE_ANCESTRY = "ignoreAncestry";
    public static final String P_IGNORE_EXTERNALS = "ignoreExternal";

    public static boolean isSetDepth() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_SETDEPTH);
    }

    public static boolean isForce() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_FORCE);
    }

    public static boolean isUseWorkingCopy() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_DEPTH_IS_WORKINGCOPY);
    }

    public static boolean isIgnoreAncestry() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_IGNORE_ANCESTRY);
    }

    public static boolean isIgnoreExtenals() {
        final AbstractUIPlugin plugin = Activator.getPlugin();
        return plugin.getPreferenceStore().getBoolean(P_IGNORE_EXTERNALS);
    }
}
