package games.pixscape.studio.service.hud;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.headless.HeadlessApplication;
import com.badlogic.gdx.backends.headless.HeadlessApplicationConfiguration;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Container;
import com.badlogic.gdx.scenes.scene2d.ui.Stack;
import com.badlogic.gdx.scenes.scene2d.ui.Table;
import com.badlogic.gdx.utils.viewport.FitViewport;
import org.junit.AfterClass;
import org.junit.BeforeClass;
import org.junit.Test;

import java.lang.reflect.Proxy;

import static org.junit.Assert.*;

public class HudAuthoringSessionLayoutBoundsTest {
    private static GL20 previousGl;
    private static GL20 previousGl20;

    @BeforeClass public static void bootGdx() {
        if (Gdx.app == null) {
            new HeadlessApplication(new ApplicationAdapter() {},
                    new HeadlessApplicationConfiguration());
        }
        if (Gdx.graphics == null) Gdx.graphics = new MockGraphics();
        previousGl = Gdx.gl;
        previousGl20 = Gdx.gl20;
        if (Gdx.gl == null) {
            Gdx.gl = (GL20) Proxy.newProxyInstance(GL20.class.getClassLoader(),
                    new Class<?>[]{GL20.class}, (proxy, method, args) -> {
                        Class<?> type = method.getReturnType();
                        if (type == boolean.class) return false;
                        if (type == int.class) return 0;
                        if (type == float.class) return 0f;
                        return null;
                    });
            Gdx.gl20 = Gdx.gl;
        }
    }

    @AfterClass public static void restoreGl() {
        Gdx.gl = previousGl;
        Gdx.gl20 = previousGl20;
    }

    @Test public void nativeDebugAllCoversNestedTablesAndSurvivesRootReplacement() {
        Batch borrowed = inertBatch();
        HudAuthoringSession session = new HudAuthoringSession(borrowed);
        Stage stage = session.stage();
        Stage unrelatedUiStage = new Stage(new FitViewport(1f, 1f), inertBatch());
        Stage unrelatedWorldStage = new Stage(new FitViewport(1f, 1f), inertBatch());
        var viewport = session.viewport();
        Group first = tree("first");
        Table firstOuter = (Table) first.findActor("first-outer");
        Table firstNested = (Table) first.findActor("first-nested");
        session.replace(null, first);

        assertFalse(session.isShowingLayoutBounds());
        assertFalse(stage.isDebugAll());
        assertEquals(Table.Debug.none, firstOuter.getTableDebug());
        assertEquals(Table.Debug.none, firstNested.getTableDebug());

        session.setShowLayoutBounds(true);
        assertTrue(stage.isDebugAll());
        assertFalse(session.selectionOverlayHost().getDebug());
        assertFalse(unrelatedUiStage.isDebugAll());
        assertFalse(unrelatedWorldStage.isDebugAll());
        assertEquals(Table.Debug.all, firstOuter.getTableDebug());
        assertEquals(Table.Debug.all, firstNested.getTableDebug());
        assertTrue(first.findActor("first-stack").getDebug());
        assertTrue(first.findActor("first-container").getDebug());

        Group second = tree("second");
        Table secondNested = (Table) second.findActor("second-nested");
        session.replace(first, second);
        assertEquals(Table.Debug.all, secondNested.getTableDebug());
        assertSame(stage, session.stage());
        assertSame(viewport, session.viewport());
        assertSame(borrowed, stage.getBatch());

        session.setShowLayoutBounds(false);
        assertFalse(stage.isDebugAll());
        assertFalse(session.selectionOverlayHost().getDebug());
        assertEquals(Table.Debug.none,
                ((Table) second.findActor("second-outer")).getTableDebug());
        assertEquals(Table.Debug.none, secondNested.getTableDebug());
        assertFalse(second.findActor("second-stack").getDebug());
        session.dispose();
        unrelatedUiStage.dispose();
        unrelatedWorldStage.dispose();
    }

    private static Group tree(String prefix) {
        Group root = new Group();
        root.setName(prefix + "-root");
        Table outer = new Table();
        outer.setName(prefix + "-outer");
        Table nested = new Table();
        nested.setName(prefix + "-nested");
        Stack stack = new Stack();
        stack.setName(prefix + "-stack");
        Container<Group> container = new Container<>(new Group());
        container.setName(prefix + "-container");
        nested.add(stack);
        nested.add(container);
        outer.add(nested);
        root.addActor(outer);
        return root;
    }

    private static Batch inertBatch() {
        return (Batch) Proxy.newProxyInstance(Batch.class.getClassLoader(),
                new Class<?>[]{Batch.class}, (proxy, method, args) -> {
                    Class<?> type = method.getReturnType();
                    if (type == boolean.class) return false;
                    if (type == int.class) return 0;
                    if (type == float.class) return 0f;
                    return null;
                });
    }
}
