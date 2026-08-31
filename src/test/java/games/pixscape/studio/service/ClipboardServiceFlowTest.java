package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.QuadDeformComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.SpatialHeightComponent;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.system.DirtyTrackerSystem;
import games.pixscape.runtime.system.GameObjectHierarchySystem;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
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
import java.nio.file.Files;
import java.nio.file.Path;

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
    public void copyPastePreservesCompleteQuadDeformation() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 7f, 9f, 0);
        QuadDeformComponent quad = h.world.getMapper(QuadDeformComponent.class).create(source);
        quad.blX = 1f;
        quad.blY = 2f;
        quad.brX = 3f;
        quad.brY = 4f;
        quad.trX = 5f;
        quad.trY = 6f;
        quad.tlX = 7f;
        quad.tlY = 8f;
        h.selection.selectOnly(source);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());

        QuadDeformComponent pasted = h.world.getMapper(QuadDeformComponent.class)
                .get(h.selection.getSelectionSnapshot().first());
        Assert.assertNotNull(pasted);
        Assert.assertArrayEquals(
                new float[]{1f, 2f, 3f, 4f, 5f, 6f, 7f, 8f},
                new float[]{
                        pasted.blX, pasted.blY, pasted.brX, pasted.brY,
                        pasted.trX, pasted.trY, pasted.tlX, pasted.tlY},
                0f);
    }

    @Test
    public void copyPasteOfUndeformedEntityDoesNotAddQuadComponent() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 7f, 9f, 0);
        h.selection.selectOnly(source);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());

        int pasted = h.selection.getSelectionSnapshot().first();
        Assert.assertFalse(h.world.getMapper(QuadDeformComponent.class).has(pasted));
    }

    @Test
    public void copyPasteGameObjectRootCapturesTheCompleteHierarchyAndSelectsOnlyTheNewRoot()
            throws Exception {
        Harness h = new Harness();
        int root = gameObject(h.world, 100, -1, 5f, 7f, 0.2f, 1.25f, 2, "source/tree");
        int child = gameObjectMember(h.world, 200, 100, 3f, -4f, 4);
        h.world.getMapper(CustomPropertiesComponent.class).create(child).properties = new PropertySet()
                .putObjectStableId("root", 100);
        h.world.process();
        h.selection.selectOnly(root);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertEquals(root, h.selection.getFirstSelectedEntityId());
        Assert.assertTrue(h.clipboard.paste());
        h.world.process();

        IntArray selected = h.selection.getSelectionSnapshot();
        Assert.assertEquals(1, selected.size);
        int copiedRoot = selected.first();
        int copiedChild = findMemberOf(h.world, copiedRoot);
        Assert.assertTrue(copiedChild >= 0);
        Assert.assertTrue(h.world.getMapper(GameObjectComponent.class).has(copiedRoot));
        Assert.assertEquals("source/tree", h.world.getMapper(GameObjectComponent.class)
                .get(copiedRoot).sourceAssetId);
        Assert.assertEquals(21f, h.world.getMapper(TransformComponent.class).get(copiedRoot).x, 0f);
        Assert.assertEquals(-9f, h.world.getMapper(TransformComponent.class).get(copiedRoot).y, 0f);
        Assert.assertEquals(3f, h.world.getMapper(TransformComponent.class).get(copiedChild).x, 0f);
        int copiedRootStableId = h.world.getMapper(PixscapeIdentityComponent.class)
                .get(copiedRoot).stableId;
        Assert.assertNotEquals(100, copiedRootStableId);
        Assert.assertEquals(copiedRootStableId, h.world.getMapper(GameObjectMemberComponent.class)
                .get(copiedChild).parentStableId);
        Assert.assertEquals(copiedRootStableId, h.world.getMapper(CustomPropertiesComponent.class)
                .get(copiedChild).properties.getObjectStableId("root", -1));
    }

    @Test
    public void copyRootAndDescendantNormalizesToOneHierarchyAndMemberOnlyCopyFailsCleanly()
            throws Exception {
        Harness h = new Harness();
        int root = gameObject(h.world, 100, -1, 0f, 0f, 0f, 1f, 1, "");
        int child = gameObjectMember(h.world, 200, 100, 1f, 2f, 2);
        h.world.process();
        h.selection.selectOnly(root);
        h.selection.selectAdd(child);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());
        h.world.process();
        Assert.assertEquals(1, h.selection.getSelectionSnapshot().size);
        Assert.assertEquals(5, countAll(h.world));

        h.selection.selectOnly(child);
        Assert.assertFalse(h.clipboard.copySelection());
        Assert.assertFalse(h.clipboard.hasContent());
        Assert.assertEquals(child, h.selection.getFirstSelectedEntityId());
        Assert.assertTrue(h.world.getEntityManager().isActive(root));
        Assert.assertTrue(h.world.getEntityManager().isActive(child));
    }

    @Test
    public void cutGameObjectRootDeletesTheSubtreeAndClipboardPasteCreatesFreshHierarchy()
            throws Exception {
        Harness h = new Harness();
        int root = gameObject(h.world, 100, -1, 3f, 4f, 0f, 1f, 1, "source/tree");
        int child = gameObjectMember(h.world, 200, 100, 5f, 6f, 2);
        h.world.process();
        h.selection.selectOnly(root);
        long originalRootHistory = h.history.historyIds().ensureForEntity(root);
        long originalChildHistory = h.history.historyIds().ensureForEntity(child);

        Assert.assertTrue(h.clipboard.cutSelection());
        h.world.process();
        Assert.assertFalse(h.world.getEntityManager().isActive(root));
        Assert.assertFalse(h.world.getEntityManager().isActive(child));
        Assert.assertTrue(h.clipboard.hasContent());

        h.history.undo();
        h.world.process();
        int restoredRoot = h.history.historyIds().entityOfHistoryId(originalRootHistory);
        int restoredChild = h.history.historyIds().entityOfHistoryId(originalChildHistory);
        Assert.assertTrue(restoredRoot >= 0);
        Assert.assertTrue(restoredChild >= 0);
        Assert.assertEquals(h.world.getMapper(PixscapeIdentityComponent.class).get(restoredRoot).stableId,
                h.world.getMapper(GameObjectMemberComponent.class).get(restoredChild).parentStableId);

        Assert.assertTrue(h.clipboard.paste());
        h.world.process();
        int pastedRoot = h.selection.getFirstSelectedEntityId();
        Assert.assertNotEquals(h.world.getMapper(PixscapeIdentityComponent.class).get(restoredRoot).stableId,
                h.world.getMapper(PixscapeIdentityComponent.class).get(pastedRoot).stableId);
        Assert.assertTrue(findMemberOf(h.world, pastedRoot) >= 0);
    }

    @Test
    public void cutNestedGameObjectDeletesOnlyItsSubtreeAndUndoRestoresItsParentRelation()
            throws Exception {
        Harness h = new Harness();
        int outer = gameObject(h.world, 100, -1, 0f, 0f, 0f, 1f, 1, "");
        int nested = gameObject(h.world, 200, 100, 2f, 3f, 0f, 1f, 2, "");
        int leaf = gameObjectMember(h.world, 300, 200, 4f, 5f, 3);
        h.world.process();
        long nestedHistory = h.history.historyIds().ensureForEntity(nested);
        h.selection.selectOnly(nested);

        Assert.assertTrue(h.clipboard.cutSelection());
        h.world.process();
        Assert.assertTrue(h.world.getEntityManager().isActive(outer));
        Assert.assertFalse(h.world.getEntityManager().isActive(nested));
        Assert.assertFalse(h.world.getEntityManager().isActive(leaf));

        h.history.undo();
        h.world.process();
        int restoredNested = h.history.historyIds().entityOfHistoryId(nestedHistory);
        Assert.assertTrue(restoredNested >= 0);
        Assert.assertEquals(h.world.getMapper(PixscapeIdentityComponent.class).get(outer).stableId,
                h.world.getMapper(GameObjectMemberComponent.class).get(restoredNested).parentStableId);
        Assert.assertTrue(findMemberOf(h.world, restoredNested) >= 0);
    }

    @Test
    public void copyPasteGameObjectHierarchySurvivesSceneSaveAndReload() throws Exception {
        Harness h = new Harness();
        int root = gameObject(h.world, 100, -1, 3f, 4f, 0f, 1f, 1, "source/tree");
        int child = gameObjectMember(h.world, 200, 100, 5f, 6f, 2);
        h.world.process();
        h.selection.selectOnly(root);
        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());
        h.world.process();

        int copiedRoot = h.selection.getFirstSelectedEntityId();
        int copiedChild = findMemberOf(h.world, copiedRoot);
        int copiedRootStableId = h.world.getMapper(PixscapeIdentityComponent.class)
                .get(copiedRoot).stableId;
        int copiedChildStableId = h.world.getMapper(PixscapeIdentityComponent.class)
                .get(copiedChild).stableId;
        Path path = Files.createTempFile("clipboard-game-object", ".json");
        World loaded = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(32),
                        new GameObjectHierarchySystem(32))
                .build());
        try {
            SceneService.saveScene(h.world, new FileHandle(path.toFile()), false);
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = h.sceneMeta.nextEntityStableId;
            SceneLoader.loadScene(loaded, new FileHandle(path.toFile()), false, meta);
            IdentityRegistry loadedIdentities = new IdentityRegistry();
            loadedIdentities.bind(loaded, meta);
            loadedIdentities.rebuild();
            loaded.process();

            int restoredRoot = findByStableId(loaded, copiedRootStableId);
            int restoredChild = findByStableId(loaded, copiedChildStableId);
            Assert.assertTrue(restoredRoot >= 0);
            Assert.assertTrue(restoredChild >= 0);
            Assert.assertEquals("source/tree", loaded.getMapper(GameObjectComponent.class)
                    .get(restoredRoot).sourceAssetId);
            Assert.assertEquals(copiedRootStableId,
                    loaded.getMapper(GameObjectMemberComponent.class)
                            .get(restoredChild).parentStableId);
            Assert.assertEquals(5f, loaded.getMapper(TransformComponent.class)
                    .get(restoredChild).x, 0f);
            Assert.assertEquals(6f, loaded.getMapper(TransformComponent.class)
                    .get(restoredChild).y, 0f);
        } finally {
            loaded.dispose();
            Files.deleteIfExists(path);
        }
    }

    @Test
    public void copyPastePointLightPreservesCompleteLightStateInClassicLayer() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 13f, 17f, 0);
        PointLightComponent light = h.world.getMapper(PointLightComponent.class).create(source);
        light.enabled = false;
        light.radius = 123f;
        light.intensity = 2.5f;
        light.falloff = 3.25f;
        light.r = 0.15f;
        light.g = 0.35f;
        light.b = 0.75f;
        h.selection.selectOnly(source);

        Assert.assertTrue(h.clipboard.copySelection());
        Assert.assertTrue(h.clipboard.paste());

        int pastedEntity = h.selection.getFirstSelectedEntityId();
        PointLightComponent pasted = h.world.getMapper(PointLightComponent.class).get(pastedEntity);
        Assert.assertNotNull(pasted);
        Assert.assertFalse(pasted.enabled);
        Assert.assertEquals(123f, pasted.radius, 0f);
        Assert.assertEquals(2.5f, pasted.intensity, 0f);
        Assert.assertEquals(3.25f, pasted.falloff, 0f);
        Assert.assertEquals(0.15f, pasted.r, 0f);
        Assert.assertEquals(0.35f, pasted.g, 0f);
        Assert.assertEquals(0.75f, pasted.b, 0f);
        Assert.assertEquals(0,
                h.world.getMapper(EntityIndexComponent.class).get(pastedEntity).layerIndex);
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
                        h.world, h.history, h.identities, h.physicsService, () -> true)
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
    public void pasteResolvesValidOrdinaryLayerAndKeepsPhysics() throws Exception {
        Harness h = new Harness();
        int source = physicalEntity(h.world, 0, false);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());

        Assert.assertTrue(h.clipboard.paste());
        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertEquals(0, h.world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertTrue(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertEquals(1, h.world.getMapper(PhysicsShapesComponent.class).get(pasted).shapes.size);
    }

    @Test
    public void pasteResolvesSecondOrdinaryLayerAndKeepsPhysics() throws Exception {
        Harness h = new Harness();
        int ordinaryLayer = h.addLayer(1, false);
        int source = physicalEntity(h.world, 0, false);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.selection.setActivelayerId(ordinaryLayer);

        Assert.assertTrue(h.clipboard.paste());
        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertEquals(1, h.world.getMapper(EntityIndexComponent.class).get(pasted).layerIndex);
        Assert.assertTrue(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertEquals(1, h.world.getMapper(PhysicsShapesComponent.class).get(pasted).shapes.size);
    }

    @Test
    public void pasteRejectsRetainedPhysicsGraphWhileScenePhysicsIsDisabled() throws Exception {
        Harness h = new Harness();
        int source = physicalEntity(h.world, 0, false);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.world.delete(source);
        h.world.process();
        h.selection.clearSelection();
        h.sceneMeta.physicsEnabled = false;
        int entitiesBefore = countAll(h.world);
        int historyCursorBefore = h.history.getCursor();
        int nextStableIdBefore = h.sceneMeta.nextEntityStableId;

        Assert.assertFalse(h.clipboard.paste());

        Assert.assertTrue(h.clipboard.hasContent());
        Assert.assertEquals(entitiesBefore, countAll(h.world));
        Assert.assertEquals(historyCursorBefore, h.history.getCursor());
        Assert.assertEquals(nextStableIdBefore, h.sceneMeta.nextEntityStableId);

        h.sceneMeta.physicsEnabled = true;
        Assert.assertTrue(h.clipboard.paste());
        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertTrue(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertEquals(
                1,
                h.world.getMapper(PhysicsShapesComponent.class).get(pasted).shapes.size);
    }

    @Test
    public void ordinaryClipboardGraphPastesWhileScenePhysicsIsDisabled() throws Exception {
        Harness h = new Harness();
        int source = createEntity(h.world, 3f, 7f, 0);
        h.selection.selectOnly(source);
        Assert.assertTrue(h.clipboard.copySelection());
        h.sceneMeta.physicsEnabled = false;

        Assert.assertTrue(h.clipboard.paste());

        int pasted = h.selection.getFirstSelectedEntityId();
        Assert.assertFalse(h.world.getMapper(PhysicsBodyComponent.class).has(pasted));
        Assert.assertEquals(19f,
                h.world.getMapper(TransformComponent.class).get(pasted).x, 0f);
    }

    @Test
    public void pasteResolvesValidSpatialEnabledLayerAndKeepsSpatialState() throws Exception {
        Harness h = new Harness();
        int spatialLayer = h.addLayer(1, true);
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
        int target = h.addLayer(1, false);
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

    private static int gameObject(
            World world, int stableId, int parentStableId, float x, float y,
            float rotation, float scale, int z, String sourceAssetId) {
        int entity = gameObjectMember(world, stableId, parentStableId, x, y, z);
        TransformComponent transform = world.getMapper(TransformComponent.class).get(entity);
        transform.rotationRad = rotation;
        transform.scaleX = scale;
        transform.scaleY = scale;
        world.getMapper(GameObjectComponent.class).create(entity).sourceAssetId = sourceAssetId;
        return entity;
    }

    private static int gameObjectMember(
            World world, int stableId, int parentStableId, float x, float y, int z) {
        int entity = createEntity(world, x, y, 0);
        world.getMapper(EntityIndexComponent.class).get(entity).zIndex = z;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        if (parentStableId >= 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entity)
                    .parentStableId = parentStableId;
        }
        return entity;
    }

    private static int findMemberOf(World world, int rootEntityId) {
        PixscapeIdentityComponent root = world.getMapper(PixscapeIdentityComponent.class)
                .getSafe(rootEntityId, null);
        if (root == null) return -1;
        com.artemis.utils.IntBag members = world.getAspectSubscriptionManager()
                .get(Aspect.all(GameObjectMemberComponent.class)).getEntities();
        for (int i = 0; i < members.size(); i++) {
            int entityId = members.get(i);
            if (world.getMapper(GameObjectMemberComponent.class)
                    .get(entityId).parentStableId == root.stableId) {
                return entityId;
            }
        }
        return -1;
    }

    private static int findByStableId(World world, int stableId) {
        IntBag identities = world.getAspectSubscriptionManager()
                .get(Aspect.all(PixscapeIdentityComponent.class)).getEntities();
        for (int i = 0; i < identities.size(); i++) {
            int entityId = identities.get(i);
            if (world.getMapper(PixscapeIdentityComponent.class).get(entityId).stableId == stableId) {
                return entityId;
            }
        }
        return -1;
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
        final World world = new World(new WorldConfigurationBuilder()
                .with(new WorldSerializationManager(), new DirtyTrackerSystem(32),
                        new GameObjectHierarchySystem(32))
                .build());
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
            sceneMeta.physicsEnabled = true;
            sceneMeta.nextEntityStableId = 1_000;
            identities.bind(world, sceneMeta);
            identities.rebuild();
            physicsService = new PhysicsService(world, null, sceneMeta);
            layers = new LayerService(world, null, history.historyIds(), identities);
            classicLayer = addLayer(0, false);
            selection = new SelectionService(world, layers);
            selection.setActivelayerId(classicLayer);
            canvas = newTestCanvas(
                    world, selection, history, layers, physicsService);
            clipboard = new ClipboardService(canvas, identities);
        }

        int addLayer(int index, boolean spatialEnabled) {
            int entity = world.create();
            LayerComponent layer = world.getMapper(LayerComponent.class).create(entity);
            layer.layerIndex = index;
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
