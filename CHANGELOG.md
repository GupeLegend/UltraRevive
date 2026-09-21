# Changelog — UltraRevive

## 1.0 — 2026-09-10

Primera versión pública, para **Minecraft 26.1.2**. Antes de esto el plugin era parte de un jar
todo-en-uno privado; acá sale solo con lo suyo: derribar, revivir y los tres tótems.

### Funciona así
- Sistema de **derribado**: el golpe mortal te deja en el piso, en modo aventura, con brillo rojo
  y una cuenta regresiva. Un compañero te levanta agachándose al lado.
- Botones clickeables en el chat para **rendirse** y **auto-revivirte**.
- **Tres tótems** con marca propia (no falsificables) y su resource pack, que cambian las reglas
  del rescate. Rareza x3 respecto a versiones internas anteriores.
- **8 idiomas**: es en pt ru de fr ja zh.
- **API pública** (`UltraReviveAPI`) + eventos `PlayerDownedEvent` y `PlayerRevivedEvent`.
- Guarda en `plugins/UltraView/UltraRevive/`.

### Arreglado al publicar
- `UltraReviveAPI.plugin()` buscaba el plugin por un nombre que ya no existía, así que **toda la
  API pública devolvía null**.
- La muerte del jugador derribado ya no ocurre dentro del evento de daño: se difiere un tick. Antes
  se procesaban dos muertes para el mismo golpe y el botín se perdía.
- Los tótems de instalaciones anteriores se siguen reconociendo (ver `Legacy`).
