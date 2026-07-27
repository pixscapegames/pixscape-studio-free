package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.Aspect;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.PixscapeIdentityComponent;
import games.pixscape.runtime.component.physics.PhysicsShapesComponent;
import games.pixscape.runtime.component.spatial.BlockPhysicsBindingsComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.component.spatial.SpatialPhysicsFootprintComponent;
import games.pixscape.runtime.component.physics.PhysicsCompiledFixturesComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.physics.BlockPhysicsBindingData;
import games.pixscape.runtime.physics.PhysicsShapeData;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.service.BlockPhysicsBindingRepository;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.configuration.ProjectConfig;
import org.junit.Assert;
import org.junit.Test;

import java.io.File;

public class SceneServiceBlockPhysicsBindingSaveTest {
    @Test
    public void linkedBindingSavesStructurallyWithoutDerivedPhysicsCaches() {
        World world = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        SceneMetaRuntime meta = new SceneMetaRuntime();
        meta.physicsEnabled = true;
        meta.nextEntityStableId = 2;
        meta.nextPhysicsShapeId = 2;

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
        binding.physicsShapeId = 1;
        world.getMapper(BlockPhysicsBindingsComponent.class).create(owner).bindings.add(binding);
        world.getMapper(PhysicsCompiledFixturesComponent.class).create(owner);
        world.getMapper(SpatialPhysicsFootprintComponent.class).create(owner);
        world.process();

        FileHandle file = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"),
                "pixscape-linked-binding-save-" + System.nanoTime() + ".json"));
        SceneSaveTestSupport.save(world, file, meta);

        String json = file.readString("UTF-8");
        Assert.assertTrue(json.contains("SpatialBlocksComponent"));
        Assert.assertTrue(json.contains("PhysicsShapesComponent"));
        Assert.assertTrue(json.contains("BlockPhysicsBindingsComponent"));
        Assert.assertFalse(json.contains("PhysicsCompiledFixturesComponent"));
        Assert.assertFalse(json.contains("SpatialPhysicsFootprintComponent"));

        World validationWorld = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            SceneLoader.loadScene(validationWorld, file, false, meta);
        } finally {
            validationWorld.dispose();
            world.dispose();
        }
    }

    @Test
    public void phaseDGateRejectsLinkedSceneBeforeActivationThenDirectRetrySucceeds() {
        World world = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        ProjectConfig config = new ProjectConfig();
        config.projectTitle = "Phase D gate";
        config.createSceneMeta("Main");
        games.pixscape.studio.configuration.SceneMeta meta = config.getCurrentSceneMeta();
        meta.physicsEnabled = true;
        meta.nextEntityStableId = 2;
        meta.nextPhysicsShapeId = 2;
        FileHandle linkedFile = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"),
                "pixscape-phase-d-linked-" + System.nanoTime() + ".json"));
        writeLinkedScene(linkedFile, meta);

        IdentityRegistry identities = new IdentityRegistry();
        BlockPhysicsBindingRepository bindings = new BlockPhysicsBindingRepository();
        identities.bind(world, meta);
        bindings.bind(world, identities);
        final int[] renderRebuilds = {0};
        ResolvedSceneActivationPipeline pipeline = new ResolvedSceneActivationPipeline(
                world,
                null,
                null,
                new HistoryManager(8),
                identities,
                bindings,
                (cfg, tag, projectDir) -> renderRebuilds[0]++);
        ResolvedSceneActivationPipeline.ResolvedSceneTarget target =
                new ResolvedSceneActivationPipeline.ResolvedSceneTarget(
                        config, meta, linkedFile, linkedFile.parent(),
                        config.projectTitle, "Main", "main");

        IllegalStateException failure = Assert.assertThrows(
                IllegalStateException.class, () -> pipeline.activate(target));
        Assert.assertTrue(failure.getMessage().contains("Phase D"));
        Assert.assertEquals(0, renderRebuilds[0]);
        Assert.assertFalse(bindings.hasAnyBindings());
        Assert.assertFalse(world.getMapper(PhysicsCompiledFixturesComponent.class).has(0));
        Assert.assertFalse(world.getMapper(SpatialPhysicsFootprintComponent.class).has(0));

        clear(world);
        bindings.bind(world, identities);
        FileHandle directFile = new FileHandle(new File(
                System.getProperty("java.io.tmpdir"),
                "pixscape-phase-d-direct-" + System.nanoTime() + ".json"));
        SceneService.saveScene(world, directFile, false, meta, bindings);
        pipeline.activate(new ResolvedSceneActivationPipeline.ResolvedSceneTarget(
                config, meta, directFile, directFile.parent(),
                config.projectTitle, "Main", "main"));
        Assert.assertEquals(1, renderRebuilds[0]);
        bindings.clear();
        identities.bind(null, null);
        world.dispose();
    }

    private static void writeLinkedScene(FileHandle file, SceneMetaRuntime meta) {
        World authored = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager()));
        try {
            int owner = authored.create();
            authored.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
            SpatialBlocksComponent blocks = authored.getMapper(SpatialBlocksComponent.class).create(owner);
            blocks.nextSpatialBlockId = 2;
            SpatialBlockData block = new SpatialBlockData();
            block.id = 1;
            block.structureId = 1;
            block.width = 1f;
            block.depth = 1f;
            blocks.blocks.add(block);
            PhysicsShapeData linked = new PhysicsShapeData();
            linked.physicsShapeId = 1;
            authored.getMapper(PhysicsShapesComponent.class).create(owner).shapes.add(linked);
            BlockPhysicsBindingData binding = new BlockPhysicsBindingData();
            binding.spatialBlockId = 1;
            binding.physicsShapeId = 1;
            authored.getMapper(BlockPhysicsBindingsComponent.class).create(owner).bindings.add(binding);
            authored.process();
            SceneSaveTestSupport.save(authored, file, meta);
        } finally {
            authored.dispose();
        }
    }

    private static void clear(World world) {
        IntBag entities = world.getAspectSubscriptionManager()
                .get(Aspect.all()).getEntities();
        int[] data = entities.getData();
        for (int i = 0; i < entities.size(); i++) {
            world.delete(data[i]);
        }
        world.process();
    }
}
