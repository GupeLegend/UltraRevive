package mc.gupe.revive.listeners;

import mc.gupe.revive.Items;
import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.ReviveManager;
import mc.gupe.revive.Settings;
import org.bukkit.Material;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.inventory.ItemStack;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.projectiles.ProjectileSource;

/** Logica de dano: - Dano letal a un jugador normal -> queda DERRIBADO en vez de morir. */
public final class CombatListener implements Listener {

    private final UltraRevive plugin;
    private final ReviveManager manager;
    private final Settings cfg;

    public CombatListener(UltraRevive plugin) {
        this.plugin = plugin;
        this.manager = plugin.manager();
        this.cfg = plugin.settings();
    }

    @EventHandler(priority = EventPriority.HIGH, ignoreCancelled = true)
    public void onDamage(EntityDamageEvent e) {
        // Un derribado NO puede pegar.
        if (e instanceof EntityDamageByEntityEvent) {
            Entity damager = ((EntityDamageByEntityEvent) e).getDamager();
            Player atk = resolveAttacker(damager);
            if (atk != null && manager.isDowned(atk.getUniqueId())) {
                e.setCancelled(true);
                return;
            }
        }

        if (!(e.getEntity() instanceof Player)) return;
        Player p = (Player) e.getEntity();

        // --- Dano a un DERRIBADO ---
        if (manager.isDowned(p.getUniqueId())) {
            e.setCancelled(true);  // por defecto invulnerable
            Player attacker = null;
            if (e instanceof EntityDamageByEntityEvent)
                attacker = resolveAttacker(((EntityDamageByEntityEvent) e).getDamager());

            if (attacker != null) {
                if (cfg.finishByPlayers) manager.bleedOut(manager.downedMap().get(p.getUniqueId()));
            } else {
                // dano de mob / entorno
                if (!cfg.immuneToMobs) manager.bleedOut(manager.downedMap().get(p.getUniqueId()));
            }
            return;
        }

        // --- 1.9.1: RIESGO al reanimar -> si te pegan mientras reanimas, se interrumpe. ---
        if (cfg.interruptOnDamageEnabled && e.getFinalDamage() > 0
                && manager.isActivelyReviving(p.getUniqueId())) {
            manager.interruptRevive(p);
        }

        // --- Dano letal a un jugador NORMAL -> derribar ---
        // El vacio / out-of-world mata normal (si no, quedaria cayendo invulnerable).
        if (e.getCause() == EntityDamageEvent.DamageCause.VOID) return;
        // Ya tiene la muerte firmada para el proximo tick: que muera tranquilo, no re-derribarlo.
        if (manager.isDying(p.getUniqueId())) return;
        if (hasBypass(p)) return;
        if (!cfg.worldEnabled(p.getWorld().getName())) return;

        // Guard: si el jugador ya esta muerto/sin vida (p.ej. dos golpes letales en el
        // mismo tick), NO lo "revivas" a derribado. Antes un 2do daño sobre el cadaver lo re-derribaba.
        if (p.isDead() || p.getHealth() <= 0.0) return;

        double finalDamage = e.getFinalDamage();
        if (p.getHealth() - finalDamage <= 0.0) {
            // Si lleva TOTEM en la mano u off-hand, NO derribar: dejar que el totem
            // lo salve normalmente (antes el plugin lo derribaba y el totem se buggeaba).
            if (hasTotem(p)) return;

            // MALDICION del Totem Maldito del Nether: si lo lleva en el inventario
            // y NO tiene un totem vanilla en la mano, muere de verdad (insta-muerte),
            // aunque tenga el Totem Bendito del Aether.
            if (cfg.boostItemEnabled && cfg.boostCurseInstaDeath
                    && plugin.items().hasInInventory(p, Items.BOOST)) {
                e.setCancelled(true);
                plugin.lang().chat(p, "nether-curse-death");
                plugin.getServer().getScheduler().runTask(plugin, () -> {
                    if (p.isOnline() && !p.isDead()) p.setHealth(0.0);
                });
                return;
            }

            e.setCancelled(true);
            manager.down(p);
        }
    }

    /** Bypass (NO derribarse). */
    private boolean hasBypass(Player p) {
        if (!p.hasPermission("ultrarevive.bypass")) return false;
        if (cfg.affectOps && p.isOp()) return false;
        return true;
    }

    /** Solo un totem VANILLA (sin nuestro tag) te auto-salva. */
    private boolean hasTotem(Player p) {
        return isVanillaTotem(p.getInventory().getItemInMainHand())
                || isVanillaTotem(p.getInventory().getItemInOffHand());
    }

    private boolean isVanillaTotem(ItemStack it) {
        return it != null
                && it.getType() == Material.TOTEM_OF_UNDYING
                && plugin.items().tagOf(it) == null;
    }

    /** Resuelve quien es el jugador atacante (directo o por proyectil). */
    private Player resolveAttacker(Entity damager) {
        if (damager instanceof Player) return (Player) damager;
        if (damager instanceof Projectile) {
            ProjectileSource src = ((Projectile) damager).getShooter();
            if (src instanceof Player) return (Player) src;
        }
        return null;
    }
}
