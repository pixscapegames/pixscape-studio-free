# Changelog

## 0.2.1 - Tiled Map Import (.tmx)

### Added
- Added `File > Import > Tiled map (.tmx)...` for importing supported Tiled maps as new Pixscape scenes.
- Added a TMX import pipeline with preflight validation, import planning, scene materialization and rollback handling.
- Added TMX diagnostics for unsupported orientations, infinite maps, missing tileset images, invalid GIDs, unsupported encodings, object layers, image layers, tile animations and ignored custom properties.
- Added a reusable tileset asset import service for atlas and folder-based tilesets.
- Added Apache-2.0 / LibreJS-compatible license notices to HTML player JavaScript.
- Added TMX image layer import. Tiled image layers are imported as editable Pixscape Classic layers containing the referenced image as a normal sprite.
- Preserved image layer visibility, opacity, offsets and parallax factors where supported.
- Added diagnostics for missing image sources/files and unsupported image layer repeat, tint and transparency options.

### Changed
- Moved asset import from `Resources > Import assets` to `File > Import > Assets...`.
- Clarified the tiled-cell capacity exceeded dialog wording.
- Refactored tileset import materialization out of `SceneService`.

### Fixed
- Fixed Preview startup when a Studio project is opened without its exported runtime project. Pixscape now triggers the normal save/export flow before launching Preview instead of failing on a missing runtime export.
- Fixed remove the Studio “always on top / foreground” behavior

### Tests
- Added characterization tests for tile and tileset asset metadata persistence.
- Added tiled-cell capacity budget tests.
- Added tileset asset import service tests.
- Added TMX preflight analysis tests covering CSV, external TSX, base64/zlib data, isometric maps, nested groups and invalid map cases.
- Added TMX import planner tests covering external TSX files, multiple tilesets, GID resolution, transform flags, nested groups and blocking diagnostics.
- Added tests for TMX scene materialization, tile coordinate conversion, transform flags, rollback and failure handling.
- Added UI contract coverage for the new-scene TMX import menu flow and preflight dialog support.
- Added regression coverage for Preview save/export requirements when the runtime export is missing.

### Notes
- TMX import currently creates a new scene; importing TMX layers into the current scene is not included yet.
- TMX reimport and synchronization are not included yet.
- TMX image layers are imported as editable Pixscape Classic layers containing the referenced image as a normal sprite.
- TMX image layer visibility, opacity, offset and parallax are preserved where supported.
- TMX object layers are detected and reported, but not imported yet.
- TMX support is limited to the first supported import scope; unsupported map features are reported before import.


## 0.2.0 - First Open Source Release
