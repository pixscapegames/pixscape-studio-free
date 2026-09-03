package games.pixscape.studio.service.entitygraph;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.DimensionsComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import org.junit.Assert;
import org.junit.Before;
import org.junit.Test;

public class ClipboardLayerCompatibilityTest {
    private World world;
    private HistoryManager history;
    private EntityGraphInstantiationService service;
    private SceneMeta sceneMeta;
    private int nextSourceShapeId;

    @Before
    public void setUp() {
        ProjectConfig config = new ProjectConfig();
        config.createSceneMeta("Main");
        ProjectConfig.setInstance(config);
        world = new World(new WorldConfiguration());
        history = new HistoryManager(32);
        nextSourceShapeId = 100;
        sceneMeta = new SceneMeta();
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, sceneMeta);
        identities.rebuild();
        service = new EntityGraphInstantiationService(
                world, history, identities, new PhysicsService(world, null, sceneMeta),
                () -> true);
    }

    @Test
    public void spatialActorToOrdinaryLayerKeepsOrdinaryPhysicsWithoutSpatialState() {
        int source = spatialActor(true);
        EntityGraphInstantiationResult result = paste(
                capture(source), 7, EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL);
        int pasted = result.createdIds().first();

        Assert.assertEquals(7, world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(pasted));
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(pasted);
        Assert.assertEquals(1, shapes.shapes.size);
        Assert.assertFalse(shapes.shapes.first().spatialFootprint);
    }

    @Test
    public void soleSpatialFootprintToOrdinaryLayerRemovesOrphanedBody() {
        int source = spatialActor(false);
        int pasted = paste(capture(source), 7,
                EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL)
                .createdIds().first();

        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(pasted));
        Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertFalse(world.getMapper(PhysicsShapesComponent.class).has(pasted));
    }

    @Test
    public void spatialActorToSpatialLayerPreservesHeightAndFootprint() {
        int source = spatialActor(true);
        EntityGraphInstantiationResult result = paste(
                capture(source), 8, EntityGraphInstantiationService.ClipboardTargetLayer.SPATIAL_ENABLED);
        int pasted = result.createdIds().first();

        SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).get(pasted);
        Assert.assertEquals(3.5f, height.altitude, 0f);
        Assert.assertEquals(2.25f, height.height, 0f);
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(pasted));
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).get(pasted);
        Assert.assertEquals(2, shapes.shapes.size);
        Assert.assertTrue(hasFootprint(shapes));
    }

    @Test
    public void physicsActorToOrdinaryLayerKeepsPhysics() {
        int source = physicalActor(false, true);
        EntityGraphInstantiationResult result = paste(
                capture(source), 2, EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL);
        int pasted = result.createdIds().first();

        Assert.assertTrue(world.getMapper(TransformComponent.class).has(pasted));
        Assert.assertTrue(world.getMapper(DimensionsComponent.class).has(pasted));
        Assert.assertEquals(2, world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertTrue(world.getMapper(PhysicsShapesComponent.class).has(pasted));
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(pasted));
    }

    @Test
    public void jointedBodiesToOrdinaryLayerPreservePhysicsGraph() {
        int a = physicalActor(false, true);
        int b = physicalActor(false, true);
        int joint = distanceJoint(a, b);
        EntityGraph graph = capture(a, b);

        int nextStableIdBefore = sceneMeta.nextEntityStableId;
        int nextShapeIdBefore = sceneMeta.nextPhysicsShapeId;
        EntityGraphInstantiationResult result = paste(
                graph, 3, EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL);
        world.process();

        Assert.assertEquals(3, result.createdIds().size);
        Assert.assertTrue(result.sourceToCreated().containsKey(a));
        Assert.assertTrue(result.sourceToCreated().containsKey(b));
        Assert.assertTrue(result.sourceToCreated().containsKey(joint));
        Assert.assertEquals(2, count(PhysicsJointComponent.class));
        Assert.assertEquals(nextStableIdBefore + 3, sceneMeta.nextEntityStableId);
        Assert.assertEquals(nextShapeIdBefore + 2, sceneMeta.nextPhysicsShapeId);
        int pastedA = result.sourceToCreated().get(a, -1);
        int pastedB = result.sourceToCreated().get(b, -1);
        int stableA = world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                .get(pastedA).stableId;
        int stableB = world.getMapper(games.pixscape.runtime.component.PixscapeIdentityComponent.class)
                .get(pastedB).stableId;
        Assert.assertTrue(stableA >= nextStableIdBefore);
        Assert.assertTrue(stableB >= nextStableIdBefore);
        Assert.assertNotEquals(stableA, stableB);
        for (int i = 0; i < result.createdIds().size; i++) {
            int pasted = result.createdIds().get(i);
            Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(pasted)
                    || world.getMapper(PhysicsJointComponent.class).has(pasted));
        }

        history.undo();
        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(pastedA));
        Assert.assertFalse(world.getEntityManager().isActive(pastedB));

        history.redo();
        world.process();
        int restoredA = result.sourceToCreated().get(a, -1);
        int restoredB = result.sourceToCreated().get(b, -1);
        Assert.assertTrue(world.getEntityManager().isActive(restoredA));
        Assert.assertTrue(world.getEntityManager().isActive(restoredB));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(restoredA));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(restoredB));
        Assert.assertEquals(2, count(PhysicsJointComponent.class));
    }

    @Test
    public void ordinaryPhysicsToOrdinaryLayerIsPreserved() {
        int source = physicalActor(false, true);
        PhysicsShapeData before = world.getMapper(PhysicsShapesComponent.class)
                .get(source).shapes.first().copy();

        int pasted = paste(capture(source), 4,
                EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL)
                .createdIds().first();

        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(pasted));
        PhysicsShapeData after = world.getMapper(PhysicsShapesComponent.class)
                .get(pasted).shapes.first();
        Assert.assertFalse(after.spatialFootprint);
        Assert.assertEquals(before.geometry.shapeType, after.geometry.shapeType);
        Assert.assertEquals(before.density, after.density, 0f);
        Assert.assertNotEquals(before.physicsShapeId, after.physicsShapeId);
    }

    @Test
    public void incompatiblePasteDoesNotPolluteFollowingCompatiblePaste() {
        int source = spatialActor(true);
        EntityGraph clipboardGraph = capture(source);

        int classicPaste = paste(clipboardGraph, 1,
                EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL)
                .createdIds().first();
        int spatialPaste = paste(clipboardGraph, 9,
                EntityGraphInstantiationService.ClipboardTargetLayer.SPATIAL_ENABLED)
                .createdIds().first();

        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(classicPaste));
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(classicPaste));
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(spatialPaste));
        Assert.assertTrue(world.getMapper(SpatialHeightComponent.class).has(spatialPaste));
        Assert.assertTrue(hasFootprint(
                world.getMapper(PhysicsShapesComponent.class).get(spatialPaste)));
        Assert.assertTrue(clipboardGraph.entries().get(0).initializer()
                .toSnapshotData(source).hasSpatialHeight);
        Assert.assertEquals(2, clipboardGraph.entries().get(0).initializer()
                .toSnapshotData(source).shapes.size);
    }

    @Test
    public void normalizedPasteUndoRedoRemainsOneAtomicHistoryOperation() {
        int source = spatialActor(true);
        int pasted = paste(capture(source), 5,
                EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL)
                .createdIds().first();
        Assert.assertTrue(history.canUndo());
        Assert.assertTrue(world.getEntityManager().isActive(pasted));

        history.undo();
        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(pasted));
        Assert.assertFalse(history.canUndo());
        Assert.assertTrue(history.canRedo());

        history.redo();
        int restored = latestEntityWithLayer(5);
        Assert.assertTrue(restored >= 0);
        Assert.assertTrue(world.getMapper(PhysicsBodyComponent.class).has(restored));
        Assert.assertFalse(world.getMapper(SpatialHeightComponent.class).has(restored));
        Assert.assertFalse(hasFootprint(
                world.getMapper(PhysicsShapesComponent.class).get(restored)));
    }

    @Test
    public void gameObjectHierarchyWithNestedSpatialActorIsRejectedBeforeMutationOnOrdinaryLayer() {
        sceneMeta.nextEntityStableId = 1_000;
        int root = gameObject(100, -1);
        int nested = gameObject(200, 100);
        world.getMapper(SpatialHeightComponent.class).create(nested).height = 2f;
        world.process();
        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureGameObjectClipboard(new IntArray(new int[]{root}));
        int activeBefore = activeEntityCount();
        int nextStableBefore = sceneMeta.nextEntityStableId;

        Assert.assertFalse(service.isClipboardInstantiationAllowed(
                graph, EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL));
        EntityGraphInstantiationResult rejected = paste(
                graph, 6, EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL);

        Assert.assertEquals(0, rejected.createdIds().size);
        Assert.assertEquals(activeBefore, activeEntityCount());
        Assert.assertEquals(nextStableBefore, sceneMeta.nextEntityStableId);
        Assert.assertFalse(history.canUndo());

        EntityGraphInstantiationResult accepted = paste(
                graph, 6, EntityGraphInstantiationService.ClipboardTargetLayer.SPATIAL_ENABLED);
        Assert.assertEquals(2, accepted.createdIds().size);
        int pastedNested = accepted.sourceToCreated().get(2, -1);
        Assert.assertTrue(world.getMapper(SpatialHeightComponent.class).has(pastedNested));
    }

    @Test
    public void gameObjectHierarchyWithAutonomousFootprintIsRejectedOnOrdinaryLayer() {
        sceneMeta.nextEntityStableId = 1_000;
        int root = gameObject(100, -1);
        int member = gameObject(200, 100);
        PhysicsShapeData footprint = shape(true);
        world.getMapper(PhysicsShapesComponent.class).create(member).shapes.add(footprint);
        world.process();
        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureGameObjectClipboard(new IntArray(new int[]{root}));

        Assert.assertFalse(service.isClipboardInstantiationAllowed(
                graph, EntityGraphInstantiationService.ClipboardTargetLayer.NON_SPATIAL));
    }

    private EntityGraph capture(int... entities) {
        return new EntityGraphCaptureService(world).capture(new IntArray(entities));
    }

    private EntityGraphInstantiationResult paste(
            EntityGraph graph,
            int layerIndex,
            EntityGraphInstantiationService.ClipboardTargetLayer target) {
        return service.instantiateForClipboard(graph, layerIndex, 0f, 0f, "Paste", target);
    }

    private int spatialActor(boolean ordinaryFixture) {
        int entity = physicalActor(true, ordinaryFixture);
        SpatialHeightComponent height = world.getMapper(SpatialHeightComponent.class).create(entity);
        height.altitude = 3.5f;
        height.height = 2.25f;
        return entity;
    }

    private int gameObject(int stableId, int parentStableId) {
        int entity = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        world.getMapper(GameObjectComponent.class).create(entity);
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entity)
                    .parentStableId = parentStableId;
        }
        return entity;
    }

    private int physicalActor(boolean footprint, boolean ordinaryFixture) {
        int entity = world.create();
        world.getMapper(TransformComponent.class).create(entity);
        world.getMapper(DimensionsComponent.class).create(entity);
        world.getMapper(EntityIndexComponent.class).create(entity);
        world.getMapper(PhysicsBodyComponent.class).create(entity);
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entity);
        if (ordinaryFixture) shapes.shapes.add(shape(false));
        if (footprint) shapes.shapes.add(shape(true));
        return entity;
    }

    private PhysicsShapeData shape(boolean footprint) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = nextSourceShapeId++;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = footprint
                ? PhysicsGeometryData.SHAPE_CIRCLE
                : PhysicsGeometryData.SHAPE_BOX;
        shape.spatialFootprint = footprint;
        return shape;
    }

    private int distanceJoint(int a, int b) {
        int entity = world.create();
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(entity);
        joint.type = PhysicsJointComponent.TYPE_DISTANCE;
        joint.aEid = a;
        joint.bEid = b;
        world.getMapper(PhysicsDistanceJointComponent.class).create(entity);
        return entity;
    }

    private boolean hasFootprint(PhysicsShapesComponent shapes) {
        for (PhysicsShapeData shape : shapes.shapes) {
            if (shape.spatialFootprint) return true;
        }
        return false;
    }

    private int latestEntityWithLayer(int layerIndex) {
        int found = -1;
        com.artemis.utils.IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(EntityIndexComponent.class)).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            int entity = data[i];
            if (world.getEntityManager().isActive(entity)
                    && world.getMapper(EntityIndexComponent.class).get(entity).layerIndex == layerIndex) {
                found = entity;
            }
        }
        return found;
    }

    private int activeEntityCount() {
        return world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size();
    }

    private int count(Class<? extends com.artemis.Component> component) {
        return world.getAspectSubscriptionManager().get(Aspect.all(component)).getEntities().size();
    }
}
