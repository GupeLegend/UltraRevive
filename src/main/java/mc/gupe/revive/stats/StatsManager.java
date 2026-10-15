package mc.gupe.revive.stats;

import mc.gupe.revive.UltraRevive;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/** Per-player statistics (times downed, revived, and so on). */
public final class StatsManager {

    private final UltraRevive plugin;
    private final File file;
    private FileConfiguration data;
    private boolean dirty = false;

    public StatsManager(UltraRevive plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.getDataFolder(), "stats.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try {
                file.getParentFile().mkdirs();
                file.createNewFile();
            } catch (IOException ignored) {
            }
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try {
            data.save(file);
        } catch (IOException e) {
            plugin.getLogger().warning("Could not save stats.yml: " + e.getMessage());
        }
    }

    /** Saves to disk only if something changed (called every ~30s and on disable). */
    public void flush() {
        if (dirty) {
            save();
            dirty = false;
        }
    }

    private void inc(UUID id, String key) {
        if (!plugin.settings().statsEnabled) {
            return;
        }
        String p = id.toString() + "." + key;
        data.set(p, data.getInt(p, 0) + 1);
        dirty = true; // batched: the actual write happens on flush()
    }

    public int get(UUID id, String key) {
        return data.getInt(id.toString() + "." + key, 0);
    }

    public void addDowned(UUID id) {
        inc(id, "downed");
    }

    public void addRevived(UUID id) {
        inc(id, "revived");
    }

    public void addRevivedOther(UUID id) {
        inc(id, "revives");
    }

    public void addSelfRevived(UUID id) {
        inc(id, "self-revives");
    }

    public void addBledOut(UUID id) {
        inc(id, "bled-out");
    }

    /** All players that have revive-related stats saved. */
    public Set<UUID> allPlayers() {
        Set<UUID> set = new HashSet<>();
        for (String key : data.getKeys(false)) {
            try {
                set.add(UUID.fromString(key));
            } catch (Exception ignored) {
            }
        }
        return set;
    }

    /** Copies kills, deaths, playtime and level from the server's own (vanilla) statistics. */
    public void syncFromVanilla(org.bukkit.entity.Player p) {
        if (!plugin.settings().statsEnabled || p == null) {
            return;
        }
        String base = p.getUniqueId().toString();
        try {
            data.set(base + ".kills", p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS));
            data.set(base + ".deaths", p.getStatistic(org.bukkit.Statistic.DEATHS));
            data.set(base + ".minutes", p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20 / 60);
            data.set(base + ".level", p.getLevel());
            dirty = true;
        } catch (Throwable ignored) {
        }
    }
}
