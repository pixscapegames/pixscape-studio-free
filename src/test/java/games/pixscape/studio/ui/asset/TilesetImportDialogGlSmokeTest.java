package games.pixscape.studio.ui.asset;

import com.badlogic.gdx.*;
import com.badlogic.gdx.backends.lwjgl3.*;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.utils.Array;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.studio.asset.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.service.asset.*;
import games.pixscape.studio.ui.main.StudioApplicationAdapter;
import org.junit.Test;
import java.nio.file.Files;
import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Real desktop UI, async progress and tile render bindings. Never opens a user project by default. */
public class TilesetImportDialogGlSmokeTest {
    @Test public void newImportKeepsExistingMapAndAsyncReimportRebindsTiledPixels() throws Exception {
        assumeTrue("1".equals(System.getenv("PIXSCAPE_TILESET_IMPORT_GL_TEST")));
        ProjectConfig original=ProjectConfig.getInstance(); String home=System.getProperty("user.home");
        FileHandle root=new FileHandle(Files.createTempDirectory("studio-import-ui").toFile());
        System.setProperty("user.home",root.child("home").path());
        ProjectConfig cfg=new ProjectConfig(); cfg.projectDirectoryPath=root.path(); cfg.projectFileName="test.json";
        cfg.createSceneMeta("Main"); root.child("scenes/scene1.json").writeString("{}",false);
        ProjectConfig.setInstance(cfg); Throwable[] failure={null};
        var gl=new Lwjgl3ApplicationConfiguration(); gl.setTitle("Tileset import UI regression");
        gl.setWindowedMode(1100,850); gl.setInitialVisible(false); gl.disableAudio(true);
        gl.setOpenGLEmulation(Lwjgl3ApplicationConfiguration.GLEmulation.GL30,3,2);
        try(var globals=new games.pixscape.studio.service.StudioGlTestScope()) {
            new Lwjgl3Application(new ApplicationAdapter() {
                StudioApplicationAdapter studio; AssetMetaDatabase db; FileHandle sheet,collection; int step,id,set,newId,mapEntity,collectionId;
                games.pixscape.runtime.component.TiledLayerComponent map;
                byte[] initialAtlas; Object initialLoadedAtlas; int initialHandle,framesWhileBusy,progressFrames;
                long started=System.nanoTime();
                @Override public void create() {
                    try {
                        studio=new StudioApplicationAdapter(); studio.create();
                        assertEquals(root.path(),ProjectConfig.getInstance().projectDirectoryPath);
                        db=new AssetMetaDatabase(); db.save(root.child("assets.json"));
                        var field=games.pixscape.studio.service.SceneService.class.getDeclaredField("assetMetaDatabase");
                        field.setAccessible(true); field.set(studio.getSceneService(),db);
                        sheet=root.child("external/sheet_N.png");
                        png(sheet,16,8,0xff0000ff); png(sheet.sibling("sheet_N_n.png"),16,8,0x12345640);
                        var first=gridDialog(studio,sheet); first.applyImportAndCloseIfSuccessful(); click(review(studio),"Import");
                        step=1;
                    } catch(Throwable error) { stop(error); }
                }
                @Override public void render() {
                    if(failure[0]!=null)return;
                    try {
                        studio.render();
                        if(System.nanoTime()-started>90_000_000_000L)throw new AssertionError("Import UI timed out");
                        if(studio.getSceneService().isTilesetImportInProgress()) {
                            framesWhileBusy++;
                            if(lastDialog(studio,"Importing tileset")!=null)progressFrames++;
                            return;
                        }
                        switch(step) {
                            case 1 -> {
                                TileAssetMeta tile=(TileAssetMeta)db.findByLogicalPath("tiles/sheet_N/0"); assertNotNull(tile);
                                id=tile.id(); set=tile.tilesetId; assertNotNull(tile.normalSourceRelPath);
                                var context=studio.getCanvas().createSceneContext("scene1",cfg.getCurrentSceneMeta());
                                studio.getCanvas().attach(context); context.layerService().addLayerTop("Layer");
                                var world=context.world(); mapEntity=world.create();
                                world.getMapper(games.pixscape.runtime.component.EntityIndexComponent.class).create(mapEntity).layerIndex=0;
                                world.getMapper(games.pixscape.runtime.component.TransformComponent.class).create(mapEntity);
                                map=world.getMapper(games.pixscape.runtime.component.TiledLayerComponent.class).create(mapEntity);
                                map.atlasTag="scene1"; map.data=new games.pixscape.runtime.tiled.TiledMapLayerData(1,1,8,8,1);
                                map.data.setTile(0,0,id); world.process();
                                var inputs=new games.pixscape.studio.service.atlas.SceneAtlasInputService();
                                studio.getCanvas().getAtlasService().repackImportedScenes(cfg,root,java.util.Set.of("scene1"),
                                        ()->inputs.syncSceneAtlasInput(cfg,"scene1",root,java.util.Set.of(tile.sourceRelPath())),
                                        ()->studio.getCanvas().getAtlasService().load("scene1",root.child("atlases/scene1.atlas")));
                                studio.getCanvas().publishAssetMetaDatabase(db); studio.getCanvas().rebindImportedSceneResources(java.util.Set.of("scene1"));
                                prepareMap(); assertEquals(0xff0000ff,sampleTile());
                                initialAtlas=root.child("atlases/scene1.png").readBytes(); initialLoadedAtlas=studio.getCanvas().getAtlasService().getAtlas("scene1"); initialHandle=tileHandle();
                                var duplicate=gridDialog(studio,sheet); duplicate.applyImportAndCloseIfSuccessful(); click(review(studio),"Import as new"); step=2;
                            }
                            case 2 -> {
                                newId=db.findByLogicalPath("tiles/sheet_N-2/0").id(); assertNotEquals(id,newId);
                                assertEquals(id,map.data.getTile(0,0)); assertEquals(initialHandle,tileHandle());
                                assertSame(initialLoadedAtlas,studio.getCanvas().getAtlasService().getAtlas("scene1"));
                                assertArrayEquals(initialAtlas,root.child("atlases/scene1.png").readBytes());
                                assertFalse(root.child("atlases/input/scene1/.repack-required").exists());
                                assertEquals(0xff0000ff,sampleTile());
                                ((TilesetAssetMeta)db.findById(set)).offsetX=17;
                                png(sheet,16,8,0x00ff00ff); sheet.sibling("sheet_N_n.png").delete();
                                var update=gridDialog(studio,sheet); update.applyImportAndCloseIfSuccessful();
                                var choices=find(review(studio),VisSelectBox.class,actor->true); choices.setSelectedIndex(1);
                                assertTrue(review(studio).getContentTable().toString().contains("Normal association will be removed"));
                                click(review(studio),"Update"); step=3;
                            }
                            case 3 -> {
                                assertEquals(id,map.data.getTile(0,0)); assertEquals(17,((TilesetAssetMeta)db.findById(set)).offsetX);
                                assertNull(((TileAssetMeta)db.findById(id)).normalSourceRelPath);
                                assertNotNull(((TileAssetMeta)db.findById(newId)).normalSourceRelPath);
                                assertEquals(0xff0000ff,pixel(root.child(db.findById(newId).sourceRelPath())));
                                prepareMap(); assertNotEquals(initialHandle,tileHandle()); assertEquals(0x00ff00ff,sampleTile());
                                assertTrue("The UI must draw while the worker packs",framesWhileBusy>2); assertTrue(progressFrames>2);
                                assertFalse(root.child("atlases/scene1.atlas").readString().contains("_n"));
                                assertFalse(root.child("atlases/input/scene1/.repack-required").exists());
                                // Reproduce the old publication defect: packed refs keep disposed page handles until chunks are dirtied.
                                int oldHandle=tileHandle();
                                studio.getCanvas().getAtlasService().load("scene1",root.child("atlases/scene1.atlas"));
                                games.pixscape.studio.helper.RenderRebindHelper.rebindAfterAtlasChange(studio.getCanvas(),"scene1",studio.getCanvas().getAssetVisualResolver());
                                prepareMap(); assertEquals(oldHandle,tileHandle());
                                assertEquals(0,games.pixscape.runtime.service.TextureRegistry.getByHandle(oldHandle).getTextureObjectHandle());
                                studio.getCanvas().rebindImportedSceneResources(java.util.Set.of("scene1"));
                                prepareMap(); assertNotEquals(oldHandle,tileHandle()); assertEquals(0x00ff00ff,sampleTile());
                                var saved=AssetMetaDatabase.load(root.child("assets.json"));
                                assertEquals(((TileAssetMeta)db.findById(id)).importSourceKey,((TileAssetMeta)saved.findById(id)).importSourceKey);
                                ((TileAssetMeta)db.findById(id)).importSourceKey=null;
                                String before=db.toSnapshotJson();
                                var legacy=gridDialog(studio,sheet); legacy.applyImportAndCloseIfSuccessful();
                                find(review(studio),VisSelectBox.class,actor->true).setSelectedIndex(1);
                                assertTrue(review(studio).getContentTable().toString().contains("lacks required reimport provenance"));
                                assertTrue(find(review(studio),VisTextButton.class,actor->((VisTextButton)actor).getText().toString().equals("Update")).isDisabled());
                                click(review(studio),"Cancel"); assertEquals(before,db.toSnapshotJson());
                                collection=root.child("external/props");
                                png(collection.child("barrel_N.png"),8,8,0xff00ffff); png(collection.child("barrel_N_n.png"),8,8,0x12345640);
                                folderDialog(studio,collection); click(review(studio),"Import"); step=4;
                            }
                            case 4 -> {
                                var tile=(TileAssetMeta)db.findByLogicalPath("tiles/props/0"); collectionId=tile.id();
                                assertEquals("barrel_N.png",tile.importSourceKey); assertNotNull(tile.normalSourceRelPath);
                                assertEquals(0x12345640,pixel(root.child(tile.normalSourceRelPath)));
                                png(collection.child("barrel_N.png"),8,8,0x112233ff);png(collection.child("barrel_N_n.png"),8,8,0x98765480);
                                folderDialog(studio,collection); click(review(studio),"Update");step=5;
                            }
                            case 5 -> {
                                var tile=(TileAssetMeta)db.findById(collectionId);
                                assertEquals(0x112233ff,pixel(root.child(tile.sourceRelPath())));
                                assertArrayEquals(collection.child("barrel_N_n.png").readBytes(),root.child(tile.normalSourceRelPath).readBytes());
                                assertEquals(id,map.data.getTile(0,0));prepareMap();assertEquals(0x00ff00ff,sampleTile());
                                Gdx.app.exit(); step=6;
                            }
                        }
                    } catch(Throwable error) { stop(error); }
                }
                void prepareMap() { studio.getCanvas().getEcsWorld().getSystem(games.pixscape.runtime.system.RenderTiledSyncSystem.class).prepareRuntimeAvailability(); }
                games.pixscape.runtime.render.TiledMapRenderState state() throws Exception {
                    var field=games.pixscape.studio.ui.main.WorldCanvas.class.getDeclaredField("tiledState"); field.setAccessible(true);
                    return (games.pixscape.runtime.render.TiledMapRenderState)field.get(studio.getCanvas());
                }
                int tileHandle() throws Exception { return state().textureHandle[map.data.getChunk(0,0).renderRefFor(0,0)]; }
                int sampleTile() throws Exception {
                    var state=state();int ref=map.data.getChunk(0,0).renderRefFor(0,0);
                    var texture=games.pixscape.runtime.service.TextureRegistry.getByHandle(state.textureHandle[ref]);
                    var fbo=new com.badlogic.gdx.graphics.glutils.FrameBuffer(com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888,8,8,false);
                    var batch=new com.badlogic.gdx.graphics.g2d.SpriteBatch();
                    try {
                        fbo.begin(); Gdx.gl.glClearColor(0,0,0,1);Gdx.gl.glClear(com.badlogic.gdx.graphics.GL20.GL_COLOR_BUFFER_BIT);
                        batch.setProjectionMatrix(new com.badlogic.gdx.math.Matrix4().setToOrtho2D(0,0,8,8));
                        batch.begin(); batch.draw(texture,0,0,8,8,state.u1[ref],state.v1[ref],state.u2[ref],state.v2[ref]); batch.end();
                        var pixels=com.badlogic.gdx.utils.ScreenUtils.getFrameBufferPixmap(0,0,8,8);
                        int rgba;try{rgba=pixels.getPixel(4,4);}finally{pixels.dispose();} fbo.end();return rgba;
                    } finally { batch.dispose();fbo.dispose(); }
                }
                void stop(Throwable error) { failure[0]=error;Gdx.app.exit(); }
                @Override public void dispose() { if(studio!=null)studio.dispose(); }
            },gl);
        } finally { ProjectConfig.setInstance(original);System.setProperty("user.home",home); }
        if(failure[0]!=null)throw new AssertionError("Real import UI failed",failure[0]);
    }
    private static int pixel(FileHandle file) {var p=new com.badlogic.gdx.graphics.Pixmap(file);try{return p.getPixel(0,0);}finally{p.dispose();}}
    private static void folderDialog(StudioApplicationAdapter studio,FileHandle directory) throws Exception {
        var dialog=new ImportDialog(studio,studio.getSceneService()::importAssets,
                (folder,settings)->studio.getSceneService().importTilesetDirectory(folder,settings));
        dialog.show(studio.getUiStage());
        var method=ImportDialog.class.getDeclaredMethod("openTilesetDirectoryProfileDialog",FileHandle.class,
                TilesetAssetImportService.DirectoryTilesetAnalysis.class);
        method.setAccessible(true);method.invoke(dialog,directory,TilesetAssetImportService.analyzeDirectory(directory));
        click(lastDialog(studio,"Tileset profile"),"Apply");
    }
    private static ImportDialog gridDialog(StudioApplicationAdapter studio,FileHandle file) throws Exception {
        ImportDialog dialog=new ImportDialog(studio,studio.getSceneService()::importAssets,
                (directory,settings)->studio.getSceneService().importTilesetDirectory(directory,settings));
        var add=ImportDialog.class.getDeclaredMethod("addOrSkip",FileHandle.class);add.setAccessible(true);
        add.invoke(dialog,file);add.invoke(dialog,file.sibling(file.nameWithoutExtension()+"_n.png"));
        var field=ImportDialog.class.getDeclaredField("items"); field.setAccessible(true);
        @SuppressWarnings("unchecked") Array<ImportDialog.ImportItem> items=(Array<ImportDialog.ImportItem>)field.get(dialog);
        assertEquals("Only the north color, never its normal companion, enters the UI list",1,items.size);
        var item=items.first();item.type=ImportDialog.ImportType.TILESET;
        item.applySlicingSettings(8,8,0,0);
        item.resolveDimensionsIfNeeded(ImportDialogValidation::readImageDimensions);
        dialog.show(studio.getUiStage()); return dialog;
    }
    private static TilesetImportReviewDialog review(StudioApplicationAdapter studio) {
        return find(studio.getUiStage().getRoot(),TilesetImportReviewDialog.class,actor->true);
    }
    private static VisDialog lastDialog(StudioApplicationAdapter studio,String title) {
        return find(studio.getUiStage().getRoot(),VisDialog.class,actor->((VisDialog)actor).getTitleLabel().getText().toString().equals(title));
    }
    private static void click(VisDialog dialog,String text) {
        assertNotNull("Dialog for "+text,dialog);
        VisTextButton button=find(dialog,VisTextButton.class,actor->((VisTextButton)actor).getText().toString().equals(text));
        assertNotNull(text,button); assertFalse(text,button.isDisabled()); button.fire(new ChangeListener.ChangeEvent());
    }
    private static <T extends Actor> T find(Actor root,Class<T> type,java.util.function.Predicate<Actor> predicate) {
        if(root instanceof Group group) {
            var children=group.getChildren();
            for(int i=children.size-1;i>=0;i--) { T found=find(children.get(i),type,predicate); if(found!=null)return found; }
        }
        return type.isInstance(root)&&predicate.test(root)?type.cast(root):null;
    }
    private static void png(FileHandle file,int w,int h,int rgba) {
        file.parent().mkdirs(); var pixels=new com.badlogic.gdx.graphics.Pixmap(w,h,com.badlogic.gdx.graphics.Pixmap.Format.RGBA8888);
        try {pixels.setBlending(com.badlogic.gdx.graphics.Pixmap.Blending.None);pixels.setColor(rgba);pixels.fill();com.badlogic.gdx.graphics.PixmapIO.writePNG(file,pixels);}
        finally {pixels.dispose();}
    }
}
