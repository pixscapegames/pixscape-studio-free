# Tile-layer execution naming

The first integration step consumes the Runtime's explicit tile-layer names:
`TiledLayerData`, `TiledLayerRef`, `TiledLayerFacade`, `TiledRenderState`,
`TiledLayerLightingGeometry` and `RoundedLayerSupport`. Painting, selection,
fallback rendering, lighting, history snapshots, TMX materialization and scene
activation keep using the existing chunks and buffers.

Runtime layer materialization is now `TiledLayerComponent.createLayerData()`;
grid validation is `validateGridConfiguration()`. The visible render slices and
prepared-order lookups also name tile layers explicitly. These are Java symbol
changes, not serialized field changes. Existing scene files are not rewritten by
this step.

The Studio currently retains its single-grid map workflow. A multi-layer map
container, its layer tree, active-layer commands, aggregate duplication/deletion,
and explicit data conversion still require implementation. The existing
`AddTiledMapCommand`, map initializer, properties panel and map selection state
must be split by responsibility when that workflow is integrated; changing their
labels alone would not implement it.

The agreed map container includes TMX tile, image and object layers in their
original order. Image/object layers retain their supported components, tools
and properties. An image or object layer between two tile layers must remain
inside that ordered composition.

Runtime's [model and naming document](../../pixscape-runtime/docs/tiled-map-layers.md)
describes the applied mapping and the remaining ownership changes.
