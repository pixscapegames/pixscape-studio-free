package games.pixscape.studio.ui.main;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.g2d.SpriteBatch;
import com.badlogic.gdx.graphics.glutils.FrameBuffer;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.helper.RuntimeFs;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.HudBatch;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.service.StudioGlTestScope;
import org.junit.Test;

import java.nio.ByteBuffer;

import static org.junit.Assert.*;

public class StudioApplicationAdapterPreviewRecoveryContractTest {
    @Test
    public void existingHudStillRendersAfterRepeatedDesktopPreviewReturns() throws Exception {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig.setInstance(new ProjectConfig());
        Throwable[] failure = {null};
        var config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 64);
        config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        try (var globals = new StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                @Override public void create() {
                    Texture texture = null;
                    AtlasRuntimeService.TextureArrayBundle bundle = null;
                    HudBatch hud = null;
                    SpriteBatch world = null;
                    try {
                        ShaderRegistry.initDefaults();
                        var shader = ShaderRegistry.get(RuntimeFs.HUD_TEXTURE_ARRAY);
                        Pixmap pixel = new Pixmap(1, 1, Pixmap.Format.RGBA8888);
                        pixel.setColor(Color.WHITE);
                        pixel.fill();
                        texture = new Texture(pixel);
                        Array<Texture> textures = new Array<>();
                        textures.add(texture);
                        bundle = AtlasRuntimeService.buildTextureArrayFromTextures(textures, 1, 1, GLCaps.detect());
                        pixel.dispose();
                        hud = new HudBatch(4, shader, bundle);
                        hud.setProjectionMatrix(new Matrix4().setToOrtho2D(0, 0, 64, 64));
                        world = new SpriteBatch();
                        world.setProjectionMatrix(new Matrix4().setToOrtho2D(200, 400, 64, 64));
                        var studio = new StudioApplicationAdapter();
                        assertHudPixel(hud, world, texture);
                        for (int i = 0; i < 3; i++) {
                            studio.setPreviewActive(true);
                            assertTrue(studio.isPreviewActive());
                            studio.setPreviewActive(false);
                            assertFalse(studio.isPreviewActive());
                            // Keep the already prepared HUD, without rematerialization.
                            assertHudPixel(hud, world, texture);
                            assertSame(shader, ShaderRegistry.get(RuntimeFs.HUD_TEXTURE_ARRAY));
                        }
                    } catch (Throwable error) {
                        failure[0] = error;
                    } finally {
                        if (hud != null) hud.dispose();
                        if (world != null) world.dispose();
                        if (bundle != null) bundle.textureArray.dispose();
                        if (texture != null) { TextureRegistry.release(texture); texture.dispose(); }
                        InternalTextures.dispose();
                        TextureRegistry.clear();
                        ShaderRegistry.disposeAll();
                        Gdx.app.exit();
                    }
                }
            }, config);
        } finally {
            ProjectConfig.setInstance(previous);
        }
        if (failure[0] != null) throw new AssertionError(failure[0]);
    }

    private static void assertHudPixel(HudBatch hud, SpriteBatch world, Texture texture) {
        FrameBuffer target = new FrameBuffer(Pixmap.Format.RGBA8888, 64, 64, false);
        target.begin();
        try {
            Gdx.gl.glClearColor(0, 0, 0, 1);
            Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            // Leave a world-space shader active, as Studio does before drawing the HUD.
            world.begin();
            world.draw(texture, 200, 400, 1, 1);
            world.end();
            hud.begin();
            hud.setColor(Color.RED);
            hud.draw(texture, 8, 8, 16, 16);
            hud.end();
            assertEquals("HUD submission must not use a disposed shader", GL20.GL_NO_ERROR, Gdx.gl.glGetError());
            ByteBuffer pixel = BufferUtils.newByteBuffer(4);
            Gdx.gl.glReadPixels(16, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
            assertEquals("HUD remains at screen coordinates after preview", 255, pixel.get(0) & 255);
            assertEquals(0, pixel.get(1) & 255);
            assertEquals(0, pixel.get(2) & 255);
        } finally {
            target.end();
            target.dispose();
        }
    }
}
