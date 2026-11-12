package games.pixscape.studio.system;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import org.junit.Test;
import org.lwjgl.glfw.GLFW;

public class StudioForwardFallbackGlSmokeTest {
    @Test
    public void standaloneForwardMatchesPackedForEveryRenderDomain() {
        check(false);
    }

    @Test
    public void suppliedMaterialsRenderInStudioOffLegacyForward() {
        check(true);
    }

    private static void check(boolean supplied) {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);
        config.setWindowedMode(64, 32);
        config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        // libGDX 1.14.2 frees the previous application's error callback at shutdown.
        // Clear its native address before LWJGL tries to wrap it when installing the next callback.
        GLFW.nglfwSetErrorCallback(0L);
        new Lwjgl3Application(new ApplicationAdapter() {
            @Override
            public void create() {
                try {
                    if (supplied) StudioForwardFallbackGlCheck.suppliedMaterials();
                    else StudioForwardFallbackGlCheck.run();
                } catch (Throwable error) {
                    failure[0] = error;
                } finally {
                    Gdx.app.exit();
                }
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Studio Forward fallback pixel oracle failed", failure[0]);
    }
}
