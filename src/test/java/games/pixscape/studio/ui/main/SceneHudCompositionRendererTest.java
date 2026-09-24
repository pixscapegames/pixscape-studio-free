package games.pixscape.studio.ui.main;

import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Rectangle;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.HudDocumentV1;
import games.pixscape.runtime.hud.document.HudNode;
import games.pixscape.runtime.hud.document.HudNodeKind;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudDocumentPersistenceService;
import org.junit.Test;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class SceneHudCompositionRendererTest {
    @Test public void associationResolutionUsesCanonicalSceneAndHudIdentity() {
        ProjectConfig configuration = new ProjectConfig();
        configuration.createSceneMeta("Main");
        configuration.getSceneMeta("Main").defaultHudScreenId = " hud\\status ";
        String sceneTag = configuration.canonicalSceneTagFor(
                configuration.getSceneMeta("Main"));

        SceneHudCompositionRenderer.Association association =
                SceneHudCompositionRenderer.resolveAssociation(
                        new FileHandle("project"), configuration, sceneTag);

        assertEquals("project", association.projectRoot());
        assertEquals(sceneTag, association.sceneTag());
        assertEquals("hud/status", association.screenId());
    }

    @Test public void openHudDocumentIsTheAuthoritativeCompositionSource() {
        EditorDocumentManager documents = new EditorDocumentManager();
        HudScreenAsset asset = new HudScreenAsset();
        asset.documentId = "hud/status.json";
        HudDocumentV1 original = new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP));
        HudScreenEditorDocument open = documents.openHudScreen(
                new HudScreenEditorDocument("hud/status", "Status", asset, original));
        open.editSession().edit("Unsaved HUD", ignored ->
                new HudDocumentV1(new HudNode("unsaved-root", HudNodeKind.GROUP)));
        HudDocumentPersistenceService diskMustNotBeRead = new HudDocumentPersistenceService();

        HudDocumentPersistenceService.Loaded loaded =
                SceneHudCompositionRenderer.loadAuthoritative(
                        new FileHandle("project"), "hud/status", documents, diskMustNotBeRead);

        assertEquals(open.asset().documentId, loaded.asset().documentId);
        assertEquals("unsaved-root", loaded.document().root.id);
        assertTrue(open.isDirty());
    }

    @Test public void compositionIgnoresEditorCamerasAndTracksOnlyCanvasBounds() {
        AtomicReference<SceneHudCompositionRenderer.Association> association =
                new AtomicReference<>(association("scene-a", "hud/a"));
        FakePreparation preparation = new FakePreparation();
        List<FakeView> views = new ArrayList<>();
        SceneHudCompositionRenderer renderer = renderer(association, preparation, views,
                new AtomicBoolean(), new ArrayList<>());
        Rectangle canvas = new Rectangle(40f, 25f, 800f, 450f);

        renderer.render("scene-a", canvas, 1f / 60f);
        assertEquals(List.of("scene-a"), preparation.requests);
        assertTrue(views.isEmpty());

        preparation.generations.put("scene-a", 1L);
        renderer.render("scene-a", canvas, 1f / 60f);
        FakeView view = views.get(0);
        assertEquals(List.of("40,25,800,450"), view.resizes);
        assertEquals(1, view.draws);

        OrthographicCamera worldCamera = new OrthographicCamera();
        worldCamera.position.set(200f, -70f, 0f);
        worldCamera.zoom = 3f;
        OrthographicCamera hudEditorCamera = new OrthographicCamera();
        hudEditorCamera.position.set(-400f, 150f, 0f);
        hudEditorCamera.zoom = 0.5f;
        renderer.render("scene-a", canvas, 1f / 60f);

        assertEquals("Neither camera is an input to the composed view", 1, view.resizes.size());
        assertEquals(2, view.draws);

        canvas.set(60f, 35f, 640f, 360f);
        renderer.render("scene-a", canvas, 1f / 60f);
        assertEquals(List.of("40,25,800,450", "60,35,640,360"), view.resizes);
        renderer.close();
        assertTrue(view.closed);
    }

    @Test public void refreshFailurePreservesSameAssociationButSwitchNeverShowsStaleHud() {
        AtomicReference<SceneHudCompositionRenderer.Association> association =
                new AtomicReference<>(association("scene-a", "hud/a"));
        FakePreparation preparation = new FakePreparation();
        preparation.generations.put("scene-a", 1L);
        List<FakeView> views = new ArrayList<>();
        AtomicBoolean fail = new AtomicBoolean();
        List<RuntimeException> errors = new ArrayList<>();
        SceneHudCompositionRenderer renderer = renderer(
                association, preparation, views, fail, errors);
        Rectangle canvas = new Rectangle(0f, 0f, 320f, 180f);

        renderer.render("scene-a", canvas, 0f);
        FakeView first = views.get(0);
        preparation.generations.put("scene-a", 2L);
        fail.set(true);
        renderer.render("scene-a", canvas, 0f);
        assertEquals(1, errors.size());
        assertFalse(first.closed);
        assertEquals(2, first.draws);

        association.set(association("scene-b", "hud/b"));
        renderer.render("scene-b", canvas, 0f);
        assertTrue(first.closed);
        assertEquals(2, first.draws);

        fail.set(false);
        preparation.generations.put("scene-b", 1L);
        renderer.render("scene-b", canvas, 0f);
        FakeView second = views.get(1);
        assertEquals(1, second.draws);

        association.set(null);
        renderer.render("scene-b", canvas, 0f);
        assertTrue(second.closed);
        assertEquals(1, second.draws);
    }

    @Test public void visibilityHidesOnlyTheSelectedAssociationComposition() {
        AtomicReference<SceneHudCompositionRenderer.Association> association =
                new AtomicReference<>(association("scene-a", "hud/a"));
        FakePreparation preparation = new FakePreparation();
        preparation.generations.put("scene-a", 1L);
        preparation.generations.put("scene-b", 1L);
        List<FakeView> views = new ArrayList<>();
        SceneHudCompositionRenderer renderer = renderer(association, preparation, views,
                new AtomicBoolean(), new ArrayList<>());
        Rectangle canvas = new Rectangle(0f, 0f, 320f, 180f);

        renderer.render("scene-a", canvas, 0f);
        FakeView sceneA = views.get(0);
        assertEquals(1, sceneA.draws);

        renderer.setVisible("scene-a", false);
        assertFalse(renderer.isVisible("scene-a"));
        renderer.render("scene-a", canvas, 0f);
        assertEquals(1, sceneA.draws);

        association.set(association("scene-b", "hud/b"));
        assertTrue(renderer.isVisible("scene-b"));
        renderer.render("scene-b", canvas, 0f);
        assertEquals(1, views.get(1).draws);

        association.set(association("scene-a", "hud/a"));
        renderer.render("scene-a", canvas, 0f);
        FakeView restoredSceneA = views.get(2);
        assertEquals(0, restoredSceneA.draws);
        renderer.setVisible("scene-a", true);
        renderer.render("scene-a", canvas, 0f);
        assertEquals(1, restoredSceneA.draws);
        renderer.close();
    }

    @Test public void authoredRefreshRecomposesWithOwnedResourcesButResourceChangeRecreatesView() {
        var association = new AtomicReference<>(association("scene-a", "hud/a"));
        var preparation = new FakePreparation();
        preparation.generations.put("scene-a", 1L);
        preparation.resourceGenerations.put("scene-a", 1L);
        List<FakeView> views = new ArrayList<>();
        var renderer = new SceneHudCompositionRenderer(ignored -> association.get(), preparation,
                ignored -> {
                    FakeView view = new FakeView();
                    view.supportsRecompose = true;
                    views.add(view);
                    return view;
                }, failure -> { throw failure; });
        Rectangle canvas = new Rectangle(0f, 0f, 320f, 180f);

        renderer.render("scene-a", canvas, 0f);
        FakeView first = views.get(0);
        preparation.generations.put("scene-a", 2L);
        renderer.render("scene-a", canvas, 0f);
        assertEquals(1, views.size());
        assertEquals(1, first.recomposes);
        assertFalse(first.closed);

        preparation.generations.put("scene-a", 3L);
        preparation.resourceGenerations.put("scene-a", 2L);
        renderer.render("scene-a", canvas, 0f);
        assertEquals(2, views.size());
        assertTrue(first.closed);
        assertEquals(0, views.get(1).recomposes);
        renderer.close();
    }

    private static SceneHudCompositionRenderer renderer(
            AtomicReference<SceneHudCompositionRenderer.Association> association,
            FakePreparation preparation,
            List<FakeView> views,
            AtomicBoolean fail,
            List<RuntimeException> errors) {
        return new SceneHudCompositionRenderer(
                ignored -> association.get(),
                preparation,
                next -> {
                    if (fail.get()) throw new IllegalStateException("candidate failed");
                    FakeView view = new FakeView();
                    views.add(view);
                    return view;
                },
                errors::add);
    }

    private static SceneHudCompositionRenderer.Association association(
            String sceneTag, String screenId) {
        return new SceneHudCompositionRenderer.Association("project", sceneTag, screenId);
    }

    private static final class FakePreparation
            implements SceneHudCompositionRenderer.PreparationAccess {
        private final List<String> requests = new ArrayList<>();
        private final Map<String, Long> generations = new HashMap<>();
        private final Map<String, Long> resourceGenerations = new HashMap<>();

        @Override public void ensureSceneRequested(String sceneTag) {
            requests.add(sceneTag);
        }

        @Override public long publishedGeneration(String sceneTag) {
            return generations.getOrDefault(sceneTag, 0L);
        }

        @Override public long resourceGeneration(String sceneTag) {
            return resourceGenerations.getOrDefault(sceneTag, publishedGeneration(sceneTag));
        }
    }

    private static final class FakeView implements SceneHudCompositionRenderer.View {
        private final List<String> resizes = new ArrayList<>();
        private int draws;
        private int recomposes;
        private boolean supportsRecompose;
        private boolean closed;

        @Override public void resize(int x, int y, int width, int height) {
            resizes.add(x + "," + y + "," + width + "," + height);
        }

        @Override public void act(float delta) {}
        @Override public void draw() { draws++; }
        @Override public boolean recompose(SceneHudCompositionRenderer.Association association) {
            if (!supportsRecompose) return false;
            recomposes++;
            return true;
        }
        @Override public void close() { closed = true; }
    }
}
