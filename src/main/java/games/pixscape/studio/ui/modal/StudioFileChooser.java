package games.pixscape.studio.ui.modal;

import com.badlogic.gdx.files.FileHandle;
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
}
