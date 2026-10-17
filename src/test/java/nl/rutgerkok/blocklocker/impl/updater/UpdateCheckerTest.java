package nl.rutgerkok.blocklocker.impl.updater;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.net.URI;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class UpdateCheckerTest {
    @Test
    void stoppedCheckerCancelsARequestWhoseServerHasNotResponded() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        CountDownLatch arrived = new CountDownLatch(1);
        CountDownLatch release = new CountDownLatch(1);
        server.createContext("/", exchange -> {
            arrived.countDown();
            try { release.await(5, TimeUnit.SECONDS); }
            catch (InterruptedException e) { Thread.currentThread().interrupt(); }
            finally { exchange.close(); }
        });
        server.start();
        UpdateChecker checker = new UpdateChecker(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "test");
        try {
            var request = checker.checkForUpdates();
            assertTrue(arrived.await(3, TimeUnit.SECONDS));
            checker.close();
            assertTrue(request.isDone(), "Stopping must finish/cancel the outstanding request");
            assertTrue(request.isCompletedExceptionally());
            assertThrows(IllegalStateException.class, checker::checkForUpdates);
        } finally {
            checker.close(); release.countDown(); server.stop(0);
        }
    }

    @Test
    void successfulRequestPreservesResponseAndUserAgent() throws Exception {
        HttpServer server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        String[] agent = new String[1];
        server.createContext("/", exchange -> {
            agent[0] = exchange.getRequestHeaders().getFirst("User-Agent");
            byte[] body = "{\"needsUpdate\":false}".getBytes(java.nio.charset.StandardCharsets.UTF_8);
            exchange.sendResponseHeaders(200, body.length);
            try (var out = exchange.getResponseBody()) { out.write(body); }
        });
        server.start();
        try (UpdateChecker checker = new UpdateChecker(URI.create("http://127.0.0.1:" + server.getAddress().getPort()), "BlockLocker-test")) {
            var response = checker.checkForUpdates().get(3, TimeUnit.SECONDS);
            assertEquals(200, response.statusCode());
            assertEquals("{\"needsUpdate\":false}", response.body());
            assertEquals("BlockLocker-test", agent[0]);
        } finally { server.stop(0); }
    }
}
