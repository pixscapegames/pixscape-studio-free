package games.pixscape.studio.system;

import com.artemis.Aspect;
import com.artemis.World;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.graphics.OrthographicCamera;
import com.badlogic.gdx.graphics.Texture;
import com.badlogic.gdx.graphics.glutils.ShaderProgram;
import games.pixscape.runtime.component.RenderMaterialComponent;
import games.pixscape.runtime.component.RenderRepeatComponent;
import games.pixscape.runtime.component.light.ConeLightComponent;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.performance.RenderStats;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.AtlasRuntimeService.TextureArrayBundle;
import games.pixscape.runtime.service.ShaderRegistry;
import games.pixscape.runtime.service.TextureRegistry;

/**
 * Studio's existing standalone preview, submitted with Forward receiver geometry and lighting.
 * Resource routing uses the same bundle/TextureRegistry contract as MeshBatchStudio in Legacy.
 * It does not depend on pack scheduling, own textures, or modify the published queue/bundle.
 */
final class StudioForwardLightingRenderer extends ForwardLightingRenderer {
    private TextureArrayBundle published;
    private ShaderProgram standalone;
    private boolean normalMaps=true,preparedNormals;
    private boolean hasStandalone;
    private DynamicEntityRenderState dynamic;
    private TiledMapRenderState tiled;
    private RenderStats stats;

    void setRenderDomains(DynamicEntityRenderState dynamic,TiledMapRenderState tiled){
        this.dynamic=dynamic;this.tiled=tiled;
    }

    @Override public void setUseNormalMaps(boolean enabled){super.setUseNormalMaps(enabled);normalMaps=enabled;}

    @Override public void prepare(World world,TextureArrayBundle bundle){
        super.prepare(world,bundle);
        published=bundle;
        if(standalone!=null&&preparedNormals!=normalMaps){standalone.dispose();standalone=null;}
        if(standalone==null){
            String version=Gdx.app.getType()==com.badlogic.gdx.Application.ApplicationType.Desktop
                    ?"#version 330 core\n":"#version 300 es\nprecision highp float;\nprecision highp int;\nprecision highp sampler2D;\n";
            ShaderProgram candidate=new ShaderProgram(vertexSource(version,normalMaps),
                    LightingShaderSources.forwardReceiver(version,normalMaps,
                            "uniform sampler2D u_colorTexture;","texture(u_colorTexture,uv)"));
            if(!candidate.isCompiled()){
                String log=candidate.getLog();candidate.dispose();
                throw new IllegalStateException("Studio Forward preview shader: "+log);
            }
            candidate.bind();candidate.setUniformi("u_colorTexture",0);
            candidate.setUniformi("u_shadow",3);candidate.setUniformi("u_shadowIdentity",4);candidate.setUniformi("u_shadowSecond",5);
            if(normalMaps)for(int i=0;i<4;i++)candidate.setUniformi("u_normal"+i,2);
            standalone=candidate;preparedNormals=normalMaps;
        }
    }

    /** Missing packed resources remain invalid in Runtime; Studio can resolve a live preview. */
    private static boolean standalone(int entity,int handle,TextureArrayBundle bundle){
        if(bundle.handle2layer.containsKey(handle)){
            ForwardAdmission.validateTexture(entity,handle,bundle);return false;
        }
        Texture texture=TextureRegistry.getByHandle(handle);
        if(handle==0||texture==null||texture.getTextureObjectHandle()==0)
            throw refused(entity,"texture handle "+handle+" is neither packed nor a live Studio preview");
        return true;
    }

    private static void material(int entity,int shader,int blend,boolean vfx,boolean preview){
        // Like Legacy's MeshBatchStudio path, preview ignores authored shader parameters.
        ForwardAdmission.validateMaterial(entity,preview?ShaderRegistry.indexOf(ShaderMode.TEXTURE_2D.defaultShaderName()):shader,blend,vfx);
    }

    private static void light(int entity,int shader,int blend){
        if(ShaderRegistry.getOrigin(shader)!=ShaderOrigin.CORE)throw refused(entity,"custom light shader");
        LightCompositionPass.validate(true,blend);
    }

    @Override protected void admitWorld(World world,TextureArrayBundle bundle){
        ForwardAdmission.validateBundle(bundle);
        var entities=world.getAspectSubscriptionManager().get(Aspect.all(RenderMaterialComponent.class)).getEntities();
        var materials=world.getMapper(RenderMaterialComponent.class);
        var repeats=world.getMapper(RenderRepeatComponent.class);
        var points=world.getMapper(PointLightComponent.class);
        var cones=world.getMapper(ConeLightComponent.class);
        for(int i=0;i<entities.size();i++){
            int e=entities.get(i);var m=materials.get(e);
            if(points.has(e)||cones.has(e)){light(e,m.shaderIdx,m.blendModeId);continue;}
            var repeat=repeats.getSafe(e,null);
            if(repeat!=null&&(repeat.repeatX||repeat.repeatY))throw refused(e,"repeat");
            boolean preview=m.textureHandle!=0&&standalone(e,m.textureHandle,bundle);
            material(e,m.shaderIdx,m.blendModeId,false,preview);
        }
        ActiveLights.validateForward(world);
        if(dynamic!=null)for(int slot=0;slot<dynamic.activeCount;slot++){
            int e=dynamic.renderSlotToEntityId[slot];if(e<0||!dynamic.enabled[slot]||dynamic.light[slot]!=0)continue;
            if(dynamic.repeatFlags[slot]!=0)throw refused(e,"repeat");
            material(e,dynamic.shader[slot],dynamic.blend[slot],false,standalone(e,dynamic.textureHandle[slot],bundle));
        }
        if(tiled!=null)for(int ref=0;ref<tiled.getRefCount();ref++){
            if(!tiled.enabled[ref])continue;
            LightingSurface s=tiled.lightingSurfaces.get(ref);int e=s==null?-1:s.ownerEntity;
            if(tiled.repeatFlags[ref]!=0)throw refused(e,"repeat at tiled ref "+ref);
            material(e,tiled.shader[ref],tiled.blend[ref],false,standalone(e,tiled.textureHandle[ref],bundle));
            ForwardAdmission.validateSurface(e,s,normalMaps);
        }
    }

    @Override protected void admitQueue(FrameRenderQueue q,TextureArrayBundle bundle,boolean normals){
        ForwardAdmission.validateBundle(bundle);
        if(q.lighting.diagnostic!=0)throw new LightingAdmissionException("Forward lighting requires normal rendering; select the reference for historical diagnostics");
        if(q.lighting.lights.size>1)throw new LightingAdmissionException("Forward lighting refuses "+q.lighting.lights.size+" active lights; maximum is one");
        hasStandalone=false;
        for(int i=0;i<q.size;i++){
            int e=q.sourceEntity[i];
            if(q.light[i]!=0){light(e,q.shader[i],q.blend[i]);continue;}
            boolean preview=standalone(e,q.textureHandle[i],bundle);hasStandalone|=preview;
            if(q.repeatFlags[i]!=0)throw refused(e,"repeat");
            material(e,q.shader[i],q.blend[i],q.sourceDomain[i]==FrameRenderQueue.SOURCE_VFX,preview);
            ForwardAdmission.validateSurface(e,surface(q,i),normals);
        }
    }

    @Override public void render(FrameRenderQueue q,LayerStateSOA layers,OrthographicCamera camera,
                                 LightingRenderer shadows,float r,float g,float b,RenderStats stats){
        this.stats=stats;
        super.render(q,layers,camera,shadows,r,g,b,stats);
    }

    @Override protected void renderEntries(FrameRenderQueue q,LayerStateSOA layers,OrthographicCamera camera,
                                           LightingRenderer shadows,float r,float g,float b){
        // Preserve the exact packed submission (batch boundaries/uploads) in the usual case.
        if(!hasStandalone){super.renderEntries(q,layers,camera,shadows,r,g,b);return;}
        int boundPreview=0;
        for(int i=0;i<q.size;i++){
            if(q.light[i]!=0)continue;
            int layer=q.layerIndex[i];
            if(layers.maxLayerIndex()>=0&&(layer<0||layer>layers.maxLayerIndex()||!layers.enabled[layer]))continue;
            int handle=q.textureHandle[i],colorLayer=published.handle2layer.get(handle,-1);
            if(colorLayer<0){
                if(boundPreview!=handle){
                    flush();
                    useReceiverShader(standalone,q,camera,shadows,r,g,b);
                    TextureRegistry.getByHandle(handle).bind(0);stats.textureBinds++;boundPreview=handle;
                }
            }else if(boundPreview!=0){
                flush();useReceiverShader(packedReceiverShader(),q,camera,shadows,r,g,b);
                published.textureArray.bind(0);stats.textureBinds++;boundPreview=0;
            }
            draw(q,i,surface(q,i),shadows,colorLayer);
        }
    }

    private static LightingSurface surface(FrameRenderQueue q,int i){
        return q.sourceDomain[i]==FrameRenderQueue.SOURCE_TILED?q.lighting.tiles.get(q.sourceSlot[i]):q.lighting.entities.get(q.sourceEntity[i]);
    }
    private static LightingAdmissionException refused(int entity,String reason){
        return new LightingAdmissionException("Studio Forward refused source entity "+entity+": "+reason);
    }
    @Override public void dispose(){
        super.dispose();if(standalone!=null)standalone.dispose();standalone=null;published=null;
    }
}
