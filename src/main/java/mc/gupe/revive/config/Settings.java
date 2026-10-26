package mc.gupe.revive.config;

import mc.gupe.revive.UltraRevive;
import org.bukkit.Material;
import org.bukkit.NamespacedKey;
import org.bukkit.Particle;
import org.bukkit.Registry;
import org.bukkit.Sound;
import org.bukkit.boss.BarColor;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.file.FileConfiguration;

import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** Loads (and keeps in memory) every value from {@code config.yml}. Reloaded by {@code /ur reload}. */
public final class Settings {

    private final UltraRevive plugin;

    public String language;
    public List<String> enabledWorlds;

    public int downDurationTicks;
    public int reviveDurationTicks;
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
    public boolean affectOps;
    public int hungerCost;

    public boolean broadcastDowned;
    public int broadcastRadius;

    public Sound soundDowned;
    public Sound soundRevived;
    public Sound soundBledOut;
    public Sound soundSurrender;
    public boolean surrenderParticles;
    public int surrenderCooldownSecs;
    public boolean bedrockForms;

    public boolean statsEnabled;
    public boolean removeExternalOwnerHeads;

    // ---- cooperative revive + tension ----
    /** Ticks of grace before the revive progress starts to decay. */
    public int reviveGraceTicks;
    public boolean coopReviveEnabled;
    public double coopBonusPerExtra;
    public int coopMaxExtra;
    public boolean bleedTensionEnabled;
    public double bleedTensionThreshold;
    public Sound bleedHeartbeatSound;
    public boolean downedBeaconEnabled;
    public Particle downedBeaconParticle;
    public boolean interruptOnDamageEnabled;
    /** Fraction of the revive progress lost when the reviver is hit (1 = all of it). */
    public double interruptPenalty;
    public boolean downedFatigueEnabled;
    public int downedFatigueWindowSecs;
    public double downedFatigueFactor;
    public double downedFatigueMin;

    /** Visual definition of a custom item (material, name, lore, glow). */
    public static final class ItemDef {
        public Material material;
        public int customModelData;
        public boolean glow;
        /** Base key in {@code lang/}: the name and lore come from {@code <key>-name} / {@code <key>-lore}. */
        public String langKey;
    }

    // Blessed Totem of the Aether (self-revive).
    public ItemDef selfItem;
    public boolean selfItemEnabled;
    public double selfReviveHealth;
    public Sound selfReviveSound;
    public boolean selfSideEffects;

    // Cursed Totem of the Nether (speeds up / improves being revived).
    public ItemDef boostItem;
    public boolean boostItemEnabled;
    public double boostSpeedMultiplier;
    public double boostReviveHealth;
    public boolean boostRewardEffects;
    public boolean boostCurseInstaDeath;
    public int boostReviveWitherTicks;

    // Unknown Totem of the End (support / escape).
    public ItemDef endItem;
    public boolean endItemEnabled;
    public boolean endTeleportHome;
    public double endSelfReviveHealth;
    public int endBlindnessTicks;
    public double endMisfireChance;
    public int endMisfireRadius;
    public int endAreaRadius;
    public int endAreaHealTicks;
    public int endAreaHealLevel;
    public boolean endAreaTeamOnly;
    public boolean endAreaSlowFalling;
    public int endAreaSlowFallTicks;
    public int endReviverNauseaTicks;
    public Sound endSound;
    public Particle endAreaParticle;

    /** Where and how likely an item is to APPEAR in the world (chests + fishing). */
    public static final class LootDef {
        public boolean enabled;
        /** Loot table keyword -> its own chance (0.0-1.0). */
        public Map<String, Double> tables = new LinkedHashMap<>();
        /** Fallback chance for a table that doesn't have its own entry. */
        public double chance;
        public boolean fishing;
        public double fishingChance;
        /** End Totem only: require the chest to be inside an End ship. */
        public boolean requireEndShip;

        /** Chance for that loot table key, or 0 if none of the configured tables match. */
        public double chanceFor(String key) {
            for (Map.Entry<String, Double> e : tables.entrySet()) {
                if (e.getKey() != null && !e.getKey().isEmpty()
                        && key.contains(e.getKey().toLowerCase(Locale.ROOT))) {
                    return e.getValue() != null && e.getValue() > 0 ? e.getValue() : chance;
                }
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
        plugin.reloadConfig();
        FileConfiguration c = plugin.getConfig();

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
        affectOps = c.getBoolean("affect-ops", true);
        hungerCost = Math.max(0, c.getInt("hunger-cost", 6));

        broadcastDowned = c.getBoolean("broadcast-downed", true);
        broadcastRadius = Math.max(0, c.getInt("broadcast-radius", 30));

        soundDowned = parseSound(c.getString("sound-downed", "ENTITY_PLAYER_HURT"));
        soundRevived = parseSound(c.getString("sound-revived", "ENTITY_PLAYER_LEVELUP"));
        soundBledOut = parseSound(c.getString("sound-bled-out", "ENTITY_WITHER_DEATH"));
        soundSurrender = parseSound(c.getString("sound-surrender", "ITEM_TOTEM_USE"));
        surrenderParticles = c.getBoolean("surrender-particles", true);
        surrenderCooldownSecs = Math.max(0, c.getInt("surrender-cooldown", 0));
        bedrockForms = c.getBoolean("bedrock-forms", true);

        statsEnabled = c.getBoolean("stats", true);
        removeExternalOwnerHeads = c.getBoolean("remove-external-heads", true);

        reviveGraceTicks = Math.max(0, c.getInt("coop-revive.grace-seconds", 2)) * 20;
        coopReviveEnabled = c.getBoolean("coop-revive.enabled", true);
        coopBonusPerExtra = Math.max(0.0, c.getDouble("coop-revive.bonus-per-extra", 1.0));
        coopMaxExtra = Math.max(0, c.getInt("coop-revive.max-extra", 3));
        bleedTensionEnabled = c.getBoolean("bleed-tension.enabled", true);
        bleedTensionThreshold = clamp01(c.getDouble("bleed-tension.threshold-percent", 25.0) / 100.0);
        bleedHeartbeatSound = parseSound(c.getString("bleed-tension.heartbeat-sound", "ENTITY_WARDEN_HEARTBEAT"));
        downedBeaconEnabled = c.getBoolean("downed-beacon.enabled", true);
        downedBeaconParticle = parseParticle(c.getString("downed-beacon.particle", "END_ROD"));
        interruptOnDamageEnabled = c.getBoolean("interrupt-on-damage.enabled", false);
        interruptPenalty = clamp01(c.getDouble("interrupt-on-damage.penalty", 1.0));
        downedFatigueEnabled = c.getBoolean("downed-fatigue.enabled", false);
        downedFatigueWindowSecs = Math.max(1, c.getInt("downed-fatigue.window-seconds", 120));
        downedFatigueFactor = clamp01(c.getDouble("downed-fatigue.factor-per-stack", 0.75));
        downedFatigueMin = clamp01(c.getDouble("downed-fatigue.min-fraction", 0.3));

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

        // Where each totem comes from. The Aether totem drops from fishing and buried treasure
        // chests; the Nether one from ruined portals; the End one from end city chests.
        selfLoot = loadLootDef(c, "items.self-revive.loot",
                Collections.singletonList("buried_treasure"), 0.20, true, 0.005);
        boostLoot = loadLootDef(c, "items.revive-boost.loot",
                Arrays.asList("ruined_portal"), 0.02, false, 0.0);
        endLoot = loadLootDef(c, "items.end-revive.loot",
                Arrays.asList("end_city"), 0.00002, false, 0.0);
    }

    private LootDef loadLootDef(FileConfiguration c, String path, List<String> defTables,
                                 double defChance, boolean defFishing, double defFishChance) {
        LootDef d = new LootDef();
        d.enabled = c.getBoolean(path + ".enabled", true);
        d.chance = clamp01(c.getDouble(path + ".chance", defChance));
        // Either a map (chance per structure) or a plain list (same chance for all); both are accepted.
        ConfigurationSection sec = c.getConfigurationSection(path + ".tables");
        if (sec != null) {
            for (String k : sec.getKeys(false)) {
                d.tables.put(k.toLowerCase(Locale.ROOT), clamp01(sec.getDouble(k, d.chance)));
            }
        } else {
            List<String> list = c.getStringList(path + ".tables");
            if (list == null || list.isEmpty()) {
                list = defTables;
            }
            for (String t : list) {
                if (t != null && !t.isEmpty()) {
                    d.tables.put(t.toLowerCase(Locale.ROOT), d.chance);
                }
            }
        }
        d.requireEndShip = c.getBoolean(path + ".require-end-ship", false);
        d.fishing = c.getBoolean(path + ".fishing", defFishing);
        d.fishingChance = clamp01(c.getDouble(path + ".fishing-chance", defFishChance));
        return d;
    }

    private double clamp01(double v) {
        if (v < 0.0) {
            return 0.0;
        }
        return Math.min(v, 1.0);
    }

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
            if (m != null) {
                return m;
            }
        } catch (Exception ignored) {
            // Falls through to the warning below.
        }
        plugin.getLogger().warning("Invalid material: " + s + " (using " + def.name() + ")");
        return def;
    }

    public boolean worldEnabled(String worldName) {
        return enabledWorlds == null || enabledWorlds.isEmpty() || enabledWorlds.contains(worldName);
    }

    private double clampHealth(double v) {
        if (v < 1.0) {
            return 1.0;
        }
        return Math.min(v, 20.0);
    }

    private org.bukkit.ChatColor parseChatColor(String s) {
        try {
            return org.bukkit.ChatColor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid glow-color: " + s + " (using RED)");
            return org.bukkit.ChatColor.RED;
        }
    }

    private BarColor parseBarColor(String s) {
        try {
            return BarColor.valueOf(s.toUpperCase(Locale.ROOT));
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid bossbar-color: " + s + " (using RED)");
            return BarColor.RED;
        }
    }

    // Accepts the Bukkit name (ITEM_TOTEM_USE) or the game key (item.totem.use / minecraft:item.totem.use).
    // Sound.valueOf is marked for removal in 26.3, so names are looked up in the sound registry.
    // ENTITY_TOTEM_USE never existed in Bukkit; 26.1.2 configs used it, so it maps to the totem sound.
    private Sound parseSound(String s) {
        if (s == null || s.isBlank()) return null;
        String name = s.trim();
        if (name.equalsIgnoreCase("ENTITY_TOTEM_USE")) name = "ITEM_TOTEM_USE";
        Sound sound = null;
        if (name.contains(".") || name.contains(":")) {
            NamespacedKey nk = NamespacedKey.fromString(name.toLowerCase(Locale.ROOT));
            sound = nk == null ? null : Registry.SOUNDS.get(nk);
        } else {
            String wanted = name.toUpperCase(Locale.ROOT);
            for (Sound candidate : Registry.SOUNDS) {
                NamespacedKey nk = Registry.SOUNDS.getKey(candidate);
                if (nk != null && nk.getKey().replace('.', '_').toUpperCase(Locale.ROOT).equals(wanted)) {
                    sound = candidate;
                    break;
                }
            }
        }
        if (sound == null) plugin.getLogger().warning("Invalid sound: " + s);
        return sound;
    }

    private Particle parseParticle(String s) {
        try {
            return Particle.valueOf(s.toUpperCase(Locale.ROOT).trim());
        } catch (Exception e) {
            plugin.getLogger().warning("Invalid particle: " + s + " (using HEART)");
            return Particle.HEART;
        }
    }
}
