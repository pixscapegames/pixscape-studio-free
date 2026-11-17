package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.studio.event.EventFlow;
import org.junit.Test;

import static games.pixscape.studio.ui.main.ResourceAssetInspectorGlTest.*;
import static org.junit.Assert.*;

/** Opt-in Stage/GL regression for the Edit menu's clipboard command routing. */
public class EditMenuClipboardGlTest {
    @Test
    public void copyAndPasteMenuItemsUseTheActiveClipboardAndHistory() throws Exception {
        withStudio(700, (studio, root) -> {
            open(studio, project(studio, root.child("clipboard")));
            var context = studio.getSceneEditorContext();
            int source = studio.getCanvas().getEditorOps().createGameObject(10, 20);
            context.selectionService().selectOnly(source);
            EventFlow.i().flush();
            int cursor = context.historyManager().getCursor();

            activate(studio, "copy");
            assertTrue(studio.getCanvas().getClipboardService().hasContent());
            assertEquals(cursor, context.historyManager().getCursor());
            activate(studio, "paste");
            int copy = context.selectionService().getFirstSelectedEntityId();
            assertNotEquals(source, copy);
            var pose = context.world().getMapper(TransformComponent.class).get(copy);
            assertEquals(26, pose.x, 0);
            assertEquals(4, pose.y, 0);
            assertEquals(cursor + 1, context.historyManager().getCursor());
            assertTrue(studio.undoActiveDocument());
            studio.render();
            assertFalse(context.world().getEntityManager().isActive(copy));
            assertTrue(context.world().getEntityManager().isActive(source));
        });
    }

    @Test
    public void cutMenuItemDeletesThroughHistoryAndUndoRestoresTheSource() throws Exception {
        withStudio(700, (studio, root) -> {
            open(studio, project(studio, root.child("clipboard")));
            var context = studio.getSceneEditorContext();
            int source = studio.getCanvas().getEditorOps().createGameObject(10, 20);
            int stableId = context.identityRegistry().getStableId(source);
            context.selectionService().selectOnly(source);
            EventFlow.i().flush();
            activate(studio, "cut");
            assertTrue(studio.getCanvas().getClipboardService().hasContent());
            assertFalse(context.world().getEntityManager().isActive(source));
            assertTrue(studio.undoActiveDocument());
            assertTrue(context.identityRegistry().findByStableId(stableId) >= 0);
        });
    }

    private static void activate(StudioApplicationAdapter studio, String item) throws Exception {
        Actor actor = (Actor) field(field(studio, "topMenuBar"), item);
        actor.fire(new ChangeListener.ChangeEvent());
        EventFlow.i().flush();
        studio.render();
    }
}
