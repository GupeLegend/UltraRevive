package mc.gupe.revive.migration;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Brings over settings from the old plugin, back when it was called UltraRevivir.
 *
 * <p>Deletes nothing. Must run before any resource is extracted from the jar: if the new
 * config.yml already existed, there would be nowhere to put the old values.
 */
public final class LegacyNameMigration {

    private static final String OLD_PLUGIN_FOLDER = "UltraRevivir";

    private LegacyNameMigration() {
    }

    public static void run(JavaPlugin plugin) {
        File marker = new File(plugin.getDataFolder(), ".migrated-from-ultrarevivir");
        if (marker.exists()) {
            return;
        }

        File pluginsRoot = plugin.getDataFolder().getParentFile();
        if (pluginsRoot == null) {
            return;
        }
        File oldFolder = new File(pluginsRoot, OLD_PLUGIN_FOLDER);
        if (!oldFolder.isDirectory()) {
            return; // clean install
        }

        copyIfMissing(new File(oldFolder, "stats.yml"), new File(plugin.getDataFolder(), "stats.yml"), plugin);
        copyFolderIfMissing(new File(oldFolder, "lang"), new File(plugin.getDataFolder(), "lang"), plugin);
        bringOverConfig(new File(oldFolder, "config.yml"), plugin);

        try {
            marker.createNewFile();
        } catch (IOException ignored) {
        }
    }

    /** Carries over values from the old config, only for keys that still exist in the new one. */
    private static void bringOverConfig(File oldConfig, JavaPlugin plugin) {
        if (!oldConfig.isFile()) {
            return;
        }
        File newConfig = new File(plugin.getDataFolder(), "config.yml");
        if (newConfig.exists()) {
            return;
        }

        plugin.saveResource("config.yml", false);
        FileConfiguration destination = YamlConfiguration.loadConfiguration(newConfig);
        FileConfiguration source = YamlConfiguration.loadConfiguration(oldConfig);

        int changed = 0;
        for (String key : source.getKeys(true)) {
            if (source.isConfigurationSection(key) || !destination.contains(key)) {
                continue;
            }
            Object value = source.get(key);
            if (value == null || value.equals(destination.get(key))) {
                continue;
            }
            destination.set(key, value);
            changed++;
        }
        if (changed > 0) {
            try {
                destination.save(newConfig);
                plugin.getLogger().info("Migration: kept " + changed + " setting(s) from the old "
                        + "config (language, totem drop chances, timers...).");
            } catch (IOException e) {
                plugin.getLogger().warning("Could not save the migrated config: " + e.getMessage());
            }
        }
    }

    private static void copyIfMissing(File src, File dst, JavaPlugin plugin) {
        if (!src.isFile() || dst.exists()) {
            return;
        }
        try {
            Files.copy(src.toPath(), dst.toPath());
            plugin.getLogger().info("Migration: brought over " + src.getName() + " from " + OLD_PLUGIN_FOLDER + "/.");
        } catch (IOException e) {
            plugin.getLogger().warning("Could not bring over " + src.getName() + ": " + e.getMessage());
        }
    }

    private static void copyFolderIfMissing(File src, File dst, JavaPlugin plugin) {
        if (!src.isDirectory()) {
            return;
        }
        File[] children = src.listFiles();
        if (children == null) {
            return;
        }
        if (!dst.isDirectory() && !dst.mkdirs()) {
            return;
        }
        int copied = 0;
        for (File child : children) {
            File dest = new File(dst, child.getName());
            if (child.isDirectory()) {
                copyFolderIfMissing(child, dest, plugin);
                continue;
            }
            if (dest.exists()) {
                continue;
            }
            try {
                Files.copy(child.toPath(), dest.toPath());
                copied++;
            } catch (IOException ignored) {
            }
        }
        if (copied > 0) {
            plugin.getLogger().info("Migration: brought over " + copied + " file(s) from "
                    + OLD_PLUGIN_FOLDER + "/" + src.getName() + "/.");
        }
    }
}
