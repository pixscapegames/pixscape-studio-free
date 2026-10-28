package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.graphics.g2d.TextureRegion;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Label;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.scenes.scene2d.ui.TextButton;
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

public class HudLabelAuthoringPipelineTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void createdFreeAndTableLabelsMaterializeAtVisibleNaturalSize() {
        Label.LabelStyle style = VisUI.getSkin().get("default", Label.LabelStyle.class);
        Label probe = new Label("Étiquette", style);
        HudVisualResources resources = labelResources(style);

        HudNode freeRoot = new HudNode("root", HudNodeKind.GROUP);
        HudDocumentV1 freeDocument = new HudDocumentV1(freeRoot);
        assertEquals("label-1", HudLayoutAuthoring.addLabel(freeDocument, "root",
                "Étiquette", null));
        MaterializedHud freeHud = materialize(freeDocument, resources);
        Actor freeLabel = freeHud.actor("label-1");
        assertTrue(freeLabel.getWidth() > 0f);
        assertTrue(freeLabel.getHeight() > 0f);
        float initialWidth = freeLabel.getWidth();
        freeDocument.root.children.get(0).node.label.text = "Étiquette beaucoup plus longue";
        Actor editedFreeLabel = materialize(freeDocument, resources).actor("label-1");
        assertTrue(editedFreeLabel.getWidth() > initialWidth);

        HudNode tableRoot = new HudNode("root", HudNodeKind.TABLE);
        tableRoot.table = HudLayoutAuthoring.newTableLayout(tableRoot, 1, 1, false);
        HudDocumentV1 tableDocument = new HudDocumentV1(tableRoot);
        assertEquals("label-1", HudLayoutAuthoring.addLabel(tableDocument, "root",
                "Étiquette", null));
        MaterializedHud tableHud = materialize(tableDocument, resources);
        Table table = (Table) tableHud.root();
        table.pack();
        table.validate();
        Actor tableLabel = tableHud.actor("label-1");
        assertEquals(probe.getPrefWidth(), tableLabel.getWidth(), 0.01f);
        assertEquals(probe.getPrefHeight(), tableLabel.getHeight(), 0.01f);
    }

    private static MaterializedHud materialize(HudDocumentV1 document,
                                                HudVisualResources resources) {
        var validation = new HudDocumentValidator().validate(document);
        assertTrue(validation.issues().toString(), validation.isValid());
        return new HudMaterializer().materialize(validation.validatedDocument(), resources);
    }

    private static HudVisualResources labelResources(Label.LabelStyle style) {
        return new HudVisualResources() {
            @Override public TextureRegion region(String name) { return null; }
            @Override public Drawable drawable(String name) { return null; }
            @Override public Label.LabelStyle labelStyle(String name) {
                return "default".equals(name) ? style : null;
            }
            @Override public Label.LabelStyle builtInLabelStyle() { return style; }
            @Override public TextButton.TextButtonStyle textButtonStyle(String name) { return null; }
        };
    }
}
