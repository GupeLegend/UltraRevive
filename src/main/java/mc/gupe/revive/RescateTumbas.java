package mc.gupe.revive;

import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.inventory.ItemStack;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** RESCATE DE TUMBAS — corre una sola vez, al actualizar. */
public final class RescateTumbas implements Listener {

    private final UltraRevive plugin;
    private final File archivo;
    private FileConfiguration data;

    public RescateTumbas(UltraRevive plugin) {
        this.plugin = plugin;
        this.archivo = plugin.view().file("rescate-tumbas.yml");
        this.data = YamlConfiguration.loadConfiguration(archivo);
    }

    /** ¿Queda alguien por cobrar? Si no, no hace falta ni registrar el listener. */
    public boolean hayPendientes() {
        ConfigurationSection s = data.getConfigurationSection("pendientes");
        return s != null && !s.getKeys(false).isEmpty();
    }

    // 1) LEER LAS TUMBAS VIEJAS
    /** @return cuántos jugadores quedaron con cosas por cobrar. */
    public int importar() {
        if (data.getBoolean("hecho", false)) return contarPendientes();

        // plugins/ sale de getDataFolder(): root() esta un nivel mas adentro
        // (plugins/UltraView/UltraRevive/) y desde ahi "UltraRevivir" no existe.
        File plugins = plugin.getDataFolder().getParentFile();
        File viejo = new File(plugins, "UltraRevive/graves.yml");
        if (!viejo.isFile()) { marcarHecho(); return 0; }

        FileConfiguration g = YamlConfiguration.loadConfiguration(viejo);
        ConfigurationSection raiz = g.getConfigurationSection("graves");
        if (raiz == null) raiz = g;

        int tumbas = 0, conCosas = 0, items = 0;
        for (String key : raiz.getKeys(false)) {
            ConfigurationSection t = raiz.getConfigurationSection(key);
            if (t == null) continue;
            tumbas++;

            List<?> lista = t.getList("items");
            String uuid = t.getString("uuid");
            String nombre = t.getString("owner", "?");

            // Sacar la cabeza del mapa aunque la tumba esté vacía: si no, quedan cabezas sueltas
            // que nadie puede romper porque ya no hay plugin que las maneje.
            limpiarDelMundo(t);

            if (lista == null || lista.isEmpty() || uuid == null) continue;
            conCosas++;

            List<ItemStack> cosas = new ArrayList<>();
            for (Object o : lista) if (o instanceof ItemStack) cosas.add((ItemStack) o);
            int xp = t.getInt("xp", 0);
            if (cosas.isEmpty() && xp <= 0) continue;
            items += cosas.size();

            // Se acumula: un jugador puede tener varias tumbas sin abrir.
            String base = "pendientes." + uuid;
            List<ItemStack> yaTenia = leerItems(base + ".items");
            yaTenia.addAll(cosas);
            data.set(base + ".nombre", nombre);
            data.set(base + ".items", yaTenia);
            data.set(base + ".xp", data.getInt(base + ".xp", 0) + xp);
        }

        marcarHecho();
        if (conCosas > 0)
            plugin.getLogger().info("Rescate de tumbas: " + tumbas + " tumbas leidas, " + conCosas
                    + " tenian cosas (" + items + " items). Se les devuelve a sus duenos cuando entren.");
        else if (tumbas > 0)
            plugin.getLogger().info("Rescate de tumbas: " + tumbas + " tumbas leidas, ninguna tenia "
                    + "nada adentro. Se limpiaron del mapa.");
        return contarPendientes();
    }

    /** Saca la cabeza de la tumba y su cápsula, si el mundo está cargado. */
    private void limpiarDelMundo(ConfigurationSection t) {
        try {
            World w = Bukkit.getWorld(t.getString("world", ""));
            if (w == null) return;
            int x = t.getInt("x"), y = t.getInt("y"), z = t.getInt("z");
            // ⚠ No cargar el chunk a propósito: si no está cargado, se limpia cuando alguien pase.
            if (!w.isChunkLoaded(x >> 4, z >> 4)) return;
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() == Material.PLAYER_HEAD || b.getType() == Material.PLAYER_WALL_HEAD)
                b.setType(Material.AIR, false);
            for (Object o : t.getList("capsule", new ArrayList<>())) {
                if (!(o instanceof Location)) continue;
                Location l = (Location) o;
                if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) continue;
                Block c = l.getBlock();
                if (c.getType() == Material.GLASS) c.setType(Material.AIR, false);
            }
        } catch (Throwable ignored) {}
    }

    // 2) DEVOLVER AL ENTRAR
    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String base = "pendientes." + p.getUniqueId();
        if (!data.contains(base)) return;

        List<ItemStack> cosas = leerItems(base + ".items");
        int xp = data.getInt(base + ".xp", 0);
        data.set(base, null);
        guardar();

        // Un tick después: al entrar, el inventario todavía se está armando.
        Bukkit.getScheduler().runTaskLater(plugin, () -> {
            if (!p.isOnline()) return;
            int caidos = 0;
            for (ItemStack it : cosas) {
                if (it == null || it.getType().isAir()) continue;
                // Lo que no entra se le tira a los pies: perder el equipo por tener el inventario
                // lleno sería exactamente lo que este rescate viene a evitar.
                for (ItemStack sobra : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), sobra);
                    caidos++;
                }
            }
            if (xp > 0) p.giveExp(xp);

            p.sendMessage(plugin.lang().pre("grave-rescue", "{items}", String.valueOf(cosas.size())));
            if (caidos > 0) p.sendMessage(plugin.lang().pre("grave-rescue-dropped",
                    "{count}", String.valueOf(caidos)));
            plugin.getLogger().info("Rescate de tumbas: se le devolvieron " + cosas.size()
                    + " items a " + p.getName() + (caidos > 0 ? " (" + caidos + " al piso)" : "") + ".");

            if (!hayPendientes()) {
                archivo.delete();
                plugin.getLogger().info("Rescate de tumbas: ya cobraron todos. Listo, no queda nada.");
            }
        }, 20L);
    }

    // ---------- utilidades ----------
    @SuppressWarnings("unchecked")
    private List<ItemStack> leerItems(String ruta) {
        List<ItemStack> out = new ArrayList<>();
        List<?> l = data.getList(ruta);
        if (l != null) for (Object o : l) if (o instanceof ItemStack) out.add((ItemStack) o);
        return out;
    }

    private int contarPendientes() {
        ConfigurationSection s = data.getConfigurationSection("pendientes");
        return s == null ? 0 : s.getKeys(false).size();
    }

    private void marcarHecho() {
        data.set("hecho", true);
        data.set("nota", "Marca del rescate de tumbas al pasar a la version sin tumbas. "
                + "Borrar este archivo hace que se vuelva a leer graves.yml y se devuelva TODO otra vez.");
        guardar();
    }

    private void guardar() {
        try { data.save(archivo); }
        catch (Exception e) { plugin.getLogger().warning("No pude guardar rescate-tumbas.yml: " + e.getMessage()); }
    }
}
