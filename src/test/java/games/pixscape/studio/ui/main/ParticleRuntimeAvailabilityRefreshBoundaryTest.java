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
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;
import static games.pixscape.studio.service.asset.VisualResolverTestSupport.texture;

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

    @Test
    public void flameDropWaitsForAtlasPublicationBeforeStrictAuthoredPreparation()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("flame-effects"));
        writeEffect(effectsRoot.child("Flame.p"), "flame.png");
        TextureAtlas publishedAtlas = new TextureAtlas();
        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", publishedAtlas);
        RenderParticleSyncSystem runtimeSystem = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(runtimeSystem).build());
        try {
            createParticle(world, "Flame.p");
            world.process();
            WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request =
                    new WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest();
            request.request();

            assertFalse(request.consumeIf(false, () ->
                    runtimeSystem.prepareRuntimeAvailability("scene", new Array<>())));
            assertTrue(request.isPending());
            assertFalse(runtimeSystem.isPrepared("scene", "Flame.p"));

            addRegion(publishedAtlas, "flame");
            assertTrue(request.consumeIf(true, () ->
                    runtimeSystem.prepareRuntimeAvailability("scene", new Array<>())));
            assertFalse(request.isPending());
            assertTrue(runtimeSystem.isPrepared("scene", "Flame.p"));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void existingParticleRegionConsumesRefreshWithoutWaitingForPack()
            throws Exception {
        FileHandle effectsRoot = new FileHandle(temporaryFolder.newFolder("existing-effects"));
        writeEffect(effectsRoot.child("existing.p"), "existing.png");
        TextureAtlas publishedAtlas = new TextureAtlas();
        AtlasRuntimeService atlasService = new AtlasRuntimeService();
        atlasService.loadBorrowed("scene", publishedAtlas);
        addRegion(publishedAtlas, "existing");
        RenderParticleSyncSystem runtimeSystem = new RenderParticleSyncSystem(
                new VfxRenderState(8), new OrthographicCamera(), 0,
                atlasService, effectsRoot);
        World world = new World(new WorldConfigurationBuilder().with(runtimeSystem).build());
        try {
            createParticle(world, "existing.p");
            world.process();
            WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request =
                    new WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest();
            request.request();

            assertTrue(request.consumeIf(true, () ->
                    runtimeSystem.prepareRuntimeAvailability("scene", new Array<>())));
            assertTrue(runtimeSystem.isPrepared("scene", "existing.p"));
        } finally {
            world.dispose();
        }
    }

    @Test
    public void repeatedRequestsCoalesceWhilePackRemainsPending() {
        WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest request =
                new WorldCanvas.ParticleRuntimeAvailabilityRefreshRequest();
        AtomicInteger refreshes = new AtomicInteger();

        request.request();
        request.request();
        assertFalse(request.consumeIf(false, refreshes::incrementAndGet));
        assertTrue(request.isPending());
        assertEquals(0, refreshes.get());

        assertTrue(request.consumeIf(true, refreshes::incrementAndGet));
        assertFalse(request.isPending());
        assertEquals(1, refreshes.get());
        assertFalse(request.consumeIf(true, refreshes::incrementAndGet));
        assertEquals(1, refreshes.get());
    }

    private static void createParticle(World world, String effectPath) {
        int entityId = world.create();
        world.getMapper(TransformComponent.class).create(entityId);
        ParticleEmitterComponent emitter =
                world.getMapper(ParticleEmitterComponent.class).create(entityId);
        emitter.atlasTag = "scene";
        emitter.effectPath = effectPath;
    }

    private static void addRegion(TextureAtlas atlas, String name) {
        TextureAtlas.AtlasRegion region = new TextureAtlas.AtlasRegion(
                texture(1, 1), 0, 0, 1, 1);
        region.name = name;
        atlas.getRegions().add(region);
    }

    private static void writeEffect(FileHandle file) throws Exception {
        writeEffect(file, null);
    }

    private static void writeEffect(FileHandle file, String imagePath) throws Exception {
        ParticleEffect source = new ParticleEffect();
        ParticleEmitter emitter = new ParticleEmitter();
        if (imagePath != null) emitter.getImagePaths().add(imagePath);
        source.getEmitters().add(emitter);
        StringWriter writer = new StringWriter();
        source.save(writer);
        file.writeString(writer.toString(), false, "UTF-8");
    }
}
