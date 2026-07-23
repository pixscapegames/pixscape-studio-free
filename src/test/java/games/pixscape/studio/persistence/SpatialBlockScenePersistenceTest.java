package games.pixscape.studio.persistence;

import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.component.physics.FixtureDefData;
import games.pixscape.runtime.component.physics.PhysicsFixturesComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.spatial.CompiledSpatialStructure;
import games.pixscape.runtime.spatial.SpatialStructureCompiler;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.spatial.SpatialTileSelectionService;
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
        block.x = 2f;
        block.y = 3f;
        block.width = 2f;
        block.depth = 2f;
        block.altitude = 155f;
        block.height = 16f;
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
        Assert.assertFalse(json.contains("\"enabled\""));
        Assert.assertFalse(json.contains("\"orientation\""));

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

    @Test
    public void normalizedSelectionRoundtripNeverRestoresRawEmptyBorders() {
        TiledMapLayerData map = new TiledMapLayerData(
                8, 8, 64, 32, 4, SceneMetaRuntime.TiledProjection.ISO);
        map.setTile(2, 3, 201);
        map.setTile(3, 3, 202);
        map.setTile(4, 3, 203);
        SpatialTileSelectionService selection = new SpatialTileSelectionService();
        selection.beginDrag(7, 0, 1);
        selection.updateDrag(7, 5);
        selection.finishDrag();
        SpatialBlockData normalized = selection.toSpatialBlockData(map, 0f, 10f);
        Assert.assertNotNull(normalized);
        normalized.id = 1;
        normalized.structureId = 1;
        com.badlogic.gdx.utils.Array<SpatialBlockData> authored = new com.badlogic.gdx.utils.Array<>(SpatialBlockData[]::new);
        authored.add(normalized);
        String compiledBefore = compiledSignature(SpatialStructureCompiler.compile(authored, 1));

        World world = worldWithSerialization();
        int layerId = world.create();
        SpatialBlocksComponent authoredComponent = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        authoredComponent.blocks.add(normalized);
        authoredComponent.revision = 7;
        world.process();
        FileHandle file = tempSceneFile("normalized-spatial-selection-roundtrip");
        SceneService.saveScene(world, file, false);
        String json = file.readString("UTF-8");
        Assert.assertFalse(json.contains("CompiledSpatialStructure"));
        Assert.assertFalse(json.contains("segmentCount"));
        Assert.assertFalse(json.contains("revision"));

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, file, false);
        loaded.process();
        SpatialBlockData restored = restoredBlock(loaded);
        Assert.assertEquals(0, loaded.getMapper(SpatialBlocksComponent.class)
                .get(loaded.getAspectSubscriptionManager().get(Aspect.all(SpatialBlocksComponent.class))
                        .getEntities().get(0)).revision);

        Assert.assertEquals(2f, restored.x, 0f);
        Assert.assertEquals(3f, restored.y, 0f);
        Assert.assertEquals(3f, restored.width, 0f);
        Assert.assertEquals(1f, restored.depth, 0f);
        Assert.assertEquals(3, restored.linkedTileRefs.size);
        for (int i = 0; i < 3; i++) {
            Assert.assertEquals(2 + i, restored.linkedTileRefs.get(i).gx);
            Assert.assertEquals(3, restored.linkedTileRefs.get(i).gy);
            Assert.assertEquals(201 + i, restored.linkedTileRefs.get(i).tileAssetId);
        }
        com.badlogic.gdx.utils.Array<SpatialBlockData> restoredWalls =
                new com.badlogic.gdx.utils.Array<>(SpatialBlockData[]::new);
        restoredWalls.add(restored);
        Assert.assertEquals(compiledBefore,
                compiledSignature(SpatialStructureCompiler.compile(restoredWalls, restored.structureId)));
    }

    @Test
    public void disabledSpatialCollisionAndDeletedGeneratedFixtureSurviveRoundtrip() {
        World world = worldWithSerialization();
        int layerId = world.create();
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = new SpatialBlockData();
        block.id = 21;
        block.structureId = 21;
        block.x = 1f;
        block.y = 1f;
        block.width = 1f;
        block.depth = 1f;
        block.physicsCollision = false;
        blocks.blocks.add(block);
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).create(layerId);
        FixtureDefData custom = new FixtureDefData();
        custom.fixtureId = 77;
        custom.shapeType = FixtureDefData.SHAPE_CIRCLE;
        custom.radius = 2.5f;
        fixtures.fixtures.add(custom);
        world.process();

        FileHandle file = tempSceneFile("spatial-collision-disabled-roundtrip");
        SceneService.saveScene(world, file, false);

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, file, false);
        loaded.process();
        IntBag entities = loaded.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class, PhysicsFixturesComponent.class))
                .getEntities();
        Assert.assertEquals(1, entities.size());
        int restoredLayer = entities.get(0);
        SpatialBlockData restored = loaded.getMapper(SpatialBlocksComponent.class)
                .get(restoredLayer).blocks.first();
        PhysicsFixturesComponent restoredFixtures = loaded.getMapper(PhysicsFixturesComponent.class)
                .get(restoredLayer);
        Assert.assertFalse(restored.physicsCollision);
        Assert.assertEquals(1, restoredFixtures.fixtures.size);
        Assert.assertEquals(77, restoredFixtures.fixtures.first().fixtureId);
        Assert.assertEquals(2.5f, restoredFixtures.fixtures.first().radius, 0f);
        Assert.assertNotEquals(1_000_021, restoredFixtures.fixtures.first().fixtureId);
    }

    @Test
    public void spatialOwnedFixtureSensorAndMaterialPropertiesSurviveRoundtrip() {
        World world = worldWithSerialization();
        int layerId = world.create();
        SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(layerId);
        SpatialBlockData block = new SpatialBlockData();
        block.id = 22;
        block.structureId = 22;
        block.physicsCollision = true;
        blocks.blocks.add(block);
        PhysicsFixturesComponent fixtures =
                world.getMapper(PhysicsFixturesComponent.class).create(layerId);
        FixtureDefData owned = new FixtureDefData();
        owned.fixtureId = 1_000_022;
        owned.shapeType = FixtureDefData.SHAPE_BOX;
        owned.halfW = 0.5f;
        owned.halfH = 0.75f;
        owned.isSensor = true;
        owned.friction = 0.65f;
        fixtures.fixtures.add(owned);
        world.process();

        FileHandle file = tempSceneFile("spatial-owned-properties-roundtrip");
        SceneService.saveScene(world, file, false);

        World loaded = worldWithSerialization();
        SceneLoader.loadScene(loaded, file, false);
        loaded.process();
        IntBag entities = loaded.getAspectSubscriptionManager()
                .get(Aspect.all(SpatialBlocksComponent.class, PhysicsFixturesComponent.class))
                .getEntities();
        Assert.assertEquals(1, entities.size());
        int restoredLayer = entities.get(0);
        SpatialBlockData restoredBlock = loaded.getMapper(SpatialBlocksComponent.class)
                .get(restoredLayer).blocks.first();
        FixtureDefData restoredFixture = loaded.getMapper(PhysicsFixturesComponent.class)
                .get(restoredLayer).fixtures.first();
        Assert.assertTrue(restoredBlock.physicsCollision);
        Assert.assertEquals(1_000_022, restoredFixture.fixtureId);
        Assert.assertTrue(restoredFixture.isSensor);
        Assert.assertEquals(0.65f, restoredFixture.friction, 0f);
    }

    private static String compiledSignature(CompiledSpatialStructure structure) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < structure.segmentCount(); i++) {
            out.append(structure.startX(i)).append(',').append(structure.startY(i)).append('-')
                    .append(structure.endX(i)).append(',').append(structure.endY(i)).append('/')
                    .append(structure.normalX(i)).append(',').append(structure.normalY(i)).append(';');
        }
        return out.toString();
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
