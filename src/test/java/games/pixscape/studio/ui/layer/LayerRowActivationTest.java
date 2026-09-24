package games.pixscape.studio.ui.layer;

import com.badlogic.gdx.Input;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.Touchable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertTrue;

public class LayerRowActivationTest {
    @BeforeClass public static void loadSkin() { VisUiTestBootstrap.loadSkin(); }
    @AfterClass public static void unloadSkin() { VisUiTestBootstrap.unloadSkin(); }

    @Test public void singleClickSelectsAndSecondClickOpensThroughScene2dInput() {
        Stage stage = new Stage(new ScreenViewport(), inertBatch());
        AtomicInteger selections = new AtomicInteger();
        AtomicInteger opens = new AtomicInteger();
        AtomicBoolean selected = new AtomicBoolean();
        try {
            stage.getViewport().update(200, 200, true);
            LayerRow row = new LayerRow();
            row.setBounds(0f, 50f, 200f, 50f);
            row.setListener(new LayerRow.Listener() {
                @Override public void onVisibleChanged(LayerRow row, boolean visible) {}
                @Override public void onLockedChanged(LayerRow row, boolean locked) {}

                @Override public void onRowClicked(LayerRow row) {
                    selected.set(true);
                    selections.incrementAndGet();
                }

                @Override public void onRowDoubleClicked(LayerRow row) {
                    assertTrue(selected.get());
                    opens.incrementAndGet();
                }
            });
            stage.addActor(row);
            row.validate();

            row.setSelected(true); // Selection restoration is not activation.
            assertEquals(0, opens.get());

            Actor label = row.getChildren().get(2);
            Vector2 screenPoint = stage.stageToScreenCoordinates(
                    label.localToStageCoordinates(new Vector2(
                            label.getWidth() * 0.5f, label.getHeight() * 0.5f)));
            int screenX = Math.round(screenPoint.x);
            int screenY = Math.round(screenPoint.y);

            click(stage, screenX, screenY);
            assertEquals(1, selections.get());
            assertEquals(0, opens.get());

            click(stage, screenX, screenY);
            assertEquals(2, selections.get());
            assertEquals(1, opens.get());
        } finally {
            stage.dispose();
        }
    }

    @Test public void hudControlsCanShowVisibilityWithoutShowingLock() {
        LayerRow row = new LayerRow();
        row.setLayerControlsVisible(true, false);

        Actor visibility = row.getChildren().get(0);
        Actor lock = row.getChildren().get(1);
        assertTrue(visibility.isVisible());
        assertEquals(Touchable.enabled, visibility.getTouchable());
        assertFalse(lock.isVisible());
        assertEquals(Touchable.disabled, lock.getTouchable());
    }

    private static void click(Stage stage, int screenX, int screenY) {
        assertTrue(stage.touchDown(screenX, screenY, 0, Input.Buttons.LEFT));
        stage.touchUp(screenX, screenY, 0, Input.Buttons.LEFT);
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(
                Batch.class.getClassLoader(),
                new Class[]{Batch.class},
                (proxy, method, args) -> defaultValue(method.getReturnType()));
    }

    private static Object defaultValue(Class<?> type) {
        if (type == Boolean.TYPE) return false;
        if (type == Integer.TYPE) return 0;
        if (type == Float.TYPE) return 0f;
        if (type == Long.TYPE) return 0L;
        if (type == Double.TYPE) return 0d;
        return null;
    }
}
