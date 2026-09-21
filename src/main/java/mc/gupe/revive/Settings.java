package mc.gupe.revive;

import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;

import java.io.File;
import java.util.List;
import java.util.Locale;

/**
 * Carga y guarda (en memoria) todos los valores de config.yml. Se vuelve a llamar a load() en
 * /grevive reload.
 */
public final class Settings {

    private final UltraRevive plugin;

    public String language;
    public List<String> enabledWorlds;

    public int downDurationTicks;  // ya convertido a ticks
    public int reviveDurationTicks;  // ya convertido a ticks
    public double reviveRadius;
    public boolean reviveBySneaking;

    public double downedHealth;
    public double reviveHealth;

    public boolean glow;
    public org.bukkit.ChatColor glowColor;
    public boolean poseCrawl;
    public boolean blindness;
    public int slownessAmplifier;
    public boolean particles;
    public boolean bossbar;
    public BarColor bossbarColor;

    public boolean finishByPlayers;
    public boolean immuneToMobs;
    public boolean dieOnQuit;
    public boolean adventureMode;
    public boolean grave;
    public boolean headDropIfNoGrave;
    public long graveDurationMillis;
    public boolean graveHologram;
    public boolean graveCapsule;  // encapsular la cabeza si mueres en agua/aire
    public Material graveCapsuleMaterial;  // bloque de la capsula
    public boolean graveKeyEnabled;  // la tumba solo se rompe con la Llave de Huesos
    public int graveKeyDurationMinutes;  // minutos antes de auto-expirar en modo LLAVE (cae loot sin cabeza)
    public int graveProtectMinutes;  // minutos de proteccion del dueno en modo SIN-llave
    public int hungerCost;

    public boolean broadcastDowned;
    public int broadcastRadius;

    public Sound soundDowned;
    public Sound soundRevived;
    public Sound soundBledOut;
    public Sound soundSurrender;
    public boolean surrenderParticles;

    // ---- 1.9.1: reanimacion cooperativa + tension ----
    public int reviveGraceTicks;  // ticks de gracia antes de que el progreso empiece a decaer
    public boolean coopReviveEnabled;  // varios companeros reaniman mas rapido
    public double coopBonusPerExtra;  // velocidad extra por cada reanimador adicional
    public int coopMaxExtra;  // tope de reanimadores extra que cuentan
    public boolean bleedTensionEnabled;  // escalada de tension en los ultimos segundos
    public double bleedTensionThreshold;  // 0..1 del tiempo restante para activar la tension
    public Sound bleedHeartbeatSound;  // latido de la tension
    public boolean downedBeaconEnabled;  // haz de particulas sobre el caido
    public boolean downedBeaconTeamOnly;  // el haz solo lo ven equipo/aliados
    public Particle downedBeaconParticle;
    public boolean downedTeamAlert;  // avisar al equipo con las coordenadas del caido
    public boolean interruptOnDamageEnabled;  // recibir dano interrumpe tu reanimacion
    public double interruptPenalty;  // fraccion del progreso que se pierde al ser golpeado (1 = todo)
    public boolean downedFatigueEnabled;  // derribos repetidos acortan el tiempo de aguante
    public int downedFatigueWindowSecs;  // ventana para acumular derribos
    public double downedFatigueFactor;  // multiplicador de duracion por stack (0.75 = -25% cada vez)
    public double downedFatigueMin;  // fraccion minima a la que puede bajar

    // ---- 1.5: comportamiento general ----
    public boolean affectOps;  // los OP/admins TAMBIEN se derriban (ignora su bypass)
    public int backupsMaxPerPlayer;  // backups guardados por jugador y por grupo
    public int backupsRetentionDays;  // dias antes de que un backup se borre solo (0 = nunca)
    public boolean removeExternalOwnerHeads;  // quita del loot las cabezas del propio jugador (anti doble-cabeza)
    public int surrenderCooldownSecs;  // segundos que debes esperar (derribado) antes de poder rendirte
    public boolean statsEnabled;  // guardar estadisticas por jugador

    // ---- 1.5: Homes ----
    public boolean homesEnabled;
    public int maxHomes;
    public boolean backOnDeath;
    public long homeCooldownMs;  // cooldown de /home (ms)
    public int homeWarmupSeconds;  // segundos de calentamiento de /home (0 = instantaneo)

    // ---- 1.7.3: TPA (se cargan desde tpa/config.yml) ----
    public boolean tpaEnabled;
    public int tpaWarmupSeconds;  // segundos de calentamiento antes de teletransportar (0 = ya)
    public int tpaTimeoutSeconds;  // segundos que dura una solicitud antes de expirar
    public long tpaCooldownMs;  // espera entre solicitudes

    // ---- 1.5: Equipos (se cargan desde teams/config.yml) ----
    public boolean teamsReviveRestriction;  // solo equipo/aliados pueden revivir
    public boolean teamsAlliesRevive;  // los aliados tambien pueden revivir
    public int teamsMaxMembers;
    public int teamsEchestRows;  // filas del cofre grupal (1-6)
    public boolean teamsWarpEnabled;  // permitir /tm setwarp y /tm warp
    public boolean teamsWarIgnoresSafezone;  // los clanes en guerra se dañan incluso en zona segura
    public long teamsWarpCooldownMs;  // cooldown de /tm warp (ms)
    public List<String> teamsBlockedWords;  // filtro de palabras (nombre/tag/descripcion)

    // ---- Items custom ----
    /** Definicion visual de un item (material, nombre, lore, brillo). */
    public static final class ItemDef {
        public Material material;
        public int customModelData;
        public boolean glow;
        /** Clave base en lang/: de ahi salen <clave>-name y <clave>-lore. */
        public String langKey;
    }

    // Totem de los Dioses (auto-revivirse).
    public ItemDef selfItem;
    public boolean selfItemEnabled;
    public double selfReviveHealth;
    public Sound selfReviveSound;
    public boolean selfSideEffects;  // mareo 20s + efecto de manzana dorada al auto-revivir

    // Totem Maldito del Nether (acelera / mejora el revivir).
    public ItemDef boostItem;
    public boolean boostItemEnabled;
    public double boostSpeedMultiplier;
    public double boostReviveHealth;
    public boolean boostRewardEffects;
    public boolean boostCurseInstaDeath;  // morir llevandolo en el inventario -> insta-muerte
    public int boostReviveWitherTicks;  // el revivido recibe Wither (veneno de wither skeleton) X seg

    // Totem Desconocido del End (soporte/escape).
    public ItemDef endItem;
    public boolean endItemEnabled;
    public boolean endTeleportHome;  // auto-revivirte te teletransporta a tu home
    public double endSelfReviveHealth;
    public int endBlindnessTicks;  // ceguera al auto-revivir (viaje del End)
    public double endMisfireChance;          // twist "Desconocido": prob. de fallar el destino (0 = off)
    public int endMisfireRadius;
    public int endAreaRadius;  // radio del area de sanacion al revivir a otro
    public int endAreaHealTicks;
    public int endAreaHealLevel;  // amplificador de Regeneracion (0 = Regen I)
    public boolean endAreaTeamOnly;  // true = solo revivido + su equipo/aliados
    public boolean endAreaSlowFalling;
    public int endAreaSlowFallTicks;
    public int endReviverNauseaTicks;  // nausea al que revive (agotamiento)
    public Sound endSound;
    public Particle endAreaParticle;  // particula del disco de la zona de sanacion

    /** Donde y con que probabilidad APARECE un item (cofres + pesca). */
    public static final class LootDef {
        public boolean enabled;
        /** palabra clave de la loot table -> probabilidad propia (0.0 - 1.0). */
        public java.util.Map<String, Double> tables = new java.util.LinkedHashMap<>();
        public double chance;  // se usa si la tabla no trae la suya
        public boolean fishing;
        public double fishingChance;  // 1.0
        /** Solo para el Totem del End: exigir que el cofre este en un BARCO del End. */
        public boolean requireEndShip;

        /** Probabilidad para esa loot table, o 0 si no aplica. */
        public double chanceFor(String key) {
            for (java.util.Map.Entry<String, Double> e : tables.entrySet()) {
                if (e.getKey() != null && !e.getKey().isEmpty()
                        && key.contains(e.getKey().toLowerCase(java.util.Locale.ROOT)))
                    return e.getValue() != null && e.getValue() > 0 ? e.getValue() : chance;
            }
            return 0;
        }
    }
    public LootDef selfLoot;
    public LootDef boostLoot;
    public LootDef endLoot;

    public Settings(UltraRevive plugin) {
        this.plugin = plugin;
    }

    public void load() {
        plugin.view().reload();
        FileConfiguration c = plugin.view().config();

        language = c.getString("language", "en").toLowerCase(Locale.ROOT);
        enabledWorlds = c.getStringList("enabled-worlds");

        downDurationTicks = Math.max(1, c.getInt("down-duration", 60)) * 20;
        reviveDurationTicks = Math.max(1, c.getInt("revive-duration", 5)) * 20;
        reviveRadius = c.getDouble("revive-radius", 3.0);
        reviveBySneaking = c.getBoolean("revive-by-sneaking", true);

        downedHealth = clampHealth(c.getDouble("downed-health", 2.0));
        reviveHealth = clampHealth(c.getDouble("revive-health", 10.0));

        glow = c.getBoolean("glow", true);
        glowColor = parseChatColor(c.getString("glow-color", "RED"));
        poseCrawl = c.getBoolean("pose-crawl", true);
        blindness = c.getBoolean("blindness", false);
        slownessAmplifier = c.getInt("slowness-amplifier", 6);
        particles = c.getBoolean("particles", true);
        bossbar = c.getBoolean("bossbar", true);
        bossbarColor = parseBarColor(c.getString("bossbar-color", "RED"));

        finishByPlayers = c.getBoolean("finish-by-players", true);
        immuneToMobs = c.getBoolean("immune-to-mobs", true);
        dieOnQuit = c.getBoolean("die-on-quit", true);
        adventureMode = c.getBoolean("adventure-mode", true);
        grave = c.getBoolean("grave", true);
        headDropIfNoGrave = c.getBoolean("head-drop-if-no-grave", true);
        graveDurationMillis = Math.max(0L, c.getLong("grave-duration-minutes", 0)) * 60_000L;
        graveHologram = c.getBoolean("grave-hologram", true);
        graveCapsule = c.getBoolean("grave-capsule", true);
        graveCapsuleMaterial = parseMaterial(c.getString("grave-capsule-material", "WHITE_STAINED_GLASS"), Material.WHITE_STAINED_GLASS);
        graveKeyEnabled = c.getBoolean("grave-key.enabled", false);
        graveKeyDurationMinutes = Math.max(0, c.getInt("grave-key.duration-minutes", 5));  // min antes de que la tumba expire (0 = nunca)
        graveProtectMinutes = Math.max(0, c.getInt("grave-protect-minutes", 1));
        hungerCost = Math.max(0, c.getInt("hunger-cost", 6));

        broadcastDowned = c.getBoolean("broadcast-downed", true);
        broadcastRadius = Math.max(0, c.getInt("broadcast-radius", 30));

        soundDowned = parseSound(c.getString("sound-downed", "ENTITY_PLAYER_HURT"));
        soundRevived = parseSound(c.getString("sound-revived", "ENTITY_PLAYER_LEVELUP"));
        soundBledOut = parseSound(c.getString("sound-bled-out", "ENTITY_WITHER_DEATH"));
        soundSurrender = parseSound(c.getString("sound-surrender", "ENTITY_TOTEM_USE"));
        surrenderParticles = c.getBoolean("surrender-particles", true);

        affectOps = c.getBoolean("affect-ops", true);
        removeExternalOwnerHeads = c.getBoolean("remove-external-heads", true);
        surrenderCooldownSecs = Math.max(0, c.getInt("surrender-cooldown", 0));
        statsEnabled = c.getBoolean("stats", true);

        // ---- 2.0.1: panel de staff / backups ----
        backupsMaxPerPlayer = Math.max(1, c.getInt("admin.backups.max-per-player", 30));
        backupsRetentionDays = Math.max(0, c.getInt("admin.backups.retention-days", 7));

        // ---- 1.9.1: reanimacion cooperativa + tension ----
        reviveGraceTicks = Math.max(0, c.getInt("coop-revive.grace-seconds", 2)) * 20;
        coopReviveEnabled = c.getBoolean("coop-revive.enabled", true);
        coopBonusPerExtra = Math.max(0.0, c.getDouble("coop-revive.bonus-per-extra", 1.0));
        coopMaxExtra = Math.max(0, c.getInt("coop-revive.max-extra", 3));
        bleedTensionEnabled = c.getBoolean("bleed-tension.enabled", true);
        bleedTensionThreshold = clamp01(c.getDouble("bleed-tension.threshold-percent", 25.0) / 100.0);
        bleedHeartbeatSound = parseSound(c.getString("bleed-tension.heartbeat-sound", "ENTITY_WARDEN_HEARTBEAT"));
        downedBeaconEnabled = c.getBoolean("downed-beacon.enabled", true);
        downedBeaconTeamOnly = c.getBoolean("downed-beacon.team-only", false);
        downedBeaconParticle = parseParticle(c.getString("downed-beacon.particle", "END_ROD"));
        downedTeamAlert = c.getBoolean("downed-beacon.team-alert", true);
        interruptOnDamageEnabled = c.getBoolean("interrupt-on-damage.enabled", false);
        interruptPenalty = clamp01(c.getDouble("interrupt-on-damage.penalty", 1.0));
        downedFatigueEnabled = c.getBoolean("downed-fatigue.enabled", false);
        downedFatigueWindowSecs = Math.max(1, c.getInt("downed-fatigue.window-seconds", 120));
        downedFatigueFactor = clamp01(c.getDouble("downed-fatigue.factor-per-stack", 0.75));
        downedFatigueMin = clamp01(c.getDouble("downed-fatigue.min-fraction", 0.3));

        // ---- Items custom (base TOTEM_OF_UNDYING + custom-model-data) ----
        selfItem = loadItemDef(c, "items.self-revive", Material.TOTEM_OF_UNDYING, "item-self");
        selfItemEnabled = c.getBoolean("items.self-revive.enabled", true);
        selfReviveHealth = clampHealth(c.getDouble("items.self-revive.health", 6.0));
        selfReviveSound = parseSound(c.getString("items.self-revive.sound", "ITEM_TOTEM_USE"));
        selfSideEffects = c.getBoolean("items.self-revive.side-effects", true);

        boostItem = loadItemDef(c, "items.revive-boost", Material.TOTEM_OF_UNDYING, "item-boost");
        boostItemEnabled = c.getBoolean("items.revive-boost.enabled", true);
        boostSpeedMultiplier = Math.max(1.0, c.getDouble("items.revive-boost.speed-multiplier", 3.0));
        boostReviveHealth = clampHealth(c.getDouble("items.revive-boost.revive-health", 20.0));
        boostRewardEffects = c.getBoolean("items.revive-boost.reward-effects", true);
        boostCurseInstaDeath = c.getBoolean("items.revive-boost.curse-insta-death", true);
        boostReviveWitherTicks = Math.max(0, c.getInt("items.revive-boost.curse-wither-seconds", 20)) * 20;

        endItem = loadItemDef(c, "items.end-revive", Material.TOTEM_OF_UNDYING, "item-end");
        endItemEnabled = c.getBoolean("items.end-revive.enabled", true);
        endTeleportHome = c.getBoolean("items.end-revive.teleport-home", true);
        endSelfReviveHealth = clampHealth(c.getDouble("items.end-revive.self-revive-health", 6.0));
        endBlindnessTicks = Math.max(0, c.getInt("items.end-revive.blindness-seconds", 120)) * 20;
        endMisfireChance = clamp01(c.getDouble("items.end-revive.unknown-misfire-chance", 0.0));
        endMisfireRadius = Math.max(1, c.getInt("items.end-revive.unknown-misfire-radius", 24));
        endAreaRadius = Math.max(1, c.getInt("items.end-revive.area-heal-radius", 5));
        endAreaHealTicks = Math.max(1, c.getInt("items.end-revive.area-heal-duration", 6)) * 20;
        endAreaHealLevel = Math.max(1, c.getInt("items.end-revive.area-heal-level", 1)) - 1;
        endAreaTeamOnly = c.getBoolean("items.end-revive.area-heal-team-only", true);
        endAreaSlowFalling = c.getBoolean("items.end-revive.area-slow-falling", true);
        endAreaSlowFallTicks = Math.max(0, c.getInt("items.end-revive.area-slow-falling-seconds", 4)) * 20;
        endReviverNauseaTicks = Math.max(0, c.getInt("items.end-revive.reviver-nausea-seconds", 20)) * 20;
        endSound = parseSound(c.getString("items.end-revive.sound", "ENTITY_ENDERMAN_TELEPORT"));
        endAreaParticle = parseParticle(c.getString("items.end-revive.area-particle", "HEART"));

        // De donde sale cada item. El Totem de los Dioses se PESCA; el del Nether
        // sale en cofres de PORTALES EN RUINAS; el del End en CIUDADES DEL END.
        selfLoot = loadLootDef(c, "items.self-revive.loot",
                java.util.Collections.emptyList(), 0.0, true, 0.02);
        boostLoot = loadLootDef(c, "items.revive-boost.loot",
                java.util.Arrays.asList("ruined_portal"), 0.02, false, 0.0);
        endLoot = loadLootDef(c, "items.end-revive.loot",
                java.util.Arrays.asList("end_city"), 0.00002, false, 0.0);
    }

    private LootDef loadLootDef(FileConfiguration c, String path, List<String> defTables,
                                double defChance, boolean defFishing, double defFishChance) {
        LootDef d = new LootDef();
        d.enabled = c.getBoolean(path + ".enabled", true);
        d.chance = clamp01(c.getDouble(path + ".chance", defChance));
        // Mapa (chance por estructura) o lista (todas con la misma). Se acepta cualquiera.
        org.bukkit.configuration.ConfigurationSection sec = c.getConfigurationSection(path + ".tables");
        if (sec != null) {
            for (String k : sec.getKeys(false)) d.tables.put(k.toLowerCase(java.util.Locale.ROOT),
                    clamp01(sec.getDouble(k, d.chance)));
        } else {
            List<String> lista = c.getStringList(path + ".tables");
            if (lista == null || lista.isEmpty()) lista = defTables;
            for (String t : lista) if (t != null && !t.isEmpty())
                d.tables.put(t.toLowerCase(java.util.Locale.ROOT), d.chance);
        }
        d.requireEndShip = c.getBoolean(path + ".require-end-ship", false);
        d.fishing = c.getBoolean(path + ".fishing", defFishing);
        d.fishingChance = clamp01(c.getDouble(path + ".fishing-chance", defFishChance));
        return d;
    }

    private double clamp01(double v) {
        if (v < 0.0) return 0.0;
        return Math.min(v, 1.0);
    }

    /** Carga la parte visual de un item desde la config. */
    private ItemDef loadItemDef(FileConfiguration c, String path, Material def, String langKey) {
        ItemDef d = new ItemDef();
        d.material = parseMaterial(c.getString(path + ".material", def.name()), def);
        d.customModelData = c.getInt(path + ".custom-model-data", 0);
        d.glow = c.getBoolean(path + ".glow", true);
        d.langKey = langKey;
        return d;
    }

    private Material parseMaterial(String s, Material def) {
        try {
            Material m = Material.matchMaterial(s);
            if (m != null) return m;
        } catch (Exception ignored) {}
        plugin.getLogger().warning("Material invalido: " + s + " (uso " + def.name() + ")");
        return def;
    }

    public boolean worldEnabled(String worldName) {
        return enabledWorlds == null || enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    /** ¿El texto contiene alguna palabra prohibida (filtro de equipos)? Insensible a mayusculas. */
    public boolean hasBlockedWord(String s) {
        if (s == null || teamsBlockedWords == null) return false;
        String low = s.toLowerCase(Locale.ROOT);
        for (String w : teamsBlockedWords)
            if (w != null && !w.isEmpty() && low.contains(w.toLowerCase(Locale.ROOT))) return true;
        return false;
    }

    // ---------- helpers de parseo seguros ----------

    private double clampHealth(double v) {
        if (v < 1.0) return 1.0;
        return Math.min(v, 20.0);
    }

    private org.bukkit.ChatColor parseChatColor(String s) {
        try {
            return org.bukkit.ChatColor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("glow-color invalido: " + s + " (uso RED)");
            return org.bukkit.ChatColor.RED;
        }
    }

    private BarColor parseBarColor(String s) {
        try {
            return BarColor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("bossbar-color invalido: " + s + " (uso RED)");
            return BarColor.RED;
        }
    }

    private Sound parseSound(String s) {
        try {
            return Sound.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("Sonido invalido: " + s);
            return null;
        }
    }

    private Particle parseParticle(String s) {
        try {
            return Particle.valueOf(s.toUpperCase(Locale.ROOT).trim());
        } catch (Exception e) {
            plugin.getLogger().warning("Particula invalida: " + s + " (uso HEART)");
            return Particle.HEART;
        }
    }
}
