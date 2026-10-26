package mc.gupe.revive.revive;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.api.PlayerDownedEvent;
import mc.gupe.revive.api.PlayerRevivedEvent;
import mc.gupe.revive.api.UltraReviveAPI;
import mc.gupe.revive.config.Lang;
import mc.gupe.revive.config.Settings;
import mc.gupe.revive.item.Items;
import mc.gupe.revive.model.Downed;
import mc.gupe.revive.util.Tasks;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.event.ClickEvent;
import net.kyori.adventure.text.event.HoverEvent;
import net.kyori.adventure.text.serializer.legacy.LegacyComponentSerializer;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Location;
import org.bukkit.Particle;
import org.bukkit.Sound;
import org.bukkit.boss.BarStyle;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.event.Event;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Core of the plugin: downed players, their per-tick loop, reviving and bleeding out.
 *
 * <p>Each downed player ticks on their own entity scheduler (see {@link Tasks}), so on Folia it runs
 * on the thread of the region they're in, and the revivers are the players next to them. That's why
 * the shared maps are concurrent.
 */
public final class ReviveManager {

    private static final String TEAM = "ultra_downed";

    private final UltraRevive plugin;
    private final Settings cfg;
    private final Lang lang;

    private final Map<UUID, Downed> downedMap = new ConcurrentHashMap<>();
    /** Who is reviving whom right now (reviver -> downed). Each downed player rebuilds theirs every tick. */
    private final Map<UUID, UUID> revivingNow = new ConcurrentHashMap<>();
    private final Map<UUID, Long> lastDownAt = new ConcurrentHashMap<>();
    private final Map<UUID, Integer> fatigueStacks = new ConcurrentHashMap<>();
    /** Players already warned about, so the watchdog only logs once per occurrence. */
    private final Set<UUID> watchdogWarned = ConcurrentHashMap.newKeySet();
    /** Players whose death is already scheduled for next tick. */
    private final Set<UUID> dying = ConcurrentHashMap.newKeySet();
    private boolean teams = true;

    public ReviveManager(UltraRevive plugin) {
        this.plugin = plugin;
        this.cfg = plugin.settings();
        this.lang = plugin.lang();
        setupTeam();
    }

    /** Creates (or reuses) the scoreboard team that gives the glow its color. */
    private void setupTeam() {
        Scoreboard board = mainScoreboard();
        if (board == null) {
            return;
        }
        try {
            Team team = board.getTeam(TEAM);
            if (team == null) {
                team = board.registerNewTeam(TEAM);
            }
            team.setColor(cfg.glowColor);
            team.setCanSeeFriendlyInvisibles(false);
        } catch (UnsupportedOperationException e) {
            teams = false;  // Folia has no scoreboard teams
        }
    }

    /** The glow-color team, or null on servers without scoreboard teams (Folia): the glow is then white. */
    private Team glowTeam() {
        Scoreboard board = teams ? mainScoreboard() : null;
        return board == null ? null : board.getTeam(TEAM);
    }

    private static Scoreboard mainScoreboard() {
        try {
            return Bukkit.getScoreboardManager().getMainScoreboard();
        } catch (UnsupportedOperationException | NullPointerException e) {
            return null;
        }
    }

    /** Re-applies settings that can change on /ur reload (e.g. the glow color). */
    public void applyConfig() {
        setupTeam();
    }

    public boolean isDowned(UUID id) {
        return downedMap.containsKey(id);
    }

    public void down(Player p) {
        if (isDowned(p.getUniqueId())) {
            return;
        }

        int downDur = cfg.downDurationTicks;
        if (cfg.downedFatigueEnabled) {
            long now = System.currentTimeMillis();
            pruneFatigue(now);
            Long last = lastDownAt.get(p.getUniqueId());
            int stacks = 0;
            if (last != null && now - last <= cfg.downedFatigueWindowSecs * 1000L) {
                stacks = fatigueStacks.getOrDefault(p.getUniqueId(), 0) + 1;
            }
            fatigueStacks.put(p.getUniqueId(), stacks);
            lastDownAt.put(p.getUniqueId(), now);
            double mult = Math.max(cfg.downedFatigueMin, Math.pow(cfg.downedFatigueFactor, stacks));
            downDur = Math.max(20, (int) Math.round(downDur * mult));
        }

        Downed d = new Downed(p, downDur);
        downedMap.put(p.getUniqueId(), d);
        d.task = Tasks.timer(p, 1, 1, t -> tick(d));

        p.setHealth(Math.min(cfg.downedHealth, p.getMaxHealth()));
        p.setFoodLevel(Math.max(p.getFoodLevel(), 6));

        if (cfg.adventureMode) {
            p.setGameMode(GameMode.ADVENTURE);
        }
        applyDownPose(p);
        // Also one tick later: switching game mode resets the client-side pose, and since we set
        // it with fixed=true the server still reports SWIMMING, so the next tick's check never
        // notices the client is standing again.
        Tasks.of(p, 1, () -> {
            if (isDowned(p.getUniqueId())) {
                applyDownPose(p);
            }
        });

        if (cfg.glow) {
            Team team = glowTeam();
            if (team != null) {
                team.addEntry(p.getName());
            }
            p.setGlowing(true);
        }

        int dur = d.totalTicks + 40;
        if (cfg.slownessAmplifier >= 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, cfg.slownessAmplifier, true, false, false));
        }
        if (cfg.blindness) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur, 0, true, false, false));
        }

        if (cfg.bossbar) {
            d.bar = Bukkit.createBossBar(lang.get("downed-bossbar", "{time}", secs(d.ticksLeft)),
                    cfg.bossbarColor, BarStyle.SOLID);
            d.bar.addPlayer(p);
        }

        lang.title(p, "downed-title", "downed-subtitle");
        playSound(p.getLocation(), cfg.soundDowned);
        if (cfg.broadcastDowned) {
            broadcast(p, "broadcast-downed");
        }
        sendDownedPrompts(p);
        plugin.stats().addDowned(p.getUniqueId());

        // Marks the state and notifies extensions last, once the down state (pose, glow, bossbar)
        // is fully applied, so anything listening sees the final state.
        UltraReviveAPI.markDowned(plugin, p, true);
        fire(new PlayerDownedEvent(p, downDur));
    }

    /** Fires an API event without letting a broken listener disrupt the down/revive flow. */
    private void fire(Event e) {
        try {
            Bukkit.getPluginManager().callEvent(e);
        } catch (Throwable t) {
            plugin.getLogger().warning("A listener of " + e.getEventName() + " failed: " + t);
        }
    }

    private void fireRevived(Player p, Player reviver, PlayerRevivedEvent.Cause cause) {
        fire(new PlayerRevivedEvent(p, reviver, cause));
    }

    /** Clickable chat prompts: surrender and (if enabled) self-revive. Bedrock gets a form instead. */
    private void sendDownedPrompts(Player p) {
        if (cfg.bedrockForms && plugin.floodgate().isBedrock(p)) {
            p.sendMessage(lang.pre("bedrock-form-hint"));
            // a bit later, so the downed title is seen before the form covers the screen
            Tasks.of(p, 30, () -> openBedrockForm(p));
            return;
        }
        Component surrenderPrompt = legacy(lang.get("surrender-prompt"));
        Component surrenderButton = legacy(lang.get("surrender-button"))
                .clickEvent(ClickEvent.runCommand("/ultrarevive surrender"))
                .hoverEvent(HoverEvent.showText(legacy(lang.get("surrender-hover"))));
        p.sendMessage(surrenderPrompt.append(surrenderButton));

        if (cfg.selfItemEnabled || cfg.endItemEnabled) {
            Component revivePrompt = legacy(lang.get("selfrevive-prompt"));
            Component reviveButton = legacy(lang.get("selfrevive-button"))
                    .clickEvent(ClickEvent.runCommand("/ultrarevive selfrevive"))
                    .hoverEvent(HoverEvent.showText(legacy(lang.get("selfrevive-hover"))));
            p.sendMessage(revivePrompt.append(reviveButton));
        }
    }

    /** Opens (or reopens, on a double sneak) the downed menu of a Bedrock player. */
    public void openBedrockForm(Player p) {
        if (p.isOnline() && isDowned(p.getUniqueId())) {
            plugin.floodgate().sendDownedForm(p, cfg.selfItemEnabled || cfg.endItemEnabled);
        }
    }

    private Component legacy(String coloredString) {
        return LegacyComponentSerializer.legacySection().deserialize(coloredString);
    }

    /** Prone pose, with a fallback for older API versions (setSwimming instead of setPose). */
    private void applyDownPose(Player p) {
        if (!cfg.poseCrawl) {
            return;
        }
        try {
            p.setPose(Pose.SWIMMING, true);
        } catch (Throwable t) {
            try {
                p.setSwimming(true);
            } catch (Throwable ignored) {
            }
        }
    }

    public void revive(Downed d, Player reviver) {
        revive(d, reviver, false);
    }

    /** Revives the player. If {@code boosted} (Nether totem), they come back with more health and effects. */
    public void revive(Downed d, Player reviver, boolean boosted) {
        cleanup(d);
        Player p = d.player;
        double hp = boosted ? cfg.boostReviveHealth : cfg.reviveHealth;
        p.setHealth(Math.min(hp, p.getMaxHealth()));

        if (boosted && cfg.boostRewardEffects) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0, true, false, false));
        }
        // Downside of the Cursed Totem of the Nether: the REVIVER gets Wither, not the downed player.
        if (boosted && reviver != null && cfg.boostReviveWitherTicks > 0) {
            reviver.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, cfg.boostReviveWitherTicks, 0, true, true, true));
        }

        lang.actionbar(p, "revived");
        lang.chat(p, "revived");
        playSound(p.getLocation(), cfg.soundRevived);
        if (cfg.broadcastDowned) {
            broadcast(p, "broadcast-revived");
        }

        if (reviver != null) {
            lang.chat(reviver, "reviver-success", "{target}", p.getName());
            if (cfg.hungerCost > 0) {
                reviver.setFoodLevel(Math.max(0, reviver.getFoodLevel() - cfg.hungerCost));
            }
            plugin.stats().addRevivedOther(reviver.getUniqueId());

            // Unknown Totem of the End: if the REVIVER carries it, it creates a healing area
            // (heals the revived player and anyone nearby) and gives the reviver nausea. Consumed.
            if (cfg.endItemEnabled && plugin.items().hasInInventory(reviver, Items.END)) {
                plugin.items().consumeFromInventory(reviver, Items.END);
                createHealingArea(p, reviver);
            }
        }
        plugin.stats().addRevived(p.getUniqueId());
        fireRevived(p, reviver, PlayerRevivedEvent.Cause.REVIVED);
    }

    /**
     * End Totem's healing area: a disc of red particles on the ground that heals (regeneration +
     * slow falling) anyone standing in it, for a few seconds.
     */
    private void createHealingArea(Player center, Player reviver) {
        org.bukkit.World w = center.getWorld();
        Location loc = center.getLocation().clone();
        double r = cfg.endAreaRadius;
        UUID centerId = center.getUniqueId();

        if (cfg.endReviverNauseaTicks > 0) {
            reviver.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    cfg.endReviverNauseaTicks, 0, true, true, true));
        }
        try {
            if (cfg.endSound != null) {
                w.playSound(loc, cfg.endSound, 1f, 1.2f);
            }
        } catch (Throwable ignored) {
        }

        int period = 12;
        int totalTicks = Math.max(period, cfg.endAreaHealTicks);
        int[] elapsed = {0};
        Tasks.timer(loc, 1, period, task -> {
            renderHealZone(w, loc, r);
            for (Player near : loc.getNearbyPlayers(r)) {
                if (cfg.endAreaTeamOnly && !near.getUniqueId().equals(centerId)
                        && !teamOrAlly(centerId, near.getUniqueId())) {
                    continue;
                }
                near.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        period + 20, cfg.endAreaHealLevel, true, true, true));
                if (cfg.endAreaSlowFalling && cfg.endAreaSlowFallTicks > 0) {
                    near.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                            cfg.endAreaSlowFallTicks, 0, true, false, false));
                }
            }
            elapsed[0] += period;
            if (elapsed[0] >= totalTicks) {
                task.cancel();
            }
        });
    }

    /** Filled particle disc on the ground that marks the healing area. */
    private void renderHealZone(org.bukkit.World w, Location center, double r) {
        double y = center.getY() + 0.12;
        for (double rad = 0.4; rad <= r; rad += 0.6) {
            int points = Math.max(6, (int) (rad * 7));
            for (int i = 0; i < points; i++) {
                double ang = 2 * Math.PI * i / points;
                double x = center.getX() + Math.cos(ang) * rad;
                double z = center.getZ() + Math.sin(ang) * rad;
                Location p = new Location(w, x, y, z);
                try {
                    if (cfg.endAreaParticle == Particle.DUST) {
                        w.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05,
                                new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                    } else {
                        w.spawnParticle(cfg.endAreaParticle, p, 1, 0.05, 0.05, 0.05, 0);
                    }
                } catch (Throwable ignored) {
                }
            }
        }
    }

    /**
     * Whether {@code b} should be healed in the End Totem's area around {@code a}. Always true:
     * team/ally filtering is handled by a separate clans plugin, not this one.
     */
    private boolean teamOrAlly(UUID a, UUID b) {
        return true;
    }

    /** Self-revive with the totem (no teammate needed). Totem-of-undying-style effect. */
    public void selfRevive(Downed d) {
        cleanup(d);
        Player p = d.player;
        p.setHealth(Math.min(cfg.selfReviveHealth, p.getMaxHealth()));

        playSound(p.getLocation(), cfg.selfReviveSound);
        try {
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.4);
        } catch (Throwable ignored) {
        }

        // Blessed Totem of the Aether side effects: golden-apple-style buffs, plus nausea.
        if (cfg.selfSideEffects) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0, true, true, true));
        }

        lang.actionbar(p, "self-revived");
        lang.chat(p, "self-revived");
        if (cfg.broadcastDowned) {
            broadcast(p, "broadcast-self-revived");
        }
        plugin.stats().addSelfRevived(p.getUniqueId());
        fireRevived(p, null, PlayerRevivedEvent.Cause.SELF_REVIVED);
    }

    /** Self-revive with the End Totem: revives and teleports home (an escape from the void). */
    public void endSelfRevive(Downed d) {
        Player p = d.player;
        Location dest = null;
        if (cfg.endTeleportHome) {
            // Without a homes plugin, "home" falls back to the bed/respawn point, then world spawn.
            dest = p.getRespawnLocation() != null ? p.getRespawnLocation() : p.getWorld().getSpawnLocation();
        }
        boolean misfired = false;
        if (dest != null && cfg.endMisfireChance > 0 && Math.random() < cfg.endMisfireChance) {
            misfired = true;
            int r = cfg.endMisfireRadius;
            Location base = dest;
            int x = base.getBlockX() + (int) ((Math.random() * 2 - 1) * r);
            int z = base.getBlockZ() + (int) ((Math.random() * 2 - 1) * r);
            int y = base.getWorld().getHighestBlockYAt(x, z) + 1;
            dest = new Location(base.getWorld(), x + 0.5, y, z + 0.5);
        }

        cleanup(d);
        p.setHealth(Math.min(cfg.endSelfReviveHealth, p.getMaxHealth()));

        try {
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.4);
            if (cfg.endSound != null) {
                p.playSound(p.getLocation(), cfg.endSound, 1f, 1f);
            }
        } catch (Throwable ignored) {
        }

        if (dest != null) {
            p.teleportAsync(dest);
            try {
                p.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.6);
                if (cfg.endSound != null) {
                    p.playSound(dest, cfg.endSound, 1f, 1f);
                }
            } catch (Throwable ignored) {
            }
        }

        if (cfg.endBlindnessTicks > 0) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, cfg.endBlindnessTicks, 0, true, true, true));
        }

        lang.chat(p, misfired ? "end-self-revived-misfire" : "end-self-revived");
        if (cfg.broadcastDowned) {
            broadcast(p, "broadcast-self-revived");
        }
        plugin.stats().addSelfRevived(p.getUniqueId());
        fireRevived(p, null, PlayerRevivedEvent.Cause.SELF_REVIVED);
    }

    /** Watchdog: warns if someone stayed downed far longer than they should have. Returns true if it bled them out. */
    private boolean watchdog(Downed d) {
        if (d.ticksLeft > -d.totalTicks || !watchdogWarned.add(d.player.getUniqueId())) {
            return false;
        }
        plugin.getLogger().warning(d.player.getName() + " has been downed for more than twice "
                + "their allotted time (" + d.totalTicks + " ticks). Something got them stuck; "
                + "forcing a bleed-out. Check whether another plugin is cancelling their death.");
        bleedOut(d);
        watchdogWarned.remove(d.player.getUniqueId());
        return true;
    }

    public void bleedOut(Downed d) {
        bleedOut(d, PlayerRevivedEvent.Cause.BLED_OUT);
    }

    private void bleedOut(Downed d, PlayerRevivedEvent.Cause cause) {
        if (d == null || d.player == null) {
            return;
        }
        if (dying.contains(d.player.getUniqueId())) {
            return;
        }
        Player p = d.player;
        cleanup(d);
        lang.chat(p, "bled-out");
        playSound(p.getLocation(), cfg.soundBledOut);
        if (cfg.broadcastDowned) {
            broadcast(p, "broadcast-bled-out");
        }
        plugin.stats().addBledOut(p.getUniqueId());

        // The actual death always happens one tick later, never inline. Finishing a downed player
        // from inside an EntityDamageEvent would nest a death inside the damage pipeline: a
        // PlayerDeathEvent fires there, the grave/drops are created, and then vanilla's own damage
        // pipeline sees the player dead and runs its own death on top, with the inventory already
        // empty. Deferring by one tick avoids that double death.
        dying.add(p.getUniqueId());
        Tasks.of(p, () -> {
            dying.remove(p.getUniqueId());
            if (p.isOnline() && !p.isDead() && p.getHealth() > 0) {
                p.setHealth(0.0);
            }
        });
        fireRevived(p, null, cause);
    }

    /** Whether this player's death is already scheduled for the next tick. */
    public boolean isDying(UUID id) {
        return dying.contains(id);
    }

    /** Surrender ([Give Up] button): a distinct sound/particle effect, then death. */
    public void surrender(Downed d) {
        Player p = d.player;
        playSound(p.getLocation(), cfg.soundSurrender);
        if (cfg.surrenderParticles) {
            try {
                p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.02);
            } catch (Throwable ignored) {
            }
        }
        bleedOut(d, PlayerRevivedEvent.Cause.SURRENDERED);
    }

    /** Clears all downed state and leaves the player back to normal. */
    public void cleanup(Downed d) {
        Player p = d.player;
        downedMap.remove(p.getUniqueId());
        if (d.task != null) {
            d.task.cancel();
        }
        revivingNow.values().removeIf(target -> target.equals(p.getUniqueId()));
        UltraReviveAPI.markDowned(plugin, p, false);
        if (d.bar != null) {
            d.bar.removeAll();
        }

        // Always restored, regardless of the CURRENT config (if it changed via /ur reload while
        // someone was downed, they used to stay stuck in adventure mode / glowing / prone).
        try {
            p.setPose(Pose.STANDING, false);
        } catch (Throwable t) {
            try {
                p.setSwimming(false);
            } catch (Throwable ignored) {
            }
        }
        if (d.prevGamemode != null && p.getGameMode() == GameMode.ADVENTURE) {
            p.setGameMode(d.prevGamemode);
        }
        p.setGlowing(false);
        Team team = glowTeam();
        if (team != null) {
            team.removeEntry(p.getName());
        }
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    /** On /ur reload or disable: revives/clears everyone so nobody stays stuck downed. */
    public void shutdown() {
        for (Downed d : new ArrayList<>(downedMap.values())) {
            cleanup(d);
            if (d.player.isOnline()) {
                d.player.setHealth(Math.min(cfg.reviveHealth, d.player.getMaxHealth()));
            }
        }
        downedMap.clear();
        revivingNow.clear();
    }

    /** Drops fatigue entries older than twice the window, so the maps don't grow unbounded. */
    private void pruneFatigue(long now) {
        long ttl = cfg.downedFatigueWindowSecs * 2000L;
        lastDownAt.entrySet().removeIf(e -> {
            if (now - e.getValue() > ttl) {
                fatigueStacks.remove(e.getKey());
                return true;
            }
            return false;
        });
    }

    /** One tick of one downed player, on their own thread. */
    private void tick(Downed d) {
        Player p = d.player;
        if (downedMap.get(p.getUniqueId()) != d) {
            if (d.task != null) {
                d.task.cancel();
            }
            return;
        }
        if (!p.isOnline() || p.isDead()) {
            cleanup(d);
            return;
        }
        if (watchdog(d)) {
            return;
        }
        revivingNow.values().removeIf(target -> target.equals(p.getUniqueId()));
        {

            // A downed player who falls into the void should still die, not float invulnerable.
            if (p.getLocation().getY() < p.getWorld().getMinHeight() - 5) {
                bleedOut(d);
                return;
            }

            d.ticksLeft--;
            if (d.ticksLeft <= 0) {
                bleedOut(d);
                return;
            }

            // Re-sent every second regardless of getPose(): with fixed=true the server always
            // reports SWIMMING, so asking it never reveals what the client actually shows.
            if (cfg.poseCrawl && d.ticksLeft % 20 == 0) {
                applyDownPose(p);
            }

            if (cfg.bleedTensionEnabled
                    && d.ticksLeft <= (int) (d.totalTicks * cfg.bleedTensionThreshold)) {
                renderBleedTension(d);
            }
            if (cfg.downedBeaconEnabled && d.ticksLeft % 10 == 0) {
                renderBeacon(d);
            }

            if (d.bar != null) {
                d.bar.setProgress(Math.max(0.0, Math.min(1.0, (double) d.ticksLeft / d.totalTicks)));
                d.bar.setTitle(lang.get("downed-bossbar", "{time}", secs(d.ticksLeft)));
            }

            List<Player> revivers = findRevivers(p);
            if (!revivers.isEmpty()) {
                d.noReviverTicks = 0;

                Player boostReviver = null;
                if (cfg.boostItemEnabled) {
                    for (Player rv : revivers) {
                        if (plugin.items().hasInInventory(rv, Items.BOOST)) {
                            boostReviver = rv;
                            break;
                        }
                    }
                }
                boolean boosted = boostReviver != null;
                int baseInc = boosted ? (int) Math.max(1, Math.round(cfg.boostSpeedMultiplier)) : 1;
                int extras = cfg.coopReviveEnabled ? Math.min(cfg.coopMaxExtra, revivers.size() - 1) : 0;
                int inc = baseInc + (int) Math.round(extras * cfg.coopBonusPerExtra);
                d.reviveTicks += Math.max(1, inc);

                int percent = (int) Math.min(100, d.reviveTicks * 100L / cfg.reviveDurationTicks);
                for (Player rv : revivers) {
                    revivingNow.put(rv.getUniqueId(), p.getUniqueId());
                    lang.actionbar(rv, "reviver-actionbar", "{target}", p.getName(), "{percent}", String.valueOf(percent));
                }
                lang.actionbar(p, "being-revived-actionbar", "{percent}", String.valueOf(percent));
                if (cfg.particles) {
                    spawnParticles(p.getLocation());
                }

                if (d.reviveTicks >= cfg.reviveDurationTicks) {
                    Player primary = boosted ? boostReviver : revivers.get(0);
                    if (boosted) {
                        plugin.items().consumeFromInventory(boostReviver, Items.BOOST);
                    }
                    for (Player rv : revivers) {
                        if (!rv.getUniqueId().equals(primary.getUniqueId())) {
                            plugin.stats().addRevivedOther(rv.getUniqueId());
                        }
                    }
                    revive(d, primary, boosted);
                }
            } else {
                // Grace period: progress doesn't vanish the instant the reviver steps away (a PvP
                // shove, a mob in the way). It freezes for a few seconds, then decays.
                d.noReviverTicks++;
                if (d.reviveTicks > 0 && d.noReviverTicks <= cfg.reviveGraceTicks) {
                    int percent = (int) Math.min(100, d.reviveTicks * 100L / cfg.reviveDurationTicks);
                    lang.actionbar(p, "revive-paused", "{percent}", String.valueOf(percent), "{time}", secs(d.ticksLeft));
                } else {
                    if (d.reviveTicks > 0) {
                        d.reviveTicks = Math.max(0, d.reviveTicks - 2);
                    }
                    lang.actionbar(p, "downed-actionbar", "{time}", secs(d.ticksLeft));
                }
            }
        }
    }

    /** Is someone reviving {@code reviverId} right now? (used to interrupt them on damage). */
    public boolean isActivelyReviving(UUID reviverId) {
        return revivingNow.containsKey(reviverId);
    }

    /** The reviver took damage: they lose part (or all) of their revive progress. */
    public void interruptRevive(Player reviver) {
        UUID targetId = revivingNow.remove(reviver.getUniqueId());
        if (targetId == null) {
            return;
        }
        Downed d = downedMap.get(targetId);
        if (d == null) {
            return;
        }
        d.reviveTicks = (int) Math.round(d.reviveTicks * (1.0 - cfg.interruptPenalty));
        d.noReviverTicks = cfg.reviveGraceTicks + 1; // no grace right after being hit
        lang.actionbar(reviver, "revive-interrupted");
    }

    /** Heartbeat sound and blood particles at the downed player's feet in their final seconds. */
    private void renderBleedTension(Downed d) {
        Player p = d.player;
        Location loc = p.getLocation();
        // The heartbeat speeds up as time runs out (interval 5..25 ticks).
        double frac = (double) d.ticksLeft / Math.max(1, (int) (d.totalTicks * cfg.bleedTensionThreshold));
        int interval = 5 + (int) (frac * 20);
        if (cfg.bleedHeartbeatSound != null && d.ticksLeft % Math.max(1, interval) == 0) {
            playSound(loc, cfg.bleedHeartbeatSound);
        }
        try {
            p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.15, 0), 6, 0.4, 0.05, 0.4,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 0), 1.4f));
        } catch (Throwable ignored) {
        }
    }

    /** Beacon-style particle column above the downed player, so their team can spot them. */
    private void renderBeacon(Downed d) {
        Location base = d.player.getLocation();
        org.bukkit.World w = base.getWorld();
        for (double dy = 1.3; dy <= 3.4; dy += 0.35) {
            beaconAt(w, base.clone().add(0, dy, 0));
        }
    }

    private void beaconAt(org.bukkit.World w, Location pt) {
        try {
            if (cfg.downedBeaconParticle == Particle.DUST) {
                Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 40, 40), 1.3f);
                w.spawnParticle(Particle.DUST, pt, 1, 0.03, 0.03, 0.03, dust);
            } else {
                w.spawnParticle(cfg.downedBeaconParticle, pt, 1, 0.02, 0.02, 0.02, 0);
            }
        } catch (Throwable ignored) {
        }
    }

    /** All valid players currently reviving {@code p}. */
    private List<Player> findRevivers(Player p) {
        List<Player> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(cfg.reviveRadius, cfg.reviveRadius, cfg.reviveRadius)) {
            if (!(e instanceof Player)) {
                continue;
            }
            Player other = (Player) e;
            if (isDowned(other.getUniqueId())) {
                continue;
            }
            if (!other.hasPermission("ultrarevive.revive")) {
                continue;
            }
            if (cfg.reviveBySneaking && !other.isSneaking()) {
                continue;
            }
            // No clans here: anyone who sneaks next to you can revive you. That's what lets a
            // stranger save you and makes for a story; team-only filtering lives in the clans plugin.
            if (cfg.hungerCost > 0 && other.getFoodLevel() <= 0) {
                if (other.getTicksLived() % 20 == 0) {
                    lang.actionbar(other, "reviver-too-hungry");
                }
                continue;
            }
            out.add(other);
        }
        return out;
    }

    private String secs(int ticks) {
        return String.valueOf((int) Math.ceil(ticks / 20.0));
    }

    private void spawnParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1.2, 0), 2, 0.3, 0.2, 0.3, 0);
    }

    private void playSound(Location loc, Sound sound) {
        if (sound != null) {
            loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
        }
    }

    private void broadcast(Player p, String key) {
        String msg = lang.pre(key, "{player}", p.getName());
        if (cfg.broadcastRadius <= 0) {
            Bukkit.broadcastMessage(msg);
        } else {
            for (Player near : p.getLocation().getNearbyPlayers(cfg.broadcastRadius)) {
                near.sendMessage(msg);
            }
        }
    }

    public Map<UUID, Downed> downedMap() {
        return downedMap;
    }
}
