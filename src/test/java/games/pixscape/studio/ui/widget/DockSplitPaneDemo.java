package games.pixscape.studio.ui.widget;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.Input;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.ui.Skin;
import com.badlogic.gdx.scenes.scene2d.ui.WidgetGroup;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.ui.StudioStage;

/** Standalone visual harness: does not instantiate or modify DockManager. */
public final class DockSplitPaneDemo extends ApplicationAdapter {
    private Stage stage;
    private DockSplitPane horizontal, vertical;
    private VisLabel status;
    private int backgroundClicks;
    private float forcedLength = -1;

    public static void main(String[] args) {
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("DockSplitPane prototype");
        config.setWindowedMode(1000, 720);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL32, 3, 2);
        new Lwjgl3Application(new DockSplitPaneDemo(), config);
    }

    @Override public void create() {
        VisUI.load(new Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
        stage = new StudioStage(new ScreenViewport());
        Gdx.input.setInputProcessor(stage);
        Actor background = new Actor();
        background.setBounds(0, 0, 10000, 10000);
        background.addListener(new ClickListener() {
            @Override public void clicked(InputEvent e, float x, float y) { backgroundClicks++; }
        });
        stage.addActor(background);
        horizontal = new DockSplitPane(content("H left"), content("H right"), false);
        vertical = new DockSplitPane(content("V top"), content("V bottom"), true);
        stage.addActor(horizontal);
        stage.addActor(vertical);
        VisTable toolbar = new VisTable();
        toolbar.setFillParent(true);
        toolbar.top().left().pad(12);
        toolbar.setTouchable(Touchable.childrenOnly);
        toolbar.add(new VisLabel("Drag handles; blank panel areas click through to the background.")).colspan(7).left().row();
        toolbar.add(new VisLabel("Esc cancels. F2 resizes during drag. F3 collapses. F4 restores.")).colspan(7).left().row();
        for (float length : new float[]{0, 2, 4, 5, 20, -1}) {
            VisTextButton button = new VisTextButton(length < 0 ? "Restore" : "Axis " + (int)length);
            button.addListener(new ChangeListener() {
                @Override public void changed(ChangeEvent e, Actor actor) { forcedLength = length; arrange(); }
            });
            toolbar.add(button).pad(4);
        }
        toolbar.row();
        status = new VisLabel("");
        toolbar.add(status).colspan(7).left();
        stage.addActor(toolbar);
        stage.addCaptureListener(new InputListener() {
            @Override public boolean keyDown(InputEvent e, int key) {
                switch (key) {
                    case Input.Keys.ESCAPE -> { stage.cancelTouchFocus(horizontal); stage.cancelTouchFocus(vertical); }
                    case Input.Keys.F2 -> { forcedLength = 200; arrange(); }
                    case Input.Keys.F3 -> { forcedLength = 2; arrange(); }
                    case Input.Keys.F4 -> { forcedLength = -1; arrange(); }
                    default -> { return false; }
                }
                return true;
            }
        });
        arrange();
    }

    private WidgetGroup content(String title) {
        WidgetGroup pane = new WidgetGroup();
        pane.setTouchable(Touchable.childrenOnly);
        VisLabel label = new VisLabel(title);
        label.setBounds(10, 120, 150, 22);
        pane.addActor(label);
        VisTextField field = new VisTextField("Type here");
        field.setBounds(10, 80, 150, 28);
        pane.addActor(field);
        VisTextButton button = new VisTextButton("Clicked 0");
        button.setBounds(10, 40, 150, 28);
        button.addListener(new ChangeListener() {
            private int clicks;
            @Override public void changed(ChangeEvent e, Actor actor) { button.setText("Clicked " + ++clicks); }
        });
        pane.addActor(button);
        return pane;
    }

    private void arrange() {
        if (horizontal == null) return;
        float halfWidth = Math.max(0, (stage.getWidth() - 60) / 2);
        float height = Math.max(0, stage.getHeight() - 190);
        horizontal.setBounds(20, 20, forcedLength < 0 ? halfWidth : forcedLength, height);
        vertical.setBounds(40 + halfWidth, 20, halfWidth, forcedLength < 0 ? height : forcedLength);
    }

    @Override public void render() {
        Gdx.gl.glClearColor(.10f, .13f, .17f, 1);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        status.setText(String.format(java.util.Locale.ROOT,
                "Background clicks: %d    H ratio: %.3f    V ratio: %.3f    H width: %.1f    V height: %.1f",
                backgroundClicks, horizontal.getSplit(), vertical.getSplit(), horizontal.getWidth(), vertical.getHeight()));
        stage.act(Gdx.graphics.getDeltaTime());
        stage.draw();
    }
    @Override public void resize(int width, int height) {
        if (stage != null) { stage.getViewport().update(width, height, true); arrange(); }
    }
    @Override public void dispose() { stage.dispose(); VisUI.dispose(); }
}
