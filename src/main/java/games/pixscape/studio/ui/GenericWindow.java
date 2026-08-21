package games.pixscape.studio.ui;

import com.badlogic.gdx.ApplicationListener;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import games.pixscape.studio.ui.docking.DockablePanel;

public class GenericWindow implements ApplicationListener {
    private Stage stage;
    private final DockablePanel panel;

    public GenericWindow(DockablePanel panel) {

        this.panel = panel;
    }

    @Override
    public void create() {
        stage = new StudioUiStage(new ScreenViewport());
        attachPanel();
        Gdx.input.setInputProcessor(stage);
    }

    public void attachPanel() {
        if (stage == null) return;
        panel.remove();
        panel.setFillParent(true);
        stage.addActor(panel);
    }

    public void detachPanel() {
        if (panel.getStage() != stage) return;
        panel.remove();
        panel.setFillParent(false);
    }

    public boolean containsPanel() {
        return panel.getStage() == stage;
    }

    @Override
    public void render() {
        float dt = Gdx.graphics.getDeltaTime();
        Gdx.gl.glClearColor(0.12f, 0.13f, 0.15f, 1f);
        Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
        stage.act(dt);
        stage.draw();
    }

    @Override
    public void pause() {

    }

    @Override
    public void resume() {

    }

    @Override
    public void resize(int width, int height) {
        stage.getViewport().update(width, height, true);
    }

    @Override
    public void dispose() {
        detachPanel();
        stage.dispose();
    }
}
