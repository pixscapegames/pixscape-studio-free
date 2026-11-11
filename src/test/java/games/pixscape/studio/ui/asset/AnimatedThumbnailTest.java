package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static org.junit.Assert.*;

public class AnimatedThumbnailTest {
    @Test public void variableDurationsLoopAndPauseUnderHiddenAncestor() throws Exception {
        Array<Texture> frames = new Array<>(); frames.setSize(3);
        AnimatedThumbnail preview = new AnimatedThumbnail(frames, new int[]{25, 75, 150});
        com.badlogic.gdx.scenes.scene2d.Group parent = new com.badlogic.gdx.scenes.scene2d.Group(); parent.addActor(preview);
        preview.startPreview(); preview.act(.025f); assertEquals(1, currentFrame(preview));
        preview.act(.075f); assertEquals(2, currentFrame(preview));
        parent.setVisible(false); preview.act(10); assertEquals(2, currentFrame(preview));
        parent.setVisible(true); preview.act(.1f); preview.act(.05f); assertEquals(0, currentFrame(preview));
        preview.act(-1); assertEquals(0, currentFrame(preview));
        preview.act(30); assertEquals("Long pauses are bounded, not replayed", 2, currentFrame(preview));
        assertThrows(IllegalArgumentException.class, () -> new AnimatedThumbnail(frames, new int[]{10, 0, 10}));
        assertThrows(IllegalArgumentException.class, () -> new AnimatedThumbnail(frames, new int[]{Integer.MAX_VALUE, 10, 10}));
    }

    @Test public void playbackUsesOnlyTheSelectedInclusiveRangeAndSupportsReverse() throws Exception {
        Array<Texture> borrowed = new Array<>();
        borrowed.setSize(8);
        AnimatedThumbnail preview = new AnimatedThumbnail(borrowed, 4, 5, 3, true);
        preview.startPreview();
        assertEquals(5, currentFrame(preview));
        preview.act(.25f);
        assertEquals(4, currentFrame(preview));
        preview.act(.25f);
        assertEquals(3, currentFrame(preview));
        preview.act(.25f);
        assertEquals(5, currentFrame(preview));
        preview.stopPreview();
        preview.act(.25f);
        assertEquals(5, currentFrame(preview));
        assertEquals(8, borrowed.size);
    }

    private static int currentFrame(AnimatedThumbnail preview) throws Exception {
        var field = AnimatedThumbnail.class.getDeclaredField("currentFrame");
        field.setAccessible(true);
        return field.getInt(preview);
    }
}
