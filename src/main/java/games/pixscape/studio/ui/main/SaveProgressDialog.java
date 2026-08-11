package games.pixscape.studio.ui.main;

import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisProgressBar;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.ui.Button;
import games.pixscape.studio.ui.modal.StudioDialog;

public final class SaveProgressDialog extends StudioDialog {

    private final VisLabel messageLabel;
    private final VisProgressBar progressBar =
            new VisProgressBar(0f, 1f, 0.01f, false);

    public SaveProgressDialog() {
        this("Saving project", "Preparing...");
    }

    public SaveProgressDialog(String title, String initialMessage) {
        super(title);
        messageLabel = new VisLabel(initialMessage != null ? initialMessage : "");
        setModal(true);
        setMovable(false);
        setResizable(false);
        setKeepWithinStage(true);

        messageLabel.setWrap(true);

        progressBar.setValue(0f);

        getContentTable().pad(12f);
        getContentTable().add(messageLabel).width(360f).left().row();
        getContentTable().add(progressBar).width(360f).padTop(10f).row();

        pack();
    }

    public void preventUserClose() {
        for (Actor child : getTitleTable().getChildren()) {
            if (child instanceof Button button) {
                button.setDisabled(true);
            }
        }
    }

    public void updateProgress(float value, String message) {
        progressBar.setValue(Math.max(0f, Math.min(1f, value)));
        messageLabel.setText(message != null ? message : "");
        invalidateHierarchy();
    }
}
