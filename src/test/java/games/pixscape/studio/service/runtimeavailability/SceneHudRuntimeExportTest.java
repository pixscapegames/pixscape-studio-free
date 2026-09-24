package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.g2d.TextureAtlas.TextureAtlasData;
import com.badlogic.gdx.utils.GdxNativesLoader;
import com.badlogic.gdx.utils.Json;
import com.badlogic.gdx.utils.JsonReader;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.runtime.hud.HudBuiltInSliderStyle;
import games.pixscape.runtime.hud.HudBitmapFontResource;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.*;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.*;
import games.pixscape.studio.document.*;
import games.pixscape.studio.io.AtomicDirectoryPublication;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.service.atlas.*;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import java.awt.image.BufferedImage;
import java.nio.file.Files;
import java.util.ArrayList;
import java.util.List;
import javax.imageio.ImageIO;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import static org.junit.Assert.*;

public class SceneHudRuntimeExportTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void natives() { GdxNativesLoader.load(); }

    @Test public void emptyRootsIgnoreAllHudAtlasOutputsButRetainWorldArtifacts() throws Exception {
        Fixture f = fixture();
        write(f.root.child("atlases/hud/scene1/hud.atlas"), "stale");
        write(f.root.child("atlases/hud/orphan/hud.png"), "orphan");
        write(f.root.child("atlases/HUD/orphan-upper/hud.png"), "case-insensitive live domain");
        write(f.root.child("atlases/hud/default/hud.atlas"), "legacy");
        write(f.root.child("atlases/world.atlas"), "world");
        write(f.root.child("atlases/hud/manual.atlas"), "custom owned HUD");
        write(f.root.child("atlases/input/source.png"), "input");
        write(f.root.child("atlases/.tmp/unrelated/file"), "temporary");
        String before = new Json().toJson(f.cfg);
        f.export();
        assertFalse(f.runtime.child("atlases/hud/scene1").exists());
        assertFalse(f.runtime.child("atlases/hud/orphan").exists());
        assertFalse(f.runtime.child("atlases/HUD/orphan-upper").exists());
        assertFalse(f.runtime.child("atlases/hud/orphan-upper").exists());
        assertFalse(f.runtime.child("atlases/hud/default/hud.atlas").exists());
        assertEquals("world", f.runtime.child("atlases/world.atlas").readString());
        assertFalse(f.runtime.child("atlases/hud/manual.atlas").exists());
        assertFalse(new JsonReader().parse(f.runtime.child("project.json")).has("sceneHudFormatVersion"));
        assertEquals(before, new Json().toJson(f.cfg));
        assertFalse(new JsonReader().parse(f.root.child("project.json")).has("sceneHudFormatVersion"));
        noTemporaryArtifacts(f.runtime);
    }

    @Test public void selectedLayoutArtifactsDeduplicateAcrossScenesWithoutBindingOrSourceMutation() throws Exception {
        Fixture f = fixture();
        HudScreenAsset asset = new HudScreenAsset();
        asset.skinId = "skins/unused.json";
        f.save("main", asset, group());
        f.save("unrelated", new HudScreenAsset(), group());
        f.select("hud/main");
        f.addScene("Other", "scene2", "hud/main");
        write(f.root.child("atlases/hud/scene1/unlisted.png"), "must not copy");
        byte[] metadata = f.root.child("hud/main.hudscreen").readBytes();
        String config = new Json().toJson(f.cfg);
        try (var prepared = new SceneHudRuntimeExport().prepare(f.root, f.cfg)) {
            assertEquals(1, prepared.artifactPaths().stream().filter("hud/main.hudscreen"::equals).count());
            assertEquals(1, prepared.artifactPaths().stream().filter("hud/main.json"::equals).count());
            prepared.copyTo(f.runtime);
        }
        f.export();
        assertTrue(f.runtime.child("hud/main.hudscreen").exists());
        assertEquals("root", new HudDocumentCodec().read(f.runtime.child("hud/main.json")).root.id);
        assertFalse(f.runtime.child("hud/unrelated.hudscreen").exists());
        assertFalse(f.runtime.child("skins/unused.json").exists());
        assertFalse(f.runtime.child("atlases/hud/scene1/unlisted.png").exists());
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene1"));
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene2"));
        assertArrayEquals(metadata, f.root.child("hud/main.hudscreen").readBytes());
        assertEquals(config, new Json().toJson(f.cfg));
        assertEquals(asset.atlasId, new JsonReader().parse(f.runtime.child("hud/main.hudscreen")).getString("atlasId"));
        f.noCandidates(); noTemporaryArtifacts(f.runtime);
    }

    @Test public void requiredSkinAndFontDescriptorsKeepPathsButBuildOnlySourcesStayPrivate() throws Exception {
        Fixture f = fixture();
        f.withSkin(false);
        AssetMeta image = f.image("icon", 0xffff0000);
        HudDocumentV1 document = label();
        addRegion(document, resourceName(image));
        HudScreenAsset asset = new HudScreenAsset(); asset.skinId = "skins/game.json";
        f.save("main", asset, document); f.select("hud/main");
        f.addScene("Other", "scene2", "hud/main");
        byte[] skin = f.root.child("skins/game.json").readBytes();
        byte[] font = f.root.child("skins/fonts/body.fnt").readBytes();
        try (var prepared = new SceneHudRuntimeExport().prepare(f.root, f.cfg)) {
            assertEquals(1, prepared.artifactPaths().stream().filter("skins/game.json"::equals).count());
            assertEquals(1, prepared.artifactPaths().stream().filter("skins/fonts/body.fnt"::equals).count());
        }
        f.export();
        assertArrayEquals(skin, f.runtime.child("skins/game.json").readBytes());
        assertArrayEquals(font, f.runtime.child("skins/fonts/body.fnt").readBytes());
        for (String excluded : List.of("skins/game.atlas", "skins/source.png", "skins/fonts/body.png", image.sourceRelPath()))
            assertFalse(excluded, f.runtime.child(excluded).exists());
        var atlas = new TextureAtlasData(f.runtime.child("atlases/hud/scene1/hud.atlas"), f.runtime.child("atlases/hud/scene1"), false);
        var names = new ArrayList<String>();
        for (var region : atlas.getRegions()) names.add(region.name);
        assertTrue(names.contains("body"));
        assertTrue(names.contains(resourceName(image)));
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene1"));
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene2"));
        assertArrayEquals(skin, f.root.child("skins/game.json").readBytes());
        assertArrayEquals(font, f.root.child("skins/fonts/body.fnt").readBytes());
        noTemporaryArtifacts(f.runtime); f.noCandidates();
    }

    @Test public void builtInLabelExportsItsFontIntoTheSceneHudAtlasWithoutAUserSkin()
            throws Exception {
        Fixture f = fixture();
        HudDocumentV1 document = label();
        document.root.children.get(0).node.label.styleName = null;
        f.save("main", new HudScreenAsset(), document);
        f.select("hud/main");

        f.export();

        FileHandle output = f.runtime.child("atlases/hud/scene1");
        TextureAtlasData atlas = new TextureAtlasData(
                output.child("hud.atlas"), output, false);
        boolean containsBuiltInFont = false;
        for (TextureAtlasData.Region region : atlas.getRegions()) {
            if (HudBuiltInLabelStyle.ATLAS_REGION.equals(region.name)) {
                containsBuiltInFont = true;
            }
        }
        assertTrue(containsBuiltInFont);
        assertFalse(f.runtime.child("skins").exists());
        assertDescriptorMembership(output);
        noTemporaryArtifacts(f.runtime);
        f.noCandidates();
    }

    @Test public void builtInSliderExportsOnlyTheSharedWhiteAtlasInputWithoutAUserSkin()
            throws Exception {
        Fixture f = fixture();
        HudNode slider = new HudNode("slider", HudNodeKind.SLIDER);
        slider.slider = new games.pixscape.runtime.hud.document.HudSliderData();
        f.save("main", new HudScreenAsset(), new HudDocumentV1(slider));
        f.select("hud/main");

        f.export();

        FileHandle output = f.runtime.child("atlases/hud/scene1");
        TextureAtlasData atlas = new TextureAtlasData(output.child("hud.atlas"), output, false);
        boolean containsWhite = false;
        for (TextureAtlasData.Region region : atlas.getRegions()) {
            if (HudBuiltInSliderStyle.BACKGROUND_REGION.equals(region.name)) containsWhite = true;
        }
        assertTrue(containsWhite);
        assertFalse(f.runtime.child("skins").exists());
        assertDescriptorMembership(output);
        noTemporaryArtifacts(f.runtime);
        f.noCandidates();
    }

    @Test public void textFieldBitmapFontExportsDescriptorAndPackedPagesWithoutSourcePngs()
            throws Exception {
        Fixture f = fixture();
        AssetMeta font = f.bitmapFont("default");
        HudDocumentV1 document = textField();
        document.root.children.get(0).node.textField.fontAssetId = font.id();
        f.save("main", new HudScreenAsset(), document);
        f.select("hud/main");

        f.export();

        assertTrue(f.runtime.child(HudBitmapFontResource.descriptorId(font.id())).exists());
        assertFalse(f.runtime.child(font.sourceRelPath()).exists());
        assertFalse(f.runtime.child("orig/fonts/" + font.id() + "/page0.png").exists());
        assertFalse(f.runtime.child("orig/fonts/" + font.id() + "/nested/page1.png").exists());
        var atlas = new TextureAtlasData(f.runtime.child("atlases/hud/scene1/hud.atlas"),
                f.runtime.child("atlases/hud/scene1"), false);
        var regionNames = new java.util.HashSet<String>();
        for (var region : atlas.getRegions()) regionNames.add(region.name);
        assertTrue(regionNames.contains(HudBitmapFontResource.pageKey(font.id(), 0)));
        assertTrue(regionNames.contains(HudBitmapFontResource.pageKey(font.id(), 1)));
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene1"));
        noTemporaryArtifacts(f.runtime);
        f.noCandidates();
    }

    @Test public void textraLabelBitmapFontUsesTheExistingDescriptorAndPackedPageExport()
            throws Exception {
        Fixture f = fixture();
        AssetMeta font = f.bitmapFont("textra");
        HudDocumentV1 document = group();
        HudNode label = new HudNode("textra", HudNodeKind.TEXTRA_LABEL);
        label.textraLabel = new games.pixscape.runtime.hud.document.HudTextraLabelData();
        label.textraLabel.text = "AB";
        label.textraLabel.fontAssetId = font.id();
        document.root.children.add(HudChild.free(label, new HudFreePlacement()));
        f.save("main", new HudScreenAsset(), document);
        f.select("hud/main");

        f.export();

        assertTrue(f.runtime.child(HudBitmapFontResource.descriptorId(font.id())).exists());
        var atlas = new TextureAtlasData(f.runtime.child("atlases/hud/scene1/hud.atlas"),
                f.runtime.child("atlases/hud/scene1"), false);
        var regionNames = new java.util.HashSet<String>();
        for (var region : atlas.getRegions()) regionNames.add(region.name);
        assertTrue(regionNames.contains(HudBitmapFontResource.pageKey(font.id(), 0)));
        assertTrue(regionNames.contains(HudBitmapFontResource.pageKey(font.id(), 1)));
        assertFalse(f.runtime.child(font.sourceRelPath()).exists());
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene1"));
        noTemporaryArtifacts(f.runtime);
        f.noCandidates();
    }

    @Test public void newerSavedPixelsWinOverStaleLiveAndRepeatArtifactMembershipIsDeterministic() throws Exception {
        Fixture f = fixture(); AssetMeta image = f.image("icon", 0xffff0000);
        HudDocumentV1 document = group(); addRegion(document, resourceName(image));
        f.save("main", new HudScreenAsset(), document); f.select("hud/main");
        write(f.root.child("atlases/hud/scene1/hud.atlas"), "stale descriptor");
        png(f.root.child(image.sourceRelPath()), 0xff00ff00);
        f.export();
        assertRegionPixel(f.runtime.child("atlases/hud/scene1"), resourceName(image), 0x00ff00ff);
        List<String> first = files(f.runtime);
        String descriptor = f.runtime.child("atlases/hud/scene1/hud.atlas").readString();
        byte[] page = f.runtime.child("atlases/hud/scene1/hud.png").readBytes();
        f.export();
        assertEquals(first, files(f.runtime));
        assertEquals(descriptor, f.runtime.child("atlases/hud/scene1/hud.atlas").readString());
        assertArrayEquals(page, f.runtime.child("atlases/hud/scene1/hud.png").readBytes());
        assertEquals("stale descriptor", f.root.child("atlases/hud/scene1/hud.atlas").readString());
        f.noCandidates();
    }

    @Test public void optionalSourceAtlasIsNotRequiredForFontRegionsOrDeclaredSkinDrawables() throws Exception {
        Fixture f = fixture(); f.withSkin(false); assertTrue(f.root.child("skins/game.atlas").delete());
        write(f.root.child("skins/game.json"), """
                {"com.badlogic.gdx.graphics.g2d.BitmapFont":{"default":{"file":"fonts/body.fnt"}},
                 "com.badlogic.gdx.scenes.scene2d.ui.Skin$TintedDrawable":{"tinted":{"name":"body","color":{"r":1,"g":1,"b":1,"a":1}}}}
                """);
        HudDocumentV1 document = label();
        for (String name : List.of("body", "tinted")) {
            HudNode image = new HudNode(name, HudNodeKind.IMAGE); image.image = new HudImageData();
            image.image.source = HudImageSource.DRAWABLE; image.image.resourceName = name;
            document.root.children.add(HudChild.free(image, new HudFreePlacement()));
        }
        HudScreenAsset asset = new HudScreenAsset(); asset.skinId = "skins/game.json";
        f.save("main", asset, document); f.select("hud/main"); f.export();
        assertTrue(f.runtime.child("skins/game.json").exists());
        assertTrue(f.runtime.child("skins/fonts/body.fnt").exists());
        assertDescriptorMembership(f.runtime.child("atlases/hud/scene1")); f.noCandidates();
    }

    @Test public void selectedDirtyDocumentsAreRejectedWithoutSavingButUnrelatedDirtyDocumentsAreAllowed() throws Exception {
        Fixture f = fixture(); f.save("main", new HudScreenAsset(), group()); f.select("hud/main");
        HudScreenAsset editorAsset = new HudScreenAsset();
        editorAsset.documentId = "hud/main.json";
        HudScreenEditorDocument editor = new HudScreenEditorDocument("hud/main", "main", editorAsset, group());
        editor.editSession().edit("Unsaved change", ignored -> new HudDocumentV1(new HudNode("unsaved", HudNodeKind.GROUP)));
        int history = editor.editSession().historySize(); byte[] saved = f.root.child("hud/main.json").readBytes();
        try {
            SceneHudRuntimeExport.requireSavedDocuments(f.cfg, List.of(editor.screenId()));
            fail("Must require ordinary save before export");
        } catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("Save selected HUD screen 'hud/main'")); }
        assertTrue(editor.isDirty()); assertEquals(history, editor.editSession().historySize());
        assertArrayEquals(saved, f.root.child("hud/main.json").readBytes());
        SceneHudRuntimeExport.requireSavedDocuments(f.cfg, List.of("hud/unrelated"));
        // File-only callers have no editor override API; their authoritative input is the saved document.
        f.export();
        assertEquals("root", new HudDocumentCodec().read(f.runtime.child("hud/main.json")).root.id);
        assertTrue(editor.isDirty());
    }

    @Test public void everyMissingRequiredDependencyPreservesPreviousExport() throws Exception {
        for (String missing : List.of("hud/main.hudscreen", "hud/main.json", "skins/game.json", "skins/source.png",
                "skins/fonts/body.fnt", "skins/fonts/body.png", "orig/images/icon__a1.png")) {
            Fixture f = fixture(); f.withSkin(false); AssetMeta image = f.image("icon", 0xffff0000);
            HudDocumentV1 document = label(); addRegion(document, resourceName(image));
            HudScreenAsset asset = new HudScreenAsset(); asset.skinId = "skins/game.json";
            f.save("main", asset, document); f.select("hud/main");
            write(f.runtime.child("previous.txt"), "usable");
            assertTrue("Fixture dependency " + missing, f.root.child(missing).delete());
            try { f.export(); fail("Expected failure for " + missing); }
            catch (RuntimeException expected) { assertEquals(List.of("previous.txt"), files(f.runtime)); }
            assertEquals("usable", f.runtime.child("previous.txt").readString());
            f.noCandidates();
        }
    }

    @Test public void missingRequiredAtlasSourceFailsBeforeDeletingPreviousExport() throws Exception {
        Fixture f = fixture(); f.withSkin(false);
        HudNode image = new HudNode("drawable", HudNodeKind.IMAGE); image.image = new HudImageData();
        image.image.source = HudImageSource.DRAWABLE; image.image.resourceName = "panel";
        HudDocumentV1 document = group(); document.root.children.add(HudChild.free(image, new HudFreePlacement()));
        HudScreenAsset asset = new HudScreenAsset(); asset.skinId = "skins/game.json";
        f.save("main", asset, document); f.select("hud/main");
        assertTrue(f.root.child("skins/game.atlas").delete());
        write(f.runtime.child("previous.txt"), "usable");
        try { f.export(); fail("Required drawable atlas missing"); }
        catch (RuntimeException expected) { assertTrue(expected.getMessage().contains("atlas")); }
        assertEquals(List.of("previous.txt"), files(f.runtime)); f.noCandidates();
    }

    @Test public void laterSceneBuilderFailurePreservesPreviousExportAndDiscardsCompletedPrivateScenes() throws Exception {
        Fixture f = fixture();
        f.save("first", new HudScreenAsset(), group()); f.select("hud/first");
        f.withSkin(true); HudScreenAsset asset = new HudScreenAsset(); asset.skinId = "skins/game.json";
        f.save("second", asset, label()); f.addScene("Other", "scene2", "hud/second");
        write(f.runtime.child("previous.txt"), "usable");
        try { f.export(); fail("Builder must reject source PMA"); }
        catch (RuntimeException expected) { assertTrue(expected.getMessage(), expected.getMessage().contains("straight-alpha")); }
        assertEquals(List.of("previous.txt"), files(f.runtime));
        assertFalse(f.root.child("atlases/hud/scene1").exists()); f.noCandidates();
    }

    @Test public void injectedPackingFailureDiscardsAllPrivateExportArtifacts() throws Exception {
        Fixture f = fixture(); f.save("main", new HudScreenAsset(), group()); f.select("hud/main");
        var exporter = new SceneHudRuntimeExport(new SceneHudAtlasBuilder((input, output, name, profile) -> {
            write(output.child("partial.png"), "partial"); throw new IllegalStateException("controlled pack failure");
        }, SceneHudAtlasBuilder::writeDescriptor));
        try { exporter.prepare(f.root, f.cfg); fail("Packing failed"); }
        catch (IllegalStateException expected) { assertTrue(expected.getMessage().contains("controlled pack failure")); }
        assertFalse(f.runtime.child("hud").exists()); f.noCandidates();
    }

    @Test public void liveServicePublicationDuringExportCannotReplaceCapturedPixelsOrCopyItsArtifacts() throws Exception {
        Fixture f = fixture(); AssetMeta image = f.image("icon", 0xffff0000);
        HudDocumentV1 document = group(); addRegion(document, resourceName(image));
        f.save("main", new HudScreenAsset(), document); f.select("hud/main");
        var manager = new EditorDocumentManager(); var executors = new ArrayList<ControlledAtlasExecutor>();
        var errors = new ArrayList<RuntimeException>();
        try (var live = new SceneHudRuntimePreparationService(() -> f.root, () -> f.cfg, () -> f.database,
                manager, new HudDocumentPersistenceService(), new SceneHudAtlasBuilder(), AtomicDirectoryPublication::move,
                runner -> { var executor = new ControlledAtlasExecutor(); executors.add(executor);
                    return new AsyncAtlasRepackCoordinator<>(runner, executor, () -> 0L, 0L); })) {
            live.setErrorHandler(errors::add); live.bindProject(f.root);
            var exporter = new SceneHudRuntimeExport(new SceneHudAtlasBuilder((input, output, name, profile) -> {
                try {
                    // Export's source tree/materialized pixels are already captured. Now publish newer live pixels.
                    png(f.root.child(image.sourceRelPath()), 0xff0000ff);
                    live.invalidateScene("scene1"); live.update(); executors.get(0).runNext(); live.update();
                    assertRegionPixel(f.root.child("atlases/hud/scene1"), resourceName(image), 0x0000ffff);
                    write(f.root.child("atlases/hud/scene1/live-only.txt"), "not exported");
                    AtlasPackingService.packHud(input, output, name, profile);
                } catch (Exception failure) { throw new IllegalStateException(failure); }
            }, SceneHudAtlasBuilder::writeDescriptor));
            try (var prepared = exporter.prepare(f.root, f.cfg)) { prepared.copyTo(f.runtime); }
            assertTrue(errors.toString(), errors.isEmpty());
            assertRegionPixel(f.runtime.child("atlases/hud/scene1"), resourceName(image), 0xff0000ff);
            assertFalse(f.runtime.child("atlases/hud/scene1/live-only.txt").exists());
            assertDescriptorMembership(f.runtime.child("atlases/hud/scene1"));
        }
        f.noCandidates();
    }

    private Fixture fixture() throws Exception { return new Fixture(new FileHandle(temporary.newFolder()), new FileHandle(temporary.newFolder())); }
    private static HudDocumentV1 group() { return new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)); }
    private static HudDocumentV1 label() {
        HudDocumentV1 document = group(); HudNode label = new HudNode("label", HudNodeKind.LABEL);
        label.label = new HudLabelData(); label.label.text = "Score"; label.label.styleName = "default";
        document.root.children.add(HudChild.free(label, new HudFreePlacement())); return document;
    }
    private static HudDocumentV1 textField() {
        HudDocumentV1 document = group();
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        document.root.children.add(HudChild.free(field, new HudFreePlacement()));
        return document;
    }
    private static void addRegion(HudDocumentV1 document, String resource) {
        HudNode image = new HudNode("image", HudNodeKind.IMAGE); image.image = new HudImageData();
        image.image.source = HudImageSource.REGION; image.image.resourceName = resource;
        document.root.children.add(HudChild.free(image, new HudFreePlacement()));
    }
    private static String resourceName(AssetMeta image) { return new FileHandle(image.sourceRelPath()).nameWithoutExtension(); }
    private static void write(FileHandle file, String text) { file.parent().mkdirs(); file.writeString(text, false, "UTF-8"); }
    private static void png(FileHandle file, int argb) throws Exception {
        file.parent().mkdirs(); BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_ARGB);
        for (int x = 0; x < 4; x++) for (int y = 0; y < 4; y++) image.setRGB(x, y, argb);
        ImageIO.write(image, "png", file.file());
    }
    private static List<String> files(FileHandle root) throws Exception {
        try (var paths = Files.walk(root.file().toPath())) { return paths.filter(Files::isRegularFile)
                .map(path -> root.file().toPath().relativize(path).toString().replace('\\', '/')).sorted().toList(); }
    }
    private static void noTemporaryArtifacts(FileHandle runtime) throws Exception {
        for (String path : files(runtime)) assertFalse(path, path.contains(".tmp") || path.contains("/inputs/")
                || path.contains("/sources/") || path.contains("stamp") || path.contains("entry-"));
    }
    private static void assertDescriptorMembership(FileHandle output) throws Exception {
        var expected = new java.util.TreeSet<String>(); expected.add("hud.atlas");
        var data = new TextureAtlasData(output.child("hud.atlas"), output, false);
        for (var page : data.getPages()) expected.add(page.name);
        assertEquals(List.copyOf(expected), files(output));
    }
    private static void assertRegionPixel(FileHandle output, String name, int rgba) {
        var data = new TextureAtlasData(output.child("hud.atlas"), output, false);
        TextureAtlasData.Region selected = null;
        for (var region : data.getRegions()) if (region.name.equals(name)) selected = region;
        assertNotNull(name, selected); var pixels = new Pixmap(selected.page.textureFile);
        try { assertEquals(rgba, pixels.getPixel(selected.left, selected.top)); } finally { pixels.dispose(); }
    }
    private final class Fixture {
        final FileHandle root, user, runtime;
        final ProjectConfig cfg = new ProjectConfig(); final AssetMetaDatabase database = new AssetMetaDatabase();
        Fixture(FileHandle root, FileHandle user) {
            this.root = root; this.user = user; runtime = user.child(RuntimeExport.RUNTIME_DIR_NAME);
            cfg.projectFileName = "export-test"; cfg.projectTitle = "Export test"; cfg.exportRootPathDir = user.path();
            cfg.createSceneMeta("Main"); write(root.child("scenes/scene1.json"), "{}");
            database.save(root.child(StudioFs.FILE_ASSETS_JSON));
            write(root.child("project.json"), "{\"authored\":true}");
        }
        void select(String rootId) { cfg.getSceneMeta("Main").defaultHudScreenId = rootId; }
        void addScene(String name, String tag, String rootId) {
            SceneMeta scene = new SceneMeta(name, tag + ".json"); scene.defaultHudScreenId = rootId;
            cfg.getScenesMap().put(name, scene); write(root.child("scenes/" + tag + ".json"), "{}");
        }
        void save(String name, HudScreenAsset asset, HudDocumentV1 document) {
            if (asset.documentId == null) asset.documentId = "hud/" + name + ".json";
            var editor = new HudScreenEditorDocument("hud/" + name, name, asset, document);
            new HudDocumentPersistenceService().save(root, editor);
        }
        AssetMeta image(String name, int argb) throws Exception {
            AssetMeta image = database.registerIfAbsent(AssetType.IMAGE, "images/" + name,
                    "orig/images/" + name + "__a" + database.nextId() + ".png", AssetMeta.AssetScope.USER);
            png(root.child(image.sourceRelPath()), argb); database.save(root.child(StudioFs.FILE_ASSETS_JSON)); return image;
        }
        AssetMeta bitmapFont(String name) throws Exception {
            FileHandle source = user.child("font-imports/" + name);
            FileHandle descriptor = source.child(name + ".fnt");
            write(descriptor, """
                    info face="test" size=16 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0
                    common lineHeight=16 base=12 scaleW=4 scaleH=4 pages=2 packed=0
                    page id=0 file="page0.png"
                    page id=1 file="nested/page1.png"
                    chars count=2
                    char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0
                    char id=66 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=1 chnl=0
                    kernings count=0
                    """);
            png(source.child("page0.png"), 0xffffffff);
            png(source.child("nested/page1.png"), 0xff00ffff);
            AssetMeta font = new games.pixscape.studio.service.asset.BitmapFontAssetImportService(
                    database).importNew(descriptor, root);
            database.save(root.child(StudioFs.FILE_ASSETS_JSON));
            return font;
        }
        void withSkin(boolean pma) throws Exception {
            write(root.child("skins/game.json"), "{\"com.badlogic.gdx.graphics.g2d.BitmapFont\":{\"default\":{\"file\":\"fonts/body.fnt\"}}}");
            write(root.child("skins/game.atlas"), "source.png\nsize: 4, 4\nformat: RGBA8888\nfilter: Linear, Linear\nrepeat: none\npma: " + pma
                    + "\npanel\n  bounds: 0, 0, 4, 4\n  index: -1\n");
            png(root.child("skins/source.png"), 0xffffffff);
            write(root.child("skins/fonts/body.fnt"), """
                    info face="test" size=16 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0
                    common lineHeight=16 base=12 scaleW=4 scaleH=4 pages=1 packed=0
                    page id=0 file="body.png"
                    chars count=1
                    char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0
                    kernings count=0
                    """);
            png(root.child("skins/fonts/body.png"), 0xffffffff);
        }
        void export() { RuntimeExport.exportRuntime(cfg, root, user); }
        void noCandidates() throws Exception {
            if (!root.child("atlases").exists()) return;
            for (String path : files(root.child("atlases"))) assertFalse(path, path.contains(".tmp")
                    && !path.equals(".tmp/unrelated/file") || path.contains(".backup-"));
        }
    }
}
