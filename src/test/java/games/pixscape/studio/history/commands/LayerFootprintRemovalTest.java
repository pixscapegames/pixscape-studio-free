package games.pixscape.studio.history.commands;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.*;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.system.*;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.LayerService;
import org.junit.Test;
import static org.junit.Assert.*;

public class LayerFootprintRemovalTest {
    @Test
    public void undoOfActivationRestoresThePreviousStateWithoutInventingAPurge() {
        try (Fixture f = new Fixture()) {
            f.layer().spatialEnabled = false;
            int owner = f.owner(-1, 0, 9.25f);
            f.publish();
            f.history.execute(new ToggleSpatialActorLayerCommand(f.world, f.history.historyIds(), f.layers, f.layer, true));
            f.history.undo();
            assertFalse(f.layer().spatialEnabled);
            assertEquals(9.25f, f.footprint(owner).radiusPx, 0);
            f.history.redo();
            assertTrue(f.layer().spatialEnabled);
            assertEquals(9.25f, f.footprint(owner).radiusPx, 0);
        }
    }

    @Test
    public void removesEveryEffectiveOwnerAndRestoresExactValuesAcrossRepeatedHistory() {
        try (Fixture f = new Fixture()) {
            int root = f.owner(-1, 0, 1.25f); // Invisible owner: no visual components.
            int nested = f.owner(root, 7, 2.5f);
            int ownChild = f.owner(nested, 8, 3.75f);
            int inherited = f.entity(nested, 9);
            int other = f.owner(-1, 1, 4.5f);
            int otherChild = f.owner(other, 0, 5.5f); // Authored 0, effective 1: preserve.
            var body = f.world.getMapper(PhysicsBodyComponent.class).create(ownChild);
            var shapes = f.world.getMapper(PhysicsShapesComponent.class).create(ownChild);
            var joint = f.world.getMapper(PhysicsJointComponent.class).create(ownChild);
            var representation = f.world.getMapper(SpatialRepresentationComponent.class).create(inherited);
            games.pixscape.runtime.spatial.SpatialRectangleTestData.setHeight(representation, .7f);
            var rectangle = representation.rectangles.staticRectangle;
            rectangle.pivotX = 6.75f;
            rectangle.pivotY = -9.5f;
            int tiles = f.entity(-1, 0);
            f.world.getMapper(GameObjectComponent.class).remove(tiles);
            var tiled = f.world.getMapper(TiledLayerComponent.class).create(tiles);
            tiled.projection = games.pixscape.runtime.tiled.TiledProjection.ORTHO;
            tiled.tileWidth = tiled.tileHeight = 32;
            tiled.spatialEnabled = true;
            tiled.data = new games.pixscape.runtime.tiled.TiledLayerData();
            tiled.data.spatialEnabled = true;
            int map = games.pixscape.runtime.tiled.TiledMapHierarchy.createMap(f.world, f.identities,
                    "Map", 0, 0, tiled.projection, tiled.tileWidth, tiled.tileHeight, 1, 1);
            games.pixscape.runtime.tiled.TiledMapHierarchy.attachLayer(f.world, f.identities, map, tiles,
                    TiledMapLayerComponent.Kind.TILE, 0);
            var blocks = f.world.getMapper(SpatialBlocksComponent.class).create(tiles);
            var volume = new games.pixscape.runtime.spatial.SpatialBlockData();
            volume.id = blocks.allocateNextSpatialBlockId();
            volume.structureId = 1;
            volume.width = volume.depth = 1;
            volume.height = 37.5f;
            volume.altitude = 4.25f;
            blocks.blocks.add(volume);
            f.publish();
            assertEquals(nested, f.composition.state().spatialOwner(inherited));
            var expected = new SpatialFootprintComponent[]{f.footprint(root).copy(), f.footprint(nested).copy(), f.footprint(ownChild).copy()};
            f.history.execute(f.command());
            assertEquals(1, f.history.getCursor());
            for (int cycle = 0; cycle < 3; cycle++) {
                assertFalse(f.layer().spatialEnabled);
                for (int owner : new int[]{root, nested, ownChild}) {
                    assertFalse(f.world.getMapper(SpatialFootprintComponent.class).has(owner));
                }
                assertEquals(-1, f.composition.state().spatialOwner(inherited));
                assertEquals(4.5f, f.footprint(other).radiusPx, 0);
                assertEquals(5.5f, f.footprint(otherChild).radiusPx, 0);
                assertSame(body, f.world.getMapper(PhysicsBodyComponent.class).get(ownChild));
                assertSame(shapes, f.world.getMapper(PhysicsShapesComponent.class).get(ownChild));
                assertSame(joint, f.world.getMapper(PhysicsJointComponent.class).get(ownChild));
                assertSame(representation, f.world.getMapper(SpatialRepresentationComponent.class).get(inherited));
                assertSame(rectangle, representation.rectangles.staticRectangle);
                assertEquals(.7f, rectangle.height, 0);
                assertEquals(6.75f, rectangle.pivotX, 0);
                assertEquals(-9.5f, rectangle.pivotY, 0);
                assertTrue(tiled.spatialEnabled && tiled.data.spatialEnabled);
                assertSame(blocks, f.world.getMapper(SpatialBlocksComponent.class).get(tiles));
                assertSame(volume, blocks.blocks.first());
                assertEquals(37.5f, volume.height, 0);
                assertEquals(4.25f, volume.altitude, 0);
                assertEquals(1, volume.width, 0);
                assertEquals(1, volume.depth, 0);
                f.world.process();
                f.history.undo();
                assertTrue(f.layer().spatialEnabled);
                int i = 0;
                for (int owner : new int[]{root, nested, ownChild}) {
                    assertFootprint(expected[i++], f.footprint(owner));
                }
                assertFalse(f.world.getMapper(SpatialFootprintComponent.class).has(inherited));
                assertEquals(nested, f.composition.state().spatialOwner(inherited));
                f.world.process();
                f.history.redo();
            }
        }
    }

    @Test
    public void missingOwnerRejectsTheWholePreparedOperation() {
        try (Fixture f = new Fixture()) {
            int first = f.owner(-1, 0, 3);
            int second = f.owner(-1, 0, 5);
            f.publish();
            var command = f.command();
            f.world.delete(second);
            f.world.process();
            f.history.execute(command);
            assertTrue(f.layer().spatialEnabled);
            assertEquals(3, f.footprint(first).radiusPx, 0);
            assertFalse(f.history.canUndo());
        }
    }

    private static void assertFootprint(SpatialFootprintComponent expected, SpatialFootprintComponent actual) {
        assertEquals(expected.radiusPx, actual.radiusPx, 0);
        assertEquals(expected.altitude, actual.altitude, 0);
        assertEquals(expected.localOffsetXPx, actual.localOffsetXPx, 0);
        assertEquals(expected.localOffsetYPx, actual.localOffsetYPx, 0);
    }

    private static final class Fixture implements AutoCloseable {
        final GameObjectCompositionSystem composition = new GameObjectCompositionSystem(null);
        final World world = new World(new WorldConfigurationBuilder().with(new DirtyTrackerSystem(32),
                new GameObjectHierarchySystem(), composition).build());
        final IdentityRegistry identities = new IdentityRegistry();
        final HistoryManager history = new HistoryManager(20);
        final LayerService layers;
        final int layer;
        Fixture() {
            identities.bind(world, new SceneMetaRuntime());
            layers = new LayerService(world, null, history.historyIds(), identities);
            layer = layers.addLayerTop("Actors");
            layers.addLayerTop("Other");
            layer().spatialEnabled = true;
        }
        int entity(int parent, int authoredLayer) {
            int e = world.create();
            identities.ensureStableId(e);
            world.getMapper(TransformComponent.class).create(e);
            world.getMapper(EntityIndexComponent.class).create(e).layerIndex = authoredLayer;
            world.getMapper(GameObjectComponent.class).create(e);
            if (parent >= 0) {
                world.getMapper(GameObjectMemberComponent.class).create(e).parentStableId = identities.getStableId(parent);
            }
            return e;
        }
        int owner(int parent, int layer, float radius) {
            int e = entity(parent, layer);
            var fp = world.getMapper(SpatialFootprintComponent.class).create(e);
            fp.radiusPx = radius;
            fp.altitude = radius + .125f;
            fp.localOffsetXPx = -radius;
            fp.localOffsetYPx = radius * 2;
            return e;
        }
        void publish() {
            world.process();
            identities.rebuild();
        }
        LayerComponent layer() {
            return world.getMapper(LayerComponent.class).get(layer);
        }
        SpatialFootprintComponent footprint(int e) {
            return world.getMapper(SpatialFootprintComponent.class).get(e);
        }
        ToggleSpatialActorLayerCommand command() {
            return new ToggleSpatialActorLayerCommand(world, history.historyIds(), layers, layer, false);
        }
        @Override
        public void close() {
            identities.bind(null, null);
            world.dispose();
            games.pixscape.studio.event.EventFlow.i().discardPending();
        }
    }
}
