package games.pixscape.studio.ui.preview;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.component.light.PointLightComponent;
import games.pixscape.runtime.render.*;
import games.pixscape.runtime.render.batch.*;
import games.pixscape.runtime.render.batch.performance.*;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.RenderSubmitSystem;

/** Isolated frozen-frame replay: no project loader, animation, physics, scripts or preferences. */
public final class FrozenPoseReplay extends ApplicationAdapter {
    private final FileHandle file;
    private final FrozenPreviewPose pose;
    private World world;
    private RenderSubmitSystem submit;
    private MetricsBatch batch;
    private AtlasRuntimeService.TextureArrayBundle bundle;
    private final Array<Texture> owned=new Array<>();
    private FileHandle output;
    private int mode;
    private boolean capture=true;
    private int frame;
    private float diagnosticLightX,diagnosticLightY;
    private final Array<LightingSurface> skinSurfaces=new Array<>();
    private final Array<float[]> skinOriginals=new Array<>();
    private final IntArray skinCounts=new IntArray();
    private final boolean scaffoldOrbit=Boolean.getBoolean("pixscape.diagnostic.scaffoldOrbit");
    FrozenPoseReplay(FileHandle file,FrozenPreviewPose pose){this.file=file;this.pose=pose;}
    public static void main(String[] args){
        System.out.println("FrozenPoseReplay pid="+ProcessHandle.current().pid()+" java="+System.getProperty("java.home")
                +" runtime="+LightingSurface.class.getProtectionDomain().getCodeSource().getLocation()
                +" studio="+FrozenPoseReplay.class.getProtectionDomain().getCodeSource().getLocation());
        if(args.length!=1)throw new IllegalArgumentException("Passer le chemin absolu de pose.json");
        FileHandle file=new FileHandle(args[0]);
        JsonValue recorded=new JsonReader().parse(file);
        discardEmptyRetiredActorField(recorded);
        FrozenPreviewPose pose=FrozenPreviewPose.codec().readValue(FrozenPreviewPose.class,recorded);
        if(System.getProperty("pixscape.diagnostic.renderer")!=null)
            pose.forward="forward".equals(System.getProperty("pixscape.diagnostic.renderer"));
        String candidateScene=System.getProperty("pixscape.diagnostic.wallGroupScene");
        if(candidateScene!=null)System.out.print(CapturedWallGroups.apply(pose,new FileHandle(candidateScene)));
        if(pose.version!=1||!file.parent().child("READY").exists())throw new IllegalArgumentException("Capture incomplète/incompatible");
        ProcessHandle.of(pose.previewPid).filter(ProcessHandle::isAlive).ifPresent(process->{
            if(pose.previewStarted==null||pose.previewStarted.isEmpty()||process.info().startInstant().map(Object::toString).orElse("").equals(pose.previewStarted))
                throw new IllegalStateException("Fermer la Preview source avant le rejeu (PID "+pose.previewPid+"). Aucun processus n'a été arrêté.");
        });
        Lwjgl3ApplicationConfiguration cfg=new Lwjgl3ApplicationConfiguration();cfg.disableAudio(true);
        cfg.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,3);
        cfg.setWindowedMode(pose.width,pose.height);cfg.setResizable(false);cfg.setHdpiMode(com.badlogic.gdx.graphics.glutils.HdpiMode.Pixels);
        cfg.setBackBufferConfig(8,8,8,8,16,0,pose.samples);
        cfg.useVsync(true);cfg.setForegroundFPS(30);cfg.setTitle("Pose figée — 0: rendu ; 1..7: diagnostics ; clic: pixel ; Échap: fermer");
        new Lwjgl3Application(new FrozenPoseReplay(file,pose),cfg);
    }
    /** Diagnostic archive adapter only; drops only null/inactive retired state, never an active caster. */
    private static void discardEmptyRetiredActorField(JsonValue node){
        if(node.isObject()&&node.has("actorShadow")){
            if(!node.get("actorShadow").isNull()&&node.get("actorShadow").getBoolean("active",false))throw new IllegalArgumentException("Historical actor geometry requires an explicit replay adapter");
            node.remove("actorShadow");
        }
        for(JsonValue child=node.child;child!=null;child=child.next)discardEmptyRetiredActorField(child);
    }
    @Override public void create(){
        try {
            String outputPath=System.getProperty("pixscape.diagnostic.replayOutput");
            output=outputPath==null?file.parent().child("replay-"+java.time.Instant.now().toString().replace(':','-')):new FileHandle(outputPath);
            if(outputPath!=null&&output.exists())throw new IllegalArgumentException("Diagnostic output already exists");
            output.mkdirs();
            if(pose.queue.lighting.lights.size>0){diagnosticLightX=pose.queue.lighting.lights.first().x;diagnosticLightY=pose.queue.lighting.lights.first().y;}
            for(var p:pose.properties)if(p.key.startsWith("pixscape.poc.")&&!p.key.contains("benchmark")&&!p.key.contains("Capture")&&!p.key.contains("freeze"))System.setProperty(p.key,p.value);
            System.clearProperty("pixscape.poc.lightingDiagnostic");System.clearProperty("pixscape.poc.forceShadowVisibility");
            ShaderRegistry.initDefaults();InternalTextures.initIfNeeded();
            IntIntMap handles=new IntIntMap();Array<Texture> colors=new Array<>();IntSet colorHandles=new IntSet();
            for(int i=0;i<pose.queue.size;i++)colorHandles.add(pose.queue.textureHandle[i]);
            int maxW=1,maxH=1;
            for(var info:pose.textures){
                if(info.handle==TextureRegistry.WHITE_HANDLE){handles.put(info.handle,InternalTextures.whiteHandle());continue;}
                Texture t=new Texture(file.parent().child(info.file),Texture.TextureFilter.valueOf(info.min).isMipMap());owned.add(t);
                t.setFilter(Texture.TextureFilter.valueOf(info.min),Texture.TextureFilter.valueOf(info.mag));t.setWrap(Texture.TextureWrap.valueOf(info.u),Texture.TextureWrap.valueOf(info.v));
                handles.put(info.handle,TextureRegistry.handleOf(t));
                if(colorHandles.contains(info.handle)){colors.add(t);maxW=Math.max(maxW,t.getWidth());maxH=Math.max(maxH,t.getHeight());}
            }
            var q=pose.queue;
            if(scaffoldOrbit)for(var caster:q.lighting.casters){var s=caster.surface;
                if(s.pocSourceBlockId==29){float[] source=s.shadowVertices==null?s.vertices:s.shadowVertices;int count=s.shadowVertices==null?s.vertices.length/8:s.shadowVertexCount;
                    skinSurfaces.add(s);skinOriginals.add(source.clone());skinCounts.add(count);s.shadowVertices=source.clone();s.shadowVertexCount=count;
                    System.out.println("SCAFFOLD ref="+caster.tiledRef+" group="+s.shadowGroupId+" vertices="+count);
                }
            }
            if(scaffoldOrbit&&skinSurfaces.size!=3)throw new IllegalStateException("Expected all three scaffold contributions");
            if(Boolean.getBoolean("pixscape.diagnostic.actorSkinSweep"))for(var caster:q.lighting.casters){
                var s=caster.surface;
                if(s.actor && s.shadowVertices!=null && !skinSurfaces.contains(s,true)){
                    skinSurfaces.add(s);skinOriginals.add(s.shadowVertices.clone());skinCounts.add(s.shadowVertexCount);
                    System.out.println("ACTOR_SKIN group="+s.shadowGroupId+" vertices="+s.shadowVertexCount);
                }
            }
            for(int i=0;i<q.size;i++){q.textureHandle[i]=mapped(handles,q.textureHandle[i]);String name=pose.shaders.get(q.shader[i]);if(name==null)throw new IllegalStateException("Shader non enregistré");q.shader[i]=ShaderRegistry.indexOf(name);}
            for(var e:q.lighting.tiles)remap(e.value,handles);
            for(var e:q.lighting.entities)remap(e.value,handles);
            for(var c:q.lighting.casters){c.texture=mapped(handles,c.texture);remap(c.surface,handles);}
            q.lighting.casterIndex.rebuild(q.lighting,q.lighting.staticCasterCount);
            OrthographicCamera camera=new OrthographicCamera();camera.position.set(pose.cameraPosition);camera.direction.set(pose.cameraDirection);camera.up.set(pose.cameraUp);
            camera.viewportWidth=pose.viewportWidth;camera.viewportHeight=pose.viewportHeight;camera.zoom=pose.zoom;camera.near=pose.near;camera.far=pose.far;camera.update();
            if(scaffoldOrbit){camera.position.set(-1300,3850,0);camera.zoom=1.5f;camera.update();}
            batch=pose.arrayBatch?new TextureArrayMeshBatch(4096):new MultiTextureMeshBatch(4096);
            if(pose.arrayBatch){bundle=AtlasRuntimeService.buildTextureArrayFromTextures(colors,maxW,maxH,GLCaps.detect());bundle.textureArray.setFilter(Texture.TextureFilter.valueOf(pose.arrayMin),Texture.TextureFilter.valueOf(pose.arrayMag));((TextureArrayMeshBatch)batch).setTextureArrayBundle(bundle);}
            submit=new RenderSubmitSystem(pose.layers,q,camera,pose.ambient[0],pose.ambient[1],pose.ambient[2],batch,new RenderStats(),new RenderStatsSink(100),pose.forward);
            if(submit.forwardRenderer()!=null)submit.forwardRenderer().setRoundedLightingEnabled(pose.roundedLighting);
            world=new World(new WorldConfigurationBuilder().with(submit).build());submit.setEnabled(false);
            int max=0;for(var e:pose.lightIntensities)max=Math.max(max,e.key);if(max>100000)throw new IllegalStateException("Identifiant hors borne");
            for(int i=0;i<=max;i++)world.create();for(var e:pose.lightIntensities){var intensity=world.getMapper(PointLightComponent.class).create(e.key);intensity.intensity=e.value;intensity.enabled=false;}
            for(var l:q.lighting.lights){var component=world.getMapper(PointLightComponent.class).create(world.create());component.shadowQuality=l.quality;component.shadowResolution=l.resolution;}
            world.setDelta(0);world.process();set(submit,"time",pose.time);submit.prepareComposition();submit.lightingRenderer().prepareFrame(q.lighting);submit.setEnabled(true);
            Gdx.input.setInputProcessor(new InputAdapter(){
                @Override public boolean keyDown(int key){
                    if(key==Input.Keys.ESCAPE){Gdx.app.exit();return true;}
                    if(key>=Input.Keys.NUM_0&&key<=Input.Keys.NUM_7){int next=key-Input.Keys.NUM_0;if(next==mode)return true;mode=next;capture=true;return true;}return false;
                }
                @Override public boolean touchDown(int x,int y,int pointer,int button){
                    output.child("pixels.tsv").writeString(mode+"\t"+x+"\t"+y+"\n",true,"UTF-8");Gdx.graphics.setTitle("Pose figée — pixel ("+x+","+y+") enregistré, mode "+mode);return true;
                }
            });
        }catch(Exception e){if(output!=null)output.child("FAILED.txt").writeString(e.toString(),false);Gdx.app.error("FrozenPoseReplay","Rejeu refusé",e);Gdx.app.exit();}
    }
    private static int mapped(IntIntMap ids,int id){if(id==0)return 0;int result=ids.get(id,0);if(result==0)throw new IllegalStateException("Texture manquante "+id);return result;}
    private static void remap(LightingSurface s,IntIntMap ids){if(s.normalMap!=null)set(s.normalMap,"textureHandle",mapped(ids,s.normalMap.textureHandle));}
    private static void set(Object o,String name,Object value){try{var f=o.getClass().getDeclaredField(name);f.setAccessible(true);f.set(o,value);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}}
    @Override public void render(){
        if(world==null||submit==null)return;
        if(Gdx.graphics.getBackBufferWidth()!=pose.width||Gdx.graphics.getBackBufferHeight()!=pose.height){Gdx.app.error("FrozenPoseReplay","Dimensions de rejeu différentes : comparaison exacte refusée");Gdx.app.exit();return;}
        boolean sweep=Boolean.getBoolean("pixscape.diagnostic.groupSweep");
        if(sweep){
            if(frame>=8){Gdx.app.exit();return;}
            mode=frame==0||frame>=4?0:frame+4;capture=true;
            if(frame>=4&&pose.queue.lighting.lights.size>0){var light=pose.queue.lighting.lights.first();
                light.x=diagnosticLightX+(frame==4?-2:frame==5?2:0);
                light.y=diagnosticLightY+(frame==6?-2:frame==7?2:0);}
        }
        String stem="mode-"+mode+(sweep&&frame>=4?"-move-"+frame:"");
        if(scaffoldOrbit){
            if(frame>=48){Gdx.app.exit();return;}int step=frame/4,selection=frame%4;
            var light=pose.queue.lighting.lights.first();double angle=Math.toRadians(step*30);
            light.x=3072+500*(float)Math.cos(angle);light.y=4480+500*(float)Math.sin(angle);light.z=300;
            for(int n=0;n<skinSurfaces.size;n++){LightingSurface s=skinSurfaces.get(n);float[] source=skinOriginals.get(n);int count=0;
                for(int v=0;v<skinCounts.get(n);v+=3){int o=v*8;float sign=source[o+7]-source[o+5]-source[o+6];
                    if(selection==0||selection==1&&sign<0||selection==2&&sign>0){System.arraycopy(source,o,s.shadowVertices,count*8,24);count+=3;}}
                s.shadowVertexCount=count;
            }
            mode=0;stem="scaffold-"+step+"-"+new String[]{"full","rear","front","off"}[selection];capture=true;
        }
        if(Boolean.getBoolean("pixscape.diagnostic.actorSkinSweep")){
            boolean orbit=Boolean.getBoolean("pixscape.diagnostic.actorSkinOrbit");
            if(frame>=(orbit?48:3)){Gdx.app.exit();return;}
            int skinMode=orbit?frame%2:frame;
            if(orbit&&skinMode==1&&Boolean.getBoolean("pixscape.diagnostic.actorSkinFrontOrbit"))skinMode=2;
            if(orbit){
                int step=frame/2;var l=pose.queue.lighting.lights.first();
                double angle=Math.toRadians(step%12*30);
                l.x=3221.7805f+500*(float)Math.cos(angle);l.y=3157.8274f+500*(float)Math.sin(angle);
                l.z=step<12?120:400;
            }
            for(int n=0;n<skinSurfaces.size;n++){
                LightingSurface s=skinSurfaces.get(n);float[] source=skinOriginals.get(n);int count=0;
                for(int v=0;v<skinCounts.get(n);v+=3){int o=v*8;
                    float sign=source[o+7]-source[o+5]-source[o+6];
                    if(skinMode==0 || (skinMode==1?sign<0:sign>0)){
                        System.arraycopy(source,o,s.shadowVertices,count*8,24);count+=3;
                    }
                }
                s.shadowVertexCount=count;
                System.out.println("SKIN frame="+frame+" group="+s.shadowGroupId+" vertices="+count);
            }
            mode=0;stem=orbit?"orbit-"+(frame/2)+"-"+(skinMode==0?"full":skinMode==1?"rear":"front"):frame==0?"mode-0":frame==1?"rear-only":"front-only";capture=true;
        }
        if(capture){if(!scaffoldOrbit&&!Boolean.getBoolean("pixscape.diagnostic.actorSkinOrbit"))System.setProperty("pixscape.poc.witnessReadback",output.child(stem).path());System.setProperty("pixscape.poc.lightingDiagnostic",Integer.toString(mode));}
        Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);Gdx.gl.glViewport(0,0,pose.width,pose.height);world.process();
        if(capture){InteractiveShadowCapture.png(output.child(stem+".png"));System.clearProperty("pixscape.poc.witnessReadback");capture=false;
            if(mode==0&&frame==0&&!scaffoldOrbit&&!Boolean.getBoolean("pixscape.diagnostic.actorSkinOrbit"))compare();}
        frame++;
    }
    private void compare(){
        Pixmap expected=new Pixmap(file.parent().child("world.png")),actual=new Pixmap(output.child("mode-0.png"));long changed=0;
        try{for(int y=0;y<expected.getHeight();y++)for(int x=0;x<expected.getWidth();x++)if(expected.getPixel(x,y)!=actual.getPixel(x,y))changed++;
            output.child("comparison.txt").writeString("world pixels differing="+changed+"\n"+(changed==0?"EXACT":"NOT EXACT — ne pas assimiler ce rejeu à la référence")+"\n",false,"UTF-8");
            Gdx.graphics.setTitle("Pose figée — "+(changed==0?"rejeu exact":"ATTENTION : "+changed+" pixels différents")+" — 0..7 modes ; clic: pixel ; Échap");
        }finally{expected.dispose();actual.dispose();}
    }
    @Override public void dispose(){System.clearProperty("pixscape.poc.witnessReadback");if(world!=null)world.dispose();if(batch!=null)batch.close();if(bundle!=null)bundle.textureArray.dispose();for(Texture t:owned){TextureRegistry.release(t);t.dispose();}ShaderRegistry.disposeAll();}
}
