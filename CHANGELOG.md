# Changelog

## 0.2.1 - Tiled Map Import (.tmx, .tsx)

### Breaking changes
* Existing Pixscape scenes created with older tiled map data are no longer compatible.
* Projects containing tiled maps should be re-imported or recreated with this version.
* This breaking change is caused by the TMX/tiled map import pipeline changes required to support Tiled transform flags, image layers, tile animations, image collection tilesets and improved runtime mapping.

### Added

* Added `File > Import > Tiled map (.tmx)...` for importing supported Tiled maps as new Pixscape scenes.
* Added a TMX import pipeline with preflight validation, import planning, scene materialization and rollback handling.
* Added TMX diagnostics for unsupported orientations, infinite maps, missing tileset images, invalid GIDs, unsupported encodings, object layers, invalid tile animations and ignored custom properties.
* Added TMX image layer import. Tiled image layers are imported as editable Pixscape Classic layers containing the referenced image as a normal sprite.
* Preserved image layer visibility, opacity, offsets and parallax factors where supported.
* Added diagnostics for missing image sources/files and unsupported image layer repeat, tint and transparency options.
* Added standalone `.tsx` tileset import for Tiled single-image tilesets.
* Added TSX parsing for tile width, tile height, spacing, margin and relative image source resolution.
* Added Tiled tile animation import from TSX/TMX single-image tilesets, mapped to Pixscape tiled animation metadata.
* Added margin and spacing support to atlas tileset import.
* Added explicit tileset profile metadata for imported tilesets, separating native tile size from logical cell size.
* Added a `Tileset profile...` import dialog for manual PNG atlas and folder-based tileset imports.
* Added tileset profile controls for cell size, projection, anchor, offset and native render size.
* Added a tileset profile placement preview showing the selected tile, logical cell overlay, anchor and offset.
* Added runtime export of tileset profile metadata through `tileset-profiles.json`.
* Added profile-aware Studio tiled ghost and fallback rendering.
* Added profile-aware Studio atlas rendering for tiled maps through the runtime tiled rendering path.
* Added Preview save/export checks for missing, stale or incomplete tileset profile manifests.
* Added TMX image layer repeat import for `repeatx` / `repeaty`, using Pixscape render-time repeated renderables. 
* Added Studio canvas rendering for repeated renderables.
* Added support for Tiled image collection tilesets in TMX imports, including external TSX files, inline tilesets, per-tile image assets, GID mapping and missing-image diagnostics.
* Added Apache-2.0 / LibreJS-compatible license notices to HTML player JavaScript.
* Added preview: new instrumentation around binds, flushes, and cache resolutions.

### Changed

* Moved asset import from `Resources > Import assets` to `File > Import > Assets...`.
* Refactored tileset import materialization out of `SceneService`.
* TMX import now reuses the improved tileset slicing pipeline for tilesets with margin and spacing.
* Tile set imports no longer require image dimensions to be exactly divisible by tile size when margin or spacing is used; they only require at least one complete tile.
* Folder-based tileset imports now collect tileset profile settings before import.
* Sprite sheet import keeps its stricter divisibility validation.
* Tiled map properties now label logical dimensions as cell width and cell height.
* Runtime export now writes tileset profiles for tile assets used by exported scenes, without mutating Runtime Availability.
* Runtime export now writes imported tiled animations to `tiled-animations.json` and includes their frame tile IDs in `tileset-profiles.json`.
* Clarified the tiled-cell capacity exceeded dialog wording.
* TMX tile layer import now resolves image collection GIDs through the existing tiled cell asset path while preserving Tiled transform flags and real image dimensions.
* TMX import diagnostics are now displayed in a scrollable dialog so all reported issues can be reviewed.
* TMX image collection tilesets now allow per-tile image dimensions that differ from the map tile size.
* Repeatable sprites and rotation are now mutually exclusive in the editor.
* Enabling Repeat X or Repeat Y resets sprite rotation to 0°.
* Rotating a repeatable sprite now clears repeat flags so the properties panel always matches the rendered result.

### Fixed

* Fixed Preview startup when a Studio project is opened without its exported runtime project. Pixscape now triggers the normal save/export flow before launching Preview instead of failing on a missing runtime export.
* Fixed Preview launches using stale runtime exports when the current scene or tileset profile manifest is out of date.
* Fixed empty `tileset-profiles.json` exports when tiled scenes use tile assets but Runtime Availability has no explicit tiled tiles.
* Fixed `tileset-profiles.json` coverage for imported tiled animation frames used by exported scenes.
* Fixed Studio atlas-backed tiled rendering losing profile metadata after tileset import, scene reload or atlas rebind.
* Fixed profiled tiles disappearing in Preview when exported scenes referenced tile IDs missing from `tileset-profiles.json`.
* Fixed the Studio “always on top / foreground” behavior.
* Fixed TMX image layer placement and layer ordering to match Tiled.
* Fixed repeated TMX image layers rendering correctly in preview but not in the Studio canvas.
* Fixed false tileset-size incompatibility errors when importing real-world Tiled maps using image collection tilesets.
* Fixed undocked panels turning black after closing the Debug Console.

### Tests

* Added characterization tests for tile and tileset asset metadata persistence.
* Added tiled-cell capacity budget tests.
* Added tileset asset import service tests.
* Added TMX preflight analysis tests covering CSV, external TSX, base64/zlib data, isometric maps, nested groups and invalid map cases.
* Added TMX import planner tests covering external TSX files, multiple tilesets, GID resolution, transform flags, nested groups and blocking diagnostics.
* Added tests for TMX scene materialization, tile coordinate conversion, transform flags, rollback and failure handling.
* Added UI contract coverage for the new-scene TMX import menu flow and preflight dialog support.
* Added regression coverage for Preview save/export requirements when the runtime export is missing or stale.
* Added coverage for margin/spacing atlas slicing, metadata persistence and row-major tile IDs.
* Added coverage for standalone TSX import, relative image resolution, missing image errors, invalid TSX files and unsupported image collection tilesets.
* Added coverage for TSX tile animation parsing, invalid tile animation diagnostics, TMX tiled animation materialization, transform flags and runtime export.
* Added coverage for tileset profile metadata serialization, migration defaults and import propagation.
* Added coverage for tileset profile validation, slicing layout, reference-cell defaults and preview placement helpers.
* Added coverage for runtime tileset profile export, Preview manifest validation and Runtime Availability separation.
* Added coverage for profile-aware Studio tiled placement and profile resolver behavior.

### Notes

* TMX import currently creates a new scene; importing TMX layers into the current scene is not included yet.
* TMX reimport and synchronization are not included yet.
* TMX image layers are imported as editable Pixscape Classic layers containing the referenced image as a normal sprite.
* TMX image layer visibility, opacity, offset and parallax are preserved where supported.
* TMX object layers are detected and reported, but not imported yet.
* TMX tile animations from supported TSX/TMX single-image tilesets are imported as Pixscape tiled animations.
* TMX support is limited to the first supported import scope; unsupported map features are reported before import.
* Standalone TSX import currently supports single-image tilesets, including tile animation metadata.
* Tileset profiles are stored as global tileset metadata; per-tile profile overrides are not included yet.

## 0.2.0 - First Open Source Release
