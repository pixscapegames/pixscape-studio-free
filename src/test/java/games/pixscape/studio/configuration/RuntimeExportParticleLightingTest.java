package games.pixscape.studio.configuration;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.artemis.io.JsonArtemisSerializer;
import com.artemis.io.SaveFileFormat;
import com.artemis.managers.WorldSerializationManager;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.io.StudioFs;
import org.junit.Test;

import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class RuntimeExportParticleLightingTest {
    @Test public void exportedInstancesRetainIndependentAmbientParticipation() throws Exception {
        Path studio = Files.createTempDirectory("particle-lighting-export-studio");
        Path user = Files.createTempDirectory("particle-lighting-export-user");
        Files.createDirectories(studio.resolve(StudioFs.DIR_SCENES));
        World authored = newWorld(), runtime = newWorld();
        try {
            SaveFileFormat request = new SaveFileFormat();
            for (boolean ignore : new boolean[]{false, true}) {
                int entity = authored.create();
                ParticleEmitterComponent emitter = authored.getMapper(ParticleEmitterComponent.class).create(entity);
                emitter.effectPath = "fire.p";
                emitter.ignoreAmbientLighting = ignore;
                request.entities.add(entity);
            }
            authored.process();
            Path scene = studio.resolve(StudioFs.DIR_SCENES).resolve("scene1.json");
            try (OutputStream output = Files.newOutputStream(scene)) {
                authored.getSystem(WorldSerializationManager.class).save(output, request);
            }
            new AssetMetaDatabase().save(new FileHandle(studio.resolve(StudioFs.FILE_ASSETS_JSON).toFile()));
            ProjectConfig config = new ProjectConfig();
            config.projectTitle = "Particle Lighting";
            config.projectFileName = "particle-lighting";
            config.exportRootPathDir = user.toString();
            config.createSceneMeta("Main");
            RuntimeExport.exportRuntime(config, new FileHandle(studio.toFile()), new FileHandle(user.toFile()));

            Path exported = user.resolve(RuntimeExport.RUNTIME_DIR_NAME).resolve("scenes/scene1.json");
            try (InputStream input = Files.newInputStream(exported)) {
                SaveFileFormat loaded = runtime.getSystem(WorldSerializationManager.class).load(input, SaveFileFormat.class);
                assertEquals(2, loaded.entities.size());
                for (int i = 0; i < 2; i++) {
                    ParticleEmitterComponent emitter = runtime.getMapper(ParticleEmitterComponent.class).get(loaded.entities.get(i));
                    assertEquals(i == 1, emitter.ignoreAmbientLighting);
                    assertEquals("fire.p", emitter.effectPath);
                }
            }
        } finally {
            authored.dispose();
            runtime.dispose();
        }
    }

    private static World newWorld() {
        World world = new World(new WorldConfigurationBuilder().with(new WorldSerializationManager()).build());
        world.getSystem(WorldSerializationManager.class).setSerializer(new JsonArtemisSerializer(world));
        return world;
    }
}

