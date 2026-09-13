<img src="pixscape_logo.png" alt="Pixscape logo" width="80">

<h1>Pixscape Studio Free</h1>

[![Changelog](https://img.shields.io/badge/changelog-0.3.0-orange.svg)](CHANGELOG.md)<br>
[![Platforms](https://img.shields.io/badge/platforms-Desktop%20%7C%20Android%20%7C%20HTML5-green.svg)](#)<br>
[![Java version](https://img.shields.io/badge/Java%20version-21-blue.svg)](#)<br>
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Visual authoring for Pixscape, a 2D and 2.5D game engine built on LibGDX.**

Build your game visually. Write gameplay in Java. Keep using LibGDX.

🌐 **Website:** https://pixscape.games/</br>
📘 **Documentation:** https://pixscape.games/docs</br>
⚙️ **Runtime:** https://github.com/pixscapegames/pixscape-runtime</br>
📝 **Changelog:** [CHANGELOG.md](CHANGELOG.md)</br>

<p align="center">
  <img src="assets/readme/pixscape-studio-free.png" alt="Pixscape Studio Free" width="100%">
</p>

## What is Pixscape?

Pixscape is a visual 2D and 2.5D game engine built on LibGDX.

It combines **Pixscape Studio**, the visual authoring environment, with
**Pixscape Runtime**, the engine layer that loads and runs authored scenes.

Use Studio to build scenes, tiled and isometric worlds, physics, animations,
particles, Game Objects, shaders, lights and 2.5D spatial environments.

Your gameplay and application logic stay in **Java**. LibGDX remains underneath
Pixscape and stays available alongside it.

Pixscape adds an opinionated game-engine layer rather than replacing LibGDX:
scene management, ECS-based entities, rendering and ordering, asset coordination,
physics, Tiled integration, animation, particles and Spatial 2.5D are handled by
the engine so game code can focus on gameplay.

## Open-source foundation

Pixscape Studio Free is open source so developers can inspect it, build it, modify it, fork it if necessary, and trust it as a real foundation for real projects.

Pixscape Runtime is also open source under Apache License 2.0.

Pixscape Pro will be a separate optional edition focused on advanced production tools. Pro does not replace, restrict, or weaken Pixscape Studio Free.

## Highlights

* **Open-source visual editor**
* **Built for LibGDX and Pixscape Runtime**
* **Scene-based 2D and 2.5D workflow**
* **Universal Layers with mixed scene content**
* **First-class Tiled Map entities**
* **Multiple Tiled Maps per scene and per Layer**
* **Sprite and animation editing**
* **Orthographic and isometric tiled maps**
* **TMX and TSX import workflows**
* **Single-image and image collection tilesets**
* **Tileset profiles for native-size and isometric tiles**
* **Tiled Object Layer import for orthogonal and isometric maps**
* **Typed entity custom properties**
* **Direct quad editing**
* **Hierarchical Game Objects with reusable assets**
* **Game Object physics, joints and Spatial Actor support**
* **Spatial V3 deterministic 2.5D wall and actor ordering**
* **Box2D physics integration**
* **Shader and light workflows**
* **Particle effects**
* **Desktop, Android and HTML5/WebGL2 export pipeline**
* **Runtime-first ECS architecture**
* **Apache 2.0 license**

## Studio Features

### Visual Editing

* Scene editor
* Asset browser
* Drag-and-drop placement
* Universal Layers with mixed content
* Context menus and property panels
* Canvas pan and zoom workflow
* Selection, lasso, gizmo and resize tools
* Debug console

### Sprites and Animation

* Sprite placement and configuration
* Spritesheet-based animations
* Animation clips
* Asset-level animation definitions
* Multi-animation entities with switchable Animation assets
* Runtime animation export
* Repeatable sprites
* 2.5D spatial properties for sprites and animations
* Direct per-corner quad editing

### Tiled Maps

* First-class Tiled Map entities
* Multiple Tiled Maps in the same Scene or Layer
* Independent Map projection, grid, dimensions and configuration
* Orthographic and isometric tiled maps
* TMX map import with external TSX files and inline tilesets
* Standalone TSX import for supported tilesets
* Single-image and image collection tilesets
* Tiled image layers imported as editable Pixscape scene content
* Tiled tile-animation import and editing
* Tileset profiles for logical cell size, native render size, projection, anchors and offsets
* Margin and spacing support for atlas tilesets
* Tile flip and diagonal transform flags compatible with Tiled
* Repeatable image layers and sprites
* Authored collision polygons
* Spatial V3 wall authoring with connected structures and junction handling
* Normal Layer and z-order composition for complete Tiled Maps
* Tiled Object Layer import for orthogonal and isometric maps
* Rectangle, point, tile, polygon and polyline objects
* Animated Tiled tile objects
* Tiled class/type metadata and typed custom properties

### Game Objects

* Hierarchical Game Object authoring
* Parent/child entity relationships
* Convert selected scene entities directly to a Game Object
* Save reusable Game Object assets
* Drag and drop Game Object instances into scenes
* Edit individual ECS entities inside a Game Object hierarchy
* Clipboard support
* Undo and redo integration
* Physics bodies and authored shapes
* Physics joints
* Spatial Actor support
* Runtime Game Object export and instantiation

### Spatial V3

* Deterministic actor, wall and tiled-structure ordering
* Automatic wall structure merging and splitting
* Precise wall footprint editing
* Corner and junction handling for complex 2.5D structures
* Altitude-aware walls and Tiled Maps
* Physics-footprint-aware actor ordering
* Optional Spatial-generated collision fixtures for authored walls
* Optional Spatial Actor participation on ordinary Layers

### Physics

* Box2D body authoring
* Fixtures and authored shapes
* Physics editing mode
* Runtime physics export
* Physics-aware actor footprints for Spatial ordering
* Optional automatically managed wall collision fixtures
* Custom collision-shape authoring for advanced layouts and passages
* Physics integration with Game Objects and joints

### Rendering

* Pixscape Runtime export
* Sprite and tiled rendering
* Universal Layer and z-order composition
* Profile-aware tile placement
* Native-size tile rendering with anchors and offsets
* Repeatable renderables
* Shader support
* Light support
* Particle support
* Atlas workflows
* Runtime asset availability
* Preview instrumentation for texture binds, batch flushes and region-cache resolution
* HiDPI-aware Studio and Preview rendering

## Pixscape Runtime

Pixscape Studio Free exports projects for **Pixscape Runtime**.

Pixscape Runtime is a separate open-source runtime built on **LibGDX** and **Artemis-ODB ECS**.

Current runtime dependency:

```gradle
games.pixscape:pixscape-runtime:0.2.1
```

Pixscape Runtime is published on Maven Central:

https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime

## Platforms

Pixscape Studio Free currently focuses on projects targeting:

* Desktop
* Android
* HTML5 / WebGL2

Pixscape Studio desktop requires **Java 25**. The HTML/GWT build remains on Java 21.

Pixscape Runtime is built with modern tooling and published as Java 8-compatible bytecode for broader LibGDX ecosystem compatibility.

iOS/RoboVM is not currently listed as an officially tested target.

## Build From a Clean Clone

Requirements:

* JDK 25 for Studio desktop and JDK 21 for HTML/GWT (selected through Gradle toolchains)
* The Gradle wrapper included in this repository

On Windows:

```powershell
.\gradlew.bat --console=plain compileJava test :html-player:compileJava
```

On Unix-like systems:

```sh
./gradlew --console=plain compileJava test :html-player:compileJava
```

To perform a complete GWT compilation of the HTML preview player:

```powershell
.\gradlew.bat :html-player:compileGwt
```

On Unix-like systems:

```sh
./gradlew :html-player:compileGwt
```

Generated outputs such as `.gradle/`, `build/`, `html-player/build/`, and `html-player/war/` are local build artifacts and should not be committed.

## Linux Distribution Builds

Build the reproducible Linux x64 tar.gz distribution:

```sh
./gradlew linuxTarGz
```

This writes:

```text
build/distributions/Pixscape-Studio-Free-<version>-linux-x64.tar.gz
build/distributions/Pixscape-Studio-Free-<version>-linux-x64.tar.gz.sha256
```

Build the optional x86_64 AppImage with `appimagetool`:

```sh
./gradlew appImage -Pappimagetool=/path/to/appimagetool
```

`appimagetool` is not committed to this repository. The Gradle task looks for it in this order:

* `-Pappimagetool=/path/to/appimagetool`
* `APPIMAGETOOL=/path/to/appimagetool`
* an executable named `appimagetool` on `PATH`

The AppImage build writes:

```text
build/distributions/Pixscape-Studio-Free-<version>-x86_64.AppImage
build/distributions/Pixscape-Studio-Free-<version>-x86_64.AppImage.sha256
```

## HTML Preview Template

The directory `src/main/resources/html-preview-template/` is intentionally versioned.

It contains the prebuilt GWT HTML preview player used by Pixscape Studio Free, so a clean clone can run the preview flow without requiring every contributor to regenerate the GWT bundle manually.

Maintainers may refresh this template when the HTML preview player changes.

## Documentation

Full documentation is available on the official website:

📘 https://pixscape.games/docs

## Changelog

See [CHANGELOG.md](CHANGELOG.md) for release notes.

## License

Pixscape Studio Free is released under the **Apache License 2.0**.

See [LICENSE](LICENSE) and [NOTICE](NOTICE) for details.

Apache 2.0 covers the source code license. It does not grant trademark rights to Pixscape names, logos, icons, or branding.

See [TRADEMARK.md](TRADEMARK.md) for trademark usage rules.
