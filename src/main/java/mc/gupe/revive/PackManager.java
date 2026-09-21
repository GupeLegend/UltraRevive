package mc.gupe.revive;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/**
 * Maneja el RESOURCE PACK de los items custom y lo AUTO-ENVIA a los jugadores al entrar (estilo
 * BackpackPlus).
 */
public final class PackManager {

    // UUID fijo del pack (estable entre reinicios; reenviar reemplaza el nuestro, no duplica).
    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("ultrarevivir-pack".getBytes());
    private static final String PACK_PATH = "/ultrarevivir.zip";

    private final UltraRevive plugin;
    private PackServer httpServer;

    private boolean enabled;
    private boolean force;
    private String prompt;
    private String url;
    private byte[] hash;

    public PackManager(UltraRevive plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() { return enabled; }

    /** Prepara el pack: lo extrae, calcula el hash y arranca el modo elegido. */
    public void start() {
        FileConfiguration c = plugin.view().config();
        enabled = c.getBoolean("resource-pack.enabled", true);
        if (!enabled) return;

        force = c.getBoolean("resource-pack.force", false);
        // Vacio = lo pone el idioma. Si el staff escribe algo aca, manda lo suyo.
        String propio = c.getString("resource-pack.prompt", "");
        prompt = propio.isEmpty() ? plugin.lang().get("pack-prompt") : Lang.color(propio);

        // Extraer el .zip empaquetado en el jar a la carpeta del plugin.
        File zip = new File(plugin.view().root(), "resourcepack.zip");
        try {
            plugin.view().saveResource("resourcepack.zip");  // siempre la version actual del jar
        } catch (Throwable t) {
            plugin.getLogger().warning("No pude extraer resourcepack.zip: " + t.getMessage());
        }

        byte[] data;
        try {
            data = Files.readAllBytes(zip.toPath());
        } catch (Throwable t) {
            plugin.getLogger().warning("No encuentro resourcepack.zip; resource pack desactivado.");
            enabled = false;
            return;
        }
        hash = sha1(data);

        String mode = c.getString("resource-pack.mode", "internal").toLowerCase(Locale.ROOT);
        if (mode.equals("url")) {
            url = c.getString("resource-pack.url", "");
            if (url == null || url.isEmpty()) {
                plugin.getLogger().warning("resource-pack.mode=url pero 'url' esta vacio. Desactivado.");
                enabled = false;
                return;
            }
        } else {
            int port = c.getInt("resource-pack.port", 8081);
            String host = c.getString("resource-pack.host", "AUTO");
            if (host == null || host.isEmpty() || host.equalsIgnoreCase("AUTO")) host = detectHost();
            url = "http://" + host + ":" + port + PACK_PATH;
            httpServer = new PackServer();
            try {
                httpServer.start(port, PACK_PATH, data);
                plugin.getLogger().info("Resource pack sirviendose en: " + url);
            } catch (Throwable t) {
                plugin.getLogger().warning("No pude iniciar el servidor del pack en el puerto " + port
                        + " (revisa que el puerto este abierto/asignado): " + t.getMessage());
                enabled = false;
                return;
            }
        }
        plugin.getLogger().info("Resource pack listo. SHA-1: " + hex(hash));
    }

    /** Envia el pack a un jugador (al entrar). Apila con otros packs por su UUID. */
    public void sendTo(Player p) {
        if (!enabled || url == null) return;
        try {
            p.setResourcePack(PACK_UUID, url, hash, prompt, force);
        } catch (Throwable t) {
            // Respaldo para APIs viejas (sin UUID/prompt).
            try { p.setResourcePack(url, hash); } catch (Throwable ignored) {}
        }
    }

    public void stop() {
        if (httpServer != null) httpServer.stop();
    }

    // ---------- utilidades ----------

    /** Deduce la IP PUBLICA del server (para que el cliente pueda descargar el pack). */
    private String detectHost() {
        // 1) IP publica real via servicio externo (2s de timeout).
        try {
            java.net.URLConnection con = new java.net.URL("https://api.ipify.org").openConnection();
            con.setConnectTimeout(2000);
            con.setReadTimeout(2000);
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream()))) {
                String ip = r.readLine();
                if (ip != null && !ip.trim().isEmpty()) return ip.trim();
            }
        } catch (Throwable ignored) {}
        // 2) IP configurada en el server (si no es 0.0.0.0).
        String ip = plugin.getServer().getIp();
        if (ip != null && !ip.isEmpty() && !ip.equals("0.0.0.0")) return ip;
        // 3) ultimo recurso: IP local.
        try { return InetAddress.getLocalHost().getHostAddress(); }
        catch (Throwable t) { return "127.0.0.1"; }
    }

    private byte[] sha1(byte[] data) {
        try {
            return MessageDigest.getInstance("SHA-1").digest(data);
        } catch (Throwable t) {
            return new byte[0];
        }
    }

    private String hex(byte[] b) {
        StringBuilder sb = new StringBuilder();
        for (byte x : b) sb.append(String.format("%02x", x));
        return sb.toString();
    }
}
