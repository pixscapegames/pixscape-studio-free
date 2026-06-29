# Changelog

## 0.2.1 - Tiled Map Import (.tmx)

### Added

* Added `File > Import > Tiled map (.tmx)...` for importing supported Tiled maps as new Pixscape scenes.
* Added a TMX import pipeline with preflight validation, import planning, scene materialization and rollback handling.
* Added TMX diagnostics for unsupported orientations, infinite maps, missing tileset images, invalid GIDs, unsupported encodings, object layers, image layers, tile animations and ignored custom properties.
* Added TMX image layer import. Tiled image layers are imported as editable Pixscape Classic layers containing the referenced image as a normal sprite.
* Preserved image layer visibility, opacity, offsets and parallax factors where supported.
* Added diagnostics for missing image sources/files and unsupported image layer repeat, tint and transparency options.
* Added standalone `.tsx` tileset import for Tiled single-image tilesets.
* Added TSX parsing for tile width, tile height, spacing, margin and relative image source resolution.
* Added margin and spacing support to atlas tileset import.
* Added explicit tileset profile metadata for imported tilesets, separating native tile size from logical cell size.
* Added a `Tileset profile...` import dialog for manual PNG atlas and folder-based tileset imports.
* Added tileset profile controls for cell size, projection, anchor, offset and native render size.
* Added a tileset profile placement preview showing the selected tile, logical cell overlay, anchor and offset.
* Added Apache-2.0 / LibreJS-compatible license notices to HTML player JavaScript.

### Changed

* Moved asset import from `Resources > Import assets` to `File > Import > Assets...`.
* Refactored tileset import materialization out of `SceneService`.
* TMX import now reuses the improved tileset slicing pipeline for tilesets with margin and spacing.
* Tile set imports no longer require image dimensions to be exactly divisible by tile size when margin or spacing is used; they only require at least one complete tile.
* Folder-based tileset imports now collect tileset profile settings before import.
* Sprite sheet import keeps its stricter divisibility validation.
* Clarified the tiled-cell capacity exceeded dialog wording.

### Fixed

* Fixed Preview startup when a Studio project is opened without its exported runtime project. Pixscape now triggers the normal save/export flow before launching Preview instead of failing on a missing runtime export.
* Fixed the Studio “always on top / foreground” behavior.

### Tests

* Added characterization tests for tile and tileset asset metadata persistence.
* Added tiled-cell capacity budget tests.
* Added tileset asset import service tests.
* Added TMX preflight analysis tests covering CSV, external TSX, base64/zlib data, isometric maps, nested groups and invalid map cases.
* Added TMX import planner tests covering external TSX files, multiple tilesets, GID resolution, transform flags, nested groups and blocking diagnostics.
* Added tests for TMX scene materialization, tile coordinate conversion, transform flags, rollback and failure handling.
* Added UI contract coverage for the new-scene TMX import menu flow and preflight dialog support.
* Added regression coverage for Preview save/export requirements when the runtime export is missing.
* Added coverage for margin/spacing atlas slicing, metadata persistence and row-major tile IDs.
* Added coverage for standalone TSX import, relative image resolution, missing image errors, invalid TSX files and unsupported image collection tilesets.
* Added coverage for tileset profile metadata serialization, migration defaults and import propagation.
* Added coverage for tileset profile validation, slicing layout, reference-cell defaults and preview placement helpers.

### Notes

* TMX import currently creates a new scene; importing TMX layers into the current scene is not included yet.
* TMX reimport and synchronization are not included yet.
* TMX image layers are imported as editable Pixscape Classic layers containing the referenced image as a normal sprite.
* TMX image layer visibility, opacity, offset and parallax are preserved where supported.
* TMX object layers are detected and reported, but not imported yet.
* TMX support is limited to the first supported import scope; unsupported map features are reported before import.
* Standalone TSX import currently supports single-image tilesets only.
* Tileset profiles are stored as global tileset metadata; per-tile profile overrides are not included yet.

## 0.2.0 - First Open Source Release
