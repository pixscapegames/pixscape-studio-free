package games.pixscape.studio.ui.main;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.system.RenderParticleSyncSystem;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.history.commands.CreateEntityCommand;
import games.pixscape.studio.history.initializer.GenericEntityInitializer;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.StringWriter;

import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class ParticleAuthoringAvailabilityHistoryTest {

    @Rule
    public final TemporaryFolder temporaryFolder = new TemporaryFolder();

    @Test
    public void createUndoNextCreateAndRedoRefreshAuthoredAndDeclaredParticlesWithoutSave()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("effects"));
        writeEffect(effectsRoot.child("a.p"));
        writeEffect(effectsRoot.child("b.p"));
        writeEffect(effectsRoot.child("declared.p"));

        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", new TextureAtlas());
        RenderParticleSyncSystem runtimeSystem = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(runtimeSystem).build());
        HistoryManager history = new HistoryManager(16);
        WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request =
                new WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest();
        Array<String> declared = new Array<>();
        declared.add("declared.p");
        try {
            history.execute(createParticle(world, history, request, 1, "a.p"));
            processAndRefresh(world, runtimeSystem, request, declared);
            assertTrue(runtimeSystem.isPrepared("scene", "a.p"));
            assertTrue(runtimeSystem.isPrepared("scene", "declared.p"));

            history.undo();
            history.execute(createParticle(world, history, request, 2, "b.p"));
            assertFalse(runtimeSystem.isPrepared("scene", "b.p"));

            processAndRefresh(world, runtimeSystem, request, declared);
            assertTrue(runtimeSystem.isPrepared("scene", "b.p"));
            assertTrue(runtimeSystem.isPrepared("scene", "declared.p"));

            history.undo();
            history.redo();
            assertTrue(request.isPending());

            processAndRefresh(world, runtimeSystem, request, declared);
            assertTrue(runtimeSystem.isPrepared("scene", "b.p"));
            assertTrue(runtimeSystem.isPrepared("scene", "declared.p"));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void redoWhileAtlasPublicationIsPendingKeepsOneRefreshUntilPublication()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("redo-effects"));
        writeEffect(effectsRoot.child("flame.p"));
        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", new TextureAtlas());
        RenderParticleSyncSystem runtimeSystem = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(runtimeSystem).build());
        HistoryManager history = new HistoryManager(16);
        WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request =
                new WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest();
        try {
            history.execute(createParticle(world, history, request, 1, "flame.p"));
            world.process();
            assertFalse(request.consumeIf(false, () ->
                    runtimeSystem.prepareRuntimeAvailability("scene", new Array<>())));
            assertTrue(request.isPending());

            history.undo();
            history.redo();
            world.process();
            assertTrue(request.isPending());
            assertFalse(request.consumeIf(false, () ->
                    runtimeSystem.prepareRuntimeAvailability("scene", new Array<>())));
            assertFalse(runtimeSystem.isPrepared("scene", "flame.p"));

            assertTrue(request.consumeIf(true, () ->
                    runtimeSystem.prepareRuntimeAvailability("scene", new Array<>())));
            assertFalse(request.isPending());
            assertTrue(runtimeSystem.isPrepared("scene", "flame.p"));
        } finally {
            world.dispose();
        }
    }

    private static CreateEntityCommand createParticle(
            World world,
            HistoryManager history,
            WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request,
            int stableId,
            String effectPath) {
        GenericEntityInitializer initializer = new GenericEntityInitializer(world)
                .configureParticleEmitter(effectPath, "scene", 0f, 0f, 0, effectPath);
        initializer.setIdentityStableId(stableId);
        return new CreateEntityCommand(
                world,
                history.historyIds(),
                initializer,
                entityId -> request.request());
    }

    private static void processAndRefresh(
            World world,
            RenderParticleSyncSystem runtimeSystem,
            WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request,
            Array<String> declared) {
        world.process();
        request.consume(() -> {
            runtimeSystem.invalidateAllEffects();
            runtimeSystem.prepareRuntimeAvailability("scene", declared);
        });
    }

    private static void writeEffect(FileHandle file) throws Exception {
        ParticleEffect source = new ParticleEffect();
        source.getEmitters().add(new ParticleEmitter());
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }
}
