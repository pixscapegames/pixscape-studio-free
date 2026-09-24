package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import games.pixscape.runtime.hud.HudMaterializer;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudLabelData;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.runtime.hud.document.HudTextraLabelData;
import com.github.tommyettinger.textra.TypingLabel;
import games.pixscape.studio.asset.AssetMeta;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.asset.AssetType;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class HudAuthoringBitmapFontTest {
    @Rule public final TemporaryFolder temporary = new TemporaryFolder();
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void loadsOneSharedMultipageFontForEveryNativeTextWidget() throws Exception {
        FileHandle project = new FileHandle(temporary.newFolder("project"));
        FileHandle bundle = project.child("orig/fonts/1");
        writePng(bundle.child("zero.png"));
        writePng(bundle.child("one.png"));
        bundle.child("font.fnt").writeString(descriptor(), false, "UTF-8");
        AssetMetaDatabase database = new AssetMetaDatabase();
        AssetMeta fontMeta = database.registerIfAbsent(AssetType.FONT, "fonts/test",
                "orig/fonts/1/font.fnt", AssetMeta.AssetScope.USER);

        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode overridden = label("override", fontMeta.id());
        HudNode inherited = label("inherited", null);
        HudNode textra = new HudNode("textra", HudNodeKind.TEXTRA_LABEL);
        textra.textraLabel = new HudTextraLabelData();
        textra.textraLabel.text = "AB";
        textra.textraLabel.fontAssetId = fontMeta.id();
        HudNode button = new HudNode("button", HudNodeKind.TEXT_BUTTON);
        button.textButton = new games.pixscape.runtime.hud.document.HudTextButtonData();
        button.textButton.text = "AB";
        button.textButton.fontAssetId = fontMeta.id();
        HudNode check = new HudNode("check", HudNodeKind.CHECK_BOX);
        check.checkBox = new games.pixscape.runtime.hud.document.HudCheckBoxData();
        check.checkBox.fontAssetId = fontMeta.id();
        HudNode field = new HudNode("field", HudNodeKind.TEXT_FIELD);
        field.textField = new games.pixscape.runtime.hud.document.HudTextFieldData();
        field.textField.fontAssetId = fontMeta.id();
        HudNode select = new HudNode("select", HudNodeKind.SELECT_BOX);
        select.selectBox = new games.pixscape.runtime.hud.document.HudSelectBoxData();
        select.selectBox.selectedIndex = -1;
        select.selectBox.fontAssetId = fontMeta.id();
        root.children.add(HudChild.direct(overridden));
        root.children.add(HudChild.direct(inherited));
        root.children.add(HudChild.direct(textra));
        root.children.add(HudChild.direct(button));
        root.children.add(HudChild.direct(check));
        root.children.add(HudChild.direct(field));
        root.children.add(HudChild.direct(select));
        HudDocumentV1 document = new HudDocumentV1(root);
        var structural = new HudDocumentValidator().validate(document);
        HudAuthoringResources resources = HudAuthoringResources.prepare(
                structural.validatedDocument(), project, database, null);
        try {
            assertSame(resources.bitmapFont(fontMeta.id()), resources.bitmapFont(fontMeta.id()));
            assertEquals(2, resources.bitmapFont(fontMeta.id()).getRegions().size);
            assertEquals(0, resources.bitmapFont(fontMeta.id()).getData().getGlyph('A').page);
            assertEquals(1, resources.bitmapFont(fontMeta.id()).getData().getGlyph('B').page);
            assertNull(resources.bitmapFont(fontMeta.id()).getData().getGlyph('\u2588'));
            assertSame(resources.textraFont(resources.bitmapFont(fontMeta.id())),
                    resources.textraFont(resources.bitmapFont(fontMeta.id())));
            var resourceAware = new HudDocumentValidator().validate(document, resources);
            assertTrue(resourceAware.issues().toString(), resourceAware.isValid());
            var hud = new HudMaterializer().materialize(resourceAware.validatedDocument(), resources);
            var overrideLabel = (com.badlogic.gdx.scenes.scene2d.ui.Label) hud.actor("override");
            var inheritedLabel = (com.badlogic.gdx.scenes.scene2d.ui.Label) hud.actor("inherited");
            assertSame(resources.bitmapFont(fontMeta.id()), overrideLabel.getStyle().font);
            assertNotSame(overrideLabel.getStyle(), inheritedLabel.getStyle());
            assertSame(resources.builtInLabelStyle(), inheritedLabel.getStyle());
            assertSame(resources.bitmapFont(fontMeta.id()),
                    ((com.badlogic.gdx.scenes.scene2d.ui.TextButton) hud.actor("button")).getStyle().font);
            assertSame(resources.bitmapFont(fontMeta.id()),
                    ((com.badlogic.gdx.scenes.scene2d.ui.CheckBox) hud.actor("check")).getStyle().font);
            var fieldActor = (com.badlogic.gdx.scenes.scene2d.ui.TextField) hud.actor("field");
            assertSame(resources.bitmapFont(fontMeta.id()), fieldActor.getStyle().font);
            assertSame(resources.bitmapFont(fontMeta.id()), fieldActor.getStyle().messageFont);
            var selectActor = (com.badlogic.gdx.scenes.scene2d.ui.SelectBox<?>) hud.actor("select");
            assertSame(resources.bitmapFont(fontMeta.id()), selectActor.getStyle().font);
            assertSame(resources.bitmapFont(fontMeta.id()), selectActor.getStyle().listStyle.font);
            assertTrue(hud.actor("textra") instanceof TypingLabel);
            assertNull(resources.bitmapFont(fontMeta.id()).getData().getGlyph('\u2588'));
            hud.dispose();
        } finally {
            resources.dispose();
        }
    }

    private static HudNode label(String id, Integer fontAssetId) {
        HudNode node = new HudNode(id, HudNodeKind.LABEL);
        node.label = new HudLabelData();
        node.label.text = "AB";
        node.label.fontAssetId = fontAssetId;
        return node;
    }

    private static void writePng(FileHandle file) {
        file.parent().mkdirs();
        Pixmap pixmap = new Pixmap(2, 2, Pixmap.Format.RGBA8888);
        try {
            pixmap.setColor(1f, 1f, 1f, 1f);
            pixmap.fill();
            PixmapIO.writePNG(file, pixmap);
        } finally {
            pixmap.dispose();
        }
    }

    private static String descriptor() {
        return "info face=\"test\" size=16 bold=0 italic=0 charset=\"\" unicode=0 stretchH=100 smooth=1 aa=1 padding=0,0,0,0 spacing=1,1\n"
                + "common lineHeight=16 base=12 scaleW=2 scaleH=2 pages=2 packed=0\n"
                + "page id=0 file=\"zero.png\"\npage id=1 file=\"one.png\"\n"
                + "chars count=2\n"
                + "char id=65 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=0 chnl=0\n"
                + "char id=66 x=0 y=0 width=1 height=1 xoffset=0 yoffset=0 xadvance=1 page=1 chnl=0\n";
    }
}
