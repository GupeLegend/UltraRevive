package mc.gupe.revive.pack;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.config.Lang;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.net.InetAddress;
import java.nio.file.Files;
import java.security.MessageDigest;
import java.util.Locale;
import java.util.UUID;

/** Serves the totems' resource pack and auto-sends it to players when they join. */
public final class ResourcePackManager {

    // Fixed UUID so resending replaces our pack instead of stacking a duplicate.
    private static final UUID PACK_UUID = UUID.nameUUIDFromBytes("ultrarevivir-pack".getBytes());
    private static final String PACK_PATH = "/ultrarevivir.zip";

    private final UltraRevive plugin;
    private ResourcePackServer httpServer;

    private boolean enabled;
    private boolean force;
    private String prompt;
    private String url;
    private byte[] hash;

    public ResourcePackManager(UltraRevive plugin) {
        this.plugin = plugin;
    }

    public boolean isEnabled() {
        return enabled;
    }

    /** Extracts the pack, hashes it and starts whichever delivery mode is configured. */
    public void start() {
        FileConfiguration c = plugin.getConfig();
        enabled = c.getBoolean("resource-pack.enabled", true);
        if (!enabled) {
            return;
        }

        force = c.getBoolean("resource-pack.force", false);
        String custom = c.getString("resource-pack.prompt", "");
        prompt = custom.isEmpty() ? plugin.lang().get("pack-prompt") : Lang.color(custom);

        File zip = new File(plugin.getDataFolder(), "resourcepack.zip");
        try {
            plugin.extractIfMissing("resourcepack.zip");
        } catch (Throwable t) {
            plugin.getLogger().warning("Could not extract resourcepack.zip: " + t.getMessage());
        }

        byte[] data;
        try {
            data = Files.readAllBytes(zip.toPath());
        } catch (Throwable t) {
            plugin.getLogger().warning("resourcepack.zip not found; resource pack disabled.");
            enabled = false;
            return;
        }
        hash = sha1(data);

        String mode = c.getString("resource-pack.mode", "internal").toLowerCase(Locale.ROOT);
        if (mode.equals("url")) {
            url = c.getString("resource-pack.url", "");
            if (url == null || url.isEmpty()) {
                plugin.getLogger().warning("resource-pack.mode=url but 'url' is empty. Disabled.");
                enabled = false;
                return;
            }
        } else {
            int port = c.getInt("resource-pack.port", 8081);
            String host = c.getString("resource-pack.host", "AUTO");
            if (host == null || host.isEmpty() || host.equalsIgnoreCase("AUTO")) {
                host = detectHost();
            }
            url = "http://" + host + ":" + port + PACK_PATH;
            httpServer = new ResourcePackServer();
            try {
                httpServer.start(port, PACK_PATH, data);
                plugin.getLogger().info("Serving the resource pack at: " + url);
            } catch (Throwable t) {
                plugin.getLogger().warning("Could not start the pack server on port " + port
                        + " (check that it's open/forwarded): " + t.getMessage());
                enabled = false;
                return;
            }
        }
        plugin.getLogger().info("Resource pack ready. SHA-1: " + hex(hash));
    }

    /** Sends the pack to a player (on join). Stacks with other packs by its UUID. */
    public void sendTo(Player p) {
        if (!enabled || url == null) {
            return;
        }
        try {
            p.setResourcePack(PACK_UUID, url, hash, prompt, force);
        } catch (Throwable t) {
            try {
                p.setResourcePack(url, hash);
            } catch (Throwable ignored) {
            }
        }
    }

    public void stop() {
        if (httpServer != null) {
            httpServer.stop();
        }
    }

    /** Guesses the server's public IP so clients can download the pack. */
    private String detectHost() {
        try {
            java.net.URLConnection con = new java.net.URL("https://api.ipify.org").openConnection();
            con.setConnectTimeout(2000);
            con.setReadTimeout(2000);
            try (java.io.BufferedReader r = new java.io.BufferedReader(
                    new java.io.InputStreamReader(con.getInputStream()))) {
                String ip = r.readLine();
                if (ip != null && !ip.trim().isEmpty()) {
                    return ip.trim();
                }
            }
        } catch (Throwable ignored) {
        }
        String ip = plugin.getServer().getIp();
        if (ip != null && !ip.isEmpty() && !ip.equals("0.0.0.0")) {
            return ip;
        }
        try {
            return InetAddress.getLocalHost().getHostAddress();
        } catch (Throwable t) {
            return "127.0.0.1";
        }
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
        for (byte x : b) {
            sb.append(String.format("%02x", x));
        }
        return sb.toString();
    }
}
