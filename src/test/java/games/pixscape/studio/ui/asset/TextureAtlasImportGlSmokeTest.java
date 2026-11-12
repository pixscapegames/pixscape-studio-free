package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.Array;
import games.pixscape.runtime.loading.RuntimeProjectIO;
import games.pixscape.runtime.service.AnimationRegistry;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.RuntimeExport;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.service.atlas.SceneAtlasInputService;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import org.junit.Test;

import java.nio.file.Files;
import java.util.Set;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/**
 * Opt-in real Studio import/preview/export check; uses only a disposable project and preferences.
 */
public class TextureAtlasImportGlSmokeTest {
    @Test
    public void importsDaemonDirectThroughDialogPreviewsReloadsAndExportsWithExistingRuntime() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_ATLAS_IMPORT_GL_TEST")));
        String source = System.getenv("PIXSCAPE_TEST_ATLAS");
        assumeTrue(source != null && !source.isBlank());
        FileHandle root = new FileHandle(Files.createTempDirectory("studio-atlas-import").toFile());
        ProjectConfig original = ProjectConfig.getInstance();
        String home = System.getProperty("user.home");
        ProjectConfig cfg = new ProjectConfig();
        cfg.projectDirectoryPath = root.path();
        cfg.projectFileName = "test.json";
        cfg.createSceneMeta("Main");
        root.child("scenes/scene1.json").writeString("{}", false);
        System.setProperty("user.home", root.child("home").path());
        ProjectConfig.setInstance(cfg);
        Throwable[] failure = {null};
        var gl = new Lwjgl3ApplicationConfiguration();
        gl.setTitle("Texture atlas import regression");
        gl.setWindowedMode(1100, 850);
        gl.setInitialVisible(false);
        gl.disableAudio(true);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        try (var globals = new StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio;

                @Override
                public void create() {
                    try {
                        studio = new StudioApplicationAdapter();
                        studio.create();
                        AssetMetaDatabase db = new AssetMetaDatabase();
                        db.save(root.child(StudioFs.FILE_ASSETS_JSON));
                        var field = SceneService.class.getDeclaredField("assetMetaDatabase");
                        field.setAccessible(true);
                        field.set(studio.getSceneService(), db);
                        ImportDialog dialog = new ImportDialog(studio, studio.getSceneService()::importAssets, null);
                        dialog.show(studio.getUiStage());
                        assertTrue(dialog.onOsFilesDropped(new String[]{source}));
                        dialog.applyImportAndCloseIfSuccessful();
                        assertEquals(11, db.size());

                        // The ordinary Image and grid Sprite sheet paths remain usable in the same project.
                        FileHandle image = root.child("external/ordinary.png");
                        Pixmap pixels = new Pixmap(4, 2, Pixmap.Format.RGBA8888);
                        try {
                            pixels.setColor(0xabcdef80);
                            pixels.fill();
                            PixmapIO.writePNG(image, pixels);
                        } finally {
                            pixels.dispose();
                        }
                        image.copyTo(image.sibling("sheet.png"));
                        var imageItem = new ImportDialog.ImportItem(image);
                        var sheetItem = new ImportDialog.ImportItem(image.sibling("sheet.png"));
                        sheetItem.type = ImportDialog.ImportType.SPRITESHEET;
                        sheetItem.tileWidth = 2;
                        sheetItem.tileHeight = 2;
                        studio.getSceneService().importAssets(new Array<>(new ImportDialog.ImportItem[]{imageItem, sheetItem}));
                        assertEquals(13, db.size());
                        assertEquals(AssetType.IMAGE, db.findByLogicalPath("images/ordinary").type());
                        assertEquals(2, ((AnimationAssetMeta) db.findByLogicalPath("animations/sheet")).frameCount);

                        AssetMetaDatabase loaded = AssetMetaDatabase.load(root.child(StudioFs.FILE_ASSETS_JSON));
                        studio.getCanvas().publishAssetMetaDatabase(loaded);
                        studio.getAssetsPanel().reloadFromProject(cfg);
                        for (int i = 0; i < loaded.size(); i++) {
                            AssetMeta asset = loaded.assetAt(i);
                            int count = asset instanceof AnimationAssetMeta a ? a.frameCount : 1;
                            for (int frame = 0; frame < count; frame++) {
                                var visual = studio.getCanvas().getAssetVisualResolver().resolveFrame(asset.id(), null, frame);
                                assertNotNull(asset.logicalPath() + " frame " + frame, visual);
                                assertTrue(visual.texture().getTextureObjectHandle() > 0);
                                assertTrue(visual.pixelWidth() > 0);
                            }
                            if (asset instanceof AnimationAssetMeta)
                                cfg.getCurrentSceneMeta().runtimeAvailability.animationAssetIds.add(asset.id());
                            else cfg.getCurrentSceneMeta().runtimeAvailability.spriteAssetIds.add(asset.id());
                        }
                        // One existing scene packing operation for all imported assets.
                        var inputs = new SceneAtlasInputService();
                        var world = new com.artemis.World();
                        Set<String> sourcePaths;
                        try {
                            sourcePaths = inputs.collectRequiredAtlasInputPathsForScene(cfg, world,
                                    cfg.getCurrentSceneMeta(), loaded, new TileAnimationsMetaDatabase());
                        } finally {
                            world.dispose();
                        }
                        assertEquals(27, sourcePaths.size()); // 24 atlas frames, 2 grid frames and 1 image.
                        studio.getCanvas().getAtlasService().repackImportedScenes(cfg, root, Set.of("scene1"),
                                () -> inputs.syncSceneAtlasInput(cfg, "scene1", root, sourcePaths), () -> {
                                });
                        FileHandle destination = root.child("export");
                        RuntimeExport.exportRuntime(cfg, root, destination);
                        FileHandle runtimeRoot = destination.child(RuntimeExport.RUNTIME_DIR_NAME);
                        AnimationRegistry animations = new AnimationRegistry();
                        RuntimeProjectIO.loadAnimations(runtimeRoot, animations);
                        assertEquals(12, animations.size());
                        AtlasRuntimeService runtimeAtlas = new AtlasRuntimeService();
                        try {
                            runtimeAtlas.load("import-test", runtimeRoot.child("atlases/scene1.atlas"));
                            for (int i = 0; i < loaded.size(); i++) {
                                AssetMeta asset = loaded.assetAt(i);
                                var binding = runtimeAtlas.resolveBinding(asset.id(), "import-test");
                                assertNotNull(asset.logicalPath(), binding);
                                assertEquals(asset instanceof AnimationAssetMeta a ? a.frameCount : 1, binding.regionCount());
                            }
                        } finally {
                            runtimeAtlas.unloadAll();
                        }
                        assertFalse(runtimeRoot.child("atlases/input").exists());
                        assertFalse(runtimeRoot.child("atlases/.tmp").exists());
                    } catch (Throwable error) {
                        failure[0] = error;
                    } finally {
                        Gdx.app.exit();
                    }
                }

                @Override
                public void dispose() {
                    if (studio != null) studio.dispose();
                }
            }, gl);
        } finally {
            ProjectConfig.setInstance(original);
            System.setProperty("user.home", home);
        }
        if (failure[0] != null) throw new AssertionError("Texture atlas Studio integration failed", failure[0]);
    }
}
