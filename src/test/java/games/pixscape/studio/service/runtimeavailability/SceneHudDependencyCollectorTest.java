package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudImageButtonData;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTextButtonData;
import games.pixscape.runtime.hud.document.HudTextFieldData;
import games.pixscape.runtime.hud.document.HudSliderData;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.stream.Stream;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SceneHudDependencyCollectorTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test public void emptyAndDuplicateRootsProduceAnEmptyOrStableSelectedClosure() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        SceneHudDependencyCollector collector = new SceneHudDependencyCollector();

        assertEquals(List.of(), collector.collect(root, database, List.of(), List.of()).rootIds());

        save(root, "hud/main", new HudScreenAsset(), group());
        SceneHudDependencyClosure closure = collector.collect(root, database,
                List.of("main", "hud/main", "main"), List.of());
        assertEquals(List.of("hud/main"), closure.rootIds());
        assertEquals(1, closure.selectedHudScreens().size());
    }

    @Test public void selectedSavedRootsRemainOrderedAndExcludeUnrelatedSavedAndOpenScreens() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        String selected = registerImage(root, database, "selected");
        String unrelated = registerImage(root, database, "unrelated");
        save(root, "hud/first", new HudScreenAsset(), regionDocument(selected));
        save(root, "hud/second", new HudScreenAsset(), group());
        save(root, "hud/unrelated", new HudScreenAsset(), regionDocument(unrelated));

        HudScreenAsset openAsset = new HudScreenAsset();
        List<SceneHudScreenSnapshot> open = List.of(new SceneHudScreenSnapshot(
                "hud/unrelated", openAsset, regionDocument(unrelated)));
        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(root, database,
                List.of("hud/second", "first"), open);

        assertEquals(List.of("hud/second", "hud/first"), closure.rootIds());
        assertEquals(List.of(selected), closure.imageDependencies().stream()
                .map(SceneHudDependencyClosure.ImageDependency::resourceName).toList());
    }

    @Test public void selectedOpenSnapshotOverridesSavedStateWithoutLeakingOtherSnapshots() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        String saved = registerImage(root, database, "saved");
        String open = registerImage(root, database, "open");
        String ignored = registerImage(root, database, "ignored");
        save(root, "hud/main", new HudScreenAsset(), regionDocument(saved));

        List<SceneHudScreenSnapshot> snapshots = List.of(
                new SceneHudScreenSnapshot("hud/main", assetFor("hud/main"), regionDocument(open)),
                new SceneHudScreenSnapshot("hud/other", assetFor("hud/other"), regionDocument(ignored)));
        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(root, database,
                List.of("main"), snapshots);

        assertEquals(List.of(open), closure.imageDependencies().stream()
                .map(SceneHudDependencyClosure.ImageDependency::resourceName).toList());
    }

    @Test public void regionImagesResolveStableAssetIdentityAndDeduplicateAcrossNodesAndRoots() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        String shared = registerImage(root, database, "shared");
        AssetMeta meta = database.findById(1);
        save(root, "hud/a", new HudScreenAsset(), twoRegionsDocument(shared));
        save(root, "hud/b", new HudScreenAsset(), regionDocument(shared));

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(root, database,
                List.of("hud/a", "hud/b"), List.of());

        assertEquals(1, closure.imageDependencies().size());
        SceneHudDependencyClosure.ImageDependency dependency = closure.imageDependencies().get(0);
        assertEquals(meta.id(), dependency.assetId());
        assertEquals(meta.logicalPath(), dependency.logicalPath());
        assertEquals(shared, dependency.resourceName());
        assertEquals(meta.sourceRelPath(), dependency.sourceRelPath());
    }

    @Test public void imageButtonCollectsEveryConfiguredRegionState() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        String up = registerImage(root, database, "up");
        String over = registerImage(root, database, "over");
        String checked = registerImage(root, database, "checked");
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = image(up);
        button.imageButton.imageOver = image(over);
        button.imageButton.imageCheckedOver = image(checked);
        HudNode documentRoot = new HudNode("root", HudNodeKind.GROUP);
        documentRoot.children.add(HudChild.free(button, new HudFreePlacement()));
        save(root, "hud/button", new HudScreenAsset(), new HudDocumentV1(documentRoot));

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(root, database,
                List.of("hud/button"), List.of());

        assertEquals(List.of(up, over, checked), closure.imageDependencies().stream()
                .map(SceneHudDependencyClosure.ImageDependency::resourceName).toList());
    }

    @Test public void missingRegionMetadataOrSourceFailsWithTheSelectedRootAndNode() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        save(root, "hud/main", new HudScreenAsset(), regionDocument("missing__a99"));
        assertRootFailure(root, database, "cannot resolve a project Image dependency", "image");

        AssetMeta missingSource = database.registerIfAbsent(AssetType.IMAGE, "images/missing-source",
                "orig/images/missing-source__a1.png", AssetMeta.AssetScope.USER);
        save(root, "hud/source", new HudScreenAsset(),
                regionDocument("missing-source__a" + missingSource.id()));
        try {
            new SceneHudDependencyCollector().collect(root, database, List.of("hud/source"), List.of());
            fail("Expected missing project source failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("hud/source"));
            assertTrue(expected.getMessage().contains("missing-source__a" + missingSource.id()));
        }
    }

    @Test public void drawableLabelAndTextButtonRequireAndShareOneSkinClosure() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        createSkin(root, "skins/game.json", true, true);
        HudScreenAsset first = skinAsset();
        HudScreenAsset second = skinAsset();
        HudScreenAsset third = skinAsset();
        save(root, "hud/drawable", first, drawableDocument());
        save(root, "hud/label", second, labelDocument());
        save(root, "hud/button", third, buttonDocument());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(root, database,
                List.of("hud/drawable", "hud/label", "hud/button"), List.of());

        assertTrue(closure.selectedHudScreens().stream().allMatch(
                SceneHudDependencyClosure.SelectedHudScreen::requiresSkin));
        assertTrue(closure.selectedHudScreens().stream().allMatch(
                SceneHudDependencyClosure.SelectedHudScreen::requiresAtlas));
        assertEquals(1, closure.skinDependencies().size());
        assertEquals("skins/game.json", closure.skinDependencies().get(0).skinId());
        assertEquals(List.of(
                        "SKIN_JSON:skins/game.json", "SKIN_ATLAS:skins/game.atlas",
                        "SKIN_ATLAS_PAGE:skins/game.png", "BITMAP_FONT_DESCRIPTOR:skins/hud.fnt",
                        "BITMAP_FONT_PAGE:skins/hud.png"),
                closure.skinDependencies().get(0).files().stream()
                        .map(file -> file.kind() + ":" + file.projectRelativePath()).toList());
    }

    @Test public void builtInLabelRequiresSceneAtlasButNoUserSkin() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        save(root, "hud/default-label", new HudScreenAsset(), builtInLabelDocument());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, database, List.of("hud/default-label"), List.of());

        SceneHudDependencyClosure.SelectedHudScreen screen =
                closure.selectedHudScreens().get(0);
        assertFalse(screen.requiresSkin());
        assertTrue(screen.requiresAtlas());
        assertTrue(screen.requiresBuiltInLabelStyle());
        assertTrue(closure.skinDependencies().isEmpty());
    }

    @Test public void builtInTextButtonRequiresPackedDefaultsButNoUserSkin() throws Exception {
        FileHandle root = root();
        HudDocumentV1 document = buttonDocument();
        document.root.children.get(0).node.textButton.styleName = null;
        save(root, "hud/default-button", new HudScreenAsset(), document);

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, new AssetMetaDatabase(), List.of("hud/default-button"), List.of());

        SceneHudDependencyClosure.SelectedHudScreen screen =
                closure.selectedHudScreens().get(0);
        assertFalse(screen.requiresSkin());
        assertTrue(screen.requiresAtlas());
        assertTrue(screen.requiresBuiltInLabelStyle());
        assertTrue(closure.skinDependencies().isEmpty());
    }

    @Test public void builtInTextFieldRequiresPackedFontAndGraphicsButNoUserSkin() throws Exception {
        FileHandle root = root();
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new HudTextFieldData();
        save(root, "hud/default-field", new HudScreenAsset(), new HudDocumentV1(field));

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, new AssetMetaDatabase(), List.of("hud/default-field"), List.of());

        SceneHudDependencyClosure.SelectedHudScreen screen =
                closure.selectedHudScreens().get(0);
        assertFalse(screen.requiresSkin());
        assertTrue(screen.requiresAtlas());
        assertTrue(screen.requiresBuiltInLabelStyle());
        assertTrue(closure.skinDependencies().isEmpty());
    }

    @Test public void builtInSliderRequiresAtlasButNoSkinOrFontDependency() throws Exception {
        FileHandle root = root();
        HudNode slider = new HudNode("slider", HudNodeKind.SLIDER);
        slider.slider = new HudSliderData();
        save(root, "hud/default-slider", new HudScreenAsset(), new HudDocumentV1(slider));

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, new AssetMetaDatabase(), List.of("hud/default-slider"), List.of());

        SceneHudDependencyClosure.SelectedHudScreen screen =
                closure.selectedHudScreens().get(0);
        assertFalse(screen.requiresSkin());
        assertTrue(screen.requiresAtlas());
        assertFalse(screen.requiresBuiltInLabelStyle());
        assertTrue(closure.skinDependencies().isEmpty());
        assertTrue(closure.fontDependencies().isEmpty());
    }

    @Test public void missingSkinFontOrFontPageFailsPreciselyForTheOwningRoot() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        save(root, "hud/no-skin", new HudScreenAsset(), labelDocument());
        assertRootFailure(root, database, "skinId is absent", "hud/no-skin");

        createSkin(root, "skins/game.json", false, false);
        save(root, "hud/missing-font", skinAsset(), labelDocument());
        assertRootFailure(root, database, "BitmapFont 'default' descriptor 'hud.fnt' is missing", "hud/missing-font");

        createSkin(root, "skins/game.json", true, false);
        save(root, "hud/missing-page", skinAsset(), labelDocument());
        assertRootFailure(root, database,
                "BitmapFont 'default' descriptor 'hud.fnt' page 'hud.png' is missing", "hud/missing-page");
    }

    @Test public void singlePageAtlasBackedFontNeedsNoFallbackPageFileAndRetainsSkinAtlasFiles()
            throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "hud.fnt");
        writeAtlas(root, "skins/game.atlas", "hud", -1);
        writeFont(root.child("skins/hud.fnt"), "hud.png");
        save(root, "hud/main", skinAsset(), labelDocument());

        List<String> before = tree(root.file().toPath());
        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, database, List.of("hud/main"), List.of());

        assertEquals(before, tree(root.file().toPath()));
        assertEquals(List.of(
                        "SKIN_JSON:skins/game.json", "SKIN_ATLAS:skins/game.atlas",
                        "SKIN_ATLAS_PAGE:skins/game.png", "BITMAP_FONT_DESCRIPTOR:skins/hud.fnt"),
                closure.skinDependencies().get(0).files().stream()
                        .map(file -> file.kind() + ":" + file.projectRelativePath()).toList());
        assertEquals(null, Gdx.gl);
    }

    @Test public void shippedStyleDescriptorPageCanBeAbsentWhenAtlasProvidesDescriptorStem()
            throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/uiskin.json", "default.fnt");
        writeAtlas(root, "skins/uiskin.atlas", "default", -1);
        writeFont(root.child("skins/default.fnt"), "default.png");
        HudScreenAsset asset = assetFor("hud/main");
        asset.skinId = "skins/uiskin.json";
        save(root, "hud/main", asset, labelDocument());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, database, List.of("hud/main"), List.of());

        assertFalse(closure.skinDependencies().get(0).files().stream()
                .anyMatch(file -> file.projectRelativePath().endsWith("default.png")));
    }

    @Test public void fontWithoutAtlasRegionFallsBackToEveryFntPageFile() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "hud.fnt");
        writeAtlas(root, "skins/game.atlas", "unrelated", -1);
        writeFont(root.child("skins/hud.fnt"), "hud.png");
        root.child("skins/hud.png").writeString("page", false, "UTF-8");
        save(root, "hud/main", skinAsset(), labelDocument());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, database, List.of("hud/main"), List.of());

        assertTrue(closure.skinDependencies().get(0).files().stream().anyMatch(file ->
                file.kind() == SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE
                        && file.projectRelativePath().equals("skins/hud.png")));
    }

    @Test public void completeIndexedAtlasFontResolvesMultipageDescriptorWithoutPageFiles()
            throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "multi.fnt");
        writeAtlas(root, "skins/game.atlas", "multi", 0, "multi", 1);
        writeFont(root.child("skins/multi.fnt"), "first.png", "second.png");
        save(root, "hud/main", skinAsset(), labelDocument());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, database, List.of("hud/main"), List.of());

        assertFalse(closure.skinDependencies().get(0).files().stream()
                .anyMatch(file -> file.kind() == SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE));
    }

    @Test public void incompleteIndexedAtlasFontFailsInsteadOfMixingFallbackPages() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "multi.fnt");
        writeAtlas(root, "skins/game.atlas", "multi", 0);
        writeFont(root.child("skins/multi.fnt"), "first.png", "second.png");
        root.child("skins/first.png").writeString("page", false, "UTF-8");
        root.child("skins/second.png").writeString("page", false, "UTF-8");
        save(root, "hud/main", skinAsset(), labelDocument());

        assertRootFailure(root, database, "incomplete indexed regions", "hud/main");
    }

    @Test public void atlasRegionsWithoutTheZeroIndexUseTheFntPageFallback() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "multi.fnt");
        writeAtlas(root, "skins/game.atlas", "multi", 1);
        writeFont(root.child("skins/multi.fnt"), "first.png", "second.png");
        root.child("skins/first.png").writeString("page", false, "UTF-8");
        root.child("skins/second.png").writeString("page", false, "UTF-8");
        save(root, "hud/main", skinAsset(), labelDocument());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, database, List.of("hud/main"), List.of());

        assertEquals(List.of("skins/first.png", "skins/second.png"),
                closure.skinDependencies().get(0).files().stream()
                        .filter(file -> file.kind() == SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE)
                        .map(SceneHudDependencyClosure.FileDependency::projectRelativePath).toList());
    }

    @Test public void malformedFontDescriptorReportsItsOwningRootAndDescriptor() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "hud.fnt");
        root.child("skins/hud.fnt").writeString("not a bitmap font", false, "UTF-8");
        save(root, "hud/main", skinAsset(), labelDocument());

        assertRootFailure(root, database, "descriptor 'hud.fnt' is malformed", "hud/main");
    }

    @Test public void malformedSkinAtlasReportsItsOwningRootAndAtlas() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        writeSkin(root, "skins/game.json", "hud.fnt");
        root.child("skins/game.atlas").writeString("game.png\nsize: malformed\n", false, "UTF-8");
        root.child("skins/game.png").writeString("page", false, "UTF-8");
        save(root, "hud/main", skinAsset(), labelDocument());

        assertRootFailure(root, database, "Skin atlas 'game.atlas' is malformed", "hud/main");
    }

    @Test public void collectionIsReadOnlyAndReturnsDefensiveSelectedScreenSnapshots() throws Exception {
        FileHandle root = root();
        AssetMetaDatabase database = new AssetMetaDatabase();
        String region = registerImage(root, database, "icon");
        HudScreenAsset asset = assetFor("hud/main");
        HudDocumentV1 document = regionDocument(region);
        List<String> before = tree(root.file().toPath());

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(root, database,
                List.of("hud/main"), List.of(new SceneHudScreenSnapshot("hud/main", asset, document)));

        assertEquals(before, tree(root.file().toPath()));
        assertEquals(null, asset.skinId);
        assertEquals("root", document.root.id);
        HudScreenAsset returnedAsset = closure.selectedHudScreens().get(0).asset();
        HudDocumentV1 returnedDocument = closure.selectedHudScreens().get(0).document();
        returnedAsset.skinId = "changed.json";
        returnedDocument.root.id = "changed";
        assertFalse("changed.json".equals(closure.selectedHudScreens().get(0).asset().skinId));
        assertEquals("root", closure.selectedHudScreens().get(0).document().root.id);
        assertNotSame(returnedDocument, closure.selectedHudScreens().get(0).document());
    }

    @Test public void sceneOpenSnapshotIsAStableCopyForScenePreparation() {
        HudScreenAsset asset = assetFor("hud/main");
        HudScreenEditorDocument editor = new HudScreenEditorDocument("hud/main", "Main", asset, group());
        SceneHudScreenSnapshot snapshot = SceneHudScreenSnapshot.snapshotOpen(List.of(editor)).get(0);

        editor.editSession().edit("Replace HUD", ignored -> new HudDocumentV1(new HudNode("changed", HudNodeKind.GROUP)));

        assertEquals("root", snapshot.document().root.id);
        assertEquals("hud/main", snapshot.screenId());
    }

    @Test public void changedOpenSkinAssociationCollectsOnlyTheCurrentSkin() throws Exception {
        FileHandle root = root();
        root.child("orig/skins/1/first.json").writeString("{}", false, "UTF-8");
        root.child("orig/skins/2/second.json").writeString("{}", false, "UTF-8");
        HudScreenAsset asset = assetFor("hud/main");
        asset.skinId = "orig/skins/1/first.json";
        HudScreenEditorDocument editor = new HudScreenEditorDocument(
                "hud/main", "Main", asset, labelDocument());
        editor.editSession().editSkin(
                "Assign HUD Screen Skin", "orig/skins/2/second.json");

        SceneHudDependencyClosure closure = new SceneHudDependencyCollector().collect(
                root, new AssetMetaDatabase(), List.of("hud/main"),
                SceneHudScreenSnapshot.snapshotOpen(List.of(editor)));

        assertEquals(List.of("orig/skins/2/second.json"),
                closure.skinDependencies().stream()
                        .map(SceneHudDependencyClosure.SkinDependency::skinId).toList());
        assertEquals("orig/skins/2/second.json",
                closure.selectedHudScreens().get(0).asset().skinId);
    }

    private FileHandle root() throws IOException {
        return Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
    }

    private static String registerImage(FileHandle root, AssetMetaDatabase database, String base) {
        AssetMeta meta = database.registerIfAbsent(AssetType.IMAGE, "images/" + base,
                "orig/images/" + base + "__a" + database.nextId() + ".png", AssetMeta.AssetScope.USER);
        root.child(meta.sourceRelPath()).writeString("image", false, "UTF-8");
        return base + "__a" + meta.id();
    }

    private static void save(FileHandle root, String id, HudScreenAsset asset, HudDocumentV1 document) {
        if (asset.documentId == null) asset.documentId = id + ".json";
        HudScreenEditorDocument editor = new HudScreenEditorDocument(id, id, asset, document);
        new HudDocumentPersistenceService().save(root, editor);
    }

    private static HudScreenAsset assetFor(String id) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = id + ".json";
        return asset;
    }

    private static HudScreenAsset skinAsset() {
        HudScreenAsset asset = new HudScreenAsset();
        asset.skinId = "skins/game.json";
        return asset;
    }

    private static HudDocumentV1 group() {
        return new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
    }

    private static HudDocumentV1 regionDocument(String region) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.children.add(HudChild.free(region("image", region), new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 twoRegionsDocument(String region) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        root.children.add(HudChild.free(region("first", region), new HudFreePlacement()));
        root.children.add(HudChild.free(region("second", region), new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudNode region(String id, String resource) {
        HudNode node = new HudNode(id, HudNodeKind.IMAGE);
        node.image = new HudImageData();
        node.image.source = HudImageSource.REGION;
        node.image.resourceName = resource;
        return node;
    }

    private static HudImageData image(String resource) {
        HudImageData image = new HudImageData();
        image.source = HudImageSource.REGION;
        image.resourceName = resource;
        return image;
    }

    private static HudDocumentV1 drawableDocument() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode image = region("drawable", "panel");
        image.image.source = HudImageSource.DRAWABLE;
        root.children.add(HudChild.free(image, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 labelDocument() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode label = new HudNode("label", HudNodeKind.LABEL);
        label.label = new HudLabelData();
        label.label.text = "Score";
        label.label.styleName = "default";
        root.children.add(HudChild.free(label, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static HudDocumentV1 builtInLabelDocument() {
        HudDocumentV1 document = labelDocument();
        document.root.children.get(0).node.label.styleName = null;
        return document;
    }

    private static HudDocumentV1 buttonDocument() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Play";
        button.textButton.styleName = "default";
        root.children.add(HudChild.free(button, new HudFreePlacement()));
        return new HudDocumentV1(root);
    }

    private static void createSkin(FileHandle root, String id, boolean fontDescriptor, boolean fontPage) {
        writeSkin(root, id, "hud.fnt");
        String normalizedId = id.replace('\\', '/');
        int lastSlash = normalizedId.lastIndexOf('/');
        String atlasId = (lastSlash < 0 ? "" : normalizedId.substring(0, lastSlash + 1))
                + root.child(id).nameWithoutExtension() + ".atlas";
        writeAtlas(root, atlasId);
        if (fontDescriptor) {
            writeFont(root.child(id).parent().child("hud.fnt"), "hud.png");
        }
        if (fontPage) root.child(id).parent().child("hud.png").writeString("page", false, "UTF-8");
    }

    private static void writeSkin(FileHandle root, String id, String fontFile) {
        root.child(id).writeString("""
                {"com.badlogic.gdx.graphics.g2d.BitmapFont":{"default":{"file":"%s"}}}
                """.formatted(fontFile), false, "UTF-8");
    }

    private static void writeAtlas(FileHandle root, String id, Object... nameIndexPairs) {
        FileHandle atlas = root.child(id);
        StringBuilder text = new StringBuilder("""
                game.png
                size: 1,1
                format: RGBA8888
                filter: Nearest,Nearest
                repeat: none
                """);
        for (int i = 0; i < nameIndexPairs.length; i += 2) {
            text.append(nameIndexPairs[i]).append("""

                  rotate: false
                  xy: 0, 0
                  size: 1, 1
                  orig: 1, 1
                  offset: 0, 0
                  index: """).append(nameIndexPairs[i + 1]).append('\n');
        }
        atlas.writeString(text.toString(), false, "UTF-8");
        atlas.sibling("game.png").writeString("page", false, "UTF-8");
    }

    private static void writeFont(FileHandle descriptor, String... pageNames) {
        StringBuilder text = new StringBuilder("""
                info face="test" size=16 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0
                common lineHeight=16 base=12 scaleW=1 scaleH=1 pages=""").append(pageNames.length).append(" packed=0\n");
        for (int page = 0; page < pageNames.length; page++) {
            text.append("page id=").append(page).append(" file=\"")
                    .append(pageNames[page]).append("\"\n");
        }
        text.append("""
                chars count=1
                char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0
                kernings count=0
                """);
        descriptor.writeString(text.toString(), false, "UTF-8");
    }

    private static List<String> tree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/'))
                    .sorted().toList();
        }
    }

    private static void assertRootFailure(FileHandle root, AssetMetaDatabase database,
                                          String expectedText, String expectedRootText) {
        try {
            new SceneHudDependencyCollector().collect(root, database,
                    List.of(expectedRootText.startsWith("hud/") ? expectedRootText : "hud/main"), List.of());
            fail("Expected dependency-closure failure");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains(expectedRootText));
            assertTrue(expected.getMessage().contains(expectedText));
        }
    }
}
