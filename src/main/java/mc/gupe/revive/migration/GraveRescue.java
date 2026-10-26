package mc.gupe.revive.migration;

import mc.gupe.revive.util.Tasks;
import mc.gupe.revive.UltraRevive;
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

/** One-time rescue of whatever was left in graves from before the grave system was removed. */
public final class GraveRescue implements Listener {

    private final UltraRevive plugin;
    private final File file;
    private final FileConfiguration data;

    public GraveRescue(UltraRevive plugin) {
        this.plugin = plugin;
        // Filename kept as-is on purpose: it's already on disk on live servers that had pending
        // graves to rescue, and renaming it would make them re-import from graves.yml and hand
        // out already-claimed items a second time.
        this.file = new File(plugin.getDataFolder(), "rescate-tumbas.yml");
        this.data = YamlConfiguration.loadConfiguration(file);
    }

    /** Is anyone still owed something? If not, there's no need to even register the listener. */
    public boolean hasPending() {
        ConfigurationSection s = data.getConfigurationSection("pendientes");
        return s != null && !s.getKeys(false).isEmpty();
    }

    /** Reads the old graves.yml, if any. @return how many players are still owed something. */
    public int importFromOldGraves() {
        if (data.getBoolean("hecho", false)) {
            return countPending();
        }

        // This is the plain, pre-UltraView data folder this plugin used before it got its own
        // shared-folder convention; graves.yml was never migrated out of it.
        File oldGraves = new File(plugin.getDataFolder(), "graves.yml");
        if (!oldGraves.isFile()) {
            markDone();
            return 0;
        }

        FileConfiguration g = YamlConfiguration.loadConfiguration(oldGraves);
        ConfigurationSection root = g.getConfigurationSection("graves");
        if (root == null) {
            root = g;
        }

        int graves = 0, withItems = 0, items = 0;
        for (String key : root.getKeys(false)) {
            ConfigurationSection t = root.getConfigurationSection(key);
            if (t == null) {
                continue;
            }
            graves++;

            List<?> list = t.getList("items");
            String uuid = t.getString("uuid");
            String name = t.getString("owner", "?");

            // Clear the head from the world even if the grave turns out empty; otherwise it's a
            // head nobody can break because the plugin that used to manage it is gone.
            removeFromWorld(t);

            if (list == null || list.isEmpty() || uuid == null) {
                continue;
            }
            withItems++;

            List<ItemStack> stuff = new ArrayList<>();
            for (Object o : list) {
                if (o instanceof ItemStack) {
                    stuff.add((ItemStack) o);
                }
            }
            int xp = t.getInt("xp", 0);
            if (stuff.isEmpty() && xp <= 0) {
                continue;
            }
            items += stuff.size();

            // Accumulates: a player can have more than one unopened grave.
            String base = "pendientes." + uuid;
            List<ItemStack> alreadyOwed = readItems(base + ".items");
            alreadyOwed.addAll(stuff);
            data.set(base + ".nombre", name);
            data.set(base + ".items", alreadyOwed);
            data.set(base + ".xp", data.getInt(base + ".xp", 0) + xp);
        }

        markDone();
        if (withItems > 0) {
            plugin.getLogger().info("Grave rescue: read " + graves + " grave(s), " + withItems
                    + " had something in them (" + items + " items). Returned to their owners on join.");
        } else if (graves > 0) {
            plugin.getLogger().info("Grave rescue: read " + graves + " grave(s), none had anything "
                    + "in them. Cleared from the map.");
        }
        return countPending();
    }

    /** Removes the grave's head (and its capsule, if any) if the world is loaded. */
    private void removeFromWorld(ConfigurationSection t) {
        try {
            World w = Bukkit.getWorld(t.getString("world", ""));
            if (w == null) {
                return;
            }
            int x = t.getInt("x"), y = t.getInt("y"), z = t.getInt("z");
            // The chunk is deliberately not loaded for this: if it isn't loaded, it gets cleaned
            // up the next time someone passes by.
            if (!w.isChunkLoaded(x >> 4, z >> 4)) {
                return;
            }
            Block b = w.getBlockAt(x, y, z);
            if (b.getType() == Material.PLAYER_HEAD || b.getType() == Material.PLAYER_WALL_HEAD) {
                b.setType(Material.AIR, false);
            }
            for (Object o : t.getList("capsule", new ArrayList<>())) {
                if (!(o instanceof Location)) {
                    continue;
                }
                Location l = (Location) o;
                if (l.getWorld() == null || !l.getWorld().isChunkLoaded(l.getBlockX() >> 4, l.getBlockZ() >> 4)) {
                    continue;
                }
                Block c = l.getBlock();
                if (c.getType() == Material.GLASS) {
                    c.setType(Material.AIR, false);
                }
            }
        } catch (Throwable ignored) {
        }
    }

    @EventHandler
    public void onJoin(PlayerJoinEvent e) {
        Player p = e.getPlayer();
        String base = "pendientes." + p.getUniqueId();
        if (!data.contains(base)) {
            return;
        }

        List<ItemStack> stuff = readItems(base + ".items");
        int xp = data.getInt(base + ".xp", 0);
        data.set(base, null);
        save();

        // One tick later: the inventory is still being assembled right as a player joins.
        Tasks.of(p, 20, () -> {
            if (!p.isOnline()) {
                return;
            }
            int dropped = 0;
            for (ItemStack it : stuff) {
                if (it == null || it.getType().isAir()) {
                    continue;
                }
                // Whatever doesn't fit gets dropped at their feet instead of lost: losing gear to a
                // full inventory is exactly what this rescue exists to prevent.
                for (ItemStack leftover : p.getInventory().addItem(it).values()) {
                    p.getWorld().dropItemNaturally(p.getLocation(), leftover);
                    dropped++;
                }
            }
            if (xp > 0) {
                p.giveExp(xp);
            }

            p.sendMessage(plugin.lang().pre("grave-rescue", "{items}", String.valueOf(stuff.size())));
            if (dropped > 0) {
                p.sendMessage(plugin.lang().pre("grave-rescue-dropped", "{count}", String.valueOf(dropped)));
            }

            if (!hasPending()) {
                file.delete();
                plugin.getLogger().info("Grave rescue: everyone has been paid back. Nothing left to do.");
            }
        });
    }

    @SuppressWarnings("unchecked")
    private List<ItemStack> readItems(String path) {
        List<ItemStack> out = new ArrayList<>();
        List<?> l = data.getList(path);
        if (l != null) {
            for (Object o : l) {
                if (o instanceof ItemStack) {
                    out.add((ItemStack) o);
                }
            }
        }
        return out;
    }

    private int countPending() {
        ConfigurationSection s = data.getConfigurationSection("pendientes");
        return s == null ? 0 : s.getKeys(false).size();
    }

    private void markDone() {
        data.set("hecho", true);
        save();
    }

    private void save() {
        try {
            data.save(file);
        } catch (Exception e) {
            plugin.getLogger().warning("Could not save rescate-tumbas.yml: " + e.getMessage());
        }
    }
}
