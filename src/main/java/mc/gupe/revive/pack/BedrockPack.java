package mc.gupe.revive.pack;

import mc.gupe.revive.UltraRevive;
import org.bukkit.Bukkit;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.Plugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.Arrays;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

/**
 * Gives Bedrock players (Geyser) the same totem textures Java players get from the resource pack.
 *
 * <p>Bedrock can't load a Java pack, so if Geyser is on this server the plugin drops two files into
 * Geyser's folder: a Bedrock pack ({@code packs/UltraRevive.mcpack}, built by tools/bedrock_pack.py) and
 * a custom item mapping ({@code custom_mappings/ultrarevive.json}) that tells Geyser which texture
 * goes with each totem's custom-model-data. The mapping is written from config.yml, so changing a
 * totem's custom-model-data or material keeps Bedrock in sync.
 *
 * <p>It runs in {@code onLoad} and the plugin is declared {@code loadbefore: Geyser-Spigot}, so the files
 * are already there when Geyser starts and reads them: no second restart. A copy of both files is
 * also left in {@code plugins/UltraRevive/bedrock/} for networks where Geyser runs on the proxy.
 */
public final class BedrockPack {

    private static final String[] GEYSER = {"Geyser-Spigot", "Geyser-Paper"};
    // config section, Bedrock id, English fallback name (Bedrock shows the item's real name anyway)
    private static final String[][] TOTEMS = {
            {"self-revive", "aether_totem", "Blessed Totem of the Aether"},
            {"revive-boost", "nether_totem", "Cursed Totem of the Nether"},
            {"end-revive", "end_totem", "Unknown Totem of the End"},
    };

    private BedrockPack() {
    }

    public static void install(UltraRevive plugin) {
        try {
            YamlConfiguration cfg = plugin.bootConfig();
            if (!cfg.getBoolean("resource-pack.bedrock")) {
                return;
            }
            byte[] pack = resource(plugin, "bedrock/UltraRevive.mcpack");
            byte[] mapping = mapping(cfg).getBytes(StandardCharsets.UTF_8);

            File copia = new File(plugin.getDataFolder(), "bedrock");
            write(new File(copia, "UltraRevive.mcpack"), pack);
            write(new File(copia, "ultrarevive.json"), mapping);

            Plugin geyser = null;
            for (String name : GEYSER) {
                geyser = Bukkit.getPluginManager().getPlugin(name);
                if (geyser != null) {
                    break;
                }
            }
            if (geyser == null) {
                plugin.getLogger().info("Geyser isn't on this server: Bedrock textures not installed. If Geyser runs on "
                        + "your proxy, copy plugins/UltraRevive/bedrock/ultrarevive.json to its custom_mappings/ folder "
                        + "and UltraRevive.mcpack to its packs/ folder.");
                return;
            }
            boolean cambio = write(new File(geyser.getDataFolder(), "packs/UltraRevive.mcpack"), pack)
                    | write(new File(geyser.getDataFolder(), "custom_mappings/ultrarevive.json"), mapping);
            if (!cambio) {
                return;
            }
            if (geyser.isEnabled()) {
                // only happens with /reload or plugin managers: Geyser already read its folders
                plugin.getLogger().warning("Bedrock textures updated in " + geyser.getName()
                        + "; restart the server so Geyser loads them.");
            } else {
                plugin.getLogger().info("Bedrock textures installed in " + geyser.getName() + " (pack + item mapping).");
            }
        } catch (IOException | RuntimeException e) {
            plugin.getLogger().warning("Couldn't install the Bedrock textures: " + e.getMessage());
        }
    }

    /** Geyser custom item mapping, format 2, "legacy" definitions (they match custom_model_data). */
    static String mapping(YamlConfiguration cfg) {
        Map<String, StringBuilder> porItem = new LinkedHashMap<>();
        for (String[] t : TOTEMS) {
            String base = "items." + t[0] + ".";
            int cmd = cfg.getInt(base + "custom-model-data");
            if (cmd <= 0) {
                continue;   // without custom-model-data the totem looks vanilla on Java too
            }
            String item = "minecraft:" + String.valueOf(cfg.getString(base + "material")).toLowerCase(Locale.ROOT);
            StringBuilder sb = porItem.computeIfAbsent(item, k -> new StringBuilder());
            if (sb.length() > 0) {
                sb.append(",\n");
            }
            sb.append("      {\n")
                    .append("        \"type\": \"legacy\",\n")
                    .append("        \"custom_model_data\": ").append(cmd).append(",\n")
                    .append("        \"bedrock_identifier\": \"ultrarevive:").append(t[1]).append("\",\n")
                    .append("        \"display_name\": \"").append(t[2]).append("\",\n")
                    .append("        \"bedrock_options\": { \"icon\": \"ultrarevive.").append(t[1])
                    .append("\", \"allow_offhand\": true }\n")
                    .append("      }");
        }
        StringBuilder json = new StringBuilder("{\n  \"format_version\": 2,\n  \"items\": {\n");
        int i = 0;
        for (Map.Entry<String, StringBuilder> e : porItem.entrySet()) {
            json.append("    \"").append(e.getKey()).append("\": [\n").append(e.getValue()).append("\n    ]")
                    .append(++i < porItem.size() ? ",\n" : "\n");
        }
        return json.append("  }\n}\n").toString();
    }

    private static byte[] resource(UltraRevive plugin, String path) throws IOException {
        try (InputStream in = plugin.getResource(path)) {
            if (in == null) {
                throw new IOException(path + " is missing from the jar");
            }
            return in.readAllBytes();
        }
    }

    /** Writes only if the content changed. Returns true if it wrote. */
    private static boolean write(File f, byte[] data) throws IOException {
        if (f.isFile() && Arrays.equals(Files.readAllBytes(f.toPath()), data)) {
            return false;
        }
        Files.createDirectories(f.getParentFile().toPath());
        Files.write(f.toPath(), data);
        return true;
    }
}
