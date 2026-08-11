package games.pixscape.studio.ui.modal;

import com.kotcrab.vis.ui.widget.VisDialog;

public class StudioDialog extends VisDialog {

    public StudioDialog(String title) {
        super(title);
        StudioModalChrome.apply(this);
        StudioModalChrome.installBackgroundTitleBar(this);
    }
}
