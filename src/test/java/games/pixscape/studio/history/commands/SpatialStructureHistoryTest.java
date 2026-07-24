package games.pixscape.studio.history.commands;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.SpatialBlockSelectionService;
import games.pixscape.studio.service.spatial.SpatialStructureGeometryCache;
import org.junit.Assert;
import org.junit.Test;

public class SpatialStructureHistoryTest {
    @Test
    public void firstBlockCreatesMissingComponentOnlyWhenCommandExecutes() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(4, 4, 16, 16, 4);
        tiled.data.setTile(1, 1, 1);
        HistoryManager history = new HistoryManager(8);
        history.historyIds().ensureForEntity(layer);
        SpatialBlockData candidate = wall(0, 0, 1, 1, 1, 1, 0f, 10f);

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                world, history.historyIds(), new SpatialBlockSelectionService(), layer, candidate);

        Assert.assertFalse(world.getMapper(SpatialBlocksComponent.class).has(layer));
        history.execute(command);
        SpatialBlocksComponent created = world.getMapper(SpatialBlocksComponent.class).get(layer);
        Assert.assertEquals(1, created.blocks.size);
        Assert.assertEquals(1, created.blocks.first().id);
    }

    @Test
    public void addUsesMaxPlusOneOnceAndRedoRestoresTheSameId() {
        Fixture fixture = fixture();
        fixture.walls.blocks.add(wall(1, 1, 0, 0, 1, 1, 0f, 10f));
        fixture.walls.blocks.add(wall(3, 2, 3, 0, 1, 1, 0f, 10f));
        fixture.walls.nextSpatialBlockId = 4;
        SpatialBlockData candidate = wall(0, 0, 6, 0, 1, 1, 0f, 10f);
        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                fixture.world, fixture.history.historyIds(), fixture.selection, fixture.layer, candidate);

        fixture.history.execute(command);
        Assert.assertEquals(4, command.getBlockId());
        Assert.assertNotNull(find(fixture.walls, 4));
        fixture.history.undo();
        Assert.assertNull(find(fixture.walls, 4));

        fixture.walls.blocks.add(wall(20, 20, 9, 0, 1, 1, 0f, 10f));
        fixture.walls.nextSpatialBlockId = 21;
        fixture.history.redo();
        Assert.assertEquals(4, command.getBlockId());
        Assert.assertNotNull(find(fixture.walls, 4));
    }

    @Test
    public void mergeUndoRedoRestoresEveryStructureIdentityAtomically() {
        Fixture fixture = fixture();
        fixture.walls.blocks.add(wall(2, 7, 0, 2, 4, 1, 0f, 10f));
        fixture.walls.blocks.add(wall(8, 3, 6, 2, 4, 1, 0f, 10f));
        SpatialBlockData bridge = wall(0, 0, 2, 0, 6, 5, 99f, 99f);

        AddSpatialBlockCommand command = new AddSpatialBlockCommand(
                fixture.world, fixture.history.historyIds(), fixture.selection, fixture.layer, bridge);
        fixture.history.execute(command);
        assertAllStructure(fixture.walls, 3);

        fixture.history.undo();
        Assert.assertEquals(2, fixture.walls.blocks.size);
        Assert.assertEquals(7, find(fixture.walls, 2).structureId);
        Assert.assertEquals(3, find(fixture.walls, 8).structureId);

        fixture.history.redo();
        assertAllStructure(fixture.walls, 3);
    }

    @Test
    public void splitUndoRedoUsesDeterministicIdsWithoutDuplicates() {
        Fixture fixture = fixture();
        fixture.walls.blocks.add(wall(2, 4, 0, 2, 4, 1, 0f, 10f));
        fixture.walls.blocks.add(wall(8, 4, 6, 2, 4, 1, 0f, 10f));
        fixture.walls.blocks.add(wall(10, 4, 2, 0, 6, 5, 0f, 10f));

        DeleteSpatialBlockCommand command = new DeleteSpatialBlockCommand(
                fixture.world, fixture.history.historyIds(), fixture.selection, fixture.layer, 10);
        fixture.history.execute(command);
        Assert.assertEquals(4, find(fixture.walls, 2).structureId);
        Assert.assertEquals(5, find(fixture.walls, 8).structureId);

        fixture.history.undo();
        Assert.assertEquals(3, fixture.walls.blocks.size);
        assertAllStructure(fixture.walls, 4);
        fixture.history.redo();
        Assert.assertEquals(2, fixture.walls.blocks.size);
        Assert.assertEquals(5, find(fixture.walls, 8).structureId);
    }

    @Test
    public void sharedAltitudeAndHeightEditUndoAsOneOperation() {
        Fixture fixture = fixture();
        SpatialBlockData horizontal = wall(1, 6, 0, 2, 5, 1, 0f, 10f);
        SpatialBlockData vertical = wall(2, 6, 2, 0, 1, 5, 0f, 10f);
        fixture.walls.blocks.add(horizontal);
        fixture.walls.blocks.add(vertical);
        SpatialBlockData edited = horizontal.copy();
        edited.altitude = 4f;
        edited.height = 18f;

        fixture.history.execute(new EditSpatialBlockCommand(
                fixture.world, fixture.history.historyIds(), fixture.selection,
                fixture.layer, 1, horizontal.copy(), edited));
        assertProperties(fixture.walls, 4f, 18f);
        fixture.history.undo();
        assertProperties(fixture.walls, 0f, 10f);
        fixture.history.redo();
        assertProperties(fixture.walls, 4f, 18f);
    }

    @Test
    public void undoRedoReconstructsIdenticalCompiledEnvelope() {
        Fixture fixture = fixture();
        fixture.walls.blocks.add(wall(1, 4, 0, 0, 4, 1, 0f, 10f));
        fixture.walls.blocks.add(wall(2, 4, 0, 0, 1, 4, 0f, 10f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        cache.synchronize(fixture.layer, fixture.walls,
                fixture.world.getMapper(TiledLayerComponent.class).get(fixture.layer).data);
        String before = signature(cache.structure(0));

        fixture.history.execute(new DeleteSpatialBlockCommand(
                fixture.world, fixture.history.historyIds(), fixture.selection, fixture.layer, 2));
        cache.synchronize(fixture.layer, fixture.walls,
                fixture.world.getMapper(TiledLayerComponent.class).get(fixture.layer).data);
        String deleted = signature(cache.structure(0));
        Assert.assertNotEquals(before, deleted);

        fixture.history.undo();
        cache.synchronize(fixture.layer, fixture.walls,
                fixture.world.getMapper(TiledLayerComponent.class).get(fixture.layer).data);
        Assert.assertEquals(before, signature(cache.structure(0)));
        fixture.history.redo();
        cache.synchronize(fixture.layer, fixture.walls,
                fixture.world.getMapper(TiledLayerComponent.class).get(fixture.layer).data);
        Assert.assertEquals(deleted, signature(cache.structure(0)));
    }

    private static Fixture fixture() {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layer);
        tiled.data = new TiledMapLayerData(12, 12, 16, 16, 4);
        for (int gy = 0; gy < 12; gy++) for (int gx = 0; gx < 12; gx++) tiled.data.setTile(gx, gy, 1);
        SpatialBlocksComponent walls = world.getMapper(SpatialBlocksComponent.class).create(layer);
        HistoryManager history = new HistoryManager(8);
        history.historyIds().ensureForEntity(layer);
        return new Fixture(world, layer, walls, history, new SpatialBlockSelectionService());
    }

    private static SpatialBlockData wall(int id, int structureId, int x, int y, int width, int depth,
                                         float altitude, float height) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structureId;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.altitude = altitude;
        wall.height = height;
        wall.beginAuthoredLinkedTileRefs();
        for (int gy = y; gy < y + depth; gy++) for (int gx = x; gx < x + width; gx++) wall.addLinkedTileRef(gx, gy, 1);
        return wall;
    }

    private static SpatialBlockData find(SpatialBlocksComponent walls, int id) {
        for (int i = 0; i < walls.blocks.size; i++) if (walls.blocks.get(i).id == id) return walls.blocks.get(i);
        return null;
    }

    private static void assertAllStructure(SpatialBlocksComponent walls, int structureId) {
        for (int i = 0; i < walls.blocks.size; i++) Assert.assertEquals(structureId, walls.blocks.get(i).structureId);
    }

    private static void assertProperties(SpatialBlocksComponent walls, float altitude, float height) {
        for (int i = 0; i < walls.blocks.size; i++) {
            Assert.assertEquals(altitude, walls.blocks.get(i).altitude, 0f);
            Assert.assertEquals(height, walls.blocks.get(i).height, 0f);
        }
    }

    private static String signature(CompiledSpatialStructure structure) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < structure.segmentCount(); i++) {
            out.append(structure.startX(i)).append(',').append(structure.startY(i)).append('-')
                    .append(structure.endX(i)).append(',').append(structure.endY(i)).append(';');
        }
        return out.toString();
    }

    private record Fixture(World world, int layer, SpatialBlocksComponent walls,
                           HistoryManager history, SpatialBlockSelectionService selection) { }
}
