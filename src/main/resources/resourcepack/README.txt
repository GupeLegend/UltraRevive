UltraRevive - resource pack
===========================

UltraRevivePack.zip holds the textures of UltraRevive's three totems (Aether, Nether and End).
Without it, the plugin works exactly the same, but the three totems look like a normal Totem of
Undying, so players can't tell them apart in their inventory or on the ground.

UltraRevive doesn't send resource packs to players by itself. On startup it puts this pack inside
the plugin that builds your server's pack, so players keep getting a single pack. It does that
with any of these:

    ResourcePackManager, Nexo, Oraxen, ItemsAdder, CraftEngine, EcoItems

None of them was found on your server, so the pack was left here. You have three ways to load it:

1. Easiest: install one of those plugins and restart.
   ResourcePackManager (free, https://modrinth.com/plugin/resourcepackmanager) merges and hosts
   the packs of all your plugins. UltraRevive will find it on the next start and install itself.

2. If you already have your own server pack: open UltraRevivePack.zip and copy its "assets"
   folder into your pack (merge the folders). If your pack also changes the Totem of Undying,
   merge assets/minecraft/items/totem_of_undying.json by hand: it's the file that picks the
   texture of each totem by its custom model data.

3. If you don't have a pack: upload UltraRevivePack.zip somewhere with a direct download link,
   then set these in server.properties and restart:
       resource-pack=<direct link to the zip>
       resource-pack-sha1=<SHA-1 of the zip>

Bedrock players (Geyser) don't need any of this: UltraRevive installs their pack in Geyser
automatically.

This folder is written again on every start, so don't edit the files here.
