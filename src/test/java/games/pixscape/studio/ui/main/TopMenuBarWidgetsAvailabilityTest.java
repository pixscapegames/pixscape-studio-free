package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.OpenEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.ui.widget.CheckBoxMenuItem;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class TopMenuBarWidgetsAvailabilityTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void availabilityFollowsActiveDocumentWithoutChangingCheckedStateOrFiringAction() {
        EditorDocumentManager documents = new EditorDocumentManager();
        CheckBoxMenuItem widgets = new CheckBoxMenuItem("Widgets", true);
        AtomicInteger actions = new AtomicInteger();
        widgets.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { actions.incrementAndGet(); }
        });
        documents.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentOpened(OpenEditorDocument document) {
                refresh();
            }
            @Override public void documentActivated(OpenEditorDocument previous,
                                                    OpenEditorDocument current) {
                refresh();
            }
            @Override public void documentClosed(OpenEditorDocument document) {
                refresh();
            }
            private void refresh() { TopMenuBar.refreshWidgetsAvailability(widgets, documents, true); }
        });

        TopMenuBar.refreshWidgetsAvailability(widgets, documents, false);
        assertDisabled(widgets);
        HudScreenEditorDocument hud = documents.openHudScreen("hud/main", "HUD");
        assertEnabled(widgets);
        TopMenuBar.refreshWidgetsAvailability(widgets, documents, false);
        assertDisabled(widgets);
        TopMenuBar.refreshWidgetsAvailability(widgets, documents, true);
        assertEnabled(widgets);

        documents.openScene("scene-a", "Scene",
                new SceneEditorContext("scene-a", new StudioEditingModeService()));
        assertDisabled(widgets);
        documents.activate(hud.key());
        assertEnabled(widgets);
        documents.closeNow(hud.key());
        assertDisabled(widgets);
        documents.clear();
        assertDisabled(widgets);

        assertTrue(widgets.check.isChecked());
        assertEquals(0, actions.get());
        widgets.check.setProgrammaticChangeEvents(false);
        widgets.check.setChecked(false);
        widgets.check.setProgrammaticChangeEvents(true);
        assertEquals(0, actions.get());
        assertFalse(widgets.check.isChecked());
    }

    @Test public void layersAvailabilityFollowsSceneHudSceneWithoutChangingPanelState() {
        EditorDocumentManager documents = new EditorDocumentManager();
        CheckBoxMenuItem layers = new CheckBoxMenuItem("Layers", true);
        AtomicInteger actions = new AtomicInteger();
        layers.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { actions.incrementAndGet(); }
        });
        documents.addListener(new EditorDocumentManager.Listener() {
            @Override public void documentOpened(OpenEditorDocument document) { refresh(); }
            @Override public void documentActivated(OpenEditorDocument previous,
                                                    OpenEditorDocument current) { refresh(); }
            @Override public void documentClosed(OpenEditorDocument document) { refresh(); }
            private void refresh() { TopMenuBar.refreshLayersAvailability(layers, documents); }
        });

        TopMenuBar.refreshLayersAvailability(layers, documents);
        assertEnabled(layers);
        OpenEditorDocument scene = documents.openScene("scene-a", "Scene",
                new SceneEditorContext("scene-a", new StudioEditingModeService()));
        assertEnabled(layers);
        HudScreenEditorDocument hud = documents.openHudScreen("hud/main", "HUD");
        assertDisabled(layers);
        documents.activate(scene.key());
        assertEnabled(layers); // The HUD is still open, but no longer active.
        documents.activate(hud.key());
        assertDisabled(layers);
        documents.closeNow(hud.key());
        assertEnabled(layers);
        documents.clear();
        assertEnabled(layers);

        assertTrue(layers.check.isChecked());
        assertEquals(0, actions.get());
    }

    private static void assertDisabled(CheckBoxMenuItem item) {
        assertTrue(item.isDisabled());
        assertTrue(item.check.isDisabled());
    }

    private static void assertEnabled(CheckBoxMenuItem item) {
        assertFalse(item.isDisabled());
        assertFalse(item.check.isDisabled());
    }
}
