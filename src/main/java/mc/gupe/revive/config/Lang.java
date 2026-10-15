package mc.gupe.revive.config;

import mc.gupe.revive.UltraRevive;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/** Player-facing messages, loaded from {@code lang/es.yml} or {@code lang/en.yml}. */
public final class Lang {

    private final UltraRevive plugin;
    private FileConfiguration messages;

    public Lang(UltraRevive plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        File file = new File(plugin.getDataFolder(), "lang/" + language + ".yml");
        String lang = language;
        if (!file.exists()) {
            plugin.getLogger().warning("Language '" + language + "' not found, falling back to 'en'.");
            file = new File(plugin.getDataFolder(), "lang/en.yml");
            lang = "en";
        }
        messages = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(lang, file);
    }

    /** Adds to the server's file any key that exists in the jar but is missing on disk. */
    private void mergeDefaults(String language, File file) {
        try (InputStream in = plugin.getResource("lang/" + language + ".yml")) {
            if (in == null) {
                return;
            }
            FileConfiguration def = YamlConfiguration.loadConfiguration(
                    new InputStreamReader(in, StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : def.getKeys(true)) {
                if (!messages.contains(key)) {
                    messages.set(key, def.get(key));
                    changed = true;
                }
            }
            if (changed) {
                messages.save(file);
            }
        } catch (Exception ignored) {
            // Nothing to merge; the file stays as it was.
        }
    }

    /** The message with colors and placeholders replaced. pairs = key1, value1, key2, value2... */
    public String get(String key, String... pairs) {
        String raw = messages.getString(key, "&c[missing: " + key + "]");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace(pairs[i], pairs[i + 1]);
        }
        return color(raw);
    }

    /** Same as {@link #get}, but prefixed with the configured chat prefix. */
    public String pre(String key, String... pairs) {
        return color(messages.getString("prefix", "")) + get(key, pairs);
    }

    /**
     * Colored lore lines for an item.
     *
     * <p>Lore lives here and not in {@code config.yml} on purpose: if it lived in the config,
     * changing {@code language} would translate the chat messages but leave the items in
     * whatever language the file was first generated with.
     */
    public List<String> getList(String key) {
        List<String> out = new ArrayList<>();
        for (String l : messages.getStringList(key)) {
            out.add(color(l));
        }
        return out;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    public void chat(Player p, String key, String... pairs) {
        p.sendMessage(pre(key, pairs));
    }

    public void actionbar(Player p, String key, String... pairs) {
        Component c = LegacyComponentSerializer.legacySection().deserialize(get(key, pairs));
        p.sendActionBar(c);
    }

    public void title(Player p, String titleKey, String subKey) {
        p.sendTitle(get(titleKey), get(subKey), 5, 40, 10);
    }
}
