package games.pixscape.studio.ui.document;

import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.OpenEditorDocument;

/** VisUI presentation of a document; the manager remains the lifecycle authority. */
final class EditorDocumentTab extends Tab {
    private final OpenEditorDocument document;
    private final VisTable unusedContent = new VisTable();

    EditorDocumentTab(OpenEditorDocument document) {
        super(false, document.closeable());
        this.document = document;
    }

    OpenEditorDocument document() { return document; }

    @Override public String getTabTitle() {
        String kind = document.type() == EditorDocumentType.SCENE ? "Scene" : "HUD";
        String dirty = document.isDirty() ? " *" : "";
        return kind + ": " + document.title() + dirty;
    }

    @Override public Table getContentTable() { return unusedContent; }
}
