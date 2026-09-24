package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.service.hud.HudEditorSession;

/** Non-interactive central status for failed HUD targets. */
public final class HudCanvasStatusOverlay extends VisTable {
    private final HudEditorSession session;
    private final VisLabel label = new VisLabel("");

    public HudCanvasStatusOverlay(HudEditorSession session) {
        this.session = session;
        setTouchable(Touchable.disabled);
        label.setWrap(true);
        label.setAlignment(com.badlogic.gdx.utils.Align.center);
        add(label).width(520f).pad(24f);
        session.addListener(this::refresh);
        refresh();
    }

    private void refresh() {
        if (session.testModeErrorMessage() != null) {
            label.setText("HUD test mode unavailable\n\n" + session.testModeErrorMessage());
            label.setColor(1f, 0.55f, 0.55f, 1f);
            setVisible(true);
            return;
        }
        switch (session.status()) {
            case CLOSED, READY -> setVisible(false);
            case ERROR -> {
                label.setText("HUD preview unavailable\n\n" + session.errorMessage());
                label.setColor(1f, 0.55f, 0.55f, 1f);
                setVisible(true);
            }
        }
    }
}
