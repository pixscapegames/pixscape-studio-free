package games.pixscape.studio.service;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.BaseSystem;
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
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import games.pixscape.studio.ui.main.WorldCanvas;
import sun.misc.Unsafe;

import java.io.File;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;

public class SceneServiceBlockPhysicsBindingSaveTest {
    private static HeadlessApplication headlessApplication;

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

    @Test
    public void preparedSaveFlushesThenRejectsUnflushedOrphanBeforeAnyWrite() throws Exception {
        ensureHeadlessApplication();
        ProcessCounterSystem processCounter = new ProcessCounterSystem();
        World world = new World(new WorldConfiguration()
                .setSystem(new WorldSerializationManager())
                .setSystem(processCounter));
        world.process();
        processCounter.processCount = 0;

        ProjectConfig config = new ProjectConfig();
        Path projectPath = Files.createTempDirectory("pixscape-unflushed-save");
        config.projectFileName = "project";
        config.projectDirectoryPath = projectPath.toString();
        config.exportRootPathDir = projectPath.resolve("export").toString();
        config.createSceneMeta("Main");
        games.pixscape.studio.configuration.SceneMeta meta = config.getCurrentSceneMeta();
        meta.physicsEnabled = true;
        meta.nextEntityStableId = 2;
        meta.nextPhysicsShapeId = 2;
        ProjectConfig.setInstance(config);
        FileHandle sentinel = new FileHandle(projectPath.resolve("scenes")
                .resolve(meta.getFile()).toFile());
        sentinel.parent().mkdirs();
        sentinel.writeString("keep", false, "UTF-8");

        IdentityRegistry identities = new IdentityRegistry();
        BlockPhysicsBindingRepository bindings = new BlockPhysicsBindingRepository();
        identities.bind(world, meta);
        bindings.bind(world, identities);
        int owner = world.create();
        world.getMapper(PixscapeIdentityComponent.class).create(owner).stableId = 1;
        PhysicsShapeData linked = new PhysicsShapeData();
        linked.physicsShapeId = 1;
        world.getMapper(PhysicsShapesComponent.class).create(owner).shapes.add(linked);

        WorldCanvas canvas = allocate(WorldCanvas.class);
        setField(canvas, "world", world);
        setField(canvas, "blockPhysicsBindingRepository", bindings);
        SceneService service = allocate(SceneService.class);
        setField(service, "canvas", canvas);

        Method prepare = SceneService.class.getDeclaredMethod("prepareSaveExecutionPlan");
        prepare.setAccessible(true);
        InvocationTargetException failure = Assert.assertThrows(
                InvocationTargetException.class, () -> prepare.invoke(service));
        Assert.assertTrue(failure.getCause() instanceof IllegalStateException);
        Assert.assertEquals(1, processCounter.processCount);
        Assert.assertEquals("keep", sentinel.readString("UTF-8"));
        Assert.assertEquals(2, meta.nextEntityStableId);
        Assert.assertEquals(2, meta.nextPhysicsShapeId);
        bindings.clear();
        identities.bind(null, null);
        world.dispose();
    }

    @Test
    public void savePipelinesValidateAfterFlushBeforeTheirFirstWrites() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/service/SceneService.java"));
        String prepared = methodBody(source, "private SaveExecutionPlan prepareSaveExecutionPlan()");
        assertOrdered(prepared,
                "flushWorldForSerialization()",
                "validateSceneForSave(",
                "scenesDir.mkdirs()");

        String currentOnly = methodBody(source, "private void saveCurrentSceneOnly(ProjectConfig cfg)");
        Assert.assertEquals(1, occurrences(currentOnly, "flushWorldForSerialization()"));
        assertOrdered(currentOnly,
                "flushWorldForSerialization()",
                "validateSceneForSave(",
                "scenesDir.mkdirs()",
                "repackSceneAtlas(cfg, sceneName, projectDir)",
                "rebuildSparseFromDense()",
                "saveActiveScene(sceneFile)",
                "TileAnimationsIO.save(");
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

    private static void ensureHeadlessApplication() {
        if (headlessApplication == null) {
            headlessApplication = new HeadlessApplication(
                    new ApplicationAdapter() {}, new HeadlessApplicationConfiguration());
        }
    }

    private static <T> T allocate(Class<T> type) throws Exception {
        return type.cast(unsafe().allocateInstance(type));
    }

    private static void setField(Object target, String fieldName, Object value) throws Exception {
        Field field = target.getClass().getDeclaredField(fieldName);
        field.setAccessible(true);
        unsafe().putObject(target, unsafe().objectFieldOffset(field), value);
    }

    private static Unsafe unsafe() throws Exception {
        Field field = Unsafe.class.getDeclaredField("theUnsafe");
        field.setAccessible(true);
        return (Unsafe) field.get(null);
    }

    private static final class ProcessCounterSystem extends BaseSystem {
        int processCount;

        @Override
        protected void processSystem() {
            processCount++;
        }
    }

    private static String methodBody(String source, String signature) {
        int signatureIndex = source.indexOf(signature);
        Assert.assertTrue("Method signature not found: " + signature, signatureIndex >= 0);
        int bodyStart = source.indexOf('{', signatureIndex);
        int depth = 0;
        for (int index = bodyStart; index < source.length(); index++) {
            char current = source.charAt(index);
            if (current == '{') depth++;
            if (current == '}' && --depth == 0) {
                return source.substring(bodyStart + 1, index);
            }
        }
        throw new AssertionError("Method body not closed: " + signature);
    }

    private static void assertOrdered(String source, String... snippets) {
        int previous = -1;
        for (String snippet : snippets) {
            int current = source.indexOf(snippet);
            Assert.assertTrue("Missing: " + snippet, current >= 0);
            Assert.assertTrue("Out of order: " + snippet, current > previous);
            previous = current;
        }
    }

    private static int occurrences(String source, String needle) {
        int count = 0;
        int index = 0;
        while ((index = source.indexOf(needle, index)) >= 0) {
            count++;
            index += needle.length();
        }
        return count;
    }
}
