package games.pixscape.studio.system;

import com.artemis.*;
import com.badlogic.gdx.*;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.scenes.scene2d.Stage;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.render.TiledMapRenderState;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.input.InputState;
import games.pixscape.studio.service.*;
import games.pixscape.studio.service.physics.*;
import games.pixscape.studio.service.spatial.*;
import java.lang.reflect.Proxy;
import static org.junit.Assert.*;

/** Native camera, input adapter, picking, stage preview and history lifecycle in the GL smoke. */
final class SpatialGestureGlChecks {
    static void run() {
        VisUI.load(new com.badlogic.gdx.scenes.scene2d.ui.Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
        try {
            for(float zoom:new float[]{.5f,1,2})for(float density:new float[]{1,1.5f,2})
                try(Fixture f=new Fixture(zoom,density)) {
                    // The left handle is outside this fixture's camera at zoom 0.5.
                    if(zoom>=1) {
                    f.press(.5f,2.6f);f.drag(.1f,2.6f);
                    assertTrue("Ordinary width resize starts",f.selection.wallEditSession().isActive());
                    assertNull(f.selection.linkPreview());assertEquals(0,f.stage.getActors().size);
                    f.release();assertEquals("No erroneous linking refusal",0,f.stage.getActors().size);
                    assertEquals(0,f.blocks.links.size);assertEquals(.1f,f.wall().x,.02f);
                    assertEquals(1,f.history.getCursor());f.history.undo();assertEquals(.5f,f.wall().x,0);
                    }

                    f.press(1.75f,2.6f);f.drag(3.75f,2.6f);
                    assertTrue("Move starts in the first dragged event",f.selection.wallEditSession().isActive());
                    assertEquals(2.5f,f.selection.wallEditSession().candidate().x,.02f);
                    assertNull(f.selection.linkPreview());f.release();assertEquals(0,f.blocks.links.size);
                    assertEquals(1,f.history.getCursor());f.history.undo();assertEquals(.5f,f.wall().x,0);

                    f.press(1.75f,2.6f);f.drag(4.75f,2.6f);
                    assertNotNull("Armed preview at zoom/density "+zoom+"/"+density,f.selection.linkPreview());
                    assertTrue(f.selection.linkPreview().diagnostic,f.selection.linkPreview().valid);
                    assertEquals(.5f,f.wall().x,0);assertEquals(0,f.blocks.links.size);
                    f.stage.act(.1f);f.stage.draw();assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
                    assertEquals("Valid link preview has no text overlay",0,f.stage.getActors().size);
                    assertEquals(1,f.selection.linkPreview().previewStructureCount());
                    games.pixscape.runtime.spatial.CompiledSpatialStructure outline=f.selection.linkPreview().previewStructure(0);
                    for(int edge=0;edge<outline.segmentCount();edge++)
                        assertFalse("The common face must not appear in the outline",
                                Math.abs(outline.startX(edge)-5)<.001f && Math.abs(outline.endX(edge)-5)<.001f);
                    if(zoom==1 && density==1) {
                        Gdx.gl.glClearColor(.08f,.08f,.08f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
                        f.world.process();f.stage.draw();
                        Pixmap image=Pixmap.createFromFrameBuffer(0,0,768,576);
                        try {PixmapIO.writePNG(new com.badlogic.gdx.files.FileHandle(System.getProperty("java.io.tmpdir")+
                                "/pixscape-voluntary-gesture-preview.png"),image,-1,true);}
                        finally {image.dispose();}
                    }
                    f.release();assertEquals(1,f.blocks.links.size);assertEquals(1,f.history.getCursor());
                    assertEquals(90,f.tiled.defaultTileHeight,0);f.history.undo();assertEquals(.5f,f.wall().x,0);
                    assertEquals(60,f.wall().height,0);assertEquals(128,f.tiled.defaultTileHeight,0);
                    f.history.redo();assertEquals(1,f.blocks.links.size);f.history.undo();

                    f.press(1.75f,2.6f);f.drag(3.75f,2.6f);f.drag(3.75f,2.7f);
                    assertNull("Tangential ISO motion must not arm",f.selection.linkPreview());
                    f.drag(4.75f,2.6f);assertNotNull(f.selection.linkPreview());
                    f.drag(3.77f,2.6f);assertNull("Normal retreat disarms",f.selection.linkPreview());
                    f.release();assertEquals(0,f.blocks.links.size);f.history.undo();

                    f.press(3,2.6f);f.drag(7,2.6f);
                    assertNotNull("Resize arms the same proposal",f.selection.linkPreview());
                    assertEquals(5,f.selection.wallEditSession().candidate().x+f.selection.wallEditSession().candidate().width,.02f);
                    f.release();assertEquals(1,f.blocks.links.size);f.history.undo();assertEquals(2.5f,f.wall().width,0);

                    f.press(1.75f,2.6f);f.drag(4.75f,2.6f);f.escape=true;f.world.process();f.escape=false;
                    assertFalse(f.selection.wallEditSession().isActive());assertNull(f.selection.linkPreview());
                    f.release();assertEquals(.5f,f.wall().x,0);assertFalse(f.history.canUndo());
                    assertEquals(0,f.stage.getActors().size);
                    f.press(1.75f,2.6f);f.drag(4.75f,2.6f);f.selection.setContextActive(false);f.world.process();
                    assertFalse(f.selection.wallEditSession().isActive());assertNull(f.selection.linkPreview());
                    assertFalse(f.input.isLeftDown());assertEquals(0,f.stage.getActors().size);assertEquals(.5f,f.wall().x,0);
                }
        } finally {VisUI.dispose();}
    }

    static final class Fixture implements AutoCloseable {
        final Input originalInput=Gdx.input;final Graphics originalGraphics=Gdx.graphics;
        final OrthographicCamera camera=new OrthographicCamera();final ScreenViewport viewport=new ScreenViewport(camera);
        final Stage stage=new Stage(new ScreenViewport());final InputState input=new InputState();
        final HistoryManager history=new HistoryManager(16);final SpatialBlockSelectionService selection=new SpatialBlockSelectionService();
        final World world;final int map;final TiledLayerComponent tiled;final SpatialBlocksComponent blocks;
        final CoordSpaces coordinates;int mouseX,mouseY;boolean escape;
        final com.badlogic.gdx.graphics.g2d.SpriteBatch batch=new com.badlogic.gdx.graphics.g2d.SpriteBatch();
        Fixture(float zoom,float density) {
            viewport.update(768,576,true);camera.position.set(356,432,0);camera.zoom=zoom;camera.update();stage.getViewport().update(768,576,true);
            coordinates=new CoordSpaces(camera,viewport,null,stage);
            PhysicsSelectionService physics=new PhysicsSelectionService();
            PickingSystem picking=new PickingSystem(camera,coordinates,input,history,history.historyIds(),stage,
                    new TiledMapRenderState(),physics,new PhysicsSelectionReconciler(physics),selection,new SpatialTileSelectionService(),null);
            GizmoSystem gizmo=new GizmoSystem(new games.pixscape.studio.helper.StudioDrawContext(batch,
                    new space.earlygrey.shapedrawer.ShapeDrawer(batch,VisUI.getSkin().getRegion("white")),camera,viewport),
                    input,coordinates,new TiledMapRenderState(),physics,selection,new SpatialTileSelectionService(),null,null);
            world=new World(new WorldConfigurationBuilder().with(new games.pixscape.runtime.system.DirtyTrackerSystem(32),
                    new games.pixscape.runtime.system.GameObjectHierarchySystem(),picking,gizmo).build());
            picking.setSelectionService(new SelectionService(world,null));
            map=world.create();tiled=world.getMapper(TiledLayerComponent.class).create(map);
            tiled.defaultTileHeight=128;tiled.data=new TiledMapLayerData(16,8,128,64,4,TiledProjection.ISO);
            tiled.data.originX=200;tiled.data.originY=200;
            for(int y=0;y<8;y++)for(int x=0;x<16;x++)tiled.data.setTile(x,y,1);
            blocks=world.getMapper(SpatialBlocksComponent.class).create(map);
            blocks.blocks.add(SpatialGestureGlChecks.wall(1,1,.5f,2,2.5f,1.2f,60));blocks.blocks.add(SpatialGestureGlChecks.wall(2,2,5,2,2,1.2f,90));blocks.nextSpatialBlockId=3;
            Gdx.input=(Input)Proxy.newProxyInstance(Input.class.getClassLoader(),new Class<?>[]{Input.class},(proxy,method,args)->{
                if(method.getName().equals("getX"))return mouseX;if(method.getName().equals("getY"))return mouseY;
                if(method.getName().equals("isKeyJustPressed"))return escape && (Integer)args[0]==Input.Keys.ESCAPE;
                if(method.getName().equals("isKeyPressed"))return false;
                return method.invoke(originalInput,args);
            });
            Gdx.graphics=(Graphics)Proxy.newProxyInstance(Graphics.class.getClassLoader(),new Class<?>[]{Graphics.class},(proxy,method,args)->{
                if(method.getName().equals("getBackBufferWidth"))return Math.round(768*density);
                if(method.getName().equals("getBackBufferHeight"))return Math.round(576*density);
                if(method.getName().equals("getDensity"))return density;
                return method.invoke(originalGraphics,args);
            });
            world.process();selection.selectBlock(map,1);history.historyIds().ensureForEntity(map);
        }
        void position(float gx,float gy){float[] point=new float[2];tiled.data.projectSpatialPoint(gx,gy,0,point,0);
            Vector2 screen=coordinates.worldToScreen(point[0],point[1],new Vector2());mouseX=Math.round(screen.x);mouseY=576-Math.round(screen.y);
            assertTrue("Native pointer must stay inside the logical canvas",mouseX>=0 && mouseX<768 && mouseY>=0 && mouseY<576);}
        void press(float gx,float gy){position(gx,gy);input.touchDown(mouseX,mouseY,0,Input.Buttons.LEFT);world.process();}
        void drag(float gx,float gy){position(gx,gy);input.touchDragged(mouseX,mouseY,0);world.process();}
        void release(){input.touchUp(mouseX,mouseY,0,Input.Buttons.LEFT);world.process();}
        SpatialBlockData wall(){return blocks.blocks.first();}
        public void close(){Gdx.input=originalInput;Gdx.graphics=originalGraphics;selection.dispose();world.dispose();stage.dispose();batch.dispose();}
    }
    static SpatialBlockData wall(int id,int structure,float x,float y,float width,float depth,float height){
        SpatialBlockData b=new SpatialBlockData();b.id=id;b.structureId=structure;b.x=x;b.y=y;b.width=width;b.depth=depth;b.height=height;
        b.beginAuthoredLinkedTileRefs();for(int gy=1;gy<5;gy++)for(int gx=0;gx<16;gx++)b.addLinkedTileRef(gx,gy,1);return b;
    }
}
