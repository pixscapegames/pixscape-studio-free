package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.EntityIndexComponent;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.LightingGeometrySystem;
import games.pixscape.runtime.system.RenderTiledSyncSystem;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile;
import games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles;
import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class StudioLightCompositionGlSmokeTest {
    @Test
    public void unlitSpatialEditsRebuildOnActivationForIsoAndOrtho() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 32);
        config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                try {
                    ShaderRegistry.initDefaults();
                    for (var projection : games.pixscape.runtime.tiled.TiledProjection.values()) {
                        var meta = new games.pixscape.studio.configuration.SceneMeta("Unlit test", "unlit.json");
                        meta.lightingEnabled = false;
                        FrameRenderQueue queue = new FrameRenderQueue(4);
                        LayerStateSOA layers = new LayerStateSOA(1);
                        var tiledState = new games.pixscape.runtime.render.TiledMapRenderState();
                        OrthographicCamera camera = new OrthographicCamera(64, 32);
                        var batch = new games.pixscape.studio.batch.MeshBatchStudio(16);
                        var submit = new StudioRenderSubmitSystem(layers, queue, camera, batch,
                                new RenderStats(), new RenderStatsSink(100))
                                .setSceneSettings(meta).setRenderDomains(null, tiledState);
                        var profile = new games.pixscape.runtime.tiled.profile.RuntimeTilesetProfile();
                        profile.tilesetId = 1;
                        profile.referenceCellWidth = 256;
                        profile.referenceCellHeight = 128;
                        profile.projection = projection;
                        profile.anchor = games.pixscape.runtime.tiled.profile.RuntimeTilesetAnchor.BOTTOM_CENTER;
                        profile.tileAssetIds = new int[]{7};
                        var profiles = games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles.empty();
                        profiles.add(profile);
                        var atlas = new AtlasRuntimeService() {
                            @Override
                            public games.pixscape.runtime.service.AtlasAssetBinding resolveBinding(int id, String tag) {
                                return games.pixscape.runtime.service.LightingTestBindings.tile(id, 256, 512);
                            }
                        };
                        var sync = new games.pixscape.runtime.system.RenderTiledSyncSystem(camera, tiledState,
                                atlas, 0, null, profiles);
                        World world = new World(new com.artemis.WorldConfiguration()
                                .register(games.pixscape.runtime.loading.SceneMetaRuntime.class.getName(), meta)
                                .setSystem(sync).setSystem(submit));
                        try {
                            int entity = world.create();
                            world.getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(entity);
                            var tiled = world.getMapper(games.pixscape.runtime.component.TiledLayerComponent.class).create(entity);
                            tiled.projection = projection;
                            tiled.tileWidth = 256;
                            tiled.tileHeight = 128;
                            tiled.chunkSize = tiled.mapWidthCells = tiled.mapHeightCells = 8;
                            tiled.data = tiled.createMapData();
                            tiled.data.setTile(1, 1, 7);
                            var blocks = world.getMapper(games.pixscape.runtime.component.spatial.SpatialBlocksComponent.class).create(entity);
                            var wall = new games.pixscape.runtime.spatial.SpatialBlockData();
                            wall.id = wall.structureId = 1;
                            wall.x = wall.y = wall.width = wall.depth = 1;
                            wall.height = 48;
                            wall.shadowCaster = true;
                            wall.beginAuthoredLinkedTileRefs();
                            wall.addLinkedTileRef(1, 1, 7);
                            blocks.blocks.add(wall);
                            blocks.nextSpatialBlockId = 2;
                            world.process();
                            var history = new games.pixscape.studio.history.HistoryManager(16);
                            Runnable enable = () -> history.execute(new games.pixscape.studio.history.commands.SetSceneLightingCommand(
                                    world, meta, true, submit::setLightingEnabled, null));
                            {
                                enable.run(); // No lights exist, but geometry is still prepared.
                                MapLightingGeometry old = tiledState.lightingMaps.get(entity);
                                Assert.assertNotNull(old);
                                int previous = old.publicationRevision();
                                history.execute(new games.pixscape.studio.history.commands.SetSceneLightingCommand(
                                        world, meta, false, submit::setLightingEnabled, null));
                                var edited = wall.copy();
                                edited.height = 96;
                                history.execute(new games.pixscape.studio.history.commands.EditSpatialBlockCommand(
                                        world, history.historyIds(), null, entity, wall.id, wall, edited));
                                Assert.assertEquals(96, blocks.blocks.first().height, 0);
                                world.process();
                                Assert.assertEquals(previous, old.publicationRevision());
                                enable.run();
                                Assert.assertTrue(meta.lightingEnabled);
                                Assert.assertTrue(tiledState.lightingEnabled);
                                Assert.assertTrue(tiledState.lightingMaps.get(entity).publicationRevision() > previous);
                                history.undo();
                                Assert.assertFalse(meta.lightingEnabled);
                                history.redo();
                                Assert.assertTrue(meta.lightingEnabled);
                                if (projection == games.pixscape.runtime.tiled.TiledProjection.ORTHO) {
                                    history.undo();
                                    int lightEntity = world.create();
                                    var light = world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(lightEntity);
                                    light.shadowQuality = 2;
                                    world.process();
                                    history.redo();
                                    Assert.assertTrue(meta.lightingEnabled);
                                    Assert.assertTrue(submit.isLightingEnabled());
                                    Assert.assertTrue(tiledState.lightingEnabled);
                                    Assert.assertEquals(2, light.shadowQuality);
                                }
                            }
                        } finally {
                            world.dispose();
                            batch.close();
                        }
                    }
                } catch (Throwable error) {
                    failure[0] = error;
                } finally {
                    ShaderRegistry.disposeAll();
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Unlit spatial activation failed", failure[0]);
    }

    @Test
    public void sceneRendererSwitchesAtPreparationWithBothNormalLayouts() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 32);
        config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                World world = null;
                games.pixscape.studio.batch.TextureArrayMeshBatchStudio batch = null;
                AtlasRuntimeService.TextureArrayBundle bundle = null;
                Texture texture = null;
                try {
                    ShaderRegistry.initDefaults();
                    Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                    pix.setColor(.4f, .7f, .2f, .6f);
                    pix.fill();
                    texture = new Texture(pix);
                    pix.dispose();
                    com.badlogic.gdx.utils.Array<Texture> textures = new com.badlogic.gdx.utils.Array<>();
                    textures.add(texture);
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(textures, 1, 1, GLCaps.detect());
                    batch = new games.pixscape.studio.batch.TextureArrayMeshBatchStudio(16);
                    batch.setTextureArrayBundle(bundle);
                    FrameRenderQueue q = new FrameRenderQueue(4);
                    LayerStateSOA layers = new LayerStateSOA(1);
                    layers.enabled[0] = true;
                    OrthographicCamera camera = new OrthographicCamera(64, 32);
                    camera.position.set(32, 16, 0);
                    camera.update();
                    var meta = new games.pixscape.studio.configuration.SceneMeta();
                    meta.lightingEnabled = true;
                    meta.ambientMulR = meta.ambientMulG = meta.ambientMulB = .2f;
                    RenderStats stats = new RenderStats();
                    StudioRenderSubmitSystem submit = new StudioRenderSubmitSystem(layers, q, camera, batch, stats, new RenderStatsSink(100)).setSceneSettings(meta);
                    world = new World(new WorldConfigurationBuilder().with(submit).build());
                    int entity = world.create();
                    world.getMapper(PointLightComponent.class).create(entity);
                    world.process();
                    var history = new games.pixscape.studio.history.HistoryManager(16);
                    history.historyIds().ensureForEntity(entity);
                    history.configureSceneLighting(world, meta, null);
                    q.addQuad(TextureRegistry.handleOf(texture), ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName()), 1,
                            0, 0, 0, 0, 0, 0, 0, 32, 64, 32, 64, 0, 0, 0, 1, 1, Color.toFloatBits(.7f, .4f, .9f, .5f), (byte) 0, FrameRenderQueue.SOURCE_ECS, 0, entity);
                    float[] vertices = new float[48];
                    int[] corners = {0, 1, 2, 0, 2, 3};
                    float[] x = {0, 0, 64, 64}, y = {0, 32, 32, 0};
                    for (int i = 0; i < 6; i++) {
                        int c = corners[i], o = i * 8;
                        vertices[o] = LightingCoordinates.worldX(x[c], y[c], 0);
                        vertices[o + 1] = LightingCoordinates.worldY(x[c], y[c], 0);
                        vertices[o + 3] = c >= 2 ? 1 : 0;
                        vertices[o + 4] = c == 1 || c == 2 ? 1 : 0;
                        vertices[o + 7] = 1;
                    }
                    LightingSurface surface = new LightingSurface(vertices);
                    surface.forwardTopology = games.pixscape.runtime.render.lighting.ForwardTopology.NATIVE;
                    q.lighting.entities.put(entity, surface);
                    LightingFrame.Light light = new LightingFrame.Light();
                    light.x = 0;
                    light.y = 0;
                    light.z = 128;
                    light.radius = 512;
                    light.falloff = 1;
                    light.intensity = 1;
                    light.r = light.g = light.b = 1;
                    light.resolution = 512;
                    q.lighting.lights.add(light);
                    byte[][] reference = new byte[2][];
                    for (boolean normals : new boolean[]{true, false}) {
                        meta.useNormalMaps = normals;
                        for (int cycle = 0; cycle < 2; cycle++)
                            for (boolean forward : new boolean[]{false, true}) {
                                history.execute(new games.pixscape.studio.history.commands.SetSceneRendererCommand(world, history.historyIds(), meta, forward));
                                submit.prepareComposition();
                                Object prepared = submit.forwardRenderer();
                                submit.prepareComposition();
                                Assert.assertSame(prepared, submit.forwardRenderer());
                                Gdx.gl.glViewport(0, 0, 64, 32);
                                Gdx.gl.glClearColor(.1f, .2f, .3f, .4f);
                                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                                submit.beginFrameSubmission();
                                try {
                                    world.process();
                                } finally {
                                    submit.endFrameSubmission();
                                }
                                ByteBuffer pixels = BufferUtils.newByteBuffer(64 * 32 * 4);
                                Gdx.gl.glReadPixels(0, 0, 64, 32, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
                                byte[] image = new byte[pixels.capacity()];
                                pixels.get(image);
                                int mode = forward ? 1 : 0;
                                if (reference[mode] == null) reference[mode] = image;
                                else Assert.assertArrayEquals(reference[mode], image);
                                if (forward)
                                    Assert.assertEquals(6L * (normals ? 128 : 100), submit.forwardRenderer().uploadedBytes);
                                Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                            }
                    }
                } catch (Throwable error) {
                    failure[0] = error;
                } finally {
                    if (world != null) world.dispose();
                    if (batch != null) batch.close();
                    if (bundle != null) bundle.textureArray.dispose();
                    if (texture != null) texture.dispose();
                    ShaderRegistry.disposeAll();
                    games.pixscape.studio.event.EventFlow.i().discardPending();
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Renderer selection GL fixture failed", failure[0]);
    }

    @Test
    public void litFractionalEdgesAndIndependentLightsRestoreTarget() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(768, 576);
        config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        org.lwjgl.glfw.GLFW.nglfwSetErrorCallback(0L);
        new Lwjgl3Application(new ApplicationAdapter() {
            boolean interrupt;
            World world;
            games.pixscape.runtime.service.AtlasRuntimeService.TextureArrayBundle bundle;
            games.pixscape.studio.batch.TextureArrayMeshBatchStudio batch;
            Texture texture;

            @Override
            public void create() {
                try {
                    games.pixscape.runtime.component.spatial.SpatialBlocksComponent linkedSnapshot = SpatialLinkGlChecks.run();
                    SpatialGestureGlChecks.run();
                    ShaderRegistry.initDefaults();
                    games.pixscape.studio.configuration.ProjectConfig project = new games.pixscape.studio.configuration.ProjectConfig();
                    games.pixscape.studio.configuration.SceneMeta meta = new games.pixscape.studio.configuration.SceneMeta("test", "test.json");
                    meta.lightingEnabled = true;
                    meta.ambientMulR = meta.ambientMulG = meta.ambientMulB = .2f;
                    project.getScenesMap().put("test", meta);
                    project.setCurrentSceneByName("test");
                    games.pixscape.studio.configuration.ProjectConfig.setInstance(project);
                    Gdx.gl.glViewport(0, 0, 64, 32);
                    Pixmap pix = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                    pix.setColor(.4f, .2f, .1f, .5f);
                    pix.fill();
                    texture = new Texture(pix);
                    int handle = TextureRegistry.handleOf(texture);
                    FrameRenderQueue q = new FrameRenderQueue(4);
                    LayerStateSOA layers = new LayerStateSOA(1);
                    layers.enabled[0] = true;
                    OrthographicCamera camera = new OrthographicCamera(64, 32);
                    camera.position.set(32, 16, 0);
                    camera.update();
                    RenderStats stats = new RenderStats();
                    batch = new games.pixscape.studio.batch.TextureArrayMeshBatchStudio(64);
                    com.badlogic.gdx.utils.Array<Texture> textures = new com.badlogic.gdx.utils.Array<>();
                    textures.add(texture);
                    Pixmap cutoutPixels = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                    cutoutPixels.setColor(.4f, .2f, .1f, .75f);
                    cutoutPixels.fill();
                    Texture cutoutTexture = new Texture(cutoutPixels);
                    textures.add(cutoutTexture);
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(textures, 1, 1, GLCaps.detect());
                    pix.dispose();
                    cutoutPixels.dispose();
                    batch.setTextureArrayBundle(bundle);
                    StudioRenderSubmitSystem submit = new StudioRenderSubmitSystem(layers, q, camera, batch, stats, new RenderStatsSink(100)).setSceneSettings(meta);
                    world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
                        protected void processSystem() {
                            if (interrupt) throw new IllegalStateException("interrupted");
                        }
                    }, submit).build());
                    int entity = world.create();
                    world.getMapper(PointLightComponent.class).create(entity);
                    world.process();
                    q.addQuad(handle, ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName()), 1, 0, 0, 0, 0,
                            0, 0, 0, 32, 64, 32, 64, 0, 0, 0, 1, 1, Color.WHITE.toFloatBits(), (byte) 0, FrameRenderQueue.SOURCE_ECS, 0, entity);
                    float[] v = new float[48];
                    int[] corner = {0, 1, 2, 0, 2, 3};
                    float[] sx = {0, 0, 64, 64}, sy = {0, 32, 32, 0};
                    for (int i = 0; i < 6; i++) {
                        int c = corner[i], o = i * 8;
                        v[o] = LightingCoordinates.worldX(sx[c], sy[c], 0);
                        v[o + 1] = LightingCoordinates.worldY(sx[c], sy[c], 0);
                        v[o + 3] = c >= 2 ? 1 : 0;
                        v[o + 4] = c == 1 || c == 2 ? 1 : 0;
                        v[o + 7] = 1;
                    }
                    q.lighting.entities.put(entity, new LightingSurface(v));
                    LightingFrame.Light light = new LightingFrame.Light();
                    light.x = 32;
                    light.y = 0;
                    light.z = 128;
                    light.radius = 512;
                    light.falloff = 2;
                    light.intensity = 1;
                    light.r = light.g = light.b = 1;
                    light.quality = 0;
                    light.resolution = 512;
                    q.lighting.lights.add(light);
                    submit.prepareComposition();
                    world.process();
                    Assert.assertEquals(0, stats.drawCalls);
                    interrupt = true;
                    submit.beginFrameSubmission();
                    try {
                        world.process();
                        Assert.fail();
                    } catch (IllegalStateException expected) {
                    } finally {
                        submit.endFrameSubmission();
                    }
                    interrupt = false;
                    world.process();
                    Assert.assertEquals(0, stats.drawCalls);
                    Gdx.gl.glClearColor(0, 0, 0, 0);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    submit.beginFrameSubmission();
                    try {
                        world.process();
                    } finally {
                        submit.endFrameSubmission();
                    }
                    ByteBuffer pixel = BufferUtils.newByteBuffer(4);
                    Gdx.gl.glReadPixels(32, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
                    int one = pixel.get(0) & 255;
                    Assert.assertTrue("Partial alpha must receive light, red=" + one, one > 10);
                    q.lighting.lights.add(light);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    submit.beginFrameSubmission();
                    try {
                        world.process();
                    } finally {
                        submit.endFrameSubmission();
                    }
                    pixel.clear();
                    Gdx.gl.glReadPixels(32, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
                    int two = pixel.get(0) & 255;
                    Assert.assertTrue("Independent light must increase contribution", two > one);
                    light.quality = 2;
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    submit.beginFrameSubmission();
                    try {
                        world.process();
                    } finally {
                        submit.endFrameSubmission();
                    }
                    Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    Assert.assertEquals(128, pixel.get(3) & 255, 3);
                    light.quality = 0;
                    q.lighting.lights.clear();
                    q.lighting.lights.add(light);
                    q.lighting.entities.get(entity).forwardTopology = ForwardTopology.NATIVE;
                    verifyJunction(world, submit, meta, q, layers, camera, linkedSnapshot, light);
                    light.x = 32;
                    light.y = 0;
                    light.z = 128;
                    light.radius = 512;
                    light.intensity = 1;
                    light.quality = 0;
                    for (boolean forward : new boolean[]{false, true}) {
                        meta.forwardLighting = forward;
                        submit.prepareComposition();
                        Assert.assertEquals(forward, submit.isForwardEffective());
                        for (int blend : new int[]{1, 2, 0, 7}) {
                            q.textureHandle[0] = blend == 7 ? TextureRegistry.handleOf(cutoutTexture) : handle;
                            q.blend[0] = blend;
                            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                            submit.beginFrameSubmission();
                            try {
                                world.process();
                            } finally {
                                submit.endFrameSubmission();
                            }
                            pixel.clear();
                            Gdx.gl.glReadPixels(32, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
                            String mode = "blend=" + blend + ", forward=" + forward;
                            Assert.assertEquals("Coverage/color rule for " + mode,
                                    blend == 1 ? 39 : 78, pixel.get(0) & 255, 3);
                            Assert.assertEquals("Alpha rule for " + mode,
                                    blend == 0 || blend == 7 ? 255 : 128, pixel.get(3) & 255, 3);
                        }
                    }
                    meta.forwardLighting = false;
                    q.blend[0] = 1;
                    q.textureHandle[0] = handle;
                    cutoutTexture.dispose();
                    // Unsupported custom coverage fails explicitly, aborts, then permits a new frame.
                    int originalShader = q.shader[0];
                    com.badlogic.gdx.graphics.glutils.ShaderProgram custom = new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                            "#version 330 core\nin vec2 a_position;uniform mat4 u_projTrans;void main(){gl_Position=u_projTrans*vec4(a_position,0,1);}",
                            "#version 330 core\nout vec4 c;void main(){c=vec4(.4,.2,.1,.5);}");
                    Assert.assertTrue(custom.getLog(), custom.isCompiled());
                    q.shader[0] = ShaderRegistry.register("lighting-unsupported-custom", custom, ShaderMode.MULTI_TEXTURE);
                    com.badlogic.gdx.graphics.glutils.FrameBuffer nested = new com.badlogic.gdx.graphics.glutils.FrameBuffer(Pixmap.Format.RGBA8888, 64, 32, false);
                    nested.begin();
                    Gdx.gl.glViewport(2, 3, 60, 28);
                    Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);
                    Gdx.gl.glScissor(4, 5, 52, 20);
                    submit.prepareComposition();
                    submit.beginFrameSubmission();
                    try {
                        world.process();
                        Assert.fail("Custom coverage needs an explicit adapter");
                    } catch (IllegalArgumentException expected) {
                        Assert.assertTrue(expected.getMessage().contains("unsupported shader"));
                    } finally {
                        submit.endFrameSubmission();
                    }
                    java.nio.IntBuffer state = BufferUtils.newIntBuffer(4);
                    Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING, state);
                    Assert.assertEquals(nested.getFramebufferHandle(), state.get(0));
                    state.clear();
                    Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT, state);
                    Assert.assertEquals(2, state.get(0));
                    Assert.assertEquals(60, state.get(2));
                    Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                    q.shader[0] = originalShader;
                    Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);
                    Gdx.gl.glViewport(0, 0, 64, 32);
                    submit.prepareComposition();
                    submit.beginFrameSubmission();
                    try {
                        world.process();
                    } finally {
                        submit.endFrameSubmission();
                    }
                    Assert.assertEquals(GL20.GL_NO_ERROR, Gdx.gl.glGetError());
                    nested.end();
                    nested.dispose();
                    world.dispose();
                    world = null;
                    batch.close();
                    bundle.textureArray.dispose();
                    texture.dispose();
                    ShaderRegistry.disposeAll();
                } catch (Throwable error) {
                    failure[0] = error;
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Real GL 2.5D test failed", failure[0]);
    }

    private static void verifyJunction(World world, StudioRenderSubmitSystem submit, SceneMeta meta,
                                       FrameRenderQueue queue, LayerStateSOA layers, OrthographicCamera camera,
                                       SpatialBlocksComponent joined, LightingFrame.Light light) {
        for (var wall : joined.blocks) wall.shadowCaster = true;
        SpatialBlocksComponent continuous = new SpatialBlocksComponent();
        var single = joined.blocks.first().copy();
        var last = joined.blocks.peek();
        single.width = last.x + last.width - single.x;
        for (var cell : last.linkedTileRefs) single.addLinkedTileRef(cell.gx, cell.gy, cell.tileAssetId);
        Assert.assertEquals(5, single.linkedTileRefs.size);
        continuous.blocks.add(single);

        // An 8x4 native cell plus a 90-pixel wall, anchored at the foot of the tile.
        Pixmap pixels = new Pixmap(8, 94, Pixmap.Format.RGBA8888);
        pixels.setColor(Color.WHITE);
        pixels.fill();
        var atlas = new TextureAtlas();
        atlas.addRegion("wall__a1", new Texture(pixels), 0, 0, 8, 94);
        pixels.dispose();
        var assets = new AtlasRuntimeService();
        assets.loadBorrowed("walls", atlas);
        var profile = new RuntimeTilesetProfile();
        profile.tilesetId = 1;
        profile.tileAssetIds = new int[]{1};
        profile.referenceCellWidth = 8;
        profile.referenceCellHeight = 4;
        profile.anchor = RuntimeTilesetAnchor.BOTTOM_CENTER;
        profile.projection = TiledProjection.ISO;
        var profiles = RuntimeTilesetProfiles.empty();
        profiles.add(profile);

        // Publish the actual tile quads, caster contributions, shadow groups and spatial index.
        var tiledState = new TiledMapRenderState();
        var sync = new RenderTiledSyncSystem(camera, tiledState, assets, queue.shader[0], null, profiles);
        var geometry = new LightingGeometrySystem(new DynamicEntityRenderState(), tiledState, queue, layers);
        World publisher = new World(new WorldConfigurationBuilder().with(sync, geometry).build());
        try {
            int owner = publisher.create();
            publisher.getMapper(EntityIndexComponent.class).create(owner);
            var tiled = publisher.getMapper(TiledLayerComponent.class).create(owner);
            tiled.atlasTag = "walls";
            tiled.projection = TiledProjection.ISO;
            tiled.tileWidth = 8;
            tiled.tileHeight = 4;
            tiled.chunkSize = 4;
            tiled.mapWidthCells = tiled.mapHeightCells = 12;
            tiled.data = tiled.createMapData();
            for (var cell : single.linkedTileRefs) tiled.data.setTile(cell.gx, cell.gy, cell.tileAssetId);
            var current = publisher.getMapper(SpatialBlocksComponent.class).create(owner);

            light.x = -12;
            light.y = -12;
            light.z = 120;
            light.intensity = 3;
            light.quality = 1;
            var empty = new SpatialBlocksComponent();
            for (boolean forward : new boolean[]{false, true}) {
                meta.forwardLighting = forward;
                byte[] lit = renderWalls(world, submit, publisher, current, empty, queue, light);
                Assert.assertEquals(forward, submit.isForwardEffective());
                byte[] shadow = renderWalls(world, submit, publisher, current, joined, queue, light);
                int shadowPixels = 0;
                for (int i = 0; i < lit.length; i += 4) {
                    if ((lit[i] & 255) > (shadow[i] & 255) + 2) shadowPixels++;
                }
                Assert.assertTrue("The junction must cast a visible shadow, pixels=" + shadowPixels,
                        shadowPixels > 5);
                byte[] reference = renderWalls(world, submit, publisher, current, continuous, queue, light);
                Assert.assertArrayEquals("No shadow seam, forward=" + forward, reference, shadow);
            }
            current.blocks.clear();
            current.links.clear();
            current.revision++;
            publisher.process();
            queue.lighting.lights.add(light);
        } finally {
            publisher.dispose();
            assets.unload("walls");
            atlas.dispose();
            meta.forwardLighting = false;
        }
    }

    private static byte[] renderWalls(World world, StudioRenderSubmitSystem submit, World publisher,
                                      SpatialBlocksComponent current, SpatialBlocksComponent walls, FrameRenderQueue queue,
                                      LightingFrame.Light light) {
        current.blocks.clear();
        current.links.clear();
        current.blocks.addAll(walls.blocks);
        current.links.addAll(walls.links);
        current.revision++;
        publisher.process();
        queue.lighting.lights.add(light);
        int expectedCasters = walls.blocks.size == 0 ? 0 : 5;
        Assert.assertEquals("Published tile casters", expectedCasters, queue.lighting.staticCasterCount);
        submit.prepareComposition();
        submit.lightingRenderer().prepareFrame(queue.lighting);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        submit.beginFrameSubmission();
        try {
            world.process();
        } finally {
            submit.endFrameSubmission();
        }
        Assert.assertEquals("Selected tile casters", expectedCasters, submit.lightingRenderer().selectedCasters);
        ByteBuffer pixels = BufferUtils.newByteBuffer(64 * 32 * 4);
        Gdx.gl.glReadPixels(0, 0, 64, 32, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixels);
        byte[] image = new byte[pixels.capacity()];
        pixels.get(image);
        return image;
    }
}
