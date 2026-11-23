# Ruta Auto (Fabric 1.21.11)

Mod de cliente que graba una ruta (WASD, salto, shift, sprint y cámara) y la repite cada X segundos.

## Uso
- **J** → empezar / parar grabación (se guarda en `config/rutaauto.json`)
- **K** → activar / desactivar la repetición
- Comandos: `/rutaauto toggle | record | clear | status | interval <segundos> | camera <true|false>`

La ruta se repite desde donde estés, con la cámara relativa a tu orientación inicial.
Se pausa si abres el inventario/chat y se detiene al desconectarte.

## Compilar
Copia la carpeta `src/` y `gradle.properties`, `build.gradle`, `settings.gradle` sobre un proyecto
de ejemplo de Fabric (que trae el gradle wrapper), o abre esta carpeta en IntelliJ IDEA (JDK 21)
y ejecuta la tarea `build`. El .jar sale en `build/libs/ruta-auto-1.0.0.jar`.

Requiere Fabric Loader >= 0.18 y Fabric API para 1.21.11.

Nota: en servidores multijugador esto es una macro; muchos lo prohíben.
