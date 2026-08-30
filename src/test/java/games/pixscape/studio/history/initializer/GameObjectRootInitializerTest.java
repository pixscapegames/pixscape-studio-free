package games.pixscape.studio.history.initializer;

import com.artemis.World;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.studio.component.EntityMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.model.EntityKind;
import org.junit.Test;

import static org.junit.Assert.*;

public class GameObjectRootInitializerTest {
    @Test
    public void createsFocusedSceneOnlyCompositionRootAndSnapshotsIt() {
        World world = new World();
        try {
            GameObjectRootInitializer initializer = new GameObjectRootInitializer(world)
                    .configure(12f, -3f, 4);
            initializer.setIdentityStableId(77);
            int entity = world.create();
            initializer.init(entity);

            assertTrue(world.getMapper(PixscapeIdentityComponent.class).has(entity));
            assertTrue(world.getMapper(PixscapeTagComponent.class).has(entity));
            assertTrue(world.getMapper(CustomPropertiesComponent.class).has(entity));
            assertTrue(world.getMapper(EntityIndexComponent.class).has(entity));
            assertTrue(world.getMapper(TransformComponent.class).has(entity));
            assertTrue(world.getMapper(GameObjectComponent.class).has(entity));
            assertEquals(EntityKind.GAME_OBJECT,
                    world.getMapper(EntityMetaComponent.class).get(entity).kind);
            assertEquals("", world.getMapper(GameObjectComponent.class).get(entity).sourceAssetId);
            assertFalse(world.getMapper(VisibilityComponent.class).has(entity));
            assertFalse(world.getMapper(DimensionsComponent.class).has(entity));
            assertFalse(world.getMapper(AABBComponent.class).has(entity));
            assertFalse(world.getMapper(OrientedBoundsComponent.class).has(entity));
            assertFalse(world.getMapper(PhysicsBodyComponent.class).has(entity));
            assertFalse(world.getMapper(SpatialBlocksComponent.class).has(entity));
            TransformComponent authored = world.getMapper(TransformComponent.class).get(entity);
            authored.originX = 14f;
            authored.originY = 9f;

            GameObjectRootInitializer snapshot = new GameObjectRootInitializer(world);
            snapshot.syncFrom(entity);
            int restored = world.create();
            snapshot.init(restored);
            assertEquals(77, world.getMapper(PixscapeIdentityComponent.class).get(restored).stableId);
            assertEquals(12f, world.getMapper(TransformComponent.class).get(restored).x, 0f);
            assertEquals(14f, world.getMapper(TransformComponent.class).get(restored).originX, 0f);
            assertEquals(9f, world.getMapper(TransformComponent.class).get(restored).originY, 0f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void createCommandUndoRedoRestoresTheRealRootAndItsHistoryIdentity() {
        World world = new World();
        try {
            HistoryManager history = new HistoryManager(8);
            int[] selected = {-1};
            GameObjectRootInitializer initializer = new GameObjectRootInitializer(world)
                    .configure(0f, 0f, 3);
            initializer.setIdentityStableId(91);
            CreateEntityCommand command = new CreateEntityCommand(
                    world, history.historyIds(), initializer, entityId -> selected[0] = entityId);

            history.execute(command);
            world.process();
            int created = selected[0];
            long historyId = history.historyIds().historyIdOfEntity(created);
            assertTrue(world.getMapper(GameObjectComponent.class).has(created));
            assertEquals("", world.getMapper(GameObjectComponent.class).get(created).sourceAssetId);
            assertTrue(historyId > 0L);

            history.undo();
            world.process();
            assertEquals(-1, history.historyIds().entityOfHistoryId(historyId));

            history.redo();
            world.process();
            int restored = history.historyIds().entityOfHistoryId(historyId);
            assertTrue(restored >= 0);
            assertEquals(restored, selected[0]);
            assertEquals(91, world.getMapper(PixscapeIdentityComponent.class).get(restored).stableId);
            assertEquals("", world.getMapper(GameObjectComponent.class).get(restored).sourceAssetId);
            assertEquals(3, world.getMapper(EntityIndexComponent.class).get(restored).layerIndex);
        } finally {
            world.dispose();
        }
    }
}
