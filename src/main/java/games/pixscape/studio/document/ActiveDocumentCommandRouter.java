package games.pixscape.studio.document;

import java.util.Objects;
import java.util.function.Consumer;

/** Routes document commands from menus/shortcuts to the exact active document. */
public final class ActiveDocumentCommandRouter {
    public interface SceneCommands {
        void save(SceneEditorDocument document, Runnable onSuccess, Consumer<Throwable> onFailure);
        boolean undo(SceneEditorDocument document);
        boolean redo(SceneEditorDocument document);
    }

    @FunctionalInterface
    public interface HudSave {
        void save(HudScreenEditorDocument document);
    }

    private final EditorDocumentManager manager;
    private final SceneCommands sceneCommands;
    private final HudSave hudSave;

    public ActiveDocumentCommandRouter(EditorDocumentManager manager,
                                       SceneCommands sceneCommands,
                                       HudSave hudSave) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.sceneCommands = Objects.requireNonNull(sceneCommands, "sceneCommands");
        this.hudSave = Objects.requireNonNull(hudSave, "hudSave");
    }

    public void save(Runnable onSuccess, Consumer<Throwable> onFailure) {
        OpenEditorDocument active = manager.activeDocument();
        if (active instanceof SceneEditorDocument scene) {
            sceneCommands.save(scene, onSuccess, onFailure);
            return;
        }
        if (active instanceof HudScreenEditorDocument hud) {
            try {
                hudSave.save(hud);
                if (onSuccess != null) onSuccess.run();
            } catch (RuntimeException failure) {
                if (onFailure != null) onFailure.accept(failure);
            }
            return;
        }
        if (onSuccess != null) onSuccess.run();
    }

    public boolean undo() {
        OpenEditorDocument active = manager.activeDocument();
        if (active instanceof HudScreenEditorDocument hud) return hud.editSession().undo();
        return active instanceof SceneEditorDocument scene && sceneCommands.undo(scene);
    }

    public boolean redo() {
        OpenEditorDocument active = manager.activeDocument();
        if (active instanceof HudScreenEditorDocument hud) return hud.editSession().redo();
        return active instanceof SceneEditorDocument scene && sceneCommands.redo(scene);
    }
}
