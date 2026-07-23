package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.LayerService;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.physics.PhysicsSelectionService;
import games.pixscape.studio.service.physics.SpatialOwnedFixtureSupport;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.tiled.TiledAllocatorService;
import org.junit.Assert;
import org.junit.Test;

import java.lang.reflect.Proxy;

public class SpatialBlockCommandsTest {
    @Test
    public void addSpatialBlock_allocatesStableIdAndUndoRedoRestoresSelection() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Wall", 2f, 3f);
        occupyLinkedTiles(world, layerId, block);

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                block
        );
        history.execute(command);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(layerId);
        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(1, component.blocks.first().id);
        Assert.assertEquals(2f, component.blocks.first().x, 0.0001f);
        Assert.assertEquals(3f, component.blocks.first().y, 0.0001f);
        Assert.assertTrue(component.blocks.first().linkedTileRefsAuthored);
        Assert.assertEquals(1, component.blocks.first().linkedTileRefs.size);
        Assert.assertEquals(2, component.blocks.first().linkedTileRefs.get(0).gx);
        Assert.assertEquals(3, component.blocks.first().linkedTileRefs.get(0).gy);
        Assert.assertEquals(1001, component.blocks.first().linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(1, command.getBlockId());
        Assert.assertEquals(layerId, selection.getEditingLayerEntityId());
        Assert.assertEquals(1, selection.getSelectedBlockId());

        history.undo();
        Assert.assertEquals(0, component.blocks.size);
        Assert.assertEquals(layerId, selection.getEditingLayerEntityId());
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());

        history.redo();
        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(1, component.blocks.first().id);
        Assert.assertEquals(1, component.blocks.first().linkedTileRefs.size);
        Assert.assertEquals(1001, component.blocks.first().linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(1, selection.getSelectedBlockId());
    }

    @Test
    public void addSpatialBlock_redoDoesNotDuplicateLinkedRefs() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Wall", 2f, 3f);
        occupyLinkedTiles(world, layerId, block);

        history.execute(new AddSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                block
        ));

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(layerId);
        history.undo();
        history.redo();
        history.undo();
        history.redo();

        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(1, component.blocks.first().linkedTileRefs.size);
        Assert.assertEquals(2, component.blocks.first().linkedTileRefs.first().gx);
        Assert.assertEquals(3, component.blocks.first().linkedTileRefs.first().gy);
    }

    @Test
    public void addSpatialBlock_refusesMalformedAuthoredGeometry() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlockData block = block(0, "Invalid", 2f, 3f);
        block.width = 0f;

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                block
        );

        Assert.assertTrue(command.isNoop());
        command.redo();
        Assert.assertFalse(world.getMapper(SpatialBlocksComponent.class).has(layerId));
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());
    }

    @Test
    public void spatialBlockCopyPreservesLinkedRefsDeeplyEnoughForHistory() {
        SpatialBlockData original = block(3, "Original", 4f, 5f);

        SpatialBlockData copy = original.copy();
        original.linkedTileRefs.first().tileAssetId = 999;
        original.addLinkedTileRef(6, 7, 777);

        Assert.assertTrue(copy.linkedTileRefsAuthored);
        Assert.assertEquals(1, copy.linkedTileRefs.size);
        Assert.assertEquals(4, copy.linkedTileRefs.first().gx);
        Assert.assertEquals(5, copy.linkedTileRefs.first().gy);
        Assert.assertEquals(1003, copy.linkedTileRefs.first().tileAssetId);
    }

    @Test
    public void editSpatialBlock_updatesRuntimeDataWithUndoRedo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(7, "Before", 1f, 1f);
        occupyLinkedTiles(world, layerId, original);
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.name = "After";
        after.x = 1.12f;
        after.y = 1.18f;
        after.width = 0.73f;
        after.depth = 0.61f;
        after.altitude = 6f;
        after.height = 32f;
        after.physicsCollision = true;

        history.execute(new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                7,
                before,
                after
        ));

        SpatialBlockData edited = component.blocks.first();
        Assert.assertEquals("After", edited.name);
        Assert.assertEquals(1.12f, edited.x, 0f);
        Assert.assertEquals(1.18f, edited.y, 0f);
        Assert.assertEquals(0.73f, edited.width, 0f);
        Assert.assertEquals(0.61f, edited.depth, 0f);
        Assert.assertEquals(1, edited.linkedTileRefs.size);
        Assert.assertEquals(6f, edited.altitude, 0.0001f);
        Assert.assertEquals(32f, edited.height, 0.0001f);
        Assert.assertTrue(edited.physicsCollision);

        history.undo();
        SpatialBlockData restored = component.blocks.first();
        Assert.assertEquals("Before", restored.name);
        Assert.assertEquals(1f, restored.width, 0.0001f);
        Assert.assertFalse(restored.physicsCollision);

        history.redo();
        Assert.assertEquals("After", component.blocks.first().name);
    }

    @Test
    public void moveSpatialBlock_onlyChangesTilePosition() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(3, "Mover", 2.1f, 4.1f);
        setLinkedRect(block, 2, 4, 4, 6);
        occupyLinkedTiles(world, layerId, block);
        block.width = 0.5f;
        block.depth = 0.5f;
        block.height = 12f;
        component.blocks.add(block);

        history.execute(new MoveSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                3,
                2.1f,
                4.1f,
                3.2f,
                5.2f
        ));

        Assert.assertEquals(3.2f, component.blocks.first().x, 0f);
        Assert.assertEquals(5.2f, component.blocks.first().y, 0f);
        Assert.assertEquals(0.5f, component.blocks.first().width, 0f);
        Assert.assertEquals(12f, component.blocks.first().height, 0.0001f);

        history.undo();
        Assert.assertEquals(2.1f, component.blocks.first().x, 0f);
        Assert.assertEquals(4.1f, component.blocks.first().y, 0f);
        Assert.assertEquals(0.5f, component.blocks.first().width, 0f);
        Assert.assertEquals(12f, component.blocks.first().height, 0.0001f);

        history.redo();
        Assert.assertEquals(3.2f, component.blocks.first().x, 0f);
        Assert.assertEquals(5.2f, component.blocks.first().y, 0f);
        Assert.assertEquals(0.5f, component.blocks.first().width, 0f);
        Assert.assertEquals(12f, component.blocks.first().height, 0.0001f);
    }

    @Test
    public void deleteSpatialBlock_preservesOrderOnUndo() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData a = block(1, "A", 0f, 0f);
        SpatialBlockData b = block(2, "B", 1f, 0f);
        SpatialBlockData c = block(3, "C", 2f, 0f);
        occupyLinkedTiles(world, layerId, a);
        occupyLinkedTiles(world, layerId, b);
        occupyLinkedTiles(world, layerId, c);
        component.blocks.add(a);
        component.blocks.add(b);
        component.blocks.add(c);
        selection.selectBlock(layerId, 2);

        history.execute(new DeleteSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                2
        ));

        Assert.assertEquals(2, component.blocks.size);
        Assert.assertEquals(1, component.blocks.get(0).id);
        Assert.assertEquals(3, component.blocks.get(1).id);
        Assert.assertEquals(SpatialBlockSelectionService.NO_BLOCK, selection.getSelectedBlockId());

        history.undo();
        Assert.assertEquals(3, component.blocks.size);
        Assert.assertEquals(1, component.blocks.get(0).id);
        Assert.assertEquals(2, component.blocks.get(1).id);
        Assert.assertEquals(3, component.blocks.get(2).id);
        Assert.assertEquals(2, selection.getSelectedBlockId());

        history.redo();
        Assert.assertEquals(2, component.blocks.size);
        Assert.assertEquals(3, component.blocks.get(1).id);
    }

    @Test
    public void editSpatialBlockPhysicsCollision_enablesStaticLayerBodyAndAddsFootprintPolygon() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(4, "Collider", 2f, 3f);
        setLinkedRect(original, 2, 3, 3, 3);
        occupyLinkedTiles(world, layerId, original);
        original.width = 2f;
        original.depth = 1f;
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.physicsCollision = true;

        history.execute(new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                4,
                before,
                after
        ));

        PhysicsBodyComponent body = world.getMapper(PhysicsBodyComponent.class).get(layerId);
        Assert.assertEquals(PhysicsBodyComponent.STATIC, body.type);
        FixtureDefData fixture = spatialFixture(world, layerId, 4);
        Assert.assertNotNull(fixture);
        Assert.assertEquals(FixtureDefData.SHAPE_POLYGON, fixture.shapeType);
        Assert.assertEquals(4, fixture.polyCount);
        Assert.assertArrayEquals(
                new float[]{0f, 1.25f, 1f, 1.75f, 0.5f, 2f, -0.5f, 1.5f},
                fixture.polyVerts,
                0.0001f
        );

        history.undo();
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(layerId));
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(layerId));
        Assert.assertFalse(component.blocks.first().physicsCollision);

        history.redo();
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(layerId));
        Assert.assertTrue(component.blocks.first().physicsCollision);
    }

    @Test
    public void editSpatialBlockFootprint_updatesGeneratedCollisionPolygonAndUndoRestoresIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(5, "Collider", 1f, 1f);
        setLinkedRect(original, 1, 1, 3, 3);
        occupyLinkedTiles(world, layerId, original);
        original.physicsCollision = true;
        component.blocks.add(original);

        SpatialBlockData before = original.copy();
        SpatialBlockData after = original.copy();
        after.x = 1.2f;
        after.y = 1.3f;
        after.width = 1.4f;
        after.depth = 0.5f;

        SpatialBlockPhysicsSync.sync(world, layerId, original, this);
        float[] originalVerts = spatialFixture(world, layerId, 5).polyVerts.clone();

        history.execute(new EditSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                5,
                before,
                after
        ));

        Assert.assertArrayEquals(
                new float[]{0.45f, 0.625f, 1.15f, 0.975f, 0.9f, 1.1f, 0.2f, 0.75f},
                spatialFixture(world, layerId, 5).polyVerts,
                0.0001f
        );

        history.undo();
        Assert.assertArrayEquals(originalVerts, spatialFixture(world, layerId, 5).polyVerts, 0.0001f);
    }

    @Test
    public void deleteSpatialBlock_removesGeneratedCollisionFixtureAndUndoRestoresIt() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);

        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(6, "Collider", 1f, 1f);
        occupyLinkedTiles(world, layerId, block);
        block.physicsCollision = true;
        component.blocks.add(block);
        SpatialBlockPhysicsSync.sync(world, layerId, block, this);

        history.execute(new DeleteSpatialBlockCommand(
                world,
                history.historyIds(),
                selection,
                layerId,
                6
        ));

        Assert.assertNull(spatialFixture(world, layerId, 6));

        history.undo();
        Assert.assertNotNull(spatialFixture(world, layerId, 6));
    }

    @Test
    public void toggleOffRemovesOnlyOwnedFixtureAndUndoRestoresExactSnapshot() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(16);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(12, "Collider", 2f, 3f);
        block.physicsCollision = true;
        blocks.blocks.add(block);
        SpatialBlockPhysicsSync.sync(world, layerId, block, this);
        FixtureDefData owned = spatialFixture(world, layerId, block.id);
        owned.density = 3.25f;
        owned.friction = 0.73f;
        owned.isSensor = true;
        float[] exactVerts = owned.polyVerts.clone();
        FixtureDefData custom = FixtureCommandSupport.createDefaultFixture();
        int customId = custom.fixtureId;
        world.getMapper(PhysicsFixturesComponent.class).get(layerId).fixtures.add(custom);
        EventFlow.i().flush();

        SpatialBlockData disabled = block.copy();
        disabled.physicsCollision = false;
        history.execute(new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, block.id, block.copy(), disabled));

        Assert.assertFalse(blocks.blocks.first().physicsCollision);
        Assert.assertNull(spatialFixture(world, layerId, block.id));
        Assert.assertNotNull(fixture(world, layerId, customId));
        Assert.assertEquals(1, history.getCursor());

        for (int i = 0; i < 3; i++) {
            history.undo();
            FixtureDefData restored = spatialFixture(world, layerId, block.id);
            Assert.assertTrue(blocks.blocks.first().physicsCollision);
            Assert.assertNotNull(restored);
            Assert.assertArrayEquals(exactVerts, restored.polyVerts, 0f);
            Assert.assertEquals(3.25f, restored.density, 0f);
            Assert.assertEquals(0.73f, restored.friction, 0f);
            Assert.assertTrue(restored.isSensor);
            Assert.assertNotNull(fixture(world, layerId, customId));
            history.redo();
            Assert.assertFalse(blocks.blocks.first().physicsCollision);
            Assert.assertNull(spatialFixture(world, layerId, block.id));
            Assert.assertNotNull(fixture(world, layerId, customId));
        }
    }

    @Test
    public void deletingOwnedFixtureUsesSameAtomicToggleTransition() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        SelectionService selection = selectionService(world, history);
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(13, "Collider", 2f, 3f);
        block.physicsCollision = true;
        blocks.blocks.add(block);
        SpatialBlockPhysicsSync.sync(world, layerId, block, this);
        FixtureDefData owned = spatialFixture(world, layerId, block.id);
        owned.restitution = 0.42f;
        physicsSelection.setSelectedFixture(layerId, owned.fixtureId);
        selection.selectOnly(layerId);
        EventFlow.i().flush();
        int[] coherentPublications = {0};
        int[] selectionClearedPublications = {0};
        EventFlow.Listener<EventFlow.PhysicsBodyStructureChanged> listener = event -> {
            if (event.entityId() != layerId) return;
            coherentPublications[0]++;
            boolean enabled = blocks.blocks.first().physicsCollision;
            Assert.assertFalse(enabled && spatialFixture(world, layerId, block.id) == null);
        };
        EventFlow.Listener<EventFlow.FixtureSelectionCleared> selectionListener = event ->
                selectionClearedPublications[0]++;
        EventFlow.i().subscribe(EventFlow.PhysicsBodyStructureChanged.class, listener);
        EventFlow.i().subscribe(EventFlow.FixtureSelectionCleared.class, selectionListener);

        try {
            history.execute(new DeleteFixtureCommand(
                    world, history.historyIds(), physicsSelection,
                    layerId, owned.fixtureId));
            EventFlow.i().flush();

            Assert.assertFalse(blocks.blocks.first().physicsCollision);
            Assert.assertNull(spatialFixture(world, layerId, block.id));
            Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE,
                    physicsSelection.getSelectedFixtureId());
            Assert.assertTrue(physicsSelection.isFocusedBody(layerId));
            Assert.assertFalse(physicsSelection.hasHoveredFixture());
            Assert.assertEquals(1, selection.getSelectionSnapshot().size);
            Assert.assertEquals(1, selectionClearedPublications[0]);
            Assert.assertEquals(1, history.getCursor());

            history.undo();
            EventFlow.i().flush();
            Assert.assertTrue(blocks.blocks.first().physicsCollision);
            Assert.assertEquals(owned.fixtureId, spatialFixture(world, layerId, block.id).fixtureId);
            Assert.assertEquals(0.42f, spatialFixture(world, layerId, block.id).restitution, 0f);
            Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE,
                    physicsSelection.getSelectedFixtureId());
            Assert.assertTrue(physicsSelection.isFocusedBody(layerId));
            Assert.assertEquals(1, selectionClearedPublications[0]);

            physicsSelection.setSelectedFixture(layerId, owned.fixtureId);
            selection.selectOnly(layerId);

            history.redo();
            EventFlow.i().flush();
            Assert.assertFalse(blocks.blocks.first().physicsCollision);
            Assert.assertNull(spatialFixture(world, layerId, block.id));
            Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE,
                    physicsSelection.getSelectedFixtureId());
            Assert.assertTrue(physicsSelection.isFocusedBody(layerId));
            Assert.assertEquals(1, selection.getSelectionSnapshot().size);
            Assert.assertEquals(2, selectionClearedPublications[0]);
            Assert.assertEquals(3, coherentPublications[0]);
        } finally {
            EventFlow.i().unsubscribe(EventFlow.PhysicsBodyStructureChanged.class, listener);
            EventFlow.i().unsubscribe(EventFlow.FixtureSelectionCleared.class, selectionListener);
        }
    }

    @Test
    public void deletingCustomFixtureDoesNotChangeSpatialFlag() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        SelectionService selection = selectionService(world, history);
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(17, "Collider", 2f, 3f);
        block.physicsCollision = true;
        blocks.blocks.add(block);
        SpatialBlockPhysicsSync.sync(world, layerId, block, this);
        FixtureDefData custom = FixtureCommandSupport.createDefaultFixture();
        world.getMapper(PhysicsFixturesComponent.class).get(layerId).fixtures.add(custom);
        physicsSelection.setSelectedFixture(layerId, custom.fixtureId);
        selection.selectOnly(layerId);

        history.execute(new DeleteFixtureCommand(
                world, history.historyIds(), physicsSelection,
                layerId, custom.fixtureId));

        Assert.assertTrue(blocks.blocks.first().physicsCollision);
        Assert.assertNotNull(spatialFixture(world, layerId, block.id));
        Assert.assertNull(fixture(world, layerId, custom.fixtureId));
        Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE,
                physicsSelection.getSelectedFixtureId());
        Assert.assertTrue(physicsSelection.isFocusedBody(layerId));
        Assert.assertEquals(1, selection.getSelectionSnapshot().size);
        history.undo();
        Assert.assertTrue(blocks.blocks.first().physicsCollision);
        Assert.assertNotNull(fixture(world, layerId, custom.fixtureId));
        Assert.assertEquals(PhysicsSelectionService.NO_FIXTURE,
                physicsSelection.getSelectedFixtureId());
    }

    @Test
    public void deletingUnselectedFixturePreservesUnrelatedSelection() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        SelectionService selection = selectionService(world, history);
        int bodyId = tiledLayer(world);
        history.historyIds().ensureForEntity(bodyId);
        world.getMapper(PhysicsBodyComponent.class).create(bodyId);
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).create(bodyId);
        FixtureDefData selected = FixtureCommandSupport.createDefaultFixture();
        FixtureDefData deleted = FixtureCommandSupport.createDefaultFixture();
        fixtures.fixtures.add(selected);
        fixtures.fixtures.add(deleted);
        physicsSelection.setSelectedFixture(bodyId, selected.fixtureId);
        selection.selectOnly(bodyId);

        history.execute(new DeleteFixtureCommand(
                world, history.historyIds(), physicsSelection,
                bodyId, deleted.fixtureId));

        Assert.assertEquals(selected.fixtureId, physicsSelection.getSelectedFixtureId());
        Assert.assertTrue(physicsSelection.isFocusedBody(bodyId));
        Assert.assertEquals(1, selection.getSelectionSnapshot().size);
        Assert.assertNull(fixture(world, bodyId, deleted.fixtureId));
    }

    @Test
    public void rejectedFixtureDeletionPreservesSelectionAndHistory() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        SelectionService selection = selectionService(world, history);
        int bodyId = tiledLayer(world);
        history.historyIds().ensureForEntity(bodyId);
        world.getMapper(PhysicsBodyComponent.class).create(bodyId);
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).create(bodyId);
        FixtureDefData target = FixtureCommandSupport.createDefaultFixture();
        fixtures.fixtures.add(target);
        physicsSelection.setSelectedFixture(bodyId, target.fixtureId);
        selection.selectOnly(bodyId);
        DeleteFixtureCommand command = new DeleteFixtureCommand(
                world, history.historyIds(), physicsSelection,
                bodyId, target.fixtureId);
        fixtures.fixtures.clear();

        history.execute(command);

        Assert.assertEquals(target.fixtureId, physicsSelection.getSelectedFixtureId());
        Assert.assertTrue(physicsSelection.isFocusedBody(bodyId));
        Assert.assertEquals(1, selection.getSelectionSnapshot().size);
        Assert.assertEquals(0, history.getCursor());
    }

    @Test
    public void ownedFixtureSilentlyRejectsGeometryButAllowsMaterial() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        PhysicsSelectionService physicsSelection = new PhysicsSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = block(14, "Collider", 2f, 3f);
        block.physicsCollision = true;
        blocks.blocks.add(block);
        SpatialBlockPhysicsSync.sync(world, layerId, block, this);
        FixtureDefData owned = spatialFixture(world, layerId, block.id);
        physicsSelection.setSelectedFixture(layerId, owned.fixtureId);
        Assert.assertTrue(SpatialOwnedFixtureSupport.isOwned(world, layerId, owned.fixtureId));
        EditFixtureCommand.Snapshot before = EditFixtureCommand.Snapshot.capture(owned);
        FixtureDefData moved = owned.copy();
        moved.offsetX += 1f;
        EditFixtureCommand geometry = new EditFixtureCommand(
                world, history.historyIds(), physicsSelection, layerId, owned.fixtureId,
                before, EditFixtureCommand.Snapshot.capture(moved), 0, true);
        Assert.assertTrue(geometry.isNoop());
        int revisionBefore = blocks.revision;
        history.execute(geometry);
        Assert.assertEquals(0f, owned.offsetX, 0f);
        Assert.assertEquals(revisionBefore, blocks.revision);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.isDirty());
        MoveFixtureCommand move = new MoveFixtureCommand(
                world, history.historyIds(), physicsSelection, layerId, owned.fixtureId,
                owned.offsetX, owned.offsetY, owned.offsetX + 1f, owned.offsetY);
        Assert.assertTrue(move.isNoop());
        history.execute(move);
        ResizeBoxFixtureCommand resize = new ResizeBoxFixtureCommand(
                world, history.historyIds(), physicsSelection, layerId, owned.fixtureId,
                owned.offsetX, owned.offsetY, owned.halfW, owned.halfH,
                owned.offsetX, owned.offsetY, owned.halfW + 1f, owned.halfH);
        Assert.assertTrue(resize.isNoop());
        history.execute(resize);
        MovePolygonVertexCommand moveVertex = new MovePolygonVertexCommand(
                world, history.historyIds(), physicsSelection, layerId, owned.fixtureId,
                0, owned.polyVerts[0], owned.polyVerts[1],
                owned.polyVerts[0] + 1f, owned.polyVerts[1]);
        Assert.assertTrue(moveVertex.isNoop());
        history.execute(moveVertex);
        float[] replacementVerts = owned.polyVerts.clone();
        replacementVerts[0] += 1f;
        ReplacePolygonVerticesCommand replaceVertices = new ReplacePolygonVerticesCommand(
                world, history.historyIds(), physicsSelection, layerId, owned.fixtureId,
                owned.polyVerts, owned.polyCount, replacementVerts, owned.polyCount);
        Assert.assertTrue(replaceVertices.isNoop());
        history.execute(replaceVertices);
        ApplyAuthoredPolygonCommand applyPolygon = new ApplyAuthoredPolygonCommand(
                world, history.historyIds(), physicsSelection, layerId, 1L,
                replacementVerts, owned.polyCount, owned, owned.fixtureId);
        Assert.assertTrue(applyPolygon.isNoop());
        history.execute(applyPolygon);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.isDirty());

        FixtureDefData material = owned.copy();
        material.density = 9f;
        history.execute(new EditFixtureCommand(
                world, history.historyIds(), physicsSelection, layerId, owned.fixtureId,
                before, EditFixtureCommand.Snapshot.capture(material), 0, false));
        Assert.assertEquals(9f, spatialFixture(world, layerId, block.id).density, 0f);
        Assert.assertEquals(1, history.getCursor());
    }

    @Test
    public void removingPhysicsComponentDisablesAllSpatialFlagsAndUndoRestoresThem() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData a = block(15, "A", 2f, 3f);
        SpatialBlockData b = block(16, "B", 4f, 3f);
        a.physicsCollision = true;
        b.physicsCollision = true;
        blocks.blocks.add(a);
        blocks.blocks.add(b);
        SpatialBlockPhysicsSync.sync(world, layerId, a, this);
        SpatialBlockPhysicsSync.sync(world, layerId, b, this);

        history.execute(new TogglePhysicsBodyCommand(
                world, history.historyIds(), layerId, false, PhysicsBodyComponent.STATIC, false));

        Assert.assertFalse(blocks.blocks.get(0).physicsCollision);
        Assert.assertFalse(blocks.blocks.get(1).physicsCollision);
        Assert.assertFalse(world.getMapper(PhysicsFixturesComponent.class).has(layerId));
        Assert.assertEquals(1, history.getCursor());

        history.undo();
        Assert.assertTrue(blocks.blocks.get(0).physicsCollision);
        Assert.assertTrue(blocks.blocks.get(1).physicsCollision);
        Assert.assertNotNull(spatialFixture(world, layerId, a.id));
        Assert.assertNotNull(spatialFixture(world, layerId, b.id));
    }

    @Test
    public void fractionalFootprintUndoRedoHasNoDriftAndKeepsRefs() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(11, "Precise", 2f, 3f);
        occupyLinkedTiles(world, layerId, original);
        component.blocks.add(original);
        SpatialBlockData after = original.copy();
        after.x = 2.1415927f;
        after.y = 3.271828f;
        after.width = 0.612345f;
        after.depth = 0.198765f;

        history.execute(new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, 11, original.copy(), after));
        for (int i = 0; i < 4; i++) {
            history.undo();
            history.redo();
        }

        SpatialBlockData edited = component.blocks.first();
        Assert.assertEquals(Float.floatToIntBits(after.x), Float.floatToIntBits(edited.x));
        Assert.assertEquals(Float.floatToIntBits(after.y), Float.floatToIntBits(edited.y));
        Assert.assertEquals(Float.floatToIntBits(after.width), Float.floatToIntBits(edited.width));
        Assert.assertEquals(Float.floatToIntBits(after.depth), Float.floatToIntBits(edited.depth));
        Assert.assertEquals(1, edited.linkedTileRefs.size);
        Assert.assertEquals(2, edited.linkedTileRefs.first().gx);
        Assert.assertEquals(3, edited.linkedTileRefs.first().gy);
    }

    @Test
    public void rejectedExecuteLeavesModelRevisionAndHistoryUnchanged() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlockData candidate = block(0, "Candidate", 2f, 3f);
        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, candidate);
        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 0);

        Application previousApplication = Gdx.app;
        int[] rejectionFeedback = {0};
        Gdx.app = countingApplication(rejectionFeedback);
        try {
            history.execute(command);
        } finally {
            Gdx.app = previousApplication;
        }

        Assert.assertFalse(world.getMapper(SpatialBlocksComponent.class).has(layerId));
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertFalse(history.isDirty());
        Assert.assertEquals(1, rejectionFeedback[0]);
    }

    @Test
    public void topologyRejectedExecuteDoesNotEnterHistory() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData existing = block(1, "Existing", 2f, 3f);
        component.blocks.add(existing);
        SpatialBlockData duplicate = block(0, "Duplicate", 2f, 3f);
        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, duplicate);

        history.execute(command);

        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals("Existing", component.blocks.first().name);
        Assert.assertEquals(0, component.revision);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertFalse(history.canRedo());
    }

    @Test
    public void rejectedUndoStaysInUndoUntilAuthoredMapIsValidAgain() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData original = block(7, "Before", 2f, 3f);
        component.blocks.add(original);
        SpatialBlockData edited = original.copy();
        edited.name = "After";
        history.execute(new EditSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, 7, original.copy(), edited));
        int revisionAfterExecute = component.revision;
        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 0);

        history.undo();

        Assert.assertEquals("After", component.blocks.first().name);
        Assert.assertEquals(revisionAfterExecute, component.revision);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
        Assert.assertEquals("Edit Spatial Wall", history.peekUndoLabel());

        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 1);
        history.undo();
        Assert.assertEquals("Before", component.blocks.first().name);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertTrue(history.canRedo());
    }

    @Test
    public void rejectedRedoStaysRetryableWithoutRevisionOrModelChange() {
        World world = new World(new WorldConfiguration());
        HistoryManager history = new HistoryManager(8);
        SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        int layerId = tiledLayer(world);
        history.historyIds().ensureForEntity(layerId);
        SpatialBlockData candidate = block(0, "Candidate", 2f, 3f);
        occupyLinkedTiles(world, layerId, candidate);
        history.execute(new AddSpatialBlockCommand(
                world, history.historyIds(), selection, layerId, candidate));
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(layerId);
        history.undo();
        int revisionAfterUndo = component.revision;
        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 0);

        history.redo();

        Assert.assertEquals(0, component.blocks.size);
        Assert.assertEquals(revisionAfterUndo, component.revision);
        Assert.assertEquals(0, history.getCursor());
        Assert.assertFalse(history.canUndo());
        Assert.assertTrue(history.canRedo());
        Assert.assertEquals("Add Spatial Wall", history.peekRedoLabel());

        world.getMapper(TiledLayerComponent.class).get(layerId).data.setTile(2, 3, 1);
        history.redo();
        Assert.assertEquals(1, component.blocks.size);
        Assert.assertEquals(revisionAfterUndo + 1, component.revision);
        Assert.assertEquals(1, history.getCursor());
        Assert.assertTrue(history.canUndo());
        Assert.assertFalse(history.canRedo());
    }

    private static SpatialBlockData block(int id, String name, float x, float y) {
        SpatialBlockData block = new SpatialBlockData();
        block.id = id;
        block.structureId = Math.max(1, id);
        block.name = name;
        block.x = x;
        block.y = y;
        block.width = 1f;
        block.depth = 1f;
        block.altitude = 0f;
        block.height = 8f;
        block.actorOccluder = true;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(Math.round(x), Math.round(y), 1000 + Math.max(1, id));
        return block;
    }

    private static Application countingApplication(int[] errorCount) {
        return (Application) Proxy.newProxyInstance(
                Application.class.getClassLoader(),
                new Class<?>[]{Application.class},
                (proxy, method, args) -> {
                    if ("error".equals(method.getName())) errorCount[0]++;
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == long.class) return 0L;
                    if (type == float.class) return 0f;
                    return null;
                });
    }

    private static void occupyLinkedTiles(World world, int layerId, SpatialBlockData block) {
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).get(layerId);
        for (int i = 0; i < block.linkedTileRefs.size; i++) {
            SpatialBlockData.LinkedTileRef ref = block.linkedTileRefs.get(i);
            tiled.data.setTile(ref.gx, ref.gy, ref.tileAssetId);
        }
    }

    private static void setLinkedRect(SpatialBlockData block,
                                      int minGx, int minGy, int maxGx, int maxGy) {
        block.beginAuthoredLinkedTileRefs();
        for (int gy = minGy; gy <= maxGy; gy++) {
            for (int gx = minGx; gx <= maxGx; gx++) block.addLinkedTileRef(gx, gy, 1);
        }
    }

    private static int tiledLayer(World world) {
        int layerId = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(
                10,
                10,
                100,
                50,
                16,
                SceneMetaRuntime.TiledProjection.ISO
        );
        for (int gy = 0; gy < 10; gy++) {
            for (int gx = 0; gx < 10; gx++) tiled.data.setTile(gx, gy, 1);
        }
        return layerId;
    }

    private static FixtureDefData spatialFixture(World world, int layerId, int blockId) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(layerId, null);
        if (fixtures == null) return null;
        int fixtureId = SpatialBlockPhysicsSync.fixtureIdForBlock(blockId);
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) {
                return fixture;
            }
        }
        return null;
    }

    private static FixtureDefData fixture(World world, int layerId, int fixtureId) {
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).getSafe(layerId, null);
        if (fixtures == null) return null;
        for (FixtureDefData fixture : fixtures.fixtures) {
            if (fixture != null && fixture.fixtureId == fixtureId) return fixture;
        }
        return null;
    }

    private static SelectionService selectionService(World world, HistoryManager history) {
        LayerService layers = new LayerService(
                world,
                new TiledAllocatorService(),
                history.historyIds());
        return new SelectionService(world, layers);
    }
}
