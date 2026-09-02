package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.LayerComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.TextureRegionComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.PhysicsGeometryData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.ConvertSelectionToGameObjectCommand;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
import games.pixscape.studio.service.SelectionService;
import games.pixscape.studio.service.zorder.LayerLogicalOrderService;
import org.junit.Assert;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

public class GameObjectAssetServiceTest {
    @Rule public TemporaryFolder temp = new TemporaryFolder();

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
        Assert.assertEquals(3, asset.entities.size());
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
        Assert.assertEquals(1, asset.entities.get(0).physicsShapes.size());
        GameObjectAsset.PhysicsShapeData shape = asset.entities.get(0).physicsShapes.get(0);
        Assert.assertEquals(1, shape.localShapeId);
        Assert.assertEquals(2f, shape.geometry.halfWidth, 0f);
        Assert.assertEquals(.7f, shape.friction, 0f);
        Assert.assertEquals((short) 0x0004, shape.categoryBits);
        Assert.assertFalse(file.readString("UTF-8").contains("physicsShapeId"));
    }

    @Test
    public void captureRejectsPhysicsJointEndpointOutsideTheHierarchy() throws Exception {
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
        try {
            new GameObjectAssetService(world).saveGameObject(
                    new FileHandle(temp.getRoot()).child("joint.gameobject"), graph);
            Assert.fail("Expected Physics joint endpoint rejection.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Physics joints in Game Object assets"));
        }
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
            Assert.assertTrue(expected.getMessage().contains("Spatial-linked Physics shapes"));
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
        Assert.assertEquals(2, asset.entities.size());
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
    public void jointedPhysicalSelectionIsUnavailableBeforeAnyAssetIsWritten() throws Exception {
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
        Assert.assertTrue(service.conversionRejection(selection.getSelectionSnapshot())
                .contains("Physics joints in Game Object assets"));
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("jointed.gameobject");
        try {
            service.convertSelectionToGameObject(
                    assetFile, new FileHandle(temp.getRoot()).child("jointed.png"), "jointed");
            Assert.fail("Expected joint-specific conversion rejection.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Physics joints in Game Object assets"));
        }
        Assert.assertFalse(assetFile.exists());
        Assert.assertFalse(world.getMapper(GameObjectMemberComponent.class).has(first));
        Assert.assertEquals(first, joint.aEid);
        Assert.assertEquals(second, joint.bEid);
    }

    @Test
    public void classifiesSelectionOnceAndNormalizesSelectedGameObjectDescendants() {
        World world = new World(new WorldConfiguration());
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
    public void jointedExistingGameObjectIsUnavailableWithTheJointSpecificReason() throws Exception {
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
        Assert.assertEquals("Save as Game Object Asset…", classification.actionLabel());
        Assert.assertTrue(classification.rejection().contains("Physics joints in Game Object assets"));

        SelectionService selection = new SelectionService(world, null);
        selection.selectOnly(root);
        FileHandle assetFile = new FileHandle(temp.getRoot()).child("jointed-go.gameobject");
        try {
            new GameObjectAssetService(world, null, null, null, null, selection, null)
                    .saveExistingGameObjectAsAsset(assetFile,
                            new FileHandle(temp.getRoot()).child("jointed-go.png"),
                            "gameobjects/jointed-go.gameobject",
                            preview -> preview.writeString("preview", false, "UTF-8"));
            Assert.fail("Expected joint-specific Game Object save rejection.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("Physics joints in Game Object assets"));
        }
        Assert.assertFalse(assetFile.exists());
        Assert.assertEquals(1, parent(world, member));
    }

    private static GameObjectAsset hierarchyAsset() {
        GameObjectAsset asset = new GameObjectAsset();
        asset.rootSourceEntityId = 100;
        GameObjectAsset.GameObjectEntityData root = authored(100, -1, true, 10f, 20f, 2);
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
