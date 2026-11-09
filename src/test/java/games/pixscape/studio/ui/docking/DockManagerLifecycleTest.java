package games.pixscape.studio.ui.docking;

import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextField;
import games.pixscape.studio.ui.main.DockingTestFixture;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import games.pixscape.studio.ui.widget.DockSplitPane;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import static org.junit.Assert.*;

public class DockManagerLifecycleTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test
    public void repeatedHideAndRedockCollapsesEmptyBranchesAndRestoresProportions() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.create()) {
            f.rightSplit.setSplitAmount(.43f);
            for (int cycle = 0; cycle < 10; cycle++) {
                f.manager.hide(f.properties);
                f.resize(1200, 800);
                assertEquals(0, f.rightSplit.getChildren().size);
                assertNull(f.rightSplit.getParent());
                assertNull(f.rightTop.getParent());
                assertEquals(f.rightBottom.getParent().getHeight(), f.rightBottom.getHeight(), .01f);
                f.manager.dockToDefault(f.properties);
                f.manager.hide(f.layers);
                f.resize(1200, 800);
                assertEquals(0, f.rightSplit.getChildren().size);
                assertNull(f.rightBottom.getParent());
                assertEquals(f.rightTop.getParent().getHeight(), f.rightTop.getHeight(), .01f);
                f.manager.dockToDefault(f.layers);
                f.resize(1200f + cycle * 23f, 800f + cycle * 17f);

                assertPersistentRightSplit(f);
                assertEquals(.43f, f.rightSplit.getSplit(), 0);
                assertSame(f.left, f.items.getParent());
                assertSame(f.rightTop, f.properties.getParent());
                assertSame(f.rightBottom, f.layers.getParent());
                assertSame(f.bottom, f.assets.getParent());
                assertTrue(f.editorHost.getWidth() > 0f && f.editorHost.getHeight() > 0f);
                assertTrue(f.menu.getWidth() > 0f && f.menu.getHeight() > 0f);
            }
        }
    }

    @Test public void contentAndEditedStateSurviveSlotChangesAndOtherPanelHides() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.create()) {
            f.studioStage.addActor(f.shell);
            VisTable content = new VisTable();
            VisTextField field = new VisTextField("edited content");
            content.add(field);
            f.properties.add(content).grow();
            for (int cycle = 0; cycle < 10; cycle++) {
                f.manager.hide(f.items);
                f.manager.dock(f.properties, DockSlot.LEFT);
                assertContent(f, f.left, content, field);
                f.manager.hide(f.assets);
                assertContent(f, f.left, content, field);
                f.manager.hide(f.properties);
                assertSame(f.properties, content.getParent());
                assertSame(content, field.getParent());
                f.manager.dock(f.properties, DockSlot.RIGHT_TOP);
                f.manager.show(f.items);
                f.manager.show(f.assets);
                assertContent(f, f.rightTop, content, field);
                assertEquals(0, DockingTestFixture.countIdentity(f.left, field));
                assertPersistentRightSplit(f);
            }
        }
    }

    private static void assertContent(DockingTestFixture f, VisTable slot,
                                      VisTable content, VisTextField field) {
        f.resize(1200, 800);
        assertSame(slot, f.properties.getParent());
        assertSame(f.properties, content.getParent());
        assertSame(content, field.getParent());
        assertSame(f.studioStage, field.getStage());
        assertSame(content, f.properties.getCell(content).getActor());
        assertEquals(1, DockingTestFixture.countIdentity(slot, field));
        assertEquals(1, DockingTestFixture.countIdentity(f.studioStage.getRoot(), field));
        assertEquals("edited content", field.getText());
        assertUsable(field);
    }

    @Test public void everyCombinationHasOnlyBinarySplitsAndPositiveUsableContent() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.create()) {
            f.studioStage.addActor(f.shell);
            DockablePanel[] panels = {f.items, f.properties, f.layers, f.assets};
            VisTable[] slots = {f.left, f.rightTop, f.rightBottom, f.bottom};
            VisTextField[] fields = new VisTextField[4];
            for (int i = 0; i < 4; i++) {
                fields[i] = new VisTextField("content " + i);
                panels[i].add(fields[i]).grow();
                f.splits[i].setSplitAmount(.35f + i * .1f);
            }
            for (int mask = 0; mask < 16; mask++) {
                for (int i = 0; i < 4; i++) {
                    if ((mask & 1 << i) != 0) f.manager.dockToDefault(panels[i]);
                    else f.manager.hide(panels[i]);
                }
                f.resize(1200, 800);
                for (int i = 0; i < 4; i++) {
                    if ((mask & 1 << i) != 0) {
                        assertSame(slots[i], panels[i].getParent());
                        assertUsable(fields[i]);
                    } else assertNull(slots[i].getParent());
                    DockSplitPane split = f.splits[i];
                    assertEquals(.35f + i * .1f, split.getSplit(), 0);
                    assertEquals(split.getParent() == null ? 0 : 2, split.getChildren().size);
                    for (Actor child : split.getChildren()) assertSame(split, child.getParent());
                }
                if (mask == 0) assertSame(f.manager.getRoot(), f.editorHost.getParent());
                assertTrue(f.editorHost.getWidth() > 0 && f.editorHost.getHeight() > 0);
            }
        }
    }

    @Test public void rebuildingCancelsSeparatorAndRemovedContentBeforeDetaching() throws Exception {
        try (DockingTestFixture f = DockingTestFixture.create()) {
            f.studioStage.addActor(f.shell);
            f.resize(1200, 800);
            DockSplitPane split = f.splits[2];
            Vector2 p = split.localToStageCoordinates(new Vector2(split.getWidth() / 2,
                    split.getSecondWidgetBounds().height + split.getStyle().handle.getMinHeight() / 2));
            InputEvent down = new InputEvent();
            down.setType(InputEvent.Type.touchDown); down.setPointer(0); down.setButton(0);
            down.setStageX(p.x); down.setStageY(p.y);
            split.fire(down);
            assertTrue(down.isHandled());
            f.manager.hide(f.properties);
            assertFalse(f.studioStage.touchDragged(10, 10, 0));
            f.manager.show(f.properties);
            f.resize(1200, 800);
            split.fire(down);
            assertTrue(f.studioStage.touchDragged(10, 10, 0));
            f.manager.cancelResize();
            assertFalse(f.studioStage.touchDragged(20, 20, 0));

            Actor child = new Actor();
            f.properties.add(child);
            boolean[] cancelledWhileAttached = {false};
            child.addListener(new InputListener() {
                @Override public boolean touchDown(InputEvent e, float x, float y, int p, int b) { return true; }
                @Override public void touchUp(InputEvent e, float x, float y, int p, int b) {
                    cancelledWhileAttached[0] = e.isTouchFocusCancel() && child.getStage() == f.studioStage;
                }
            });
            child.fire(down);
            f.manager.hide(f.properties);
            assertTrue(cancelledWhileAttached[0]);
        }
    }

    private static void assertUsable(Actor field) {
        assertTrue(field.isVisible());
        for (Actor actor = field; actor != null; actor = actor.getParent()) {
            assertTrue(actor.getClass().getSimpleName(), actor.isVisible());
            if (actor != field.getStage().getRoot()) assertTrue(actor.getWidth() > 0 && actor.getHeight() > 0);
        }
        Vector2 p = field.localToStageCoordinates(new Vector2(field.getWidth() / 2, field.getHeight() / 2));
        Actor target = field.getStage().hit(p.x, p.y, true);
        assertTrue(target != null && target.isDescendantOf(field));
    }

    private static void assertPersistentRightSplit(DockingTestFixture f) {
        assertSame(f.rightSplit, f.rightTop.getParent());
        assertSame(f.rightSplit, f.rightBottom.getParent());
        assertEquals(2, f.rightSplit.getChildren().size);
        assertSame(f.rightTop, f.rightSplit.getChildren().get(0));
        assertSame(f.rightBottom, f.rightSplit.getChildren().get(1));
        assertEquals(Touchable.enabled, f.rightSplit.getTouchable());
    }
}
