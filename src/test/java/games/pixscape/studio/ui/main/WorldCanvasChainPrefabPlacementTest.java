package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.utils.GdxNativesLoader;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsRuntimeBodyComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.Box2dWorldService;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.system.Box2dSyncSystem;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import games.pixscape.studio.history.initializer.GenericEntitySnapshotData;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphEntry;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService;
import org.junit.Assert;
import org.junit.Test;

import java.util.ArrayList;
import java.util.List;

public class WorldCanvasChainPrefabPlacementTest {
    private static final float PPM = 100f;
    private static final float TARGET_X = 640f;
    private static final float TARGET_Y = 360f;
    private static final float SOURCE_CENTER_X = -56.76475f;
    private static final float SOURCE_CENTER_Y = 3720.0151f;

    private static final int[] BODY_SOURCE_IDS = {13, 10, 15, 12, 22, 14, 11};
    private static final float[] BODY_X = {
            -56.101448f, -56.49156f, -54.848732f, -57.021843f,
            -54.947586f, -57.5521f, -58.68077f
    };
    private static final float[] BODY_Y = {
            3683.9143f, 3724.8184f, 3642.977f, 3760.426f,
            3601.7554f, 3798.9368f, 3838.275f
    };

    @Test
    public void realChainOriginUsesRenderedBodiesInsteadOfJointTransforms() {
        World world = new World();
        try {
            Vector2 origin = new Vector2();
            WorldCanvas.computePrefabOrigin(chainGraph(world), origin);

            Assert.assertEquals(SOURCE_CENTER_X, origin.x, 0.0001f);
            Assert.assertEquals(SOURCE_CENTER_Y, origin.y, 0.0001f);
        } finally {
            world.dispose();
        }
    }

    @Test
    public void realChainDropLandsAtTargetAndPhysicsKeepsTranslatedBodies() {
        GdxNativesLoader.load();
        DirtyTrackerSystem dirty = new DirtyTrackerSystem(64);
        Box2dWorldService box2d =
                new Box2dWorldService(PPM, new Vector2(0f, -9.8f));
        Box2dSyncSystem sync = new Box2dSyncSystem(box2d);
        World world = new World(new WorldConfigurationBuilder()
                .with(dirty, sync)
                .build());
        try {
            SceneMeta meta = new SceneMeta();
            meta.physicsEnabled = true;
            meta.pixelsPerMeter = PPM;
            sync.setSceneMeta(meta);
            sync.setStepEnabled(false);

            HistoryManager history = new HistoryManager(32);
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(world, meta);
            identities.rebuild();
            int layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 0;
            world.getMapper(LayerMetaComponent.class).create(layer).name = "Physics";

            EntityGraph graph = chainGraph(world);
            Vector2 origin = new Vector2();
            WorldCanvas.computePrefabOrigin(graph, origin);
            float dx = TARGET_X - origin.x;
            float dy = TARGET_Y - origin.y;
            EntityGraphInstantiationResult result = new EntityGraphInstantiationService(
                    world,
                    history,
                    identities,
                    new PhysicsService(world, box2d, meta))
                    .instantiatePrefab(
                            graph, 0, dx, dy,
                            "Drop real chain geometry", 41, "chain");

            assertVisualPlacement(world, result, dx, dy);
            assertJointRemaps(world, result);
            Assert.assertFalse(world.getMapper(TransformComponent.class)
                    .has(result.sourceToCreated().get(20, -1)));
            Assert.assertFalse(world.getMapper(TransformComponent.class)
                    .has(result.sourceToCreated().get(23, -1)));

            world.process();

            Assert.assertEquals(7, box2d.world.getBodyCount());
            Assert.assertEquals(6, box2d.world.getJointCount());
            Assert.assertEquals(TARGET_X, visualCenterX(world, result), 0.001f);
            Assert.assertEquals(TARGET_Y, visualCenterY(world, result), 0.001f);
            for (int sourceId : BODY_SOURCE_IDS) {
                int created = result.sourceToCreated().get(sourceId, -1);
                TransformComponent transform = world.getMapper(TransformComponent.class)
                        .get(created);
                PhysicsRuntimeBodyComponent runtime = world.getMapper(
                        PhysicsRuntimeBodyComponent.class).get(created);
                Assert.assertNotNull(runtime);
                Assert.assertNotNull(runtime.body);
                Assert.assertEquals(transform.x / PPM,
                        runtime.body.getPosition().x, 0.0001f);
                Assert.assertEquals(transform.y / PPM,
                        runtime.body.getPosition().y, 0.0001f);
                Assert.assertEquals(transform.x,
                        runtime.body.getPosition().x * PPM, 0.001f);
                Assert.assertEquals(transform.y,
                        runtime.body.getPosition().y * PPM, 0.001f);
            }

            sync.setStepEnabled(true);
            world.setDelta(1f / 60f);
            world.process();
            Assert.assertEquals(TARGET_X, visualCenterX(world, result), 0.25f);
            Assert.assertEquals(TARGET_Y, visualCenterY(world, result), 0.5f);
        } finally {
            world.dispose();
            box2d.dispose();
        }
    }

    private static void assertVisualPlacement(
            World world,
            EntityGraphInstantiationResult result,
            float dx,
            float dy) {
        for (int i = 0; i < BODY_SOURCE_IDS.length; i++) {
            int created = result.sourceToCreated().get(BODY_SOURCE_IDS[i], -1);
            TransformComponent transform = world.getMapper(TransformComponent.class)
                    .get(created);
            Assert.assertEquals(BODY_X[i] + dx, transform.x, 0.001f);
            Assert.assertEquals(BODY_Y[i] + dy, transform.y, 0.001f);
            if (i > 0) {
                int previous = result.sourceToCreated().get(BODY_SOURCE_IDS[i - 1], -1);
                TransformComponent previousTransform = world.getMapper(
                        TransformComponent.class).get(previous);
                Assert.assertEquals(BODY_X[i] - BODY_X[i - 1],
                        transform.x - previousTransform.x, 0.001f);
                Assert.assertEquals(BODY_Y[i] - BODY_Y[i - 1],
                        transform.y - previousTransform.y, 0.001f);
            }
        }
        Assert.assertEquals(TARGET_X, visualCenterX(world, result), 0.001f);
        Assert.assertEquals(TARGET_Y, visualCenterY(world, result), 0.001f);
    }

    private static void assertJointRemaps(
            World world,
            EntityGraphInstantiationResult result) {
        int[][] expected = {
                {16, 11, 14}, {17, 12, 14}, {18, 10, 12},
                {19, 10, 13}, {20, 13, 15}, {23, 15, 22}
        };
        for (int[] row : expected) {
            int createdJoint = result.sourceToCreated().get(row[0], -1);
            PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class)
                    .get(createdJoint);
            Assert.assertEquals(PhysicsJointComponent.TYPE_REVOLUTE, joint.type);
            Assert.assertEquals(result.sourceToCreated().get(row[1], -1), joint.aEid);
            Assert.assertEquals(result.sourceToCreated().get(row[2], -1), joint.bEid);
        }
    }

    private static float visualCenterX(
            World world,
            EntityGraphInstantiationResult result) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int sourceId : BODY_SOURCE_IDS) {
            float value = world.getMapper(TransformComponent.class)
                    .get(result.sourceToCreated().get(sourceId, -1)).x;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return (min + max) * 0.5f;
    }

    private static float visualCenterY(
            World world,
            EntityGraphInstantiationResult result) {
        float min = Float.POSITIVE_INFINITY;
        float max = Float.NEGATIVE_INFINITY;
        for (int sourceId : BODY_SOURCE_IDS) {
            float value = world.getMapper(TransformComponent.class)
                    .get(result.sourceToCreated().get(sourceId, -1)).y;
            min = Math.min(min, value);
            max = Math.max(max, value);
        }
        return (min + max) * 0.5f;
    }

    private static EntityGraph chainGraph(World world) {
        List<EntityGraphEntry> entries = new ArrayList<>();
        float[] radii = {
                0.10096689f, 0.11502838f, 0.101235f, 0.10891243f,
                0.09815457f, 0.11446609f, 0.10742673f
        };
        for (int i = 0; i < BODY_SOURCE_IDS.length; i++) {
            entries.add(entry(world, bodySnapshot(
                    BODY_SOURCE_IDS[i], BODY_X[i], BODY_Y[i], radii[i], i + 1)));
        }
        entries.add(entry(world, jointSnapshot(16, 11, 14, true,
                -0.0070488737f, -0.19042969f, -0.018335572f, 0.20295165f)));
        entries.add(entry(world, jointSnapshot(17, 12, 14, true,
                -0.008559112f, 0.186333f, -0.0032565307f, -0.19877441f)));
        entries.add(entry(world, jointSnapshot(18, 10, 12, true,
                -0.018873444f, 0.16328613f, -0.013570633f, -0.19279052f)));
        entries.add(entry(world, jointSnapshot(19, 10, 13, true,
                -0.003559189f, -0.21560547f, -0.0074603274f, 0.19343506f)));
        entries.add(entry(world, jointSnapshot(20, 13, 15, false,
                0.010863419f, -0.23261474f, -0.0016637421f, 0.17675781f)));
        entries.add(entry(world, jointSnapshot(23, 15, 22, false,
                -0.00909462f, -0.21902099f, -0.008106079f, 0.1931958f)));
        return new EntityGraph(entries);
    }

    private static EntityGraphEntry entry(
            World world,
            GenericEntitySnapshotData snapshot) {
        return new EntityGraphEntry(
                snapshot.sourceEntityId,
                new GenericEntityInitializer(world).applySnapshotData(snapshot));
    }

    private static GenericEntitySnapshotData bodySnapshot(
            int sourceId,
            float x,
            float y,
            float radius,
            int physicsShapeId) {
        GenericEntitySnapshotData snapshot = new GenericEntitySnapshotData();
        snapshot.sourceEntityId = sourceId;
        snapshot.hasTransform = true;
        snapshot.x = x;
        snapshot.y = y;
        snapshot.scaleX = 1f;
        snapshot.scaleY = 1f;
        snapshot.originX = 10.5f;
        snapshot.originY = 24.5f;
        snapshot.hasEntityIndex = true;
        snapshot.layerIndex = 5;
        snapshot.zIndex = physicsShapeId;
        snapshot.hasMeta = true;
        snapshot.metaKind = "SPRITE";
        snapshot.hasIdentity = true;
        snapshot.identityName = "chainlink";
        snapshot.hasDimensions = true;
        snapshot.dimensionsWidth = 21f;
        snapshot.dimensionsHeight = 49f;
        snapshot.hasAssetRef = true;
        snapshot.assetRefAssetId = 1285;
        snapshot.hasPhysicsBody = true;
        snapshot.bodyType = PhysicsBodyComponent.DYNAMIC;
        snapshot.allowSleep = true;
        snapshot.awake = true;
        snapshot.gravityScale = 1f;
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = physicsShapeId;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        shape.geometry.radius = radius;
        shape.density = 1f;
        shape.friction = 0.2f;
        shape.categoryBits = 1;
        shape.maskBits = -1;
        shape.enabled = true;
        snapshot.shapes.add(shape);
        return snapshot;
    }

    private static GenericEntitySnapshotData jointSnapshot(
            int sourceId,
            int bodyA,
            int bodyB,
            boolean hasTransform,
            float anchorAx,
            float anchorAy,
            float anchorBx,
            float anchorBy) {
        GenericEntitySnapshotData snapshot = new GenericEntitySnapshotData();
        snapshot.sourceEntityId = sourceId;
        snapshot.hasTransform = hasTransform;
        snapshot.x = -0.95157623f;
        snapshot.y = 5.189453f;
        snapshot.hasEntityIndex = hasTransform;
        snapshot.layerIndex = 5;
        snapshot.hasIdentity = true;
        snapshot.identityName = "unnamed";
        snapshot.hasJoint = true;
        snapshot.jointType = PhysicsJointComponent.TYPE_REVOLUTE;
        snapshot.jointAEid = bodyA;
        snapshot.jointBEid = bodyB;
        snapshot.jointAnchorAx = anchorAx;
        snapshot.jointAnchorAy = anchorAy;
        snapshot.jointAnchorBx = anchorBx;
        snapshot.jointAnchorBy = anchorBy;
        snapshot.hasRevoluteJoint = true;
        return snapshot;
    }
}
