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

    public interface GameObjectCommands {
        void save(GameObjectEditorDocument document);
        boolean undo(GameObjectEditorDocument document);
        boolean redo(GameObjectEditorDocument document);
    }

    private final EditorDocumentManager manager;
    private final SceneCommands sceneCommands;
    private final HudSave hudSave;
    private final GameObjectCommands gameObjectCommands;

    public ActiveDocumentCommandRouter(EditorDocumentManager manager,
                                       SceneCommands sceneCommands,
                                       HudSave hudSave) {
        this(manager, sceneCommands, hudSave, new GameObjectCommands() {
            @Override public void save(GameObjectEditorDocument document) { }
            @Override public boolean undo(GameObjectEditorDocument document) { return false; }
            @Override public boolean redo(GameObjectEditorDocument document) { return false; }
        });
    }

    public ActiveDocumentCommandRouter(EditorDocumentManager manager,
                                       SceneCommands sceneCommands,
                                       HudSave hudSave,
                                       GameObjectCommands gameObjectCommands) {
        this.manager = Objects.requireNonNull(manager, "manager");
        this.sceneCommands = Objects.requireNonNull(sceneCommands, "sceneCommands");
        this.hudSave = Objects.requireNonNull(hudSave, "hudSave");
        this.gameObjectCommands = Objects.requireNonNull(gameObjectCommands, "gameObjectCommands");
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
        if (active instanceof GameObjectEditorDocument gameObject) {
            try {
                gameObjectCommands.save(gameObject);
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
        if (active instanceof GameObjectEditorDocument gameObject) return gameObjectCommands.undo(gameObject);
        return active instanceof SceneEditorDocument scene && sceneCommands.undo(scene);
    }

    public boolean redo() {
        OpenEditorDocument active = manager.activeDocument();
        if (active instanceof HudScreenEditorDocument hud) return hud.editSession().redo();
        if (active instanceof GameObjectEditorDocument gameObject) return gameObjectCommands.redo(gameObject);
        return active instanceof SceneEditorDocument scene && sceneCommands.redo(scene);
    }
}
