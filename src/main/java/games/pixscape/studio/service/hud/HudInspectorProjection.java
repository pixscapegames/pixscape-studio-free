package games.pixscape.studio.service.hud;

import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudCellConstraints;
import games.pixscape.runtime.hud.document.HudChild;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudFreePlacement;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudPlacementKind;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Locale;

/** Read-only inspector rows derived directly from Runtime DTOs. */
public final class HudInspectorProjection {
    public record Field(String label, String value) {}

    private HudInspectorProjection() {}

    public static List<Field> screen(HudEditorSession session) {
        List<Field> rows = new ArrayList<>();
        rows.add(new Field("Screen ID", text(session.screenId())));
        HudScreenAsset asset = session.asset();
        if (asset != null) {
            rows.add(new Field("Document", text(asset.documentId)));
            rows.add(new Field("Skin", text(asset.skinId)));
            rows.add(new Field("Atlas", text(asset.atlasId)));
            rows.add(new Field("Texture profile", text(asset.textureProfileId)));
        }
        rows.add(new Field("Status", session.status().name()));
        return Collections.unmodifiableList(rows);
    }

    public static List<Field> node(HudDocumentV1 document, HudNode node) {
        if (node == null) return List.of();
        List<Field> rows = new ArrayList<>();
        rows.add(new Field("Node ID", text(node.id)));
        rows.add(new Field("Width", dimension(node.actor != null ? node.actor.width : 0f)));
        rows.add(new Field("Height", dimension(node.actor != null ? node.actor.height : 0f)));
        HudChild relation = HudLayoutAuthoring.childRelation(document, node.id);
        HudPlacementKind placement = relation != null ? relation.placementKind : HudPlacementKind.DIRECT;
        rows.add(new Field("Placement", String.valueOf(placement)));
        if (node.fillParent) rows.add(new Field("Fill HUD surface", "true"));
        if (placement == HudPlacementKind.CELL && relation.cell != null) addCell(rows, relation.cell);
        if (placement == HudPlacementKind.FREE && relation.free != null) addFree(rows, relation.free);
        if (node.image != null) {
            rows.add(new Field("Image source", String.valueOf(node.image.source)));
            rows.add(new Field("Resource", text(node.image.resourceName)));
        }
        if (node.label != null) {
            rows.add(new Field("Text", text(node.label.text)));
            rows.add(new Field("Style", text(node.label.styleName)));
        }
        if (node.textraLabel != null) {
            rows.add(new Field("Text", text(node.textraLabel.text)));
            rows.add(new Field("Style", text(node.textraLabel.styleName)));
            rows.add(new Field("Typing",
                    Boolean.toString(node.textraLabel.typingEnabled)));
        }
        if (node.textButton != null) {
            rows.add(new Field("Text", text(node.textButton.text)));
            rows.add(new Field("Style", text(node.textButton.styleName)));
        }
        if (node.checkBox != null) {
            rows.add(new Field("Text", text(node.checkBox.text)));
            rows.add(new Field("Style", text(node.checkBox.styleName)));
            rows.add(new Field("Checked", Boolean.toString(node.checkBox.checked)));
            rows.add(new Field("Disabled", Boolean.toString(node.checkBox.disabled)));
        }
        if (node.textField != null) {
            rows.add(new Field("Text", text(node.textField.text)));
            rows.add(new Field("Placeholder", text(node.textField.messageText)));
            rows.add(new Field("Style", text(node.textField.styleName)));
            rows.add(new Field("Max length", Integer.toString(node.textField.maxLength)));
            rows.add(new Field("Password", Boolean.toString(node.textField.passwordMode)));
        }
        if (node.container != null) rows.add(new Field("Clip", Boolean.toString(node.container.clip)));
        return Collections.unmodifiableList(rows);
    }

    private static void addCell(List<Field> rows, HudCellConstraints cell) {
        rows.add(new Field("Minimum", number(cell.minWidth) + " × " + number(cell.minHeight)));
        rows.add(new Field("Preferred", number(cell.prefWidth)
                + " × " + number(cell.prefHeight)));
        rows.add(new Field("Maximum", number(cell.maxWidth)
                + " × " + number(cell.maxHeight)));
        rows.add(new Field("Padding", number(cell.padTop) + ", " + number(cell.padRight)
                + ", " + number(cell.padBottom) + ", " + number(cell.padLeft)));
        rows.add(new Field("Fill / expand", cell.fillX + "/" + cell.fillY + "  "
                + cell.expandX + "/" + cell.expandY));
        rows.add(new Field("Alignment", cell.horizontalAlign + " / " + cell.verticalAlign));
    }

    private static void addFree(List<Field> rows, HudFreePlacement free) {
        rows.add(new Field("Horizontal anchor", String.valueOf(free.horizontalAnchor)));
        rows.add(new Field("Vertical anchor", String.valueOf(free.verticalAnchor)));
        rows.add(new Field("Pivot X", number(free.pivotX)));
        rows.add(new Field("Pivot Y", number(free.pivotY)));
        rows.add(new Field("Offset X", wholeNumber(free.offsetX)));
        rows.add(new Field("Offset Y", wholeNumber(free.offsetY)));
    }

    private static String text(String value) { return value == null || value.isBlank() ? "—" : value; }
    private static String number(Float value) { return value == null ? "Auto" : Float.toString(value); }
    private static String wholeNumber(float value) { return String.format(Locale.ROOT, "%.0f", value); }
    private static String dimension(float value) { return String.format(Locale.ROOT, "%.1f", value); }
}
