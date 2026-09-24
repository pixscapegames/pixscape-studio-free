package games.pixscape.studio.ui.main;

import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.EditorDocumentManager;
import games.pixscape.studio.document.EditorDocumentType;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.scene.SceneEditorContext;
import games.pixscape.studio.service.StudioEditingModeService;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

public class BottomMenuBarSceneControlsContractTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void selectorProjectsTheCompleteCatalogWithoutAnyOpenDocuments() {
        ProjectConfig config = project("Tutorial", "Demo");
        config.setCurrentSceneByName("Tutorial");
        VisSelectBox<String> selector = new VisSelectBox<>("default");
        Array<String> items = new Array<>();

        BottomMenuBar.populateSceneSelector(selector, items, config);

        assertEquals(2, selector.getItems().size);
        assertTrue(selector.getItems().contains("Tutorial", false));
        assertTrue(selector.getItems().contains("Demo", false));
        assertEquals("Tutorial", selector.getSelected());
        assertNotNull(selector.getSelected());
        assertTrue(selector.getSelection().getRequired());
    }

    @Test
    public void selectingAnOpenInactiveSceneIsAStrictNoOp() {
        ProjectConfig config = project("Tutorial", "Demo");
        EditorDocumentManager documents = new EditorDocumentManager();
        SceneEditorDocument tutorial = open(documents, config, "Tutorial");
        SceneEditorDocument demo = open(documents, config, "Demo");
        config.setCurrentSceneByName("Demo");
        VisSelectBox<String> selector = new VisSelectBox<>("default");
        Array<String> items = new Array<>();
        BottomMenuBar.populateSceneSelector(selector, items, config);
        selector.setSelected("Tutorial");
        AtomicInteger opens = new AtomicInteger();
        AtomicInteger busyChanges = new AtomicInteger();

        boolean opened = BottomMenuBar.navigateSceneFromSelector(
                config, documents, selector, items,
                ignored -> opens.incrementAndGet(),
                ignored -> busyChanges.incrementAndGet(),
                "Tutorial");

        assertFalse(opened);
        assertEquals(0, opens.get());
        assertEquals(0, busyChanges.get());
        assertSame(demo, documents.activeDocument());
        assertSame(tutorial, documents.find(tutorial.key()));
        assertEquals(2, documents.documents().size());
        assertEquals("Demo", selector.getSelected());
    }

    @Test
    public void selectingAClosedSceneOpensExactlyOneDocumentAndActivatesIt() {
        ProjectConfig config = project("Tutorial", "Demo");
        EditorDocumentManager documents = new EditorDocumentManager();
        open(documents, config, "Demo");
        config.setCurrentSceneByName("Demo");
        VisSelectBox<String> selector = new VisSelectBox<>("default");
        Array<String> items = new Array<>();
        BottomMenuBar.populateSceneSelector(selector, items, config);
        selector.setSelected("Tutorial");
        AtomicInteger opens = new AtomicInteger();

        boolean opened = BottomMenuBar.navigateSceneFromSelector(
                config,
                documents,
                selector,
                items,
                sceneName -> {
                    opens.incrementAndGet();
                    config.setCurrentSceneByName(sceneName);
                    open(documents, config, sceneName);
                },
                selector::setDisabled,
                "Tutorial");
        boolean duplicate = BottomMenuBar.navigateSceneFromSelector(
                config, documents, selector, items,
                ignored -> opens.incrementAndGet(), selector::setDisabled, "Tutorial");

        assertTrue(opened);
        assertFalse(duplicate);
        assertEquals(1, opens.get());
        assertEquals(2, documents.documents().size());
        assertEquals(EditorDocumentType.SCENE, documents.activeDocument().type());
        assertEquals(config.canonicalSceneTag("Tutorial"),
                documents.activeDocument().key().domainId());
        assertEquals("Tutorial", selector.getSelected());
        assertFalse(selector.isDisabled());
    }

    @Test
    public void closingTheOnlyScenePreservesNonNullSelectorAndAllowsSameChoiceToReopen() {
        ProjectConfig config = project("Tutorial");
        EditorDocumentManager documents = new EditorDocumentManager();
        SceneEditorDocument first = open(documents, config, "Tutorial");
        VisSelectBox<String> selector = new VisSelectBox<>("default");
        Array<String> items = new Array<>();
        BottomMenuBar.populateSceneSelector(selector, items, config);

        assertTrue(documents.closeNow(first.key()));
        BottomMenuBar.populateSceneSelector(selector, items, config);

        assertEquals("Tutorial", selector.getSelected());
        assertFalse(selector.isDisabled());
        assertTrue(BottomMenuBar.navigateSceneFromSelector(
                config,
                documents,
                selector,
                items,
                sceneName -> {
                    config.setCurrentSceneByName(sceneName);
                    open(documents, config, sceneName);
                },
                selector::setDisabled,
                selector.getSelected()));
        assertEquals(1, documents.documents().size());
        assertEquals("Tutorial", selector.getSelected());
        assertFalse(selector.isDisabled());
    }

    @Test
    public void selectorStaysEnabledAndPopulatedWithHudOrNoDocuments() {
        ProjectConfig config = project("Tutorial", "Demo");
        config.setCurrentSceneByName("Tutorial");
        EditorDocumentManager documents = new EditorDocumentManager();
        HudScreenEditorDocument hud = documents.openHudScreen("hud/main", "HUD");
        VisSelectBox<String> selector = new VisSelectBox<>("default");
        Array<String> items = new Array<>();

        BottomMenuBar.populateSceneSelector(selector, items, config);
        assertSame(hud, documents.activeDocument());
        assertFalse(selector.isDisabled());
        assertEquals(2, selector.getItems().size);
        assertEquals("Tutorial", selector.getSelected());

        assertTrue(documents.closeNow(hud.key()));
        BottomMenuBar.populateSceneSelector(selector, items, config);
        assertEquals(null, documents.activeDocument());
        assertFalse(selector.isDisabled());
        assertEquals(2, selector.getItems().size);
        assertEquals("Tutorial", selector.getSelected());
    }

    @Test
    public void popupListHasAnExplicitClickPathForAnUnchangedSelection() throws Exception {
        String source = Files.readString(Path.of(
                "src/main/java/games/pixscape/studio/ui/main/BottomMenuBar.java"),
                StandardCharsets.UTF_8);

        assertTrue(source.contains("sceneSelectBox.getList().addListener(new ClickListener()"));
        assertFalse(source.contains("setRequired(false)"));
        assertFalse(source.contains("getSelection().clear()"));
        assertFalse(source.contains("lastValue"));
        assertFalse(source.contains("SceneSwitchWorkflow"));
    }

    private static ProjectConfig project(String... names) {
        ProjectConfig config = new ProjectConfig();
        for (String name : names) config.createSceneMeta(name);
        return config;
    }

    private static SceneEditorDocument open(EditorDocumentManager documents,
                                            ProjectConfig config,
                                            String sceneName) {
        String canonical = config.canonicalSceneTag(sceneName);
        return documents.openScene(
                canonical,
                sceneName,
                new SceneEditorContext(canonical, new StudioEditingModeService()));
    }
}
