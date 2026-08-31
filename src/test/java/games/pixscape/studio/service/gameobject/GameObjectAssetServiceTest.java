package games.pixscape.studio.service.gameobject;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.CustomPropertiesComponent;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.GameObjectComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.PixscapeTagComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.gameobject.GameObjectAsset;
import games.pixscape.studio.service.entitygraph.EntityGraph;
import games.pixscape.studio.service.entitygraph.EntityGraphCaptureService;
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
