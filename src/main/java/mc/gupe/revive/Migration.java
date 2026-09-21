package mc.gupe.revive;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Trae lo propio desde el plugin viejo, que se llamaba UltraRevive (con "r" al final).
 *
 * No borra nada. ⚠ Tiene que correr ANTES de extraer los recursos del jar: si el config.yml nuevo
 * ya existe, no habría dónde volcar los valores viejos.
 */
public final class Migration {

    private Migration() {}

    public static void run(JavaPlugin plugin, UltraView view) {
        File marca = new File(view.root(), ".migrado-desde-ultrarevivir");
        if (marca.exists()) return;

        // plugins/ sale de getDataFolder(), NO de view().root(): root() está un nivel más adentro.
        File plugins = plugin.getDataFolder().getParentFile();
        if (plugins == null) return;
        File viejo = new File(plugins, "UltraRevivir");
        if (!viejo.isDirectory()) return;  // instalación limpia

        copiarSiFalta(new File(viejo, "stats.yml"), new File(view.root(), "stats.yml"), plugin);
        copiarCarpetaSiFalta(new File(viejo, "lang"), new File(view.root(), "lang"), plugin);
        traerConfig(new File(viejo, "config.yml"), view, plugin);

        try { marca.createNewFile(); } catch (IOException ignored) {}
    }

    /** Vuelca los valores del config viejo en el nuevo, solo donde la clave siga existiendo. */
    private static void traerConfig(File viejo, UltraView view, JavaPlugin plugin) {
        if (!viejo.isFile()) return;
        File nuevo = view.file("config.yml");
        if (nuevo.exists()) return;  // ya hay uno: no se pisa

        view.saveResource("config.yml");
        FileConfiguration destino = YamlConfiguration.loadConfiguration(nuevo);
        FileConfiguration origen = YamlConfiguration.loadConfiguration(viejo);

        int n = 0;
        for (String clave : origen.getKeys(true)) {
            if (origen.isConfigurationSection(clave)) continue;  // solo valores
            if (!destino.contains(clave)) continue;  // esa opción ya no existe
            Object v = origen.get(clave);
            if (v == null || v.equals(destino.get(clave))) continue;
            destino.set(clave, v);
            n++;
        }
        if (n > 0) {
            try {
                destino.save(nuevo);
                plugin.getLogger().info("Migracion: se conservaron " + n + " ajustes del config "
                        + "viejo (idioma, chances de los totems, tiempos...).");
            } catch (IOException e) {
                plugin.getLogger().warning("No pude guardar el config migrado: " + e.getMessage());
            }
        }
    }

    private static void copiarSiFalta(File src, File dst, JavaPlugin plugin) {
        if (!src.isFile() || dst.exists()) return;
        try {
            Files.copy(src.toPath(), dst.toPath());
            plugin.getLogger().info("Migracion: traje " + src.getName() + " desde UltraRevive/.");
        } catch (IOException e) {
            plugin.getLogger().warning("No pude traer " + src.getName() + ": " + e.getMessage());
        }
    }

    private static void copiarCarpetaSiFalta(File src, File dst, JavaPlugin plugin) {
        if (!src.isDirectory()) return;
        File[] hijos = src.listFiles();
        if (hijos == null) return;
        if (!dst.isDirectory() && !dst.mkdirs()) return;
        int n = 0;
        for (File h : hijos) {
            File d = new File(dst, h.getName());
            if (h.isDirectory()) { copiarCarpetaSiFalta(h, d, plugin); continue; }
            if (d.exists()) continue;
            try { Files.copy(h.toPath(), d.toPath()); n++; } catch (IOException ignored) {}
        }
        if (n > 0) plugin.getLogger().info("Migracion: traje " + n + " archivos de "
                + src.getName() + "/ desde UltraRevive/.");
    }
}
