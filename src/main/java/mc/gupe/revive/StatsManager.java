package mc.gupe.revive;

import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.io.IOException;
import java.util.UUID;

/** Estadisticas por jugador (cuantas veces te derribaron, reviviste, etc.). */
public final class StatsManager {

    private final UltraRevive plugin;
    private final File file;
    private FileConfiguration data;
    private boolean dirty = false;

    public StatsManager(UltraRevive plugin) {
        this.plugin = plugin;
        this.file = new File(plugin.view().root(), "stats.yml");
        load();
    }

    public void load() {
        if (!file.exists()) {
            try { file.getParentFile().mkdirs(); file.createNewFile(); } catch (IOException ignored) {}
        }
        data = YamlConfiguration.loadConfiguration(file);
    }

    public void save() {
        try { data.save(file); }
        catch (IOException e) { plugin.getLogger().warning("No se pudo guardar stats.yml: " + e.getMessage()); }
    }

    /** Guarda a disco SOLO si hubo cambios (lo llama un timer cada ~30s y al apagar). */
    public void flush() {
        if (dirty) { save(); dirty = false; }
    }

    private void inc(UUID id, String key) {
        if (!plugin.settings().statsEnabled) return;
        String p = id.toString() + "." + key;
        data.set(p, data.getInt(p, 0) + 1);
        dirty = true;  // no escribimos a disco en cada evento; el flush lo hace en lote
    }

    public int get(UUID id, String key) {
        return data.getInt(id.toString() + "." + key, 0);
    }

    // --- contadores ---
    public void addDowned(UUID id)       { inc(id, "downed"); }  // te derribaron
    public void addRevived(UUID id)      { inc(id, "revived"); }  // te revivieron
    public void addRevivedOther(UUID id) { inc(id, "revives"); }  // reviviste a alguien
    public void addSelfRevived(UUID id)  { inc(id, "self-revives"); }  // te auto-reviviste
    public void addBledOut(UUID id)      { inc(id, "bled-out"); }  // te desangraste

    // --- 1.6.1: stats para el menu de equipos (kills, muertes, horas, nivel) ---
    public void addKill(UUID id)  { inc(id, "kills"); }
    public void addDeath(UUID id) { inc(id, "deaths"); }
    public void addMinute(UUID id) {
        if (!plugin.settings().statsEnabled) return;
        String p = id.toString() + ".minutes";
        data.set(p, data.getInt(p, 0) + 1);
        dirty = true;
    }
    public void setLevel(UUID id, int lvl) {
        if (!plugin.settings().statsEnabled) return;
        data.set(id.toString() + ".level", lvl);
        dirty = true;
    }

    // kills/muertes/horas salen de las ESTADISTICAS DEL MUNDO (online y offline). El nivel XP
    // no es una estadistica vanilla, asi que ese si lo sacamos de nuestro snapshot (stats.yml).
    public int kills(UUID id) { return 0; }  // lo daba el TOP de clanes, que salio
    public int deaths(UUID id) { return 0; }  // lo daba el TOP de clanes, que salio
    public int minutes(UUID id) { return 0; }  // lo daba el TOP de clanes, que salio
    public int level(UUID id)   { return get(id, "level"); }

    /** Todos los jugadores con estadisticas de revivir guardadas. */
    public java.util.Set<UUID> allPlayers() {
        java.util.Set<UUID> set = new java.util.HashSet<>();
        for (String key : data.getKeys(false)) {
            try { set.add(UUID.fromString(key)); } catch (Exception ignored) {}
        }
        return set;
    }

    /**
     * Vincula nuestras stats con las ESTADISTICAS DEL MUNDO (vanilla): copia kills, muertes,
     * minutos jugados y nivel del jugador.
     */
    public void syncFromVanilla(org.bukkit.entity.Player p) {
        if (!plugin.settings().statsEnabled || p == null) return;
        String base = p.getUniqueId().toString();
        try {
            data.set(base + ".kills", p.getStatistic(org.bukkit.Statistic.PLAYER_KILLS));
            data.set(base + ".deaths", p.getStatistic(org.bukkit.Statistic.DEATHS));
            data.set(base + ".minutes", p.getStatistic(org.bukkit.Statistic.PLAY_ONE_MINUTE) / 20 / 60);  // ticks -> min
            data.set(base + ".level", p.getLevel());
            dirty = true;
        } catch (Throwable ignored) {}
    }
}
