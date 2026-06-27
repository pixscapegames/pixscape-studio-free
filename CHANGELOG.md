# Changelog

## 0.2.1

### Added
- Added a side-effect-free TMX preflight analyzer for inspecting Tiled maps before import.
- Added TMX preflight diagnostics for unsupported orientations, infinite maps, missing tileset images, invalid GIDs, unsupported encodings, object layers, image layers, tile animations and ignored custom properties.
- Added tests for TMX preflight analysis, including CSV, external TSX, base64/zlib data, isometric maps, nested groups and invalid map cases.
- Added a reusable tileset asset import service for atlas and folder-based tilesets.
- Added a side-effect-free TMX import planner that converts valid preflight results into Pixscape-oriented import plans.
- Added programmatic TMX scene materialization that can create a new Pixscape scene from a valid TMX import plan.
- Added TMX import rollback handling for failed scene materialization.
- Added tests for TMX scene materialization, tile coordinate conversion, transform flags and failure handling.

### Changed
- Moved asset import from `Resources > Import assets` to `File > Import > Assets...`.
- Clarified the tiled-cell capacity exceeded dialog wording.
- Refactored tileset import materialization out of `SceneService`.

### Tests
- Added characterization tests for tile and tileset asset metadata persistence.
- Added tiled-cell capacity budget tests.
- Added tileset asset import service tests.
- Added TMX import planner tests covering external TSX files, multiple tilesets, GID resolution, transform flags, nested groups and blocking diagnostics.

### Notes
- TMX import UI and scene conversion are not included yet.
- Importing TMX layers into the current scene is not included yet.

## 0.2.0 - First Open Source Release
