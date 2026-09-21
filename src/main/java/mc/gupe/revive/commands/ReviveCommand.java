package mc.gupe.revive.commands;

import mc.gupe.revive.UltraRevive;
import mc.gupe.revive.Items;
import mc.gupe.revive.Downed;
import mc.gupe.revive.Lang;
import mc.gupe.revive.StatsManager;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.command.Command;
import org.bukkit.command.CommandExecutor;
import org.bukkit.command.CommandSender;
import org.bukkit.command.TabCompleter;
import org.bukkit.entity.Player;
import org.bukkit.util.StringUtil;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

public final class ReviveCommand implements CommandExecutor, TabCompleter {

    private final UltraRevive plugin;

    public ReviveCommand(UltraRevive plugin) {
        this.plugin = plugin;
    }

    @Override
    public boolean onCommand(CommandSender s, Command cmd, String label, String[] args) {
        Lang lang = plugin.lang();

        // RENDIRSE: cualquier jugador DERRIBADO puede hacerlo (sin permiso admin).
        // Esto lo dispara el boton de Rendirse del chat.
        if (args.length >= 1 && args[0].equalsIgnoreCase("surrender")) {
            if (s instanceof Player) {
                Player ps = (Player) s;
                Downed d = plugin.manager().downedMap().get(ps.getUniqueId());
                if (d != null) {
                    if (!ps.hasPermission("ultrarevive.surrender")) {
                        ps.sendMessage(lang.pre("surrender-no-permission"));
                    } else {
                        int cd = plugin.settings().surrenderCooldownSecs;
                        // usar la duracion REAL del derribo (d.totalTicks), no la global:
                        // con fatiga, un jugador fatigado arranca con menos ticks y saltaba el cooldown.
                        int elapsed = (d.totalTicks - d.ticksLeft) / 20;
                        if (cd > 0 && elapsed < cd) {
                            ps.sendMessage(lang.pre("surrender-cooldown", "{secs}", String.valueOf(cd - elapsed)));
                        } else {
                            plugin.manager().surrender(d);
                        }
                    }
                }
            }
            return true;
        }

        // AUTO-REVIVIR: lo dispara el boton de Auto-Revivir. Gasta el totem del inventario.
        if (args.length >= 1 && args[0].equalsIgnoreCase("selfrevive")) {
            if (s instanceof Player) {
                Player ps = (Player) s;
                if (plugin.manager().isDowned(ps.getUniqueId())) {
                    Downed d = plugin.manager().downedMap().get(ps.getUniqueId());
                    if (plugin.settings().selfItemEnabled && plugin.items().hasInInventory(ps, Items.SELF)) {
                        plugin.items().consumeFromInventory(ps, Items.SELF);
                        plugin.manager().selfRevive(d);
                    } else if (plugin.settings().endItemEnabled && plugin.items().hasInInventory(ps, Items.END)) {
                        plugin.items().consumeFromInventory(ps, Items.END);
                        plugin.manager().endSelfRevive(d);
                    } else {
                        ps.sendMessage(lang.pre("selfrevive-no-item"));
                    }
                }
            }
            return true;
        }

        // ESTADISTICAS: /ur stats [jugador]  (sin jugador = las tuyas; con jugador = admin)
        if (args.length >= 1 && args[0].equalsIgnoreCase("stats")) {
            UUID target; String name;
            if (args.length >= 2) {
                if (!s.hasPermission("ultrarevive.admin")) { s.sendMessage(lang.pre("cmd-no-permission")); return true; }
                OfflinePlayer op = Bukkit.getOfflinePlayer(args[1]);
                target = op.getUniqueId(); name = args[1];
            } else if (s instanceof Player) {
                target = ((Player) s).getUniqueId(); name = s.getName();
            } else { s.sendMessage(lang.pre("cmd-usage")); return true; }

            StatsManager st = plugin.stats();
            s.sendMessage(lang.pre("stats-header", "{player}", name));
            s.sendMessage(lang.get("stats-downed", "{n}", String.valueOf(st.get(target, "downed"))));
            s.sendMessage(lang.get("stats-revived", "{n}", String.valueOf(st.get(target, "revived"))));
            s.sendMessage(lang.get("stats-revives", "{n}", String.valueOf(st.get(target, "revives"))));
            s.sendMessage(lang.get("stats-self-revives", "{n}", String.valueOf(st.get(target, "self-revives"))));
            s.sendMessage(lang.get("stats-bled-out", "{n}", String.valueOf(st.get(target, "bled-out"))));
            return true;
        }

        // /ur sin nada = la ayuda.
        if (args.length == 0) {
            sendHelp(s);
            return true;
        }
        // AYUDA: /ur help.
        if (args[0].equalsIgnoreCase("help") || args[0].equalsIgnoreCase("ayuda")) {
            sendHelp(s);
            return true;
        }

        if (!s.hasPermission("ultrarevive.admin")) {
            s.sendMessage(lang.pre("cmd-no-permission"));
            return true;
        }

        String sub = args[0].toLowerCase();
        if (sub.equals("reload")) {
            plugin.reload();
            s.sendMessage(plugin.lang().pre("cmd-reload"));
            return true;
        }

        // DAR ITEMS: /ur give <self|boost> [jugador] [cantidad]
        if (sub.equals("give") || sub.equals("dar")) {
            if (args.length < 2) { s.sendMessage(lang.pre("cmd-usage")); return true; }
            String t = args[1].toLowerCase();
            String tag = t.startsWith("self") || t.startsWith("aether") || t.startsWith("jer") ? Items.SELF
                       : (t.startsWith("boost") || t.startsWith("nether") || t.startsWith("kit") ? Items.BOOST
                       : (t.startsWith("end") ? Items.END : null));
            if (tag == null) { s.sendMessage(lang.pre("cmd-usage")); return true; }

            Player who = (args.length >= 3) ? Bukkit.getPlayerExact(args[2])
                       : (s instanceof Player ? (Player) s : null);
            if (who == null) { s.sendMessage(lang.pre("cmd-player-not-found")); return true; }

            int amount = 1;
            if (args.length >= 4) { try { amount = Math.max(1, Integer.parseInt(args[3])); } catch (Exception ignored) {} }

            who.getInventory().addItem(plugin.items().build(tag, amount));
            s.sendMessage(lang.pre("cmd-item-given", "{amount}", String.valueOf(amount), "{target}", who.getName()));
            return true;
        }

        if (args.length < 2) { s.sendMessage(lang.pre("cmd-usage")); return true; }
        Player target = Bukkit.getPlayerExact(args[1]);
        if (target == null) { s.sendMessage(lang.pre("cmd-player-not-found")); return true; }

        switch (sub) {
            case "revive":
                if (!plugin.manager().isDowned(target.getUniqueId())) { s.sendMessage(lang.pre("cmd-not-downed")); return true; }
                plugin.manager().revive(plugin.manager().downedMap().get(target.getUniqueId()), null);
                s.sendMessage(lang.pre("cmd-revived-by-admin", "{target}", target.getName()));
                return true;
            case "down":
                if (plugin.manager().isDowned(target.getUniqueId())) { s.sendMessage(lang.pre("cmd-already-downed")); return true; }
                plugin.manager().down(target);
                s.sendMessage(lang.pre("cmd-downed-by-admin", "{target}", target.getName()));
                return true;
            case "finish":
            case "kill":
                if (!plugin.manager().isDowned(target.getUniqueId())) { s.sendMessage(lang.pre("cmd-not-downed")); return true; }
                plugin.manager().bleedOut(plugin.manager().downedMap().get(target.getUniqueId()));
                s.sendMessage(lang.pre("cmd-finished-by-admin", "{target}", target.getName()));
                return true;
            default:
                s.sendMessage(lang.pre("cmd-usage"));
                return true;
        }
    }

    /** Ayuda: lista TODOS los comandos del plugin, agrupados. Admin ve además los de admin. */
    /**
     * La ayuda de ESTE plugin y nada mas.
     *
     * ⚠ Antes listaba clanes, mochilas, el panel de staff y la moderacion, porque todo eso vivia
     * adentro. Ahora son plugins aparte: publicitar esos comandos desde aca seria mandar a la
     * gente a un "comando desconocido" si no los tienen instalados. Para todo lo demas se manda a
     * /uh, que es el que sabe que hay instalado.
     */
    private void sendHelp(CommandSender s) {
        boolean admin = s.hasPermission("ultrarevive.admin");
        s.sendMessage(Lang.color("&8&m                                        "));
        s.sendMessage(Lang.color("        &6&lUltraRevive &7- &fComandos"));
        s.sendMessage(Lang.color("&8&m                                        "));

        s.sendMessage(Lang.color("&e▸ Jugador"));
        s.sendMessage(Lang.color("  &f/ur stats &7[jugador] &8- cuantas veces te levantaron y levantaste"));
        s.sendMessage(Lang.color("  &f/ur surrender &8- rendirte (si estas derribado)"));
        s.sendMessage(Lang.color("  &8Estando derribado te salen botones en el chat."));

        if (admin) {
            s.sendMessage(Lang.color("&c▸ Admin"));
            s.sendMessage(Lang.color("  &f/ur reload &8- recarga config e idiomas"));
            s.sendMessage(Lang.color("  &f/ur revive|down|finish <jugador> &8- gestionar derribados"));
            s.sendMessage(Lang.color("  &f/ur give <self|boost|end> &7[jug] [n] &8- dar los totems"));
        }

        s.sendMessage(Lang.color("&8&m                                        "));
    }

    @Override
    public List<String> onTabComplete(CommandSender s, Command cmd, String label, String[] args) {
        List<String> out = new ArrayList<>();
        if (args.length == 1) {
            StringUtil.copyPartialMatches(args[0], Arrays.asList("help", "reload", "revive", "down", "finish", "give", "stats"), out);
        } else if (args.length == 2 && args[0].equalsIgnoreCase("give")) {
            StringUtil.copyPartialMatches(args[1], Arrays.asList("self", "boost", "end"), out);
        } else if (args.length == 3 && args[0].equalsIgnoreCase("give")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            StringUtil.copyPartialMatches(args[2], names, out);
        } else if (args.length == 2 && !args[0].equalsIgnoreCase("reload")) {
            List<String> names = new ArrayList<>();
            for (Player p : Bukkit.getOnlinePlayers()) names.add(p.getName());
            StringUtil.copyPartialMatches(args[1], names, out);
        }
        return out;
    }
}
