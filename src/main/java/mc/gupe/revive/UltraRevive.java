package mc.gupe.revive;

import mc.gupe.revive.commands.ReviveCommand;
import mc.gupe.revive.listeners.CombatListener;
import mc.gupe.revive.listeners.ConnectionListener;
import mc.gupe.revive.listeners.LootListener;
import mc.gupe.revive.listeners.RestrictionListener;
import org.bukkit.plugin.PluginManager;
import org.bukkit.plugin.java.JavaPlugin;

/** UltraRevive — no morís de una: quedás DERRIBADO y tu equipo puede levantarte. */
public final class UltraRevive extends JavaPlugin {

    /** Carpeta compartida de la familia Ultra. */
    private UltraView view;
    public UltraView view() { return view; }

    private Settings settings;
    private Lang lang;
    private Items items;
    private ReviveManager manager;
    private PackManager pack;
    private StatsManager stats;
    private LuckHook luck;
    private Heads heads;
    private final Cooldowns cooldowns = new Cooldowns();

    @Override
    public void onEnable() {
        // VA PRIMERO: crea plugins/UltraView/ y se trae lo que hubiera de una instalación anterior.
        // Cualquier extracción de recursos antes de esto escribiría en la carpeta equivocada.
        view = new UltraView(this);
        // Antes de extraer NADA del jar: si el config nuevo ya existiera, no habria donde
        // volcar los ajustes del viejo (el plugin se llamaba UltraRevive, con "r" al final,
        // asi que Bukkit le daba otra carpeta y el server arrancaria en blanco).
        Migration.run(this, view);

        view.saveResource("config.yml");
        for (String l : new String[]{"en", "es"}) {
            view.saveResource("lang/" + l + ".yml");
        }

        settings = new Settings(this);
        settings.load();
        lang = new Lang(this);
        lang.load(settings.language);

        items = new Items(this);  // los 3 tótems
        stats = new StatsManager(this);
        luck = new LuckHook(this);
        heads = new Heads(this);
        manager = new ReviveManager(this);

        PluginManager pm = getServer().getPluginManager();
        pm.registerEvents(new CombatListener(this), this);
        pm.registerEvents(new RestrictionListener(this), this);
        pm.registerEvents(new ConnectionListener(this), this);
        pm.registerEvents(new LootListener(this), this);
        pm.registerEvents(new mc.gupe.revive.listeners.StatsListener(this), this);

        ReviveCommand cmd = new ReviveCommand(this);
        for (String n : new String[]{"ultrarevive"}) {
            if (getCommand(n) != null) {
                getCommand(n).setExecutor(cmd);
                getCommand(n).setTabCompleter(cmd);
            }
        }

        // Rescate de tumbas: solo la primera vez, para devolverle a la gente lo que habia
        // quedado adentro cuando las tumbas salieron del plugin. Si no hay nada pendiente ni
        // siquiera se registra el listener.
        RescateTumbas rescate = new RescateTumbas(this);
        rescate.importar();
        if (rescate.hayPendientes()) getServer().getPluginManager().registerEvents(rescate, this);

        pack = new PackManager(this);
        pack.start();

        manager.start();
        getLogger().info("UltraRevive v" + getPluginMeta().getVersion() + " activado.");
    }

    @Override
    public void onDisable() {
        if (manager != null) manager.shutdown();
        if (pack != null) pack.stop();
        if (stats != null) stats.flush();
    }

    /** Recarga config e idiomas (/ur reload). */
    public void reload() {
        view.reload();
        settings.load();
        lang.load(settings.language);
        manager.applyConfig();
    }

    // ---- accesores ----
    public Settings settings() { return settings; }
    public Lang lang() { return lang; }
    public Items items() { return items; }
    public ReviveManager manager() { return manager; }
    public PackManager pack() { return pack; }
    public StatsManager stats() { return stats; }
    public Cooldowns cooldowns() { return cooldowns; }
    public LuckHook luck() { return luck; }
    public Heads heads() { return heads; }

    // API PÚBLICA — la consumen otros plugins POR REFLEXIÓN.
    // ⚠ No cambiar estas firmas sin avisar: se llaman por nombre.

    /** ¿Ese jugador está DERRIBADO ahora mismo? */
    public boolean apiIsDowned(org.bukkit.entity.Player p) {
        return p != null && manager != null && manager.isDowned(p.getUniqueId());
    }

    /** ¿Lo están levantando en este momento? */
    public boolean apiIsReviving(org.bukkit.entity.Player p) {
        return p != null && manager != null && manager.isActivelyReviving(p.getUniqueId());
    }

    public boolean apiAvailable() { return true; }
}
