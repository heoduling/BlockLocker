package nl.rutgerkok.blocklocker.impl.updater;

import java.io.IOException;
import java.util.Optional;
import java.util.logging.Level;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.google.common.base.Preconditions;
import com.google.common.collect.ImmutableSet;
import com.google.gson.JsonParser;

import nl.rutgerkok.blocklocker.Translator;
import nl.rutgerkok.blocklocker.impl.BlockLockerPluginImpl;
import nl.rutgerkok.blocklocker.impl.updater.UpdateResult.Status;

/**
 * Handles the complete update procedure.
 *
 */
public final class Updater {

    /**
     * Every twelve hours.
     */
    private static final long CHECK_INTERVAL = 20 * 60 * 60 * 12;

    private final BlockLockerPluginImpl plugin;
    private volatile UpdatePreference preference;
    private final Translator translator;
    private UpdateChecker checker;
    private boolean stopped;

    public Updater(UpdatePreference preference, Translator translator, BlockLockerPluginImpl plugin) {
        this.preference = Preconditions.checkNotNull(preference);
        this.translator = Preconditions.checkNotNull(translator);
        this.plugin = Preconditions.checkNotNull(plugin);

    }

    private Optional<String> getMinecraftVersion() {
        String serverVersion = plugin.getServer().getVersion();
        String regex = "MC\\: *([A-Za-z0-9\\._\\-]+)";
        Matcher matcher = Pattern.compile(regex).matcher(serverVersion);
        if (matcher.find() && matcher.groupCount() == 1) {
            return Optional.of(matcher.group(1));
        } else {
            return Optional.empty();
        }
    }

    /**
     * Notifies admins of the server of an updated version of the plugin. Can be
     * called from any thread.
     *
     * @param result
     *            The update result.
     */
    private void notifyServer(final UpdateResult result) {
        if (!result.hasNotification()) {
            return;
        }

        // Disable further update checks
        preference = UpdatePreference.DISABLED;

        // Notify admins of existing result
        if (plugin.getServer().isPrimaryThread()) {
            notifyServerFromServerThread(result);
        } else {
            plugin.runLaterGlobally(new Runnable() {
                @Override
                public void run() {
                    notifyServerFromServerThread(result);
                }
            }, 1);
        }
    }

    private synchronized void notifyServerFromServerThread(UpdateResult result) {
        if (stopped) return;
        // Must be called from notifyServer
        // Result must have a notification

        UpdateNotifier notifier = new UpdateNotifier(translator, result);

        // Notify players
        plugin.getServer().getPluginManager().registerEvents(notifier, plugin);

        // Notify console
        notifier.sendNotification(plugin.getServer().getConsoleSender());
    }

    /**
     * Starts the update process. Does nothing if updates have been disabled.
     *
     * @throws IllegalStateException
     *             If this method was called earlier.
     */
    public synchronized void startUpdater() {
        if (!preference.checkForUpdates()) {
            return;
        }
        checker = new UpdateChecker(plugin);
        plugin.runTimerAsync(task -> {
            synchronized (Updater.this) {
                if (!stopped && preference.checkForUpdates()) {
                    checkForUpdates();
                } else {
                    task.cancel();
                }
            }
        }, CHECK_INTERVAL);
    }

    /** Runs before the core closes the plugin JAR. Never waits for network I/O. */
    public synchronized void stop() {
        stopped = true;
        if (checker != null) checker.close();
    }

    private void notifyUpdateAvailable(UpdateCheckResult result) throws IOException {
        Optional<String> minecraftVersion = getMinecraftVersion();

        if (minecraftVersion.isEmpty() || result.getMinecraftVersions().containsAll(ImmutableSet.of(minecraftVersion.get()))) {
            // Notify that an update is available
            notifyServer(new UpdateResult(Status.MANUAL_UPDATE, result));
        } else {
            // Server version no longer supported
            notifyServer(new UpdateResult(Status.UNSUPPORTED_SERVER, result));
        }
    }

    private void checkForUpdates() {
        checker.checkForUpdates().whenComplete((response, failure) -> {
            // Serialize completion with stop, including parsing and notification scheduling.
            synchronized (Updater.this) {
                if (stopped) return;
                try {
                    if (failure != null) throw new IOException("Update request failed", failure);
                    if (response.statusCode() != 200) throw new IOException("HTTP " + response.statusCode());
                    UpdateCheckResult result = new UpdateCheckResult(JsonParser.parseString(response.body()).getAsJsonObject());
                    if (result.needsUpdate()) notifyUpdateAvailable(result);
                } catch (Exception e) {
                    plugin.getLogger().log(Level.WARNING, "Error during update check"
                            + " (you can disable automatic updates in the config file)", e);
                }
            }
        });
    }

}
