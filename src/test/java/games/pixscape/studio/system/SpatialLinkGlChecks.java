package games.pixscape.studio.system;

import com.artemis.World;
import com.artemis.WorldConfiguration;
import com.badlogic.gdx.Gdx;
import com.badlogic.gdx.files.FileHandle;
import com.badlogic.gdx.graphics.*;
import com.badlogic.gdx.math.Vector2;
import com.badlogic.gdx.physics.box2d.*;
import com.badlogic.gdx.scenes.scene2d.*;
import com.badlogic.gdx.scenes.scene2d.utils.ChangeListener;
import com.badlogic.gdx.scenes.scene2d.utils.ClickListener;
import com.badlogic.gdx.utils.viewport.ScreenViewport;
import com.kotcrab.vis.ui.VisUI;
import com.kotcrab.vis.ui.widget.*;
import games.pixscape.runtime.component.TiledLayerComponent;
import games.pixscape.runtime.component.TransformComponent;
import games.pixscape.runtime.component.physics.*;
import games.pixscape.runtime.component.spatial.SpatialBlocksComponent;
import games.pixscape.runtime.physics.*;
import games.pixscape.runtime.service.PhysicsService;
import games.pixscape.runtime.spatial.SpatialBlockData;
import games.pixscape.runtime.tiled.*;
import games.pixscape.studio.configuration.ProjectConfig;
import games.pixscape.studio.history.HistoryManager;
import games.pixscape.studio.service.spatial.*;
import games.pixscape.studio.ui.contextmenu.StudioContextMenu;
import games.pixscape.studio.ui.modal.SpatialLinkDialog;
import static org.junit.Assert.*;

/** Real native menu/dialog, linked fixture publication and actor passage, called by the GL smoke. */
final class SpatialLinkGlChecks {
    static SpatialBlocksComponent run() {
        ProjectConfig previous = ProjectConfig.getInstance();
        ProjectConfig config = new ProjectConfig(); config.createSceneMeta("Link fixture");
        config.getCurrentSceneMeta().pixelsPerMeter=32; ProjectConfig.setInstance(config);
        VisUI.load(new com.badlogic.gdx.scenes.scene2d.ui.Skin(Gdx.files.internal("assets/ui/skin/uiskin.json")));
        Stage stage=new Stage(new ScreenViewport()); stage.getViewport().update(768,576,true);
        World world=new World(new WorldConfiguration()); int map=world.create();
        TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(map);
        tiled.data=new TiledMapLayerData(12,12,32,16,4,TiledProjection.ISO);
        for(int y=0;y<12;y++)for(int x=0;x<12;x++)tiled.data.setTile(x,y,1);
        world.getMapper(TransformComponent.class).create(map).refreshCaches();
        SpatialBlocksComponent blocks=world.getMapper(SpatialBlocksComponent.class).create(map);
        blocks.blocks.add(wall(1,1,1,1,3,.3f,90)); blocks.blocks.add(wall(2,2,4,1,2,.15f,60));
        blocks.nextSpatialBlockId=3;
        PhysicsBodyComponent body=world.getMapper(PhysicsBodyComponent.class).create(map); body.type=PhysicsBodyComponent.STATIC;
        PhysicsShapesComponent shapes=world.getMapper(PhysicsShapesComponent.class).create(map);
        for(int id=1;id<=2;id++){PhysicsShapeData shape=new PhysicsShapeData();shape.physicsShapeId=id;shape.spatialBlockId=id;shapes.shapes.add(shape);}
        PhysicsShapeData manual=PhysicsService.createDefaultShape(3); manual.geometry.offsetX=1000; shapes.shapes.add(manual);
        PhysicsCompiledFixturesComponent compiled=world.getMapper(PhysicsCompiledFixturesComponent.class).create(map);
        world.process(); PhysicsService.publishPreparedCandidate(shapes,compiled,PhysicsService.prepareBodyCandidate(world,map,shapes.shapes,32));
        String manualBefore=new com.badlogic.gdx.utils.Json().toJson(shapes.shapes.peek());
        HistoryManager history=new HistoryManager(8); history.historyIds().ensureForEntity(map);
        SpatialBlockSelectionService selection=new SpatialBlockSelectionService(); selection.selectBlock(map,1);selection.toggleBlock(map,2);
        try {
            PopupMenu menu=new PopupMenu(); StudioContextMenu.appendSpatialLinkItems(menu,world,history,selection,stage,map);
            assertEquals(1,menu.getChildren().size); MenuItem link=(MenuItem)menu.getChildren().first();assertEquals("Lier",link.getText().toString());
            click(link); SpatialLinkDialog dialog=dialog(stage);assertNotNull(dialog);assertNotNull(selection.linkPreview());assertTrue(selection.linkPreview().valid);
            for(Actor actor:dialog.getContentTable().getChildren())if(actor instanceof games.pixscape.studio.ui.widget.SimpleSelectBox){
                @SuppressWarnings("unchecked") games.pixscape.studio.ui.widget.SimpleSelectBox<Integer> reference=(games.pixscape.studio.ui.widget.SimpleSelectBox<Integer>)actor;
                reference.setSelected(2);assertEquals(2,selection.linkPreview().referenceId);assertEquals(1,selection.linkPreview().selectedId);
                reference.setSelected(1);assertEquals(1,selection.linkPreview().referenceId);
            }
            Gdx.gl.glClearColor(.08f,.08f,.08f,1);Gdx.gl.glClear(GL20.GL_COLOR_BUFFER_BIT);
            stage.act(.5f); stage.act(.5f); stage.draw(); assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
            assertTrue("Visible preview alpha="+dialog.getColor().a,dialog.getColor().a>.9f);
            Pixmap screenshot=Pixmap.createFromFrameBuffer(0,0,768,576);
            assertNotEquals("Preview must actually paint",screenshot.getPixel(0,0),screenshot.getPixel(384,288));
            PixmapIO.PNG png=new PixmapIO.PNG();png.setFlipY(true);
            try{png.write(new FileHandle(System.getProperty("java.io.tmpdir")+"/pixscape-voluntary-link-preview.png"),screenshot);}
            catch(java.io.IOException failure){throw new RuntimeException(failure);}finally{png.dispose();screenshot.dispose();}
            button(dialog,"Cancel").fire(new ChangeListener.ChangeEvent());stage.act(1);
            assertEquals(0,blocks.links.size);assertNull(selection.linkPreview());assertFalse(history.canUndo());
            click(link);dialog=dialog(stage);button(dialog,"Lier").fire(new ChangeListener.ChangeEvent());stage.act(1);
            assertEquals(1,blocks.links.size);assertEquals(.3f,blocks.blocks.get(1).depth,0);assertTrue(compiled.valid);
            SpatialBlocksComponent linkedSnapshot=SpatialLinkProposal.copy(blocks);
            assertEquals(manualBefore,new com.badlogic.gdx.utils.Json().toJson(shapes.shapes.peek()));
            nativeActorPassage(compiled,tiled.data);
            verticalDialog(world,stage,history,selection,map,blocks,compiled,tiled.data,manualBefore,shapes);
            history.undo();assertEquals(0,blocks.links.size);history.redo();assertEquals(1,blocks.links.size);
            PopupMenu unlinkMenu=new PopupMenu();StudioContextMenu.appendSpatialLinkItems(unlinkMenu,world,history,selection,stage,map);
            MenuItem unlink=null;for(Actor actor:unlinkMenu.getChildren())if(actor instanceof MenuItem && ((MenuItem)actor).getText().toString().startsWith("Délier"))unlink=(MenuItem)actor;
            assertNotNull(unlink);click(unlink);dialog=dialog(stage);button(dialog,"Délier").fire(new ChangeListener.ChangeEvent());stage.act(1);
            assertEquals(0,blocks.links.size);assertEquals(.3f,blocks.blocks.get(1).depth,0);
            return linkedSnapshot;
        } finally { selection.dispose();world.dispose();stage.dispose();VisUI.dispose();ProjectConfig.setInstance(previous); }
    }

    private static void verticalDialog(World world,Stage stage,HistoryManager history,
                                       SpatialBlockSelectionService selection,int map,SpatialBlocksComponent blocks,
                                       PhysicsCompiledFixturesComponent compiled,TiledMapLayerData data,
                                       String manual,PhysicsShapesComponent shapes) {
        int upper=world.create();
        TiledLayerComponent tiled=world.getMapper(TiledLayerComponent.class).create(upper);
        tiled.projection=TiledProjection.ISO;tiled.tileWidth=32;tiled.tileHeight=16;
        tiled.mapWidthCells=tiled.mapHeightCells=12;tiled.chunkSize=4;
        tiled.defaultTileAltitude=100;tiled.originY=100;tiled.defaultTileHeight=90;tiled.data=tiled.createMapData();
        for(int y=0;y<12;y++)for(int x=0;x<12;x++)tiled.data.setTile(x,y,1);
        SpatialBlocksComponent reference=world.getMapper(SpatialBlocksComponent.class).create(upper);
        SpatialBlockData fixed=wall(1,1,1,1,3,.3f,90);fixed.altitude=100;reference.blocks.add(fixed);reference.nextSpatialBlockId=2;
        world.process();selection.selectBlock(map,1);
        PopupMenu menu=new PopupMenu();StudioContextMenu.appendSpatialLinkItems(menu,world,history,selection,stage,map);
        MenuItem item=null;for(Actor actor:menu.getChildren())if(actor instanceof MenuItem && ((MenuItem)actor).getText().toString().equals("Raccorder"))item=(MenuItem)actor;
        assertNotNull(item);click(item);
        games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog dialog=verticalDialog(stage);
        assertNotNull(dialog);assertTrue(selection.verticalJoinPreview().diagnostic,selection.verticalJoinPreview().valid);
        assertEquals(2,selection.verticalJoinPreview().affected.size());
        stage.act(1);stage.draw();assertEquals(GL20.GL_NO_ERROR,Gdx.gl.glGetError());
        verticalButton(dialog,"Cancel").fire(new ChangeListener.ChangeEvent());stage.act(1);
        assertNull(selection.verticalJoinPreview());assertEquals(90,blocks.blocks.first().height,0);
        click(item);dialog=verticalDialog(stage);verticalButton(dialog,"Raccorder").fire(new ChangeListener.ChangeEvent());stage.act(1);
        assertEquals(100,blocks.blocks.first().height,0);assertEquals(100,blocks.blocks.get(1).height,0);
        assertEquals(100,world.getMapper(TiledLayerComponent.class).get(map).defaultTileHeight,0);
        assertEquals(100,fixed.altitude,0);assertEquals(90,fixed.height,0);assertEquals(1,blocks.links.size);
        assertTrue(compiled.valid);assertEquals(manual,new com.badlogic.gdx.utils.Json().toJson(shapes.shapes.peek()));
        nativeActorPassage(compiled,data);
        history.undo();assertEquals(90,blocks.blocks.first().height,0);
        history.redo();assertEquals(100,blocks.blocks.first().height,0);
        history.undo();selection.toggleBlock(map,2);
    }
    private static games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog verticalDialog(Stage stage) {
        for(int i=stage.getActors().size-1;i>=0;i--)if(stage.getActors().get(i) instanceof games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog)
            return (games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog)stage.getActors().get(i);
        return null;
    }
    private static VisTextButton verticalButton(games.pixscape.studio.ui.modal.SpatialVerticalJoinDialog dialog,String text) {
        for(Actor actor:dialog.getButtonsTable().getChildren())if(actor instanceof VisTextButton && ((VisTextButton)actor).getText().toString().equals(text))return(VisTextButton)actor;
        throw new AssertionError(text);
    }

    private static void nativeActorPassage(PhysicsCompiledFixturesComponent compiled,TiledMapLayerData map) {
        Box2D.init(); com.badlogic.gdx.physics.box2d.World physics=new com.badlogic.gdx.physics.box2d.World(new Vector2(),true);
        try {
            Body wallBody=physics.createBody(new BodyDef());
            for(CompiledFixtureData fixture:compiled.fixtures)if(fixture.shapeType==PhysicsGeometryData.SHAPE_POLYGON){PolygonShape polygon=new PolygonShape();polygon.set(fixture.polygonVertices,0,fixture.polygonVertexCount*2);wallBody.createFixture(polygon,0);polygon.dispose();}
            BodyDef definition=new BodyDef();definition.type=BodyDef.BodyType.DynamicBody;
            float[] start=new float[2],end=new float[2]; map.projectSpatialPoint(3.5f,.8f,0,start,0);map.projectSpatialPoint(4.5f,.8f,0,end,0);
            definition.position.set(start[0]/32,start[1]/32); Body actor=physics.createBody(definition);
            CircleShape circle=new CircleShape();circle.setRadius(.02f);actor.createFixture(circle,1);circle.dispose();
            actor.setLinearVelocity((end[0]-start[0])/32,(end[1]-start[1])/32);
            for(int i=0;i<60;i++)physics.step(1f/60,6,2);
            assertEquals(end[0]/32,actor.getPosition().x,.01f);assertEquals(end[1]/32,actor.getPosition().y,.01f);
            // The same real linked fixtures block a transverse attempted crossing.
            map.projectSpatialPoint(4,.8f,0,start,0);map.projectSpatialPoint(4,1.6f,0,end,0);
            actor.setTransform(start[0]/32,start[1]/32,0);actor.setLinearVelocity((end[0]-start[0])/32,(end[1]-start[1])/32);
            for(int i=0;i<60;i++)physics.step(1f/60,6,2);
            assertTrue(actor.getPosition().dst(end[0]/32,end[1]/32)>.05f);
        }finally{physics.dispose();}
    }

    private static SpatialBlockData wall(int id,int structure,float x,float y,float width,float depth,float height){
        SpatialBlockData wall=new SpatialBlockData();wall.id=id;wall.structureId=structure;wall.x=x;wall.y=y;wall.width=width;wall.depth=depth;wall.height=height;
        wall.beginAuthoredLinkedTileRefs();for(int gy=(int)Math.floor(y);gy<(int)Math.ceil(y+depth);gy++)for(int gx=(int)Math.floor(x);gx<(int)Math.ceil(x+width);gx++)wall.addLinkedTileRef(gx,gy,1);return wall;
    }
    private static void click(MenuItem item){InputEvent event=new InputEvent();for(com.badlogic.gdx.scenes.scene2d.EventListener listener:item.getListeners())if(listener instanceof ClickListener)((ClickListener)listener).clicked(event,1,1);}
    private static SpatialLinkDialog dialog(Stage stage){for(int i=stage.getActors().size-1;i>=0;i--){Actor actor=stage.getActors().get(i);if(actor instanceof SpatialLinkDialog)return(SpatialLinkDialog)actor;}return null;}
    private static VisTextButton button(SpatialLinkDialog dialog,String text){for(Actor actor:dialog.getButtonsTable().getChildren())if(actor instanceof VisTextButton && ((VisTextButton)actor).getText().toString().equals(text))return(VisTextButton)actor;throw new AssertionError("Missing button "+text);}
}
