package games.pixscape.studio.ui;

import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.Viewport;

/** Scene2D stage compatible with the Studio GL3 context. */
public final class StudioStage extends Stage {

    private final StudioSpriteBatch batch;

    public StudioStage(Viewport viewport) {
        this(viewport, StudioSpriteBatch.create());
    }

    private StudioStage(Viewport viewport, StudioSpriteBatch batch) {
        super(viewport, batch);
        this.batch = batch;
    }

    @Override
    public void dispose() {
        try {
            super.dispose();
        } finally {
            batch.dispose();
        }
    }
}