package nl.rutgerkok.blocklocker.impl.updater;

import org.bukkit.plugin.Plugin;

/**
 * Sets the correct user agent.
 *
 */
final class UserAgent {

    /**
     * Sets the correct user agent as used by this plugin on the request.
     *
     * @param plugin
     *            The plugin.
     */
    static String forPlugin(Plugin plugin) {
        return "Mozilla/5.0 (" + plugin.getName() + "/" + plugin.getDescription().getVersion() + ")";
    }

    private UserAgent() {

    }
}
