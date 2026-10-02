package games.pixscape.studio.batch;

import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.GL20;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.math.Matrix4;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.ShaderFloatParam;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.render.DynamicEntityRenderState;
import games.pixscape.runtime.render.InternalTextures;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.ShaderParameterLayout;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;
import java.nio.file.Files;

/** Opt-in integration check for Studio's distinct vertex layout. */
public class StudioShaderParameterGlSmokeTest {
    private static final String FRAGMENT = "#version 330 core\n"
            + "flat in int v_paramId;\n"
            + "uniform sampler2D u_entityParams;\n"
            + "out vec4 fragColor;\n"
            + "void main(){fragColor=vec4(texelFetch(u_entityParams,ivec2(0,v_paramId),0).rgb,1.0);}\n";
    private static final String BOOST_FRAGMENT = "#version 330 core\n"
            + "flat in int v_paramId;\n"
            + "uniform sampler2D u_entityParams;\n"
            + "out vec4 fragColor;\n"
            + "void main(){fragColor=vec4(0.0,texelFetch(u_entityParams,ivec2(0,v_paramId),0).r,0.0,1.0);}\n";

    @Test
    public void studioBatchKeepsTwoEntityRowsInOneDraw() {
        Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setTitle("Pixscape Studio shader parameter smoke");
        config.setWindowedMode(64, 32);
        config.setInitialVisible(false);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 2);
        config.disableAudio(true);
        new Lwjgl3Application(new ApplicationAdapter() {
            ShaderProgram shader;
            ShaderProgram gainShader;
            ShaderProgram boostShader;
            TextureArrayMeshBatchStudio batch;
            AtlasRuntimeService.TextureArrayBundle bundle;
            FileHandle projectDir;

            @Override public void create() {
                try {
                    ShaderRegistry.initDefaults();
                    shader = new ShaderProgram(
                            Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(),
                            FRAGMENT);
                    Assert.assertTrue(shader.getLog(), shader.isCompiled());
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(
                            new Array<>(), 16, 16, GLCaps.detect());
                    batch = new TextureArrayMeshBatchStudio(2);
                    batch.setTextureArrayBundle(bundle);

                    Array<ShaderFloatParam> defaults = new Array<>();
                    defaults.add(new ShaderFloatParam("red", 0f));
                    defaults.add(new ShaderFloatParam("green", 0f));
                    defaults.add(new ShaderFloatParam("blue", 0f));
                    ShaderParameterLayout layout = new ShaderParameterLayout("studio-smoke", defaults);
                    RenderStats stats = new RenderStats();
                    Gdx.gl.glViewport(0, 0, 64, 32);
                    Gdx.gl.glClearColor(0, 0, 0, 1);
                    Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                    batch.begin(new Matrix4().setToOrtho2D(0, 0, 64, 32), stats);
                    batch.setShader(shader, stats);
                    batch.setParameterLayout(layout, stats);
                    entity(0, 1f, 0f, stats);
                    entity(32, 0f, 1f, stats);
                    batch.end(stats);

                    Assert.assertEquals(1, stats.drawCalls);
                    Assert.assertEquals(1, stats.flushes);
                    Assert.assertEquals(0, stats.flushStateChanges);
                    assertRgb(16, 255, 0);
                    assertRgb(48, 0, 255);

                    String vertex = Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString();
                    gainShader = new ShaderProgram(vertex, FRAGMENT);
                    boostShader = new ShaderProgram(vertex, BOOST_FRAGMENT);
                    Assert.assertTrue(gainShader.getLog(), gainShader.isCompiled());
                    Assert.assertTrue(boostShader.getLog(), boostShader.isCompiled());
                    Array<ShaderFloatParam> gainDefaults = new Array<>();
                    gainDefaults.add(new ShaderFloatParam("u_gain", 0f));
                    Array<ShaderFloatParam> boostDefaults = new Array<>();
                    boostDefaults.add(new ShaderFloatParam("u_boost", 0f));
                    ShaderParameterLayout gainLayout = new ShaderParameterLayout("test", gainDefaults);
                    ShaderParameterLayout boostLayout = new ShaderParameterLayout("glow_pulse", boostDefaults);
                    Matrix4 projection = new Matrix4().setToOrtho2D(0, 0, 64, 32);

                    // Simulate culling while panning: each shader enters and leaves in both orders.
                    transitionFrame(projection, gainShader, gainLayout, 0, "u_gain", .25f,
                            null, null, null, 0f, 64, 0, 0, 0);
                    transitionFrame(projection, gainShader, gainLayout, 0, "u_gain", .5f,
                            boostShader, boostLayout, "u_boost", .75f, 128, 0, 0, 191);
                    transitionFrame(projection, boostShader, boostLayout, 32, "u_boost", .4f,
                            null, null, null, 0f, 0, 0, 0, 102);
                    transitionFrame(projection, boostShader, boostLayout, 0, "u_boost", .6f,
                            gainShader, gainLayout, "u_gain", .8f, 0, 153, 204, 0);
                    transitionFrame(projection, gainShader, gainLayout, 0, "u_gain", .3f,
                            null, null, null, 0f, 77, 0, 0, 0);

                    projectDir = new FileHandle(Files.createTempDirectory("pixscape-studio-shaders").toFile());
                    writeProjectShader("test", "u_gain", FRAGMENT);
                    writeProjectShader("tint", "u_tint", BOOST_FRAGMENT);
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    int testIndex = ShaderRegistry.indexOf("test");
                    int tintIndex = ShaderRegistry.indexOf("tint");
                    int glowIndex = ShaderRegistry.indexOf("glow_pulse");
                    Assert.assertTrue(testIndex >= 0 && tintIndex >= 0 && glowIndex >= 0);
                    RenderMaterialComponent testMaterial = new RenderMaterialComponent();
                    RenderMaterialComponent tintMaterial = new RenderMaterialComponent();
                    testMaterial.shaderIdx = testIndex;
                    tintMaterial.shaderIdx = tintIndex;
                    DynamicEntityRenderState renderState = new DynamicEntityRenderState(2);
                    int testSlot = renderState.acquireSlotForEntity(101);
                    int tintSlot = renderState.acquireSlotForEntity(202);
                    renderState.shader[testSlot] = testIndex;
                    renderState.shader[tintSlot] = tintIndex;
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .25f,
                            null, null, null, 0f, 64, 0, 0, 0);

                    writeProjectShader("alpha", "u_alpha", FRAGMENT);
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    int alphaIndex = ShaderRegistry.indexOf("alpha");
                    Assert.assertTrue(alphaIndex > testIndex && alphaIndex > tintIndex && alphaIndex > glowIndex);
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .5f,
                            ShaderRegistry.get("tint"), ShaderRegistry.getParameterLayout(tintIndex),
                            "u_tint", .75f, 128, 0, 0, 191);

                    Assert.assertTrue(projectDir.child("shaders/custom/material/alpha").deleteDirectory());
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    ShaderRegistry.saveProjectIndices();
                    Assert.assertEquals(-1, ShaderRegistry.indexOf("alpha"));
                    Assert.assertNull(ShaderRegistry.getByIdx(alphaIndex));
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("tint"),
                            ShaderRegistry.getParameterLayout(tintIndex), 32, "u_tint", .4f,
                            null, null, null, 0f, 0, 0, 0, 102);

                    ShaderRegistry.disposeAll();
                    ShaderRegistry.reloadForProject(projectDir, "shaders");
                    Assert.assertNull(ShaderRegistry.getByIdx(alphaIndex));
                    assertShaderReference("test", "u_gain", testMaterial, renderState, testSlot);
                    assertShaderReference("tint", "u_tint", tintMaterial, renderState, tintSlot);
                    transitionFrame(projection, ShaderRegistry.get("tint"),
                            ShaderRegistry.getParameterLayout(tintIndex), 0, "u_tint", .6f,
                            ShaderRegistry.get("test"), ShaderRegistry.getParameterLayout(testIndex),
                            "u_gain", .8f, 0, 153, 204, 0);
                    transitionFrame(projection, ShaderRegistry.get("test"),
                            ShaderRegistry.getParameterLayout(testIndex), 0, "u_gain", .3f,
                            null, null, null, 0f, 77, 0, 0, 0);
                } catch (Throwable ex) {
                    failure[0] = ex;
                } finally {
                    Gdx.app.exit();
                }
            }

            private void entity(float x, float red, float green, RenderStats stats) {
                Array<ShaderFloatParam> values = new Array<>();
                values.add(new ShaderFloatParam("red", red));
                values.add(new ShaderFloatParam("green", green));
                batch.setEntityParameters(values, stats);
                batch.draw(InternalTextures.whiteHandle(), x, 0, x, 32, x + 32, 32, x + 32, 0,
                        0, 0, 1, 1, stats);
            }

            private void assertRgb(int x, int red, int green) {
                ByteBuffer pixel = BufferUtils.newByteBuffer(4);
                Gdx.gl.glReadPixels(x, 16, 1, 1, GL20.GL_RGBA, GL20.GL_UNSIGNED_BYTE, pixel);
                Assert.assertTrue(Math.abs(red - (pixel.get(0) & 255)) <= 1);
                Assert.assertTrue(Math.abs(green - (pixel.get(1) & 255)) <= 1);
            }

            private void transitionFrame(Matrix4 projection, ShaderProgram firstShader,
                                         ShaderParameterLayout firstLayout, float firstX,
                                         String firstName, float firstValue,
                                         ShaderProgram secondShader, ShaderParameterLayout secondLayout,
                                         String secondName, float secondValue,
                                         int leftRed, int leftGreen, int rightRed, int rightGreen) {
                Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                RenderStats frame = new RenderStats();
                batch.begin(projection, frame);
                transitionEntity(firstShader, firstLayout, firstX, firstName, firstValue, frame);
                if (secondShader != null) {
                    transitionEntity(secondShader, secondLayout, 32, secondName, secondValue, frame);
                }
                batch.end(frame);
                Assert.assertEquals(secondShader == null ? 1 : 2, frame.drawCalls);
                if (secondShader != null) Assert.assertEquals(1, frame.flushStateChanges);
                assertRgb(16, leftRed, leftGreen);
                assertRgb(48, rightRed, rightGreen);
            }

            private void transitionEntity(ShaderProgram program, ShaderParameterLayout layout,
                                          float x, String name, float value, RenderStats frame) {
                batch.setShader(program, frame);
                batch.setParameterLayout(layout, frame);
                Array<ShaderFloatParam> values = new Array<>();
                values.add(new ShaderFloatParam(name, value));
                batch.setEntityParameters(values, frame);
                batch.draw(InternalTextures.whiteHandle(), x, 0, x, 32, x + 32, 32, x + 32, 0,
                        0, 0, 1, 1, frame);
            }

            private void writeProjectShader(String name, String parameter, String fragment) {
                FileHandle directory = projectDir.child("shaders/custom/material/" + name);
                directory.mkdirs();
                directory.child("shader.json").writeString("{\"name\":\"" + name
                        + "\",\"mode\":\"TEXTURE_ARRAY\",\"kind\":\"MATERIAL\",\"parameters\":{\""
                        + parameter + "\":0.0}}", false);
                directory.child("desktop-gl30.vert").writeString(
                        Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString(), false);
                directory.child("desktop-gl30.frag").writeString(fragment, false);
            }

            private void assertShaderReference(String name, String parameter,
                                               RenderMaterialComponent material,
                                               DynamicEntityRenderState state, int slot) {
                int index = ShaderRegistry.indexOf(name);
                Assert.assertEquals(index, material.shaderIdx);
                Assert.assertEquals(index, state.shader[slot]);
                Assert.assertSame(ShaderRegistry.get(name), ShaderRegistry.getByIdx(state.shader[slot]));
                Assert.assertEquals(name, ShaderRegistry.getParameterLayout(index).shaderName());
                Assert.assertEquals(0, ShaderRegistry.getParameterLayout(index).slot(parameter));
            }

            @Override public void dispose() {
                if (batch != null) batch.close();
                if (bundle != null) bundle.textureArray.dispose();
                if (shader != null) shader.dispose();
                if (gainShader != null) gainShader.dispose();
                if (boostShader != null) boostShader.dispose();
                ShaderRegistry.disposeAll();
                InternalTextures.dispose();
                TextureRegistry.clear();
                if (projectDir != null) projectDir.deleteDirectory();
            }
        }, config);
        if (failure[0] != null) throw new AssertionError("Studio GPU parameter smoke failed", failure[0]);
    }
}
