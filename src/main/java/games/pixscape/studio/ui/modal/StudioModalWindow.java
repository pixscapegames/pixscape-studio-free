package games.pixscape.studio.ui.modal;

import com.kotcrab.vis.ui.widget.VisWindow;

public class StudioModalWindow extends VisWindow {

    public StudioModalWindow(String title) {
        super(title);
        setModal(true);
        StudioModalChrome.apply(this);
    }

    @Override
    public void layout() {
        super.layout();
        StudioModalChrome.layoutTitleBarEdgeToEdge(this);
    }
}
