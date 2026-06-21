<img src="pixscape_logo.png" alt="Pixscape logo" width="80">

<h1>Pixscape Studio Free</h1>

[![Changelog](https://img.shields.io/badge/changelog-0.2.0-orange.svg)](CHANGELOG.md)
[![Runtime](https://img.shields.io/badge/runtime-0.1.7-purple.svg)](https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime)<br>
[![Platforms](https://img.shields.io/badge/exports-Desktop%20%7C%20Android%20%7C%20HTML5-green.svg)](#)
[![Java](https://img.shields.io/badge/studio-Java%2021-blue.svg)](#)
[![License](https://img.shields.io/badge/license-Apache%202.0-blue.svg)](LICENSE)

**Open-source 2D game studio for LibGDX and Pixscape Runtime.**

Build fast. Stay free. Keep full control.

🌐 **Website:** https://pixscape.games/
📘 **Documentation:** https://pixscape.games/docs
⚙️ **Runtime:** https://github.com/pixscapegames/pixscape-runtime
📝 **Changelog:** [CHANGELOG.md](CHANGELOG.md)

<p align="center">
  <img src="assets/readme/pixscape-studio-free.png" alt="Pixscape Studio Free" width="100%">
</p>

## What is Pixscape Studio Free?

Pixscape Studio Free is the open-source visual editor for **Pixscape Runtime**, built for developers who want the speed of a game editor without giving up code-level control.

It is designed for **LibGDX** projects and focuses on lightweight 2D production workflows: scenes, sprites, tiled maps, isometric maps, animations, particles, shaders, lights, physics, prefabs, runtime export, and 2.5D spatial ordering.

Pixscape Studio Free is released under the **Apache License 2.0**.

It is not a trial version. You can use it to build real games, including commercial games, with no Pixscape royalties, no runtime fees, and no hidden export fees.

**Pixscape Free is the foundation. Pixscape Pro funds the future.**

## Open-source foundation

Pixscape Studio Free is open source so developers can inspect it, build it, modify it, fork it if necessary, and trust it as a real foundation for real projects.

Pixscape Runtime is also open source under Apache License 2.0.

Pixscape Pro will be a separate optional edition focused on advanced production tools. Pro does not replace, restrict, or weaken Pixscape Studio Free.

## Highlights

* **Open-source visual editor**
* **Built for LibGDX and Pixscape Runtime**
* **Scene-based 2D workflow**
* **Sprite and animation editing**
* **Orthographic and isometric tiled maps**
* **2.5D spatial ordering**
* **Prefab authoring and drag-and-drop placement**
* **Box2D physics integration**
* **Shader and light workflows**
* **Particle effects**
* **Desktop, Android and HTML5/WebGL2 export pipeline**
* **Runtime-first architecture**
* **Apache 2.0 license**

## Studio Features

### Visual Editing

* Scene editor
* Asset browser
* Drag-and-drop placement
* Multi-layer editing
* Context menus and property panels
* Canvas pan/zoom workflow
* Selection, lasso, gizmo and resize tools
* Debug console

### Sprites and Animation

* Sprite placement and configuration
* Spritesheet-based animations
* Animation clips
* Asset-level animation definitions
* Runtime animation export
* 2.5D spatial properties for sprites and animations

### Tiled Maps

* Orthographic tiled maps
* Isometric tiled maps
* Tiled animations
* Tile flip/rotation flags compatible with TMX-style workflows
* Authored collision polygons
* Spatial blocks for 2.5D ordering
* Layer-based rendering and depth control

### Physics

* Box2D body authoring
* Fixtures and authored shapes
* Physics editing mode
* Runtime physics export
* Physics-aware spatial footprint support

### Rendering

* Pixscape Runtime export
* Sprite and tiled rendering
* Shader support
* Light support
* Particle support
* Atlas workflows
* Runtime asset availability

### Prefabs

* Create reusable prefabs from selected entities
* Save prefab assets
* Preview prefabs in the asset browser
* Drag and drop prefabs into scenes
* Export prefab data for runtime use

## Pixscape Runtime

Pixscape Studio Free exports projects for **Pixscape Runtime**.

Pixscape Runtime is a separate open-source runtime built on **LibGDX** and **Artemis-ODB ECS**.

Current runtime dependency:

```gradle
games.pixscape:pixscape-runtime:0.1.7
```

Pixscape Runtime is published on Maven Central:

https://central.sonatype.com/artifact/games.pixscape/pixscape-runtime

## Platforms

Pixscape Studio Free currently focuses on projects targeting:

* Desktop
* Android
* HTML5 / WebGL2

Pixscape Studio requires **Java 21**.

Pixscape Runtime is built with modern tooling and published as Java 8-compatible bytecode for broader LibGDX ecosystem compatibility.

iOS/RoboVM is not currently listed as an officially tested target.

## Build From a Clean Clone

Requirements:

* JDK 21
* The Gradle wrapper included in this repository

On Windows:

```powershell
.\gradlew.bat --console=plain compileJava test :html-player:compileJava
```

On Unix-like systems:

```sh
./gradlew --console=plain compileJava test :html-player:compileJava
```

Generated outputs such as `.gradle/`, `build/`, `html-player/build/`, and `html-player/war/` are local build artifacts and should not be committed.

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
