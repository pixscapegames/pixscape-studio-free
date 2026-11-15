# Tiled map integration

## Model and editor ownership

A TMX document creates one map inside one Pixscape scene layer. The map is a
composition root carrying `TiledMapComponent`; its ordered direct children carry
`TiledMapLayerComponent`. Tile children reuse their existing grid, chunks, brush,
selection state and inspector. Image children reuse sprite tools. Object layers
are groups containing the existing objects. There is no parallel selection model.

Stable `PixscapeIdentityComponent` IDs and explicit parent IDs identify maps,
layers and objects. Local `EntityIndexComponent.zIndex` controls composition,
independently of identity and physical altitude. Scene placement belongs to the
map root. See the [Runtime contract](../../pixscape-runtime/docs/tiled-map-layers.md)
for grid bounds, local offsets, inherited visibility and parallax.

The existing tree now shows tile-layer children inside their map. Select a tile
child to paint or inspect its grid; select an image or object for its existing
tools. Selecting the map exposes its translation. Map rotation/scaling and generic
Game Object attach/detach operations that would invalidate map ownership are
rejected. The map context menu deletes the entire hierarchy.

Map containers display type `TILED_MAP` with the skin drawable `icon-tiled`.
Tile children display type `TILED_LAYER` with `icon-tiled-layer`. Both the tree
and inspector derive these types from runtime components, including converted
scenes whose editor metadata is absent or still identifies a tile grid as a map.

## Import, history and files

The existing TMX planner and asset pipeline are retained. The materializer keeps
tile, image and object layers in the planner's composition order. Existing group
flattening and inherited offsets/visibility/properties remain the supported group
behavior; this does not add full Tiled group editing, blend modes or arbitrary
Tiled features absent from the importer.

Scene schema 4 serializes the container, child kinds, stable relationships, order
and ordinary components. Derived tile projection, cell size and local-origin
caches are omitted by Studio save and rebuilt from map configuration and local
transforms on load. Schema 3 conversion creates one container for each old tile
grid, preserving the old layer ID, cells, volumes and properties. It never guesses
which grids belong together.

The imported scene records `Import Tiled Map` in its content history: undo removes
its complete map, redo restores the same identities. The newly created scene and
imported project assets remain available, as scene/catalog management is separate
from scene content history. Import failure rollback retains its existing transaction.

History and clipboard snapshots carry detached authored map/layer components.
The existing graph instantiator remaps child parent IDs and object-valued custom
properties. Deletion captures the full current hierarchy, including indexed
children created before ECS publication. Undo restores identities and prepares
grids, animations and linked physics before normal publication. ID high-water
marks never roll backward.

## Reproducible witness

1. Start Studio normally and open or create a small project.
2. Choose **File > Import > Tiled map (.tmx)...** and select
   `src/test/resources/tmx/mixed-layers/mixed-layers.tmx` in this checkout.
3. Open the imported scene and expand the map. Composition draws `Bottom red`,
   `Middle yellow`, `Middle green`, then `Top blue`. The tree shows frontmost first,
   so these children appear in reverse order there.
4. Select each tile child to inspect/paint its own grid. Both initially contain
   a cell at the same coordinates. Undo/redo a stroke and check the other layer.
5. Save, reopen and launch Desktop Preview. The overlap consists of a red square,
   yellow inset, green diamond and blue diagonal, with blue drawn last.

For local development, use the regular Studio run task with the local Runtime:
`./gradlew.bat run --include-build ../pixscape-runtime`. HTML Preview normally reuses
the checked-in player bundle. To try the new model in HTML after a clean build,
refresh it with `./gradlew.bat buildHtmlPreviewTemplate --include-build ../pixscape-runtime`
before starting Studio. That existing task produces generated template files; they
are intentionally excluded from this source-only change.

`TmxSceneImportServiceTest.mixedLayerWitnessPaintsCopiesRestoresAndReloadsStableAssociations`
imports this exact fixture through the normal service, exercises brush undo/redo,
copy reference remapping, deletion before publication, restoration, reordered
save/reload and Runtime export. The generated test project path is written to
ignored `build/tiled-map-layers-fixture-path.txt` for local native validation.

## Next authoring tranche

Dedicated layer insertion/removal/reordering controls, layer reparenting, full
Tiled group editing and map-wide grid reconfiguration are still future work.
Model order can already change without changing layer IDs. The existing per-layer
grid resize remains available; map extent is document metadata, not a second grid.
Cross-layer Spatial volumes and the spatial-single-map prototype are not included.

## Validation of this integration

The mixed-layer integration test covers brush undo/redo, import content undo/redo,
copy remapping, deletion/restoration before and after ECS publication, reordered
save/load and export. Runtime tests cover mixed composition, schema-3 conversion
with retained spatial links, and indexed Tiled-layer name queries.

Native validation used the normal Studio and Desktop Preview entry points on the
tiny generated project. The tree, active Top blue inspector, eraser stroke and
menu Undo/Redo were checked. Both native processes closed successfully. Automated
text entry into the native file chooser did not populate its field; the full TMX
import and import-history cycle were therefore verified through the normal service
integration test, not claimed as a completed file-chooser interaction.

The GWT module compiled successfully. Its generated JavaScript was overlaid only
in ignored build resources for validation, then the normal Studio HTML Preview
served the fixture to Chrome. Actual WebGL2 rendering showed all four overlapping
quads; the stats reported two visible tile refs and two ECS slots. This was a real
browser run, not only compilation. Generated HTML bundles are not source changes.

The final Studio JVM suite completed 2,627 tests with no failures and 62 skipped.
Both repository diffs passed `git diff --check`. No commits or pushes were made;
the pre-existing Studio stash was preserved.
