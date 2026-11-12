package games.pixscape.studio.ui.docking;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.InputEvent;
import com.badlogic.gdx.scenes.scene2d.InputListener;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.Layout;
import com.badlogic.gdx.utils.ObjectMap;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.ui.GenericWindow;
import games.pixscape.studio.ui.GenericWindowListener;
import games.pixscape.studio.ui.StudioStage;
import games.pixscape.studio.ui.main.DockingTestFixture;
import games.pixscape.studio.ui.widget.DockSplitPane;

import static org.junit.Assert.*;

/**
 * Opt-in real GLFW lifecycle assertions against the production docking composition.
 */
public final class DockWindowsNativeCheck extends ApplicationAdapter {
    private final boolean autoClose;
    private final ObjectMap<DockablePanel, Lwjgl3Window> windows = new ObjectMap<>();
    private final com.badlogic.gdx.utils.Array<Stage> oldStages = new com.badlogic.gdx.utils.Array<>();
    private DockablePanel openingPanel;
    private DockingTestFixture f;
    private Stage stage;
    private Probe a, b;
    private VisLabel status;
    private int step;
    private int waitingFrames;
    private boolean running = true;
    private final float[] ratios = new float[4];
    private String armed;
    private boolean gestureAction;

    private DockWindowsNativeCheck(boolean autoClose) {
        this.autoClose = autoClose;
    }

    public static void main(String[] args) {
        DockWindowsNativeCheck check = new DockWindowsNativeCheck(args.length > 0 && args[0].equals("--auto-close"));
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Dock windows integration check");
        config.setWindowedMode(1100, 760);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2);
        new Lwjgl3Application(check, config) {
            @Override
            public Lwjgl3Window newWindow(ApplicationListener listener, Lwjgl3WindowConfiguration cfg) {
                cfg.setWindowPosition(check.openingPanel == check.f.properties ? 60 : 470, 130);
                Lwjgl3Window window = super.newWindow(listener, cfg);
                check.windows.put(check.openingPanel, window);
                return window;
            }
        };
    }

    @Override
    public void create() {
        VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
        stage = new StudioStage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        try {
            f = DockingTestFixture.create();
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        f.manager.setRulersVisible(false); // The fixture has no world camera or ruler renderer.
        ((Lwjgl3Graphics) Gdx.graphics).getWindow().setWindowListener(new Lwjgl3WindowAdapter() {
            @Override
            public void focusLost() {
                f.manager.cancelResize();
                System.out.println("OS focus lost: separator gestures cancelled");
            }
        });
        a = new Probe(f.properties, "A - preserved", true);
        b = new Probe(f.layers, "B - preserved", false);
        VisTable root = new VisTable();
        root.setFillParent(true);
        status = new VisLabel("Running native lifecycle assertions...");
        root.add(status).growX().row();
        VisTable buttons = new VisTable();
        button(buttons, "Float both", () -> {
            undock(f.properties);
            undock(f.layers);
        });
        button(buttons, "Dock A LEFT", () -> {
            f.manager.hide(f.items);
            f.manager.dock(f.properties, DockSlot.LEFT);
        });
        button(buttons, "Hide assets", () -> f.manager.hide(f.assets));
        button(buttons, "Hide B", () -> f.manager.hide(f.layers));
        button(buttons, "Reopen B", () -> undock(f.layers));
        button(buttons, "Check content", this::assertAllContent);
        root.add(buttons).growX().row();
        VisTable gestures = new VisTable();
        for (String action : new String[]{"Resize", "Cancel", "OS focus"}) {
            button(gestures, "Arm " + action, () -> {
                if (action.equals("OS focus") && windows.size == 0) {
                    status.setText("Float both panels before arming the OS focus check.");
                    return;
                }
                armed = action;
                status.setText("Drag a separator while holding the button: " + action);
            });
        }
        root.add(gestures).growX().row();
        root.add(f.manager.getRoot()).grow();
        stage.addActor(root);
        // Test-only observer: the actual mouse press/drag and resize listener remain native.
        stage.addListener(new InputListener() {
            @Override
            public boolean touchDown(InputEvent e, float x, float y, int pointer, int button) {
                gestureAction = armed != null && e.getTarget() instanceof DockSplitPane;
                return gestureAction;
            }

            @Override
            public void touchDragged(InputEvent e, float x, float y, int pointer) {
                if (!gestureAction) return;
                gestureAction = false;
                String action = armed;
                armed = null;
                switch (action) {
                    case "Resize" -> Gdx.graphics.setWindowedMode(Gdx.graphics.getWidth() == 1100 ? 1000 : 1100, 760);
                    case "Cancel" -> f.manager.cancelResize();
                    case "OS focus" -> {
                        if (windows.size > 0) windows.values().next().focusWindow();
                    }
                }
                status.setText(action + " during held-button drag; release and try another drag.");
                System.out.println("Executed during native held-button drag: " + action);
            }

            @Override
            public boolean keyDown(InputEvent e, int key) {
                if (key == com.badlogic.gdx.Input.Keys.ESCAPE) f.manager.cancelResize();
                return false;
            }
        });
    }

    private static void button(VisTable table, String text, Runnable action) {
        VisTextButton button = new VisTextButton(text);
        button.addListener(new ChangeListener() {
            @Override
            public void changed(ChangeEvent e, Actor actor) {
                action.run();
            }
        });
        table.add(button).pad(3);
    }

    private void undock(DockablePanel panel) {
        openingPanel = panel;
        try {
            f.manager.undock(panel);
        } finally {
            openingPanel = null;
        }
    }

    private GenericWindow window(DockablePanel panel) {
        return (GenericWindow) windows.get(panel).getListener();
    }

    private boolean ready(DockablePanel panel) {
        return windows.containsKey(panel) && window(panel).containsPanel() && panel.getStage() != null;
    }

    private void advance() {
        if (++waitingFrames > 600) throw new AssertionError("Native window lifecycle stalled at step " + step);
        // Each assertion runs in a later main-window frame, after newWindow's queued creation.
        if (step < 40) {
            switch (step % 4) {
                case 0 -> {
                    f.manager.dock(f.layers, DockSlot.RIGHT_BOTTOM);
                    validateTree(stage.getRoot());
                    for (int i = 0; i < f.splits.length; i++) {
                        resizeByDrag(f.splits[i], i < 2);
                        ratios[i] = f.splits[i].getSplit();
                    }
                    assertDocked(a, f.rightTop);
                    assertDocked(b, f.rightBottom);
                    undock(f.properties);
                    undock(f.layers);
                }
                case 1 -> {
                    if (!ready(f.properties) || !ready(f.layers)) return;
                    assertFloating(a);
                    assertFloating(b);
                    f.manager.hide(f.assets);
                    assertFloating(a);
                    assertFloating(b);
                    f.manager.hide(f.layers);
                    assertFloating(a);
                    b.assertInsidePanel();
                }
                case 2 -> {
                    f.manager.hide(f.items);
                    f.manager.dock(f.properties, DockSlot.LEFT);
                    assertDocked(a, f.left);
                    // An old window's delayed detach must not remove transferred content.
                    window(f.properties).detachPanel();
                    assertDocked(a, f.left);
                    undock(f.layers);
                }
                case 3 -> {
                    if (!ready(f.layers)) return;
                    assertFloating(b);
                    f.manager.dock(f.properties, DockSlot.RIGHT_TOP);
                    f.manager.show(f.items);
                    f.manager.show(f.assets);
                    assertDocked(a, f.rightTop);
                    assertFloating(b);
                }
            }
        } else switch (step) {
            case 40 -> {
                // Invoke the production callback on a real window. GLFW's should-close flag
                // bypasses this callback; the OS close button is checked separately by hand.
                assertDocked(a, f.rightTop);
                assertFalse(windows.get(f.layers).getWindowListener().closeRequested());
            }
            case 41 -> {
                if (f.layers.isVisible()) return;
                a.assertInsidePanel();
                b.assertInsidePanel();
                assertDocked(a, f.rightTop);
                undock(f.layers);
            }
            case 42 -> {
                if (!ready(f.layers)) return;
                assertFloating(b);
                f.manager.dock(f.layers, DockSlot.BOTTOM);
                assertDocked(b, f.bottom);
                // Dispose old native stages only after ownership has moved to the main Stage.
                for (Lwjgl3Window w : windows.values()) {
                    ((GenericWindowListener) w.getWindowListener()).forceClose();
                    assertTrue(w.getWindowListener().closeRequested());
                    w.closeWindow();
                }
            }
            case 43 -> {
                assertDocked(a, f.rightTop);
                assertDocked(b, f.bottom);
                for (Lwjgl3Window w : windows.values()) assertFalse(((GenericWindow) w.getListener()).containsPanel());
                assertAllContent();
                System.out.println("PASS: 10 native resize/dock/undock cycles, simultaneous windows, other-panel hides, close callback, cross-slot redock and stale-window disposal; ratios, content identity/text/toggle, positive visible bounds and hit targets retained.");
                status.setText("PASS - 10 native cycles + close callback/dispose. Use buttons to inspect manually.");
                windows.clear();
                running = false;
                if (autoClose) Gdx.app.exit();
            }
            default -> throw new AssertionError("Unexpected step " + step);
        }
        step++;
        for (int i = 0; i < f.splits.length; i++) assertEquals(ratios[i], f.splits[i].getSplit(), 0);
        waitingFrames = 0;
    }

    private void resizeByDrag(DockSplitPane split, boolean horizontal) {
        validateTree(stage.getRoot());
        float thickness = horizontal ? split.getStyle().handle.getMinWidth() : split.getStyle().handle.getMinHeight();
        Vector2 p = split.localToStageCoordinates(horizontal
                ? new Vector2(split.getFirstWidgetBounds().width + thickness / 2, split.getHeight() / 2)
                : new Vector2(split.getWidth() / 2, split.getSecondWidgetBounds().height + thickness / 2));
        stage.stageToScreenCoordinates(p);
        float before = split.getSplit();
        assertTrue(stage.touchDown(Math.round(p.x), Math.round(p.y), 0, 0));
        assertTrue(stage.touchDragged(Math.round(p.x) + (horizontal ? 12 : 0), Math.round(p.y) + (horizontal ? 0 : 8), 0));
        assertTrue(stage.touchUp(Math.round(p.x), Math.round(p.y), 0, 0));
        assertNotEquals(before, split.getSplit(), .00001f);
        validateTree(stage.getRoot());
    }

    private static void validateTree(Actor actor) {
        if (actor instanceof Layout layout) layout.validate();
        if (actor instanceof com.badlogic.gdx.scenes.scene2d.Group group)
            for (Actor child : group.getChildren()) validateTree(child);
    }

    private void assertFloating(Probe probe) {
        assertTrue(ready(probe.panel));
        assertNotSame(stage, probe.field.getStage());
        if (!oldStages.contains(probe.panel.getStage(), true)) oldStages.add(probe.panel.getStage());
        assertSame(probe.panel.getStage().getRoot(), probe.panel.getParent());
        probe.assertInsidePanel();
        probe.assertUsable();
        assertEquals(0, DockingTestFixture.countIdentity(stage.getRoot(), probe.field));
        assertNoStaleOwners(probe);
    }

    private void assertDocked(Probe probe, VisTable slot) {
        assertSame(slot, probe.panel.getParent());
        assertSame(stage, probe.field.getStage());
        assertSame(probe.panel, slot.getCell(probe.panel).getActor());
        probe.assertInsidePanel();
        probe.assertUsable();
        assertEquals(1, DockingTestFixture.countIdentity(slot, probe.field));
        assertNoStaleOwners(probe);
    }

    private void assertNoStaleOwners(Probe probe) {
        for (Stage oldStage : oldStages) {
            assertEquals(oldStage == probe.field.getStage() ? 1 : 0,
                    DockingTestFixture.countIdentity(oldStage.getRoot(), probe.field));
        }
    }

    private void assertAllContent() {
        a.assertInsidePanel();
        b.assertInsidePanel();
        if (a.field.getStage() != null) a.assertUsable();
        if (b.field.getStage() != null) b.assertUsable();
        assertNoStaleOwners(a);
        assertNoStaleOwners(b);
    }

    @Override
    public void render() {
        if (running) {
            status.setText("Running native lifecycle step " + step);
            advance();
        }
        Gdx.gl.glClearColor(.12f, .14f, .18f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }

    @Override
    public void resize(int w, int h) {
        if (stage != null) stage.getViewport().update(w, h, true);
    }

    @Override
    public void dispose() {
        f.close();
        stage.dispose();
        VisUI.dispose();
    }

    private static final class Probe {
        final DockablePanel panel;
        final VisTable content = new VisTable();
        final VisTextField field;
        final VisCheckBox toggle = new VisCheckBox("Retained state");
        final String text;
        final boolean checked;

        Probe(DockablePanel panel, String text, boolean checked) {
            this.panel = panel;
            this.text = text;
            this.checked = checked;
            field = new VisTextField(text);
            toggle.setChecked(checked);
            content.add(new VisLabel(text)).growX().row();
            content.add(field).growX().row();
            content.add(toggle).growX();
            panel.add(content).grow();
        }

        void assertInsidePanel() {
            assertSame(panel, content.getParent());
            assertSame(content, field.getParent());
            assertSame(content, toggle.getParent());
            assertSame(content, panel.getCell(content).getActor());
            assertEquals(1, DockingTestFixture.countIdentity(panel, field));
            assertEquals(text, field.getText());
            assertEquals(checked, toggle.isChecked());
            if (panel.getStage() != null) {
                assertSame(panel.getStage(), field.getStage());
                assertEquals(1, DockingTestFixture.countIdentity(panel.getStage().getRoot(), field));
            }
        }

        void assertUsable() {
            Stage owner = field.getStage();
            assertNotNull(owner);
            validateTree(owner.getRoot());
            for (Actor actor = field; actor != owner.getRoot(); actor = actor.getParent()) {
                assertTrue(actor.getClass().getSimpleName() + " is visible", actor.isVisible());
                assertTrue(actor.getClass().getSimpleName() + " has positive bounds", actor.getWidth() > 0 && actor.getHeight() > 0);
            }
            Vector2 p = field.localToStageCoordinates(new Vector2(field.getWidth() / 2, field.getHeight() / 2));
            Actor target = owner.hit(p.x, p.y, true);
            assertTrue("Visible field is reachable", target != null && target.isDescendantOf(field));
        }
    }
}
