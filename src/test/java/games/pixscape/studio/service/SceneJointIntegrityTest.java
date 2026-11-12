package games.pixscape.studio.service;

import com.artemis.Aspect;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.artemis.utils.IntBag;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.physics.PhysicsBodyComponent;
import games.pixscape.runtime.component.physics.PhysicsDistanceJointComponent;
import games.pixscape.runtime.component.physics.PhysicsJointComponent;
import games.pixscape.runtime.loading.SceneLoader;
import games.pixscape.runtime.service.IdentityRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateLayerCommand;
import games.pixscape.studio.history.commands.DeleteLayerCommand;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.ByteArrayOutputStream;

import static org.junit.Assert.*;

public class SceneJointIntegrityTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void saveRejectsOrphanBeforeReplacingFileOrMutatingWorld() throws Exception {
        try (Fixture f = new Fixture()) {
            f.layers.addLayerTop("Existing");
            int a = f.body(), b = f.body(), joint = f.joint(a, b);
            f.world.process();
            FileHandle file = file("save.json");
            SceneService.saveScene(f.world, file, false);
            byte[] original = file.readBytes();
            f.world.getMapper(PhysicsBodyComponent.class).remove(a);
            IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                    () -> SceneService.saveScene(f.world, file, false));
            assertTrue(failure.getMessage(), failure.getMessage().contains("aEid"));
            assertTrue(failure.getMessage(), failure.getMessage().contains("stableId=" + f.identities.getStableId(joint)));
            assertArrayEquals(original, file.readBytes());
            assertEquals(a, f.world.getMapper(PhysicsJointComponent.class).get(joint).aEid);
        }
    }

    @Test
    public void loadRejectsOrphanBeforeClearingExistingScene() throws Exception {
        FileHandle file = file("orphan.json");
        SceneMeta meta;
        try (Fixture source = new Fixture()) {
            int a = source.body(), b = source.body();
            source.joint(a, b);
            source.world.getMapper(PhysicsBodyComponent.class).remove(a);
            source.world.process();
            // Deliberately bypass Studio validation to supply a corrupt external file.
            WorldSerializationManager manager = source.world.getSystem(WorldSerializationManager.class);
            manager.setSerializer(new JsonArtemisSerializer(source.world));
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            manager.save(out, new SaveFileFormat(source.all()));
            file.writeBytes(out.toByteArray(), false);
            meta = source.meta;
        }
        try (Fixture target = new Fixture()) {
            int survivor = target.body();
            target.world.process();
            RuntimeException failure = assertThrows(RuntimeException.class,
                    () -> SceneLoader.loadScene(target.world, file, true, meta));
            assertTrue(failure.getMessage(), failure.getMessage().contains("aEid"));
            assertTrue(target.world.getMapper(PhysicsBodyComponent.class).has(survivor));
            assertEquals(1, target.all().size());
        }
    }

    @Test
    public void jointFreeSceneRemainsJointFreeThroughActivationLayersHistoryAndReload() throws Exception {
        exerciseLayers(false);
    }

    @Test
    public void realJointIsDeletedRestoredAndRemappedAcrossHistoryAndReload() throws Exception {
        exerciseLayers(true);
    }

    private void exerciseLayers(boolean withJoint) throws Exception {
        FileHandle file = file("scene.json");
        SceneMeta meta;
        int jointStable = -1, aStable, bStable;
        try (Fixture source = new Fixture()) {
            source.layers.addLayerTop("Existing");
            source.layers.addLayerTop("Other");
            int a = source.body(), b = source.body();
            source.world.getMapper(EntityIndexComponent.class).create(a);
            source.world.getMapper(EntityIndexComponent.class).create(b).layerIndex = 1;
            aStable = source.identities.getStableId(a);
            bStable = source.identities.getStableId(b);
            if (withJoint) jointStable = source.identities.getStableId(source.joint(a, b));
            source.world.process();
            SceneService.saveScene(source.world, file, false);
            meta = source.meta;
        }
        int initialCount = withJoint ? 1 : 0;
        try (Fixture f = new Fixture(meta)) {
            // Leave an occupied ECS ID so serialized IDs cannot be treated as stable IDs.
            f.world.create();
            f.world.process();
            ProjectConfig config = new ProjectConfig();
            new ResolvedSceneActivationPipeline(f.world, null, null, f.history,
                    (cfg, tag, dir) -> assertJoints(f.world, initialCount, f.all()),
                    (world, input, clear, loadedMeta) -> {
                        SaveFileFormat loaded = SceneLoader.loadScene(world, input, clear, loadedMeta);
                        assertJoints(world, initialCount, loaded.entities);
                    }).activate(new ResolvedSceneActivationPipeline.ResolvedSceneTarget(
                    config, meta, file, file.parent(), "Joint regression", "Scene", "scene"));
            f.identities.rebuild();
            assertJoints(f.world, initialCount, f.all());
            assertEquals(2, f.layers.count());
            for (int i = 0; i < 3; i++) {
                f.layers.count();
                f.history.execute(new CreateLayerCommand(f.layers, 1, "Temporary", null));
                f.world.process();
                assertJoints(f.world, initialCount, f.all());
            }
            for (int i = 0; i < 3; i++) {
                f.history.execute(new DeleteLayerCommand(f.layers, f.layers.getLayerEntity(1), null));
                f.world.process();
                assertJoints(f.world, initialCount, f.all());
            }
            f.history.execute(new DeleteLayerCommand(f.layers, f.layers.getLayerEntity(0), null));
            for (int cycle = 0; cycle < 3; cycle++) {
                f.world.process();
                assertJoints(f.world, 0, f.all());
                if (cycle == 0) {
                    SceneService.saveScene(f.world, file, false);
                    try (Fixture deleted = new Fixture(meta)) {
                        SaveFileFormat loaded = SceneLoader.loadScene(deleted.world, file, false, meta);
                        assertJoints(deleted.world, 0, loaded.entities);
                        deleted.world.process();
                        deleted.identities.rebuild();
                        assertEquals(-1, deleted.identities.findByStableId(aStable));
                        assertTrue(deleted.identities.findByStableId(bStable) >= 0);
                    }
                }
                assertEquals(-1, f.identities.findByStableId(aStable));
                f.history.undo();
                // Inspect restored components before ECS subscription publication as well.
                IntBag restored = new IntBag();
                if (withJoint) restored.add(f.identities.findByStableId(jointStable));
                assertJoints(f.world, initialCount, restored);
                f.world.process();
                assertJoints(f.world, initialCount, f.all());
                if (withJoint) {
                    PhysicsJointComponent joint = f.world.getMapper(PhysicsJointComponent.class)
                            .get(f.identities.findByStableId(jointStable));
                    assertEquals(f.identities.findByStableId(aStable), joint.aEid);
                    assertEquals(f.identities.findByStableId(bStable), joint.bEid);
                }
                f.history.redo();
            }
            f.history.undo();
            f.world.process();
            SceneService.saveScene(f.world, file, false);
        }
        try (Fixture reloaded = new Fixture(meta)) {
            reloaded.world.create();
            reloaded.world.create();
            reloaded.world.process();
            SaveFileFormat loaded = SceneLoader.loadScene(reloaded.world, file, false, meta);
            assertJoints(reloaded.world, initialCount, loaded.entities);
            reloaded.world.process();
            reloaded.identities.rebuild();
            assertJoints(reloaded.world, initialCount, reloaded.all());
            assertTrue(reloaded.identities.findByStableId(aStable) >= 0);
            assertTrue(reloaded.identities.findByStableId(bStable) >= 0);
            if (withJoint) {
                PhysicsJointComponent joint = reloaded.world.getMapper(PhysicsJointComponent.class)
                        .get(reloaded.identities.findByStableId(jointStable));
                assertEquals(reloaded.identities.findByStableId(aStable), joint.aEid);
                assertEquals(reloaded.identities.findByStableId(bStable), joint.bEid);
            }
        }
    }

    private FileHandle file(String name) throws Exception {
        return new FileHandle(temporary.newFile(name));
    }

    private static void assertJoints(World world, int expected, IntBag entities) {
        int count = 0;
        for (int i = 0; i < entities.size(); i++) {
            int entity = entities.get(i);
            PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).getSafe(entity, null);
            if (joint == null) {
                assertFalse(world.getMapper(PhysicsDistanceJointComponent.class).has(entity));
                continue;
            }
            count++;
            assertTrue(joint.aEid >= 0 && world.getMapper(PhysicsBodyComponent.class).has(joint.aEid));
            assertTrue(joint.bEid >= 0 && world.getMapper(PhysicsBodyComponent.class).has(joint.bEid));
            assertTrue(world.getMapper(PhysicsDistanceJointComponent.class).has(entity));
        }
        assertEquals(expected, count);
    }

    private static final class Fixture implements AutoCloseable {
        final World world = new World(new WorldConfiguration().setSystem(new WorldSerializationManager()));
        final HistoryManager history = new HistoryManager(64);
        final IdentityRegistry identities = new IdentityRegistry();
        final SceneMeta meta;
        final LayerService layers;

        Fixture() {
            this(new SceneMeta());
        }

        Fixture(SceneMeta meta) {
            this.meta = meta;
            meta.physicsEnabled = true;
            identities.bind(world, meta);
            layers = new LayerService(world, null, history.historyIds(), identities);
        }

        int body() {
            int entity = world.create();
            world.getMapper(PhysicsBodyComponent.class).create(entity);
            identities.ensureStableId(entity);
            return entity;
        }

        int joint(int a, int b) {
            int entity = world.create();
            PhysicsJointComponent joint = world.getMapper(PhysicsJointComponent.class).create(entity);
            joint.aEid = a;
            joint.bEid = b;
            world.getMapper(PhysicsDistanceJointComponent.class).create(entity);
            identities.ensureStableId(entity);
            return entity;
        }

        IntBag all() {
            return world.getAspectSubscriptionManager().get(Aspect.all()).getEntities();
        }

        @Override
        public void close() {
            identities.bind(null, null);
            world.dispose();
        }
    }
}
