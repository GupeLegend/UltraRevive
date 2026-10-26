package mc.gupe.revive;

import mc.gupe.revive.command.ReviveCommand;
import mc.gupe.revive.config.Lang;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.integration.FloodgateHook;
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
import mc.gupe.revive.pack.BedrockPack;
import mc.gupe.revive.pack.PackInstaller;
import mc.gupe.revive.revive.ReviveManager;
import mc.gupe.revive.stats.StatsManager;
import mc.gupe.revive.util.PlayerHeads;
import mc.gupe.revive.util.Tasks;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;

/** UltraRevive - you don't die outright: you go DOWNED and your team can pick you back up. */
public final class UltraRevive extends JavaPlugin {

    private Settings settings;
    private Lang lang;
    private Items items;
    private ReviveManager manager;
    private String packRebuild;
    private StatsManager stats;
    private LuckPermsHook luck;
    private FloodgateHook floodgate;
    private PlayerHeads heads;
    private final Cooldowns cooldowns = new Cooldowns();

    @Override
    public void onLoad() {
        // Before the pack plugins and Geyser start (plugin.yml: loadbefore), so they pick up the
        // textures on this same boot.
        try {
            // Only 'install' decides. Older configs still have resource-pack.enabled: false, which meant
            // "don't send the pack yourself", not "don't install it": ignored on purpose.
            YamlConfiguration cfg = bootConfig();
            packRebuild = PackInstaller.install(this, cfg.getString("resource-pack.install")).rebuild();
        } catch (IOException e) {
            getLogger().warning("Couldn't read config.yml: " + e.getMessage());
        }
        BedrockPack.install(this);
    }

    @Override
    public void onEnable() {
        // Must run first, before anything extracts a resource or reads the config: both bring
        // over data from older install layouts and would otherwise find the new files already
        // in place and skip themselves.
        Tasks.init(this);
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
        floodgate = new FloodgateHook(this);
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

        if (packRebuild != null) {
            // The pack plugin had already started (or it's ItemsAdder, which only builds on command):
            // once every plugin is up, ask it to rebuild with our textures.
            String rebuild = packRebuild;
            Tasks.global(() -> {
                getLogger().info("Rebuilding the server resource pack: /" + rebuild);
                getServer().dispatchCommand(getServer().getConsoleSender(), rebuild);
            });
            packRebuild = null;
        }

        getLogger().info("UltraRevive v" + getPluginMeta().getVersion() + " enabled.");
    }

    @Override
    public void onDisable() {
        if (manager != null) {
            manager.shutdown();
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

    /** config.yml as the server has it, with the jar's defaults underneath. Works in onLoad, before
     *  the file even exists. Read it with the one-argument getters: the others ignore the defaults. */
    public YamlConfiguration bootConfig() throws IOException {
        YamlConfiguration defaults;
        try (InputStream in = getResource("config.yml")) {
            defaults = YamlConfiguration.loadConfiguration(new InputStreamReader(in, StandardCharsets.UTF_8));
        }
        File f = new File(getDataFolder(), "config.yml");
        YamlConfiguration cfg = f.isFile() ? YamlConfiguration.loadConfiguration(f) : new YamlConfiguration();
        cfg.setDefaults(defaults);
        return cfg;
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

    public StatsManager stats() {
        return stats;
    }

    public Cooldowns cooldowns() {
        return cooldowns;
    }

    public LuckPermsHook luck() {
        return luck;
    }

    public FloodgateHook floodgate() {
        return floodgate;
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
