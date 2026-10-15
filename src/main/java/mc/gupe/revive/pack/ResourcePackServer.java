package mc.gupe.revive.pack;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/** Tiny built-in web server that serves the resource pack .zip, no external hosting needed. */
public final class ResourcePackServer {

    private HttpServer server;

    /** Starts the server on the given port and serves {@code data} at {@code path}. */
    public void start(int port, String path, byte[] data) throws IOException {
        server = HttpServer.create(new InetSocketAddress(port), 0);
        server.createContext(path, exchange -> {
            try {
                exchange.getResponseHeaders().set("Content-Type", "application/zip");
                exchange.sendResponseHeaders(200, data.length);
                try (OutputStream os = exchange.getResponseBody()) {
                    os.write(data);
                }
            } catch (Throwable t) {
                exchange.close();
            }
        });
        server.setExecutor(null); // one default thread is enough for a small zip
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
