package games.pixscape.studio.ui.main;

import com.badlogic.gdx.scenes.scene2d.ui.ImageButton;
import com.badlogic.gdx.utils.IntArray;
import com.kotcrab.vis.ui.widget.VisCheckBox;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.GameObjectMemberComponent;
import games.pixscape.runtime.component.VisibilityComponent;
import games.pixscape.studio.history.commands.DeleteEntitiesCommand;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.service.AlignService;
import games.pixscape.studio.service.AlignServiceTest;
import games.pixscape.studio.service.StudioEditingMode;
import games.pixscape.studio.system.AfterEcsStep;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import games.pixscape.studio.ui.widget.UiBinders;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Field;

import static org.junit.Assert.*;

public class ToolBarPlacementTest {
    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void publishedRefreshDoesNotCollectUnrelatedCurrentRelations() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture();
             EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true)) {
            ToolBar toolbar = toolbar(f, scope);
            int root = f.root(100, 0);
            f.attach(f.sprite(0, 0, 10, 10), root);
            f.select(root, f.sprite(300, 0, 10, 10));
            assertAdmission(toolbar, f.align, true);
            int unrelated = f.sprite(500, 0, 10, 10);
            f.world.getMapper(GameObjectMemberComponent.class).create(unrelated).parentStableId = Integer.MAX_VALUE;
            assertFalse(f.align.isPlacementAvailable());
            // Replay only the registered UI callback against unchanged published selection geometry.
            // A global current-relations collection would encounter the invalid unrelated parent.
            Field refresh = ToolBar.class.getDeclaredField("placementRefresh");
            refresh.setAccessible(true);
            ((AfterEcsStep) refresh.get(toolbar)).afterEcsStep();
            assertTrue(f.align.isPlacementAvailableAfterEcsStep());
            for (String name : new String[]{"alignLeftButton", "alignRightButton", "alignTopButton", "alignBottomButton",
                    "centerHorizontalButton", "centerVerticalButton", "packHorizontalButton", "packVerticalButton"}) {
                assertFalse(button(toolbar, name).isDisabled());
            }
            f.world.getMapper(GameObjectMemberComponent.class).remove(unrelated);
            f.process();
            assertAdmission(toolbar, f.align, true);
            toolbar.suspendSceneContext();
        }
    }

    @Test
    public void visibilityBinderThenPlacementButtonUsesCurrentContentBeforeEcs() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture();
             EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true)) {
            ToolBar toolbar = toolbar(f, scope);
            int root = f.root(100, 0), distant = f.sprite(100, 0, 10, 10);
            f.attach(f.sprite(0, 0, 10, 10), root);
            f.attach(distant, root);
            int target = f.sprite(300, 0, 10, 10);
            var visibility = f.world.getMapper(VisibilityComponent.class);
            VisCheckBox checkbox = new VisCheckBox("Visible");
            UiBinders.CheckBoxBinder binder = new UiBinders.CheckBoxBinder(f.world, checkbox,
                    visibility::has, e -> visibility.get(e).visible,
                    (e, value) -> visibility.get(e).visible = value);
            binder.setEntityId(distant);
            f.select(root, target);
            checkbox.setChecked(false);
            button(toolbar, "alignRightButton").setChecked(true);
            assertEquals(100, f.t.get(target).x, 0.03f);
            assertEquals(1, f.history.getCursor());
            f.process();
            assertEquals(f.bounds(root)[2], f.bounds(target)[2], 0.03f);
            f.history.undo();
            assertEquals(300, f.t.get(target).x, 0.03f);
            f.history.redo();
            assertEquals(100, f.t.get(target).x, 0.03f);
            toolbar.suspendSceneContext();
        }
    }

    @Test
    public void visibilityWithoutNotificationRefreshesAllPlacementButtons() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture();
             EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true)) {
            ToolBar toolbar = toolbar(f, scope);
            int root = f.root(100, 0), child = f.sprite(0, 0, 10, 10);
            f.attach(child, root);
            VisibilityComponent visibility = f.world.getMapper(VisibilityComponent.class).create(child);
            f.select(root, f.sprite(300, 0, 10, 10));
            assertAdmission(toolbar, f.align, true);
            // Same mutation as EntityProperties' visibility binder; no history/event notification.
            visibility.visible = false;
            f.process();
            assertAdmission(toolbar, f.align, false);
            visibility.visible = true;
            f.process();
            assertAdmission(toolbar, f.align, true);
            toolbar.suspendSceneContext();
        }
    }

    @Test
    public void renderBindingLossAndRecoveryRefreshAllPlacementButtons() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture(true);
             EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true)) {
            ToolBar toolbar = toolbar(f, scope);
            int root = f.root(100, 0), child = f.sprite(0, 0, 10, 10);
            f.attach(child, root);
            f.select(root, f.sprite(300, 0, 10, 10));
            assertAdmission(toolbar, f.align, true);
            RenderMaterialComponent material = f.world.getMapper(RenderMaterialComponent.class).get(child);
            // Real RenderSpriteSyncSystem publishes slot removal/recreation from binding validity.
            material.textureHandle = 0;
            f.dirty.material(child);
            f.process();
            assertAdmission(toolbar, f.align, false);
            material.textureHandle = 1;
            f.dirty.material(child);
            f.process();
            assertAdmission(toolbar, f.align, true);
            toolbar.suspendSceneContext();
        }
    }

    private static ToolBar toolbar(AlignServiceTest.Fixture f, EventFlow.SubscriptionScope scope) throws Exception {
        StudioApplicationAdapter app = AlignServiceTest.allocate(StudioApplicationAdapter.class);
        AlignServiceTest.setField(app, "canvas", f.canvas);
        ToolBar[] result = new ToolBar[1];
        scope.run(() -> result[0] = new ToolBar(app));
        return result[0];
    }

    @Test
    public void actualButtonsTrackSelectionContextAndHistoryAndRunMixedPlacement() throws Exception {
        try (AlignServiceTest.Fixture f = new AlignServiceTest.Fixture();
             EventFlow.SubscriptionScope scope = EventFlow.i().newSubscriptionScope(() -> true)) {
            StudioApplicationAdapter app = AlignServiceTest.allocate(StudioApplicationAdapter.class);
            AlignServiceTest.setField(app, "canvas", f.canvas);
            ToolBar[] holder = new ToolBar[1];
            scope.run(() -> holder[0] = new ToolBar(app));
            ToolBar toolbar = holder[0];
            f.process();
            assertAdmission(toolbar, f.align, false);
            int image = f.sprite(10, 0, 20, 10);
            int animation = f.target(1, 70, 20, 8, 10);
            int root = f.target(2, 110, 40, 12, 10);
            f.select(image);
            assertAdmission(toolbar, f.align, false);
            f.select(image, animation, root);
            assertAdmission(toolbar, f.align, true);
            button(toolbar, "alignLeftButton").setChecked(true);
            f.process();
            assertEquals(f.bounds(image)[0], f.bounds(animation)[0], 0.02f);
            assertEquals(f.bounds(image)[0], f.bounds(root)[0], 0.02f);
            assertEquals(1, f.history.getCursor());
            f.history.undo();
            f.process();
            assertEquals(70, f.t.get(animation).x, 0.02f);
            f.history.redo();
            f.process();
            assertEquals(10, f.t.get(animation).x, 0.02f);

            // History revision catches commands with no UI event, including undo/redo.
            f.changeWidth(animation, 0);
            f.process();
            assertAdmission(toolbar, f.align, false);
            f.history.undo();
            f.process();
            assertAdmission(toolbar, f.align, true);
            f.history.redo();
            f.process();
            assertAdmission(toolbar, f.align, false);
            f.history.undo();
            f.process();

            f.modes.setMode(StudioEditingMode.SPATIAL, 0);
            f.process();
            assertAdmission(toolbar, f.align, false);
            f.align.packHorizontal();
            assertEquals(10, f.t.get(animation).x, 0.02f);
            f.modes.setMode(StudioEditingMode.NORMAL, 0);
            f.process();
            assertAdmission(toolbar, f.align, true);
            f.select(image);
            assertAdmission(toolbar, f.align, false);
            f.select(image, animation);
            assertAdmission(toolbar, f.align, true);
            f.history.execute(new DeleteEntitiesCommand(f.world, f.history.historyIds(),
                    new IntArray(new int[]{animation}), f.selection::selectAdd));
            f.process();
            f.selection.reconcileActiveSelection();
            f.process();
            assertAdmission(toolbar, f.align, false);
            f.history.undo();
            f.process();
            assertAdmission(toolbar, f.align, true);
            f.history.redo();
            f.process();
            f.selection.reconcileActiveSelection();
            f.process();
            assertAdmission(toolbar, f.align, false);

            f.select(image, root);
            assertAdmission(toolbar, f.align, true);
            f.context.layerService().meta(0).locked = true;
            EventFlow.i().publish(new EventFlow.LayerLockChanged(f.context.layerService().getLayerEntity(0), true, 0));
            f.process();
            assertAdmission(toolbar, f.align, false);
            f.align.alignLeft();
            f.context.layerService().meta(0).locked = false;
            EventFlow.i().publish(new EventFlow.LayerLockChanged(f.context.layerService().getLayerEntity(0), false, 0));
            f.process();
            assertAdmission(toolbar, f.align, true);
            toolbar.suspendSceneContext();
            f.context.detached();
            assertAdmission(toolbar, f.align, false);
            f.context.attached();
            toolbar.bindSceneContext(f.context);
            f.process();
            assertAdmission(toolbar, f.align, true);
            f.modes.activateHudDocument(0);
            EventFlow.i().flush();
            assertAdmission(toolbar, f.align, false);
            toolbar.suspendSceneContext();
        }
    }

    private static void assertAdmission(ToolBar toolbar, AlignService service, boolean available) throws Exception {
        assertEquals(available, service.isPlacementAvailable());
        for (String name : new String[]{"alignLeftButton", "alignRightButton", "alignTopButton", "alignBottomButton",
                "centerHorizontalButton", "centerVerticalButton", "packHorizontalButton", "packVerticalButton"}) {
            assertEquals(name, !available, button(toolbar, name).isDisabled());
        }
    }

    private static ImageButton button(ToolBar toolbar, String name) throws Exception {
        Field field = ToolBar.class.getDeclaredField(name);
        field.setAccessible(true);
        return (ImageButton) field.get(toolbar);
    }
}
