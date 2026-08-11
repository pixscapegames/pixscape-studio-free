package games.pixscape.html.client;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.math.MathUtils;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.ProgressBar;
import com.badlogic.gdx.scenes.scene2d.utils.TextureRegionDrawable;
import com.badlogic.gdx.utils.viewport.ScreenViewport;

/** Minimal Scene2D progress presentation used after the browser preloader. */
final class PreviewLoadingUi {
    private static final float BAR_HEIGHT = 10f;

    private final Texture texture;
    private final SpriteBatch batch;
    private final Stage stage;
    private final ProgressBar progressBar;

    PreviewLoadingUi() {
        Pixmap pixmap = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
        pixmap.setColor(Color.WHITE);
        pixmap.fill();
        texture = new Texture(pixmap);
        pixmap.dispose();

        TextureRegionDrawable pixel = new TextureRegionDrawable(texture);
        ProgressBar.ProgressBarStyle style = new ProgressBar.ProgressBarStyle();
        style.background = pixel.tint(new Color(0.08f, 0.11f, 0.14f, 1f));
        style.knobBefore = pixel.tint(new Color(0.25f, 0.82f, 0.94f, 1f));
        style.background.setMinHeight(BAR_HEIGHT);
        style.knobBefore.setMinHeight(BAR_HEIGHT);
        progressBar = new ProgressBar(0f, 1f, 0.001f, false, style);
        progressBar.setProgrammaticChangeEvents(false);

        batch = new SpriteBatch();
        stage = new Stage(new ScreenViewport(), batch);
        stage.addActor(progressBar);
        resize(Gdx.graphics.getWidth(), Gdx.graphics.getHeight());
    }

    void render(float delta, float progress) {
        progressBar.setValue(MathUtils.clamp(progress, 0f, 1f));
        Gdx.gl.glClearColor(0.035f, 0.047f, 0.06f, 1f);
        Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
        stage.act(Math.min(delta, 0.1f));
        stage.getViewport().apply(true);
        stage.draw();
    }

    void resize(int width, int height) {
        int safeWidth = Math.max(1, width);
        int safeHeight = Math.max(1, height);
        stage.getViewport().update(safeWidth, safeHeight, true);
        float barWidth = Math.min(safeWidth * 0.52f, 420f);
        progressBar.setBounds((safeWidth - barWidth) * 0.5f,
                (safeHeight - BAR_HEIGHT) * 0.5f, barWidth, BAR_HEIGHT);
    }

    void dispose() {
        stage.dispose();
        batch.dispose();
        texture.dispose();
    }
}
