# Sonet — Registro de cambios

**Autor y mantenimiento de esta versión:** Vryex  
**Base de código:** [Metrolist](https://github.com/MetrolistGroup/Metrolist) (véase [CREDITS.md](CREDITS.md))  
**Licencia del código derivado:** [GPL-3.0](LICENSE)

Este archivo documenta las modificaciones propias de Sonet; el historial del proyecto original permanece en [changelog.md](changelog.md).

## 2026-10-17 — Personalización Sonet (rama de desarrollo)

### Identidad y experiencia visual
- Identidad de **Sonet** y perfil de **Vryex** en la pantalla «Sobre».
- Rediseño oscuro y minimalista con controles redondeados, navegación y secciones reorganizadas.
- Mejoras de presentación en inicio, biblioteca, resultados de búsqueda, álbumes, listas, artistas y pantalla de reproducción.
- Eliminación de contornos y elementos decorativos innecesarios en distintos controles.

### Colores y portadas
- Degradados dinámicos extraídos de las portadas en el reproductor y las páginas de artistas y álbumes.
- Tarjetas de artistas y Recomendaciones con degradados del arte asociado.
- Colores secundarios de las portadas para diferenciar el botón de reproducción del fondo.
- Extractor de paleta de baja resolución con caché, priorizando el rendimiento.
- Degradado de «Tus me gusta» basado en la portada de su primera canción.
- Restauración de **negro puro** en la cola de reproducción.

### Reproductor
- Mini reproductor con información y controles rediseñados.
- Indicador de barras animadas sobre la portada cuando la reproducción está activa.
- Adaptación de la posición del mini reproductor al teclado del buscador.
- Personalización de barra de progreso y ajustes visuales del reproductor principal.
- Modo portada inmersiva: tocar la portada oculta los controles del reproductor sin interrumpir la música y otro toque los recupera.
- Trabajo experimental en reproducción de video integrada y comportamiento de respaldo con portada; la reproducción real depende del contenido y entorno.

### Búsqueda, biblioteca y letras
- Resultados de artistas resaltados con un degradado horizontal.
- Eliminación de rótulos innecesarios relacionados con el origen de las búsquedas.
- Mejoras de espacio y presentación del texto en tarjetas y listas.
- Traducciones propias de Sonet al español.
- Simplificación de ajustes de contenido, apariencia y pantalla «Sobre», sin eliminar las funciones internas heredadas necesarias.
- Limpieza visual de las etiquetas de proveedor dentro del visor de letras.

### Notas
- Este es un **registro de trabajo de la rama**, no un anuncio de versión estable.
- Los cambios de origen siguen perteneciendo a sus autores; este documento recoge las modificaciones de Sonet.
- Cada cambio adicional debe registrarse aquí, con fecha y descripción, para identificar qué se ha mejorado y quién mantiene esta versión.
