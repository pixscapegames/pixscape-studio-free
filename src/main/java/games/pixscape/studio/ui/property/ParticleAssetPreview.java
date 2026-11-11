package games.pixscape.studio.ui.property;

import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.Batch;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.math.collision.BoundingBox;
import com.badlogic.gdx.scenes.scene2d.Actor;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.scenes.scene2d.ui.Widget;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.VisLabel;
import com.kotcrab.vis.ui.widget.VisTable;
import com.kotcrab.vis.ui.widget.VisTextButton;
import games.pixscape.runtime.particle.ParticleEffect;
import games.pixscape.studio.ui.widget.SimpleFloatSlider;

import java.util.Locale;

/** Inspector-only simulation. No scene effect, atlas texture, or authored parameter is borrowed or changed. */
final class ParticleAssetPreview extends VisTable {
    private static final double STEP = 0.01;
    private static final float MAX_FRAME_DELTA = 0.05f;
    private static final int MAX_STEPS = 80;
    private final OwnedEffect effect = new OwnedEffect();
    private final PreviewCanvas canvas = new PreviewCanvas();
    private float speed;
    private double remainder;
    private boolean disposed;
    private float frameWidth = 64f, frameHeight = 64f, centerX, centerY;

    ParticleAssetPreview(FileHandle source, FileHandle images, float speed) {
        super(true);
        this.speed = speed;
        try {
            if (!source.exists()) throw new IllegalArgumentException("Particle file is missing: " + source.name());
            effect.load(source, images);
            if (effect.getEmitters().isEmpty()) throw new IllegalArgumentException("No particle emitters in file");
            for (var emitter : effect.getEmitters()) {
                if (emitter.getSprites().isEmpty())
                    throw new IllegalArgumentException("Particle image is missing: " + emitter.getName());
            }
            prepareFraming();
        } catch (RuntimeException failure) {
            dispose();
            throw failure;
        }
        canvas.setName("particleAssetPreview");
        add(canvas).growX().height(240).row();
        VisLabel value = new VisLabel(multiplier());
        value.setName("particlePreviewSpeedValue");
        VisTable heading = new VisTable(true);
        heading.add(new VisLabel("Preview speed")).left().expandX();
        heading.add(value).right();
        add(heading).growX().row();
        SimpleFloatSlider slider = new SimpleFloatSlider(0f, 16f, 0.01f);
        slider.setName("particlePreviewSpeed");
        slider.bind(() -> this.speed, next -> {
            this.speed = next;
            value.setText(multiplier());
        });
        add(slider).minWidth(40).growX().row();
        VisTextButton restart = new VisTextButton("Restart");
        restart.setName("particlePreviewRestart");
        restart.addListener(new ChangeListener() {
            @Override public void changed(ChangeEvent event, Actor actor) { restart(); }
        });
        add(restart).left().row();
        VisLabel hint = new VisLabel("Preview only");
        hint.setWrap(true);
        add(hint).growX().left().row();
    }

    float speed() { return speed; }
    // The bundled UI font has no multiplication-sign glyph.
    private String multiplier() { return String.format(Locale.ROOT, "%.2fx", speed); }

    void restart() {
        effect.reset(false);
        remainder = 0;
    }

    /** Fixed integer-millisecond steps avoid the emitter's fractional-delta motion/lifetime mismatch.
     * Slow frames discard elapsed time beyond 50 ms; no catch-up backlog survives hiding or a stall. */
    void advance(float delta) {
        if (disposed || speed == 0 || !Float.isFinite(delta) || delta <= 0) return;
        remainder += Math.min(delta, MAX_FRAME_DELTA) * (double) speed;
        int steps = Math.min(MAX_STEPS, (int) ((remainder + 1e-9) / STEP));
        for (int i = 0; i < steps; i++) effect.update((float) STEP);
        remainder = Math.max(0, remainder - steps * STEP);
    }

    /** One bounded load-time sample fixes the camera for the entire preview, including Restart.
     * Long-delayed or unusually expansive effects may extend beyond this three-second framing sample. */
    private void prepareFraming() {
        BoundingBox bounds = new BoundingBox();
        bounds.ext(-32, -32, 0).ext(32, 32, 0);
        effect.start();
        for (int i = 0; i < 300; i++) {
            effect.update((float) STEP);
            BoundingBox sample = effect.getBoundingBox();
            if (sample.isValid() && Float.isFinite(sample.getWidth()) && Float.isFinite(sample.getHeight()))
                bounds.ext(sample);
        }
        frameWidth = bounds.getWidth();
        frameHeight = bounds.getHeight();
        centerX = bounds.getCenterX();
        centerY = bounds.getCenterY();
        restart();
    }

    void dispose() {
        if (disposed) return;
        disposed = true;
        effect.dispose();
    }

    private final class PreviewCanvas extends Widget {
        private final Matrix4 savedTransform = new Matrix4();
        private final Matrix4 previewTransform = new Matrix4();
        private boolean drawn;

        @Override public void act(float delta) {
            super.act(delta);
            boolean visible = getStage() != null;
            for (Actor actor = this; actor != null && visible; actor = actor.getParent()) visible = actor.isVisible();
            if (drawn && visible) advance(delta);
            drawn = false;
        }

        @Override protected void setStage(Stage stage) {
            drawn = false;
            super.setStage(stage);
        }

        @Override public void draw(Batch batch, float parentAlpha) {
            if (disposed || getWidth() <= 0 || getHeight() <= 0) return;
            batch.flush();
            if (!clipBegin()) return;
            drawn = true;
            savedTransform.set(batch.getTransformMatrix());
            int src = batch.getBlendSrcFunc(), dst = batch.getBlendDstFunc();
            int srcAlpha = batch.getBlendSrcFuncAlpha(), dstAlpha = batch.getBlendDstFuncAlpha();
            boolean blending = batch.isBlendingEnabled();
            float color = batch.getPackedColor();
            try {
                float scale = Math.min(getWidth() * 0.85f / frameWidth, getHeight() * 0.85f / frameHeight);
                previewTransform.set(savedTransform)
                        .translate(getX() + getWidth() / 2f, getY() + getHeight() / 2f, 0)
                        .scale(scale, scale, 1).translate(-centerX, -centerY, 0);
                batch.setTransformMatrix(previewTransform);
                batch.enableBlending();
                effect.draw(batch); // Deliberately no delta: drawing never advances the effect.
                batch.flush();
            } finally {
                batch.setTransformMatrix(savedTransform);
                batch.setBlendFunctionSeparate(src, dst, srcAlpha, dstAlpha);
                if (!blending) batch.disableBlending();
                batch.setPackedColor(color);
                clipEnd();
            }
        }
    }

    /** Directory loading owns every returned texture. Track allocations immediately, including partial
     * loads; dispose each once even when several emitters reuse a sprite. Never call the base disposer. */
    private static final class OwnedEffect extends ParticleEffect {
        private final Array<Texture> textures = new Array<>();

        @Override protected Texture loadTexture(FileHandle file) {
            if (!file.exists()) throw new IllegalArgumentException("Particle image is missing: " + file.name());
            Texture texture = super.loadTexture(file);
            textures.add(texture);
            return texture;
        }

        @Override public void dispose() {
            for (int i = 0; i < textures.size; i++) textures.get(i).dispose();
            textures.clear();
        }
    }
}
