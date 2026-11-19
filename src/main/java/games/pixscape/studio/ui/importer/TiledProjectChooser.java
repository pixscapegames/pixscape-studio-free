package games.pixscape.studio.ui.importer;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.kotcrab.vis.ui.widget.file.FileChooser;
import com.kotcrab.vis.ui.widget.file.FileTypeFilter;
import com.kotcrab.vis.ui.widget.file.StreamingFileChooserListener;
import games.pixscape.studio.io.StudioFs;
import games.pixscape.studio.ui.modal.StudioFileChooser;
import java.util.function.Consumer;

/** Explicit project selection; cancelling never invokes the continuation or mutates project data. */
public final class TiledProjectChooser {
    private TiledProjectChooser() {
    }

    public static void show(Stage stage, FileHandle directory, Consumer<FileHandle> selected) {
        StudioFileChooser chooser = new StudioFileChooser(directory != null ? directory
                : StudioFs.defaultUserProjectsRoot(), FileChooser.Mode.OPEN);
        chooser.getTitleLabel().setText("Select Tiled project for custom types");
        chooser.setSelectionMode(FileChooser.SelectionMode.FILES);
        chooser.setMultiSelectionEnabled(false);
        FileTypeFilter filter = new FileTypeFilter(false);
        filter.addRule("Tiled project (*.tiled-project)", "tiled-project");
        chooser.setFileTypeFilter(filter);
        chooser.setListener(new StreamingFileChooserListener() {
            @Override
            public void selected(FileHandle file) {
                selected.accept(file);
            }
        });
        stage.addActor(chooser.fadeIn());
    }
}
