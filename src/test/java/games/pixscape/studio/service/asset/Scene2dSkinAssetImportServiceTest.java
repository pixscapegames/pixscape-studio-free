package games.pixscape.studio.service.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.scenes.scene2d.ui.List;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.badlogic.gdx.scenes.scene2d.ui.TextTooltip;
import com.badlogic.gdx.scenes.scene2d.ui.Window;
import com.badlogic.gdx.scenes.scene2d.ui.SelectBox;
import com.badlogic.gdx.scenes.scene2d.ui.Slider;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class Scene2dSkinAssetImportServiceTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass public static void bootGdx() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass public static void stopGdx() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void importsPartialNativeSkinAndPersistsStableIdentity() throws Exception {
        FileHandle external = folder("partial-source");
        FileHandle descriptor = external.child("minimal.json");
        descriptor.writeString("""
                {
                  "com.badlogic.gdx.scenes.scene2d.ui.List$ListStyle": { "list": {} },
                  "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane$ScrollPaneStyle": { "scroll": {} }
                }
                """, false, "UTF-8");
        FileHandle project = folder("partial-project");
        AssetMetaDatabase database = new AssetMetaDatabase();

        AssetMeta imported = new Scene2dSkinAssetImportService(database)
                .importNew(descriptor, project);
        database.save(project.child(StudioFs.FILE_ASSETS_JSON));
        AssetMeta restored = AssetMetaDatabase.load(project.child(StudioFs.FILE_ASSETS_JSON))
                .findById(imported.id());

        assertEquals(AssetType.SKIN, restored.type());
        assertEquals("skins/minimal", restored.logicalPath());
        assertEquals("orig/skins/" + imported.id() + "/minimal.json", restored.sourceRelPath());
        assertTrue(project.child(restored.sourceRelPath()).exists());
    }

    @Test public void missingFontDependencyPublishesNeitherAssetNorBundle() throws Exception {
        FileHandle external = folder("missing-source");
        FileHandle descriptor = external.child("broken.json");
        descriptor.writeString("""
                { "com.badlogic.gdx.graphics.g2d.BitmapFont": {
                    "default": { "file": "fonts/missing.fnt" }
                } }
                """, false, "UTF-8");
        FileHandle project = folder("missing-project");
        AssetMetaDatabase database = new AssetMetaDatabase();

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Scene2dSkinAssetImportService(database).importNew(descriptor, project));

        assertTrue(failure.getMessage().contains("missing"));
        assertEquals(0, database.size());
        FileHandle root = project.child(StudioFs.DIR_ORIG_SKINS);
        assertTrue(!root.exists() || root.list().length == 0);
    }

    @Test public void copiesSkinFontDescriptorAndDirectPagesWithRelativePaths() throws Exception {
        FileHandle external = folder("font-source");
        writePng(external.child("fonts/pages/font.png"));
        external.child("fonts/font.fnt").writeString(fontDescriptor("pages/font.png"),
                false, "UTF-8");
        FileHandle descriptor = external.child("game.json");
        descriptor.writeString("""
                {
                  "com.badlogic.gdx.graphics.Color": {
                    "white": { "r": 1, "g": 1, "b": 1, "a": 1 }
                  },
                  "com.badlogic.gdx.graphics.g2d.BitmapFont": {
                    "default": { "file": "fonts/font.fnt" }
                  },
                  "com.badlogic.gdx.scenes.scene2d.ui.Label$LabelStyle": {
                    "default": { "font": "default", "fontColor": "white" }
                  }
                }
                """, false, "UTF-8");
        FileHandle project = folder("font-project");

        AssetMeta imported = new Scene2dSkinAssetImportService(new AssetMetaDatabase())
                .importNew(descriptor, project);
        FileHandle bundle = project.child(StudioFs.DIR_ORIG_SKINS + "/" + imported.id());

        assertTrue(bundle.child("game.json").exists());
        assertTrue(bundle.child("fonts/font.fnt").exists());
        assertTrue(bundle.child("fonts/pages/font.png").exists());
    }

    @Test public void customClassIsRejectedBeforeNativeLoading() throws Exception {
        FileHandle external = folder("custom-source");
        FileHandle descriptor = external.child("custom.json");
        descriptor.writeString("{ \"example.CustomStyle\": { \"default\": {} } }",
                false, "UTF-8");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Scene2dSkinAssetImportService(new AssetMetaDatabase())
                        .importNew(descriptor, folder("custom-project")));

        assertTrue(failure.getMessage().contains("example.CustomStyle"));
    }

    @Test public void nativeButUnsupportedWidgetStyleRemainsRejected() throws Exception {
        FileHandle external = folder("unsupported-native-source");
        FileHandle descriptor = external.child("unsupported.json");
        descriptor.writeString("{ \"com.badlogic.gdx.scenes.scene2d.ui.Touchpad$TouchpadStyle\":"
                        + " { \"default\": {} } }",
                false, "UTF-8");

        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Scene2dSkinAssetImportService(new AssetMetaDatabase())
                        .importNew(descriptor, folder("unsupported-native-project")));

        assertTrue(failure.getMessage().contains("Touchpad$TouchpadStyle"));
    }

    @Test public void importsSiblingAtlasAndPagesAndRejectsPma() throws Exception {
        FileHandle external = folder("atlas-source");
        writePng(external.child("page.png"));
        external.child("game.json").writeString("{}", false, "UTF-8");
        external.child("game.atlas").writeString(atlas(false), false, "UTF-8");
        FileHandle project = folder("atlas-project");

        AssetMeta imported = new Scene2dSkinAssetImportService(new AssetMetaDatabase())
                .importNew(external.child("game.json"), project);
        FileHandle bundle = project.child(StudioFs.DIR_ORIG_SKINS + "/" + imported.id());

        assertTrue(bundle.child("game.atlas").exists());
        assertTrue(bundle.child("page.png").exists());

        FileHandle pma = folder("pma-source");
        writePng(pma.child("page.png"));
        pma.child("game.json").writeString("{}", false, "UTF-8");
        pma.child("game.atlas").writeString(atlas(true), false, "UTF-8");
        IllegalArgumentException failure = assertThrows(IllegalArgumentException.class,
                () -> new Scene2dSkinAssetImportService(new AssetMetaDatabase())
                        .importNew(pma.child("game.json"), folder("pma-project")));
        assertTrue(failure.getMessage().contains("pma: false"));
    }

    @Test public void acceptsAtlasBackedFontSelectBoxAndAuxiliaryStyles() throws Exception {
        FileHandle external = folder("select-source");
        writePng(external.child("page.png"), 4, 2);
        external.child("game.atlas").writeString("""
                page.png
                size: 4, 2
                format: RGBA8888
                filter: Nearest, Nearest
                repeat: none
                pma: false
                panel
                  bounds: 0, 0, 2, 2
                font
                  bounds: 2, 0, 2, 2
                """, false, "UTF-8");
        external.child("fonts/font.fnt").writeString(fontDescriptor("missing-direct-page.png"),
                false, "UTF-8");
        FileHandle descriptor = external.child("game.json");
        descriptor.writeString("""
                {
                  "com.badlogic.gdx.graphics.Color": {
                    "white": { "r": 1, "g": 1, "b": 1, "a": 1 }
                  },
                  "com.badlogic.gdx.graphics.g2d.BitmapFont": {
                    "default": { "file": "fonts/font.fnt" }
                  },
                  "com.badlogic.gdx.scenes.scene2d.ui.List$ListStyle": {
                    "default": { "font": "default", "fontColorSelected": "white",
                      "fontColorUnselected": "white", "selection": "panel" }
                  },
                  "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane$ScrollPaneStyle": {
                    "default": { "background": "panel" }
                  },
                  "com.badlogic.gdx.scenes.scene2d.ui.SelectBox$SelectBoxStyle": {
                    "default": { "font": "default", "fontColor": "white",
                      "background": "panel", "scrollStyle": "default", "listStyle": "default" }
                  }
                }
                """, false, "UTF-8");
        FileHandle project = folder("select-project");

        AssetMeta imported = new Scene2dSkinAssetImportService(new AssetMetaDatabase())
                .importNew(descriptor, project);
        FileHandle bundle = project.child(StudioFs.DIR_ORIG_SKINS + "/" + imported.id());

        assertTrue(bundle.child("game.json").exists());
        assertTrue(bundle.child("game.atlas").exists());
        assertTrue(bundle.child("page.png").exists());
        assertTrue(bundle.child("fonts/font.fnt").exists());
        assertFalse(bundle.child("fonts/missing-direct-page.png").exists());
    }

    @Test public void nativeAliasesAndFullClassNamesProduceEquivalentResources() throws Exception {
        FileHandle aliasesSource = folder("aliases-source");
        FileHandle fullNamesSource = folder("full-names-source");
        writeSelectBoxBundle(aliasesSource, true);
        writeSelectBoxBundle(fullNamesSource, false);
        FileHandle project = folder("equivalent-project");
        AssetMetaDatabase database = new AssetMetaDatabase();
        Scene2dSkinAssetImportService importer = new Scene2dSkinAssetImportService(database);

        AssetMeta aliasesAsset = importer.importNew(aliasesSource.child("game.json"), project);
        AssetMeta fullNamesAsset = importer.importNew(fullNamesSource.child("game.json"), project);

        Skin aliases = loadSkin(project.child(aliasesAsset.sourceRelPath()));
        Skin fullNames = loadSkin(project.child(fullNamesAsset.sourceRelPath()));
        try {
            SelectBox.SelectBoxStyle aliasesSelect = aliases.get("default", SelectBox.SelectBoxStyle.class);
            SelectBox.SelectBoxStyle fullNamesSelect = fullNames.get("default", SelectBox.SelectBoxStyle.class);
            assertNotNull(aliasesSelect.font);
            assertNotNull(aliasesSelect.listStyle);
            assertNotNull(aliasesSelect.scrollStyle);
            assertEquals(fullNamesSelect.font.getData().lineHeight,
                    aliasesSelect.font.getData().lineHeight, 0f);
            assertEquals(fullNames.getAll(List.ListStyle.class).size,
                    aliases.getAll(List.ListStyle.class).size);
            assertEquals(fullNames.getAll(ScrollPane.ScrollPaneStyle.class).size,
                    aliases.getAll(ScrollPane.ScrollPaneStyle.class).size);
            assertEquals(fullNames.getAll(SelectBox.SelectBoxStyle.class).size,
                    aliases.getAll(SelectBox.SelectBoxStyle.class).size);
            assertNotNull(aliases.get("default", Slider.SliderStyle.class).background);
            assertEquals(fullNames.getAll(Slider.SliderStyle.class).size,
                    aliases.getAll(Slider.SliderStyle.class).size);
            assertNotNull(aliases.get("progress", ProgressBar.ProgressBarStyle.class).background);
            assertNotNull(aliases.get("progress", ProgressBar.ProgressBarStyle.class).knobBefore);
            assertEquals(fullNames.getAll(ProgressBar.ProgressBarStyle.class).size,
                    aliases.getAll(ProgressBar.ProgressBarStyle.class).size);
            assertNotNull(aliases.get("hint", TextTooltip.TextTooltipStyle.class).label.font);
            assertEquals(fullNames.getAll(TextTooltip.TextTooltipStyle.class).size,
                    aliases.getAll(TextTooltip.TextTooltipStyle.class).size);
            assertNotNull(aliases.get("dialog", Window.WindowStyle.class).titleFont);
            assertEquals(fullNames.getAll(Window.WindowStyle.class).size,
                    aliases.getAll(Window.WindowStyle.class).size);
        } finally {
            aliases.dispose();
            fullNames.dispose();
        }
    }

    @Test public void homonymsRemainDistinctAndReimportPreservesIdPathAndLogicalName() throws Exception {
        FileHandle first = folder("homonym-first");
        FileHandle second = folder("homonym-second");
        first.child("game.json").writeString("{}", false, "UTF-8");
        second.child("game.json").writeString("{}", false, "UTF-8");
        FileHandle replacement = folder("replacement");
        replacement.child("renamed.json").writeString("""
                { "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane$ScrollPaneStyle": {
                    "default": {}
                } }
                """, false, "UTF-8");
        FileHandle project = folder("homonym-project");
        AssetMetaDatabase database = new AssetMetaDatabase();
        Scene2dSkinAssetImportService importer = new Scene2dSkinAssetImportService(database);

        AssetMeta original = importer.importNew(first.child("game.json"), project);
        AssetMeta other = importer.importNew(second.child("game.json"), project);
        String originalPath = original.sourceRelPath();
        AssetMeta reimported = importer.reimport(original.id(), replacement.child("renamed.json"), project);

        assertNotEquals(original.id(), other.id());
        assertNotEquals(original.logicalPath(), other.logicalPath());
        assertEquals(original.id(), reimported.id());
        assertEquals(original.logicalPath(), reimported.logicalPath());
        assertEquals(originalPath, reimported.sourceRelPath());
        assertTrue(project.child(originalPath).readString("UTF-8").contains("ScrollPaneStyle"));
        assertFalse(project.child(StudioFs.DIR_ORIG_SKINS + "/" + original.id()
                + "/renamed.json").exists());
    }

    private FileHandle folder(String name) throws Exception {
        return new FileHandle(temporary.newFolder(name));
    }

    private static void writePng(FileHandle file) {
        writePng(file, 2, 2);
    }

    private static void writePng(FileHandle file, int width, int height) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(width, height, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static String fontDescriptor(String page) {
        return """
                info face="test" size=16 bold=0 italic=0 charset="" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1
                common lineHeight=16 base=12 scaleW=2 scaleH=2 pages=1 packed=0
                page id=0 file="%s"
                chars count=1
                char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0
                """.formatted(page);
    }

    private static String atlas(boolean pma) {
        return """
                page.png
                size: 2, 2
                format: RGBA8888
                filter: Nearest, Nearest
                repeat: none
                pma: %s
                panel
                  bounds: 0, 0, 2, 2
                """.formatted(pma);
    }

    private static void writeSelectBoxBundle(FileHandle root, boolean aliases) {
        writePng(root.child("page.png"), 4, 2);
        root.child("game.atlas").writeString("""
                page.png
                size: 4, 2
                format: RGBA8888
                filter: Nearest, Nearest
                repeat: none
                pma: false
                panel
                  bounds: 0, 0, 2, 2
                font
                  bounds: 2, 0, 2, 2
                """, false, "UTF-8");
        root.child("fonts/font.fnt").writeString(fontDescriptor("atlas-font.png"),
                false, "UTF-8");
        String color = aliases ? "Color" : "com.badlogic.gdx.graphics.Color";
        String font = aliases ? "BitmapFont" : "com.badlogic.gdx.graphics.g2d.BitmapFont";
        String list = aliases ? "ListStyle" : "com.badlogic.gdx.scenes.scene2d.ui.List$ListStyle";
        String scroll = aliases ? "ScrollPaneStyle"
                : "com.badlogic.gdx.scenes.scene2d.ui.ScrollPane$ScrollPaneStyle";
        String select = aliases ? "SelectBoxStyle"
                : "com.badlogic.gdx.scenes.scene2d.ui.SelectBox$SelectBoxStyle";
        String slider = aliases ? "SliderStyle"
                : "com.badlogic.gdx.scenes.scene2d.ui.Slider$SliderStyle";
        String progressBar = aliases ? "ProgressBarStyle"
                : "com.badlogic.gdx.scenes.scene2d.ui.ProgressBar$ProgressBarStyle";
        String tooltip = aliases ? "TextTooltipStyle"
                : "com.badlogic.gdx.scenes.scene2d.ui.TextTooltip$TextTooltipStyle";
        String window = aliases ? "WindowStyle"
                : "com.badlogic.gdx.scenes.scene2d.ui.Window$WindowStyle";
        String drawable = aliases ? "TextureRegionDrawable"
                : "com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable";
        root.child("game.json").writeString("""
                {
                  "%s": { "white": { "r": 1, "g": 1, "b": 1, "a": 1 } },
                  "%s": { "default": { "file": "fonts/font.fnt" } },
                  "%s": {},
                  "%s": { "default": { "font": "default", "fontColorSelected": "white",
                    "fontColorUnselected": "white", "selection": "panel" } },
                  "%s": { "default": { "background": "panel" } },
                  "%s": { "default": { "font": "default", "fontColor": "white",
                    "background": "panel", "scrollStyle": "default", "listStyle": "default" } },
                  "%s": { "default": { "background": "panel", "knob": "panel" } },
                  "%s": { "progress": { "background": "panel", "knobBefore": "panel" } },
                  "%s": { "hint": { "label": { "font": "default", "fontColor": "white" },
                    "background": "panel", "wrapWidth": 150 } },
                  "%s": { "dialog": { "titleFont": "default", "background": "panel" } }
                }
                """.formatted(color, font, drawable, list, scroll, select, slider, progressBar,
                        tooltip, window), false, "UTF-8");
    }

    private static Skin loadSkin(FileHandle descriptor) {
        FileHandle atlas = descriptor.sibling(descriptor.nameWithoutExtension() + ".atlas");
        Skin skin = new Skin(new TextureAtlas(atlas));
        skin.load(descriptor);
        return skin;
    }
}
