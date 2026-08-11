package games.pixscape.studio.ui.importer;

import games.pixscape.studio.ui.modal.StudioDialog;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ScrollPane;
import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisScrollPane;
import com.kotcrab.vis.ui.widget.VisTable;

public final class TmxImportMessageDialog {

    private static final float CONTENT_WIDTH = 620f;
    private static final float MIN_SCROLL_HEIGHT = 140f;
    private static final float MAX_SCROLL_HEIGHT = 380f;
    private static final float STAGE_HEIGHT_FRACTION = 0.48f;

    private TmxImportMessageDialog() {
    }

    public static void show(Stage stage, String title, String message) {
        if (stage == null) {
            return;
        }

        VisDialog dialog = new StudioDialog(title != null ? title : "Tiled map import");
        dialog.setModal(true);
        dialog.setMovable(true);
        dialog.closeOnEscape();

        VisLabel body = new VisLabel(message != null && !message.isBlank() ? message : "No diagnostics.");
        body.setWrap(true);

        VisTable content = new VisTable();
        content.defaults().left().top();
        content.add(body).width(CONTENT_WIDTH).growX();

        ScrollPane scroll = new VisScrollPane(content);
        scroll.setFadeScrollBars(false);
        scroll.setScrollingDisabled(true, false);

        dialog.getContentTable()
                .pad(12)
                .add(scroll)
                .width(CONTENT_WIDTH + 24f)
                .height(scrollHeight(stage))
                .growX();
        dialog.button("OK");
        dialog.show(stage);
    }

    static float scrollHeight(Stage stage) {
        float stageHeight = stage != null ? stage.getHeight() : 0f;
        if (stageHeight <= 0f) {
            return 240f;
        }
        return Math.max(MIN_SCROLL_HEIGHT, Math.min(MAX_SCROLL_HEIGHT, stageHeight * STAGE_HEIGHT_FRACTION));
    }
}
