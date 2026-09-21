package mc.gupe.revive;

import com.sun.net.httpserver.HttpServer;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;

/**
 * Mini-servidor web interno que sirve el resource pack (.zip) a los jugadores, sin necesidad de
 * hostear el pack en ningun lado (estilo BackpackPlus).
 */
public final class PackServer {

    private HttpServer server;

    /** Arranca el servidor en el puerto dado y sirve 'data' en la ruta 'path'. */
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
        server.setExecutor(null);  // un hilo por defecto, suficiente para un .zip pequeno
        server.start();
    }

    public void stop() {
        if (server != null) {
            server.stop(0);
            server = null;
        }
    }
}
