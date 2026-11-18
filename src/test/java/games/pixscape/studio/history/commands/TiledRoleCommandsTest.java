package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialFootprintComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.helper.GameObjectGizmoGeometry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import org.junit.Test;

import static org.junit.Assert.*;

public class TiledRoleCommandsTest {
    @Test
    public void forbiddenCompositionAndActorCommandsLeaveEntitiesAndHistoryUntouched() {
        try (Fixture f = new Fixture(TiledProjection.ORTHO)) {
            int sprite = f.node(false, -1);
            var initializer = new GenericEntityInitializer(f.world);
            initializer.syncFrom(sprite);
            f.world.process();
            for (int parent : new int[]{f.map, f.tiles, f.image}) {
                assertNotNull(CreateGameObjectChildCommand.parentRejection(f.world, parent));
                assertThrows(IllegalArgumentException.class, () -> f.history.execute(new CreateGameObjectChildCommand(
                        f.world, f.history.historyIds(), f.ids, parent, initializer, null)));
                assertThrows(IllegalArgumentException.class, () -> f.history.execute(new AddEntityToGameObjectCommand(
                        f.world, f.history.historyIds(), f.ids, sprite, parent, 0)));
            }
            for (int structure : new int[]{f.map, f.tiles, f.objects, f.image}) {
                assertThrows(IllegalArgumentException.class, () -> f.history.execute(new AddEntityToGameObjectCommand(
                        f.world, f.history.historyIds(), f.ids, structure, sprite, 0)));
                f.history.execute(new AddPhysicsBodyCommand(f.world, f.history.historyIds(), f.physics,
                        structure, PhysicsBodyComponent.DYNAMIC, true));
                assertFalse(f.world.getMapper(PhysicsBodyComponent.class).has(structure));
                assertThrows(IllegalArgumentException.class, () -> f.history.execute(new ToggleSpatialActorCommand(
                        f.world, f.history.historyIds(), structure, true, true, new SpatialFootprintComponent())));
                assertFalse(f.world.getMapper(SpatialFootprintComponent.class).has(structure));
            }
            assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(sprite));
            assertFalse(f.world.getMapper(GameObjectComponent.class).has(sprite));
            assertFalse(f.world.getMapper(GameObjectComponent.class).has(f.tiles));
            assertFalse(f.history.canUndo());
            assertEquals(0, f.history.getCursor());
        }
    }

    @Test
    public void objectLayerKeepsObjectCompositionThroughRepeatedHistory() {
        try (Fixture f = new Fixture(TiledProjection.ISO)) {
            int sprite = f.node(false, -1);
            f.world.process();
            assertNull(CreateGameObjectChildCommand.parentRejection(f.world, f.objects));
            f.history.execute(new AddEntityToGameObjectCommand(f.world, f.history.historyIds(), f.ids,
                    sprite, f.objects, 0));
            for (int cycle = 0; cycle < 3; cycle++) {
                f.world.process();
                assertEquals(f.ids.getStableId(f.objects),
                        f.world.getMapper(GameObjectMemberComponent.class).get(sprite).parentStableId);
                f.history.undo();
                f.world.process();
                assertFalse(f.world.getMapper(GameObjectMemberComponent.class).has(sprite));
                f.history.redo();
            }
        }
    }

    @Test
    public void translationsKeepLocalAndWorldGridOriginsDistinctWithoutFakeBounds() {
        for (TiledProjection projection : new TiledProjection[]{TiledProjection.ORTHO, TiledProjection.ISO}) {
            try (Fixture f = new Fixture(projection)) {
                f.move(f.tiles, 3.5f, -4.25f);
                f.move(f.map, 17.25f, 8.5f);
                for (int cycle = 0; cycle < 3; cycle++) {
                    f.world.process();
                    assertEquals(20.75f, f.grid().data.originX, 0f);
                    assertEquals(4.25f, f.grid().data.originY, 0f);
                    assertEquals(3.5f, f.grid().originX, 0f);
                    f.history.undo();
                    f.world.process();
                    assertEquals(3.5f, f.grid().data.originX, 0f);
                    f.history.redo();
                }
                var geometry = new GameObjectGizmoGeometry(f.world);
                assertFalse(geometry.writeWorldCorners(f.map, 8f, new float[8]));
                assertFalse(geometry.writeLocalBounds(f.map, new float[4]));
                assertFalse(geometry.writeWorldCorners(f.tiles, 8f, new float[8]));
                for (int entity : new int[]{f.map, f.tiles}) {
                    var before = EditTransformCommand.Snapshot.capture(f.pose(entity));
                    assertThrows(IllegalArgumentException.class, () -> f.history.execute(new EditTransformCommand(
                            f.world, f.history.historyIds(), entity, TransformOp.SCALE, before, before.withUniformScale(2f))));
                    assertThrows(IllegalArgumentException.class, () -> f.history.execute(new EditTransformCommand(
                            f.world, f.history.historyIds(), entity, TransformOp.ROTATE, before, before.withRotationRad(1f))));
                    assertEquals(1f, f.pose(entity).scaleX, 0f);
                }
                assertEquals(2, f.history.getCursor());
            }
        }
    }

    @Test
    public void mapsCanBeCapturedForClipboardButNeverGameObjectAssetsOrChildInitializers() {
        try (Fixture f = new Fixture(TiledProjection.ORTHO)) {
            var capture = new EntityGraphCaptureService(f.world);
            assertThrows(IllegalArgumentException.class, () -> capture.captureForGameObject(IntArray.with(f.map)));
            assertNotNull(capture.capture(IntArray.with(f.map)));
            int parent = f.node(true, -1);
            f.world.process();
            var initializer = new GenericEntityInitializer(f.world);
            initializer.syncFrom(f.map);
            assertThrows(IllegalArgumentException.class, () -> f.history.execute(new CreateGameObjectChildCommand(
                    f.world, f.history.historyIds(), f.ids, parent, initializer, null)));
            assertFalse(f.history.canUndo());
        }
    }

    @Test
    public void invalidClipboardOwnershipIsRejectedBeforeAllocatingOrPublishing() {
        try (Fixture f = new Fixture(TiledProjection.ORTHO)) {
            int parent = f.node(true, -1);
            f.world.process();
            var graph = new EntityGraphCaptureService(f.world).capture(IntArray.with(f.map, parent));
            int parentSource = graph.entries().stream().filter(entry -> !entry.initializer().isTiledStructure())
                    .findFirst().orElseThrow().sourceEntityId();
            var entries = new java.util.ArrayList<games.pixscape.studio.service.entitygraph.EntityGraphEntry>();
            for (var entry : graph.entries()) {
                entries.add(entry.parentSourceEntityId() == -1 && entry.initializer().isTiledStructure()
                        ? new games.pixscape.studio.service.entitygraph.EntityGraphEntry(entry.sourceEntityId(), parentSource,
                            entry.gameObjectRoot(), entry.initializer(), entry.customProperties()) : entry);
            }
            int nextId = f.scene.nextEntityStableId;
            int active = f.world.getAspectSubscriptionManager().get(com.artemis.Aspect.all()).getEntities().size();
            var invalid = new games.pixscape.studio.service.entitygraph.EntityGraph(entries);
            var spawn = new games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService(
                    f.world, f.history, f.ids, null, () -> false);
            assertThrows(IllegalArgumentException.class, () -> spawn.instantiate(invalid, 0, 0, 0, "Invalid paste"));
            assertEquals(nextId, f.scene.nextEntityStableId);
            assertEquals(active, f.world.getAspectSubscriptionManager().get(com.artemis.Aspect.all()).getEntities().size());
            assertFalse(f.history.canUndo());
        }
    }

    private static final class Fixture implements AutoCloseable {
        final World world = new World(new WorldConfigurationBuilder()
                .with(new DirtyTrackerSystem(32), new GameObjectHierarchySystem(32)).build());
        final HistoryManager history = new HistoryManager(32);
        final IdentityRegistry ids = new IdentityRegistry();
        final SceneMeta scene = new SceneMeta();
        final PhysicsService physics = new PhysicsService(world, null, scene);
        final int map, tiles, objects, image;

        Fixture(TiledProjection projection) {
            ids.bind(world, scene);
            map = node(true, -1);
            var data = world.getMapper(TiledMapComponent.class).create(map);
            data.projection = projection;
            data.tileWidth = 32;
            data.tileHeight = 16;
            data.width = data.height = 8;
            tiles = layer(TiledMapLayerComponent.Kind.TILE);
            var grid = world.getMapper(TiledLayerComponent.class).create(tiles);
            grid.projection = projection;
            grid.tileWidth = 32;
            grid.tileHeight = 16;
            grid.mapWidthCells = grid.mapHeightCells = 8;
            grid.chunkSize = 4;
            grid.data = grid.createLayerData();
            objects = layer(TiledMapLayerComponent.Kind.OBJECT);
            image = layer(TiledMapLayerComponent.Kind.IMAGE);
            world.process();
        }

        int layer(TiledMapLayerComponent.Kind kind) {
            int entity = node(kind == TiledMapLayerComponent.Kind.OBJECT, map);
            world.getMapper(TiledMapLayerComponent.class).create(entity).kind = kind;
            return entity;
        }

        int node(boolean group, int parent) {
            int entity = world.create();
            world.getMapper(TransformComponent.class).create(entity);
            world.getMapper(EntityIndexComponent.class).create(entity);
            ids.ensureStableId(entity);
            if (group) {
                world.getMapper(GameObjectComponent.class).create(entity);
            }
            if (parent >= 0) {
                world.getMapper(GameObjectMemberComponent.class).create(entity).parentStableId = ids.getStableId(parent);
            }
            return entity;
        }

        TransformComponent pose(int entity) {
            return world.getMapper(TransformComponent.class).get(entity);
        }

        TiledLayerComponent grid() {
            return world.getMapper(TiledLayerComponent.class).get(tiles);
        }

        void move(int entity, float x, float y) {
            var before = EditTransformCommand.Snapshot.capture(pose(entity));
            history.execute(new EditTransformCommand(world, history.historyIds(), entity, TransformOp.MOVE,
                    before, before.withX(x).withY(y)));
        }

        @Override
        public void close() {
            ids.bind(null, null);
            world.dispose();
        }
    }
}
