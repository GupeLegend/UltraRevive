package mc.gupe.revive.migration;

import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;

/**
 * Moves data out of the old shared folder ({@code plugins/UltraView/UltraRevive/}) and into the
 * plugin's own, standard data folder ({@code plugins/UltraRevive/}).
 *
 * <p>Must run before anything extracts resources from the jar: if {@code config.yml} already
 * existed in the new folder, there would be nowhere to move the old one's settings into.
 */
public final class FolderMigration {

    private static final String SHARED_ROOT_NAME = "UltraView";

    private FolderMigration() {
    }

    public static void run(JavaPlugin plugin) {
        File dataFolder = plugin.getDataFolder();
        File pluginsRoot = dataFolder.getParentFile();
        if (pluginsRoot == null) {
            return;
        }
        File sharedRoot = new File(pluginsRoot, SHARED_ROOT_NAME);
        File sharedOwnFolder = new File(sharedRoot, plugin.getName());
        if (!sharedOwnFolder.isDirectory()) {
            return; // nothing to migrate, or already migrated and cleaned up
        }

        boolean alreadyMigrated = new File(dataFolder, "config.yml").isFile();
        if (!alreadyMigrated) {
            int moved = moveAll(sharedOwnFolder, dataFolder, plugin);
            if (moved > 0) {
                plugin.getLogger().info("Moved " + moved + " file(s) from the old shared folder "
                        + "(plugins/UltraView/" + plugin.getName() + "/) to plugins/" + plugin.getName() + "/.");
            }
        }

        deleteIfEmpty(sharedOwnFolder);
        deleteIfEmpty(sharedRoot);
    }

    /** Moves everything from {@code from} into {@code to}; files that would collide are left untouched. */
    private static int moveAll(File from, File to, JavaPlugin plugin) {
        if (!to.isDirectory() && !to.mkdirs()) {
            return 0;
        }
        File[] children = from.listFiles();
        if (children == null) {
            return 0;
        }
        int moved = 0;
        for (File child : children) {
            File dest = new File(to, child.getName());
            if (child.isDirectory()) {
                moved += moveAll(child, dest, plugin);
                deleteIfEmpty(child);
                continue;
            }
            if (dest.exists()) {
                plugin.getLogger().warning("Skipped " + child.getName()
                        + ": a file with that name already exists in the new folder.");
                continue;
            }
            try {
                Files.move(child.toPath(), dest.toPath());
                moved++;
            } catch (IOException moveFailed) {
                try {
                    Files.copy(child.toPath(), dest.toPath());
                    moved++;
                } catch (IOException copyFailed) {
                    plugin.getLogger().warning("Could not move " + child.getName() + ": " + copyFailed.getMessage());
                }
            }
        }
        return moved;
    }

    private static void deleteIfEmpty(File dir) {
        if (!dir.isDirectory()) {
            return;
        }
        File[] children = dir.listFiles();
        if (children != null && children.length == 0) {
            dir.delete();
        }
    }
}
