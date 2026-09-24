package games.pixscape.studio.service.asset;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.AnimationComponent;
import games.pixscape.runtime.component.AssetRefComponent;
import games.pixscape.runtime.loading.SceneMetaRuntime;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTooltipData;
import games.pixscape.runtime.hud.document.HudWindowData;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.runtime.tiled.TiledMapLayerData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import org.junit.Test;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertThrows;
import static org.junit.Assert.assertTrue;

public class AssetUsageScannerTest {

    @Test
    public void scanAssetFindsNonActiveAnimationInCurrentScene() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta active = db.registerIfAbsent(
                AssetType.ANIMATION, "animations/idle", "orig/animations/idle",
                AssetMeta.AssetScope.USER);
        AssetMeta nonActive = db.registerIfAbsent(
                AssetType.ANIMATION, "animations/run", "orig/animations/run",
                AssetMeta.AssetScope.USER);
        World world = new World(new WorldConfiguration());
        int entityId = world.create();
        AnimationComponent animation = world.getMapper(AnimationComponent.class).create(entityId);
        animation.animationAssetIds.add(active.id());
        animation.animationAssetIds.add(nonActive.id());
        AssetRefComponent assetRef = world.getMapper(AssetRefComponent.class).create(entityId);
        assetRef.assetId = active.id();

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(world, cfg, db).scanAsset(nonActive.id());

        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.referencedInCurrentLoadedScene());
    }

    @Test
    public void scanAssetFindsAnimationUsageInInactiveSceneFile() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta animation = db.registerIfAbsent(
                AssetType.ANIMATION,
                StudioFs.PREFIX_ANIMATIONS + "Attack 1",
                StudioFs.DIR_ORIG_ANIMATIONS + "/Attack 1__a1",
                AssetMeta.AssetScope.USER
        );

        writeSceneWithAnimationAssetIds(cfg.getSceneMeta("Other"), cfg, animation.id());

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(new World(new WorldConfiguration()), cfg, db)
                        .scanAsset(animation.id());

        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.sceneNames().contains("Other", false));
    }

    @Test
    public void scanAssetFindsParticleUsageWithRuntimeRelativePathInInactiveSceneFile() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta particle = db.registerIfAbsent(
                AssetType.PARTICLE,
                StudioFs.PREFIX_EFFECTS + "fire",
                StudioFs.DIR_ORIG_EFFECTS + "/fire.p",
                AssetMeta.AssetScope.USER
        );

        writeSceneWithParticle(cfg.getSceneMeta("Other"), cfg, "fire.p");

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(new World(new WorldConfiguration()), cfg, db)
                        .scanAsset(particle.id());

        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.sceneNames().contains("Other", false));
    }

    @Test
    public void scanAssetFindsTileUsageInCurrentLoadedScene() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta tile = db.registerIfAbsent(
                AssetType.TILE,
                StudioFs.PREFIX_TILES + "ground/ground_0_0",
                StudioFs.DIR_ORIG_TILES + "/ground/ground_0_0.png",
                AssetMeta.AssetScope.USER
        );

        World world = new World(new WorldConfiguration().setSystem(new BaseSystem() {
            @Override
            protected void processSystem() {
            }
        }));
        int layerId = world.create();
        TiledLayerComponent tiled = world.getMapper(TiledLayerComponent.class).create(layerId);
        tiled.data = new TiledMapLayerData(4, 4, 16, 16, 8, TiledProjection.ORTHO);
        tiled.data.setTile(1, 1, tile.id());
        tiled.tileAssetIds.add(tile.id());
        world.process();

        AssetUsageScanner.AssetUsageReport report =
                new AssetUsageScanner(world, cfg, db).scanAsset(tile.id());

        assertTrue(report.used());
        assertTrue(report.occurrenceCount() >= 1);
        assertTrue(report.sceneNames().contains("Main", false));
    }

    @Test public void bitmapFontUsageIncludesOpenDirtyAndPersistedHudDocuments() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta used = db.registerIfAbsent(AssetType.FONT, "fonts/used", "orig/fonts/1/used.fnt",
                AssetMeta.AssetScope.USER);
        AssetMeta unused = db.registerIfAbsent(AssetType.FONT, "fonts/unused", "orig/fonts/2/unused.fnt",
                AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        HudScreenAsset savedAsset = new HudScreenAsset();
        savedAsset.documentId = "hud/saved.json";
        new HudDocumentPersistenceService().save(project,
                new HudScreenEditorDocument("hud/saved", "Saved HUD", savedAsset, label(used.id())));
        HudScreenAsset dirtyAsset = new HudScreenAsset();
        dirtyAsset.documentId = "hud/dirty.json";
        HudScreenEditorDocument dirty = new HudScreenEditorDocument(
                "hud/dirty", "Dirty HUD", dirtyAsset, label(used.id()));
        AssetUsageScanner scanner = new AssetUsageScanner(null, cfg, db, List.of(dirty));

        AssetUsageScanner.AssetUsageReport report = scanner.scanAsset(used.id());

        assertTrue(report.used());
        assertEquals(2, report.occurrenceCount());
        assertTrue(report.documentNames().contains("hud/dirty", false));
        assertTrue(report.documentNames().contains("saved", false));
        assertFalse(scanner.scanAsset(unused.id()).used());
    }

    @Test public void unreadablePersistedHudBlocksBitmapFontUsageDecision() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta font = db.registerIfAbsent(AssetType.FONT, "fonts/test", "orig/fonts/1/test.fnt",
                AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        project.child("hud/broken.hudscreen").writeString("{not-json", false, "UTF-8");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new AssetUsageScanner(null, cfg, db).scanAsset(font.id()));

        assertTrue(failure.getMessage().contains("broken"));
    }

    @Test public void bitmapFontUsageIncludesEveryNativeTextPayload() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta font = db.registerIfAbsent(AssetType.FONT, "fonts/test",
                "orig/fonts/1/test.fnt", AssetMeta.AssetScope.USER);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/text-widgets.json";
        HudScreenEditorDocument open = new HudScreenEditorDocument(
                "hud/text-widgets", "Text widgets", asset, textWidgets(font.id()));

        AssetUsageScanner.AssetUsageReport report = new AssetUsageScanner(
                null, cfg, db, List.of(open)).scanAsset(font.id());

        assertTrue(report.used());
        assertEquals(5, report.occurrenceCount());
        assertTrue(report.documentNames().contains("hud/text-widgets", false));
    }

    @Test public void bitmapFontUsedOnlyByTooltipBlocksDeletion() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta font = db.registerIfAbsent(AssetType.FONT, "fonts/tooltip",
                "orig/fonts/1/tooltip.fnt", AssetMeta.AssetScope.USER);
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.tooltip = new HudTooltipData();
        root.tooltip.fontAssetId = font.id();
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/tooltip.json";
        HudScreenEditorDocument open = new HudScreenEditorDocument(
                "hud/tooltip", "Tooltip", asset, new HudDocumentV1(root));

        AssetUsageScanner.AssetUsageReport report = new AssetUsageScanner(
                null, cfg, db, List.of(open)).scanAsset(font.id());
        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.documentNames().contains("hud/tooltip", false));
    }

    @Test public void bitmapFontUsedOnlyByWindowTitleBlocksDeletion() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta font = db.registerIfAbsent(AssetType.FONT, "fonts/window",
                "orig/fonts/1/window.fnt", AssetMeta.AssetScope.USER);
        HudNode root = new HudNode("root", HudNodeKind.WINDOW);
        root.window = new HudWindowData();
        root.table = games.pixscape.studio.service.hud.HudLayoutAuthoring
                .newTableLayout(root, 1, 1, false);
        root.window.fontAssetId = font.id();
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/window.json";
        HudScreenEditorDocument open = new HudScreenEditorDocument(
                "hud/window", "Window", asset, new HudDocumentV1(root));

        AssetUsageScanner.AssetUsageReport report = new AssetUsageScanner(
                null, cfg, db, List.of(open)).scanAsset(font.id());
        assertTrue(report.used());
        assertEquals(1, report.occurrenceCount());
        assertTrue(report.documentNames().contains("hud/window", false));
    }

    @Test public void skinUsageIncludesOpenDirtyAndPersistedHudAssetsWithoutLiveScene() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta used = db.registerIfAbsent(AssetType.SKIN, "skins/used",
                "orig/skins/1/used.json", AssetMeta.AssetScope.USER);
        AssetMeta unused = db.registerIfAbsent(AssetType.SKIN, "skins/unused",
                "orig/skins/2/unused.json", AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        HudScreenAsset savedAsset = new HudScreenAsset();
        savedAsset.documentId = "hud/saved.json";
        savedAsset.skinId = used.sourceRelPath();
        new HudDocumentPersistenceService().save(project,
                new HudScreenEditorDocument("hud/saved", "Saved HUD", savedAsset, emptyDocument()));
        HudScreenAsset dirtyAsset = new HudScreenAsset();
        dirtyAsset.documentId = "hud/dirty.json";
        dirtyAsset.skinId = used.sourceRelPath();
        HudScreenEditorDocument dirty = new HudScreenEditorDocument(
                "hud/dirty", "Dirty HUD", dirtyAsset, emptyDocument());
        AssetUsageScanner scanner = new AssetUsageScanner(null, cfg, db, List.of(dirty));

        AssetUsageScanner.AssetUsageReport report = scanner.scanAsset(used.id());

        assertTrue(report.used());
        assertEquals(2, report.occurrenceCount());
        assertTrue(report.documentNames().contains("hud/dirty", false));
        assertTrue(report.documentNames().contains("saved", false));
        assertFalse(scanner.scanAsset(unused.id()).used());
    }

    @Test public void skinUsageTracksTheCurrentUndoableOpenHudAssociation() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta previous = db.registerIfAbsent(AssetType.SKIN, "skins/previous",
                "orig/skins/1/previous.json", AssetMeta.AssetScope.USER);
        AssetMeta current = db.registerIfAbsent(AssetType.SKIN, "skins/current",
                "orig/skins/2/current.json", AssetMeta.AssetScope.USER);
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/open.json";
        asset.skinId = previous.sourceRelPath();
        HudScreenEditorDocument open = new HudScreenEditorDocument(
                "hud/open", "Open HUD", asset, emptyDocument());
        open.editSession().editSkin("Assign HUD Screen Skin", current.sourceRelPath());
        AssetUsageScanner scanner = new AssetUsageScanner(null, cfg, db, List.of(open));

        assertFalse(scanner.scanAsset(previous.id()).used());
        assertTrue(scanner.scanAsset(current.id()).used());

        assertTrue(open.editSession().undo());
        assertTrue(scanner.scanAsset(previous.id()).used());
        assertFalse(scanner.scanAsset(current.id()).used());
    }

    @Test public void unreadablePersistedHudBlocksSkinUsageDecision() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta skin = db.registerIfAbsent(AssetType.SKIN, "skins/test",
                "orig/skins/1/test.json", AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        project.child("hud/broken.hudscreen").writeString("{not-json", false, "UTF-8");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new AssetUsageScanner(null, cfg, db).scanAsset(skin.id()));

        assertTrue(failure.getMessage().contains("broken"));
    }

    @Test public void skinUsageNormalizesDotParentSegmentsAndSupportedSeparators() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta skin = db.registerIfAbsent(AssetType.SKIN, "skins/test",
                "orig/skins/12/game.json", AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        writeHudAsset(project, "dot", "orig/skins/12/./game.json");
        writeHudAsset(project, "parent", "orig/skins/12/pages/../game.json");
        writeHudAsset(project, "separators", "orig\\skins\\12\\game.json");

        AssetUsageScanner.AssetUsageReport report = new AssetUsageScanner(null, cfg, db)
                .scanAsset(skin.id());

        assertTrue(report.used());
        assertEquals(3, report.occurrenceCount());
    }

    @Test public void normalizedPersistedSkinReferenceIsFoundButDifferentReferenceIsNot() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta used = db.registerIfAbsent(AssetType.SKIN, "skins/used",
                "orig/skins/12/game.json", AssetMeta.AssetScope.USER);
        AssetMeta different = db.registerIfAbsent(AssetType.SKIN, "skins/different",
                "orig/skins/13/game.json", AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        writeHudAsset(project, "normalized", "orig/skins/12/nested/../game.json");

        AssetUsageScanner scanner = new AssetUsageScanner(null, cfg, db);

        assertTrue(scanner.scanAsset(used.id()).used());
        assertFalse(scanner.scanAsset(different.id()).used());
    }

    @Test public void escapingPersistedSkinReferenceBlocksUsageDecision() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta skin = db.registerIfAbsent(AssetType.SKIN, "skins/test",
                "orig/skins/12/game.json", AssetMeta.AssetScope.USER);
        FileHandle project = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile());
        writeHudAsset(project, "escaping", "../outside/game.json");

        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> new AssetUsageScanner(null, cfg, db).scanAsset(skin.id()));

        assertTrue(failure.getMessage().contains("escaping"));
        assertTrue(failure.getCause().getMessage().contains("escapes"));
    }

    @Test public void absentOpenHudSkinReferenceRemainsUnused() throws Exception {
        ProjectConfig cfg = projectConfig();
        AssetMetaDatabase db = new AssetMetaDatabase();
        AssetMeta skin = db.registerIfAbsent(AssetType.SKIN, "skins/test",
                "orig/skins/12/game.json", AssetMeta.AssetScope.USER);
        HudScreenAsset noSkin = new HudScreenAsset();
        noSkin.documentId = "hud/no-skin.json";
        HudScreenEditorDocument open = new HudScreenEditorDocument(
                "hud/no-skin", "No Skin", noSkin, emptyDocument());

        assertFalse(new AssetUsageScanner(null, cfg, db, List.of(open))
                .scanAsset(skin.id()).used());
    }

    private static HudDocumentV1 label(int fontAssetId) {
        HudNode node = new HudNode("label", HudNodeKind.LABEL);
        node.label = new HudLabelData();
        node.label.text = "";
        node.label.fontAssetId = fontAssetId;
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.children.add(HudChild.free(node, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 textWidgets(int fontAssetId) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode textra = new HudNode("textra", HudNodeKind.TEXTRA_LABEL);
        textra.textraLabel = new games.pixscape.runtime.hud.document.HudTextraLabelData();
        textra.textraLabel.text = "Text";
        textra.textraLabel.fontAssetId = fontAssetId;
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "Button";
        button.textButton.fontAssetId = fontAssetId;
        HudNode check = new HudNode("check", HudNodeKind.CHECK_BOX);
        check.checkBox = new games.pixscape.runtime.hud.document.HudCheckBoxData();
        check.checkBox.fontAssetId = fontAssetId;
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new games.pixscape.runtime.hud.document.HudTextFieldData();
        field.textField.fontAssetId = fontAssetId;
        HudNode select = new HudNode("select", HudNodeKind.SELECT_BOX);
        select.selectBox = new games.pixscape.runtime.hud.document.HudSelectBoxData();
        select.selectBox.selectedIndex = -1;
        select.selectBox.fontAssetId = fontAssetId;
        root.children.add(HudChild.direct(textra));
        root.children.add(HudChild.direct(button));
        root.children.add(HudChild.direct(check));
        root.children.add(HudChild.direct(field));
        root.children.add(HudChild.direct(select));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 emptyDocument() {
        return new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
    }

    private static void writeHudAsset(FileHandle project, String name, String skinId) {
        project.child("hud/" + name + ".hudscreen").writeString(
                "{\"skinId\":\"" + skinId.replace("\\", "\\\\") + "\"}",
                false, "UTF-8");
    }

    private static ProjectConfig projectConfig() throws Exception {
        Path dir = Files.createTempDirectory("pixscape-asset-usage-scanner");
        Files.createDirectories(dir.resolve(StudioFs.DIR_SCENES));

        ProjectConfig cfg = new ProjectConfig();
        cfg.projectTitle = "Asset Usage Scanner";
        cfg.projectFileName = "asset-usage-scanner";
        cfg.projectDirectoryPath = dir.toString();
        cfg.exportRootPathDir = dir.resolve("runtime").toString();
        cfg.createSceneMeta("Main");
        cfg.createSceneMeta("Other");
        cfg.setCurrentSceneByName("Main");
        return cfg;
    }

    private static void writeSceneWithAssetRef(SceneMeta scene, ProjectConfig cfg, int assetId) {
        FileHandle sceneFile = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile())
                .child(StudioFs.DIR_SCENES)
                .child(scene.getFile());
        sceneFile.writeString(
                """
                        {
                          "entities": {
                            "1": {
                              "components": {
                                "AssetRefComponent": {
                                  "assetId": %d
                                }
                              }
                            }
                          }
                        }
                        """.formatted(assetId),
                false,
                "UTF-8"
        );
    }

    private static void writeSceneWithAnimationAssetIds(SceneMeta scene,
                                                        ProjectConfig cfg,
                                                        int assetId) {
        FileHandle sceneFile = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile())
                .child(StudioFs.DIR_SCENES)
                .child(scene.getFile());
        sceneFile.writeString(
                """
                        {
                          "entities": {
                            "1": {
                              "components": {
                                "AnimationComponent": {
                                  "animationAssetIds": [%d]
                                }
                              }
                            }
                          }
                        }
                        """.formatted(assetId),
                false,
                "UTF-8"
        );
    }

    private static void writeSceneWithParticle(SceneMeta scene, ProjectConfig cfg, String effectPath) {
        FileHandle sceneFile = new FileHandle(Path.of(cfg.projectDirectoryPath).toFile())
                .child(StudioFs.DIR_SCENES)
                .child(scene.getFile());
        sceneFile.writeString(
                """
                        {
                          "entities": {
                            "1": {
                              "components": {
                                "ParticleEmitterComponent": {
                                  "effectPath": "%s"
                                }
                              }
                            }
                          }
                        }
                        """.formatted(effectPath),
                false,
                "UTF-8"
        );
    }
}
