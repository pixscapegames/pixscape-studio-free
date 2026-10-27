package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfigurationBuilder;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.graphics.g2d.TextureAtlas;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.BufferUtils;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.GLCaps;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.*;
import games.pixscape.studio.batch.TextureArrayMeshBatchStudio;
import games.pixscape.studio.configuration.SceneMeta;
import org.junit.Assert;

/** Pixel oracle for the actual Studio submit system, including cold selection with previews. */
final class StudioForwardFallbackGlCheck {
    static void run() throws Exception {
        ShaderRegistry.initDefaults();
        Texture back=texture(.1f,.2f,.7f,1),front=texture(.7f,.1f,.2f,.35f);
        Texture first=texture(.1f,.8f,.2f,.6f),second=texture(.8f,.6f,.1f,.75f);
        Texture normal=texture(.8f,.5f,.9f,1);
        AtlasRuntimeService.TextureArrayBundle old=AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(back,front),1,1,GLCaps.detect());
        AtlasRuntimeService.TextureArrayBundle packed=AtlasRuntimeService.buildTextureArrayFromTextures(Array.with(back,front,first,second),1,1,GLCaps.detect());
        try {
            for(boolean normals:new boolean[]{true,false})for(boolean forward:new boolean[]{true,false}){
                try(Fixture f=new Fixture(old,first,forward,normals)){
                    var ctor=NormalMapBinding.class.getDeclaredConstructor(TextureAtlas.AtlasRegion.class,TextureAtlas.AtlasRegion.class);
                    ctor.setAccessible(true);
                    NormalMapBinding binding=ctor.newInstance(new TextureAtlas.AtlasRegion(first,0,0,1,1),new TextureAtlas.AtlasRegion(normal,0,0,1,1));
                    LightingSurface surface=surface();surface.normalMap=binding;
                    f.q.lighting.entities.put(f.entity,surface);f.q.lighting.tiles.put(0,surface);
                    LightingFrame.Light light=new LightingFrame.Light();light.x=0;light.y=0;light.z=128;
                    light.radius=512;light.falloff=1;light.intensity=.7f;light.r=1;light.g=.5f;light.b=.3f;light.resolution=256;
                    f.q.lighting.lights.add(light);
                    for(byte domain:new byte[]{FrameRenderQueue.SOURCE_ECS,FrameRenderQueue.SOURCE_TILED,FrameRenderQueue.SOURCE_VFX}){
                        int[] modes=domain==FrameRenderQueue.SOURCE_VFX?new int[]{0,1,2,3,4,7}:new int[]{0,1,2,7};
                        for(int mode:modes)for(boolean exempt:new boolean[]{false,true}){
                            fill(f,back,first,front,domain,mode,exempt);
                            Assert.assertThrows(LightingAdmissionException.class,()->ForwardAdmission.validateQueue(f.q,old,normals));
                            byte[] before=f.render(old);
                            Assert.assertEquals("The queue is never filtered or rewritten",3,f.q.size);
                            if(forward)Assert.assertTrue("Fallback must already draw",f.stats.drawnQuads>=3);
                            if(forward)Assert.assertEquals(1,f.submit.forwardRenderer().presentationDraws);
                            byte[] after=f.render(packed);
                            equal("packed/preview/packed domain="+domain+" blend="+mode+" normals="+normals+" forward="+forward,before,after);
                            if(forward){
                                long draws=f.stats.drawCalls,uploads=f.stats.bufferUploads;
                                f.render(packed);
                                Assert.assertEquals("Stable all-packed draws",draws,f.stats.drawCalls);
                                Assert.assertEquals("Stable all-packed uploads",uploads,f.stats.bufferUploads);
                            }
                            // A second frame texture can appear while the original bundle is still active.
                            fill(f,back,second,front,domain,mode,exempt);
                            byte[] changed=f.render(old);
                            equal("Second pending frame",changed,f.render(packed));
                            if(mode==1)Assert.assertFalse("Animation must visibly change before publication",java.util.Arrays.equals(before,changed));
                        }
                    }
                    // Shared shadow resources must survive packed/preview shader switches too.
                    fill(f,back,first,front,FrameRenderQueue.SOURCE_ECS,1,false);
                    light.quality=0;byte[] unshadowed=f.render(old);
                    ShadowExclusionIdentities identities=new ShadowExclusionIdentities();
                    surface.shadowGroupId=identities.idForActor(f.entity);
                    float[] blocker=surface.vertices.clone();
                    for(int v=0;v<blocker.length;v+=8){blocker[v]*=.5f;blocker[v+1]*=.5f;blocker[v+2]=64;}
                    LightingFrame.Caster caster=new LightingFrame.Caster();caster.surface=new LightingSurface(blocker);
                    caster.surface.shadowGroupId=surface.shadowGroupId;caster.texture=TextureRegistry.handleOf(first);
                    caster.u2=caster.v2=1;caster.bounds();f.q.lighting.casters.add(caster);
                    for(int quality:new int[]{1,2}){
                        light.quality=quality;caster.surface.shadowGroupId=surface.shadowGroupId;
                        equal("Own shadow excluded for a preview",unshadowed,f.render(old));
                        caster.surface.shadowGroupId=identities.idForActor(f.lightEntity);
                        byte[] occluded=f.render(old);
                        Assert.assertFalse("External caster must darken preview pixels",java.util.Arrays.equals(unshadowed,occluded));
                        long shadowDraws=f.stats.shadowDraws;
                        equal("Shared shadows across publication",occluded,f.render(packed));
                        Assert.assertEquals("Mixed routing does not replay the shadow pass",shadowDraws,f.stats.shadowDraws);
                    }
                    f.q.lighting.casters.clear();light.quality=0;
                    fill(f,back,first,front,FrameRenderQueue.SOURCE_ECS,1,false);
                    f.render(old);
                    f.q.textureHandle[1]=0;
                    if(forward)Assert.assertThrows(LightingAdmissionException.class,()->f.render(old));
                    f.q.textureHandle[1]=TextureRegistry.handleOf(first);
                    f.render(old); // Rejection does not poison the next valid frame.
                    // Cold toggling must also admit live previews (not only already admitted worlds).
                    f.meta.forwardLighting=false;f.submit.prepareComposition();
                    f.meta.forwardLighting=true;f.submit.prepareComposition();f.render(old);
                }
            }
        }finally{
            old.textureArray.dispose();packed.textureArray.dispose();
            for(Texture t:new Texture[]{back,front,first,second,normal}){t.getTextureData().consumePixmap().dispose();TextureRegistry.release(t);t.dispose();}
            ShaderRegistry.disposeAll();
        }
    }

    private static void fill(Fixture f,Texture back,Texture preview,Texture front,byte domain,int blend,boolean exempt){
        f.q.clear();add(f,back,FrameRenderQueue.SOURCE_VFX,0,0,64,0);
        add(f,preview,domain,blend,8,56,f.entity);f.q.ignoreAmbientLighting[1]=exempt;
        add(f,front,FrameRenderQueue.SOURCE_VFX,1,24,64,0);
    }
    private static void add(Fixture f,Texture texture,byte domain,int blend,float left,float right,int entity){
        f.q.addQuad(TextureRegistry.handleOf(texture),ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName()),blend,
                0,0,0,0,left,0,left,32,right,32,right,0,0,0,1,1,Color.toFloatBits(.8f,.7f,.9f,.8f),(byte)0,domain,0,entity);
    }
    private static Texture texture(float r,float g,float b,float a){
        Pixmap p=new Pixmap(1,1,Pixmap.Format.RGBA8888);p.setColor(r,g,b,a);p.fill();return new Texture(p);
    }
    private static LightingSurface surface(){
        float[] v=new float[48];int[] corners={0,1,2,0,2,3};float[] x={8,8,56,56},y={0,32,32,0};
        for(int i=0;i<6;i++){int c=corners[i],o=i*8;v[o]=LightingCoordinates.worldX(x[c],y[c],0);v[o+1]=LightingCoordinates.worldY(x[c],y[c],0);
            v[o+3]=c>=2?1:0;v[o+4]=c==1||c==2?1:0;v[o+7]=1;}
        LightingSurface s=new LightingSurface(v);s.forwardTopology=ForwardTopology.NATIVE;s.twoSided=true;return s;
    }
    private static void equal(String label,byte[] a,byte[] b){
        for(int i=0;i<a.length;i++)Assert.assertEquals(label+" byte="+i,a[i]&255,b[i]&255,1);
    }
    private static final class Fixture implements AutoCloseable {
        final FrameRenderQueue q=new FrameRenderQueue(8);
        final TextureArrayMeshBatchStudio batch=new TextureArrayMeshBatchStudio(64);
        final RenderStats stats=new RenderStats();
        final SceneMeta meta=new SceneMeta();
        final StudioRenderSubmitSystem submit;
        final World world;
        final int entity;
        final int lightEntity;
        Fixture(AtlasRuntimeService.TextureArrayBundle bundle,Texture preview,boolean forward,boolean normals){
            batch.setTextureArrayBundle(bundle);LayerStateSOA layers=new LayerStateSOA(1);layers.enabled[0]=true;
            OrthographicCamera cam=new OrthographicCamera(64,32);cam.position.set(32,16,0);cam.update();
            meta.forwardLighting=forward;meta.useNormalMaps=normals;meta.ambientMulR=.2f;meta.ambientMulG=.3f;meta.ambientMulB=.4f;
            submit=new StudioRenderSubmitSystem(layers,q,cam,batch,stats,new RenderStatsSink(100)).setSceneSettings(meta);
            world=new World(new WorldConfigurationBuilder().with(submit).build());
            entity=world.create();var material=world.getMapper(RenderMaterialComponent.class).create(entity);
            material.textureHandle=TextureRegistry.handleOf(preview);material.shaderIdx=ShaderRegistry.indexOf(ShaderMode.TEXTURE_ARRAY.defaultShaderName());material.blendModeId=1;
            lightEntity=world.create();var light=world.getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).create(lightEntity);
            light.shadowQuality=1;light.shadowResolution=256;
            world.process();
        }
        byte[] render(AtlasRuntimeService.TextureArrayBundle bundle){
            stats.reset();batch.setTextureArrayBundle(bundle);Gdx.gl.glViewport(0,0,64,32);submit.prepareComposition();
            submit.lightingRenderer().prepareFrame(q.lighting);
            Gdx.gl.glClearColor(.1f,.2f,.3f,.4f);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            submit.beginFrameSubmission();try{world.process();}finally{submit.endFrameSubmission();}
            var pixels=BufferUtils.newByteBuffer(64*32*4);Gdx.gl.glReadPixels(0,0,64,32,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixels);
            Assert.assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());byte[] result=new byte[pixels.capacity()];pixels.get(result);return result;
        }
        public void close(){world.dispose();batch.close();}
    }
}
