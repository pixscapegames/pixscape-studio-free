package games.pixscape.studio.persistence;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.SpatialBlockData;
import games.pixscape.runtime.component.SpatialBlockOrientation;
import games.pixscape.runtime.component.SpatialBlocksComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.studio.service.SceneService;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class SpatialBlockScenePersistenceTest {
    @Test
    public void spatialBlockFootprintSurvivesSceneSaveLoadRoundtripWithoutAnchors() {
        World world = worldWithSerialization();
        int layerId = world.create();
        SpatialBlocksComponent spatialBlocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = new SpatialBlockData();
        block.id = 1;
        block.name = "Tall wall";
        block.enabled = true;
        block.x = 2f;
        block.y = 3f;
        block.width = 2f;
        block.depth = 2f;
        block.altitude = 155f;
        block.height = 16f;
        block.orientation = SpatialBlockOrientation.TILE_CELL;
        block.actorOccluder = true;
        block.beginAuthoredLinkedTileRefs();
        block.addLinkedTileRef(2, 3, 101);
        block.addLinkedTileRef(3, 3, 102);
        block.addLinkedTileRef(2, 4, 103);
        block.addLinkedTileRef(3, 4, 104);
        spatialBlocks.blocks.add(block);
        world.process();

        FileHandle file = tempSceneFile("spatial-block-footprint-roundtrip");
        SceneService.saveScene(world, file, false);

        String json = file.readString("UTF-8");
        Assert.assertFalse(json.contains("\"" + legacyAnchorField("Gx") + "\""));
        Assert.assertFalse(json.contains("\"" + legacyAnchorField("Gy") + "\""));

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, file, false);
        loaded.process();

        SpatialBlockData restored = restoredBlock(loaded);
        Assert.assertEquals(2f, restored.x, 0.0001f);
        Assert.assertEquals(3f, restored.y, 0.0001f);
        Assert.assertEquals(2f, restored.width, 0.0001f);
        Assert.assertEquals(2f, restored.depth, 0.0001f);
        Assert.assertEquals(155f, restored.altitude, 0.0001f);
        Assert.assertEquals(16f, restored.height, 0.0001f);
        Assert.assertTrue(restored.linkedTileRefsAuthored);
        Assert.assertEquals(4, restored.linkedTileRefs.size);
        Assert.assertEquals(2, restored.linkedTileRefs.get(0).gx);
        Assert.assertEquals(3, restored.linkedTileRefs.get(0).gy);
        Assert.assertEquals(101, restored.linkedTileRefs.get(0).tileAssetId);
        Assert.assertEquals(3, restored.linkedTileRefs.get(3).gx);
        Assert.assertEquals(4, restored.linkedTileRefs.get(3).gy);
        Assert.assertEquals(104, restored.linkedTileRefs.get(3).tileAssetId);
    }

    private static SpatialBlockData restoredBlock(World world) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class))
                .getEntities();
        Assert.assertEquals(1, entities.size());
        SpatialBlocksComponent component = world.getMapper(SpatialBlocksComponent.class).get(entities.get(0));
        Assert.assertEquals(1, component.blocks.size);
        return component.blocks.first();
    }

    private static World worldWithSerialization() {
        return new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
    }

    private static FileHandle tempSceneFile(String name) {
        File dir = new File(System.getProperty("java.io.tmpdir"), "pixscape-studio-tests");
        Assert.assertTrue(dir.exists() || dir.mkdirs());
        File file = new File(dir, name + ".json");
        if (file.exists()) {
            Assert.assertTrue(file.delete());
        }
        return new FileHandle(file);
    }

    private static String legacyAnchorField(String axis) {
        return "anchor" + axis;
    }
}
