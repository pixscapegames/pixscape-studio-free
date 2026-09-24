package games.pixscape.studio.service.runtimeavailability;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudBuiltInLabelStyle;
import games.pixscape.runtime.hud.HudBitmapFontResource;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudImageData;
import games.pixscape.runtime.hud.document.HudImageSource;
import games.pixscape.runtime.hud.document.HudImageButtonData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
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
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;

public class SceneHudPackInputProjectorTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        if (Gdx.files == null) new HeadlessApplication(new ApplicationAdapter() {},
                new HeadlessApplicationConfiguration());
    }

    @Test public void regionImagesUseTheirLogicalResourceNameAndDeduplicate() throws Exception {
        FileHandle root = root();
        root.child("orig/unrelated-basename.png").writeString("pixels", false, "UTF-8");
        SceneHudDependencyClosure.ImageDependency image = new SceneHudDependencyClosure.ImageDependency(
                7, "images/logo", "logo__a7", "orig/unrelated-basename.png");
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/main"), List.of(image, image), List.of()));

        SceneHudPackInputPlan.AtlasEntry entry = plan.entry(key("logo__a7", -1));
        assertNotNull(entry);
        assertEquals("orig/unrelated-basename.png", entry.sourcePath());
        assertEquals(1, plan.regionImageEntries().size());
        assertEquals(List.of("hud/main"), entry.provenance().get(0).hudRoots());
    }

    @Test public void standaloneFontHomonymsUseAssetIdentityForMultipageKeys() throws Exception {
        FileHandle root = root();
        writeStandaloneFont(root, "orig/fonts/7/default.fnt", "a.png", "nested/b.png");
        writeStandaloneFont(root, "orig/fonts/8/default.fnt", "a.png");
        var first = standaloneFont(7, "orig/fonts/7/default.fnt", "orig/fonts/7/a.png",
                "orig/fonts/7/nested/b.png");
        var second = standaloneFont(8, "orig/fonts/8/default.fnt", "orig/fonts/8/a.png");

        SceneHudPackInputPlan plan = project(root, new SceneHudDependencyClosure(
                List.of(), List.of(), List.of(), List.of(first, second)));

        assertNotNull(plan.entry(key(HudBitmapFontResource.pageKey(7, 0), -1)));
        assertNotNull(plan.entry(key(HudBitmapFontResource.pageKey(7, 1), -1)));
        assertNotNull(plan.entry(key(HudBitmapFontResource.pageKey(8, 0), -1)));
        assertEquals(2, plan.bitmapFonts().get(0).keys().size());
        assertEquals(1, plan.bitmapFonts().get(1).keys().size());
    }

    private static SceneHudDependencyClosure.FontDependency standaloneFont(
            int id, String descriptor, String... pages) {
        java.util.ArrayList<SceneHudDependencyClosure.FileDependency> files = new java.util.ArrayList<>();
        files.add(new SceneHudDependencyClosure.FileDependency(
                SceneHudDependencyClosure.FileKind.BITMAP_FONT_DESCRIPTOR, descriptor));
        for (String page : pages) files.add(new SceneHudDependencyClosure.FileDependency(
                SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE, page));
        return new SceneHudDependencyClosure.FontDependency(id, "fonts/default", descriptor,
                files, List.of("hud/main"));
    }

    private static void writeStandaloneFont(FileHandle root, String path, String... pages) {
        FileHandle descriptor = root.child(path);
        descriptor.parent().mkdirs();
        StringBuilder text = new StringBuilder("info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\n")
                .append("common lineHeight=16 base=12 scaleW=2 scaleH=2 pages=")
                .append(pages.length).append(" packed=0\n");
        for (int i = 0; i < pages.length; i++) {
            text.append("page id=").append(i).append(" file=\"").append(pages[i]).append("\"\n");
            descriptor.parent().child(pages[i]).writeString("png", false, "UTF-8");
        }
        text.append("chars count=1\nchar id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0\n");
        descriptor.writeString(text.toString(), false, "UTF-8");
    }

    @Test public void imageButtonSecondaryStatesKeepTheirPackedImageProvenance() throws Exception {
        FileHandle root = root();
        root.child("orig/up.png").writeString("up", false, "UTF-8");
        root.child("orig/over.png").writeString("over", false, "UTF-8");
        var up = new SceneHudDependencyClosure.ImageDependency(1, "images/up", "up__a1", "orig/up.png");
        var over = new SceneHudDependencyClosure.ImageDependency(2, "images/over", "over__a2", "orig/over.png");
        HudNode button = new HudNode("button", HudNodeKind.IMAGE_BUTTON);
        button.imageButton = new HudImageButtonData();
        button.imageButton.imageUp = image("up__a1");
        button.imageButton.imageCheckedOver = image("over__a2");
        HudNode documentRoot = new HudNode("root", HudNodeKind.GROUP);
        documentRoot.children.add(HudChild.free(button, new HudFreePlacement()));
        HudScreenAsset asset = new HudScreenAsset();
        var screen = new SceneHudDependencyClosure.SelectedHudScreen("hud/main", asset,
                new HudDocumentV1(documentRoot), false, true);
        SceneHudPackInputPlan plan = project(root, new SceneHudDependencyClosure(
                List.of(screen), List.of(up, over), List.of()));

        assertEquals(List.of("hud/main"), plan.entry(key("up__a1", -1)).provenance().get(0).hudRoots());
        assertEquals(List.of("hud/main"), plan.entry(key("over__a2", -1)).provenance().get(0).hudRoots());
    }

    @Test public void skinRegionsPreserveAtlasMetadataAndIndexedLookupIdentity() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/game.json", "{}");
        writeAtlas(root, "skins/game.atlas", "page.png", """
                panel
                rotate: 90
                bounds: 1, 2, 30, 40
                offsets: 3, 4, 50, 60
                index: -1
                nine
                rotate: false
                bounds: 5, 6, 7, 8
                offsets: 0, 0, 7, 8
                split: 1, 2, 3, 4
                pad: 5, 6, 7, 8
                index: -1
                frames
                rotate: false
                bounds: 9, 10, 11, 12
                offsets: 0, 0, 11, 12
                index: 2
                """);
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/main"), List.of(),
                List.of(skin("skins/game.json", "skins/game.atlas", List.of()))));

        SceneHudPackInputPlan.AtlasEntry panel = plan.entry(key("panel", -1));
        SceneHudPackInputPlan.AtlasRegionMetadata metadata = panel.atlasRegion();
        assertEquals("skins/page.png", panel.sourcePath());
        assertEquals(1, metadata.left());
        assertEquals(2, metadata.top());
        assertEquals(30, metadata.width());
        assertEquals(40, metadata.height());
        assertEquals(90, metadata.degrees());
        assertTrue(metadata.rotate());
        assertEquals(50, metadata.originalWidth());
        assertEquals(60, metadata.originalHeight());
        assertEquals(3f, metadata.offsetX(), 0f);
        assertEquals(4f, metadata.offsetY(), 0f);
        assertEquals(List.of(1, 2, 3, 4), plan.entry(key("nine", -1)).atlasRegion().split());
        assertEquals(List.of(5, 6, 7, 8), plan.entry(key("nine", -1)).atlasRegion().pad());
        assertNotNull(plan.entry(key("frames", 2)));
    }

    @Test public void atlasBackedFontsReuseExistingSingleAndIndexedSkinEntries() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/game.json", """
                {"com.badlogic.gdx.graphics.g2d.BitmapFont":{
                  "default":{"file":"default.fnt"},"multi":{"file":"multi.fnt"}}}
                """);
        writeAtlas(root, "skins/game.atlas", "page.png", """
                default
                rotate: false
                bounds: 0, 0, 1, 1
                offsets: 0, 0, 1, 1
                index: -1
                multi
                rotate: false
                bounds: 1, 0, 1, 1
                offsets: 0, 0, 1, 1
                index: 0
                multi
                rotate: false
                bounds: 2, 0, 1, 1
                offsets: 0, 0, 1, 1
                index: 1
                """);
        writeFont(root.child("skins/default.fnt"), "default.png");
        writeFont(root.child("skins/multi.fnt"), "first.png", "second.png");
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/main"), List.of(),
                List.of(skin("skins/game.json", "skins/game.atlas", List.of("skins/default.fnt", "skins/multi.fnt")))));

        assertEquals(List.of(key("default", -1)), plan.bitmapFonts().get(0).keys());
        assertEquals(List.of(key("multi", 0), key("multi", 1)), plan.bitmapFonts().get(1).keys());
        assertEquals(0, plan.fontPageEntries().size());
    }

    @Test public void fallbackFontsUseDescriptorStemsInsteadOfPageBasenames() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/game.json", """
                {"BitmapFont":{"single":{"file":"fancy.fnt"},"multi":{"file":"multi.fnt"}}}
                """);
        writeFont(root.child("skins/fancy.fnt"), "not-the-key.png");
        writeFont(root.child("skins/multi.fnt"), "first-page.png", "second-page.png");
        root.child("skins/not-the-key.png").writeString("pixels", false, "UTF-8");
        root.child("skins/first-page.png").writeString("pixels", false, "UTF-8");
        root.child("skins/second-page.png").writeString("pixels", false, "UTF-8");
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/main"), List.of(), List.of(
                skin("skins/game.json", null, List.of("skins/fancy.fnt", "skins/multi.fnt",
                        "skins/not-the-key.png", "skins/first-page.png", "skins/second-page.png")))));

        assertNotNull(plan.entry(key("fancy", -1)));
        assertNull(plan.entry(key("not-the-key", -1)));
        assertNotNull(plan.entry(key("multi", 0)));
        assertNotNull(plan.entry(key("multi", 1)));
    }

    @Test public void whitePixelIsReservedOnceAndProjectionHasNoWritesOrGl() throws Exception {
        FileHandle root = root();
        List<String> before = tree(root.file().toPath());
        SceneHudPackInputPlan plan = project(root, closure(List.of(), List.of(), List.of()));

        assertEquals(before, tree(root.file().toPath()));
        assertEquals(1, plan.internalEntries().size());
        assertNotNull(plan.entry(key("__pixscape_internal__/__ps_internal_white_px", -1)));
        assertNull(Gdx.gl);
    }

    @Test public void builtInLabelFontIsOneReservedSceneLocalAtlasInput() throws Exception {
        FileHandle root = root();
        SceneHudDependencyClosure.SelectedHudScreen screen =
                new SceneHudDependencyClosure.SelectedHudScreen(
                        "hud/main", new HudScreenAsset(), document(List.of()),
                        false, true, true);

        SceneHudPackInputPlan plan = project(root,
                new SceneHudDependencyClosure(List.of(screen), List.of(), List.of()));

        SceneHudPackInputPlan.AtlasEntry entry = plan.entry(
                key(HudBuiltInLabelStyle.ATLAS_REGION, -1));
        assertNotNull(entry);
        assertEquals(SceneHudPackInputPlan.MaterialKind.INTERNAL_BUILT_IN_LABEL_FONT,
                entry.materialKind());
        assertNull(entry.sourcePath());
        assertEquals(SceneHudPackInputPlan.ProvenanceKind.INTERNAL_BUILT_IN_LABEL_FONT,
                entry.provenance().get(0).kind());
        assertEquals(List.of("hud/main"), entry.provenance().get(0).hudRoots());
    }

    @Test public void incompatibleImageAndSkinCollisionNamesBothProvenances() throws Exception {
        FileHandle root = root();
        root.child("orig/button.png").writeString("image", false, "UTF-8");
        writeSkin(root, "skins/game.json", "{}");
        writeAtlas(root, "skins/game.atlas", "page.png", simpleRegion("button", -1));

        assertCollision(root, closure(List.of("hud/main"), List.of(new SceneHudDependencyClosure.ImageDependency(
                1, "images/button", "button", "orig/button.png")),
                List.of(skin("skins/game.json", "skins/game.atlas", List.of()))), "REGION Image", "Skin");
    }

    @Test public void incompatibleSkinsAndFontAndInternalCollisionsFailDeterministically() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/a.json", "{}");
        writeAtlas(root, "skins/a.atlas", "a.png", simpleRegion("button", -1));
        writeSkin(root, "skins/b.json", "{}");
        writeAtlas(root, "skins/b.atlas", "b.png", simpleRegion("button", -1));
        assertCollision(root, closure(List.of("hud/a", "hud/b"), List.of(), List.of(
                skin("skins/a.json", "skins/a.atlas", List.of()), skin("skins/b.json", "skins/b.atlas", List.of()))),
                "Skin 'skins/a.json'", "Skin 'skins/b.json'");

        writeSkin(root, "skins/font.json", "{" + "\"BitmapFont\":{\"button\":{\"file\":\"button.fnt\"}}}");
        writeFont(root.child("skins/button.fnt"), "font-page.png");
        root.child("skins/font-page.png").writeString("pixels", false, "UTF-8");
        assertCollision(root, closure(List.of("hud/a", "hud/font"), List.of(), List.of(
                skin("skins/a.json", "skins/a.atlas", List.of()), skin("skins/font.json", null,
                        List.of("skins/button.fnt", "skins/font-page.png")))), "Skin", "BitmapFont");

        root.child("orig/internal.png").writeString("pixels", false, "UTF-8");
        assertCollision(root, closure(List.of("hud/main"), List.of(new SceneHudDependencyClosure.ImageDependency(
                2, "images/internal", "__pixscape_internal__/__ps_internal_white_px", "orig/internal.png")),
                List.of()), "REGION Image", "Pixscape internal");
    }

    @Test public void incompatibleFallbackFontPagesWithTheSameDescriptorStemFail() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/a.json", "{\"BitmapFont\":{\"first\":{\"file\":\"a/button.fnt\"}}}");
        writeSkin(root, "skins/b.json", "{\"BitmapFont\":{\"second\":{\"file\":\"b/button.fnt\"}}}");
        writeFont(root.child("skins/a/button.fnt"), "first.png");
        writeFont(root.child("skins/b/button.fnt"), "second.png");
        root.child("skins/a/first.png").writeString("first", false, "UTF-8");
        root.child("skins/b/second.png").writeString("second", false, "UTF-8");

        assertCollision(root, closure(List.of("hud/a", "hud/b"), List.of(), List.of(
                skin("skins/a.json", null, List.of("skins/a/button.fnt", "skins/a/first.png")),
                skin("skins/b.json", null, List.of("skins/b/button.fnt", "skins/b/second.png")))),
                "BitmapFont descriptor 'skins/a/button.fnt'", "BitmapFont descriptor 'skins/b/button.fnt'");
    }

    @Test public void equivalentSkinRegionsDeduplicateAndRetainBothProvenances() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/a.json", "{}");
        writeSkin(root, "skins/b.json", "{}");
        writeAtlas(root, "skins/a.atlas", "shared.png", simpleRegion("button", -1));
        writeAtlas(root, "skins/b.atlas", "shared.png", simpleRegion("button", -1));
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/a", "hud/b"), List.of(), List.of(
                skin("skins/a.json", "skins/a.atlas", "skins/shared.png", List.of()),
                skin("skins/b.json", "skins/b.atlas", "skins/shared.png", List.of()))));

        SceneHudPackInputPlan.AtlasEntry entry = plan.entry(key("button", -1));
        assertEquals(1, plan.skinAtlasRegionEntries().stream().filter(candidate -> candidate.key().equals(key("button", -1))).count());
        assertEquals(2, entry.provenance().size());
        assertEquals("skins/a.json", entry.provenance().get(0).skinId());
        assertEquals("skins/b.json", entry.provenance().get(1).skinId());
    }

    @Test public void distinctAtlasKeysCannotCollapseToSkinRegistrationKeys() throws Exception {
        for (int index : List.of(0, 2)) {
            FileHandle root = root();
            String name = index == 0 ? "legitimate" : "button";
            String collapsed = name + "_" + index;
            root.child("orig/image.png").writeString("image", false, "UTF-8");
            writeSkin(root, "skins/game.json", "{}");
            writeAtlas(root, "skins/game.atlas", "page.png", simpleRegion(name, index));
            SceneHudDependencyClosure dependencies = closure(List.of("hud/main"), List.of(
                    new SceneHudDependencyClosure.ImageDependency(1, "images/image", collapsed, "orig/image.png")),
                    List.of(skin("skins/game.json", "skins/game.atlas", List.of())));
            List<String> before = tree(root.file().toPath());
            String diagnostic = null;
            for (int attempt = 0; attempt < 2; attempt++) {
                try {
                    project(root, dependencies);
                    fail("Expected Skin registration collision");
                } catch (IllegalStateException expected) {
                    String message = expected.getMessage();
                    assertTrue(message.contains("Skin registration key collision: '" + collapsed + "'"));
                    assertTrue(message.contains(key(collapsed, -1).toString()));
                    assertTrue(message.contains(key(name, index).toString()));
                    assertTrue(message.contains("REGION Image"));
                    assertTrue(message.contains("images/image"));
                    assertTrue(message.contains("Skin 'skins/game.json'"));
                    if (diagnostic != null) assertEquals(diagnostic, message);
                    diagnostic = message;
                }
            }
            assertEquals(before, tree(root.file().toPath()));
        }
    }

    @Test public void distinctSkinRegistrationKeysAndRepeatedIndexedKeysRemainValid() throws Exception {
        FileHandle root = root();
        writeSkin(root, "skins/game.json", "{}");
        writeAtlas(root, "skins/game.atlas", "page.png", simpleRegion("ordinary", -1)
                + simpleRegion("different", -1) + simpleRegion("button", 0)
                + simpleRegion("button", 2) + simpleRegion("button", 2));
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/main"), List.of(),
                List.of(skin("skins/game.json", "skins/game.atlas", List.of()))));
        assertEquals(4, plan.skinAtlasRegionEntries().size());
        for (SceneHudPackInputPlan.AtlasKey key : List.of(key("ordinary", -1), key("different", -1),
                key("button", 0), key("button", 2))) assertNotNull(plan.entry(key));
    }

    @Test public void planRetainsExactlyTheClosureRoots() throws Exception {
        FileHandle root = root();
        SceneHudPackInputPlan plan = project(root, closure(List.of("hud/selected", "hud/other"), List.of(), List.of()));
        assertEquals(List.of("hud/selected", "hud/other"), plan.rootIds());
        assertFalse(plan.rootIds().contains("hud/unrelated"));
    }

    private static SceneHudPackInputPlan project(FileHandle root, SceneHudDependencyClosure closure) {
        return new SceneHudPackInputProjector().project(root, closure);
    }

    private static SceneHudDependencyClosure closure(List<String> roots,
                                                      List<SceneHudDependencyClosure.ImageDependency> images,
                                                      List<SceneHudDependencyClosure.SkinDependency> skins) {
        List<String> imageNames = images.stream().map(SceneHudDependencyClosure.ImageDependency::resourceName).toList();
        List<SceneHudDependencyClosure.SelectedHudScreen> screens = new java.util.ArrayList<>();
        for (int index = 0; index < roots.size(); index++) {
            String skinId = skins.isEmpty() ? null : skins.get(Math.min(index, skins.size() - 1)).skinId();
            screens.add(screen(roots.get(index), skinId, imageNames));
        }
        return new SceneHudDependencyClosure(screens, images, skins);
    }

    private static SceneHudDependencyClosure.SelectedHudScreen screen(String root, String skinId,
                                                                        List<String> imageNames) {
        HudScreenAsset asset = new HudScreenAsset();
        asset.skinId = skinId;
        return new SceneHudDependencyClosure.SelectedHudScreen(root, asset, document(imageNames),
                skinId != null, skinId != null);
    }

    private static SceneHudDependencyClosure.SkinDependency skin(String skinJson, String atlas,
                                                                  List<String> additionalFiles) {
        return skin(skinJson, atlas, null, additionalFiles);
    }

    private static SceneHudDependencyClosure.SkinDependency skin(String skinJson, String atlas,
                                                                  String explicitPage, List<String> additionalFiles) {
        List<SceneHudDependencyClosure.FileDependency> files = new java.util.ArrayList<>();
        files.add(file(SceneHudDependencyClosure.FileKind.SKIN_JSON, skinJson));
        if (atlas != null) {
            files.add(file(SceneHudDependencyClosure.FileKind.SKIN_ATLAS, atlas));
            String page = explicitPage != null ? explicitPage : atlas.substring(0, atlas.lastIndexOf('/') + 1)
                    + (atlas.contains("game") ? "page.png" : atlas.contains("a.atlas") ? "a.png"
                    : atlas.contains("b.atlas") ? "b.png" : "page.png");
            files.add(file(SceneHudDependencyClosure.FileKind.SKIN_ATLAS_PAGE, page));
        }
        for (String path : additionalFiles) files.add(file(path.endsWith(".fnt")
                ? SceneHudDependencyClosure.FileKind.BITMAP_FONT_DESCRIPTOR
                : SceneHudDependencyClosure.FileKind.BITMAP_FONT_PAGE, path));
        return new SceneHudDependencyClosure.SkinDependency(skinJson, files);
    }

    private static SceneHudDependencyClosure.FileDependency file(SceneHudDependencyClosure.FileKind kind, String path) {
        return new SceneHudDependencyClosure.FileDependency(kind, path);
    }

    private static SceneHudPackInputPlan.AtlasKey key(String name, int index) {
        return new SceneHudPackInputPlan.AtlasKey(name, index);
    }

    private FileHandle root() throws IOException {
        return Gdx.files.absolute(temporary.newFolder().getAbsolutePath());
    }

    private static HudDocumentV1 group() { return new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP)); }

    private static HudDocumentV1 document(List<String> imageNames) {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        for (String resourceName : imageNames) {
            HudNode image = new HudNode(resourceName, HudNodeKind.IMAGE);
            image.image = new HudImageData();
            image.image.source = HudImageSource.REGION;
            image.image.resourceName = resourceName;
            root.children.add(HudChild.free(image, new HudFreePlacement()));
        }
        return new HudDocumentV1(root);
    }

    private static HudImageData image(String resourceName) {
        HudImageData image = new HudImageData();
        image.source = HudImageSource.REGION;
        image.resourceName = resourceName;
        return image;
    }

    private static void writeSkin(FileHandle root, String path, String json) {
        root.child(path).writeString(json, false, "UTF-8");
    }

    private static void writeAtlas(FileHandle root, String path, String pageName, String regions) {
        FileHandle atlas = root.child(path);
        atlas.writeString("""
                %s
                size: 16,16
                format: RGBA8888
                filter: Nearest,Nearest
                repeat: none
                %s""".formatted(pageName, regions), false, "UTF-8");
        atlas.sibling(pageName).writeString("page", false, "UTF-8");
    }

    private static String simpleRegion(String name, int index) {
        return """
                %s
                rotate: false
                bounds: 0, 0, 1, 1
                offsets: 0, 0, 1, 1
                index: %s
                """.formatted(name, index);
    }

    private static void writeFont(FileHandle descriptor, String... pages) {
        StringBuilder text = new StringBuilder("""
                info face="test" size=16 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=0,0
                common lineHeight=16 base=12 scaleW=1 scaleH=1 pages=""").append(pages.length).append(" packed=0\n");
        for (int page = 0; page < pages.length; page++) {
            text.append("page id=").append(page).append(" file=\"").append(pages[page]).append("\"\n");
        }
        text.append("""
                chars count=1
                char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0
                kernings count=0
                """);
        descriptor.writeString(text.toString(), false, "UTF-8");
    }

    private static void assertCollision(FileHandle root, SceneHudDependencyClosure closure, String first, String second) {
        try {
            project(root, closure);
            fail("Expected atlas-key collision");
        } catch (IllegalStateException expected) {
            assertTrue(expected.getMessage().contains("Scene HUD atlas key collision"));
            assertTrue(expected.getMessage().contains(first));
            assertTrue(expected.getMessage().contains(second));
        }
    }

    private static List<String> tree(Path root) throws IOException {
        try (Stream<Path> paths = Files.walk(root)) {
            return paths.filter(Files::isRegularFile)
                    .map(path -> root.relativize(path).toString().replace('\\', '/')).sorted().toList();
        }
    }
}
