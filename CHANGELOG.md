# Changelog


## [0.4.3-SNAPSHOT]

### Compatibility
* Uses Runtime `0.2.5-SNAPSHOT`, scene schema 7 and Game Object definition/fragment schema 6. Older formats are rejected without historical readers, conversion or migration.
* Occlusion zones now use normalized image-local storage and are edited in logical image pixels. Existing files using the previous rectangle coordinates must be adapted explicitly.

### Added
* Restored the single Spatial actor scene layer independently of Physics. The Add component menu offers Spatial only in that effective layer. Disabling Spatial now asks for confirmation when footprints exist and removes every own footprint in the effective layer, including nested and invisible composition owners, in one undoable command. Cancel/close leaves data and history unchanged; an empty layer disables directly. Undo restores exact values and owners; representation rectangles, shadow pivots, Physics and Tiled volumes remain untouched. Spatial Depth permits several tile layers of one participating Tiled map, including a map hosted outside the actor layer. Activation and map paste reject a second participating map before publication/history mutation; Undo/Redo and layer persistence preserve admission.
* Added distinct inspector actions for optional Physics/Spatial/Repeat capabilities and existing child creation. Add component lists only currently addable components: unavailable or already present choices are omitted, including Physics when scene physics is disabled. Commands still revalidate before mutation. Optional sections have the Layers-style removal cross beside the title; required Texture/Transform/Material sections do not. The Physics removal tooltip explains that Undo restores the Body, shapes, dependent joints and their previous parameters.
* Spatial activation initializes missing stored occlusion rectangles and shadow pivots on every visual descendant, including children created or attached later, within the triggering Undo/Redo command. Existing settings remain unchanged. Nonvisual nodes transmit footprint ownership without fabricated representation data.
* The Spatial inspector uses a text-only title and aligned Footprint, Occlusion rectangle and Shadow pivot groups, with coordinate tooltips and explicit units. Descendants use an "Inherited from parent" checkbox; creating an own footprint preserves the inherited world circle and altitude. Only the spatial root has a removal cross: Cancel/OK confirmation removes every footprint override in the subtree in one history command while retaining representation settings. The confirmation explicitly directs users to Undo to recover Spatial and all previous descendant settings. Inactive sections disappear; descendants cannot independently remove Spatial.
* Items shows an own Footprint component row using the footprint drawable. It inspects the owner without exposing entity copy/cut/delete/drag operations on the component row. Inherited footprints do not create duplicate rows; subtree removal and Undo/Redo refresh descendant labels and rows.
* Sprite parents retain their type, visibility control and required Texture section; Game Object source provenance is shown separately only when an asset source exists.
* Items and the inspector use the dedicated Tiled rectangle/polygon skin icons. Entity inspector headers place the matching type icon to the left of the title.
* Separated Tiled Map, Tile Layer, Object Layer and Image Layer controls from generic Game Object composition. Structural entities no longer expose actor Body/footprint or source-asset controls; object layers retain valid child objects. Map/tile overlays use translation markers instead of resize bounds, and decimal tile-layer local offsets use Undo/Redo without rounding.
* Enforced Tiled ownership before generic commands, drag/drop and clipboard publication while retaining dedicated layer order, map duplication/restoration, managed tile collisions and volumes. Physical map translation and repeated Undo/Redo have native Box2D regressions.
* Sprites, including sprites with children, now have a required Texture inspector section with an image preview and project Image selector. Replacement adopts the selected region's native dimensions while preserving transforms, signed scale/flips, material, tint, repeat, children, physics and footprint settings. One history command restores image and authored dimensions together; cancel, unchanged selection and unavailable images do not add history entries. Changes synchronize reachable atlas resources through the existing asynchronous pipeline, including undo/redo. Static normalized occlusion settings stay attached to the sprite and follow its image dimensions without rewriting their values.
* Added rectangle and polygon object tools for Tiled object layers without Physics, explicit object-layer creation, canvas vertex/corner gestures and numeric rectangle edge sizes. Shared polygon gestures retain domain-specific Physics validation, isolated previews, cancellation and undo/redo. Isometric rectangles preserve their projected axes.
* Spatial Actor activation and circle editing now work with scene physics disabled. Physics removal preserves spatial data; collision geometry, circle and occlusion rectangle have independent settings. Native canvas gestures edit circle center/radius, rectangle or shadow pivot with undo/redo and Escape cancellation. Undo/redo, save, export and Game Object snapshots preserve all three geometries.
* Added persistent Studio-only visibility controls for Tiled maps and their layers, in a fixed Items column before the scrollbar.
* Added `Grid on top` beside `Rulers`, keeping the grid behind world content by default.
* TMX imports now create one map containing ordered tile, image and object layers, with map translation and import undo/redo.
* Added tile custom property editing with persistent local tile IDs.
* Added image, animation, particle and Game Object asset previews, and unified HUD screen inspection.
* Added drag-and-drop scene-layer ordering, item ordering and reparenting, and tile animation frame ordering, with undo/redo.
* Added direct child creation on compatible visual entities, preserving their appearance and promoting them to Game Object parents in one undoable operation.
* Added explicit `Copy visual only`, which omits physics while retaining included spatial capabilities and representation settings.

### Changed
* Game Object roots can have their own visuals. Physics-backed compositions support explicit placement, and physics-backed entities support visual scaling.
* The inspector distinguishes an entity's own footprint from the nearest inherited owner. Editing a shared footprint updates its identified owner; each selected visual keeps its own optional occlusion zone and footprint-relative shadow pivot. Removing a footprint preserves custom representation settings.
* Removed ordinary-layer spatial activation and layer-dependent spatial cleanup. Spatial participation follows footprint ownership through empty and physical hierarchy nodes; Tiled map/layer structures keep their specialized behavior.
* Occlusion zones and canvas handles follow visual rotation, signed scale, flips and deformation. Missing zone settings cover the full image; configured dimensions remain finite and strictly positive even when clipping produces empty geometry.
* Physical copy and Game Object asset capture require the complete joint-connected group, including Gear dependencies. Incomplete transfers fail before mutation and preserve the previous clipboard instead of silently omitting joints or adding Bodies.
* Runtime export retains dormant spatial settings for later API activation and publishes only available Game Object definitions and declared scene atlas pages. Normal-map preparation uses the unpublished export candidate; orphan atlas packs and authoring inputs are excluded.
* The tree and inspector distinguish Tiled maps from tile layers. Tile layers retain their existing editing tools.
* Simplified the Assets tree and Items toolbar, and refreshed add/delete button states.
* Refreshed the distributed HTML Preview player for the current Game Object composition formats and the Runtime physics bootstrap correction.

### Fixed
* Preserve custom properties when deleting and undoing standalone entities. Generic snapshots and transfers retain absent/empty components and deep-copy all supported values; hierarchy and Game Object restoration use the remapped properties without a second write.
* Tiled points and polylines use the dedicated tiled-point and tiled-polyline skin drawables in Items and entity inspector headers instead of the image fallback.
* Empty Runtime availability categories share one asset-neutral drop message, matching automatic asset-type recognition during drag-and-drop.
* Add child menus in Items and the inspector omit unavailable choices instead of displaying gray entries. Sprite/Animation entries require a compatible selected asset; adding assets onto a node by drag-and-drop remains unchanged. Incompatible parents no longer expose a disabled Add child submenu.
* Entity inspectors hide the Lighting section when scene Lighting is disabled and refresh it on activation, deactivation and Undo/Redo. Stored receive-light, shadow-casting and threshold settings remain unchanged.
* An entity's own Physics section remains visible and removable without Spatial, including visual parents with children. Removing Spatial no longer hides an existing Body; Add component still omits Physics when it is already present. Existing removal and Undo/Redo preserve physical parameters and composition links.
* Tiled map and tile-layer markers capture the queued Scene2D press before motion and tile tools, retain the final release position, and restore the preview on Escape or touch-focus cancellation. Tile editing targets display the same translation-only marker and reuse the existing move/history path.
* Corrected the HUD Stage regression fixture so its Shift-selection click stays above the horizontal scrollbar after expanding the tree; selection, expansion and scroll assertions remain intact without changing HUD behavior.
* Fixed the source nine-patch borders for horizontal scrollbars so repacking retains a 17 px region height and valid stretch/padding metadata. A packing regression preserves the Tiled rectangle/polygon and footprint drawables; the machine-local Skin Composer project stays outside Git.
* Restored horizontal scrollbar tracks and knobs in the shared list skin used by Studio panes, including code editors, trees and tileset views. Horizontal bars now appear when their content overflows.
* Studio snapshots now preserve polygon and open-polyline vertices through copy/paste, delete undo/redo and Game Object capture/restoration. Vertex arrays are independently owned; authored order, transforms and isometric rectangle roles are retained without contour normalization or physics conversion.
* Wired the Edit menu's Cut, Copy and Paste entries to the active document's existing clipboard commands and undo history.
* Pasting without a valid active layer now refuses the operation before accessing layer data, preserving the clipboard, selection and history.
* Footprint addition/removal and undo/redo now refresh inherited spatial labels throughout the affected Items subtree without replacing its tree rows.
* Adding a child to a parent with an incompatible transform now displays the refusal reason directly in the menu.
* Repeated subtree deletion and undo/redo preserve descendants in both the Items tree and canvas, using the corrected Runtime child-order invalidation.
* Normal canvas selection exposes footprint, occlusion rectangle and shadow pivot handles together, with distinct colors/shapes and hover feedback. Removed the Edit on canvas selector; nearest-handle picking uses stable ties and locks the target for the gesture. Coincident handles remain at their true anchors and can be separated by moving the first geometry.
* Spatial numeric fields reject invalid values with the standard VisUI red border, without publishing or automatically correcting the input.
* Undoing Body removal restores independent shapes, Tiled-linked collisions and joints, preparing collisions before publishing the restored Body.
* Spatial volume collision creation requires scene Physics and explains where to enable it. Existing collisions can still be removed and restored during cleanup.
* Desktop Preview requires a fresh export when the exported scene format differs from the current schema; obsolete exports are never treated as ready.
* Actors now pass behind scaffold beams correctly when spatial constraints conflict, including through transparent openings.
* Fixed drawing resource cleanup when closing Studio.
* Improved hierarchy, identity and joint restoration during deletion, copying and undo/redo, including complete Tiled maps.
* Fixed docking resize and panel transfers, preview cleanup, and project/scene subscription leaks.
* Fixed item drag target stability, mixed-entity placement, and Spatial selection and inspector refresh during editing.


## [0.4.2]

### Added
* Point and cone lights on orthographic maps now support the None, Hard and PCF shadow settings. New lights in scenes containing an orthographic map start with shadows off.

### Changed
* When a painted tile refers to a missing asset or an asset of the wrong type, Runtime Export now reports the scene name, tile ID and asset catalog issue.

### Fixed
* The material inspector and scene lighting switch now explain incompatible blend modes before applying them. Changing a blend supports undo and redo, and correcting an incompatible material resumes the canvas.
* Enabling Spatial Actor after removing a physics body now draws the actor and its shadow on the first frame. Removing the body from a Spatial actor keeps the canvas editable and shows how to restore the missing footprint in the actor properties.
* Deleting a tileset or tile animation now checks saved scenes and unsaved edits in every open tab. If a scene still uses the asset or cannot be read, Studio blocks deletion and identifies the scene.
* Spatial edits, undo and redo now respect each scene’s lighting setting. Wall volumes and tile lighting settings can be edited with lighting off and take effect when lighting is enabled.
* Renaming a scene now updates its tab title even when another tab is active.


## [0.4.1]

### Added
* Added LibGDX `.atlas` import with PNG pages. Indexed regions with the same name become an Animation, including single-frame animations. Unindexed regions become Image assets. The importer supports multiple pages, 90° packing rotation and trimmed frames.
* Added an **Enable lighting** scene setting with undo and redo. New scenes, including TMX imports, start with lighting off. Switching it off preserves the scene’s lights and renderer settings.
* Added Lit and Unlit materials to the Shader Manager and shader selectors. Authors can edit a surface and its optional cutout mask together. A failed compilation keeps the last working material active.
* Added a VSync option to Desktop Preview, enabled by default. With VSync off, Preview limits the number of frames queued on the GPU.

### Changed
* Opening a project upgrades unchanged copies of the supplied materials while preserving customized shaders. Renaming a shader keeps its ID and entity assignments.
* Runtime exports now preserve layer names for gameplay lookups.
* The Properties panel labels a Game Object child’s position, rotation and scale as **Local Transform**.

### Fixed
* Fixed the extra “Game Object” layer shown above the asset root in the Item Tree. The root and its children can now be selected directly in the Game Object editor.
* Failed Runtime exports now preserve the last complete export.


## [0.4.0] - HUD, Multi-View and 2.5D Lighting

### Breaking changes
* Custom material shaders must use per-entity material parameters instead of per-entity float uniforms.
* HUD screen assets using the former fixed-resolution format must be recreated.

### Added
* Added visual HUD screen editing with a widget hierarchy, properties, drag-and-drop placement, undo/redo, and interactive test mode.
* Added simultaneous editing of multiple scenes through document tabs with independent editor contexts.
* Added scene-to-HUD associations and HUD-aware runtime export using the shared Pixscape Runtime.
* Added independently editable Game Object assets.
* Added canvas calibration for actor shadows, including per-animation foot profiles and thickness.
* Added controls for particle ambient-light exemption and point/cone-light intensity, height and inclination.
* Added tileset reimport with retained source links, including tile normal-map import and export.
* Added per-entity material parameters in the Shader Manager.

### Changed
* HUD screens now adapt to the available canvas surface; root Tables can fill it and cells can define maximum dimensions.
* Spatial actor influence, map-plane altitude and light range previews now use the Runtime projection.
* Preview lighting now supports tile normal maps, deformed images and actor-to-decor shadows.

### Fixed
* Fixed tile normal-map thumbnails in the Assets panel.
* Fixed picking and movement of parented lights.
* Fixed deletion and undo of Game Object hierarchies with dependent Physics joints.


## [0.3.0] - Universal Layers and Tiled Maps

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


## [0.2.3] - Tiled Objects, Quad Editing and Prefab Instances

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


## [0.2.2] - Physics Authoring and Spatial Collisions

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


## [0.2.1] - TMX Import and Spatial V3

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


## [0.2.0] - First Open Source Release
