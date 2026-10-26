package games.pixscape.studio.system;

import com.artemis.BaseSystem;
import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.ApplicationAdapter;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3Application;
import com.badlogic.gdx.backends.lwjgl3.Lwjgl3ApplicationConfiguration;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.render.LayerStateSOA;
import games.pixscape.runtime.render.ShaderMode;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.batch.performance.RenderStatsSink;
import games.pixscape.runtime.render.lighting.LightingCoordinates;
import games.pixscape.runtime.render.lighting.LightingFrame;
import games.pixscape.runtime.render.lighting.LightingSurface;
import games.pixscape.runtime.render.lighting.MapLightingGeometry;
import games.pixscape.runtime.service.AtlasRuntimeService;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;
import org.junit.Assert;
import org.junit.Test;

import java.nio.ByteBuffer;

public class StudioLightCompositionGlSmokeTest {
    @Test public void litFractionalEdgesAndIndependentLightsRestoreTarget() {
        final Throwable[] failure={null};
        Lwjgl3ApplicationConfiguration config=new Lwjgl3ApplicationConfiguration();
        config.setInitialVisible(false);config.setWindowedMode(768,576);config.disableAudio(true);
        config.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        new Lwjgl3Application(new ApplicationAdapter(){
            boolean interrupt;World world;games.pixscape.runtime.service.AtlasRuntimeService.TextureArrayBundle bundle;games.pixscape.studio.batch.TextureArrayMeshBatchStudio batch;Texture texture;
            @Override public void create(){try{
                games.pixscape.runtime.component.spatial.SpatialBlocksComponent linkedSnapshot=SpatialLinkGlChecks.run();
                SpatialGestureGlChecks.run();
                ShaderRegistry.initDefaults();
                games.pixscape.studio.configuration.ProjectConfig project=new games.pixscape.studio.configuration.ProjectConfig();
                games.pixscape.studio.configuration.SceneMeta meta=new games.pixscape.studio.configuration.SceneMeta("test","test.json");
                meta.ambientMulR=meta.ambientMulG=meta.ambientMulB=.2f;
                project.getScenesMap().put("test",meta);project.setCurrentSceneByName("test");
                games.pixscape.studio.configuration.ProjectConfig.setInstance(project);Gdx.gl.glViewport(0,0,64,32);
                Pixmap pix=new Pixmap(1,1,Pixmap.Format.RGBA8888);pix.setColor(.4f,.2f,.1f,.5f);pix.fill();
                texture=new Texture(pix);int handle=TextureRegistry.handleOf(texture);
                FrameRenderQueue q=new FrameRenderQueue(4);LayerStateSOA layers=new LayerStateSOA(1);
                layers.enabled[0]=true;
                OrthographicCamera camera=new OrthographicCamera(64,32);camera.position.set(32,16,0);camera.update();
                RenderStats stats=new RenderStats();batch=new games.pixscape.studio.batch.TextureArrayMeshBatchStudio(64);
                com.badlogic.gdx.utils.Array<Texture> textures=new com.badlogic.gdx.utils.Array<>();textures.add(texture);
                bundle=AtlasRuntimeService.buildTextureArrayFromTextures(textures,1,1,GLCaps.detect());pix.dispose();batch.setTextureArrayBundle(bundle);
                StudioRenderSubmitSystem submit=new StudioRenderSubmitSystem(layers,q,camera,batch,stats,new RenderStatsSink(100));
                world=new World(new WorldConfigurationBuilder().with(new BaseSystem(){protected void processSystem(){if(interrupt)throw new IllegalStateException("interrupted");}},submit).build());
                int entity=world.create();world.getMapper(PointLightComponent.class).create(entity);world.process();
                q.addQuad(handle,ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName()),1,0,0,0,0,
                        0,0,0,32,64,32,64,0,0,0,1,1,Color.WHITE.toFloatBits(),(byte)0,FrameRenderQueue.SOURCE_ECS,0,entity);
                float[] v=new float[48];int[] corner={0,1,2,0,2,3};float[] sx={0,0,64,64},sy={0,32,32,0};
                for(int i=0;i<6;i++){int c=corner[i],o=i*8;v[o]=LightingCoordinates.worldX(sx[c],sy[c],0);
                    v[o+1]=LightingCoordinates.worldY(sx[c],sy[c],0);v[o+3]=c>=2?1:0;v[o+4]=c==1||c==2?1:0;v[o+7]=1;}
                q.lighting.entities.put(entity,new LightingSurface(v));LightingFrame.Light light=new LightingFrame.Light();
                light.x=32;light.y=0;light.z=128;light.radius=512;light.falloff=2;light.intensity=1;
                light.r=light.g=light.b=1;light.quality=0;light.resolution=512;q.lighting.lights.add(light);
                submit.prepareComposition();
                world.process();Assert.assertEquals(0,stats.drawCalls);
                interrupt=true;submit.beginFrameSubmission();
                try{world.process();Assert.fail();}catch(IllegalStateException expected){}finally{submit.endFrameSubmission();}
                interrupt=false;world.process();Assert.assertEquals(0,stats.drawCalls);
                Gdx.gl.glClearColor(0,0,0,0);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
                ByteBuffer pixel=BufferUtils.newByteBuffer(4);Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                int one=pixel.get(0)&255;Assert.assertTrue("Partial alpha must receive light, red="+one,one>10);
                q.lighting.lights.add(light);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
                pixel.clear();Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                int two=pixel.get(0)&255;Assert.assertTrue("Independent light must increase contribution",two>one);
                light.quality=2;Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
                Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                Assert.assertEquals(128,pixel.get(3)&255,3);
                light.quality=0;q.lighting.lights.clear();q.lighting.lights.add(light);
                // Compare the real shadow of the adjusted junction to one continuous wall.
                games.pixscape.runtime.tiled.TiledMapLayerData linkMap=new games.pixscape.runtime.tiled.TiledMapLayerData(
                        12,12,8,4,4,games.pixscape.runtime.tiled.TiledProjection.ISO);
                for(games.pixscape.runtime.spatial.SpatialBlockData wall:linkedSnapshot.blocks)wall.shadowCaster=true;
                MapLightingGeometry geometry=new MapLightingGeometry();geometry.ensure(linkMap,linkedSnapshot);
                light.x=-12;light.y=-12;light.z=120;light.radius=512;light.intensity=3;light.quality=1;
                Pixmap wallPixmap=new Pixmap(1,1,Pixmap.Format.RGBA8888);wallPixmap.setColor(Color.WHITE);wallPixmap.fill();
                Texture wallTexture=new Texture(wallPixmap);wallPixmap.dispose();int wallHandle=TextureRegistry.handleOf(wallTexture);
                fillCasters(q.lighting,geometry,wallHandle);
                submit.prepareComposition();Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
                ByteBuffer joined=BufferUtils.newByteBuffer(64*32*4);Gdx.gl.glReadPixels(0,0,64,32,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,joined);
                games.pixscape.runtime.component.spatial.SpatialBlocksComponent continuous=new games.pixscape.runtime.component.spatial.SpatialBlocksComponent();
                games.pixscape.runtime.spatial.SpatialBlockData single=linkedSnapshot.blocks.first().copy();single.width=5;continuous.blocks.add(single);
                MapLightingGeometry baseline=new MapLightingGeometry();baseline.ensure(linkMap,continuous);fillCasters(q.lighting,baseline,wallHandle);
                submit.prepareComposition();Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
                ByteBuffer reference=BufferUtils.newByteBuffer(64*32*4);Gdx.gl.glReadPixels(0,0,64,32,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,reference);
                for(int i=0;i<joined.capacity();i++)Assert.assertEquals("No shadow seam at byte "+i,reference.get(i)&255,joined.get(i)&255,2);
                q.lighting.casters.clear();q.lighting.staticCasterCount=0;q.lighting.casterIndex.rebuild(q.lighting,0);
                submit.prepareComposition();Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
                ByteBuffer unshadowed=BufferUtils.newByteBuffer(64*32*4);Gdx.gl.glReadPixels(0,0,64,32,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,unshadowed);
                int shadowPixels=0;for(int i=0;i<joined.capacity();i+=4)if((unshadowed.get(i)&255)>(joined.get(i)&255)+2)shadowPixels++;
                Assert.assertTrue("The junction must cast a visible shadow, pixels="+shadowPixels,shadowPixels>5);
                wallTexture.dispose();
                light.x=32;light.y=0;light.z=128;light.radius=512;light.intensity=1;light.quality=0;
                Pixmap cutoutPixmap=new Pixmap(1,1,Pixmap.Format.RGBA8888);cutoutPixmap.setColor(.4f,.2f,.1f,.75f);cutoutPixmap.fill();
                Texture cutoutTexture=new Texture(cutoutPixmap);cutoutPixmap.dispose();
                for(int blend:new int[]{1,2,0,7}){
                    q.textureHandle[0]=blend==7?TextureRegistry.handleOf(cutoutTexture):handle;
                    q.blend[0]=blend;Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}pixel.clear();
                    Gdx.gl.glReadPixels(32,16,1,1,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixel);
                    Assert.assertEquals("Coverage/color rule for blend "+blend,blend==1?39:78,pixel.get(0)&255,3);
                    Assert.assertEquals("Alpha rule for blend "+blend,blend==0||blend==7?255:128,pixel.get(3)&255,3);
                }
                q.blend[0]=1;q.textureHandle[0]=handle;cutoutTexture.dispose();
                // Unsupported custom coverage fails explicitly, aborts, then permits a new frame.
                int originalShader=q.shader[0];
                com.badlogic.gdx.graphics.glutils.ShaderProgram custom=new com.badlogic.gdx.graphics.glutils.ShaderProgram(
                        "#version 330 core\nin vec2 a_position;uniform mat4 u_projTrans;void main(){gl_Position=u_projTrans*vec4(a_position,0,1);}",
                        "#version 330 core\nout vec4 c;void main(){c=vec4(.4,.2,.1,.5);}");
                Assert.assertTrue(custom.getLog(),custom.isCompiled());
                q.shader[0]=ShaderRegistry.register("lighting-unsupported-custom",custom,ShaderMode.MULTI_TEXTURE);
                com.badlogic.gdx.graphics.glutils.FrameBuffer nested=new com.badlogic.gdx.graphics.glutils.FrameBuffer(Pixmap.Format.RGBA8888,64,32,false);
                nested.begin();Gdx.gl.glViewport(2,3,60,28);Gdx.gl.glEnable(GL20.GL_SCISSOR_TEST);Gdx.gl.glScissor(4,5,52,20);
                submit.prepareComposition();
                submit.beginFrameSubmission();try{world.process();Assert.fail("Custom coverage needs an explicit adapter");}
                catch(IllegalArgumentException expected){Assert.assertTrue(expected.getMessage().contains("unsupported shader"));}finally{submit.endFrameSubmission();}
                java.nio.IntBuffer state=BufferUtils.newIntBuffer(4);
                Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);Assert.assertEquals(nested.getFramebufferHandle(),state.get(0));
                state.clear();Gdx.gl.glGetIntegerv(GL20.GL_VIEWPORT,state);Assert.assertEquals(2,state.get(0));Assert.assertEquals(60,state.get(2));
                Assert.assertTrue(Gdx.gl.glIsEnabled(GL20.GL_SCISSOR_TEST));
                q.shader[0]=originalShader;Gdx.gl.glDisable(GL20.GL_SCISSOR_TEST);Gdx.gl.glViewport(0,0,64,32);
                submit.prepareComposition();submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                nested.end();nested.dispose();
                world.dispose();world=null;batch.close();bundle.textureArray.dispose();texture.dispose();ShaderRegistry.disposeAll();
            }catch(Throwable error){failure[0]=error;}finally{Gdx.app.exit();}}
        },config);
        if(failure[0]!=null)throw new AssertionError("Real GL 2.5D test failed",failure[0]);
    }

    private static void fillCasters(LightingFrame frame,MapLightingGeometry geometry,int texture){
        frame.casters.clear();for(LightingSurface surface:geometry.casters){LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=surface;caster.texture=texture;caster.u2=caster.v2=1;caster.bounds();frame.casters.add(caster);}
        frame.staticCasterCount=frame.casters.size;frame.casterIndex.rebuild(frame,frame.staticCasterCount);
    }
}
