package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.document.*;
import org.junit.Assert;
import org.junit.Test;

import java.util.List;

public class HudInspectorProjectionTest {
    @Test
    public void projectsCommonCellAndLabelFields() {
        HudNode root = new HudNode("table", HudNodeKind.TABLE);
        HudNode label = new HudNode("score", HudNodeKind.LABEL);
        label.actor.width = 120f;
        label.actor.height = 32f;
        label.label = new HudLabelData();
        label.label.text = "Score";
        label.label.styleName = "hud-label";
        HudCellConstraints cell = new HudCellConstraints();
        cell.prefWidth = 120f;
        cell.padLeft = 8f;
        root.children.add(HudChild.cell(label, cell));

        List<HudInspectorProjection.Field> fields = HudInspectorProjection.node(
                new HudDocumentV1(root), label);
        assertField(fields, "Node ID", "score");
        assertField(fields, "Width", "120.0");
        assertField(fields, "Height", "32.0");
        assertField(fields, "Placement", "CELL");
        assertField(fields, "Text", "Score");
        assertField(fields, "Style", "hud-label");
        Assert.assertFalse(fields.stream().anyMatch(f -> f.label().equals("Kind")));
    }

    @Test
    public void projectsFreeImageButtonAndContainerPayloads() {
        HudNode root = new HudNode("root", HudNodeKind.GROUP);
        HudNode image = new HudNode("portrait", HudNodeKind.IMAGE);
        image.image = new HudImageData();
        image.image.source = HudImageSource.DRAWABLE;
        image.image.resourceName = "portrait-frame";
        HudFreePlacement free = new HudFreePlacement();
        free.horizontalAnchor = HudHorizontalAnchor.RIGHT;
        free.verticalAnchor = HudVerticalAnchor.TOP;
        free.pivotX = 1f;
        free.pivotY = 1f;
        free.offsetX = -12.4f;
        free.offsetY = 7.6f;
        root.children.add(HudChild.free(image, free));
        List<HudInspectorProjection.Field> imageFields = HudInspectorProjection.node(
                new HudDocumentV1(root), image);
        assertField(imageFields, "Placement", "FREE");
        assertField(imageFields, "Offset X", "-12");
        assertField(imageFields, "Offset Y", "8");
        assertField(imageFields, "Image source", "DRAWABLE");
        assertField(imageFields, "Resource", "portrait-frame");

        HudNode button = new HudNode("play", HudNodeKind.TEXT_BUTTON);
        button.textButton = new HudTextButtonData();
        button.textButton.text = "Play";
        button.textButton.styleName = "primary";
        assertField(HudInspectorProjection.node(new HudDocumentV1(button), button), "Style", "primary");

        HudNode container = new HudNode("clip", HudNodeKind.CONTAINER);
        container.container = new HudContainerData();
        container.container.clip = true;
        assertField(HudInspectorProjection.node(new HudDocumentV1(container), container), "Clip", "true");
    }

    private static void assertField(List<HudInspectorProjection.Field> fields,
                                    String label, String value) {
        Assert.assertTrue(fields.stream().anyMatch(f -> f.label().equals(label) && f.value().equals(value)));
    }
}
