package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
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
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = identityRegistry(world);
        ClipboardService clipboard = new ClipboardService(
                newTestCanvas(world, selection, history), identities);

        int source = createEntity(world, 10f, 20f, 3);
        selection.selectOnly(source);

        Assert.assertTrue(clipboard.copySelection());
        Assert.assertTrue(clipboard.hasContent());
        Assert.assertEquals(source, selection.getFirstSelectedEntityId());

        Assert.assertTrue(clipboard.paste());
        IntArray selectedAfterPaste = selection.getSelectionSnapshot();
        Assert.assertEquals(1, selectedAfterPaste.size);

        int pasted = selectedAfterPaste.get(0);
        Assert.assertNotEquals(source, pasted);

        TransformComponent sourceTr = world.getMapper(TransformComponent.class).get(source);
        TransformComponent pastedTr = world.getMapper(TransformComponent.class).get(pasted);
        Assert.assertEquals(sourceTr.x + 16f, pastedTr.x, 0f);
        Assert.assertEquals(sourceTr.y - 16f, pastedTr.y, 0f);

        history.undo();
        world.process();
        Assert.assertFalse(world.getEntityManager().isActive(pasted));
    }

    @Test
    public void cutSelection_copiesThenDeletesAndUndoRestoresEntity() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = identityRegistry(world);
        ClipboardService clipboard = new ClipboardService(
                newTestCanvas(world, selection, history), identities);

        int source = createEntity(world, 4f, 5f, 2);
        selection.selectOnly(source);

        Assert.assertTrue(clipboard.cutSelection());
        world.process();

        Assert.assertTrue(clipboard.hasContent());
        Assert.assertFalse(world.getEntityManager().isActive(source));
        Assert.assertEquals(-1, selection.getFirstSelectedEntityId());

        history.undo();
        world.process();

        Assert.assertEquals(1, countEntitiesAt(world, 4f, 5f));
    }

    @Test
    public void cutPasteAnimationPreservesClipFlipX() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = identityRegistry(world);
        ClipboardService clipboard = new ClipboardService(
                newTestCanvas(world, selection, history), identities);

        int source = createEntity(world, 7f, 9f, 1);
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(source);
        animation.animation = "hero";
        animation.currentClip = "run";
        animation.clips.put("idle", new AnimationComponent.Clip(0, 1));
        AnimationComponent.Clip run = new AnimationComponent.Clip(2, 5);
        run.flipX = true;
        animation.clips.put("run", run);
        selection.selectOnly(source);

        Assert.assertTrue(clipboard.cutSelection());
        world.process();
        Assert.assertTrue(clipboard.paste());

        IntArray selectedAfterPaste = selection.getSelectionSnapshot();
        Assert.assertEquals(1, selectedAfterPaste.size);

        int pasted = selectedAfterPaste.first();
        AnimationComponent pastedAnimation = world.getMapper(AnimationComponent.class).get(pasted);

        Assert.assertEquals("run", pastedAnimation.currentClip);
        Assert.assertFalse(pastedAnimation.clips.get("idle").flipX);
        Assert.assertTrue(pastedAnimation.clips.get("run").flipX);
    }

    @Test
    public void clipboardAndEntityGraphShareOneIdentityRegistry() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = identityRegistry(world);
        ClipboardService clipboard = new ClipboardService(
                newTestCanvas(world, selection, history), identities);

        int source = createEntity(world, 1f, 2f, 0);
        identities.ensureStableId(source);
        selection.selectOnly(source);
        Assert.assertTrue(clipboard.copySelection());
        Assert.assertTrue(clipboard.paste());
        int clipboardEntity = selection.getFirstSelectedEntityId();
        int clipboardStableId = world.getMapper(PixscapeIdentityComponent.class)
                .get(clipboardEntity).stableId;

        EntityGraph graph = new EntityGraphCaptureService(world)
                .capture(new IntArray(new int[]{source}));
        EntityGraphInstantiationResult directResult =
                new EntityGraphInstantiationService(
                        world, history, identities,
                        new games.pixscape.runtime.service.PhysicsService(
                                world, null, new games.pixscape.studio.configuration.SceneMeta()))
                        .instantiate(graph, 0, 4f, 4f, "Direct graph path");
        int directEntity = directResult.createdIds().first();
        int directStableId = world.getMapper(PixscapeIdentityComponent.class)
                .get(directEntity).stableId;
        world.process();

        Assert.assertNotEquals(clipboardStableId, directStableId);
        Assert.assertEquals(clipboardEntity, identities.findByStableId(clipboardStableId));
        Assert.assertEquals(directEntity, identities.findByStableId(directStableId));
    }

    @Test
    public void copyingSpatialEntityRejectsAndPreservesPreviousClipboardContent() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = identityRegistry(world);
        ClipboardService clipboard = new ClipboardService(
                newTestCanvas(world, selection, history), identities);
        int direct = createEntity(world, 1f, 2f, 0);
        selection.selectOnly(direct);
        Assert.assertTrue(clipboard.copySelection());

        int spatial = createEntity(world, 3f, 4f, 0);
        world.getMapper(SpatialBlocksComponent.class).create(spatial);
        selection.selectOnly(spatial);
        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class, clipboard::copySelection);
        Assert.assertEquals(
                games.pixscape.studio.service.entitygraph.ActorPrefabSpatialScopeGuard.MESSAGE,
                failure.getMessage());
        Assert.assertTrue(clipboard.hasContent());

        selection.selectOnly(direct);
        Assert.assertTrue(clipboard.paste());
    }

    @Test
    public void injectedLinkedGraphRejectsPasteWithoutWorldOrHistoryMutation() throws Exception {
        World world = new World(new WorldConfiguration());
        SelectionService selection = new SelectionService(world, null);
        HistoryManager history = new HistoryManager(32);
        IdentityRegistry identities = identityRegistry(world);
        ClipboardService clipboard = new ClipboardService(
                newTestCanvas(world, selection, history), identities);
        int source = createEntity(world, 1f, 2f, 0);
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(source);
        games.pixscape.runtime.physics.PhysicsShapeData linked =
                new games.pixscape.runtime.physics.PhysicsShapeData();
        linked.physicsShapeId = 1;
        shapes.shapes.add(linked);
        GenericEntityInitializer initializer = new GenericEntityInitializer(world);
        initializer.syncFrom(source);
        setPrivateField(clipboard, "graph", new EntityGraph(java.util.List.of(
                new games.pixscape.studio.service.entitygraph.EntityGraphEntry(source, initializer))));
        int entitiesBefore = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all()).getEntities().size();
        int cursorBefore = history.getCursor();
        games.pixscape.runtime.loading.SceneMetaRuntime meta =
                (games.pixscape.runtime.loading.SceneMetaRuntime) getPrivateField(
                        identities, "sceneMeta");
        int stableHighWaterBefore = meta.nextEntityStableId;
        int shapeHighWaterBefore = meta.nextPhysicsShapeId;

        IllegalArgumentException failure = Assert.assertThrows(
                IllegalArgumentException.class, clipboard::paste);
        Assert.assertEquals(
                games.pixscape.studio.service.entitygraph.ActorPrefabSpatialScopeGuard.MESSAGE,
                failure.getMessage());
        Assert.assertEquals(entitiesBefore, world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all()).getEntities().size());
        Assert.assertEquals(cursorBefore, history.getCursor());
        Assert.assertEquals(stableHighWaterBefore, meta.nextEntityStableId);
        Assert.assertEquals(shapeHighWaterBefore, meta.nextPhysicsShapeId);
    }

    private static int createEntity(World world, float x, float y, int layerIndex) {
        int eid = world.create();
        TransformComponent tr = world.getMapper(TransformComponent.class).create(eid);
        tr.x = x;
        tr.y = y;

        EntityIndexComponent idx = world.getMapper(EntityIndexComponent.class).create(eid);
        idx.layerIndex = layerIndex;
        idx.zIndex = 1;
        return eid;
    }

    private static WorldCanvas newTestCanvas(World world,
                                              SelectionService selection,
                                              HistoryManager history) throws Exception {
        Unsafe unsafe = getUnsafe();
        WorldCanvas canvas = (WorldCanvas) unsafe.allocateInstance(WorldCanvas.class);
        setFieldUnsafe(unsafe, canvas, "world", world);
        setFieldUnsafe(unsafe, canvas, "selectionService", selection);
        setFieldUnsafe(unsafe, canvas, "historyManager", history);
        return canvas;
    }

    private static IdentityRegistry identityRegistry(World world) {
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, new games.pixscape.studio.configuration.SceneMeta());
        identities.rebuild();
        return identities;
    }

    private static Unsafe getUnsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static void setFieldUnsafe(Unsafe unsafe, Object target, String fieldName, Object value) throws Exception {
        Field field = WorldCanvas.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        long offset = unsafe.objectFieldOffset(field);
        unsafe.putObject(target, offset, value);
    }

    private static void setPrivateField(Object target, String fieldName, Object value) throws Exception {
        Field field = ClipboardService.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        field.set(target, value);
    }

    private static Object getPrivateField(Object target, String fieldName) throws Exception {
        Field field = IdentityRegistry.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(target);
    }

    private static int countEntitiesAt(World world, float x, float y) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(com.artemis.Aspect.all(TransformComponent.class, EntityIndexComponent.class))
                .getEntities();

        int count = 0;
        int[] data = entities.getData();
        for (int i = 0, n = entities.size(); i < n; i++) {
            int eid = data[i];
            TransformComponent tr = world.getMapper(TransformComponent.class).getSafe(eid, null);
            if (tr != null && tr.x == x && tr.y == y) {
                count++;
            }
        }
        return count;
    }
}
