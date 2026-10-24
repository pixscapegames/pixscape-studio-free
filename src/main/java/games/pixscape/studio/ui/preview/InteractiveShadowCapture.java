package games.pixscape.studio.ui.preview;

import com.badlogic.gdx.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.utils.*;
import games.pixscape.runtime.engine.PixscapeEngine;
import games.pixscape.runtime.render.lighting.*;
import games.pixscape.runtime.service.*;
import games.pixscape.runtime.system.RenderSubmitSystem;
import java.lang.reflect.Field;
import java.nio.IntBuffer;
import java.nio.file.*;
import java.nio.file.Files;
import java.util.*;

/** F8 request, consumed on exactly one rendered frame. All files stay outside authored scene data. */
final class InteractiveShadowCapture {
    private FileHandle directory;
    private boolean pending,worldCaptured;
    private String oldRequest;
    private final boolean forward;
    private final FileHandle root;
    private final String scene;
    InteractiveShadowCapture(FileHandle root,String scene,boolean forward){this.root=root;this.scene=scene;this.forward=forward;}
    void request(){
        if(pending)return;
        oldRequest=System.getProperty("pixscape.poc.witnessReadback");
        try{
        directory=root.child(".pixscape/shadow-captures/"+java.time.Instant.now().toString().replace(':','-')+"-"+UUID.randomUUID());
        directory.mkdirs();pending=true;worldCaptured=false;
        System.setProperty("pixscape.poc.witnessReadback",directory.child("frame-mode-0").path());
        Gdx.graphics.setTitle("Preview — capture diagnostique en cours");
        }catch(RuntimeException failure){fail(failure);}
    }
    boolean pending(){return pending;}
    void world(PixscapeEngine engine){
        if(!pending||worldCaptured)return;
        try {
            png(directory.child("world.png"));
            FrozenPreviewPose p=new FrozenPreviewPose();p.forward=forward;p.scene=scene;p.project=root.file().getAbsolutePath();
            p.session=System.getProperty("pixscape.preview.session","");p.javaVersion=System.getProperty("java.version");
            p.previewPid=ProcessHandle.current().pid();p.previewStarted=ProcessHandle.current().info().startInstant().map(Object::toString).orElse("");
            p.glDriver=Gdx.graphics.getGLVersion().getDebugVersionString();p.javaHome=System.getProperty("java.home");p.javaVendor=System.getProperty("java.vendor");
            p.runtime=LightingRenderer.class.getProtectionDomain().getCodeSource().getLocation().toString();
            p.width=Gdx.graphics.getBackBufferWidth();p.height=Gdx.graphics.getBackBufferHeight();p.logicalWidth=Gdx.graphics.getWidth();p.logicalHeight=Gdx.graphics.getHeight();
            IntBuffer samples=BufferUtils.newIntBuffer(1);Gdx.gl.glGetIntegerv(GL20.GL_SAMPLES,samples);p.samples=samples.get(0);
            var c=engine.getCamera();p.cameraPosition.set(c.position);p.cameraDirection.set(c.direction);p.cameraUp.set(c.up);
            p.viewportWidth=c.viewportWidth;p.viewportHeight=c.viewportHeight;p.zoom=c.zoom;p.near=c.near;p.far=c.far;
            p.queue=engine.getFrameQueue();p.layers=engine.getLayerState();
            RenderSubmitSystem submit=engine.getWorld().getSystem(RenderSubmitSystem.class);
            if(submit.forwardRenderer()!=null)p.roundedLighting=submit.forwardRenderer().roundedLightingEnabled();
            if(field(submit,"metricsBatch") instanceof games.pixscape.runtime.render.batch.TextureArrayMeshBatch arrayBatch&&arrayBatch.getBundle()!=null){
                p.arrayBatch=true;
                p.arrayMin=arrayBatch.getBundle().textureArray.getMinFilter().name();p.arrayMag=arrayBatch.getBundle().textureArray.getMagFilter().name();
            }
            for(int i=0;i<3;i++)p.ambient[i]=(Float)field(submit,"ambientMul"+"RGB".charAt(i));
            p.time=(Float)field(submit,"time");
            for(int i=0;i<p.queue.size;i++){
                int shader=p.queue.shader[i];
                if(ShaderRegistry.getOrigin(shader)!=games.pixscape.runtime.render.ShaderOrigin.CORE)throw new IllegalStateException("Rejeu exact refusé : shader non-core dans la pose ("+shader+")");
                for(String name:ShaderRegistry.getRegisteredNames())if(ShaderRegistry.indexOf(name)==shader){p.shaders.put(shader,name);break;}
                int e=p.queue.sourceEntity[i];if(p.queue.light[i]!=0&&e>=0){
                    var point=engine.getWorld().getMapper(games.pixscape.runtime.component.light.PointLightComponent.class).getSafe(e,null);
                    var cone=engine.getWorld().getMapper(games.pixscape.runtime.component.light.ConeLightComponent.class).getSafe(e,null);
                    if(point!=null||cone!=null)p.lightIntensities.put(e,point!=null?point.intensity:cone.intensity);
                }
            }
            for(String key:System.getProperties().stringPropertyNames())if(key.startsWith("pixscape.")&&!key.contains("witness")&&!key.contains("Capture")&&!key.contains("session")&&!key.contains("diagnosticLog"))p.properties.put(key,System.getProperty(key));
            IntSet handles=new IntSet();for(int i=0;i<p.queue.size;i++)if(p.queue.textureHandle[i]>0)handles.add(p.queue.textureHandle[i]);
            for(var caster:p.queue.lighting.casters)if(caster.texture>0)handles.add(caster.texture);
            for(var e:p.queue.lighting.tiles)normal(handles,e.value);
            for(var e:p.queue.lighting.entities)normal(handles,e.value);
            for(var iterator=handles.iterator();iterator.hasNext;)saveTexture(p,iterator.next());
            for(int i=0;i<p.queue.lighting.lights.size;i++){
                var light=p.queue.lighting.lights.get(i);if(light.quality==0)continue;
                String stem="frame-mode-0-index-"+i+"-light-"+light.x+"-"+light.y+"-"+light.z;
                for(String suffix:new String[]{"-d1.gz","-id1.gz","-d2.gz"})if(!directory.child(stem+suffix).exists())throw new IllegalStateException("Atlas manquant : "+stem+suffix);
            }
            directory.child("pose.json").writeString(FrozenPreviewPose.codec().toJson(p),false,"UTF-8");
            // Preserve the actual loaded source metadata for provenance, not as the replay state.
            FileHandle published=root.child(games.pixscape.studio.configuration.RuntimeExport.RUNTIME_DIR_NAME);
            FileHandle loadedScene=published.child("scenes/"+scene+".json");if(loadedScene.exists())loadedScene.copyTo(directory.child("source-scene.json"));
            for(String name:new String[]{"project.json","assets.json"})if(published.child(name).exists())published.child(name).copyTo(directory.child("source-"+name));
            saveLauncher();
            worldCaptured=true;
        }catch(Exception e){fail(e);}
    }
    private static void normal(IntSet handles,LightingSurface s){if(s.normalMap!=null)handles.add(s.normalMap.textureHandle);}
    static Object field(Object object,String name){
        try{Field f=object.getClass().getDeclaredField(name);f.setAccessible(true);return f.get(object);}catch(ReflectiveOperationException e){throw new IllegalStateException(e);}
    }
    static void png(FileHandle file){
        Pixmap pixmap=Pixmap.createFromFrameBuffer(0,0,Gdx.graphics.getBackBufferWidth(),Gdx.graphics.getBackBufferHeight());
        PixmapIO.PNG writer=new PixmapIO.PNG();writer.setFlipY(true);
        try{writer.write(file,pixmap);}catch(java.io.IOException e){throw new IllegalStateException(e);}finally{writer.dispose();pixmap.dispose();}
    }
    private void saveTexture(FrozenPreviewPose pose,int handle)throws java.io.IOException {
        Texture texture=TextureRegistry.getByHandle(handle);if(texture==null)throw new IllegalStateException("Texture absente : "+handle);
        FrozenPreviewPose.TextureInfo info=new FrozenPreviewPose.TextureInfo();info.handle=handle;info.width=texture.getWidth();info.height=texture.getHeight();
        info.min=texture.getMinFilter().name();info.mag=texture.getMagFilter().name();info.u=texture.getUWrap().name();info.v=texture.getVWrap().name();info.file="texture-"+handle+".png";
        IntBuffer state=BufferUtils.newIntBuffer(1);Gdx.gl.glGetIntegerv(GL20.GL_FRAMEBUFFER_BINDING,state);int previous=state.get(0),fbo=Gdx.gl.glGenFramebuffer();
        Pixmap pixmap=new Pixmap(info.width,info.height,Pixmap.Format.RGBA8888);PixmapIO.PNG writer=new PixmapIO.PNG();writer.setFlipY(false);
        try{
            Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,fbo);Gdx.gl.glFramebufferTexture2D(GL20.GL_FRAMEBUFFER,GL20.GL_COLOR_ATTACHMENT0,GL20.GL_TEXTURE_2D,texture.getTextureObjectHandle(),0);
            if(Gdx.gl.glCheckFramebufferStatus(GL20.GL_FRAMEBUFFER)!=GL20.GL_FRAMEBUFFER_COMPLETE)throw new IllegalStateException("Texture non lisible "+handle);
            Gdx.gl.glReadPixels(0,0,info.width,info.height,GL20.GL_RGBA,GL20.GL_UNSIGNED_BYTE,pixmap.getPixels());
            writer.write(directory.child(info.file),pixmap);pose.textures.add(info);
        }finally{Gdx.gl.glBindFramebuffer(GL20.GL_FRAMEBUFFER,previous);Gdx.gl.glDeleteFramebuffer(fbo);writer.dispose();pixmap.dispose();}
    }
    void after(){
        if(!pending)return;
        try{
            if(!worldCaptured)throw new IllegalStateException("Le hook après rendu monde n'a pas été exécuté");
            if(directory.child("frame-mode-0-FAILED.txt").exists())throw new IllegalStateException("Échec de lecture des atlas : voir frame-mode-0-FAILED.txt");
            png(directory.child("render.png"));
            directory.child("READY").writeString("Capture complète — voir pose.json, world.png, render.png et les atlas.\n",false,"UTF-8");
            Gdx.graphics.setTitle("Preview — capture enregistrée : "+directory.name());
            PreviewDiagnosticLog.child("SHADOW_CAPTURE_READY",directory.file().getAbsolutePath());
        }catch(Exception e){fail(e);}finally{restore();}
    }
    private void fail(Exception e){
        try{if(directory!=null)directory.child("FAILED.txt").writeString(e.toString(),false,"UTF-8");}catch(RuntimeException writeFailure){e.addSuppressed(writeFailure);}
        finally{restore();}
        Gdx.app.error("ShadowCapture","Capture refusée/incomplète : "+directory,e);Gdx.graphics.setTitle("Preview — capture incomplète : voir journal / FAILED.txt");
    }
    void abort(String reason){if(pending)fail(new IllegalStateException(reason));}
    void restore(){pending=false;if(oldRequest==null)System.clearProperty("pixscape.poc.witnessReadback");else System.setProperty("pixscape.poc.witnessReadback",oldRequest);}
    private void saveLauncher()throws java.io.IOException {
        // Copy compiled code, not projects/preferences: later builds cannot change this replay silently.
        FileHandle code=directory.child("code");code.mkdirs();java.util.List<String> cp=new ArrayList<>();int n=0;
        long bytes=0;int files=0;
        for(String entry:System.getProperty("java.class.path").split(java.util.regex.Pattern.quote(java.io.File.pathSeparator))){
            Path source=Path.of(entry).toRealPath();
            if(code.file().toPath().toRealPath().startsWith(source))throw new java.io.IOException("Classpath englobe la destination de capture : "+source);
            Path target=code.file().toPath().resolve((n++)+"-"+source.getFileName());
            if(Files.isDirectory(source)){try(var paths=Files.walk(source)){for(Path path:(Iterable<Path>)paths::iterator){Path to=target.resolve(source.relativize(path));if(Files.isSymbolicLink(path))throw new java.io.IOException("Lien dans le classpath : "+path);if(Files.isDirectory(path))Files.createDirectories(to);else{bytes+=Files.size(path);if(++files>30000||bytes>1024L*1024*1024)throw new java.io.IOException("Archive de code hors borne (1 Gio / 30000 fichiers)");Files.createDirectories(to.getParent());Files.copy(path,to);}}}}
            else {bytes+=Files.size(source);if(++files>30000||bytes>1024L*1024*1024)throw new java.io.IOException("Archive de code hors borne");Files.copy(source,target);}
            cp.add("%~dp0code\\"+target.getFileName());
        }
        String java=Path.of(System.getProperty("java.home"),"bin","java.exe").toString().replace("%","%%");
        directory.child("replay.cmd").writeString("@echo off\r\nsetlocal\r\ncd /d \"%~dp0\"\r\n\""+java+"\" -Xmx768m -cp \""+String.join(";",cp)+"\" games.pixscape.studio.ui.preview.FrozenPoseReplay \"%~dp0pose.json\"\r\npause\r\n",false,"UTF-8");
    }
}
