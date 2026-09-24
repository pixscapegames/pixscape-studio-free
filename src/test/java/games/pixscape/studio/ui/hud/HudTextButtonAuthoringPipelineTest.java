package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
import com.badlogic.gdx.scenes.scene2d.utils.BaseDrawable;
import com.badlogic.gdx.scenes.scene2d.utils.Drawable;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.hud.HudMaterializer;
import games.pixscape.runtime.hud.HudVisualResources;
import games.pixscape.runtime.hud.MaterializedHud;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.service.hud.HudLayoutAuthoring;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class HudTextButtonAuthoringPipelineTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void createdFreeAndTableButtonsMaterializeAtNaturalSize() {
        TextButton.TextButtonStyle style =
                VisUI.getSkin().get("default", TextButton.TextButtonStyle.class);
        TextButton probe = new TextButton("Button", style);
        HudVisualResources resources = buttonResources(style);

        HudDocumentV1 freeDocument = new HudDocumentV1(
                new HudNode("root", HudNodeKind.GROUP));
        assertEquals("text-button-1", HudLayoutAuthoring.addTextButton(
                freeDocument, "root", "Button", null));
        Actor freeButton = materialize(freeDocument, resources).actor("text-button-1");
        assertEquals(probe.getPrefWidth(), freeButton.getWidth(), 0.01f);
        assertEquals(probe.getPrefHeight(), freeButton.getHeight(), 0.01f);

        HudDocumentV1 tableDocument = new HudDocumentV1(
                tableRoot());
        assertEquals("text-button-1", HudLayoutAuthoring.addTextButton(
                tableDocument, "root", "Button", null));
        MaterializedHud tableHud = materialize(tableDocument, resources);
        Table table = (Table) tableHud.root();
        table.pack();
        table.validate();
        Actor tableButton = tableHud.actor("text-button-1");
        assertEquals(probe.getPrefWidth(), tableButton.getWidth(), 0.01f);
        assertEquals(probe.getPrefHeight(), tableButton.getHeight(), 0.01f);
    }

    @Test public void explicitWidthAndAutomaticHeightFollowNativeTextAndStyleLayout() {
        TextButton.TextButtonStyle style =
                VisUI.getSkin().get("default", TextButton.TextButtonStyle.class);
        HudVisualResources resources = buttonResources(style);
        HudDocumentV1 document = new HudDocumentV1(tableRoot());
        assertEquals("text-button-1", HudLayoutAuthoring.addTextButton(
                document, "root", "One line", null));
        float formerNaturalWidth = new TextButton("One line", style).getPrefWidth();
        document.root.table.rows.get(0).cells.get(0).constraints.prefWidth = formerNaturalWidth;

        MaterializedHud before = materialize(document, resources);
        Table beforeTable = (Table) before.root();
        beforeTable.pack();
        beforeTable.validate();
        Actor beforeButton = before.actor("text-button-1");
        float initialHeight = beforeButton.getHeight();

        document.root.table.rows.get(0).cells.get(0).content.textButton.text = "One line\nSecond line";
        assertEquals(Float.valueOf(formerNaturalWidth),
                document.root.table.rows.get(0).cells.get(0).constraints.prefWidth);
        document.root.table.rows.get(0).cells.get(0).constraints.prefWidth = 220f;
        MaterializedHud after = materialize(document, resources);
        Table afterTable = (Table) after.root();
        afterTable.pack();
        afterTable.validate();
        Actor afterButton = after.actor("text-button-1");

        assertEquals(220f, afterButton.getWidth(), 0.01f);
        assertTrue(afterButton.getHeight() > initialHeight);

        TextButton.TextButtonStyle tallStyle = new TextButton.TextButtonStyle(style);
        BaseDrawable tallBackground = new BaseDrawable();
        tallBackground.setMinHeight(afterButton.getHeight() + 40f);
        tallStyle.up = tallBackground;
        document.root.table.rows.get(0).cells.get(0).content.textButton.styleName = "tall";
        MaterializedHud restyled = materialize(document, buttonResources(style, tallStyle));
        Table restyledTable = (Table) restyled.root();
        restyledTable.pack();
        restyledTable.validate();
        Actor restyledButton = restyled.actor("text-button-1");
        assertEquals(220f, restyledButton.getWidth(), 0.01f);
        assertTrue(restyledButton.getHeight() > afterButton.getHeight());
    }

    private static MaterializedHud materialize(
            HudDocumentV1 document, HudVisualResources resources) {
        var validation = new HudDocumentValidator().validate(document);
        assertTrue(validation.issues().toString(), validation.isValid());
        return new HudMaterializer().materialize(validation.validatedDocument(), resources);
    }

    private static HudNode tableRoot() {
        HudNode root = new HudNode("root", HudNodeKind.TABLE);
        root.table = HudLayoutAuthoring.newTableLayout(root, 1, 1, false);
        return root;
    }

    private static HudVisualResources buttonResources(TextButton.TextButtonStyle style) {
        return buttonResources(style, null);
    }

    private static HudVisualResources buttonResources(
            TextButton.TextButtonStyle style, TextButton.TextButtonStyle tallStyle) {
        return new HudVisualResources() {
            @Override public TextureRegion region(String name) { return null; }
            @Override public Drawable drawable(String name) { return null; }
            @Override public Label.LabelStyle labelStyle(String name) { return null; }
            @Override public TextButton.TextButtonStyle textButtonStyle(String name) {
                if ("default".equals(name)) return style;
                return "tall".equals(name) ? tallStyle : null;
            }
            @Override public TextButton.TextButtonStyle builtInTextButtonStyle() { return style; }
        };
    }
}
