package games.pixscape.studio.service.gameobject;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialRepresentationComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.property.PropertyValue;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ConvertSelectionToGameObjectCommand;
import games.pixscape.studio.history.commands.DeleteEntitiesCommandFactory;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameObjectAssetServiceTest {
    @Rule
    public TemporaryFolder temp = new TemporaryFolder();

    @Test
    public void autonomousSpatialAuthoringSurvivesHistorySaveAndReopeningWithoutSpatialLayer() throws Exception {
        var type = games.pixscape.studio.document.EditorDocumentType.GAME_OBJECT;
        var context = new games.pixscape.studio.scene.SceneEditorContext(null,
                new games.pixscape.studio.service.StudioEditingModeService(), type);
        World world = new World(new WorldConfiguration());
        var meta = new games.pixscape.studio.configuration.SceneMeta();
        meta.nextEntityStableId = 1000;
        meta.physicsEnabled = false;
        context.initializeWorld(world, new games.pixscape.studio.service.tiled.TiledAllocatorService(), meta);
        var reopenedContext = new games.pixscape.studio.scene.SceneEditorContext(null,
                new games.pixscape.studio.service.StudioEditingModeService(), type);
        var sceneContext = new games.pixscape.studio.scene.SceneEditorContext("scene",
                new games.pixscape.studio.service.StudioEditingModeService());
        try {
            int layerIndex = context.layerService().addLayerTop("Asset internal layer");
            int root = entity(world, 10, -1, true, 0, 0);
            int child = entity(world, 11, 10, false, 4, 1);
            world.getMapper(TextureRegionComponent.class).create(child);
            world.getMapper(AssetRefComponent.class).create(child).assetId = 7;
            context.identityRegistry().rebuild();
            world.process();
            var file = new FileHandle(temp.getRoot()).child("authored-spatial.gameobject");
            var document = new games.pixscape.studio.document.GameObjectEditorDocument(
                    "authored-spatial", "Spatial", file, context, root);
            Assert.assertEquals(type, document.context().documentType());
            Assert.assertTrue(context.layerService().admitsSpatialActor(root));
            var layer = world.getMapper(LayerComponent.class).get(context.layerService().getLayerEntity(layerIndex));
            Assert.assertFalse(layer.spatialEnabled);
            var history = context.historyManager();
            history.execute(new games.pixscape.studio.history.commands.ToggleSpatialActorCommand(
                    world, history.historyIds(), root, true, true,
                    new games.pixscape.runtime.component.spatial.SpatialFootprintComponent(), type));
            var footprints = world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class);
            var representations = world.getMapper(SpatialRepresentationComponent.class);
            for (int cycle = 0; cycle < 3; cycle++) {
                Assert.assertTrue(footprints.has(root));
                Assert.assertFalse(footprints.has(child));
                Assert.assertFalse(representations.has(root));
                Assert.assertNotNull(representations.get(child).rectangles.staticRectangle);
                history.undo();
                Assert.assertFalse(footprints.has(root));
                Assert.assertFalse(representations.has(child));
                history.redo();
            }
            history.execute(new games.pixscape.studio.history.commands.SetFootprintInheritanceCommand(
                    world, history.historyIds(), child, false, type));
            Assert.assertTrue(footprints.has(child));
            history.undo();
            Assert.assertFalse(footprints.has(child));
            history.redo();
            Assert.assertTrue(footprints.has(child));
            history.execute(new games.pixscape.studio.history.commands.ToggleSpatialActorCommand(
                    world, history.historyIds(), root, false, true, null, type));
            Assert.assertFalse(footprints.has(root));
            history.undo();
            Assert.assertTrue(footprints.has(root));
            new GameObjectAssetService(world).saveGameObject(file,
                    new EntityGraphCaptureService(world).captureForGameObject(IntArray.with(root)));
            var saved = new GameObjectAssetLoader().load(file);
            Assert.assertNotNull(saved.entities.get(1).spatialRepresentation.rectangles.staticRectangle);
            Assert.assertFalse(layer.spatialEnabled);
            Assert.assertFalse(world.getMapper(PhysicsBodyComponent.class).has(root));

            World reopened = new World(new WorldConfiguration());
            reopenedContext.initializeWorld(reopened, new games.pixscape.studio.service.tiled.TiledAllocatorService(),
                    new games.pixscape.studio.configuration.SceneMeta());
            int internalLayer = reopenedContext.layerService().addLayerTop("Internal");
            reopened.process();
            var service = new GameObjectAssetService(reopened, reopenedContext.historyManager(),
                    reopenedContext.identityRegistry(), null, null, null, null, () -> false, type);
            var result = service.instantiateGameObject(file, "authored-spatial", internalLayer, 0, 0);
            reopened.process();
            Assert.assertEquals(2, result.createdIds().size);
            Assert.assertTrue(reopened.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class)
                    .has(result.createdIds().first()));
            Assert.assertNotNull(reopened.getMapper(SpatialRepresentationComponent.class)
                    .get(result.createdIds().get(1)).rectangles.staticRectangle);
            Assert.assertFalse(reopened.getMapper(LayerComponent.class)
                    .get(reopenedContext.layerService().getLayerEntity(internalLayer)).spatialEnabled);
            // Insert the same asset into a distinct scene document.
            World sceneWorld = new World(new WorldConfiguration());
            sceneContext.initializeWorld(sceneWorld, new games.pixscape.studio.service.tiled.TiledAllocatorService(),
                    new games.pixscape.studio.configuration.SceneMeta());
            int sceneLayer = sceneContext.layerService().addLayerTop("Scene layer");
            sceneWorld.process();
            var sceneService = new GameObjectAssetService(sceneWorld, sceneContext.historyManager(),
                    sceneContext.identityRegistry(), null, null, null, null, () -> false, sceneContext.documentType());
            int nextId = sceneContext.sceneMeta().nextEntityStableId;
            int cursor = sceneContext.historyManager().getCursor();
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> sceneService.instantiateGameObject(file, "authored-spatial", sceneLayer, 0, 0));
            Assert.assertEquals(nextId, sceneContext.sceneMeta().nextEntityStableId);
            Assert.assertEquals(cursor, sceneContext.historyManager().getCursor());
            sceneWorld.getMapper(LayerComponent.class)
                    .get(sceneContext.layerService().getLayerEntity(sceneLayer)).spatialEnabled = true;
            Assert.assertEquals(2, sceneService.instantiateGameObject(file, "authored-spatial", sceneLayer, 0, 0).createdIds().size);
        } finally {
            context.dispose();
            reopenedContext.dispose();
            sceneContext.dispose();
        }
    }

    @Test
    public void spatialAssetDestinationIsCheckedBeforeIdentitiesAndHistoryButNotForAssetEditing() throws Exception {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        try {
            int root = entity(world, 10, -1, true, 0, 0);
            world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).create(root);
            int layer = world.create();
            var spatial = world.getMapper(LayerComponent.class).create(layer);
            spatial.layerIndex = 3;
            spatial.spatialEnabled = true;
            world.getMapper(LayerMetaComponent.class).create(layer);
            world.process();
            FileHandle file = new FileHandle(temp.getRoot()).child("spatial.gameobject");
            new GameObjectAssetService(world).saveGameObject(file,
                    new EntityGraphCaptureService(world).captureForGameObject(IntArray.with(root)));
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 1000;
            identities.bind(world, meta);
            identities.rebuild();
            HistoryManager history = new HistoryManager(16);
            GameObjectAssetService service = new GameObjectAssetService(world, history, identities, null, null);
            service.instantiateGameObject(file, "spatial", 3, 0, 0);
            history.undo();
            world.process();
            int nextId = meta.nextEntityStableId;
            int count = world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size();
            long nextHistory = history.historyIds().allocateHistoryId();
            long revision = history.getMutationRevision();
            Assert.assertThrows(IllegalArgumentException.class,
                    () -> service.instantiateGameObject(file, "spatial", 1, 0, 0));
            world.process();
            Assert.assertEquals(nextId, meta.nextEntityStableId);
            Assert.assertEquals(nextHistory + 1, history.historyIds().allocateHistoryId());
            Assert.assertEquals(count, world.getAspectSubscriptionManager().get(Aspect.all()).getEntities().size());
            Assert.assertEquals(revision, history.getMutationRevision());
            Assert.assertTrue(history.canRedo());
            Assert.assertFalse(history.canUndo());
            history.redo();
            history.undo();
            world.process();
            spatial.spatialEnabled = false;
            GameObjectAssetService assetEditor = new GameObjectAssetService(world, history, identities, null, null,
                    null, null, () -> false, games.pixscape.studio.document.EditorDocumentType.GAME_OBJECT);
            Assert.assertFalse(assetEditor.instantiateGameObject(file, "spatial", 3, 0, 0).createdIds().isEmpty());
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    @Test
    public void assetTagsAndRepeatSurviveInsertionAndDeletionHistoryWithoutDuplication() throws Exception {
        World world = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        try {
            int root = entity(world, 10, -1, true, 0, 0);
            int child = entity(world, 11, 10, false, 4, 1);
            world.getMapper(PixscapeTagComponent.class).create(root).tags.add("root-tag");
            world.getMapper(PixscapeTagComponent.class).create(child).tags.add("child-tag");
            world.getMapper(RenderRepeatComponent.class).create(root).repeatX = true;
            world.getMapper(RenderRepeatComponent.class).create(child).repeatY = true;
            int layer = world.create();
            world.getMapper(LayerComponent.class).create(layer).layerIndex = 3;
            world.getMapper(LayerMetaComponent.class).create(layer);
            world.process();
            FileHandle file = new FileHandle(temp.getRoot()).child("authored.gameobject");
            new GameObjectAssetService(world).saveGameObject(file,
                    new EntityGraphCaptureService(world).captureForGameObject(IntArray.with(root)));
            GameObjectAsset saved = new GameObjectAssetLoader().load(file);
            Assert.assertEquals("root-tag", saved.entities.first().tags.values.first());
            Assert.assertTrue(saved.entities.first().repeat.repeatX);
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 1000;
            identities.bind(world, meta);
            identities.rebuild();
            HistoryManager history = new HistoryManager(16);
            GameObjectAssetService service = new GameObjectAssetService(world, history, identities, null, null);
            EntityGraphInstantiationResult result = service.instantiateGameObject(file, "authored", 3, 20, 30);
            world.process();
            long rootHistory = history.historyIds().historyIdOfEntity(result.sourceToCreated().get(1, -1));
            long childHistory = history.historyIds().historyIdOfEntity(result.sourceToCreated().get(2, -1));
            for (int cycle = 0; cycle < 3; cycle++) {
                history.undo();
                world.process();
                history.redo();
                world.process();
                assertAssetTagsAndRepeat(world, history.historyIds().entityOfHistoryId(rootHistory),
                        history.historyIds().entityOfHistoryId(childHistory));
            }
            history.execute(DeleteEntitiesCommandFactory.create(world, history.historyIds(),
                    IntArray.with(history.historyIds().entityOfHistoryId(rootHistory)), null));
            world.process();
            for (int cycle = 0; cycle < 3; cycle++) {
                history.undo();
                world.process();
                assertAssetTagsAndRepeat(world, history.historyIds().entityOfHistoryId(rootHistory),
                        history.historyIds().entityOfHistoryId(childHistory));
                history.redo();
                world.process();
            }
            Assert.assertEquals(1, world.getMapper(PixscapeTagComponent.class).get(root).tags.size);
            Assert.assertEquals(1, world.getMapper(PixscapeTagComponent.class).get(child).tags.size);
        } finally {
            identities.bind(null, null);
            world.dispose();
        }
    }

    private static void assertAssetTagsAndRepeat(World world, int root, int child) {
        Assert.assertEquals(1, world.getMapper(PixscapeTagComponent.class).get(root).tags.size);
        Assert.assertEquals("root-tag", world.getMapper(PixscapeTagComponent.class).get(root).tags.first());
        Assert.assertEquals(1, world.getMapper(PixscapeTagComponent.class).get(child).tags.size);
        Assert.assertEquals("child-tag", world.getMapper(PixscapeTagComponent.class).get(child).tags.first());
        Assert.assertTrue(world.getMapper(RenderRepeatComponent.class).get(root).repeatX);
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).get(root).repeatY);
        Assert.assertFalse(world.getMapper(RenderRepeatComponent.class).get(child).repeatX);
        Assert.assertTrue(world.getMapper(RenderRepeatComponent.class).get(child).repeatY);
        Assert.assertEquals(world.getMapper(PixscapeIdentityComponent.class).get(root).stableId,
                world.getMapper(GameObjectMemberComponent.class).get(child).parentStableId);
    }

    @Test
    public void contoursSurviveGameObjectCaptureSaveLoadAndIndependentInstances() throws Exception {
        World source = new World(new WorldConfiguration());
        World target = new World(new WorldConfiguration());
        IdentityRegistry identities = new IdentityRegistry();
        try {
            int root = entity(source, 10, -1, true, 0, 0);
            int polygon = entity(source, 11, 10, false, 4, 0);
            int line = entity(source, 12, 10, false, 8, 1);
            float[] closed = {0, 0, 20, 20, 0, 20, 20, 0};
            float[] open = {-10, 5, 20, -7, 40, 12};
            source.getMapper(PolygonComponent.class).create(polygon).setVertices(closed);
            source.getMapper(PolylineComponent.class).create(line).setVertices(open);
            source.getMapper(CustomPropertiesComponent.class).create(polygon).properties.putObjectStableId("route", 12)
                    .putList("routes", Array.with(PropertyValue.ofClass("Link",
                            new PropertySet().putObjectStableId("target", 12).putFloat("precise", 16777217d))));
            source.process();
            EntityGraph graph = new EntityGraphCaptureService(source).captureForGameObject(new IntArray(new int[]{root}));
            source.getMapper(PolygonComponent.class).get(polygon).vertices[0] = 123;
            source.getMapper(PolylineComponent.class).get(line).vertices[0] = 456;
            FileHandle file = new FileHandle(temp.getRoot()).child("contours.gameobject");
            new GameObjectAssetService(source).saveGameObject(file, graph);
            GameObjectAsset asset = new GameObjectAssetLoader().load(file);
            Assert.assertArrayEquals(closed, asset.entities.get(1).polygonVertices, 0f);
            Assert.assertArrayEquals(open, asset.entities.get(2).polylineVertices, 0f);
            int layer = target.create();
            target.getMapper(LayerComponent.class).create(layer).layerIndex = 3;
            target.getMapper(LayerMetaComponent.class).create(layer);
            target.process();
            SceneMetaRuntime meta = new SceneMetaRuntime();
            meta.nextEntityStableId = 1000;
            identities.bind(target, meta);
            identities.rebuild();
            HistoryManager history = new HistoryManager(16);
            GameObjectAssetService service = new GameObjectAssetService(target, history, identities, null, null);
            EntityGraphInstantiationResult first = service.instantiateGameObject(file, "contours", 3, 20, 30);
            target.process();
            int firstPolygon = first.sourceToCreated().get(2, -1);
            int firstLine = first.sourceToCreated().get(3, -1);
            Assert.assertArrayEquals(closed, target.getMapper(PolygonComponent.class).get(firstPolygon).vertices, 0f);
            Assert.assertArrayEquals(open, target.getMapper(PolylineComponent.class).get(firstLine).vertices, 0f);
            Assert.assertFalse(target.getMapper(PolygonComponent.class).has(firstLine));
            Assert.assertEquals(target.getMapper(PixscapeIdentityComponent.class).get(firstLine).stableId,
                    target.getMapper(CustomPropertiesComponent.class).get(firstPolygon).properties.getObjectStableId("route", -1));
            PropertySet firstProperties = target.getMapper(CustomPropertiesComponent.class).get(firstPolygon).properties;
            Assert.assertEquals(target.getMapper(PixscapeIdentityComponent.class).get(firstLine).stableId,
                    firstProperties.getListValue("routes").getClassValue(0).properties().getObjectStableId("target", -1));
            firstProperties.putList("routes", new Array<>());
            target.getMapper(PolygonComponent.class).get(firstPolygon).vertices[0] = 999;
            target.getMapper(PolylineComponent.class).get(firstLine).vertices[0] = 999;
            EntityGraphInstantiationResult second = service.instantiateGameObject(file, "contours", 3, 40, 50);
            target.process();
            Assert.assertArrayEquals(closed, target.getMapper(PolygonComponent.class)
                    .get(second.sourceToCreated().get(2, -1)).vertices, 0f);
            Assert.assertArrayEquals(open, target.getMapper(PolylineComponent.class)
                    .get(second.sourceToCreated().get(3, -1)).vertices, 0f);
            PropertySet secondProperties = target.getMapper(CustomPropertiesComponent.class)
                    .get(second.sourceToCreated().get(2, -1)).properties;
            Assert.assertEquals(target.getMapper(PixscapeIdentityComponent.class)
                    .get(second.sourceToCreated().get(3, -1)).stableId,
                    secondProperties.getListValue("routes").getClassValue(0).properties().getObjectStableId("target", -1));
            Assert.assertEquals(16777217d, secondProperties.getListValue("routes").getClassValue(0)
                    .properties().getDouble("precise", 0), 0);
            Assert.assertEquals(1, source.getMapper(CustomPropertiesComponent.class).get(polygon).properties.getListValue("routes").size());
            Assert.assertArrayEquals(closed, new GameObjectAssetLoader().load(file).entities.get(1).polygonVertices, 0f);
        } finally {
            identities.bind(null, null);
            source.dispose();
            target.dispose();
        }
    }

    @Test
    public void lightAssetInsertionIsConfirmedBeforeReservingSceneIds() throws Exception {
        World source = new World(new WorldConfiguration()), target = new World(new WorldConfiguration());
        try {
            int root = entity(source, 10, -1, true, 0, 0), child = entity(source, 11, 10, false, 0, 1);
            source.getMapper(PointLightComponent.class).create(child);
            source.process();
            FileHandle file = new FileHandle(temp.getRoot()).child("lamp.gameobject");
            new GameObjectAssetService(source).saveGameObject(file, new EntityGraphCaptureService(source).captureForGameObject(new IntArray(new int[]{root})));
            int layer = target.create();
            target.getMapper(LayerComponent.class).create(layer).layerIndex = 3;
            target.getMapper(LayerMetaComponent.class).create(layer);
            int existing = entity(target, 50, -1, false, 0, 0);
            target.getMapper(ConeLightComponent.class).create(existing);
            target.process();
            var meta = new games.pixscape.studio.configuration.SceneMeta();
            meta.nextEntityStableId = 1000;
            meta.forwardLighting = true;
            IdentityRegistry identities = new IdentityRegistry();
            identities.bind(target, meta);
            identities.rebuild();
            HistoryManager history = new HistoryManager(16);
            history.historyIds().ensureForEntity(existing);
            Runnable[] accept = {null};
            history.configureSceneLighting(target, meta, action -> accept[0] = action);
            GameObjectAssetService service = new GameObjectAssetService(target, history, identities, null, null);
            Assert.assertEquals(0, service.instantiateGameObject(file, "lamp", 3, 0, 0).createdIds().size);
            Assert.assertEquals(1000, meta.nextEntityStableId);
            Assert.assertEquals(0, history.getCursor());
            Assert.assertNotNull(accept[0]);
            accept[0].run();
            target.process();
            Assert.assertFalse(meta.forwardLighting);
            Assert.assertEquals(2, history.historyIds().activeLightCount(target));
            history.undo();
            target.process();
            Assert.assertTrue(meta.forwardLighting);
            Assert.assertEquals(1, history.historyIds().activeLightCount(target));
            history.redo();
            target.process();
            Assert.assertFalse(meta.forwardLighting);
            Assert.assertEquals(2, history.historyIds().activeLightCount(target));
        } finally {
            source.dispose();
            target.dispose();
            games.pixscape.studio.event.EventFlow.i().discardPending();
        }
    }

    @Test
    public void savingAnEditedCopyLeavesPlacedObjectsUntouchedAndUpdatesFutureInstances()
            throws Exception {
        World placedWorld = new World(new WorldConfiguration());
        int placedRoot = entity(placedWorld, 11, -1, true, 0f, 0);
        int placedChild = entity(placedWorld, 12, 11, false, 4f, 1);
        placedWorld.getMapper(GameObjectComponent.class).get(placedRoot).sourceAssetId =
                "gameobjects/enemy.gameobject";
        placedWorld.process();
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("enemy.gameobject");
        new GameObjectAssetService(placedWorld).saveGameObject(assetFile,
                new EntityGraphCaptureService(placedWorld).captureForGameObject(
                        new IntArray(new int[]{placedRoot})));

        // The asset editor works in a different World from the placed instance.
        World editorWorld = new World(new WorldConfiguration());
        int editorRoot = entity(editorWorld, 101, -1, true, 0f, 0);
        int editorChild = entity(editorWorld, 102, 101, false, 4f, 1);
        editorWorld.process();
        editorWorld.getMapper(TransformComponent.class).get(editorChild).x = 42f;
        new GameObjectAssetService(editorWorld).saveGameObject(assetFile,
                new EntityGraphCaptureService(editorWorld).captureForGameObject(
                        new IntArray(new int[]{editorRoot})));

        Assert.assertEquals(4f, placedWorld.getMapper(TransformComponent.class)
                .get(placedChild).x, 0f);
        Assert.assertEquals(42f, new GameObjectAssetLoader().load(assetFile)
                .entities.get(1).transform.x, 0f);

        World futureWorld = new World(new WorldConfiguration());
        int layer = futureWorld.create();
        futureWorld.getMapper(LayerComponent.class).create(layer).layerIndex = 3;
        futureWorld.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 1000;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(futureWorld, meta);
        identities.rebuild();
        EntityGraphInstantiationResult future = new GameObjectAssetService(
                futureWorld, new HistoryManager(16), identities, null, null)
                .instantiateGameObject(assetFile, "enemy", 3, 0f, 0f);
        futureWorld.process();
        int futureChild = future.sourceToCreated().get(2, -1);
        Assert.assertEquals(42f, futureWorld.getMapper(TransformComponent.class)
                .get(futureChild).x, 0f);
    }

    @Test
    public void hierarchyUsesAssetLocalIdsAndPreservesAuthoredState() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 101, -1, true, 0f, 0);
        int nested = entity(world, 205, 101, true, 4f, 7);
        int leaf = entity(world, 309, 205, false, 2f, 3);
        world.getMapper(PixscapeTagComponent.class).create(leaf).tags.add("enemy");
        world.getMapper(CustomPropertiesComponent.class).create(root).properties
                .putObjectStableId("target", 309)
                .putString("role", "root");
        world.process();

        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureForGameObject(new IntArray(new int[]{root}));
        FileHandle file = new FileHandle(temp.newFile("enemy.gameobject"));
        GameObjectAssetService service = new GameObjectAssetService(world);
        service.saveGameObject(file, graph);

        GameObjectAsset asset = service.loadGameObjectAsset(file);
        Assert.assertEquals(GameObjectAsset.SCHEMA_VERSION, asset.schemaVersion);
        Assert.assertEquals(1, asset.rootSourceEntityId);
        Assert.assertEquals(3, asset.entities.size);
        Assert.assertEquals(-1, asset.entities.get(0).parentSourceEntityId);
        Assert.assertEquals(1, asset.entities.get(1).parentSourceEntityId);
        Assert.assertEquals(2, asset.entities.get(2).parentSourceEntityId);
        Assert.assertEquals(4f, asset.entities.get(1).transform.x, 0f);
        Assert.assertEquals(7, asset.entities.get(1).entityIndex.zIndex);
        Assert.assertEquals("enemy", asset.entities.get(2).tags.values.get(0));
        Assert.assertEquals(3, asset.entities.get(0).customProperties
                .getObjectStableId("target", -1));
        String json = file.readString("UTF-8");
        Assert.assertFalse(json.contains("101"));
        Assert.assertFalse(json.contains("parentStableId"));
        Assert.assertFalse(json.contains("sourceAssetId"));
        Assert.assertFalse(json.contains("layerIndex"));
    }

    @Test
    public void physicalHierarchyIsCapturedWithAssetLocalShapeIds() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        world.getMapper(PhysicsBodyComponent.class).create(root);
        PhysicsShapesComponent shapes = world.getMapper(PhysicsShapesComponent.class).create(root);
        PhysicsShapeData sceneShape = manualShape(77, 2f);
        sceneShape.friction = .7f;
        sceneShape.categoryBits = 0x0004;
        shapes.shapes.add(sceneShape);
        world.process();
        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureForGameObject(new IntArray(new int[]{root}));
        FileHandle file = new FileHandle(temp.getRoot()).child("physics.gameobject");
        new GameObjectAssetService(world).saveGameObject(file, graph);

        GameObjectAsset asset = new GameObjectAssetLoader().load(file);
        Assert.assertNotNull(asset.entities.get(0).physicsBody);
        Assert.assertEquals(1, asset.entities.get(0).physicsShapes.size);
        GameObjectAsset.PhysicsShapeData shape = asset.entities.get(0).physicsShapes.get(0);
        Assert.assertEquals(1, shape.localShapeId);
        Assert.assertEquals(2f, shape.geometry.halfWidth, 0f);
        Assert.assertEquals(.7f, shape.friction, 0f);
        Assert.assertEquals((short) 0x0004, shape.categoryBits);
        Assert.assertFalse(file.readString("UTF-8").contains("physicsShapeId"));
    }

    @Test
    public void capturePersistsAutonomousSpatialFootprintAndHeight() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        world.getMapper(PhysicsBodyComponent.class).create(root);
        PhysicsShapeData footprint = manualShape(77, 2f);
        footprint.geometry.shapeType = PhysicsGeometryData.SHAPE_CIRCLE;
        footprint.geometry.radius = 2f;
        world.getMapper(PhysicsShapesComponent.class).create(root).shapes.add(footprint);
        SpatialRepresentationComponent height = world.getMapper(SpatialRepresentationComponent.class).create(root);
        world.getMapper(games.pixscape.runtime.component.spatial.SpatialFootprintComponent.class).create(root).altitude = 4f;
        height.rectangles = new games.pixscape.runtime.spatial.ActorSpatialRectangles();
        height.rectangles.staticRectangle = new games.pixscape.runtime.spatial.ActorSpatialRectangleData();
        height.rectangles.staticRectangle.height = 6f;
        height.rectangles.staticRectangle.pivotX = 37.25f;
        world.process();

        FileHandle file = new FileHandle(temp.getRoot()).child("spatial.gameobject");
        new GameObjectAssetService(world).saveGameObject(file,
                new EntityGraphCaptureService(world).captureForGameObject(
                        new IntArray(new int[]{root})));

        GameObjectAsset.GameObjectEntityData restored = new GameObjectAssetLoader().load(file).entities.get(0);
        Assert.assertNotNull(restored.physicsShapes.get(0).geometry);
        Assert.assertEquals(4f, restored.spatialFootprint.altitude, 0f);
        Assert.assertEquals(6f, restored.spatialRepresentation.rectangles.staticRectangle.height, 0f);
        Assert.assertEquals(37.25f, restored.spatialRepresentation.rectangles.staticRectangle.pivotX, 0f);
    }

    @Test
    public void captureRejectsExternalJointWithoutChangingSource() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        int other = entity(world, 2, -1, false, 0f, 0);
        int jointEntity = world.create();
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(jointEntity);
        joint.aEid = root;
        joint.bEid = other;
        world.process();

        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureForGameObject(new IntArray(new int[]{root}));
        GameObjectAssetService service = new GameObjectAssetService(world);
        FileHandle file = new FileHandle(temp.getRoot()).child("joint.gameobject");
        Assert.assertThrows(IllegalArgumentException.class, () -> service.saveGameObject(file, graph));
        Assert.assertFalse(file.exists());
        Assert.assertEquals(root, joint.aEid);
        Assert.assertEquals(other, joint.bEid);
        Assert.assertTrue(world.getEntityManager().isActive(jointEntity));
    }

    @Test
    public void saveTopLevelGameObjectCapturesInternalJointWithoutChangingScene() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        int bodyA = entity(world, 2, 1, false, 1f, 0);
        int bodyB = entity(world, 3, 1, false, 3f, 0);
        world.getMapper(PhysicsBodyComponent.class).create(bodyA);
        world.getMapper(PhysicsBodyComponent.class).create(bodyB);
        world.getMapper(PhysicsShapesComponent.class).create(bodyA).shapes.add(manualShape(1, 1f));
        world.getMapper(PhysicsShapesComponent.class).create(bodyB).shapes.add(manualShape(2, 1f));
        int jointEntity = world.create();
        PhysicsJointComponent base = world.getMapper(PhysicsJointComponent.class).create(jointEntity);
        base.type = PhysicsJointComponent.TYPE_DISTANCE;
        base.aEid = bodyA;
        base.bEid = bodyB;
        base.anchorAx = .2f;
        base.anchorBy = .8f;
        PhysicsDistanceJointComponent distance = world.getMapper(
                PhysicsDistanceJointComponent.class).create(jointEntity);
        distance.lengthM = 2f;
        distance.frequencyHz = 3f;
        distance.dampingRatio = .5f;
        world.process();

        FileHandle file = new FileHandle(temp.getRoot()).child("jointed.gameobject");
        new GameObjectAssetService(world).saveGameObject(file,
                new EntityGraphCaptureService(world).captureForGameObject(
                        new IntArray(new int[]{root})));

        GameObjectAsset asset = new GameObjectAssetLoader().load(file);
        Assert.assertEquals(1, asset.joints.size);
        GameObjectAsset.GameObjectJointData joint = asset.joints.get(0);
        Assert.assertEquals(PhysicsJointComponent.TYPE_DISTANCE, joint.type);
        Assert.assertEquals(2, joint.bodyALocalEntityId);
        Assert.assertEquals(3, joint.bodyBLocalEntityId);
        Assert.assertEquals(2f, joint.distance.lengthM, 0f);
        Assert.assertEquals(bodyA, base.aEid);
        Assert.assertEquals(bodyB, base.bEid);
        Assert.assertTrue(world.getEntityManager().isActive(jointEntity));
    }

    @Test
    public void captureRejectsSpatialLinkedPhysicsShapeBeforeAssetWrite() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        world.getMapper(PhysicsBodyComponent.class).create(root);
        PhysicsShapeData spatialLinked = manualShape(17, 1f);
        spatialLinked.spatialBlockId = 3;
        spatialLinked.geometry = null;
        world.getMapper(PhysicsShapesComponent.class).create(root).shapes.add(spatialLinked);
        world.process();

        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureForGameObject(new IntArray(new int[]{root}));
        FileHandle file = new FileHandle(temp.getRoot()).child("spatial-linked.gameobject");
        try {
            new GameObjectAssetService(world).saveGameObject(file, graph);
            Assert.fail("Expected Spatial-linked Physics rejection.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("spatialBlockId > 0"));
        }
        Assert.assertFalse(file.exists());
    }

    @Test
    public void wrongExtensionIsRejected() throws Exception {
        World world = new World(new WorldConfiguration());
        FileHandle oldFile = new FileHandle(temp.newFile("old.pixprefab"));
        try {
            new GameObjectAssetService(world).loadGameObjectAsset(oldFile);
            Assert.fail("Expected wrong-extension rejection.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("expected .gameobject"));
        }
    }

    @Test
    public void instantiateBuildsRealHierarchyAndUndoRedoPreservesIdentities() throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 7;
        world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        int unrelated = entity(world, 50, -1, false, 1f, SortKey64.MAX_Z);
        world.getMapper(EntityIndexComponent.class).get(unrelated).layerIndex = 7;
        world.process();

        GameObjectAsset asset = hierarchyAsset();
        FileHandle file = new FileHandle(temp.newFile("ship.gameobject"));
        new GameObjectAssetLoader().save(file, asset);

        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 1000;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        HistoryManager history = new HistoryManager(16);
        IntArray selected = new IntArray();
        GameObjectAssetService service = new GameObjectAssetService(
                world, history, identities, null, entityId -> {
            selected.clear();
            if (entityId >= 0) selected.add(entityId);
        });

        EntityGraphInstantiationResult result = service.instantiateGameObject(
                file, "ship", 7, 40f, 60f);
        world.process();

        Assert.assertEquals(1, history.getCursor());
        Assert.assertEquals("Instantiate Game Object", history.peekUndoLabel());
        Assert.assertEquals(4, result.sourceToCreated().size);
        int root = result.sourceToCreated().get(100, -1);
        int sprite = result.sourceToCreated().get(200, -1);
        int nested = result.sourceToCreated().get(300, -1);
        int light = result.sourceToCreated().get(400, -1);
        Assert.assertEquals(new IntArray(new int[]{root}), selected);
        Assert.assertEquals("gameobjects/ship.gameobject",
                world.getMapper(GameObjectComponent.class).get(root).sourceAssetId);
        Assert.assertTrue(world.getMapper(GameObjectComponent.class).has(nested));
        Assert.assertEquals("", world.getMapper(GameObjectComponent.class)
                .get(nested).sourceAssetId);

        int rootStable = stable(world, root);
        int spriteStable = stable(world, sprite);
        int nestedStable = stable(world, nested);
        int lightStable = stable(world, light);
        Assert.assertTrue(rootStable >= 1000);
        Assert.assertNotEquals(100, rootStable);
        Assert.assertNotEquals(200, spriteStable);
        Assert.assertEquals(rootStable, parent(world, sprite));
        Assert.assertEquals(rootStable, parent(world, nested));
        Assert.assertEquals(nestedStable, parent(world, light));

        TransformComponent rootTransform = world.getMapper(TransformComponent.class).get(root);
        Assert.assertEquals(40f, rootTransform.x, 0f);
        Assert.assertEquals(60f, rootTransform.y, 0f);
        Assert.assertEquals(0.75f, rootTransform.rotationRad, 0f);
        Assert.assertEquals(2f, rootTransform.scaleX, 0f);
        Assert.assertEquals(3f, rootTransform.originX, 0f);
        TransformComponent spriteTransform = world.getMapper(TransformComponent.class).get(sprite);
        Assert.assertEquals(4f, spriteTransform.x, 0f);
        Assert.assertEquals(5f, spriteTransform.y, 0f);
        Assert.assertEquals(6, world.getMapper(EntityIndexComponent.class).get(sprite).zIndex);
        Assert.assertEquals(7, world.getMapper(EntityIndexComponent.class).get(root).layerIndex);
        Assert.assertEquals(7, world.getMapper(EntityIndexComponent.class).get(sprite).layerIndex);
        Assert.assertEquals(1, world.getMapper(EntityIndexComponent.class).get(root).zIndex);
        Assert.assertEquals(0, world.getMapper(EntityIndexComponent.class).get(unrelated).zIndex);

        Assert.assertTrue(world.getMapper(AssetRefComponent.class).has(sprite));
        Assert.assertTrue("Renderable Game Object children must be eligible for atlas rebind.",
                world.getMapper(TextureRegionComponent.class).has(sprite));
        Assert.assertFalse(world.getMapper(TextureRegionComponent.class).get(sprite).valid);
        Assert.assertTrue(world.getMapper(AnimationComponent.class).has(sprite));
        Assert.assertTrue(world.getMapper(PointLightComponent.class).has(light));
        Assert.assertTrue(world.getMapper(ConeLightComponent.class).has(light));
        Assert.assertEquals("enemy", world.getMapper(PixscapeTagComponent.class)
                .get(sprite).tags.first());
        PropertySet properties = world.getMapper(CustomPropertiesComponent.class)
                .get(root).properties;
        Assert.assertEquals(spriteStable, properties.getObjectStableId("target", -1));
        Assert.assertEquals(rootStable, properties.getClassValue("nested")
                .properties().getObjectStableId("owner", -1));
        Assert.assertEquals(-1, properties.getObjectStableId("none", 0));

        long rootHistory = history.historyIds().historyIdOfEntity(root);
        long spriteHistory = history.historyIds().historyIdOfEntity(sprite);
        history.undo();
        world.process();
        Assert.assertEquals(0, selected.size);
        Assert.assertFalse(world.getEntityManager().isActive(root));
        Assert.assertFalse(world.getEntityManager().isActive(sprite));
        Assert.assertFalse(world.getEntityManager().isActive(nested));
        Assert.assertFalse(world.getEntityManager().isActive(light));

        history.redo();
        world.process();
        int redoneRoot = history.historyIds().entityOfHistoryId(rootHistory);
        int redoneSprite = history.historyIds().entityOfHistoryId(spriteHistory);
        Assert.assertEquals(rootStable, stable(world, redoneRoot));
        Assert.assertEquals(spriteStable, stable(world, redoneSprite));
        Assert.assertEquals(rootStable, parent(world, redoneSprite));
        Assert.assertEquals(new IntArray(new int[]{redoneRoot}), selected);
        Assert.assertEquals(4f, world.getMapper(TransformComponent.class)
                .get(redoneSprite).x, 0f);
        Assert.assertEquals("gameobjects/ship.gameobject",
                world.getMapper(GameObjectComponent.class).get(redoneRoot).sourceAssetId);
    }

    @Test
    public void instantiatePhysicalAssetAllocatesFreshSceneIdsAndPreservesThemOnRedo()
            throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 4;
        world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        world.process();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        meta.nextPhysicsShapeId = 50;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        HistoryManager history = new HistoryManager(16);
        PhysicsService physics = new PhysicsService(world, null, meta);
        FileHandle file = new FileHandle(temp.newFile("physical.gameobject"));
        new GameObjectAssetLoader().save(file, physicalHierarchyAsset());
        GameObjectAssetService service = new GameObjectAssetService(
                world, history, identities, null, null, null, physics);

        EntityGraphInstantiationResult result = service.instantiateGameObject(
                file, "physical", 4, 10f, 20f);
        world.process();
        int root = result.sourceToCreated().get(1, -1);
        int nested = result.sourceToCreated().get(2, -1);
        Assert.assertEquals(PhysicsBodyComponent.DYNAMIC,
                world.getMapper(PhysicsBodyComponent.class).get(root).type);
        Assert.assertEquals(50, physicsShapeId(world, root));
        Assert.assertEquals(51, physicsShapeId(world, nested));
        Assert.assertTrue(world.getMapper(PhysicsCompiledFixturesComponent.class).get(root).valid);
        Assert.assertEquals(1, world.getMapper(PhysicsCompiledFixturesComponent.class)
                .get(nested).fixtures.size);
        Assert.assertEquals(52, meta.nextPhysicsShapeId);
        long rootHistoryId = history.historyIds().historyIdOfEntity(root);
        int rootShapeId = physicsShapeId(world, root);

        history.undo();
        world.process();
        history.redo();
        world.process();
        int redoneRoot = history.historyIds().entityOfHistoryId(rootHistoryId);
        Assert.assertEquals(rootShapeId, physicsShapeId(world, redoneRoot));
        Assert.assertEquals(52, meta.nextPhysicsShapeId);
    }

    @Test
    public void convertPhysicalSelectionPublishesAssetWithoutChangingSceneShapeIds()
            throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 6;
        world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        int body = entity(world, 10, -1, false, 12f, 0);
        world.getMapper(EntityIndexComponent.class).get(body).layerIndex = 6;
        world.getMapper(PhysicsBodyComponent.class).create(body).type = PhysicsBodyComponent.DYNAMIC;
        world.getMapper(PhysicsShapesComponent.class).create(body).shapes.add(manualShape(88, 2f));
        world.process();

        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        meta.nextPhysicsShapeId = 100;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        HistoryManager history = new HistoryManager(16);
        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(body);
        GameObjectAssetService service = new GameObjectAssetService(
                world, history, identities, null, null, selection,
                new PhysicsService(world, null, meta));
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("converted.gameobject");

        Assert.assertTrue(service.canConvertSelectionToGameObject(selection.getSelectionSnapshot()));
        history.execute(new ConvertSelectionToGameObjectCommand(
                world, history.historyIds(), identities, selection,
                new IntArray(new int[]{body}), new LayerLogicalOrderService(world).derive(6),
                12f, 0f, 0f, 0f, "gameobjects/converted.gameobject"));
        Assert.assertEquals(88, physicsShapeId(world, body));
        int convertedRoot = selection.getFirstSelectedEntityId();
        Assert.assertTrue(world.getMapper(GameObjectComponent.class).has(convertedRoot));
        Assert.assertEquals(0f, world.getMapper(TransformComponent.class).get(body).x, 0f);
        world.process();
        service.saveGameObject(assetFile, new EntityGraphCaptureService(world)
                .captureForGameObject(new IntArray(new int[]{convertedRoot})));
        GameObjectAsset asset = service.loadGameObjectAsset(assetFile);
        Assert.assertEquals(2, asset.entities.size);
        GameObjectAsset.GameObjectEntityData assetBody = asset.entities.get(1);
        Assert.assertNotNull(assetBody.physicsBody);
        Assert.assertEquals(1, assetBody.physicsShapes.get(0).localShapeId);
        Assert.assertEquals(2f, assetBody.physicsShapes.get(0).geometry.halfWidth, 0f);

        EntityGraphInstantiationResult copy = service.instantiateGameObject(
                assetFile, "converted", 6, 40f, 0f);
        world.process();
        int copiedBody = copy.sourceToCreated().get(assetBody.sourceEntityId, -1);
        Assert.assertEquals(100, physicsShapeId(world, copiedBody));
        Assert.assertNotEquals(physicsShapeId(world, body), physicsShapeId(world, copiedBody));
    }

    @Test
    public void repeatedWheelJointAssetDropsAndDeletesKeepExactlyTwoJoints() throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 4;
        world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        world.process();
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        meta.nextPhysicsShapeId = 50;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        HistoryManager history = new HistoryManager(32);
        GameObjectAsset asset = physicalHierarchyAsset();
        GameObjectAsset.GameObjectEntityData secondWheel = authored(3, 1, false, 6f, 0f, 0);
        secondWheel.physicsBody = new GameObjectAsset.PhysicsBodyData();
        secondWheel.physicsShapes.add(assetShape(10, 1f));
        asset.entities.add(secondWheel);
        for (int i = 0; i < 2; i++) {
            GameObjectAsset.GameObjectJointData joint = new GameObjectAsset.GameObjectJointData();
            joint.jointLocalId = i + 1;
            joint.type = PhysicsJointComponent.TYPE_WHEEL;
            joint.bodyALocalEntityId = 1;
            joint.bodyBLocalEntityId = i + 2;
            joint.wheel = new GameObjectAsset.WheelJointData();
            joint.wheel.axisY = 1f;
            asset.joints.add(joint);
        }
        FileHandle file = new FileHandle(temp.newFile("car.gameobject"));
        GameObjectAssetLoader loader = new GameObjectAssetLoader();
        loader.save(file, asset);
        GameObjectAssetService service = new GameObjectAssetService(
                world, history, identities, null, null, null, new PhysicsService(world, null, meta));

        for (int cycle = 0; cycle < 3; cycle++) {
            EntityGraphInstantiationResult dropped = service.instantiateGameObject(
                    file, "car", 4, cycle * 10f, 0f);
            world.process();
            Assert.assertEquals(2, world.getAspectSubscriptionManager()
                    .get(Aspect.all(PhysicsJointComponent.class)).getEntities().size());
            int root = dropped.sourceToCreated().get(1, -1);
            history.execute(DeleteEntitiesCommandFactory.create(world, history.historyIds(),
                    new IntArray(new int[]{root}), null));
            world.process();
            Assert.assertEquals(0, world.getAspectSubscriptionManager()
                    .get(Aspect.all(PhysicsJointComponent.class)).getEntities().size());
            Assert.assertEquals(2, loader.load(file).joints.size);
        }
    }

    @Test
    public void convertingSelectionPublishesAnOriginRootWhileKeepingScenePlacement() throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 6;
        world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        int first = entity(world, 10, -1, false, 120f, 1);
        int second = entity(world, 11, -1, false, 180f, 0);
        world.getMapper(EntityIndexComponent.class).get(first).layerIndex = 6;
        world.getMapper(EntityIndexComponent.class).get(second).layerIndex = 6;
        world.process();

        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(first);
        selection.selectAdd(second);
        GameObjectAssetService service = new GameObjectAssetService(
                world, new HistoryManager(8), identities, null, null, selection, null);
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("origin.gameobject");

        service.convertSelectionToGameObject(assetFile,
                new FileHandle(temp.getRoot()).child("origin.png"),
                "gameobjects/origin.gameobject",
                preview -> preview.writeString("preview", false, "UTF-8"));

        GameObjectAsset asset = service.loadGameObjectAsset(assetFile);
        Assert.assertEquals(0f, asset.entities.get(0).transform.x, 0f);
        Assert.assertEquals(0f, asset.entities.get(0).transform.y, 0f);
        Assert.assertEquals(0, asset.entities.get(0).entityIndex.zIndex);
        Assert.assertEquals(120f, world.getMapper(TransformComponent.class)
                .get(selection.getFirstSelectedEntityId()).x, 0f);
        Assert.assertEquals(0f, asset.entities.get(1).transform.x, 0f);
        Assert.assertEquals(60f, asset.entities.get(2).transform.x, 0f);
    }

    @Test
    public void incompleteJointedSelectionRejectsConversionBeforeFileOrIdentityMutation() throws Exception {
        World world = new World(new WorldConfiguration());
        int first = entity(world, 10, -1, false, 0f, 0);
        int second = entity(world, 11, -1, false, 2f, 1);
        world.getMapper(PhysicsBodyComponent.class).create(first);
        world.getMapper(PhysicsBodyComponent.class).create(second);
        int jointEntity = world.create();
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(jointEntity);
        joint.aEid = first;
        joint.bEid = second;
        world.process();

        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(first);
        GameObjectAssetService service = new GameObjectAssetService(
                world, new HistoryManager(8), identities, null, null, selection,
                new PhysicsService(world, null, meta));
        Assert.assertFalse(service.canConvertSelectionToGameObject(selection.getSelectionSnapshot()));
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("jointed.gameobject");
        Assert.assertThrows(IllegalArgumentException.class, () -> service.convertSelectionToGameObject(assetFile,
                new FileHandle(temp.getRoot()).child("jointed.png"), "jointed",
                preview -> preview.writeString("preview", false, "UTF-8")));
        Assert.assertFalse(assetFile.exists());
        Assert.assertEquals(100, meta.nextEntityStableId);
        Assert.assertEquals(first, joint.aEid);
        Assert.assertEquals(second, joint.bEid);
    }

    @Test
    public void classifiesSelectionOnceAndNormalizesSelectedGameObjectDescendants() {
        World world = new World(new WorldConfiguration());
        var meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        new IdentityRegistry().bind(world, meta);
        int root = entity(world, 1, -1, true, 0f, 0);
        int child = entity(world, 2, 1, false, 2f, 0);
        int nested = entity(world, 3, 1, true, 4f, 0);
        int sprite = entity(world, 4, -1, false, 8f, 1);
        world.getMapper(PhysicsBodyComponent.class).create(sprite);
        world.process();
        GameObjectAssetService service = new GameObjectAssetService(world);

        Assert.assertEquals(GameObjectAssetService.SelectionMode.CONVERT_SELECTION,
                service.classifySelection(new IntArray(new int[]{sprite})).mode());
        Assert.assertEquals(GameObjectAssetService.SelectionMode.CONVERT_SELECTION,
                service.classifySelection(new IntArray(new int[]{root, sprite})).mode());
        Assert.assertEquals(GameObjectAssetService.SelectionMode.SAVE_EXISTING_GAME_OBJECT,
                service.classifySelection(new IntArray(new int[]{root})).mode());

        GameObjectAssetService.SelectionClassification nestedSelection =
                service.classifySelection(new IntArray(new int[]{nested}));
        Assert.assertEquals(GameObjectAssetService.SelectionMode.UNAVAILABLE, nestedSelection.mode());
        Assert.assertEquals("Save as Game Object Asset…", nestedSelection.actionLabel());
        Assert.assertTrue(nestedSelection.rejection().contains("Nested Game Objects"));

        GameObjectAssetService.SelectionClassification normalized =
                service.classifySelection(new IntArray(new int[]{root, child}));
        Assert.assertEquals(GameObjectAssetService.SelectionMode.SAVE_EXISTING_GAME_OBJECT,
                normalized.mode());
        Assert.assertEquals(1, normalized.effectiveSelection().size);
        Assert.assertEquals(root, normalized.effectiveSelection().first());
    }

    @Test
    public void classifiesMultipleGameObjectRootsAsConversion() {
        World world = new World(new WorldConfiguration());
        var meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        new IdentityRegistry().bind(world, meta);
        int first = entity(world, 1, -1, true, 0f, 0);
        int second = entity(world, 2, -1, true, 4f, 1);
        int firstChild = entity(world, 3, 1, false, 1f, 0);
        world.process();
        GameObjectAssetService service = new GameObjectAssetService(world);

        Assert.assertEquals(GameObjectAssetService.SelectionMode.CONVERT_SELECTION,
                service.classifySelection(new IntArray(new int[]{first, second})).mode());
        GameObjectAssetService.SelectionClassification normalized = service.classifySelection(
                new IntArray(new int[]{first, firstChild, second}));
        Assert.assertEquals(GameObjectAssetService.SelectionMode.CONVERT_SELECTION, normalized.mode());
        Assert.assertEquals(2, normalized.effectiveSelection().size);
    }

    @Test
    public void saveExistingGameObjectPublishesWithoutChangingSceneOrHistory() throws Exception {
        World world = new World(new WorldConfiguration());
        int layer = world.create();
        world.getMapper(LayerComponent.class).create(layer).layerIndex = 6;
        world.getMapper(LayerMetaComponent.class).create(layer).locked = false;
        int root = entity(world, 11, -1, true, 10f, 4);
        int member = entity(world, 12, 11, false, 3f, 2);
        world.getMapper(EntityIndexComponent.class).get(root).layerIndex = 6;
        world.getMapper(EntityIndexComponent.class).get(member).layerIndex = 6;
        world.getMapper(GameObjectComponent.class).get(root).sourceAssetId =
                "gameobjects/enemy.gameobject";
        world.getMapper(PhysicsBodyComponent.class).create(member);
        world.getMapper(PhysicsShapesComponent.class).create(member).shapes.add(manualShape(77, 2f));
        world.process();

        HistoryManager history = new HistoryManager(8);
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.nextEntityStableId = 100;
        meta.nextPhysicsShapeId = 100;
        IdentityRegistry identities = new IdentityRegistry();
        identities.bind(world, meta);
        identities.rebuild();
        long rootHistoryId = history.historyIds().ensureForEntity(root);
        long memberHistoryId = history.historyIds().ensureForEntity(member);
        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(root);
        GameObjectAssetService service = new GameObjectAssetService(
                world, history, identities, null, null, selection,
                new PhysicsService(world, null, meta));
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("enemy-variant.gameobject");
        FileHandle previewFile = new FileHandle(temp.getRoot()).child("enemy-variant.png");

        service.saveExistingGameObjectAsAsset(assetFile, previewFile,
                "gameobjects/enemy-variant.gameobject",
                preview -> preview.writeString("preview", false, "UTF-8"));

        Assert.assertTrue(assetFile.exists());
        Assert.assertTrue(previewFile.exists());
        GameObjectAsset asset = service.loadGameObjectAsset(assetFile);
        Assert.assertEquals(GameObjectAsset.SCHEMA_VERSION, asset.schemaVersion);
        Assert.assertEquals(0f, asset.entities.get(0).transform.x, 0f);
        Assert.assertEquals(0f, asset.entities.get(0).transform.y, 0f);
        Assert.assertEquals(3f, asset.entities.get(1).transform.x, 0f);
        Assert.assertEquals(1, asset.entities.get(1).physicsShapes.get(0).localShapeId);
        Assert.assertEquals(77, physicsShapeId(world, member));
        Assert.assertEquals(11, stable(world, root));
        Assert.assertEquals(12, stable(world, member));
        Assert.assertEquals(11, parent(world, member));
        Assert.assertEquals(10f, world.getMapper(TransformComponent.class).get(root).x, 0f);
        Assert.assertEquals(3f, world.getMapper(TransformComponent.class).get(member).x, 0f);
        Assert.assertEquals(6, world.getMapper(EntityIndexComponent.class).get(root).layerIndex);
        Assert.assertEquals(6, world.getMapper(EntityIndexComponent.class).get(member).layerIndex);
        Assert.assertEquals(4, world.getMapper(EntityIndexComponent.class).get(root).zIndex);
        Assert.assertEquals(2, world.getMapper(EntityIndexComponent.class).get(member).zIndex);
        Assert.assertEquals("gameobjects/enemy.gameobject",
                world.getMapper(GameObjectComponent.class).get(root).sourceAssetId);
        Assert.assertEquals(rootHistoryId, history.historyIds().historyIdOfEntity(root));
        Assert.assertEquals(memberHistoryId, history.historyIds().historyIdOfEntity(member));
        Assert.assertFalse(history.canUndo());
        Assert.assertEquals(root, selection.getFirstSelectedEntityId());

        EntityGraphInstantiationResult dropped = service.instantiateGameObject(
                assetFile, "enemy-variant", 6, 30f, 0f);
        world.process();
        int droppedBody = dropped.sourceToCreated().get(asset.entities.get(1).sourceEntityId, -1);
        Assert.assertEquals(100, physicsShapeId(world, droppedBody));
        Assert.assertNotEquals(77, physicsShapeId(world, droppedBody));
    }

    @Test
    public void saveExistingGameObjectKeepsEmptyProvenanceEmpty() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        world.process();
        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(root);
        GameObjectAssetService service = new GameObjectAssetService(
                world, null, null, null, null, selection, null);

        service.saveExistingGameObjectAsAsset(
                new FileHandle(temp.getRoot()).child("manual.gameobject"),
                new FileHandle(temp.getRoot()).child("manual.png"),
                "gameobjects/manual.gameobject",
                preview -> preview.writeString("preview", false, "UTF-8"));

        Assert.assertEquals("", world.getMapper(GameObjectComponent.class).get(root).sourceAssetId);
    }

    @Test
    public void jointedExistingGameObjectRejectsIncompleteAssetPublication() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        int member = entity(world, 2, 1, false, 1f, 0);
        int external = entity(world, 3, -1, false, 2f, 1);
        world.getMapper(PhysicsBodyComponent.class).create(member);
        world.getMapper(PhysicsBodyComponent.class).create(external);
        PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(world.create());
        joint.aEid = member;
        joint.bEid = external;
        world.process();

        GameObjectAssetService.SelectionClassification classification =
                new GameObjectAssetService(world).classifySelection(new IntArray(new int[]{root}));
        Assert.assertEquals(GameObjectAssetService.SelectionMode.UNAVAILABLE, classification.mode());
        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(root);
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("jointed-go.gameobject");
        GameObjectAssetService service = new GameObjectAssetService(world, null, null, null, null, selection, null);
        Assert.assertThrows(IllegalArgumentException.class, () -> service.saveExistingGameObjectAsAsset(assetFile,
                new FileHandle(temp.getRoot()).child("jointed-go.png"), "gameobjects/jointed-go.gameobject",
                preview -> preview.writeString("preview", false, "UTF-8")));
        Assert.assertFalse(assetFile.exists());
        Assert.assertEquals(member, joint.aEid);
        Assert.assertEquals(external, joint.bEid);
        Assert.assertEquals(1, parent(world, member));
    }

    private static GameObjectAsset hierarchyAsset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 100;
        GameObjectAsset.GameObjectEntityData root = authored(100, -1, true, 0f, 0f, 0);
        root.transform.rotationRad = 0.75f;
        root.transform.scaleX = 2f;
        root.transform.scaleY = 2f;
        root.transform.originX = 3f;
        root.transform.originY = 9f;
        root.customProperties = new PropertySet()
                .putObjectStableId("target", 200)
                .putObjectStableId("none", -1)
                .putClass("nested", "Link", new PropertySet()
                        .putObjectStableId("owner", 100));
        GameObjectAsset.GameObjectEntityData sprite = authored(200, 100, false, 4f, 5f, 6);
        sprite.assetRef = new GameObjectAsset.AssetRefData();
        sprite.assetRef.assetId = 42;
        sprite.assetRef.atlasTag = "main";
        sprite.animation = new GameObjectAsset.AnimationData();
        sprite.animation.animationAssetIds.add(9);
        sprite.tags = new GameObjectAsset.TagsData();
        sprite.tags.values.add("enemy");
        GameObjectAsset.GameObjectEntityData nested = authored(300, 100, true, 7f, 8f, 3);
        GameObjectAsset.GameObjectEntityData light = authored(400, 300, false, 1f, 2f, 4);
        light.pointLight = new GameObjectAsset.PointLightData();
        light.pointLight.enabled = true;
        light.coneLight = new GameObjectAsset.ConeLightData();
        light.coneLight.enabled = true;
        asset.entities.add(root);
        asset.entities.add(sprite);
        asset.entities.add(nested);
        asset.entities.add(light);
        return asset;
    }

    private static GameObjectAsset.GameObjectEntityData authored(
            int sourceId, int parentId, boolean gameObject, float x, float y, int z) {
        GameObjectAsset.GameObjectEntityData data = new GameObjectAsset.GameObjectEntityData();
        data.sourceEntityId = sourceId;
        data.parentSourceEntityId = parentId;
        data.transform = new GameObjectAsset.TransformData();
        data.transform.x = x;
        data.transform.y = y;
        data.transform.scaleX = 1f;
        data.transform.scaleY = 1f;
        data.entityIndex = new GameObjectAsset.EntityIndexData();
        data.entityIndex.zIndex = z;
        data.identity = new GameObjectAsset.IdentityData();
        data.identity.name = "Entity " + sourceId;
        if (gameObject) data.gameObject = new GameObjectAsset.GameObjectData();
        return data;
    }

    private static GameObjectAsset physicalHierarchyAsset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 1;
        GameObjectAsset.GameObjectEntityData root = authored(1, -1, true, 0f, 0f, 0);
        root.physicsBody = new GameObjectAsset.PhysicsBodyData();
        root.physicsBody.type = PhysicsBodyComponent.DYNAMIC;
        root.physicsShapes.add(assetShape(7, 2f));
        GameObjectAsset.GameObjectEntityData nested = authored(2, 1, true, 3f, 4f, 0);
        nested.physicsBody = new GameObjectAsset.PhysicsBodyData();
        nested.physicsBody.type = PhysicsBodyComponent.KINEMATIC;
        nested.physicsShapes.add(assetShape(9, 3f));
        asset.entities.add(root);
        asset.entities.add(nested);
        return asset;
    }

    private static GameObjectAsset.PhysicsShapeData assetShape(int localShapeId, float halfWidth) {
        GameObjectAsset.PhysicsShapeData shape = new GameObjectAsset.PhysicsShapeData();
        shape.localShapeId = localShapeId;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        shape.geometry.halfWidth = halfWidth;
        shape.geometry.halfHeight = 1f;
        return shape;
    }

    private static PhysicsShapeData manualShape(int sceneShapeId, float halfWidth) {
        PhysicsShapeData shape = new PhysicsShapeData();
        shape.physicsShapeId = sceneShapeId;
        shape.geometry = new PhysicsGeometryData();
        shape.geometry.shapeType = PhysicsGeometryData.SHAPE_BOX;
        shape.geometry.halfWidth = halfWidth;
        shape.geometry.halfHeight = 1f;
        return shape;
    }

    private static int physicsShapeId(World world, int entityId) {
        return world.getMapper(PhysicsShapesComponent.class).get(entityId)
                .shapes.first().physicsShapeId;
    }

    private static int stable(World world, int entityId) {
        return world.getMapper(PixscapeIdentityComponent.class).get(entityId).stableId;
    }

    private static int parent(World world, int entityId) {
        return world.getMapper(GameObjectMemberComponent.class).get(entityId).parentStableId;
    }

    private static int entity(
            World world, int stableId, int parentStableId,
            boolean gameObject, float x, int z) {
        int entity = world.create();
        TransformComponent transform = world.getMapper(TransformComponent.class).create(entity);
        transform.x = x;
        transform.scaleX = 1f;
        transform.scaleY = 1f;
        world.getMapper(EntityIndexComponent.class).create(entity).zIndex = z;
        world.getMapper(PixscapeIdentityComponent.class).create(entity).stableId = stableId;
        if (gameObject) world.getMapper(GameObjectComponent.class).create(entity);
        if (parentStableId > 0) {
            world.getMapper(GameObjectMemberComponent.class).create(entity)
                    .parentStableId = parentStableId;
        }
        return entity;
    }
}
