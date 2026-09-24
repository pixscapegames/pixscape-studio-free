package games.pixscape.studio.service.hud;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.runtime.hud.HudScreenAssetId;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.history.commands.Command;

import java.util.Objects;
import java.util.function.BiConsumer;
import java.util.function.Consumer;
import java.util.function.Supplier;

/** One history-backed mutation seam for a Scene's authored default HUD association. */
public final class SceneHudAssociationService {
    private final Supplier<FileHandle> projectDir;
    private final Supplier<ProjectConfig> configuration;
    private final HudDocumentPersistenceService persistence;
    private final Consumer<String> invalidateScene;
    private final BiConsumer<String, String> associationChanged;

    public SceneHudAssociationService(Supplier<FileHandle> projectDir,
                                      Supplier<ProjectConfig> configuration,
                                      HudDocumentPersistenceService persistence,
                                      Consumer<String> invalidateScene,
                                      BiConsumer<String, String> associationChanged) {
        this.projectDir = Objects.requireNonNull(projectDir);
        this.configuration = Objects.requireNonNull(configuration);
        this.persistence = Objects.requireNonNull(persistence);
        this.invalidateScene = Objects.requireNonNull(invalidateScene);
        this.associationChanged = Objects.requireNonNull(associationChanged);
    }

    public boolean canAssociate(SceneEditorDocument document, String screenId) {
        return target(document) != null && resolveValidScreenId(screenId) != null;
    }

    public boolean associate(SceneEditorDocument document, String screenId) {
        Target target = target(document);
        String canonicalScreenId = resolveValidScreenId(screenId);
        if (target == null || canonicalScreenId == null) return false;
        return change(document, target, canonicalScreenId);
    }

    public boolean remove(SceneEditorDocument document) {
        Target target = target(document);
        return target != null && change(document, target, null);
    }

    private boolean change(SceneEditorDocument document, Target target, String nextScreenId) {
        String previous = target.scene.defaultHudScreenId;
        String previousCanonical = normalizeOptional(previous);
        if (nextScreenId == null ? previous == null || previous.isBlank()
                : Objects.equals(previousCanonical, nextScreenId)) return false;
        document.context().historyManager().execute(new ChangeAssociationCommand(
                target.scene, target.sceneTag, previous, nextScreenId,
                invalidateScene, associationChanged));
        return true;
    }

    private String resolveValidScreenId(String screenId) {
        try {
            String canonical = HudScreenAssetId.normalize(screenId);
            FileHandle root = projectDir.get();
            if (root == null) return null;
            persistence.loadAsset(root, canonical);
            return canonical;
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private Target target(SceneEditorDocument document) {
        if (document == null || document.isClosed() || document.context().isDisposed()) return null;
        ProjectConfig cfg = configuration.get();
        if (cfg == null) return null;
        String documentId = document.key().domainId();
        if (!Objects.equals(documentId, document.context().sceneIdentity())) return null;
        for (String sceneName : cfg.getSceneNames()) {
            SceneMeta scene = cfg.getSceneMeta(sceneName);
            String sceneTag = cfg.canonicalSceneTagFor(scene);
            if (Objects.equals(documentId, sceneTag)) return new Target(scene, sceneTag);
        }
        return null;
    }

    private static String normalizeOptional(String screenId) {
        try {
            return HudScreenAssetId.normalizeOptional(screenId);
        } catch (RuntimeException failure) {
            return null;
        }
    }

    private record Target(SceneMeta scene, String sceneTag) {}

    private static final class ChangeAssociationCommand implements Command {
        private final SceneMeta scene;
        private final String sceneTag;
        private final String before;
        private final String after;
        private final Consumer<String> invalidateScene;
        private final BiConsumer<String, String> associationChanged;

        private ChangeAssociationCommand(SceneMeta scene, String sceneTag,
                                         String before, String after,
                                         Consumer<String> invalidateScene,
                                         BiConsumer<String, String> associationChanged) {
            this.scene = scene;
            this.sceneTag = sceneTag;
            this.before = before;
            this.after = after;
            this.invalidateScene = invalidateScene;
            this.associationChanged = associationChanged;
        }

        @Override public String label() { return after == null ? "Remove Scene HUD" : "Set Scene HUD"; }
        @Override public void redo() { apply(after); }
        @Override public void undo() { apply(before); }

        private void apply(String screenId) {
            scene.defaultHudScreenId = screenId;
            invalidateScene.accept(sceneTag);
            associationChanged.accept(sceneTag, screenId);
        }
    }
}
