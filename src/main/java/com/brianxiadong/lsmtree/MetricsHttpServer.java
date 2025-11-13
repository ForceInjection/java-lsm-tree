package com.brianxiadong.lsmtree;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpHandler;
import com.sun.net.httpserver.HttpServer;
import io.micrometer.prometheus.PrometheusMeterRegistry;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

public class MetricsHttpServer {
    private static volatile HttpServer server;

    public static void startIfEnabled() {
        String enabled = System.getProperty("lsm.metrics.http.enabled", "false");
        if (!"true".equalsIgnoreCase(enabled))
            return;
        if (server != null)
            return;
        try {
            int port = Integer.parseInt(System.getProperty("lsm.metrics.http.port", "9091"));
            server = HttpServer.create(new InetSocketAddress(port), 0);
            server.createContext("/metrics", new MetricsHandler());
            server.setExecutor(null);
            server.start();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    public static void stopIfRunning() {
        HttpServer s = server;
        if (s != null) {
            try {
                s.stop(0);
            } finally {
                server = null;
            }
        }
    }

    static class MetricsHandler implements HttpHandler {
        @Override
        public void handle(HttpExchange exchange) throws IOException {
            PrometheusMeterRegistry reg = (PrometheusMeterRegistry) MetricsRegistry.get();
            byte[] data = reg.scrape().getBytes("UTF-8");
            exchange.getResponseHeaders().set("Content-Type", "text/plain; charset=UTF-8");
            exchange.sendResponseHeaders(200, data.length);
            try (OutputStream os = exchange.getResponseBody()) {
                os.write(data);
            }
        }
    }
}
