package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.utils.Array;
import org.junit.Test;

import static org.junit.Assert.*;

public class AnimatedThumbnailTest {
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
