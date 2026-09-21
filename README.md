# UltraRevive

[![Minecraft](https://img.shields.io/badge/Minecraft-26.1.2-brightgreen)](https://papermc.io)
[![Paper](https://img.shields.io/badge/Paper-26.1.2%2B-blue)](https://papermc.io)
[![Java](https://img.shields.io/badge/Java-25-orange)](https://adoptium.net)
[![License](https://img.shields.io/badge/License-MIT-yellow)](LICENSE)

**Don't die on the first hit.** You go down instead — prone, glowing, unable to fight — and your
team has a few seconds to pick you back up.

A Paper plugin by **Social Studio**.

---

## What it does

When a hit would kill you, you drop to the floor with a boss bar counting down your last seconds.
A teammate crouches next to you to revive you. Nobody comes, and you bleed out and die for real.

Three totems — real items with their own textures and an un-fakeable hidden tag — change the rules
of the rescue:

| Totem | Drops from | Effect |
|---|---|---|
| **Blessed Totem of the Aether** | Fishing, 0.67% | Get yourself back up. |
| **Cursed Totem of the Nether** | Ruined portals 0.67%, bastions and fortresses 1.2% | Revive others 3× faster — but **die outright** if you're carrying it. |
| **Unknown Totem of the End** | End cities, 0.00067% | Get up **and teleport home**; leaves a healing area when used on someone else. |

Carrying a totem does **not** stop you from going down. That's deliberate — it's what makes the
self-revive button meaningful.

## Commands and permissions

```
/ur                              help
/ur reload                       reload config and language
/ur revive|down|finish <player>  manage downed players   (admin)
/ur give <self|boost|end> [p]    hand out the totems     (admin)
/ur stats [player]               rescue statistics
/ur surrender                    give up while downed
```

| Permission | Default |
|---|---|
| `ultrarevive.admin` | op |
| `ultrarevive.bypass` | `false` — that player dies normally, never goes down |
| `ultrarevive.revive` | `true` |
| `ultrarevive.surrender` | `true` |

## Requirements

- **Paper 26.1.2+** (Spigot won't work — this uses the Adventure API)
- **Java 25** (required by Paper 26, not by us)

No hard dependencies. LuckPerms, LPC, ItemEdit and ItemTag are optional and only add small
conveniences.

## Install

Drop the jar in `plugins/` and start the server. Everything is configured and working out of the
box.

Data lives in **`plugins/UltraView/UltraRevive/`**, not in the plugin's own folder — so everything
from Social Studio sits together. Your config is never overwritten on update.

The plugin serves its own resource pack for the totem textures over HTTP (port 8081 by default).
If the port isn't open it says so in console and disables the pack; the totems then look like
vanilla totems and the rest works normally.

---

## Building

No Gradle, no Maven — just `javac`.

```bash
bash compilar.sh          # -> ../../Jars/UltraRevive-1.0.jar
```

The script downloads its dependencies to `../_libs/` on first run. You need **JDK 25**: Paper 26 is
compiled for Java 25, and with an older JDK `javac` fails with a `cannot access Player` error that
looks like an API problem but isn't.

## Project layout

```
src/main/java/mc/gupe/revive/
  UltraRevive.java        startup, listener and command registration
  ReviveManager.java      the core: going down, the countdown, reviving, bleeding out
  Items.java              builds and recognises the three totems
  Settings.java           reads config.yml into typed fields
  listeners/              combat, connection, loot, restrictions, stats
  api/                    public API and events
  commands/               /ur

src/main/resources/
  config.yml              every timer, health value, effect and drop chance
  lang/{en,es}.yml        all player-facing text, including item names and lore
  resourcepack.zip        the totem textures, served by the plugin
```

## Internationalisation

Two languages: **English** (default) and **Spanish**. `language:` in `config.yml` switches
*everything* — chat messages **and** the totem names and lore.

Item names live in `lang/<lang>.yml`, not in `config.yml`, on purpose: if they lived in the config,
changing the language would translate the messages but leave the items in whatever language the
file was first generated with.

## Public API

Other plugins can ask whether a player is down, without a compile-time dependency:

```java
UltraReviveAPI.isDowned(uuid);
UltraReviveAPI.isReviving(uuid);
```

Plus the Bukkit events `PlayerDownedEvent` and `PlayerRevivedEvent`.

## Notes for contributors

Three things in here are load-bearing and easy to break:

**Never kill inside a damage event.** When a downed player takes the finishing blow, the kill is
deferred one tick. Killing inline nests a death inside the damage pipeline and the server processes
two deaths for one hit — the inventory empties on the first and the second takes the loot with it.
This bit us in production.

**Never `setOwningPlayer(OfflinePlayer)` in a menu.** If that player's profile isn't cached, Paper
goes and asks Mojang for the textures **from the main thread**. Use the `Heads` cache instead.

**The hidden tag on an item carries the plugin name.** `new NamespacedKey(plugin, "ur_item")`
stores it as `ultrarevive:ur_item`. Rename the plugin and every totem already handed out stops
being recognised — they become plain totems of undying. That's what `Legacy` is for; don't delete
it.

Code comments and config files are written in Spanish.

## License

MIT — see [LICENSE](LICENSE). Use it, change it, ship it; just keep the copyright notice.
