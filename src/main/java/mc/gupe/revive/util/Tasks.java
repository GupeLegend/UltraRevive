package mc.gupe.revive.util;

import io.papermc.paper.threadedregions.scheduler.ScheduledTask;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.Plugin;

import java.util.function.Consumer;

/**
 * Where each task runs. Paper's region and entity schedulers do the same as the old main-thread
 * scheduler on Paper and Purpur, and run on the right region thread on Folia, so the same jar works
 * on all three:
 * <ul>
 *   <li>{@link #of} for anything that touches a player or entity (it follows them around);</li>
 *   <li>{@link #at} for things anchored to a place;</li>
 *   <li>{@link #global} for the console and server-wide work;</li>
 *   <li>{@link #async} for network and disk.</li>
 * </ul>
 */
public final class Tasks {

    private static Plugin plugin;

    private Tasks() {
    }

    public static void init(Plugin owner) {
        plugin = owner;
    }

    /** Runs on the entity's thread, {@code delay} ticks from now (0 = next tick). Skipped if it's gone. */
    public static void of(Entity e, long delay, Runnable r) {
        if (delay <= 0) {
            e.getScheduler().run(plugin, t -> r.run(), null);
        } else {
            e.getScheduler().runDelayed(plugin, t -> r.run(), null, delay);
        }
    }

    public static void of(Entity e, Runnable r) {
        of(e, 0, r);
    }

    /** Repeats on the entity's thread; stops by itself when the entity is removed. */
    public static ScheduledTask timer(Entity e, long delay, long period, Consumer<ScheduledTask> r) {
        return e.getScheduler().runAtFixedRate(plugin, r, null, Math.max(1, delay), period);
    }

    /** Repeats on the thread that owns that location. */
    public static ScheduledTask timer(Location at, long delay, long period, Consumer<ScheduledTask> r) {
        return Bukkit.getRegionScheduler().runAtFixedRate(plugin, at, r, Math.max(1, delay), period);
    }

    public static void at(Location at, Runnable r) {
        Bukkit.getRegionScheduler().execute(plugin, at, r);
    }

    public static void global(Runnable r) {
        Bukkit.getGlobalRegionScheduler().execute(plugin, r);
    }

    public static void async(Runnable r) {
        Bukkit.getAsyncScheduler().runNow(plugin, t -> r.run());
    }
}
