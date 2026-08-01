package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.kotcrab.vis.ui.widget.VisDialog;

public class StudioDialog extends VisDialog {

    public StudioDialog(String title) {
        super(title);
        StudioModalChrome.apply(this);
    }

    @Override
    protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
        super.drawBackground(batch, parentAlpha, x, y);
        StudioModalChrome.drawTitleBarBackground(this, batch, parentAlpha, x, y);
    }
}
