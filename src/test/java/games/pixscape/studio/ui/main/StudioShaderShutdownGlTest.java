package games.pixscape.studio.ui.main;

import com.badlogic.gdx.Application;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.graphics.Mesh;
import com.badlogic.gdx.graphics.Texture;
import games.pixscape.studio.ui.StudioStage;
import games.pixscape.studio.ui.widget.ColorPickerField;
import com.kotcrab.vis.ui.widget.color.ColorPicker;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import java.util.Map;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.event.EventFlow;
import games.pixscape.studio.configuration.SceneMeta;
import games.pixscape.studio.service.StandaloneTextureCache;
import com.badlogic.gdx.utils.ObjectMap;
import games.pixscape.runtime.service.ShaderRegistry;
import org.junit.Test;

import static org.junit.Assert.*;

public class StudioShaderShutdownGlTest {
    @Test
    public void shutdownReleasesProgramsAndDoesNotRetainTheClosedApplication() throws Exception {
        Application[] application = new Application[1];
        Texture[] borrowed = new Texture[1];
        ResourceAssetInspectorGlTest.withStudio(700, (studio, root) -> {
            application[0] = Gdx.app;
            ProjectConfig.getInstance().projectDirectoryPath = root.path();
            ProjectConfig.getInstance().projectFileName = "shutdown";
            Gdx.files.classpath("com/badlogic/gdx/utils/lsans-15.png").copyTo(root.child("preview.png"));
            borrowed[0] = StandaloneTextureCache.getOrLoadProjectRelative("preview.png");
            assertNotNull(borrowed[0]);
            assertTrue(borrowed[0].getTextureObjectHandle() != 0);
            assertTrue(ShaderRegistry.getRegisteredNames().size > 0);
            assertTrue(ShaderProgram.getNumManagedShaderPrograms() > 0);
        });

        assertEquals("Studio owns the registry programs until shutdown", 0,
                ShaderRegistry.getRegisteredNames().size);
        assertEquals(0, borrowed[0].getTextureObjectHandle());
        assertNoManagedResources(ShaderProgram.class, "shaders", application[0]);
        assertNoManagedResources(Mesh.class, "meshes", application[0]);
        assertNoManagedResources(Texture.class, "managedTextures", application[0]);
    }

    @Test
    public void transientColorPickersReleaseShadersOnRemovalAndStageShutdown() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(700, (studio, root) -> {
            int baseline = ShaderProgram.getNumManagedShaderPrograms();
            StudioStage stage = new StudioStage(new ScreenViewport());
            try {
                ColorPickerField color = new ColorPickerField("Color", "Choose");
                var show = ColorPickerField.class.getDeclaredMethod("showPicker");
                show.setAccessible(true);
                int stageBaseline = ShaderProgram.getNumManagedShaderPrograms();
                show.invoke(color);
                assertEquals("A detached field cannot allocate a picker", stageBaseline,
                        ShaderProgram.getNumManagedShaderPrograms());
                stage.addActor(color);
                for (int i = 0; i < 3; i++) {
                    show.invoke(color);
                    ColorPicker picker = (ColorPicker) stage.getActors().peek();
                    assertTrue(ShaderProgram.getNumManagedShaderPrograms() > stageBaseline);
                    picker.remove();
                    assertTrue(picker.isDisposed());
                    assertEquals(stageBaseline, ShaderProgram.getNumManagedShaderPrograms());
                }
                show.invoke(color); // Also close a picker still open when its stage shuts down.
            } finally {
                stage.dispose();
            }
            assertEquals(baseline, ShaderProgram.getNumManagedShaderPrograms());
        });
    }

    @Test
    public void closingSceneContextsReleasesTheirSystemSubscriptions() throws Exception {
        ResourceAssetInspectorGlTest.withStudio(700, (studio, root) -> {
            int selectionListeners = listenerCount(EventFlow.SelectionChanged.class);
            int fixtureListeners = listenerCount(EventFlow.FixtureSelectionCleared.class);
            for (int i = 0; i < 3; i++) {
                var context = studio.getCanvas().createSceneContext("candidate" + i, new SceneMeta());
                assertTrue(listenerCount(EventFlow.SelectionChanged.class) > selectionListeners);
                assertTrue(listenerCount(EventFlow.FixtureSelectionCleared.class) > fixtureListeners);
                studio.getCanvas().releaseSceneContext(context);
                context.dispose();
                assertEquals(selectionListeners, listenerCount(EventFlow.SelectionChanged.class));
                assertEquals(fixtureListeners, listenerCount(EventFlow.FixtureSelectionCleared.class));
            }
        });
    }

    @SuppressWarnings("unchecked")
    private static int listenerCount(Class<?> eventType) throws Exception {
        var field = EventFlow.class.getDeclaredField("listeners");
        field.setAccessible(true);
        var listeners = (ObjectMap<Class<?>, com.badlogic.gdx.utils.Array<?>>) field.get(EventFlow.i());
        var registered = listeners.get(eventType);
        return registered == null ? 0 : registered.size;
    }

    @SuppressWarnings("unchecked")
    private static void assertNoManagedResources(Class<?> type, String name, Application application)
            throws Exception {
        var field = type.getDeclaredField(name);
        field.setAccessible(true);
        Object managed = field.get(null);
        if (managed instanceof ObjectMap<?, ?> map) {
            assertFalse(type.getSimpleName(),
                    ((ObjectMap<Application, ?>) map).containsKey(application));
        } else {
            assertFalse(type.getSimpleName(), ((Map<?, ?>) managed).containsKey(application));
        }
    }
}
