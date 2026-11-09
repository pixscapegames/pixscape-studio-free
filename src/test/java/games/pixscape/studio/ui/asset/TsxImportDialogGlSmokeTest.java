package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.TileAssetMeta;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import org.junit.Test;

import java.nio.file.Files;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

import static games.pixscape.studio.ui.asset.TilesetImportDialogGlSmokeTest.*;

/** Real desktop import dialogs and async progress on disposable projects. */
public class TsxImportDialogGlSmokeTest {
    @Test public void tsxProgressPersistsBeforePublicationAndCollectionChoicesKeepDefinitionsDistinct() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_TILESET_IMPORT_GL_TEST")));
        ProjectConfig original = ProjectConfig.getInstance(); String home = System.getProperty("user.home");
        FileHandle root = new FileHandle(Files.createTempDirectory("tsx-properties-ui").toFile());
        System.setProperty("user.home", root.child("home").path());
        ProjectConfig cfg = new ProjectConfig(); cfg.projectDirectoryPath = root.path(); cfg.projectFileName = "test.json";
        cfg.projectTitle = "TSX regression"; cfg.createSceneMeta("Main");
        root.child("scenes/scene1.json").writeString("{}", false);
        cfg.exportRootPathDir = root.child("export").path();
        ProjectConfig.setInstance(cfg); Throwable[] failure = {null};
        var gl = new Lwjgl3ApplicationConfiguration(); gl.setTitle("TSX properties import regression");
        gl.setWindowedMode(1100, 850); gl.setInitialVisible(false); gl.disableAudio(true);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        try (var globals = new games.pixscape.studio.service.StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio; AssetMetaDatabase db;
                FileHandle atlas, first, second; int step, baseId, firstId, secondId, animationId;
                String snapshot; byte[] saved; int busyFrames;
                long started = System.nanoTime();
                @Override public void create() {
                    try {
                        studio = new StudioApplicationAdapter(); studio.create();
                        db = new AssetMetaDatabase(); db.save(root.child("assets.json"));
                        var field = games.pixscape.studio.service.SceneService.class.getDeclaredField("assetMetaDatabase");
                        field.setAccessible(true); field.set(studio.getSceneService(), db);
                        png(root.child("external/shared.png"), 16, 8, 0x112233ff);
                        atlas = root.child("external/atlas.tsx");
                        atlas.writeString("<tileset name=\"atlas\" tilewidth=\"8\" tileheight=\"8\" tilecount=\"2\" columns=\"2\">"
                                + "<image source=\"shared.png\" width=\"16\" height=\"8\"/>"
                                + "<tile id=\"0\"><properties><property name=\"cost\" type=\"int\" value=\"3\"/></properties>"
                                + "<animation><frame tileid=\"1\" duration=\"100\"/></animation></tile>"
                                + "<tile id=\"1\"><properties><property name=\"cost\" type=\"int\" value=\"99\"/></properties></tile></tileset>", false);
                        first = collectionTsx(root, "first", 1); second = collectionTsx(root, "second", 9);
                        tsxDialog(studio, atlas); click(review(studio), "Import"); step = 1;
                    } catch (Throwable error) { stop(error); }
                }
                @Override public void render() {
                    if (failure[0] != null) return;
                    try {
                        studio.render();
                        if (System.nanoTime() - started > 90_000_000_000L) throw new AssertionError("TSX import timed out");
                        if (studio.getSceneService().isTilesetImportInProgress()) { busyFrames++; return; }
                        switch (step) {
                            case 1 -> {
                                TileAssetMeta tile = (TileAssetMeta) db.findByLogicalPath("tiles/atlas/0"); baseId = tile.id();
                                assertCost(root, db, baseId, 0, 3);
                                var profilesField = games.pixscape.studio.ui.main.WorldCanvas.class.getDeclaredField("studioTilesetProfiles");
                                profilesField.setAccessible(true);
                                var published = (games.pixscape.runtime.tiled.profile.RuntimeTilesetProfiles) profilesField.get(studio.getCanvas());
                                assertEquals(3, published.tile(tile.tilesetId, 0).properties().getInt("cost", -1));
                                var animations = games.pixscape.studio.io.TileAnimationsIO.load(root.child(games.pixscape.runtime.helper.RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
                                assertEquals(1, animations.animations.size);
                                var animation = animations.animations.first(); assertEquals(baseId, animation.baseTileAssetId);
                                animationId = animation.id;
                                assertNotEquals(baseId, animation.frameAssetIds[0]);
                                assertTrue(AssetMetaDatabase.load(root.child("assets.json")).nextId() > animation.id);
                                assertEquals(99, ((TileAssetMeta) db.findById(animation.frameAssetIds[0])).properties.getInt("cost", -1));
                                cfg.getCurrentSceneMeta().runtimeAvailability.tiledAnimationIds.add(animation.id);
                                assertCostExport(root, cfg, tile, 3);
                                tsxDialog(studio, first); click(review(studio), "Import"); step = 2;
                            }
                            case 2 -> {
                                firstId = db.findByLogicalPath("tiles/same/7").id(); assertCost(root, db, firstId, 7, 1);
                                tsxDialog(studio, second); click(review(studio), "Import as new"); step = 3;
                            }
                            case 3 -> {
                                secondId = db.findByLogicalPath("tiles/same-2/7").id(); assertNotEquals(firstId, secondId);
                                assertCost(root, db, firstId, 7, 1); assertCost(root, db, secondId, 7, 9);
                                assertNotEquals(((TileAssetMeta) db.findById(firstId)).tilesetId, ((TileAssetMeta) db.findById(secondId)).tilesetId);
                                snapshot = db.toSnapshotJson(); saved = root.child("assets.json").readBytes();
                                tsxDialog(studio, second); click(review(studio), "Cancel"); step = 4;
                            }
                            case 4 -> {
                                assertEquals(snapshot, db.toSnapshotJson()); assertArrayEquals(saved, root.child("assets.json").readBytes());
                                // A third file has no exact provenance match: Update targets the same-name set.
                                FileHandle replacement = collectionTsx(root, "replacement", 9);
                                tsxDialog(studio, replacement);
                                assertTrue(find(review(studio), VisSelectBox.class, actor -> true).getSelected().toString().contains("tiles/same ("));
                                click(review(studio), "Update"); step = 5;
                            }
                            case 5 -> {
                                assertEquals(firstId, db.findByLogicalPath("tiles/same/7").id());
                                assertCost(root, db, firstId, 7, 9); assertCost(root, db, secondId, 7, 9);
                                assertCost(root, db, baseId, 0, 3);
                                cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(firstId);
                                cfg.getCurrentSceneMeta().runtimeAvailability.tiledTileAssetIds.add(secondId);
                                assertCostExport(root, cfg, (TileAssetMeta) db.findById(firstId), 9);
                                assertCostExport(root, cfg, (TileAssetMeta) db.findById(secondId), 9);
                                // The synchronous entry point must also persist complete metadata.
                                ImportDialog.ImportItem item = new ImportDialog.ImportItem(atlas);
                                studio.getSceneService().importAssets(new Array<>(new ImportDialog.ImportItem[]{item}));
                                assertCost(root, db, db.findByLogicalPath("tiles/atlas-2/0").id(), 0, 3);
                                assertTrue("The async path must run", busyFrames > 0);
                                studio.getSceneService().removeFrameFromTileAnimation(animationId, 0);
                                studio.getSceneService().addTileToTileAnimation(animationId, firstId);
                                atlas.writeString(atlas.readString().replace("value=\"3\"", "value=\"5\""), false);
                                tsxDialog(studio, atlas);
                                find(review(studio), VisSelectBox.class, actor -> true).setSelectedIndex(1);
                                click(review(studio), "Update"); step = 6;
                            }
                            case 6 -> {
                                assertCost(root, db, baseId, 0, 5);
                                var animations = games.pixscape.studio.io.TileAnimationsIO.load(root.child(games.pixscape.runtime.helper.RuntimeFs.FILE_TILE_ANIMATIONS_JSON));
                                var animation = animations.animations.first();
                                assertEquals(animationId, animation.id); assertEquals(baseId, animation.baseTileAssetId);
                                assertArrayEquals(new int[]{firstId}, animation.frameAssetIds);
                                assertCost(root, db, firstId, 7, 9);
                                assertCostExport(root, cfg, (TileAssetMeta) db.findById(baseId), 5);
                                step = 7; Gdx.app.exit();
                            }
                        }
                    } catch (Throwable error) { stop(error); }
                }
                void stop(Throwable error) { failure[0] = error; Gdx.app.exit(); }
                @Override public void dispose() { if (studio != null) studio.dispose(); }
            }, gl);
        } finally { ProjectConfig.setInstance(original); System.setProperty("user.home", home); }
        if (failure[0] != null) throw new AssertionError("TSX properties UI regression", failure[0]);
    }

    private static void assertCost(FileHandle root, AssetMetaDatabase db, int id, int local, int cost) {
        TileAssetMeta live = (TileAssetMeta) db.findById(id);
        TileAssetMeta saved = (TileAssetMeta) AssetMetaDatabase.load(root.child("assets.json")).findById(id);
        assertEquals(local, live.sheetIndex); assertEquals(local, saved.sheetIndex);
        assertEquals(cost, live.properties.getInt("cost", -1)); assertEquals(cost, saved.properties.getInt("cost", -1));
    }

    private static void assertCostExport(FileHandle root, ProjectConfig cfg, TileAssetMeta tile, int cost) {
        games.pixscape.studio.configuration.RuntimeExport.exportRuntime(cfg, root, root.child("export"));
        var profiles = games.pixscape.runtime.loading.RuntimeProjectIO.loadTilesetProfiles(
                root.child("export").child(games.pixscape.studio.configuration.RuntimeExport.RUNTIME_DIR_NAME));
        assertEquals(cost, profiles.tile(tile.tilesetId, tile.sheetIndex).properties().getInt("cost", -1));
    }

    private static FileHandle collectionTsx(FileHandle root, String name, int cost) {
        FileHandle file = root.child("external/" + name + ".tsx");
        file.writeString("<tileset name=\"same\" tilewidth=\"16\" tileheight=\"8\" tilecount=\"1\" columns=\"0\">"
                + "<tile id=\"7\"><image source=\"shared.png\" width=\"16\" height=\"8\"/>"
                + "<properties><property name=\"cost\" type=\"int\" value=\"" + cost + "\"/></properties></tile></tileset>", false);
        return file;
    }

    private static void tsxDialog(StudioApplicationAdapter studio, FileHandle tsx) throws Exception {
        ImportDialog dialog = new ImportDialog(studio, studio.getSceneService()::importAssets,
                (directory, settings) -> studio.getSceneService().importTilesetDirectory(directory, settings));
        var add = ImportDialog.class.getDeclaredMethod("addOrSkip", FileHandle.class); add.setAccessible(true); add.invoke(dialog, tsx);
        dialog.show(studio.getUiStage()); dialog.applyImportAndCloseIfSuccessful();
    }

}
