package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneSaveTestSupport;
import org.junit.Assert;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

public class RuntimeExportBlockPhysicsBindingTest {
    @Test
    public void validLinkedSceneIsTransportedWithoutDerivedCachesOrNativePhysics() throws Exception {
        Fixture fixture = new Fixture();
        fixture.writeLinkedScene(1);

        RuntimeExport.exportRuntime(fixture.config, fixture.studioDir, fixture.userDir);

        FileHandle exported = fixture.runtimeSceneFile();
        String json = exported.readString("UTF-8");
        Assert.assertTrue(json.contains("SpatialBlocksComponent"));
        Assert.assertTrue(json.contains("PhysicsShapesComponent"));
        Assert.assertTrue(json.contains("BlockPhysicsBindingsComponent"));
        Assert.assertFalse(json.contains("PhysicsCompiledFixturesComponent"));
        Assert.assertFalse(json.contains("SpatialPhysicsFootprintComponent"));
        Assert.assertFalse(json.contains("PhysicsRuntimeBodyComponent"));
    }

    @Test
    public void orphanLinkedShapeRejectsBeforeReplacingExistingRuntimeExport() throws Exception {
        Fixture fixture = new Fixture();
        fixture.writeLinkedScene(1);
        fixture.sceneFile().writeString(
                fixture.sceneFile().readString("UTF-8")
                        .replaceFirst(",?\\\"BlockPhysicsBindingsComponent\\\":\\{[^}]*\\}", ""),
                false,
                "UTF-8");
        fixture.writeSentinel();

        Assert.assertThrows(RuntimeException.class,
                () -> RuntimeExport.exportRuntime(fixture.config, fixture.studioDir, fixture.userDir));
        Assert.assertEquals("keep", fixture.sentinel().readString("UTF-8"));
    }

    @Test
    public void danglingBindingRejectsBeforeReplacingExistingRuntimeExport() throws Exception {
        Fixture fixture = new Fixture();
        fixture.writeLinkedScene(1);
        String json = fixture.sceneFile().readString("UTF-8");
        int bindingsStart = json.indexOf("\"BlockPhysicsBindingsComponent\"");
        Assert.assertTrue(bindingsStart >= 0);
        String prefix = json.substring(0, bindingsStart);
        String bindingTail = json.substring(bindingsStart);
        String dangling = prefix + bindingTail.replaceFirst(
                "\\\"physicsShapeId\\\"\\s*:\\s*1", "\"physicsShapeId\":2");
        Assert.assertNotEquals(json, dangling);
        fixture.sceneFile().writeString(
                dangling,
                false,
                "UTF-8");
        fixture.writeSentinel();

        Assert.assertThrows(RuntimeException.class,
                () -> RuntimeExport.exportRuntime(fixture.config, fixture.studioDir, fixture.userDir));
        Assert.assertEquals("keep", fixture.sentinel().readString("UTF-8"));
    }

    private static final class Fixture {
        final Path studioPath;
        final Path userPath;
        final FileHandle studioDir;
        final FileHandle userDir;
        final ProjectConfig config = new ProjectConfig();

        Fixture() throws Exception {
            studioPath = Files.createTempDirectory("pixscape-export-linked-studio");
            userPath = Files.createTempDirectory("pixscape-export-linked-user");
            studioDir = new FileHandle(studioPath.toFile());
            userDir = new FileHandle(userPath.toFile());
            config.projectTitle = "Linked export";
            config.projectFileName = "linked-export";
            config.exportRootPathDir = userPath.toString();
            config.createSceneMeta("Main");
            config.getCurrentSceneMeta().physicsEnabled = true;
            config.getCurrentSceneMeta().nextEntityStableId = 2;
            config.getCurrentSceneMeta().nextPhysicsShapeId = 2;
            studioDir.child(StudioFs.DIR_SCENES).mkdirs();
            new AssetMetaDatabase().save(studioDir.child(StudioFs.FILE_ASSETS_JSON));
        }

        void writeLinkedScene(int bindingShapeId) {
            World world = new World(new WorldConfiguration()
                    .setSystem(new WorldSerializationManager()));
            try {
                int owner = world.create();
                world.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
                SpatialBlocksComponent blocks = world.getMapper(SpatialBlocksComponent.class).create(owner);
                blocks.nextSpatialBlockId = 2;
                SpatialBlockData block = new SpatialBlockData();
                block.id = 1;
                block.structureId = 1;
                block.width = 1f;
                block.depth = 1f;
                blocks.blocks.add(block);
                PhysicsShapeData linked = new PhysicsShapeData();
                linked.physicsShapeId = 1;
                linked.enabled = true;
                world.getMapper(PhysicsShapesComponent.class).create(owner).shapes.add(linked);
                BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
                binding.spatialBlockId = 1;
                binding.physicsShapeId = bindingShapeId;
                world.getMapper(BlockPhysicsBindingsComponent.class).create(owner).bindings.add(binding);
                world.getMapper(PhysicsCompiledFixturesComponent.class).create(owner);
                world.getMapper(SpatialPhysicsFootprintComponent.class).create(owner);
                world.process();
                SceneSaveTestSupport.save(world, sceneFile(), config.getCurrentSceneMeta());
            } finally {
                world.dispose();
            }
        }

        FileHandle sceneFile() {
            return studioDir.child(StudioFs.DIR_SCENES)
                    .child(config.getCurrentSceneMeta().getFile());
        }

        FileHandle runtimeSceneFile() {
            return userDir.child(RuntimeExport.RUNTIME_DIR_NAME)
                    .child("scenes")
                    .child(config.getCurrentSceneMeta().getFile());
        }

        void writeSentinel() {
            FileHandle runtimeDir = userDir.child(RuntimeExport.RUNTIME_DIR_NAME);
            runtimeDir.mkdirs();
            sentinel().writeString("keep", false, StandardCharsets.UTF_8.name());
        }

        FileHandle sentinel() {
            return userDir.child(RuntimeExport.RUNTIME_DIR_NAME).child("sentinel.txt");
        }
    }
}
