package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.kotcrab.vis.ui.widget.VisSelectBox;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.document.SceneEditorDocument;
import games.pixscape.studio.ui.document.EditorDocumentHost;
import org.junit.Test;

import static org.junit.Assert.*;

public class SceneSelectorGlTest {
    @Test
    public void popupClicksActivateExistingSceneTabsAndKeepTheirContexts() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(800, (studio, root) -> {
            var project = ResourceAssetInspectorGlTest.project(studio, root.child("project"));
            ResourceAssetInspectorGlTest.open(studio, project);
            var documents = studio.getEditorDocumentManager();
            var first = (SceneEditorDocument) documents.activeDocument();
            studio.getSceneService().createNewScene("Second");
            var second = (SceneEditorDocument) documents.activeDocument();
            first.context().markExplicitSaveRequired();
            var bottom = ResourceAssetInspectorGlTest.field(studio, "bottomMenuBar");
            @SuppressWarnings("unchecked")
            var selector = (VisSelectBox<String>) ResourceAssetInspectorGlTest.field(bottom, "sceneSelectBox");
            EditorDocumentHost host = findHost(studio.getUiStage().getRoot());
            assertNotNull(host);

            for (var scene : new SceneEditorDocument[]{first, second, first, first}) {
                studio.render();
                selector.showScrollPane();
                var list = selector.getList();
                int index = list.getItems().indexOf(scene.title(), false);
                assertTrue(index >= 0);
                float topPadding = list.getStyle().background == null ? 0 : list.getStyle().background.getTopHeight();
                var point = list.localToStageCoordinates(new Vector2(list.getWidth() / 2,
                        list.getHeight() - topPadding - (index + .5f) * list.getItemHeight()));
                var stage = studio.getUiStage();
                stage.stageToScreenCoordinates(point);
                stage.touchDown(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
                stage.touchUp(Math.round(point.x), Math.round(point.y), 0, Input.Buttons.LEFT);
                studio.render();

                assertSame(scene, documents.activeDocument());
                assertSame(scene.context(), studio.getCanvas().getAttachedSceneContext());
                assertEquals(scene.key(), host.selectedKey());
                assertEquals(scene.title(), selector.getSelected());
                assertEquals(scene.title(), ProjectConfig.getInstance().getCurrentSceneName());
                assertEquals(2, host.documentTabCount());
                assertTrue(first.isDirty());
            }
        });
    }

    private static EditorDocumentHost findHost(Actor actor) {
        if (actor instanceof EditorDocumentHost host) {
            return host;
        }
        if (actor instanceof Group group) {
            for (Actor child : group.getChildren()) {
                EditorDocumentHost host = findHost(child);
                if (host != null) {
                    return host;
                }
            }
        }
        return null;
    }
}
