package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.graphics.g2d.Batch;
import com.kotcrab.vis.ui.widget.VisWindow;

public class StudioModalWindow extends VisWindow {

    public StudioModalWindow(String title) {
        super(title);
        setModal(true);
        StudioModalChrome.apply(this);
    }

    @Override
    protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
        super.drawBackground(batch, parentAlpha, x, y);
        StudioModalChrome.drawTitleBarBackground(this, batch, parentAlpha, x, y);
    }
}
