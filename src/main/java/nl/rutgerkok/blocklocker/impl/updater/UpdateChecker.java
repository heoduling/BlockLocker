package nl.rutgerkok.blocklocker.impl.updater;

import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.CompletableFuture;
import org.bukkit.plugin.Plugin;

/** Owns the cancellable request; only JDK code reads the network response body. */
final class UpdateChecker implements AutoCloseable {
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(5)).followRedirects(HttpClient.Redirect.NORMAL).build();
    private final HttpRequest request;
    private CompletableFuture<HttpResponse<String>> pending;
    private boolean closed;

    UpdateChecker(Plugin plugin) {
        this(URI.create("https://rutgerkok.nl/tools/updater/blocklocker.php?version="
                + URLEncoder.encode(plugin.getDescription().getVersion(), StandardCharsets.UTF_8)),
                UserAgent.forPlugin(plugin));
    }

    UpdateChecker(URI uri, String userAgent) {
        request = HttpRequest.newBuilder(uri).timeout(Duration.ofSeconds(10))
                .header("Content-Type", "application/json").header("User-Agent", userAgent).GET().build();
    }

    synchronized CompletableFuture<HttpResponse<String>> checkForUpdates() {
        if (closed) throw new IllegalStateException("Update checker has stopped");
        if (pending == null || pending.isDone()) {
            pending = client.sendAsync(request, HttpResponse.BodyHandlers.ofString(StandardCharsets.UTF_8));
        }
        return pending;
    }

    @Override
    public synchronized void close() {
        closed = true;
        if (pending != null) pending.cancel(true);
        client.shutdownNow();
    }
}
