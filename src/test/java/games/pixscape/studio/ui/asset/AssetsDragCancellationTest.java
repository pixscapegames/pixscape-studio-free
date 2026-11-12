package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import games.pixscape.studio.ui.asset.dnd.DragContext;
import games.pixscape.studio.ui.asset.dnd.DragPayload;
import games.pixscape.studio.ui.main.DockingTestFixture;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.*;

import static org.junit.Assert.*;

public class AssetsDragCancellationTest {
    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void cancellingAssetDragClearsPayloadAndGhostWithoutReleasingADrop() throws Exception {
        Graphics previous = Gdx.graphics;
        Gdx.graphics = new MockGraphics() {
            @Override
            public int getWidth() {
                return 1200;
            }

            @Override
            public int getHeight() {
                return 800;
            }
        };
        DragContext drag = DragContext.get();
        try (DockingTestFixture f = DockingTestFixture.create()) {
            f.studioStage.getViewport().update(1200, 800, true);
            f.studioStage.addActor(f.shell);
            AssetsThumbsView view = new AssetsThumbsView(null);
            Actor thumbnail = new Actor();
            AssetNode node = new AssetNode(AssetNode.Kind.HUD_SCREEN, AssetNode.Root.HUD, "hud/test", "Test", null);
            // Attach the production listener; all gesture delivery below goes through Stage.
            var attach = AssetsThumbsView.class.getDeclaredMethod("attachDnD", Actor.class, AssetNode.class);
            attach.setAccessible(true);
            attach.invoke(view, thumbnail, node);
            f.assets.add(thumbnail).grow();
            f.resize(1200, 800);
            Vector2 p = f.studioStage.stageToScreenCoordinates(thumbnail.localToStageCoordinates(new Vector2(10, 10)));
            f.studioStage.touchDown(Math.round(p.x), Math.round(p.y), 0, 0);
            f.studioStage.touchDragged(Math.round(p.x) + 20, Math.round(p.y), 0);
            DragPayload cancelled = drag.peek();
            assertNotNull(cancelled);
            assertNotNull(cancelled.ghostPixmap);
            f.manager.hide(f.assets);
            assertFalse(drag.active());
            assertFalse(drag.releasePending());
            assertNull(cancelled.ghostPixmap);
            assertNull(drag.consumeIfReleasedInside(true));
            f.studioStage.touchUp(Math.round(p.x) + 20, Math.round(p.y), 0, 0);
            assertFalse(drag.releasePending());

            f.manager.show(f.assets);
            f.resize(1200, 800);
            p = f.studioStage.stageToScreenCoordinates(thumbnail.localToStageCoordinates(new Vector2(10, 10)));
            f.studioStage.touchDown(Math.round(p.x), Math.round(p.y), 0, 0);
            f.studioStage.touchDragged(Math.round(p.x) + 20, Math.round(p.y), 0);
            DragPayload released = drag.peek();
            assertNotNull("A new gesture must start after cancellation", released);
            f.studioStage.touchUp(Math.round(p.x) + 20, Math.round(p.y), 0, 0);
            assertTrue(drag.releasePending());
            assertSame(released, drag.consumeIfReleasedInside(true));
            assertNull("Normal release offers exactly one drop", drag.consumeIfReleasedInside(true));
            if (released.ghostPixmap != null) released.ghostPixmap.dispose();
        } finally {
            DragPayload pending = drag.peek();
            if (pending != null && pending.ghostPixmap != null) pending.ghostPixmap.dispose();
            drag.cancel();
            Gdx.graphics = previous;
        }
    }
}
