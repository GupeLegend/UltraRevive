package mc.gupe.revive;

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
import org.bukkit.boss.BossBar;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Pose;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;
import org.bukkit.scheduler.BukkitTask;
import org.bukkit.scoreboard.Scoreboard;
import org.bukkit.scoreboard.Team;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Nucleo del sistema: maneja a los jugadores derribados, el bucle por tick, revivir y desangrarse. */
public final class ReviveManager {

    private static final String TEAM = "ultra_downed";

    private final UltraRevive plugin;
    private final Settings cfg;
    private final Lang lang;

    private final Map<UUID, Downed> downedMap = new HashMap<>();
    // quien esta reanimando a quien AHORA (reanimador -> caido). Se reconstruye cada tick.
    private final Map<UUID, UUID> revivingNow = new HashMap<>();
    // fatiga por derribos repetidos.
    private final Map<UUID, Long> lastDownAt = new HashMap<>();
    private final Map<UUID, Integer> fatigueStacks = new HashMap<>();
    private BukkitTask task;

    public ReviveManager(UltraRevive plugin) {
        this.plugin = plugin;
        this.cfg = plugin.settings();
        this.lang = plugin.lang();
        setupTeam();
    }

    /** Crea (o reusa) el team que da el color al brillo. */
    private void setupTeam() {
        Scoreboard board = Bukkit.getScoreboardManager().getMainScoreboard();
        Team team = board.getTeam(TEAM);
        if (team == null) team = board.registerNewTeam(TEAM);
        team.setColor(cfg.glowColor);
        team.setCanSeeFriendlyInvisibles(false);
    }

    /** Re-aplica ajustes que cambian en /reload (ej. el color del brillo). */
    public void applyConfig() {
        setupTeam();
    }

    public void start() {
        task = Bukkit.getScheduler().runTaskTimer(plugin, this::tick, 1L, 1L);
    }

    public boolean isDowned(UUID id) {
        return downedMap.containsKey(id);
    }

    // DERRIBAR
    public void down(Player p) {
        if (isDowned(p.getUniqueId())) return;

        // FATIGA: si te derriban una y otra vez en poco tiempo, aguantas menos.
        int downDur = cfg.downDurationTicks;
        if (cfg.downedFatigueEnabled) {
            long now = System.currentTimeMillis();
            pruneFatigue(now);  // evita que los mapas crezcan sin techo
            Long last = lastDownAt.get(p.getUniqueId());
            int stacks = 0;
            if (last != null && now - last <= cfg.downedFatigueWindowSecs * 1000L)
                stacks = fatigueStacks.getOrDefault(p.getUniqueId(), 0) + 1;
            fatigueStacks.put(p.getUniqueId(), stacks);
            lastDownAt.put(p.getUniqueId(), now);
            double mult = Math.max(cfg.downedFatigueMin, Math.pow(cfg.downedFatigueFactor, stacks));
            downDur = Math.max(20, (int) Math.round(downDur * mult));
        }

        Downed d = new Downed(p, downDur);
        downedMap.put(p.getUniqueId(), d);

        // Vida cosmetica baja (es invulnerable mientras esta derribado).
        p.setHealth(Math.min(cfg.downedHealth, p.getMaxHealth()));
        p.setFoodLevel(Math.max(p.getFoodLevel(), 6));  // que no muera de hambre derribado

        // Modo AVENTURA (no puede romper bloques) + pose tumbada.
        if (cfg.adventureMode) p.setGameMode(GameMode.ADVENTURE);
        applyDownPose(p);
        // ⚠ Y OTRA VEZ un tick despues. Cambiar de modo de juego resetea la pose EN EL CLIENTE,
        // y como la fijamos con fixed=true el servidor cree que sigue tumbado: getPose() devuelve
        // SWIMMING y el chequeo del tick nunca detecta que el jugador se ve de pie.
        plugin.getServer().getScheduler().runTaskLater(plugin, () -> {
            if (isDowned(p.getUniqueId())) applyDownPose(p);
        }, 1L);

        // Brillo de color.
        if (cfg.glow) {
            Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM);
            if (team != null) team.addEntry(p.getName());
            p.setGlowing(true);
        }

        // Efectos (lentitud / ceguera) por toda la duracion REAL (con fatiga aplicada).
        int dur = d.totalTicks + 40;
        if (cfg.slownessAmplifier >= 0)
            p.addPotionEffect(new PotionEffect(PotionEffectType.SLOWNESS, dur, cfg.slownessAmplifier, true, false, false));
        if (cfg.blindness)
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, dur, 0, true, false, false));

        // Bossbar.
        if (cfg.bossbar) {
            d.bar = Bukkit.createBossBar(lang.get("downed-bossbar", "{time}", secs(d.ticksLeft)),
                    cfg.bossbarColor, BarStyle.SOLID);
            d.bar.addPlayer(p);
        }

        // Titulo + sonido + anuncio.
        lang.title(p, "downed-title", "downed-subtitle");
        playSound(p.getLocation(), cfg.soundDowned);
        if (cfg.broadcastDowned) broadcast(p, "broadcast-downed");
        sendDownedPrompts(p);
        if (cfg.downedTeamAlert) sendTeamAlert(p);  // aviso con coordenadas a equipo/aliados
        plugin.stats().addDowned(p.getUniqueId());

        // 2.0 (API): marcar + avisar a las extensiones. Va al FINAL, con el derribo ya aplicado
        // (pose, glow, bossbar), asi quien escuche ve el estado definitivo.
        mc.gupe.revive.api.UltraReviveAPI.markDowned(plugin, p, true);
        fire(new mc.gupe.revive.api.PlayerDownedEvent(p, downDur));
    }

    /** Dispara un evento de la API sin que un listener roto pueda tumbar el derribo/revivido. */
    private void fire(org.bukkit.event.Event e) {
        try { Bukkit.getPluginManager().callEvent(e); }
        catch (Throwable t) { plugin.getLogger().warning("Un listener de " + e.getEventName() + " fallo: " + t); }
    }

    /** 2.0 (API): avisa que alguien dejo de estar derribado y por que. */
    private void fireRevived(Player p, Player reviver, mc.gupe.revive.api.PlayerRevivedEvent.Cause cause) {
        fire(new mc.gupe.revive.api.PlayerRevivedEvent(p, reviver, cause));
    }

    /** Avisa a los companeros de equipo/aliados DONDE cayo el jugador. */
    private void sendTeamAlert(Player p) {
        if (true) return;  // aviso al clan: lo maneja el plugin de clanes, no este
        Location l = p.getLocation();
        for (Player other : Bukkit.getOnlinePlayers()) {
            if (other.getUniqueId().equals(p.getUniqueId())) continue;
            if (!teamOrAlly(p.getUniqueId(), other.getUniqueId())) continue;
            other.sendMessage(lang.pre("downed-team-alert",
                    "{player}", p.getName(),
                    "{x}", String.valueOf(l.getBlockX()),
                    "{y}", String.valueOf(l.getBlockY()),
                    "{z}", String.valueOf(l.getBlockZ())));
        }
    }

    /** Mensajes clickeables en el chat: RENDIRSE y (si esta activo) AUTO-REVIVIR. */
    private void sendDownedPrompts(Player p) {
        // 1) Rendirse.
        Component sPrompt = leg(lang.get("surrender-prompt"));
        Component sButton = leg(lang.get("surrender-button"))
                .clickEvent(ClickEvent.runCommand("/ultrarevive surrender"))
                .hoverEvent(HoverEvent.showText(leg(lang.get("surrender-hover"))));
        p.sendMessage(sPrompt.append(sButton));

        // 2) Auto-revivir (Totem del Aether o del End).
        if (cfg.selfItemEnabled || cfg.endItemEnabled) {
            Component rPrompt = leg(lang.get("selfrevive-prompt"));
            Component rButton = leg(lang.get("selfrevive-button"))
                    .clickEvent(ClickEvent.runCommand("/ultrarevive selfrevive"))
                    .hoverEvent(HoverEvent.showText(leg(lang.get("selfrevive-hover"))));
            p.sendMessage(rPrompt.append(rButton));
        }

    }

    private Component leg(String coloredString) {
        return LegacyComponentSerializer.legacySection().deserialize(coloredString);
    }

    /** Pose tumbada con respaldo multi-version (si setPose no existe, usa setSwimming). */
    private void applyDownPose(Player p) {
        if (!cfg.poseCrawl) return;
        try { p.setPose(Pose.SWIMMING, true); }
        catch (Throwable t) { try { p.setSwimming(true); } catch (Throwable ignored) {} }
    }

    private void clearPose(Player p) {
        if (!cfg.poseCrawl) return;
        try { p.setPose(Pose.STANDING, false); }
        catch (Throwable t) { try { p.setSwimming(false); } catch (Throwable ignored) {} }
    }

    // REVIVIR
    public void revive(Downed d, Player reviver) {
        revive(d, reviver, false);
    }

    /** Revivir. Si 'boosted' (Totem del Nether), vuelve con mas vida y efectos de premio. */
    public void revive(Downed d, Player reviver, boolean boosted) {
        cleanup(d);
        Player p = d.player;
        double hp = boosted ? cfg.boostReviveHealth : cfg.reviveHealth;
        p.setHealth(Math.min(hp, p.getMaxHealth()));

        if (boosted && cfg.boostRewardEffects) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 200, 0, true, false, false));
        }
        // Desventaja del Totem Maldito del Nether: QUIEN revive (el que usa el
        // totem) recibe el veneno del wither skeleton (Wither), NO el de arana (Poison).
        if (boosted && reviver != null && cfg.boostReviveWitherTicks > 0) {
            reviver.addPotionEffect(new PotionEffect(PotionEffectType.WITHER, cfg.boostReviveWitherTicks, 0, true, true, true));
        }

        lang.actionbar(p, "revived");
        lang.chat(p, "revived");
        playSound(p.getLocation(), cfg.soundRevived);
        if (cfg.broadcastDowned) broadcast(p, "broadcast-revived");

        if (reviver != null) {
            lang.chat(reviver, "reviver-success", "{target}", p.getName());
            if (cfg.hungerCost > 0)
                reviver.setFoodLevel(Math.max(0, reviver.getFoodLevel() - cfg.hungerCost));
            plugin.stats().addRevivedOther(reviver.getUniqueId());

            // Totem Desconocido del End: si QUIEN revive lo lleva, crea un AREA DE SANACION
            // (cura al revivido + cercanos) y a el le da nausea (agotamiento). Se gasta.
            if (cfg.endItemEnabled && plugin.items().hasInInventory(reviver, Items.END)) {
                plugin.items().consumeFromInventory(reviver, Items.END);
                createHealingArea(p, reviver);
            }
        }
        plugin.stats().addRevived(p.getUniqueId());
        fireRevived(p, reviver, mc.gupe.revive.api.PlayerRevivedEvent.Cause.REVIVED);  // 2.0 (API)
    }

    /**
     * ZONA DE SANACION del Totem del End: disco de particulas rojas en el suelo que CURA
     * (Regeneracion + caida lenta) a quien este dentro, durante varios segundos.
     */
    private void createHealingArea(Player center, Player reviver) {
        final org.bukkit.World w = center.getWorld();
        final Location loc = center.getLocation().clone();
        final double r = cfg.endAreaRadius;
        final UUID centerId = center.getUniqueId();

        // Nausea al que revive (agotamiento), una sola vez.
        if (cfg.endReviverNauseaTicks > 0)
            reviver.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA,
                    cfg.endReviverNauseaTicks, 0, true, true, true));
        try { if (cfg.endSound != null) w.playSound(loc, cfg.endSound, 1f, 1.2f); } catch (Throwable ignored) {}

        // Zona persistente: cada 'period' ticks pinta el disco y cura a los de dentro, hasta agotar la duracion.
        final int period = 12;
        final int totalTicks = Math.max(period, cfg.endAreaHealTicks);
        final BukkitTask[] holder = new BukkitTask[1];
        final int[] elapsed = {0};
        holder[0] = plugin.getServer().getScheduler().runTaskTimer(plugin, () -> {
            renderHealZone(w, loc, r);
            for (Player near : w.getPlayers()) {
                if (near.getLocation().distanceSquared(loc) > r * r) continue;
                if (cfg.endAreaTeamOnly && !near.getUniqueId().equals(centerId)
                        && !teamOrAlly(centerId, near.getUniqueId())) continue;
                near.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION,
                        period + 20, cfg.endAreaHealLevel, true, true, true));
                if (cfg.endAreaSlowFalling && cfg.endAreaSlowFallTicks > 0)
                    near.addPotionEffect(new PotionEffect(PotionEffectType.SLOW_FALLING,
                            cfg.endAreaSlowFallTicks, 0, true, false, false));
            }
            elapsed[0] += period;
            if (elapsed[0] >= totalTicks && holder[0] != null) holder[0].cancel();
        }, 0L, period);
    }

    /** Disco RELLENO de particulas a ras del suelo que marca la zona de sanacion. */
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
                    if (cfg.endAreaParticle == Particle.DUST)
                        w.spawnParticle(Particle.DUST, p, 1, 0.05, 0.05, 0.05,
                                new Particle.DustOptions(org.bukkit.Color.RED, 1.2f));
                    else
                        w.spawnParticle(cfg.endAreaParticle, p, 1, 0.05, 0.05, 0.05, 0);
                } catch (Throwable ignored) {}
            }
        }
    }

    /** ¿Se le cura a este jugador en el area del Totem del End? */
    private boolean teamOrAlly(UUID a, UUID b) {
        return true;
    }

    /** Auto-revivirse con el totem (sin companero). Efecto estilo totem. */
    public void selfRevive(Downed d) {
        cleanup(d);
        Player p = d.player;
        p.setHealth(Math.min(cfg.selfReviveHealth, p.getMaxHealth()));

        // Efecto de "totem": sonido + particulas de totem.
        playSound(p.getLocation(), cfg.selfReviveSound);
        try {
            p.getWorld().spawnParticle(Particle.TOTEM_OF_UNDYING, p.getLocation().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.4);
        } catch (Throwable ignored) {}

        // Totem Bendito del Aether: efectos secundarios al auto-revivir.
        // Ventaja: efecto de manzana dorada NORMAL (Absorcion I 2:00 + Regeneracion II 0:05).
        // Desventaja: mareo (nausea) durante 20s.
        if (cfg.selfSideEffects) {
            p.addPotionEffect(new PotionEffect(PotionEffectType.ABSORPTION, 2400, 0, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.REGENERATION, 100, 1, true, false, false));
            p.addPotionEffect(new PotionEffect(PotionEffectType.NAUSEA, 400, 0, true, true, true));
        }

        lang.actionbar(p, "self-revived");
        lang.chat(p, "self-revived");
        if (cfg.broadcastDowned) broadcast(p, "broadcast-self-revived");
        plugin.stats().addSelfRevived(p.getUniqueId());
        fireRevived(p, null, mc.gupe.revive.api.PlayerRevivedEvent.Cause.SELF_REVIVED);  // 2.0 (API)
    }

    /** Auto-revivirse con el Totem del End: te REVIVE y te teletransporta a tu home (escape del vacio). */
    public void endSelfRevive(Downed d) {
        Player p = d.player;
        // Destino: home (o cama/respawn o spawn del mundo).
        Location dest = null;
        if (cfg.endTeleportHome) {
            // Sin el plugin de homes, el escape del Totem del End te manda a tu CAMA (y si no tenes,
                // al spawn del mundo). Es el equivalente vanilla de "tu hogar".
                dest = p.getRespawnLocation() != null ? p.getRespawnLocation()
                        : p.getWorld().getSpawnLocation();
            if (dest == null) dest = p.getRespawnLocation();  // cama / ancla
            if (dest == null) dest = p.getWorld().getSpawnLocation();
        }
        // Twist "Desconocido": con prob. configurada, falla a un punto aleatorio cercano.
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

        // Particulas/sonido de salida (en el sitio donde cayo).
        try {
            p.getWorld().spawnParticle(Particle.REVERSE_PORTAL, p.getLocation().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.4);
            if (cfg.endSound != null) p.playSound(p.getLocation(), cfg.endSound, 1f, 1f);
        } catch (Throwable ignored) {}

        if (dest != null) {
            p.teleport(dest);
            try {
                p.getWorld().spawnParticle(Particle.PORTAL, dest.clone().add(0, 1, 0), 60, 0.4, 0.7, 0.4, 0.6);
                if (cfg.endSound != null) p.playSound(dest, cfg.endSound, 1f, 1f);
            } catch (Throwable ignored) {}
        }

        // Desventaja: ceguera (desorientacion del viaje por el End).
        if (cfg.endBlindnessTicks > 0)
            p.addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, cfg.endBlindnessTicks, 0, true, true, true));

        lang.chat(p, misfired ? "end-self-revived-misfire" : "end-self-revived");
        if (cfg.broadcastDowned) broadcast(p, "broadcast-self-revived");
        plugin.stats().addSelfRevived(p.getUniqueId());
        fireRevived(p, null, mc.gupe.revive.api.PlayerRevivedEvent.Cause.SELF_REVIVED);  // 2.0 (API)
    }

    // DESANGRARSE (muerte real)
    /** Vigia: avisa si alguien quedo atrapado en derribado mucho mas alla de su tiempo. */
    private final java.util.Set<UUID> avisados = new java.util.HashSet<>();

    private void watchdog() {
        for (Downed d : new ArrayList<>(downedMap.values())) {
            if (d.ticksLeft > -d.totalTicks) continue;  // todavia dentro de lo razonable
            if (!avisados.add(d.player.getUniqueId())) continue;
            plugin.getLogger().warning("[DIAG] " + d.player.getName() + " lleva mas del doble de su "
                    + "tiempo DERRIBADO (" + d.totalTicks + " ticks). Algo lo dejo pegado: se lo "
                    + "destraba solo. Revisar que otro plugin no este cancelando su muerte.");
            bleedOut(d);
        }
        if (downedMap.isEmpty()) avisados.clear();
    }

    public void bleedOut(Downed d) {
        bleedOut(d, mc.gupe.revive.api.PlayerRevivedEvent.Cause.BLED_OUT);
    }

    /** igual, pero sabiendo POR QUE murio (desangrado o rendido) para el evento de la API. */
    private void bleedOut(Downed d, mc.gupe.revive.api.PlayerRevivedEvent.Cause cause) {
        if (d == null || d.player == null) return;  // ya lo remataron en este mismo tick
        if (dying.contains(d.player.getUniqueId())) {
            plugin.getLogger().warning("[DIAG] Se intento rematar dos veces a " + d.player.getName()
                    + " en el mismo tick. Se ignora la segunda (antes esto vaciaba su tumba).");
            return;
        }
        Player p = d.player;
        cleanup(d);
        lang.chat(p, "bled-out");
        playSound(p.getLocation(), cfg.soundBledOut);
        if (cfg.broadcastDowned) broadcast(p, "broadcast-bled-out");
        plugin.stats().addBledOut(p.getUniqueId());

        // ⚠ LA MUERTE VA UN TICK DESPUES, NUNCA EN EL ACTO.
        // Rematar a un caido se dispara DENTRO del EntityDamageEvent. Si ahi mismo se hace
        // setHealth(0), la muerte ocurre anidada: salta un PlayerDeathEvent, la tumba se crea CON
        // los items y se vacia el inventario... y despues, al volver a la tuberia de daño, vanilla
        // ve al jugador muerto y corre SU propia muerte -> SEGUNDO PlayerDeathEvent, esta vez con
        // el inventario ya vacio. Como la tumba se guarda por POSICION, la vacia pisaba a la buena.
        // Eso es lo que reportaron: "te siguen pegando y la tumba no da los items".
        // (La maldicion del Totem del Nether ya difiere la muerte por esto mismo.)
        dying.add(p.getUniqueId());
        plugin.getServer().getScheduler().runTask(plugin, () -> {
            dying.remove(p.getUniqueId());
            if (p.isOnline() && !p.isDead() && p.getHealth() > 0) p.setHealth(0.0);
        });
        fireRevived(p, null, cause);  // 2.0 (API)
    }

    /** Jugadores a los que ya se les firmo la muerte y estan esperando el tick que los mata. */
    private final java.util.Set<UUID> dying = java.util.concurrent.ConcurrentHashMap.newKeySet();

    /** ¿Tiene la muerte ya firmada y pendiente para el proximo tick? */
    public boolean isDying(UUID id) { return dying.contains(id); }

    /** Rendirse (boton [Here]): efecto especial de sonido/particulas y muerte. */
    public void surrender(Downed d) {
        Player p = d.player;
        playSound(p.getLocation(), cfg.soundSurrender);
        if (cfg.surrenderParticles) {
            try { p.getWorld().spawnParticle(Particle.LARGE_SMOKE, p.getLocation().add(0, 1, 0), 25, 0.3, 0.5, 0.3, 0.02); }
            catch (Throwable ignored) {}
        }
        bleedOut(d, mc.gupe.revive.api.PlayerRevivedEvent.Cause.SURRENDERED);
    }

    /** Quita TODO el estado de derribado y deja al jugador normal. */
    public void cleanup(Downed d) {
        Player p = d.player;
        downedMap.remove(p.getUniqueId());
        // 2.0 (API): sacar la marca que leen las extensiones. Va aca, en el UNICO punto por el que
        // se sale del estado derribado, para que no pueda quedar pegada.
        mc.gupe.revive.api.UltraReviveAPI.markDowned(plugin, p, false);
        if (d.bar != null) d.bar.removeAll();

        // Restaurar SIEMPRE, sin depender del config ACTUAL (si cambiara por /ur reload
        // mientras alguien esta derribado, antes quedaba atrapado en ADVENTURE / con glow / tumbado).
        try { p.setPose(Pose.STANDING, false); } catch (Throwable t) { try { p.setSwimming(false); } catch (Throwable ignored) {} }
        if (d.prevGamemode != null && p.getGameMode() == GameMode.ADVENTURE) p.setGameMode(d.prevGamemode);
        p.setGlowing(false);
        Team team = Bukkit.getScoreboardManager().getMainScoreboard().getTeam(TEAM);
        if (team != null) team.removeEntry(p.getName());
        p.removePotionEffect(PotionEffectType.SLOWNESS);
        p.removePotionEffect(PotionEffectType.BLINDNESS);
    }

    /** En /reload o disable: revive/limpia a todos para no dejarlos atrapados. */
    public void shutdown() {
        if (task != null) task.cancel();
        for (Downed d : new ArrayList<>(downedMap.values())) {
            cleanup(d);
            if (d.player.isOnline()) d.player.setHealth(Math.min(cfg.reviveHealth, d.player.getMaxHealth()));
        }
        downedMap.clear();
        revivingNow.clear();  // higiene
    }

    /**
     * Poda las entradas de fatiga ya vencidas (mas viejas que 2x la ventana) para que los mapas
     * lastDownAt/fatigueStacks no crezcan sin techo con la rotacion de jugadores.
     */
    private void pruneFatigue(long now) {
        long ttl = cfg.downedFatigueWindowSecs * 2000L;
        lastDownAt.entrySet().removeIf(e -> {
            if (now - e.getValue() > ttl) { fatigueStacks.remove(e.getKey()); return true; }
            return false;
        });
    }

    // BUCLE POR TICK
    private void tick() {
        if (downedMap.isEmpty()) { if (!revivingNow.isEmpty()) revivingNow.clear(); return; }
        watchdog();
        revivingNow.clear();  // se reconstruye segun quien reanime este tick
        for (Downed d : new ArrayList<>(downedMap.values())) {
            Player p = d.player;
            if (!p.isOnline() || p.isDead()) { cleanup(d); continue; }

            // Si un derribado cae al vacio, que muera (no quede flotando invulnerable).
            if (p.getLocation().getY() < p.getWorld().getMinHeight() - 5) { bleedOut(d); continue; }

            d.ticksLeft--;
            if (d.ticksLeft <= 0) { bleedOut(d); continue; }

            // Mantener la pose. Se re-manda cada segundo SIN preguntar por getPose(): con
            // fixed=true el servidor siempre contesta SWIMMING, asi que preguntarle no sirve para
            // saber lo que ve el cliente. Es un paquete chico y solo para los que estan caidos.
            if (cfg.poseCrawl && d.ticksLeft % 20 == 0) applyDownPose(p);

            // Escalada de tension en los ultimos segundos (latido + particulas de sangre).
            if (cfg.bleedTensionEnabled
                    && d.ticksLeft <= (int) (d.totalTicks * cfg.bleedTensionThreshold)) {
                renderBleedTension(d);
            }
            // Haz de particulas sobre el caido para que el equipo lo ubique.
            if (cfg.downedBeaconEnabled && d.ticksLeft % 10 == 0) renderBeacon(d);

            // Bossbar (progreso relativo a SU duracion, ya con fatiga aplicada).
            if (d.bar != null) {
                d.bar.setProgress(Math.max(0.0, Math.min(1.0, (double) d.ticksLeft / d.totalTicks)));
                d.bar.setTitle(lang.get("downed-bossbar", "{time}", secs(d.ticksLeft)));
            }

            // Buscar TODOS los companeros validos que esten reviviendo.
            List<Player> revivers = findRevivers(p);
            if (!revivers.isEmpty()) {
                d.noReviverTicks = 0;

                // Totem del Nether EN EL INVENTARIO de cualquiera -> revive mas rapido.
                Player boostReviver = null;
                if (cfg.boostItemEnabled) {
                    for (Player rv : revivers)
                        if (plugin.items().hasInInventory(rv, Items.BOOST)) { boostReviver = rv; break; }
                }
                boolean boosted = boostReviver != null;
                int baseInc = boosted ? (int) Math.max(1, Math.round(cfg.boostSpeedMultiplier)) : 1;
                // COOPERATIVO: cada reanimador extra suma velocidad (con tope).
                int extras = cfg.coopReviveEnabled
                        ? Math.min(cfg.coopMaxExtra, revivers.size() - 1) : 0;
                int inc = baseInc + (int) Math.round(extras * cfg.coopBonusPerExtra);
                d.reviveTicks += Math.max(1, inc);

                int percent = (int) Math.min(100, d.reviveTicks * 100L / cfg.reviveDurationTicks);
                for (Player rv : revivers) {
                    revivingNow.put(rv.getUniqueId(), p.getUniqueId());
                    lang.actionbar(rv, "reviver-actionbar", "{target}", p.getName(), "{percent}", String.valueOf(percent));
                }
                lang.actionbar(p, "being-revived-actionbar", "{percent}", String.valueOf(percent));
                if (cfg.particles) spawnParticles(p.getLocation());

                if (d.reviveTicks >= cfg.reviveDurationTicks) {
                    Player primary = boosted ? boostReviver : revivers.get(0);
                    if (boosted) plugin.items().consumeFromInventory(boostReviver, Items.BOOST);
                    // Credito de "asistencia" para los reanimadores extra.
                    for (Player rv : revivers)
                        if (!rv.getUniqueId().equals(primary.getUniqueId()))
                            plugin.stats().addRevivedOther(rv.getUniqueId());
                    revive(d, primary, boosted);
                }
            } else {
                // GRACIA: el progreso NO se borra de golpe si el reanimador se aleja un
                // instante (empujon en PvP, un mob que estorba). Se congela unos segundos y luego decae.
                d.noReviverTicks++;
                if (d.reviveTicks > 0 && d.noReviverTicks <= cfg.reviveGraceTicks) {
                    int percent = (int) Math.min(100, d.reviveTicks * 100L / cfg.reviveDurationTicks);
                    lang.actionbar(p, "revive-paused", "{percent}", String.valueOf(percent), "{time}", secs(d.ticksLeft));
                } else {
                    if (d.reviveTicks > 0) d.reviveTicks = Math.max(0, d.reviveTicks - 2);  // decae al doble de rapido
                    lang.actionbar(p, "downed-actionbar", "{time}", secs(d.ticksLeft));
                }
            }
        }
    }

    /** ¿Alguien esta reanimando a 'reviver' ahora mismo? (para interrumpir por dano). */
    public boolean isActivelyReviving(UUID reviverId) {
        return revivingNow.containsKey(reviverId);
    }

    /** El reanimador recibio dano: pierde parte (o todo) el progreso de la reanimacion. */
    public void interruptRevive(Player reviver) {
        UUID targetId = revivingNow.remove(reviver.getUniqueId());
        if (targetId == null) return;
        Downed d = downedMap.get(targetId);
        if (d == null) return;
        d.reviveTicks = (int) Math.round(d.reviveTicks * (1.0 - cfg.interruptPenalty));
        d.noReviverTicks = cfg.reviveGraceTicks + 1;  // sin gracia tras un golpe
        lang.actionbar(reviver, "revive-interrupted");
    }

    /** Latido + particulas de sangre a ras del caido en sus ultimos segundos. */
    private void renderBleedTension(Downed d) {
        Player p = d.player;
        Location loc = p.getLocation();
        // El latido se acelera a medida que se acaba el tiempo (intervalo 5..25 ticks).
        double frac = (double) d.ticksLeft / Math.max(1, (int) (d.totalTicks * cfg.bleedTensionThreshold));
        int interval = 5 + (int) (frac * 20);
        if (cfg.bleedHeartbeatSound != null && d.ticksLeft % Math.max(1, interval) == 0)
            playSound(loc, cfg.bleedHeartbeatSound);
        try {
            p.getWorld().spawnParticle(Particle.DUST, loc.clone().add(0, 0.15, 0), 6, 0.4, 0.05, 0.4,
                    new Particle.DustOptions(org.bukkit.Color.fromRGB(140, 0, 0), 1.4f));
        } catch (Throwable ignored) {}
    }

    /** Columna de particulas sobre el caido (tipo baliza) para ubicarlo. */
    private void renderBeacon(Downed d) {
        Player p = d.player;
        Location base = p.getLocation();
        org.bukkit.World w = base.getWorld();
        boolean teamOnly = false;  // el haz lo ve todo el mundo: filtrar por clan es del otro plugin
        for (double dy = 1.3; dy <= 3.4; dy += 0.35) {
            Location pt = base.clone().add(0, dy, 0);
            if (teamOnly) {
                for (Player o : Bukkit.getOnlinePlayers()) {
                    if (!o.getUniqueId().equals(p.getUniqueId())
                            && !teamOrAlly(p.getUniqueId(), o.getUniqueId())) continue;
                    beaconAt(o, null, pt);
                }
            } else {
                beaconAt(null, w, pt);
            }
        }
    }

    /** Pinta una particula del haz, a un jugador concreto o a todo el mundo. */
    private void beaconAt(Player only, org.bukkit.World w, Location pt) {
        try {
            if (cfg.downedBeaconParticle == Particle.DUST) {
                Particle.DustOptions dust = new Particle.DustOptions(org.bukkit.Color.fromRGB(255, 40, 40), 1.3f);
                if (only != null) only.spawnParticle(Particle.DUST, pt, 1, 0.03, 0.03, 0.03, dust);
                else w.spawnParticle(Particle.DUST, pt, 1, 0.03, 0.03, 0.03, dust);
            } else {
                if (only != null) only.spawnParticle(cfg.downedBeaconParticle, pt, 1, 0.02, 0.02, 0.02, 0);
                else w.spawnParticle(cfg.downedBeaconParticle, pt, 1, 0.02, 0.02, 0.02, 0);
            }
        } catch (Throwable ignored) {}
    }

    /** Devuelve TODOS los jugadores validos que esten reviviendo a 'p'. */
    private List<Player> findRevivers(Player p) {
        List<Player> out = new ArrayList<>();
        for (Entity e : p.getNearbyEntities(cfg.reviveRadius, cfg.reviveRadius, cfg.reviveRadius)) {
            if (!(e instanceof Player)) continue;
            Player other = (Player) e;
            if (isDowned(other.getUniqueId())) continue;
            if (!other.hasPermission("ultrarevive.revive")) continue;
            if (cfg.reviveBySneaking && !other.isSneaking()) continue;
            // Restriccion de equipos (si esta activa): solo equipo/aliados (o si el caido no tiene equipo).
            // null-guard: si el modulo de equipos estuviera desactivado, no NPE por tick.
            // Sin clanes: te levanta cualquiera que se agache al lado. Es lo que hace que un desconocido
            // te salve y salga una historia; el filtro por equipo vive en el plugin de clanes.
            if (cfg.hungerCost > 0 && other.getFoodLevel() <= 0) {
                // throttle: antes spameaba el actionbar 20x/seg a cualquier agachado hambriento cercano.
                if (other.getTicksLived() % 20 == 0) lang.actionbar(other, "reviver-too-hungry");
                continue;
            }
            out.add(other);
        }
        return out;
    }

    // ---------- utilidades ----------

    private String secs(int ticks) {
        return String.valueOf((int) Math.ceil(ticks / 20.0));
    }

    private void spawnParticles(Location loc) {
        loc.getWorld().spawnParticle(Particle.HEART, loc.clone().add(0, 1.2, 0), 2, 0.3, 0.2, 0.3, 0);
    }

    private void playSound(Location loc, Sound sound) {
        if (sound != null) loc.getWorld().playSound(loc, sound, 1.0f, 1.0f);
    }

    private void broadcast(Player p, String key) {
        String msg = lang.pre(key, "{player}", p.getName());
        if (cfg.broadcastRadius <= 0) {
            Bukkit.broadcastMessage(msg);
        } else {
            for (Player near : p.getWorld().getPlayers()) {
                if (near.getLocation().distance(p.getLocation()) <= cfg.broadcastRadius) near.sendMessage(msg);
            }
        }
    }

    public Map<UUID, Downed> downedMap() { return downedMap; }
}
