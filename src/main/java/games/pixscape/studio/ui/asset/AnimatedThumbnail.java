package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.Scaling;

public final class AnimatedThumbnail extends Actor {

    private final Array<Texture> frames;
    private final float fps;
    private final int firstFrame;
    private final int frameCount;
    private final int direction;
    private final boolean flipX;
    private Scaling scaling = Scaling.fit;

    private int currentFrame;
    private float time;
    private boolean playing = false;
    private int[] frameEndsMs;

    public AnimatedThumbnail(Array<Texture> frames, float fps) {
        this(frames, fps, 0, Math.max(0, frames.size - 1), false);
    }

    /**
     * Borrows textures; the shared cache remains their owner. Supports reversed clip ranges.
     */
    public AnimatedThumbnail(Array<Texture> frames, float fps, int start, int end, boolean flipX) {
        this.frames = frames;
        this.fps = fps > 0f ? fps : 12f;
        firstFrame = Math.max(0, Math.min(start, frames.size - 1));
        int lastFrame = Math.max(0, Math.min(end, frames.size - 1));
        frameCount = Math.abs(lastFrame - firstFrame) + 1;
        direction = lastFrame >= firstFrame ? 1 : -1;
        this.flipX = flipX;
        currentFrame = firstFrame;
    }

    public void setScaling(Scaling scaling) {
        this.scaling = scaling;
    }

    /**
     * Variable durations preserve each occurrence, including repeated textures.
     */
    public AnimatedThumbnail(Array<Texture> frames, int[] durationsMs) {
        this(frames, 12f);
        if (durationsMs.length != frames.size) throw new IllegalArgumentException("Frame/duration count mismatch.");
        frameEndsMs = new int[durationsMs.length];
        int total = 0;
        for (int i = 0; i < durationsMs.length; i++) {
            if (durationsMs[i] <= 0 || (long) total + durationsMs[i] > Integer.MAX_VALUE)
                throw new IllegalArgumentException("Invalid frame duration.");
            total += durationsMs[i];
            frameEndsMs[i] = total;
        }
    }

    public void startPreview() {
        playing = true;
        time = 0f;
        currentFrame = firstFrame;
    }

    public void stopPreview() {
        playing = false;
        currentFrame = firstFrame;
    }

    @Override
    public void act(float delta) {
        if (!playing || !isVisible() || frames.isEmpty() || frameCount == 1) return;
        for (Actor ancestor = getParent(); ancestor != null; ancestor = ancestor.getParent()) {
            if (!ancestor.isVisible()) return;
        }

        if (frameEndsMs != null) {
            time = (time + Math.min(.1f, Math.max(0, delta)) * 1000) % frameEndsMs[frameEndsMs.length - 1];
            int found = java.util.Arrays.binarySearch(frameEndsMs, (int) time);
            currentFrame = found >= 0 ? found + 1 : -found - 1;
            return;
        }
        time += delta;
        currentFrame = firstFrame + direction * ((int) (time * fps) % frameCount);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (frames.isEmpty()) return;

        Texture tex = frames.get(currentFrame);

        if (tex == null) return;
        var size = scaling.apply(tex.getWidth(), tex.getHeight(), getWidth(), getHeight());
        batch.draw(tex, getX() + (getWidth() - size.x) / 2f,
                getY() + (getHeight() - size.y) / 2f, size.x, size.y,
                flipX ? 1f : 0f, 1f, flipX ? 0f : 1f, 0f);
    }
}
