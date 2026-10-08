package games.pixscape.studio.service.spatial;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.*;
import games.pixscape.runtime.tiled.*;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.EditSpatialBlockCommand;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class SpatialDrawnPlaneTest {
    @Test
    public void productionCreationEditAndSceneReloadKeepAbsoluteAltitudeAndAlignedProjection() throws Exception {
        float[][] cases = {{0, 0, 0}, {128, 128, 0}, {256, 256, 0},
                {128, 160, 32}, {128, 96, -32}};
        for (TiledProjection projection : new TiledProjection[]{TiledProjection.ISO, TiledProjection.ORTHO}) {
        for (float[] c : cases) {
            try (Fixture f = new Fixture(c[0], projection)) {
                SpatialBlockData block = f.create(27, 18, 27, 19);
                Assert.assertEquals(c[0], block.altitude, 0);
                Assert.assertEquals(27, block.x, 0);
                Assert.assertEquals(18, block.y, 0);
                Assert.assertEquals(1, block.width, 0);
                Assert.assertEquals(2, block.depth, 0);
                Assert.assertEquals(2, block.linkedTileRefs.size);
                if (c[1] != c[0]) {
                    SpatialBlockData after = block.copy(); after.altitude = c[1];
                    f.history.execute(new EditSpatialBlockCommand(f.world, f.history.historyIds(),
                            f.selection, f.owner, block.id, block, after));
                    block = f.blocks().blocks.first();
                }
                assertProjectionAndManipulation(f.map, f.blocks(), block, c[2]);
                // A tile-specific absolute altitude is independent of the map plane.
                f.map.setTileSpatialOverride(27, 18, 512, 20, 0);
                Assert.assertEquals(512, f.map.getTileAltitude(27, 18), 0);
                assertProjectionAndManipulation(f.map, f.blocks(), block, c[2]);
                FileHandle file = new FileHandle(File.createTempFile("spatial-plane-", ".json"));
                World loaded = world();
                try {
                    SceneService.saveScene(f.world, file, false);
                    SceneMetaRuntime meta = new SceneMetaRuntime(); meta.nextEntityStableId = 2;
                    SceneLoader.loadScene(loaded, file, false, meta);
                    int owner = loaded.getAspectSubscriptionManager().get(Aspect.all(TiledLayerComponent.class))
                            .getEntities().get(0);
                    TiledLayerComponent tiled = loaded.getMapper(TiledLayerComponent.class).get(owner);
                    SpatialBlocksComponent blocks = loaded.getMapper(SpatialBlocksComponent.class).get(owner);
                    Assert.assertEquals(c[0], tiled.defaultTileAltitude, 0);
                    Assert.assertEquals(c[1], blocks.blocks.first().altitude, 0);
                    Assert.assertEquals(128, blocks.blocks.first().height, 0);
                    TiledMapLayerData map = tiled.createMapData();
                    for (int i = 0; i < tiled.tileAssetIds.size; i++) {
                        map.setTile(tiled.tileXs.get(i), tiled.tileYs.get(i), tiled.tileAssetIds.get(i));
                    }
                    assertProjectionAndManipulation(map, blocks, blocks.blocks.first(), c[2]);
                } finally { loaded.dispose(); file.delete(); }
            }
        }
        }
    }

    @Test
    public void productionAttachmentToEqualAltitudeStructureUsesSameDrawnPlane() {
        try (Fixture f = new Fixture(128)) {
            SpatialBlockData host = f.create(36, 22, 36, 24);
            SpatialBlockData branch = f.create(36, 22, 38, 22);
            Assert.assertEquals(host.structureId, branch.structureId);
            Assert.assertEquals(128, branch.altitude, 0);
            Assert.assertEquals(128, host.altitude, 0);
            assertProjectionAndManipulation(f.map, f.blocks(), branch, 0);
        }
    }

    @Test
    public void overlayUsesNewPlaneWithUnchangedAuthoredBlock() {
        try (Fixture f = new Fixture(128)) {
            SpatialBlockData b = f.create(27, 18, 27, 19);
            float[] before = new float[8], after = new float[8];
            SpatialBlockProjection.projectBaseFootprint(f.map, b, before);
            f.map.defaultTileAltitude = 256;
            SpatialBlockProjection.projectBaseFootprint(f.map, b, after);
            Assert.assertEquals(before[1] - 128, after[1], 0);
            Assert.assertEquals(128, b.altitude, 0);
        }
    }

    @Test
    public void nonzeroResizeAndMoveUsePlaneInverseInBothProjections() {
        for (TiledProjection projection : new TiledProjection[]{TiledProjection.ISO, TiledProjection.ORTHO}) {
            for (float delta : new float[]{0f, 32f, -32f}) {
                try (Fixture f = new Fixture(128f, projection)) {
                    SpatialBlockData original = f.create(27, 18, 27, 19);
                    SpatialBlockData resized = original.copy();
                    resized.altitude = 128f + delta;
                    float[] point = new float[2];
                    Vector2 grid = new Vector2();
                    f.map.projectSpatialPoint(27.5f, 19f, resized.altitude, point, 0);
                    SpatialBlockProjection.footprintWorldToTileLocal(f.map, point[0], point[1], resized.altitude, grid);
                    Assert.assertTrue(SpatialBlockInteractiveEditSupport.resize(resized, original,
                            SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MAX_Y, grid.x, grid.y));
                    f.history.execute(new EditSpatialBlockCommand(f.world, f.history.historyIds(),
                            f.selection, f.owner, original.id, original, resized));
                    SpatialBlockData current = f.blocks().blocks.first();
                    Assert.assertEquals(.5f, current.width, .001f);
                    Assert.assertEquals(1f, current.depth, .001f);
                    SpatialBlockData moved = current.copy();
                    f.map.projectSpatialPoint(current.x + .25f, current.y + .5f, current.altitude, point, 0);
                    SpatialBlockProjection.footprintWorldToTileLocal(f.map, point[0], point[1], current.altitude, grid);
                    Assert.assertTrue(SpatialBlockInteractiveEditSupport.move(moved, current,
                            grid.x - current.x, grid.y - current.y));
                    f.history.execute(new EditSpatialBlockCommand(f.world, f.history.historyIds(),
                            f.selection, f.owner, current.id, current, moved));
                    current = f.blocks().blocks.first();
                    Assert.assertEquals(27.25f, current.x, .001f);
                    Assert.assertEquals(18.5f, current.y, .001f);
                    Assert.assertEquals(128f + delta, current.altitude, 0f);
                    Assert.assertEquals(original.id, current.id);
                    Assert.assertEquals(2, current.linkedTileRefs.size);
                    assertProjectionAndManipulation(f.map, f.blocks(), current, delta);
                }
            }
        }
    }

    private static void assertProjectionAndManipulation(TiledMapLayerData map,
                                                         SpatialBlocksComponent blocks,
                                                         SpatialBlockData b, float displacement) {
        float[] base = new float[8], top = new float[8];
        SpatialBlockProjection.projectBaseFootprint(map, b, base);
        SpatialBlockProjection.projectTopFootprint(map, b, top);
        if (map.projection == TiledProjection.ISO && b.x == 27) {
            Assert.assertEquals(1280 + map.originX, base[0], .001f);
            Assert.assertEquals(2880 + map.originY + displacement, base[1], .001f);
        } else {
            Assert.assertEquals(map.tileToWorldY(b.x, b.y) + displacement, base[1], .001f);
        }
        Vector2 inverse = new Vector2();
        float[] gx = {b.x, b.x + b.width, b.x + b.width, b.x};
        float[] gy = {b.y, b.y, b.y + b.depth, b.y + b.depth};
        for (int i = 0; i < 4; i++) {
            Assert.assertEquals(base[i * 2 + 1] + b.height, top[i * 2 + 1], .001f);
            SpatialBlockProjection.footprintWorldToTileLocal(map, base[i * 2], base[i * 2 + 1], b.altitude, inverse);
            Assert.assertEquals(gx[i], inverse.x, .001f);
            Assert.assertEquals(gy[i], inverse.y, .001f);
        }
        // Resize at the existing maximum corner and stationary move must not jump.
        SpatialBlockData preview = b.copy();
        SpatialBlockProjection.footprintWorldToTileLocal(map, base[4], base[5], b.altitude, inverse);
        SpatialBlockInteractiveEditSupport.resize(preview, b,
                SpatialBlockInteractiveEditSupport.ResizeHandle.MAX_X_MAX_Y, inverse.x, inverse.y);
        Assert.assertEquals(b.width, preview.width, .001f);
        Assert.assertEquals(b.depth, preview.depth, .001f);
        Assert.assertFalse(SpatialBlockInteractiveEditSupport.move(preview, b, 0, 0));
        Assert.assertEquals(b.x, preview.x, 0);
        Assert.assertEquals(b.y, preview.y, 0);
        Assert.assertEquals(b.linkedTileRefs.size, preview.linkedTileRefs.size);
        SpatialCompiledLayerCache compiled = new SpatialCompiledLayerCache(); compiled.ensure(blocks);
        SpatialProjectedFaceCache faces = new SpatialProjectedFaceCache(); faces.ensure(compiled, map);
        Assert.assertTrue(faces.faceCount > 0);
        for (int face = 0; face < faces.faceCount; face++) {
            CompiledSpatialStructure structure = compiled.structure(0);
            CompiledSpatialStructure.FaceSet set = structure.actorOccluder();
            int index = faces.faceCompiledIndex[face];
            float[] point = new float[2];
            SpatialBlockProjection.projectStructurePoint(map, set.startX(index), set.startY(index),
                    structure.altitude(), point, 0);
            Assert.assertEquals(point[1], faces.slope[face] * point[0] + faces.intercept[face], .002f);
            Assert.assertEquals(b.altitude, faces.faceAltitude[face], 0);
        }
    }

    private static World world() {
        return new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
    }

    private static final class Fixture implements AutoCloseable {
        final World world = world();
        final HistoryManager history = new HistoryManager(16);
        final SpatialTileSelectionService tiles = new SpatialTileSelectionService();
        final SpatialBlockSelectionService selection = new SpatialBlockSelectionService();
        final int owner = world.create();
        final TiledMapLayerData map;
        Fixture(float altitude) { this(altitude, TiledProjection.ISO); }
        Fixture(float altitude, TiledProjection projection) {
            world.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
            TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(owner);
            tiled.mapWidthCells = 50; tiled.mapHeightCells = 50; tiled.tileWidth = 256; tiled.tileHeight = 128;
            tiled.projection = projection;
            tiled.chunkSize = 16;
            tiled.originX = 37; tiled.originY = -83;
            tiled.defaultTileAltitude = altitude; tiled.defaultTileHeight = 128;
            map = tiled.data = tiled.createMapData();
            map.setTile(27, 18, 151); map.setTile(27, 19, 139);
            for (int gy = 22; gy <= 24; gy++) map.setTile(36, gy, 1);
            for (int gx = 36; gx <= 38; gx++) map.setTile(gx, 22, 1);
            world.process();
        }
        SpatialBlocksComponent blocks() { return world.getMapper(SpatialBlocksComponent.class).get(owner); }
        SpatialBlockData create(int x0, int y0, int x1, int y1) {
            SpatialCellPicker.Result hit = new SpatialCellPicker.Result();
            Assert.assertTrue(SpatialCellPicker.pickForSpatialSelection(map, null,
                    map.tileToWorldX(x0, y0) + 128, map.tileToWorldY(x0, y0) + 64, hit));
            Assert.assertEquals(x0, hit.gx); Assert.assertEquals(y0, hit.gy);
            tiles.beginDrag(owner, hit.gx, hit.gy); tiles.updateDrag(x1, y1); tiles.finishDrag();
            Assert.assertTrue(SpatialWallCreationService.executeSelectedRectangle(world, history, selection, tiles));
            return blocks().blocks.peek();
        }
        public void close() { tiles.dispose(); selection.dispose(); world.dispose(); }
    }
}
