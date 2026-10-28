<img src="pixscape_logo.png" alt="Pixscape logo" width="80">

# Pixscape Studio Free

[![Version](https://img.shields.io/badge/version-0.4.0-orange.svg)](CHANGELOG.md)<br>
[![Platforms](https://img.shields.io/badge/platforms-Windows%20%7C%20Linux%20%7C%20macOS%20%7C%20Android%20%7C%20HTML5-green.svg)](#get-started)<br>
[![Java](https://img.shields.io/badge/Java-25-blue.svg)](#build-from-source)<br>
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)<br>

**Create 2.5D worlds from 2D artwork. Build visually. Write gameplay in Java.**

Give your sprites and tiles depth with elevated scenery, characters moving in front of and behind walls, dynamic lighting and cast shadows. Pixscape Studio Free brings these tools together in an open-source visual editor built on LibGDX, with support for traditional 2D games too.

[Download Studio](https://pixscape.games/) · [Documentation](https://pixscape.games/docs) · [Runtime](https://github.com/pixscapegames/pixscape-runtime) · [Changelog](CHANGELOG.md)

![Pixscape Studio Free](assets/readme/pixscape-studio-free.png)

## Bring your 2D artwork into a 2.5D world

- **Build depth into your scene.** Author wall volumes, heights and actor footprints. Spatial ordering handles characters moving around the scenery in orthographic and isometric worlds.
- **Light the world.** Place point and cone lights, cast shadows onto scenery and actors, and add optional normal maps. Position actor shadows with a simple rectangle and contact pivot.
- **Work directly on the canvas.** Drag in sprites, animations and particles and see them immediately while atlases pack in the background. Edit multiple scenes with undo/redo.
- **Bring your Tiled maps.** Import TMX/TSX maps and tilesets, including orthographic and isometric layouts, object layers and animated tiles.
- **Compose reusable Game Objects.** Combine scene entities into reusable hierarchies, with animation, Box2D bodies and joints.
- **Design your HUD visually.** Arrange widgets, edit their properties and try the interface in interactive test mode.
- **Preview desktop and web.** Run your scene in desktop or HTML Preview; target Windows, Linux, macOS, Android and HTML5/WebGL2 through Pixscape Runtime.

## From editor to game

1. **Create or import** your assets and scenes.
2. **Configure** animation, physics, lighting, particles and HUD screens.
3. **Preview and export**, then write gameplay in Java using Pixscape Runtime.

Studio and Runtime are both open source under Apache 2.0. LibGDX stays available for your own code and ecosystem libraries.

**Studio 0.4.0 uses Runtime 0.2.2.** Legacy rendering supports multiple lights; experimental Forward rendering supports at most one active light. Normal maps are a scene-wide option.

## Get started

[Download Pixscape Studio Free](https://pixscape.games/) and follow the [documentation](https://pixscape.games/docs).

The editor runs on **Windows, Linux and macOS**. Games target these desktop platforms, **Android** and **HTML5/WebGL2** through Pixscape Runtime.

Studio desktop uses **Java 25**. Building the HTML player uses **JDK 21**. Runtime is published as Java 8-compatible bytecode. iOS/RoboVM is not currently an officially tested target.

## Build from source

<details>
<summary><strong>Build, test and refresh HTML Preview</strong></summary>

Use JDK 25 for Studio and JDK 21 for HTML/GWT, selected through Gradle toolchains.

```sh
./gradlew --console=plain compileJava test :html-player:compileJava
```

To rebuild the embedded HTML Preview template:

```sh
./gradlew buildHtmlPreviewTemplate
```

On Windows, use `gradlew.bat` instead of `./gradlew`.

The prebuilt player in `src/main/resources/html-preview-template/` is intentionally versioned so contributors can use HTML Preview without rebuilding GWT first. Temporary Gradle and build outputs are not committed.

</details>

<details>
<summary><strong>Linux distribution packages</strong></summary>

```sh
./gradlew linuxTarGz
./gradlew appImage -Pappimagetool=/path/to/appimagetool
```

Packages are written to `build/distributions/`. AppImage requires an external `appimagetool`, supplied by the property above, `APPIMAGETOOL`, or `PATH`.

</details>

## License

Source code is licensed under [Apache 2.0](LICENSE); see also [NOTICE](NOTICE).
Pixscape names, logos and branding are covered separately by the [trademark policy](TRADEMARK.md).
