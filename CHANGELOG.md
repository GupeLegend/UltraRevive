# Changelog — UltraRevive

## 1.0 — 2026-10-15

Primera versión pública, para **Paper 26.1.2** (Java 25). Antes el plugin era parte de un jar
todo-en-uno privado; ahora sale solo con lo suyo: derribar, revivir y los tres tótems.

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

- El resource pack de los tótems lo sirve el propio plugin (puerto 8081), o puedes usar tu propia
  URL. Si el puerto no está abierto, lo avisa en consola y los tótems se ven como tótems normales.

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
