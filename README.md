<div align="center">

<img src="fastlane/metadata/android/en-US/images/icon.png" alt="Icono provisional de Sonet" width="144" />

# Sonet by Vryex

**Tu música. Tu espacio.**

Aplicación de música para Android, personalizada y desarrollada como una versión modificada de [Metrolist](https://github.com/MetrolistGroup/Metrolist).

**Creado y personalizado por Vryex**  
[Instagram: @whois.brxn_](https://www.instagram.com/whois.brxn_/)

[Características](#características-de-sonet) · [Cambios](ONYX_CHANGELOG.md) · [Créditos](CREDITS.md) · [Licencia](LICENSE)

</div>

---

## Sonet

Sonet es una versión modificada del cliente Android de código abierto Metrolist. Su identidad visual, diseño de pantallas y las modificaciones específicas de esta versión se desarrollan bajo la dirección de **Vryex**.

Esta no es una publicación oficial de Metrolist ni está afiliada a sus desarrolladores. Las contribuciones anteriores, el código de base y los derechos de sus autores se reconocen por separado en [CREDITS.md](CREDITS.md).

## Características de Sonet

Estas son **adaptaciones y mejoras de esta versión** sobre la base original, no una afirmación de que todas las funcionalidades de reproducción se crearon desde cero:

- **Identidad visual Sonet:** interfaz oscura minimalista, diseño de tarjetas redondeadas, reorganización de navegación y pantallas de inicio, biblioteca, artistas y reproductor.
- **Colores de portadas:** degradados adaptados a las portadas de canciones, artistas, álbumes y recomendaciones; color secundario para el control de reproducción.
- **Mini reproductor:** controles rediseñados, animación de barras durante la reproducción y adaptación al teclado de búsqueda.
- **Artistas y búsqueda:** perfiles visuales con color extraído de sus imágenes, tarjetas resaltadas y resultados con diseño actualizado.
- **Biblioteca y listas:** presentación simplificada, títulos y espacios mejorados, lista de «Tus me gusta» con un degradado basado en su contenido.
- **Letras y ajustes:** interfaz simplificada, traducciones propias y reorganización de los ajustes visibles sin suprimir los componentes internos que siguen utilizándose.
- **Optimización visual:** reutilización del extractor de colores de baja resolución y su caché, evitando aplicar desenfoques costosos a las listas.
- **Modo portada inmersiva:** un toque en la portada oculta los controles inferiores para destacar la imagen; otro toque los devuelve sin interrumpir la reproducción.
- **Reproducción de video experimental:** integración de visualización con portada de respaldo cuando el contenido no pueda mostrarse. La disponibilidad depende de la fuente y del dispositivo.

Algunas funciones —como reproducción de música, biblioteca, descargas, proveedores de letras y reproducción en segundo plano— proceden de la base de código original o de sus colaboradores y se mantienen con sus respectivos derechos.

## Desarrollo y versiones

El trabajo activo de personalización se desarrolla en la rama [`feat/onyx-liquid-v2`](https://github.com/vryex/Onyx-Music/tree/feat/onyx-liquid-v2).

- **Historial propio:** [ONYX_CHANGELOG.md](ONYX_CHANGELOG.md), actualizado con las funciones y mejoras de Sonet.
- **Compilaciones:** [GitHub Actions](https://github.com/vryex/Onyx-Music/actions), donde las ejecuciones exitosas pueden publicar paquetes APK en la sección *Artifacts*.
- **Historial heredado:** [changelog.md](changelog.md) conserva el historial de Metrolist.
- **Documentación original:** [UPSTREAM_README.md](UPSTREAM_README.md).

No se debe asumir que todo cambio documentado ya dispone de una APK publicada: comprueba la ejecución de GitHub Actions correspondiente.

## Autoría, créditos y licencia

**Vryex** es el responsable de la identidad, el diseño personalizado y las modificaciones específicas de Sonet; sus nuevas contribuciones quedan identificadas en el historial de Git y en [ONYX_CHANGELOG.md](ONYX_CHANGELOG.md).

**Metrolist y sus colaboradores** son los autores de la base original y conservan sus avisos de copyright y atribución. Puedes consultar sus créditos en [CREDITS.md](CREDITS.md), la [documentación original](UPSTREAM_README.md) y el [proyecto de origen](https://github.com/MetrolistGroup/Metrolist).

Sonet, en cuanto obra derivada del código cubierto, se distribuye bajo **GNU GPL-3.0**; se conserva una copia de la licencia en [LICENSE](LICENSE). La redistribución de la APK debe respetar los requisitos de la GPL, incluido el acceso al código fuente correspondiente y la preservación de los avisos legales existentes. Los archivos con licencias distintas mantienen los términos que les correspondan.

**Identidad de producto:** Sonet — **Vryex**  
**Base de código:** Metrolist y sus respectivos colaboradores  
**Modificaciones documentadas:** 2026-10-17

