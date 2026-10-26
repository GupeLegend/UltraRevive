# Changelog — UltraRevive

## 1.0 — 2026-10-24

Primera versión pública. Antes el plugin era parte de un jar todo-en-uno privado; ahora sale solo con lo
suyo: derribar, revivir y los tres tótems.

Funciona en **Paper, Purpur y Folia**, en **26.1, 26.2 y 26.3** (Java 25), con un solo jar. Se compila contra la versión más vieja
(`paper-api 26.1.2.build.74-stable`) y `api-version: '26.1'`, así que también lo aceptan 26.2 y 26.3.
Probado con bots en 26.1.2, 26.2 y 26.3, 20 de 20 en cada una: derribo, rescate, los tres tótems con botón
y clic derecho, maldición, zona de curación, que los mobs no rematen, que el vacío mate, rendirse,
`/ur finish`, sin hambre no se reanima, `/ur stats` y `/ur reload`.

### Paper, Purpur y Folia
- Cada derribado tiene su propio ciclo en el planificador de su entidad (antes había uno solo para todos):
  en Paper y Purpur funciona igual que siempre y en Folia corre en el hilo de la región donde está. Lo que
  pasa en un lugar (la zona de curación del End) va en el planificador de ese lugar, y los comandos de
  admin actúan en el hilo del jugador afectado. `folia-supported: true`.
- En Folia el brillo del derribado sale blanco: Folia no tiene equipos del scoreboard, que son los que le
  dan el color rojo.

### Texturas en Java
- UltraRevive **no manda resource packs** ni abre puertos. Al arrancar mete sus
  texturas dentro del plugin que arma el pack del servidor, así los jugadores reciben un solo pack. Funciona
  con **ResourcePackManager, Nexo, Oraxen, ItemsAdder, CraftEngine y EcoItems** (si hay varios, en ese orden).
- Lo hace antes de que ese plugin arranque (`loadbefore`), así que el pack sale con los tótems en el mismo
  arranque. ItemsAdder y CraftEngine solo arman su pack con un comando: UltraRevive corre `/iazip` o
  `/ce reload pack` cuando el servidor termina de arrancar, y solo si sus texturas cambiaron.
- Si no hay ninguno, deja `plugins/UltraRevive/resourcepack/UltraRevivePack.zip` con un `README.txt` que
  explica cómo cargarlo, y lo avisa en consola con una ADVERTENCIA.
- `config.yml`: `resource-pack.install: auto` (o el nombre de un plugin, o `none`). Si una instalación vieja
  dejó un `resourcepack.zip` en la carpeta del plugin, se borra solo.
- Probado con EcoItems, ResourcePackManager, CraftEngine y sin ninguno. Nexo, Oraxen e ItemsAdder siguen su
  documentación oficial, pero no se pudieron probar (son de pago).

### Texturas en Bedrock
- Si el servidor tiene **Geyser**, el plugin le instala solo un pack de Bedrock (`packs/UltraRevive.mcpack`) y
  el mapeo de los tótems (`custom_mappings/ultrarevive.json`): en consola y celular los tres tótems se ven
  igual que en Java. Se hace antes de que arranque Geyser (`loadbefore`), así que funciona en el mismo
  arranque, sin reiniciar dos veces.
- El mapeo sale de `config.yml`: si se cambia el `custom-model-data` o el material de un tótem, Bedrock lo
  sigue sin tocar nada.
- Si Geyser corre en el proxy, los dos archivos quedan en `plugins/UltraRevive/bedrock/` para copiarlos allá.
- `resource-pack.bedrock: true` en `config.yml` lo activa o desactiva.

### Sonidos y clic derecho
- Auto-revivir con **clic derecho** funciona con el Tótem del Aether y con el del End, además del botón del chat.

- Los sonidos se buscan en el registro del juego, porque `Sound.valueOf` está marcado para eliminarse en 26.3.
  Se aceptan los dos nombres: el de Bukkit (`ITEM_TOTEM_USE`) y la clave del juego (`item.totem.use`).
- `sound-surrender` por defecto pasa a `ITEM_TOTEM_USE`. `ENTITY_TOTEM_USE` nunca existió en Bukkit y daba
  `Invalid sound` al arrancar; las configs viejas que lo traen siguen andando porque se toma como alias.

### Bedrock (consolas)
- En consola no se puede hacer clic en el chat, así que los jugadores de Bedrock (con **Floodgate**) reciben un
  **menú con botones grandes** al caer derribados: *Esperar rescate*, *Rendirse* y *Auto-Revivir (tótem)*. Los
  botones usan los mismos comandos que el chat, así que los permisos y el `surrender-cooldown` se respetan igual.
- Si cierran el menú, **agacharse dos veces** lo vuelve a abrir.
- Los jugadores de Java siguen con los botones del chat.
- `bedrock-forms: true` en `config.yml` lo activa o desactiva. Los textos (`bedrock-form-*`) están en los dos
  idiomas y se agregan solos a los archivos de idioma del servidor.
- Floodgate es opcional (softdepend). Sin él, todo funciona como antes.


### Derribado y rescate
- El golpe que te mataría te deja **derribado** en vez de muerto:
  - quedas en el piso, arrastrándote, en modo aventura y con brillo rojo;
  - tienes Lentitud y Ceguera, y una barra de jefe que cuenta los segundos que te quedan (60 por
    defecto).
- Un compañero **se agacha a tu lado** 5 segundos y te levanta. Le cuesta hambre, y con hambre 0 no
  puede reanimarte.
- **Rescate cooperativo**: si te reaniman varios a la vez, es más rápido. Si el que reanima se
  aleja, el progreso se congela un momento y después baja de a poco; no se pierde de golpe.
- **Botones en el chat**: `[Rendirse]` para morir ya, y `[Auto-Revivir]` si tienes un tótem que lo
  permita.
- Mientras estás derribado no puedes atacar, romper ni poner bloques, soltar ítems ni comer.
  - Los mobs y el entorno no te rematan; otro jugador sí.
  - El vacío mata igual.
  - Si te desconectas derribado, mueres.
- **Tensión en los últimos segundos**: suena un latido que se acelera y salen partículas de sangre.
- **Baliza**: un haz de partículas sobre el caído para encontrarlo.
- Opcionales: que te interrumpan la reanimación si te pegan, y la fatiga, que acorta el tiempo si
  te derriban varias veces seguidas.
- Anuncios de derribos, rescates y desangrados a los jugadores cercanos, con sonido propio para
  cada uno.
- **Estadísticas por jugador** (`/ur stats`): derribos, veces revivido, rescates hechos,
  auto-revividas y desangrados.

### Los tres tótems
Son ítems propios, con textura y una marca oculta que impide falsificarlos. Llevar uno **no evita**
que te derriben; así el botón de auto-revivir tiene sentido.

| Tótem | Dónde sale | Qué hace |
|---|---|---|
| **Bendito del Aether** | cofres de **tesoro enterrado** (20%) y **pesca** (0,5%) | Te levantas solo (botón o click derecho), con Absorción, Regeneración y un poco de mareo. |
| **Maldito del Nether** | portales en ruinas (0,67%), bastiones y fortalezas (1,2%) | Quien lo lleva revive a otros **×3 más rápido** y con la vida llena, pero recibe 20 s de Marchitamiento. Si mueres llevándolo, **no hay derribo: mueres de una**. |
| **Desconocido del End** | cofres de los barcos del End (0,00067%) | Te levantas solo **y te manda a tu cama**, con 2 minutos de ceguera. Si lo usas para revivir a otro, deja un área de curación. |

- Sin el resource pack los tótems se ven como tótems normales; todo lo demás funciona igual (ver
  *Texturas en Java* arriba).

### Comandos y permisos
- `/ur` (alias `revivir`):
  - `help`, `surrender`, `selfrevive`, `stats [jugador]`;
  - para admins: `reload`, `revive`, `down`, `finish` y `give <self|boost|end> [jugador] [cantidad]`.
- Permisos:
  - `ultrarevive.admin` (op);
  - `ultrarevive.bypass` (nunca te derriban);
  - `ultrarevive.revive` y `ultrarevive.surrender` (todos).

  Los permisos viejos `ultrarevivir.*` siguen funcionando.

### Configuración e idiomas
- `config.yml` controla todo:
  - tiempos, vida, efectos y colores;
  - qué se puede hacer derribado, el rescate cooperativo, la fatiga y la baliza;
  - sonidos, el pack y la probabilidad de cada tótem por estructura.
- Dos idiomas, **inglés y español**. `language:` cambia los mensajes **y** los nombres y
  descripciones de los tótems.
- Mundos donde funciona: `enabled-worlds` (vacío = todos).

### Carpeta propia
- Los datos se guardan en **`plugins/UltraRevive/`**, como cualquier plugin. Si tu servidor tenía
  los datos en la carpeta compartida `plugins/UltraView/UltraRevive/`, al arrancar se mueven solos a
  la nueva: config, idiomas y estadísticas.
  - Nada se pisa: si algo no se puede mover, la carpeta vieja se queda como estaba.
  - La carpeta vieja se borra sola cuando queda vacía.

### Para desarrolladores
- **API pública** (`UltraReviveAPI.isDowned`, `isReviving`) y los eventos `PlayerDownedEvent` y
  `PlayerRevivedEvent`.
- El jugador derribado lleva el metadata `ultrarevive_downed`.
- Código ordenado por paquetes:
  - `api`, `command`, `config`, `item`, `listener`, `migration`;
  - `model`, `pack`, `revive`, `stats`, `util`.
- Se compila con **Gradle** (`./gradlew build`) o sin Gradle con `compilar.sh`.

### Arreglos
- La API pública buscaba el plugin por un nombre que ya no existía y devolvía siempre null.
- Rematar a un derribado procesaba dos muertes por un mismo golpe y se perdía el botín. Ahora la
  muerte se aplica un tick después.
- Los tótems entregados en instalaciones anteriores se siguen reconociendo.
