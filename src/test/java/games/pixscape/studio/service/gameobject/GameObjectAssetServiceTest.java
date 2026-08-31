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
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.runtime.gameobject.GameObjectAssetLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.property.PropertySet;
import games.pixscape.runtime.render.SortKey64;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.component.LayerMetaComponent;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
import games.pixscape.studio.service.entitygraph.EntityGraphInstantiationResult;
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
        Assert.assertEquals(1, asset.schemaVersion);
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
    public void unsupportedPhysicsIsRejectedBeforeWrite() throws Exception {
        World world = new World(new WorldConfiguration());
        int root = entity(world, 1, -1, true, 0f, 0);
        world.getMapper(PhysicsBodyComponent.class).create(root);
        world.process();
        EntityGraph graph = new EntityGraphCaptureService(world)
                .captureForGameObject(new IntArray(new int[]{root}));
        FileHandle file = new FileHandle(temp.getRoot()).child("physics.gameobject");
        try {
            new GameObjectAssetService(world).saveGameObject(file, graph);
            Assert.fail("Expected unsupported Physics rejection.");
        } catch (IllegalArgumentException expected) {
            Assert.assertTrue(expected.getMessage().contains("unsupported component domain Physics"));
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
