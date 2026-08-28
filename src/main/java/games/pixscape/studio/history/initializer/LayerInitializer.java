package games.pixscape.studio.history.initializer;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.LayerParallaxComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

/**
 * Initializer specialized for layer entities.
 */
public final class LayerInitializer extends AbstractCommonInitializer {

    private boolean hasLayerIndex;
    private int layerIndex;

    private boolean hasLayerMeta;
    private String layerName = "Layer";
    private String layerDescription = "";
    private boolean layerLocked = false;

    private boolean hasParallax;
    private float parallaxX = 1f;
    private float parallaxY = 1f;

    // --- LayerComponent ---
    private boolean hasLayerType;
    private int layerType = LayerComponent.TYPE_CLASSIC;
    private boolean layerSpatialEnabled = false;

    // Initial creation
    private boolean hasTiledDimensions;
    private int tiledWidthCells;
    private int tiledHeightCells;

    // Snapshot
    private boolean hasTiledData;
    private int snapTiledWidth;
    private int snapTiledHeight;
    private float snapOriginX;
    private float snapOriginY;
    private boolean snapTiledSpatialEnabled;
    private float snapTiledDefaultAltitude;
    private float snapTiledDefaultHeight;
    private boolean hasSpatialBlocks;
    private int nextSpatialBlockId = 1;
    private final Array<SpatialBlockData> snapSpatialBlocks =
            new Array<>(SpatialBlockData[]::new);

    private final TiledAllocatorService tiledAllocatorService;

    public LayerInitializer(World world,
                            TiledAllocatorService tiledAllocatorService) {
        super(world);
        this.tiledAllocatorService = tiledAllocatorService;
    }

    @Override
    public void syncFrom(int e) {
        super.syncFrom(e);

        ComponentMapper<LayerComponent> mLayer = world.getMapper(LayerComponent.class);
        ComponentMapper<LayerMetaComponent> mMeta = world.getMapper(LayerMetaComponent.class);
        ComponentMapper<LayerParallaxComponent> mPar = world.getMapper(LayerParallaxComponent.class);
        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        ComponentMapper<SpatialBlocksComponent> mSpatial = world.getMapper(SpatialBlocksComponent.class);

        if (mLayer.has(e)) {
            LayerComponent li = mLayer.get(e);
            hasLayerIndex = true;
            layerIndex = li.layerIndex;
            hasLayerType = true;
            layerType = li.type;
            layerSpatialEnabled = li.spatialEnabled;
        } else {
            hasLayerIndex = false;
            hasLayerType = false;
            layerSpatialEnabled = false;
        }

        if (mMeta.has(e)) {
            LayerMetaComponent meta = mMeta.get(e);
            hasLayerMeta = true;
            layerName = meta.name;
            layerDescription = meta.description;
            layerLocked = meta.locked;
        } else {
            hasLayerMeta = false;
        }

        if (mPar.has(e)) {
            LayerParallaxComponent par = mPar.get(e);
            hasParallax = true;
            parallaxX = par.factorX;
            parallaxY = par.factorY;
        } else {
            hasParallax = false;
        }

        if (mTiled.has(e)) {
            TiledLayerComponent t = mTiled.get(e);

            hasTiledData = true;
            snapTiledWidth = t.mapWidthCells;
            snapTiledHeight = t.mapHeightCells;
            snapOriginX = t.originX;
            snapOriginY = t.originY;
            snapTiledSpatialEnabled = t.spatialEnabled;
            snapTiledDefaultAltitude = t.defaultTileAltitude;
            snapTiledDefaultHeight = t.defaultTileHeight;
        } else {
            hasTiledData = false;
            snapTiledSpatialEnabled = false;
            snapTiledDefaultAltitude = 0f;
            snapTiledDefaultHeight = 0f;
        }
        hasSpatialBlocks = mSpatial.has(e);
        snapSpatialBlocks.clear();
        if (hasSpatialBlocks) {
            SpatialBlocksComponent spatial = mSpatial.get(e);
            nextSpatialBlockId = spatial.nextSpatialBlockId;
            for (int i = 0; i < spatial.blocks.size; i++)
                snapSpatialBlocks.add(spatial.blocks.get(i).copy());
        }
    }

    @Override
    public void init(int e) {
        super.init(e);

        ComponentMapper<LayerComponent> mLayer = world.getMapper(LayerComponent.class);
        ComponentMapper<LayerMetaComponent> mMeta = world.getMapper(LayerMetaComponent.class);
        ComponentMapper<LayerParallaxComponent> mPar = world.getMapper(LayerParallaxComponent.class);
        ComponentMapper<TiledLayerComponent> mTiled = world.getMapper(TiledLayerComponent.class);
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);

        if (hasLayerIndex) {
            LayerComponent li = mLayer.has(e) ? mLayer.get(e) : mLayer.create(e);
            li.layerIndex = layerIndex;
            if (hasLayerType) {
                li.type = layerType;
            } else {
                li.type = LayerComponent.TYPE_CLASSIC;
            }
            li.spatialEnabled = layerSpatialEnabled;
            if (dirty != null) {
                dirty.layer(e);
                dirty.order(e);
            }
        }

        // --- TILED layer support ---
        if (hasLayerType && layerType == LayerComponent.TYPE_TILED) {

            TiledLayerComponent tiled =
                    world.getMapper(TiledLayerComponent.class).create(e);

            ProjectConfig cfg = ProjectConfig.getInstance();
            SceneMeta meta = (cfg != null) ? cfg.getCurrentSceneMeta() : null;

            if (meta == null) {
                throw new IllegalStateException("SceneMeta required for Tiled layer");
            }

            if (hasTiledData) {
                // redo via snapshot
                tiled.mapWidthCells = snapTiledWidth;
                tiled.mapHeightCells = snapTiledHeight;
                tiled.originX = snapOriginX;
                tiled.originY = snapOriginY;
                tiled.spatialEnabled = snapTiledSpatialEnabled;
                tiled.defaultTileAltitude = snapTiledDefaultAltitude;
                tiled.defaultTileHeight = snapTiledDefaultHeight;

            } else if (hasTiledDimensions) {
                // initial creation
                tiled.mapWidthCells = tiledWidthCells;
                tiled.mapHeightCells = tiledHeightCells;
                tiled.originX = 0f;
                tiled.originY = 0f;

            } else {
                // safety fallback
                tiled.mapWidthCells = 256;
                tiled.mapHeightCells = 256;
                tiled.originX = 0f;
                tiled.originY = 0f;
            }

            tiled.data = new TiledMapLayerData(
                    tiled.mapWidthCells,
                    tiled.mapHeightCells,
                    (int) meta.tileWidth,
                    (int) meta.tileHeight,
                    meta.chunkSize,
                    meta.tiledProjection
            );

            tiled.data.originX = tiled.originX;
            tiled.data.originY = tiled.originY;
            tiled.data.spatialEnabled = tiled.spatialEnabled;
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;

            tiledAllocatorService.allocateLayer(tiled);

            tiled.atlasTag = cfg.canonicalSceneTagCurrent();
        }

        if (hasLayerMeta) {
            LayerMetaComponent meta = mMeta.has(e) ? mMeta.get(e) : mMeta.create(e);
            meta.name = layerName;
            meta.description = layerDescription;
            meta.locked = layerLocked;
        }

        if (hasSpatialBlocks) {
            SpatialBlocksComponent spatial = world.getMapper(SpatialBlocksComponent.class).create(e);
            spatial.nextSpatialBlockId = nextSpatialBlockId;
            for (int i = 0; i < snapSpatialBlocks.size; i++)
                spatial.blocks.add(snapSpatialBlocks.get(i).copy());
        }

        if (hasParallax) {
            LayerParallaxComponent par = mPar.has(e) ? mPar.get(e) : mPar.create(e);
            par.factorX = parallaxX;
            par.factorY = parallaxY;
        }
    }

    @Override
    public String label() {
        return "Layer";
    }

    public LayerInitializer configureNewLayer(String name, int index) {
        return configureLayerDefaults(name, index, LayerComponent.TYPE_CLASSIC);
    }

    private LayerInitializer configureLayerDefaults(String name, int index, int type) {
        hasLayerIndex = true;
        layerIndex = index;

        hasMeta = true;
        metaKind = EntityKind.LAYER;

        hasLayerMeta = true;
        layerName = name;
        layerDescription = "";
        layerLocked = false;

        hasVisibility = true;
        visible = true;

        hasParallax = false;

        hasLayerType = true;
        layerType = type;
        layerSpatialEnabled = false;

        return this;
    }

    public LayerInitializer configureNewTiledLayer(
            String name,
            int index,
            int width,
            int height
    ) {
        configureLayerDefaults(name, index, LayerComponent.TYPE_TILED);

        hasTiledDimensions = true;
        tiledWidthCells = width;
        tiledHeightCells = height;

        return this;
    }
}
