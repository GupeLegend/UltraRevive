package mc.gupe.revive;

import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.ChatColor;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;

import java.io.File;

/** Maneja los mensajes en el idioma elegido (lang/es.yml o lang/en.yml). */
public final class Lang {

    private final UltraRevive plugin;
    private org.bukkit.configuration.file.FileConfiguration messages;

    public Lang(UltraRevive plugin) {
        this.plugin = plugin;
    }

    public void load(String language) {
        File file = new File(plugin.view().root(), "lang/" + language + ".yml");
        String lang = language;
        if (!file.exists()) {
            plugin.getLogger().warning("Language '" + language + "' not found, falling back to 'en'.");
            file = new File(plugin.view().root(), "lang/en.yml");
            lang = "en";
        }
        messages = YamlConfiguration.loadConfiguration(file);
        mergeDefaults(lang, file);  // agrega las claves nuevas del jar que falten en el server
    }

    /**
     * Copia al archivo del server cualquier clave que exista en el idioma del jar pero falte en
     * disco (así al actualizar el jar no quedan mensajes como "[falta: ...]" sin tocar nada).
     */
    private void mergeDefaults(String language, File file) {
        try (java.io.InputStream in = plugin.getResource("lang/" + language + ".yml")) {
            if (in == null) return;
            org.bukkit.configuration.file.FileConfiguration def = YamlConfiguration.loadConfiguration(
                    new java.io.InputStreamReader(in, java.nio.charset.StandardCharsets.UTF_8));
            boolean changed = false;
            for (String key : def.getKeys(true)) {
                if (!messages.contains(key)) { messages.set(key, def.get(key)); changed = true; }
            }
            if (changed) messages.save(file);
        } catch (Exception ignored) {}
    }

    /** Devuelve el mensaje con color y placeholders reemplazados. pairs = clave1, valor1, clave2, valor2... */
    public String get(String key, String... pairs) {
        String raw = messages.getString(key, "&c[falta: " + key + "]");
        for (int i = 0; i + 1 < pairs.length; i += 2) {
            raw = raw.replace(pairs[i], pairs[i + 1]);
        }
        return color(raw);
    }

    /** Igual que get() pero antepone el prefix. */
    public String pre(String key, String... pairs) {
        return color(messages.getString("prefix", "")) + get(key, pairs);
    }

    /**
     * Lista de lineas con color, para el lore de los items.
     *
     * ⚠ El lore vive ACA y no en config.yml a proposito: si estuviera en el config, cambiar
     * 'language' traduciria los mensajes pero los items seguirian en el idioma con el que se
     * genero el archivo la primera vez.
     */
    public java.util.List<String> getList(String key) {
        java.util.List<String> out = new java.util.ArrayList<>();
        for (String l : messages.getStringList(key)) out.add(color(l));
        return out;
    }

    public static String color(String s) {
        return ChatColor.translateAlternateColorCodes('&', s);
    }

    // ---------- helpers de envio ----------

    public void chat(Player p, String key, String... pairs) {
        p.sendMessage(pre(key, pairs));
    }

    public void actionbar(Player p, String key, String... pairs) {
        // get() ya devuelve la cadena con codigos de seccion; la convertimos a Component (adventure).
        Component c = LegacyComponentSerializer.legacySection().deserialize(get(key, pairs));
        p.sendActionBar(c);
    }

    public void title(Player p, String titleKey, String subKey) {
        p.sendTitle(get(titleKey), get(subKey), 5, 40, 10);
    }
}
