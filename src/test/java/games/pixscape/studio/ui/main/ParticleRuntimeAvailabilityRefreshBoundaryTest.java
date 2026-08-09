package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.component.ParticleEmitterComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.StringWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParticleRuntimeAvailabilityRefreshBoundaryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void pendingRefreshIsConsumedOnlyAfterEntityInsertionIsVisible() throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("effects"));
        writeEffect(effectsRoot.child("fire.p"));
        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", new TextureAtlas());
        RenderParticleSyncSystem runtimeSystem = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(runtimeSystem).build());
        try {
            int entityId = world.create();
            world.getMapper(TransformComponent.class).create(entityId);
            ParticleEmitterComponent emitter =
                    world.getMapper(ParticleEmitterComponent.class).create(entityId);
            emitter.atlasTag = "scene";
            emitter.effectPath = "fire.p";

            WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request =
                    new WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest();
            request.request();

            assertTrue(request.isPending());
            assertFalse(runtimeSystem.isPrepared("scene", "fire.p"));

            world.process();
            request.consume(() -> runtimeSystem.prepareRuntimeAvailability(
                    "scene", new Array<String>()));

            assertFalse(request.isPending());
            assertTrue(runtimeSystem.isPrepared("scene", "fire.p"));
        } finally {
            world.dispose();
        }
    }

    private static void writeEffect(FileHandle file) throws Exception {
        ParticleEffect source = new ParticleEffect();
        source.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }
}
