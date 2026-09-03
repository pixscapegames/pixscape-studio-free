# Changelog


## 0.3.0 - Universal Layers and Tiled Maps

### Breaking changes

* Pixscape Studio Free 0.2.4 requires Pixscape Runtime 0.1.11.
* Removed the legacy Layer type system. Layers are now universal composition containers instead of Classic, Tiled, Physics, Light or Spatial-specific Layer types.
* Existing schema 3 scenes using the obsolete typed-Layer representation are no longer supported and must be recreated or migrated with a future migration tool.
* Tiled Maps are no longer represented by special Tiled Layers. They are now first-class scene entities placed inside ordinary Layers.
* Tiled Map configuration is no longer stored at Scene level. Projection, tile size, map size and chunk size belong to each Map.

### Added

* Added first-class Tiled Map entities that can be selected and edited independently from their owning Layer.
* Added support for multiple Tiled Maps in the same Scene, including multiple Maps inside the same Layer.
* Added `Add Tiled Map` to create a Map inside an ordinary Layer with its own projection, grid and dimensions.
* Added dedicated Tiled Map properties for Map configuration, Spatial Depth and collision authoring.
* Added independent z-ordering for Tiled Maps alongside sprites, animations, lights and other scene entities.

### Changed

* Layers can now freely contain mixed content such as sprites, animations, lights, physical entities and Tiled Maps.
* Lights are now ordinary entities and no longer require a dedicated Light Layer.
* Physics authoring is now based on Scene Physics configuration and entity components instead of dedicated Physics Layers.
* Spatial Actor participation is now an optional property of an ordinary Layer rather than a separate Layer type.
* Layer Spatial Actors and Tiled Map Spatial Depth are now completely independent.
* Tiled editing now targets an explicit Map entity instead of relying on the active Layer.
* Tiled Maps now participate in normal Layer and z-order composition while preserving their own internal tile ordering.
* TMX import now materializes imported Tiled content using universal Layers and first-class Tiled Map entities.
* New Layers are created immediately as ordinary Layers without a type-selection dialog.
* New Scenes are created immediately as `New Scene` without a creation dialog or Tiled defaults.
* Removed Scene-level `Tiled Map Creation Defaults`; Map creation settings are now chosen when adding each Tiled Map.
* Simplified Scene metadata by removing obsolete persisted editor-mode and Physics debug state.

### Improved

* Simplified Layer Properties around universal Layer behavior, visibility, parallax and optional Spatial Actor participation.
* Simplified Scene Properties by removing Tiled creation settings and transient editor state.
* Improved Item Tree handling for Tiled Maps as real selectable scene content.
* Improved mixed-content Layer workflows and Map selection when several Maps share the same Layer.
* Simplified asset-drop and clipboard compatibility rules now that content is no longer restricted by Layer type.
* Simplified scene and Layer creation workflows by removing unnecessary dialogs.

### Fixed

* Fixed Tiled Map Spatial Depth incorrectly affecting ordinary Layer Spatial Actor state.
* Fixed Map collision controls so authored Physics state is preserved correctly.
* Fixed Tiled editing state when switching between Maps or leaving a Map editing context.
* Fixed Layer deletion/history handling for Layers containing Tiled Maps.
* Fixed ambient-light normalization so valid zero color channels remain zero.

### Tests

* Added regression coverage for universal Layers and removal of legacy Layer types.
* Added coverage for multiple Tiled Maps per Scene and per Layer.
* Added persistence and history coverage for Tiled Map configuration, creation and deletion.
* Added coverage for independent Layer Spatial Actors and Map Spatial Depth.
* Added coverage for explicit Tiled Map editing targets and Item Tree integration.
* Added regression coverage for immediate Layer creation and simplified Scene creation.
* Added compatibility coverage for the new schema 3 universal-Layer representation.


## 0.2.3 - Tiled Objects, Quad Editing and Prefab Instances

### Added

* Added Tiled Object Layer import for orthogonal and isometric maps.
* Added import support for Tiled rectangle, point, tile, polygon and polyline objects.
* Added support for animated Tiled tile objects as regular Pixscape entities.
* Added Tiled object class/type metadata and custom-property import, including inherited tile properties and object-level overrides.
* Added generic custom properties to entities with String, Boolean, Integer, Float, Color, Object and nested Class values.
* Added direct quad editing for sprites, with per-corner deformation handles and undo/redo support.
* Added logical prefab instance nodes to the Item Tree, allowing complete prefab instances to be selected, reordered and deleted as a group while keeping their individual entities editable.
* Added physics joint nodes to the Item Tree, displayed below their owning body with joint-specific labels and selection support.

### Changed

* Pixscape Studio Free 0.2.3 requires Pixscape Runtime 0.1.10.
* Prefab instances now participate in Item Tree ordering as logical blocks while their visual entities keep compact, contiguous z-order values.
* Physics joints remain prefab members but no longer participate in visual z-order.
* Quad deformation is now preserved through scene history, prefab creation, prefab instantiation and Runtime export.
* Tiled Object Layers are materialized as native Pixscape layers and entities instead of introducing a separate Tiled-specific runtime object model.

### Improved

* Greatly reduced sprite, animation and prefab drop latency when their atlas content is already present in the published GPU texture array.
* Optimized TMX import and scene-save workflows.
* Improved Tiled object overlays, picking and authored-geometry gizmos.
* Improved polygon and polyline selection, resizing and degenerate-geometry handling.
* Improved Item Tree selection synchronization for prefab instances and physics joints.

### Fixed

* Fixed complex physics prefabs appearing far from the mouse position when dropped into a scene.
* Fixed isometric Tiled object placement and rotation.
* Fixed authored geometry picking and gizmo transforms.
* Fixed resizing one axis unexpectedly changing the unaffected scale axis.
* Fixed selection state after undo/redo.
* Fixed editor overlays when layer parallax is enabled.
* Fixed custom-property Object references when the referenced entity is missing.
* Fixed Color property cancellation in the color picker.
* Fixed Studio UI and VisUI color-picker rendering on GL3.
* Fixed WebGL2 material shader precision for generated Studio shaders.

### Tests

* Added extensive regression coverage for Tiled Object Layers, tile objects, animated tile objects, custom properties, polygon/polyline geometry and isometric coordinate conversion.
* Added regression coverage for direct quad editing, prefab persistence and Runtime export.
* Added regression coverage for logical prefab ordering, atomic prefab selection, Item Tree joint integration and physics-aware prefab deletion/history.
* Added a real-world regression based on the tiled-iso-demo chain prefab to verify drop placement, joint remapping and Box2D synchronization.


## 0.2.2 - Physics Authoring and Spatial Collisions

### Breaking changes

* Replaced the legacy Studio physics authoring model with persistent Runtime physics shapes.
* Existing physics scenes and prefabs using the previous schema must be recreated or re-exported.
* Pixscape Studio Free 0.2.2 requires Pixscape Runtime 0.1.9.
* Particle emitters now use `Transform.x/y` directly as their position. Legacy particle local-space and transform-origin behavior is no longer preserved.

### Added

* Added stable physics shape identities across editing, persistence, duplication and prefab instantiation.
* Added `Physics collision` to Spatial Blocks, deriving a static polygon fixture from the block footprint.
* Added a read-only `Linked to Spatial Block #...` indicator for linked fixtures.
* Added a persistent canvas indicator showing the active editing mode, including Normal, Physics, Spatial, Tiled and Lights contexts.
* Added a dedicated Spatial actor layer with automatic physics body and footprint setup.
* Added visible progress feedback during Tiled map imports.
* Tiled editing mode now displays the logical grid coordinates under the cursor directly in the canvas mode indicator.
* Added multi-animation entities, allowing multiple Animation assets to be attached and the active animation to be switched from the Properties panel.

### Changed

* Unified body, fixture, polygon, joint, clipboard, prefab and scene workflows around the new authored physics model.
* Tiled layer physics bodies are now always static.
* Linked fixture geometry is read-only, while material, sensor, filter and enabled properties remain editable.
* Spatial Block edits and pixels-per-meter changes now recompile linked collisions at commit or activation boundaries.
* Deleting a Spatial Block now removes its linked collision atomically; undo restores both with the same physics shape identity.
* Particle entities now use a dedicated fixed-size viewport marker instead of rectangular dimensions, bounds, resize handles and rotation handles.
* Particle Transform properties now expose only X and Y, which directly represent the emitter position.
* Polygon authoring vertices are now displayed as circular handles while resize and Spatial handles remain square.
* Asset labels, tooltips and default entity names now use logical asset names and distinguish Asset IDs from entity stable IDs.
* Refreshed the Studio interface with a more compact and consistent layout, clearer panel hierarchy, harmonized dialogs and unified icon-based list controls.
* Desktop and HTML Preview now use render-driven progressive scene loading with a simple progress bar and enter normal preview state only after Runtime READY.
* HTML bootstrap now defers scene files, atlases and pages, particle effects, prefab fragments and other selected-scene resources to Runtime availability loading.
* Animation asset clip and FPS metadata is now authoritative and stays synchronized across entity switching, editing, undo/redo and scene reloads.

### Improved

* Indexed Studio asset metadata by asset ID, logical path and source ownership.
* Migrated Studio atlas resolution paths to the Runtime asset binding index instead of repeatedly scanning atlas regions.
* Centralized atlas and standalone visual resolution for sprites, animations, tiled fallback rendering, tiled ghost previews and render rebinds.
* Cached particle atlas readiness so each particle effect is probed once per atlas publication instead of once per emitter and per frame.
* Tiled fallback rendering now runs only while standalone tile visuals are required, then disables itself until a relevant scene, asset, animation or atlas change requests revalidation.
* Reduced repeated asset metadata loading and duplicated atlas/standalone resolution during repacks, scene changes and undo/redo operations.
* Greatly reduced undo/redo overhead for particle effects and other non-render entities.
* Atlas page decoding and GPU snapshot preparation now run off the render thread before publication.
* Prepared atlas pages are reused for both normal textures and texture-array publication.
* Particle file drops no longer trigger unnecessary atlas rebinds or full asset refreshes, reducing Preview and editor stalls.
* Simplified HTML Preview startup by serving the fixed player and current Runtime export directly instead of copying the complete player template and exported project for every launch.
* Reduced the packaged HTML Preview player to production GWT assets, removing development-only deployment output.

### Fixed

* Prevented transient missing-region particle errors while an asynchronous replacement atlas is pending publication.
* Prevented failed physics and Spatial operations from publishing partial state or incorrectly advancing history.
* Fixed stale physics selection and picking after fixture or body changes, undo/redo and scene activation.
* Fixed linked collision activation and scene loading when a tiled layer initially has no transform.
* Fixed prefab and clipboard instantiation so physics shapes receive fresh identities and joint references remain valid.
* Prevented asynchronous save failures from leaving the save-progress dialog open indefinitely.
* Fixed editor camera stutters caused by particle fallback effects being repeatedly loaded and disposed during rendering.
* Fixed asset metadata replacement sharing mutable state between databases and producing inconsistent source-owner ordering.
* Fixed particle selection, hover, dragging and lasso behavior so they consistently use the emitter position instead of synthetic rectangular bounds.
* Scene switching now prompts to Save, Don't Save or Cancel when the current scene has unsaved changes.
* Scene changes now wait for a successful visible save before loading the target scene, and restore the selector after cancellation or failure.
* Removed the silent automatic scene save previously performed when selecting another scene.
* Fixed undo/redo identity handling after entity deletion and restoration, preventing duplicate stable identities.
* Fixed particle fallback looping and premultiplied-alpha behavior to match Runtime playback.
* Fixed fallback texture ownership so shared atlas textures are not disposed by particle fallback cleanup.
* Fixed copied and pasted entities retaining the source layer instead of adapting to the destination layer.
* Fixed Runtime Availability particle preparation after Studio atlas publication so authored and declared effects are rebuilt against the canonical scene atlas, including renamed scenes.
* Particle effect replacement is failure-atomic and keeps the previous valid effect when replacement fails.
* Fixed prefab atlas dependency collection for multi-animation entities and animation metadata propagation after asset edits.
* Fixed HTML Preview/player prefab spawning with the updated Runtime GWT prefab reflection support.

### Tests

* Expanded regression coverage for physics authoring, persistence, history, prefabs, joints and linked Spatial collisions.
* Added regression coverage for indexed asset metadata, Runtime atlas bindings, centralized visual resolution, particle fallback readiness and tiled fallback gating.
* Revalidated the Studio against the Runtime asset index changes with forced GWT compilation.
* Added regression coverage for asset identity presentation, particle composition, marker picking, transform editing, drag history, lasso behavior and polygon handles.
* Added regression coverage for the shared unsaved-scene decision flow and scene-switch Save, Don't Save, Cancel and failure handling.
* Added grouped coverage for off-thread atlas and fallback publication, deferred preload classification and progressive HTML loading through forced GWT compilation.


## 0.2.1 - TMX Import and Spatial V3

### Breaking changes

* Existing scenes containing legacy tiled map or Spatial tiled data are no longer compatible.
* Affected tiled and Spatial scenes should be re-imported or recreated with this version.
* This breaking change is caused by the TMX and tiled-map pipeline changes required to support Tiled transform flags, tile animations, image collection tilesets, repeatable sprites, tileset profiles and improved runtime mapping.
* Spatial tiled data was also redesigned for Spatial V3, including canonical tile ranks, deterministic ordering, connected wall structures and junction rules.

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
* Added Spatial V3 wall authoring with automatic structure merging and precise footprint editing.

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
* Improved spatial wall selection, resizing, hover feedback, and connected-wall editing.
* Improved Spatial V3 structure visualization.
* Spatial-generated collision fixtures are now managed automatically while `Use for physics collision` is enabled.
* Spatial-generated fixture geometry is read-only: type and offsets are locked and geometry handles are hidden, while sensor, material and filter properties remain editable.
* Deleting a Spatial-generated collision fixture now disables `Use for physics collision` atomically; undo and redo restore the exact fixture and authored state.

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
* Fixed preview rendering for complex spatial tiled scenes with corners, wall junctions, and enclosed structures.
* Fixed preview rendering of sprites after physics body changes through the updated runtime.
* Fixed Spatial ordering becoming inactive after switching scenes and returning to a previously loaded Spatial scene.
* Fixed tiled and Spatial scene activation so each scene is deserialized once before tiled reconstruction and Spatial validation.
* Fixed Spatial layer default changes leaving compiled structures, projected faces, canonical tile ordering or Studio overlays stale.
* Fixed rejected Spatial wall operations incorrectly advancing undo/redo history.
* Fixed failed Spatial geometry compilation leaving geometry from the previous valid source visible in the Studio overlay.
* Fixed tiled edits that would invalidate linked Spatial anchors being applied before validation.
* Fixed Spatial-generated collision fixtures becoming detached editable fixtures when `Use for physics collision` was disabled.
* Fixed deletion of a selected physics fixture leaving stale fixture properties visible; the owning body now remains active in the Properties panel.
* Fixed recursive fixture-edit rejection dialogs causing a `StackOverflowError` when an owned fixture offset field lost focus.
* Fixed redundant World and editor-state clearing when activating a newly imported TMX scene.

### Improved
* Improved preview reliability for 2.5D tiled spatial scenes.

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
* Added regression coverage for Spatial canonical ranks, scene switching, cache invalidation, rejected command history, overlay failure handling and Spatial-owned collision fixtures.

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
