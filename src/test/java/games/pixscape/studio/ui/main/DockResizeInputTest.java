package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Graphics;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.InputMultiplexer;
import com.badlogic.gdx.backends.headless.mock.graphics.MockGraphics;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Group;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import games.pixscape.runtime.hud.HudScreenAsset;
import games.pixscape.runtime.hud.document.*;
import games.pixscape.studio.asset.AssetMetaDatabase;
import games.pixscape.studio.document.HudScreenEditorDocument;
import games.pixscape.studio.service.hud.HudEditorSession;
import games.pixscape.studio.ui.hud.HudTestInputRouter;
import games.pixscape.studio.ui.widget.DockSplitPane;
import games.pixscape.studio.ui.widget.VisUiTestBootstrap;
import org.junit.*;
import org.junit.rules.TemporaryFolder;

import static org.junit.Assert.*;

public class DockResizeInputTest {
    @Rule
    public TemporaryFolder temporary = new TemporaryFolder();

    @BeforeClass
    public static void loadSkin() {
        VisUiTestBootstrap.loadSkin();
    }

    @AfterClass
    public static void unloadSkin() {
        VisUiTestBootstrap.unloadSkin();
    }

    @Test
    public void escapeCancelsNativeResizeBeforeHudTestAndOtherwiseKeepsItsNormalRoute() throws Exception {
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
        try (DockingTestFixture f = DockingTestFixture.create()) {
            HudEditorSession hud = new HudEditorSession(AssetMetaDatabase::new, () -> f.floatingStage.getBatch());
            try {
                HudScreenAsset asset = new HudScreenAsset();
                asset.documentId = "hud/main.json";
                hud.open(new FileHandle(temporary.newFolder()), new HudScreenEditorDocument("hud/main", "Main", asset,
                        new HudDocumentV1(new HudNode("root", HudNodeKind.GROUP))));
                assertTrue(hud.enterTestMode());
                f.studioStage.getViewport().update(1200, 800, true);
                f.studioStage.addActor(f.shell);
                f.resize(1200, 800);
                int[] hudEscapes = {0}, studioEscapes = {0};
                hud.testStage().addListener(escapeCounter(hudEscapes));
                f.studioStage.addListener(escapeCounter(studioEscapes));
                InputMultiplexer inputs = StudioApplicationAdapter.createInputMultiplexer(f.manager,
                        new HudTestInputRouter(hud, f.studioStage), f.studioStage, f.floatingStage);
                assertNull(f.studioStage.getKeyboardFocus());
                assertTrue(inputs.keyDown(Input.Keys.ESCAPE));
                assertEquals(1, hudEscapes[0]);
                for (DockSplitPane split : f.splits) {
                    split.validate();
                    boolean horizontal = split == f.splits[0] || split == f.splits[1];
                    Vector2 p = handle(f, split, horizontal);
                    Vector2 stagePoint = f.studioStage.screenToStageCoordinates(new Vector2(Math.round(p.x), Math.round(p.y)));
                    assertSame("Handle at " + p + " for " + split.getWidth() + "x" + split.getHeight(),
                            split, f.studioStage.hit(stagePoint.x, stagePoint.y, true));
                    assertTrue("Native handle press at " + p, inputs.touchDown(Math.round(p.x), Math.round(p.y), 0, 0));
                    assertNull(f.studioStage.getKeyboardFocus());
                    float before = split.getSplit();
                    inputs.touchDragged(Math.round(p.x) + 15, Math.round(p.y) + 15, 0);
                    assertNotEquals(before, split.getSplit(), 0);
                    float cancelled = split.getSplit();
                    assertTrue(inputs.keyDown(Input.Keys.ESCAPE));
                    assertEquals("Escape must not reach HUD during resize", 1, hudEscapes[0]);
                    assertEquals(0, studioEscapes[0]);
                    inputs.touchDragged(Math.round(p.x) + 60, Math.round(p.y) + 60, 0);
                    inputs.touchUp(Math.round(p.x) + 60, Math.round(p.y) + 60, 0, 0);
                    assertEquals(cancelled, split.getSplit(), 0);
                    p = handle(f, split, horizontal);
                    inputs.touchDown(Math.round(p.x), Math.round(p.y), 0, 0);
                    inputs.touchDragged(Math.round(p.x) + 10, Math.round(p.y) + 10, 0);
                    inputs.touchUp(Math.round(p.x) + 10, Math.round(p.y) + 10, 0, 0);
                    assertNotEquals("New resize and normal release still work", cancelled, split.getSplit(), 0);
                }
                inputs.keyDown(Input.Keys.ESCAPE);
                assertEquals("Normal releases leave Escape available to HUD", 2, hudEscapes[0]);
                hud.exitTestMode();
                inputs.keyDown(Input.Keys.ESCAPE);
                assertEquals(1, studioEscapes[0]);
            } finally {
                hud.dispose();
            }
        } finally {
            Gdx.graphics = previous;
        }
    }

    private static InputListener escapeCounter(int[] count) {
        return new InputListener() {
            @Override
            public boolean keyDown(InputEvent event, int key) {
                if (key != Input.Keys.ESCAPE) return false;
                count[0]++;
                return true;
            }
        };
    }

    private static Vector2 handle(DockingTestFixture f, DockSplitPane split, boolean horizontal) {
        f.resize(1200, 800);
        validateLayout(f.shell);
        Vector2 p = horizontal
                ? new Vector2(split.getFirstWidgetBounds().width + split.getStyle().handle.getMinWidth() / 2, split.getHeight() / 2)
                : new Vector2(split.getWidth() / 2, split.getSecondWidgetBounds().height + split.getStyle().handle.getMinHeight() / 2);
        return f.studioStage.stageToScreenCoordinates(split.localToStageCoordinates(p));
    }

    private static void validateLayout(Actor actor) {
        if (actor instanceof Layout layout) layout.validate();
        if (actor instanceof Group group) for (Actor child : group.getChildren()) validateLayout(child);
    }
}
