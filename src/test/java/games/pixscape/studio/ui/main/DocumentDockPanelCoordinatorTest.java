package games.pixscape.studio.ui.main;

import com.badlogic.gdx.files.FileHandle;
import games.pixscape.studio.document.GameObjectEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;
import java.util.ArrayDeque;
import java.util.Queue;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class DocumentDockPanelCoordinatorTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void inactiveWidgetsOpenedFromViewPreserveFloatingPresentationAndRestoreLayers()
            throws Exception {
        DeferredQueue queue = new DeferredQueue();
        try (DockingTestFixture f = DockingTestFixture.createCoordinated(queue::post)) {
            f.placeOnFloatingStage(f.widgets);
            f.documents.activate(f.scene);
            assertEquals("FLOATING", presentation(f.coordinator, "hudPresentation"));
            assertSame(f.rightBottom, f.layers.getParent());

            for (int attempt = 0; attempt < 4; attempt++) {
                f.manager.show(f.widgets); // Same public path as View → Widgets.
                assertEquals(1, queue.size());
            }
            queue.drain();

            assertFalse(f.widgets.isVisible());
            assertNull(f.widgets.getParent());
            assertEquals("FLOATING", presentation(f.coordinator, "hudPresentation"));
            assertSame(f.rightBottom, f.layers.getParent());
            assertEquals(1, DockingTestFixture.countIdentity(f.manager.getRoot(), f.layers));
            assertEquals(0, DockingTestFixture.countIdentity(f.manager.getRoot(), f.widgets));
            assertEquals(0, DockingTestFixture.countIdentity(f.floatingStage.getRoot(), f.widgets));
        }
    }

    @Test
    public void inactiveLayersOpenedFromViewAreHiddenAndWidgetsRemainActive() throws Exception {
        DeferredQueue queue = new DeferredQueue();
        try (DockingTestFixture f = DockingTestFixture.createCoordinated(queue::post)) {
            assertSame(f.rightBottom, f.widgets.getParent());

            f.manager.show(f.layers); // Same public path as View → Layers.
            queue.drain();

            assertFalse(f.layers.isVisible());
            assertNull(f.layers.getParent());
            assertSame(f.rightBottom, f.widgets.getParent());
            f.documents.activate(f.scene);
            assertSame(f.rightBottom, f.layers.getParent());
            f.documents.activate(f.hud);
            assertSame(f.rightBottom, f.widgets.getParent());
        }
    }

    @Test
    public void hiddenPresentationSurvivesRepeatedSceneHudRoundTrips() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.createCoordinated(Runnable::run)) {
            f.manager.hide(f.widgets);
            for (int cycle = 0; cycle < 10; cycle++) {
                f.documents.activate(f.scene);
                assertSame(f.rightBottom, f.layers.getParent());
                f.documents.activate(f.hud);
                assertNull(f.widgets.getParent());
                assertFalse(f.widgets.isVisible());
            }
        }
    }

    @Test
    public void gameObjectDocumentUsesTheScenePanels() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.createCoordinated(Runnable::run)) {
            GameObjectEditorDocument document = new GameObjectEditorDocument(
                    "enemy", "enemy", new FileHandle("build/test-gameobjects/enemy.gameobject"),
                    new SceneEditorContext("asset:enemy", new StudioEditingModeService()), 1);
            f.documents.openGameObject(document);
            assertSame(f.rightBottom, f.layers.getParent());
            assertNull(f.widgets.getParent());
        }
    }

    private static String presentation(DocumentDockPanelCoordinator coordinator, String fieldName)
            throws Exception {
        Field field = DocumentDockPanelCoordinator.class.getDeclaredField(fieldName);
        field.setAccessible(true);
        return field.get(coordinator).toString();
    }

    private static final class DeferredQueue {
        private final Queue<Runnable> pending = new ArrayDeque<>();
        void post(Runnable runnable) { pending.add(runnable); }
        int size() { return pending.size(); }
        void drain() {
            while (!pending.isEmpty()) pending.remove().run();
            assertTrue(pending.isEmpty());
        }
    }
}
