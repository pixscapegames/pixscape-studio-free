package games.pixscape.studio.document;

import com.kotcrab.vis.ui.widget.VisTable;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.ui.document.EditorDocumentHost;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertSame;

public class HudIsolationDocumentActivationTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void sceneAndHudSwitchingChangesActivationWithoutClosingAnyTab() {
        EditorDocumentManager manager = new EditorDocumentManager();
        EditorDocumentHost host = new EditorDocumentHost(manager, new VisTable());
        SceneEditorDocument sceneA = manager.openScene("scene-a", "A", context("scene-a"));
        SceneEditorDocument sceneB = manager.openScene("scene-b", "B", context("scene-b"));
        HudScreenEditorDocument hudA = manager.openHudScreen("hud/a", "HUD A");
        HudScreenEditorDocument hudB = manager.openHudScreen("hud/b", "HUD B");
        AtomicInteger closes = new AtomicInteger();
        manager.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentClosed(OpenEditorDocument document) { closes.incrementAndGet(); }
        });

        manager.activate(sceneA.key());
        manager.activate(hudA.key());
        manager.activate(sceneA.key());
        manager.activate(hudA.key());
        manager.activate(sceneB.key());
        manager.activate(hudA.key());
        manager.activate(hudB.key());

        assertSame(hudB, manager.activeDocument());
        assertEquals(hudB.key(), host.selectedKey());
        assertEquals(4, host.documentTabCount());
        assertEquals(0, closes.get());
        assertSame(sceneA, manager.find(sceneA.key()));
        assertSame(sceneB, manager.find(sceneB.key()));
    }

    private static SceneEditorContext context(String id) {
        return new SceneEditorContext(id, new StudioEditingModeService());
    }
}
