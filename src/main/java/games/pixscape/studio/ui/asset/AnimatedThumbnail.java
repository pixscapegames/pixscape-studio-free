package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;

public final class AnimatedThumbnail extends Actor {

    private final Array<Texture> frames;
    private final float fps;

    private int currentFrame;
    private float time;
    private boolean playing = false;

    public AnimatedThumbnail(Array<Texture> frames, float fps) {
        this.frames = frames;
        this.fps = fps > 0f ? fps : 12f;
    }

    public void startPreview() {
        playing = true;
        time = 0f;
        currentFrame = 0;
    }

    public void stopPreview() {
        playing = false;
        currentFrame = 0;
    }

    @Override
    public void act(float delta) {
        if (!playing || frames.isEmpty()) return;

        time += delta;
        int frameCount = frames.size;
        currentFrame = (int) (time * fps) % frameCount;
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (frames.isEmpty()) return;

        Texture tex = frames.get(currentFrame);

        batch.draw(
                tex,
                getX(), getY(),
                getWidth(), getHeight()
        );
    }
}