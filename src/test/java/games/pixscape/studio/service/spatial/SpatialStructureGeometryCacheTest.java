package games.pixscape.studio.service.spatial;

import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import org.junit.Assert;
import org.junit.Test;

public class SpatialStructureGeometryCacheTest {
    @Test
    public void unchangedStructuresAreNotRecompiledAndOnlyChangedStructureIsInvalidated() {
        SpatialBlocksComponent walls = component(
                wall(1, 2, 0f, 0f, 2f, 1f),
                wall(2, 5, 4f, 0f, 2f, 1f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();

        cache.synchronize(7, walls, map);
        Assert.assertEquals(2, cache.compilationCount());
        cache.synchronize(7, walls, map);
        Assert.assertEquals(2, cache.compilationCount());

        walls.blocks.get(0).x = 0.25f;
        walls.revision++;
        cache.synchronize(7, walls, map);

        Assert.assertEquals(3, cache.compilationCount());
        Assert.assertEquals(2, cache.structureCount());
    }

    @Test
    public void mergeAndSplitReplaceEveryAffectedCacheEntry() {
        SpatialBlockData first = wall(1, 2, 0f, 0f, 3f, 1f);
        SpatialBlockData second = wall(2, 5, 2f, 0f, 3f, 1f);
        SpatialBlocksComponent walls = component(first, second);
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        cache.synchronize(7, walls, map);

        second.structureId = 2;
        walls.revision++;
        cache.synchronize(7, walls, map);
        CompiledSpatialStructure merged = cache.structure(0);

        Assert.assertEquals(1, cache.structureCount());
        Assert.assertEquals(4, merged.segmentCount());
        int afterMerge = cache.compilationCount();

        second.structureId = 6;
        second.x = 4f;
        walls.revision++;
        cache.synchronize(7, walls, map);

        Assert.assertEquals(2, cache.structureCount());
        Assert.assertEquals(afterMerge + 2, cache.compilationCount());
    }

    @Test
    public void deletingContributingWallRebuildsRemainingEnvelope() {
        SpatialBlocksComponent walls = component(
                wall(1, 3, 0f, 0f, 3f, 1f),
                wall(2, 3, 2f, 0f, 3f, 1f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        cache.synchronize(9, walls, map);
        Assert.assertEquals(12f, perimeter(cache.structure(0)), 0f);
        int beforeDelete = cache.compilationCount();

        walls.blocks.removeIndex(1);
        walls.revision++;
        cache.synchronize(9, walls, map);

        Assert.assertEquals(beforeDelete + 1, cache.compilationCount());
        Assert.assertEquals(8f, perimeter(cache.structure(0)), 0f);
    }

    @Test
    public void restoringAuthoredSnapshotReconstructsIdenticalCompiledGeometry() {
        SpatialBlockData first = wall(1, 4, 0f, 0f, 4f, 1f);
        SpatialBlockData second = wall(2, 4, 0f, 0f, 1f, 4f);
        SpatialBlocksComponent walls = component(first, second);
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        cache.synchronize(11, walls, map);
        String before = signature(cache.structure(0));

        second.x = 2f;
        second.structureId = 5;
        walls.revision++;
        cache.synchronize(11, walls, map);
        second.x = 0f;
        second.structureId = 4;
        walls.revision++;
        cache.synchronize(11, walls, map);

        Assert.assertEquals(before, signature(cache.structure(0)));
    }

    @Test
    public void distinctValidSourceWithReusedEntityIdAndRevisionReplacesGeometry() {
        SpatialBlocksComponent first = component(wall(1, 4, 0f, 0f, 4f, 1f));
        SpatialBlocksComponent second = component(wall(1, 4, 6f, 0f, 2f, 1f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();

        Assert.assertTrue(cache.synchronize(13, first, map).published());
        String firstGeometry = signature(cache.structure(0));
        Assert.assertTrue(cache.synchronize(13, second, map).published());

        Assert.assertEquals(1, cache.structureCount());
        Assert.assertNotEquals(firstGeometry, signature(cache.structure(0)));
    }

    @Test
    public void invalidCurrentSourceClearsPreviouslyPublishedGeometryUntilCorrected() {
        SpatialBlocksComponent walls = component(
                wall(1, 4, 0f, 0f, 4f, 1f),
                wall(2, 4, 3f, 0f, 1f, 4f));
        TiledMapLayerData map = map();
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        Assert.assertTrue(cache.synchronize(13, walls, map).success());
        int revision = cache.publishedRevision();
        int compilations = cache.compilationCount();

        walls.blocks.get(1).height = 9f;
        walls.revision++;
        SpatialStructureGeometryCache.SynchronizeResult failed = cache.synchronize(13, walls, map);

        Assert.assertFalse(failed.success());
        Assert.assertFalse(failed.published());
        Assert.assertNotNull(failed.diagnostic());
        Assert.assertEquals(revision + 1, cache.publishedRevision());
        Assert.assertEquals(compilations, cache.compilationCount());
        Assert.assertEquals(0, cache.structureCount());
        Assert.assertEquals(1, cache.failureCount());
        Assert.assertFalse(cache.synchronize(13, walls, map).success());
        Assert.assertEquals(0, cache.structureCount());
        Assert.assertEquals(1, cache.failureCount());

        walls.blocks.get(1).height = 6f;
        walls.revision++;
        Assert.assertTrue(cache.synchronize(13, walls, map).success());
        Assert.assertEquals(1, cache.structureCount());
    }

    @Test
    public void failedReplacementWithReusedEntityIdCannotExposePreviousSource() {
        SpatialBlocksComponent valid = component(wall(1, 4, 0f, 0f, 4f, 1f));
        SpatialBlocksComponent invalid = component(wall(2, 5, 6f, 0f, 2f, 1f));
        invalid.blocks.get(0).height = 0f;
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        Assert.assertTrue(cache.synchronize(13, valid, map).published());

        SpatialStructureGeometryCache.SynchronizeResult failure = cache.synchronize(13, invalid, map);

        Assert.assertFalse(failure.success());
        Assert.assertEquals(0, cache.structureCount());
        Assert.assertSame(failure, cache.synchronize(13, invalid, map));
        Assert.assertEquals(1, cache.failureCount());
    }

    @Test
    public void sceneSwitchAndReturnNeverReuseAnotherScenesGeometry() {
        SpatialBlocksComponent sceneA = component(wall(1, 4, 0f, 0f, 4f, 1f));
        SpatialBlocksComponent sceneB = component(wall(2, 8, 7f, 0f, 2f, 1f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData mapA = map();
        TiledMapLayerData mapB = map();
        cache.synchronize(3, sceneA, mapA);
        String geometryA = signature(cache.structure(0));

        cache.synchronize(3, sceneB, mapB);
        String geometryB = signature(cache.structure(0));
        cache.synchronize(3, sceneA, mapA);

        Assert.assertNotEquals(geometryA, geometryB);
        Assert.assertEquals(geometryA, signature(cache.structure(0)));
    }

    @Test
    public void mapContentFailureInvalidatesPublishedGeometryAndRetriesAfterCorrection() {
        SpatialBlocksComponent walls = component(wall(1, 4, 0f, 0f, 2f, 1f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        Assert.assertTrue(cache.synchronize(13, walls, map).published());
        map.setTile(0, 0, 0);

        Assert.assertFalse(cache.synchronize(13, walls, map).success());
        Assert.assertEquals(0, cache.structureCount());

        map.setTile(0, 0, 1);
        Assert.assertTrue(cache.synchronize(13, walls, map).published());
        Assert.assertEquals(1, cache.structureCount());
    }

    @Test
    public void emptyValidSourcePublishesAnIntentionallyEmptySnapshot() {
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();

        SpatialStructureGeometryCache.SynchronizeResult result = cache.synchronize(13, component(), map());

        Assert.assertTrue(result.success());
        Assert.assertTrue(result.published());
        Assert.assertEquals(0, cache.structureCount());
        Assert.assertNull(cache.lastDiagnostic());
    }

    @Test
    public void previewCameraAndViewportReadsDoNotScanOrCompileWithoutCommittedRevision() {
        SpatialBlocksComponent walls = component(
                wall(1, 4, 0f, 0f, 4f, 1f),
                wall(2, 4, 3f, 0f, 1f, 4f));
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        Assert.assertTrue(cache.synchronize(13, walls, map).published());
        int compilations = cache.compilationCount();
        int revision = cache.publishedRevision();

        for (int frame = 0; frame < 20; frame++) {
            Assert.assertFalse(cache.synchronize(13, walls, map).published());
        }

        Assert.assertEquals(compilations, cache.compilationCount());
        Assert.assertEquals(revision, cache.publishedRevision());
    }

    @Test
    public void onlyCompilerRelevantFeatureChangesRecompileGeometry() {
        SpatialBlockData first = wall(1, 4, 0f, 0f, 4f, 1f);
        SpatialBlockData second = wall(2, 4, 3f, 0f, 1f, 4f);
        SpatialBlocksComponent walls = component(first, second);
        SpatialStructureGeometryCache cache = new SpatialStructureGeometryCache();
        TiledMapLayerData map = map();
        cache.synchronize(13, walls, map);
        int initial = cache.compilationCount();

        first.physicsCollision = !first.physicsCollision;
        walls.revision++;
        Assert.assertFalse(cache.synchronize(13, walls, map).published());
        Assert.assertEquals(initial, cache.compilationCount());
        Assert.assertFalse(cache.synchronize(13, walls, map).published());
        Assert.assertEquals(initial, cache.compilationCount());

        first.actorOccluder = !first.actorOccluder;
        walls.revision++;
        Assert.assertTrue(cache.synchronize(13, walls, map).published());
        Assert.assertEquals(initial + 1, cache.compilationCount());
    }

    private static SpatialBlocksComponent component(SpatialBlockData... walls) {
        SpatialBlocksComponent component = new SpatialBlocksComponent();
        for (SpatialBlockData wall : walls) component.blocks.add(wall);
        return component;
    }

    private static SpatialBlockData wall(int id, int structureId, float x, float y, float width, float depth) {
        SpatialBlockData wall = new SpatialBlockData();
        wall.id = id;
        wall.structureId = structureId;
        wall.x = x;
        wall.y = y;
        wall.width = width;
        wall.depth = depth;
        wall.altitude = 1f;
        wall.height = 6f;
        wall.beginAuthoredLinkedTileRefs();
        int minX = Math.max(0, (int) Math.floor(x) - 2);
        int maxX = (int) Math.ceil(x + width) + 2;
        int minY = Math.max(0, (int) Math.floor(y) - 2);
        int maxY = (int) Math.ceil(y + depth) + 2;
        for (int gy = minY; gy < maxY; gy++) {
            for (int gx = minX; gx < maxX; gx++) wall.addLinkedTileRef(gx, gy, 1);
        }
        return wall;
    }

    private static TiledMapLayerData map() {
        return SpatialWallAuthoringValidatorTest.map(20, 20);
    }

    private static float perimeter(CompiledSpatialStructure structure) {
        float result = 0f;
        for (int i = 0; i < structure.segmentCount(); i++) {
            result += Math.abs(structure.endX(i) - structure.startX(i));
            result += Math.abs(structure.endY(i) - structure.startY(i));
        }
        return result;
    }

    private static String signature(CompiledSpatialStructure structure) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < structure.segmentCount(); i++) {
            out.append(structure.startX(i)).append(',').append(structure.startY(i)).append('-')
                    .append(structure.endX(i)).append(',').append(structure.endY(i)).append('/')
                    .append(structure.normalX(i)).append(',').append(structure.normalY(i)).append(';');
        }
        return out.toString();
    }
}
