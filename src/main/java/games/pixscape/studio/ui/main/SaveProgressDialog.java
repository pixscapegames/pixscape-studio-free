package games.pixscape.studio.ui.main;

import com.kotcrab.vis.ui.widget.VisDialog;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisProgressBar;

public final class SaveProgressDialog extends VisDialog {

    private final VisLabel messageLabel = new VisLabel("Preparing...");
    private final VisProgressBar progressBar =
            new VisProgressBar(0f, 1f, 0.01f, false);

    public SaveProgressDialog() {
        super("Saving project");
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

    public void updateProgress(float value, String message) {
        progressBar.setValue(Math.max(0f, Math.min(1f, value)));
        messageLabel.setText(message != null ? message : "");
        invalidateHierarchy();
    }
}
