package games.pixscape.studio.system;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.service.*;
import org.junit.*;
import java.nio.ByteBuffer;

/** Opt-in real GL regression for ordered composition, not a saved-scene diagnostic. */
public class StudioLightCompositionGlSmokeTest {
    @Test public void orderedMasksPreserveAmbientColorsCoverageAndHdr() {
        final Throwable[] failure = {null};
        Lwjgl3ApplicationConfiguration config = new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false); config.setWindowedMode(64, 32); config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30, 3, 3);
        new Lwjgl3Application(new ApplicationAdapter() {
            boolean interruptBeforeSubmit;
            World world; games.pixscape.studio.batch.TextureArrayMeshBatchStudio batch; AtlasRuntimeService.TextureArrayBundle bundle;
            Texture cutoutTexture; int readX=40;
            @Override public void create() {
                try {
                    ShaderRegistry.initDefaults();
                    Gdx.gl.glViewport(0,0,64,32); // physical pixels, independent of Windows HiDPI window size
                    games.pixscape.studio.configuration.ProjectConfig config = new games.pixscape.studio.configuration.ProjectConfig();
                    games.pixscape.studio.configuration.SceneMeta meta = new games.pixscape.studio.configuration.SceneMeta("test", "test.json");
                    meta.ambientMulR=meta.ambientMulG=meta.ambientMulB=.2f;
                    config.getScenesMap().put("test",meta);config.setCurrentSceneByName("test");
                    games.pixscape.studio.configuration.ProjectConfig.setInstance(config);
                    Pixmap cutoutImage=new Pixmap(16,16,Pixmap.Format.RGBA8888);
                    cutoutImage.setColor(0,0,0,0);cutoutImage.fill();cutoutImage.setColor(.4f,.2f,.1f,1);cutoutImage.fillRectangle(8,0,8,16);
                    cutoutTexture=new Texture(cutoutImage);Array<Texture> textures=new Array<>();textures.add(cutoutTexture);
                    bundle = AtlasRuntimeService.buildTextureArrayFromTextures(textures, 16, 16, GLCaps.detect());
                    cutoutImage.dispose();
                    batch = new games.pixscape.studio.batch.TextureArrayMeshBatchStudio(64); batch.setTextureArrayBundle(bundle);
                    FrameRenderQueue queue = new FrameRenderQueue(16);
                    OrthographicCamera camera = new OrthographicCamera(64, 32);
                    camera.position.set(32, 16, 0); camera.update();
                    RenderStats stats = new RenderStats();
                    RenderStatsSink sink = new RenderStatsSink(1000);
                    StudioRenderSubmitSystem submit = new StudioRenderSubmitSystem(new LayerStateSOA(1), queue, camera,
                            batch, stats, sink);
                    world = new World(new WorldConfigurationBuilder().with(new BaseSystem() {
                        @Override protected void processSystem() {
                            if (interruptBeforeSubmit) throw new IllegalStateException("before submit");
                        }
                    }, submit).build());
                    // Scene activation processes ECS before the canvas has prepared any GPU targets.
                    world.process();
                    Assert.assertEquals(0, stats.drawCalls);
                    world.setDelta(.25f);
                    submit.prepareComposition(); // Resource preparation alone never grants submission.
                    world.process();
                    Assert.assertEquals(0f, ((Number)field(submit,"time")).floatValue(),0f);
                    Assert.assertEquals(0f, ((Number)field(sink,"acc")).floatValue(),0f);
                    interruptBeforeSubmit=true;
                    submit.beginFrameSubmission();
                    try { world.process(); Assert.fail("Expected interruption before submit"); }
                    catch (IllegalStateException expected) { Assert.assertEquals("before submit",expected.getMessage()); }
                    finally { submit.endFrameSubmission(); }
                    interruptBeforeSubmit=false;
                    world.process();
                    Assert.assertEquals(0,stats.drawCalls);
                    Assert.assertEquals(0f, ((Number)field(submit,"time")).floatValue(),0f);
                    Assert.assertEquals(0f, ((Number)field(sink,"acc")).floatValue(),0f);
                    int base = constant("composition-base", "vec4(.4,.2,.1,1.)");
                    int light = constant("composition-light", "vec4(.4,.2,.1,1.)");
                    int mask = constant("composition-mask", "vec4(.4,.2,.1,.5)");
                    for (int count : new int[]{0,1,2,4}) {
                        queue.clear(); entry(queue, base, BlendMode.ALPHA, false);
                        for (int i=0;i<count;i++) entry(queue, light, BlendMode.ADDITIVE, true);
                        pixel(submit, camera, stats, .4f*(.2f+count*.4f), .2f*(.2f+count*.2f), .1f*(.2f+count*.1f));
                        if (count>0) {
                            entry(queue, mask, BlendMode.ALPHA, false);
                            pixel(submit, camera, stats, .4f*(.2f+count*.2f), .2f*(.2f+count*.1f), .1f*(.2f+count*.05f));
                        }
                    }
                    // Prefix is not replayed; custom discard and deformed geometry use the same program twice.
                    int submittedDraws = stats.drawCalls;
                    float shaderTime=((Number)field(submit,"time")).floatValue();
                    int sinkDraws=((RenderStats)field(sink,"accStats")).drawCalls;
                    world.setDelta(.25f);world.process();
                    Assert.assertEquals(submittedDraws, stats.drawCalls);
                    Assert.assertEquals(shaderTime,((Number)field(submit,"time")).floatValue(),0f);
                    Assert.assertEquals(sinkDraws,((RenderStats)field(sink,"accStats")).drawCalls);
                    int discard = constant("composition-discard", "vec4(.4,.2,.1,1.)", "if(gl_FragCoord.x<32.)discard;");
                    queue.clear(); entry(queue, base, BlendMode.ALPHA, false); entry(queue, light, BlendMode.ADDITIVE, true);
                    entry(queue, discard, BlendMode.ALPHA, false);
                    pixel(submit, camera, stats, .08f, .04f, .02f);
                    Assert.assertEquals(5, stats.drawCalls); // two original + light/mask + composition
                    // Original additive images are not mistaken for lights or field occluders.
                    queue.clear(); entry(queue, base, BlendMode.ALPHA, false); entry(queue, light, BlendMode.ADDITIVE, false);
                    pixel(submit, camera, stats, .16f, .08f, .04f);
                    // Real procedural point/cone programs read the existing per-entity GPU table.
                    for (String name : new String[]{games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_POINTLIGHT,
                            games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_CONELIGHT}) {
                        queue.clear(); entry(queue,base,BlendMode.ALPHA,false);
                        entry(queue,ShaderRegistry.indexOf(name),BlendMode.ADDITIVE,true);
                        // Pixel (40,8): UV(.625,.75), d=sqrt(.25^2+.5^2). Cone points to (.25,.5).
                        int id=world.create(); queue.sourceEntity[1]=id;
                        games.pixscape.runtime.component.ShaderParamsComponent params=world.getMapper(games.pixscape.runtime.component.ShaderParamsComponent.class).create(id);
                        if(name.equals(games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_CONELIGHT)) {
                        params.floats.add(new games.pixscape.runtime.component.ShaderFloatParam("u_dirX",.25f));
                        params.floats.add(new games.pixscape.runtime.component.ShaderFloatParam("u_dirY",.5f));
                        }
                        params.floats.add(new games.pixscape.runtime.component.ShaderFloatParam("u_falloff",1f));
                        // Raster sample is at (40.5,8.5), rather than the mathematical pixel edge.
                        float attenuation=1f-(float)Math.sqrt(.265625f*.265625f+.46875f*.46875f);
                        games.pixscape.runtime.component.light.PointLightComponent point = null;
                        games.pixscape.runtime.component.light.ConeLightComponent cone = null;
                        if(name.equals(games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_POINTLIGHT))
                            point=world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(id);
                        else cone=world.getMapper(games.pixscape.runtime.component.light.ConeLightComponent.class).create(id);
                        queue.colorPacked[1]=Color.toFloatBits(.25f,.5f,.75f,1f);
                        for(BlendMode lightBlend:new BlendMode[]{BlendMode.ADDITIVE,BlendMode.ADDITIVE_ALPHA}) {
                            queue.blend[1]=lightBlend.id;
                            float energy=lightBlend==BlendMode.ADDITIVE_ALPHA?attenuation*attenuation:attenuation;
                            for(float intensity:new float[]{0f,.25f,1f,3f}) {
                                if(point!=null)point.intensity=intensity;else cone.intensity=intensity;
                                pixel(submit,camera,stats,.4f*(.2f+.25f*intensity*energy),
                                        .2f*(.2f+.5f*intensity*energy),.1f*(.2f+.75f*intensity*energy));
                            }
                        }
                    }
                    // Premultiplied, opaque and cutout masks preserve ambient at full coverage.
                    int opaque=constant("composition-opaque", "vec4(.4,.2,.1,.5)", "", true);
                    for(BlendMode blend:new BlendMode[]{BlendMode.OPAQUE,BlendMode.CUTOUT}) {
                        queue.clear();entry(queue,base,BlendMode.ALPHA,false);entry(queue,light,BlendMode.ADDITIVE,true);
                        entry(queue,opaque,blend,false);pixel(submit,camera,stats,.08f,.04f,.02f);
                    }
                    queue.clear();entry(queue,base,BlendMode.ALPHA,false);entry(queue,light,BlendMode.ADDITIVE,true);
                    entry(queue,ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName()),BlendMode.CUTOUT,false);
                    queue.textureHandle[2]=TextureRegistry.handleOf(cutoutTexture);
                    pixel(submit,camera,stats,.08f,.04f,.02f);readX=20;
                    pixel(submit,camera,stats,.24f,.08f,.03f);readX=40;
                    // Exactly the same holed image outside the atlas must preserve prior light in its holes.
                    Pixmap holes=new Pixmap(16,16,Pixmap.Format.RGBA8888);
                    holes.setColor(0,0,0,0);holes.fill();holes.setColor(.4f,.2f,.1f,1);holes.fillRectangle(8,0,8,16);
                    Texture standaloneHoles=new Texture(holes);holes.dispose();
                    Pixmap alphaImage=new Pixmap(16,16,Pixmap.Format.RGBA8888);
                    alphaImage.setColor(.4f,.2f,.1f,.25f);alphaImage.fill();Texture alphaTexture=new Texture(alphaImage);alphaImage.dispose();
                    Pixmap premultImage=new Pixmap(16,16,Pixmap.Format.RGBA8888);
                    premultImage.setColor(.1f,.05f,.025f,.25f);premultImage.fill();Texture standalonePremult=new Texture(premultImage);premultImage.dispose();
                    try {
                        queue.textureHandle[2]=TextureRegistry.handleOf(standaloneHoles);
                        pixel(submit,camera,stats,.08f,.04f,.02f);readX=20;
                        pixel(submit,camera,stats,.24f,.08f,.03f);
                        entry(queue,base,BlendMode.ALPHA,false);queue.textureHandle[3]=TextureRegistry.handleOf(alphaTexture);
                        // CUTOUT -> ALPHA with the same standalone program must clear the threshold.
                        pixel(submit,camera,stats,.20f,.07f,.0275f);readX=40;
                        pixel(submit,camera,stats,.08f,.04f,.02f);
                        queue.clear();entry(queue,base,BlendMode.ALPHA,false);entry(queue,light,BlendMode.ADDITIVE,true);
                        entry(queue,base,BlendMode.PREMULT_ALPHA,false);queue.textureHandle[2]=TextureRegistry.handleOf(standalonePremult);
                        pixel(submit,camera,stats,.20f,.07f,.0275f);
                        queue.blend[2]=BlendMode.OPAQUE.id;queue.textureHandle[2]=TextureRegistry.handleOf(alphaTexture);
                        pixel(submit,camera,stats,.08f,.04f,.02f);
                    } finally {standaloneHoles.dispose();alphaTexture.dispose();standalonePremult.dispose();}
                    int premult=constant("composition-premult", "vec4(.2,.1,.05,.5)");
                    queue.clear();entry(queue,base,BlendMode.ALPHA,false);entry(queue,light,BlendMode.ADDITIVE,true);entry(queue,premult,BlendMode.PREMULT_ALPHA,false);
                    pixel(submit,camera,stats,.16f,.06f,.025f);
                    // Custom animated alpha and vertex deformation are replayed, with the same numeric row.
                    String vertex=Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString().replace("vec4(a_position, 0.0, 1.0)", "vec4(a_position+vec2(8.,0.), 0.0, 1.0)");
                    String fragment="#version 330 core\nflat in int v_paramId;uniform sampler2D u_entityParams;out vec4 fragColor;void main(){float a=texelFetch(u_entityParams,ivec2(0,v_paramId),0).r;if(a<0.)discard;fragColor=vec4(.4,.2,.1,a);}";
                    ShaderProgram animated=new ShaderProgram(vertex,fragment);Assert.assertTrue(animated.getLog(),animated.isCompiled());
                    int animatedId=ShaderRegistry.register("composition-animated",animated,ShaderMode.TEXTURE_ARRAY);
                    Array<games.pixscape.runtime.component.ShaderFloatParam> defaults=new Array<>();defaults.add(new games.pixscape.runtime.component.ShaderFloatParam("alpha",.5f));
                    ShaderRegistry.registerParameterLayout("composition-animated",defaults);
                    int entity=world.create();games.pixscape.runtime.component.ShaderParamsComponent params=world.getMapper(games.pixscape.runtime.component.ShaderParamsComponent.class).create(entity);
                    params.floats.add(new games.pixscape.runtime.component.ShaderFloatParam("alpha",.5f));
                    queue.clear();entry(queue,base,BlendMode.ALPHA,false);entry(queue,light,BlendMode.ADDITIVE_ALPHA,true);entry(queue,animatedId,BlendMode.ALPHA,false);queue.sourceEntity[2]=entity;
                    pixel(submit,camera,stats,.16f,.06f,.025f);
                    params.floats.get(0).value=-1f;pixel(submit,camera,stats,.24f,.08f,.03f);
                    // Preserve the background, also when the caller owns a nested framebuffer.
                    com.badlogic.gdx.graphics.glutils.FrameBuffer nested=new com.badlogic.gdx.graphics.glutils.FrameBuffer(Pixmap.Format.RGBA8888,64,32,false);
                    try {
                        nested.begin();queue.clear();pixel(submit,camera,stats,.25f,.5f,.75f,.25f,.5f,.75f);
                        entry(queue,mask,BlendMode.ALPHA,false);pixel(submit,camera,stats,.165f,.27f,.385f,.25f,.5f,.75f);
                    } finally { nested.end(); nested.dispose();Gdx.gl.glViewport(0,0,64,32); }
                    com.badlogic.gdx.graphics.glutils.FrameBuffer failureTarget=new com.badlogic.gdx.graphics.glutils.FrameBuffer(Pixmap.Format.RGBA8888,64,32,false);
                    try {
                        failureTarget.begin();Gdx.gl.glViewport(2,3,60,28);
                        Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(4,5,52,20);
                        int first=world.create(),second=world.create();
                        games.pixscape.runtime.component.light.PointLightComponent valid=world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(first);
                        games.pixscape.runtime.component.light.PointLightComponent invalid=world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(second);
                        valid.intensity=1f;invalid.intensity=Float.NaN;
                        queue.clear();entry(queue,base,BlendMode.ALPHA,false);
                        int pointShader=ShaderRegistry.indexOf(games.pixscape.runtime.helper.RuntimeFs.TEXTURE_ARRAY_POINTLIGHT);
                        entry(queue,pointShader,BlendMode.ADDITIVE,true);queue.sourceEntity[1]=first;
                        entry(queue,pointShader,BlendMode.ADDITIVE,true);queue.sourceEntity[2]=second;
                        submit.prepareComposition();submit.beginFrameSubmission();
                        try {world.process();Assert.fail("Invalid light must fail after pending first light");}
                        catch(IllegalArgumentException expected) {Assert.assertTrue(expected.getMessage(),expected.getMessage().contains("intensity"));}
                        finally {submit.endFrameSubmission();}
                        Assert.assertEquals(0,((Number)field(batch,"quadCount")).intValue());
                        Assert.assertEquals(0,((Number)field(batch,"vertCount")).intValue());
                        Assert.assertEquals(false,field(batch,"drawing"));Assert.assertNull(field(batch,"shader"));
                        java.nio.IntBuffer state=BufferUtils.newIntBuffer(4);
                        Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);Assert.assertEquals(failureTarget.getFramebufferHandle(),state.get(0));
                        Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(2,state.get(0));Assert.assertEquals(3,state.get(1));Assert.assertEquals(60,state.get(2));Assert.assertEquals(28,state.get(3));
                        Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                        Gdx.gl.glGetIntegerv(GL20.GL_SCISSOR_BOX,state);Assert.assertEquals(4,state.get(0));Assert.assertEquals(5,state.get(1));Assert.assertEquals(52,state.get(2));Assert.assertEquals(20,state.get(3));
                        Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glViewport(0,0,64,32);
                        ShaderParameterRows rows=(ShaderParameterRows)field(batch,"parameterRows");
                        Assert.assertEquals(1,rows.rowCount());Assert.assertEquals(0,rows.currentId());
                        valid.intensity=2f;invalid.intensity=0f;
                        float recoveredEnergy=2f*(float)Math.pow(1f-Math.sqrt(.265625f*.265625f+.46875f*.46875f),1.5);
                        pixel(submit,camera,stats,.4f*(.2f+recoveredEnergy),.2f*(.2f+recoveredEnergy),.1f*(.2f+recoveredEnergy));
                    } finally {failureTarget.end();failureTarget.dispose();Gdx.gl.glViewport(0,0,64,32);Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);}
                    // Explicit abort of each real fallback batch must discard CPU geometry without a draw.
                    MetricsBatch[] fallbacks=new MetricsBatch[]{new games.pixscape.studio.batch.MeshBatchStudio(64),new games.pixscape.studio.batch.MultiTextureMeshBatchStudio(64)};
                    try {
                        for(int i=0;i<fallbacks.length;i++) {
                            MetricsBatch fallback=fallbacks[i];int calls=stats.drawCalls;
                            fallback.begin(camera.combined,stats);
                            fallback.setShader(ShaderRegistry.get(i==0?ShaderMode.TEXTURE_2D.defaultShaderName():ShaderMode.MULTI_TEXTURE.defaultShaderName()),stats);
                            fallback.setColor(1,1,1,1);
                            fallback.draw(TextureRegistry.handleOf(cutoutTexture),0,0,0,32,64,32,64,0,0,0,1,1,stats);
                            Assert.assertTrue(((Number)field(fallback,"quadCount")).intValue()>0);
                            fallback.abort();fallback.end(stats);
                            Assert.assertEquals(calls,stats.drawCalls);Assert.assertNull(field(fallback,"shader"));
                            fallback.begin(camera.combined,stats);
                            fallback.setShader(ShaderRegistry.get(i==0?ShaderMode.TEXTURE_2D.defaultShaderName():ShaderMode.MULTI_TEXTURE.defaultShaderName()),stats);
                            fallback.draw(TextureRegistry.handleOf(cutoutTexture),0,0,0,32,64,32,64,0,0,0,1,1,stats);fallback.end(stats);
                            Assert.assertEquals(calls+1,stats.drawCalls);
                        }
                    } finally {for(MetricsBatch fallback:fallbacks)fallback.close();}
                    // Repeated world quads remain visible with the camera moved; all copies use original UVs.
                    queue.clear();entry(queue,base,BlendMode.ALPHA,false);queue.x3[0]=queue.x4[0]=8;queue.y2[0]=queue.y3[0]=8;
                    queue.repeatFlags[0]=RenderRepeatFlags.ANY;camera.position.x+=16;camera.update();
                    pixel(submit,camera,stats,.08f,.04f,.02f);
                    // Studio's unpacked 2D fallback stays between atlas submissions, with the same alpha.
                    Pixmap image=new Pixmap(16,16,Pixmap.Format.RGBA8888);image.setColor(.4f,.2f,.1f,.5f);image.fill();Texture unpacked=new Texture(image);image.dispose();
                    try {
                        queue.clear();entry(queue,base,BlendMode.ALPHA,false);entry(queue,light,BlendMode.ADDITIVE,true);entry(queue,base,BlendMode.ALPHA,false);
                        queue.textureHandle[2]=TextureRegistry.handleOf(unpacked);
                        pixel(submit,camera,stats,.16f,.06f,.025f);
                        entry(queue,base,BlendMode.ALPHA,false);pixel(submit,camera,stats,.08f,.04f,.02f);
                    } finally {unpacked.dispose();}
                    // Camera and physical target changes are applied before processing the world.
                    Gdx.gl.glViewport(0,0,32,16); camera.viewportWidth=32; camera.viewportHeight=16; camera.position.set(16,8,0); camera.update();
                    queue.clear(); entry(queue, base, BlendMode.ALPHA, false);
                    pixel(submit, camera, stats, .08f, .04f, .02f);
                    System.out.println("RGBA16F composition verified on "+Gdx.gl.glGetString(GL20.GL_RENDERER));
                } catch (Throwable t) { failure[0]=t; }
                finally { Gdx.app.exit(); }
            }
            Object field(Object object,String name) {
                try {java.lang.reflect.Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}
                catch(ReflectiveOperationException e){throw new AssertionError(e);}
            }
            int constant(String name, String value) { return constant(name,value,""); }
            int constant(String name, String value, String before) { return constant(name,value,before,false); }
            int constant(String name, String value, String before, boolean coverage) {
                String vertex=Gdx.files.classpath("shaders/core/desktop-gl30/texture-array.vert").readString();
                ShaderProgram shader=new ShaderProgram(vertex,"#version 330 core\n"+(coverage?"uniform float u_worldCoverage;":"")+"out vec4 fragColor;void main(){"+before+"fragColor="+value+";"+(coverage?"if(u_worldCoverage>.5)fragColor.a=1.;":"")+"}");
                Assert.assertTrue(shader.getLog(),shader.isCompiled());
                return ShaderRegistry.register(name,shader,ShaderMode.TEXTURE_ARRAY);
            }
            void entry(FrameRenderQueue q,int shader,BlendMode blend,boolean light) {
                q.addQuad(InternalTextures.whiteHandle(),shader,blend.id,0,0,0,0,
                        0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_VFX,0,-1);
                q.light[q.size-1]=(byte)(light?1:0);
            }
            void pixel(StudioRenderSubmitSystem submit,OrthographicCamera camera,RenderStats stats,float r,float g,float b) {
                pixel(submit,camera,stats,r,g,b,0,0,0);
            }
            void pixel(StudioRenderSubmitSystem submit,OrthographicCamera camera,RenderStats stats,float r,float g,float b,float backgroundR,float backgroundG,float backgroundB) {
                stats.reset(); Gdx.gl.glClearColor(backgroundR,backgroundG,backgroundB,1); Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                submit.prepareComposition(); world.setDelta(0); submit.beginFrameSubmission();
                try {world.process();} finally {submit.endFrameSubmission();}
                ByteBuffer pixel=BufferUtils.newByteBuffer(4); Gdx.gl.glReadPixels(20,8,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                // Right hand pixel is used by the discard case on the full viewport.
                if (camera.viewportWidth==64) Gdx.gl.glReadPixels(readX,8,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                Assert.assertEquals(r*255,pixel.get(0)&255,3); Assert.assertEquals(g*255,pixel.get(1)&255,3); Assert.assertEquals(b*255,pixel.get(2)&255,3);
                Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
            }
            @Override public void dispose() {
                if(world!=null)world.dispose(); if(batch!=null)batch.close(); if(bundle!=null)bundle.textureArray.dispose();if(cutoutTexture!=null)cutoutTexture.dispose();
            }
        },config);
        if(failure[0]!=null)throw new AssertionError(failure[0]);
    }
}
