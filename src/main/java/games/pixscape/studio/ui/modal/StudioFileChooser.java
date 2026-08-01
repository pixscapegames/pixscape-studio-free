package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.kotcrab.vis.ui.widget.file.FileChooser;

public class StudioFileChooser extends FileChooser {

    public StudioFileChooser(Mode mode) {
        super(mode);
        StudioModalChrome.apply(this);
    }

    public StudioFileChooser(FileHandle directory, Mode mode) {
        super(directory, mode);
        StudioModalChrome.apply(this);
    }

    @Override
    protected void drawBackground(Batch batch, float parentAlpha, float x, float y) {
        super.drawBackground(batch, parentAlpha, x, y);
        StudioModalChrome.drawTitleBarBackground(this, batch, parentAlpha, x, y);
    }
}
