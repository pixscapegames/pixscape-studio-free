package games.pixscape.studio.service.atlas;

import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.Color;
import com.badlogic.gdx.graphics.Pixmap;
import com.badlogic.gdx.graphics.PixmapIO;
import com.badlogic.gdx.utils.Array;
import com.badlogic.gdx.utils.IntArray;
import games.pixscape.runtime.component.*;
import games.pixscape.runtime.particle.ParticleEmitter;
import games.pixscape.runtime.render.FrameRenderQueue;
import games.pixscape.runtime.service.TextureRegistry;
import games.pixscape.runtime.tiled.TiledProjection;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.batch.TextureArrayMeshBatchStudio;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.service.SceneService;
import games.pixscape.studio.service.StudioGlTestScope;
import games.pixscape.studio.system.StudioRenderSubmitSystem;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import games.pixscape.studio.ui.main.WorldCanvas;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.nio.file.Files;

import static org.junit.Assert.*;

/** Real editor/canvas, real fallback producers and real pack/publication; only worker scheduling is controlled. */
public final class StudioForwardDropGlCheck implements AutoCloseable {
    private final String home=System.getProperty("user.home");
    private final ProjectConfig previous=ProjectConfig.getInstance();
    private final StudioGlTestScope globals=new StudioGlTestScope();
    private StudioApplicationAdapter app;
    public StudioForwardDropGlCheck() throws Exception {}
    public void run() throws Exception {
        FileHandle root=new FileHandle(Files.createTempDirectory("studio-forward-drop-").toFile());
        System.setProperty("user.home",root.path());
            ProjectConfig cfg=new ProjectConfig();cfg.projectDirectoryPath=root.path();cfg.projectFileName="test.pixscape";
            ProjectConfig.setInstance(cfg);
            AssetMetaDatabase db=assets(root);db.save(root.child("assets.json"));
            app=new StudioApplicationAdapter();app.create();
            // Studio startup may replace the singleton while restoring its empty workspace.
            ProjectConfig.setInstance(cfg);
            field(SceneService.class,"assetMetaDatabase").set(app.getSceneService(),db);
            cfg.createSceneMeta("Preview");var meta=cfg.getCurrentSceneMeta();meta.forwardLighting=true;
            meta.ambientMulR=.6f;meta.ambientMulG=.7f;meta.ambientMulB=.8f;
            String tag=cfg.canonicalSceneTagCurrent();WorldCanvas canvas=app.getCanvas();
            var context=canvas.createSceneContext(tag,meta);canvas.attach(context);
            app.getEditorDocumentManager().openScene(tag,"Preview",context);
            int index=canvas.getLayerService().addLayerTop("Assets");
            int layer=canvas.getLayerService().getLayerEntity(index);
            canvas.getSelectionService().setActivelayerId(layer);
            canvas.refreshProjectBoundServices();
            canvas.getGpuSnapshotManager().syncIfDirty(tag);
            var atlas=canvas.getAtlasService();ControlledAtlasExecutor executor=new ControlledAtlasExecutor();
            var coordinatorField=field(AtlasStudioService.class,"repackCoordinator");
            ((AsyncAtlasRepackCoordinator<?>)coordinatorField.get(atlas)).dispose();
            var pack=AtlasStudioService.class.getDeclaredMethod("packAsyncToTemp",String.class,long.class,AsyncAtlasRepackCoordinator.RepackReason.class);
            pack.setAccessible(true);
            coordinatorField.set(atlas,new AsyncAtlasRepackCoordinator<AtlasStudioService.ScenePrepared>((target,generation,reason)->{
                try{return (AtlasStudioService.ScenePrepared)pack.invoke(atlas,target,generation,reason);}
                catch(InvocationTargetException ex){throw (RuntimeException)ex.getCause();}
            },executor,()->0L,0));
            var ops=canvas.getEditorOps();
            // Establish an already published scene before adding anything under test.
            int background=ops.createStandaloneSprite(imageName(db,"background"),-80,0);
            frame(canvas);publish(executor,atlas);frame(canvas);
            assertPacked(canvas,background);
            var active=((TextureArrayMeshBatchStudio)canvas.getMetricsBatch()).getBundle();

            int image=ops.createStandaloneSprite(imageName(db,"image"),0,0);
            frame(canvas);assertPending(canvas,image);assertSame(active,bundle(canvas));
            assertTrue("The dropped image has red pixels on the canvas before packing",(pixel(canvas,image)>>>24)>100);
            assertTrue(atlas.hasAsyncPackQueuedOrRunningFor(tag));assertTrue(executor.queued()>0);
            float x=canvas.getEcsWorld().getMapper(TransformComponent.class).get(image).x;
            ops.applyTransform(IntArray.with(image),null,null,12f,0f,null,null,null,null,null);
            frame(canvas);assertEquals(x+12,canvas.getEcsWorld().getMapper(TransformComponent.class).get(image).x,.01f);

            int animation=ops.createAnimationSprite("walk",65,0);assertTrue(animation>=0);
            frame(canvas);assertPending(canvas,animation);
            var handles=new java.util.HashSet<Integer>();
            var framePixels=new java.util.HashSet<Integer>();
            for(int i=0;i<12;i++){frame(canvas);handles.add(texture(canvas,animation));framePixels.add(pixel(canvas,animation));}
            assertTrue("Animation advances while no worker can finish",handles.size()>1);
            assertTrue("Animated pixels change before publication",framePixels.size()>1);
            ops.applyTransform(IntArray.with(animation),null,null,0f,12f,null,null,null,null,null);frame(canvas);assertPending(canvas,animation);

            int map=ops.addTiledMap(layer,2,2,TiledProjection.ORTHO,32,32,2);
            var component=canvas.getEcsWorld().getMapper(TiledLayerComponent.class).get(map);
            var brush=new games.pixscape.studio.service.tiled.TiledBrushSession(map);
            brush.apply(component,0,0,db.findByLogicalPath("tile").id());
            var history=canvas.getHistoryManager();
            history.execute(new games.pixscape.studio.history.commands.TiledBrushCommand(canvas.getEcsWorld(),app.getSceneService(),
                    history.historyIds(),history.historyIds().ensureForEntity(map),brush.toPlan(),new games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner()));
            frame(canvas);assertDomain(canvas,FrameRenderQueue.SOURCE_TILED,false);

            int particle=ops.createParticleEffect("spark.p",-25,55);assertTrue(particle>=0);
            for(int i=0;i<5;i++)frame(canvas);
            assertDomain(canvas,FrameRenderQueue.SOURCE_VFX,false);
            assertSame("No publication before releasing the worker",active,bundle(canvas));
            assertPacked(canvas,background);assertPending(canvas,image);assertPending(canvas,animation);
            ops.createPointLight(-25,45);
            canvas.getEcsWorld().getMapper(SurfaceLightingComponent.class).create(image).shadowCaster=true;
            canvas.getEcsWorld().getMapper(SurfaceLightingComponent.class).create(animation).shadowCaster=true;
            frame(canvas);
            assertTrue("Unpacked sprites are already published as casters",queue(canvas).lighting.casters.size>=2);
            assertTrue("Their shared shadow pass runs before packing",canvas.getEcsWorld().getSystem(StudioRenderSubmitSystem.class).lightingRenderer().shadowDraws>0);
            capture(root,"pending.png");
            // Cold renderer switching and both layouts must admit the already live previews.
            meta.useNormalMaps=false;frame(canvas);assertPending(canvas,image);
            meta.forwardLighting=false;frame(canvas);assertPending(canvas,image);assertDomain(canvas,FrameRenderQueue.SOURCE_TILED,false);assertDomain(canvas,FrameRenderQueue.SOURCE_VFX,false);
            meta.forwardLighting=true;meta.useNormalMaps=true;frame(canvas);

            int deleted=ops.createStandaloneSprite(imageName(db,"image"),-60,-50);frame(canvas);
            ops.deleteEntities(IntArray.with(deleted));frame(canvas);
            int undone=ops.createStandaloneSprite(imageName(db,"image"),60,-50);frame(canvas);
            canvas.getHistoryManager().undo();frame(canvas);
            int undoneAnimation=ops.createAnimationSprite("walk",100,-50);frame(canvas);
            history.undo();frame(canvas);
            int deletedParticle=ops.createParticleEffect("spark.p",-100,-50);frame(canvas);
            ops.deleteEntities(IntArray.with(deletedParticle));frame(canvas);
            var removedTile=new games.pixscape.studio.service.tiled.TiledBrushSession(map);
            removedTile.apply(component,1,0,db.findByLogicalPath("tile").id());
            history.execute(new games.pixscape.studio.history.commands.TiledBrushCommand(canvas.getEcsWorld(),app.getSceneService(),
                    history.historyIds(),history.historyIds().ensureForEntity(map),removedTile.toPlan(),new games.pixscape.studio.service.tiled.TiledSpatialMutationPlanner()));
            frame(canvas);history.undo();frame(canvas);
            assertEquals(0,component.data.getTile(1,0));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(deleted));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(undone));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(undoneAnimation));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(deletedParticle));
            publish(executor,atlas);frame(canvas);
            assertNotSame(active,bundle(canvas));assertPacked(canvas,image);assertPacked(canvas,animation);
            assertDomain(canvas,FrameRenderQueue.SOURCE_TILED,true);assertDomain(canvas,FrameRenderQueue.SOURCE_VFX,true);
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(deleted));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(undone));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(undoneAnimation));
            assertFalse(canvas.getEcsWorld().getEntityManager().isActive(deletedParticle));
            assertEquals("No tile resurrected by publication",0,component.data.getTile(1,0));
            capture(root,"published.png");

            // Real pack failure: corrupt an input after the previous valid publication.
            int failed=ops.createStandaloneSprite(imageName(db,"later"),-40,-40);frame(canvas);assertPending(canvas,failed);
            var coherentAtlas=atlas.getAtlas(tag);int coherentRevision=atlas.publicationRevision(tag);
            root.child("atlases/input/"+tag+"/broken.png").writeString("invalid image",false);
            publish(executor,atlas,false);frame(canvas);
            assertNotNull(atlas.lastPackCompletion().failure());assertSame(coherentAtlas,atlas.getAtlas(tag));
            assertEquals(coherentRevision,atlas.publicationRevision(tag));
            assertPending(canvas,failed);assertPacked(canvas,image);
            assertFalse(atlas.hasAsyncPackQueuedOrRunningFor(tag));
            capture(root,"failed-pack.png");
            System.out.println("Studio Forward blocked-pack validation: image, animated frames, Tiled, additive particles, transform, deletion/undo, superseded requests, publication, failure, Legacy. Captures: "+root.path());
    }
    @Override public void close() throws Exception {
        try{if(app!=null)app.dispose();}finally{globals.close();System.setProperty("user.home",home);ProjectConfig.setInstance(previous);}
    }

    private static AssetMetaDatabase assets(FileHandle root) throws Exception {
        AssetMetaDatabase db=new AssetMetaDatabase();
        String[] names={"background","image","later","spark"};Color[] colors={Color.BLUE,Color.RED,Color.MAGENTA,Color.YELLOW};
        for(int i=0;i<names.length;i++){
            var asset=db.registerIfAbsent(AssetType.IMAGE,names[i],null,AssetMeta.AssetScope.USER);
            String path="orig/images/"+names[i]+"__a"+asset.id()+".png";
            db.updateSourceRelPath(asset.id(),path);png(root.child(path),colors[i]);
        }
        var anim=(AnimationAssetMeta)db.registerIfAbsent(AssetType.ANIMATION,"walk","orig/animations/walk",AssetMeta.AssetScope.USER);
        png(root.child("orig/animations/walk/walk__a"+anim.id()+"_0.png"),Color.GREEN);png(root.child("orig/animations/walk/walk__a"+anim.id()+"_1.png"),Color.CYAN);
        anim.frameCount=2;anim.fps=8;anim.currentClip="all";anim.clips.put("all",new AnimationClipMeta(0,1));
        var set=(TilesetAssetMeta)db.registerIfAbsent(AssetType.TILESET,"set",null,AssetMeta.AssetScope.USER);
        set.tileWidth=set.tileHeight=set.referenceCellWidth=set.referenceCellHeight=32;set.columns=set.rows=1;set.imageWidth=set.imageHeight=32;
        var tile=(TileAssetMeta)db.registerIfAbsent(AssetType.TILE,"tile",null,AssetMeta.AssetScope.USER);
        String tilePath="orig/tiles/tile__a"+tile.id()+".png";db.updateSourceRelPath(tile.id(),tilePath);png(root.child(tilePath),Color.ORANGE);
        tile.tilesetId=set.id();tile.sheetIndex=0;tile.cellX=tile.cellY=0;
        ParticleEmitter emitter=new ParticleEmitter();emitter.setName("spark");emitter.setContinuous(true);emitter.setAdditive(true);
        emitter.setMaxParticleCount(10);emitter.getDuration().setLow(1000);emitter.getEmission().setHigh(20);
        emitter.getLife().setHigh(1000);emitter.getXScale().setHigh(16);emitter.getTransparency().setHigh(1);
        emitter.setImagePaths(Array.with(imageName(db,"spark")));
        FileHandle effect=root.child("orig/effects/spark.p");effect.parent().mkdirs();try(var writer=effect.writer(false)){emitter.save(writer);}
        db.registerIfAbsent(AssetType.PARTICLE,"spark-effect","orig/effects/spark.p",AssetMeta.AssetScope.USER);
        return db;
    }
    private static void png(FileHandle file,Color color){file.parent().mkdirs();Pixmap p=new Pixmap(32,32,Pixmap.Format.RGBA8888);p.setColor(color);p.fill();PixmapIO.writePNG(file,p);p.dispose();}
    private static String imageName(AssetMetaDatabase db,String logical){return new FileHandle(db.findByLogicalPath(logical).sourceRelPath()).name();}
    private static Field field(Class<?> type,String name)throws Exception{Field f=type.getDeclaredField(name);f.setAccessible(true);return f;}
    private static FrameRenderQueue queue(WorldCanvas c)throws Exception{return (FrameRenderQueue)field(WorldCanvas.class,"frameQueue").get(c);}
    private static games.pixscape.runtime.service.AtlasRuntimeService.TextureArrayBundle bundle(WorldCanvas c){return ((TextureArrayMeshBatchStudio)c.getMetricsBatch()).getBundle();}
    private static void frame(WorldCanvas c)throws Exception{
        c.act(.1f);Gdx.gl.glClearColor(.07f,.07f,.07f,1);Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);c.draw();
        var submit=c.getEcsWorld().getSystem(StudioRenderSubmitSystem.class);
        if(submit.forwardRenderer()!=null)assertEquals("Canvas must present every frame",1,submit.forwardRenderer().presentationDraws);
        Object boundary=field(WorldCanvas.class,"spatialInvariantBoundary").get(c);
        assertFalse("The canvas never pauses",field(boundary.getClass(),"blocked").getBoolean(boundary));
    }
    private static int texture(WorldCanvas c,int entity)throws Exception{FrameRenderQueue q=queue(c);for(int i=0;i<q.size;i++)if(q.sourceEntity[i]==entity&&q.sourceDomain[i]==FrameRenderQueue.SOURCE_ECS)return q.textureHandle[i];throw new AssertionError("Entity missing from canvas queue: "+entity);}
    private static int pixel(WorldCanvas c,int entity)throws Exception{
        FrameRenderQueue q=queue(c);var camera=(com.badlogic.gdx.graphics.OrthographicCamera)field(WorldCanvas.class,"camera").get(c);
        for(int i=0;i<q.size;i++)if(q.sourceEntity[i]==entity&&q.sourceDomain[i]==FrameRenderQueue.SOURCE_ECS){
            var p=camera.project(new com.badlogic.gdx.math.Vector3((q.x1[i]+q.x3[i])*.5f,(q.y1[i]+q.y3[i])*.5f,0));
            var bytes=com.badlogic.gdx.utils.BufferUtils.newByteBuffer(4);
            Gdx.gl.glReadPixels((int)p.x,(int)p.y,1,1,com.badlogic.gdx.graphics.GL20.GL_RGBA,com.badlogic.gdx.graphics.GL20.GL_UNSIGNED_BYTE,bytes);
            return (bytes.get(0)&255)<<24|(bytes.get(1)&255)<<16|(bytes.get(2)&255)<<8|(bytes.get(3)&255);
        }throw new AssertionError("Missing entity pixels: "+entity);
    }
    private static void assertPending(WorldCanvas c,int e)throws Exception{int handle=texture(c,e);assertFalse(bundle(c).handle2layer.containsKey(handle));assertNotNull(TextureRegistry.getByHandle(handle));}
    private static void assertPacked(WorldCanvas c,int e)throws Exception{assertTrue(bundle(c).handle2layer.containsKey(texture(c,e)));}
    private static void assertDomain(WorldCanvas c,byte domain,boolean packed)throws Exception{FrameRenderQueue q=queue(c);int count=0;for(int i=0;i<q.size;i++)if(q.sourceDomain[i]==domain){count++;assertEquals(packed,bundle(c).handle2layer.containsKey(q.textureHandle[i]));}assertTrue("Visible domain "+domain,count>0);}
    private static void publish(ControlledAtlasExecutor executor,AtlasStudioService service)throws Exception{publish(executor,service,true);}
    private static void publish(ControlledAtlasExecutor executor,AtlasStudioService service,boolean success)throws Exception{
        service.updateAsyncPack();while(executor.queued()>0)executor.runNext();service.applyIfPackReady();
        assertNotNull(service.lastPackCompletion());if(success)assertNull(service.lastPackCompletion().failure());
    }
    private static void capture(FileHandle root,String name){Pixmap pixels=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0,0,Gdx.graphics.getWidth(),Gdx.graphics.getHeight());PixmapIO.writePNG(root.child(name),pixels);pixels.dispose();}
}
