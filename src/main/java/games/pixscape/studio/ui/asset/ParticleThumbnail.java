package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.scenes.scene2d.Actor;
import games.pixscape.runtime.particle.ParticleEffect;

public final class ParticleThumbnail extends Actor {

    private final ParticleEffect effect;
    private boolean playing = false;

    public ParticleThumbnail(FileHandle particleFile, FileHandle imagesDir) {
        effect = new ParticleEffect();
        effect.load(particleFile, imagesDir);
        effect.scaleEffect(0.35f);
    }

    public void startPreview() {
        effect.reset();
        playing = true;
    }

    public void stopPreview() {
        playing = false;
    }

    @Override
    public void act(float delta) {
        if (!playing) return;
        effect.update(delta);
    }

    @Override
    public void draw(Batch batch, float parentAlpha) {
        if (!playing) return;

        effect.setPosition(
                getX() + getWidth() * 0.5f,
                getY() + getHeight() * 0.5f
        );
        effect.draw(batch);
    }

    public void dispose() {
        effect.dispose();
    }
}