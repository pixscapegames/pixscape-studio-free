package games.pixscape.studio.history.commands;

import com.artemis.World;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.physics.PreparedPhysicsBodyCandidate;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialCompiledLayerCache;
import games.pixscape.runtime.spatial.SpatialProjectedFaceCache;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.history.HistoryIdRegistry;
import games.pixscape.studio.history.HistoryManager;

public final class EditTiledLayerSpatialDefaultsCommand implements Command, HistoryManager.SupportsNoop, OutcomeAwareCommand {
    public record Snapshot(float defaultAltitude, float defaultHeight) {
        public static Snapshot capture(TiledLayerComponent component) {
            if (component == null) return null;
            return new Snapshot(component.defaultTileAltitude, component.defaultTileHeight);
        }

        public Snapshot withDefaultAltitude(float value) {
            return new Snapshot(value, defaultHeight);
        }

        public Snapshot withDefaultHeight(float value) {
            return new Snapshot(defaultAltitude, value);
        }

        public boolean sameAs(Snapshot other) {
            if (other == null) return false;
            return Float.compare(defaultAltitude, other.defaultAltitude) == 0
                    && Float.compare(defaultHeight, other.defaultHeight) == 0;
        }
    }

    private final World world;
    private final HistoryIdRegistry historyIds;
    private final long layerHistoryId;
    private final Snapshot before;
    private final Snapshot after;
    private final boolean noop;
    private final games.pixscape.studio.configuration.SceneMeta documentMeta;

    public EditTiledLayerSpatialDefaultsCommand(World world,
                                                HistoryIdRegistry historyIds,
                                                int layerEntityId,
                                                Snapshot before,
                                                Snapshot after) {
        this.world = world;
        games.pixscape.studio.configuration.ProjectConfig config = games.pixscape.studio.configuration.ProjectConfig.getInstance();
        this.documentMeta = config != null ? config.getCurrentSceneMeta() : null;
        this.historyIds = historyIds;
        this.before = before;
        this.after = after;
        this.layerHistoryId = historyIds != null ? historyIds.ensureForEntity(layerEntityId) : -1L;
        this.noop = world == null
                || historyIds == null
                || layerHistoryId <= 0L
                || before == null
                || after == null
                || before.sameAs(after);
    }

    @Override
    public String label() {
        return "Edit Tiled Drawn Plane and Default Height";
    }

    @Override
    public boolean isNoop() {
        return noop;
    }

    @Override public void redo() { redoOutcome(); }
    @Override public void undo() { undoOutcome(); }
    @Override public CommandOutcome executeOutcome() { return apply(after); }
    @Override public CommandOutcome redoOutcome() { return apply(after); }
    @Override public CommandOutcome undoOutcome() { return apply(before); }

    private CommandOutcome apply(Snapshot snapshot) {
        if (noop || snapshot == null) return CommandOutcome.NO_CHANGE;
        int entityId = resolveEntityId();
        if (entityId < 0) return CommandOutcome.NO_CHANGE;
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).getSafe(entityId, null);
        if (tiled == null) return CommandOutcome.NO_CHANGE;
        if (!Float.isFinite(snapshot.defaultAltitude) || !Float.isFinite(snapshot.defaultHeight)) {
            return CommandOutcome.REJECTED;
        }
        float height = Math.max(0f, snapshot.defaultHeight);
        if (Float.compare(tiled.defaultTileAltitude, snapshot.defaultAltitude) == 0
                && Float.compare(tiled.defaultTileHeight, height) == 0) return CommandOutcome.NO_CHANGE;

        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).getSafe(entityId, null);
        boolean linked = shapes != null && FixtureCommandSupport.containsLinkedShape(shapes.shapes);
        PreparedPhysicsBodyCandidate physics = null;
        try {
            TiledMapLayerData candidate = projectionCandidate(tiled, snapshot.defaultAltitude, height);
            SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).getSafe(entityId, null);
            if (blocks != null) {
                if (SpatialBlockCommandSupport.validateBlocks(world, entityId, blocks.blocks)
                        != CommandOutcome.APPLIED) return CommandOutcome.REJECTED;
                SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache();
                compiled.ensure(blocks);
                new SpatialProjectedFaceCache().ensure(compiled, candidate);
            }
            if (linked) {
                PhysicsCompiledFixturesComponent current = world.getMapper(PhysicsCompiledFixturesComponent.class)
                        .getSafe(entityId, null);
                if (current == null || !current.valid || tiled.data == null) return CommandOutcome.REJECTED;
                physics = PhysicsService.prepareBodyCandidate(world, entityId, shapes.shapes,
                        requireDocumentPixelsPerMeter(), candidate);
            }
        } catch (RuntimeException failure) {
            if (com.badlogic.gdx.Gdx.app != null) com.badlogic.gdx.Gdx.app.error("TiledSpatialDefaults",
                    "Rejected drawn plane change for map " + entityId + ": " + failure.getMessage());
            return CommandOutcome.REJECTED;
        }

        // Blocks already have explicit absolute altitudes. Never infer inheritance from a value.
        tiled.defaultTileAltitude = snapshot.defaultAltitude;
        tiled.defaultTileHeight = height;
        syncRuntimeDefaults(tiled);
        if (linked) SpatialBlockCommandSupport.publishStaticTiledPhysicsCandidate(world, entityId, shapes.shapes, physics);
        markDirty(entityId);
        return CommandOutcome.APPLIED;
    }

    private static TiledMapLayerData projectionCandidate(TiledLayerComponent tiled, float altitude, float height) {
        TiledMapLayerData source = tiled.data;
        TiledMapLayerData map = source == null ? tiled.createMapData() : new TiledMapLayerData(
                source.mapWidth, source.mapHeight, source.tileWidth, source.tileHeight, source.chunkSize, source.projection);
        if (source != null) { map.originX = source.originX; map.originY = source.originY; }
        map.defaultTileAltitude = altitude;
        map.defaultTileHeight = height;
        return map;
    }

    private float requireDocumentPixelsPerMeter() {
        if (documentMeta == null || !Float.isFinite(documentMeta.pixelsPerMeter) || documentMeta.pixelsPerMeter <= 0f) {
            throw new IllegalStateException("Edited document pixelsPerMeter must be finite and positive.");
        }
        return documentMeta.pixelsPerMeter;
    }

    private int resolveEntityId() {
        int entityId = historyIds.entityOfHistoryId(layerHistoryId);
        if (entityId < 0 || !world.getEntityManager().isActive(entityId)) {
            return -1;
        }
        return entityId;
    }

    private void syncRuntimeDefaults(TiledLayerComponent tiled) {
        if (tiled.data != null) {
            tiled.data.defaultTileAltitude = tiled.defaultTileAltitude;
            tiled.data.defaultTileHeight = tiled.defaultTileHeight;
            tiled.data.markAllChunksContentDirty();
        }
    }

    private void markDirty(int entityId) {
        DirtyTrackerSystem dirty = world.getSystem(DirtyTrackerSystem.class);
        if (dirty != null) {
            dirty.layer(entityId);
            dirty.order(entityId);
        }
        EventFlow.i().publish(new EventFlow.LayerSpatialDepthChanged(entityId, EventFlow.tag(this)));
    }
}
