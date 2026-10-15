package mc.gupe.revive;

import mc.gupe.revive.command.ReviveCommand;
import mc.gupe.revive.config.Lang;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.integration.LuckPermsHook;
import mc.gupe.revive.item.Items;
import mc.gupe.revive.listener.CombatListener;
import mc.gupe.revive.listener.ConnectionListener;
import mc.gupe.revive.listener.LootListener;
import mc.gupe.revive.listener.RestrictionListener;
import mc.gupe.revive.listener.StatsListener;
import mc.gupe.revive.migration.FolderMigration;
import mc.gupe.revive.migration.GraveRescue;
import mc.gupe.revive.migration.LegacyNameMigration;
import mc.gupe.revive.model.Cooldowns;
import mc.gupe.revive.pack.ResourcePackManager;
import mc.gupe.revive.revive.ReviveManager;
import mc.gupe.revive.stats.StatsManager;
import mc.gupe.revive.util.PlayerHeads;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** UltraRevive - you don't die outright: you go DOWNED and your team can pick you back up. */
public final class UltraRevive extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private Items items;
    private ReviveManager manager;
    private ResourcePackManager pack;
    private StatsManager stats;
    private LuckPermsHook luck;
    private PlayerHeads heads;
    private final Cooldowns cooldowns = new Cooldowns();

    @Override
    public void onEnable() {
        // Must run first, before anything extracts a resource or reads the config: both bring
        // over data from older install layouts and would otherwise find the new files already
        // in place and skip themselves.
        FolderMigration.run(this);
        LegacyNameMigration.run(this);

        extractIfMissing("config.yml");
        for (String l : new String[]{"en", "es"}) {
            extractIfMissing("lang/" + l + ".yml");
        }

        settings = new Settings(this);
        settings.load();
        lang = new Lang(this);
        lang.load(settings.language);

        items = new Items(this);
        stats = new StatsManager(this);
        luck = new LuckPermsHook(this);
        heads = new PlayerHeads(this);
        manager = new ReviveManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new LootListener(this), this);
        pm.registerEvents(new StatsListener(this), this);

        ReviveCommand cmd = new ReviveCommand(this);
        if (getCommand("ultrarevive") != null) {
            getCommand("ultrarevive").setExecutor(cmd);
            getCommand("ultrarevive").setTabCompleter(cmd);
        }

        // One-time grave rescue: hands back whatever was left inside graves from before the grave
        // system was removed. If nothing is pending, the listener isn't even registered.
        GraveRescue graveRescue = new GraveRescue(this);
        graveRescue.importFromOldGraves();
        if (graveRescue.hasPending()) {
            getServer().getPluginManager().registerEvents(graveRescue, this);
        }

        pack = new ResourcePackManager(this);
        pack.start();

        manager.start();
        getLogger().info("UltraRevive v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
        }
        if (pack != null) {
            pack.stop();
        }
        if (stats != null) {
            stats.flush();
        }
    }

    /** Reloads config and languages (/ur reload). */
    public void reload() {
        settings.load();
        lang.load(settings.language);
        manager.applyConfig();
    }

    /** Extracts a jar resource only if it's missing, so a normal restart never logs a warning. */
    public void extractIfMissing(String path) {
        if (!new java.io.File(getDataFolder(), path).exists()) {
            saveResource(path, false);
        }
    }

    public Settings settings() {
        return settings;
    }

    public Lang lang() {
        return lang;
    }

    public Items items() {
        return items;
    }

    public ReviveManager manager() {
        return manager;
    }

    public ResourcePackManager pack() {
        return pack;
    }

    public StatsManager stats() {
        return stats;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public LuckPermsHook luck() {
        return luck;
    }

    public PlayerHeads heads() {
        return heads;
    }

    // PUBLIC API - consumed by other plugins BY REFLECTION.
    // Do not change these signatures without warning: they're called by name.

    /** Is that player DOWNED right now? */
    public boolean apiIsDowned(Player p) {
        return p != null && manager != null && manager.isDowned(p.getUniqueId());
    }

    /** Is someone reviving them right now? */
    public boolean apiIsReviving(Player p) {
        return p != null && manager != null && manager.isActivelyReviving(p.getUniqueId());
    }

    public boolean apiAvailable() {
        return true;
    }
}
