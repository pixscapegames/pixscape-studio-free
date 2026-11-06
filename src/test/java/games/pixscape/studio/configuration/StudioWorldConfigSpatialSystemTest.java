package games.pixscape.studio.configuration;

import com.artemis.World;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.render.VfxRenderState;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.system.RenderExtractFrameQueueSystem;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.system.StudioParticleFallbackSystem;
import games.pixscape.studio.system.StudioRenderSubmitSystem;
import games.pixscape.studio.ui.main.DockingTestFixture;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import org.lwjgl.glfw.GLFW;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class StudioWorldConfigSpatialSystemTest {


    @Rule public final TemporaryFolder home = new TemporaryFolder();

    @Test
    public void studioViewportUsesRuntimeWorldFactoryAndStudioSubmitSystem() throws Exception {
        String previousHome = System.getProperty("user.home");
        ProjectConfig previousConfig = ProjectConfig.getInstance();
        Lwjgl3ApplicationConfiguration gl = new Lwjgl3ApplicationConfiguration();
        gl.setInitialVisible(false);
        gl.disableAudio(true);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        try (var globals = new StudioGlTestScope()) {
            System.setProperty("user.home", home.getRoot().getAbsolutePath());
            ProjectConfig.setInstance(new ProjectConfig());
            // LWJGL can leave the native error callback pointing to a previous test's freed callback.
            GLFW.nglfwSetErrorCallback(0L);
            new Lwjgl3Application(new ApplicationAdapter() {
                final StudioApplicationAdapter studio = new StudioApplicationAdapter();

                @Override public void create() {
                    try {
                        studio.create();
                        assertViewportConfiguration(studio.getCanvas().getEcsWorld());
                    } finally {
                        Gdx.app.exit();
                    }
                }

                @Override public void dispose() {
                    studio.dispose();
                    InternalTextures.dispose();
                    TextureRegistry.clear();
                    ShaderRegistry.disposeAll();
                }
            }, gl);
        } finally {
            GLFW.nglfwSetErrorCallback(0L);
            ProjectConfig.setInstance(previousConfig);
            System.setProperty("user.home", previousHome);
        }
    }

    private static void assertViewportConfiguration(World world) {
        var extract = world.getSystem(RenderExtractFrameQueueSystem.class);
        var submit = world.getSystem(StudioRenderSubmitSystem.class);
        var particles = world.getSystem(StudioParticleFallbackSystem.class);
        assertNotNull(extract);
        assertNotNull(submit);
        assertNotNull(particles);
        try {
            FrameRenderQueue queue = DockingTestFixture.field(extract, "frameQueue", FrameRenderQueue.class);
            assertSame(queue, DockingTestFixture.field(submit, "frameQueue", FrameRenderQueue.class));
            assertFalse("The initial viewport has no scene lighting", submit.isLightingEnabled());
            assertNull("An unlit viewport must not allocate a lighting frame", queue.lighting);
            var vfx = DockingTestFixture.field(extract, "vfxState", VfxRenderState.class);
            assertNotNull(vfx);
            assertSame(vfx, DockingTestFixture.field(particles, "vfxState", VfxRenderState.class));
        } catch (ReflectiveOperationException ex) {
            throw new AssertionError(ex);
        }
    }

    @Test
    public void studioViewportPassesTilesetProfilesToRuntimeTiledSync() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");

        assertTrue(source.contains("studioTilesetProfiles = StudioTilesetProfileResolver.buildRuntimeProfiles(assetMetaDatabaseForFallback);"));
        assertTrue(source.contains("tileAnimationRegistry,\n                        animationRegistry,\n                        studioTilesetProfiles,\n                        systemProfiler,"));
        assertTrue(source.contains("public void publishAssetMetaDatabase(AssetMetaDatabase assetMetaDatabase)"));
    }

    @Test
    public void assetImportsRefreshStudioTilesetProfileRegistryFromLiveDatabase() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        assertPublishesSavedMetadata(
                methodBody(source, "public void importAssets("),
                "assetMetaDatabase.save(ctx.projectDir.child(StudioFs.FILE_ASSETS_JSON));");
        assertPublishesSavedMetadata(
                methodBody(source, "private TilesetImportResult applyTilesetImportPlan("),
                "assetMetaDatabase.save(project.child(StudioFs.FILE_ASSETS_JSON));");
    }

    @Test
    public void authoritativeAssetMetadataPublicationRebindsAllConsumersAndRegistries() throws Exception {
        String canvas = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        String publish = methodBody(canvas, "public void publishAssetMetaDatabase(");
        String bind = methodBody(canvas, "public void bindAssetMetaLookup(");
        String derived = methodBody(canvas, "private void reloadDerivedAssetMetadata(");

        assertTrue(publish.contains("bindAssetMetaLookup(published::findById);"));
        assertTrue(publish.contains("reloadDerivedAssetMetadata(published);"));
        assertTrue(bind.contains("assetVisualResolver.setAssetMetaLookup(assetMetaLookup);"));
        assertTrue(bind.contains("animationPreviewRefresher.setAssetMetaLookup(assetMetaLookup);"));
        assertTrue(bind.contains("animationFallbackSystem.setAssetMetaLookup(assetMetaLookup);"));
        assertTrue(bind.contains("tiledFallbackSystem.setAssetMetaLookup(assetMetaLookup);"));
        assertTrue(bind.contains("tiledGhostPreviewSystem.setAssetMetaLookup(assetMetaLookup);"));
        assertTrue(derived.contains("reloadAnimationRegistry(assetMetaDatabase);"));
        assertTrue(derived.contains("StudioTilesetProfileResolver.reloadRuntimeProfiles("));
    }

    @Test
    public void animationClipSavePublishesBeforeEntityReconciliation() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String save = methodBody(source, "public void saveAnimationAssetClips(");

        int validate = save.indexOf("StudioAnimationAssets.validate(animation);");
        int persist = save.indexOf("reloadedDatabase.save(assetsFile);");
        int install = save.indexOf("assetMetaDatabase = reloadedDatabase;");
        int publish = save.indexOf("canvas.publishAssetMetaDatabase(assetMetaDatabase);");
        int reconcile = save.indexOf("AnimationAssetEntityReconciler.reconcile(");
        assertTrue(validate >= 0);
        assertTrue(validate < persist);
        assertTrue(persist < install);
        assertTrue(install < publish);
        assertTrue(publish < reconcile);
    }

    @Test
    public void sceneReloadRefreshesStudioTilesetProfileRegistryBeforeAtlasRebind() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/service/SceneService.java");
        String method = methodBody(source, "private void rebuildRenderRuntimeForScene(");

        int refresh = method.indexOf("publishStudioAssetMetadata(projectDir);");
        int loadAtlas = method.indexOf("SceneAtlasLoaderService.loadSceneAtlas(");
        int rebind = method.indexOf("rebindTiles();");

        assertTrue(refresh >= 0);
        assertTrue(refresh < loadAtlas);
        assertTrue(loadAtlas < rebind);
    }

    @Test
    public void spatialBlockModeSuppressesTiledPreviewGhost() throws Exception {
        String source = read("src/main/java/games/pixscape/studio/ui/main/WorldCanvas.java");
        int methodStart = source.indexOf("private void updateTiledPreview()");
        int methodEnd = source.indexOf("private boolean isTiledToolInputEnabled()", methodStart);

        assertTrue(methodStart >= 0);
        assertTrue(methodEnd > methodStart);

        String method = source.substring(methodStart, methodEnd);
        int guard = method.indexOf("if (!isTiledToolInputEnabled())");
        int guardClear = method.indexOf("tiledPreviewService.clear();", guard);
        int guardReturn = method.indexOf("return;", guardClear);
        int tintedPreview = method.indexOf("tiledPreviewService.showTintedCoverage(");
        int ghostPreview = method.indexOf("tiledPreviewService.show(");

        assertTrue(guard >= 0);
        assertTrue(guard < guardClear);
        assertTrue(guardClear < guardReturn);
        assertTrue(guard < tintedPreview);
        assertTrue(guard < ghostPreview);
    }

    private static String read(String path) throws Exception {
        return Files.readString(Path.of(path), StandardCharsets.UTF_8).replace("\r\n", "\n");
    }

    private static void assertPublishesSavedMetadata(String method, String saveStatement) {
        int save = method.indexOf(saveStatement);
        int publish = method.indexOf("canvas.publishAssetMetaDatabase(assetMetaDatabase);");
        int refresh = method.indexOf("refreshAssetsPanel();", publish);

        assertTrue(save >= 0);
        assertTrue(save < publish);
        assertTrue(publish < refresh);
    }

    private static String methodBody(String source, String signaturePrefix) {
        int signatureIndex = source.indexOf(signaturePrefix);
        if (signatureIndex < 0) throw new AssertionError("Method signature not found: " + signaturePrefix);

        int bodyStart = source.indexOf('{', signatureIndex);
        if (bodyStart < 0) throw new AssertionError("Method body start not found: " + signaturePrefix);

        int depth = 0;
        for (int i = bodyStart; i < source.length(); i++) {
            char c = source.charAt(i);
            if (c == '{') depth++;
            if (c == '}') {
                depth--;
                if (depth == 0) {
                    return source.substring(bodyStart + 1, i);
                }
            }
        }
        throw new AssertionError("Method body end not found: " + signaturePrefix);
    }
}
