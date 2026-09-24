package games.pixscape.studio.ui.document;

import com.kotcrab.vis.ui.widget.VisTable;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.tabbedpane.Tab;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPane.TabbedPaneStyle;
import com.kotcrab.vis.ui.widget.tabbedpane.TabbedPaneListener;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;
import games.pixscape.studio.document.EditorDocumentKey;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.ui.config.CommonLayout;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;

import java.util.ArrayDeque;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.junit.Assert.*;

public class EditorDocumentHostTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void managerProjectsTabsAndBidirectionalActivationHasNoFeedbackLoop() {
        EditorDocumentManager manager = new EditorDocumentManager();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable());
        SceneEditorDocument scene = manager.registerMaterializedScene("scene0", "World", context());
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "main");

        assertEquals(2, host.documentTabCount());
        assertEquals(hud.key(), host.selectedKey());
        assertTrue(host.requestActivation(scene.key()));
        assertSame(scene, manager.activeDocument());

        AtomicInteger activations = new AtomicInteger();
        manager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentActivated(games.pixscape.studio.document.OpenEditorDocument previous,
                                                     games.pixscape.studio.document.OpenEditorDocument current) {
                activations.incrementAndGet();
            }
        });
        manager.activate(hud.key());
        assertEquals(hud.key(), host.selectedKey());
        assertEquals(1, activations.get());
    }

    @Test
    public void sceneAndHudTabsExposeCloseAffordanceAndUseManagerFallback() {
        EditorDocumentManager manager = new EditorDocumentManager();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable());
        SceneEditorDocument scene = manager.registerMaterializedScene("scene0", "World", context());
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "main");

        assertTrue(host.tabbedPane().getTabs().get(0).isCloseableByUser());
        assertTrue(host.tabbedPane().getTabs().get(1).isCloseableByUser());
        host.tabbedPane().remove(host.tabbedPane().getTabs().get(1));

        assertNull(manager.find(hud.key()));
        assertSame(scene, manager.activeDocument());
        assertEquals(1, host.documentTabCount());
    }

    @Test
    public void userCloseActiveHudWithOneSceneRemovesOnceAndActivatesFallbackOnce() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        SceneEditorDocument scene = manager.openScene("scene-a", "A", context("scene-a"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
        deferred.runAll();

        assertNull(manager.find(hud.key()));
        assertSame(scene, manager.activeDocument());
        assertEquals(scene.key(), host.selectedKey());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.modelActivations.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void userCloseActiveHudWithTwoScenesDoesNotReenterTabbedPane() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        manager.openScene("scene-a", "A", context("scene-a"));
        SceneEditorDocument sceneB = manager.openScene("scene-b", "B", context("scene-b"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
        deferred.runAll();

        assertNull(manager.find(hud.key()));
        assertSame(sceneB, manager.activeDocument());
        assertEquals(sceneB.key(), host.selectedKey());
        assertEquals(2, host.documentTabCount());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.modelActivations.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void userCloseHudBetweenScenesReconcilesVisUiFallbackToManagerHistory() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        manager.openScene("scene-a", "A", context("scene-a"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        SceneEditorDocument sceneB = manager.openScene("scene-b", "B", context("scene-b"));
        manager.activate(hud.key());
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
        // VisUI chooses the previous physical tab (Scene A); the model chooses recent Scene B.
        assertNotEquals(sceneB.key(), host.selectedKey());
        assertSame(sceneB, manager.activeDocument());
        deferred.runAll();

        assertEquals(sceneB.key(), host.selectedKey());
        assertSame(sceneB, manager.activeDocument());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.modelActivations.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void programmaticHudCloseRemovesPhysicalTabOnce() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        SceneEditorDocument scene = manager.openScene("scene-a", "A", context("scene-a"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(manager.requestClose(hud.key()));
        deferred.runAll();

        assertSame(scene, manager.activeDocument());
        assertEquals(scene.key(), host.selectedKey());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.modelActivations.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void userCloseInactiveHudDoesNotDisturbActiveScene() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        manager.openScene("scene-a", "A", context("scene-a"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        SceneEditorDocument sceneB = manager.openScene("scene-b", "B", context("scene-b"));
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
        deferred.runAll();

        assertSame(sceneB, manager.activeDocument());
        assertEquals(sceneB.key(), host.selectedKey());
        assertEquals(0, counts.modelActivations.get());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void repeatedUserHudOpenCloseKeepsSceneProjectionStable() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        SceneEditorDocument sceneA = manager.openScene("scene-a", "A", context("scene-a"));
        SceneEditorDocument sceneB = manager.openScene("scene-b", "B", context("scene-b"));
        AtomicInteger physicalRemovals = new AtomicInteger();
        host.tabbedPane().addListener(new TabbedPaneListener() {
            @Override public void switchedTab(Tab tab) { }
            @Override public void removedTab(Tab tab) { physicalRemovals.incrementAndGet(); }
            @Override public void removedAllTabs() { }
        });

        for (int i = 0; i < 10; i++) {
            HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
            assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
            deferred.runAll();
            assertSame(sceneB, manager.activeDocument());
            assertEquals(sceneB.key(), host.selectedKey());
            assertEquals(2, host.documentTabCount());
        }

        assertSame(sceneA, manager.find(sceneA.key()));
        assertSame(sceneB, manager.find(sceneB.key()));
        assertEquals(10, physicalRemovals.get());
    }

    @Test
    public void userHudThenSceneCloseLeavesRemainingSceneSwitchable() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        SceneEditorDocument tutorial = manager.openScene("tutorial", "Tutorial", context("tutorial"));
        SceneEditorDocument demo = manager.openScene("demo", "demo", context("demo"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");

        assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
        deferred.runAll();
        assertSame(demo, manager.activeDocument());

        assertTrue(host.tabbedPane().remove(tabFor(host, demo.key()), false));
        deferred.runAll();
        assertTrue(demo.context().isDisposed());
        assertSame(tutorial, manager.activeDocument());
        assertEquals(tutorial.key(), host.selectedKey());

        assertTrue(host.requestActivation(tutorial.key()));
        assertSame(tutorial, manager.activeDocument());
        assertFalse(tutorial.context().isDisposed());
    }

    @Test
    public void cleanSceneUserCloseDisposesContextOnce() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        SceneEditorDocument sceneA = manager.openScene("scene-a", "A", context("scene-a"));
        SceneEditorDocument sceneB = manager.openScene("scene-b", "B", context("scene-b"));
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, sceneB.key()), false));
        deferred.runAll();

        assertTrue(sceneB.context().isDisposed());
        assertEquals(1, sceneB.context().disposeCount());
        assertSame(sceneA, manager.activeDocument());
        assertEquals(sceneA.key(), host.selectedKey());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void dirtySceneCancelRestoresTabWithoutClosingOrDisposingContext() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        manager.openHudScreen("hud/main", "HUD");
        SceneEditorDocument scene = manager.openScene("scene-a", "A", context("scene-a"));
        scene.context().markExplicitSaveRequired();
        AtomicReference<SceneEditorDocument> pending = new AtomicReference<>();
        manager.setDirtySceneCloseHandler(pending::set);

        assertTrue(host.tabbedPane().remove(tabFor(host, scene.key()), false));
        deferred.runAll();

        assertSame(scene, pending.get());
        assertSame(scene, manager.find(scene.key()));
        assertSame(scene, manager.activeDocument());
        assertEquals(scene.key(), host.selectedKey());
        assertEquals(2, host.documentTabCount());
        assertFalse(scene.context().isDisposed());
    }

    @Test
    public void dirtySceneSaveClosesOnceAfterSavingOnce() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        SceneEditorDocument scene = manager.openScene("scene-a", "A", context("scene-a"));
        scene.context().markExplicitSaveRequired();
        AtomicInteger saves = new AtomicInteger();
        manager.setDirtySceneCloseHandler(document -> {
            saves.incrementAndGet();
            manager.closeNow(document.key());
        });
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, scene.key()), false));
        deferred.runAll();

        assertEquals(1, saves.get());
        assertNull(manager.find(scene.key()));
        assertSame(hud, manager.activeDocument());
        assertEquals(hud.key(), host.selectedKey());
        assertTrue(scene.context().isDisposed());
        assertEquals(1, scene.context().disposeCount());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void dirtySceneDiscardClosesOnceWithoutSave() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        SceneEditorDocument scene = manager.openScene("scene-a", "A", context("scene-a"));
        scene.context().markExplicitSaveRequired();
        AtomicInteger discards = new AtomicInteger();
        manager.setDirtySceneCloseHandler(document -> {
            discards.incrementAndGet();
            manager.closeNow(document.key());
        });
        LifecycleCounts counts = trackLifecycle(manager, host);

        assertTrue(host.tabbedPane().remove(tabFor(host, scene.key()), false));
        deferred.runAll();

        assertEquals(1, discards.get());
        assertNull(manager.find(scene.key()));
        assertSame(hud, manager.activeDocument());
        assertEquals(hud.key(), host.selectedKey());
        assertEquals(1, scene.context().disposeCount());
        assertEquals(1, counts.modelCloses.get());
        assertEquals(1, counts.physicalRemovals.get());
    }

    @Test
    public void hudDirtyMarkerTracksEditUndoRedoAndSaveBaseline() {
        EditorDocumentManager manager = new EditorDocumentManager();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable());
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        Tab tab = tabFor(host, hud.key());
        assertFalse(tab.getTabTitle().endsWith("*"));
        hud.editSession().edit("Resize root", candidate -> { candidate.root.actor.width = 1f; return candidate; });
        assertTrue(tab.getTabTitle().endsWith("*"));
        assertTrue(hud.editSession().undo());
        assertFalse(tab.getTabTitle().endsWith("*"));
        assertTrue(hud.editSession().redo());
        assertTrue(tab.getTabTitle().endsWith("*"));
        hud.editSession().markSaved();
        assertFalse(tab.getTabTitle().endsWith("*"));
    }

    @Test
    public void dirtyHudCancelRestoresTabWithoutClosing() {
        EditorDocumentManager manager = new EditorDocumentManager();
        DeferredUi deferred = new DeferredUi();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable(), deferred);
        manager.openScene("scene-a", "A", context("scene-a"));
        HudScreenEditorDocument hud = manager.openHudScreen("hud/main", "HUD");
        hud.editSession().edit("Resize root", candidate -> { candidate.root.actor.width = 1f; return candidate; });
        AtomicReference<HudScreenEditorDocument> pending = new AtomicReference<>();
        manager.setDirtyHudCloseHandler(pending::set);

        assertTrue(host.tabbedPane().remove(tabFor(host, hud.key()), false));
        deferred.runAll();

        assertSame(hud, pending.get());
        assertSame(hud, manager.find(hud.key()));
        assertSame(hud, manager.activeDocument());
        assertEquals(hud.key(), host.selectedKey());
        assertEquals(2, host.documentTabCount());
        assertTrue(hud.isDirty());
    }

    @Test
    public void documentTabBandIsOpaqueWhileCenterContentRemainsTransparent() {
        EditorDocumentHost host = new EditorDocumentHost(new EditorDocumentManager(), new VisTable());
        TabbedPaneStyle style = VisUI.getSkin().get("document-tabs", TabbedPaneStyle.class);

        assertEquals(CommonLayout.DOCUMENT_TAB_GAP,
                host.tabbedPane().getTabsPane().getHorizontalFlowGroup().getSpacing(), 0f);
        assertSame(VisUI.getSkin().getDrawable("panel-header"), style.background);
        assertSame(host.tabbedPane().getTable(), host.getChildren().get(0));
        assertSame(style.background, host.tabbedPane().getTable().getBackground());
        assertNull(((Table) host.getChildren().get(1)).getBackground());
    }

    private static SceneEditorContext context() {
        return context("scene0");
    }

    private static SceneEditorContext context(String identity) {
        return new SceneEditorContext(identity, new StudioEditingModeService());
    }

    private static Tab tabFor(EditorDocumentHost host, EditorDocumentKey key) {
        for (Tab tab : host.tabbedPane().getTabs()) {
            if (tab instanceof EditorDocumentTab documentTab
                    && documentTab.document().key().equals(key)) return tab;
        }
        fail("No tab for " + key);
        return null;
    }

    private static LifecycleCounts trackLifecycle(EditorDocumentManager manager,
                                                  EditorDocumentHost host) {
        LifecycleCounts counts = new LifecycleCounts();
        manager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentClosed(OpenEditorDocument document) {
                counts.modelCloses.incrementAndGet();
            }

            @Override public void documentActivated(OpenEditorDocument previous,
                                                     OpenEditorDocument current) {
                counts.modelActivations.incrementAndGet();
            }
        });
        host.tabbedPane().addListener(new TabbedPaneListener() {
            @Override public void switchedTab(Tab tab) { }
            @Override public void removedTab(Tab tab) { counts.physicalRemovals.incrementAndGet(); }
            @Override public void removedAllTabs() { }
        });
        return counts;
    }

    private static final class LifecycleCounts {
        private final AtomicInteger modelCloses = new AtomicInteger();
        private final AtomicInteger modelActivations = new AtomicInteger();
        private final AtomicInteger physicalRemovals = new AtomicInteger();
    }

    private static final class DeferredUi implements Consumer<Runnable> {
        private final ArrayDeque<Runnable> tasks = new ArrayDeque<>();

        @Override public void accept(Runnable runnable) {
            tasks.addLast(runnable);
        }

        private void runAll() {
            while (!tasks.isEmpty()) tasks.removeFirst().run();
        }
    }
}
