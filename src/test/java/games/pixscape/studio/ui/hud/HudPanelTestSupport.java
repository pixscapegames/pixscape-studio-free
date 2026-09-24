package games.pixscape.studio.ui.hud;

import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.files.FileHandle;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.hud.document.HudDocumentValidator;
import games.pixscape.runtime.hud.document.HudResourceCatalog;
import games.pixscape.runtime.hud.document.HudValidationResult;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudAuthoringResources;
import games.pixscape.studio.service.hud.HudEditRejectedException;
import games.pixscape.studio.service.hud.HudEditorSession;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.function.Predicate;

final class HudPanelTestSupport {
    private HudPanelTestSupport() {
    }

    static HudEditorSession projectedSession(HudScreenEditorDocument hud) throws Exception {
        return projectedSession(hud, new HudEditorSession());
    }

    static HudEditorSession projectedSession(HudScreenEditorDocument hud,
                                             HudEditorSession session) throws Exception {
        projectDocument(session, hud);
        bindPreview(session, hud, null);
        return session;
    }

    static void bindResourceValidatingPreview(HudEditorSession session,
                                              HudScreenEditorDocument hud,
                                              HudResourceCatalog resources) {
        bindPreview(session, hud, resources);
    }

    static void bindSkinValidatingPreview(HudEditorSession session,
                                          HudScreenEditorDocument hud,
                                          Predicate<HudScreenAsset> accepted) {
        hud.editSession().bindActivePreview((candidateAsset, candidate, validation) -> {
            if (!accepted.test(candidateAsset)) {
                throw new HudEditRejectedException(
                        "The selected Skin is incompatible with node label-1 style 'body'.");
            }
            try {
                project(session, "asset", candidateAsset);
                project(session, "document", candidate);
                project(session, "validation", validation);
                project(session, "status", HudEditorSession.Status.READY);
                session.refreshAssetOptions();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    private static void bindPreview(HudEditorSession session,
                                    HudScreenEditorDocument hud,
                                    HudResourceCatalog resources) {
        hud.editSession().bindActivePreview((candidateAsset, candidate, structuralValidation) -> {
            HudValidationResult validation = resources == null ? structuralValidation
                    : new HudDocumentValidator().validate(candidate, resources);
            if (!validation.isValid()) {
                throw new HudEditRejectedException(validation.issues().get(0).message());
            }
            try {
                project(session, "document", candidate);
                project(session, "asset", candidateAsset);
                project(session, "validation", validation);
                project(session, "status", HudEditorSession.Status.READY);
                if (session.selectedNodeId() != null
                        && validation.validatedDocument().node(session.selectedNodeId()) == null) {
                    session.selectNode(null);
                }
                if (resources != null) session.refreshAssetOptions();
            } catch (ReflectiveOperationException failure) {
                throw new AssertionError(failure);
            }
        });
    }

    static void projectDirectory(HudEditorSession session, FileHandle projectDir)
            throws ReflectiveOperationException {
        project(session, "projectDir", projectDir);
    }

    static void projectDocument(HudEditorSession session, HudScreenEditorDocument hud)
            throws ReflectiveOperationException {
        project(session, "screenId", hud.screenId());
        project(session, "status", HudEditorSession.Status.READY);
        project(session, "editorDocument", hud);
        project(session, "asset", hud.asset());
        project(session, "document", hud.document());
        project(session, "validation", new HudDocumentValidator().validate(hud.document()));
        project(session, "selectedNodeId", hud.selectedNodeId());
        project(session, "selectedCellId", hud.selectedCellId());
    }

    static void selectCell(HudEditorSession session, HudScreenEditorDocument hud, String cellId)
            throws ReflectiveOperationException {
        hud.setSelectedCellId(cellId);
        project(session, "selectedNodeId", null);
        project(session, "selectedCellId", cellId);
    }

    static void projectLabelResources(HudEditorSession session) throws Exception {
        projectLabelResources(session, VisUI.getSkin());
    }

    static void projectLabelResources(HudEditorSession session, Skin skin) throws Exception {
        Constructor<HudAuthoringResources> constructor =
                HudAuthoringResources.class.getDeclaredConstructor(Skin.class);
        constructor.setAccessible(true);
        project(session, "currentResources", constructor.newInstance(skin));
    }

    static void projectHistoryNavigation(HudEditorSession session,
                                         HudScreenEditorDocument hud) throws Exception {
        Field field = HudEditorSession.class.getDeclaredField("historyNavigationListener");
        field.setAccessible(true);
        hud.editSession().addHistoryNavigationListener(
                (games.pixscape.studio.service.hud.HudDocumentEditSession.HistoryNavigationListener)
                        field.get(session));
    }

    private static void project(Object target, String name, Object value)
            throws ReflectiveOperationException {
        Field field = target.getClass().getDeclaredField(name);
        field.setAccessible(true);
        field.set(target, value);
    }

    static final class DeferredUi {
        private final ArrayDeque<Runnable> queue = new ArrayDeque<>();
        void post(Runnable runnable) { queue.add(runnable); }
        int size() { return queue.size(); }
        void runAll() { while (!queue.isEmpty()) queue.remove().run(); }
    }
}
