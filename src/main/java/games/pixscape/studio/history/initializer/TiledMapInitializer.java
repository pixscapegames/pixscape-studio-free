package games.pixscape.studio.history.initializer;

import com.artemis.ComponentMapper;
import com.artemis.World;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.ByteArray;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.render.PhysicsDirtyBits;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.model.EntityKind;
import games.pixscape.studio.service.tiled.TiledAllocatorService;

/** Dedicated deep snapshot/materializer for the transitional Tiled Map root entity. */
public final class TiledMapInitializer implements Initializer {
    private final World world;
    private final TiledAllocatorService allocator;

    private int layerIndex;
    private int zIndex;
    private int stableId = -1;
    private String identityName = "Map";
    private String note = "";
    private boolean visible = true;

    private String atlasTag = "main";
    private TiledProjection projection;
    private int tileWidth;
    private int tileHeight;
    private int mapWidthCells;
    private int mapHeightCells;
    private int chunkSize;
    private float originX;
    private float originY;
    private boolean spatialEnabled;
    private float defaultTileAltitude;
    private float defaultTileHeight;
    private final IntArray tileXs = new IntArray();
    private final IntArray tileYs = new IntArray();
    private final IntArray tileAssetIds = new IntArray();
    private final ByteArray tileTransformFlags = new ByteArray();
    private float[] tileAltitudes;
    private float[] tileHeights;
    private IntArray tileSpatialFlags;
    private ByteArray tileSpatialOverrides;

    private boolean hasSpatialBlocks;
    private int nextSpatialBlockId = 1;
    private final Array<SpatialBlockData> spatialBlocks = new Array<>(SpatialBlockData[]::new);

    private boolean hasPhysicsBody;
    private int bodyType = PhysicsBodyComponent.STATIC;
    private boolean fixedRotation;
    private boolean bullet;
    private boolean allowSleep = true;
    private boolean awake = true;
    private float gravityScale = 1f;
    private float linearDamping;
    private float angularDamping;
    private boolean hasPhysicsShapes;
    private final Array<PhysicsShapeData> physicsShapes = new Array<>();

    public TiledMapInitializer(World world, TiledAllocatorService allocator) {
        this.world = world;
        this.allocator = allocator;
    }

    public TiledMapInitializer configureNew(int layerIndex,
                                            int width,
                                            int height,
                                            String atlasTag,
                                            TiledProjection projection,
                                            int tileWidth,
                                            int tileHeight,
                                            int chunkSize) {
        if (projection == null || width <= 0 || height <= 0
                || tileWidth <= 0 || tileHeight <= 0 || chunkSize <= 0) {
            throw new IllegalArgumentException(
                    "Tiled map creation requires a projection and positive tile, map, and chunk dimensions.");
        }
        this.layerIndex = layerIndex;
        this.zIndex = 0;
        this.mapWidthCells = width;
        this.mapHeightCells = height;
        this.atlasTag = atlasTag != null && !atlasTag.isBlank() ? atlasTag : "main";
        this.projection = projection;
        this.tileWidth = tileWidth;
        this.tileHeight = tileHeight;
        this.chunkSize = chunkSize;
        return this;
    }

    @Override
    public void syncFrom(int entityId) {
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).get(entityId);
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).get(entityId);
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(entityId);
        if (index == null || identity == null || tiled == null) {
            throw new IllegalArgumentException(
                    "Tiled map snapshot requires EntityIndex, PixscapeIdentity and TiledLayer components.");
        }
        tiled.validateMapConfiguration();

        layerIndex = index.layerIndex;
        zIndex = index.zIndex;
        stableId = identity.stableId;
        identityName = identity.name != null ? identity.name : "Map";
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).getSafe(entityId, null);
        note = meta != null && meta.note != null ? meta.note : "";
        VisibilityComponent visibility = world.getMapper(VisibilityComponent.class).getSafe(entityId, null);
        visible = visibility == null || visibility.visible;

        atlasTag = tiled.atlasTag;
        projection = tiled.projection;
        tileWidth = tiled.tileWidth;
        tileHeight = tiled.tileHeight;
        mapWidthCells = tiled.mapWidthCells;
        mapHeightCells = tiled.mapHeightCells;
        chunkSize = tiled.chunkSize;
        originX = tiled.originX;
        originY = tiled.originY;
        spatialEnabled = tiled.spatialEnabled;
        defaultTileAltitude = tiled.defaultTileAltitude;
        defaultTileHeight = tiled.defaultTileHeight;
        tiled.ensureSparseTileStorageConsistency();
        copy(tileXs, tiled.tileXs);
        copy(tileYs, tiled.tileYs);
        copy(tileAssetIds, tiled.tileAssetIds);
        copy(tileTransformFlags, tiled.tileTransformFlags);
        tileAltitudes = copy(tiled.tileAltitudes);
        tileHeights = copy(tiled.tileHeights);
        tileSpatialFlags = tiled.tileSpatialFlags != null ? new IntArray(tiled.tileSpatialFlags) : null;
        tileSpatialOverrides = tiled.tileSpatialOverrides != null
                ? new ByteArray(tiled.tileSpatialOverrides) : null;

        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class)
                .getSafe(entityId, null);
        hasSpatialBlocks = blocks != null;
        spatialBlocks.clear();
        if (blocks != null) {
            nextSpatialBlockId = blocks.nextSpatialBlockId;
            for (int i = 0; i < blocks.blocks.size; i++) spatialBlocks.add(blocks.blocks.get(i).copy());
        }

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).getSafe(entityId, null);
        hasPhysicsBody = body != null;
        if (body != null) {
            bodyType = body.type;
            fixedRotation = body.fixedRotation;
            bullet = body.bullet;
            allowSleep = body.allowSleep;
            awake = body.awake;
            gravityScale = body.gravityScale;
            linearDamping = body.linearDamping;
            angularDamping = body.angularDamping;
        }
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).getSafe(entityId, null);
        hasPhysicsShapes = shapes != null;
        physicsShapes.clear();
        if (shapes != null && shapes.shapes != null) {
            for (int i = 0; i < shapes.shapes.size; i++) physicsShapes.add(shapes.shapes.get(i).copy());
        }
    }

    @Override
    public void init(int entityId) {
        SceneMeta sceneMeta = currentSceneMeta();
        if (sceneMeta == null) throw new IllegalStateException("SceneMeta required for Tiled map materialization.");

        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entityId);
        index.layerIndex = layerIndex;
        index.zIndex = zIndex;
        PixscapeIdentityComponent identity = world.getMapper(PixscapeIdentityComponent.class).create(entityId);
        identity.stableId = stableId;
        identity.name = identityName;
        EntityMetaComponent meta = world.getMapper(EntityMetaComponent.class).create(entityId);
        meta.kind = EntityKind.TILED_MAP;
        meta.note = note;
        // Stage 1 bridge: authored Tiled origin remains authoritative; Transform mirrors it only.
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entityId);
        transform.x = originX;
        transform.y = originY;
        transform.rotationRad = 0f;
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        transform.refreshCaches();
        // Stage 1 bridge: host layer visibility remains authoritative for rendering and UX.
        VisibilityComponent mapVisibility = world.getMapper(VisibilityComponent.class).create(entityId);
        mapVisibility.visible = visible;
        mapVisibility.culledByFrustum = false;
        mapVisibility.inView = true;

        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(entityId);
        tiled.atlasTag = atlasTag;
        tiled.projection = projection;
        tiled.tileWidth = tileWidth;
        tiled.tileHeight = tileHeight;
        tiled.mapWidthCells = mapWidthCells;
        tiled.mapHeightCells = mapHeightCells;
        tiled.chunkSize = chunkSize;
        tiled.originX = originX;
        tiled.originY = originY;
        tiled.spatialEnabled = spatialEnabled;
        tiled.defaultTileAltitude = defaultTileAltitude;
        tiled.defaultTileHeight = defaultTileHeight;
        copy(tiled.tileXs, tileXs);
        copy(tiled.tileYs, tileYs);
        copy(tiled.tileAssetIds, tileAssetIds);
        copy(tiled.tileTransformFlags, tileTransformFlags);
        tiled.tileAltitudes = copy(tileAltitudes);
        tiled.tileHeights = copy(tileHeights);
        tiled.tileSpatialFlags = tileSpatialFlags != null ? new IntArray(tileSpatialFlags) : null;
        tiled.tileSpatialOverrides = tileSpatialOverrides != null
                ? new ByteArray(tileSpatialOverrides) : null;
        tiled.data = tiled.createMapData();
        if (allocator != null) allocator.allocateLayer(tiled);
        restoreDenseTiles(tiled);
        if (allocator != null) allocator.synchronizeAnimations(tiled);

        if (hasSpatialBlocks) {
            SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(entityId);
            blocks.nextSpatialBlockId = nextSpatialBlockId;
            for (int i = 0; i < spatialBlocks.size; i++) blocks.blocks.add(spatialBlocks.get(i).copy());
        }

        if (hasPhysicsBody) restorePhysics(entityId, sceneMeta.pixelsPerMeter);

        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(entityId);
            dirty.order(entityId);
            if (hasPhysicsBody) dirty.physics(entityId, PhysicsDirtyBits.ALL);
        }
    }

    private void restoreDenseTiles(TiledLayerComponent tiled) {
        tiled.ensureSparseTileStorageConsistency();
        tiled.data.beginContentMutation();
        try {
            for (int i = 0; i < tiled.tileAssetIds.size; i++) {
                int gx = tiled.tileXs.get(i);
                int gy = tiled.tileYs.get(i);
                tiled.data.setTile(gx, gy, tiled.tileAssetIds.get(i), tiled.tileTransformFlags.get(i));
                if (tiled.hasSparseSpatialOverride(i)) {
                    tiled.data.setTileSpatialOverride(gx, gy, tiled.sparseTileAltitude(i),
                            tiled.sparseTileHeight(i), tiled.sparseTileSpatialFlags(i));
                }
            }
        } finally {
            tiled.data.endContentMutation();
        }
        tiled.data.markAllChunksContentDirty();
    }

    private void restorePhysics(int entityId, float pixelsPerMeter) {
        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).create(entityId);
        body.type = bodyType;
        body.fixedRotation = fixedRotation;
        body.bullet = bullet;
        body.allowSleep = allowSleep;
        body.awake = awake;
        body.gravityScale = gravityScale;
        body.linearDamping = linearDamping;
        body.angularDamping = angularDamping;
        Array<PhysicsShapeData> sources = new Array<>();
        if (hasPhysicsShapes) {
            for (int i = 0; i < physicsShapes.size; i++) sources.add(physicsShapes.get(i).copy());
        }
        PreparedPhysicsBodyCandidate prepared = PhysicsService.prepareBodyCandidate(
                world, entityId, sources, pixelsPerMeter);
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entityId);
        PhysicsCompiledFixturesComponent compiled = world.getMapper(PhysicsCompiledFixturesComponent.class)
                .create(entityId);
        PhysicsService.publishPreparedCandidate(shapes, compiled, prepared);
    }

    private SceneMeta currentSceneMeta() {
        ProjectConfig config = ProjectConfig.getInstance();
        return config != null ? config.getCurrentSceneMeta() : null;
    }

    public int stableId() {
        return stableId;
    }

    public void overrideLayerIndex(int value) {
        layerIndex = value;
    }

    @Override
    public String label() {
        return "TiledMap";
    }

    private static void copy(IntArray target, IntArray source) {
        target.clear();
        if (source != null) target.addAll(source);
    }

    private static void copy(ByteArray target, ByteArray source) {
        target.clear();
        if (source != null) target.addAll(source);
    }

    private static float[] copy(float[] source) {
        if (source == null) return null;
        float[] out = new float[source.length];
        System.arraycopy(source, 0, out, 0, source.length);
        return out;
    }
}
