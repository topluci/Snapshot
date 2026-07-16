package com.luci.snapshot.client.compat;

import com.luci.snapshot.SnapshotInit;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.io.InputStream;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public final class Image2MapBridge {
    private static final long TOKEN_LIFETIME_NANOS = TimeUnit.MINUTES.toNanos(5);
    private static final Map<String, ServedPhoto> PHOTOS = new ConcurrentHashMap<>();
    private static HttpServer server;
    private static ExecutorService executor;

    private Image2MapBridge() {
    }

    public static synchronized String expose(Path image) throws IOException {
        Path normalized = image.toAbsolutePath().normalize();
        if (!Files.isRegularFile(normalized)) {
            throw new IOException("Captured PNG does not exist");
        }
        ensureStarted();
        long now = System.nanoTime();
        PHOTOS.entrySet().removeIf(entry -> entry.getValue().expiresAtNanos() < now);
        String token = UUID.randomUUID().toString().replace("-", "");
        PHOTOS.put(token, new ServedPhoto(normalized, now + TOKEN_LIFETIME_NANOS));
        return "http://127.0.0.1:" + server.getAddress().getPort() + "/snapshot/" + token + ".png";
    }

    public static synchronized void stop() {
        PHOTOS.clear();
        if (server != null) {
            server.stop(0);
            server = null;
        }
        if (executor != null) {
            executor.shutdownNow();
            executor = null;
        }
    }

    private static void ensureStarted() throws IOException {
        if (server != null) {
            return;
        }
        server = HttpServer.create(new InetSocketAddress(InetAddress.getLoopbackAddress(), 0), 0);
        server.createContext("/snapshot/", Image2MapBridge::serve);
        executor = Executors.newSingleThreadExecutor(task -> {
            Thread thread = new Thread(task, "Snapshot Image2Map bridge");
            thread.setDaemon(true);
            return thread;
        });
        server.setExecutor(executor);
        server.start();
        SnapshotInit.LOGGER.info("[Snapshot] Image2Map loopback bridge listening on port {}.", server.getAddress().getPort());
    }

    private static void serve(HttpExchange exchange) throws IOException {
        if (exchange.getRemoteAddress() == null || exchange.getRemoteAddress().getAddress() == null
            || !exchange.getRemoteAddress().getAddress().isLoopbackAddress()) {
            respond(exchange, 403, 0L);
            return;
        }
        String method = exchange.getRequestMethod();
        if (!"GET".equals(method) && !"HEAD".equals(method)) {
            exchange.getResponseHeaders().set("Allow", "GET, HEAD");
            respond(exchange, 405, 0L);
            return;
        }
        String path = exchange.getRequestURI().getPath();
        String prefix = "/snapshot/";
        if (!path.startsWith(prefix) || !path.endsWith(".png")) {
            respond(exchange, 404, 0L);
            return;
        }
        String token = path.substring(prefix.length(), path.length() - 4);
        ServedPhoto photo = PHOTOS.get(token);
        if (photo == null || photo.expiresAtNanos() < System.nanoTime() || !Files.isRegularFile(photo.path())) {
            PHOTOS.remove(token);
            respond(exchange, 404, 0L);
            return;
        }

        long length = Files.size(photo.path());
        exchange.getResponseHeaders().set("Content-Type", "image/png");
        exchange.getResponseHeaders().set("Cache-Control", "no-store");
        if ("HEAD".equals(method)) {
            respond(exchange, 200, -1L);
            return;
        }
        exchange.sendResponseHeaders(200, length);
        try (InputStream input = Files.newInputStream(photo.path()); var output = exchange.getResponseBody()) {
            input.transferTo(output);
        } finally {
            exchange.close();
        }
    }

    private static void respond(HttpExchange exchange, int status, long length) throws IOException {
        exchange.sendResponseHeaders(status, length);
        exchange.close();
    }

    private record ServedPhoto(Path path, long expiresAtNanos) {
    }
}
