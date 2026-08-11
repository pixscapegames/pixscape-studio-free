package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationService;
import games.pixscape.studio.ui.main.WorldCanvas;
import org.junit.Assert;
import org.junit.Test;
import sun.misc.Unsafe;

import java.lang.reflect.Field;

public class ClipboardServiceFlowTest {

    @Test
    public void copySelection_keepsSelectionAndStoresPasteableSnapshot() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 10f, 20f, 0);
        h.selection.selectOnly(source);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.hasContent());
        Assert.assertEquals(source, h.selection.getFirstSelectedEntityId());
        Assert.assertTrue(h.clipboard.paste());

        int pasted = h.selection.getSelectionSnapshot().first();
        TransformComponent sourceTr = h.world.getMapper(TransformComponent.class).get(source);
        TransformComponent pastedTr = h.world.getMapper(TransformComponent.class).get(pasted);
        Assert.assertEquals(sourceTr.x + 16f, pastedTr.x, 0f);
        Assert.assertEquals(sourceTr.y - 16f, pastedTr.y, 0f);

        h.history.undo();
        h.world.process();
        Assert.assertFalse(h.world.getEntityManager().isActive(pasted));
    }

    @Test
    public void cutSelection_copiesThenDeletesAndUndoRestoresEntity() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 4f, 5f, 0);
        h.selection.selectOnly(source);

        Assert.assertTrue(h.clipboard.cutSelection());
        h.world.process();
        Assert.assertTrue(h.clipboard.hasContent());
        Assert.assertFalse(h.world.getEntityManager().isActive(source));
        Assert.assertEquals(-1, h.selection.getFirstSelectedEntityId());

        h.history.undo();
        h.world.process();
        Assert.assertEquals(1, countEntitiesAt(h.world, 4f, 5f));
    }

    @Test
    public void copyPasteAnimationPreservesIndependentAnimationAssetIds() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 7f, 9f, 0);
        AnimationComponent animation = h.world.getMapper(AnimationComponent.class).create(source);
        animation.animationAssetIds.add(17);
        animation.animationAssetIds.add(31);
        animation.currentClip = "run";
        h.selection.selectOnly(source);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());
        AnimationComponent pasted = h.world.getMapper(AnimationComponent.class)
                .get(h.selection.getSelectionSnapshot().first());
        Assert.assertEquals("run", pasted.currentClip);
        Assert.assertArrayEquals(new int[]{17, 31}, pasted.animationAssetIds.toArray());
        Assert.assertNotSame(animation.animationAssetIds, pasted.animationAssetIds);
    }

    @Test
    public void clipboardAndEntityGraphShareOneIdentityRegistry() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 1f, 2f, 0);
        h.identities.ensureStableId(source);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());
        int clipboardEntity = h.selection.getFirstSelectedEntityId();
        int clipboardStableId = h.world.getMapper(PixscapeIdentityComponent.class)
                .get(clipboardEntity).stableId;

        EntityGraph graph = new EntityGraphCaptureService(h.world)
                .capture(new IntArray(new int[]{source}));
        EntityGraphInstantiationResult directResult =
                new EntityGraphInstantiationService(
                        h.world, h.history, h.identities, h.physicsService)
                        .instantiate(graph, 0, 4f, 4f, "Direct graph path");
        int directEntity = directResult.createdIds().first();
        int directStableId = h.world.getMapper(PixscapeIdentityComponent.class)
                .get(directEntity).stableId;
        h.world.process();

        Assert.assertNotEquals(clipboardStableId, directStableId);
        Assert.assertEquals(clipboardEntity, h.identities.findByStableId(clipboardStableId));
        Assert.assertEquals(directEntity, h.identities.findByStableId(directStableId));
    }

    @Test
    public void pasteResolvesValidClassicLayerAndStripsPhysics() throws Exception {
        Harness h = new Harness();
        int source = physicalEntity(h.world, 0, false);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());

        Assert.assertTrue(h.clipboard.paste());
        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertEquals(0, h.world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertFalse(h.world.getMapper(PhysicsShapesComponent.class).has(pasted));
    }

    @Test
    public void pasteResolvesValidPhysicsLayerAndKeepsPhysics() throws Exception {
        Harness h = new Harness();
        int physicsLayer = h.addLayer(1, LayerComponent.TYPE_PHYSICS, false);
        int source = physicalEntity(h.world, 0, false);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.selection.setActivelayerId(physicsLayer);

        Assert.assertTrue(h.clipboard.paste());
        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertEquals(1, h.world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertTrue(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertEquals(1, h.world.getMapper(PhysicsShapesComponent.class).get(pasted).shapes.size);
    }

    @Test
    public void pasteResolvesValidSpatialPhysicsLayerAndKeepsSpatialState() throws Exception {
        Harness h = new Harness();
        int spatialLayer = h.addLayer(1, LayerComponent.TYPE_PHYSICS, true);
        int source = physicalEntity(h.world, 0, true);
        SpatialHeightComponent height = h.world.getMapper(SpatialHeightComponent.class).create(source);
        height.altitude = 4f;
        height.height = 2f;
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.selection.setActivelayerId(spatialLayer);

        Assert.assertTrue(h.clipboard.paste());
        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertEquals(1, h.world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertTrue(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertTrue(h.world.getMapper(SpatialHeightComponent.class).has(pasted));
        Assert.assertTrue(hasFootprint(h.world.getMapper(PhysicsShapesComponent.class).get(pasted)));
    }

    @Test
    public void pasteRejectsActiveIndexWithoutRegisteredLayerWithoutAdvancingState() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 5f, 6f, 0);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        int rogueLayer = h.world.create();
        LayerComponent rogue = h.world.getMapper(LayerComponent.class).create(rogueLayer);
        rogue.layerIndex = 99;
        rogue.type = LayerComponent.TYPE_CLASSIC;
        h.world.process();
        h.selection.setActivelayerId(rogueLayer);

        int entitiesBefore = countAll(h.world);
        int nextStableIdBefore = h.sceneMeta.nextEntityStableId;
        Assert.assertFalse(h.clipboard.paste());
        Assert.assertEquals(entitiesBefore, countAll(h.world));
        Assert.assertEquals(nextStableIdBefore, h.sceneMeta.nextEntityStableId);
        Assert.assertFalse(h.history.canUndo());
        Assert.assertEquals(source, h.selection.getFirstSelectedEntityId());

        h.selection.setActivelayerId(h.classicLayer);
        Assert.assertTrue(h.clipboard.paste());
        TransformComponent pasted = h.world.getMapper(TransformComponent.class)
                .get(h.selection.getFirstSelectedEntityId());
        Assert.assertEquals(21f, pasted.x, 0f);
        Assert.assertEquals(-10f, pasted.y, 0f);
    }

    @Test
    public void pasteRejectsLayerDeletedBetweenCopyAndPaste() throws Exception {
        Harness h = new Harness();
        int target = h.addLayer(1, LayerComponent.TYPE_CLASSIC, false);
        int source = createEntity(h.world, 1f, 1f, 0);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.selection.setActivelayerId(target);
        h.layers.removeLayerCascade(1);
        h.world.process();

        int entitiesBefore = countAll(h.world);
        int nextStableIdBefore = h.sceneMeta.nextEntityStableId;
        Assert.assertFalse(h.clipboard.paste());
        Assert.assertEquals(entitiesBefore, countAll(h.world));
        Assert.assertEquals(nextStableIdBefore, h.sceneMeta.nextEntityStableId);
        Assert.assertFalse(h.history.canUndo());
        Assert.assertEquals(source, h.selection.getFirstSelectedEntityId());
    }

    @Test
    public void pasteRejectsUnknownLayerType() throws Exception {
        Harness h = new Harness();
        int unknownLayer = h.addLayer(1, 999, false);
        int source = createEntity(h.world, 2f, 3f, 0);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.selection.setActivelayerId(unknownLayer);

        int entitiesBefore = countAll(h.world);
        int nextStableIdBefore = h.sceneMeta.nextEntityStableId;
        Assert.assertFalse(h.clipboard.paste());
        Assert.assertEquals(entitiesBefore, countAll(h.world));
        Assert.assertEquals(nextStableIdBefore, h.sceneMeta.nextEntityStableId);
        Assert.assertFalse(h.history.canUndo());
        Assert.assertEquals(source, h.selection.getFirstSelectedEntityId());
    }

    private static int createEntity(World world, float x, float y, int layerIndex) {
        int entity = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = x;
        transform.y = y;
        EntityIndexComponent index = world.getMapper(EntityIndexComponent.class).create(entity);
        index.layerIndex = layerIndex;
        index.zIndex = 1;
        return entity;
    }

    private static int physicalEntity(World world, int layerIndex, boolean footprint) {
        int entity = createEntity(world, 0f, 0f, layerIndex);
        world.getMapper(PhysicsBodyComponent.class).create(entity);
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(entity);
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = 100 + entity;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = footprint
                ? PhysicsGeometryData.SHAPE_CIRCLE
                : PhysicsGeometryData.SHAPE_BOX;
        shape.spatialFootprint = footprint;
        shapes.shapes.add(shape);
        return entity;
    }

    private static boolean hasFootprint(PhysicsShapesComponent shapes) {
        for (PhysicsShapeData shape : shapes.shapes) {
            if (shape != null && shape.spatialFootprint) return true;
        }
        return false;
    }

    private static int countAll(World world) {
        return world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size();
    }

    private static int countEntitiesAt(World world, float x, float y) {
        com.artemis.utils.IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(TransformComponent.class, EntityIndexComponent.class))
                .getEntities();
        int count = 0;
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            TransformComponent transform = world.getMapper(TransformComponent.class)
                    .getSafe(data[i], null);
            if (transform != null && transform.x == x && transform.y == y) count++;
        }
        return count;
    }

    private static final class Harness {
        final World world = new World(new WorldConfiguration());
        final HistoryManager history = new HistoryManager(32);
        final SceneMeta sceneMeta;
        final IdentityRegistry identities = new IdentityRegistry();
        final PhysicsService physicsService;
        final LayerService layers;
        final SelectionService selection;
        final WorldCanvas canvas;
        final ClipboardService clipboard;
        final int classicLayer;

        Harness() throws Exception {
            ProjectConfig config = new ProjectConfig();
            config.createSceneMeta("Main");
            ProjectConfig.setInstance(config);
            sceneMeta = config.getCurrentSceneMeta();
            identities.bind(world, sceneMeta);
            identities.rebuild();
            physicsService = new PhysicsService(world, null, sceneMeta);
            layers = new LayerService(world, null, history.historyIds(), identities);
            classicLayer = addLayer(0, LayerComponent.TYPE_CLASSIC, false);
            selection = new SelectionService(world, layers);
            selection.setActivelayerId(classicLayer);
            canvas = newTestCanvas(
                    world, selection, history, layers, physicsService);
            clipboard = new ClipboardService(canvas, identities);
        }

        int addLayer(int index, int type, boolean spatialEnabled) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = index;
            layer.type = type;
            layer.spatialEnabled = spatialEnabled;
            world.getMapper(LayerMetaComponent.class).create(entity);
            world.process();
            return entity;
        }
    }

    private static WorldCanvas newTestCanvas(
            World world,
            SelectionService selection,
            HistoryManager history,
            LayerService layers,
            PhysicsService physicsService) throws Exception {
        Unsafe unsafe = getUnsafe();
        WorldCanvas canvas = (WorldCanvas) unsafe.allocateInstance(WorldCanvas.class);
        setFieldUnsafe(unsafe, canvas, "world", world);
        setFieldUnsafe(unsafe, canvas, "selectionService", selection);
        setFieldUnsafe(unsafe, canvas, "historyManager", history);
        setFieldUnsafe(unsafe, canvas, "layerService", layers);
        setFieldUnsafe(unsafe, canvas, "physicsService", physicsService);
        return canvas;
    }

    private static Unsafe getUnsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setFieldUnsafe(
            Unsafe unsafe, Object target, String fieldName, Object value) throws Exception {
        Field field = WorldCanvas.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        unsafe.putObject(target, unsafe.objectFieldOffset(field), value);
    }
}
